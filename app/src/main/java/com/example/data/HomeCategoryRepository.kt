package com.example.data

import com.example.data.model.HomeCategory
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.discovery.ReleasePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

internal data class CachedHomeCategory(val items: List<MediaItem>, val savedAt: Long)
data class HomeCategoryState(
    val category: HomeCategory? = null, val items: List<MediaItem> = emptyList(),
    val loading: Boolean = false, val message: String? = null
)

internal fun homeCategoryParameters(category: HomeCategory, type: MediaType, page: Int, today: String): Map<String, String> {
    val movie = type == MediaType.MOVIE
    return buildMap {
        put("page", page.toString()); put("language", "en-US"); put("include_adult", "false")
        put("sort_by", "popularity.desc")
        put(if (movie) "primary_release_date.lte" else "first_air_date.lte", today)
        if (!movie) put("include_null_first_air_dates", "false")
        if (category.isService) {
            put("with_watch_providers", category.providerIds)
            put("watch_region", category.region)
            put("with_watch_monetization_types", "flatrate")
        } else {
            val genres = if (movie) category.movieGenres else category.tvGenres
            if (genres.isNotEmpty()) put("with_genres", genres)
            if (!movie && category.tvKeyword.isNotEmpty()) put("with_keywords", category.tvKeyword)
            if (category.originalLanguage.isNotEmpty()) put("with_original_language", category.originalLanguage)
        }
    }
}

/** Real provider/genre discovery, no title-word guesses; only the selected scope is requested. */
internal class HomeCategoryRepository(
    private val fetch: suspend (MediaType, Map<String, String>) -> List<MediaItem>,
    private val readCache: suspend (HomeCategory) -> CachedHomeCategory? = { null },
    private val writeCache: suspend (HomeCategory, List<MediaItem>) -> Unit = { _, _ -> },
    private val now: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val slots = Semaphore(2)
    private val memory = object : LinkedHashMap<HomeCategory, CachedHomeCategory>(18, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<HomeCategory, CachedHomeCategory>?) = size > 18
    }
    suspend fun load(category: HomeCategory, online: Boolean, force: Boolean = false): HomeCategoryState = mutex.withLock {
        val cached = memory[category] ?: readCache(category)?.also { memory[category] = it }
        if (!online) return@withLock HomeCategoryState(category, cached?.items.orEmpty(), message =
            if (cached == null) "Connect to load ${category.label} titles." else "Showing saved ${category.label} titles. Connect to refresh.")
        if (!force && cached != null && now() - cached.savedAt in 0 until 6L * 60 * 60_000) {
            return@withLock HomeCategoryState(category, cached.items)
        }
        try {
            val today = ReleasePolicy.day(now())
            val items = withTimeout(28_000L) {
                coroutineScope {
                    MediaType.entries.flatMap { type -> (1..2).map { page -> async {
                        slots.withPermit { withTimeout(12_000L) { fetch(type, homeCategoryParameters(category, type, page, today)) } }
                    } } }.awaitAll().flatten().distinctBy { it.type to it.id }
                }
            }
            memory[category] = CachedHomeCategory(items, now())
            // A disk failure should not hide an already-fetched category.
            try { writeCache(category, items) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {}
            HomeCategoryState(category, items)
        } catch (cancelled: kotlinx.coroutines.TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            HomeCategoryState(category, cached?.items.orEmpty(), message = "${category.label} took too long to load. Try again.")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            HomeCategoryState(category, cached?.items.orEmpty(), message =
                if (cached == null) "Couldn't load ${category.label}. Try again." else "Showing saved ${category.label} titles. Couldn't refresh; try again.")
        }
    }
}
