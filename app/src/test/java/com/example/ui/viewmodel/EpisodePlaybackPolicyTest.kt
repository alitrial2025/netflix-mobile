package com.example.ui.viewmodel

import org.junit.Assert.*
import org.junit.Test

class EpisodePlaybackPolicyTest {
    @Test fun explicitOpeningThemeUsesTheNextDialogueBoundary() {
        val captions = "1\n00:00:45,000 --> 00:00:58,000\n[Opening theme]\n\n2\n00:01:20,000 --> 00:01:22,000\nHello."
        assertEquals(IntroWindow(45, 80), EpisodePlaybackPolicy.detectIntro(captions, 1800))
    }
    @Test fun ordinaryMusicDoesNotSkipDialogue() {
        assertNull(EpisodePlaybackPolicy.detectIntro("00:00:45.000 --> 00:00:58.000\n[Music playing]", 1800))
    }
    @Test fun missingCaptionsDoNotInventAnIntro() {
        assertNull(EpisodePlaybackPolicy.detectIntro("", 1800))
    }
    @Test fun shortVideosNeverSkipPastTheirEnd() {
        assertNull(EpisodePlaybackPolicy.detectIntro("00:00:45.000 --> 00:01:10.000\n[Opening theme]", 60))
    }
    @Test fun ThemeLateInAnEpisodeIsNotAnOpening() {
        assertNull(EpisodePlaybackPolicy.detectIntro("00:20:45.000 --> 00:21:10.000\n[Opening theme]", 1800))
    }
    @Test fun webVttCueSettingsAreSupported() {
        assertEquals(IntroWindow(45, 70), EpisodePlaybackPolicy.detectIntro("WEBVTT\n\n00:45.000 --> 01:10.000 align:center\n[Title theme]", 1800))
    }
}
