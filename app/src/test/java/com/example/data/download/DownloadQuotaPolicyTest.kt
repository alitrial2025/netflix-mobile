package com.example.data.download

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DownloadQuotaPolicyTest {
    private fun task(key: String, status: DownloadTaskStatus, profile: String = "profile") =
        DownloadTaskInfo(key, key, key, status = status, profileId = profile)

    @Test fun failedCompletedAndOtherProfileTasksDoNotConsumeExtraSlots() {
        val tasks = listOf(task("saved", DownloadTaskStatus.DOWNLOADING),
            task("failed", DownloadTaskStatus.ERROR), task("old", DownloadTaskStatus.COMPLETED),
            task("other", DownloadTaskStatus.QUEUED, "other-profile"))
        assertEquals(setOf("saved"), DownloadQuotaPolicy.occupiedKeys(listOf("saved"), tasks, "profile"))
        assertTrue(DownloadQuotaPolicy.canQueue("failed", listOf("saved"), tasks, "profile", 3))
        assertFalse(DownloadQuotaPolicy.canQueue("failed", listOf("saved"), tasks, "profile", 1))
    }

    @Test fun pausedDownloadsReserveTheirSlotAndCanResumeAtTheLimit() {
        val tasks = listOf(task("paused", DownloadTaskStatus.PAUSED), task("queued", DownloadTaskStatus.QUEUED))
        assertFalse(DownloadQuotaPolicy.canQueue("new", listOf("saved"), tasks, "profile", 3))
        assertTrue(DownloadQuotaPolicy.canQueue("paused", listOf("saved"), tasks, "profile", 3))
        assertFalse(DownloadQuotaPolicy.canQueue("paused", listOf("saved"), tasks, "profile", 0))
    }

    @Test fun concurrentWorkersCannotTakeTheSameLastSlotAndCancellationCanReleaseIt() = runTest {
        val reservations = DownloadSlotReservations()
        val results = (1..5).map { index -> async {
            reservations.reserve("profile", "new$index", 3) { listOf("saved1", "saved2") }
        } }.awaitAll()
        assertEquals(1, results.count { it })
        val winner = results.indexOf(true) + 1
        reservations.release("profile", "new$winner")
        assertTrue(reservations.reserve("profile", "retry", 3) { listOf("saved1", "saved2") })
        assertTrue(reservations.reserve("other", "another", 1) { emptyList() })
        reservations.release("profile", "retry")
        assertFalse(reservations.reserve("profile", "new", 3) { listOf("saved1", "saved2", "retry") })
    }
}
