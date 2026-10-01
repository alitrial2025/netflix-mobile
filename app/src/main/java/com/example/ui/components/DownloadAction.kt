package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.download.DownloadTaskInfo
import com.example.data.download.DownloadTaskStatus
import com.example.ui.theme.NetflixRed

/** One download state/action presentation shared by Details and Downloads. */
@Composable
fun DownloadAction(
    downloadKey: String,
    progress: Float?,
    paused: Boolean,
    completed: Boolean,
    task: DownloadTaskInfo? = null,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fraction = (task?.progress ?: progress ?: 0f).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val failed = task?.status == DownloadTaskStatus.ERROR
    val isPaused = paused || task?.status == DownloadTaskStatus.PAUSED
    val active = progress != null || task?.status in listOf(DownloadTaskStatus.QUEUED, DownloadTaskStatus.PREPARING, DownloadTaskStatus.DOWNLOADING, DownloadTaskStatus.PAUSED, DownloadTaskStatus.ERROR)
    if (!active || completed) {
        Button(onClick = if (completed) onCompleted else onStart,
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626), contentColor = Color.White),
            modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("download_action_$downloadKey")) {
            if (completed) Icon(Icons.Default.Check, null, Modifier.size(20.dp))
            else Icon(painterResource(R.drawable.ic_outline_download), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (completed) "Downloaded · View" else "Download", fontWeight = FontWeight.SemiBold)
        }
    } else {
        Column(modifier.fillMaxWidth().background(Color(0xFF202020), RoundedCornerShape(8.dp)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(when {
                        failed -> "Download interrupted"
                        isPaused -> "Paused · ${(fraction * 100).toInt()}%"
                        task?.status == DownloadTaskStatus.QUEUED -> "Queued"
                        task?.status == DownloadTaskStatus.PREPARING -> "Preparing download"
                        else -> "Downloading · ${(fraction * 100).toInt()}%"
                    }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (failed || !task?.errorMessage.isNullOrBlank() || task?.speedBytesPerSec?.let { it > 0 } == true) Text(
                        task?.errorMessage?.takeIf { it.isNotBlank() } ?: if (failed) "Continue to resume your saved progress." else listOfNotNull(task?.speedFormatted, task?.etaFormatted?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        color = Color.White.copy(alpha = .6f), fontSize = 11.sp)
                }
                IconButton(onClick = if (failed || isPaused) onResume else onPause,
                    modifier = Modifier.testTag("download_pause_resume_btn_$downloadKey")) {
                    Icon(if (failed || isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        if (failed) "Retry download" else if (isPaused) "Resume download" else "Pause download", tint = Color.White)
                }
                IconButton(onClick = onCancel, modifier = Modifier.testTag("download_cancel_btn_$downloadKey")) {
                    Icon(Icons.Default.Close, "Cancel download", tint = Color.White.copy(alpha = .7f))
                }
            }
            LinearProgressIndicator(progress = { fraction }, color = if (isPaused) Color.LightGray else NetflixRed,
                trackColor = Color.White.copy(alpha = .12f), modifier = Modifier.fillMaxWidth().height(3.dp))
        }
    }
}
