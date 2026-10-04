package com.example.data.billing

import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class PaymentFailureTest {
    @Test fun deniedReceiptOrActivationIsNotReportedAsAConnectionFailure() {
        val error = RuntimeException(FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED))
        val message = PaymentFailure.message(error)
        assertTrue(message.contains("membership service"))
        assertTrue(message.contains("do not pay again"))
        assertFalse(message.contains("Check your connection"))
    }
    @Test fun realNetworkAndAuthenticationFailuresKeepDistinctRecoveryActions() {
        assertTrue(PaymentFailure.message(IOException("timeout")).contains("Check your connection"))
        assertTrue(PaymentFailure.message(FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)).contains("temporarily unavailable"))
        assertTrue(PaymentFailure.message(FirebaseFirestoreException("expired", FirebaseFirestoreException.Code.UNAUTHENTICATED)).contains("same account"))
    }
}
