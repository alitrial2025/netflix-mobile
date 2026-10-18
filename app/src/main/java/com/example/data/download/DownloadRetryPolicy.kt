package com.example.data.download

import java.io.IOException
import com.example.data.PlaybackRateLimitedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class DownloadHttpException(val code: Int, val usedProviderSession: Boolean = false) : IOException("Download server returned HTTP $code")
internal class DownloadMembershipException(message: String) : Exception(message)
internal class DownloadStorageException : IOException("Not enough free storage for this download")
internal class DownloadVerificationUnavailableException(cause: Exception) : IOException("Download access verification unavailable", cause)
internal class DownloadSourceUnavailableException(cause: Exception) : IOException("Download source unavailable", cause)

/** A resolver's own deadline is retryable; stopping the worker must still cancel the transfer. */
internal suspend fun <T> resolveDownloadSource(resolve: suspend () -> T): T = try {
    resolve()
} catch (timeout: TimeoutCancellationException) {
    currentCoroutineContext().ensureActive()
    throw DownloadSourceUnavailableException(timeout)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (limited: PlaybackRateLimitedException) {
    throw limited
} catch (error: IOException) {
    throw DownloadSourceUnavailableException(error)
}

internal object DownloadRetryPolicy {
    private fun isStorageFailure(error: Exception) = error is DownloadStorageException ||
        error.message.orEmpty().let { it.contains("ENOSPC", true) || it.contains("No space left", true) }
    fun shouldRetry(error: Exception, attempt: Int): Boolean = attempt < 8 && when (error) {
        is IOException -> when {
            isStorageFailure(error) -> false
            error is DownloadHttpException -> error.code in setOf(401, 403, 408, 429) || error.code >= 500
            else -> true
        }
        else -> false
    }
    fun userMessage(error: Exception): String = if (isStorageFailure(error)) "Free up device storage, then continue the download." else when (error) {
        is DownloadMembershipException -> error.message ?: "Renew your membership to continue."
        is DownloadVerificationUnavailableException -> "Could not verify download access. Check your connection and retry."
        is DownloadSourceUnavailableException -> "The download service is unavailable. Retry to request a download link."
        is PlaybackRateLimitedException -> "The download service is busy. Wait a little, then retry."
        is DownloadHttpException -> when (error.code) {
            401, 403 -> "The download link expired. Continue to request a fresh link."
            404, 410 -> "This title cannot be downloaded. Try again later."
            else -> "The server is unavailable. Continue to retry your saved download."
        }
        is IOException -> "The connection was interrupted. Continue to resume your saved download."
        else -> "This title cannot be downloaded. Try again later."
    }

    fun retryMessage(error: Exception): String = when (error) {
        is DownloadVerificationUnavailableException -> "Waiting to verify download access. Your membership has not changed."
        is DownloadSourceUnavailableException -> "Waiting for a download link. Retrying automatically."
        is PlaybackRateLimitedException -> "The download service is busy. Retrying automatically after a wait."
        is DownloadHttpException -> when (error.code) {
            401, 403 -> "Refreshing the expired download link. Your saved progress will resume automatically."
            429 -> "The download server is busy. Retrying automatically after a wait."
            else -> "The download server is unavailable. Retrying automatically."
        }
        else -> "Connection interrupted. Your saved progress will resume automatically."
    }
}
