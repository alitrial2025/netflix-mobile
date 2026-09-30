package com.example.ui

import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.components.featuredTrendingMedia
import com.example.ui.components.posterModel
import org.junit.Assert.*
import org.junit.Test

class FeaturedArtworkPolicyTest {
    private val media = MediaItem("42", "Title", MediaType.TV_SHOW, "", "", 90, "13+", 2026, "1 Season",
        genres = emptyList(), cast = emptyList(), director = "")

    @Test fun trendingTmdbPosterWinsOverBundledArtworkAndNonTrendingTitles() {
        val trending = media.copy(id = "trending", isTrending = true, posterUrl = "https://image.tmdb.org/poster.jpg", bannerDrawableRes = 123)
        assertEquals(trending, featuredTrendingMedia(listOf(media.copy(posterUrl = "other"), trending)))
        assertEquals(trending.posterUrl, posterModel(trending))
    }

    @Test fun bundledArtworkIsNotUsedWhenTmdbArtworkIsMissing() {
        assertNull(posterModel(media.copy(bannerDrawableRes = 123)))
        assertNull(featuredTrendingMedia(listOf(media)))
    }
}
