package com.example.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DownloadReferenceTest {
    @Test fun deletingOneProfilePreservesAnotherProfilesReference() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val dao = db.netflixDao()
            val download = DownloadEntity(profileId = "one", downloadKey = "show_ep", mediaId = "show", mediaTitle = "Show", episodeTitle = "Episode", fileSizeMb = 5)
            dao.saveDownload(download)
            dao.saveDownload(download.copy(profileId = "two"))
            assertEquals(2, dao.downloadReferenceCount("show_ep"))
            dao.deleteDownload("one", "show_ep")
            assertEquals(1, dao.downloadReferenceCount("show_ep"))
            assertEquals(1, dao.getAllDownloadsOnce("two").size)
            dao.deleteDownload("two", "show_ep")
            assertEquals(0, dao.downloadReferenceCount("show_ep"))
        } finally { db.close() }
    }
}
