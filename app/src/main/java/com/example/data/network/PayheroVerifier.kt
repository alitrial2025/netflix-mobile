package com.example.data.network

import android.content.Context
import com.example.BuildConfig
import com.example.data.fetchText
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SubscriptionPlans
import com.example.data.model.UserSubscription
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class VerificationResult {
    data class Success(val subscription: UserSubscription, val amountPaid: Int, val message: String) : VerificationResult()
    data class Error(val message: String) : VerificationResult()
}

/** The Worker verifies merchant evidence and grants entitlements; the phone holds no merchant secret. */
object PayheroVerifier {
    const val LIPWA_ACCOUNT_ID = "7976"
    const val LIPWA_URL = "https://lipwa.link/7976"
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).callTimeout(65, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    private fun referencePrefix(userId: String, planId: String): String {
        val owner = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray(Charsets.UTF_8))
            .take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "NF_${owner}_${planId.removePrefix("plan_")}_"
    }

    @Synchronized fun checkoutReference(context: Context, userId: String, planId: String): String {
        require(userId.isNotBlank() && SubscriptionPlans.PLANS.any { it.id == planId })
        val prefs = context.getSharedPreferences("pending_payments", Context.MODE_PRIVATE)
        val key = "$userId:$planId"
        val prefix = referencePrefix(userId, planId)
        val stored = prefs.getString(key, null)
        if (stored != null && stored.startsWith(prefix) && stored.removePrefix(prefix).matches(Regex("[a-f0-9]{32}"))) return stored
        return (prefix + UUID.randomUUID().toString().replace("-", "")).also {
            check(prefs.edit().putString(key, it).commit()) { "Unable to save checkout reference" }
        }
    }

    @Synchronized fun clearCheckoutReference(context: Context, userId: String, planId: String, reference: String) {
        val prefs = context.getSharedPreferences("pending_payments", Context.MODE_PRIVATE)
        val key = "$userId:$planId"
        if (prefs.getString(key, null) == reference) prefs.edit().remove(key).commit()
    }

    fun checkoutUrl(plan: SubscriptionPlan, reference: String): String =
        checkNotNull(LIPWA_URL.toHttpUrlOrNull()).newBuilder()
            .addQueryParameter("amount", plan.priceKes.toString()).addQueryParameter("reference", reference).build().toString()

    private fun endpoint(path: String): HttpUrl {
        val base = BuildConfig.PAYMENT_API_URL.toHttpUrlOrNull()
        require(base != null && base.isHttps && base.username.isEmpty() && base.password.isEmpty() &&
            base.query == null && base.fragment == null) { "Payment verification is not configured. Contact support before paying." }
        return base.newBuilder().encodedPath(path).build()
    }

    private suspend fun backend(path: String, userId: String, payload: JSONObject? = null): JSONObject {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser?.takeIf { it.uid == userId && !it.isAnonymous }
            ?: error("Sign in to your account on this phone before paying.")
        val token = user.getIdToken(false).await().token ?: error("Sign in again to verify payment.")
        val request = Request.Builder().url(endpoint(path)).header("Authorization", "Bearer $token")
            .header("Accept", "application/json").apply {
                if (payload != null) post(payload.toString().toRequestBody("application/json".toMediaType()))
            }.build()
        val response = http.fetchText(request, 32 * 1024L)
        check(auth.currentUser?.uid == userId) { "Your account changed. Sign in to the payment account to continue." }
        val json = runCatching { JSONObject(response.body) }.getOrNull()
        if (!response.isSuccessful) error(json?.optString("message")?.takeIf { it.isNotBlank() }?.take(512)
            ?: "Payment verification is unavailable. Retry the same code; do not pay again.")
        return json ?: error("Invalid membership response. Retry the same code; do not pay again.")
    }

    suspend fun billingAvailability(userId: String): String? = withContext(Dispatchers.IO) {
        try {
            val status = backend("/v1/billing/status", userId)
            if (status.optBoolean("enabled") && status.optInt("version") == 1) null
            else "Payment verification is unavailable. Contact support before paying."
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { "Payment verification is unavailable. Reconnect or contact support before paying." }
    }

    suspend fun verifyPayment(receiptCode: String, planPrice: Int, planId: String, userId: String,
                              paymentReference: String): VerificationResult = withContext(Dispatchers.IO) {
        val code = receiptCode.trim().uppercase(Locale.ROOT)
        val plan = SubscriptionPlans.PLANS.find { it.id == planId }
        if (plan == null || plan.priceKes != planPrice) return@withContext VerificationResult.Error("Please select a valid membership plan.")
        if (!Regex("^[A-Z0-9]{8,12}$").matches(code)) return@withContext VerificationResult.Error("Enter the 8–12 character M-Pesa code from your confirmation SMS.")
        if (!paymentReference.startsWith(referencePrefix(userId, planId))) return@withContext VerificationResult.Error("Open checkout from this account and plan first.")
        try {
            val result = backend("/v1/billing/verify", userId, JSONObject().put("receiptCode", code)
                .put("planId", planId).put("paymentReference", paymentReference))
            val data = result.getJSONObject("subscription")
            check(data.getString("planId") == planId && data.getString("mpesaReceipt") == code &&
                data.getString("paymentReference") == paymentReference && data.getInt("amount") == planPrice &&
                data.getString("currency") == "KES") { "Invalid membership response. Retry the same code; do not pay again." }
            val serverNow = result.getLong("serverTimeMs")
            check(serverNow > 0)
            com.example.data.SubscriptionTime.synchronize(serverNow)
            val subscription = UserSubscription(status = data.getString("status"), planId = planId, planName = plan.name,
                amount = planPrice, currency = "KES", paymentReference = paymentReference, mpesaReceipt = code,
                subscribedAt = data.getLong("subscribedAt"), expiresAt = data.getLong("expiresAt"))
            VerificationResult.Success(subscription, result.getInt("amountPaid"), result.getString("message"))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { VerificationResult.Error(error.message?.take(512)
            ?: "Unable to finish verification. Retry the same code; do not pay again.") }
    }
}
