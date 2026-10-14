package com.example.data

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
