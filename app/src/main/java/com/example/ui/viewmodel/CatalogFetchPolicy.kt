package com.example.ui.viewmodel

import kotlinx.coroutines.CancellationException

/** Catch inside the child: a failing async request must not cancel startup's parent job. */
internal suspend fun <T> requestCatalogSection(block: suspend () -> T): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}
