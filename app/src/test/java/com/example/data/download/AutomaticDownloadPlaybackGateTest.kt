package com.example.data.download

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class AutomaticDownloadPlaybackGateTest {
    @Test fun nextEpisodeAndCuratorMakeNoRequestsDuringPlaybackThenResumeOnClose() = runBlocking {
        val gate = AutomaticDownloadPlaybackGate()
        gate.setForeground(true)
        var nextRequests = 0
        var curatorRequests = 0
        val next = launch { gate.awaitPermission(true); nextRequests++ }
        val curator = launch { gate.awaitPermission(true); curatorRequests++ }
        yield()
        assertEquals(0, nextRequests)
        assertEquals(0, curatorRequests)
        gate.setForeground(false)
        next.join(); curator.join()
        assertEquals(1, nextRequests)
        assertEquals(1, curatorRequests)
    }
    @Test fun activeAutomaticTransferYieldsBeforeNextSegmentWhileManualDownloadContinues() = runBlocking {
        val gate = AutomaticDownloadPlaybackGate()
        var segments = 0
        gate.awaitPermission(true); segments++
        gate.setForeground(true)
        val nextSegment = launch { gate.awaitPermission(true); segments++ }
        val manual = async { gate.awaitPermission(false); "manual completed" }
        yield()
        assertEquals("manual completed", manual.await())
        assertEquals(1, segments)
        assertFalse(nextSegment.isCompleted)
        gate.setForeground(false)
        nextSegment.join()
        assertEquals(2, segments)
    }
    @Test fun automaticHttpRequestDefersWithoutBlockingManualTransfers() {
        val gate = AutomaticDownloadPlaybackGate()
        gate.setForeground(true)
        try { gate.requirePermission(true); fail("Automatic request started") }
        catch (_: AutomaticDownloadDeferredException) { }
        gate.requirePermission(false)
        gate.setForeground(false)
        gate.requirePermission(true)
    }
    @Test fun stoppedAutomaticWorkCannotIssueRequestsAfterClose() = runBlocking {
        val gate = AutomaticDownloadPlaybackGate()
        gate.setForeground(true)
        var requests = 0
        val cancelled = launch { gate.awaitPermission(true); requests++ }
        yield(); cancelled.cancelAndJoin()
        gate.setForeground(false); yield()
        assertEquals(0, requests)
    }
}
