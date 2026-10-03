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
import kotlinx.coroutines.tasks.await
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
import androidx.work.*
import com.example.data.local.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.io.IOException
import java.io.ByteArrayOutputStream

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
    val localFilePath: String? = null,
    val profileId: String? = null
)

class NetflixDownloadManager(
    private val application: Application,
    private val repository: NetflixRepository,
    private val netMirrorResolver: NetMirrorResolver,
    clientOverride: OkHttpClient? = null,
    private val accountIdProvider: () -> String? = { FirebaseAuth.getInstance().currentUser?.uid },
    private val candidateProbe: (suspend (MediaItem, Episode?) -> Boolean)? = null
) {
    companion object {
        const val WORK_TAG = "netflixpro.video.download"
        @Volatile private var instance: NetflixDownloadManager? = null
        fun getInstance(application: Application): NetflixDownloadManager = instance ?: synchronized(this) {
            instance ?: NetflixDownloadManager(application,
                NetflixRepository(AppDatabase.getInstance(application).netflixDao()), NetMirrorResolver(application))
                .also { instance = it }
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transferSlots = kotlinx.coroutines.sync.Semaphore(2)
    private val cookieJar = AppCookieJar()

    private val httpClient: OkHttpClient = (clientOverride ?: OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()).newBuilder().cookieJar(okhttp3.CookieJar.NO_COOKIES).build()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val downloadOwners = ConcurrentHashMap<String, String>()
    private val transferLocks = ConcurrentHashMap<String, Mutex>()
    private val workManager by lazy { WorkManager.getInstance(application) }
    private val requestStore = DownloadRequestStore(File(application.noBackupFilesDir, "download-requests"))
    private val requests = ConcurrentHashMap<String, SavedDownloadRequest>()
    
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

    init {
        requestStore.all().forEach { request ->
            requests[request.task.downloadKey] = request
            if (request.accountId == accountIdProvider()) {
                val task = request.task.let { if (it.status in setOf(DownloadTaskStatus.PREPARING, DownloadTaskStatus.DOWNLOADING))
                    it.copy(status = DownloadTaskStatus.QUEUED, speedFormatted = "Waiting to resume") else it }
                updateTask(task)
                _downloadingProgress.update { it + (task.downloadKey to task.progress) }
                if (task.status == DownloadTaskStatus.PAUSED) _pausedDownloadKeys.update { it + task.downloadKey }
            }
        }
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
        profileId: String, media: MediaItem, episode: Episode? = null,
        isWifiOnly: Boolean = true, isHighQuality: Boolean = true, isForYou: Boolean = false
    ) {
        val key = if (episode != null) "${media.id}_${episode.id}" else media.id
        require(key.matches(Regex("[A-Za-z0-9_-]{1,200}"))) { "Invalid download identifier" }
        val account = accountIdProvider() ?: run { emitToast("Sign in to download titles"); return }
        val previous = _downloadTasks.value[key]
        if (previous?.status in setOf(DownloadTaskStatus.QUEUED, DownloadTaskStatus.PREPARING, DownloadTaskStatus.DOWNLOADING)) {
            emitToast("Download already queued for ${media.title}"); return
        }
        if (requests[key]?.profileId?.let { it != profileId } == true) {
            emitToast("This title is downloading in another profile"); return
        }
        val task = (previous ?: DownloadTaskInfo(key, media.id, media.title,
            episode?.let { com.example.ui.viewmodel.episodeDownloadLabel(it) }, episode?.id, profileId = profileId))
            .copy(status = DownloadTaskStatus.QUEUED, errorMessage = null, speedFormatted = "Queued")
        // Keep only metadata needed to resume; never serialize the whole recommendation tree.
        val request = SavedDownloadRequest(UUID.randomUUID().toString(), account, profileId,
            media.copy(episodes = emptyList(), similarMedia = emptyList()), episode, isWifiOnly, isHighQuality, task, isForYou = isForYou)
        try {
            requestStore.put(request)
            requests[key] = request
            _pausedDownloadKeys.update { it - key }
            updateTask(task)
            _downloadingProgress.update { it + (key to task.progress) }
            enqueue(request)
        } catch (_: Exception) {
            updateTask(task.copy(status = DownloadTaskStatus.ERROR, errorMessage = "Could not save download. Check device storage."))
        }
    }

    private fun enqueue(request: SavedDownloadRequest) {
        val constraints = Constraints.Builder().setRequiredNetworkType(
            if (request.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true).build()
        val work = OneTimeWorkRequestBuilder<VideoDownloadWorker>()
            .setInputData(workDataOf("key" to request.task.downloadKey, "token" to request.token))
            .setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(WORK_TAG).build()
        workManager.enqueueUniqueWork(downloadWorkName(request.task.downloadKey), ExistingWorkPolicy.REPLACE, work)
    }

    /** Reapply network constraints without losing checkpoints or starting paused work. */
    @Synchronized
    fun updateWifiOnlyPolicy(wifiOnly: Boolean) {
        val owner = accountIdProvider() ?: return
        requests.values.filter { it.accountId == owner && it.wifiOnly != wifiOnly }.forEach { request ->
            val active = request.task.status in setOf(DownloadTaskStatus.QUEUED, DownloadTaskStatus.PREPARING, DownloadTaskStatus.DOWNLOADING)
            val updated = request.copy(token = if (active) UUID.randomUUID().toString() else request.token,
                wifiOnly = wifiOnly, task = if (active) request.task.copy(status = DownloadTaskStatus.QUEUED, errorMessage = null,
                    speedFormatted = "Waiting to resume") else request.task)
            requestStore.put(updated)
            requests[updated.task.downloadKey] = updated
            updateTask(updated.task)
            if (active) enqueue(updated)
        }
    }

    @Synchronized
    fun resumeSavedDownload(key: String, profileId: String): Boolean {
        val request = requests[key] ?: return false
        if (request.profileId != profileId || request.accountId != accountIdProvider()) return false
        startOrResumeDownload(profileId, request.media, request.episode, request.wifiOnly, request.highQuality, request.isForYou)
        return true
    }

    internal fun isCurrentRequest(key: String, token: String): Boolean = requests[key]?.let {
        it.token == token && it.task.status != DownloadTaskStatus.PAUSED
    } == true

    @Synchronized
    internal fun markWaiting(key: String, token: String, message: String) {
        if (!isCurrentRequest(key, token)) return
        _downloadTasks.value[key]?.let { updateTask(it.copy(status = DownloadTaskStatus.QUEUED,
            errorMessage = message, speedFormatted = "Waiting")) }
    }

    @Synchronized
    internal fun markFailed(key: String, token: String, message: String) {
        if (!isCurrentRequest(key, token)) return
        _downloadTasks.value[key]?.let { updateTask(it.copy(status = DownloadTaskStatus.ERROR, errorMessage = message)) }
    }

    internal suspend fun runSavedDownload(key: String, token: String, attempt: Int): DownloadRunResult =
        transferLocks.getOrPut(key) { Mutex() }.withLock {
            val request = requests[key]?.takeIf { it.token == token } ?: return@withLock DownloadRunResult.COMPLETE
            if (request.accountId != accountIdProvider()) {
                removeSavedRequest(key, token)
                return@withLock DownloadRunResult.FAILED
            }
            if (!isCurrentRequest(key, token)) return@withLock DownloadRunResult.COMPLETE
            if (request.wifiOnly && !isWifiConnected()) {
                markWaiting(key, token, "Waiting for Wi-Fi")
                return@withLock DownloadRunResult.RETRY
            }
            val job = currentCoroutineContext()[Job]!!
            activeJobs[key] = job
            downloadOwners[key] = request.profileId
            try {
                transferSlots.acquire()
                try {
                    updateTask(request.task.copy(status = DownloadTaskStatus.PREPARING, errorMessage = null))
                    val plan = confirmedDownloadPlan(request.accountId)
                    if (request.isForYou && !plan.downloadsForYou)
                        throw DownloadMembershipException("Downloads for You is included with Premium. Upgrade to continue.")
                    val completedDownloads = repository.getDownloadsOnce(request.profileId)
                    if (completedDownloads.none { it.downloadKey == key } && completedDownloads.size >= plan.maxDownloads)
                        throw DownloadMembershipException("Your plan's offline title limit is reached. Delete a download to continue.")
                    executeDownloadPipeline(request.profileId, request.media, request.episode, key, request.task.episodeTitle, request.highQuality, plan.maxVideoHeight)
                    removeSavedRequest(key, token)
                    DownloadRunResult.COMPLETE
                } finally { transferSlots.release() }
            } catch (cancelled: CancellationException) {
                markWaiting(key, token, "Waiting to resume download")
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                val retry = DownloadRetryPolicy.shouldRetry(error, attempt)
                if (retry) markWaiting(key, token, "Connection interrupted. Your saved progress will resume automatically.")
                else if (isCurrentRequest(key, token)) _downloadTasks.value[key]?.let {
                    updateTask(it.copy(status = DownloadTaskStatus.ERROR, errorMessage = DownloadRetryPolicy.userMessage(error)))
                }
                if (retry) DownloadRunResult.RETRY else DownloadRunResult.FAILED
            } finally {
                activeJobs.remove(key, job)
                downloadOwners.remove(key, request.profileId)
            }
        }

    @Synchronized
    private fun removeSavedRequest(key: String, token: String? = null) {
        if (token != null && requests[key]?.token != token) return
        requests.remove(key)
        requestStore.remove(key)
    }

    /** Workers must recheck the cloud plan after an Activity closes or a queued job resumes. */
    private suspend fun confirmedDownloadPlan(accountId: String): com.example.data.model.SubscriptionPlan {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous || user.uid != accountId)
            throw DownloadMembershipException("Sign in to the account that started this download.")
        val snapshot = try {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(accountId).collection("subscription").document("current")
                .get(com.google.firebase.firestore.Source.SERVER).await()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: com.google.firebase.firestore.FirebaseFirestoreException) {
            if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
                throw DownloadMembershipException("Your membership could not be confirmed. Sign in again or contact support.")
            throw IOException("Could not refresh membership", error)
        }
        currentCoroutineContext().ensureActive()
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != accountId)
            throw DownloadMembershipException("Your account changed. Sign in again to continue.")
        if (snapshot.metadata.hasPendingWrites()) throw IOException("Membership confirmation is pending")
        val plan = com.example.data.model.SubscriptionPlans.PLANS.firstOrNull { it.id == snapshot.getString("planId") }
        if (plan == null || !com.example.data.RenewalPolicy.grantsAccess(snapshot.getString("status").orEmpty(),
            snapshot.getLong("expiresAt") ?: 0L, com.example.data.SubscriptionTime.now()))
            throw DownloadMembershipException("Renew your membership to continue this download.")
        val checked = try { com.example.data.DeviceAccessGuard.confirm(application, tv = false) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { throw DownloadMembershipException(error.message ?: "This device is not authorized for downloads.") }
        val confirmedPlan = com.example.data.model.SubscriptionPlans.PLANS.firstOrNull { it.id == checked.getString("planId") }
        if (confirmedPlan == null || !com.example.data.RenewalPolicy.grantsAccess(checked.getString("status").orEmpty(),
            checked.getLong("expiresAt") ?: 0L, com.example.data.SubscriptionTime.now()))
            throw DownloadMembershipException("Renew your membership to continue this download.")
        return confirmedPlan
    }

    private suspend fun executeDownloadPipeline(
        profileId: String,
        media: MediaItem,
        episode: Episode?,
        key: String,
        episodeTitle: String?,
        isHighQuality: Boolean,
        maxVideoHeight: Int = Int.MAX_VALUE
    ) = withContext(Dispatchers.IO) {
        val saved = requests[key]
        if (saved?.completedPath != null) {
            val completed = File(saved.completedPath)
            if (completed.isFile && completed.length() == saved.completedBytes && saved.completedBytes > 0) {
                commitCompletedDownload(saved)
                return@withContext
            }
        }
        val type = if (media.type == MediaType.MOVIE) "movie" else "tv"
        val season = episode?.let { com.example.ui.viewmodel.episodeCoordinates(it.id).first } ?: 0
        val number = episode?.episodeNumber ?: 0
        // A resume must resolve current signed URLs, not reuse the failed segment's credentials.
        if (saved?.task?.downloadedBytes?.let { it > 0 } == true)
            netMirrorResolver.evictCachedStream(media.id, type, season, number)
        for (renewal in 0..1) {
            val stream = netMirrorResolver.resolveNet52(media.id, type, season, number)
            currentCoroutineContext().ensureActive()
            try {
                executeResolvedDownload(profileId, media, episode, key, episodeTitle, isHighQuality, stream, maxVideoHeight)
                return@withContext
            } catch (error: DownloadHttpException) {
                if (error.code !in setOf(401, 403) || renewal == 1) throw error
                netMirrorResolver.invalidateDownloadSession(media.id, type, season, number, error.usedProviderSession)
            }
        }
    }

    internal suspend fun executeResolvedDownload(
        profileId: String, media: MediaItem, episode: Episode?, key: String, episodeTitle: String?,
        isHighQuality: Boolean, resolvedStream: com.example.data.NetMirrorStream, maxVideoHeight: Int = Int.MAX_VALUE
    ) = withContext(Dispatchers.IO) {
        val itemTitle = if (episodeTitle != null) "${media.title} ($episodeTitle)" else media.title
        val targetUrl = resolvedStream.url
        val targetHeaders = resolvedStream.headers
        val extension = if (DownloadTransferPolicy.isHls(targetUrl) || targetUrl.substringBefore('?').endsWith(".ts")) "ts" else "mp4"
        var destinationFile = File(downloadsDir, "$key.$extension")
        val partFile = File(downloadsDir, "$key.mp4.part")
        val sourceFile = File(downloadsDir, "$key.source")
        val sourceFingerprint = java.security.MessageDigest.getInstance("SHA-256")
            .digest(targetUrl.toByteArray()).joinToString("") { "%02x".format(it) }
        if (!sourceFile.isFile || sourceFile.readText() != sourceFingerprint) {
            // HLS owns durable segment checkpoints. Direct resumes require a server validator
            // when the signed resource URL changes, otherwise restarting is safer than corruption.
            val validator = File(downloadsDir, "$key.validator")
            if (partFile.exists() && (DownloadTransferPolicy.isHls(targetUrl) || !validator.isFile))
                require(partFile.delete()) { "Could not replace partial download" }
            sourceFile.writeText(sourceFingerprint)
        }

        // Caption links may expire during a long video transfer. Fetch them first and
        // reuse complete local tracks after a pause, process restart or session renewal.
        val localCaptions = downloadCaptions(key, resolvedStream)

        // 2. Perform stream transfer to .part file
        val downloadSuccess = performStreamDownload(
            streamUrl = targetUrl,
            headers = targetHeaders,
            partFile = partFile,
            key = key,
            media = media,
            episodeTitle = episodeTitle,
            isHighQuality = isHighQuality,
            maxVideoHeight = maxVideoHeight
        )

        // If paused or cancelled, retain .part file for instant resumption
        if (!currentCoroutineContext().isActive || _pausedDownloadKeys.value.contains(key)) {
            return@withContext
        }

        if (downloadSuccess && partFile.exists() && partFile.length() > 0) {
            val isOfflineBundle = partFile.inputStream().use { input -> ByteArray(7).also { input.read(it) }.toString(Charsets.US_ASCII) } == "#EXTM3U"
            if (isOfflineBundle) destinationFile = File(downloadsDir, "$key.m3u8")
            val mediaBytes = if (isOfflineBundle) File(downloadsDir, "$key.offline").walkTopDown()
                .filter { it.isFile }.sumOf { it.length() } + partFile.length() else partFile.length()
            // Journal finalization before rename: a crash between the file move and Room save
            // can recover the completed asset without downloading it all over again.
            currentCoroutineContext().ensureActive()
            val request = requests[key] ?: throw CancellationException("Download was canceled")
            check(request.accountId == accountIdProvider() && request.profileId == profileId) { "Download account changed" }
            val completed = request.copy(completedPath = destinationFile.absolutePath,
                completedBytes = partFile.length(), mediaBytes = mediaBytes, localCaptionsJson = serializeCaptions(localCaptions))
            requestStore.put(completed)
            requests[key] = completed
            if (destinationFile.exists()) require(destinationFile.delete()) { "Could not replace completed download" }
            require(partFile.renameTo(destinationFile)) { "Could not finalize download" }
            commitCompletedDownload(completed)
            sourceFile.delete()
            File(downloadsDir, "$key.validator").delete()
            File(downloadsDir, "$key.segments").deleteRecursively()
        } else {
            throw IOException("The transfer ended before the complete video was saved")
        }
    }

    private suspend fun commitCompletedDownload(request: SavedDownloadRequest) {
        currentCoroutineContext().ensureActive()
        check(request.accountId == accountIdProvider()) { "Download account changed" }
        val key = request.task.downloadKey
        repository.addDownload(request.profileId, request.media.id, request.media.title, request.task.episodeTitle,
            (((request.mediaBytes.takeIf { it > 0 } ?: request.completedBytes) + 1024 * 1024 - 1) / (1024 * 1024)).toInt(), request.episode?.id,
            request.completedPath, "", request.localCaptionsJson, request.isForYou)
        _downloadingProgress.update { it - key }
        _downloadTasks.update { it - key }
        sendSystemNotification("Download Complete", "${request.media.title} is ready to watch offline.", key.hashCode(), true)
        emitToast("Downloaded: ${request.media.title}!")
    }

    /** Keep actual caption files offline; missing optional captions never invalidate the video. */
    internal suspend fun downloadCaptions(key: String, stream: com.example.data.NetMirrorStream): List<com.example.data.Caption> {
        // Simultaneous titles may select different OTT cookies. Their captions must
        // not overwrite one another's provider session or the video transfer's jar.
        val captionCookies = AppCookieJar()
        val captionClient = httpClient.newBuilder().cookieJar(okhttp3.CookieJar.NO_COOKIES).build()
        return OfflineCaptionDownloader(File(downloadsDir, "$key.captions")) { caption ->
            withResponse(downloadRequest(caption.url, stream.captionHeaders, stream.url, captionCookies), captionClient) { response ->
                if (!response.isSuccessful) throw DownloadHttpException(response.code,
                    response.request.header("Cookie")?.contains("t_hash_t=") == true)
                val body = response.body ?: throw IOException("Empty subtitles")
                body.byteStream().use { input ->
                    val bytes = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(bytes.size() + count <= OfflineCaptionDownloader.MAX_BYTES) { "Subtitles exceed supported size" }
                        bytes.write(buffer, 0, count)
                    }
                    bytes.toByteArray()
                }
            }
        }.download(stream.captions)
    }

    private suspend fun performStreamDownload(
        streamUrl: String,
        headers: Map<String, String>,
        partFile: File,
        key: String,
        media: MediaItem,
        episodeTitle: String?,
        isHighQuality: Boolean,
        maxVideoHeight: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val isHls = DownloadTransferPolicy.isHls(streamUrl)

        if (isHls) {
            downloadHlsStream(streamUrl, headers, partFile, key, media, episodeTitle, isHighQuality, maxVideoHeight)
        } else {
            downloadDirectStream(streamUrl, headers, partFile, key, media, episodeTitle)
        }
    }

    private fun downloadRequest(url: String, headers: Map<String, String>, sourceUrl: String, jar: AppCookieJar = cookieJar): Request {
        return Request.Builder().url(url).apply {
            headers.forEach { (name, value) ->
                val allowed = !name.equals("Cookie", true) &&
                    !name.equals("Authorization", true)
                if (allowed) {
                    header(name, value)
                }
            }
            header("Accept-Encoding", "identity")
        }.build()
    }

    private suspend fun <T> withResponse(request: Request, client: OkHttpClient = httpClient, block: suspend (okhttp3.Response) -> T): T = coroutineScope {
        val call = client.newCall(request)
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
        val validatorFile = File(downloadsDir, "$key.validator")
        val validator = validatorFile.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
        val request = downloadRequest(streamUrl, headers, streamUrl).newBuilder().apply {
            if (existingBytes > 0L) {
                header("Range", "bytes=$existingBytes-")
                if (validator != null) header("If-Range", validator)
            }
        }.build()
        withResponse(request) { response ->
            currentCoroutineContext().ensureActive()
            if (!response.isSuccessful) throw DownloadHttpException(response.code, response.request.header("Cookie")?.contains("t_hash_t=") == true)
            val body = response.body ?: throw IOException("Download response is empty")
            if (body.contentType()?.subtype?.contains("html") == true) throw DownloadHttpException(401)
            val plan = DownloadTransferPolicy.directPlan(
                response.code, existingBytes, response.header("Content-Range"), body.contentLength()
            )
            val responseValidator = response.header("ETag")?.takeUnless { it.startsWith("W/") } ?: response.header("Last-Modified")
            if (plan.append && validator != null && responseValidator != null && validator != responseValidator) {
                partFile.delete(); validatorFile.delete()
                throw IOException("Download resource changed; restarting safely")
            }
            if (responseValidator != null) validatorFile.writeText(responseValidator) else if (!plan.append) validatorFile.delete()
            val required = if (plan.expectedBytes > 0) plan.expectedBytes - (if (plan.append) existingBytes else 0L) else 1024 * 1024L
            if (partFile.parentFile!!.usableSpace < required + 8 * 1024 * 1024L) throw DownloadStorageException()
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
                            val progress = DownloadTransferPolicy.progress(totalRead, plan.expectedBytes)
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

    internal suspend fun downloadHlsStream(
        streamUrl: String, headers: Map<String, String>, partFile: File, key: String,
        media: MediaItem, episodeTitle: String?, isHighQuality: Boolean, maxVideoHeight: Int = Int.MAX_VALUE
    ): Boolean = withContext(Dispatchers.IO) {
        suspend fun fetchPlaylist(url: String): String = withResponse(downloadRequest(url, headers, streamUrl)) {
            if (!it.isSuccessful) throw DownloadHttpException(it.code)
            val body = it.body ?: throw IOException("Empty playlist")
            body.byteStream().use { input ->
                val saved = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (saved.size() <= 2 * 1024 * 1024) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    saved.write(buffer, 0, count)
                }
                val bytes = saved.toByteArray()
                require(bytes.size <= 2 * 1024 * 1024) { "Playlist exceeds supported size" }
                bytes.toString(Charsets.UTF_8).also { text ->
                    if (!text.trimStart().startsWith("#EXTM3U") && text.contains("<html", true)) throw DownloadHttpException(401)
                }
            }
        }
        val manifest = fetchPlaylist(streamUrl)
        currentCoroutineContext().ensureActive()
        val selected = OfflineHlsPlan.variant(manifest, streamUrl, isHighQuality, maxVideoHeight)
        if (selected.audioUrl != null) return@withContext downloadOfflineHlsBundle(
            selected, ::fetchPlaylist, streamUrl, headers, partFile, key, media, episodeTitle, isHighQuality)
        val variantUrl = selected.url
        val playlist = if (variantUrl == streamUrl) manifest else fetchPlaylist(variantUrl)
        val segments = DownloadTransferPolicy.hlsSegments(playlist, variantUrl)
        val store = HlsSegmentStore(File(partFile.parentFile, "$key.segments"), HlsSegmentStore.identity(playlist, segments, isHighQuality))
        // Build a fresh assembly from verified whole segments, never append partial TS bytes.
        FileOutputStream(partFile, false).use { output ->
            var totalBytes = 0L
            for ((index, segmentUrl) in segments.withIndex()) {
                currentCoroutineContext().ensureActive()
                if (_pausedDownloadKeys.value.contains(key)) return@withContext false
                var segment = store.completed(index)
                if (segment == null) {
                    var failure: Exception? = null
                    for (attempt in 0..2) {
                        try {
                            val partial = store.partial(index)
                            withResponse(downloadRequest(segmentUrl, headers, streamUrl)) { response ->
                                if (!response.isSuccessful) throw DownloadHttpException(response.code, response.request.header("Cookie")?.contains("t_hash_t=") == true)
                                val body = response.body ?: throw IOException("Empty video segment")
                                if (body.contentType()?.subtype?.contains("html") == true) throw DownloadHttpException(401)
                                val length = body.contentLength()
                                val needed = length.coerceAtLeast(1024 * 1024L)
                                // Segment storage and final assembly coexist until completion.
                                if (partial.parentFile!!.usableSpace < needed * 2 + 8 * 1024 * 1024L) throw DownloadStorageException()
                                FileOutputStream(partial, false).use { saved ->
                                    body.byteStream().use { input ->
                                        val buffer = ByteArray(64 * 1024)
                                        while (true) {
                                            currentCoroutineContext().ensureActive()
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            saved.write(buffer, 0, count)
                                        }
                                    }
                                    saved.fd.sync()
                                }
                                if (length >= 0 && partial.length() != length) throw IOException("Video segment was truncated")
                            }
                            segment = store.commit(index)
                            break
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            failure = error
                            if (error is DownloadStorageException || error is IllegalArgumentException ||
                                (error is DownloadHttpException && error.code !in setOf(408, 429) && error.code < 500)) throw error
                            if (attempt < 2) delay((attempt + 1) * 1000L)
                        }
                    }
                    if (segment == null) throw failure ?: IOException("Video segment ${index + 1} is waiting to retry")
                }
                val completed = segment!!
                if (partFile.parentFile!!.usableSpace < completed.length() + 8 * 1024 * 1024L) throw DownloadStorageException()
                completed.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
                totalBytes += completed.length()
                val progress = DownloadTransferPolicy.progress((index + 1).toLong(), segments.size.toLong())
                updateTask(DownloadTaskInfo(key, media.id, media.title, episodeTitle = episodeTitle,
                    progress = progress, downloadedBytes = totalBytes, status = DownloadTaskStatus.DOWNLOADING))
                _downloadingProgress.update { it + (key to progress) }
            }
            output.fd.sync()
        }
        currentCoroutineContext().ensureActive()
        partFile.length() > 0L
    }

    /** Keep separate audio alongside video, without transcoding or expired remote URLs. */
    private suspend fun downloadOfflineHlsBundle(
        variant: OfflineHlsVariant, fetchPlaylist: suspend (String) -> String,
        streamUrl: String, headers: Map<String, String>, partFile: File, key: String,
        media: MediaItem, episodeTitle: String?, highQuality: Boolean
    ): Boolean {
        val video = OfflineHlsPlan.track(fetchPlaylist(variant.url), variant.url)
        val audioUrl = requireNotNull(variant.audioUrl)
        val audio = OfflineHlsPlan.track(fetchPlaylist(audioUrl), audioUrl)
        val tracks = listOf("video" to video, "audio" to audio)
        val totalResources = tracks.sumOf { it.second.resources.size }
        var completedCount = 0
        var totalBytes = 0L
        for ((name, track) in tracks) {
            val directory = File(partFile.parentFile, "$key.offline/$name")
            val urls = track.resources.map { it.url }
            val store = HlsSegmentStore(directory, HlsSegmentStore.identity(track.playlist, urls, highQuality), true, track.resources.map { it.fileName })
            for ((index, resource) in track.resources.withIndex()) {
                currentCoroutineContext().ensureActive()
                if (_pausedDownloadKeys.value.contains(key)) return false
                val completed = store.completed(index) ?: downloadOfflineResource(
                    resource.url, headers, streamUrl, store, index)
                completedCount++
                totalBytes += completed.length()
                val progress = DownloadTransferPolicy.progress(completedCount.toLong(), totalResources.toLong())
                updateTask(DownloadTaskInfo(key, media.id, media.title, episodeTitle = episodeTitle,
                    progress = progress, downloadedBytes = totalBytes, status = DownloadTaskStatus.DOWNLOADING))
                _downloadingProgress.update { it + (key to progress) }
            }
            OfflineHlsBundle.write(File(directory, "index.m3u8"), track.playlist)
        }
        currentCoroutineContext().ensureActive()
        // Entry point is written last. Finalization journals and renames it atomically.
        FileOutputStream(partFile, false).use { output ->
            output.write(OfflineHlsBundle.master(key, variant).toByteArray())
            output.fd.sync()
        }
        return true
    }

    private suspend fun downloadOfflineResource(url: String, headers: Map<String, String>,
        source: String, store: HlsSegmentStore, index: Int): File {
        for (attempt in 0..2) {
            try {
                val partial = store.partial(index)
                withResponse(downloadRequest(url, headers, source)) { response ->
                    if (!response.isSuccessful) throw DownloadHttpException(response.code, response.request.header("Cookie")?.contains("t_hash_t=") == true)
                    val body = response.body ?: throw IOException("Empty media segment")
                    if (body.contentType()?.subtype?.contains("html") == true) throw DownloadHttpException(401)
                    val length = body.contentLength()
                    if (partial.parentFile!!.usableSpace < length.coerceAtLeast(1024 * 1024L) + 8 * 1024 * 1024L)
                        throw DownloadStorageException()
                    FileOutputStream(partial, false).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer); if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                        output.fd.sync()
                    }
                    if (length >= 0 && partial.length() != length) throw IOException("Media segment was truncated")
                }
                return store.commit(index)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (attempt == 2 || error is DownloadStorageException || error is IllegalArgumentException ||
                    (error is DownloadHttpException && error.code !in setOf(408, 429) && error.code < 500)) throw error
                delay((attempt + 1) * 1000L)
            }
        }
        error("Media segment could not be saved")
    }

    @Synchronized
    fun pauseDownload(key: String) {
        workManager.cancelUniqueWork(downloadWorkName(key))
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
        workManager.cancelUniqueWork(downloadWorkName(key))
        removeSavedRequest(key)
        _pausedDownloadKeys.update { it - key }
        val previousJob = activeJobs[key]
        previousJob?.cancel()
        _downloadingProgress.update { it - key }
        _downloadTasks.update { it - key }

        val cleanupJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                previousJob?.join()
                transferLocks.getOrPut(key) { Mutex() }.withLock {
                    if (requests[key] == null && !repository.isDownloadReferenced(key)) deleteDownloadFiles(key)
                }
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
        File(downloadsDir, "$key.segments").deleteRecursively()
        File(downloadsDir, "$key.captions").deleteRecursively()
        File(downloadsDir, "$key.offline").deleteRecursively()
        for (suffix in listOf(".mp4", ".ts", ".m3u8", ".mp4.part", ".source", ".validator")) {
            File(downloadsDir, key + suffix).let { if (it.exists()) it.delete() }
        }
    }

    private suspend fun deleteUnreferencedFiles(key: String) {
        if (activeJobs[key]?.isActive != true && !repository.isDownloadReferenced(key)) deleteDownloadFiles(key)
    }

    suspend fun cancelAndJoinTransfers() {
        withContext(Dispatchers.IO) { workManager.cancelAllWorkByTag(WORK_TAG).result.get() }
        requests.keys.toList().forEach { removeSavedRequest(it) }
        val transfers = activeJobs.values.toList()
        transfers.forEach { it.cancel() }
        transfers.forEach { it.join() }
        // Include pending curation/cleanup work so sign-out cannot enqueue a late transfer.
        while (true) {
            val jobs = scope.coroutineContext[Job]?.children?.toList().orEmpty()
            if (jobs.isEmpty()) break
            jobs.forEach { it.cancel() }
            jobs.forEach { it.join() }
        }
        _downloadTasks.value = emptyMap()
        _downloadingProgress.value = emptyMap()
        _pausedDownloadKeys.value = emptySet()
        downloadOwners.clear()
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
                downloadOwners.filterValues { it == profileId }.keys + requests.values.filter { it.profileId == profileId }.map { it.task.downloadKey }
            try {
                for (key in keys) {
                    workManager.cancelUniqueWork(downloadWorkName(key))
                    removeSavedRequest(key)
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
        catalog: List<MediaItem> = emptyList(),
        maxDownloads: Int = Int.MAX_VALUE
    ) {
        if (!isSmartDownloadsEnabled) return

        scope.launch {
            val watchedKey = "${media.id}_${watchedEpisode.id}"
            
            // 2. Find next episode in the series
            val currentIdx = media.episodes.indexOfFirst { it.id == watchedEpisode.id }
            val nextEpisode = if (currentIdx >= 0 && currentIdx + 1 < media.episodes.size) {
                media.episodes[currentIdx + 1]
            } else null

            if (nextEpisode != null) {
                val available = try { providerHasDownload(media, nextEpisode) }
                    catch (_: com.example.data.PlaybackRateLimitedException) { false }
                if (!available) return@launch // Keep the watched copy when the provider has no next episode.
                repository.removeDownload(profileId, watchedKey)
                deleteUnreferencedFiles(watchedKey)
                val owned = _downloadTasks.value.values.count { it.profileId == profileId }
                if (repository.getDownloadsOnce(profileId).size + owned >= maxDownloads) return@launch
                emitToast("Smart Downloads: ${com.example.ui.viewmodel.episodeDownloadLabel(nextEpisode)} downloading for offline...")
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
                    candidatePool = catalog,
                    maxDownloads = maxDownloads
                )
            }
        }
    }

    // Provider availability, not TMDB popularity, decides whether an automatic download is queued.
    private val candidateAvailability = ConcurrentHashMap<String, Pair<Long, Boolean>>()
    private suspend fun providerHasDownload(media: MediaItem, episode: Episode?): Boolean {
        val type = if (media.type == MediaType.MOVIE) "movie" else "tv"
        val season = episode?.let { com.example.ui.viewmodel.episodeCoordinates(it.id).first } ?: 0
        val number = episode?.episodeNumber ?: 0
        val key = "${com.example.data.PlaybackServiceGate.sourceRevision}:$type:${media.id}:$season:$number"
        candidateAvailability[key]?.takeIf { System.currentTimeMillis() - it.first < 15 * 60_000L }
            ?.let { return it.second }
        val available = try {
            candidateProbe?.invoke(media, episode) ?: (kotlinx.coroutines.withTimeoutOrNull(60_000L) {
                netMirrorResolver.resolveNet52(media.id, type, season, number)
            } != null)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (limited: com.example.data.PlaybackRateLimitedException) { throw limited }
        catch (_: Exception) { false }
        if (candidateAvailability.size >= 64) candidateAvailability.keys.firstOrNull()?.let(candidateAvailability::remove)
        candidateAvailability[key] = System.currentTimeMillis() to available
        return available
    }

    suspend fun curateSmartDownloads(
        profileId: String,
        allocatedGb: Float,
        isWifiOnly: Boolean,
        isHighQuality: Boolean,
        candidatePool: List<MediaItem>,
        userLikedIds: Set<String> = emptySet(),
        watchlistIds: Set<String> = emptySet(),
        maxDownloads: Int = Int.MAX_VALUE
    ) = withContext(Dispatchers.IO) {
        if (candidatePool.isEmpty() || (isWifiOnly && candidateProbe == null && !isWifiConnected())) return@withContext

        val curatorAccount = accountIdProvider() ?: return@withContext
        // Calculate current storage consumed by completed downloads + active downloads
        val currentDownloads = repository.getDownloadsOnce(profileId)
        val existingDownloadedKeys = currentDownloads.map { it.downloadKey }.toSet()
        val existingMediaIds = currentDownloads.map { it.mediaId }.toSet()

        val ownerTasks = _downloadTasks.value.values.filter { it.profileId == profileId }
        val remainingSlots = (maxDownloads - currentDownloads.size - ownerTasks.size).coerceAtLeast(0)
        if (remainingSlots == 0) return@withContext
        val completedSizeMb = currentDownloads.filter { it.isForYou }.sumOf { it.fileSizeMb }
        val activeTasksSizeMb = ownerTasks.filter { requests[it.downloadKey]?.isForYou == true }
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
            !media.isComingSoon && !existingMediaIds.contains(media.id) && !existingDownloadedKeys.any { it == media.id || it.startsWith("${media.id}_") } && ownerTasks.none { it.mediaId == media.id }
        }.sortedWith(
            compareByDescending<MediaItem> { userLikedIds.contains(it.id) || watchlistIds.contains(it.id) }
                .thenByDescending { it.matchPercentage }
                .thenByDescending { it.releaseYear }
        )

        var queuedCount = 0
        for (media in candidates.take(6)) {
            if (remainingBudgetMb < 280 || queuedCount >= minOf(3, remainingSlots)) break

            val episode = if (media.type == MediaType.TV_SHOW) {
                media.episodes.firstOrNull() ?: Episode("ep_${media.id}_S1_1", 1, "Episode 1", 45, media.description)
            } else null

            val estimatedSizeMb = episode?.downloadSizeMb ?: 420

            if (estimatedSizeMb <= remainingBudgetMb) {
                val available = try { providerHasDownload(media, episode) }
                    catch (_: com.example.data.PlaybackRateLimitedException) { break }
                if (!available) continue
                currentCoroutineContext().ensureActive()
                if (curatorAccount != accountIdProvider()) return@withContext
                withContext(Dispatchers.Main) {
                    startOrResumeDownload(
                        profileId = profileId,
                        media = media,
                        episode = episode,
                        isWifiOnly = isWifiOnly,
                        isHighQuality = isHighQuality,
                        isForYou = true
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

    @Synchronized
    private fun updateTask(info: DownloadTaskInfo) {
        val previous = _downloadTasks.value[info.downloadKey]
        val task = info.copy(episodeId = info.episodeId ?: previous?.episodeId, profileId = info.profileId ?: previous?.profileId)
        _downloadTasks.update { it + (info.downloadKey to task) }
        requests[info.downloadKey]?.let { request ->
            val updated = request.copy(task = task)
            requests[info.downloadKey] = updated
            if (request.task.status != task.status) requestStore.put(updated)
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
                    description = "NetflixPro Download Notifications"
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
