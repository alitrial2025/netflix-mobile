package com.example.data

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(manifest = org.robolectric.annotation.Config.NONE, sdk = [28])
class EntryAccessFailureTest {
    @Test fun transientFailuresAndCancellationDoNotRevokeAnAdmittedSession() {
        val session = EntrySessionAdmission().apply { admit("paid-user", "phone") }
        val failures = listOf(
            UnknownHostException("offline"),
            SocketTimeoutException("timeout"),
            CancellationException("superseded"),
            MembershipCheckException(),
            FirebaseFirestoreException("unavailable", FirebaseFirestoreException.Code.UNAVAILABLE),
            FirebaseFirestoreException("deadline", FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
        )
        failures.forEach { failure ->
            assertFalse(failure.isAuthoritativeAccessFailure())
            session.onRefreshFailure(failure.isAuthoritativeAccessFailure())
            assertTrue(session.isAdmitted("paid-user", "phone"))
        }
    }

    @Test fun explicitMembershipDeviceAndScreenDenialsBlockTheSessionEvenWhenWrapped() {
        val failures = listOf(
            MembershipRevokedException(),
            DeviceAccessException("Another device owns this plan"),
            DeviceAccessException("All screens are in use"),
            FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED),
            FirebaseFirestoreException("unauthenticated", FirebaseFirestoreException.Code.UNAUTHENTICATED)
        )
        failures.forEach { failure ->
            val session = EntrySessionAdmission().apply { admit("paid-user", "phone") }
            val wrapped = MembershipCheckException(failure)
            assertTrue(wrapped.isAuthoritativeAccessFailure())
            session.onRefreshFailure(wrapped.isAuthoritativeAccessFailure())
            assertFalse(session.isAdmitted("paid-user", "phone"))
        }
    }
}
