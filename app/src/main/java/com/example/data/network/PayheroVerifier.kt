package com.example.data.network

import android.content.Context
import android.util.Log
import com.example.data.billing.PaymentEvidence
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SubscriptionPlans
import com.example.data.model.UserSubscription
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class VerificationResult {
    data class Success(val subscription: UserSubscription, val amountPaid: Int, val message: String) : VerificationResult()
    data class Error(val message: String) : VerificationResult()
}

/** Automatic verification lives on the phone. A modified client can bypass it;
 * merchant credentials and entitlement writes need a trusted service for tamper resistance. */
object PayheroVerifier {
    private const val TAG = "PayheroVerifier"
    // The Lipwa URL identifies a merchant account, not a payment channel ID.
    const val LIPWA_ACCOUNT_ID = "7976"
    const val LIPWA_URL = "https://lipwa.link/7976"
    private val PAYHERO_API_AUTH get() = com.example.BuildConfig.PAYHERO_API_AUTH

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val receiptFormat = Regex("^[A-Z0-9]{8,12}$")

    private fun referencePrefix(userId: String, planId: String): String {
        val owner = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray(Charsets.UTF_8))
            .take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "NF_${owner}_${planId.removePrefix("plan_")}_"
    }

    // Keep the reference after closing the sheet or restarting the phone so a
    // payment can be retried without asking the customer to pay again.
    fun checkoutReference(context: Context, userId: String, planId: String): String {
        require(userId.isNotBlank() && SubscriptionPlans.PLANS.any { it.id == planId })
        val prefs = context.getSharedPreferences("pending_payments", Context.MODE_PRIVATE)
        val key = "$userId:$planId"
        val prefix = referencePrefix(userId, planId)
        val stored = prefs.getString(key, null)
        if (stored != null && stored.startsWith(prefix)) return stored
        return (prefix + UUID.randomUUID().toString().replace("-", "")).also {
            prefs.edit().putString(key, it).apply()
        }
    }

    fun clearCheckoutReference(context: Context, userId: String, planId: String, reference: String) {
        val prefs = context.getSharedPreferences("pending_payments", Context.MODE_PRIVATE)
        val key = "$userId:$planId"
        if (prefs.getString(key, null) == reference) prefs.edit().remove(key).apply()
    }

    fun checkoutUrl(plan: SubscriptionPlan, reference: String): String =
        LIPWA_URL.toHttpUrl().newBuilder()
            .addQueryParameter("amount", plan.priceKes.toString())
            .addQueryParameter("reference", reference)
            .build().toString()

    private class PaymentError(message: String) : Exception(message)
    private data class GatewayPayment(val amount: Int, val serverTimeMs: Long, val reference: String)

    suspend fun verifyPayment(
        receiptCode: String,
        planPrice: Int,
        planId: String,
        userId: String,
        paymentReference: String
    ): VerificationResult = withContext(Dispatchers.IO) {
        val code = receiptCode.trim().uppercase(Locale.ROOT)
        val plan = SubscriptionPlans.PLANS.find { it.id == planId }
        val auth = FirebaseAuth.getInstance()
        if (userId.isBlank() || auth.currentUser?.uid != userId || auth.currentUser?.isAnonymous == true) {
            return@withContext VerificationResult.Error("Sign in to your account on this phone before paying.")
        }
        if (plan == null || plan.priceKes != planPrice) {
            return@withContext VerificationResult.Error("Please select a valid membership plan.")
        }
        if (!receiptFormat.matches(code)) {
            return@withContext VerificationResult.Error("Enter the 8–12 character M-Pesa code from your confirmation SMS.")
        }
        if (!paymentReference.startsWith(referencePrefix(userId, planId))) {
            return@withContext VerificationResult.Error("Open checkout from this account and plan, then enter its confirmation code.")
        }

        try {
            val db = FirebaseFirestore.getInstance()
            val receiptRef = db.collection("used_receipts").document(code)
            val userRef = db.collection("users").document(userId)
            val currentRef = userRef.collection("subscription").document("current")
            val existing = receiptRef.get(Source.SERVER).await()
            if (existing.exists()) {
                checkReceiptOwner(existing, userId, planId)
                val current = currentRef.get(Source.SERVER).await()
                if (auth.currentUser?.uid != userId) throw PaymentError("Your account changed. Sign in again to continue.")
                val subscription = subscriptionFrom(current)
                if (subscription.mpesaReceipt != code || subscription.planId != planId) throw PaymentError("This code has already been redeemed. Use a new payment to renew.")
                return@withContext VerificationResult.Success(subscription, subscription.amount, "This payment is already applied to your account.")
            }

            val payment = lookupPayment(code, planPrice, paymentReference)
            com.example.data.SubscriptionTime.synchronize(payment.serverTimeMs)
            currentCoroutineContext().ensureActive()
            if (auth.currentUser?.uid != userId) throw PaymentError("Your account changed. Sign in again to continue.")

            // Receipt consumption and all entitlement mirrors commit together.
            // Retrying the same code cannot add another 30-day period, including on two phones.
            val subscription = db.runTransaction { transaction ->
                if (auth.currentUser?.uid != userId) throw PaymentError("Your account changed. Sign in again to continue.")
                val redeemed = transaction.get(receiptRef)
                val current = transaction.get(currentRef)
                if (redeemed.exists()) {
                    checkReceiptOwner(redeemed, userId, planId)
                    val applied = subscriptionFrom(current)
                    if (applied.mpesaReceipt != code || applied.planId != planId) throw PaymentError("This code has already been redeemed. Use a new payment to renew.")
                    applied
                } else {
                    val previous = subscriptionFrom(current)
                    val renewalExpiry = if (previous.planId == planId &&
                        previous.status.equals("ACTIVE", true) && previous.expiresAt > payment.serverTimeMs) {
                        previous.expiresAt
                    } else 0L
                    val activated = UserSubscription(
                        status = "ACTIVE", planId = plan.id, planName = plan.name,
                        amount = plan.priceKes, currency = "KES", paymentReference = paymentReference,
                        mpesaReceipt = code, subscribedAt = payment.serverTimeMs,
                        expiresAt = SubscriptionPlans.oneMonthExpiry(payment.serverTimeMs, renewalExpiry)
                    )
                    val data = subscriptionData(activated)
                    transaction.set(receiptRef, mapOf(
                        "receipt" to code, "usedByUserId" to userId, "planId" to planId,
                        "amount" to payment.amount, "currency" to "KES",
                        "paymentReference" to paymentReference, "gatewayReference" to payment.reference,
                        "expiresAt" to activated.expiresAt, "verifiedAt" to FieldValue.serverTimestamp()
                    ))
                    transaction.set(currentRef, data, SetOptions.merge())
                    transaction.set(db.collection("subscriptions").document(userId),
                        data + mapOf("userId" to userId), SetOptions.merge())
                    transaction.set(userRef, mapOf(
                        "subscriptionPlanId" to planId, "subscriptionStatus" to "ACTIVE",
                        "updatedAt" to FieldValue.serverTimestamp()
                    ), SetOptions.merge())
                    activated
                }
            }.await()
            if (auth.currentUser?.uid != userId) throw PaymentError("Payment was saved to the original account. Sign in to that account to see it.")
            VerificationResult.Success(subscription, payment.amount, "Payment verified. Your ${plan.name} membership is active for 30 days.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Payment verification or atomic activation failed (${e.javaClass.simpleName})")
            val reason = generateSequence<Throwable>(e) { it.cause }.filterIsInstance<PaymentError>().firstOrNull()
            VerificationResult.Error(reason?.message ?: "Could not verify and save your membership. Check your connection and retry this same code; do not pay again.")
        }
    }

    private fun checkReceiptOwner(snapshot: DocumentSnapshot, userId: String, planId: String) {
        if (snapshot.getString("usedByUserId") != userId || snapshot.getString("planId") != planId) {
            throw PaymentError("This M-Pesa code has already been redeemed. Each payment can activate only one account and plan.")
        }
    }

    private fun subscriptionFrom(snapshot: DocumentSnapshot): UserSubscription = UserSubscription(
        status = snapshot.getString("status") ?: "NONE",
        planId = snapshot.getString("planId") ?: "plan_guest",
        planName = snapshot.getString("planName") ?: "Guest",
        amount = (snapshot.getLong("amount") ?: 0L).toInt(),
        currency = snapshot.getString("currency") ?: "KES",
        paymentReference = snapshot.getString("paymentReference") ?: "",
        mpesaReceipt = snapshot.getString("mpesaReceipt") ?: "",
        subscribedAt = snapshot.getLong("subscribedAt") ?: 0L,
        expiresAt = snapshot.getLong("expiresAt") ?: 0L
    )

    private fun subscriptionData(sub: UserSubscription): Map<String, Any> = mapOf(
        "status" to sub.status, "planId" to sub.planId, "planName" to sub.planName,
        "amount" to sub.amount, "currency" to sub.currency,
        "paymentReference" to sub.paymentReference, "mpesaReceipt" to sub.mpesaReceipt,
        "subscribedAt" to sub.subscribedAt, "expiresAt" to sub.expiresAt,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private data class GatewayResponse(val json: Any, val serverTimeMs: Long)

    private fun gatewayGet(path: String, parameters: Map<String, String>): GatewayResponse {
        if (PAYHERO_API_AUTH.isBlank()) throw PaymentError("Payment verification is not configured. Contact support before paying.")
        val url = "https://backend.payhero.co.ke/api/v2/$path".toHttpUrl().newBuilder().apply {
            parameters.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .header("Authorization", PAYHERO_API_AUTH).build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw PaymentError("Payment gateway lookup failed (HTTP ${response.code}). Retry shortly or contact support with your receipt.")
            val body = response.body?.string()?.trim().orEmpty()
            if (body.isEmpty()) throw PaymentError("Payment gateway returned no transaction. Retry shortly.")
            val serverTime = response.header("Date")?.let { date ->
                runCatching {
                    SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("GMT")
                        isLenient = false
                    }.parse(date)?.time
                }.getOrNull()
            } ?: throw PaymentError("Payment gateway did not provide a verification time. Retry shortly.")
            val json = runCatching { JSONTokener(body).nextValue() }.getOrNull()
            if (json !is JSONObject && json !is JSONArray) throw PaymentError("Unexpected payment gateway response. No membership was activated.")
            GatewayResponse(json, serverTime)
        }
    }

    private fun records(value: Any): List<JSONObject> = when (value) {
        is JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it) }
        is JSONObject -> {
            val nested = listOf("response", "data", "results", "transactions").mapNotNull { value.opt(it) }
                .firstOrNull { it is JSONArray || it is JSONObject }
            if (nested != null) records(nested) else listOf(value)
        }
        else -> emptyList()
    }

    private fun textField(item: JSONObject, vararg names: String) = PaymentEvidence.text(item, *names)
    private fun hasReceipt(item: JSONObject, receipt: String) = PaymentEvidence.hasReceipt(item, receipt)
    private fun amount(item: JSONObject) = PaymentEvidence.amount(item)

    private fun lookupPayment(code: String, price: Int, checkoutReference: String): GatewayPayment {
        // PayHero documents receipt lookup on transaction-status; /payments is
        // the POST collection endpoint, not a successful-payment search.
        val response = gatewayGet("transaction-status", mapOf("reference" to code))
        val payment = records(response.json).singleOrNull { hasReceipt(it, code) }
            ?: throw PaymentError("No exact payment found for this M-Pesa code. Confirm the SMS code and retry.")
        try { PaymentEvidence.validateTransaction(payment, code, checkoutReference) }
        catch (error: IllegalArgumentException) { throw PaymentError(error.message ?: "Invalid payment evidence") }
        // The legacy status response can omit the amount. Confirm it against the
        // same receipt in the authenticated merchant ledger before activating.
        val paid = amount(payment) ?: lookupReceiptAmount(code)
        val confirmed = try { PaymentEvidence.validate(payment, code, checkoutReference, price, paid) }
            catch (error: IllegalArgumentException) { throw PaymentError(error.message ?: "Invalid payment evidence") }
        return GatewayPayment(confirmed, response.serverTimeMs, textField(payment, "reference"))
    }

    private fun lookupReceiptAmount(code: String): Double {
        var page = 1
        val visited = mutableSetOf<Int>()
        repeat(10) {
            if (!visited.add(page)) throw PaymentError("Unexpected gateway pagination. Contact support with your receipt.")
            val response = gatewayGet("transactions", mapOf("page" to page.toString(), "per" to "100"))
            val item = records(response.json).firstOrNull { hasReceipt(it, code) && amount(it) != null }
            if (item != null) {
                val type = textField(item, "transaction_type").lowercase(Locale.ROOT)
                val currency = textField(item, "currency")
                if (listOf("withdraw", "charge", "payout", "refund", "revers").any { type.contains(it) } ||
                    (currency.isNotEmpty() && !currency.equals("KES", true))) {
                    throw PaymentError("This receipt is not an incoming KES membership payment.")
                }
                val ledgerStatus = textField(item, "status", "Status")
                if (ledgerStatus.isNotBlank() && !ledgerStatus.equals("SUCCESS", true))
                    throw PaymentError("This receipt is not a completed membership payment.")
                return amount(item) ?: throw PaymentError("The gateway could not confirm the paid amount.")
            }
            val pagination = (response.json as? JSONObject)?.optJSONObject("pagination")
            val next = pagination?.optInt("next_page", 0) ?: 0
            if (next <= page) throw PaymentError("The gateway has not confirmed this receipt's amount. Retry shortly or contact support; do not pay again.")
            page = next
        }
        throw PaymentError("This older receipt needs support to confirm its amount. Contact support; do not pay again.")
    }
}
