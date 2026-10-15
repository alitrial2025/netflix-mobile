package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UserSubscription
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
class OfflineEntryCheckpointRaceTest {
    private val now = 1_800_000_000_000L
    private val uid = "entry-account"
    private val device = "entry-phone"
    private val subscription = UserSubscription(status = "ACTIVE", planId = "plan_premium", planName = "Premium",
        expiresAt = now + 30L * RenewalPolicy.DAY_MS)
    private val entry = OfflineEntryProof(MembershipProof(uid, device, subscription.planId, subscription.expiresAt,
        subscription.status), subscription, serverVersion = now)
    private lateinit var directory: File
    private lateinit var store: OfflineEntryProofStore

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        directory = File(context.noBackupFilesDir, "entry-race-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        store = OfflineEntryProofStore(File(directory, "proof.json"))
    }

    @After fun tearDown() { directory.deleteRecursively() }

    private fun checkpoint() = store.checkpointEntryServerResult(store.checkpointGeneration(), entry, now,
        isCurrent = { true }, onStorageFailure = { throw AssertionError("Unexpected storage failure", it) })!!

    @Test fun workerRevocationDuringScreenClaimPreventsBothOnlineAdmissionAndFailureFallback() = runBlocking {
        // These are the three outcomes of the production entry's suspended ScreenLease.acquire.
        for (leaseFailure in listOf(null, IOException("Wi-Fi disappeared"), ScreenLimitException("All screens busy"))) {
            val verified = checkpoint()
            val claimStarted = CompletableDeferred<Unit>()
            val claimFinished = CompletableDeferred<Unit>()
            val admission = EntrySessionAdmission()
            val confirmation = AccessSessionState<MembershipProof>()
            val pendingEntry = async {
                try {
                    claimStarted.complete(Unit)
                    claimFinished.await()
                    if (leaseFailure != null) throw leaseFailure
                } catch (_: IOException) {
                    // A screen limit/transport interruption can use the fresh in-memory proof,
                    // but only through the same checkpoint fence as successful online entry.
                }
                store.withCheckpoint(verified, uid, device, now, { true }) { proof ->
                    confirmation.storeIfCurrent(confirmation.snapshot().generation, proof.proof)
                    admission.admit(uid, device)
                }
            }
            claimStarted.await()
            try {
                verifyDownloadMembershipAndCheckpoint<Unit>(uid, device, store, { now }, { true },
                    onDenial = { confirmation.clear() }) { throw MembershipRevokedException() }
                fail("The worker must reject revoked membership")
            } catch (_: MembershipRevokedException) { }
            claimFinished.complete(Unit)

            assertNull(pendingEntry.await())
            assertFalse(admission.isAdmitted(uid, device))
            assertNull(confirmation.snapshot().value)
            assertTrue(store.isMigrationBlocked(uid, device))
            assertNull(store.load(uid, device, now))
        }
    }

    @Test fun staleInactiveEntryOrScreenDenialCannotDeleteANewerWorkerConfirmation() = runBlocking {
        for (afterEntryCheckpoint in listOf(false, true)) {
            val generation = if (afterEntryCheckpoint) checkpoint().generation else store.checkpointGeneration()
            val newer = entry.copy(proof = entry.proof.copy(plan = "plan_standard"),
                subscription = subscription.copy(planId = "plan_standard", planName = "Standard"), serverVersion = now + 1)
            verifyDownloadMembershipAndCheckpoint(uid, device, store, { now }, { true }) { Unit to newer }

            assertFalse(store.blockIfCurrent(generation, uid, device, { true }))
            assertEquals(newer, store.load(uid, device, now))
            assertFalse(store.isMigrationBlocked(uid, device))
        }
    }

