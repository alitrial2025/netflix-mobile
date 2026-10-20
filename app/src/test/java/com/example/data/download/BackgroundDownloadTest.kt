package com.example.data.download

import android.app.Application
import android.net.NetworkRequest
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.NetMirrorResolver
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.repository.NetflixRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints

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
            assertHotspotUsesStableNetworkTracking(queued)
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
            } finally { runBlocking { restored.cancelAndJoinTransfers() }; restored.close() }
        } finally { runBlocking { manager.cancelAndJoinTransfers() }; manager.close(); database.close(); wm.cancelAllWork().result.get(); store.remove(item.id) }
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
        } finally { runBlocking { manager.cancelAndJoinTransfers() }; manager.close(); database.close(); server.shutdown(); directory.deleteRecursively() }
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

    @Test fun wifiPreferenceUpdatesDurableConstraintsAndLeavesPausedWorkPaused() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build())
        val database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val manager = NetflixDownloadManager(app, NetflixRepository(database.netflixDao()), NetMirrorResolver(app), accountIdProvider = { "account" })
        val item = media().copy(id = "wifi_policy_movie")
        val store = DownloadRequestStore(File(app.noBackupFilesDir, "download-requests"))
        val work = WorkManager.getInstance(app)
        val name = downloadWorkName(item.id)
        try {
            manager.startOrResumeDownload("profile", item, isWifiOnly = true)
            manager.updateWifiOnlyPolicy(false)
            val active = work.getWorkInfosForUniqueWork(name).get().last { it.state == WorkInfo.State.ENQUEUED }
            assertEquals(NetworkType.CONNECTED, active.constraints.requiredNetworkType)
            assertFalse(store.all().single { it.task.downloadKey == item.id }.wifiOnly)
            manager.pauseDownload(item.id)
            manager.updateWifiOnlyPolicy(true)
            val paused = store.all().single { it.task.downloadKey == item.id }
            assertTrue(paused.wifiOnly)
            assertEquals(DownloadTaskStatus.PAUSED, paused.task.status)
            assertTrue(work.getWorkInfosForUniqueWork(name).get().all { it.state == WorkInfo.State.CANCELLED })
            assertTrue(manager.resumeSavedDownload(item.id, "profile"))
            assertHotspotUsesStableNetworkTracking(work.getWorkInfosForUniqueWork(name).get().last { it.state == WorkInfo.State.ENQUEUED })
        } finally { runBlocking { manager.cancelAndJoinTransfers() }; manager.close(); database.close(); work.cancelAllWork().result.get(); store.remove(item.id) }
    }

    private fun assertHotspotUsesStableNetworkTracking(info: WorkInfo) {
        // The scheduler must accept metered hotspots without its broken custom-request
        // foreground subscription. The transfer guard tests enforce the Wi-Fi preference.
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertNull(info.constraints.requiredNetworkRequest)
    }

    @Test fun appUpdateReplacesBrokenForegroundNetworkTrackingWithoutLosingProgressOrStartingPausedTitles() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build())
        val database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val store = DownloadRequestStore(File(app.noBackupFilesDir, "download-requests"))
        val item = media().copy(id = "hotspot_migration")
        val pausedItem = media().copy(id = "hotspot_paused")
        val saved = SavedDownloadRequest("old-token", "account", "profile", item, null, true, true,
            DownloadTaskInfo(item.id, item.id, item.title, progress = .4f, downloadedBytes = 400,
                status = DownloadTaskStatus.PREPARING, profileId = "profile"), schedulerVersion = 2)
        val paused = saved.copy(token = "paused-token", media = pausedItem,
            task = saved.task.copy(downloadKey = pausedItem.id, mediaId = pausedItem.id, status = DownloadTaskStatus.PAUSED))
        val work = WorkManager.getInstance(app)
        val old = OneTimeWorkRequestBuilder<VideoDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkRequest(NetworkRequest.Builder()
                .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI).build(), NetworkType.CONNECTED).build())
            .addTag(saved.token).build()
        store.put(saved)
        store.put(paused)
        work.enqueueUniqueWork(downloadWorkName(item.id), ExistingWorkPolicy.REPLACE, old).result.get()
        val manager = NetflixDownloadManager(app, NetflixRepository(database.netflixDao()), NetMirrorResolver(app), accountIdProvider = { "account" })
        try {
            val migrated = withTimeout(5_000) {
                var found: WorkInfo? = null
                while (found == null) {
                    found = work.getWorkInfosForUniqueWork(downloadWorkName(item.id)).get()
                        .firstOrNull { it.id != old.id && it.state == WorkInfo.State.ENQUEUED }
                    if (found == null) delay(10)
                }
                found
            }
            assertHotspotUsesStableNetworkTracking(migrated)
            // REPLACE may prune the cancelled predecessor from WorkManager's database.
            assertTrue(work.getWorkInfoById(old.id).get()?.state.let { it == null || it == WorkInfo.State.CANCELLED })
            assertEquals(.4f, manager.downloadTasks.value[item.id]!!.progress, 0f)
            assertEquals(400L, manager.downloadTasks.value[item.id]!!.downloadedBytes)
            assertEquals(4, store.all().first { it.task.downloadKey == item.id }.schedulerVersion)
            assertEquals(DownloadTaskStatus.PAUSED, manager.downloadTasks.value[pausedItem.id]!!.status)
            assertTrue(work.getWorkInfosForUniqueWork(downloadWorkName(pausedItem.id)).get().isEmpty())
            // A scheduler cancellation must become retryable instead of remaining permanently queued.
            work.cancelUniqueWork(downloadWorkName(item.id)).result.get()
            withTimeout(5_000) {
                while (manager.downloadTasks.value[item.id]?.status != DownloadTaskStatus.ERROR) delay(10)
            }
            assertTrue(manager.downloadTasks.value[item.id]!!.errorMessage!!.contains("Retry"))
        } finally { manager.cancelAndJoinTransfers(); manager.close(); database.close(); work.cancelAllWork().result.get(); store.remove(item.id); store.remove(pausedItem.id) }
    }
}
