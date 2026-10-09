package com.example.ui.screens

import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.UserProfile
import com.example.ui.viewmodel.CategoryFilter
import org.junit.Assert.*
import org.junit.Test

class BrowseCatalogTest {
    private val adult = UserProfile("adult", "Alex")
    private fun title(id: String, type: MediaType = MediaType.MOVIE, genre: String = "Drama", rating: String = "16+",
        upcoming: Boolean = false) = MediaItem(id, "Title $id", type, "", "", 90, rating, 2026, "2h",
        genres = listOf(genre), cast = emptyList(), director = "", isTrending = true, isComingSoon = upcoming)

    @Test fun emptyGenreDoesNotFallBackToOtherTitles() {
        val home = buildHomeCatalog(listOf(title("drama")), emptyList(), CategoryFilter.CATEGORIES, "Comedy", adult)
        assertNull(home.hero)
        assertTrue(home.sections.isEmpty())
    }

    @Test fun mediaTypeFilterAppliesToHeroPersonalizedAndEveryRow() {
        val movie = title("movie")
        val show = title("show", MediaType.TV_SHOW)
        val home = buildHomeCatalog(listOf(movie, show), listOf(show, movie), CategoryFilter.MOVIES, null, adult)
        assertEquals(movie, home.hero)
        assertTrue(home.sections.isNotEmpty())
        assertTrue(home.sections.flatMap { it.items }.all { it.type == MediaType.MOVIE })
        assertFalse(matchesHomeFilter(show, CategoryFilter.MOVIES, null, adult))
    }

    @Test fun upcomingOnlyCatalogStillOffersReminderRowsWithoutAPlayableHero() {
        val upcoming = title("future", upcoming = true)
        val home = buildHomeCatalog(listOf(upcoming), emptyList(), CategoryFilter.ALL, null, adult)
        assertNull(home.hero)
        assertEquals(listOf(upcoming), home.sections.first { it.id == "section_coming_soon" }.items)
    }

    @Test fun restrictedProfileCannotUseMatureCatalogOrPersonalizedTitles() {
        val child = UserProfile("kid", "Kids", isKids = true)
        val safe = title("safe", rating = "PG")
        val mature = title("mature", rating = "18+")
        val home = buildHomeCatalog(listOf(mature, safe), listOf(mature), CategoryFilter.ALL, null, child)
        assertEquals(safe, home.hero)
        assertTrue(home.sections.flatMap { it.items }.all { it.id == safe.id })
    }

    @Test fun clipsRefreshesFromItsInputsAndKeepsBothTypesWithTheSameId() {
        val movie = title("42")
        val show = movie.copy(type = MediaType.TV_SHOW)
        assertTrue(buildClipsCatalog(emptyList(), emptyList(), adult).feed.isEmpty())
        val clips = buildClipsCatalog(listOf(movie, show, movie), emptyList(), adult)
        assertEquals(listOf(movie, show), clips.feed)
    }

    @Test fun clipsFiltersEveryModeAndDeduplicatesOverlappingUpcomingFeeds() {
        val child = UserProfile("kid", "Kids", isKids = true)
        val safe = title("safe", rating = "PG", upcoming = true)
        val mature = title("mature", rating = "18+").copy(top10Rank = 1)
        val clips = buildClipsCatalog(listOf(mature, safe), listOf(safe, mature.copy(isComingSoon = true)), child)
        assertEquals(listOf(safe), clips.feed)
        assertEquals(listOf(safe), clips.upcoming)
        assertTrue(clips.rankedShows.isEmpty())
        assertTrue(clips.rankedMovies.isEmpty())
    }

    @Test fun combinedMovieAndSeriesRanksNeverRenderMoreThanTenCardsInATopTenRow() {
        val ranked = (1..10).flatMap { rank ->
            listOf(title("movie$rank"), title("show$rank", MediaType.TV_SHOW)).map { it.copy(top10Rank = rank) }
        }
        val home = buildHomeCatalog(ranked, emptyList(), CategoryFilter.ALL, null, adult)
        assertEquals(10, home.sections.first { it.isTop10 }.items.size)
    }
}
