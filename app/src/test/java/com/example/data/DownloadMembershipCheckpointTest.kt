package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UserSubscription
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
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
class DownloadMembershipCheckpointTest {
    private val now = 1_800_000_000_000L
    private val expiry = now + 30L * RenewalPolicy.DAY_MS
    private val uid = "download-account"
    private val device = "verified-phone"
    private val subscription = UserSubscription(status = "ACTIVE", planId = "plan_premium", planName = "Premium",
        amount = 1_299, currency = "KES", paymentReference = "confirmed-payment", mpesaReceipt = "CONFIRMED-RECEIPT",
        subscribedAt = now, expiresAt = expiry)
    private val entry = OfflineEntryProof(MembershipProof(uid, device, subscription.planId, expiry, subscription.status),
        subscription, serverVersion = now)
    private lateinit var directory: File
    private lateinit var file: File
    private lateinit var store: OfflineEntryProofStore

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        directory = File(context.noBackupFilesDir, "download-proof-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        file = File(directory, "verified-entry.json")
        store = OfflineEntryProofStore(file)
    }

    @After fun tearDown() { directory.deleteRecursively() }

    @Test fun backgroundVerificationSavesFullPaidMembershipBeforeMediaTransferCanStart() = runBlocking {
        var verifications = 0
        val plan = verifyDownloadMembershipAndCheckpoint(uid, device, store, { now }, { true }) {
            verifications++
            assertNull(store.load(uid, device, now))
            subscription.planId to entry
        }

        assertEquals(1, verifications)
        assertEquals(subscription.planId, plan)
        // The durable checkpoint exists before the caller can begin its media pipeline.
        val restarted = OfflineEntryProofStore(file)
        assertEquals(entry, restarted.load(uid, device, now))
        assertEquals(subscription, restarted.load(uid, device, now)!!.subscription)
        assertNotNull(restarted.load(uid, device, expiry + RenewalPolicy.DAY_MS))
        assertNull(restarted.load(uid, device, expiry + RenewalPolicy.GRACE_MS))
        assertNull(restarted.load("another-account", device, now))
        assertNull(restarted.load(uid, "another-phone", now))
    }

    @Test fun transportFailurePreservesExistingProofAndCannotCreateANewOne() = runBlocking {
        store.save(entry, serverConfirmed = true)
        try {
            verifyDownloadMembershipAndCheckpoint<Unit>(uid, device, store, { now }, { true }) {
                throw MembershipCheckException(IOException("Network disappeared"))
            }
            fail("A transfer cannot start after failed verification")
        } catch (_: MembershipCheckException) { }
        assertEquals(entry, OfflineEntryProofStore(file).load(uid, device, now))
        assertFalse(store.isMigrationBlocked(uid, device))
        store.clear()
        try {
            verifyDownloadMembershipAndCheckpoint<Unit>(uid, device, store, { now }, { true }) {
                throw IOException("Offline")
            }
            fail("An unverified download cannot grant membership")
        } catch (_: IOException) { }
        assertNull(store.load(uid, device, now))
        assertFalse(store.isMigrationBlocked(uid, device))
    }

    @Test fun deviceDenialClearsProofEvenWhenConfirmationInvalidatesItsOwnInMemoryState() = runBlocking {
        store.save(entry, serverConfirmed = true)
        val confirmation = AccessSessionState<String>()
        val generation = confirmation.snapshot().generation
        confirmation.storeIfCurrent(generation, "old-confirmation")
        try {
            verifyDownloadMembershipAndCheckpoint<Unit>(uid, device, store, { now }, { true }) {
                // DeviceAccessGuard clears its in-memory confirmation before propagating a denial.
                confirmation.clearIfCurrent(generation)
                throw DeviceAccessException("Device is bound elsewhere")
            }
            fail("A denied worker cannot transfer")
        } catch (_: DeviceAccessException) { }
        assertNotEquals(generation, confirmation.snapshot().generation)
        assertNull(OfflineEntryProofStore(file).load(uid, device, now))
        assertTrue(OfflineEntryProofStore(file).isMigrationBlocked(uid, device))
    }

