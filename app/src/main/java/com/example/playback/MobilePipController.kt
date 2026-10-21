package com.example.playback

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

val LocalMobilePipController = staticCompositionLocalOf<MobilePipController?> { null }

/** Activity-owned PiP; only the full-screen player can make Home enter it. */
class MobilePipController(
    private val activity: Activity,
    private val onTogglePlayback: () -> Unit,
    private val onDismissPlayback: () -> Unit
) {
    private val supported = Build.VERSION.SDK_INT >= 26 &&
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    private val action = "${activity.packageName}.PIP_TOGGLE"
    private val _compact = MutableStateFlow(false)
    val compact = _compact.asStateFlow()
    private var eligible = false
    private var playing = false
    private var ratio = Rational(16, 9)
    private var sourceRect: Rect? = null
    private var receiverRegistered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == action && _compact.value && eligible) onTogglePlayback()
        }
    }

    init {
        if (supported) {
            ContextCompat.registerReceiver(activity, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
    }

    fun update(canEnter: Boolean, isPlaying: Boolean, aspectRatio: Float = 16f / 9f) {
        val nextRatio = Rational((aspectRatio.takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(1f / 2.39f, 2.39f) ?: (16f / 9f)).times(10_000).toInt(), 10_000)
        if (eligible == canEnter && playing == isPlaying && ratio == nextRatio) return
        eligible = canEnter
        playing = isPlaying
        ratio = nextRatio
        applyParams()
    }

    fun setSourceRect(rect: Rect) {
        if (sourceRect == rect || rect.isEmpty) return
        sourceRect = Rect(rect)
        applyParams()
    }

    @androidx.annotation.RequiresApi(26)
    private fun params(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder().setAspectRatio(ratio)
        sourceRect?.let { builder.setSourceRectHint(it) }
        if (eligible) {
            val pending = PendingIntent.getBroadcast(activity, 0,
                Intent(action).setPackage(activity.packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val label = if (playing) "Pause" else "Play"
            builder.setActions(listOf(RemoteAction(Icon.createWithResource(activity,
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play), label, label, pending)))
        } else builder.setActions(emptyList())
        if (Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(eligible && playing).setSeamlessResizeEnabled(true)
        return builder.build()
    }

    private fun applyParams() {
        if (Build.VERSION.SDK_INT < 26 || !supported || activity.isFinishing || activity.isDestroyed) return
        try { activity.setPictureInPictureParams(params()) }
        catch (_: IllegalStateException) { /* Device policy can disable PiP. Normal playback remains available. */ }
        catch (_: IllegalArgumentException) { /* Vendor restrictions must not crash the player. */ }
    }

    fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT < 26 || !supported || !eligible || !playing) return
        if (Build.VERSION.SDK_INT >= 31) return // Android handles gesture navigation before ON_PAUSE.
        try {
            if (activity.enterPictureInPictureMode(params())) _compact.value = true
        } catch (_: IllegalStateException) { }
        catch (_: IllegalArgumentException) { }
    }

    fun onModeChanged(inPip: Boolean) { _compact.value = inPip }
    fun onStop() {
        if (Build.VERSION.SDK_INT >= 26 && supported && activity.isInPictureInPictureMode) {
            clear()
            onDismissPlayback()
        }
    }
    fun clear() { update(false, false) }
    fun destroy() {
        if (receiverRegistered) { activity.unregisterReceiver(receiver); receiverRegistered = false }
    }
}
