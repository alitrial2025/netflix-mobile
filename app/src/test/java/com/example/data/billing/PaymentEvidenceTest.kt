package com.example.data.billing

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaymentEvidenceTest {
    private fun payment() = JSONObject().put("provider_reference", "TEST123456").put("status", "SUCCESS")
        .put("provider", "M-PESA").put("currency", "KES").put("external_reference", "fixture-checkout")
        .put("transaction_type", "PAYMENT").put("amount", 1350)

    @Test fun onlyExactSuccessfulIncomingReceiptReferenceAndSufficientAmountAreAccepted() {
        assertEquals(1350, PaymentEvidence.validate(payment(), "TEST123456", "fixture-checkout", 1350, 1350.0))
        assertEquals(1400, PaymentEvidence.validate(payment(), "TEST123456", "fixture-checkout", 1350, 1400.0))
        val invalid = listOf("provider_reference" to "OTHER12345", "external_reference" to "other-account",
            "status" to "PENDING", "success" to false, "provider" to "CARD", "currency" to "USD",
            "transaction_type" to "WITHDRAWAL", "transaction_type" to "REFUND", "transaction_type" to "REVERSAL")
        for ((field, value) in invalid) {
            assertThrows("Rejected $field=$value", IllegalArgumentException::class.java) {
                PaymentEvidence.validate(payment().put(field, value), "TEST123456", "fixture-checkout", 1350, 1350.0)
            }
        }
        for (amount in listOf(1349.0, 1350.5, Double.NaN, Double.POSITIVE_INFINITY, Int.MAX_VALUE.toDouble() + 1)) {
            assertThrows(IllegalArgumentException::class.java) {
                PaymentEvidence.validate(payment(), "TEST123456", "fixture-checkout", 1350, amount)
            }
        }
    }
}
