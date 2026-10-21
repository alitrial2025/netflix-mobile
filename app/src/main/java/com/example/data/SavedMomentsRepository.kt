package com.example.data

import android.content.Context
import android.util.AtomicFile
import com.example.data.model.SavedMoment
import com.example.data.model.MediaType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class SavedMomentsDocument(val version: Int = 1, val items: List<SavedMoment> = emptyList())

/**
 * Private, account AND profile scoped bookmarks, excluded from Android backup.
 * A bookmark confers no playback entitlement; reopening still uses normal playback admission.
 * Calls are serialized, and observers see changes only after a durable atomic write succeeds.
 */
class SavedMomentsRepository internal constructor(private val directory: File) {
    private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        .adapter(SavedMomentsDocument::class.java)
    private val streams = mutableMapOf<String, MutableStateFlow<List<SavedMoment>>>()
    private val empty = MutableStateFlow<List<SavedMoment>>(emptyList()).asStateFlow()

    @Synchronized
    fun observe(accountUid: String?, profileId: String?): StateFlow<List<SavedMoment>> {
        val key = scopeKey(accountUid, profileId) ?: return empty
        return stream(key).asStateFlow()
    }

    @Synchronized
    fun get(accountUid: String?, profileId: String?): List<SavedMoment> =
        scopeKey(accountUid, profileId)?.let { stream(it).value }.orEmpty()

    /** Returns false for missing scope, invalid input, or storage failure. */
    @Synchronized
    fun save(accountUid: String?, profileId: String?, moment: SavedMoment): Boolean {
        val key = scopeKey(accountUid, profileId) ?: return false
        val safe = sanitize(moment) ?: return false
        val state = stream(key)
        val next = (state.value.filterNot { it.id == safe.id } + safe)
            .sortedByDescending { it.createdAt }.take(MAX_MOMENTS)
        if (!write(key, next)) return false
        state.value = next
        return true
    }

    @Synchronized
    fun remove(accountUid: String?, profileId: String?, momentId: String): Boolean {
        val key = scopeKey(accountUid, profileId) ?: return false
        if (momentId.isBlank()) return false
        val state = stream(key)
        val next = state.value.filterNot { it.id == momentId }
        if (next.size == state.value.size) return true
        if (!write(key, next)) return false
        state.value = next
        return true
    }

    private fun stream(key: String): MutableStateFlow<List<SavedMoment>> =
        streams.getOrPut(key) { MutableStateFlow(read(key)) }

    private fun read(key: String): List<SavedMoment> {
        return try {
            val file = AtomicFile(File(directory, "$key.json"))
            // openRead also recovers a last-good .bak after an interrupted write.
            file.openRead().use { input ->
                val sink = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                while (sink.size() <= MAX_BYTES) {
                    val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - sink.size()))
                    if (count < 0) break
                    sink.write(buffer, 0, count)
                }
                val bytes = sink.toByteArray()
                if (bytes.size > MAX_BYTES) return emptyList()
                val document = adapter.fromJson(bytes.toString(Charsets.UTF_8)) ?: return emptyList()
                if (document.version != 1) return emptyList()
                document.items.mapNotNull(::sanitize).distinctBy { it.id }
                    .sortedByDescending { it.createdAt }.take(MAX_MOMENTS)
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun write(key: String, items: List<SavedMoment>): Boolean {
        val bytes = adapter.toJson(SavedMomentsDocument(items = items)).toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) return false
        if (!directory.isDirectory && !directory.mkdirs()) return false
        val file = AtomicFile(File(directory, "$key.json"))
        val output = try { file.startWrite() } catch (_: Exception) { return false }
        return try {
            output.write(bytes)
            file.finishWrite(output)
            true
        } catch (_: Exception) {
            file.failWrite(output)
            false
        }
    }

    private fun scopeKey(accountUid: String?, profileId: String?): String? {
        if (accountUid.isNullOrBlank() || profileId.isNullOrBlank() ||
            accountUid.length > 256 || profileId.length > 256) return null
        // Length framing avoids collisions such as (a:b,c) and (a,b:c); raw IDs stay off disk.
        val scope = "${accountUid.length}:$accountUid${profileId.length}:$profileId"
        return MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun sanitize(moment: SavedMoment): SavedMoment? {
        val media = moment.media
        if (moment.id.isBlank() || moment.id.length > 512 || media.id.isBlank() || media.id.length > 512 ||
            media.title.isBlank() || moment.positionSeconds !in 0..MAX_POSITION_SECONDS ||
            moment.durationSeconds !in 1..120 || moment.createdAt <= 0 ||
            (moment.episodeId != null && (moment.episodeId.isBlank() || moment.episodeId.length > 512)) ||
            (moment.episodeNumber != null && moment.episodeNumber !in 1..100_000) ||
            (moment.seasonNumber != null && moment.seasonNumber !in 0..10_000)) return null
        if (moment.titleDurationSeconds != null && (moment.titleDurationSeconds <= 0 ||
            moment.positionSeconds >= moment.titleDurationSeconds ||
            moment.durationSeconds > moment.titleDurationSeconds - moment.positionSeconds)) return null
        if (media.type == MediaType.TV_SHOW && (moment.episodeId == null ||
            moment.episodeNumber == null || moment.seasonNumber == null)) return null
        return moment.copy(episodeTitle = moment.episodeTitle?.take(1_000), media = media.copy(
            title = media.title.take(1_000), description = media.description.take(8_000),
            tagline = media.tagline.take(1_000), maturityRating = media.maturityRating.take(50),
            durationOrSeasons = media.durationOrSeasons.take(100), director = media.director.take(1_000),
            genres = media.genres.take(30).map { it.take(100) },
            cast = media.cast.take(50).map { it.take(200) },
            releaseDateBadge = media.releaseDateBadge?.take(100), releaseDate = media.releaseDate?.take(50),
            posterUrl = publicArtwork(media.posterUrl), backdropUrl = publicArtwork(media.backdropUrl),
            logoUrl = publicArtwork(media.logoUrl), heroPosterUrl = publicArtwork(media.heroPosterUrl),
            episodes = emptyList(), similarMedia = emptyList(),
            genreIds = media.genreIds.take(100), bannerDrawableRes = null,
            voteAverage = media.voteAverage.takeIf { it.isFinite() } ?: 0.0,
            popularity = media.popularity.takeIf { it.isFinite() } ?: 0.0
        ))
    }

    /** Persist only public TMDB images, never signed CDN links, media segments, query tokens or headers. */
    private fun publicArtwork(value: String?): String? = value?.takeIf { it.length <= 2_048 }?.let { raw ->
        runCatching { URI(raw) }.getOrNull()?.takeIf { uri ->
            uri.scheme == "https" && uri.host.equals("image.tmdb.org", ignoreCase = true) &&
                uri.port == -1 && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                uri.path.startsWith("/t/p/") &&
                Regex("(?i).+\\.(jpg|jpeg|png|webp)$").matches(uri.path)
        }?.toString()
    }

    companion object {
        const val MAX_MOMENTS = 100
        private const val MAX_POSITION_SECONDS = 7 * 24 * 60 * 60
        private const val MAX_BYTES = 2 * 1024 * 1024
        @Volatile private var instance: SavedMomentsRepository? = null

        fun get(context: Context): SavedMomentsRepository = instance ?: synchronized(this) {
            instance ?: SavedMomentsRepository(File(context.applicationContext.noBackupFilesDir, "saved-moments"))
                .also { instance = it }
        }
    }
}
