package com.example.data

import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Reuse connections while keeping provider, redirects and CDN requests cookie-free. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object ScopedPlaybackHttp {
    private val sharedClient = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
        .followSslRedirects(false).build()

    fun client(clientOverride: OkHttpClient? = null): OkHttpClient = clientOverride?.newBuilder()
        ?.cookieJar(CookieJar.NO_COOKIES)?.followSslRedirects(false)?.build() ?: sharedClient

    fun requestHeaders(headers: Map<String, String>): Map<String, String> {
        val properties = headers.filterKeys { !it.equals("Cookie", true) && !it.equals("Authorization", true) }.toMutableMap()
        if (properties.keys.none { it.equals("User-Agent", true) })
            properties["User-Agent"] = "Mozilla/5.0 (Linux; Android 16; Mobile)"
        return properties
    }

    fun factory(headers: Map<String, String>, sourceUrl: String): OkHttpDataSource.Factory {
        return OkHttpDataSource.Factory(sharedClient).setDefaultRequestProperties(requestHeaders(headers))
    }
}
