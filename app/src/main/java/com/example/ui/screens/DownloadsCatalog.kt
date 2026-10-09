package com.example.ui.screens

import com.example.data.local.DownloadEntity
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.viewmodel.downloadEpisodeId

/** Episode keys retain title type even when the catalog or episode label is unavailable. */
internal fun downloadedMedia(download: DownloadEntity, catalog: List<MediaItem>): MediaItem {
    val type = if (downloadEpisodeId(download.mediaId, download.downloadKey) != null) MediaType.TV_SHOW else MediaType.MOVIE
    return catalog.firstOrNull { it.id == download.mediaId && it.type == type }
        ?: MediaItem(download.mediaId, download.mediaTitle, type, "", "", 0, "16+", 0, "Downloaded",
            genres = emptyList(), cast = emptyList(), director = "", isOriginal = false)
}
