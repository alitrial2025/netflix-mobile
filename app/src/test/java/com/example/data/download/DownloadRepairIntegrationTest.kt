package com.example.data.download

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.NetMirrorResolver
import com.example.data.NetMirrorStream
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.repository.NetflixRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DownloadRepairIntegrationTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val root get() = File(app.filesDir, "downloads")
    private fun media(key: String) = MediaItem(key, "Repair fixture", MediaType.MOVIE, "", "", 90, "16+", 2026, "2h", genres = emptyList(), cast = emptyList(), director = "")
    private fun packet(value: Byte) = ByteArray(188 * 3) { value }.apply { for (index in indices step 188) this[index] = 0x47 }
    private fun fixture(key: String, block: suspend (NetflixDownloadManager, NetflixRepository) -> Unit) = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build())
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val repository = NetflixRepository(db.netflixDao())
        val manager = NetflixDownloadManager(app, repository, NetMirrorResolver(app), accountIdProvider = { "account" })
        try { block(manager, repository) }
        finally {
            manager.cancelAndJoinTransfers(); manager.close(); WorkManager.getInstance(app).cancelAllWork().result.get(); db.close()
            root.listFiles().orEmpty().filter { it.name.startsWith("$key.") }.forEach { it.deleteRecursively() }
            DownloadRequestStore(File(app.noBackupFilesDir, "download-requests")).remove(key)
        }
    }
    private fun stream(url: String) = NetMirrorStream(url, emptyMap(), emptyList(), "Fixture", Long.MAX_VALUE, "Repair fixture")

    @Test fun completedHlsRepairDownloadsOnlyTheCorruptedSegmentAfterSignedUrlRenewal() = fixture("repair_hls") { manager, repository ->
        val server = MockWebServer(); val paths = CopyOnWriteArrayList<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath; paths += path
                if (path.endsWith("m3u8")) return MockResponse().setBody("#EXTM3U\n#EXTINF:6,\n1.jpg\n#EXTINF:6,\n2.jpg\n#EXTINF:6,\n3.jpg\n#EXT-X-ENDLIST\n")
                return MockResponse().setBody(Buffer().write(packet(path.substringAfterLast('/').substringBefore('.').toByte())))
            }
        }
        server.start(); val item = media("repair_hls")
        try {
            manager.startOrResumeDownload("profile", item, isWifiOnly = false)
            manager.executeResolvedDownload("profile", item, null, item.id, null, true, stream(server.url("/video.m3u8?token=first").toString()))
            val destination = File(repository.getDownloadsOnce("profile").single().localFilePath!!)
            assertEquals(DownloadHealth.HEALTHY, manager.inspectDownload("profile", item.id).health)
            assertFalse(File(root, "${item.id}.segments").exists())
            RandomAccessFile(destination, "rw").use { it.seek(packet(1).size.toLong() + 5); it.write(99) }
            assertEquals(DownloadHealth.DAMAGED, manager.inspectDownload("profile", item.id).health)
            manager.startOrResumeDownload("profile", item, isWifiOnly = false)
            manager.executeResolvedDownload("profile", item, null, item.id, null, true, stream(server.url("/video.m3u8?token=renewed").toString()))
            assertArrayEquals(packet(1) + packet(2) + packet(3), destination.readBytes())
            assertEquals(1, paths.count { it == "/1.jpg" }); assertEquals(2, paths.count { it == "/2.jpg" }); assertEquals(1, paths.count { it == "/3.jpg" })
            assertEquals(DownloadHealth.HEALTHY, manager.inspectDownload("profile", item.id).health)
        } finally { server.shutdown() }
    }

    @Test fun deletingAQueuedRepairCancelsItsIntentAndRetainsAnotherProfilesSharedAsset() = fixture("repair_shared") { manager, repository ->
        val key = "repair_shared"
        val item = media(key)
        root.mkdirs()
        val saved = File(root, "$key.mp4").apply { writeText("verified saved media") }
        DownloadIntegrity.write(root, key, DownloadIntegrity.directReceipt(saved, saved.name, "\"a\""))
        for (profile in listOf("profile", "other")) {
            repository.addDownload(profile, item.id, item.title, null, 1, null, saved.path, "", "[]", false)
        }
        manager.startOrResumeDownload("profile", item, isWifiOnly = true)
        val requestStore = DownloadRequestStore(File(app.noBackupFilesDir, "download-requests"))
        assertTrue(requestStore.all().any { it.task.downloadKey == key })
        manager.deleteCompletedDownload("profile", key)
        withTimeout(5_000) { while (repository.getDownloadsOnce("profile").isNotEmpty()) delay(10) }
        assertFalse(requestStore.all().any { it.task.downloadKey == key })
        assertTrue(saved.isFile)
        assertEquals(1, repository.getDownloadsOnce("other").size)
        assertTrue(DownloadIntegrity.receiptFile(root, key).isFile)
        manager.deleteCompletedDownload("other", key)
        withTimeout(5_000) { while (saved.exists()) delay(10) }
        assertFalse(DownloadIntegrity.receiptFile(root, key).exists())
    }

    @Test fun directRepairRequestsOnlyBadChunkAndChangedValidatorsRestartWithoutMixingEditions() = fixture("repair_direct") { manager, repository ->
        val server = MockWebServer(); val ranges = CopyOnWriteArrayList<String>()
        var video = ByteArray(DownloadIntegrity.CHUNK_BYTES * 2 + 20) { (it % 113).toByte() }
        var validator = "\"edition-a\""
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range"); ranges += range ?: "full"
                if (range != null && request.getHeader("If-Range") == validator) {
                    val (start, end) = range.removePrefix("bytes=").split('-').map { it.toInt() }
                    return MockResponse().setResponseCode(206).setHeader("ETag", validator)
                        .setHeader("Content-Range", "bytes $start-$end/${video.size}").setBody(Buffer().write(video.copyOfRange(start, end + 1)))
                }
                return MockResponse().setHeader("ETag", validator).setBody(Buffer().write(video))
            }
        }
        server.start(); val item = media("repair_direct"); val source = stream(server.url("/video.mp4").toString())
        try {
            manager.startOrResumeDownload("profile", item, isWifiOnly = false)
            manager.executeResolvedDownload("profile", item, null, item.id, null, true, source)
            val destination = File(repository.getDownloadsOnce("profile").single().localFilePath!!)
            RandomAccessFile(destination, "rw").use { it.seek(DownloadIntegrity.CHUNK_BYTES + 5L); it.write(255) }
            manager.startOrResumeDownload("profile", item, isWifiOnly = false)
            manager.executeResolvedDownload("profile", item, null, item.id, null, true, source)
            assertArrayEquals(video, destination.readBytes())
            assertEquals(listOf("full", "bytes=0-0", "bytes=1048576-2097151"), ranges.toList())
            assertEquals(DownloadHealth.HEALTHY, manager.inspectDownload("profile", item.id).health)
            RandomAccessFile(destination, "rw").use { it.seek(5); it.write(255) }
            video = ByteArray(video.size) { 8 }; validator = "\"edition-b\""
            manager.startOrResumeDownload("profile", item, isWifiOnly = false)
            manager.executeResolvedDownload("profile", item, null, item.id, null, true, source)
            assertArrayEquals(video, destination.readBytes())
            assertEquals(listOf("bytes=0-0", "full"), ranges.takeLast(2))
            assertEquals("\"edition-b\"", DownloadIntegrity.read(root, item.id)!!.validator)
            assertEquals(DownloadHealth.HEALTHY, manager.inspectDownload("profile", item.id).health)
            // An ETag is scoped to its resource, so a different URL cannot borrow it.
            video = ByteArray(video.size) { 9 }
            RandomAccessFile(destination, "rw").use { it.seek(5); it.write(255) }
            manager.startOrResumeDownload("profile", item, isWifiOnly = false)
            manager.executeResolvedDownload("profile", item, null, item.id, null, true, stream(server.url("/video.mp4?signature=new").toString()))
            assertEquals("full", ranges.last())
            assertArrayEquals(video, destination.readBytes())
            assertEquals(DownloadHealth.HEALTHY, manager.inspectDownload("profile", item.id).health)
        } finally { server.shutdown() }
    }
}
