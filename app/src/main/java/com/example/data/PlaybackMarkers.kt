package com.example.data

/** Exact publisher or validated community timings for one playback edition, in milliseconds. */
data class PlaybackMarkers(
    val introStart: Long? = null,
    val introEnd: Long? = null,
    val outroStart: Long? = null
)
