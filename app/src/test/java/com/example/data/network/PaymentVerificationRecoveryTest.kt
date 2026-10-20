package com.example.data.network

import com.example.data.model.UserSubscription
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentVerificationRecoveryTest {
    private val success = VerificationResult.Success(UserSubscription(planId = "plan_mobile"), 150, "Verified")

    @Test fun onlyTheExplicitVerificationThrottleCanScheduleARetry() {
        assertEquals(3_000L, paymentVerificationCooldown(429, "VERIFICATION_BUSY", 2_100, "3"))
        assertEquals(3_100L, paymentVerificationCooldown(429, "VERIFICATION_BUSY", 3_100L, "3"))
        assertEquals(5_000L, paymentVerificationCooldown(429, "VERIFICATION_BUSY", null, "5"))
        assertEquals(60_000L, paymentVerificationCooldown(429, "VERIFICATION_BUSY", 60_000L, "60"))
        assertNull(paymentVerificationCooldown(503, "VERIFICATION_BUSY", 3_000, "3"))
        assertNull(paymentVerificationCooldown(429, "PAYMENT_PENDING", 3_000, "3"))
        assertNull(paymentVerificationCooldown(422, "RECEIPT_USED", 3_000, "3"))
    }

    @Test fun malformedOrUnboundedServerDelaysNeverCauseAnEarlyRetry() {
        for (delay in listOf(-1, 0, 60_001, 3.5, Double.NaN, Double.POSITIVE_INFINITY, "3000")) {
            assertNull("Invalid body delay $delay", paymentVerificationCooldown(429, "VERIFICATION_BUSY", delay, "3"))
        }
        for (header in listOf("-1", "0", "61", "3.5", "not-a-time")) {
            assertNull(paymentVerificationCooldown(429, "VERIFICATION_BUSY", 3_000, header))
        }
        assertNull(paymentVerificationCooldown(429, "VERIFICATION_BUSY", null, null))
    }

    @Test fun threeSecondCooldownCountsDownAndAutomaticallyVerifiesTheSamePaymentOnce() = runTest {
        val countdown = mutableListOf<Int>()
        val requestTimes = mutableListOf<Long>()
        val receipt = "TEST123456"
        val reference = "same-account-plan-checkout"
        val requests = mutableListOf<Pair<String, String>>()
        val result = async {
            recoverPaymentVerification(onCooldown = { countdown += it }, elapsedMs = { testScheduler.currentTime }) {
                requests += receipt to reference
                requestTimes += testScheduler.currentTime
                if (requests.size == 1) VerificationResult.Error("Please wait 3 seconds", 3_000L) else success
            }
        }
        runCurrent()
        assertEquals(listOf(3), countdown)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf(3, 2), countdown)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf(3, 2, 1), countdown)
        assertEquals(1, requests.size)
        advanceTimeBy(1_000); runCurrent()
        assertSame(success, result.await())
        assertEquals(listOf(0L, 3_000L), requestTimes)
        assertEquals(listOf(receipt to reference, receipt to reference), requests)
        assertEquals(0, countdown.last())
    }

    @Test fun fullMinuteBudgetCooldownDoesNotSendRequestsBeforeItsDeadline() = runTest {
        var calls = 0
        val result = async {
            recoverPaymentVerification(elapsedMs = { testScheduler.currentTime }) {
                calls++
                if (calls == 1) VerificationResult.Error("wait", 60_000L) else success
            }
        }
        runCurrent()
        advanceTimeBy(59_999); runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(1); runCurrent()
        assertSame(success, result.await())
        assertEquals(2, calls)
    }

    @Test fun closingTheScreenCancelsTheWaitAndNeverRetriesTheReceipt() = runTest {
        var calls = 0
        var seconds = -1
        val job = launch {
            recoverPaymentVerification(onCooldown = { seconds = it }, elapsedMs = { testScheduler.currentTime }) {
                calls++
                VerificationResult.Error("wait", 3_000L)
            }
        }
        runCurrent()
        assertEquals(3, seconds)
        advanceTimeBy(1_000); runCurrent()
        job.cancelAndJoin()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, calls)
        assertEquals(0, seconds)
    }

    @Test fun failedReceiptOrChangedAccountStopsRecoveryWithoutGrantingAccess() = runTest {
        for (message in listOf("Wrong account", "Receipt already used", "Payment pending", "Merchant unavailable")) {
            val error = VerificationResult.Error(message)
            var calls = 0
            val result = recoverPaymentVerification(elapsedMs = { testScheduler.currentTime }) {
                calls++
                if (calls == 1) VerificationResult.Error("wait", 1_000L) else error
            }
            assertSame(error, result)
            assertEquals(2, calls)
        }
    }

    @Test fun repeatedBusyResponsesAreBoundedAndNeverLeaveAStaleSecondsError() = runTest {
        var calls = 0
        val result = recoverPaymentVerification(elapsedMs = { testScheduler.currentTime }) {
            calls++
            VerificationResult.Error("Please wait 3 seconds", 3_000L)
        }
        assertEquals(3, calls)
        assertEquals(6_000L, testScheduler.currentTime)
        assertTrue(result is VerificationResult.Error)
        assertNull((result as VerificationResult.Error).retryAfterMs)
        assertFalse(result.message.contains("3 seconds"))
        assertTrue(result.message.contains("do not pay again"))
    }

    @Test fun cumulativeCooldownBudgetCannotKeepTheScreenWaitingForMinutes() = runTest {
        var calls = 0
        val result = recoverPaymentVerification(elapsedMs = { testScheduler.currentTime }) {
            calls++
            VerificationResult.Error("wait", 60_000L)
        }
        assertEquals(2, calls)
        assertEquals(60_000L, testScheduler.currentTime)
        assertTrue(result is VerificationResult.Error)
    }

    @Test fun immediateSuccessAndNonRecoverableFailuresMakeOnlyOneRequest() = runTest {
        for (expected in listOf(success, VerificationResult.Error("Receipt rejected"))) {
            var calls = 0
            val result = recoverPaymentVerification(elapsedMs = { testScheduler.currentTime }) {
                calls++
                expected
            }
            assertSame(expected, result)
            assertEquals(1, calls)
        }
        assertEquals(0L, testScheduler.currentTime)
    }
}
