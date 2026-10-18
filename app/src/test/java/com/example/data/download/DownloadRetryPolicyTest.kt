package com.example.data.download

import com.example.data.PlaybackRateLimitedException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class DownloadRetryPolicyTest {
    @Test fun resolverDeadlineBecomesRetryableInsteadOfCancellingTheWorker() = runBlocking {
        val error = runCatching { resolveDownloadSource { withTimeout(10) { delay(1_000) } } }.exceptionOrNull()!!
        assertTrue(error is DownloadSourceUnavailableException)
        assertTrue(DownloadRetryPolicy.shouldRetry(error as Exception, 0))
        assertTrue(DownloadRetryPolicy.retryMessage(error).contains("download link"))
    }

    @Test fun pausingOrStoppingTheWorkerStillCancelsSourceResolution() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var caught: Throwable? = null
        val job = launch {
            try { resolveDownloadSource { entered.complete(Unit); awaitCancellation() } }
            catch (error: Throwable) { caught = error; throw error }
        }
        entered.await()
        job.cancelAndJoin()
        assertTrue(caught is CancellationException)
        assertFalse(caught is DownloadSourceUnavailableException)
    }

    @Test fun sourceTransportFailureAndRateLimitRemainDistinct() = runBlocking {
        val source = runCatching { resolveDownloadSource { throw IOException("DNS unavailable") } }.exceptionOrNull()!!
        assertTrue(source is DownloadSourceUnavailableException)
        val limit = PlaybackRateLimitedException(60_000)
        assertSame(limit, runCatching { resolveDownloadSource { throw limit } }.exceptionOrNull())
        assertTrue(DownloadRetryPolicy.retryMessage(limit).contains("busy"))
        assertFalse(DownloadRetryPolicy.retryMessage(limit).contains("Connection interrupted"))
    }

    @Test fun accessCheckFailureDoesNotClaimTheWifiOrSubscriptionExpired() {
        val error = DownloadVerificationUnavailableException(IOException("Service unavailable"))
        assertTrue(DownloadRetryPolicy.shouldRetry(error, 0))
        assertTrue(DownloadRetryPolicy.retryMessage(error).contains("verify download access"))
        assertTrue(DownloadRetryPolicy.userMessage(error).contains("verify download access"))
        assertFalse(DownloadRetryPolicy.userMessage(error).contains("Renew"))
    }

    @Test fun expiredLinksAndUnavailableServersHaveAccurateRetryMessages() {
        assertTrue(DownloadRetryPolicy.retryMessage(DownloadHttpException(403)).contains("expired download link"))
        assertTrue(DownloadRetryPolicy.retryMessage(DownloadHttpException(429)).contains("busy"))
        assertTrue(DownloadRetryPolicy.retryMessage(DownloadHttpException(503)).contains("server is unavailable"))
        assertTrue(DownloadRetryPolicy.retryMessage(IOException()).contains("Connection interrupted"))
        assertFalse(DownloadRetryPolicy.shouldRetry(DownloadMembershipException("Suspended"), 0))
    }
}
