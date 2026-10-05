package com.example.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OfflineAccessProofTest {
    @Test fun confirmedLocalAccessSurvivesRestartAndRejectsOtherAccountsDevicesAndPlans() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val expiry = SubscriptionTime.now() + RenewalPolicy.DAY_MS
        OfflineAccessProof(context).save("alice", "phone-a", "plan_basic", expiry, "ACTIVE")
        val restored = OfflineAccessProof(context)
        assertTrue(restored.matches("alice", "phone-a", "plan_basic", expiry, "ACTIVE"))
        assertFalse(restored.matches("bob", "phone-a", "plan_basic", expiry, "ACTIVE"))
        assertFalse(restored.matches("alice", "phone-b", "plan_basic", expiry, "ACTIVE"))
        assertFalse(restored.matches("alice", "phone-a", "plan_premium", expiry, "ACTIVE"))
        assertFalse(restored.matches("alice", "phone-a", "plan_basic", expiry + 1, "ACTIVE"))
        assertFalse(restored.matches("alice", "phone-a", "plan_basic", expiry, "SUSPENDED"))
        restored.clear()
        assertFalse(restored.matches("alice", "phone-a", "plan_basic", expiry, "ACTIVE"))
    }
    @Test fun expiredOrRevokedServerEntitlementClearsPriorOfflineAccess() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val expiry = SubscriptionTime.now() + RenewalPolicy.DAY_MS
        val proof = OfflineAccessProof(context)
        proof.save("alice", "phone-a", "plan_basic", expiry, "ACTIVE")
        proof.save("alice", "phone-a", "plan_basic", expiry, "SUSPENDED")
        assertFalse(proof.matches("alice", "phone-a", "plan_basic", expiry, "ACTIVE"))
        val expired = SubscriptionTime.now() - RenewalPolicy.GRACE_MS - 1000
        proof.save("alice", "phone-a", "plan_basic", expired, "ACTIVE")
        assertFalse(proof.matches("alice", "phone-a", "plan_basic", expired, "ACTIVE"))
    }
}
