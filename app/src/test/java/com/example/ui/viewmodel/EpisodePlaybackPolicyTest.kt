package com.example.ui.viewmodel

import com.example.data.PlaybackMarkers
import org.junit.Assert.*
import org.junit.Test

class EpisodePlaybackPolicyTest {
    @Test fun publishedIntroKeepsItsExactInterval() {
        assertEquals(IntroWindow(45, 90), EpisodePlaybackPolicy.verifiedIntro(PlaybackMarkers(45_000, 90_000), 1_800_000))
    }
    @Test fun subsecondBoundariesRoundForwardSoSeekingCannotLandInsideTheIntro() {
        assertEquals(IntroWindow(46, 91), EpisodePlaybackPolicy.verifiedIntro(PlaybackMarkers(45_100, 90_001), 1_800_000))
    }
    @Test fun missingOrReversedIntervalsNeverCreateAnIntro() {
        for (marker in listOf(PlaybackMarkers(), PlaybackMarkers(30_000, null), PlaybackMarkers(-1, 30_000), PlaybackMarkers(30_000, 20_000)))
            assertNull(EpisodePlaybackPolicy.verifiedIntro(marker, 1_800_000))
    }
    @Test fun unknownDurationAndOutOfBoundsMetadataCannotCreateSkipTargets() {
        assertNull(EpisodePlaybackPolicy.verifiedIntro(PlaybackMarkers(45_000, 90_000), 0))
        assertNull(EpisodePlaybackPolicy.verifiedIntro(PlaybackMarkers(45_000, 90_000), 60_000))
    }
    @Test fun lastThirtySecondsWithoutVerifiedCreditsDoNotCreateAPill() {
        assertFalse(EpisodePlaybackPolicy.hasVerifiedCredits(PlaybackMarkers(), 1_775_000, 1_800_000))
    }
    @Test fun creditPillBeginsOnlyAtThePublishedBoundary() {
        val markers = PlaybackMarkers(outroStart = 1_700_000)
        assertFalse(EpisodePlaybackPolicy.hasVerifiedCredits(markers, 1_699_999, 1_800_000))
        assertTrue(EpisodePlaybackPolicy.hasVerifiedCredits(markers, 1_700_000, 1_800_000))
        assertFalse(EpisodePlaybackPolicy.hasVerifiedCredits(markers, 1_800_001, 1_800_000))
        assertFalse(EpisodePlaybackPolicy.hasVerifiedCredits(markers, 0, 0))
    }
}
