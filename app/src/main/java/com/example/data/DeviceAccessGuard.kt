package com.example.data

import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.google.firebase.firestore.FirebaseFirestoreException
import java.security.MessageDigest
import java.util.UUID

open class DeviceAccessException(message: String) : java.io.IOException(message)
class ScreenLimitException(message: String) : DeviceAccessException(message)
class MembershipCheckException(cause: Throwable? = null) : java.io.IOException("We couldn't verify your membership and device. Reconnect and try again.", cause)
class MembershipRevokedException : java.io.IOException("Your membership is no longer active. Choose a plan to continue.")

internal fun Throwable.isAuthoritativeAccessFailure(): Boolean = generateSequence(this) { it.cause }.any {
    it is DeviceAccessException || it is MembershipRevokedException ||
        (it is FirebaseFirestoreException && it.code in setOf(
            FirebaseFirestoreException.Code.PERMISSION_DENIED, FirebaseFirestoreException.Code.UNAUTHENTICATED))
}
internal fun Throwable.accessFailureMessage(): String = generateSequence(this) { it.cause }
    .firstOrNull { it is DeviceAccessException || it is MembershipRevokedException }?.message
    ?: "We couldn't verify your membership and device. Reconnect and try again."

/** Login-device binding is separate from concurrent playback slots. Sign-out does not release a binding. */
object DeviceAccessGuard {
    private data class Confirmation(val proof: MembershipProof, val snapshot: com.google.firebase.firestore.DocumentSnapshot)
    private val confirmationState = AccessSessionState<Confirmation>()
    private val confirmationMutex = Mutex()

    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences("device_access", Context.MODE_PRIVATE)
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeUnless { it.isBlank() || it == "9774d56d682e549c" }
        val seed = androidId ?: synchronized(this) {
            prefs.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString("installation_id", it).commit()
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun clear() { confirmationState.clear() }
    fun isConfirmed(uid: String, plan: String, expiry: Long, status: String): Boolean {
        val proof = confirmationState.snapshot().value?.proof ?: return false
        return proof.matches(uid, proof.device, plan, expiry, status, SubscriptionTime.now())
    }

    suspend fun confirm(context: Context, tv: Boolean, forceRefresh: Boolean = false): com.google.firebase.firestore.DocumentSnapshot {
        val requestGeneration = confirmationState.snapshot().generation
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser?.takeUnless { it.isAnonymous } ?: throw MembershipCheckException()
        val device = deviceId(context)
        val coroutine = currentCoroutineContext()
        return confirmationMutex.withLock {
            coroutine.ensureActive()
            val current = confirmationState.snapshot()
            if (current.generation != requestGeneration || auth.currentUser?.uid != user.uid) throw MembershipCheckException()
            val previous = current.value
            val proof = previous?.proof
            if (!forceRefresh && proof?.matches(user.uid, device, proof.plan, proof.expiry, proof.status, SubscriptionTime.now()) == true) {
                return@withLock previous.snapshot
            }
            val db = FirebaseFirestore.getInstance()
            val subRef = db.collection("users").document(user.uid).collection("subscription").document("current")
            val bindingRef = db.collection("users").document(user.uid).collection("device_binding").document("current")
            try {
            // Server transaction serializes two first-device logins, including Mobile -> Basic changes.
            val snapshot = kotlinx.coroutines.withTimeoutOrNull(15_000L) { db.runTransaction { tx ->
                val sub = tx.get(subRef)
                val plan = sub.getString("planId").orEmpty()
                val active = DeviceAccessPolicy.screenCount(plan) > 0 && RenewalPolicy.grantsAccess(
                    sub.getString("status").orEmpty(), sub.getLong("expiresAt") ?: 0L, SubscriptionTime.now())
                val binding = if (active && DeviceAccessPolicy.isSingleDevice(plan)) tx.get(bindingRef) else null
                if (!DeviceAccessPolicy.permits(plan, active, tv, binding?.getString("deviceId"), device)) {
                    throw DeviceAccessException(if (tv && plan == "plan_mobile")
                        "The Mobile plan works on one phone or tablet. Choose Basic, Standard or Premium for TV."
                    else "This plan is tied to another device. Use that device, upgrade your plan, or contact support to change devices.")
                }
                if (active && DeviceAccessPolicy.isSingleDevice(plan) && binding?.exists() != true) {
                    tx.set(bindingRef, mapOf("deviceId" to device, "deviceType" to if (tv) "tv" else "mobile",
                        "boundAt" to FieldValue.serverTimestamp()))
                }
                sub
            }.await() } ?: throw MembershipCheckException()
            coroutine.ensureActive()
            val updated = Confirmation(MembershipProof(user.uid, device, snapshot.getString("planId").orEmpty(), snapshot.getLong("expiresAt") ?: 0L, snapshot.getString("status").orEmpty()), snapshot)
            if (!confirmationState.storeIfCurrent(requestGeneration, updated) { coroutine.isActive && auth.currentUser?.uid == user.uid }) {
                coroutine.ensureActive()
                throw MembershipCheckException()
            }
            snapshot
            } catch (cancelled: CancellationException) {
                confirmationState.cancelPending(requestGeneration)
                throw cancelled
            } catch (error: Exception) {
            // A transport failure says nothing about a previously verified membership.
            if (error.isAuthoritativeAccessFailure()) confirmationState.clearIfCurrent(requestGeneration)
            val restriction = generateSequence<Throwable>(error) { it.cause }.filterIsInstance<DeviceAccessException>().firstOrNull()
            if (restriction != null) throw restriction
            throw MembershipCheckException(error)
            }
        }
    }
}

/** Each account has at most four fixed slots; claims race in one Firestore transaction. */
object ScreenLease {
    private data class Lease(val uid: String, val slot: Int, val device: String, val token: String)
    private val leaseState = AccessSessionState<Lease>()
    private val acquisitionMutex = Mutex()
    internal fun availableSlot(devices: List<String?>, heartbeats: List<Long>, released: List<Boolean>,
                               device: String, now: Long): Int? =
        devices.indexOf(device).takeIf { it >= 0 } ?: devices.indices.firstOrNull {
            devices[it] == null || released[it] || now - heartbeats[it] >= 45_000L
        }

