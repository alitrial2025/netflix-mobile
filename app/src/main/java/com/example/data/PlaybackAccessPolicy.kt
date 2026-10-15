package com.example.data

import android.util.AtomicFile
import com.example.data.model.UserSubscription
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import org.json.JSONObject

internal object DeviceAccessPolicy {
    fun isSingleDevice(planId: String) = planId == "plan_mobile" || planId == "plan_basic"
    fun permits(planId: String, active: Boolean, tv: Boolean, boundDevice: String?, device: String): Boolean =
        !active || (!(tv && planId == "plan_mobile") &&
            (!isSingleDevice(planId) || boundDevice.isNullOrBlank() || boundDevice == device))
    fun confirmationStatusMatches(verified: String, displayed: String, expiry: Long, now: Long): Boolean =
        verified.equals(displayed, true) ||
            (RenewalPolicy.grantsAccess(verified, expiry, now) && RenewalPolicy.grantsAccess(displayed, expiry, now))
    fun screenCount(planId: String): Int = when (planId) {
        "plan_mobile", "plan_basic" -> 1
        "plan_standard" -> 2
        "plan_premium" -> 4
        else -> 0
    }
}

/** A server-verified membership belongs to one account and installation, with its original expiry. */
internal data class MembershipProof(
    val uid: String,
    val device: String,
    val plan: String,
    val expiry: Long,
    val status: String
) {
    fun matches(uid: String, device: String, plan: String, expiry: Long, status: String, now: Long): Boolean =
        uid.isNotBlank() && device.isNotBlank() && this.uid == uid && this.device == device &&
            this.plan == plan && this.expiry == expiry && DeviceAccessPolicy.screenCount(plan) > 0 &&
            RenewalPolicy.grantsAccess(this.status, this.expiry, now) &&
            DeviceAccessPolicy.confirmationStatusMatches(this.status, status, expiry, now)
}

/** Verified membership/device authorization is independent of an online concurrent-screen slot. */
internal data class OfflineEntryProof(val proof: MembershipProof, val subscription: UserSubscription, val serverVersion: Long = 0L) {
    fun permits(uid: String, device: String, now: Long, expected: UserSubscription? = null): Boolean =
        proof.matches(uid, device, subscription.planId, subscription.expiresAt, subscription.status, now) &&
            (expected == null || proof.matches(uid, device, expected.planId, expected.expiresAt, expected.status, now))
}

/** The proof and its write ordering travel together across suspended entry/cache work. */
internal data class OfflineMembershipCheckpoint(val generation: Long, val entry: OfflineEntryProof?)

/** App-private and excluded from backup; a new installation cannot inherit another device's admission. */
internal class OfflineEntryProofStore(private val file: File) {
    companion object {
        private val stores = java.util.concurrent.ConcurrentHashMap<String, OfflineEntryProofStore>()

        /** Entry and background workers must share write ordering within this process. */
        fun get(context: android.content.Context): OfflineEntryProofStore {
            val file = File(context.noBackupFilesDir, "verified-entry.json")
            return stores.getOrPut(file.absolutePath) { OfflineEntryProofStore(file) }
        }
    }

    private val atomicFile = AtomicFile(file)
    private val blockedFile = AtomicFile(File(file.parentFile, "${file.name}.blocked"))
    private val serverVerificationMutex = Mutex()
    private var generation = 0L

    /** Capture ordering only after acquiring this lock, and release it before claiming a screen. */
    suspend fun <T> withServerVerification(action: suspend () -> T): T = serverVerificationMutex.withLock { action() }

    @Synchronized fun checkpointGeneration(): Long = generation

    @Synchronized fun checkpoint(uid: String, device: String, now: Long, expected: UserSubscription? = null,
        expectedGeneration: Long? = null) = OfflineMembershipCheckpoint(generation,
        load(uid, device, now, expected.takeIf { expectedGeneration == null || generation == expectedGeneration }))

    @Synchronized fun <T> withGeneration(expectedGeneration: Long, action: () -> T): T? =
        if (generation == expectedGeneration) action() else null

    /** Apply admission while a worker cannot replace the proof with a newer result. */
    @Synchronized fun <T> withCheckpoint(checkpoint: OfflineMembershipCheckpoint, uid: String, device: String,
        now: Long, isCurrent: () -> Boolean, action: (OfflineEntryProof) -> T): T? {
        if (generation != checkpoint.generation || !isCurrent()) return null
        val entry = checkpoint.entry?.takeIf { it.permits(uid, device, now) } ?: return null
        return action(entry)
    }

