package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UserSubscription
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineMembershipRecoveryTest {
    private val now = 1_790_000_000_000L
    private val subscription = UserSubscription(status = "ACTIVE", planId = "plan_premium", planName = "Premium",
        amount = 1_299, currency = "KES", paymentReference = "verified-payment", mpesaReceipt = "verified-receipt",
        subscribedAt = now - RenewalPolicy.DAY_MS, expiresAt = now + 29L * RenewalPolicy.DAY_MS)
    private lateinit var folder: File
    private lateinit var file: File
    private lateinit var store: OfflineEntryProofStore

    private class Epoch : OfflineEntryEpoch {
        var current = true
        override fun ensureCurrent() { check(current) { "Account/session changed" } }
        override fun <T> withCurrent(action: () -> T): T { ensureCurrent(); return action() }
    }

    private class CachedReader(var cached: CachedOfflineSubscription?, var cachedBinding: CachedOfflineBinding? = null) : OfflineMembershipCacheReader {
        var subscriptionReads = 0
        var bindingReads = 0
        var afterSubscription: () -> Unit = {}
        override suspend fun subscription(uid: String): CachedOfflineSubscription? {
            assertEquals("account-a", uid)
            subscriptionReads++
            afterSubscription()
            return cached
        }
        override suspend fun binding(uid: String): CachedOfflineBinding? {
            assertEquals("account-a", uid)
            bindingReads++
            return cachedBinding
        }
    }

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        folder = File(context.noBackupFilesDir, "recovery-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        file = File(folder, "proof.json")
        store = OfflineEntryProofStore(file)
    }

    @After fun tearDown() { folder.deleteRecursively() }

    private fun entry(sub: UserSubscription = subscription, version: Long = 100L) = OfflineEntryProof(
        MembershipProof("account-a", "phone-a", sub.planId, sub.expiresAt, sub.status), sub, version)

    private fun cached(sub: UserSubscription = subscription, version: Long = 100L) =
        CachedOfflineSubscription("account-a", true, false, sub, version)

    private suspend fun recover(reader: CachedReader, epoch: Epoch = Epoch(), at: Long = now, expected: UserSubscription? = null) =
        recoverOfflineMembershipProof("account-a", "phone-a", at, expected, store, reader, epoch)

    @Test fun existingPremiumInstallRecoversCommittedCacheWithoutAnyOnlineSlotOrProofFile() = runBlocking {
        val reader = CachedReader(cached())
        assertFalse(file.exists())

        val restored = recover(reader)

        assertEquals(entry(), restored)
        assertEquals(subscription, restored!!.subscription)
        assertEquals(1, reader.subscriptionReads)
        assertEquals(0, reader.bindingReads)
        assertEquals(restored, OfflineEntryProofStore(file).load("account-a", "phone-a", now))
    }

    @Test fun legacyMobilePlanRecoversOnlyItsCommittedMatchingDeviceBinding() = runBlocking {
        val mobile = subscription.copy(planId = "plan_mobile", planName = "Mobile")
        val reader = CachedReader(cached(mobile), CachedOfflineBinding("account-a", true, false, "phone-a"))
        assertEquals(entry(mobile), recover(reader))
        assertEquals(1, reader.bindingReads)
    }

    @Test fun absentOrPendingSubscriptionCannotCreateMembershipProof() = runBlocking {
        for (snapshot in listOf(null, cached().copy(pendingWrites = true), cached().copy(uid = "account-b"))) {
            val reader = CachedReader(snapshot)
            assertNull(recover(reader))
            assertFalse(file.exists())
            assertEquals(0, reader.bindingReads)
        }
    }

    @Test fun missingOrPendingDeviceBindingCannotGrantLegacySingleDeviceAccess() = runBlocking {
        val basic = subscription.copy(planId = "plan_basic")
        for (binding in listOf(null, CachedOfflineBinding("account-a", false, false, null),
            CachedOfflineBinding("account-a", true, true, "phone-a"))) {
            assertNull(recover(CachedReader(cached(basic), binding)))
            assertFalse(file.exists())
        }
    }

    @Test fun newerCommittedDeviceMismatchInvalidatesOlderProofAndBlocksResurrection() = runBlocking {
        val basic = subscription.copy(planId = "plan_basic")
        store.save(entry(basic), serverConfirmed = true)
        val reader = CachedReader(cached(basic, 101L), CachedOfflineBinding("account-a", true, false, "phone-b"))
        assertNull(recover(reader))
        assertTrue(store.isMigrationBlocked("account-a", "phone-a"))
        assertNull(store.load("account-a", "phone-a", now))
        assertNull(recover(CachedReader(cached())))
    }

    @Test fun knownNewerRevocationInvalidatesProofButOlderSnapshotCannotRevokeNewerConfirmation() = runBlocking {
        store.save(entry(version = 100L), serverConfirmed = true)
        assertEquals(entry(), recover(CachedReader(cached(subscription.copy(status = "SUSPENDED"), 99L))))
        assertFalse(store.isMigrationBlocked("account-a", "phone-a"))
        assertNull(recover(CachedReader(cached(subscription.copy(status = "SUSPENDED"), 101L))))
        assertTrue(store.isMigrationBlocked("account-a", "phone-a"))
    }

    @Test fun freshServerTierCannotBeReplacedByAnUnversionedActiveLegacyCache() = runBlocking {
        val basic = subscription.copy(planId = "plan_basic")
        store.save(entry(basic, 100L), serverConfirmed = true)
        val reader = CachedReader(cached(subscription, 0L))
        assertEquals(entry(basic), recover(reader))
        assertEquals(0, reader.bindingReads)
    }

    @Test fun definitiveDenialTombstoneStopsCacheMigrationAcrossRestart() = runBlocking {
        store.blockMigration("account-a", "phone-a")
        val reader = CachedReader(cached())
        val restarted = OfflineEntryProofStore(file)
        assertNull(recoverOfflineMembershipProof("account-a", "phone-a", now, null, restarted, reader, Epoch()))
        assertEquals(0, reader.subscriptionReads)
        assertTrue(restarted.isMigrationBlocked("account-a", "phone-a"))
    }

    @Test fun cacheCannotExtendOriginalExpiryOrOverrideAuthoritativeExpectedTier() = runBlocking {
        assertNull(recover(CachedReader(cached()), at = subscription.expiresAt + RenewalPolicy.GRACE_MS))
        store.save(entry(), serverConfirmed = true)
        assertNull(recover(CachedReader(cached()), expected = subscription.copy(planId = "plan_mobile")))
        assertTrue(store.isMigrationBlocked("account-a", "phone-a"))
    }

    @Test fun accountChangeDuringCacheReadCannotOverwriteCurrentAccountProofOrMarkers() = runBlocking {
        val accountB = entry().copy(proof = entry().proof.copy(uid = "account-b"))
        store.save(accountB, serverConfirmed = true)
        val epoch = Epoch()
        val reader = CachedReader(cached()).apply { afterSubscription = { epoch.current = false } }
        try {
            recover(reader, epoch)
            fail("Stale account recovery must be rejected")
        } catch (_: IllegalStateException) {
            assertEquals(accountB, store.load("account-b", "phone-a", now))
            assertFalse(store.isMigrationBlocked("account-a", "phone-a"))
            assertFalse(store.isMigrationBlocked("account-b", "phone-a"))
        }
    }
}
