package com.example.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.WatchProgressEntity
import com.example.data.repository.NetflixRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ContinueWatchingRepositoryTest {
    @Test fun tvImportRewindNewEpisodeAndRemovalKeepTheOriginalEventOrder() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val repository = NetflixRepository(db.netflixDao())
            val saved = WatchProgressEntity("profile", "42", "ep_42_S4_8", 300, 600, "Episode", 4, 8, 100L)
            repository.upsertRemoteProgressIfNewer(saved)
            repository.upsertRemoteProgressIfNewer(saved.copy(positionSeconds = 50, lastWatchedTimestamp = 200L))
            repository.upsertRemoteProgressIfNewer(saved.copy(positionSeconds = 400, lastWatchedTimestamp = 150L))
            assertEquals(50, repository.getProgress("profile", "42").first()?.positionSeconds)
            repository.upsertRemoteProgressIfNewer(saved.copy(positionSeconds = 600, lastWatchedTimestamp = 300L))
            repository.upsertRemoteProgressIfNewer(saved.copy(positionSeconds = 100, lastWatchedTimestamp = 250L))
            assertEquals(600, repository.getProgress("profile", "42").first()?.positionSeconds)
            repository.upsertRemoteProgressIfNewer(saved.copy(episodeId = "ep_42_S4_9", episode = 9, positionSeconds = 0, lastWatchedTimestamp = 400L))
            assertEquals(9, repository.getProgress("profile", "42").first()?.episode)
            assertEquals(400L, repository.getProgress("profile", "42").first()?.lastWatchedTimestamp)
        } finally { db.close() }
    }
}
