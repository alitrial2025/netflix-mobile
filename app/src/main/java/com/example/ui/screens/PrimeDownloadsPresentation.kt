package com.example.ui.screens

import com.example.data.SeriesEpisodeOrder
import com.example.data.local.DownloadEntity
import com.example.data.model.MediaItem
import com.example.ui.viewmodel.downloadEpisodeId
import java.util.Locale

internal fun primeDownloadedSeason(download: DownloadEntity): Int? {
    val id = downloadEpisodeId(download.mediaId, download.downloadKey) ?: return null
    val canonical = Regex("(?:_S|^s)(\\d+)(?:_e|_)(\\d+)$", RegexOption.IGNORE_CASE).find(id)
    val stored = Regex("^S(\\d+):E(\\d+)", RegexOption.IGNORE_CASE).find(download.episodeTitle.orEmpty())
    val season = canonical?.groupValues?.get(1)?.toIntOrNull() ?: stored?.groupValues?.get(1)?.toIntOrNull() ?: return null
    val episode = canonical?.groupValues?.get(2)?.toIntOrNull() ?: stored?.groupValues?.get(2)?.toIntOrNull() ?: 1
    return SeriesEpisodeOrder.displaySeason(download.mediaId, season, episode)
}

internal fun primeDownloadsSummary(downloads: List<DownloadEntity>, catalog: Map<String, MediaItem>): String {
    val ready = downloads.filter { it.isComplete }.distinctBy { it.downloadKey }
    val bytesMb = ready.sumOf { it.fileSizeMb.toLong().coerceAtLeast(0) }
    val sizes = if (bytesMb >= 1024) String.format(Locale.US, "%.1f GB", bytesMb / 1024.0) else "$bytesMb MB"
    val knownMinutes = ready.map { row ->
        val id = downloadEpisodeId(row.mediaId, row.downloadKey)
        catalog[row.mediaId]?.episodes?.firstOrNull { it.id == id }?.durationMinutes?.takeIf { it > 0 }
    }
    val runtime = if (ready.isNotEmpty() && knownMinutes.all { it != null }) {
        val minutes = knownMinutes.sumOf { it!!.toLong() }
        if (minutes >= 60) "${minutes / 60}h ${minutes % 60}min" else "${minutes}min"
    } else null
    return listOfNotNull("${ready.size} video${if (ready.size == 1) "" else "s"}", runtime, sizes).joinToString("  ")
}
