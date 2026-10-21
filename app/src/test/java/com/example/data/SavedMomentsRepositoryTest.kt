package com.example.data

import android.app.Application
import android.util.AtomicFile
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.SavedMoment
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SavedMomentsRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val movie = MediaItem(
        id = "tmdb:movie:123", title = "An actual title", type = MediaType.MOVIE,
        description = "An original overview", tagline = "Tagline", matchPercentage = 94,
        maturityRating = "16+", releaseYear = 2026, durationOrSeasons = "2h 4m",
        genres = listOf("Drama"), cast = listOf("An Actor"), director = "A Director",
        posterUrl = "https://image.tmdb.org/t/p/w500/poster.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/w780/backdrop.jpg"
    )
    private fun moment(id: String = "moment-1", at: Long = 1_800_000_000_000) =
        SavedMoment(id = id, media = movie, positionSeconds = 2_960, createdAt = at)

    @Test fun roundTripRestoresOriginalTimestampAndSeriesCoordinates() {
        val directory = folder.newFolder()
        val series = moment().copy(media = movie.copy(type = MediaType.TV_SHOW),
            episodeId = "episode:47", seasonNumber = 2, episodeNumber = 4, episodeTitle = "The actual episode",
            titleDurationSeconds = 3_600)
        assertTrue(SavedMomentsRepository(directory).save("account-a", "profile-a", series))
        assertEquals(listOf(series), SavedMomentsRepository(directory).get("account-a", "profile-a"))
    }

    @Test fun knownDurationBoundsAreEnforcedAndLastSecondsRoundTrip() {
        val directory = folder.newFolder()
        val repository = SavedMomentsRepository(directory)
        for (invalid in listOf(moment().copy(titleDurationSeconds = 0),
            moment().copy(titleDurationSeconds = 2_960),
            moment().copy(titleDurationSeconds = 2_965))) {
            assertFalse(repository.save("account", "profile", invalid))
        }
        val finalSeconds = moment().copy(titleDurationSeconds = 2_965, durationSeconds = 5)
        assertTrue(repository.save("account", "profile", finalSeconds))
        assertEquals(listOf(finalSeconds), SavedMomentsRepository(directory).get("account", "profile"))
    }

    @Test fun accountAndProfileIsolationSurvivesRecreationAndDelimitedIds() {
        val directory = folder.newFolder()
        val repository = SavedMomentsRepository(directory)
        assertTrue(repository.save("a:b", "c", moment("first")))
        assertTrue(repository.save("a", "b:c", moment("second")))
        assertTrue(repository.save("a:b", "other-profile", moment("third")))
        val reopened = SavedMomentsRepository(directory)
        assertEquals("first", reopened.get("a:b", "c").single().id)
        assertEquals("second", reopened.get("a", "b:c").single().id)
        assertEquals("third", reopened.get("a:b", "other-profile").single().id)
        assertTrue(reopened.get("another-account", "c").isEmpty())
        assertTrue(reopened.get("a:b", "unknown").isEmpty())
    }

    @Test fun signedOutOrMissingProfileCannotReadOrWrite() {
        val repository = SavedMomentsRepository(folder.newFolder())
        assertTrue(repository.save("account", "profile", moment()))
        for ((account, profile) in listOf(null to "profile", "" to "profile", "account" to null, "account" to "  ")) {
            assertFalse(repository.save(account, profile, moment()))
            assertFalse(repository.remove(account, profile, "moment-1"))
            assertTrue(repository.get(account, profile).isEmpty())
            assertTrue(repository.observe(account, profile).value.isEmpty())
        }
    }

    @Test fun deletionIsDurableAndOnlyUpdatesItsScopeObserver() {
        val directory = folder.newFolder()
        val repository = SavedMomentsRepository(directory)
        val own = repository.observe("account", "profile")
        val other = repository.observe("account", "other")
        assertTrue(repository.save("account", "profile", moment()))
        assertEquals(listOf(moment()), own.value)
        assertTrue(other.value.isEmpty())
        assertTrue(repository.remove("account", "profile", "moment-1"))
        assertTrue(own.value.isEmpty())
        assertTrue(SavedMomentsRepository(directory).get("account", "profile").isEmpty())
    }

    @Test fun corruptOrFutureVersionDataDoesNotCrashOrExposePartialRecords() {
        val directory = folder.newFolder()
        assertTrue(SavedMomentsRepository(directory).save("account", "profile", moment()))
        val file = directory.listFiles()!!.single()
        val goodJson = file.readText()
        for (corrupt in listOf("broken {", "null", goodJson.replace("\"version\":1", "\"version\":2"))) {
            file.writeText(corrupt)
            assertTrue(SavedMomentsRepository(directory).get("account", "profile").isEmpty())
        }
        file.writeText(goodJson)
        // A crash after startWrite recovers the previous complete document via AtomicFile.
        AtomicFile(file).startWrite().use { it.write("partial".toByteArray()) }
        assertEquals(listOf(moment()), SavedMomentsRepository(directory).get("account", "profile"))
    }

    @Test fun invalidPositionsAndIncompleteSeriesAreRejectedWithoutChangingStoredRecords() {
        val repository = SavedMomentsRepository(folder.newFolder())
        assertTrue(repository.save("account", "profile", moment()))
        val invalid = listOf(moment().copy(positionSeconds = -1), moment().copy(durationSeconds = 0),
            moment().copy(createdAt = 0), moment().copy(id = ""),
            moment().copy(media = movie.copy(type = MediaType.TV_SHOW)),
            moment().copy(episodeId = "e", episodeNumber = 0, seasonNumber = 1))
        invalid.forEach { assertFalse(repository.save("account", "profile", it)) }
        assertEquals(listOf(moment()), repository.get("account", "profile"))
    }

    @Test fun keepsMostRecentHundredAndReplacesExistingBookmarkById() {
        val repository = SavedMomentsRepository(folder.newFolder())
        repeat(105) { assertTrue(repository.save("account", "profile", moment("m$it", 1_000L + it))) }
        assertEquals(100, repository.get("account", "profile").size)
        assertEquals("m104", repository.get("account", "profile").first().id)
        assertFalse(repository.get("account", "profile").any { it.id == "m4" })
        assertTrue(repository.save("account", "profile", moment("m104", 5_000).copy(positionSeconds = 900)))
        assertEquals(100, repository.get("account", "profile").size)
        assertEquals(900, repository.get("account", "profile").first().positionSeconds)
    }

    @Test fun doesNotPersistPlaybackLinksTokensOrRecursiveCatalog() {
        val directory = folder.newFolder()
        val repository = SavedMomentsRepository(directory)
        val unsafe = moment().copy(media = movie.copy(
            posterUrl = "https://cdn.example/movie.m3u8?token=secret",
            backdropUrl = "https://image.tmdb.org/t/p/w500/image.jpg?token=secret",
            logoUrl = "https://user:secret@image.tmdb.org/t/p/w500/image.png",
            heroPosterUrl = "https://image.tmdb.org/t/p/w780/image.jpg?token=secret",
            similarMedia = listOf(movie)
        ))
        assertTrue(repository.save("account", "profile", unsafe))
        val restored = SavedMomentsRepository(directory).get("account", "profile").single()
        assertNull(restored.media.posterUrl)
        assertNull(restored.media.backdropUrl)
        assertNull(restored.media.logoUrl)
        assertNull(restored.media.heroPosterUrl)
        assertTrue(restored.media.similarMedia.isEmpty())
        assertFalse(directory.listFiles()!!.single().readText().contains("secret"))
    }

    @Test fun failedDiskWriteDoesNotEmitAnUnsavedMoment() {
        val blockedDirectory = File(folder.newFolder(), "file-not-directory").apply { writeText("occupied") }
        val repository = SavedMomentsRepository(blockedDirectory)
        val observed = repository.observe("account", "profile")
        assertFalse(repository.save("account", "profile", moment()))
        assertTrue(observed.value.isEmpty())
    }
}
