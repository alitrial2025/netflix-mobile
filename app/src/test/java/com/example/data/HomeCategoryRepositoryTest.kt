package com.example.data

import com.example.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HomeCategoryRepositoryTest {
    private val item = MediaItem("42", "Title", MediaType.MOVIE, "Not genre evidence: comedy romance", "", 90,
        "18+", 2026, "2h", genres = emptyList(), cast = emptyList(), director = "")

    @Test fun allDropdownGenresUseMetadataInsteadOfSynopsisWords() {
        val examples = mapOf(HomeCategory.ACTION to 28, HomeCategory.CRIME to 80, HomeCategory.SCIENCE_FICTION to 878,
            HomeCategory.DRAMA to 18, HomeCategory.ANIMATION to 16, HomeCategory.COMEDY to 35,
            HomeCategory.DOCUMENTARY to 99, HomeCategory.HORROR to 27, HomeCategory.FAMILY to 10751,
            HomeCategory.ROMANCE to 10749, HomeCategory.FANTASY to 14)
        examples.forEach { (category, id) ->
            assertTrue(category.label, item.copy(genreIds = listOf(id)).matchesGenre(category.label))
            assertFalse(category.label, item.copy(genreIds = listOf(10402)).matchesGenre(category.label))
        }
        assertFalse(item.matchesGenre("Romance"))
        assertFalse(item.copy(genreIds = listOf(16)).matchesGenre(HomeCategory.FAMILY.label))
        assertFalse(item.copy(genreIds = listOf(878)).matchesGenre(HomeCategory.FANTASY.label))
        assertTrue(item.copy(genres = listOf("Science Fiction")).matchesGenre("Sci-Fi"))
        assertTrue(item.copy(genreIds = listOf(10759), type = MediaType.TV_SHOW).matchesGenre(HomeCategory.ACTION.label))
        assertFalse(item.copy(title = "Netflix Original").matchesGenre("Netflix"))
    }

    @Test fun internationalDoesNotAccidentallySelectEveryDramaAndKoreanRailsRemainKorean() {
        assertFalse(item.copy(genreIds = listOf(18), originalLanguage = "en").matchesGenre(HomeCategory.INTERNATIONAL.label))
        assertTrue(item.copy(originalLanguage = "ko").matchesGenre(HomeCategory.INTERNATIONAL.label))
        assertTrue(item.copy(originalLanguage = "es").matchesGenre(HomeCategory.INTERNATIONAL.label))
        assertFalse(item.copy(originalLanguage = "es").matchesGenre("K-Dramas"))
    }

    @Test fun providerQueriesUseSubscriptionCatalogsAndCorrectRegionsForBothMediaTypes() {
        val providers = listOf("8|1796|175", "9|119|2100", "337", "2336|122", "350", "386|387")
        HomeCategory.services.zip(providers).forEach { (category, ids) -> MediaType.entries.forEach { type ->
            val parameters = homeCategoryParameters(category, type, 2, "2026-10-21")
            assertEquals(ids, parameters["with_watch_providers"])
            assertEquals(if (category == HomeCategory.HOTSTAR) "IN" else "US", parameters["watch_region"])
            assertEquals("flatrate", parameters["with_watch_monetization_types"])
            assertEquals("2", parameters["page"])
            assertEquals("2026-10-21", parameters[if (type == MediaType.MOVIE) "primary_release_date.lte" else "first_air_date.lte"])
            assertFalse(parameters.containsKey("with_genres"))
        } }
        assertEquals("9840", homeCategoryParameters(HomeCategory.ROMANCE, MediaType.TV_SHOW, 1, "2026-10-21")["with_keywords"])
        assertEquals("10749", homeCategoryParameters(HomeCategory.ROMANCE, MediaType.MOVIE, 1, "2026-10-21")["with_genres"])
        assertEquals("ko", homeCategoryParameters(HomeCategory.INTERNATIONAL, MediaType.TV_SHOW, 1, "2026-10-21")["with_original_language"])
    }

    @Test fun requestsAreBoundedDeduplicatedAndCachedByProviderAndMediaType() = runTest {
        var active = 0; var peak = 0; var calls = 0
        val repository = HomeCategoryRepository(fetch = { type, _ ->
            calls++; active++; peak = maxOf(peak, active); delay(30); active--
            listOf(item.copy(type = type))
        })
        val netflix = repository.load(HomeCategory.NETFLIX, true)
        assertEquals(2, netflix.items.size); assertEquals(4, calls); assertEquals(2, peak)
        assertEquals(netflix, repository.load(HomeCategory.NETFLIX, true)); assertEquals(4, calls)
        repository.load(HomeCategory.APPLE, true); assertEquals(8, calls)
        assertTrue(repository.load(HomeCategory.PEACOCK, false).items.isEmpty())
        assertEquals(netflix.items, repository.load(HomeCategory.NETFLIX, false).items)
    }

    @Test fun refreshErrorsPreserveOnlyTheSelectedCacheAndForcedEmptyResultsReplaceIt() = runTest {
        var fail = false; var empty = false
        val saved = mutableMapOf<HomeCategory, List<MediaItem>>()
        val repository = HomeCategoryRepository(fetch = { _, _ ->
            if (fail) throw java.io.IOException("offline")
            if (empty) emptyList() else listOf(item)
        }, writeCache = { category, items -> saved[category] = items })
        repository.load(HomeCategory.NETFLIX, true)
        fail = true
        assertEquals(listOf(item), repository.load(HomeCategory.NETFLIX, true, true).items)
        assertTrue(repository.load(HomeCategory.DISNEY, true).items.isEmpty())
        fail = false; empty = true
        assertTrue(repository.load(HomeCategory.NETFLIX, true, true).items.isEmpty())
        assertEquals(emptyList<MediaItem>(), saved[HomeCategory.NETFLIX])
        assertTrue(repository.load(HomeCategory.NETFLIX, false).items.isEmpty())
    }

    @Test fun diskCacheSurvivesRestartAndMissingOfflineScopeNeverFetches() = runTest {
        var calls = 0
        val repository = HomeCategoryRepository(fetch = { _, _ -> calls++; emptyList() },
            readCache = { if (it == HomeCategory.HOTSTAR) CachedHomeCategory(listOf(item), 100) else null }, now = { 101 })
        assertEquals(listOf(item), repository.load(HomeCategory.HOTSTAR, false).items)
        assertTrue(repository.load(HomeCategory.APPLE, false).items.isEmpty()); assertEquals(0, calls)
        assertEquals(listOf(item), repository.load(HomeCategory.HOTSTAR, true).items); assertEquals(0, calls)
    }

    @Test fun selectionCancellationAndParentTimeoutPropagateAndReleaseTheRequestLock() = runTest {
        var slow = true
        val repository = HomeCategoryRepository(fetch = { _, _ -> if (slow) delay(60_000); listOf(item) })
        assertNull(withTimeoutOrNull(20) { repository.load(HomeCategory.NETFLIX, true) })
        slow = false
        assertEquals(listOf(item), repository.load(HomeCategory.DISNEY, true).items)
        val cancelled = HomeCategoryRepository(fetch = { _, _ -> throw CancellationException("selection changed") })
        assertTrue(runCatching { cancelled.load(HomeCategory.APPLE, true) }.exceptionOrNull() is CancellationException)
    }
}
