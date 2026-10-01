package com.example.data.billing

import org.json.JSONObject
import java.util.Locale

/** Validate the authenticated gateway's evidence; this is not an independent signature. */
internal object PaymentEvidence {
    fun text(item: JSONObject, vararg names: String): String = names.asSequence()
        .map { item.optString(it, "").trim() }.firstOrNull { it.isNotEmpty() && it != "null" }.orEmpty()

    fun hasReceipt(item: JSONObject, receipt: String): Boolean = text(item,
        "provider_reference", "MpesaReceiptNumber", "providerReference", "mpesa_receipt", "third_party_reference"
    ).uppercase(Locale.ROOT) == receipt

    fun amount(item: JSONObject): Double? = item.opt("amount")?.toString()?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it > 0.0 }

    fun validate(payment: JSONObject, receipt: String, checkoutReference: String, price: Int, confirmedAmount: Double): Int {
        require(price > 0) { "Invalid membership price" }
        validateTransaction(payment, receipt, checkoutReference)
        require(confirmedAmount.isFinite() && confirmedAmount >= price && confirmedAmount <= Int.MAX_VALUE && confirmedAmount % 1.0 == 0.0) {
            "The confirmed amount is insufficient or invalid for this plan (KES $price)."
        }
        return confirmedAmount.toInt()
    }
    fun validateTransaction(payment: JSONObject, receipt: String, checkoutReference: String) {
        require(hasReceipt(payment, receipt)) { "No exact payment found for this M-Pesa code." }
        require(text(payment, "status", "Status").equals("SUCCESS", true) &&
            (!payment.has("success") || payment.optBoolean("success"))) {
            "This payment is not completed yet. Wait for the M-Pesa confirmation and retry."
        }
        val provider = text(payment, "provider", "gateway").lowercase(Locale.ROOT)
        require(provider.isEmpty() || provider in setOf("m-pesa", "mpesa")) { "Only completed M-Pesa payments can activate this plan." }
        require(text(payment, "payment_reference", "external_reference", "user_reference") == checkoutReference) {
            "This payment does not match checkout for this account and plan. Use the code from checkout opened inside this app."
        }
        val currency = text(payment, "currency")
        require(currency.isEmpty() || currency.equals("KES", true)) { "The payment must be in Kenyan shillings (KES)." }
        val direction = text(payment, "transaction_type", "type").lowercase(Locale.ROOT)
        require(listOf("withdraw", "charge", "payout", "refund", "revers").none { direction.contains(it) }) {
            "This transaction is not a membership payment."
        }
    }

}
