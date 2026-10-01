package com.example.data.repository

import com.example.data.CatalogData
import com.example.data.local.DownloadEntity
import com.example.data.local.NetflixDao
import com.example.data.local.RatingEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.model.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NetflixRepository(private val dao: NetflixDao) {

    fun getWatchlistEntries(profileId: String): Flow<List<WatchlistEntity>> = dao.getWatchlist(profileId)

    suspend fun getWatchlistEntriesOnce(profileId: String): List<WatchlistEntity> =
        dao.getWatchlistOnce(profileId)

    fun getWatchlistItems(profileId: String): Flow<List<MediaItem>> = dao.getWatchlist(profileId).map { entities ->
        entities.mapNotNull { entity ->
            CatalogData.getById(entity.mediaId)
        }
    }

    fun getContinueWatchingList(profileId: String): Flow<List<Pair<MediaItem, WatchProgressEntity>>> = dao.getAllProgress(profileId).map { progressList ->
        progressList.groupBy { it.mediaId }.mapNotNull { (mediaId, list) ->
            val latestProgress = list.maxByOrNull { it.lastWatchedTimestamp } ?: return@mapNotNull null
            val media = CatalogData.getById(mediaId)
            if (media != null) Pair(media, latestProgress) else null
        }
    }

    fun getAllProgress(profileId: String): Flow<List<WatchProgressEntity>> = dao.getAllProgress(profileId)

    fun getDownloads(profileId: String): Flow<List<DownloadEntity>> = dao.getAllDownloads(profileId)

    suspend fun getDownloadsOnce(profileId: String): List<DownloadEntity> = dao.getAllDownloadsOnce(profileId)

    val reminders: Flow<List<ReminderEntity>> = dao.getAllReminders()

    fun getLikedItems(profileId: String): Flow<List<MediaItem>> = dao.getLikedRatings(profileId).map { entities ->
        entities.mapNotNull { entity ->
            CatalogData.getById(entity.mediaId)
        }
    }

    fun isInWatchlist(profileId: String, mediaId: String): Flow<Boolean> = dao.isInWatchlist(profileId, mediaId)

    fun getProgress(profileId: String, mediaId: String): Flow<WatchProgressEntity?> = dao.getProgress(profileId, mediaId)

    fun getRating(profileId: String, mediaId: String): Flow<RatingEntity?> = dao.getRating(profileId, mediaId)

    suspend fun isInWatchlistOnce(profileId: String, mediaId: String): Boolean =
        dao.isInWatchlistOnce(profileId, mediaId)

    suspend fun applyRemoteMyListChange(profileId: String, mediaId: String, isAdded: Boolean, addedAt: Long) {
        if (isAdded) {
            dao.addToWatchlist(WatchlistEntity(profileId = profileId, mediaId = mediaId, addedTimestamp = addedAt))
        } else {
            dao.removeFromWatchlist(profileId, mediaId)
        }
    }

    suspend fun toggleWatchlist(profileId: String, mediaId: String, currentInWatchlist: Boolean) {
        if (currentInWatchlist) {
            dao.removeFromWatchlist(profileId, mediaId)
        } else {
            dao.addToWatchlist(WatchlistEntity(profileId = profileId, mediaId = mediaId))
        }
    }

    suspend fun saveProgress(
        profileId: String,
        mediaId: String,
        positionSec: Int,
        totalSec: Int,
        episodeId: String?,
        episodeTitle: String?,
        season: Int = 1,
        episode: Int = 1
    ) {
        dao.saveProgress(
            WatchProgressEntity(
                profileId = profileId,
                mediaId = mediaId,
                episodeId = episodeId ?: "",
                positionSeconds = positionSec,
                totalSeconds = totalSec,
                episodeTitle = episodeTitle,
                season = season,
                episode = episode,
                lastWatchedTimestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeProgress(profileId: String, mediaId: String) {
        dao.removeProgress(profileId, mediaId)
    }

    suspend fun upsertRemoteProgressIfNewer(progress: WatchProgressEntity) {
        val existing = dao.getProgressOnce(progress.profileId, progress.mediaId)
        if (existing == null || progress.lastWatchedTimestamp > existing.lastWatchedTimestamp) {
            dao.saveProgress(progress)
        }
    }

    suspend fun clearAccountData() {
        dao.clearWatchlist()
        dao.clearProgress()
        dao.clearRatings()
        dao.clearAllGlobalDownloads()
        dao.clearReminders()
        dao.clearProfiles()
    }

    suspend fun addDownload(
        profileId: String,
        mediaId: String,
        mediaTitle: String,
        episodeTitle: String?,
        sizeMb: Int,
        episodeId: String? = null,
        localFilePath: String? = null,
        videoUrl: String? = null,
        captionsJson: String? = null,
        isForYou: Boolean = false
    ) {
        val downloadKey = if (episodeId != null) "${mediaId}_$episodeId" else mediaId
        dao.saveDownload(
            DownloadEntity(
                profileId = profileId,
                downloadKey = downloadKey,
                mediaId = mediaId,
                mediaTitle = mediaTitle,
                episodeTitle = episodeTitle,
                fileSizeMb = sizeMb,
                localFilePath = localFilePath,
                videoUrl = videoUrl,
                captionsJson = captionsJson,
                isForYou = isForYou
            )
        )
    }

    suspend fun removeDownload(profileId: String, downloadKey: String) {
        dao.deleteDownload(profileId, downloadKey)
    }

    suspend fun isDownloadReferenced(downloadKey: String): Boolean = dao.downloadReferenceCount(downloadKey) > 0

    suspend fun clearDownloads(profileId: String) {
        dao.clearAllDownloads(profileId)
    }

    suspend fun clearAllGlobalDownloads() {
        dao.clearAllGlobalDownloads()
    }

    suspend fun setRating(profileId: String, mediaId: String, ratingType: String) {
        dao.setRating(RatingEntity(profileId = profileId, mediaId = mediaId, ratingType = ratingType))
    }

    suspend fun clearRating(profileId: String, mediaId: String) {
        dao.clearRating(profileId, mediaId)
    }

    suspend fun toggleReminder(mediaId: String, isReminded: Boolean) {
        if (isReminded) {
            dao.removeReminder(mediaId)
        } else {
            dao.setReminder(ReminderEntity(mediaId = mediaId))
        }
    }

    // Profiles
    fun getAllProfiles(): Flow<List<com.example.data.local.ProfileEntity>> = dao.getAllProfiles()

    suspend fun getAllProfilesList(): List<com.example.data.local.ProfileEntity> = dao.getAllProfilesList()

    suspend fun insertProfiles(profiles: List<com.example.data.local.ProfileEntity>) {
        dao.insertProfiles(profiles)
    }

    suspend fun insertProfile(profile: com.example.data.local.ProfileEntity) {
        dao.insertProfile(profile)
    }

    suspend fun deleteProfile(profileId: String) {
        dao.deleteProfile(profileId)
    }

    suspend fun clearProfiles() {
        dao.clearProfiles()
    }
}
