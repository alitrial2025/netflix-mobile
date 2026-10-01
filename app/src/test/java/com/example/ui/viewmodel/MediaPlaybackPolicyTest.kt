package com.example.ui.viewmodel

import com.example.data.local.WatchProgressEntity
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import org.junit.Assert.*
import org.junit.Test

class MediaPlaybackPolicyTest {
    private fun show(id: String = "mr_robot", type: MediaType = MediaType.TV_SHOW) = MediaItem(
        id, id, type, "", "", 90, "16+", 2015, "4 Seasons", genres = listOf("Drama"), cast = emptyList(), director = "")
    private fun progress(position: Int = 400) = WatchProgressEntity(profileId = "profile", mediaId = "mr_robot",
        positionSeconds = position, totalSeconds = 2400, episodeId = "ep_mr_robot_S3_7", episodeTitle = "A remembered episode", season = 3, episode = 7)

    @Test fun homeResumePreservesSeasonAndEpisodeWithoutLoadedMetadata() {
        val target = initialPlaybackEpisode(show(), null, progress())!!
        assertEquals("ep_mr_robot_S3_7", target.id)
        assertEquals(3 to 7, episodeCoordinates(target.id))
        assertEquals("A remembered episode", target.title)
    }
    @Test fun zeroPositionInNewEpisodeKeepsItsSeasonRatherThanRestartingEpisodeOne() {
        assertEquals("ep_mr_robot_S3_7", initialPlaybackEpisode(show(), null, progress(0))?.id)
    }
    @Test fun explicitEpisodeSelectionOverridesAnotherSavedSeason() {
        val chosen = Episode("ep_mr_robot_S2_1", 1, "Selected", 45, "")
        assertEquals(chosen, initialPlaybackEpisode(show(), chosen, progress()))
    }
    @Test fun completedProgressDoesNotResumeCredits() {
        val first = Episode("ep_mr_robot_S1_1", 1, "First", 45, "")
        assertFalse(progress(2399).canResume())
        assertEquals(first, initialPlaybackEpisode(show().copy(episodes = listOf(first)), null, progress(2399)))
    }
    @Test fun nextEpisodeUsesCoordinatesRatherThanListOrderOrAnUnknownEpisode() {
        val current = Episode("ep_mr_robot_S2_3", 3, "Current", 45, "")
        val next = Episode("ep_mr_robot_S2_4", 4, "Next", 45, "")
        val otherSeason = Episode("ep_mr_robot_S3_4", 4, "Other", 45, "")
        assertEquals(next, nextLoadedEpisode(current, listOf(otherSeason, next, current)))
        assertNull(nextLoadedEpisode(current, listOf(otherSeason)))
    }
    @Test fun downloadCompositeKeysPreserveUnderscoresAndSeasonLabels() {
        assertEquals("ep_mr_robot_S3_7", downloadEpisodeId("mr_robot", "mr_robot_ep_mr_robot_S3_7"))
        assertNull(downloadEpisodeId("mr_robot", "mr_robot"))
        assertNull(downloadEpisodeId("mr_robot", "another_ep_mr_robot_S3_7"))
        assertEquals("S3:E7 Chapter", episodeDownloadLabel(Episode("ep_mr_robot_S3_7", 7, "Chapter", 40, "")))
    }
    @Test fun smartDownloadsNeverDeleteAnEpisodeDuringPlaybackOrSeeking() {
        val credits = PlayerState(media = show(), currentPositionSec = 2399, durationSec = 2400)
        assertFalse(smartDownloadCompletionReady(credits))
        assertTrue(smartDownloadCompletionReady(credits.copy(hasEnded = true)))
        assertFalse(smartDownloadCompletionReady(credits.copy(hasEnded = true, durationSec = 30)))
    }
    @Test fun postPlayRecommendationsStayInsideThePermittedCatalog() {
        val current = show()
        val allowed = show("allowed")
        val future = show("future").copy(isComingSoon = true)
        // Unfiltered related metadata must never expand the profile's permitted pool.
        assertEquals(listOf(allowed), postPlayCandidates(current.copy(similarMedia = listOf(show("restricted"))), listOf(current, allowed, future)))
    }
}
