package com.example.data.model

import androidx.compose.runtime.Immutable

/** A local playback bookmark. This does not contain or record any video. */
@Immutable
data class SavedMoment(
    val id: String,
    val media: MediaItem,
    val positionSeconds: Int,
    val durationSeconds: Int = 15,
    val createdAt: Long,
    val episodeId: String? = null,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    val episodeTitle: String? = null,
    val titleDurationSeconds: Int? = null
)
