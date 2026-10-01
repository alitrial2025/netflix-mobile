package com.example.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Real reachability and HTTP request timing; never invent bandwidth or CDN credentials. */
internal class NetworkDiagnostic(private val client: OkHttpClient = OkHttpClient.Builder()
    .callTimeout(10, TimeUnit.SECONDS).build()) {
    suspend fun run(online: Boolean, endpoint: String = "https://www.gstatic.com/generate_204"): String {
        if (!online) return "No internet connection. Connect to Wi-Fi or mobile data, then try again."
        val start = System.nanoTime()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(endpoint).get().build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resume("Connection check failed. Check your network and try again.")
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val elapsed = (System.nanoTime() - start) / 1_000_000
                        val message = if (it.code == 204) "Internet reachable • Test request: ${elapsed} ms"
                            else "Network reached the test server • HTTP ${it.code}"
                        if (continuation.isActive) continuation.resume(message)
                    }
                }
            })
        }
    }
}
