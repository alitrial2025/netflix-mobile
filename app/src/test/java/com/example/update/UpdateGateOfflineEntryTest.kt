package com.example.update

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkInfo

/** Real launch orchestration: offline entry must not touch a pending update/install. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, packageName = "com.netflixpro.apk")
class UpdateGateOfflineEntryTest {
    private lateinit var application: Application
    private val models = ViewModelStore()

    @Before fun disconnect() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        application = ApplicationProvider.getApplicationContext()
        application.getSharedPreferences(UpdateGateViewModel.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val connectivity = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowOf(connectivity).clearAllNetworks()
    }

    @After fun cleanup() {
        models.clear()
        Dispatchers.resetMain()
    }

    @Test fun offlineLaunchWithNoCachedReleaseEntersTheApp() = runBlocking {
        val gate = UpdateGateViewModel(application)
        models.put("gate", gate)
        val completed = withTimeout(5000L) { gate.state.first { it.phase != UpdatePhase.CHECKING } }
        assertEquals(UpdatePhase.CONTINUE, completed.phase)
    }

    @Test fun offlineLaunchRetainsMandatoryManifestAndExistingDownloadAndInstall() = runBlocking {
        val installed = PackageInfoCompat.getLongVersionCode(
            application.packageManager.getPackageInfo(application.packageName, 0)
        )
        val release = JSONObject().apply {
            put("schemaVersion", 1)
            put("channel", "mobile")
            put("packageName", application.packageName)
            put("available", true)
            put("versionCode", installed + 1)
            put("versionName", "next")
            put("minSdk", 24)
            put("sizeBytes", 1024)
            put("sha256", "a".repeat(64))
            put("apkUrl", "/downloads/mobile/next.apk")
        }.toString()
        val prefs = application.getSharedPreferences(UpdateGateViewModel.PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString("release", release).putLong("download_id", 808L)
            .putInt("install_session", 42).commit()
        val before = prefs.all.toMap()
        val gate = UpdateGateViewModel(application)
        models.put("gate", gate)
        val completed = withTimeout(5000L) { gate.state.first { it.phase != UpdatePhase.CHECKING } }
        assertEquals(UpdatePhase.CONTINUE, completed.phase)
        assertEquals(before, prefs.all)
        val parsed = UpdateRelease.parse(release, UpdateRelease.manifestUrl(application)!!, application.packageName)
        assertEquals(UpdateGatePolicy.Decision.Required(parsed!!, true),
            UpdateGatePolicy.decide(installed, 34, null, parsed, online = true))
    }
    @Suppress("DEPRECATION")
    @Test fun losingWifiDuringPausedRequiredDownloadAllowsEntryWithoutDiscardingPendingUpdate() = runBlocking {
        val gate = UpdateGateViewModel(application)
        models.put("gate", gate)
        withTimeout(5000L) { gate.state.first { it.phase == UpdatePhase.CONTINUE } }

        val connectivity = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = shadowOf(connectivity)
        network.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(
            NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, true
        ))
        network.setDefaultNetworkActive(true)
        network.setNetworkCapabilities(connectivity.activeNetwork!!, NetworkCapabilities().apply {
            addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        })
        val installed = PackageInfoCompat.getLongVersionCode(
            application.packageManager.getPackageInfo(application.packageName, 0)
        )
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("channel", "mobile")
            put("packageName", application.packageName)
            put("available", true)
            put("versionCode", installed + 1)
            put("versionName", "next")
            put("minSdk", 24)
            put("sizeBytes", 1024)
            put("sha256", "a".repeat(64))
            put("apkUrl", "/downloads/mobile/next.apk")
        }.toString()
        val release = UpdateRelease.parse(json, UpdateRelease.manifestUrl(application)!!, application.packageName)!!
        val downloads = application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
        val id = downloads.enqueue(request)
        shadowOf(request).apply {
            status = DownloadManager.STATUS_PAUSED
            totalSize = release.sizeBytes
            bytesSoFar = 128L
        }
        val prefs = application.getSharedPreferences(UpdateGateViewModel.PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString("release", json).putLong("download_id", id).commit()
        val before = prefs.all.toMap()
        val monitoring = launch { gate.monitorDownload(release) }
        withTimeout(5000L) { gate.state.first { it.phase == UpdatePhase.DOWNLOADING } }
        network.clearAllNetworks()
        withTimeout(5000L) { monitoring.join() }

        assertEquals(UpdatePhase.CONTINUE, gate.state.value.phase)
        assertEquals(before, prefs.all)
        assertEquals(1, shadowOf(downloads).requestCount)
        assertEquals(request, shadowOf(downloads).getRequest(id))
        assertEquals(UpdateGatePolicy.Decision.Required(release, true),
            UpdateGatePolicy.decide(installed, 34, null, release, online = true))
    }

}