    @Test fun serverInactiveMembershipRevokesThePreviouslyPaidOfflineProof() = runBlocking {
        store.save(entry, serverConfirmed = true)
        val inactive = entry.copy(proof = entry.proof.copy(status = "SUSPENDED"),
            subscription = subscription.copy(status = "SUSPENDED"), serverVersion = now + 1)
        try {
            verifyDownloadMembershipAndCheckpoint(uid, device, store, { now }, { true }) { Unit to inactive }
            fail("An inactive membership cannot start a transfer")
        } catch (_: MembershipRevokedException) { }
        assertNull(OfflineEntryProofStore(file).load(uid, device, now))
        assertTrue(store.isMigrationBlocked(uid, device))
    }

    @Test fun accountChangeDuringServerVerificationCannotCheckpointItsResult() = runBlocking {
        var account = uid
        try {
            verifyDownloadMembershipAndCheckpoint(uid, device, store, { now }, { account == uid }) {
                account = "account-b"
                Unit to entry
            }
            fail("A result belonging to the previous account cannot start a transfer")
        } catch (_: MembershipCheckException) { }
        assertNull(store.load(uid, device, now))
        assertNull(store.load(account, device, now))
    }

    @Test fun delayedWorkerSuccessCannotClearNewerSignOutOrRevocation() = runBlocking {
        store.save(entry, serverConfirmed = true)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val result = async {
            runCatching {
                verifyDownloadMembershipAndCheckpoint(uid, device, store, { now }, { true }) {
                    started.complete(Unit)
                    release.await()
                    Unit to entry
                }
            }
        }
        started.await()
        store.blockMigration(uid, device)
        release.complete(Unit)
        assertTrue(result.await().exceptionOrNull() is MembershipCheckException)
        assertTrue(OfflineEntryProofStore(file).isMigrationBlocked(uid, device))
        assertNull(store.load(uid, device, now))
    }

    @Test fun delayedWorkerCannotOverwriteNewerVerifiedTierOrRevokeNewerEntry() = runBlocking {
        for (denied in listOf(false, true)) {
            store.save(entry, serverConfirmed = true)
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val result = async {
                runCatching {
                    verifyDownloadMembershipAndCheckpoint(uid, device, store, { now }, { true }) {
                        started.complete(Unit)
                        release.await()
                        if (denied) throw MembershipRevokedException()
                        Unit to entry
                    }
                }
            }
            started.await()
            val newer = entry.copy(proof = entry.proof.copy(plan = "plan_standard"),
                subscription = subscription.copy(planId = "plan_standard", planName = "Standard"), serverVersion = now + 100)
            store.save(newer, serverConfirmed = true)
            release.complete(Unit)
            assertTrue(result.await().isFailure)
            assertEquals(newer, OfflineEntryProofStore(file).load(uid, device, now))
            assertFalse(store.isMigrationBlocked(uid, device))
        }
    }

    @Test fun delayedAppEntryCannotClearANewerWorkerRevocation() = runBlocking {
        store.save(entry, serverConfirmed = true)
        val entryCheckpointGeneration = store.checkpointGeneration()
        try {
            verifyDownloadMembershipAndCheckpoint<Unit>(uid, device, store, { now }, { true }) {
                throw MembershipRevokedException()
            }
            fail("The worker must reject revoked membership")
        } catch (_: MembershipRevokedException) { }

        assertFalse(store.checkpointServerResult(entryCheckpointGeneration, entry, now, isCurrent = { true }))
        assertTrue(OfflineEntryProofStore(file).isMigrationBlocked(uid, device))
        assertNull(store.load(uid, device, now))
    }

