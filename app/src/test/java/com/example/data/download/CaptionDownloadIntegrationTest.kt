package com.example.data.download

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.Caption
import com.example.data.NetMirrorResolver
import com.example.data.NetMirrorStream
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.repository.NetflixRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CaptionDownloadIntegrationTest {
    @Test fun providerCaptionCookiesAreScopedAndLocalTracksAreCommittedBeforeOfflinePlayback() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder()
            .setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build())
        val certificate = HeldCertificate.Builder().commonName("caption-test")
            .addSubjectAlternativeName("provider.test").addSubjectAlternativeName("cdn.test").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        val paths = CopyOnWriteArrayList<String>()
        val failures = CopyOnWriteArrayList<String>()
        val caption = "WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nHello offline\n"
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                paths += path
                if (path == "/provider.vtt") {
                    if (!request.getHeader("Cookie").orEmpty().contains("t_hash_t=fixture-cookie")) {
                        failures += "Missing provider caption session"
                        return MockResponse().setResponseCode(403)
                    }
                } else if (request.getHeader("Cookie") != null) {
                    failures += "Provider cookie leaked to CDN"
                    return MockResponse().setResponseCode(403)
                }
                return MockResponse().setBody(if (path.endsWith(".vtt")) caption else "fixture-video-bytes")
            }
        }
        server.start()
        val client = OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).dns(object : okhttp3.Dns {
            override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1"))
        })
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        val database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val repository = NetflixRepository(database.netflixDao())
        val manager = NetflixDownloadManager(app, repository, NetMirrorResolver(app), client, { "account" })
        val key = "caption_movie"
        val media = MediaItem(key, "Caption movie", MediaType.MOVIE, "", "", 90, "16+", 2026, "2h",
            genres = emptyList(), cast = emptyList(), director = "")
        fun url(host: String, path: String) = server.url(path).newBuilder().host(host).build().toString()
        val headers = mapOf("Origin" to url("provider.test", "/").removeSuffix("/"))
        val stream = NetMirrorStream(url("cdn.test", "/video.mp4"), headers,
            listOf(Caption(url("provider.test", "/provider.vtt"), "English", "vtt", "en"),
                Caption(url("cdn.test", "/cdn.vtt"), "French", "vtt", "fr")), "Fixture", Long.MAX_VALUE,
            media.title, captionHeaders = headers + ("Cookie" to "t_hash_t=fixture-cookie; ott=nf"))
        val store = DownloadRequestStore(File(app.noBackupFilesDir, "download-requests"))
        try {
            manager.startOrResumeDownload("profile", media, isWifiOnly = true)
            manager.executeResolvedDownload("profile", media, null, key, null, true, stream)
            assertTrue(failures.toString(), failures.isEmpty())
            assertTrue(paths.indexOf("/provider.vtt") < paths.indexOf("/video.mp4"))
            assertTrue(paths.indexOf("/cdn.vtt") < paths.indexOf("/video.mp4"))
            val record = repository.getDownloadsOnce("profile").single()
            val tracks = JSONArray(record.captionsJson)
            assertEquals(2, tracks.length())
            for (i in 0 until tracks.length()) {
                val uri = URI(tracks.getJSONObject(i).getString("url"))
                assertEquals("file", uri.scheme)
                assertEquals(caption, File(uri).readText())
            }
            assertFalse(record.captionsJson!!.contains("fixture-cookie"))
            // Remove the network entirely; a reconstructed downloader reuses both tracks.
            server.shutdown()
            assertEquals(2, manager.downloadCaptions(key, stream).size)
        } finally {
            manager.cancelAndJoinTransfers(); manager.close(); database.close()
            WorkManager.getInstance(app).cancelAllWork().result.get()
            store.remove(key)
            File(app.filesDir, "downloads/$key.captions").deleteRecursively()
            File(app.filesDir, "downloads/$key.mp4").delete()
            server.shutdown()
        }
    }
}
