# Payment verification cooldown recovery

The payment service enforces a five-second gap and five verification attempts per minute per authenticated account. A cooldown response is HTTP 429 with code VERIFICATION_BUSY, retryAfterMs and Retry-After. This response does not confirm or reject the underlying payment.

Previously the app displayed the server's seconds as a fixed error and immediately re-enabled Activate. Mobile 1.30 retains the receipt, account, plan and checkout reference while displaying a live countdown and automatically retrying after the server deadline. Both retry hints are respected. The wait uses elapsed time, stops when the screen is closed, and permits at most two retries and 65 seconds of cumulative cooldown. Repeated busy responses end with an actionable message rather than a stale seconds label.

Only explicit verification cooldowns are retried. Authentication, receipt ownership, merchant evidence, subscription expiry and atomic activation checks remain on the trusted server. Other server errors are shown directly and cannot grant access. Account identity is checked again on every attempt; the UI enables payment access only after the normal committed-membership confirmation.

PaymentVerificationRecoveryTest covers countdown timing, minute-long throttles, cancellation, bounded retries, unchanged receipt/reference, immediate success, nonrecoverable rejection and malformed retry hints. Existing payment evidence, failure, subscription and checkout tests remain enabled. These tests use synthetic payments; no customer receipt is redeemed by testing.
