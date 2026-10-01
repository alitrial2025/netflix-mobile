package com.example.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadEntity
import com.example.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProfilePreferencesTest {
    @Test fun upgradePreservesExistingProfilesAndDownloads() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val name = "profile-upgrade-test.db"
        app.deleteDatabase(name)
        val original = Room.databaseBuilder(app, AppDatabase::class.java, name).build()
        original.netflixDao().insertProfile(UserProfile("p1", "Alex").toEntity())
        original.netflixDao().saveDownload(DownloadEntity("p1", "movie", "movie", "Saved movie", null, 185, localFilePath = "/existing/movie.mp4"))
        original.close()
        // Recreate the previous schema on a full real Room database, then run the registered migration.
        SQLiteDatabase.openDatabase(app.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("CREATE TABLE user_profiles_legacy (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, avatarUrl TEXT, avatarType TEXT NOT NULL, isKids INTEGER NOT NULL, maxAge INTEGER NOT NULL, pin TEXT, language TEXT NOT NULL, autoplayNext INTEGER NOT NULL, autoplayPreviews INTEGER NOT NULL, gameHandle TEXT, updatedAt INTEGER NOT NULL)")
            db.execSQL("INSERT INTO user_profiles_legacy SELECT id, name, avatarUrl, avatarType, isKids, maxAge, pin, language, autoplayNext, autoplayPreviews, gameHandle, updatedAt FROM user_profiles")
            db.execSQL("DROP TABLE user_profiles")
            db.execSQL("ALTER TABLE user_profiles_legacy RENAME TO user_profiles")
            db.execSQL("CREATE TABLE downloads_legacy (profileId TEXT NOT NULL, downloadKey TEXT NOT NULL, mediaId TEXT NOT NULL, mediaTitle TEXT NOT NULL, episodeTitle TEXT, fileSizeMb INTEGER NOT NULL, isComplete INTEGER NOT NULL, downloadedAt INTEGER NOT NULL, localFilePath TEXT, videoUrl TEXT, captionsJson TEXT, PRIMARY KEY(profileId, downloadKey))")
            db.execSQL("INSERT INTO downloads_legacy SELECT profileId, downloadKey, mediaId, mediaTitle, episodeTitle, fileSizeMb, isComplete, downloadedAt, localFilePath, videoUrl, captionsJson FROM downloads")
            db.execSQL("DROP TABLE downloads")
            db.execSQL("ALTER TABLE downloads_legacy RENAME TO downloads")
            db.version = 7
        }
        val upgraded = Room.databaseBuilder(app, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_7_8).build()
        try {
            val profile = upgraded.netflixDao().getAllProfilesList().single().toUserProfile()
            assertEquals("Alex", profile.name)
            assertEquals("Original", profile.audioLanguage)
            assertEquals("Off", profile.subtitleLanguage)
            val download = upgraded.netflixDao().getAllDownloadsOnce("p1").single()
            assertEquals("/existing/movie.mp4", download.localFilePath)
            assertFalse(download.isForYou)
        } finally { upgraded.close(); app.deleteDatabase(name) }
    }

    @Test fun maturityLimitsApplyToTeenProfilesAndDoNotTreatGenreAsAnAgeCertificate() {
        val show = MediaItem("show", "Show", MediaType.TV_SHOW, "", "", 90, "16+", 2025, "1 Season", genres = listOf("Family"), cast = emptyList(), director = "")
        assertTrue(UserProfile("p", "Teen", maxAge = 16).hasMaturityRestriction)
        assertTrue(show.isKidSafe(16)); assertFalse(show.isKidSafe(12))
        assertFalse(show.copy(maturityRating = "Unrated").isKidSafe(12))
        assertFalse(show.copy(maturityRating = "R").isKidSafe(16))
        assertEquals(12, UserProfile("p", "Kids", isKids = true, maxAge = 18).contentMaxAge)
    }

    @Test fun profileLanguagePreferencesRoundTripAndMapToActualTrackLanguageCodes() {
        val profile = UserProfile("p", "Alex", audioLanguage = "French", subtitleLanguage = "Japanese")
        assertEquals(profile, profile.toEntity().toUserProfile())
        assertEquals("fr", playbackLanguageCode(profile.audioLanguage))
        assertEquals("ja", playbackLanguageCode(profile.subtitleLanguage))
        assertNull(playbackLanguageCode("Original")); assertNull(playbackLanguageCode("Off"))
    }
}
