package com.example.ui.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.example.data.model.MediaItem

/** Catch inside the child: a failing async request must not cancel startup's parent job. */
internal suspend fun <T> requestCatalogSection(block: suspend () -> T): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

/** Publish the browsing catalog before slower genre requests occupy network slots. */
internal suspend fun <T> fetchCatalogInStages(
    primary: List<suspend () -> List<T>>,
    secondary: List<suspend () -> List<T>>,
    onPrimary: suspend (List<T>) -> Unit
): List<T> = coroutineScope {
    val slots = Semaphore(4)
    suspend fun fetch(requests: List<suspend () -> List<T>>): List<T> = requests.map { request ->
        async { slots.withPermit { requestCatalogSection(request).orEmpty() } }
    }.awaitAll().flatten()
    val first = fetch(primary)
    onPrimary(first)
    first + fetch(secondary)
}

/** Retain ranking flags when trending, ranked, and genre endpoints return the same title. */
internal fun mergeCatalogSections(items: List<MediaItem>): List<MediaItem> {
    val merged = linkedMapOf<String, MediaItem>()
    for (item in items) {
        val previous = merged[item.id]
        // Room's existing keys are numeric IDs; a cross-type storage migration is separate.
        merged[item.id] = if (previous == null) item else if (previous.type == item.type) previous.copy(
            isTrending = previous.isTrending || item.isTrending,
            top10Rank = previous.top10Rank ?: item.top10Rank,
            logoUrl = previous.logoUrl ?: item.logoUrl
        ) else previous
    }
    return merged.values.toList()
}
