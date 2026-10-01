package com.example.ui.viewmodel

import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CatalogFetchPolicyTest {
    @Test fun offlineChildDoesNotCancelStartupOrSuccessfulSibling() = runBlocking {
        val offline = async { requestCatalogSection<String> { throw UnknownHostException("offline") } }
        val cachedSection = async { requestCatalogSection { listOf("cached title") } }
        assertNull(offline.await())
        assertEquals(listOf("cached title"), cachedSection.await())
        assertTrue(currentCoroutineContext().isActive)
    }

    @Test fun cancellationStillStopsTheRequest() = runBlocking {
        val request = async { requestCatalogSection<String> { throw CancellationException("screen closed") } }
        try { request.await(); fail("Cancellation was swallowed") } catch (_: CancellationException) { }
        assertTrue(request.isCancelled)
    }
}
