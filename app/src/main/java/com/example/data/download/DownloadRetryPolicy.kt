package com.example.data.download

import java.io.IOException

internal class DownloadHttpException(val code: Int, val usedProviderSession: Boolean = false) : IOException("Download server returned HTTP $code")
internal class DownloadMembershipException(message: String) : Exception(message)
internal class DownloadStorageException : IOException("Not enough free storage for this download")

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
        is DownloadHttpException -> when (error.code) {
            401, 403 -> "The download link expired. Continue to request a fresh link."
            404, 410 -> "This title cannot be downloaded. Try again later."
            else -> "The server is unavailable. Continue to retry your saved download."
        }
        is IOException -> "The connection was interrupted. Continue to resume your saved download."
        else -> "This title cannot be downloaded. Try again later."
    }
}
