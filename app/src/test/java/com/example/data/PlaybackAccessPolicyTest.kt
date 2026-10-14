package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackAccessPolicyTest {
    private val expiry = 1_800_000_000_000L
    private val proof = MembershipProof("account-a", "phone-a", "plan_standard", expiry, "ACTIVE")

    @Test fun proofSurvivesTemporaryConnectionLossWithoutExtendingItsOriginalExpiry() {
        val admission = EntrySessionAdmission().apply { admit(proof.uid, proof.device) }
        admission.onRefreshFailure(authoritative = false)
        assertTrue(admission.isAdmitted(proof.uid, proof.device))
        assertTrue(proof.matches("account-a", "phone-a", "plan_standard", expiry, "ACTIVE", expiry - 1L))
        assertTrue(proof.matches("account-a", "phone-a", "plan_standard", expiry, "GRACE_PERIOD", expiry))
        assertFalse(proof.matches("account-a", "phone-a", "plan_standard", expiry, "ACTIVE", expiry + RenewalPolicy.GRACE_MS))
    }

    @Test fun membershipProofCannotCrossAccountOrInstallationBoundaries() {
        for ((uid, device) in listOf("account-b" to "phone-a", "account-a" to "phone-b", "" to "phone-a", "account-a" to " ")) {
            assertFalse(proof.matches(uid, device, proof.plan, expiry, proof.status, expiry - 1L))
        }
        assertFalse(proof.copy(uid = "").matches("", "phone-a", proof.plan, expiry, proof.status, expiry - 1L))
        assertFalse(proof.copy(device = " ").matches("account-a", " ", proof.plan, expiry, proof.status, expiry - 1L))
    }

    @Test fun planAndExpiryChangesRequireANewServerProof() {
        assertFalse(proof.matches(proof.uid, proof.device, "plan_premium", expiry, proof.status, expiry - 1L))
        assertFalse(proof.matches(proof.uid, proof.device, proof.plan, expiry + RenewalPolicy.DAY_MS, proof.status, expiry - 1L))
        assertFalse(proof.matches(proof.uid, proof.device, proof.plan, expiry - RenewalPolicy.DAY_MS, proof.status, expiry - 1L))
    }

    @Test fun authoritativeSuspensionAndInvalidMembershipsDoNotMatchEvenIdenticalDisplayedStatus() {
        for (status in listOf("SUSPENDED", "EXPIRED", "NONE", "APPROVED")) {
            assertFalse(proof.matches(proof.uid, proof.device, proof.plan, expiry, status, expiry - 1L))
            val revoked = proof.copy(status = status)
            assertFalse(revoked.matches(revoked.uid, revoked.device, revoked.plan, expiry, status, expiry - 1L))
        }
        for (plan in listOf("plan_guest", "plan_admin", "")) {
            val unknown = proof.copy(plan = plan)
            assertFalse(unknown.matches(unknown.uid, unknown.device, plan, expiry, unknown.status, expiry - 1L))
        }
        assertFalse(proof.copy(expiry = 0L).matches(proof.uid, proof.device, proof.plan, 0L, proof.status, 0L))
    }

    @Test fun eachKnownPaidPlanRemainsValidAcrossTheFixedRenewalGraceBoundary() {
        for (plan in listOf("plan_mobile", "plan_basic", "plan_standard", "plan_premium")) {
            val paid = proof.copy(plan = plan)
            assertTrue(paid.matches(paid.uid, paid.device, plan, expiry, "active", expiry - 1L))
            assertTrue(paid.matches(paid.uid, paid.device, plan, expiry, "GRACE_PERIOD", expiry + RenewalPolicy.GRACE_MS - 1L))
            assertFalse(paid.matches(paid.uid, paid.device, plan, expiry, "GRACE_PERIOD", expiry + RenewalPolicy.GRACE_MS))
            val grace = paid.copy(status = "GRACE_PERIOD")
            assertTrue(grace.matches(grace.uid, grace.device, plan, expiry, "ACTIVE", expiry + RenewalPolicy.DAY_MS))
            assertFalse(grace.matches(grace.uid, grace.device, plan, expiry, "GRACE_PERIOD", expiry + RenewalPolicy.GRACE_MS))
        }
    }

    @Test fun transientFailuresPreserveAdmissionButCannotAdmitANewSession() {
        val admission = EntrySessionAdmission()
        admission.onRefreshFailure(authoritative = false)
        assertFalse(admission.isAdmitted("account-a", "phone-a"))
        admission.admit("account-a", "phone-a")
        repeat(10) { admission.onRefreshFailure(authoritative = false) }
        assertTrue(admission.isAdmitted("account-a", "phone-a"))
    }

    @Test fun admissionCannotCrossAccountOrDeviceBoundaries() {
        val admission = EntrySessionAdmission().apply { admit("account-a", "phone-a") }
        for ((uid, device) in listOf("account-b" to "phone-a", "account-a" to "phone-b", "" to "phone-a", "account-a" to "")) {
            assertFalse(admission.isAdmitted(uid, device))
        }
        admission.admit("account-b", "phone-b")
        assertFalse(admission.isAdmitted("account-a", "phone-a"))
        assertTrue(admission.isAdmitted("account-b", "phone-b"))
    }

    @Test fun authoritativeFailureAndSignOutRequireFreshAdmission() {
        val admission = EntrySessionAdmission().apply { admit("account-a", "phone-a") }
        admission.onRefreshFailure(authoritative = true)
        assertFalse(admission.isAdmitted("account-a", "phone-a"))
        admission.onRefreshFailure(authoritative = false)
        assertFalse(admission.isAdmitted("account-a", "phone-a"))
        admission.admit("account-a", "phone-a")
        assertTrue(admission.isAdmitted("account-a", "phone-a"))
        admission.clear()
        assertFalse(admission.isAdmitted("account-a", "phone-a"))
    }

    @Test fun blankAccountOrInstallationCannotBeAdmitted() {
        val admission = EntrySessionAdmission()
        for ((uid, device) in listOf("" to "phone-a", "account-a" to "", " " to "phone-a", "account-a" to " ")) {
            assertThrows(IllegalArgumentException::class.java) { admission.admit(uid, device) }
            assertFalse(admission.isAdmitted(uid, device))
        }
    }

    @Test fun revokedSessionRejectsAnInFlightSuccessEvenAfterTheSameAccountSignsInAgain() {
        val state = AccessSessionState<MembershipProof>()
        val oldRequest = state.snapshot().generation
        state.clear() // Account A signs out.
        state.clear() // Account B signs out before A signs back in.
        val newRequest = state.snapshot().generation
        val current = proof.copy(expiry = expiry + RenewalPolicy.DAY_MS)
        assertTrue(state.storeIfCurrent(newRequest, current))
        assertFalse(state.storeIfCurrent(oldRequest, proof))
        assertEquals(current, state.snapshot().value)
        assertFalse(state.clearIfCurrent(oldRequest))
        assertEquals(current, state.snapshot().value)
    }

    @Test fun cancellationKeepsThePreviousLeaseTokenForReleaseAndRejectsLateCompletion() {
        val state = AccessSessionState<String>()
        val request = state.snapshot().generation
        assertTrue(state.storeIfCurrent(request, "previous-token"))
        state.cancelPending(request)
        assertFalse(state.storeIfCurrent(request, "cancelled-request-token"))
        assertEquals("previous-token", state.clear())
        assertNull(state.snapshot().value)
    }

    @Test fun releaseInvalidatesPendingAcquisitionEvenWhenThereIsNoCurrentLease() {
        val state = AccessSessionState<String>()
        val request = state.snapshot().generation
        assertNull(state.clear())
        assertFalse(state.storeIfCurrent(request, "late-token"))
        assertNull(state.snapshot().value)
    }

    @Test fun invalidAccountOrCancelledCoroutineCannotPublishIntoTheCurrentGeneration() {
        val state = AccessSessionState<String>()
        val request = state.snapshot().generation
        assertFalse(state.storeIfCurrent(request, "stale-result") { false })
        assertNull(state.snapshot().value)
        assertTrue(state.storeIfCurrent(request, "verified-result") { true })
        assertTrue(state.clearIfCurrent(request))
        assertNull(state.snapshot().value)
    }
}
