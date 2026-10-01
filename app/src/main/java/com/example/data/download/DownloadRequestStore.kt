package com.example.data.download

import android.util.AtomicFile
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.security.MessageDigest

internal data class SavedDownloadRequest(
    val token: String,
    val accountId: String,
    val profileId: String,
    val media: MediaItem,
    val episode: Episode?,
    val wifiOnly: Boolean,
    val highQuality: Boolean,
    val task: DownloadTaskInfo,
    val completedPath: String? = null,
    val completedBytes: Long = 0,
    val localCaptionsJson: String = "[]",
    val isForYou: Boolean = false
)

/** Persist intent and display state, never expiring stream URLs, cookies or headers. */
internal class DownloadRequestStore(private val directory: File) {
    private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        .adapter(SavedDownloadRequest::class.java)

    @Synchronized fun put(request: SavedDownloadRequest) {
        directory.mkdirs()
        val file = AtomicFile(File(directory, "${downloadWorkId(request.task.downloadKey)}.json"))
        val output = file.startWrite()
        try {
            output.write(adapter.toJson(request).toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) { file.failWrite(output); throw error }
    }

    @Synchronized fun all(): List<SavedDownloadRequest> = directory.listFiles().orEmpty()
        .filter { it.name.endsWith(".json") || it.name.endsWith(".json.bak") }
        .map { it.name.removeSuffix(".bak") }.distinct().mapNotNull { name ->
            runCatching { AtomicFile(File(directory, name)).openRead().use { adapter.fromJson(it.reader().readText()) } }.getOrNull()
        }

    @Synchronized fun remove(key: String) {
        AtomicFile(File(directory, "${downloadWorkId(key)}.json")).delete()
    }
}

internal fun downloadWorkId(key: String): String = MessageDigest.getInstance("SHA-256")
    .digest(key.toByteArray()).joinToString("") { "%02x".format(it) }

internal fun downloadWorkName(key: String) = "netflixpro.download.${downloadWorkId(key)}"
