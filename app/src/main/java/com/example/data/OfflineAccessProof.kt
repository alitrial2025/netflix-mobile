package com.example.data

import android.content.Context

/** A previously server-confirmed entitlement for local playback on this installation. */
internal class OfflineAccessProof(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("offline_device_access", Context.MODE_PRIVATE)

    fun save(uid: String, device: String, plan: String, expiry: Long, status: String) {
        if (uid.isBlank() || DeviceAccessPolicy.screenCount(plan) == 0 ||
            !RenewalPolicy.grantsAccess(status, expiry, SubscriptionTime.now())) {
            clear()
            return
        }
        prefs.edit().putString("uid", uid).putString("device", device).putString("plan", plan)
            .putLong("expiry", expiry).putString("status", status).commit()
    }

    fun matches(uid: String, device: String, plan: String, expiry: Long, status: String): Boolean =
        uid.isNotBlank() && prefs.getString("uid", null) == uid && prefs.getString("device", null) == device &&
            prefs.getString("plan", null) == plan && prefs.getLong("expiry", 0L) == expiry &&
            RenewalPolicy.grantsAccess(status, expiry, SubscriptionTime.now()) &&
            DeviceAccessPolicy.confirmationStatusMatches(prefs.getString("status", "").orEmpty(), status, expiry, SubscriptionTime.now())

    fun clear() { prefs.edit().clear().commit() }
}
