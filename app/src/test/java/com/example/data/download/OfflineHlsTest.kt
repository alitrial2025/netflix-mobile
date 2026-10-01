package com.example.data.download

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.NetMirrorResolver
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.repository.NetflixRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OfflineHlsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private fun media(id: String = "movie") = MediaItem(id, "A movie", MediaType.MOVIE,
        "", "", 90, "16+", 2026, "2h", genres = emptyList(), cast = emptyList(), director = "")

    @Test fun separateAudioResumesWithoutRedownloadingVideoAndWritesOnlyLocalReferences() = runBlocking {
        val counts = ConcurrentHashMap<String, Int>()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                val token = url.queryParameter("token") ?: "old"
                val path = url.encodedPath
                counts.merge("$path:$token", 1, Int::plus)
                if (path == "/master.m3u8") return MockResponse().setBody("""
                    #EXTM3U
                    #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="English",LANGUAGE="en",DEFAULT=YES,URI="audio/index.m3u8?token=$token"
                    #EXT-X-STREAM-INF:BANDWIDTH=300000,CODECS="avc1.42c00b,mp4a.40.2",AUDIO="audio"
                    video/index.m3u8?token=$token
                """.trimIndent())
                if (path.endsWith("index.m3u8")) {
                    val manifest = if (path.contains("video")) javaClass.getResource("/offline-hls/video/index.m3u8")!!.readText()
                        else "#EXTM3U\n#EXT-X-TARGETDURATION:2\n" + (0..3).joinToString("\n") { "#EXTINF:${if (it == 3) ".022" else "1.003"},\n$it.aac" } + "\n#EXT-X-ENDLIST\n"
                    return MockResponse().setBody(manifest.lines().joinToString("\n") { line ->
                        if (line.startsWith("#EXT-X-MAP:")) line.replace("init.mp4", "init.mp4?token=$token")
                        else if (line.isNotBlank() && !line.startsWith('#')) "$line?token=$token" else line
                    })
                }
                if (path == "/audio/1.aac" && token == "old") return MockResponse().setResponseCode(403)
                val bytes = javaClass.getResourceAsStream("/offline-hls$path")?.use { it.readBytes() }
                    ?: return MockResponse().setResponseCode(404)
                return MockResponse().setBody(Buffer().write(bytes))
            }
        }
        server.start()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val manager = NetflixDownloadManager(app, NetflixRepository(db.netflixDao()), NetMirrorResolver(app),
            OkHttpClient(), { "account" })
        val output = File("build/reports/offline-hls-playback").apply { deleteRecursively(); mkdirs() }
        val part = File(output, "movie.part")
        try {
            try {
                manager.downloadHlsStream(server.url("/master.m3u8?token=old").toString(), emptyMap(), part, "movie", media(), null, true)
                fail("Expired audio authorization must not produce a completed silent download")
            } catch (expired: DownloadHttpException) { assertEquals(403, expired.code) }
            assertFalse(part.exists())
            assertTrue(manager.downloadHlsStream(server.url("/master.m3u8?token=new").toString(), emptyMap(), part, "movie", media(), null, true))
            val entry = File(output, "movie.m3u8")
            assertTrue(part.renameTo(entry))
            assertTrue(entry.readText().contains("AUDIO=\"offline\""))
            assertTrue(entry.readText().contains("CODECS=\"avc1.42c00b,mp4a.40.2\""))
            output.walkTopDown().filter { it.extension == "m3u8" }.forEach { manifest ->
                assertFalse(manifest.readText().contains("http"))
                assertFalse(manifest.readText().contains("token="))
            }
            assertEquals(1, counts["/video/init.mp4:old"])
            assertNull(counts["/video/init.mp4:new"])
            assertNull(counts["/video/0.m4s:new"])
            assertNull(counts["/audio/0.aac:new"])
            assertEquals(1, counts["/audio/1.aac:new"])
            assertEquals(8, output.walkTopDown().count { it.extension in setOf("mp4", "m4s", "aac") }) // init + 3 video + 4 audio
        } finally { manager.close(); db.close(); server.shutdown() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun smartDownloadsQueueOnlyConfirmedProviderTitlesAndCacheUnavailableCandidates() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        val wm = WorkManager.getInstance(app)
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val probed = mutableListOf<String>()
        val manager = NetflixDownloadManager(app, NetflixRepository(db.netflixDao()), NetMirrorResolver(app),
            accountIdProvider = { "account" }, candidateProbe = { item, _ -> probed += item.id; item.id == "available" })
        try {
            val pool = listOf(media("missing"), media("available"), media("another-missing"))
            manager.curateSmartDownloads("profile", 3f, false, true, pool)
            assertEquals(setOf("available"), manager.downloadTasks.value.keys)
            manager.curateSmartDownloads("profile", 3f, false, true, pool)
            assertEquals(1, probed.count { it == "missing" })
            assertEquals(1, probed.count { it == "another-missing" })
        } finally { manager.cancelAndJoinTransfers(); manager.close(); db.close(); wm.cancelAllWork().result.get(); Dispatchers.resetMain() }
    }

    @Test fun onlyTheSelectedAudioGroupIsUsedAndEmbeddedAudioDoesNotRequireAnotherTrack() {
        val master = "#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"other\",URI=\"wrong.m3u8\"\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=200000\nvideo.m3u8\n"
        assertNull(OfflineHlsPlan.variant(master, "https://cdn.example/master.m3u8", true).audioUrl)
    }

    @Test fun internalProviderErrorsNeverReachDownloadMessages() {
        assertEquals("This title cannot be downloaded. Try again later.",
            DownloadRetryPolicy.userMessage(Exception("Premium: No search results for test")))
        assertEquals("This title cannot be downloaded. Try again later.",
            DownloadRetryPolicy.userMessage(IllegalArgumentException("Separate audio unsupported")))
        assertTrue(DownloadRetryPolicy.userMessage(DownloadStorageException()).contains("storage"))
    }
}
