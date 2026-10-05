package com.example.data.download

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One completed or pending title occupies one slot, including paused resumable work. */
object DownloadQuotaPolicy {
    fun occupiedKeys(completed: Iterable<String>, tasks: Iterable<DownloadTaskInfo>, profileId: String): Set<String> =
        completed.toMutableSet().apply {
            tasks.filter { it.profileId == profileId && it.status != DownloadTaskStatus.ERROR &&
                it.status != DownloadTaskStatus.COMPLETED }.forEach { add(it.downloadKey) }
        }

    fun canQueue(key: String, completed: Iterable<String>, tasks: Iterable<DownloadTaskInfo>, profileId: String, limit: Int): Boolean {
        val occupied = occupiedKeys(completed, tasks, profileId)
        return limit > 0 && (key in occupied || occupied.size < limit)
    }
}

/** Prevent concurrent workers from consuming the same last slot after a plan downgrade. */
internal class DownloadSlotReservations {
    private val mutex = Mutex()
    private val running = mutableMapOf<String, MutableSet<String>>()

    suspend fun reserve(profileId: String, key: String, limit: Int, completed: suspend () -> Iterable<String>): Boolean = mutex.withLock {
        val occupied = completed().toMutableSet().apply { addAll(running[profileId].orEmpty()) }
        if (limit <= 0 || (key !in occupied && occupied.size >= limit)) return@withLock false
        running.getOrPut(profileId) { mutableSetOf() }.add(key)
        true
    }

    suspend fun release(profileId: String, key: String) = mutex.withLock {
        running[profileId]?.let { keys ->
            keys.remove(key)
            if (keys.isEmpty()) running.remove(profileId)
        }
        Unit
    }
}
