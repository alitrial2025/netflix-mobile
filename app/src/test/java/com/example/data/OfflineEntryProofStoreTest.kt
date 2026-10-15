package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UserSubscription
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineEntryProofStoreTest {
    private val expiry = 1_800_000_000_000L
    private val subscription = UserSubscription(status = "ACTIVE", planId = "plan_premium", planName = "Premium", expiresAt = expiry)
    private val entry = OfflineEntryProof(MembershipProof("account-a", "phone-a", subscription.planId, expiry, subscription.status), subscription)
    private lateinit var file: File
    private lateinit var store: OfflineEntryProofStore

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = File(context.noBackupFilesDir, "test-verified-entry-${java.util.UUID.randomUUID()}.json")
        store = OfflineEntryProofStore(file)
        store.clear()
    }

    @After fun tearDown() {
        store.clear()
        File(file.parentFile, "${file.name}.blocked").delete()
    }

    @Test fun processRestartRestoresOnlyPreviouslyAdmittedMembershipWithItsOriginalExpiry() {
        store.save(entry)
        val restarted = OfflineEntryProofStore(file)
        val restored = restarted.load("account-a", "phone-a", expiry - 1L)!!
        assertEquals(entry, restored)
        val admission = EntrySessionAdmission().apply { admit(restored.proof.uid, restored.proof.device) }
        repeat(10) { admission.onRefreshFailure(authoritative = false) }
        assertTrue(admission.isAdmitted("account-a", "phone-a"))
        assertEquals(expiry, restarted.load("account-a", "phone-a", expiry + RenewalPolicy.DAY_MS)!!.proof.expiry)
        assertNull(restarted.load("account-a", "phone-a", expiry + RenewalPolicy.GRACE_MS))
    }

    @Test fun offlineRestartPreservesAllPreviouslyVerifiedSubscriptionAndPaymentDetails() {
        val paidSubscription = subscription.copy(amount = 1_299, currency = "USD", paymentReference = "checkout-reference-123",
            mpesaReceipt = "VERIFIED-RECEIPT-456", subscribedAt = expiry - 30L * RenewalPolicy.DAY_MS)
        val paidEntry = entry.copy(subscription = paidSubscription, serverVersion = expiry - RenewalPolicy.DAY_MS)
        store.save(paidEntry)

        val restored = OfflineEntryProofStore(file).load("account-a", "phone-a", expiry - 1L)!!

        assertEquals(paidEntry, restored)
        assertEquals(paidSubscription, restored.subscription)
    }

    @Test fun restartWithoutServerAdmissionCannotCreateOfflineAccess() {
        assertNull(store.load("account-a", "phone-a", expiry - 1L))
        assertFalse(EntrySessionAdmission().isAdmitted("account-a", "phone-a"))
    }

    @Test fun cachedAdmissionCannotMoveToAnotherAccountOrInstallation() {
        store.save(entry)
        assertNull(store.load("account-b", "phone-a", expiry - 1L))
        assertNull(store.load("account-a", "phone-b", expiry - 1L))
        assertNull(store.load("", "phone-a", expiry - 1L))
        assertNull(store.load("account-a", "", expiry - 1L))
    }

    @Test fun authoritativeSignOutOrRevocationRemovesAdmissionAcrossRestarts() {
        store.save(entry)
        store.blockMigration("account-a", "phone-a")
        assertNull(OfflineEntryProofStore(file).load("account-a", "phone-a", expiry - 1L))
        assertFalse(file.exists())
        assertTrue(OfflineEntryProofStore(file).isMigrationBlocked("account-a", "phone-a"))
    }

    @Test fun oldCachedMembershipCannotClearRevocationButFreshServerConfirmationCan() {
        store.save(entry, serverConfirmed = true)
        store.blockMigration("account-a", "phone-a")
        val restarted = OfflineEntryProofStore(file)
        restarted.save(entry)
        assertTrue(restarted.isMigrationBlocked("account-a", "phone-a"))
        assertNull(restarted.load("account-a", "phone-a", expiry - 1L))

        restarted.save(entry, serverConfirmed = true)
        assertFalse(restarted.isMigrationBlocked("account-a", "phone-a"))
        assertEquals(entry, restarted.load("account-a", "phone-a", expiry - 1L))
    }

    @Test fun tombstonesAreScopedToTheDeniedAccountAndInstallation() {
        store.blockMigration("account-a", "phone-a")
        store.blockMigration("account-b", "phone-a")
        assertTrue(store.isMigrationBlocked("account-a", "phone-a"))
        assertTrue(store.isMigrationBlocked("account-b", "phone-a"))
        assertFalse(store.isMigrationBlocked("account-a", "phone-b"))
        store.save(entry, serverConfirmed = true)
        assertFalse(store.isMigrationBlocked("account-a", "phone-a"))
        assertTrue(store.isMigrationBlocked("account-b", "phone-a"))
    }

    @Test fun corruptRevocationMarkerCannotResurrectOldMembership() {
        file.parentFile!!.mkdirs()
        File(file.parentFile, "${file.name}.blocked").writeText("{truncated")
        assertTrue(store.isMigrationBlocked("account-a", "phone-a"))
        store.save(entry)
        assertNull(store.load("account-a", "phone-a", expiry - 1L))
        store.save(entry, serverConfirmed = true)
        assertEquals(entry, store.load("account-a", "phone-a", expiry - 1L))
        assertTrue(store.isMigrationBlocked("account-b", "phone-a"))
    }

    @Test fun serverPlanOrExpiryChangeCannotFallBackToAnOlderHigherTier() {
        store.save(entry)
        assertNull(store.load("account-a", "phone-a", expiry - 1L, subscription.copy(planId = "plan_mobile")))
        assertNull(store.load("account-a", "phone-a", expiry - 1L, subscription.copy(expiresAt = expiry - RenewalPolicy.DAY_MS)))
        assertNull(store.load("account-a", "phone-a", expiry - 1L, subscription.copy(status = "SUSPENDED")))
        assertNotNull(store.load("account-a", "phone-a", expiry, subscription.copy(status = "GRACE_PERIOD")))
    }

    @Test fun invalidRevokedOrUnknownPaidMembershipCannotBeRestored() {
        for (invalid in listOf(
            entry.copy(proof = entry.proof.copy(status = "SUSPENDED")),
            entry.copy(proof = entry.proof.copy(plan = "plan_admin")),
            entry.copy(proof = entry.proof.copy(expiry = 0L))
        )) {
            store.save(invalid)
            assertNull(store.load("account-a", "phone-a", expiry - 1L))
        }
    }

    @Test fun truncatedOrUnsupportedCacheCannotGrantAccess() {
        file.parentFile!!.mkdirs()
        file.writeText("{\"version\":1,\"uid\":\"account-a\"")
        assertNull(store.load("account-a", "phone-a", expiry - 1L))
        store.save(entry)
        file.writeText(file.readText().replace("\"version\":1", "\"version\":2"))
        assertNull(store.load("account-a", "phone-a", expiry - 1L))
    }

    @Test fun networkAndRetryableListenerFailuresCannotRevokeAdmission() {
        for (code in listOf(FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.ABORTED, FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED)) {
            val failure = MembershipCheckException(FirebaseFirestoreException("Temporary transport failure", code))
            assertFalse(failure.isAuthoritativeAccessFailure())
            val admission = EntrySessionAdmission().apply { admit("account-a", "phone-a") }
            admission.onRefreshFailure(failure.isAuthoritativeAccessFailure())
            assertTrue(admission.isAdmitted("account-a", "phone-a"))
        }
        assertFalse(MembershipCheckException(java.net.SocketException("Network is unreachable")).isAuthoritativeAccessFailure())
    }

    @Test fun explicitMembershipDeviceAndServerAuthorizationDenialsStillRevokeAdmission() {
        val denials = listOf(MembershipRevokedException(), DeviceAccessException("Device removed"),
            FirebaseFirestoreException("Access denied", FirebaseFirestoreException.Code.PERMISSION_DENIED),
            FirebaseFirestoreException("Invalid account credentials", FirebaseFirestoreException.Code.UNAUTHENTICATED))
        for (failure in denials) {
            assertTrue(MembershipCheckException(failure).isAuthoritativeAccessFailure())
            val admission = EntrySessionAdmission().apply { admit("account-a", "phone-a") }
            admission.onRefreshFailure(failure.isAuthoritativeAccessFailure())
            assertFalse(admission.isAdmitted("account-a", "phone-a"))
        }
    }

    @Test fun fullOnlineScreensDoNotRevokeVerifiedOfflineMembership() {
        store.save(entry, serverConfirmed = true)
        val failure = MembershipCheckException(ScreenLimitException("Screens in use"))
        assertFalse(failure.isAuthoritativeAccessFailure())
        val admission = EntrySessionAdmission().apply { admit("account-a", "phone-a") }
        admission.onRefreshFailure(failure.isAuthoritativeAccessFailure())
        assertTrue(admission.isAdmitted("account-a", "phone-a"))
        assertEquals(entry, store.load("account-a", "phone-a", expiry - 1L))
    }
}
