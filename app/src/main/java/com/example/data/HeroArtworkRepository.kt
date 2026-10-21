package com.example.data

import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.network.TmdbImage
import com.example.data.network.TmdbImagesResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

internal data class HeroArtwork(val logoUrl: String?, val portraitUrl: String?) {
    fun applyTo(media: MediaItem): MediaItem = media.copy(
        logoUrl = logoUrl ?: media.logoUrl,
        heroPosterUrl = portraitUrl ?: media.heroPosterUrl
    )
}

/** Fetch only visible heroes. Typed keys, bounded concurrency and caching avoid catalog-wide bursts. */
internal class HeroArtworkRepository(
    private val images: suspend (MediaType, Int) -> TmdbImagesResponse,
    private val now: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private data class Key(val type: MediaType, val id: Int)
    private data class CachedArtwork(val artwork: HeroArtwork, val expiresAt: Long)
    private val cache = object : LinkedHashMap<Key, CachedArtwork>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, CachedArtwork>?) = size > 64
    }
    private val locks = Array(16) { Mutex() }
    private val slots = Semaphore(2)

    suspend fun load(type: MediaType, id: Int): HeroArtwork? {
        if (id <= 0) return null
        val key = Key(type, id)
        return locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            synchronized(cache) { cache[key]?.takeIf { it.expiresAt > now() } }?.let { return@withLock it.artwork }
            val response = try {
                slots.withPermit { withTimeout(8_000L) { images(type, id) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@withLock null }
            val artwork = selectHeroArtwork(response)
            val ttl = if (artwork.logoUrl == null) 10L * 60_000 else 24L * 60 * 60_000
            synchronized(cache) { cache[key] = CachedArtwork(artwork, now() + ttl) }
            artwork
        }
    }
}

internal fun selectHeroArtwork(images: TmdbImagesResponse): HeroArtwork {
    val imagePath = Regex("^/[A-Za-z0-9_.-]+\\.(png|webp|jpe?g)$", RegexOption.IGNORE_CASE)
    fun valid(image: TmdbImage) = image.filePath?.matches(imagePath) == true
    val logos = images.logos.filter(::valid)
    val logo = logos.filter { it.language == "en" }.maxByOrNull { it.width ?: 0 }
        ?: logos.filter { it.language.isNullOrBlank() }.maxByOrNull { it.width ?: 0 }
    val portrait = images.posters.filter { valid(it) && it.language.isNullOrBlank() &&
        (it.width == null || it.height == null || it.height > it.width) }.maxByOrNull { it.width ?: 0 }
    return HeroArtwork(logo?.filePath?.let { "https://image.tmdb.org/t/p/w500$it" },
        portrait?.filePath?.let { "https://image.tmdb.org/t/p/w780$it" })
}

/** Enrich displayed discovery titles as well as the base catalog, without restoring stale metadata. */
internal fun enrichHeroArtwork(items: List<MediaItem>, artwork: List<MediaItem>): List<MediaItem> {
    if (artwork.isEmpty()) return items
    val byTitle = artwork.associateBy { it.type to it.id }
    return items.map { item ->
        byTitle[item.type to item.id]?.let { HeroArtwork(it.logoUrl, it.heroPosterUrl).applyTo(item) } ?: item
    }
}
