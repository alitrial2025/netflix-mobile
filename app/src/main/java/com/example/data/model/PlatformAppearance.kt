package com.example.data.model

/** Appearance only. Never changes membership, playback sources, or provider availability. */
enum class PlatformAppearance(val key: String, val label: String) {
    ORIGINAL("original", "NetflixPro"), NETFLIX("netflix", "Netflix"),
    PRIME("prime", "Prime Video"), DISNEY("disney", "Disney+"),
    HOTSTAR("hotstar", "Hotstar"), APPLE("apple", "Apple TV"), PEACOCK("peacock", "Peacock");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: ORIGINAL
        fun fromCategory(label: String?): PlatformAppearance? = HomeCategory.fromLabel(label)
            ?.takeIf { it.isService }?.let { category -> entries.firstOrNull { it.key == category.key } }
        fun effective(enabled: Boolean, selected: PlatformAppearance) = if (enabled) selected else ORIGINAL
    }
}
