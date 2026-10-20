package com.example.data.download

import android.app.Application
import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.repository.NetflixRepository
import java.io.File
import java.time.Duration
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StreamingDownloadParityTest {
    private fun media() = MediaItem("4604", "Smallville", MediaType.TV_SHOW,
        "", "", 90, "16+", 2001, "45m", genres = emptyList(), cast = emptyList(), director = "")
    private fun packet() = ByteArray(188 * 3) { 1 }.apply { for (i in indices step 188) this[i] = 0x47 }

    @Test fun downloadsUsePlayerHeadersAndVerifiedManifestHandoffAndSaveRealJpgSegments() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val manager = NetflixDownloadManager(app, NetflixRepository(db.netflixDao()), NetMirrorResolver(app), accountIdProvider = { "account" })
        val server = MockWebServer()
        val directory = File(app.cacheDir, "stream-download-parity").apply { deleteRecursively(); mkdirs() }
        val manifest = "#EXTM3U\n#EXTINF:6,\nsegment.jpg\n#EXT-X-ENDLIST\n"
        val headers = mapOf("user-agent" to "Provider playback agent", "Origin" to "https://provider.example",
            "Referer" to "https://provider.example/", "Cookie" to "must-not-send", "Authorization" to "must-not-send")
        server.enqueue(MockResponse().addHeader("Set-Cookie", "t_hash_t=must-not-replay; Path=/").setBody(manifest))
        server.enqueue(MockResponse().setBody(Buffer().write(packet())))
        server.start()
        try {
            val url = server.url("/video.m3u8").toString()
            val player = ScopedPlaybackHttp.factory(headers, url).createDataSource()
            try { player.open(DataSpec(Uri.parse(url))); player.read(ByteArray(16), 0, 16) }
            finally { player.close() }
            VerifiedManifestHandoff.offer(url, headers, manifest)
            val file = File(directory, "video.part")
            assertTrue(manager.downloadHlsStream(url, headers, file, "parity", media(), "S6:E22", true))
            assertArrayEquals(packet(), file.readBytes())
            assertEquals("The downloader must reuse the verified playlist", 2, server.requestCount)
            val streaming = server.takeRequest()
            val downloading = server.takeRequest()
            assertEquals("/segment.jpg", downloading.path)
            for (name in listOf("User-Agent", "Origin", "Referer")) {
                assertEquals(streaming.getHeader(name), downloading.getHeader(name))
            }
            assertEquals("Provider playback agent", downloading.getHeader("User-Agent"))
            for (request in listOf(streaming, downloading)) {
                assertNull(request.getHeader("Cookie"))
                assertNull(request.getHeader("Authorization"))
            }
            assertNull(VerifiedManifestHandoff.take(url, headers))
        } finally { VerifiedManifestHandoff.clear(); manager.close(); db.close(); server.shutdown(); directory.deleteRecursively() }
    }

    @Test fun segmentRateLimitStopsDownloadRetriesAndThePlayerWithoutPoisoningIssuedProvider() = runBlocking {
        for (splitAudio in listOf(false, true)) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
            val manager = NetflixDownloadManager(app, NetflixRepository(db.netflixDao()), NetMirrorResolver(app), accountIdProvider = { "account" })
            val server = MockWebServer()
            val directory = File(app.cacheDir, "stream-download-rate").apply { deleteRecursively(); mkdirs() }
            val sourceId = "Premium [NF] [NewTV]"
            if (splitAudio) {
                server.enqueue(MockResponse().setBody("#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"English\",DEFAULT=YES,URI=\"audio.m3u8\"\n#EXT-X-STREAM-INF:BANDWIDTH=1000000,AUDIO=\"audio\"\nvideo.m3u8\n"))
            }
            server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:6,\nsegment.ts\n#EXT-X-ENDLIST\n"))
            if (splitAudio) server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:6,\naudio.aac\n#EXT-X-ENDLIST\n"))
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
            server.start()
            val issuedRemaining = PlaybackServiceGate.remainingMs()
            try {
                val url = server.url("/video.m3u8").toString()
                repeat(2) {
                    val error = runCatching { manager.downloadHlsStream(url, emptyMap(), File(directory, "video.part"),
                        "rate", media(), "S6:E22", true, sourceId = sourceId) }.exceptionOrNull()
                    assertTrue(error.toString(), error is PlaybackRateLimitedException)
                }
                val player = GuardedPlaybackDataSourceFactory(ScopedPlaybackHttp.factory(emptyMap(), url), sourceId = sourceId).createDataSource()
                try { assertThrows(PlaybackRateLimitedException::class.java) { player.open(DataSpec(Uri.parse(url))) } }
                finally { player.close() }
                assertEquals("Neither consumer may repeat a blocked request", if (splitAudio) 4 else 2, server.requestCount)
                assertEquals(issuedRemaining, PlaybackServiceGate.remainingMs())
            } finally {
                ShadowSystemClock.advanceBy(Duration.ofMinutes(3))
                VerifiedManifestHandoff.clear(); manager.close(); db.close(); server.shutdown(); directory.deleteRecursively()
            }
        }
    }
}
