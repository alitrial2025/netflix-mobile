package com.example.data.download

import android.util.Log
import com.example.data.Caption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Save captions while their signed links are fresh; reuse valid files on video resume. */
internal class OfflineCaptionDownloader(
    private val directory: File,
    private val fetch: suspend (Caption) -> ByteArray
) {
    suspend fun download(captions: List<Caption>): List<Caption> {
        val candidates = captions.filter { it.type.lowercase() in setOf("vtt", "srt") }
            .distinctBy { listOf(it.url, it.languageCode, it.language) }.take(32)
        if (candidates.isEmpty()) return emptyList()
        check(directory.isDirectory || directory.mkdirs()) { "Could not create subtitle directory" }
        val saved = ConcurrentHashMap<Int, Caption>()
        val slots = Semaphore(3)
        // Optional unavailable tracks must not indefinitely delay the video transfer.
        withTimeoutOrNull(60_000L) {
            coroutineScope {
                candidates.mapIndexed { index, caption -> async {
                    slots.withPermit {
                        for (attempt in 0..1) {
                            currentCoroutineContext().ensureActive()
                            try {
                                val local = withTimeoutOrNull(15_000L) { save(caption) }
                                if (local != null) { saved[index] = local; break }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (error: Exception) {
                                // Never log signed URLs, cookie values or response bodies.
                                Log.w("DownloadManager", "Subtitle unavailable: ${error.javaClass.simpleName}")
                                if (error !is IOException || (error is DownloadHttpException &&
                                    error.code !in setOf(408, 429) && error.code < 500)) break
                            }
                            if (attempt == 0) delay(500L)
                        }
                    }
                } }.awaitAll()
            }
        }
        currentCoroutineContext().ensureActive()
        return saved.toSortedMap().values.toList()
    }

    private suspend fun save(caption: Caption): Caption {
        // A provider can rotate query signatures without changing the caption itself.
        val identity = listOf(URI(caption.url).path, caption.language, caption.languageCode).joinToString("\n")
        val name = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(24)
        for (type in listOf("vtt", "srt")) {
            val existing = File(directory, "$name.$type")
            if (existing.isFile && existing.length() <= MAX_BYTES && format(existing.readBytes()) == type)
                return caption.copy(url = existing.toURI().toString(), type = type)
        }
        val bytes = fetch(caption)
        currentCoroutineContext().ensureActive()
        require(bytes.size <= MAX_BYTES) { "Subtitles exceed supported size" }
        val type = format(bytes) ?: throw IOException("Invalid subtitle data")
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val file = File(directory, "$name.$type")
        val temp = File(directory, "$name.part")
        try {
            FileOutputStream(temp).use { output -> output.write(text.toByteArray(Charsets.UTF_8)); output.fd.sync() }
            currentCoroutineContext().ensureActive()
            check(temp.renameTo(file)) { "Could not save subtitles" }
        } finally { temp.delete() }
        return caption.copy(url = file.toURI().toString(), type = type)
    }

    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        private val srtCue = Regex("""(?m)^\s*\d{2}:\d{2}:\d{2}[,.]\d{3}\s+-->\s+\d{2}:\d{2}:\d{2}[,.]\d{3}""")
        private fun format(bytes: ByteArray): String? {
            val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").trimStart()
            return when {
                text.startsWith("WEBVTT") && (text.length == 6 || text[6].isWhitespace()) -> "vtt"
                srtCue.containsMatchIn(text) -> "srt"
                else -> null
            }
        }
    }
}
