package com.example.ui.components

import com.example.data.model.MediaItem

/** Trending artwork stays stable while logo metadata arrives asynchronously. */
internal fun featuredTrendingMedia(items: List<MediaItem>): MediaItem? =
    items.firstOrNull { it.isTrending && !it.isComingSoon && !it.posterUrl.isNullOrBlank() }
        ?: items.firstOrNull { it.isTrending && !it.isComingSoon && !it.backdropUrl.isNullOrBlank() }

