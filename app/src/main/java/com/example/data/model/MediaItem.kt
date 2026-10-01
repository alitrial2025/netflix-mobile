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
    val popularity: Double = 0.0
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

    fun matchesGenre(filter: String?): Boolean {
        if (filter.isNullOrBlank() || filter.equals("All", ignoreCase = true) || filter.equals("All Categories", ignoreCase = true)) {
            return true
        }

        val rawQuery = filter.trim().lowercase()
        val itemGenresLower = genres.map { it.lowercase() }
        val titleLower = title.lowercase()
        val descLower = description.lowercase()
        val taglineLower = tagline.lowercase()

        // Split multiple category keywords like "Crime Thrillers" or "Action & Adventure"
        val queryTokens = rawQuery.split(Regex("[^a-zA-Z0-9]+")).filter { it.length >= 3 }

        // 1. Direct contains check between tokens and item genres
        if (itemGenresLower.any { g -> 
            g.contains(rawQuery) || rawQuery.contains(g) || queryTokens.any { token -> g.contains(token) || token.contains(g) }
        }) {
            return true
        }

        // 2. Keyword cluster mapping for Netflix category titles
        val keywords: List<String> = when {
            rawQuery.contains("crime") || rawQuery.contains("thrill") || rawQuery.contains("mystery") || rawQuery.contains("suspense") -> {
                listOf(
                    "crime", "thriller", "mystery", "suspense", "detective", "police",
                    "investigation", "heist", "murder", "cartel", "gangster", "noir",
                    "underworld", "courtroom", "law", "fbi", "cia", "assassin", "killer",
                    "mafia", "prison", "robbery", "con", "fugitive", "action & adventure"
                )
            }
            rawQuery.contains("action") || rawQuery.contains("adventure") -> {
                listOf("action", "adventure", "superhero", "martial arts", "hero", "combat", "fight", "war", "battle", "chase", "mission", "blockbuster", "thriller")
            }
            rawQuery.contains("sci-fi") || rawQuery.contains("science fiction") || rawQuery.contains("cyber") ||
                    rawQuery.contains("fantasy") || rawQuery.contains("supernatural") -> {
                listOf(
                    "sci-fi", "science fiction", "fantasy", "cyberpunk", "futuristic", "space",
                    "alien", "dystopian", "supernatural", "magic", "time travel", "multiverse",
                    "monster", "ai", "virtual", "apocalypse", "universe", "planet", "galaxy", "superhero", "sci-fi & fantasy"
                )
            }
            rawQuery.contains("drama") || rawQuery.contains("dramas") -> {
                listOf("drama", "dramas", "dramatic", "emotional", "tragedy", "biography", "romance", "romantic", "melodrama", "historical", "period", "coming-of-age")
            }
            rawQuery.contains("anime") || rawQuery.contains("animation") -> {
                listOf("anime", "animation", "animated", "manga", "cartoon", "japanese", "japan", "studio ghibli", "shonen", "otaku")
            }
            rawQuery.contains("doc") || rawQuery.contains("biograph") -> {
                listOf("documentary", "documentaries", "docuseries", "biography", "history", "nature", "real life", "true story", "investigative", "historical")
            }
            rawQuery.contains("horror") || rawQuery.contains("scary") || rawQuery.contains("spooky") -> {
                listOf("horror", "thriller", "terror", "creepy", "spooky", "scary", "ghost", "demon", "zombie", "monster", "slasher", "curse", "haunted", "witch", "vampire", "fear", "dark", "paranormal", "gore")
            }
            rawQuery.contains("k-drama") || rawQuery.contains("korea") || rawQuery.contains("asian") || rawQuery.contains("international") -> {
                listOf("korean", "k-drama", "kdrama", "korea", "asian", "seoul", "k-pop", "international", "foreign", "spanish", "japanese")
            }
            rawQuery.contains("comedy") || rawQuery.contains("comedies") || rawQuery.contains("stand-up") || rawQuery.contains("sitcom") -> {
                listOf("comedy", "comedies", "sitcom", "funny", "humor", "stand-up", "parody", "satire", "laugh", "hilarious")
            }
            rawQuery.contains("romanc") || rawQuery.contains("love") -> {
                listOf("romance", "romantic", "love", "relationship", "dating", "wedding", "passion", "heart", "couple")
            }
            rawQuery.contains("kid") || rawQuery.contains("family") || rawQuery.contains("children") -> {
                listOf("kids", "family", "children", "animation", "disney", "pixar", "cartoon", "toddler", "teen")
            }
            rawQuery.contains("award") || rawQuery.contains("acclaim") || rawQuery.contains("top 10") -> {
                listOf("award", "acclaimed", "oscar", "emmy", "critics", "masterpiece", "top rated")
            }
            else -> {
                queryTokens
            }
        }

        // Check if any existing genre matches any keyword
        if (itemGenresLower.any { g -> keywords.any { kw -> g.contains(kw) || kw.contains(g) } }) {
            return true
        }

        // Secondary search on title, synopsis overview, or tagline for relevant keywords
        if (keywords.any { kw -> titleLower.contains(kw) || descLower.contains(kw) || taglineLower.contains(kw) }) {
            return true
        }

        return false
    }
}