    suspend fun acquire(context: Context, tv: Boolean) {
        val requestGeneration = leaseState.snapshot().generation
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser?.takeUnless { it.isAnonymous } ?: throw MembershipCheckException()
        val device = DeviceAccessGuard.deviceId(context)
        val coroutine = currentCoroutineContext()
        acquisitionMutex.withLock {
        coroutine.ensureActive()
        if (leaseState.snapshot().generation != requestGeneration || auth.currentUser?.uid != user.uid) throw MembershipCheckException()
        val db = FirebaseFirestore.getInstance()
        val account = db.collection("users").document(user.uid)
        val token = UUID.randomUUID().toString()
        try {
        val slot = kotlinx.coroutines.withTimeoutOrNull(15_000L) { db.runTransaction { tx ->
            val sub = tx.get(account.collection("subscription").document("current"))
            val plan = sub.getString("planId").orEmpty()
            val max = DeviceAccessPolicy.screenCount(plan)
            if (max == 0 || !RenewalPolicy.grantsAccess(sub.getString("status").orEmpty(),
                    sub.getLong("expiresAt") ?: 0L, SubscriptionTime.now())) throw MembershipRevokedException()
            val binding = if (DeviceAccessPolicy.isSingleDevice(plan))
                tx.get(account.collection("device_binding").document("current")).getString("deviceId") else null
            if (!DeviceAccessPolicy.permits(plan, true, tv, binding, device) ||
                (DeviceAccessPolicy.isSingleDevice(plan) && binding == null)) throw DeviceAccessException("This device isn't authorized for your plan. Sign in again.")
            val slots = (0 until max).map { tx.get(account.collection("stream_slots").document(it.toString())) }
            val available = availableSlot(slots.map { it.getString("deviceId") },
                slots.map { it.getTimestamp("lastHeartbeat")?.toDate()?.time ?: 0L },
                slots.map { it.getBoolean("released") == true }, device, SubscriptionTime.now())
                ?: throw ScreenLimitException("All $max screens on your plan are in use. Stop playback on another device or upgrade your plan.")
            tx.set(account.collection("stream_slots").document(available.toString()), mapOf(
                "deviceId" to device, "deviceType" to if (tv) "tv" else "mobile",
                "lastHeartbeat" to FieldValue.serverTimestamp(), "released" to false, "leaseToken" to token))
            available
        }.await() } ?: throw MembershipCheckException()
        coroutine.ensureActive()
        if (!leaseState.storeIfCurrent(requestGeneration, Lease(user.uid, slot, device, token)) { coroutine.isActive && auth.currentUser?.uid == user.uid }) {
            coroutine.ensureActive()
            throw MembershipCheckException()
        }
        } catch (cancelled: CancellationException) {
            leaseState.cancelPending(requestGeneration)
            throw cancelled
        }
        }
    }

    fun releaseIn(scope: CoroutineScope) {
        val captured = leaseState.clear() ?: return
        scope.launch { releaseCaptured(captured) }
    }
    suspend fun release() {
        val captured = leaseState.clear() ?: return
        releaseCaptured(captured)
    }
    private suspend fun releaseCaptured(captured: Lease) {
        if (FirebaseAuth.getInstance().currentUser?.uid != captured.uid) return
        val ref = FirebaseFirestore.getInstance().collection("users").document(captured.uid)
            .collection("stream_slots").document(captured.slot.toString())
        try {
            FirebaseFirestore.getInstance().runTransaction { tx ->
                val slot = tx.get(ref)
                if (slot.getString("deviceId") == captured.device && slot.getString("leaseToken") == captured.token) tx.update(ref,
                    mapOf("released" to true, "lastHeartbeat" to FieldValue.serverTimestamp()))
            }.await()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* A disconnected device's slot expires after 45 seconds. */ }
    }
}
