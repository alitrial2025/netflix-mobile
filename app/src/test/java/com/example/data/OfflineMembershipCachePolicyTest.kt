package com.example.data

import org.junit.Assert.*
import org.junit.Test

class OfflineMembershipCachePolicyTest {
    @Test fun legacySingleDeviceMembersNeedTheirCommittedDeviceBinding() {
        for (plan in listOf("plan_mobile", "plan_basic")) {
            assertEquals(OfflineCacheDecision.VERIFIED,
                OfflineMembershipCachePolicy.bindingDecision(plan, true, false, "phone-a", "phone-a"))
            assertEquals(OfflineCacheDecision.INCOMPLETE,
                OfflineMembershipCachePolicy.bindingDecision(plan, false, false, null, "phone-a"))
            assertEquals(OfflineCacheDecision.INCOMPLETE,
                OfflineMembershipCachePolicy.bindingDecision(plan, true, true, "phone-a", "phone-a"))
            assertEquals(OfflineCacheDecision.DENIED,
                OfflineMembershipCachePolicy.bindingDecision(plan, true, false, "phone-b", "phone-a"))
            assertEquals(OfflineCacheDecision.DENIED,
                OfflineMembershipCachePolicy.bindingDecision(plan, true, false, null, "phone-a"))
        }
    }

    @Test fun multiScreenPlansDoNotRequireAnUnrelatedSingleDeviceBindingOrAnOnlineSlot() {
        for (plan in listOf("plan_standard", "plan_premium")) {
            assertEquals(OfflineCacheDecision.VERIFIED,
                OfflineMembershipCachePolicy.bindingDecision(plan, false, false, null, "phone-a"))
            assertEquals(OfflineCacheDecision.VERIFIED,
                OfflineMembershipCachePolicy.bindingDecision(plan, true, false, "old-device", "phone-a"))
        }
    }

    @Test fun olderKnownCacheVersionsCannotReplaceNewerVerifiedMembership() {
        assertFalse(OfflineMembershipCachePolicy.cacheMayOverrideProof(100L, 101L))
        assertTrue(OfflineMembershipCachePolicy.cacheMayOverrideProof(101L, 101L))
        assertTrue(OfflineMembershipCachePolicy.cacheMayOverrideProof(102L, 101L))
        assertTrue(OfflineMembershipCachePolicy.cacheMayOverrideProof(0L, 101L))
        assertTrue(OfflineMembershipCachePolicy.cacheMayOverrideProof(101L, 0L))
    }
}
