package com.example.ui.viewmodel

import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import org.junit.Assert.*
import org.junit.Test

class CatalogFetchPolicyTest {
    @Test fun offlineChildDoesNotCancelStartupOrSuccessfulSibling() = runBlocking {
        val offline = async { requestCatalogSection<String> { throw UnknownHostException("offline") } }
        val cachedSection = async { requestCatalogSection { listOf("cached title") } }
        assertNull(offline.await())
        assertEquals(listOf("cached title"), cachedSection.await())
        assertTrue(currentCoroutineContext().isActive)
    }

    @Test fun cancellationStillStopsTheRequest() = runBlocking {
        val request = async { requestCatalogSection<String> { throw CancellationException("screen closed") } }
        try { request.await(); fail("Cancellation was swallowed") } catch (_: CancellationException) { }
        assertTrue(request.isCancelled)
    }

    @Test fun publishesHomeBeforeASlowGenreRequestFinishes() = runBlocking {
        val published = CompletableDeferred<List<String>>()
        val finishGenre = CompletableDeferred<Unit>()
        val result = async {
            fetchCatalogInStages(listOf({ listOf("trending") }), listOf({ finishGenre.await(); listOf("comedy") })) {
                published.complete(it)
            }
        }
        assertEquals(listOf("trending"), withTimeout(5000) { published.await() })
        assertFalse(result.isCompleted)
        finishGenre.complete(Unit)
        assertEquals(listOf("trending", "comedy"), result.await())
    }

    @Test fun failedGenreDoesNotEraseSuccessfulSections() = runBlocking {
        val result = fetchCatalogInStages(listOf({ listOf("primary") }),
            listOf({ throw UnknownHostException("offline") }, { listOf("secondary") })) {}
        assertEquals(listOf("primary", "secondary"), result)
    }

    @Test fun cancellingTheRefreshCancelsItsBlockedRequests() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val request = launch {
            fetchCatalogInStages<String>(emptyList(), listOf({
                started.complete(Unit)
                try { CompletableDeferred<Unit>().await(); emptyList() } finally { cancelled = true }
            })) {}
        }
        withTimeout(5000) { started.await() }
        request.cancelAndJoin()
        assertTrue(cancelled)
    }

    @Test fun discoveryConcurrencyIsBoundedToFourRequests() = runBlocking {
        val active = java.util.concurrent.atomic.AtomicInteger()
        val peak = java.util.concurrent.atomic.AtomicInteger()
        val fourStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val requests = (1..12).map { number -> suspend {
            val count = active.incrementAndGet()
            peak.updateAndGet { maxOf(it, count) }
            if (count == 4) fourStarted.complete(Unit)
            try { release.await(); listOf(number) } finally { active.decrementAndGet() }
        } }
        val result = async { fetchCatalogInStages(emptyList(), requests) {} }
        withTimeout(5000) { fourStarted.await() }
        assertEquals(4, peak.get())
        release.complete(Unit)
        assertEquals((1..12).toList(), result.await())
        assertEquals(4, peak.get())
    }

    @Test fun aTrendingTitleKeepsItsTopTenRankWhenResponsesOverlap() {
        val trending = MediaItem("42", "Title", MediaType.MOVIE, "", "", 90, "16+", 2026, "2h",
            genres = emptyList(), cast = emptyList(), director = "", isTrending = true)
        val ranked = trending.copy(isTrending = false, top10Rank = 3)
        val merged = mergeCatalogSections(listOf(trending, ranked, trending)).single()
        assertTrue(merged.isTrending)
        assertEquals(3, merged.top10Rank)
    }
}
