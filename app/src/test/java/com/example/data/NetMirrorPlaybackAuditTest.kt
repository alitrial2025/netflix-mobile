package com.example.data

import android.content.Context
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class NetMirrorPlaybackAuditTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("netmirror_prefs", Context.MODE_PRIVATE)
    private fun session(cookie: String) = JSONObject().put("domain", "provider.invalid")
        .put("addhashRaw", "fixture").put("addhashEncoded", "fixture")
        .put("tHashTRaw", cookie).put("tHashTEncoded", cookie).put("fetchedAt", System.currentTimeMillis()).toString()

    @Test fun unsignedMasterPlaceholderDoesNotRevokeCookie() {
        assertFalse(StreamSessionPolicy.isSessionRejected(200, """{"sources":[{"file":"/mobile/hls/id.m3u8?in=unknown::future-mode"}]}"""))
        assertTrue(StreamSessionPolicy.isSessionRejected(200, "#EXTM3U\nvideo.m3u8?in=unknown"))
        val result = ProviderMasterRequest.resolve("https://provider.invalid/mobile/pv/hls/id.m3u8?in=unknown::future-mode&lang=eng", "id", "https://provider.invalid")
        assertFalse(result.contains("unknown")); assertTrue(result.contains("lang=eng"))
    }

    @Test fun cdnManifestDoesNotWaitBehindProviderQueue() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { c -> response(c.request(), "#EXTM3U\n#EXTINF:1,\ns.jpg") }.build()
        val resolver = NetMirrorResolver(context, client)
        val held = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val queued = launch { PlaybackServiceGate.request { held.complete(Unit); release.await() } }
        held.await()
        try {
            withTimeout(2_000) { assertEquals(200, resolver.fetch(Request.Builder().url("https://dynamic-cdn.invalid/new-route/media.m3u8").build()).code) }
        } finally { release.complete(Unit); queued.join() }
    }

    @Test fun movieAndProviderCaptionsResolveWithoutRestoringSavedCookies() = runBlocking {
        val tmdbId = "public-caption-fixture"
        NetMirrorResolver(context).evictCachedStream(tmdbId, "movie")
        prefs.edit().clear().putString("netmirror_session", session("old-cookie")).commit()
        val seen = CopyOnWriteArrayList<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request(); seen += req
            assertNull(req.header("Cookie"))
            when (req.url.encodedPath) {
                "/3/movie/$tmdbId" -> response(req, """{"title":"Caption Fixture","release_date":"2001-01-01"}""")
                "/search.php" -> response(req, """{"searchResult":[{"id":"8100000001","t":"Caption Fixture","y":"2001"}]}""")
                "/mobile/post.php" -> response(req, "{}", 404)
                "/title/8100000001" -> response(req, """<script type="application/ld+json">{"@type":"Movie","name":"Caption Fixture","datePublished":"2001-01-01"}</script>""")
                "/mobile/playlist.php" -> response(req, """{"sources":[{"file":"https://cdn.invalid/caption-video.m3u8?in=issued-signature"}],"tracks":[{"file":"/captions/english.vtt","kind":"subtitles","label":"English","srclang":"en"}]}""")
                "/caption-video.m3u8" -> response(req, "#EXTM3U\n#EXTINF:1,\ns.jpg")
                else -> throw AssertionError("Unexpected handshake ${req.url.encodedPath}")
            }
        }.build()
        val resolver = NetMirrorResolver(context, client)
        val result = resolver.resolveStream(tmdbId, "movie")
        assertEquals("https://cdn.invalid/caption-video.m3u8?in=issued-signature", result.url)
        assertNull(result.headers["Cookie"]); assertNull(result.captionHeaders["Cookie"])
        assertEquals("https://net52.cc/captions/english.vtt", result.captions.single().url)
        val count = seen.size
        assertEquals(result, resolver.resolveStream(tmdbId, "movie"))
        assertEquals(count, seen.size)
    }

    private fun response(req: Request, body: String, code: Int = 200) = Response.Builder().request(req)
        .protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(body.toResponseBody()).build()
}
