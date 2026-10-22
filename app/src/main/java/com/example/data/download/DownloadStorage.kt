package com.example.data.download

import com.example.data.local.DownloadEntity
import com.example.data.local.WatchProgressEntity
import java.io.File

internal data class DownloadStorageEntry(val download: DownloadEntity, val bytes: Long, val watched: Boolean)
internal data class DownloadStorageSnapshot(val freeBytes: Long, val allDownloadBytes: Long,
    val profileBytes: Long, val entries: List<DownloadStorageEntry>)
internal object DownloadStorage {
    fun bytesLabel(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
    fun watched(download: DownloadEntity, progress: List<WatchProgressEntity>): Boolean {
        val episode = com.example.ui.viewmodel.downloadEpisodeId(download.mediaId, download.downloadKey).orEmpty()
        return progress.any { it.profileId == download.profileId && it.mediaId == download.mediaId && it.episodeId == episode &&
            it.totalSeconds > 0 && it.positionSeconds.toLong() * 100 >= it.totalSeconds.toLong() * 90 }
    }
    fun snapshot(directory: File, downloads: List<DownloadEntity>, progress: List<WatchProgressEntity>): DownloadStorageSnapshot {
        val root = directory.canonicalFile
        val files = root.walkTopDown().filter { it.isFile && runCatching { it.canonicalPath.startsWith(root.path + File.separator) }.getOrDefault(false) }.toList()
        val profileFiles = hashSetOf<String>()
        val entries = downloads.filter { it.isComplete }.distinctBy { it.downloadKey }.map { download ->
            val key = download.downloadKey
            val associated = files.filter { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                relative in listOf("$key.ts", "$key.mp4", "$key.m3u8", "$key.integrity.json") ||
                    relative.startsWith("$key.offline/") || relative.startsWith("$key.captions/")
            }
            profileFiles.addAll(associated.map { it.path })
            DownloadStorageEntry(download, associated.sumOf { it.length() }, watched(download, progress))
        }
        return DownloadStorageSnapshot(if (root.exists()) root.usableSpace else root.parentFile?.usableSpace ?: 0L, files.sumOf { it.length() }, files.filter { it.path in profileFiles }.sumOf { it.length() }, entries)
    }
}
