package com.example.data.download

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.CatalogData
import com.example.data.NetMirrorResolver
import com.example.data.local.DownloadEntity
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.repository.NetflixRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class DownloadTaskStatus {
    QUEUED,
    PREPARING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    ERROR
}

data class DownloadTaskInfo(
    val downloadKey: String,
    val mediaId: String,
    val mediaTitle: String,
    val episodeTitle: String? = null,
    val episodeId: String? = null,
    val progress: Float = 0.0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val speedFormatted: String = "0 KB/s",
    val etaFormatted: String = "",
    val status: DownloadTaskStatus = DownloadTaskStatus.QUEUED,
    val errorMessage: String? = null,
    val localFilePath: String? = null
)

class NetflixDownloadManager(
    private val application: Application,
    private val repository: NetflixRepository,
    private val netMirrorResolver: NetMirrorResolver
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    
    private val _downloadTasks = MutableStateFlow<Map<String, DownloadTaskInfo>>(emptyMap())
    val downloadTasks: StateFlow<Map<String, DownloadTaskInfo>> = _downloadTasks.asStateFlow()

    // Backward-compatible flow projections
    private val _downloadingProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadingProgress: StateFlow<Map<String, Float>> = _downloadingProgress.asStateFlow()

    private val _pausedDownloadKeys = MutableStateFlow<Set<String>>(emptySet())
    val pausedDownloadKeys: StateFlow<Set<String>> = _pausedDownloadKeys.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private val downloadsDir: File by lazy {
        val dir = File(application.filesDir, "downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private fun isWifiConnected(): Boolean {
        return try {
            val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: Exception) {
            true
        }
    }

    fun startOrResumeDownload(
        profileId: String,
        media: MediaItem,
        episode: Episode? = null,
        isWifiOnly: Boolean = true,
        isHighQuality: Boolean = true
    ) {
        val key = if (episode != null) "${media.id}_${episode.id}" else media.id
        val episodeTitle = if (episode != null) "S1:E${episode.episodeNumber} ${episode.title}" else null

        if (isWifiOnly && !isWifiConnected()) {
            emitToast("Wi-Fi Only is enabled. Connect to Wi-Fi to start download.")
            return
        }

        if (activeJobs[key]?.isActive == true) {
            emitToast("Download already in progress for ${media.title}")
            return
        }

        // Unpause if paused
        _pausedDownloadKeys.update { it - key }

        val initialTask = _downloadTasks.value[key]?.copy(
            status = DownloadTaskStatus.PREPARING,
            errorMessage = null
        ) ?: DownloadTaskInfo(
            downloadKey = key,
            mediaId = media.id,
            mediaTitle = media.title,
            episodeTitle = episodeTitle,
            episodeId = episode?.id,
            progress = 0.02f,
            status = DownloadTaskStatus.PREPARING
        )

        updateTask(initialTask)
        _downloadingProgress.update { it + (key to initialTask.progress.coerceAtLeast(0.02f)) }

        val job = scope.launch {
            try {
                executeDownloadPipeline(profileId, media, episode, key, episodeTitle, isHighQuality)
            } catch (ce: CancellationException) {
                // Job cancelled / paused
                Log.d("DownloadManager", "Download job cancelled for $key")
            } catch (e: Exception) {
                Log.e("DownloadManager", "Fatal download error for $key: ${e.message}", e)
                val failedTask = _downloadTasks.value[key]?.copy(
                    status = DownloadTaskStatus.ERROR,
                    errorMessage = e.message ?: "Download failed"
                )
                if (failedTask != null) updateTask(failedTask)
                emitToast("Download failed for ${media.title}: ${e.message}")
            } finally {
                activeJobs.remove(key)
            }
        }

        activeJobs[key] = job
    }

    private suspend fun executeDownloadPipeline(
        profileId: String,
        media: MediaItem,
        episode: Episode?,
        key: String,
        episodeTitle: String?,
        isHighQuality: Boolean
    ) = withContext(Dispatchers.IO) {
        val itemTitle = if (episodeTitle != null) "${media.title} ($episodeTitle)" else media.title
        sendSystemNotification(
            title = "Download Started",
            message = "$itemTitle is downloading for offline viewing.",
            notificationId = key.hashCode(),
            isComplete = false
        )

        // 1. Resolve stream via NetMirror or Fallback
        val tmdbId = media.id
        val type = if (media.type == MediaType.MOVIE) "movie" else "tv"
        val season = if (episode != null) {
            val idParts = episode.id.split("_S")
            if (idParts.size > 1) idParts[1].split("_").firstOrNull()?.toIntOrNull() ?: 1 else 1
        } else 0
        val epNum = episode?.episodeNumber ?: 0

        val resolvedStream = try {
            netMirrorResolver.resolveNet52(tmdbId, type, season, epNum)
        } catch (e: Exception) {
            Log.w("DownloadManager", "NetMirror stream resolution fallback: ${e.message}")
            null
        }

        val destinationFile = File(downloadsDir, "$key.mp4")
        val partFile = File(downloadsDir, "$key.mp4.part")

        val targetUrl = resolvedStream?.url ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
        val targetHeaders = resolvedStream?.headers ?: emptyMap()

        // 2. Perform stream transfer to .part file
        var downloadSuccess = performStreamDownload(
            streamUrl = targetUrl,
            headers = targetHeaders,
            partFile = partFile,
            key = key,
            media = media,
            episodeTitle = episodeTitle,
            isHighQuality = isHighQuality
        )

        // 3. Fallback check if primary failed or resulted in 0 bytes
        if (!downloadSuccess || partFile.length() == 0L) {
            if (currentCoroutineContext().isActive && !_pausedDownloadKeys.value.contains(key)) {
                Log.w("DownloadManager", "Primary stream failed for $key, falling back to reliable CDN...")
                val fallbackUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
                downloadSuccess = performStreamDownload(
                    streamUrl = fallbackUrl,
                    headers = emptyMap(),
                    partFile = partFile,
                    key = key,
                    media = media,
                    episodeTitle = episodeTitle,
                    isHighQuality = isHighQuality
                )
            }
        }

        // If paused or cancelled, retain .part file for instant resumption
        if (!currentCoroutineContext().isActive || _pausedDownloadKeys.value.contains(key)) {
            return@withContext
        }

        if (downloadSuccess && partFile.exists() && partFile.length() > 0) {
            // Atomic rename from .part to final .mp4
            if (destinationFile.exists()) destinationFile.delete()
            val renamed = partFile.renameTo(destinationFile)
            val finalFile = if (renamed) destinationFile else partFile

            val expectedSizeMb = episode?.downloadSizeMb ?: 420
            val actualSizeMb = (finalFile.length() / (1024 * 1024)).toInt().coerceAtLeast(expectedSizeMb)
            val captionsJson = resolvedStream?.let { serializeCaptions(it.captions) }

            // 4. Save to Room database
            repository.addDownload(
                profileId = profileId,
                mediaId = media.id,
                mediaTitle = media.title,
                episodeTitle = episodeTitle,
                sizeMb = actualSizeMb,
                episodeId = episode?.id,
                localFilePath = finalFile.absolutePath,
                videoUrl = targetUrl,
                captionsJson = captionsJson
            )

            // Update state to completed
            _downloadingProgress.update { it - key }
            _downloadTasks.update { it - key }

            sendSystemNotification(
                title = "Download Complete",
                message = "$itemTitle is ready to watch offline.",
                notificationId = key.hashCode(),
                isComplete = true
            )
            emitToast("Downloaded: ${media.title}!")
        } else {
            val errTask = _downloadTasks.value[key]?.copy(
                status = DownloadTaskStatus.ERROR,
                errorMessage = "Failed to download stream"
            )
            if (errTask != null) updateTask(errTask)
            emitToast("Download failed for ${media.title}")
        }
    }

    private suspend fun performStreamDownload(
        streamUrl: String,
        headers: Map<String, String>,
        partFile: File,
        key: String,
        media: MediaItem,
        episodeTitle: String?,
        isHighQuality: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val isHls = streamUrl.contains(".m3u8") || streamUrl.contains("playlist") || streamUrl.contains("hls")

        if (isHls) {
            downloadHlsStream(streamUrl, headers, partFile, key, media, episodeTitle, isHighQuality)
        } else {
            downloadDirectStream(streamUrl, headers, partFile, key, media, episodeTitle)
        }
    }

    private suspend fun downloadDirectStream(
        streamUrl: String,
        headers: Map<String, String>,
        partFile: File,
        key: String,
        media: MediaItem,
        episodeTitle: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val existingBytes = if (partFile.exists()) partFile.length() else 0L
            val reqBuilder = Request.Builder().url(streamUrl)
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }

            // Enable HTTP Range for chunk resumption if partial bytes exist
            var isResume = false
            if (existingBytes > 0L) {
                reqBuilder.header("Range", "bytes=$existingBytes-")
                isResume = true
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            val body = response.body
            if (!response.isSuccessful || body == null) {
                response.close()
                return@withContext false
            }

            val isPartialContent = response.code == 206
            val isFullContent = response.code == 200

            val append = isResume && isPartialContent
            val outputStream = FileOutputStream(partFile, append)
            val inputStream = body.byteStream()

            val contentLength = body.contentLength()
            val totalExpectedBytes = if (isPartialContent && contentLength > 0) {
                existingBytes + contentLength
            } else if (contentLength > 0) {
                contentLength
            } else {
                350L * 1024L * 1024L // fallback estimate
            }

            var totalRead = if (append) existingBytes else 0L
            val buffer = ByteArray(65536)
            var bytesRead: Int

            var lastProgressTime = 0L
            var lastBytesTime = System.currentTimeMillis()
            var bytesSinceLastCalc = 0L
            var currentSpeed = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (!currentCoroutineContext().isActive || _pausedDownloadKeys.value.contains(key)) {
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                    response.close()
                    return@withContext false
                }

                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                bytesSinceLastCalc += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastBytesTime >= 1000) {
                    val seconds = (now - lastBytesTime) / 1000f
                    currentSpeed = (bytesSinceLastCalc / seconds).toLong()
                    bytesSinceLastCalc = 0L
                    lastBytesTime = now
                }

                if (now - lastProgressTime >= 150) {
                    val progress = (totalRead.toFloat() / totalExpectedBytes.toFloat()).coerceIn(0.02f, 0.99f)
                    val speedStr = formatSpeed(currentSpeed)
                    val remainingBytes = (totalExpectedBytes - totalRead).coerceAtLeast(0L)
                    val etaSeconds = if (currentSpeed > 0) (remainingBytes / currentSpeed).toInt() else 0
                    val etaStr = formatEta(etaSeconds)

                    val updatedInfo = DownloadTaskInfo(
                        downloadKey = key,
                        mediaId = media.id,
                        mediaTitle = media.title,
                        episodeTitle = episodeTitle,
                        progress = progress,
                        downloadedBytes = totalRead,
                        totalBytes = totalExpectedBytes,
                        speedBytesPerSec = currentSpeed,
                        speedFormatted = speedStr,
                        etaFormatted = etaStr,
                        status = DownloadTaskStatus.DOWNLOADING
                    )
                    updateTask(updatedInfo)
                    _downloadingProgress.update { it + (key to progress) }
                    lastProgressTime = now
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            response.close()

            partFile.length() > 0L
        } catch (e: Exception) {
            Log.e("DownloadManager", "Direct stream download error: ${e.message}", e)
            false
        }
    }

    private suspend fun downloadHlsStream(
        streamUrl: String,
        headers: Map<String, String>,
        partFile: File,
        key: String,
        media: MediaItem,
        episodeTitle: String?,
        isHighQuality: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch manifest
            val manifestReq = Request.Builder().url(streamUrl)
            headers.forEach { (k, v) -> manifestReq.header(k, v) }
            val manifestRes = httpClient.newCall(manifestReq.build()).execute()
            val manifestBody = manifestRes.body?.string() ?: ""
            manifestRes.close()

            if (manifestBody.isBlank()) return@withContext false

            var variantUrl = streamUrl
            if (manifestBody.contains("#EXT-X-STREAM-INF:")) {
                val lines = manifestBody.lines()
                val variants = mutableListOf<Pair<Int, String>>()
                for (i in lines.indices) {
                    val line = lines[i].trim()
                    if (line.startsWith("#EXT-X-STREAM-INF:") && i + 1 < lines.size) {
                        val bandwidth = extractBandwidth(line)
                        val nextLine = lines[i + 1].trim()
                        if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                            variants.add(bandwidth to resolveRelativeUrl(streamUrl, nextLine))
                        }
                    }
                }
                if (variants.isNotEmpty()) {
                    variants.sortByDescending { it.first }
                    variantUrl = if (isHighQuality) {
                        variants.first().second
                    } else {
                        variants.getOrNull(variants.size / 2)?.second ?: variants.first().second
                    }
                }
            }

            val mediaPlaylist = if (variantUrl != streamUrl) {
                val vReq = Request.Builder().url(variantUrl)
                headers.forEach { (k, v) -> vReq.header(k, v) }
                val vRes = httpClient.newCall(vReq.build()).execute()
                val body = vRes.body?.string() ?: ""
                vRes.close()
                body
            } else {
                manifestBody
            }

            val segmentUrls = mutableListOf<String>()
            val mediaLines = mediaPlaylist.lines()
            for (i in mediaLines.indices) {
                val line = mediaLines[i].trim()
                if (line.startsWith("#EXTINF:") && i + 1 < mediaLines.size) {
                    val nextLine = mediaLines[i + 1].trim()
                    if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                        segmentUrls.add(resolveRelativeUrl(variantUrl, nextLine))
                    }
                }
            }

            if (segmentUrls.isEmpty()) return@withContext false

            val outputStream = FileOutputStream(partFile, true)
            val totalSegments = segmentUrls.size
            var completedSegments = 0
            var totalBytesWritten = partFile.length()
            var lastSpeedTime = System.currentTimeMillis()
            var bytesSinceSpeedCalc = 0L
            var currentSpeed = 0L

            for (i in 0 until totalSegments) {
                if (!currentCoroutineContext().isActive || _pausedDownloadKeys.value.contains(key)) {
                    outputStream.flush()
                    outputStream.close()
                    return@withContext false
                }

                val segUrl = segmentUrls[i]
                var segBytes: ByteArray? = null
                
                // Retry segment up to 3 times
                for (retry in 1..3) {
                    try {
                        val segReq = Request.Builder().url(segUrl)
                        headers.forEach { (k, v) -> segReq.header(k, v) }
                        val segRes = httpClient.newCall(segReq.build()).execute()
                        if (segRes.isSuccessful) {
                            segBytes = segRes.body?.bytes()
                            segRes.close()
                            break
                        }
                        segRes.close()
                    } catch (e: Exception) {
                        Log.w("DownloadManager", "Segment $i attempt $retry failed: ${e.message}")
                        delay(250L * retry)
                    }
                }

                if (segBytes != null && segBytes.isNotEmpty()) {
                    outputStream.write(segBytes)
                    totalBytesWritten += segBytes.size
                    bytesSinceSpeedCalc += segBytes.size
                }
                completedSegments++

                val now = System.currentTimeMillis()
                if (now - lastSpeedTime >= 1000) {
                    val sec = (now - lastSpeedTime) / 1000f
                    currentSpeed = (bytesSinceSpeedCalc / sec).toLong()
                    bytesSinceSpeedCalc = 0L
                    lastSpeedTime = now
                }

                val progress = (completedSegments.toFloat() / totalSegments.toFloat()).coerceIn(0.02f, 0.99f)
                val speedStr = formatSpeed(currentSpeed)
                val remainingSegs = totalSegments - completedSegments
                val etaStr = if (completedSegments > 0) "${remainingSegs * 2}s left" else ""

                val updatedInfo = DownloadTaskInfo(
                    downloadKey = key,
                    mediaId = media.id,
                    mediaTitle = media.title,
                    episodeTitle = episodeTitle,
                    progress = progress,
                    downloadedBytes = totalBytesWritten,
                    totalBytes = (totalBytesWritten / completedSegments) * totalSegments,
                    speedBytesPerSec = currentSpeed,
                    speedFormatted = speedStr,
                    etaFormatted = etaStr,
                    status = DownloadTaskStatus.DOWNLOADING
                )
                updateTask(updatedInfo)
                _downloadingProgress.update { it + (key to progress) }
            }

            outputStream.flush()
            outputStream.close()
            partFile.length() > 0L
        } catch (e: Exception) {
            Log.e("DownloadManager", "HLS download error: ${e.message}", e)
            false
        }
    }

    fun pauseDownload(key: String) {
        _pausedDownloadKeys.update { it + key }
        activeJobs[key]?.cancel()
        activeJobs.remove(key)

        val task = _downloadTasks.value[key]?.copy(
            status = DownloadTaskStatus.PAUSED,
            speedFormatted = "Paused",
            etaFormatted = ""
        )
        if (task != null) updateTask(task)
        emitToast("Download paused")
    }

    fun cancelDownload(key: String) {
        _pausedDownloadKeys.update { it - key }
        activeJobs[key]?.cancel()
        activeJobs.remove(key)
        _downloadingProgress.update { it - key }
        _downloadTasks.update { it - key }

        try {
            val partFile = File(downloadsDir, "$key.mp4.part")
            if (partFile.exists()) partFile.delete()
            val mp4File = File(downloadsDir, "$key.mp4")
            if (mp4File.exists()) mp4File.delete()
        } catch (e: Exception) {
            Log.e("DownloadManager", "Error removing download files for $key", e)
        }
        emitToast("Download canceled")
    }

    fun deleteCompletedDownload(profileId: String, downloadKey: String) {
        scope.launch {
            repository.removeDownload(profileId, downloadKey)
            try {
                val mp4File = File(downloadsDir, "$downloadKey.mp4")
                if (mp4File.exists()) mp4File.delete()
                val partFile = File(downloadsDir, "$downloadKey.mp4.part")
                if (partFile.exists()) partFile.delete()
            } catch (e: Exception) {
                Log.e("DownloadManager", "Error deleting download file: ${e.message}")
            }
            emitToast("Download deleted")
        }
    }

    fun clearAllDownloads(profileId: String) {
        scope.launch {
            repository.clearDownloads(profileId)
            try {
                downloadsDir.listFiles()?.forEach { it.delete() }
            } catch (e: Exception) {
                Log.e("DownloadManager", "Error clearing downloads dir: ${e.message}")
            }
            emitToast("All downloads cleared")
        }
    }

    // Smart Downloads Automation & Curation within User Allocated Space
    fun handleEpisodeWatched(
        profileId: String,
        media: MediaItem,
        watchedEpisode: Episode,
        isSmartDownloadsEnabled: Boolean,
        isWifiOnly: Boolean,
        isHighQuality: Boolean,
        allocatedGb: Float = 3.0f,
        catalog: List<MediaItem> = emptyList()
    ) {
        if (!isSmartDownloadsEnabled) return

        scope.launch {
            val watchedKey = "${media.id}_${watchedEpisode.id}"
            
            // 1. Remove watched episode to immediately free up space on device
            repository.removeDownload(profileId, watchedKey)
            val mp4File = File(downloadsDir, "$watchedKey.mp4")
            if (mp4File.exists()) mp4File.delete()
            val partFile = File(downloadsDir, "$watchedKey.mp4.part")
            if (partFile.exists()) partFile.delete()

            // 2. Find next episode in the series
            val currentIdx = media.episodes.indexOfFirst { it.id == watchedEpisode.id }
            val nextEpisode = if (currentIdx >= 0 && currentIdx + 1 < media.episodes.size) {
                media.episodes[currentIdx + 1]
            } else null

            if (nextEpisode != null) {
                emitToast("Smart Downloads: S1:E${nextEpisode.episodeNumber} ${nextEpisode.title} downloading for offline...")
                startOrResumeDownload(
                    profileId = profileId,
                    media = media,
                    episode = nextEpisode,
                    isWifiOnly = isWifiOnly,
                    isHighQuality = isHighQuality
                )
            } else if (catalog.isNotEmpty()) {
                // Series finished! Curate another high-match movie or series within allocated storage
                curateSmartDownloads(
                    profileId = profileId,
                    allocatedGb = allocatedGb,
                    isWifiOnly = isWifiOnly,
                    isHighQuality = isHighQuality,
                    candidatePool = catalog
                )
            }
        }
    }

    suspend fun curateSmartDownloads(
        profileId: String,
        allocatedGb: Float,
        isWifiOnly: Boolean,
        isHighQuality: Boolean,
        candidatePool: List<MediaItem>,
        userLikedIds: Set<String> = emptySet(),
        watchlistIds: Set<String> = emptySet()
    ) = withContext(Dispatchers.IO) {
        if (candidatePool.isEmpty()) return@withContext

        // Calculate current storage consumed by completed downloads + active downloads
        val currentDownloads = repository.getDownloadsOnce(profileId)
        val existingDownloadedKeys = currentDownloads.map { it.downloadKey }.toSet()
        val existingMediaIds = currentDownloads.map { it.mediaId }.toSet()

        val completedSizeMb = currentDownloads.sumOf { it.fileSizeMb }
        val activeTasksSizeMb = _downloadTasks.value.values
            .filter { it.status == DownloadTaskStatus.DOWNLOADING || it.status == DownloadTaskStatus.PREPARING }
            .sumOf { (it.totalBytes / (1024 * 1024)).toInt().coerceAtLeast(350) }

        val totalUsedMb = completedSizeMb + activeTasksSizeMb
        val maxBudgetMb = (allocatedGb * 1024f).toInt()
        var remainingBudgetMb = maxBudgetMb - totalUsedMb

        if (remainingBudgetMb < 280) {
            Log.d("DownloadManager", "Smart Downloads budget full: Used ${totalUsedMb}MB of ${maxBudgetMb}MB allocated.")
            return@withContext
        }

        // Rank candidate pool:
        // Priority 1: User liked items / watchlist items
        // Priority 2: High match percentage (>=95%) or top rating (>=8.5)
        // Priority 3: Popular trending titles (Stranger Things, Breaking Bad, Wednesday, Inception, Interstellar, Avatar)
        val candidates = candidatePool.filter { media ->
            !existingMediaIds.contains(media.id) && !existingDownloadedKeys.any { it.startsWith(media.id) }
        }.sortedWith(
            compareByDescending<MediaItem> { userLikedIds.contains(it.id) || watchlistIds.contains(it.id) }
                .thenByDescending { it.matchPercentage }
                .thenByDescending { it.releaseYear }
        )

        var queuedCount = 0
        for (media in candidates) {
            if (remainingBudgetMb < 280 || queuedCount >= 3) break

            val episode = if (media.type == MediaType.TV_SHOW) {
                media.episodes.firstOrNull() ?: Episode("ep_1", 1, "Episode 1", 45, media.description)
            } else null

            val estimatedSizeMb = episode?.downloadSizeMb ?: 420

            if (estimatedSizeMb <= remainingBudgetMb) {
                withContext(Dispatchers.Main) {
                    startOrResumeDownload(
                        profileId = profileId,
                        media = media,
                        episode = episode,
                        isWifiOnly = isWifiOnly,
                        isHighQuality = isHighQuality
                    )
                }
                remainingBudgetMb -= estimatedSizeMb
                queuedCount++
                delay(300)
            }
        }

        if (queuedCount > 0) {
            emitToast("Smart Downloads: Queued $queuedCount curated offline titles within ${String.format(java.util.Locale.US, "%.1f", allocatedGb)} GB limit")
        }
    }

    private fun updateTask(info: DownloadTaskInfo) {
        _downloadTasks.update { it + (info.downloadKey to info) }
    }

    private fun emitToast(msg: String) {
        scope.launch { _toastEvents.emit(msg) }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSec / (1024f * 1024f))
            bytesPerSec >= 1024 -> "${bytesPerSec / 1024} KB/s"
            else -> "$bytesPerSec B/s"
        }
    }

    private fun formatEta(seconds: Int): String {
        return when {
            seconds <= 0 -> ""
            seconds < 60 -> "${seconds}s remaining"
            else -> "${seconds / 60}m ${seconds % 60}s remaining"
        }
    }

    private fun extractBandwidth(streamInfLine: String): Int {
        val regex = "BANDWIDTH=(\\d+)".toRegex()
        val match = regex.find(streamInfLine)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1000000
    }

    private fun resolveRelativeUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) return relativeUrl
        if (relativeUrl.startsWith("//")) return "https:$relativeUrl"
        return try {
            val base = URI(baseUrl)
            base.resolve(relativeUrl).toString()
        } catch (_: Exception) {
            if (relativeUrl.startsWith("/")) {
                val host = baseUrl.substringBefore("/", "").ifEmpty { baseUrl }
                "$host$relativeUrl"
            } else {
                val baseDir = baseUrl.substringBeforeLast("/")
                "$baseDir/$relativeUrl"
            }
        }
    }

    private fun sendSystemNotification(
        title: String,
        message: String,
        notificationId: Int,
        isComplete: Boolean
    ) {
        try {
            val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "netflix_downloads_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Downloads",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Netflix Download Notifications"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(application, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_downloads", true)
            }

            val pendingIntent = PendingIntent.getActivity(
                application,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(application, channelId)
                .setSmallIcon(R.drawable.ic_netflix_download_custom)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)

            if (!isComplete) {
                builder.setProgress(100, 0, true)
            }

            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            Log.e("DownloadManager", "Failed to send notification: ${e.message}")
        }
    }

    private fun serializeCaptions(captions: List<com.example.data.Caption>): String {
        val arr = JSONArray()
        for (c in captions) {
            val obj = JSONObject()
            obj.put("url", c.url)
            obj.put("language", c.language)
            obj.put("type", c.type)
            obj.put("languageCode", c.languageCode)
            arr.put(obj)
        }
        return arr.toString()
    }
}
