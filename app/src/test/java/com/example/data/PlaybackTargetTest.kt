package com.example.data

import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import org.junit.Assert.*
import org.junit.Test

class PlaybackTargetTest {
    private fun media(type: MediaType = MediaType.TV_SHOW) = MediaItem("4604", "Smallville", type,
        "", "", 90, "16+", 2001, "45m", genres = emptyList(), cast = emptyList(), director = "")

    @Test fun mobileAndTvEpisodeIdsResolveTheSameSeasonAndEpisode() {
        val mobile = PlaybackTarget.from(media(), Episode("ep_4604_S6_22", 22, "Episode", 45, ""))
        val tv = PlaybackTarget.from(media(), Episode("s6_e22", 22, "Episode", 45, ""))
        assertEquals(mobile, tv)
        assertEquals(6, mobile.season)
        assertEquals(22, mobile.episode)
        assertEquals("Smallville", mobile.title)
        assertEquals("2001", mobile.year)
    }

    @Test fun seriesWithoutSelectedEpisodeUsesTheFirstAvailableEpisode() {
        val selected = Episode("ep_4604_S2_5", 5, "Episode", 45, "")
        val item = media().copy(episodes = listOf(selected))
        assertEquals(PlaybackTarget.from(item, selected), PlaybackTarget.from(item, null))
        assertEquals(2, PlaybackTarget.from(item, null).season)
    }

    @Test fun seriesWithoutEpisodeMetadataDefaultsToSeasonOneEpisodeOne() {
        val target = PlaybackTarget.from(media(), null)
        assertEquals(1, target.season)
        assertEquals(1, target.episode)
        assertEquals("tv", target.type)
    }

    @Test fun moviesNeverSendEpisodeCoordinates() {
        val target = PlaybackTarget.from(media(MediaType.MOVIE), Episode("s6_e22", 22, "Ignored", 45, ""))
        assertEquals("movie", target.type)
        assertEquals(0, target.season)
        assertEquals(0, target.episode)
    }
}
