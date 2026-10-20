package com.example.data.download

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.NetMirrorResolver
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.SubscriptionPlans
import com.example.data.repository.NetflixRepository
import java.io.File
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo
import androidx.concurrent.futures.ResolvableFuture

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DownloadResumeTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val plan = SubscriptionPlans.getById("plan_premium")
    private fun media() = MediaItem("resume_movie", "Resume movie", MediaType.MOVIE, "", "", 90, "16+", 2026, "2h",
        genres = emptyList(), cast = emptyList(), director = "")

    private fun network(wifi: Boolean) {
        val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadow = shadowOf(connectivity)
        shadow.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
            if (wifi) ConnectivityManager.TYPE_WIFI else ConnectivityManager.TYPE_MOBILE, 0, true, true))
        val caps = ShadowNetworkCapabilities.newInstance().also {
            shadowOf(it).addTransportType(if (wifi) NetworkCapabilities.TRANSPORT_WIFI else NetworkCapabilities.TRANSPORT_CELLULAR)
            shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            shadowOf(it).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        val active = connectivity.activeNetwork!!
        shadow.setNetworkCapabilities(active, caps)
        shadow.networkCallbacks.toList().forEach { it.onCapabilitiesChanged(active, caps) }
    }

    private fun fixture(test: suspend (NetflixDownloadManager, DownloadRequestStore, WorkManager) -> Unit) = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build())
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val store = DownloadRequestStore(File(app.noBackupFilesDir, "download-requests"))
        val manager = NetflixDownloadManager(app, NetflixRepository(db.netflixDao()), NetMirrorResolver(app), accountIdProvider = { "account" })
        val wm = WorkManager.getInstance(app)
        try { test(manager, store, wm) }
        finally { manager.cancelAndJoinTransfers(); manager.close(); wm.cancelAllWork().result.get(); db.close() }
    }

    @Test fun cancelledPreparationReturnsRetryThenTheSameRequestSavesRealHlsBytes() = fixture { manager, store, _ ->
        manager.startOrResumeDownload("profile", media(), isWifiOnly = false)
        val saved = store.all().single()
        assertEquals(DownloadRunResult.RETRY, manager.runSavedDownload(saved.task.downloadKey, saved.token, 0,
            confirmPlan = {
                assertEquals(DownloadTaskStatus.PREPARING, manager.downloadTasks.value[media().id]!!.status)
                throw CancellationException("Remote membership operation cancelled")
            }, transfer = { _, _ -> fail("Unverified access must not transfer bytes") }))
        val waiting = manager.downloadTasks.value[media().id]!!
        assertEquals(DownloadTaskStatus.QUEUED, waiting.status)
        assertTrue(waiting.errorMessage!!.contains("Retrying automatically"))
        val server = MockWebServer()
        val packet = ByteArray(188 * 3) { 1 }.apply { for (i in indices step 188) this[i] = 0x47 }
        server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:6,\nsegment.jpg\n#EXT-X-ENDLIST\n"))
        server.enqueue(MockResponse().setBody(Buffer().write(packet)))
        server.start()
        val partial = File(app.cacheDir, "resume-regression.part")
        try {
            assertEquals(DownloadRunResult.COMPLETE, manager.runSavedDownload(saved.task.downloadKey, saved.token, 1,
                confirmPlan = { plan }, transfer = { request, confirmed ->
                    assertEquals(plan, confirmed)
                    assertTrue(manager.downloadHlsStream(server.url("/stream.m3u8").toString(), emptyMap(), partial,
                        request.task.downloadKey, request.media, null, true))
                }))
            assertArrayEquals(packet, partial.readBytes())
            assertTrue(store.all().isEmpty())
        } finally { partial.delete(); File(partial.path + ".resume.json").delete(); server.shutdown() }
    }

    @Test fun exhaustedPreparationRetriesBecomeActionableError() = fixture { manager, store, _ ->
        manager.startOrResumeDownload("profile", media(), isWifiOnly = false)
        val saved = store.all().single()
        assertEquals(DownloadRunResult.FAILED, manager.runSavedDownload(media().id, saved.token, 8,
            confirmPlan = { throw CancellationException("Remote operation cancelled") }, transfer = { _, _ -> fail() }))
        assertEquals(DownloadTaskStatus.ERROR, manager.downloadTasks.value[media().id]!!.status)
    }

    @Test fun cancelledForegroundHandoffReturnsActualWorkerRetryInsteadOfEscaping() = fixture { manager, store, _ ->
        manager.startOrResumeDownload("profile", media(), isWifiOnly = false)
        val saved = store.all().single()
        val worker = TestListenableWorkerBuilder<VideoDownloadWorker>(app)
            .setInputData(workDataOf("key" to media().id, "token" to saved.token))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(context: android.content.Context, name: String, parameters: WorkerParameters): ListenableWorker =
                    VideoDownloadWorker(context, parameters) { manager }
            })
            .setForegroundUpdater { _, _, _ -> ResolvableFuture.create<Void>().apply { cancel(false) } }
            .build()
        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
        assertEquals(DownloadTaskStatus.QUEUED, manager.downloadTasks.value[media().id]!!.status)
        assertTrue(manager.downloadTasks.value[media().id]!!.errorMessage!!.contains("Retrying automatically"))
    }

    @Test fun providerCancellingItsOwnCoroutineIsRetryableWithoutCancellingWorker() = fixture { manager, store, _ ->
        manager.startOrResumeDownload("profile", media(), isWifiOnly = false)
        val saved = store.all().single()
        assertEquals(DownloadRunResult.RETRY, manager.runSavedDownload(media().id, saved.token, 0,
            confirmPlan = { plan }, transfer = { _, _ -> currentCoroutineContext().cancel(CancellationException("Provider operation ended")) }))
        currentCoroutineContext().ensureActive()
    }

    @Test fun pauseDuringPreparationStaysPausedAndDoesNotStartTransfer() = fixture { manager, store, _ ->
        manager.startOrResumeDownload("profile", media(), isWifiOnly = false)
        val saved = store.all().single()
        val started = CompletableDeferred<Unit>()
        val attempt = CoroutineScope(currentCoroutineContext()).async {
            manager.runSavedDownload(media().id, saved.token, 0,
                confirmPlan = { started.complete(Unit); awaitCancellation() }, transfer = { _, _ -> fail() })
        }
        started.await()
        manager.pauseDownload(media().id)
        attempt.join()
        assertTrue(attempt.isCancelled)
        assertEquals(DownloadTaskStatus.PAUSED, manager.downloadTasks.value[media().id]!!.status)
        assertEquals(DownloadTaskStatus.PAUSED, store.all().single().task.status)
    }

    @Test fun waitingRetryReplacesScheduledWorkButDuplicateStartDoesNot() = fixture { manager, store, wm ->
        manager.startOrResumeDownload("profile", media(), isWifiOnly = false)
        val saved = store.all().single()
        val original = wm.getWorkInfosForUniqueWork(downloadWorkName(media().id)).get().single()
        manager.startOrResumeDownload("profile", media(), isWifiOnly = false)
        assertEquals(saved.token, store.all().single().token)
        manager.markWaiting(media().id, saved.token, "Waiting to resume download")
        assertFalse(manager.resumeSavedDownload(media().id, "other-profile"))
        assertTrue(manager.resumeSavedDownload(media().id, "profile"))
        val replacement = store.all().single()
        assertNotEquals(saved.token, replacement.token)
        manager.markWaiting(media().id, saved.token, "Stale worker must not overwrite replacement")
        assertNull(manager.downloadTasks.value[media().id]!!.errorMessage)
        val next = wm.getWorkInfosForUniqueWork(downloadWorkName(media().id)).get().single { !it.state.isFinished }
        assertNotEquals(original.id, next.id)
        assertEquals(WorkInfo.State.ENQUEUED, next.state)
    }

    @Test fun restorationRepairsMissingSchedulerWorkAndPreservesCheckpoint() = fixture { manager, store, wm ->
        val saved = SavedDownloadRequest("orphan-token", "account", "profile", media(), null, false, true,
            DownloadTaskInfo(media().id, media().id, media().title, downloadedBytes = 400, progress = .4f,
                status = DownloadTaskStatus.QUEUED, errorMessage = "Waiting to resume download", profileId = "profile"), schedulerVersion = 2)
        store.put(saved)
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val restored = NetflixDownloadManager(app, NetflixRepository(db.netflixDao()), NetMirrorResolver(app), accountIdProvider = { "account" })
        try {
            withTimeout(5_000) {
                while (wm.getWorkInfosForUniqueWork(downloadWorkName(media().id)).get().none { !it.state.isFinished }) delay(10)
            }
            assertNotEquals(saved.token, store.all().single().token)
            assertEquals(400L, restored.downloadTasks.value[media().id]!!.downloadedBytes)
            assertEquals(.4f, restored.downloadTasks.value[media().id]!!.progress, 0f)
        } finally { restored.cancelAndJoinTransfers(); restored.close(); db.close() }
    }

    @Test fun wifiOnlyStillBlocksCellularBeforeMembershipOrSourceRequests() = fixture { manager, store, _ ->
        network(wifi = false)
        manager.startOrResumeDownload("profile", media(), isWifiOnly = true)
        val saved = store.all().single()
        assertEquals(DownloadRunResult.RETRY, manager.runSavedDownload(media().id, saved.token, 0,
            confirmPlan = { error("Must wait for Wi-Fi before membership requests") },
            transfer = { _, _ -> fail("Wi-Fi-only must never transfer on cellular") }))
        assertEquals("Waiting for Wi-Fi", manager.downloadTasks.value[media().id]!!.errorMessage)
    }

    @Test fun leavingHotspotForCellularStopsTheActiveTransferAndWaitsForWifi() = fixture { manager, store, _ ->
        network(wifi = true)
        manager.startOrResumeDownload("profile", media(), isWifiOnly = true)
        val saved = store.all().single()
        val started = CompletableDeferred<Unit>()
        var stopped = false
        val download = CoroutineScope(currentCoroutineContext()).async {
            manager.runSavedDownload(media().id, saved.token, 0, confirmPlan = { plan }, transfer = { _, _ ->
                try { started.complete(Unit); awaitCancellation() }
                finally { stopped = true }
            })
        }
        started.await()
        network(wifi = false)
        assertEquals(DownloadRunResult.RETRY, withTimeout(5_000) { download.await() })
        assertTrue(stopped)
        assertTrue(manager.downloadTasks.value[media().id]!!.errorMessage!!.contains("Waiting for Wi-Fi"))
    }
}
