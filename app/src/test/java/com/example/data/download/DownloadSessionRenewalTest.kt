package com.example.data.download

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.NetMirrorResolver
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DownloadSessionRenewalTest {
    @Test fun cdnRejectionPreservesWarmHandshakeAcrossRestartButProviderCookieRejectionRenewsIt() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val resolver = NetMirrorResolver(app)
        resolver.invalidateDownloadSession("42", "tv", 1, 1, true)
        val prefs = app.getSharedPreferences("netmirror_prefs", 0)
        val fixture = JSONObject().put("domain", "provider.example")
            .put("addhashRaw", "test-only").put("addhashEncoded", "test-only")
            .put("tHashTRaw", "test-only").put("tHashTEncoded", "test-only")
            .put("fetchedAt", System.currentTimeMillis()).toString()
        prefs.edit().putString("netmirror_session", fixture).commit()
        assertTrue(resolver.restoreSessionFromStorage())
        resolver.invalidateDownloadSession("42", "tv", 1, 1, false)
        assertEquals(fixture, prefs.getString("netmirror_session", null))
        assertTrue(NetMirrorResolver(app).restoreSessionFromStorage())
        resolver.invalidateDownloadSession("42", "tv", 1, 1, true)
        assertFalse(prefs.contains("netmirror_session"))
        assertFalse(NetMirrorResolver(app).restoreSessionFromStorage())
    }
}
