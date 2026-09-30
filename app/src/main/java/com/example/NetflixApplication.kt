package com.example

import android.app.Application
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.google.firebase.FirebaseApp
import java.io.File
import java.util.concurrent.Executors

@androidx.media3.common.util.UnstableApi
class NetflixApplication : Application(), ImageLoaderFactory {
    companion object {
        lateinit var downloadCache: Cache
        lateinit var downloadManager: DownloadManager
    }

    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val exoCacheDir = File(filesDir, "exoplayer_downloads")
        val evictor = LeastRecentlyUsedCacheEvictor(1024L * 1024L * 1024L) // 1GB
        val databaseProvider = StandaloneDatabaseProvider(this)
        downloadCache = SimpleCache(exoCacheDir, evictor, databaseProvider)
        
        val dataSourceFactory = DefaultHttpDataSource.Factory()
        downloadManager = DownloadManager(
            this,
            databaseProvider,
            downloadCache,
            dataSourceFactory,
            Executors.newFixedThreadPool(4)
        )
        downloadManager.maxParallelDownloads = 3
        downloadManager.resumeDownloads()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("netflix_image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024) // 250 MB disk cache for instant poster access
                    .build()
            }
            .components {
                add(SvgDecoder.Factory())
            }
            .respectCacheHeaders(false)
            .allowHardware(true)
            .build()
    }
}
