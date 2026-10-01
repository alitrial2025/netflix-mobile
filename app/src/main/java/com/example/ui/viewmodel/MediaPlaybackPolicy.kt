package com.example.ui.viewmodel

import com.example.data.local.WatchProgressEntity
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.MediaType

data class DetailLoadState(
    val loading: Boolean = false,
    val error: String? = null,
    val season: Int = 1,
    val seasonLoading: Boolean = false,
    val seasonError: String? = null
)

internal fun WatchProgressEntity.canResume(): Boolean =
    positionSeconds > 0 && totalSeconds > 0 && positionSeconds < totalSeconds * .95

/** Preserve saved season coordinates even when a lightweight Home item has no episodes. */
internal fun initialPlaybackEpisode(media: MediaItem, requested: Episode?, progress: WatchProgressEntity?): Episode? {
    if (media.type != MediaType.TV_SHOW) return null
    requested?.let { return it }
    if (progress?.canResume() == true && progress.episodeId.isNotBlank()) {
        return media.episodes.firstOrNull { it.id == progress.episodeId } ?: Episode(
            progress.episodeId, progress.episode, progress.episodeTitle ?: "Episode ${progress.episode}",
            (progress.totalSeconds / 60).coerceAtLeast(1), ""
        )
    }
    return media.episodes.minWithOrNull(compareBy({ episodeCoordinates(it.id).first }, { it.episodeNumber }))
        ?: Episode("ep_${media.id}_S1_1", 1, "Episode 1", 45, "")
}

internal fun nextLoadedEpisode(current: Episode, episodes: List<Episode>): Episode? {
    val coordinates = episodeCoordinates(current.id)
    return episodes.filter {
        val candidate = episodeCoordinates(it.id)
        candidate.first == coordinates.first && candidate.second == coordinates.second + 1
    }.firstOrNull()
}

/** IDs contain underscores; the composite download key must never be split on the first one. */
internal fun downloadEpisodeId(mediaId: String, key: String): String? =
    key.takeIf { it != mediaId && it.startsWith("${mediaId}_") }?.removePrefix("${mediaId}_")?.takeIf { it.isNotBlank() }

internal fun episodeDownloadLabel(episode: Episode): String {
    val coordinates = episodeCoordinates(episode.id, fallbackEpisode = episode.episodeNumber)
    return "S${coordinates.first}:E${coordinates.second} ${episode.title}"
}

internal fun postPlayCandidates(current: MediaItem, safeCatalog: List<MediaItem>): List<MediaItem> =
    safeCatalog.filter { it.id != current.id && !it.isComingSoon }
        .distinctBy { it.type to it.id }
        .sortedWith(compareByDescending<MediaItem> { candidate -> candidate.genres.count { it in current.genres } }
            .thenByDescending { it.matchPercentage })
        .take(3)

/** Seeking into credits is not a completed playback event. */
internal fun smartDownloadCompletionReady(state: PlayerState): Boolean = state.hasEnded && state.durationSec > 60
