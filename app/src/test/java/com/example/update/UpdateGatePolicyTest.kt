package com.example.update

import org.junit.Assert.*
import org.junit.Test

class UpdateGatePolicyTest {
    private fun release(code: Long, sdk: Int = 24) = UpdateRelease(
        "mobile", "com.netflixpro.apk", code, "v$code", sdk, 1024, "a".repeat(64),
        "https://npro-app.vercel.app/downloads/mobile/$code.apk", "", "{}"
    )

    @Test fun latestInstalledReleaseUnlocks() {
        assertEquals(UpdateGatePolicy.Decision.Current, UpdateGatePolicy.decide(10, 34, release(10), release(9)))
    }

    @Test fun newerReleaseRequiresInstallation() {
        assertEquals(UpdateGatePolicy.Decision.Required(release(11), true), UpdateGatePolicy.decide(10, 34, release(11), null))
    }

    @Test fun failedCheckCannotDismissKnownRequiredRelease() {
        assertEquals(UpdateGatePolicy.Decision.Required(release(11), true), UpdateGatePolicy.decide(10, 34, null, release(11)))
    }

    @Test fun rolledBackManifestCannotDismissKnownRequiredRelease() {
        assertEquals(UpdateGatePolicy.Decision.Required(release(12), true), UpdateGatePolicy.decide(10, 34, release(11), release(12)))
    }

    @Test fun offlineFirstLaunchDoesNotRequireAnUpdateWithoutEvidenceOfANewerRelease() {
        assertEquals(UpdateGatePolicy.Decision.Current, UpdateGatePolicy.decide(10, 34, null, null))
    }

    @Test fun offlineLaunchWithOlderCachedReleaseAllowsEntry() {
        assertEquals(UpdateGatePolicy.Decision.Current, UpdateGatePolicy.decide(10, 34, null, release(9)))
    }

    @Test fun offlineLaunchAfterInstallingCachedReleaseAllowsEntry() {
        assertEquals(UpdateGatePolicy.Decision.Current, UpdateGatePolicy.decide(10, 34, null, release(10)))
    }

    @Test fun unavailableManifestKeepsIncompatibleCachedUpgradeRequired() {
        assertEquals(UpdateGatePolicy.Decision.Required(release(11, 35), false), UpdateGatePolicy.decide(10, 34, null, release(11, 35)))
    }

    @Test fun offlineEntryDefersAndRetainsKnownRequiredRelease() {
        val pending = release(11)
        val decision = UpdateGatePolicy.decide(10, 34, null, pending, online = false)
        assertEquals(UpdateGatePolicy.Decision.ContinueOffline(pending), decision)
        // Re-entering with connectivity enforces the same retained release.
        assertEquals(UpdateGatePolicy.Decision.Required(pending, true), UpdateGatePolicy.decide(10, 34, null, pending, online = true))
    }

    @Test fun offlineEntryDefersEvenAnIncompatibleRequiredReleaseWithoutDiscardingIt() {
        val pending = release(11, 35)
        assertEquals(UpdateGatePolicy.Decision.ContinueOffline(pending), UpdateGatePolicy.decide(10, 34, null, pending, online = false))
        assertEquals(UpdateGatePolicy.Decision.Required(pending, false), UpdateGatePolicy.decide(10, 34, null, pending, online = true))
    }

    @Test fun offlineEntryDoesNotRetainAnAlreadyInstalledReleaseAsPending() {
        assertEquals(UpdateGatePolicy.Decision.ContinueOffline(null), UpdateGatePolicy.decide(10, 34, null, release(10), online = false))
    }

    @Test fun offlineEntryKeepsHighestKnownPendingVersionAfterManifestRollback() {
        assertEquals(UpdateGatePolicy.Decision.ContinueOffline(release(12)), UpdateGatePolicy.decide(10, 34, release(11), release(12), online = false))
    }

    @Test fun incompatibleNewReleaseCannotUnlockOldApp() {
        assertEquals(UpdateGatePolicy.Decision.Required(release(11, 35), false), UpdateGatePolicy.decide(10, 34, release(11, 35), null))
    }
}
