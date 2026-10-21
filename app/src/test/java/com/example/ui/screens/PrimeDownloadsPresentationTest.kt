package com.example.ui.screens

import com.example.data.local.DownloadEntity
import org.junit.Assert.*
import org.junit.Test

class PrimeDownloadsPresentationTest {
    private fun row(key: String, label: String? = null, size: Int = 100, complete: Boolean = true) =
        DownloadEntity(downloadKey = key, mediaId = "show", mediaTitle = "A show", episodeTitle = label, fileSizeMb = size, isComplete = complete)

    @Test fun canonicalAndStoredEpisodeSeasonsRemainDistinctWithoutInventingUnknownSeasons() {
        assertEquals(6, primeDownloadedSeason(row("show_show_S6_e22")))
        assertEquals(2, primeDownloadedSeason(row("show_s2_1")))
        assertEquals(3, primeDownloadedSeason(row("show_legacy", "S3:E5 Episode")))
        assertNull(primeDownloadedSeason(row("show_legacy", "Unknown episode")))
        assertNull(primeDownloadedSeason(row("show")))
    }
    @Test fun summaryExcludesQueuedDuplicatesAndUnknownRuntime() {
        val complete = row("show_show_S1_e1", size = 1024)
        assertEquals("1 video  1.0 GB", primeDownloadsSummary(listOf(complete, complete, row("show_pending", complete = false)), emptyMap()))
        assertEquals("0 videos  0 MB", primeDownloadsSummary(emptyList(), emptyMap()))
    }
}
