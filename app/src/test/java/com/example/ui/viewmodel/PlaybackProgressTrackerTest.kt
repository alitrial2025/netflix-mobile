package com.example.ui.viewmodel

import org.junit.Assert.*
import org.junit.Test

class PlaybackProgressTrackerTest {
    @Test fun repeatedPlayerTicksPersistOnlyOnce() {
        val tracker = PlaybackProgressTracker()
        assertTrue(tracker.shouldSave(10))
        assertFalse(tracker.shouldSave(10))
        assertFalse(tracker.shouldSave(11))
        assertTrue(tracker.shouldSave(20))
    }
    @Test fun smartDownloadRunsOncePerPlaybackSession() {
        val tracker = PlaybackProgressTracker()
        assertTrue(tracker.claimSmartDownload())
        assertFalse(tracker.claimSmartDownload())
        tracker.reset()
        assertTrue(tracker.claimSmartDownload())
    }
    @Test fun newEpisodeCanSaveTheSamePosition() {
        val tracker = PlaybackProgressTracker()
        assertTrue(tracker.shouldSave(10))
        tracker.reset()
        assertTrue(tracker.shouldSave(10))
    }
    @Test fun unknownEpisodeNeverRestartsTheFirstEpisode() {
        assertNull(nextEpisodeIndex("unknown", listOf("first", "second")))
        assertNull(nextEpisodeIndex("second", listOf("first", "second")))
        assertEquals(1, nextEpisodeIndex("first", listOf("first", "second")))
    }
    @Test fun episodeCoordinatesMatchBetweenTvAndMobile() {
        assertEquals(2 to 3, episodeCoordinates("ep_42_S2_3"))
        assertEquals(2 to 3, episodeCoordinates("s2_e3"))
        assertEquals(2 to 3, episodeCoordinates(null, 2, 3))
    }
}
