package com.example.data.download

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope

internal class DownloadAttemptInterruptedException(cause: CancellationException) :
    IOException("Download preparation was interrupted", cause)

/** A cancelled provider/Firebase operation is not necessarily a stopped Android worker. */
internal suspend fun <T> withResumableDownloadAttempt(transfer: suspend () -> T): T = supervisorScope {
    val attempt = async { transfer() }
    try {
        attempt.await()
    } catch (cancelled: CancellationException) {
        // Preserve real pause, sign-out and Android constraint cancellations. Otherwise return
        // a retryable error so WorkManager receives Result.retry(), rather than an escaped
        // CancellationException leaving the durable request waiting without a scheduled retry.
        currentCoroutineContext().ensureActive()
        throw DownloadAttemptInterruptedException(cancelled)
    }
}
