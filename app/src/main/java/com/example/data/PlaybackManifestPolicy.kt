package com.example.data

import android.net.Uri

/** Shared validation and resolver handoff for the player and offline transfers. */
internal class PlaybackManifestPolicy(
    private val sourceId: String?,
    private val newTvGate: CookieFreePlaybackGate = NewTvPlaybackGate
) {
    private val newTv = sourceId?.contains("[NewTV]", true) == true
    private fun remainingMs() = if (newTv) newTvGate.remainingMs() else PlaybackServiceGate.remainingMs()
    private fun usesProvider(url: String): Boolean {
        val uri = Uri.parse(url)
        val host = uri.host.orEmpty()
        return newTv || host.contains("freecdn", true) ||
            host in listOf("net52.cc", "netmirror.app", "netmirror.gg", "mobidetects.com", "mobidetect.art") ||
            uri.path?.startsWith("/files/") == true
    }

    fun beforeRequest(url: String) {
        if (usesProvider(url)) {
            if (newTv) newTvGate.check() else PlaybackServiceGate.check()
        }
        // Reopening a cached waiting URL must not extend the cooldown.
        if (StreamSessionPolicy.isWaitingVideo(url)) throw PlaybackRateLimitedException(remainingMs())
    }

    fun takeVerified(url: String, headers: Map<String, String>): ByteArray? {
        val bytes = VerifiedManifestHandoff.take(url, headers, sourceId) ?: return null
        if (StreamSessionPolicy.isRateLimited(200, bytes.toString(Charsets.UTF_8)))
            throw PlaybackRateLimitedException(remainingMs())
        return bytes
    }

    fun checkManifest(body: String, retryAfter: String?, url: String) {
        if (newTv) newTvGate.checkResponse(200, body, retryAfter, url)
        else PlaybackServiceGate.checkResponse(200, body, retryAfter, url)
    }

    fun checkHttpFailure(code: Int, retryAfter: String?, url: String) {
        if (code == 429 && usesProvider(url)) {
            if (newTv) newTvGate.checkResponse(code, "", retryAfter, url)
            else PlaybackServiceGate.checkResponse(code, "", retryAfter, url)
        }
    }

    companion object { const val MAX_BYTES = 8 * 1024 * 1024 }
}
