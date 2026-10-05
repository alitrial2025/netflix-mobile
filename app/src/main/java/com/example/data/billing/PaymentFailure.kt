package com.example.data.billing

import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException

/** Preserve actionable failure categories instead of describing denied writes as lost Wi-Fi. */
internal object PaymentFailure {
    fun message(error: Throwable): String {
        val causes = generateSequence(error) { it.cause }.toList()
        val firestore = causes.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        return when (firestore?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "This payment could not be verified for this account or saved by the membership service. Contact support with your receipt; do not pay again."
            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "Your sign-in could not be confirmed. Sign in to the same account, then retry this code; do not pay again."
            FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "Membership verification is temporarily unavailable. Reconnect and retry this same code; do not pay again."
            else -> if (causes.any { it is IOException })
                "The payment gateway could not be reached. Check your connection and retry this same code; do not pay again."
            else "Could not finish membership verification. Contact support with your receipt and retry this same code; do not pay again."
        }
    }
}