    /** Entry may retain a fresh server proof in memory if only its durable write fails. */
    @Synchronized fun checkpointEntryServerResult(expectedGeneration: Long, entry: OfflineEntryProof, now: Long,
        isCurrent: () -> Boolean, onStorageFailure: (Exception) -> Unit): OfflineMembershipCheckpoint? {
        if (generation != expectedGeneration || !isCurrent()) return null
        try {
            if (!checkpointServerResult(expectedGeneration, entry, now, isCurrent)) return null
        } catch (failure: Exception) {
            onStorageFailure(failure)
        }
        return OfflineMembershipCheckpoint(generation, entry.takeIf { it.permits(it.proof.uid, it.proof.device, now) })
    }

    /** A delayed worker cannot undo a newer checkpoint, account change or server denial. */
    @Synchronized fun checkpointServerResult(expectedGeneration: Long, entry: OfflineEntryProof, now: Long,
        isCurrent: () -> Boolean, onConfirmed: () -> Unit = {}): Boolean {
        if (generation != expectedGeneration || !isCurrent()) return false
        if (entry.permits(entry.proof.uid, entry.proof.device, now)) {
            save(entry, serverConfirmed = true)
            onConfirmed()
        } else blockMigration(entry.proof.uid, entry.proof.device)
        return true
    }

    @Synchronized fun blockIfCurrent(expectedGeneration: Long, uid: String, device: String, isCurrent: () -> Boolean,
        onBlocked: () -> Unit = {}): Boolean {
        if (generation != expectedGeneration || !isCurrent()) return false
        try { blockMigration(uid, device) }
        finally { onBlocked() }
        return true
    }

    @Synchronized fun save(entry: OfflineEntryProof, serverConfirmed: Boolean = false) {
        // Failed storage still supersedes work that started before this fresh verification.
        generation++
        val data = JSONObject().apply {
            put("version", 1)
            put("uid", entry.proof.uid)
            put("device", entry.proof.device)
            put("plan", entry.proof.plan)
            put("expiry", entry.proof.expiry)
            put("status", entry.proof.status)
            put("planName", entry.subscription.planName)
            put("amount", entry.subscription.amount)
            put("currency", entry.subscription.currency)
            put("paymentReference", entry.subscription.paymentReference)
            put("mpesaReceipt", entry.subscription.mpesaReceipt)
            put("subscribedAt", entry.subscription.subscribedAt)
            put("serverVersion", entry.serverVersion)
        }.toString().toByteArray(Charsets.UTF_8)
        file.parentFile?.mkdirs()
        val output = atomicFile.startWrite()
        try {
            output.write(data)
            atomicFile.finishWrite(output)
            if (serverConfirmed) unblock(entry.proof.uid, entry.proof.device)
        } catch (failure: Exception) {
            atomicFile.failWrite(output)
            throw failure
        }
    }

    @Synchronized fun load(uid: String, device: String, now: Long, expected: UserSubscription? = null): OfflineEntryProof? {
        if (isMigrationBlocked(uid, device)) return null
        val entry = runCatching {
            val json = JSONObject(atomicFile.readFully().toString(Charsets.UTF_8))
            require(json.getInt("version") == 1)
            val proof = MembershipProof(json.getString("uid"), json.getString("device"), json.getString("plan"),
                json.getLong("expiry"), json.getString("status"))
            OfflineEntryProof(proof, UserSubscription(status = proof.status, planId = proof.plan,
                planName = json.getString("planName"), amount = json.getInt("amount"), currency = json.getString("currency"),
                paymentReference = json.getString("paymentReference"), mpesaReceipt = json.getString("mpesaReceipt"),
                subscribedAt = json.getLong("subscribedAt"), expiresAt = proof.expiry), json.optLong("serverVersion", 0L))
        }.getOrNull() ?: return null
        return entry.takeIf { it.permits(uid, device, now, expected) }
    }

    @Synchronized fun clear() {
        generation++
        atomicFile.delete()
    }

    /** A deleted proof must not be resurrected from older Firestore cache after a denial/sign-out. */
    @Synchronized fun blockMigration(uid: String, device: String) {
        clear()
        if (uid.isBlank() || device.isBlank()) return
        val blocked = readBlocked()
        blocked.put("$uid:$device", true)
        writeBlocked(blocked)
    }

    @Synchronized fun isMigrationBlocked(uid: String, device: String): Boolean {
        val blocked = readBlocked()
        val key = "$uid:$device"
        return if (blocked.has(key)) blocked.optBoolean(key, true) else blocked.optBoolean("corrupt", false)
    }

