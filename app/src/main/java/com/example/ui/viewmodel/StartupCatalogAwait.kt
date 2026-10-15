package com.example.ui.viewmodel

import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select

/** Cached content can open immediately if the connection disappears during startup refresh. */
internal suspend fun awaitCatalogWhileOnline(catalog: Job?, online: Flow<Boolean>) = coroutineScope {
    if (catalog == null || catalog.isCompleted) return@coroutineScope
    val loaded = async { catalog.join() }
    val disconnected = async { online.first { !it } }
    try {
        select<Unit> {
            loaded.onAwait { }
            disconnected.onAwait { }
        }
    } finally {
        loaded.cancel()
        disconnected.cancel()
    }
}
