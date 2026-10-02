package com.example.data

import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Reuse connections while letting OkHttp scope cookies across HLS redirects and CDN hosts. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object ScopedPlaybackHttp {
    private val jar = AppCookieJar()
    private val client = OkHttpClient.Builder().cookieJar(jar)
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
        .followSslRedirects(false).build()
    private val publicClient = client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()

    fun factory(headers: Map<String, String>, sourceUrl: String): OkHttpDataSource.Factory {
        val origin = (headers["Origin"] ?: sourceUrl).toHttpUrlOrNull()
        if (origin != null) {
            val cookies = headers["Cookie"].orEmpty().split(';').mapNotNull {
                val pair = it.trim()
                if (pair.isEmpty()) null else Cookie.parse(origin, "$pair; Path=/; Secure")
            }
            jar.saveFromResponse(origin, cookies)
        }
        val properties = headers.filterKeys { !it.equals("Cookie", true) }.toMutableMap()
        properties.putIfAbsent("User-Agent", "Mozilla/5.0 (Linux; Android 16; Mobile)")
        val authenticated = headers.entries.any { it.key.equals("Cookie", true) && it.value.isNotBlank() }
        return OkHttpDataSource.Factory(if (authenticated) client else publicClient).setDefaultRequestProperties(properties)
    }
}
