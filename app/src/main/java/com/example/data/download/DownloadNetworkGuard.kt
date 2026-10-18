package com.example.data.download

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class DownloadWifiRequiredException : IOException("Waiting for Wi-Fi")

/** Also protects Android 7/8, whose scheduler only supports a CONNECTED fallback. */
internal suspend fun <T> withDownloadNetwork(
    wifiOnly: Boolean,
    connectedToWifi: () -> Boolean,
    observe: (onChange: () -> Unit) -> (() -> Unit),
    transfer: suspend () -> T
): T {
    if (!wifiOnly) return transfer()
    return coroutineScope {
        val networkLost = AtomicBoolean(false)
        val work = async(start = CoroutineStart.LAZY) { transfer() }
        var unregister: (() -> Unit)? = null
        try {
            unregister = observe {
                if (!connectedToWifi()) {
                    networkLost.set(true)
                    work.cancel()
                }
            }
            if (!connectedToWifi()) throw DownloadWifiRequiredException()
            work.await()
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            if (networkLost.get()) throw DownloadWifiRequiredException()
            throw cancelled
        } finally {
            work.cancel()
            unregister?.invoke()
        }
    }
}
