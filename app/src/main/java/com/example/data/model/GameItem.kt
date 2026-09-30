package com.example.data.model

data class GameItem(
    val id: String,
    val title: String,
    val developer: String,
    val category: String,
    val maturityRating: String, // e.g. "18+", "12+", "Everyone"
    val sizeDisplay: String, // e.g. "2.8 GB", "420 MB"
    val description: String,
    val bannerUrl: String? = null,
    val iconUrl: String? = null,
    val bannerRes: Int? = null,
    val isInstalled: Boolean = false,
    val downloadProgress: Float? = null // null if idle, 0..1f if downloading
) {
    fun isKidFriendly(): Boolean {
        val rating = maturityRating.uppercase()
        return rating.contains("EVERYONE") || rating.contains("ALL") || rating.contains("E") || rating.contains("10+") || rating.contains("PG") || rating.contains("7+")
    }
}
