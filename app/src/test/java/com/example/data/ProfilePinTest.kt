package com.example.data

import com.example.data.model.ProfilePin
import org.junit.Assert.*
import org.junit.Test

class ProfilePinTest {
    @Test fun hashedAndLegacyPinsMatchWithoutRehashingCloudDigests() {
        val digest = ProfilePin.hash("0123")!!
        assertEquals(digest, ProfilePin.hash(digest))
        assertEquals(digest, ProfilePin.hash(digest.uppercase()))
        assertTrue(ProfilePin.matches(digest, "0123"))
        assertTrue(ProfilePin.matches(digest.uppercase(), "0123"))
        assertTrue(ProfilePin.matches("0123", "0123"))
        assertFalse(ProfilePin.matches(digest, "123"))
        assertFalse(ProfilePin.matches(digest, "0124"))
    }
}
