package com.example.ui.viewmodel

import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StartupCatalogAwaitTest {
    @Test fun offlineEntryDoesNotWaitForTheRemoteCatalog() = runTest {
        val catalog = Job()
        val online = MutableStateFlow(false)
        awaitCatalogWhileOnline(catalog, online)
        assertTrue(catalog.isActive)
        assertEquals(0, online.subscriptionCount.value)
        catalog.cancel()
    }

    @Test fun losingConnectivityReleasesStartupWithoutCancellingTheRefresh() = runTest {
        val catalog = Job()
        val online = MutableStateFlow(true)
        val startup = async { awaitCatalogWhileOnline(catalog, online) }
        runCurrent()
        assertFalse(startup.isCompleted)
        online.value = false
        startup.await()
        assertTrue(catalog.isActive)
        assertEquals(0, online.subscriptionCount.value)
        catalog.cancel()
    }

    @Test fun onlineEntryWaitsUntilTheCatalogCompletesThenReleasesTheObserver() = runTest {
        val catalog = Job()
        val online = MutableStateFlow(true)
        val startup = async { awaitCatalogWhileOnline(catalog, online) }
        runCurrent()
        assertFalse(startup.isCompleted)
        catalog.complete()
        startup.await()
        assertEquals(0, online.subscriptionCount.value)
    }
}
