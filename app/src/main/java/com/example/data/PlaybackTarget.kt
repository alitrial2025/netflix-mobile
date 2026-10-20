package com.example.data

import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.MediaType

/** Identical title/episode identity for playback, downloads, probes and cache eviction. */
internal data class PlaybackTarget(
    val tmdbId: String, val type: String, val season: Int, val episode: Int,
    val title: String, val year: String
) {
    companion object {
        fun from(media: MediaItem, episode: Episode?): PlaybackTarget {
            val movie = media.type == MediaType.MOVIE
            val selected = if (movie) null else episode ?: media.episodes.firstOrNull()
            val coordinates = episodeCoordinates(selected?.id)
            return PlaybackTarget(media.id, if (movie) "movie" else "tv",
                if (movie) 0 else coordinates.first,
                if (movie) 0 else selected?.episodeNumber?.takeIf { it > 0 } ?: coordinates.second,
                media.title, media.releaseYear.toString())
        }

        fun episodeCoordinates(id: String?, fallbackSeason: Int = 1, fallbackEpisode: Int = 1): Pair<Int, Int> {
            val match = Regex("(?:_S|^s)(\\d+)(?:_e|_)(\\d+)$", RegexOption.IGNORE_CASE).find(id.orEmpty())
            return if (match != null) {
                (match.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: fallbackSeason.coerceAtLeast(1)) to
                    (match.groupValues[2].toIntOrNull()?.coerceAtLeast(1) ?: fallbackEpisode.coerceAtLeast(1))
            } else fallbackSeason.coerceAtLeast(1) to fallbackEpisode.coerceAtLeast(1)
        }
    }
}
