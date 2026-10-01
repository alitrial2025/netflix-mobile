package com.example.data.download

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.NetMirrorResolver
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.repository.NetflixRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.testing.SynchronousExecutor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackgroundDownloadTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private fun media() = MediaItem("movie", "A movie", MediaType.MOVIE, "", "", 90, "16+", 2026, "2h",
        genres = emptyList(), cast = emptyList(), director = "")
    private fun packet() = ByteArray(188 * 3) { 1 }.apply { for (i in indices step 188) this[i] = 0x47 }

    @Test fun queuedDownloadsAreDurableAndPauseResumeCancelOperateBeforeWorkerStarts() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build())
        val database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val repository = NetflixRepository(database.netflixDao())
        val manager = NetflixDownloadManager(app, repository, NetMirrorResolver(app), accountIdProvider = { "account" })
        val wm = WorkManager.getInstance(app)
        val item = media().copy(id = "durable_movie")
        val name = downloadWorkName(item.id)
        val store = DownloadRequestStore(File(app.noBackupFilesDir, "download-requests"))
        try {
            manager.startOrResumeDownload("profile", item, isWifiOnly = true)
            val queued = wm.getWorkInfosForUniqueWork(name).get().single()
            assertEquals(WorkInfo.State.ENQUEUED, queued.state)
            assertEquals(NetworkType.UNMETERED, queued.constraints.requiredNetworkType)
            assertTrue(queued.constraints.requiresStorageNotLow())
            manager.pauseDownload(item.id)
            assertEquals(WorkInfo.State.CANCELLED, wm.getWorkInfoById(queued.id).get()!!.state)
            assertEquals(DownloadTaskStatus.PAUSED, store.all().first { it.task.downloadKey == item.id }.task.status)
            // Simulate Activity/ViewModel reconstruction. The job and exact metadata survive.
            val restored = NetflixDownloadManager(app, repository, NetMirrorResolver(app), accountIdProvider = { "account" })
            try {
                assertEquals(DownloadTaskStatus.PAUSED, restored.downloadTasks.value[item.id]!!.status)
                assertFalse(restored.resumeSavedDownload(item.id, "other-profile"))
                assertTrue(restored.resumeSavedDownload(item.id, "profile"))
                val resumed = wm.getWorkInfosForUniqueWork(name).get().last { it.state == WorkInfo.State.ENQUEUED }
                assertNotEquals(queued.id, resumed.id)
                restored.cancelDownload(item.id)
                assertTrue(store.all().none { it.task.downloadKey == item.id })
                assertTrue(wm.getWorkInfosForUniqueWork(name).get().all { it.state == WorkInfo.State.CANCELLED })
            } finally { restored.close() }
        } finally { manager.close(); database.close(); wm.cancelAllWork().result.get(); store.remove(item.id) }
    }

    @Test fun segment165FailureResumesWithRenewedTokensWithoutRestartOrDuplicateBytes() = runBlocking {
        val counts = ConcurrentHashMap<Int, Int>()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                if (url.encodedPath == "/stream.m3u8") {
                    val token = url.queryParameter("token")!!
                    val manifest = "#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:0\n" + (0 until 170).joinToString("\n") {
                        "#EXTINF:6,\nsegment/$it.ts?token=$token"
                    } + "\n#EXT-X-ENDLIST\n"
                    return MockResponse().setBody(manifest)
                }
                val index = url.pathSegments.last().removeSuffix(".ts").toInt()
                counts.merge(index, 1, Int::plus)
                return if (index == 164 && url.queryParameter("token") == "old") MockResponse().setResponseCode(403)
                    else MockResponse().setBody(Buffer().write(packet()))
            }
        }
        server.start()
        val database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val manager = NetflixDownloadManager(app, NetflixRepository(database.netflixDao()), NetMirrorResolver(app),
            OkHttpClient.Builder().readTimeout(3, TimeUnit.SECONDS).build(), { "test-account" })
        val directory = File(app.cacheDir, "segment165-test").apply { deleteRecursively(); mkdirs() }
        val partial = File(directory, "movie.mp4.part")
        try {
            try {
                manager.downloadHlsStream(server.url("/stream.m3u8?token=old").toString(), emptyMap(), partial, "movie", media(), null, true)
                fail("Expected expired segment authorization")
            } catch (error: DownloadHttpException) { assertEquals(403, error.code) }
            assertEquals(164 * packet().size.toLong(), partial.length())
            assertTrue(manager.downloadHlsStream(server.url("/stream.m3u8?token=new").toString(), emptyMap(), partial, "movie", media(), null, true))
            assertEquals(170 * packet().size.toLong(), partial.length())
            assertTrue((0 until 164).all { counts[it] == 1 })
            assertEquals(2, counts[164])
            assertArrayEquals(packet().let { bytes -> ByteArray(bytes.size * 170) { bytes[it % bytes.size] } }, partial.readBytes())
        } finally { manager.close(); database.close(); server.shutdown(); directory.deleteRecursively() }
    }

    @Test fun requestMetadataAndPauseStateRestoreWithoutStoringStreamCredentials() {
        val root = File(app.noBackupFilesDir, "request-test").apply { deleteRecursively() }
        val original = SavedDownloadRequest("token", "account", "profile", media(), null, true, false,
            DownloadTaskInfo("movie", "movie", "A movie", progress = .6f, status = DownloadTaskStatus.PAUSED, profileId = "profile"))
        try {
            DownloadRequestStore(root).put(original)
            assertEquals(original, DownloadRequestStore(root).all().single())
            val serialized = root.listFiles()!!.single().readText()
            assertFalse(serialized.contains("Cookie"))
            assertFalse(serialized.contains("resolvedUrl"))
            DownloadRequestStore(root).remove("movie")
            assertTrue(DownloadRequestStore(root).all().isEmpty())
        } finally { root.deleteRecursively() }
    }
}