    private fun unblock(uid: String, device: String) {
        val blocked = readBlocked()
        if (blocked.optBoolean("corrupt", false)) blocked.put("$uid:$device", false) else blocked.remove("$uid:$device")
        writeBlocked(blocked)
    }

    private fun readBlocked(): JSONObject = runCatching {
        JSONObject(blockedFile.readFully().toString(Charsets.UTF_8))
    }.getOrElse {
        // A missing marker is normal. Corrupt marker data must fail closed for migration.
        if (blockedFile.baseFile.exists()) JSONObject().put("corrupt", true) else JSONObject()
    }

    private fun writeBlocked(blocked: JSONObject) {
        file.parentFile?.mkdirs()
        val output = blockedFile.startWrite()
        try {
            output.write(blocked.toString().toByteArray(Charsets.UTF_8))
            blockedFile.finishWrite(output)
        } catch (failure: Exception) {
            blockedFile.failWrite(output)
            throw failure
        }
    }
}

/** A background transfer must checkpoint its fresh membership before it can create offline media. */
internal suspend fun <T> verifyDownloadMembershipAndCheckpoint(
    uid: String,
    device: String,
    store: OfflineEntryProofStore,
    now: () -> Long,
    isCurrent: () -> Boolean,
    onDenial: () -> Unit = {},
    onConfirmed: (T, OfflineEntryProof) -> Unit = { _, _ -> },
    verify: suspend () -> Pair<T, OfflineEntryProof>
): T = store.withServerVerification {
    val generation = store.checkpointGeneration()
    try {
        if (!isCurrent()) throw MembershipCheckException()
        val (result, entry) = verify()
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val verifiedAt = now()
        if (entry.proof.uid != uid || entry.proof.device != device) throw MembershipCheckException()
        if (!entry.permits(uid, device, verifiedAt)) {
            store.blockIfCurrent(generation, uid, device, isCurrent, onDenial)
            throw MembershipRevokedException()
        }
        if (!store.checkpointServerResult(generation, entry, verifiedAt, isCurrent) { onConfirmed(result, entry) })
            throw MembershipCheckException()
        result
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        if (failure.isAuthoritativeAccessFailure()) store.blockIfCurrent(generation, uid, device, isCurrent, onDenial)
        throw failure
    }
}

internal enum class OfflineCacheDecision { VERIFIED, INCOMPLETE, DENIED }

/** Cached pending writes cannot establish paid membership or a single-device binding. */
internal object OfflineMembershipCachePolicy {
    fun bindingDecision(plan: String, exists: Boolean, pendingWrites: Boolean, boundDevice: String?, device: String): OfflineCacheDecision = when {
        !DeviceAccessPolicy.isSingleDevice(plan) -> OfflineCacheDecision.VERIFIED
        !exists || pendingWrites -> OfflineCacheDecision.INCOMPLETE
        boundDevice.isNullOrBlank() || boundDevice != device -> OfflineCacheDecision.DENIED
        else -> OfflineCacheDecision.VERIFIED
    }

    fun cacheMayOverrideProof(cacheVersion: Long, proofVersion: Long): Boolean =
        cacheVersion <= 0L || proofVersion <= 0L || cacheVersion >= proofVersion
}

internal data class CachedOfflineSubscription(val uid: String, val exists: Boolean, val pendingWrites: Boolean,
    val subscription: UserSubscription, val serverVersion: Long = 0L)
internal data class CachedOfflineBinding(val uid: String, val exists: Boolean, val pendingWrites: Boolean, val device: String?)

/** Implementations read local committed records only; recovery never requests a server transaction. */
internal interface OfflineMembershipCacheReader {
    suspend fun subscription(uid: String): CachedOfflineSubscription?
    suspend fun binding(uid: String): CachedOfflineBinding?
}

internal interface OfflineEntryEpoch {
    fun ensureCurrent()
    fun <T> withCurrent(action: () -> T): T
}

internal suspend fun recoverOfflineMembershipProof(
    uid: String,
    device: String,
    now: Long,
    expected: UserSubscription?,
    store: OfflineEntryProofStore,
    reader: OfflineMembershipCacheReader,
    epoch: OfflineEntryEpoch,
    onInvalidation: () -> Unit = {}
): OfflineEntryProof? = recoverOfflineMembershipCheckpoint(uid, device, now, expected, store, reader, epoch, onInvalidation).entry

