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
        val result = ProviderMasterRequest.resolve("https://provider.invalid/mobile/pv/hls/id.m3u8?in=unknown::future-mode&lang=eng", "id")
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

    @Test fun providerRejectionRetriesOnceWithReplacementSessionAndCdnRejectionKeepsIt() = runBlocking {
        NetMirrorResolver(context).invalidateDownloadSession("renewal-fixture", "movie", 0, 0, true)
        prefs.edit().clear().putString("netmirror_session", session("old-cookie")).commit()
        val seen = CopyOnWriteArrayList<Request>()
        var rejected = false
        val client = OkHttpClient.Builder().addInterceptor { c ->
            val req = c.request(); seen += req
            when (req.url.encodedPath) {
                "/3/movie/renewal-fixture" -> response(req, """{"title":"Renewal Fixture","release_date":"2001-01-01"}""")
                "/mobile/search.php" -> {
                    if (!rejected) {
                        rejected = true
                        // A replacement is ready on disk while the old request finishes.
                        prefs.edit().putString("netmirror_session", session("new-cookie")).commit()
                        response(req, "", 403)
                    } else {
                        assertTrue(req.header("Cookie").orEmpty().contains("new-cookie"))
                        response(req, """{"searchResult":[{"id":"fixture-movie","t":"Renewal Fixture","y":"2001"}]}""")
                    }
                }
                "/mobile/playlist.php" -> response(req, """{"sources":[{"file":"https://cdn.invalid/exact/video.m3u8?in=issued-signature"}],"tracks":[]}""")
                "/exact/video.m3u8" -> { assertNull(req.header("Cookie")); response(req, "#EXTM3U\n#EXTINF:1,\ns.jpg") }
                else -> throw AssertionError("Unexpected request ${req.url.encodedPath}")
            }
        }.build()
        val resolver = NetMirrorResolver(context, client)
        // A late rejection must not delete the newer persisted cookie.
        val result = resolver.resolveStream("renewal-fixture", "movie")
        assertEquals("https://cdn.invalid/exact/video.m3u8?in=issued-signature", result.url)
        assertEquals(2, seen.count { it.url.encodedPath == "/mobile/search.php" })
        assertTrue(resolver.isSessionWarm())
        assertTrue(prefs.getString("netmirror_session", "")!!.contains("new-cookie"))
    }

    private fun response(req: Request, body: String, code: Int = 200) = Response.Builder().request(req)
        .protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(body.toResponseBody()).build()
}
