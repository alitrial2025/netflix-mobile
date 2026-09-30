package com.example.data.model

enum class NotificationIconType {
    BELL, NEW_SEASON, DOWNLOAD, REMINDER, LEAVING_SOON
}

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: String,
    val mediaId: String? = null,
    val isRead: Boolean = false,
    val iconType: NotificationIconType = NotificationIconType.BELL,
    val imageUrl: String? = null
)

data class TrailerItem(
    val id: String,
    val media: MediaItem,
    val trailerTitle: String,
    val durationStr: String,
    val watchedTimeAgo: String
)

data class CastDevice(
    val id: String,
    val name: String,
    val type: String = "Google TV • Living Room",
    val isConnected: Boolean = false
)