internal suspend fun recoverOfflineMembershipCheckpoint(
    uid: String,
    device: String,
    now: Long,
    expected: UserSubscription?,
    store: OfflineEntryProofStore,
    reader: OfflineMembershipCacheReader,
    epoch: OfflineEntryEpoch,
    onInvalidation: () -> Unit = {},
    expectedCheckpointGeneration: Long? = null
): OfflineMembershipCheckpoint {
    val initial = epoch.withCurrent { store.checkpoint(uid, device, now, expected, expectedCheckpointGeneration) }
    // An entry's expected tier cannot revoke a newer server-verified worker result.
    if (expectedCheckpointGeneration != null && initial.generation != expectedCheckpointGeneration) return initial
    val stored = initial.entry
    // Keep both the decision and any resulting write atomic with worker checkpoints.
    fun finish(action: () -> OfflineEntryProof?): OfflineMembershipCheckpoint = epoch.withCurrent {
        store.withGeneration(initial.generation) {
            val entry = action()
            OfflineMembershipCheckpoint(store.checkpointGeneration(), entry)
        } ?: store.checkpoint(uid, device, now, expected, expectedCheckpointGeneration)
    }
    if (store.isMigrationBlocked(uid, device)) return finish { stored }
    val cached = reader.subscription(uid)
    epoch.ensureCurrent()
    if (cached == null || cached.uid != uid || cached.pendingWrites) return finish { stored }
    if (stored != null && !OfflineMembershipCachePolicy.cacheMayOverrideProof(cached.serverVersion, stored.serverVersion)) return finish { stored }
    val subscription = if (cached.exists) cached.subscription else UserSubscription()
    // An unversioned active cache cannot roll back a known newer server-confirmed tier.
    if (stored != null && cached.serverVersion <= 0L && stored.serverVersion > 0L &&
        DeviceAccessPolicy.screenCount(subscription.planId) > 0 && RenewalPolicy.grantsAccess(subscription.status, subscription.expiresAt, now)) return finish { stored }
    val candidate = OfflineEntryProof(MembershipProof(uid, device, subscription.planId, subscription.expiresAt, subscription.status),
        subscription, cached.serverVersion)
    if (!candidate.permits(uid, device, now, expected)) {
        return finish {
            store.blockMigration(uid, device)
            onInvalidation()
            null
        }
    }
    val binding = if (DeviceAccessPolicy.isSingleDevice(subscription.planId)) reader.binding(uid) else null
    epoch.ensureCurrent()
    val decision = OfflineMembershipCachePolicy.bindingDecision(subscription.planId, binding?.exists == true && binding.uid == uid,
        binding?.pendingWrites == true, binding?.device, device)
    return finish {
        when (decision) {
            OfflineCacheDecision.DENIED -> {
                store.blockMigration(uid, device)
                onInvalidation()
                null
            }
            OfflineCacheDecision.INCOMPLETE -> {
                if (stored?.permits(uid, device, now, subscription) == true) stored
                else {
                    store.clear()
                    onInvalidation()
                    null
                }
            }
            OfflineCacheDecision.VERIFIED -> {
                // A failed disk checkpoint still permits this process's committed cached proof.
                runCatching { store.save(candidate) }
                candidate
            }
        }
    }
}

/** App entry owns the screen admission; temporary refresh failures do not revoke that session. */
internal class EntrySessionAdmission {
    @Volatile private var owner: Pair<String, String>? = null

    fun admit(uid: String, device: String) {
        require(uid.isNotBlank() && device.isNotBlank())
        owner = uid to device
    }

    fun isAdmitted(uid: String, device: String): Boolean {
        val current = owner ?: return false
        return current.first == uid && current.second == device
    }

    fun clear() { owner = null }

    fun onRefreshFailure(authoritative: Boolean) {
        if (authoritative) clear()
    }
}

/** Invalidating an access session also invalidates network work that started in that session. */
internal class AccessSessionState<T> {
    data class Snapshot<T>(val generation: Long, val value: T?)
    private var generation = 0L
    private var value: T? = null

    @Synchronized fun snapshot(): Snapshot<T> = Snapshot(generation, value)

    @Synchronized fun storeIfCurrent(expectedGeneration: Long, updated: T, isCurrent: () -> Boolean = { true }): Boolean {
        if (generation != expectedGeneration || !isCurrent()) return false
        value = updated
        return true
    }

    @Synchronized fun clear(): T? {
        generation++
        return value.also { value = null }
    }

    @Synchronized fun clearIfCurrent(expectedGeneration: Long): Boolean {
        if (generation != expectedGeneration) return false
        clear()
        return true
    }

    /** Cancellation rejects pending results while keeping the previous token available for release. */
    @Synchronized fun cancelPending(expectedGeneration: Long) {
        if (generation == expectedGeneration) generation++
    }
}
