package com.example.ui.viewmodel

import com.example.data.PlaybackMarkers

data class IntroWindow(val startSec: Int, val endSec: Int)

/** Pills require actual metadata; dialogue gaps and a percentage of runtime are not boundaries. */
internal object EpisodePlaybackPolicy {
    fun verifiedIntro(markers: PlaybackMarkers, durationMs: Long): IntroWindow? {
        val start = markers.introStart ?: return null
        val end = markers.introEnd ?: return null
        if (durationMs <= 0 || start < 0 || end <= start || end > durationMs) return null
        // Player seeks are whole seconds: round forward, so a skip never stops inside the intro.
        val startSec = (start / 1000 + if (start % 1000 == 0L) 0 else 1).toInt()
        val endSec = (end / 1000 + if (end % 1000 == 0L) 0 else 1).toInt()
        return IntroWindow(startSec, endSec).takeIf { endSec > startSec }
    }

    fun hasVerifiedCredits(markers: PlaybackMarkers, positionMs: Long, durationMs: Long): Boolean {
        val credits = markers.outroStart ?: return false
        return durationMs > 0 && credits in 0 until durationMs && positionMs >= credits && positionMs <= durationMs
    }
}
