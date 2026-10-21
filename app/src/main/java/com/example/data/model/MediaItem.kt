package com.example.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Episode(
    val id: String,
    val episodeNumber: Int,
    val title: String,
    val durationMinutes: Int,
    val description: String,
    val stillUrl: String? = null,
    val isDownloaded: Boolean = false,
    val downloadSizeMb: Int = 185
)

enum class MediaType {
    MOVIE, TV_SHOW
}

@Immutable
data class MediaItem(
    val id: String,
    val title: String,
    val type: MediaType,
    val description: String,
    val tagline: String,
    val matchPercentage: Int,
    val maturityRating: String, // "18+", "16+", "13+", "PG"
    val releaseYear: Int,
    val durationOrSeasons: String, // "4 Seasons" or "2h 14m"
    val isOriginal: Boolean = true,
    val top10Rank: Int? = null,
    val genres: List<String>,
    val cast: List<String>,
    val director: String,
    val isTrending: Boolean = false,
    val isComingSoon: Boolean = false,
    val releaseDateBadge: String? = null, // e.g. "AUG 25", "SEP 12"
    val bannerDrawableRes: Int? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val primaryColorHex: Long = 0xFFE50914,
    val secondaryColorHex: Long = 0xFF141414,
    val episodes: List<Episode> = emptyList(),
    val videoPreviewDurationSec: Int = 120,
    val totalSeasons: Int = 1,
    val totalEpisodes: Int = 0,
    val similarMedia: List<MediaItem> = emptyList(),
    val releaseDate: String? = null,
    val genreIds: List<Int> = emptyList(),
    val voteAverage: Double = 0.0,
    val voteCount: Int = 0,
    val popularity: Double = 0.0,
    val heroPosterUrl: String? = null,
    val originalLanguage: String? = null
) {
    fun isKidSafe(maxAge: Int = 12): Boolean {
        val rating = maturityRating.trim().uppercase()
        val requiredAge = when (rating) {
            "TV-Y", "TV-G", "G", "U", "ALL", "EVERYONE" -> 0
            "TV-Y7" -> 7
            "PG", "TV-PG" -> 10
            "PG-13" -> 13
            "TV-14" -> 14
            "R" -> 17
            "TV-MA", "NC-17", "MA" -> 18
            else -> Regex("(?:^|[^0-9])(\\d{1,2})\\+").find(rating)?.groupValues?.get(1)?.toIntOrNull()
        }
        // Genre is not a maturity certificate. Unknown ratings stay out of restricted profiles.
        return requiredAge != null && requiredAge <= maxAge
    }

    fun matchesGenre(filter: String?): Boolean = matchesCatalogGenre(this, filter)
}
