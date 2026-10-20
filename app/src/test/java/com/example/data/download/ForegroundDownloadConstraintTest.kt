package com.example.data.download

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkRequest
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.constraints.ConstraintsState
import androidx.work.impl.constraints.WorkConstraintsTracker
import androidx.work.impl.constraints.trackers.Trackers
import androidx.work.impl.model.WorkSpec
import androidx.work.impl.utils.taskexecutor.WorkManagerTaskExecutor
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.NetMirrorResolver
import com.example.data.NetMirrorStream
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.SubscriptionPlans
import com.example.data.repository.NetflixRepository
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer

/** Exercise both WorkManager subscriptions: scheduling, then foreground constraint tracking. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ForegroundDownloadConstraintTest {
    @Test fun startingForegroundDownloadOnStableHotspotDoesNotCancelPreparationAfterOneSecond() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadow = shadowOf(connectivity)
        shadow.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
            ConnectivityManager.TYPE_WIFI, 0, true, true))
        val network = connectivity.activeNetwork!!
        val capabilities = ShadowNetworkCapabilities.newInstance().also {
            val caps = shadowOf(it)
            caps.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            NetworkRequest.Builder().build().capabilities.forEach(caps::addCapability)
            caps.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            caps.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            caps.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        shadow.setNetworkCapabilities(network, capabilities)
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build())
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val repository = NetflixRepository(db.netflixDao())
        val manager = NetflixDownloadManager(app, repository, NetMirrorResolver(app), accountIdProvider = { "account" })
        val item = MediaItem("foreground_movie", "Movie", MediaType.MOVIE, "", "", 90, "16+", 2026, "2h",
            genres = emptyList(), cast = emptyList(), director = "")
        val wm = WorkManager.getInstance(app)
        try {
            manager.startOrResumeDownload("profile", item, isWifiOnly = true)
            val queued = wm.getWorkInfosForUniqueWork(downloadWorkName(item.id)).get().single()
            val spec = WorkSpec(queued.id.toString(), VideoDownloadWorker::class.java.name).apply { constraints = queued.constraints }
            val trackers = Trackers(app, WorkManagerTaskExecutor(SynchronousExecutor()))
            val tracker = WorkConstraintsTracker(trackers)
            val schedulingStates = mutableListOf<ConstraintsState>()
            val foregroundStates = mutableListOf<ConstraintsState>()
            val scheduling = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                tracker.track(spec).collect { schedulingStates += it }
            }
            runCurrent()
            shadow.networkCallbacks.toList().forEach { it.onCapabilitiesChanged(network, capabilities) }
            runCurrent()
            assertTrue(schedulingStates.last() is ConstraintsState.ConstraintsMet)
            val saved = DownloadRequestStore(File(app.noBackupFilesDir, "download-requests")).all().single()
            val verified = CompletableDeferred<Unit>()
            val packet = ByteArray(188 * 3) { 1 }.apply { for (i in indices step 188) this[i] = 0x47 }
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:6,\nsegment.ts\n#EXT-X-ENDLIST\n"))
            server.enqueue(MockResponse().setBody(Buffer().write(packet)))
            server.start()
            val file = File(app.filesDir, "downloads/${item.id}.ts")
            val download = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
                manager.runSavedDownload(item.id, saved.token, 0,
                    confirmPlan = { verified.await(); SubscriptionPlans.getById("plan_premium") },
                    transfer = { request, plan ->
                        manager.executeResolvedDownload(request.profileId, request.media, request.episode, item.id, null,
                            true, NetMirrorStream(server.url("/stream.m3u8").toString(), emptyMap(), emptyList(),
                                "test", Long.MAX_VALUE, item.title), plan.maxVideoHeight)
                    })
            }
            runCurrent()
            assertEquals(DownloadTaskStatus.PREPARING, manager.downloadTasks.value[item.id]!!.status)
            // setForeground starts a second subscription on exactly the same work constraints.
            val foreground = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                tracker.track(spec).collect {
                    foregroundStates += it
                    if (it is ConstraintsState.ConstraintsNotMet) download.cancel()
                }
            }
            try {
                runCurrent()
                // Wi-Fi never changes: Android correctly sends no further connectivity callback.
                advanceTimeBy(1_500)
                runCurrent()
                assertTrue("Stable Wi-Fi must immediately satisfy foreground constraints: $foregroundStates",
                    foregroundStates.lastOrNull() is ConstraintsState.ConstraintsMet)
                assertTrue("A false constraint event stops the worker during Preparing",
                    foregroundStates.none { it is ConstraintsState.ConstraintsNotMet })
                assertEquals(DownloadTaskStatus.PREPARING, manager.downloadTasks.value[item.id]!!.status)
                assertFalse(download.isCancelled)
                verified.complete(Unit)
                assertEquals(DownloadRunResult.COMPLETE, download.await())
                assertArrayEquals(packet, file.readBytes())
                val complete = repository.getDownloadsOnce("profile").single()
                assertTrue(complete.isComplete)
                assertEquals(file.absolutePath, complete.localFilePath)
            } finally {
                foreground.cancelAndJoin(); scheduling.cancelAndJoin(); download.cancelAndJoin()
                server.shutdown(); file.delete()
            }
        } finally { manager.cancelAndJoinTransfers(); manager.close(); wm.cancelAllWork().result.get(); db.close() }
    }
}
