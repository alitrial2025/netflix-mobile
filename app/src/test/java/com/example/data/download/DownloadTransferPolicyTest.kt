package com.example.data.download

import org.junit.Assert.*
import org.junit.Test

class DownloadTransferPolicyTest {
    @Test fun fullResponseRestartsInsteadOfAppending() {
        assertEquals(DirectTransferPlan(false, 100), DownloadTransferPolicy.directPlan(200, 20, null, 100))
    }
    @Test fun correctRangeResumesAgainstTheWholeFileSize() {
        assertEquals(DirectTransferPlan(true, 100), DownloadTransferPolicy.directPlan(206, 20, "bytes 20-99/100", 80))
    }
    @Test(expected = IllegalArgumentException::class) fun incorrectRangeCannotCorruptTheFile() {
        DownloadTransferPolicy.directPlan(206, 20, "bytes 0-79/100", 80)
    }
    @Test(expected = IllegalStateException::class) fun missingRangeIsRejected() {
        DownloadTransferPolicy.directPlan(206, 20, null, 80)
    }
    @Test(expected = IllegalArgumentException::class) fun mismatchedLengthIsRejected() {
        DownloadTransferPolicy.directPlan(206, 20, "bytes 20-99/100", 79)
    }
    @Test fun truncatedAndEmptyResponsesAreNotComplete() {
        assertFalse(DownloadTransferPolicy.isComplete(90, 100))
        assertFalse(DownloadTransferPolicy.isComplete(0, -1))
        assertTrue(DownloadTransferPolicy.isComplete(100, 100))
    }
    @Test fun vodPlaylistResolvesSegmentsAcrossTags() {
        val playlist = "#EXTM3U\n#EXTINF:10,\n# comment\none.ts\n#EXTINF:10,\n../two.ts\n#EXT-X-ENDLIST"
        assertEquals(listOf("https://cdn.example/path/one.ts", "https://cdn.example/two.ts"),
            DownloadTransferPolicy.hlsSegments(playlist, "https://cdn.example/path/index.m3u8"))
    }
    @Test(expected = IllegalArgumentException::class) fun encryptedPlaylistCannotBecomeFakeMp4() {
        DownloadTransferPolicy.hlsSegments("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=key\n#EXTINF:10,\none.ts\n#EXT-X-ENDLIST", "https://cdn.example/index.m3u8")
    }
    @Test(expected = IllegalArgumentException::class) fun fragmentedMp4RequiresItsInitializationSegment() {
        DownloadTransferPolicy.hlsSegments("#EXTM3U\n#EXT-X-MAP:URI=init.mp4\n#EXTINF:10,\none.m4s\n#EXT-X-ENDLIST", "https://cdn.example/index.m3u8")
    }
    @Test(expected = IllegalArgumentException::class) fun livePlaylistIsNotACompletedDownload() {
        DownloadTransferPolicy.hlsSegments("#EXTM3U\n#EXTINF:10,\none.ts", "https://cdn.example/index.m3u8")
    }
    @Test(expected = IllegalArgumentException::class) fun missingFinalSegmentIsRejected() {
        DownloadTransferPolicy.hlsSegments("#EXTM3U\n#EXTINF:10,\n#EXT-X-ENDLIST", "https://cdn.example/index.m3u8")
    }
}
