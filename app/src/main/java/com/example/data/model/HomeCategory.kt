package com.example.data.model

import java.util.Locale

/** Labels, local metadata rules and TMDB discovery use the same category definitions. */
enum class HomeCategory(
    val label: String, val key: String,
    val movieGenres: String = "", val tvGenres: String = "",
    val providerIds: String = "", val region: String = "US",
    val tvKeyword: String = "", val originalLanguage: String = ""
) {
    NETFLIX("Netflix", "netflix", providerIds = "8|1796|175"),
    PRIME("Prime Video", "prime", providerIds = "9|119|2100"),
    DISNEY("Disney+", "disney", providerIds = "337"),
    HOTSTAR("Hotstar", "hotstar", providerIds = "2336|122", region = "IN"),
    APPLE("Apple TV", "apple", providerIds = "350"),
    PEACOCK("Peacock", "peacock", providerIds = "386|387"),
    ACTION("Action & Adventure", "action", "28|12", "10759"),
    CRIME("Crime Thrillers", "crime", "80|53|9648", "80|9648"),
    SCIENCE_FICTION("Sci-Fi & Cyberpunk", "science_fiction", "878", "10765"),
    DRAMA("Dramas", "drama", "18", "18"),
    ANIMATION("Anime & Animation", "animation", "16", "16"),
    COMEDY("Comedies", "comedy", "35", "35"),
    DOCUMENTARY("Documentaries", "documentary", "99", "99"),
    HORROR("Horror & Suspense", "horror", "27|53", "9648"),
    INTERNATIONAL("International & K-Dramas", "international", originalLanguage = "ko"),
    FAMILY("Kids & Family", "family", "10751", "10751|10762"),
    ROMANCE("Romance", "romance", "10749", tvKeyword = "9840"),
    FANTASY("Fantasy & Supernatural", "fantasy", "14", "10765");

    val isService: Boolean get() = providerIds.isNotEmpty()

    companion object {
        val services = entries.filter { it.isService }
        val genres = entries.filterNot { it.isService }
        fun fromLabel(label: String?): HomeCategory? = entries.firstOrNull { it.label.equals(label?.trim(), true) }
    }
}

/** Genre IDs/language are authoritative. Synopsis words never establish category membership. */
internal fun matchesCatalogGenre(media: MediaItem, filter: String?): Boolean {
    if (filter.isNullOrBlank() || filter.equals("All", true) || filter.equals("All Categories", true)) return true
    val query = filter.trim().lowercase(Locale.ROOT)
    val category = HomeCategory.fromLabel(filter)
    if (category?.isService == true) return false // Service membership comes from provider discovery.
    val international = category == HomeCategory.INTERNATIONAL || query in setOf("k-dramas", "k-drama", "korean", "international")
    if (international) {
        val language = media.originalLanguage?.lowercase(Locale.ROOT)
        if (category != HomeCategory.INTERNATIONAL && query != "international") return language == "ko" ||
            media.genres.any { it.lowercase(Locale.ROOT) in setOf("korean", "k-drama", "k-dramas") }
        return (language != null && language !in setOf("", "en", "und", "xx", "zxx")) ||
            media.genres.any { it.lowercase(Locale.ROOT) in setOf("korean", "k-drama", "k-dramas", "international", "japanese", "spanish") }
    }
    val aliases = when (query) {
        "action" -> setOf(28, 10759)
        "adventure" -> setOf(12, 10759)
        "crime", "crime tv shows" -> setOf(80)
        "drama", "dramas" -> setOf(18)
        "comedy", "comedies" -> setOf(35)
        "documentary", "documentaries" -> setOf(99)
        "anime", "animation" -> setOf(16)
        "sci-fi", "science fiction", "sci-fi & fantasy" -> setOf(878, 10765)
        "fantasy" -> setOf(14, 10765)
        "horror" -> setOf(27)
        "thriller", "thrillers", "suspense" -> setOf(53, 9648)
        "family", "kids" -> setOf(10751, 10762)
        "romance", "romantic" -> setOf(10749, 10766)
        else -> emptySet()
    }
    val ids = if (category != null) {
        (if (media.type == MediaType.MOVIE) category.movieGenres else category.tvGenres)
            .split('|').mapNotNull(String::toIntOrNull).toSet() +
            if (category == HomeCategory.ROMANCE) setOf(10749, 10766) else emptySet()
    } else aliases
    if (media.genreIds.isNotEmpty() && ids.isNotEmpty()) return media.genreIds.any { it in ids }
    val names = when (category) {
        HomeCategory.ACTION -> setOf("action", "adventure", "action & adventure")
        HomeCategory.CRIME -> setOf("crime", "thriller", "thrillers", "mystery", "crime thrillers", "mystery thrillers")
        HomeCategory.SCIENCE_FICTION -> setOf("sci-fi", "science fiction", "sci-fi & fantasy", "cyberpunk")
        HomeCategory.DRAMA -> setOf("drama", "dramas")
        HomeCategory.ANIMATION -> setOf("anime", "animation", "animated")
        HomeCategory.COMEDY -> setOf("comedy", "comedies")
        HomeCategory.DOCUMENTARY -> setOf("documentary", "documentaries")
        HomeCategory.HORROR -> setOf("horror", "thriller", "thrillers", "suspense", "mystery")
        HomeCategory.FAMILY -> setOf("family", "kids", "kids & family", "children")
        HomeCategory.ROMANCE -> setOf("romance", "romantic", "soap")
        HomeCategory.FANTASY -> setOf("fantasy", "supernatural", "sci-fi & fantasy")
        else -> when (query) {
            "anime", "animation" -> setOf("anime", "animation", "animated")
            "sci-fi", "science fiction", "sci-fi & fantasy" -> setOf("sci-fi", "science fiction", "sci-fi & fantasy")
            "comedy", "comedies" -> setOf("comedy", "comedies")
            "documentary", "documentaries" -> setOf("documentary", "documentaries")
            "kids", "family" -> setOf("family", "kids", "kids & family", "children")
            "fantasy" -> setOf("fantasy", "supernatural", "sci-fi & fantasy")
            "action", "adventure" -> setOf(query, "action & adventure")
            else -> setOf(query)
        }
    }
    return media.genres.any { it.trim().lowercase(Locale.ROOT) in names }
}
