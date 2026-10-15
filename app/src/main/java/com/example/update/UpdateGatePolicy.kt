package com.example.update

/**
 * A pending upgrade is retained across offline entry, failed checks, and rollbacks.
 * Offline entry defers enforcement until the next online app entry; it never dismisses the release.
 */
internal object UpdateGatePolicy {
    sealed interface Decision {
        data object Current : Decision
        data class ContinueOffline(val pendingRequired: UpdateRelease?) : Decision
        data class Required(val release: UpdateRelease, val compatible: Boolean) : Decision
    }

    fun decide(
        installedVersion: Long,
        sdk: Int,
        latest: UpdateRelease?,
        cached: UpdateRelease?,
        online: Boolean = true
    ): Decision {
        val required = listOfNotNull(latest, cached)
            .filter { it.versionCode > installedVersion }.maxByOrNull { it.versionCode }
        if (!online) return Decision.ContinueOffline(required)
        if (required != null) return Decision.Required(required, required.minSdk <= sdk)
        return Decision.Current
    }
}
