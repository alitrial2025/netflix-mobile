package com.example.data.download

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class HlsSegmentStoreTest {
    private fun segment() = ByteArray(188 * 3) { 1 }.apply { for (i in indices step 188) this[i] = 0x47 }

    @Test fun onlyCommittedIntactSegmentsSurviveRestart() {
        val root = Files.createTempDirectory("hls-checkpoints").toFile()
        try {
            val first = HlsSegmentStore(root, "same")
            first.partial(0).writeBytes(segment()); first.commit(0)
            first.partial(1).writeBytes(segment().copyOf(100))
            val restored = HlsSegmentStore(root, "same")
            assertArrayEquals(segment(), restored.completed(0)!!.readBytes())
            assertNull(restored.completed(1))
            File(root, "0.ts").appendBytes(byteArrayOf(1))
            assertNull(restored.completed(0))
        } finally { root.deleteRecursively() }
    }

    @Test fun signedTokenRenewalPreservesSegmentsButChangedContentOrQualityDoesNot() {
        val playlist = "#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:0\n#EXTINF:6,\n#EXT-X-ENDLIST"
        val old = HlsSegmentStore.identity(playlist, listOf("https://cdn.example/movie/1.ts?token=old"), true)
        assertEquals(old, HlsSegmentStore.identity(playlist, listOf("https://cdn.example/movie/1.ts?token=new"), true))
        assertNotEquals(old, HlsSegmentStore.identity(playlist.replace("6,", "7,"), listOf("https://cdn.example/movie/1.ts?token=new"), true))
        assertNotEquals(old, HlsSegmentStore.identity(playlist, listOf("https://cdn.example/movie/1.ts?token=new"), false))
        val root = Files.createTempDirectory("hls-stale").toFile()
        try {
            val store = HlsSegmentStore(root, old)
            store.partial(0).writeBytes(segment()); store.commit(0)
            assertNull(HlsSegmentStore(root, "different-content").completed(0))
        } finally { root.deleteRecursively() }
    }

    @Test fun errorPagesCannotBeMarkedAsCompletedVideo() {
        val root = Files.createTempDirectory("hls-invalid").toFile()
        try {
            val store = HlsSegmentStore(root, "identity")
            store.partial(0).writeText("<html>expired session</html>".repeat(20))
            assertThrows(IllegalArgumentException::class.java) { store.commit(0) }
            assertNull(store.completed(0))
        } finally { root.deleteRecursively() }
    }

    @Test fun retriesAreBoundedAndSeparateNetworkFailuresFromUnsupportedFormats() {
        assertTrue(DownloadRetryPolicy.shouldRetry(DownloadHttpException(503), 0))
        assertTrue(DownloadRetryPolicy.shouldRetry(DownloadHttpException(403), 0))
        assertFalse(DownloadRetryPolicy.shouldRetry(DownloadHttpException(503), 8))
        assertFalse(DownloadRetryPolicy.shouldRetry(DownloadHttpException(404), 0))
        assertFalse(DownloadRetryPolicy.shouldRetry(DownloadStorageException(), 0))
        assertFalse(DownloadRetryPolicy.shouldRetry(IllegalArgumentException("Unsupported audio"), 0))
    }
}