    @Test fun entryWriteReturnsItsOwnGenerationAndRejectsAnOlderWorkerResponse() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val olderWorker = async {
            runCatching {
                verifyDownloadMembershipAndCheckpoint(uid, device, store, { now }, { true }) {
                    started.complete(Unit)
                    finish.await()
                    Unit to entry
                }
            }
        }
        started.await()
        val verified = checkpoint()
        finish.complete(Unit)
        assertTrue(olderWorker.await().exceptionOrNull() is MembershipCheckException)
        assertEquals(verified.generation, store.checkpointGeneration())
        assertEquals(entry, store.withCheckpoint(verified, uid, device, now, { true }) { it })
    }

    @Test fun storageFailureRetainsOnlyTheCurrentUnexpiredAccountScopedInMemoryEntryProof() {
        val parentFile = File(directory, "not-a-directory").apply { writeText("occupied") }
        val unavailable = OfflineEntryProofStore(File(parentFile, "proof.json"))
        var storageFailures = 0
        val verified = unavailable.checkpointEntryServerResult(unavailable.checkpointGeneration(), entry, now,
            { true }, { storageFailures++ })!!

        assertEquals(1, storageFailures)
        assertEquals(entry, unavailable.withCheckpoint(verified, uid, device, now, { true }) { it })
        assertNull(unavailable.withCheckpoint(verified, "other-account", device, now, { true }) { it })
        assertNull(unavailable.withCheckpoint(verified, uid, "other-phone", now, { true }) { it })
        assertNull(unavailable.withCheckpoint(verified, uid, device, now, { false }) { it })
        assertNull(unavailable.withCheckpoint(verified, uid, device, entry.proof.expiry + RenewalPolicy.GRACE_MS, { true }) { it })
        unavailable.clear()
        assertNull(unavailable.withCheckpoint(verified, uid, device, now, { true }) { it })
    }

    @Test fun workerDenialInvalidatesAProofRestoredBetweenItsServerFailureAndDurableTombstone() = runBlocking {
        val verified = checkpoint()
        val confirmation = AccessSessionState<MembershipProof>()
        try {
            verifyDownloadMembershipAndCheckpoint<Unit>(uid, device, store, { now }, { true },
                onDenial = { confirmation.clear() }) {
                // DeviceAccessGuard clears before propagating the authoritative server failure.
                confirmation.clear()
                store.withCheckpoint(verified, uid, device, now, { true }) { proof ->
                    confirmation.storeIfCurrent(confirmation.snapshot().generation, proof.proof)
                }
                throw DeviceAccessException("Device was rebound")
            }
            fail("A denied device cannot keep a restored confirmation")
        } catch (_: DeviceAccessException) { }
        assertNull(confirmation.snapshot().value)
        assertTrue(store.isMigrationBlocked(uid, device))
    }

    @Test fun freshWorkerCheckpointReappliesItsTierAfterAnOlderEntryRestoresDuringVerification() = runBlocking {
        val olderEntry = checkpoint()
        val newer = entry.copy(proof = entry.proof.copy(plan = "plan_standard"),
            subscription = subscription.copy(planId = "plan_standard", planName = "Standard"), serverVersion = now + 1)
        val confirmation = AccessSessionState<MembershipProof>()
        val confirmationGeneration = confirmation.snapshot().generation
        var confirmations = 0

        verifyDownloadMembershipAndCheckpoint(uid, device, store, { now }, { true },
            onConfirmed = { _, verified ->
                confirmations++
                confirmation.storeIfCurrent(confirmationGeneration, verified.proof)
            }) {
            // The server transaction publishes its newer tier before releasing the guard mutex.
            confirmation.storeIfCurrent(confirmationGeneration, newer.proof)
            // Entry can still restore the older proof before the worker's durable checkpoint.
            store.withCheckpoint(olderEntry, uid, device, now, { true }) {
                confirmation.storeIfCurrent(confirmation.snapshot().generation, it.proof)
            }
            assertEquals(entry.proof, confirmation.snapshot().value)
            Unit to newer
        }

        assertEquals(1, confirmations)
        assertEquals(newer, store.load(uid, device, now))
        assertEquals(newer.proof, confirmation.snapshot().value)
        assertFalse(confirmation.snapshot().value!!.matches(uid, device, subscription.planId,
            subscription.expiresAt, subscription.status, now))
    }

    @Test fun supersededWorkerNeverReappliesItsConfirmationAfterNewerRevocationOrEntry() = runBlocking {
        for (revoked in listOf(false, true)) {
            checkpoint()
            val confirmation = AccessSessionState<MembershipProof>()
            val confirmationGeneration = confirmation.snapshot().generation
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            var confirmations = 0
            val pendingWorker = async {
                runCatching {
                    verifyDownloadMembershipAndCheckpoint(uid, device, store, { now }, { true },
                        onConfirmed = { _, verified ->
                            confirmations++
                            confirmation.storeIfCurrent(confirmationGeneration, verified.proof)
                        }) {
                        confirmation.storeIfCurrent(confirmationGeneration, entry.proof)
                        started.complete(Unit)
                        finish.await()
                        Unit to entry
                    }
                }
            }
            started.await()
            val newer = entry.copy(proof = entry.proof.copy(plan = "plan_standard"),
                subscription = subscription.copy(planId = "plan_standard", planName = "Standard"), serverVersion = now + 1)
            if (revoked) {
                store.blockMigration(uid, device)
                confirmation.clear()
            } else {
                store.save(newer, serverConfirmed = true)
                confirmation.storeIfCurrent(confirmationGeneration, newer.proof)
            }
            finish.complete(Unit)

            assertTrue(pendingWorker.await().exceptionOrNull() is MembershipCheckException)
            assertEquals(0, confirmations)
            assertEquals(if (revoked) null else newer.proof, confirmation.snapshot().value)
            assertEquals(if (revoked) null else newer, store.load(uid, device, now))
            assertEquals(revoked, store.isMigrationBlocked(uid, device))
        }
    }

    @Test fun queuedEntryVerificationCommitsItsNewerTierOrRevocationAfterTheWorkerCheckpoint() = runBlocking {
        for (revoked in listOf(false, true)) {
            checkpoint()
            val initialGeneration = store.checkpointGeneration()
            val workerStarted = CompletableDeferred<Unit>()
            val workerFinished = CompletableDeferred<Unit>()
            val entryQueued = CompletableDeferred<Unit>()
            val worker = async {
                verifyDownloadMembershipAndCheckpoint(uid, device, store, { now }, { true }) {
                    workerStarted.complete(Unit)
                    workerFinished.await()
                    Unit to entry
                }
            }
            workerStarted.await()
            var entryGeneration = initialGeneration
            val newer = entry.copy(proof = entry.proof.copy(plan = "plan_standard"),
                subscription = subscription.copy(planId = "plan_standard", planName = "Standard"), serverVersion = now + 1)
            val queuedEntry = async {
                entryQueued.complete(Unit)
                store.withServerVerification {
                    // Match production: generation belongs to the transaction that runs now,
                    // rather than the earlier time this entry joined the verification queue.
                    entryGeneration = store.checkpointGeneration()
                    if (revoked) store.blockIfCurrent(entryGeneration, uid, device, { true })
                    else store.checkpointEntryServerResult(entryGeneration, newer, now, { true },
                        { throw AssertionError("Unexpected storage failure", it) }) != null
                }
            }
            entryQueued.await()
            assertFalse(queuedEntry.isCompleted)
            workerFinished.complete(Unit)

            worker.await()
            assertTrue(queuedEntry.await())
            assertTrue(entryGeneration > initialGeneration)
            assertEquals(if (revoked) null else newer, store.load(uid, device, now))
            assertEquals(revoked, store.isMigrationBlocked(uid, device))
        }
    }

    @Test fun queuedWorkerCapturesStoreAndGuardGenerationsAfterTheEarlierEntryDenial() = runBlocking {
        checkpoint()
        val confirmation = AccessSessionState<MembershipProof>()
        val initialGuardGeneration = confirmation.snapshot().generation
        confirmation.storeIfCurrent(initialGuardGeneration, entry.proof)
        val entryStarted = CompletableDeferred<Unit>()
        val entryFinished = CompletableDeferred<Unit>()
        val workerQueued = CompletableDeferred<Unit>()
        val earlierEntry = async {
            store.withServerVerification {
                val generation = store.checkpointGeneration()
                entryStarted.complete(Unit)
                entryFinished.await()
                store.blockIfCurrent(generation, uid, device, { true }, onBlocked = { confirmation.clear() })
            }
        }
        entryStarted.await()
        var workerGuardGeneration = initialGuardGeneration
        var verifications = 0
        val laterWorker = async {
            workerQueued.complete(Unit)
            verifyDownloadMembershipAndCheckpoint(uid, device, store, { now }, { true },
                onConfirmed = { _, verified -> confirmation.storeIfCurrent(workerGuardGeneration, verified.proof) }) {
                verifications++
                workerGuardGeneration = confirmation.snapshot().generation
                confirmation.storeIfCurrent(workerGuardGeneration, entry.proof)
                Unit to entry
            }
        }
        workerQueued.await()
        assertEquals(0, verifications)
        entryFinished.complete(Unit)

        assertTrue(earlierEntry.await())
        laterWorker.await()
        assertEquals(1, verifications)
        assertTrue(workerGuardGeneration > initialGuardGeneration)
        assertEquals(entry.proof, confirmation.snapshot().value)
        assertEquals(entry, store.load(uid, device, now))
        assertFalse(store.isMigrationBlocked(uid, device))
    }
}