    @Test fun cancellationNeverTurnsAnUnfinishedServerResponseIntoOfflineAccess() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val worker = launch {
            verifyDownloadMembershipAndCheckpoint(uid, device, store, { now }, { true }) {
                started.complete(Unit)
                release.await()
                Unit to entry
            }
        }
        started.await()
        worker.cancelAndJoin()
        assertNull(OfflineEntryProofStore(file).load(uid, device, now))
        assertFalse(store.isMigrationBlocked(uid, device))
    }

    @Test fun failedDiskCheckpointStopsTheNewTransferWithoutRecordingARevocation() = runBlocking {
        val parentFile = File(directory, "not-a-directory").apply { writeText("occupied") }
        val unavailableStore = OfflineEntryProofStore(File(parentFile, "verified-entry.json"))
        try {
            verifyDownloadMembershipAndCheckpoint(uid, device, unavailableStore, { now }, { true }) { Unit to entry }
            fail("A new offline download needs a durable entitlement checkpoint")
        } catch (_: IOException) { }
        assertFalse(unavailableStore.isMigrationBlocked(uid, device))
        assertFalse(parentFile.isDirectory)
    }

    @Test fun entryAndBackgroundWorkersShareTheSameStoreAndWriteGeneration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertSame(OfflineEntryProofStore.get(context), OfflineEntryProofStore.get(context))
    }

    @Test fun verifiedAppEntryStartsDownloadWithoutAnotherFirebaseRequestOrCheckpointRewrite() = runBlocking {
        store.save(entry, serverConfirmed = true)
        val generation = store.checkpointGeneration()
        // Repeating the old forced request fails even though entry already admitted this device.
        val forced = runCatching {
            verifyDownloadMembershipAndCheckpoint<String>(uid, device, store, { now }, { true }) {
                throw MembershipCheckException(IOException("Firebase temporarily unavailable"))
            }
        }
        assertTrue(forced.exceptionOrNull() is MembershipCheckException)
        val result = verifyDownloadMembershipAndCheckpoint(uid, device, store, { now }, { true },
            admittedEntry = { DownloadEntryAuthorization("server-admitted", entry.proof) { true } }) {
            error("A download in this admitted session must not force another Firebase request")
        }
        assertEquals("server-admitted", result)
        assertEquals(generation, store.checkpointGeneration())
        assertEquals(entry, store.load(uid, device, now))
    }

    @Test fun savedProofAloneStillRequiresServerAdmissionForANewBackgroundDownload() = runBlocking {
        store.save(entry, serverConfirmed = true)
        var checked = false
        val result = verifyDownloadMembershipAndCheckpoint(uid, device, store, { now }, { true }) {
            checked = true
            "verified-background" to entry
        }
        assertTrue(checked)
        assertEquals("verified-background", result)
    }

    @Test fun expiredRevokedMismatchedOrInvalidatedEntryCannotAuthorizeADownload() = runBlocking {
        for (kind in listOf("expired", "revoked", "changed-plan", "changed-account", "changed-device", "invalidated", "missing-checkpoint")) {
            store.save(entry, serverConfirmed = true)
            var admitted = entry.proof
            when (kind) {
                "revoked" -> store.blockMigration(uid, device)
                "changed-plan" -> store.save(entry.copy(proof = entry.proof.copy(plan = "plan_basic"),
                    subscription = subscription.copy(planId = "plan_basic")), serverConfirmed = true)
                "changed-account" -> admitted = admitted.copy(uid = "other-account")
                "changed-device" -> admitted = admitted.copy(device = "other-phone")
                "missing-checkpoint" -> store.clear()
            }
            var checked = false
            val result = runCatching {
                verifyDownloadMembershipAndCheckpoint<String>(uid, device, store,
                    { if (kind == "expired") expiry + RenewalPolicy.GRACE_MS else now }, { true },
                    admittedEntry = { DownloadEntryAuthorization("must-not-grant", admitted) { kind != "invalidated" } }) {
                    checked = true
                    throw MembershipCheckException(IOException("Server required"))
                }
            }
            assertTrue("Fresh verification required for $kind", checked)
            assertTrue("Cannot grant $kind entry", result.exceptionOrNull() is MembershipCheckException)
        }
    }

    @Test fun accountSwitchCannotReuseAnotherAccountsLiveEntry() = runBlocking {
        store.save(entry, serverConfirmed = true)
        val result = runCatching {
            verifyDownloadMembershipAndCheckpoint(uid, device, store, { now }, { false },
                admittedEntry = { DownloadEntryAuthorization("must-not-grant", entry.proof) { true } }) {
                error("Account change must reject before server request")
            }
        }
        assertTrue(result.exceptionOrNull() is MembershipCheckException)
    }
}
