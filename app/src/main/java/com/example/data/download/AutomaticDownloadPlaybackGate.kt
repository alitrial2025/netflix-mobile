package com.example.data.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Automatic offline work yields between requests throughout foreground playback. */
internal class AutomaticDownloadPlaybackGate {
    private val foreground = MutableStateFlow(false)
    fun setForeground(active: Boolean) { foreground.value = active }
    fun requirePermission(automatic: Boolean) {
        if (automatic && foreground.value) throw AutomaticDownloadDeferredException()
    }
    suspend fun awaitPermission(automatic: Boolean) {
        if (automatic) foreground.first { !it }
    }
}
internal class AutomaticDownloadDeferredException : java.io.IOException("Automatic download waits until playback closes")
internal class AutomaticDownloadContext(val automatic: Boolean) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AutomaticDownloadContext>
}
