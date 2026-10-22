package com.example.data.download

import com.example.data.local.DownloadEntity
import com.example.data.local.WatchProgressEntity
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DownloadIntegrityTest {
    private fun fixture(block: (File) -> Unit) { val root = Files.createTempDirectory("download-integrity").toFile(); try { block(root) } finally { root.deleteRecursively() } }
    private fun packet(value: Byte) = ByteArray(188 * 3) { value }.apply { for (index in indices step 188) this[index] = 0x47 }

    @Test fun directReceiptsDetectExactlyWhichChunksWereDamaged() = fixture { directory ->
        val file = File(directory, "movie.mp4").apply { writeBytes(ByteArray(DownloadIntegrity.CHUNK_BYTES * 2 + 20) { (it % 97).toByte() }) }
        val receipt = DownloadIntegrity.directReceipt(file, file.name, "\"edition-a\"")
        DownloadIntegrity.write(directory, "movie", receipt)
        assertEquals(DownloadHealth.HEALTHY, DownloadIntegrity.inspect(directory, "movie", file.path).health)
        RandomAccessFile(file, "rw").use { it.seek(DownloadIntegrity.CHUNK_BYTES.toLong() + 2); it.write(255) }
        val damaged = DownloadIntegrity.inspect(directory, "movie", file.path)
        assertEquals(DownloadHealth.DAMAGED, damaged.health)
        assertEquals(1, damaged.damagedParts)
        assertEquals(DownloadIntegrity.CHUNK_BYTES + 20L, damaged.verifiedBytes)
        assertTrue(DownloadIntegrity.intact(directory, receipt.chunks.first()))
        assertFalse(DownloadIntegrity.intact(directory, receipt.chunks[1]))
    }

    @Test fun truncationMissingAndLegacyFilesAreNeverReportedHealthy() = fixture { directory ->
        val file = File(directory, "movie.mp4").apply { writeText("complete saved media") }
        assertEquals(DownloadHealth.LEGACY, DownloadIntegrity.inspect(directory, "movie", file.path).health)
        DownloadIntegrity.write(directory, "movie", DownloadIntegrity.directReceipt(file, file.name, null))
        RandomAccessFile(file, "rw").use { it.setLength(4) }
        assertEquals(DownloadHealth.DAMAGED, DownloadIntegrity.inspect(directory, "movie", file.path).health)
        file.delete()
        assertEquals(DownloadHealth.DAMAGED, DownloadIntegrity.inspect(directory, "movie", file.path).health)
    }

    @Test fun hlsRepairSeedsOnlyIntactOriginalSegmentsAndNeverDifferentEditions() = fixture { directory ->
        val saved = File(directory, "movie.ts").apply { writeBytes(packet(1) + packet(2) + packet(3)) }
        val length = packet(1).size.toLong()
        val chunks = (0..2).map { DownloadChunk(saved.name, it * length, length, DownloadIntegrity.checksum(saved, it * length, length)) }
        DownloadIntegrity.write(directory, "movie", DownloadReceipt(kind = "hls", identity = "edition-a", chunks = chunks))
        RandomAccessFile(saved, "rw").use { it.seek(length + 2); it.write(9) }
        val checkpoints = File(directory, "movie.segments")
        val store = HlsSegmentStore(checkpoints, "edition-a")
        DownloadIntegrity.seedHls(directory, "movie", "edition-a", store, 3)
        assertArrayEquals(packet(1), store.completed(0)!!.readBytes())
        assertNull(store.completed(1))
        assertArrayEquals(packet(3), store.completed(2)!!.readBytes())
        checkpoints.deleteRecursively()
        val different = HlsSegmentStore(checkpoints, "edition-b")
        DownloadIntegrity.seedHls(directory, "movie", "edition-b", different, 3)
        assertNull(different.completed(0))
    }

    @Test fun bundleChecksIncludeMasterPlaylistsVideoAndAudio() = fixture { directory ->
        File(directory, "show.offline/video").mkdirs(); File(directory, "show.offline/audio").mkdirs()
        File(directory, "show.offline/video/index.m3u8").writeText("#EXTM3U\n0.ts\n")
        File(directory, "show.offline/video/0.ts").writeBytes(packet(1))
        File(directory, "show.offline/audio/index.m3u8").writeText("#EXTM3U\n0.aac\n")
        File(directory, "show.offline/audio/0.aac").writeBytes(byteArrayOf(-1, -15, 1, 2, 3, 4, 5, 6))
        val entry = File(directory, "show.m3u8").apply { writeText("#EXTM3U\nshow.offline/video/index.m3u8\n") }
        DownloadIntegrity.write(directory, "show", DownloadIntegrity.bundleReceipt(directory, "show", entry))
        assertEquals(DownloadHealth.HEALTHY, DownloadIntegrity.inspect(directory, "show", entry.path).health)
        File(directory, "show.offline/audio/0.aac").delete()
        assertEquals(DownloadHealth.DAMAGED, DownloadIntegrity.inspect(directory, "show", entry.path).health)
        assertTrue(DownloadIntegrity.read(directory, "show")!!.chunks.any { it.path == "show.offline/video/index.m3u8" })
    }

    @Test fun stagedReceiptsDoNotReplaceOldProofUntilTheMediaIsFinalized() = fixture { directory ->
        val file = File(directory, "movie.mp4").apply { writeText("old bytes") }
        DownloadIntegrity.write(directory, "movie", DownloadIntegrity.directReceipt(file, file.name, "old"))
        val next = File(directory, "movie.part").apply { writeText("new media bytes") }
        DownloadIntegrity.write(directory, "movie", DownloadIntegrity.directReceipt(next, file.name, "new"), pending = true)
        assertEquals("old", DownloadIntegrity.read(directory, "movie")!!.validator)
        assertTrue(next.renameTo(file)); DownloadIntegrity.promote(directory, "movie")
        assertEquals("new", DownloadIntegrity.read(directory, "movie")!!.validator)
        assertEquals(DownloadHealth.HEALTHY, DownloadIntegrity.inspect(directory, "movie", file.path).health)
    }

    @Test fun receiptsCannotReadOutsideTheDownloadDirectory() = fixture { directory ->
        assertThrows(IllegalArgumentException::class.java) { DownloadIntegrity.safeFile(directory, "../secret") }
        assertThrows(IllegalArgumentException::class.java) { DownloadIntegrity.safeFile(directory, "/etc/passwd") }
        val file = File(directory, "movie.mp4").apply { writeText("video") }
        val invalid = DownloadReceipt(kind = "direct", chunks = listOf(DownloadChunk("../secret", 0, 5, "a".repeat(64))))
        DownloadIntegrity.write(directory, "movie", invalid)
        assertNull(DownloadIntegrity.read(directory, "movie"))
        assertEquals(DownloadHealth.LEGACY, DownloadIntegrity.inspect(directory, "movie", file.path).health)
    }

    @Test fun cancelledCheckingRemainsCancelledInsteadOfLookingCorrupt() = fixture { directory ->
        val file = File(directory, "movie.mp4").apply { writeText("video") }
        DownloadIntegrity.write(directory, "movie", DownloadIntegrity.directReceipt(file, file.name, null))
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            DownloadIntegrity.inspect(directory, "movie", file.path) { throw java.util.concurrent.CancellationException() }
        }
    }

    @Test fun conditionalRangeRequiresAnExactStrongValidatorAndExactRepresentation() {
        assertTrue(DownloadRangeRepairPolicy.matches(206, "bytes 10-19/30", 10, 10, 10, 30, "\"a\"", "\"a\""))
        assertFalse(DownloadRangeRepairPolicy.matches(200, null, 30, 10, 10, 30, "\"a\"", "\"a\""))
        assertFalse(DownloadRangeRepairPolicy.matches(206, "bytes 10-19/30", 10, 10, 10, 30, "\"a\"", "\"b\""))
        assertFalse(DownloadRangeRepairPolicy.matches(206, "bytes 10-19/31", 10, 10, 10, 30, "\"a\"", "\"a\""))
        assertFalse(DownloadRangeRepairPolicy.matches(206, "bytes 11-20/30", 10, 10, 10, 30, "\"a\"", "\"a\""))
        assertFalse(DownloadRangeRepairPolicy.matches(206, "bytes 10-19/30", 11, 10, 10, 30, "\"a\"", "\"a\""))
        assertFalse(DownloadRangeRepairPolicy.matches(206, "bytes 10-19/30", 10, 10, 10, 30, "W/\"a\"", "W/\"a\""))
        assertFalse(DownloadRangeRepairPolicy.strongValidator("Wed, 21 Oct 2026 10:00:00 GMT"))
        assertNotEquals(DownloadIntegrity.sourceIdentity("https://cdn.example/movie.mp4?token=a"), DownloadIntegrity.sourceIdentity("https://cdn.example/movie.mp4?token=b"))
    }

    @Test fun storageUsesActualFilesAndWatchedCandidatesMatchTheSpecificProfileAndEpisode() = fixture { directory ->
        File(directory, "123_ep_123_S6_22.ts").writeBytes(ByteArray(100))
        File(directory, "123_ep_123_S6_22.captions").mkdirs()
        File(directory, "123_ep_123_S6_22.captions/en.vtt").writeBytes(ByteArray(20))
        File(directory, "other.mp4").writeBytes(ByteArray(50))
        val download = DownloadEntity("p1", "123_ep_123_S6_22", "123", "Series", "S6:E22", 999)
        val different = WatchProgressEntity("p1", "123", "ep_123_S6_21", 100, 100)
        val watched = WatchProgressEntity("p1", "123", "ep_123_S6_22", 95, 100)
        assertFalse(DownloadStorage.watched(download, listOf(different)))
        assertFalse(DownloadStorage.watched(download, listOf(watched.copy(profileId = "p2"))))
        val state = DownloadStorage.snapshot(directory, listOf(download, download), listOf(watched))
        assertEquals(170L, state.allDownloadBytes)
        assertEquals(120L, state.profileBytes)
        assertEquals(1, state.entries.size)
        assertTrue(state.entries.single().watched)
    }
}
