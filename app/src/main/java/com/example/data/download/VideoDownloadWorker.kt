package com.example.data.download

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.collect

/** WorkManager owns execution across Activities, network changes and process restarts. */
class VideoDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val manager by lazy { NetflixDownloadManager.getInstance(applicationContext as Application) }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val channel = "netflixpro_active_downloads"
        val notifications = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(
            NotificationChannel(channel, "Active downloads", NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getActivity(applicationContext, id.hashCode(),
            Intent(applicationContext, MainActivity::class.java).putExtra("open_downloads", true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val key = inputData.getString("key").orEmpty()
        val task = manager.downloadTasks.value[key]
        val notification = NotificationCompat.Builder(applicationContext, channel)
            .setSmallIcon(R.drawable.ic_netflix_download_custom).setContentTitle(task?.mediaTitle ?: "NetflixPro download")
            .setContentText(task?.let { "${(it.progress * 100).toInt()}% · ${it.speedFormatted}" } ?: "Downloading for offline viewing")
            .setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(pending).setProgress(100, ((task?.progress ?: 0f) * 100).toInt(), task?.status != DownloadTaskStatus.DOWNLOADING)
            .addAction(0, "Pause", VideoDownloadControlReceiver.pauseIntent(applicationContext, key)).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(id.hashCode(), notification)
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override suspend fun doWork(): Result {
        val key = inputData.getString("key") ?: return Result.failure()
        val token = inputData.getString("token") ?: return Result.failure()
        if (!manager.isCurrentRequest(key, token)) return Result.success()
        try {
            setForeground(getForegroundInfo())
            return coroutineScope {
                val progress = launch {
                    manager.downloadTasks.sample(1500).collect {
                        try { setForeground(getForegroundInfo()) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* Android may suppress notification updates. */ }
                    }
                }
                try {
                    when (manager.runSavedDownload(key, token, runAttemptCount)) {
                        DownloadRunResult.COMPLETE -> Result.success()
                        DownloadRunResult.RETRY -> Result.retry()
                        DownloadRunResult.FAILED -> Result.failure()
                    }
                } finally { progress.cancel() }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalStateException) {
            if (runAttemptCount >= 8) {
                manager.markFailed(key, token, "Android blocked background execution. Continue the download with the app open.")
                return Result.failure()
            }
            manager.markWaiting(key, token, "Waiting for Android to allow background downloading")
            return Result.retry()
        }
    }
}

internal enum class DownloadRunResult { COMPLETE, RETRY, FAILED }
