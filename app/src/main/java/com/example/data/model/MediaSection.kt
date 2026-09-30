package com.example.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

@Immutable
data class MediaSection(
    val id: String,
    val title: String,
    val items: List<MediaItem>,
    val isTop10: Boolean = false,
    val cardWidth: Dp? = null,
    val cardHeight: Dp? = null
)
