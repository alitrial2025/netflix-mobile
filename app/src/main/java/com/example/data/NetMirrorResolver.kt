package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import com.example.BuildConfig

data class NetMirrorStream(
    val url: String,
    val headers: Map<String, String>,
    val captions: List<Caption>,
    val sourceId: String,
    val expiresAt: Long,
    val title: String,
    val isRateLimited: Boolean = false,
    // Captions can remain on the provider even when the video source is on a CDN.
    // Keep this session in memory; download journals persist only local caption URIs.
    val captionHeaders: Map<String, String> = headers
)

data class Caption(
    val url: String,
    val language: String,
    val type: String,
    val languageCode: String = "en"
)

data class WarmSession(
    val domain: String,
    val addhashRaw: String,
    val addhashEncoded: String,
    val tHashTEncoded: String,
    val tHashTRaw: String,
    val fetchedAt: Long
)

data class TmdbInfo(
    val title: String,
    val year: String
)

data class SearchResult(
    val id: String,
    val title: String,
    val year: String,
    val ott: String,
    val score: Int
)

class AppCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val current = cookieStore.getOrPut(host) { mutableListOf() }
        synchronized(current) {
            for (newCookie in cookies) {
                current.removeAll { it.name == newCookie.name && it.domain == newCookie.domain && it.path == newCookie.path }
                current.add(newCookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val matching = mutableListOf<Cookie>()
        cookieStore.values.forEach { list ->
            synchronized(list) {
                list.removeAll { it.expiresAt <= System.currentTimeMillis() }
                matching.addAll(list.filter { it.matches(url) })
            }
        }
        return matching.distinctBy { Triple(it.name, it.domain, it.path) }
            .sortedByDescending { it.path.length }
    }

    fun getCookieValue(host: String, name: String): String? {
        val cookies = cookieStore[host] ?: return null
        synchronized(cookies) {
            return cookies.firstOrNull { it.name == name && it.expiresAt > System.currentTimeMillis() }?.value
        }
    }

    fun addManualCookie(host: String, name: String, value: String) {
        val current = cookieStore.getOrPut(host) { mutableListOf() }
        synchronized(current) {
            current.removeAll { it.name == name }
            val cookie = Cookie.Builder()
                .hostOnlyDomain(host)
                .secure()
                .name(name)
                .value(value)
                .path("/")
                .expiresAt(System.currentTimeMillis() + 86400000L)
                .build()
            current.add(cookie)
        }
    }

    fun clear() {
        cookieStore.clear()
    }
}

class NetMirrorResolver(private val context: Context, clientOverride: OkHttpClient? = null) {

    private val cookieJar = AppCookieJar()

    private val client = clientOverride ?: OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val metadataClient = client.newBuilder()
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS).build()
    private val publicConfig = ProviderRuntimeConfig(context, client, "netmirror_prefs")
    private val publicPlayback = PublicPlaybackResolver(client, catalog = PublicProviderCatalog(context),
        backgroundCatalogRefresh = clientOverride == null, runtimeConfig = publicConfig)
    // Domain Seed Pool & Fallback Mirrors discovered from reverse engineering
    private val DOMAIN_POOL = listOf(
        "net52.cc",
        "netmirror.app",
        "netmirror.gg",
        "mobidetect.art"
    )

    private val TMDB_API_KEY = BuildConfig.TMDB_API_KEY
    private val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 16; sdk_gphone64_x86_64 Build/BE2A.250530.026.D1; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/133.0.6943.137 " +
                "Mobile Safari/537.36 /OS.Gatu v3.0"
    private val SEC_CH_UA = "\"Not(A:Brand\";v=\"99\", \"Android WebView\";v=\"133\", \"Chromium\";v=\"133\""
    private val X_REQUESTED_WITH = "app.netmirror.netmirrornew"
    private val OTT_SEARCH_ORDER = listOf("nf", "pv", "hs", "dp", "hb", "atp", "pm", "pc", "hlu")
    private val SESSION_TTL_MS = 10 * 60 * 60 * 1000L
    private val SESSION_STORAGE_KEY = "netmirror_session"
    private val ACTIVE_DOMAIN_KEY = "netmirror_active_domain"

    companion object {
        @Volatile private var applicationResolver: NetMirrorResolver? = null
        fun getInstance(context: Context): NetMirrorResolver = applicationResolver ?: synchronized(this) {
            applicationResolver ?: NetMirrorResolver(context.applicationContext).also { applicationResolver = it }
        }
        @Volatile private var _session: WarmSession? = null
        private val sessionMutex = Mutex()
        private var cachedSourceRevision = PlaybackServiceGate.sourceRevision
        private val postCache = ConcurrentHashMap<String, Pair<Long, JSONObject>>()
        private val episodeCache = ConcurrentHashMap<String, Pair<Long, JSONArray>>()
        private val streamCache = ConcurrentHashMap<String, NetMirrorStream>()
        private val streamRequests = KeyedRequestGate()
        private val tmdbInfoCache = ConcurrentHashMap<String, TmdbInfo>()
        private val searchResultCache = ConcurrentHashMap<String, SearchResult>()
    }

    fun playbackCooldownMillis(): Long = PlaybackServiceGate.remainingMs()
    fun evictCachedStream(tmdbId: String, type: String, season: Int = 0, episode: Int = 0) {
        publicPlayback.evict(tmdbId, type, season, episode)
        streamCache.remove("${type}_${tmdbId}_${season}_${episode}")
    }
    /** Renew CDN links without throwing away a still-valid provider handshake. */
    suspend fun invalidateDownloadSession(tmdbId: String, type: String, season: Int, episode: Int, providerSessionRejected: Boolean = false) {
        sessionMutex.withLock {
            if (providerSessionRejected) {
                _session = null
                getPrefs().edit().remove(SESSION_STORAGE_KEY).apply()
            }
            evictCachedStream(tmdbId, type, season, episode)
        }
    }
    private class SessionRejectedException : java.io.IOException("Playback session expired. Please try again.")
    private fun rethrowControlFailure(error: Exception) {
        if (error is CancellationException || error is PlaybackRateLimitedException || error is SessionRejectedException) throw error
    }
    internal suspend fun fetch(request: Request): HttpTextResponse {
        if (request.url.host == "api.themoviedb.org") return client.fetchText(request)
        val execute: suspend () -> HttpTextResponse = {
            PlaybackServiceGate.check()
            val response = client.fetchText(request)
            currentCoroutineContext().ensureActive()
            PlaybackServiceGate.checkResponse(response.code, response.body, response.header("Retry-After"), request.url.toString())
            val usesSession = request.header("Cookie")?.contains("t_hash_t=") == true
            if (usesSession && (StreamSessionPolicy.isSessionRejected(response.code, response.body) ||
                StreamSessionPolicy.isAuthLandingPage(request.url.encodedPath, response.request.url.encodedPath, response.code, response.body))) {
                val rejectedHash = request.header("Cookie")?.split(';')?.map(String::trim)
                    ?.firstOrNull { it.startsWith("t_hash_t=") }?.substringAfter('=')
                if (_session?.tHashTEncoded == rejectedHash) {
                    _session = null
                    cookieJar.clear()
                    val saved = getPrefs().getString(SESSION_STORAGE_KEY, null)
                    val savedHash = try { saved?.let { JSONObject(it).optString("tHashTEncoded") } } catch (_: org.json.JSONException) { null }
                    if (savedHash == null || savedHash == rejectedHash)
                        getPrefs().edit().remove(SESSION_STORAGE_KEY).apply()
                    streamCache.clear()
                }
                throw SessionRejectedException()
            }
            if (usesSession && StreamSessionPolicy.isUnexpectedAuthenticatedHtml(request.url.encodedPath, response.code, response.body))
                throw java.io.IOException("Playback service is temporarily unavailable")
            response
        }
        return if (ProviderRequestPolicy.needsPacing(request, getSavedActiveDomain(), DOMAIN_POOL))
            PlaybackServiceGate.request(block = execute) else execute()
    }

    private fun getPrefs(): SharedPreferences {
        return context.getSharedPreferences("netmirror_prefs", Context.MODE_PRIVATE)
    }

    private fun getSavedActiveDomain(): String {
        return getPrefs().getString(ACTIVE_DOMAIN_KEY, DOMAIN_POOL.first()) ?: DOMAIN_POOL.first()
    }

    private fun saveActiveDomain(domain: String) {
        getPrefs().edit().putString(ACTIVE_DOMAIN_KEY, domain).apply()
    }

    private fun encodeURIComponent(s: String): String {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }

    private fun ottPathPrefix(ott: String): String {
        return when (ott) {
            "nf" -> "/mobile"
            "pv" -> "/mobile/pv"
            "hs" -> "/mobile/hs"
            "dp" -> "/mobile/dp"
            "hb" -> "/mobile/hb"
            "atp" -> "/mobile/atp"
            "pm" -> "/mobile/pm"
            "pc" -> "/mobile/pc"
            "hlu" -> "/mobile/hlu"
            else -> "/mobile"
        }
    }

    private fun mobileHeaders(referer: String, includeXRW: Boolean = true): Map<String, String> {
        val headers = mutableMapOf(
            "User-Agent" to MOBILE_UA,
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.9",
            "sec-ch-ua" to SEC_CH_UA,
            "sec-ch-ua-mobile" to "?1",
            "sec-ch-ua-platform" to "\"Android\"",
            "Sec-Fetch-Site" to "same-origin",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Dest" to "empty",
            "Referer" to referer
        )
        if (includeXRW) {
            headers["X-Requested-With"] = X_REQUESTED_WITH
        }
        return headers
    }

    private fun extractSetCookie(headers: okhttp3.Headers, name: String): String {
        val setCookies = headers.values("Set-Cookie")
        for (h in setCookies) {
            val trimmed = h.trim()
            if (trimmed.startsWith("$name=")) {
                return trimmed.substring(name.length + 1).split(";")[0].trim()
            }
        }
        return ""
    }

    data class AddhashResult(
        val raw: String,
        val encoded: String,
        val quryParam: String = "hee5",
        val vsiteSubdomain: String = "userver",
        val verifyEndpoint: String = "/mobile/verify2.php",
        val resolvedDomain: String = "net52.cc"
    )

    /**
     * Resolves the active domain dynamically by testing the cached domain first
     * and rotating through the fallback pool if needed.
     */
    private suspend fun resolveActiveDomain(): String = withContext(Dispatchers.IO) {
        val cached = getSavedActiveDomain()
        val candidateList = publicConfig.snapshot().bootstrapDomains(listOf(cached) + DOMAIN_POOL)

        for (cand in candidateList) {
            try {
                val req = Request.Builder()
                    .url("https://$cand/mobile/home?app=1")
                    .header("User-Agent", MOBILE_UA)
                    .build()
                val res = fetch(req)
                val finalHost = res.request.url.host

                if (res.isSuccessful || res.code == 301 || res.code == 302) {
                    val active = if (finalHost.isNotEmpty()) finalHost else cand
                    Log.d("NetMirror", "🌐 Active Domain Resolved: $active (tested $cand)")
                    saveActiveDomain(active)
                    return@withContext active
                }
            } catch (e: Exception) {
                rethrowControlFailure(e)
                Log.d("NetMirror", "⚠️ Domain $cand unreachable: ${e.message}")
            }
        }
        return@withContext candidateList.first()
    }

    /**
     * Dynamic extraction of addhash, Qury parameter, Vsite subdomain, and verify endpoint
     * using generalized regex patterns to withstand backend shifts.
     */
    private suspend fun fetchAddhash(domain: String): AddhashResult? = withContext(Dispatchers.IO) {
        val url = "https://$domain/mobile/home?app=1"
        Log.d("NetMirror", "🌐 Step 1: GET $url")

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", MOBILE_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("sec-ch-ua", SEC_CH_UA)
                .header("sec-ch-ua-mobile", "?1")
                .header("sec-ch-ua-platform", "\"Android\"")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")
                .header("x-requested-with", "")
                .build()

            val res = fetch(req)
            val finalDomain = res.request.url.host.ifBlank { domain }
            val headers = res.headers
            val body = res.body

            var encoded = extractSetCookie(headers, "addhash")
            if (encoded.isEmpty()) {
                encoded = cookieJar.getCookieValue(finalDomain, "addhash") ?: ""
            }
            var raw = ""

            if (encoded.isNotEmpty()) {
                raw = URLDecoder.decode(encoded, "UTF-8")
                Log.d("NetMirror", "addhash received from response")
            } else {
                // Generalized regex patterns for HTML attributes & script variables
                val m1 = Regex("""data-hash=["']([^"']+)["']""").find(body)
                val m2 = Regex("""data-addhash=["']([^"']+)["']""").find(body)
                val m3 = Regex("""data-token=["']([^"']+)["']""").find(body)
                val m4 = Regex("""(?:var|window\.)addhash\s*=\s*["']([^"']+)["']""").find(body)
                val m5 = Regex("""\b([A-Za-z0-9+/=]{10,}::[A-Za-z0-9+/=]{4,}::[A-Za-z0-9+/=]{4,})\b""").find(body)

                val m = m1 ?: m2 ?: m3 ?: m4 ?: m5
                if (m != null) {
                    raw = m.groupValues[1]
                    encoded = encodeURIComponent(raw)
                    Log.d("NetMirror", "addhash received from provider page")
                } else {
                    Log.d("NetMirror", "❌ No addhash cookie or HTML attribute found")
                    return@withContext null
                }
            }

            val parts = raw.split("::")
            if (parts.size < 3) {
                Log.d("NetMirror", "Invalid addhash format (${parts.size} parts)")
                return@withContext null
            }

            // Dynamically extract Qury parameter name (e.g. hee5, ffr455, etc.)
            val quryMatch1 = Regex("""var\s+Qury\s*=\s*["']([^"']+)["']""").find(body)
            val quryMatch2 = Regex("""(?:window\.)?Qury\s*=\s*["']([^"']+)["']""").find(body)
            val quryMatch3 = Regex("""\?([a-zA-Z0-9_]{3,10})=\s*\+\s*encodeURIComponent""").find(body)
            val quryParam = quryMatch1?.groupValues?.get(1)
                ?: quryMatch2?.groupValues?.get(1)
                ?: quryMatch3?.groupValues?.get(1)
                ?: "hee5"

            // Dynamically extract Vsite subdomain (e.g. userver, vsite2, etc.)
            val vsiteMatch1 = Regex("""var\s+Vsite2?\s*=\s*["']([^"']+)["']""").find(body)
            val vsiteMatch2 = Regex("""(?:window\.)?Vsite2?\s*=\s*["']([^"']+)["']""").find(body)
            val vsiteSubdomain = vsiteMatch1?.groupValues?.get(1)
                ?: vsiteMatch2?.groupValues?.get(1)
                ?: "userver"

            // Dynamically extract verify endpoint
            val verifyMatch = Regex("""['"]/(?:mobile/)?(verify[0-9]*\.php)['"]""").find(body)
            val verifyEndpoint = if (verifyMatch != null) "/mobile/${verifyMatch.groupValues[1]}" else "/mobile/verify2.php"

            Log.d("NetMirror", "✅ Parsed Handshake -> Qury: $quryParam | Vsite: $vsiteSubdomain | Verify: $verifyEndpoint | Domain: $finalDomain")
            return@withContext AddhashResult(raw, encoded, quryParam, vsiteSubdomain, verifyEndpoint, finalDomain)
        } catch (e: Exception) {
            rethrowControlFailure(e)
            Log.d("NetMirror", "❌ fetchAddhash failed: ${e.message}")
            return@withContext null
        }
    }

    private suspend fun triggerUserver(
        domain: String,
        addhashRaw: String,
        quryParam: String = "hee5",
        vsiteSubdomain: String = "userver"
    ) = withContext(Dispatchers.IO) {
        val ffr = encodeURIComponent(addhashRaw)
        val t = Math.random().toString()
        val url = "https://$vsiteSubdomain.$domain/?$quryParam=$ffr&a=y&t=$t"
        Log.d("NetMirror", "Triggering provider handshake on $vsiteSubdomain with param $quryParam")

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", MOBILE_UA)
                .header("sec-ch-ua", SEC_CH_UA)
                .header("sec-ch-ua-mobile", "?1")
                .header("sec-ch-ua-platform", "\"Android\"")
                .header("Referer", "https://$domain/")
                .build()
            fetch(req)
            Log.d("NetMirror", "✅ userver triggered")
        } catch (e: Exception) {
            rethrowControlFailure(e)
            Log.d("NetMirror", "⚠️ userver error (non-fatal): ${e.message}")
        }
    }

    private suspend fun pollVerify2(
        domain: String,
        addhashEncoded: String,
        verifyEndpoint: String = "/mobile/verify2.php",
        maxAttempts: Int = 40,
        delayMs: Long = 1200
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        Log.d("NetMirror", "🔑 Step 4: Polling $verifyEndpoint (up to $maxAttempts attempts)...")
        val url = "https://$domain$verifyEndpoint"

        for (i in 1..maxAttempts) {
            try {
                val reqBody = "verify=$addhashEncoded".toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaType())
                val req = Request.Builder()
                    .url(url)
                    .post(reqBody)
                    .header("User-Agent", MOBILE_UA)
                    .header("sec-ch-ua", SEC_CH_UA)
                    .header("sec-ch-ua-mobile", "?1")
                    .header("sec-ch-ua-platform", "\"Android\"")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Origin", "https://$domain")
                    .header("Referer", "https://$domain/mobile/home?app=1")
                    .header("Cookie", "addhash=$addhashEncoded")
                    .build()

                val res = fetch(req)
                val bodyText = res.body
                Log.d("NetMirror", "$verifyEndpoint attempt $i: HTTP ${res.code}")

                var tHashT = extractSetCookie(res.headers, "t_hash_t")
                if (tHashT.isEmpty()) {
                    tHashT = cookieJar.getCookieValue(domain, "t_hash_t") ?: ""
                }
                if (tHashT.isNotEmpty()) {
                    val raw = URLDecoder.decode(tHashT, "UTF-8")
                    Log.d("NetMirror", "Session cookie received on attempt $i")
                    return@withContext Pair(tHashT, raw)
                }
                if (i < maxAttempts) {
                    delay(delayMs)
                }
            } catch (e: Exception) {
                rethrowControlFailure(e)
                Log.d("NetMirror", "⚠️ $verifyEndpoint #$i error: ${e.message}")
                if (i < maxAttempts) {
                    delay(delayMs)
                }
            }
        }
        Log.d("NetMirror", "❌ $verifyEndpoint exhausted $maxAttempts attempts")
        return@withContext null
    }

    /**
     * Optional Silent WebView Fallback (executed ONLY on handshake failure)
     * Automatically extracts cookies from background WebView if JS challenges are encountered.
     */
    private suspend fun silentWebViewWarmup(domain: String): Pair<String, String>? = withContext(Dispatchers.Main) {
        Log.d("NetMirror", "🌐 Invoking Optional Silent WebView Fallback for $domain...")
        val deferred = CompletableDeferred<Pair<String, String>?>()
        val webView = WebView(context)
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = MOBILE_UA
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        var completed = false
        val checkCookies = object : Runnable {
            override fun run() {
                if (completed) return
                val cookies = cookieManager.getCookie("https://$domain") ?: ""
                val addhashMatch = Regex("""addhash=([^;]+)""").find(cookies)
                val tHashMatch = Regex("""t_hash_t=([^;]+)""").find(cookies)

                if (addhashMatch != null && tHashMatch != null) {
                    completed = true
                    val addhashEnc = addhashMatch.groupValues[1]
                    val tHashEnc = tHashMatch.groupValues[1]
                    Log.d("NetMirror", "✅ Silent WebView successfully retrieved cookies!")
                    deferred.complete(Pair(addhashEnc, tHashEnc))
                } else {
                    Handler(Looper.getMainLooper()).postDelayed(this, 1000)
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                Log.w("NetMirror", "⚠️ WebView render process gone; cleaning up silently.")
                completed = true
                deferred.complete(null)
                return true
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                Log.d("NetMirror", "⚠️ WebView resource error: ${error?.description}")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Handler(Looper.getMainLooper()).post(checkCookies)
            }
        }

        webView.loadUrl("https://$domain/mobile/home?app=1")

        val result = withTimeoutOrNull(25000) {
            deferred.await()
        }

        completed = true
        webView.stopLoading()
        webView.destroy()
        return@withContext result
    }

    private suspend fun refreshSession(
        domain: String,
        addhashEncoded: String,
        tHashTEncoded: String
    ) = withContext(Dispatchers.IO) {
        Log.d("NetMirror", "🔄 Step 5: Refreshing session (returning-session home)...")
        try {
            cookieJar.addManualCookie(domain, "addhash", addhashEncoded)
            cookieJar.addManualCookie(domain, "t_hash_t", tHashTEncoded)
            cookieJar.addManualCookie(domain, "lang", "eng")

            val req = Request.Builder()
                .url("https://$domain/mobile/home?app=1")
                .header("Cache-Control", "max-age=0")
                .header("User-Agent", MOBILE_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("sec-ch-ua", SEC_CH_UA)
                .header("sec-ch-ua-mobile", "?1")
                .header("sec-ch-ua-platform", "\"Android\"")
                .header("Upgrade-Insecure-Requests", "1")
                .header("X-Requested-With", X_REQUESTED_WITH)
                .header("Sec-Fetch-Site", "same-origin")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Dest", "document")
                .header("Referer", "https://$domain/mobile/home?app=1")
                .header("Cookie", "addhash=$addhashEncoded; t_hash_t=$tHashTEncoded")
                .build()
            fetch(req)
            Log.d("NetMirror", "✅ Session refreshed (returning-session)")
        } catch (e: Exception) {
            rethrowControlFailure(e)
            Log.d("NetMirror", "⚠️ Session refresh failed: ${e.message}")
        }
    }

    private suspend fun warmSession(requiredDomain: String? = null): WarmSession? = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val domain = requiredDomain ?: resolveActiveDomain()
        Log.d("NetMirror", "━━━ Session warmup START on $domain ━━━")

        val addhashRes = fetchAddhash(domain)
        if (addhashRes != null) {
            val addhashRaw = addhashRes.raw
            val addhashEncoded = addhashRes.encoded
            val activeDomain = addhashRes.resolvedDomain
            if (!ProviderRuntimeConfig.sessionMatches(activeDomain, requiredDomain))
                throw java.io.IOException("Generated session does not match the playback origin")

            triggerUserver(activeDomain, addhashRaw, addhashRes.quryParam, addhashRes.vsiteSubdomain)
            delay(1000)

            val tHashTPair = pollVerify2(activeDomain, addhashEncoded, addhashRes.verifyEndpoint, maxAttempts = 40, delayMs = 1200)
            if (tHashTPair != null) {
                val (tHashTEncoded, tHashTRaw) = tHashTPair
                refreshSession(activeDomain, addhashEncoded, tHashTEncoded)

                val session = WarmSession(
                    domain = activeDomain,
                    addhashRaw = addhashRaw,
                    addhashEncoded = addhashEncoded,
                    tHashTEncoded = tHashTEncoded,
                    tHashTRaw = tHashTRaw,
                    fetchedAt = System.currentTimeMillis()
                )

                saveSessionToStorage(session)
                Log.d("NetMirror", "━━━ Native Session warmup DONE in ${(System.currentTimeMillis() - t0) / 1000}s ━━━")
                return@withContext session
            }
        }

        // Silent Fallback if Native HTTP Handshake Encountered JS/Anti-Bot Challenge
        Log.d("NetMirror", "⚠️ Native handshake failed — triggering on-demand silent fallback...")
        val fallbackResult = silentWebViewWarmup(domain)
        if (fallbackResult != null) {
            val (addhashEnc, tHashEnc) = fallbackResult
            val addhashRaw = URLDecoder.decode(addhashEnc, "UTF-8")
            val tHashRaw = URLDecoder.decode(tHashEnc, "UTF-8")
            refreshSession(domain, addhashEnc, tHashEnc)

            val session = WarmSession(
                domain = domain,
                addhashRaw = addhashRaw,
                addhashEncoded = addhashEnc,
                tHashTEncoded = tHashEnc,
                tHashTRaw = tHashRaw,
                fetchedAt = System.currentTimeMillis()
            )
            saveSessionToStorage(session)
            Log.d("NetMirror", "━━━ Fallback Session warmup DONE in ${(System.currentTimeMillis() - t0) / 1000}s ━━━")
            return@withContext session
        }

        Log.d("NetMirror", "❌ Session warmup FAILED")
        return@withContext null
    }

    private fun saveSessionToStorage(session: WarmSession) {
        val json = JSONObject().apply {
            put("domain", session.domain)
            put("addhashRaw", session.addhashRaw)
            put("addhashEncoded", session.addhashEncoded)
            put("tHashTEncoded", session.tHashTEncoded)
            put("tHashTRaw", session.tHashTRaw)
            put("fetchedAt", session.fetchedAt)
        }
        getPrefs().edit().putString(SESSION_STORAGE_KEY, json.toString()).apply()
    }

    fun restoreSessionFromStorage(): Boolean {
        val existing = _session
        if (existing != null && StreamSessionPolicy.isFresh(existing.fetchedAt, System.currentTimeMillis())) {
            return true
        }
        val storedStr = getPrefs().getString(SESSION_STORAGE_KEY, null) ?: return false
        return try {
            val json = JSONObject(storedStr)
            val fetchedAt = json.getLong("fetchedAt")
            if (StreamSessionPolicy.isFresh(fetchedAt, System.currentTimeMillis())) {
                val restored = WarmSession(
                    domain = json.getString("domain"),
                    addhashRaw = json.getString("addhashRaw"),
                    addhashEncoded = json.getString("addhashEncoded"),
                    tHashTEncoded = json.getString("tHashTEncoded"),
                    tHashTRaw = json.getString("tHashTRaw"),
                    fetchedAt = fetchedAt
                )
                _session = restored
                cookieJar.addManualCookie(restored.domain, "addhash", restored.addhashEncoded)
                cookieJar.addManualCookie(restored.domain, "t_hash_t", restored.tHashTEncoded)
                cookieJar.addManualCookie(restored.domain, "lang", "eng")
                Log.d("NetMirror", "💾 Restored session from storage")
                true
            } else {
                Log.d("NetMirror", "🗑️ Stored session expired")
                getPrefs().edit().remove(SESSION_STORAGE_KEY).apply()
                false
            }
        } catch (e: Exception) {
            rethrowControlFailure(e)
            Log.e("NetMirror", "Failed to read session", e)
            false
        }
    }

    fun isSessionWarm(): Boolean {
        return _session?.let { StreamSessionPolicy.isFresh(it.fetchedAt, System.currentTimeMillis()) } == true
    }

    suspend fun getSession(requiredDomain: String? = null): WarmSession? = withContext(Dispatchers.IO) {
        if (requiredDomain != null && !ProviderRuntimeConfig.sessionMatches(requiredDomain, requiredDomain))
            throw java.io.IOException("Invalid session origin")
        if (restoreSessionFromStorage()) {
            val restored = _session
            if (restored != null && ProviderRuntimeConfig.sessionMatches(restored.domain, requiredDomain))
                return@withContext restored
        }

        sessionMutex.withLock {
            val existing = _session
            if (existing != null && StreamSessionPolicy.isFresh(existing.fetchedAt, System.currentTimeMillis()) &&
                ProviderRuntimeConfig.sessionMatches(existing.domain, requiredDomain)) {
                return@withContext existing
            }
            // Storage and the current session remain intact if targeted warming fails.
            cookieJar.clear()
            val result = warmSession(requiredDomain)
            if (result != null) _session = result
            return@withContext result
        }
    }

    private suspend fun getTmdbInfo(tmdbId: String, type: String, cardTitle: String = "", cardYear: String = ""): TmdbInfo = withContext(Dispatchers.IO) {
        val cacheKey = "${type}_$tmdbId"
        val cached = tmdbInfoCache[cacheKey]
        if (cached != null) {
            return@withContext cached
        }
        if (cardTitle.isNotBlank() && cardYear.matches(Regex("[0-9]{4}")) && cardTitle.any { it in 'A'..'Z' || it in 'a'..'z' }) {
            return@withContext TmdbInfo(cardTitle, cardYear)
        }
        try {
            val url = "https://api.themoviedb.org/3/$type/$tmdbId?api_key=$TMDB_API_KEY"
            val req = Request.Builder().url(url).build()
            val body = metadataClient.fetchText(req).body
            val json = JSONObject(body)
            val title = json.optString("title").takeIf { it.isNotEmpty() } ?: json.optString("name")
            val dateStr = json.optString("release_date").takeIf { it.isNotEmpty() } ?: json.optString("first_air_date")
            val year = dateStr.split("-").firstOrNull() ?: ""
            val info = TmdbInfo(title, year)
            tmdbInfoCache[cacheKey] = info
            info
        } catch (e: Exception) {
            rethrowControlFailure(e)
            Log.w("NetMirror", "Failed to fetch TMDB info: ${e.message}")
            TmdbInfo("", "")
        }
    }

    private fun normalizeTitle(s: String): String {
        return s.lowercase()
            .replace(Regex("^(the|a|an)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    private fun wordOverlapScore(a: String, b: String): Double {
        val wordsA = a.split(" ").filter { it.length > 1 }.toSet()
        val wordsB = b.split(" ").filter { it.length > 1 }.toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0.0
        var overlap = 0.0
        for (w in wordsA) {
            if (wordsB.contains(w)) overlap++
        }
        return overlap / minOf(wordsA.size, wordsB.size)
    }

    private fun sanitizeSearchQuery(title: String): String {
        return title.replace(Regex("Tyler Perry's\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+S\\d+E\\d+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+Season\\s+\\d+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+Episode\\s+\\d+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    private fun searchVariants(cleanTitle: String): List<String> {
        val variants = mutableListOf(cleanTitle)
        val words = cleanTitle.split(" ")
        if (words.size >= 4) variants.add(words.take(3).joinToString(" "))
        if (words.size >= 3) variants.add(words.take(2).joinToString(" "))
        if (words.isNotEmpty() && words[0].length >= 4) variants.add(words[0])
        return variants.distinct()
    }

    private fun ottSearchPaths(ott: String): List<String> {
        val prefix = ottPathPrefix(ott)
        return if (prefix == "/mobile") {
            listOf("/search.php", "/mobile/search.php")
        } else {
            listOf("$prefix/search.php", "/search.php", "/mobile/search.php")
        }
    }

    private fun extractResults(parsed: Any?): JSONArray {
        if (parsed is JSONArray) return parsed
        if (parsed is JSONObject) {
            if (parsed.has("searchResult")) {
                val searchResult = parsed.optJSONArray("searchResult")
                if (searchResult != null && searchResult.length() > 0) {
                    if (parsed.optString("status") == "n") return JSONArray()
                    return searchResult
                }
            }
        }
        return JSONArray()
    }

    private fun findBestMatch(results: JSONArray, searchTitle: String, searchYear: String, ott: String): SearchResult? {
        if (results.length() == 0) return null
        val searchNorm = normalizeTitle(searchTitle)
        if (searchNorm.isEmpty()) return null

        data class ScoredResult(val r: JSONObject, val score: Int, val reason: String)
        val scored = mutableListOf<ScoredResult>()

        val searchWords = searchNorm.split(" ").filter { it.length > 2 }

        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            val rId = r.optString("id").takeIf { it.isNotEmpty() } ?: r.optString("Id")
            if (rId.isEmpty()) continue

            val rTitle = r.optString("t").takeIf { it.isNotEmpty() } ?: r.optString("title").takeIf { it.isNotEmpty() } ?: r.optString("T").takeIf { it.isNotEmpty() } ?: r.optString("Title")
            val rYear = r.optString("y").takeIf { it.isNotEmpty() } ?: r.optString("year").takeIf { it.isNotEmpty() } ?: r.optString("Y").takeIf { it.isNotEmpty() } ?: r.optString("Year")
            val rNorm = normalizeTitle(rTitle)

            val isExactTitle = rNorm == searchNorm
            val isYearMatch = if (searchYear.isNotEmpty() && rYear.length == 4 && searchYear.length == 4) {
                val yearDiff = Math.abs((searchYear.toIntOrNull() ?: 0) - (rYear.toIntOrNull() ?: 0))
                if (isExactTitle) {
                    yearDiff <= 4
                } else {
                    if (yearDiff > 2) continue
                    yearDiff == 0
                }
            } else isExactTitle

            val rWords = rNorm.split(" ").filter { it.length > 2 }
            val missingSearchWords = searchWords.filter { !rWords.contains(it) }

            if (!isExactTitle && missingSearchWords.size >= 2) {
                continue
            }

            if (isExactTitle) {
                val baseScore = if (isYearMatch) 100 else 90
                scored.add(ScoredResult(r, baseScore, "exact"))
                continue
            }

            if (rNorm.startsWith("$searchNorm ") || searchNorm.startsWith("$rNorm ") ||
                rNorm.startsWith(searchNorm) || searchNorm.startsWith(rNorm)) {
                val lenRatio = rNorm.length.toDouble() / maxOf(searchNorm.length, 1)
                if ((searchNorm.length > 6 || lenRatio <= 1.8) && missingSearchWords.isEmpty()) {
                    val baseScore = if (isYearMatch) 85 else 70
                    scored.add(ScoredResult(r, baseScore, "prefix"))
                    continue
                }
            }

            val overlap = wordOverlapScore(searchNorm, rNorm)
            if (overlap >= 0.7 && missingSearchWords.isEmpty()) {
                val baseScore = (60 + overlap * 20).toInt() + (if (isYearMatch) 10 else 0)
                scored.add(ScoredResult(r, baseScore, "overlap"))
                continue
            }

            if (searchNorm.length > 5 && (rNorm.contains(searchNorm) || searchNorm.contains(rNorm)) && missingSearchWords.isEmpty()) {
                val baseScore = 50 + (if (isYearMatch) 10 else 0)
                scored.add(ScoredResult(r, baseScore, "contains"))
                continue
            }
        }

        if (scored.isEmpty()) return null
        scored.sortByDescending { it.score }
        val best = scored.first()
        if (best.score < 30) return null

        val rTitle = best.r.optString("t").takeIf { it.isNotEmpty() } ?: best.r.optString("title").takeIf { it.isNotEmpty() } ?: best.r.optString("T").takeIf { it.isNotEmpty() } ?: best.r.optString("Title")
        val rYear = best.r.optString("y").takeIf { it.isNotEmpty() } ?: best.r.optString("year").takeIf { it.isNotEmpty() } ?: best.r.optString("Y").takeIf { it.isNotEmpty() } ?: best.r.optString("Year")
        val rId = best.r.optString("id").takeIf { it.isNotEmpty() } ?: best.r.optString("Id")

        Log.d("NetMirror", "✅ Search match [${ott.uppercase()}]: \"$rTitle\" ($rYear) ID: $rId [${best.reason}, score=${best.score}]")
        return SearchResult(rId, rTitle, rYear, ott, best.score)
    }

    private suspend fun searchContent(
        domain: String,
        searchTitle: String,
        searchYear: String,
        cookie: String,
        ott: String = "nf"
    ): SearchResult? = withContext(Dispatchers.IO) {
        val base = "https://$domain"
        val cleanTitle = sanitizeSearchQuery(searchTitle)
        val variants = searchVariants(cleanTitle)
        val paths = ottSearchPaths(ott)

        Log.d("NetMirror", "🔍 Searching \"$cleanTitle\" on $domain [${ott.uppercase()}]")

        for (query in variants) {
            for (searchPath in paths) {
                try {
                    val ts = System.currentTimeMillis() / 1000
                    val searchUrl = "$base$searchPath?s=${encodeURIComponent(query)}&t=$ts"
                    val req = Request.Builder()
                        .url(searchUrl)
                        .header("User-Agent", MOBILE_UA)
                        .header("Cookie", cookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", "$base/")
                        .build()

                    val res = fetch(req)
                    val bodyText = res.body

                    var parsed: Any? = null
                    try {
                        parsed = if (bodyText.trim().startsWith("[")) JSONArray(bodyText) else JSONObject(bodyText)
                    } catch (e: Exception) {
                        rethrowControlFailure(e)
                        Log.d("NetMirror", "❌ non-JSON response")
                        continue
                    }

                    val results = extractResults(parsed)
                    if (results.length() == 0) continue

                    val match = findBestMatch(results, searchTitle, searchYear, ott)
                    if (match != null) return@withContext match
                } catch (e: Exception) {
                    rethrowControlFailure(e)
                    Log.d("NetMirror", "❌ $searchPath error: ${e.message}")
                }
            }
        }
        return@withContext null
    }

    private suspend fun fetchPostDetail(
        domain: String,
        showId: String,
        cookie: String,
        ott: String
    ): JSONObject? = withContext(Dispatchers.IO) {
        val cacheKey = "$domain:$ott:$showId"
        postCache[cacheKey]?.takeIf { StreamSessionPolicy.isFresh(it.first, System.currentTimeMillis()) }?.let { return@withContext it.second }
        val ts = System.currentTimeMillis() / 1000
        val prefix = ottPathPrefix(ott)
        val url = "https://$domain$prefix/post.php?id=$showId&t=$ts"
        try {
            val reqBuilder = Request.Builder().url(url)
            mobileHeaders("https://$domain/mobile/home?app=1").forEach { (k, v) -> reqBuilder.header(k, v) }
            reqBuilder.header("X-Requested-With", "XMLHttpRequest").header("Cookie", cookie)
            val res = fetch(reqBuilder.build())
            val bodyText = res.body
            val post = JSONObject(bodyText)
            if (post.has("season") || post.has("seasons") || post.has("episodes")) {
                if (postCache.size >= 32) postCache.keys.firstOrNull()?.let(postCache::remove)
                postCache[cacheKey] = System.currentTimeMillis() to post
            }
            return@withContext post
        } catch (e: Exception) {
            rethrowControlFailure(e)
            return@withContext null
        }
    }

    private fun findEpisodeId(episodes: JSONArray, season: Int, episode: Int): String? {
        for (i in 0 until episodes.length()) {
            val ep = episodes.optJSONObject(i) ?: continue
            val rawS = ep.optString("s")
                .ifBlank { ep.optString("season") }
                .ifBlank { ep.optString("s_num") }
                .ifBlank { ep.optString("season_number") }
            val rawEp = ep.optString("ep")
                .ifBlank { ep.optString("episode") }
                .ifBlank { ep.optString("e") }
                .ifBlank { ep.optString("ep_num") }
                .ifBlank { ep.optString("episode_number") }
                .ifBlank { ep.optString("num") }
                .ifBlank { ep.optString("name") }
                .ifBlank { ep.optString("title") }

            var parsedS = rawS.lowercase().replace("season", "").replace("s", "").trim().toIntOrNull()
            if (parsedS == null && rawS.isNotBlank()) {
                val match = Regex("""(?:season|s|^|\b)(\d+)""", RegexOption.IGNORE_CASE).find(rawS)
                parsedS = match?.groupValues?.getOrNull(1)?.toIntOrNull()
            }

            var parsedE = rawEp.lowercase()
                .replace("episode", "")
                .replace("ep", "")
                .replace("e", "")
                .trim()
                .toIntOrNull()
            if (parsedE == null && rawEp.isNotBlank()) {
                val match = Regex("""(?:ep(?:isode)?\s*|e|^|\b)(\d+)(?:\b|\.|\s|$)""", RegexOption.IGNORE_CASE).find(rawEp)
                parsedE = match?.groupValues?.getOrNull(1)?.toIntOrNull()
            }

            if (parsedE == episode) {
                if (parsedS == null || parsedS == season) {
                    val id = ep.optString("id").takeIf { it.isNotEmpty() } ?: ep.optString("Id")
                    if (!id.isNullOrEmpty()) return id
                }
            }
        }

        return null
    }

    private suspend fun fetchSeasonEpisodes(
        domain: String,
        seasonId: String,
        showId: String,
        cookie: String,
        ott: String
    ): JSONArray? = withContext(Dispatchers.IO) {
        val cacheKey = "$domain:$ott:$showId:$seasonId"
        episodeCache[cacheKey]?.takeIf { StreamSessionPolicy.isFresh(it.first, System.currentTimeMillis()) }?.let { return@withContext it.second }
        val ts = System.currentTimeMillis() / 1000
        val prefix = ottPathPrefix(ott)
        val url = "https://$domain$prefix/episodes.php?s=$seasonId&series=$showId&t=$ts"
        try {
            val reqBuilder = Request.Builder().url(url)
            mobileHeaders("https://$domain/mobile/home?app=1").forEach { (k, v) -> reqBuilder.header(k, v) }
            reqBuilder.header("X-Requested-With", "XMLHttpRequest").header("Cookie", cookie)
            val res = fetch(reqBuilder.build())
            val bodyText = res.body
            val json = JSONObject(bodyText)
            val episodes = json.optJSONArray("episodes")
            if (episodes != null && episodes.length() > 0) {
                if (episodeCache.size >= 32) episodeCache.keys.firstOrNull()?.let(episodeCache::remove)
                episodeCache[cacheKey] = System.currentTimeMillis() to episodes
            }
            return@withContext episodes
        } catch (e: Exception) {
            rethrowControlFailure(e)
            return@withContext null
        }
    }

    private suspend fun fetchPlaylist(
        domain: String,
        contentId: String,
        title: String,
        cookie: String,
        ott: String
    ): Pair<String, List<Caption>>? = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis() / 1000
        val prefix = ottPathPrefix(ott)
        val candidateUrls = if (prefix == "/mobile") {
            listOf(
                "https://$domain/mobile/playlist.php?id=${encodeURIComponent(contentId)}&t=${encodeURIComponent(title)}&tm=$ts",
                "https://$domain/playlist.php?id=${encodeURIComponent(contentId)}&t=${encodeURIComponent(title)}&tm=$ts"
            )
        } else {
            listOf(
                "https://$domain$prefix/playlist.php?id=${encodeURIComponent(contentId)}&t=${encodeURIComponent(title)}&tm=$ts",
                "https://$domain/mobile/playlist.php?id=${encodeURIComponent(contentId)}&t=${encodeURIComponent(title)}&tm=$ts",
                "https://$domain/playlist.php?id=${encodeURIComponent(contentId)}&t=${encodeURIComponent(title)}&tm=$ts"
            )
        }

        for (url in candidateUrls) {
            try {
                val reqBuilder = Request.Builder().url(url).header("X-Requested-With", X_REQUESTED_WITH).header("Cookie", cookie)
                mobileHeaders("https://$domain/mobile/home?app=1").forEach { (k, v) -> reqBuilder.header(k, v) }
                val res = fetch(reqBuilder.build())
                val bodyText = res.body

                val parsed = if (bodyText.trim().startsWith("[")) JSONArray(bodyText).optJSONObject(0) else JSONObject(bodyText)
                if (parsed == null) continue

                if (!res.isSuccessful) continue
                var hlsFile = ""
                val sources = parsed.optJSONArray("sources")
                if (sources != null && sources.length() > 0) {
                    for (i in 0 until sources.length()) {
                        val s = sources.optJSONObject(i) ?: continue
                        if (s.optString("label") == "Auto" || s.optString("default") == "true") {
                            hlsFile = s.optString("file")
                            break
                        }
                    }
                    if (hlsFile.isEmpty()) {
                        hlsFile = sources.optJSONObject(0)?.optString("file") ?: ""
                    }
                }
                if (hlsFile.isEmpty()) continue

                val captions = mutableListOf<Caption>()
                val tracks = parsed.optJSONArray("tracks") ?: parsed.optJSONArray("captions") ?: parsed.optJSONArray("subtitles")
                if (tracks != null) {
                    for (i in 0 until tracks.length()) {
                        val t = tracks.optJSONObject(i) ?: continue
                        val kind = t.optString("kind").lowercase()
                        val rawFile = t.optString("file").ifBlank { t.optString("src") }.ifBlank { t.optString("url") }
                        if (rawFile.isNotBlank() && (kind.contains("sub") || kind.contains("cap") || kind == "vtt" || kind == "thumbnails" || kind.isEmpty())) {
                            var fileUrl = rawFile
                            if (fileUrl.startsWith("//")) {
                                fileUrl = "https:$fileUrl"
                            } else if (fileUrl.startsWith("/")) {
                                fileUrl = "https://$domain$fileUrl"
                            } else if (!fileUrl.startsWith("http")) {
                                fileUrl = "https://$domain/$fileUrl"
                            }
                            val label = t.optString("label")
                                .ifBlank { t.optString("language") }
                                .ifBlank { t.optString("name") }
                                .ifBlank { t.optString("lang") }
                                .ifBlank { "English" }
                            val langCode = t.optString("language")
                                .ifBlank { t.optString("srclang") }
                                .ifBlank { t.optString("lang") }
                                .ifBlank { t.optString("code") }
                                .ifBlank { "en" }
                            val type = if (kind == "thumbnails") "thumbnails" else if (fileUrl.contains(".srt", ignoreCase = true)) "srt" else "vtt"
                            captions.add(Caption(fileUrl, label, type, langCode))
                        }
                    }
                }

                val source = "https://$domain/".toHttpUrlOrNull()?.resolve(hlsFile)
                    ?: throw java.io.IOException("Invalid playback source")
                val value = source.queryParameter("in")
                if (source.host == domain && value?.startsWith("unknown") == true) {
                    hlsFile = ProviderMasterRequest.resolve(source.toString(), contentId)
                }
                return@withContext Pair(hlsFile, captions)
            } catch (e: Exception) {
                rethrowControlFailure(e)
                // try next candidate URL
            }
        }
        return@withContext null
    }

    private suspend fun postRecentplay(domain: String, showId: String, cookie: String) = withContext(Dispatchers.IO) {
        val seKey = "SE$showId"
        try {
            val reqBody = "recentplay=$seKey".toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaType())
            val reqBuilder = Request.Builder()
                .url("https://$domain/mobile/recentplay.php")
                .post(reqBody)
            mobileHeaders("https://$domain/mobile/home?app=1").forEach { (k, v) -> reqBuilder.header(k, v) }
            reqBuilder
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Origin", "https://$domain")
                .header("Cookie", cookie)

            fetch(reqBuilder.build())
        } catch (e: Exception) {
            rethrowControlFailure(e)
            // non-fatal
        }
    }

    private fun buildCookieString(session: WarmSession, showId: String, contentId: String, ott: String): String {
        val parts = mutableListOf(
            "addhash=${session.addhashEncoded}",
            "t_hash_t=${session.tHashTEncoded}",
            "SE$showId=$contentId",
            "lang=eng"
        )
        if (ott != "nf") {
            parts.add("ott=$ott")
        }
        return parts.joinToString("; ")
    }

    suspend fun resolveStream(tmdbId: String, type: String, season: Int = 0, episode: Int = 0, label: String = "Net52", cardTitle: String = "", cardYear: String = ""): NetMirrorStream = kotlinx.coroutines.withTimeout(34_000L) {
        withContext(Dispatchers.IO) {
          streamRequests.withKey("${type}_${tmdbId}_${season}_${episode}") {
            PlaybackServiceGate.check()
            if (cachedSourceRevision != PlaybackServiceGate.sourceRevision) {
                streamCache.clear()
                cachedSourceRevision = PlaybackServiceGate.sourceRevision
            }
            val key = "${type}_${tmdbId}_${season}_${episode}"
            streamCache[key]?.takeIf { it.expiresAt - System.currentTimeMillis() > StreamSessionPolicy.EXPIRY_MARGIN_MS }?.let { return@withKey it }
            val info = getTmdbInfo(tmdbId, type, cardTitle, cardYear)
            val source = publicPlayback.resolve(info.title, info.year, type, season, episode, tmdbId)
            val stream = NetMirrorStream(source.url, source.headers, source.captions, "$label [${source.ott.uppercase()}]", source.expiresAt, info.title)
            if (streamCache.size >= 32) streamCache.keys.firstOrNull()?.let(streamCache::remove)
            streamCache[key] = stream
            stream
          }
        }
    }

    // Legacy session implementation retained for migration diagnostics; playback uses publicPlayback.
    private suspend fun resolveStreamOnce(tmdbId: String, type: String, season: Int, episode: Int, label: String) = withContext(Dispatchers.IO) {
        PlaybackServiceGate.check()
        currentCoroutineContext().ensureActive()
        if (cachedSourceRevision != PlaybackServiceGate.sourceRevision) {
            streamCache.clear()
            cachedSourceRevision = PlaybackServiceGate.sourceRevision
        }
        val cacheKey = "${type}_${tmdbId}_${season}_${episode}"
        val cached = streamCache[cacheKey]
        if (cached != null && cached.expiresAt - System.currentTimeMillis() > StreamSessionPolicy.EXPIRY_MARGIN_MS) {
            Log.d("NetMirror", "💾 Returning cached stream for $cacheKey")
            return@withContext cached
        }

        val tmdbInfo = getTmdbInfo(tmdbId, type)
        if (tmdbInfo.title.isEmpty()) throw Exception("$label: Could not get title from TMDB for $tmdbId")

        val session = getSession() ?: throw Exception("$label: Session warmup failed")
        val domain = session.domain
        val sessionCookie = "addhash=${session.addhashEncoded}; t_hash_t=${session.tHashTEncoded}; lang=eng"

        val cacheMatchKey = "${type}_$tmdbId"
        val cachedMatch = searchResultCache[cacheMatchKey]
        val match = if (cachedMatch != null) {
            cachedMatch
        } else {
            var bestMatch: SearchResult? = null
            for (ott in OTT_SEARCH_ORDER) {
                val searchCookie = "addhash=${session.addhashEncoded}; t_hash_t=${session.tHashTEncoded}; ott=$ott; lang=eng"
                val matchResult = searchContent(domain, tmdbInfo.title, tmdbInfo.year, searchCookie, ott)
                if (matchResult != null) {
                    val currentIsExactYear = tmdbInfo.year.isNotEmpty() && matchResult.year == tmdbInfo.year
                    val bestIsExactYear = bestMatch != null && tmdbInfo.year.isNotEmpty() && bestMatch.year == tmdbInfo.year

                    if (bestMatch == null || (currentIsExactYear && !bestIsExactYear) || (matchResult.score > bestMatch.score && (!bestIsExactYear || currentIsExactYear))) {
                        bestMatch = matchResult
                    }
                    if (bestMatch.score >= 90) {
                        break
                    }
                }
            }
            val finalMatch = bestMatch ?: throw Exception("$label: No search results for \"${tmdbInfo.title}\"")
            searchResultCache[cacheMatchKey] = finalMatch
            finalMatch
        }
        val matchOtt = match.ott
        val showId = match.id
        var contentId = showId
        var requestedEpisodeFound = type != "tv" || season <= 0 || episode <= 0

        if (type == "tv" && season > 0 && episode > 0) {
            val postData = fetchPostDetail(domain, showId, sessionCookie, matchOtt)
            if (postData != null) {
                val rawSeasonList = postData.optJSONArray("season") ?: postData.optJSONArray("seasons") ?: JSONArray()
                var targetSeasonId: String? = null

                if (rawSeasonList.length() > 0) {
                    for (i in 0 until rawSeasonList.length()) {
                        val sObj = rawSeasonList.optJSONObject(i) ?: continue
                        val sVal = sObj.optString("s")
                            .ifBlank { sObj.optString("season") }
                            .ifBlank { sObj.optString("name") }
                            .ifBlank { sObj.optString("title") }
                            .ifBlank { sObj.optString("val") }
                            .lowercase().replace("season", "").replace("s", "").trim()
                        val parsedS = sVal.toIntOrNull()
                        if (parsedS == season && sObj.has("id")) {
                            targetSeasonId = sObj.optString("id")
                            break
                        }
                    }
                    if (targetSeasonId == null && season in 1..rawSeasonList.length()) {
                        targetSeasonId = rawSeasonList.optJSONObject(season - 1)?.optString("id")
                    }
                }

                if (targetSeasonId != null) {
                    Log.d("NetMirror", "[$label] 📺 Resolving TV Show: Season $season (seasonId: $targetSeasonId), Episode $episode")
                    val seasonEps = fetchSeasonEpisodes(domain, targetSeasonId, showId, sessionCookie, matchOtt)
                    if (seasonEps != null && seasonEps.length() > 0) {
                        val seasonEpId = findEpisodeId(seasonEps, season, episode)
                        if (seasonEpId != null) {
                            contentId = seasonEpId
                            requestedEpisodeFound = true
                            Log.d("NetMirror", "[$label] 🎯 Found episode contentId: $contentId for S${season}E${episode}")
                        }
                    }
                } else {
                    val epId = findEpisodeId(postData.optJSONArray("episodes") ?: JSONArray(), season, episode)
                    if (epId != null) {
                        contentId = epId
                        requestedEpisodeFound = true
                    }
                }
            }
        }

        if (!requestedEpisodeFound)
            throw java.io.IOException("Requested episode S${season}E${episode} is unavailable")
        val cookie = buildCookieString(session, showId, contentId, matchOtt)
        val playlistResult = fetchPlaylist(domain, contentId, tmdbInfo.title, cookie, matchOtt)
            ?: throw Exception("$label: playlist.php returned no HLS URL")

        var hlsUrl = playlistResult.first
        hlsUrl = java.net.URI("https://$domain/").resolve(hlsUrl).toString()
        if (!hlsUrl.startsWith("https://")) throw java.io.IOException("Invalid playback source")

        val headers = mutableMapOf(
            "User-Agent" to MOBILE_UA,
            "Origin" to "https://$domain",
            "Referer" to "https://$domain/",
            "sec-ch-ua" to SEC_CH_UA,
            "sec-ch-ua-mobile" to "?1",
            "sec-ch-ua-platform" to "\"Android\"",
            "X-Requested-With" to X_REQUESTED_WITH
        )

        if (java.net.URI(hlsUrl).host == domain) headers["Cookie"] = cookie

        // HLS Manifest Validation
        val validationHeaders = mobileHeaders("https://$domain/mobile/home?app=1").toMutableMap().apply {
            put("X-Requested-With", X_REQUESTED_WITH)
            put("Origin", "https://$domain")
            put("Referer", "https://$domain/")
            put("Cookie", cookie)
        }

        var mediaExpiry = minOf(System.currentTimeMillis() + 3600000, session.fetchedAt + SESSION_TTL_MS)
        suspend fun validateManifest(url: String, depth: Int = 0) {
            val req = Request.Builder().url(url)
            validationHeaders.filterKeys { it != "Cookie" || java.net.URI(url).host == domain }
                .forEach { (k, v) -> req.header(k, v) }
            val response = fetch(req.build())
            if (!response.isSuccessful || !response.body.trimStart().startsWith("#EXTM3U"))
                throw java.io.IOException("Playback manifest unavailable (HTTP ${response.code})")
            CdnRoutePolicy.earliestManifestExpiry(response.body, System.currentTimeMillis())?.let {
                mediaExpiry = minOf(mediaExpiry, it)
            }
            CdnRoutePolicy.token(url)?.let {
                mediaExpiry = minOf(mediaExpiry, StreamSessionPolicy.tokenIssuedAt(it.timestamp, System.currentTimeMillis()) + SESSION_TTL_MS)
            }
            if (mediaExpiry - System.currentTimeMillis() <= StreamSessionPolicy.EXPIRY_MARGIN_MS)
                throw java.io.IOException("Provider returned an expired playback link")
            if (response.body.contains("#EXT-X-STREAM-INF")) {
                if (depth >= 2) throw java.io.IOException("Playback playlist nesting is invalid")
                val lines = response.body.lines()
                val start = lines.indexOfFirst { it.startsWith("#EXT-X-STREAM-INF") }
                val child = lines.drop(start + 1).firstOrNull { it.isNotBlank() && !it.startsWith('#') }
                    ?: throw java.io.IOException("Missing playback video variant")
                validateManifest(java.net.URI(url).resolve(child.trim()).toString(), depth + 1)
            }
        }
        validateManifest(hlsUrl)
        currentCoroutineContext().ensureActive()

        val result = NetMirrorStream(
            url = hlsUrl,
            headers = headers,
            captions = playlistResult.second,
            sourceId = "$label [${matchOtt.uppercase()}]",
            expiresAt = mediaExpiry,
            title = tmdbInfo.title,
            captionHeaders = headers + ("Cookie" to cookie)
        )
        if (streamCache.size >= 32) streamCache.keys.firstOrNull()?.let(streamCache::remove)
        streamCache[cacheKey] = result
        return@withContext result
    }

    suspend fun resolveNet52(tmdbId: String, type: String, season: Int = 0, episode: Int = 0, cardTitle: String = "", cardYear: String = ""): NetMirrorStream = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        Log.d("NetMirror", "[Stream] ▶️ resolve: TMDB $tmdbId ($type) S${season}E$episode")
        try {
            val result = resolveStream(tmdbId, type, season, episode, "Premium", cardTitle, cardYear)
            Log.d("NetMirror", "[Stream] ✅ Total: ${System.currentTimeMillis() - t0}ms | source: ${result.sourceId}")
            return@withContext result
        } catch (e: Exception) {
            rethrowControlFailure(e)
            Log.e("NetMirror", "[Stream] ❌ FAILED after ${System.currentTimeMillis() - t0}ms: ${e.message}")
            throw e
        }
    }

    private fun extractCdnHost(manifest: String): String? {
        val audioMatch = Regex("""URI="https://([^"/]+)/files/""").find(manifest)
        if (audioMatch != null) return audioMatch.groupValues[1]

        val videoMatch = Regex("""^https://([^/]+)/files/\d+/""", RegexOption.MULTILINE).find(manifest)
        if (videoMatch != null) return videoMatch.groupValues[1]

        return null
    }

    suspend fun warmNetMirrorSession() = withContext(Dispatchers.IO) {
        getSession()
    }
}
