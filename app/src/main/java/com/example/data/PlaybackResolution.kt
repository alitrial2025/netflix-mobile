package com.example.data

import java.io.IOException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A resolver deadline is a load failure; user cancellation must still stop all work. */
internal suspend fun <T> resolvePlaybackSource(resolve: suspend () -> T): T = try {
    resolve()
} catch (timeout: TimeoutCancellationException) {
    currentCoroutineContext().ensureActive()
    throw IOException("Playback source resolution timed out", timeout)
}
