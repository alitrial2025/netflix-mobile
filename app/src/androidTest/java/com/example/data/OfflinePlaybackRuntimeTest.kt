package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.model.UserSubscription
import com.example.update.UpdateGateViewModel
import com.example.update.UpdatePhase
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Run prepareOfflineRestartFixture, force-stop the target app, then run
 * persistedMembershipAndLocalVideoSurviveAnOfflineProcessRestart separately.
 * The synthetic proof lives in its own fixture directory and never authorizes a real account.
 */
@androidx.media3.common.util.UnstableApi
@RunWith(AndroidJUnit4::class)
class OfflinePlaybackRuntimeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val directory get() = File(context.noBackupFilesDir, "runtime-offline-fixture")
    private val proofFile get() = File(directory, "verified-entry.json")
    private val mediaFile get() = File(directory, "download.mp4")
    private val fixtureUid = "isolated-runtime-fixture"

    @Test fun prepareOfflineRestartFixture() {
        directory.mkdirs()
        val expiry = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)
        val subscription = UserSubscription(status = "ACTIVE", planId = "plan_premium", planName = "Premium", expiresAt = expiry)
        val entry = OfflineEntryProof(MembershipProof(fixtureUid, DeviceAccessGuard.deviceId(context),
            subscription.planId, expiry, subscription.status), subscription)
        // Exercise the production worker's durable checkpoint before creating downloaded media.
        // Only the server response is an isolated fixture; no real account is involved.
        runBlocking {
            verifyDownloadMembershipAndCheckpoint(fixtureUid, entry.proof.device, OfflineEntryProofStore(proofFile),
                now = System::currentTimeMillis, isCurrent = { true }, verify = { Unit to entry })
        }
        instrumentation.context.assets.open("offline-runtime-fixture.mp4").use { input ->
            mediaFile.outputStream().use { input.copyTo(it) }
        }
        File(directory, "prepared-pid").writeText(android.os.Process.myPid().toString())
        val installed = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
        val pendingRelease = JSONObject().apply {
            put("schemaVersion", 1)
            put("channel", "mobile")
            put("packageName", context.packageName)
            put("available", true)
            put("versionCode", installed + 1)
            put("versionName", "isolated-runtime-fixture")
            put("minSdk", 24)
            put("sizeBytes", 1024)
            put("sha256", "a".repeat(64))
            put("apkUrl", "/downloads/mobile/isolated-runtime-fixture.apk")
        }.toString()
        context.getSharedPreferences(UpdateGateViewModel.PREFS, Context.MODE_PRIVATE).edit().clear()
            .putString("release", pendingRelease).putLong("download_id", 808L).putInt("install_session", 42).commit()
        assertTrue(mediaFile.length() > 10_000L)
    }

    @Test fun persistedMembershipAndLocalVideoSurviveAnOfflineProcessRestart() {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        assertNull("This runtime check must run with Wi-Fi and mobile data disabled", connectivity.activeNetwork)
        assertNotEquals("Invoke this test after a separate process and force-stop", File(directory, "prepared-pid").readText(),
            android.os.Process.myPid().toString())
        val device = DeviceAccessGuard.deviceId(context)
        val store = OfflineEntryProofStore(proofFile)
        val restored = store.load(fixtureUid, device, System.currentTimeMillis())
        assertNotNull("Actual Android AtomicFile must preserve the original proof across process death", restored)
        assertNull("A different account must not inherit it", store.load("another-runtime-account", device, System.currentTimeMillis()))
        assertNull("A different installation must not inherit it", store.load(fixtureUid, "another-installation", System.currentTimeMillis()))
        assertNull("Offline access must still expire", store.load(fixtureUid, device, restored!!.proof.expiry + RenewalPolicy.GRACE_MS))

        val firstFrame = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val failure = AtomicReference<PlaybackException?>()
        val audioDecoder = AtomicReference<String?>()
        var player: ExoPlayer? = null
        lateinit var gate: UpdateGateViewModel
        val models = ViewModelStore()
        val updatePrefs = context.getSharedPreferences(UpdateGateViewModel.PREFS, Context.MODE_PRIVATE)
        val pendingUpdate = updatePrefs.all.toMap()
        assertTrue("The fixture must include a retained mandatory update", pendingUpdate.containsKey("release"))
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity { activity ->
                gate = UpdateGateViewModel(activity.application)
                models.put("runtime-offline-update", gate)
                val surface = SurfaceView(activity)
                activity.setContentView(surface)
                player = ExoPlayer.Builder(activity).build().also { active ->
                    active.setVideoSurfaceView(surface)
                    active.addAnalyticsListener(object : AnalyticsListener {
                        override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String,
                            initializedTimestampMs: Long, initializationDurationMs: Long) {
                            audioDecoder.set(decoderName)
                        }
                    })
                    active.addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() { firstFrame.countDown() }
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) ended.countDown()
                        }
                        override fun onPlayerError(error: PlaybackException) {
                            failure.set(error)
                            firstFrame.countDown()
                            ended.countDown()
                        }
                    })
                    active.setMediaItem(MediaItem.fromUri(Uri.fromFile(mediaFile)))
                    active.prepare()
                    active.play()
                }
            }
            val entry = runBlocking { withTimeout(30_000L) { gate.state.first { it.phase != UpdatePhase.CHECKING } } }
            assertEquals("Offline startup must defer even a pending mandatory update", UpdatePhase.CONTINUE, entry.phase)
            assertEquals("Offline startup must preserve the pending update", pendingUpdate, updatePrefs.all)
            assertTrue("Local Media3 playback never rendered a video frame", firstFrame.await(90, TimeUnit.SECONDS))
            assertNull("Media3 decoder failure: ${failure.get()}", failure.get())
            assertTrue("The complete local fixture did not finish offline", ended.await(90, TimeUnit.SECONDS))
            assertNull("Media3 playback failure: ${failure.get()}", failure.get())
            assertNotNull("The local fixture must also initialize a real audio decoder", audioDecoder.get())
            assertNotNull("Local playback must not remove the original membership proof",
                store.load(fixtureUid, device, System.currentTimeMillis()))
            scenario.onActivity { gate.onResume(it) }
            assertEquals("Resuming offline playback must not open the updater", UpdatePhase.CONTINUE, gate.state.value.phase)
            assertEquals("Local playback must not discard the pending update", pendingUpdate, updatePrefs.all)
        } finally {
            instrumentation.runOnMainSync {
                player?.release()
                models.clear()
            }
            scenario.close()
        }
    }
}
