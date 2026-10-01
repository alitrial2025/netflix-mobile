package com.example.data.download

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class VideoDownloadControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        if (intent.action == PAUSE) NetflixDownloadManager.getInstance(context.applicationContext as Application).pauseDownload(key)
    }
    companion object {
        private const val PAUSE = "com.netflixpro.apk.PAUSE_DOWNLOAD"
        internal fun pauseIntent(context: Context, key: String): PendingIntent = PendingIntent.getBroadcast(context,
            downloadWorkName(key).hashCode(), Intent(context, VideoDownloadControlReceiver::class.java).setAction(PAUSE).putExtra("key", key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
