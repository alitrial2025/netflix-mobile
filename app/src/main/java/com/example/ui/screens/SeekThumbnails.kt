package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import java.net.URI

internal data class SeekThumbnail(val startMs: Long, val endMs: Long, val imageUrl: String,
    val x: Int = 0, val y: Int = 0, val width: Int = 0, val height: Int = 0)

/** Optional provider VTT metadata; invalid cues never affect playback or seek coordinates. */
internal fun parseSeekThumbnails(text: String, baseUrl: String): List<SeekThumbnail> {
    fun milliseconds(value: String): Long? {
        val parts = value.trim().substringBefore(' ').replace(',', '.').split(':')
        if (parts.size !in 2..3) return null
        val seconds = parts.last().toDoubleOrNull() ?: return null
        val minutes = parts[parts.lastIndex - 1].toLongOrNull() ?: return null
        val hours = if (parts.size == 3) parts.first().toLongOrNull() ?: return null else 0
        if (seconds !in 0.0..<60.0 || minutes !in 0..59 || hours < 0) return null
        return ((hours * 3600 + minutes * 60 + seconds) * 1000).toLong()
    }
    return text.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n\\s*\n")).mapNotNull { block ->
        val lines = block.lines().map(String::trim).filter(String::isNotEmpty)
        val index = lines.indexOfFirst { "-->" in it }
        if (index < 0) return@mapNotNull null
        val times = lines[index].split("-->")
        if (times.size != 2) return@mapNotNull null
        val start = milliseconds(times[0]) ?: return@mapNotNull null
        val end = milliseconds(times[1]) ?: return@mapNotNull null
        if (end <= start) return@mapNotNull null
        val payload = lines.getOrNull(index + 1) ?: return@mapNotNull null
        val uri = runCatching { URI(baseUrl).resolve(payload.substringBefore('#')) }.getOrNull() ?: return@mapNotNull null
        if (uri.scheme != "https" || uri.host == null || uri.userInfo != null) return@mapNotNull null
        val coordinates = payload.substringAfter("#xywh=", "").split(',').mapNotNull(String::toIntOrNull)
        if ("#xywh=" in payload && (coordinates.size != 4 || coordinates.any { it < 0 } || coordinates[2] == 0 || coordinates[3] == 0)) return@mapNotNull null
        if (coordinates.size == 4) SeekThumbnail(start, end, uri.toString(), coordinates[0], coordinates[1], coordinates[2], coordinates[3])
        else SeekThumbnail(start, end, uri.toString())
    }.sortedBy { it.startMs }.take(5000)
}

internal fun seekThumbnailAt(cues: List<SeekThumbnail>, millis: Long): SeekThumbnail? {
    // Last start at/before the seek point, with exclusive end boundaries.
    var low = 0; var high = cues.lastIndex; var found = -1
    while (low <= high) { val middle = (low + high) ushr 1; if (cues[middle].startMs <= millis) { found = middle; low = middle + 1 } else high = middle - 1 }
    return cues.getOrNull(found)?.takeIf { millis < it.endMs }
}


private data class PreviewAtlas(val bitmap: Bitmap, val sample: Int)
private val seekAtlasCache by lazy { object : android.util.LruCache<String, PreviewAtlas>(8 * 1024) {
    override fun sizeOf(key: String, value: PreviewAtlas) = (value.bitmap.byteCount / 1024).coerceAtLeast(1)
} }

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private suspend fun loadPreview(cue: SeekThumbnail, headers: Map<String, String>): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val atlas = seekAtlasCache.get(cue.imageUrl) ?: run {
            val source = com.example.data.ScopedPlaybackHttp.factory(headers, cue.imageUrl).createDataSource()
            val bytes = try {
                source.open(androidx.media3.datasource.DataSpec(android.net.Uri.parse(cue.imageUrl)))
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val count = source.read(buffer, 0, buffer.size)
                    if (count < 0) break
                    require(output.size() + count <= 4 * 1024 * 1024)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } finally { source.close() }
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > 1024L * 1024) sample *= 2
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext null
            PreviewAtlas(bitmap, sample).also { seekAtlasCache.put(cue.imageUrl, it) }
        }
        val bitmap = atlas.bitmap
        val frame = if (cue.width > 0 && cue.height > 0) {
            val x = cue.x / atlas.sample; val y = cue.y / atlas.sample
            val width = (cue.width / atlas.sample).coerceAtLeast(1); val height = (cue.height / atlas.sample).coerceAtLeast(1)
            if (x.toLong() + width > bitmap.width || y.toLong() + height > bitmap.height) return@withContext null
            Bitmap.createBitmap(bitmap, x, y, width, height)
        } else bitmap
        Bitmap.createScaledBitmap(frame, 320, 180, true)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }
}

@Composable
internal fun SeekThumbnailFrame(cue: SeekThumbnail, headers: Map<String, String>, description: String) {
    val bitmap by produceState<Bitmap?>(null, cue, headers) {
        value = null
        value = loadPreview(cue, headers)
    }
    bitmap?.let { image -> Image(remember(image) { image.asImageBitmap() }, description,
        contentScale = ContentScale.Crop, modifier = Modifier.width(160.dp).height(90.dp)) }
}
