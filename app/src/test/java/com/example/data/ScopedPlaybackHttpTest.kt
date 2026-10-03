package com.example.data

import android.net.Uri
import androidx.media3.datasource.DataSpec
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ScopedPlaybackHttpTest {
    @Test fun media3IgnoresCallerCookiesAndResponseCookiesAcrossRequests() {
        val server = MockWebServer()
        server.enqueue(MockResponse().addHeader("Set-Cookie", "t_hash_t=ignored; Path=/").setBody("hello"))
        server.enqueue(MockResponse().setBody("world"))
        server.start()
        try {
            val url = server.url("/media").toString()
            val factory = ScopedPlaybackHttp.factory(mapOf("Cookie" to "old-session=ignored", "Authorization" to "ignored"), url)
            repeat(2) {
                val source = factory.createDataSource()
                try { source.open(DataSpec(Uri.parse(url))); source.read(ByteArray(5), 0, 5) }
                finally { source.close() }
                val request = server.takeRequest()
                assertNull(request.getHeader("Cookie"))
                assertNull(request.getHeader("Authorization"))
            }
        } finally { server.shutdown() }
    }
}
