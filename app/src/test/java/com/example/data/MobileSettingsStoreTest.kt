package com.example.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MobileSettingsStoreTest {
    @Test fun preferencesSurviveReconstructionAndInvalidCellularValuesUseAutomatic() {
        val prefs = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("settings-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val first = MobileSettingsStore(prefs)
        MobileSetting.entries.forEach { assertTrue(first.get(it)); first.set(it, false) }
        first.cellularDataMode = CellularDataMode.SAVE_DATA
        val restored = MobileSettingsStore(prefs)
        MobileSetting.entries.forEach { assertFalse(restored.get(it)) }
        assertEquals(CellularDataMode.SAVE_DATA, restored.cellularDataMode)
        prefs.edit().putString("pref_cellular_data", "invalid").commit()
        assertEquals(CellularDataMode.AUTOMATIC, MobileSettingsStore(prefs).cellularDataMode)
    }

    @Test fun cellularPoliciesEnforceWifiDataSavingAndMembershipResolutionLimits() {
        assertFalse(MobilePlaybackPolicy.permitsStreaming(CellularDataMode.WIFI_ONLY, false))
        assertTrue(MobilePlaybackPolicy.permitsStreaming(CellularDataMode.WIFI_ONLY, true))
        assertEquals(480, MobilePlaybackPolicy.maxVideoHeight(true, CellularDataMode.SAVE_DATA, false, "plan_premium"))
        assertEquals(2160, MobilePlaybackPolicy.maxVideoHeight(true, CellularDataMode.SAVE_DATA, true, "plan_premium"))
        assertEquals(1080, MobilePlaybackPolicy.maxVideoHeight(true, CellularDataMode.AUTOMATIC, false, "plan_premium"))
        assertEquals(2160, MobilePlaybackPolicy.maxVideoHeight(true, CellularDataMode.MAXIMUM, false, "plan_premium"))
        assertEquals(720, MobilePlaybackPolicy.maxVideoHeight(true, CellularDataMode.MAXIMUM, true, "plan_basic"))
        assertEquals(720, MobilePlaybackPolicy.maxVideoHeight(false, CellularDataMode.MAXIMUM, true, "plan_premium"))
        assertEquals(480, MobilePlaybackPolicy.maxVideoHeight(true, CellularDataMode.MAXIMUM, true, "plan_mobile"))
    }

    @Test fun diagnosticsUseActualHttpResultsAndNeverClaimInventedBandwidth() = runBlocking {
        val server = MockWebServer()
        server.start()
        val diagnostic = NetworkDiagnostic(OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build())
        try {
            assertTrue(diagnostic.run(false, server.url("/").toString()).startsWith("No internet"))
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setResponseCode(204))
            val result = diagnostic.run(true, server.url("/").toString())
            assertTrue(result.contains("Internet reachable"))
            assertTrue(Regex("Test request: \\d+ ms").containsMatchIn(result))
            assertFalse(result.contains("Mbps"))
            server.enqueue(MockResponse().setResponseCode(503))
            assertTrue(diagnostic.run(true, server.url("/").toString()).contains("HTTP 503"))
        } finally { server.shutdown() }
    }
}
