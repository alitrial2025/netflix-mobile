package com.example.data.network

import android.content.Context
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SubscriptionPlans
import com.example.data.model.UserSubscription
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class VerificationResult {
    data class Success(val subscription: UserSubscription, val amountPaid: Int, val message: String) : VerificationResult()
    data class Error(val message: String) : VerificationResult()
}

/** Merchant credentials, gateway evidence and atomic activation live only in the trusted service. */
object PayheroVerifier {
    const val LIPWA_ACCOUNT_ID = "7976"
    const val LIPWA_URL = "https://lipwa.link/7976"
    private const val VERIFICATION_URL = "https://npro-app.vercel.app/api/payments/verify"
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(65, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
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


    suspend fun verifyPayment(
        receiptCode: String, planPrice: Int, planId: String, userId: String, paymentReference: String
    ): VerificationResult = withContext(Dispatchers.IO) {
        val code = receiptCode.trim().uppercase(Locale.ROOT)
        val user = FirebaseAuth.getInstance().currentUser
        val plan = SubscriptionPlans.PLANS.find { it.id == planId }
        if (user == null || user.uid != userId || user.isAnonymous)
            return@withContext VerificationResult.Error("Sign in to your account on this phone before verifying payment.")
        if (plan == null || plan.priceKes != planPrice)
            return@withContext VerificationResult.Error("Please select a valid membership plan.")
        if (!receiptFormat.matches(code))
            return@withContext VerificationResult.Error("Enter the 8–12 character M-Pesa code from your confirmation SMS.")
        if (!paymentReference.startsWith(referencePrefix(userId, planId)))
            return@withContext VerificationResult.Error("Open checkout from this account and plan, then enter its confirmation code.")
        try {
            val body = JSONObject().put("receiptCode", code).put("planId", planId)
                .put("paymentReference", paymentReference).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            for (attempt in 0..1) {
                val token = user.getIdToken(attempt > 0).await().token
                    ?: return@withContext VerificationResult.Error("Sign in to the same account and retry this code; do not pay again.")
                if (FirebaseAuth.getInstance().currentUser?.uid != userId)
                    return@withContext VerificationResult.Error("Your account changed. Sign in to the account used for this payment.")
                val request = Request.Builder().url(VERIFICATION_URL).post(body)
                    .header("Authorization", "Bearer $token").header("Accept", "application/json").build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.code == 401 && attempt == 0) return@use
                    val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                    if (!response.isSuccessful) return@withContext VerificationResult.Error(
                        json?.optString("message")?.takeIf { it.isNotBlank() }
                            ?: "Payment verification is unavailable. Retry this same code; do not pay again.")
                    if (FirebaseAuth.getInstance().currentUser?.uid != userId)
                        return@withContext VerificationResult.Error("Payment was saved to the original account. Sign in to that account to see it.")
                    val saved = json?.optJSONObject("subscription")
                        ?: return@withContext VerificationResult.Error("The membership service returned an incomplete result. Retry the same code; do not pay again.")
                    if (saved.optString("planId") != planId || saved.optString("mpesaReceipt") != code ||
                        saved.optString("status") != "ACTIVE" || saved.optLong("expiresAt") <= 0)
                        return@withContext VerificationResult.Error("The membership service could not confirm this payment. Contact support; do not pay again.")
                    val subscription = UserSubscription(status = saved.getString("status"), planId = planId,
                        planName = saved.optString("planName", plan.name), amount = saved.optInt("amount", plan.priceKes),
                        currency = saved.optString("currency", "KES"), paymentReference = saved.optString("paymentReference"),
                        mpesaReceipt = code, subscribedAt = saved.optLong("subscribedAt"), expiresAt = saved.getLong("expiresAt"))
                    return@withContext VerificationResult.Success(subscription, json?.optInt("amountPaid", subscription.amount) ?: subscription.amount,
                        json?.optString("message", "Payment verified. Your membership is active.") ?: "Payment verified. Your membership is active.")
                }
            }
            VerificationResult.Error("Your sign-in could not be verified. Sign in to the same account and retry this code; do not pay again.")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { VerificationResult.Error(com.example.data.billing.PaymentFailure.message(error)) }
    }
}
