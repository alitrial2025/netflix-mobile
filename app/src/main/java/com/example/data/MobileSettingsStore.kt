package com.example.data

import android.content.SharedPreferences

enum class MobileSetting(val key: String, val defaultValue: Boolean = true) {
    WIFI_ONLY_DOWNLOADS("pref_wifi_only_downloads"), HIGH_QUALITY("pref_high_quality"),
    AUTOPLAY_NEXT("pref_autoplay_next"), AUTOPLAY_PREVIEWS("pref_autoplay_previews"),
    GLASSY_THEME("pref_glassy_theme"), PLATFORMS("pref_platforms", false), SPATIAL_AUDIO("pref_spatial_audio")
}

enum class CellularDataMode(val label: String) {
    AUTOMATIC("Automatic (Balanced)"), WIFI_ONLY("Wi-Fi Only"),
    SAVE_DATA("Save Data"), MAXIMUM("Maximum Data");

    companion object {
        fun fromLabel(label: String) = entries.firstOrNull { it.label == label } ?: AUTOMATIC
    }
}

class MobileSettingsStore(private val prefs: SharedPreferences) {
    fun get(setting: MobileSetting) = prefs.getBoolean(setting.key, setting.defaultValue)
    fun set(setting: MobileSetting, value: Boolean) { prefs.edit().putBoolean(setting.key, value).apply() }
    var selectedPlatform: com.example.data.model.PlatformAppearance
        get() = com.example.data.model.PlatformAppearance.fromKey(prefs.getString("pref_platform_style", null))
        set(value) { prefs.edit().putString("pref_platform_style", value.key).apply() }
    val effectivePlatform: com.example.data.model.PlatformAppearance
        get() = com.example.data.model.PlatformAppearance.effective(get(MobileSetting.PLATFORMS), selectedPlatform)
    fun setPlatformsEnabled(enabled: Boolean, currentCategory: String? = null) {
        val selected = com.example.data.model.PlatformAppearance.fromCategory(currentCategory)
        prefs.edit().putBoolean(MobileSetting.PLATFORMS.key, enabled).apply {
            if (enabled && selected != null) putString("pref_platform_style", selected.key)
        }.apply()
    }
    fun selectPlatformCategory(label: String?) {
        if (get(MobileSetting.PLATFORMS)) com.example.data.model.PlatformAppearance.fromCategory(label)?.let { selectedPlatform = it }
    }
    var cellularDataMode: CellularDataMode
        get() = CellularDataMode.fromLabel(prefs.getString("pref_cellular_data", null).orEmpty())
        set(value) { prefs.edit().putString("pref_cellular_data", value.label).apply() }
}

object MobilePlaybackPolicy {
    fun permitsStreaming(mode: CellularDataMode, wifiOrEthernet: Boolean) =
        mode != CellularDataMode.WIFI_ONLY || wifiOrEthernet

    fun maxVideoHeight(highQuality: Boolean, mode: CellularDataMode, wifiOrEthernet: Boolean, planId: String): Int {
        val planLimit = when (planId) {
            "plan_mobile" -> 480
            "plan_basic" -> 720
            "plan_standard" -> 1080
            "plan_premium" -> 2160
            else -> 1080 // Trailer previews.
        }
        val qualityLimit = if (highQuality) planLimit else minOf(planLimit, 720)
        return if (wifiOrEthernet) qualityLimit else when (mode) {
            CellularDataMode.SAVE_DATA -> minOf(qualityLimit, 480)
            CellularDataMode.AUTOMATIC -> minOf(qualityLimit, 1080)
            else -> qualityLimit
        }
    }
}
