package com.example.ui.screens

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.download.*
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadEntity
import com.example.ui.glass.GlassHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Checks are local reads; a repair goes through the same admitted download path as a new transfer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadStorageScreen(downloads: List<DownloadEntity>, profileId: String,
    onBack: () -> Unit, onDelete: (String) -> Unit,
    onRepair: ((DownloadEntity) -> Unit)? = null) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val manager = remember(application) { NetflixDownloadManager.getInstance(application) }
    val health by manager.downloadHealth.collectAsState()
    val tasks by manager.downloadTasks.collectAsState()
    val progress by remember(profileId) { AppDatabase.getInstance(application).netflixDao().getAllProgress(profileId) }.collectAsState(initial = emptyList())
    val design = com.example.ui.theme.LocalPlatformDesign.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val snapshot by produceState<DownloadStorageSnapshot?>(null, downloads, progress, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { DownloadStorage.snapshot(File(application.filesDir, "downloads"), downloads, progress) }.getOrNull() }
    }
    var selected by remember(profileId) { mutableStateOf(emptySet<String>()) }
    var confirm by remember { mutableStateOf(false) }
    var checkingAll by remember { mutableStateOf(false) }
    LaunchedEffect(downloads) { selected = selected.intersect(downloads.map { it.downloadKey }.toSet()) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(design.background).statusBarsPadding().navigationBarsPadding().testTag("download_storage_screen")) {
        GlassHeader("Download storage", onBack) {
            TextButton(onClick = { refresh++ }) { Text("Refresh", color = Color.White) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item("summary") {
                Surface(color = design.surfaceColor(Color(0xFF202020)), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(snapshot?.let { "${DownloadStorage.bytesLabel(it.freeBytes)} free on device" } ?: "Reading device storage…", color = Color.White, fontWeight = FontWeight.Bold)
                        snapshot?.let { state ->
                            Text("This profile: ${DownloadStorage.bytesLabel(state.profileBytes)}", color = Color.LightGray)
                            Text("All app downloads: ${DownloadStorage.bytesLabel(state.allDownloadBytes)}", color = Color.LightGray, fontSize = 13.sp)
                        }
                        Text("Sizes include saved media, subtitles and verification data. Files shared by profiles stay on the device until no profile uses them.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
            item("actions") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = {
                        if (!checkingAll) scope.launch {
                            checkingAll = true
                            try { downloads.filter { it.profileId == profileId && it.isComplete }.forEach { manager.inspectDownload(profileId, it.downloadKey) }; refresh++ }
                            finally { checkingAll = false }
                        }
                    }, enabled = !checkingAll && downloads.isNotEmpty(), modifier = Modifier.testTag("downloads_verify_all")) {
                        Icon(Icons.Default.CheckCircleOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (checkingAll) "Checking…" else "Check downloads")
                    }
                    OutlinedButton(onClick = { selected = snapshot?.entries.orEmpty().filter { it.watched }.map { it.download.downloadKey }.toSet() }, enabled = snapshot?.entries.orEmpty().any { it.watched }, modifier = Modifier.testTag("downloads_select_watched")) { Text("Select watched") }
                }
            }
            if (downloads.isEmpty()) item("empty") { Text("Your downloaded movies and episodes will appear here.", color = Color.LightGray, modifier = Modifier.padding(vertical = 32.dp)) }
            items(snapshot?.entries.orEmpty(), key = { it.download.downloadKey }) { entry ->
                val download = entry.download
                val key = download.downloadKey
                val inspection = health[key]
                val active = tasks[key]?.status in setOf(DownloadTaskStatus.QUEUED, DownloadTaskStatus.PREPARING, DownloadTaskStatus.DOWNLOADING, DownloadTaskStatus.PAUSED)
                Surface(color = design.surfaceColor(Color(0xFF1B1B1B)), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(key in selected, onCheckedChange = { checked -> selected = if (checked) selected + key else selected - key }, enabled = !active)
                            Column(Modifier.weight(1f)) {
                                Text(download.mediaTitle, color = Color.White, fontWeight = FontWeight.SemiBold)
                                download.episodeTitle?.let { Text(it, color = Color.LightGray, fontSize = 12.sp) }
                                Text("${DownloadStorage.bytesLabel(entry.bytes)}${if (entry.watched) " · Watched" else ""}", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                        val text = when {
                            active -> tasks[key]?.let { task -> if (task.errorMessage != null) task.errorMessage else if (task.status == DownloadTaskStatus.DOWNLOADING) "Repairing · ${(task.progress * 100).toInt()}%" else "Repair ${task.status.name.lowercase()}" }.orEmpty()
                            inspection?.health == DownloadHealth.CHECKING -> "Checking saved video…"
                            inspection?.health == DownloadHealth.HEALTHY -> "Verified · Ready to watch offline"
                            inspection?.health == DownloadHealth.DAMAGED -> "${inspection.damagedParts} missing or damaged part(s)"
                            inspection?.health == DownloadHealth.LEGACY -> "Saved before verification was added. Download again to create a verified copy."
                            inspection?.health == DownloadHealth.UNAVAILABLE -> "Could not check this file. Refresh and try again."
                            else -> "Not checked yet"
                        }
                        Text(text, color = if (inspection?.health == DownloadHealth.DAMAGED) design.accent else Color.LightGray, fontSize = 12.sp, modifier = Modifier.testTag("download_health_$key"))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { scope.launch { manager.inspectDownload(profileId, key); refresh++ } }, enabled = !active && inspection?.health != DownloadHealth.CHECKING, modifier = Modifier.testTag("download_check_$key")) { Text("Check") }
                            TextButton(onClick = { onRepair?.invoke(download) }, enabled = !active && onRepair != null && inspection?.health in setOf(DownloadHealth.DAMAGED, DownloadHealth.LEGACY), modifier = Modifier.testTag("download_repair_$key")) { Text(if (inspection?.health == DownloadHealth.LEGACY) "Download again" else "Repair") }
                        }
                    }
                }
            }
        }
        if (selected.isNotEmpty()) Button(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("storage_delete_selected"), colors = ButtonDefaults.buttonColors(containerColor = design.accent, contentColor = design.onAccent)) {
            Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("Delete selected (${selected.size})")
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Delete selected downloads?") }, text = {
        Text("Remove ${selected.size} saved title(s) from this profile? Download them again to watch offline. Shared files used by another profile will be kept.")
    }, confirmButton = { TextButton(onClick = { selected.forEach(onDelete); selected = emptySet(); confirm = false; refresh++ }, modifier = Modifier.testTag("storage_confirm_delete")) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
