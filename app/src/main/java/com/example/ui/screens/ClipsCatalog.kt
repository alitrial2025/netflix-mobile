package com.example.ui.screens

import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.UserProfile

internal data class ClipsCatalog(
    val feed: List<MediaItem>, val upcoming: List<MediaItem>,
    val rankedShows: List<MediaItem>, val rankedMovies: List<MediaItem>
)

/** Use observable, profile-scoped inputs; never fall back to the process-wide catalog. */
internal fun buildClipsCatalog(catalog: List<MediaItem>, upcoming: List<MediaItem>, profile: UserProfile): ClipsCatalog {
    fun visible(items: List<MediaItem>) = items
        .filter { !profile.hasMaturityRestriction || it.isKidSafe(profile.contentMaxAge) }
        .distinctBy { it.type to it.id }
    val titles = visible(catalog)
    val comingSoon = visible(titles.filter { it.isComingSoon } + upcoming)
    val ranked = titles.filter { !it.isComingSoon && it.top10Rank != null }.sortedBy { it.top10Rank }
    return ClipsCatalog(visible(titles.filter { it.isTrending && !it.isComingSoon } + comingSoon), comingSoon,
        ranked.filter { it.type == MediaType.TV_SHOW }.take(10), ranked.filter { it.type == MediaType.MOVIE }.take(10))
}
