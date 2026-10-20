package com.example.data.network

import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Only the authenticated service's explicit verification throttle is recoverable here. */
internal fun paymentVerificationCooldown(status: Int, code: String?, retryAfterMs: Any?, retryAfterHeader: String?): Long? {
    if (status != 429 || code != "VERIFICATION_BUSY") return null
    val bodyDelay = (retryAfterMs as? Number)?.toDouble()?.takeIf {
        it.isFinite() && it % 1.0 == 0.0 && it in 1.0..60_000.0
    }?.toLong()
    val headerDelay = retryAfterHeader?.trim()?.toLongOrNull()?.takeIf { it in 1L..60L }?.times(1_000L)
    if (retryAfterMs != null && bodyDelay == null) return null
    if (!retryAfterHeader.isNullOrBlank() && headerDelay == null) return null
    // Retry-After rounds up to seconds. Never retry earlier than either server hint.
    return listOfNotNull(bodyDelay, headerDelay).maxOrNull()
}

/** Keep the original receipt/checkout throughout a bounded, cancellable cooldown. */
internal suspend fun recoverPaymentVerification(
    onCooldown: (secondsRemaining: Int) -> Unit = {},
    elapsedMs: () -> Long = SystemClock::elapsedRealtime,
    verify: suspend () -> VerificationResult
): VerificationResult {
    var retries = 0
    var totalWaitMs = 0L
    try {
        while (true) {
            currentCoroutineContext().ensureActive()
            val result = verify()
            if (result !is VerificationResult.Error || result.retryAfterMs == null) return result
            val waitMs = result.retryAfterMs
            if (waitMs !in 1L..60_000L || retries >= 2 || totalWaitMs + waitMs > 65_000L) {
                return result.copy(
                    message = "Verification is still busy. Retry this same confirmation code shortly; do not pay again.",
                    retryAfterMs = null
                )
            }
            retries++
            totalWaitMs += waitMs
            val deadline = elapsedMs() + waitMs
            while (true) {
                val remaining = deadline - elapsedMs()
                if (remaining <= 0L) break
                onCooldown(((remaining + 999L) / 1_000L).toInt())
                delay(minOf(remaining, 1_000L))
            }
            onCooldown(0)
        }
    } finally {
        onCooldown(0)
    }
}
