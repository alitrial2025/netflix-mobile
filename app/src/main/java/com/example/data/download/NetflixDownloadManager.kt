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
import com.example.data.AppCookieJar
import com.example.data.local.DownloadEntity
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.repository.NetflixRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitCancellation
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
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
    private val transferSlots = kotlinx.coroutines.sync.Semaphore(2)
    private val cookieJar = AppCookieJar()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val downloadOwners = ConcurrentHashMap<String, String>()
    
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
            false
        }
    }

    @Synchronized
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
        val previousJob = activeJobs[key]

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

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                // A canceled transfer must close its file before the next transfer opens it.
                previousJob?.join()
                transferSlots.acquire()
                try {
                    executeDownloadPipeline(profileId, media, episode, key, episodeTitle, isHighQuality)
                } finally {
                    transferSlots.release()
                }
            } catch (ce: CancellationException) {
                // Job cancelled / paused
                Log.d("DownloadManager", "Download job cancelled for $key")
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e("DownloadManager", "Fatal download error for $key: ${e.message}", e)
                val failedTask = _downloadTasks.value[key]?.copy(
                    status = DownloadTaskStatus.ERROR,
                    errorMessage = e.message ?: "Download failed"
                )
                if (failedTask != null) updateTask(failedTask)
                emitToast("Download failed for ${media.title}: ${e.message}")
            } finally {
                currentCoroutineContext()[Job]?.let {
                    if (activeJobs.remove(key, it) && key !in _pausedDownloadKeys.value) downloadOwners.remove(key, profileId)
                }
            }
        }

        activeJobs[key] = job
        downloadOwners[key] = profileId
        job.start()
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

        val resolvedStream = netMirrorResolver.resolveNet52(tmdbId, type, season, epNum)
        currentCoroutineContext().ensureActive()
        val targetUrl = resolvedStream.url
        val targetHeaders = resolvedStream.headers
        val extension = if (DownloadTransferPolicy.isHls(targetUrl) || targetUrl.substringBefore('?').endsWith(".ts")) "ts" else "mp4"
        val destinationFile = File(downloadsDir, "$key.$extension")
        val partFile = File(downloadsDir, "$key.mp4.part")
        val sourceFile = File(downloadsDir, "$key.source")
        val sourceFingerprint = java.security.MessageDigest.getInstance("SHA-256")
            .digest(targetUrl.toByteArray()).joinToString("") { "%02x".format(it) }
        if (!sourceFile.isFile || sourceFile.readText() != sourceFingerprint) {
            if (partFile.exists()) require(partFile.delete()) { "Could not replace partial download" }
            sourceFile.writeText(sourceFingerprint)
        }

        // 2. Perform stream transfer to .part file
        val downloadSuccess = performStreamDownload(
            streamUrl = targetUrl,
            headers = targetHeaders,
            partFile = partFile,
            key = key,
            media = media,
            episodeTitle = episodeTitle,
            isHighQuality = isHighQuality
        )

        // If paused or cancelled, retain .part file for instant resumption
        if (!currentCoroutineContext().isActive || _pausedDownloadKeys.value.contains(key)) {
            return@withContext
        }

        if (downloadSuccess && partFile.exists() && partFile.length() > 0) {
            // Atomic rename from .part to final .mp4
            if (destinationFile.exists()) require(destinationFile.delete()) { "Could not replace completed download" }
            require(partFile.renameTo(destinationFile)) { "Could not finalize download" }
            val finalFile = destinationFile
            sourceFile.delete()
            val actualSizeMb = ((finalFile.length() + 1024 * 1024 - 1) / (1024 * 1024)).toInt()
            val captionsJson = serializeCaptions(resolvedStream.captions)

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
        val isHls = DownloadTransferPolicy.isHls(streamUrl)

        if (isHls) {
            downloadHlsStream(streamUrl, headers, partFile, key, media, episodeTitle, isHighQuality)
        } else {
            downloadDirectStream(streamUrl, headers, partFile, key, media, episodeTitle)
        }
    }

    private fun downloadRequest(url: String, headers: Map<String, String>, sourceUrl: String): Request {
        val origin = (headers.entries.firstOrNull { it.key.equals("Origin", true) }?.value ?: sourceUrl).toHttpUrlOrNull()
        val cookieHeader = headers.entries.firstOrNull { it.key.equals("Cookie", true) }?.value.orEmpty()
        if (origin != null && cookieHeader.isNotBlank()) {
            cookieJar.saveFromResponse(origin, cookieHeader.split(';').mapNotNull {
                Cookie.parse(origin, "${it.trim()}; Path=/; Secure")
            })
        }
        return Request.Builder().url(url).apply {
            headers.forEach { (name, value) ->
                if (!name.equals("Cookie", true) &&
                    (!name.equals("Authorization", true) || url.toHttpUrlOrNull()?.host == sourceUrl.toHttpUrlOrNull()?.host)) {
                    header(name, value)
                }
            }
            header("Accept-Encoding", "identity")
        }.build()
    }

    private suspend fun <T> withResponse(request: Request, block: suspend (okhttp3.Response) -> T): T = coroutineScope {
        val call = httpClient.newCall(request)
        // Cancel the socket too: coroutine cancellation alone does not interrupt execute()/read().
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try { call.execute().use { block(it) } } finally { cancellation.cancel() }
    }

    private suspend fun downloadDirectStream(
        streamUrl: String,
        headers: Map<String, String>,
        partFile: File,
        key: String,
        media: MediaItem,
        episodeTitle: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val existingBytes = if (partFile.exists()) partFile.length() else 0L
        val request = downloadRequest(streamUrl, headers, streamUrl).newBuilder().apply {
            if (existingBytes > 0L) header("Range", "bytes=$existingBytes-")
        }.build()
        withResponse(request) { response ->
            currentCoroutineContext().ensureActive()
            val body = response.body ?: error("Download response is empty")
            val plan = DownloadTransferPolicy.directPlan(
                response.code, existingBytes, response.header("Content-Range"), body.contentLength()
            )
            var totalRead = if (plan.append) existingBytes else 0L
            FileOutputStream(partFile, plan.append).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var lastUpdate = android.os.SystemClock.elapsedRealtime()
                    var bytesSinceUpdate = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (_pausedDownloadKeys.value.contains(key)) return@withResponse false
                        val count = input.read(buffer)
                        currentCoroutineContext().ensureActive()
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        totalRead += count
                        bytesSinceUpdate += count
                        require(plan.expectedBytes < 0L || totalRead <= plan.expectedBytes) { "Download exceeds declared size" }
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastUpdate >= 500L) {
                            val speed = bytesSinceUpdate * 1000L / (now - lastUpdate).coerceAtLeast(1L)
                            val progress = if (plan.expectedBytes > 0L)
                                (totalRead.toDouble() / plan.expectedBytes).toFloat().coerceIn(0.02f, 0.99f) else 0.02f
                            updateTask(DownloadTaskInfo(
                                downloadKey = key, mediaId = media.id, mediaTitle = media.title,
                                episodeTitle = episodeTitle, progress = progress, downloadedBytes = totalRead,
                                totalBytes = plan.expectedBytes.coerceAtLeast(0L), speedBytesPerSec = speed,
                                speedFormatted = formatSpeed(speed), status = DownloadTaskStatus.DOWNLOADING
                            ))
                            _downloadingProgress.update { it + (key to progress) }
                            lastUpdate = now
                            bytesSinceUpdate = 0L
                        }
                    }
                }
                output.fd.sync()
            }
            currentCoroutineContext().ensureActive()
            DownloadTransferPolicy.isComplete(partFile.length(), plan.expectedBytes)
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
        suspend fun fetchPlaylist(url: String): String = withResponse(downloadRequest(url, headers, streamUrl)) {
            require(it.isSuccessful) { "Playlist request returned HTTP ${it.code}" }
            it.body?.string()?.takeIf { text -> text.isNotBlank() } ?: error("Empty playlist")
        }
        val manifest = fetchPlaylist(streamUrl)
        currentCoroutineContext().ensureActive()
        val lines = manifest.lines().map { it.trim() }
        val variants = lines.mapIndexedNotNull { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF:")) null else {
                val next = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith('#') }
                    ?: error("Missing video variant")
                extractBandwidth(line) to resolveRelativeUrl(streamUrl, next)
            }
        }.sortedByDescending { it.first }
        // Separate audio renditions cannot be represented by a concatenated TS file.
        require(lines.none { it.startsWith("#EXT-X-MEDIA:") && it.contains("TYPE=AUDIO") && it.contains("URI=") }) {
            "This video requires separate audio tracks that are not supported for offline viewing yet"
        }
        val variantUrl = if (variants.isEmpty()) streamUrl else if (isHighQuality)
            variants.first().second else variants[variants.size / 2].second
        val playlist = if (variantUrl == streamUrl) manifest else fetchPlaylist(variantUrl)
        val segments = DownloadTransferPolicy.hlsSegments(playlist, variantUrl)
        currentCoroutineContext().ensureActive()
        // HLS retries restart from segment zero; appending the playlist duplicates earlier segments.
        FileOutputStream(partFile, false).use { output ->
            var totalBytes = 0L
            for ((index, segmentUrl) in segments.withIndex()) {
                currentCoroutineContext().ensureActive()
                if (_pausedDownloadKeys.value.contains(key)) return@withContext false
                var segment: ByteArray? = null
                var failure: Exception? = null
                for (attempt in 1..3) {
                    try {
                        withResponse(downloadRequest(segmentUrl, headers, streamUrl)) { response ->
                            require(response.isSuccessful) { "Segment request returned HTTP ${response.code}" }
                            segment = response.body?.bytes()?.takeIf { it.isNotEmpty() } ?: error("Empty video segment")
                        }
                        currentCoroutineContext().ensureActive()
                        break
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failure = error
                        if (attempt < 3) delay(attempt * 250L)
                    }
                }
                val bytes = segment ?: throw java.io.IOException("Video segment ${index + 1} could not be downloaded", failure)
                output.write(bytes)
                totalBytes += bytes.size
                val progress = ((index + 1).toFloat() / segments.size).coerceIn(0.02f, 0.99f)
                updateTask(DownloadTaskInfo(
                    downloadKey = key, mediaId = media.id, mediaTitle = media.title,
                    episodeTitle = episodeTitle, progress = progress, downloadedBytes = totalBytes,
                    status = DownloadTaskStatus.DOWNLOADING
                ))
                _downloadingProgress.update { it + (key to progress) }
            }
            output.fd.sync()
        }
        currentCoroutineContext().ensureActive()
        partFile.length() > 0L
    }

    @Synchronized
    fun pauseDownload(key: String) {
        _pausedDownloadKeys.update { it + key }
        activeJobs[key]?.cancel()

        val task = _downloadTasks.value[key]?.copy(
            status = DownloadTaskStatus.PAUSED,
            speedFormatted = "Paused",
            etaFormatted = ""
        )
        if (task != null) updateTask(task)
        emitToast("Download paused")
    }

    @Synchronized
    fun cancelDownload(key: String) {
        _pausedDownloadKeys.update { it - key }
        val previousJob = activeJobs[key]
        previousJob?.cancel()
        _downloadingProgress.update { it - key }
        _downloadTasks.update { it - key }

        val cleanupJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                previousJob?.join()
                if (!repository.isDownloadReferenced(key)) deleteDownloadFiles(key)
            } finally {
                currentCoroutineContext()[Job]?.let {
                    if (activeJobs.remove(key, it)) downloadOwners.remove(key)
                }
            }
        }
        activeJobs[key] = cleanupJob
        cleanupJob.start()
        emitToast("Download canceled")
    }

    private fun deleteDownloadFiles(key: String) {
        for (suffix in listOf(".mp4", ".ts", ".mp4.part", ".source")) {
            File(downloadsDir, key + suffix).let { if (it.exists()) it.delete() }
        }
    }

    private suspend fun deleteUnreferencedFiles(key: String) {
        if (activeJobs[key]?.isActive != true && !repository.isDownloadReferenced(key)) deleteDownloadFiles(key)
    }

    fun close() {
        scope.coroutineContext[Job]?.cancel()
    }

    fun deleteCompletedDownload(profileId: String, downloadKey: String) {
        scope.launch {
            val transfer = activeJobs[downloadKey]?.takeIf { downloadOwners[downloadKey] == profileId }
            transfer?.cancel()
            transfer?.join()
            repository.removeDownload(profileId, downloadKey)
            try {
                deleteUnreferencedFiles(downloadKey)
            } catch (e: Exception) {
                Log.e("DownloadManager", "Error deleting download file: ${e.message}")
            }
            emitToast("Download deleted")
        }
    }

    fun clearAllDownloads(profileId: String) {
        scope.launch {
            val keys = repository.getDownloadsOnce(profileId).map { it.downloadKey }.toSet() +
                downloadOwners.filterValues { it == profileId }.keys
            try {
                for (key in keys) {
                    val transfer = activeJobs[key]?.takeIf { downloadOwners[key] == profileId }
                    transfer?.cancel()
                    transfer?.join()
                    repository.removeDownload(profileId, key)
                    deleteUnreferencedFiles(key)
                    _downloadingProgress.update { it - key }
                    _downloadTasks.update { it - key }
                    _pausedDownloadKeys.update { it - key }
                    downloadOwners.remove(key, profileId)
                }
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
            deleteUnreferencedFiles(watchedKey)

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
        _downloadTasks.update { tasks ->
            val previous = tasks[info.downloadKey]
            tasks + (info.downloadKey to info.copy(episodeId = info.episodeId ?: previous?.episodeId))
        }
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
