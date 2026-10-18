package com.example.data.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DownloadNetworkGuardTest {
    @Test fun wifiLossStopsActiveTransferAndReleasesItsNetworkObserver() = runBlocking {
        var wifi = true
        var changed: (() -> Unit)? = null
        var closed = false
        var stopped = false
        val started = CompletableDeferred<Unit>()
        val result = async {
            runCatching {
                withDownloadNetwork(true, { wifi }, { listener -> changed = listener; { closed = true } }) {
                    try { started.complete(Unit); awaitCancellation() }
                    finally { stopped = true }
                }
            }
        }
        started.await()
        wifi = false
        changed!!.invoke()
        val error = result.await().exceptionOrNull()
        assertTrue(error is DownloadWifiRequiredException)
        assertTrue(stopped)
        assertTrue(closed)
        assertTrue(DownloadRetryPolicy.shouldRetry(error as Exception, 0))
        assertTrue(DownloadRetryPolicy.retryMessage(error).contains("Waiting for Wi-Fi"))
    }

    @Test fun cellularCannotStartAWifiOnlyTransfer() = runBlocking {
        var transferred = false
        var closed = false
        val result = runCatching {
            withDownloadNetwork(true, { false }, { { closed = true } }) { transferred = true }
        }
        assertTrue(result.exceptionOrNull() is DownloadWifiRequiredException)
        assertFalse(transferred)
        assertTrue(closed)
    }

    @Test fun mobileDataPreferenceDoesNotWaitForWifi() = runBlocking {
        assertEquals("download", withDownloadNetwork(false, { false }, { error("Must not monitor Wi-Fi") }) { "download" })
    }

    @Test fun manualPauseRemainsCancellationAndClosesTheObserver() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var error: Throwable? = null
        var closed = false
        val job = async {
            try { withDownloadNetwork(true, { true }, { { closed = true } }) { started.complete(Unit); awaitCancellation() } }
            catch (caught: Throwable) { error = caught; throw caught }
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(error is CancellationException)
        assertTrue(closed)
    }
}
