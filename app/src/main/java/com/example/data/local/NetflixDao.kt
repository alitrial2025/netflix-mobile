package com.example.data.local

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "watchlist",
    primaryKeys = ["profileId", "mediaId"]
)
data class WatchlistEntity(
    val profileId: String = "p4",
    val mediaId: String,
    val addedTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playback_progress",
    primaryKeys = ["profileId", "mediaId", "episodeId"]
)
data class WatchProgressEntity(
    val profileId: String = "p4",
    val mediaId: String,
    val episodeId: String = "",
    val positionSeconds: Int,
    val totalSeconds: Int,
    val episodeTitle: String? = null,
    val season: Int = 1,
    val episode: Int = 1,
    val lastWatchedTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "downloads",
    primaryKeys = ["profileId", "downloadKey"]
)
data class DownloadEntity(
    val profileId: String = "p4",
    val downloadKey: String, // mediaId or mediaId_episodeId
    val mediaId: String,
    val mediaTitle: String,
    val episodeTitle: String?,
    val fileSizeMb: Int,
    val isComplete: Boolean = true,
    val downloadedAt: Long = System.currentTimeMillis(),
    val localFilePath: String? = null,
    val videoUrl: String? = null,
    val captionsJson: String? = null,
    @ColumnInfo(defaultValue = "0") val isForYou: Boolean = false
)

@Entity(
    tableName = "ratings",
    primaryKeys = ["profileId", "mediaId"]
)
data class RatingEntity(
    val profileId: String = "p4",
    val mediaId: String,
    val ratingType: String // "LIKE", "DOUBLE_LIKE", "DISLIKE"
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val mediaId: String,
    val remindedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val avatarType: String = "CUSTOM",
    val isKids: Boolean = false,
    val maxAge: Int = 18,
    val pin: String? = null,
    val language: String = "English",
    val autoplayNext: Boolean = true,
    val autoplayPreviews: Boolean = true,
    val gameHandle: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'Original'") val audioLanguage: String = "Original",
    @ColumnInfo(defaultValue = "'Off'") val subtitleLanguage: String = "Off"
)

@Dao
interface NetflixDao {
    // Profiles
    @Query("SELECT * FROM user_profiles ORDER BY updatedAt ASC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM user_profiles ORDER BY updatedAt ASC")
    suspend fun getAllProfilesList(): List<ProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<ProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Query("DELETE FROM user_profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: String)

    @Query("DELETE FROM user_profiles")
    suspend fun clearProfiles()

    // Watchlist
    @Query("SELECT * FROM watchlist WHERE profileId = :profileId ORDER BY addedTimestamp DESC")
    fun getWatchlist(profileId: String): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE profileId = :profileId ORDER BY addedTimestamp DESC")
    suspend fun getWatchlistOnce(profileId: String): List<WatchlistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWatchlist(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE profileId = :profileId AND mediaId = :mediaId")
    suspend fun removeFromWatchlist(profileId: String, mediaId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE profileId = :profileId AND mediaId = :mediaId)")
    fun isInWatchlist(profileId: String, mediaId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE profileId = :profileId AND mediaId = :mediaId)")
    suspend fun isInWatchlistOnce(profileId: String, mediaId: String): Boolean

    @Query("DELETE FROM watchlist")
    suspend fun clearWatchlist()

    // Progress
    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId ORDER BY lastWatchedTimestamp DESC")
    fun getAllProgress(profileId: String): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND mediaId = :mediaId ORDER BY lastWatchedTimestamp DESC LIMIT 1")
    fun getProgress(profileId: String, mediaId: String): Flow<WatchProgressEntity?>

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND mediaId = :mediaId ORDER BY lastWatchedTimestamp DESC LIMIT 1")
    suspend fun getProgressOnce(profileId: String, mediaId: String): WatchProgressEntity?

    @Query("DELETE FROM playback_progress")
    suspend fun clearProgress()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: WatchProgressEntity)

    @Query("DELETE FROM playback_progress WHERE profileId = :profileId AND mediaId = :mediaId")
    suspend fun removeProgress(profileId: String, mediaId: String)

    // Downloads
    @Query("SELECT * FROM downloads WHERE profileId = :profileId ORDER BY downloadedAt DESC")
    fun getAllDownloads(profileId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE profileId = :profileId ORDER BY downloadedAt DESC")
    suspend fun getAllDownloadsOnce(profileId: String): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE downloadKey = :downloadKey")
    suspend fun downloadReferenceCount(downloadKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDownload(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE profileId = :profileId AND downloadKey = :downloadKey")
    suspend fun deleteDownload(profileId: String, downloadKey: String)

    @Query("DELETE FROM downloads WHERE profileId = :profileId")
    suspend fun clearAllDownloads(profileId: String)

    @Query("DELETE FROM downloads")
    suspend fun clearAllGlobalDownloads()

    // Ratings
    @Query("SELECT * FROM ratings WHERE profileId = :profileId AND mediaId = :mediaId")
    fun getRating(profileId: String, mediaId: String): Flow<RatingEntity?>

    @Query("SELECT * FROM ratings WHERE profileId = :profileId AND (ratingType = 'LIKE' OR ratingType = 'DOUBLE_LIKE')")
    fun getLikedRatings(profileId: String): Flow<List<RatingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setRating(rating: RatingEntity)

    @Query("DELETE FROM ratings WHERE profileId = :profileId AND mediaId = :mediaId")
    suspend fun clearRating(profileId: String, mediaId: String)

    @Query("DELETE FROM ratings")
    suspend fun clearRatings()

    // Reminders for New & Hot
    @Query("SELECT * FROM reminders")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE mediaId = :mediaId")
    suspend fun removeReminder(mediaId: String)

    @Query("DELETE FROM reminders")
    suspend fun clearReminders()
}
