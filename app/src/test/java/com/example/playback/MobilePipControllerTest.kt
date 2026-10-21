package com.example.playback

import android.app.Activity
import android.app.Application
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class)
class MobilePipControllerTest {
    class Host : Activity() {
        var params: PictureInPictureParams? = null
        var entries = 0
        var inPip = false
        override fun setPictureInPictureParams(params: PictureInPictureParams) { this.params = params }
        override fun enterPictureInPictureMode(params: PictureInPictureParams): Boolean {
            this.params = params; entries++; inPip = true; return true
        }
        override fun isInPictureInPictureMode() = inPip
    }
    private fun fixture(block: (Host, MobilePipController, IntArray) -> Unit) {
        val activity = Robolectric.buildActivity(Host::class.java).setup()
        val host = activity.get()
        shadowOf(host.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        // Robolectric API 28 does not automatically grant the signature permission
        // contributed by androidx.core; the installed APK manifest already declares it.
        shadowOf(host).grantPermissions("${host.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val events = intArrayOf(0, 0)
        val controller = MobilePipController(host, { events[0]++ }, { events[1]++ })
        try { block(host, controller, events) }
        finally { controller.destroy(); activity.pause().stop().destroy() }
    }
    @Test @Config(sdk = [24])
    fun androidSevenKeepsNormalPlaybackWithoutCallingPipApis() = fixture { host, pip, _ ->
        pip.update(true, true)
        pip.onUserLeaveHint()
        pip.onStop()
        assertEquals(0, host.entries)
        assertNull(host.params)
    }
    @Test fun homeOnlyEntersForAnEligiblePlayingMainVideo() = fixture { host, pip, _ ->
        pip.onUserLeaveHint()
        assertEquals(0, host.entries)
        pip.update(true, false)
        pip.onUserLeaveHint()
        assertEquals(0, host.entries)
        pip.update(true, true)
        pip.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31) assertEquals(1, host.entries)
        else {
            assertEquals(0, host.entries) // Gesture navigation uses OS auto-enter, not a second manual entry.
            assertTrue(host.params!!.isAutoEnterEnabled)
        }
    }
    @Test fun clearingPlayerRemovesActionsAndAutomaticEntry() = fixture { host, pip, _ ->
        pip.update(true, true, Float.NaN)
        assertEquals(1, host.params!!.actions.size)
        if (Build.VERSION.SDK_INT >= 33) assertTrue(host.params!!.aspectRatio!!.toFloat().isFinite())
        pip.clear()
        assertTrue(host.params!!.actions.isEmpty())
        pip.onUserLeaveHint()
        assertEquals(0, host.entries)
        if (Build.VERSION.SDK_INT >= 31) assertFalse(host.params!!.isAutoEnterEnabled)
    }
    @Test fun systemActionTogglesExactlyOnceOnlyWhileInPip() = fixture { host, pip, events ->
        pip.update(true, true)
        val action = host.params!!.actions.single().actionIntent
        action.send()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(0, events[0])
        pip.onModeChanged(true)
        action.send()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, events[0])
        pip.update(true, false)
        assertEquals("Play", host.params!!.actions.single().title.toString())
        pip.onModeChanged(false)
        action.send()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, events[0])
    }
    @Test fun dismissingPipClosesPlaybackButNormalBackgroundDoesNot() = fixture { host, pip, events ->
        pip.update(true, true)
        pip.onStop()
        assertEquals(0, events[1])
        host.inPip = true
        pip.onModeChanged(true)
        pip.onStop()
        assertEquals(1, events[1])
        assertTrue(host.params!!.actions.isEmpty())
    }
}
