package com.example.data

import com.example.data.model.MediaType
import com.example.data.network.TmdbImage
import com.example.data.network.TmdbImagesResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HeroArtworkRepositoryTest {
    private fun response(path: String = "/english.png") = TmdbImagesResponse(logos = listOf(
        TmdbImage(path, width = 600, language = "en")))

    @Test fun selectsValidEnglishLogosAndTextlessPortraitsWithoutUsingForeignWordmarks() {
        val result = selectHeroArtwork(TmdbImagesResponse(logos = listOf(
            TmdbImage("", language = "en"), TmdbImage("//wrong.png", language = "en"),
            TmdbImage("/foreign.png", language = "ja", width = 2000),
            TmdbImage("/neutral.png", width = 1000), TmdbImage("/english.png", language = "en", width = 500)),
            posters = listOf(TmdbImage("/titled.jpg", language = "en", width = 2000, height = 3000),
                TmdbImage("/textless.jpg", width = 780, height = 1170))))
        assertEquals("https://image.tmdb.org/t/p/w500/english.png", result.logoUrl)
        assertEquals("https://image.tmdb.org/t/p/w780/textless.jpg", result.portraitUrl)
        assertNull(selectHeroArtwork(response().copy(logos = listOf(TmdbImage("/foreign.png", language = "ja")))).logoUrl)
    }

    @Test fun simultaneousRequestsShareOneLookupButMoviesAndSeriesUseIndependentKeys() = runTest {
        val calls = mutableListOf<MediaType>()
        val repository = HeroArtworkRepository(images = { type, _ -> calls += type; delay(25); response("/$type.png") })
        val results = listOf(async { repository.load(MediaType.MOVIE, 42) },
            async { repository.load(MediaType.MOVIE, 42) }, async { repository.load(MediaType.TV_SHOW, 42) }).awaitAll()
        assertEquals(2, calls.size)
        assertEquals(results[0], results[1])
        assertNotEquals(results[0]?.logoUrl, results[2]?.logoUrl)
        assertEquals(results[0], repository.load(MediaType.MOVIE, 42))
        assertEquals(2, calls.size)
    }

    @Test fun absentLogosAreCachedBrieflyAndDoNotCauseARequestLoop() = runTest {
        var calls = 0
        var time = 0L
        val repository = HeroArtworkRepository(images = { _, _ -> calls++; TmdbImagesResponse() }, now = { time })
        repeat(10) { assertNull(repository.load(MediaType.TV_SHOW, 42)?.logoUrl) }
        assertEquals(1, calls)
        time = 10 * 60_000L + 1
        repository.load(MediaType.TV_SHOW, 42)
        assertEquals(2, calls)
    }

    @Test fun aNetworkFailureCanRecoverInsteadOfPoisoningTheArtworkCache() = runTest {
        var calls = 0
        val repository = HeroArtworkRepository(images = { _, _ ->
            if (++calls == 1) throw java.io.IOException("offline")
            response()
        })
        assertNull(repository.load(MediaType.TV_SHOW, 42))
        assertNotNull(repository.load(MediaType.TV_SHOW, 42)?.logoUrl)
        assertEquals(2, calls)
    }

    @Test fun cancellationPropagatesAndDoesNotBlockLaterLookups() = runTest {
        var calls = 0
        val repository = HeroArtworkRepository(images = { _, _ ->
            if (++calls == 1) throw CancellationException("hero changed")
            response()
        })
        assertTrue(runCatching { repository.load(MediaType.TV_SHOW, 42) }.exceptionOrNull() is CancellationException)
        assertNotNull(repository.load(MediaType.TV_SHOW, 42)?.logoUrl)
    }
    @Test fun discoveryOnlyHeroesReceiveLogosWithoutReplacingFreshMetadataOrAnotherMediaType() {
        val show = com.example.data.model.MediaItem("42", "Fresh title", MediaType.TV_SHOW, "Fresh synopsis", "", 90,
            "13+", 2026, "2 Seasons", genres = emptyList(), cast = emptyList(), director = "")
        val movie = show.copy(type = MediaType.MOVIE)
        val cached = show.copy(title = "Old title", description = "Old synopsis", logoUrl = "logo.png", heroPosterUrl = "textless.jpg")
        val result = enrichHeroArtwork(listOf(show, movie), listOf(cached))
        assertEquals("logo.png", result[0].logoUrl)
        assertEquals("textless.jpg", result[0].heroPosterUrl)
        assertEquals(show.title, result[0].title)
        assertEquals(show.description, result[0].description)
        assertEquals(movie, result[1])
    }

}
