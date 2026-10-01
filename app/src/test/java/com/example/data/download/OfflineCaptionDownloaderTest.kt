package com.example.data.download

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.Caption
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OfflineCaptionDownloaderTest {
    private fun directory() = File(ApplicationProvider.getApplicationContext<Application>().cacheDir,
        "caption-test-${System.nanoTime()}").apply { mkdirs() }
    private val vtt = "WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nHello\n"
    private val srt = "1\n00:00:01,000 --> 00:00:03,000\nHello\n"

    @Test fun savesAllLanguagesLocallyAndDetectsBomAndActualSubtitleFormat() = runBlocking {
        val root = directory()
        val captions = (0..7).map { Caption("https://provider.test/$it?in=secret", "Language $it", "vtt", "lang$it") }
        try {
            val saved = OfflineCaptionDownloader(root) { caption ->
                (if (caption.languageCode == "lang0") srt else "\uFEFF$vtt").toByteArray()
            }.download(captions + Caption("https://provider.test/thumbs", "Preview", "thumbnails"))
            assertEquals(8, saved.size)
            assertEquals("srt", saved.first().type)
            assertEquals(captions.map { it.languageCode }, saved.map { it.languageCode })
            saved.forEach { caption ->
                assertEquals("file", URI(caption.url).scheme)
                val file = File(URI(caption.url))
                assertTrue(file.isFile)
                assertFalse(file.readText().startsWith("\uFEFF"))
                assertFalse(file.name.contains("secret"))
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun signedUrlRotationAndProcessRestartReuseCompleteCaptionFiles() = runBlocking {
        val root = directory()
        val caption = Caption("https://provider.test/english.vtt?in=old", "English", "vtt", "en")
        try {
            val first = OfflineCaptionDownloader(root) { vtt.toByteArray() }.download(listOf(caption)).single()
            val restored = OfflineCaptionDownloader(root) { throw AssertionError("Should reuse the saved caption") }
                .download(listOf(caption.copy(url = "https://provider.test/english.vtt?in=new"))).single()
            assertEquals(first, restored)
        } finally { root.deleteRecursively() }
    }

    @Test fun temporaryFailuresRetryButRejectedAndInvalidResponsesNeverBecomeCaptionFiles() = runBlocking {
        val root = directory()
        val attempts = AtomicInteger()
        val rejected = AtomicInteger()
        try {
            val saved = OfflineCaptionDownloader(root) { caption -> when (caption.languageCode) {
                "en" -> if (attempts.incrementAndGet() == 1) throw DownloadHttpException(503) else vtt.toByteArray()
                "fr" -> { rejected.incrementAndGet(); throw DownloadHttpException(403) }
                else -> "<html>expired token --></html>".toByteArray()
            } }.download(listOf(Caption("https://provider.test/en", "English", "vtt", "en"),
                Caption("https://provider.test/fr", "French", "vtt", "fr"),
                Caption("https://provider.test/de", "German", "vtt", "de")))
            assertEquals(listOf("en"), saved.map { it.languageCode })
            assertEquals(2, attempts.get())
            assertEquals(1, rejected.get())
            assertEquals(1, root.listFiles()!!.size)
        } finally { root.deleteRecursively() }
    }

    @Test fun cancellationStopsCaptionWorkAndNeverPublishesAPartialFile() = runBlocking {
        val root = directory()
        val started = CompletableDeferred<Unit>()
        try {
            val job = launch {
                OfflineCaptionDownloader(root) { started.complete(Unit); awaitCancellation() }
                    .download(listOf(Caption("https://provider.test/en", "English", "vtt")))
                fail("Cancellation must propagate")
            }
            started.await()
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertTrue(root.listFiles()!!.isEmpty())
        } finally { root.deleteRecursively() }
    }
}
