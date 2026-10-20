package com.example.ui.viewmodel

/** The player reports twice a second; persistence and smart downloads run once per event. */
internal class PlaybackProgressTracker {
    private var lastSavedSecond = -1
    private var smartDownloadStarted = false

    fun reset() {
        lastSavedSecond = -1
        smartDownloadStarted = false
    }

    fun shouldSave(second: Int): Boolean {
        if (second <= 0 || second % 10 != 0 || second == lastSavedSecond) return false
        lastSavedSecond = second
        return true
    }

    fun claimSmartDownload(): Boolean {
        if (smartDownloadStarted) return false
        smartDownloadStarted = true
        return true
    }
}

internal fun nextEpisodeIndex(currentId: String, episodeIds: List<String>): Int? {
    val currentIndex = episodeIds.indexOf(currentId)
    return (currentIndex + 1).takeIf { currentIndex >= 0 && it < episodeIds.size }
}

/** TV uses s2_e3; mobile uses ep_<title>_S2_3. Both identify the same episode. */
internal fun episodeCoordinates(id: String?, fallbackSeason: Int = 1, fallbackEpisode: Int = 1): Pair<Int, Int> {
    return com.example.data.PlaybackTarget.episodeCoordinates(id, fallbackSeason, fallbackEpisode)
}
