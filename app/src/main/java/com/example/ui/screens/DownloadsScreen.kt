package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.CatalogData
import com.example.data.download.DownloadTaskInfo
import com.example.data.download.DownloadTaskStatus
import com.example.data.local.DownloadEntity
import com.example.data.model.CastDevice
import com.example.data.model.MediaItem
import com.example.data.model.Episode
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixBorderGray
import com.example.ui.theme.NetflixCardBg
import com.example.ui.theme.NetflixDarkGray
import com.example.ui.theme.NetflixGreen
import com.example.ui.theme.NetflixRed

@Composable
fun DownloadsScreen(
    downloads: List<DownloadEntity>,
    downloadingProgress: Map<String, Float> = emptyMap(),
    downloadTasks: Map<String, DownloadTaskInfo> = emptyMap(),
    pausedDownloadKeys: Set<String> = emptySet(),
    smartDownloadsEnabled: Boolean,
    allocatedStorageGb: Float = 3.0f,
    connectedCastDevice: CastDevice?,
    onClose: () -> Unit,
    onPlayMedia: (MediaItem, Episode?) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onClearAllDownloads: () -> Unit,
    onToggleSmartDownloads: (Boolean) -> Unit,
    onUpdateAllocatedStorage: (Float) -> Unit = {},
    onSetUpDownloadsForYouWithAllocation: (Float) -> Unit = {},
    onSetUpDownloadsForYou: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCast: () -> Unit,
    onOpenMediaDetail: (MediaItem) -> Unit,
    onShowToast: (String) -> Unit,
    onPauseDownload: (String) -> Unit = {},
    onResumeDownload: (String) -> Unit = {},
    onCancelDownload: (String) -> Unit = {},
    userSubscription: com.example.data.model.UserSubscription = com.example.data.model.UserSubscription(),
    onOpenSubscription: () -> Unit = {},
    onOpenAuth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isEditMode by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showAllocationDialog by remember { mutableStateOf(false) }
    val selectedDownloadKeys = remember { mutableStateMapOf<String, Boolean>() }
    var showSmartDownloadsInfo by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val statFs = remember(downloads) {
        try {
            android.os.StatFs(context.filesDir.absolutePath)
        } catch (e: Exception) {
            null
        }
    }
    val totalDeviceBytes = statFs?.totalBytes ?: (64L * 1024 * 1024 * 1024)
    val freeDeviceBytes = statFs?.availableBytes ?: (38L * 1024 * 1024 * 1024)
    val totalStorageUsedMb = downloads.sumOf { it.fileSizeMb.toLong() }
    val netflixBytes = totalStorageUsedMb * 1024 * 1024
    val otherBytes = (totalDeviceBytes - freeDeviceBytes - netflixBytes).coerceAtLeast(0)

    val netflixStr = if (totalStorageUsedMb >= 1000) {
        String.format(java.util.Locale.US, "%.1f GB", totalStorageUsedMb / 1024f)
    } else {
        "$totalStorageUsedMb MB"
    }
    val otherGbStr = String.format(java.util.Locale.US, "%.1f GB", otherBytes / (1024f * 1024f * 1024f))
    val freeGbStr = String.format(java.util.Locale.US, "%.1f GB", freeDeviceBytes / (1024f * 1024f * 1024f))

    val netflixUsedFraction = (netflixBytes.toFloat() / totalDeviceBytes.toFloat()).coerceIn(0.005f, 0.95f)
    val otherUsedFraction = (otherBytes.toFloat() / totalDeviceBytes.toFloat()).coerceIn(0.005f, 0.95f)

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "Delete All Downloads?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will remove all downloaded movies and episodes from this device.",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            containerColor = NetflixDarkGray,
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearAllDownloads()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed)
                ) {
                    Text("Delete All", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
        ) {
            // ====================================================
            // 1. TOP APP BAR: [<- Downloads] ... [Edit] [Cast] [Search]
            // ====================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("downloads_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "Downloads",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("downloads_screen_title")
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (downloads.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                isEditMode = !isEditMode
                                if (!isEditMode) {
                                    selectedDownloadKeys.clear()
                                }
                            },
                            modifier = Modifier.testTag("downloads_edit_toggle_btn")
                        ) {
                            Text(
                                text = if (isEditMode) "Done" else "Edit",
                                color = if (isEditMode) NetflixRed else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenCast,
                        modifier = Modifier.testTag("downloads_cast_btn")
                    ) {
                        Icon(
                            imageVector = if (connectedCastDevice != null) Icons.Default.CastConnected else Icons.Default.Cast,
                            contentDescription = "Cast",
                            tint = if (connectedCastDevice != null) NetflixRed else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier.testTag("downloads_search_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // ====================================================
            // 2. MAIN SCROLLABLE BODY
            // ====================================================
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
            ) {
                // Smart Downloads Header Card
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NetflixDarkGray)
                            .border(1.dp, NetflixBorderGray, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0071EB).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_netflix_download_custom),
                                            contentDescription = null,
                                            tint = Color(0xFF0071EB),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Smart Downloads",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        if (smartDownloadsEnabled) NetflixGreen.copy(alpha = 0.2f)
                                                        else Color.White.copy(alpha = 0.1f)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (smartDownloadsEnabled) "ON" else "OFF",
                                                    color = if (smartDownloadsEnabled) NetflixGreen else Color.White.copy(alpha = 0.6f),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                            text = "Auto-downloads curated titles within your allocated space.",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Switch(
                                    checked = smartDownloadsEnabled,
                                    onCheckedChange = { onToggleSmartDownloads(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF0071EB),
                                        uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                                        uncheckedTrackColor = Color.Black.copy(alpha = 0.5f)
                                    )
                                )
                            }

                            if (smartDownloadsEnabled) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color.White.copy(alpha = 0.1f))
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Allocated Download Space",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${String.format(java.util.Locale.US, "%.1f", allocatedStorageGb)} GB",
                                        color = Color(0xFF0071EB),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Slider(
                                    value = allocatedStorageGb,
                                    onValueChange = { onUpdateAllocatedStorage(it) },
                                    valueRange = 0.5f..10.0f,
                                    steps = 18,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF0071EB),
                                        activeTrackColor = Color(0xFF0071EB),
                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(1.0f, 3.0f, 5.0f, 10.0f).forEach { gb ->
                                        val isSelected = kotlin.math.abs(allocatedStorageGb - gb) < 0.2f
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isSelected) Color(0xFF0071EB) else Color.White.copy(alpha = 0.08f)
                                                )
                                                .clickable { onUpdateAllocatedStorage(gb) }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${gb.toInt()} GB",
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Storage Meter Bar
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NetflixDarkGray)
                            .border(1.dp, NetflixBorderGray, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Device Storage",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "$netflixStr used by Netflix",
                                    color = Color(0xFF0071EB),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Storage Bar Breakdown
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(netflixUsedFraction)
                                        .fillMaxSize()
                                        .background(Color(0xFF0071EB))
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(otherUsedFraction)
                                        .fillMaxSize()
                                        .background(Color.White.copy(alpha = 0.45f))
                                )
                                Box(
                                    modifier = Modifier
                                        .weight((1f - netflixUsedFraction - otherUsedFraction).coerceAtLeast(0.05f))
                                        .fillMaxSize()
                                        .background(Color.Transparent)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0071EB))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Netflix ($netflixStr)",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.45f))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Other Apps ($otherGbStr)",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.15f))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Free ($freeGbStr)",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Batch Actions when in Edit Mode
                if (isEditMode && downloads.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedDownloadKeys.filter { it.value }.size} of ${downloads.size} selected",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        val allSelected = selectedDownloadKeys.size == downloads.size && selectedDownloadKeys.values.all { it }
                                        if (allSelected) {
                                            selectedDownloadKeys.clear()
                                        } else {
                                            downloads.forEach { selectedDownloadKeys[it.downloadKey] = true }
                                        }
                                    }
                                ) {
                                    Text(
                                        text = if (selectedDownloadKeys.size == downloads.size && selectedDownloadKeys.values.all { it }) "Deselect All" else "Select All",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }

                                Button(
                                    onClick = {
                                        val toDelete = selectedDownloadKeys.filter { it.value }.keys
                                        toDelete.forEach { onDeleteDownload(it) }
                                        selectedDownloadKeys.clear()
                                        if (downloads.size <= toDelete.size) {
                                            isEditMode = false
                                        }
                                    },
                                    enabled = selectedDownloadKeys.any { it.value },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NetflixRed,
                                        disabledContainerColor = NetflixRed.copy(alpha = 0.3f)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete Selected", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Downloading In Progress Section
                if (downloadingProgress.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Downloads In Progress (${downloadingProgress.size})",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )

                            downloadingProgress.forEach { (key, progress) ->
                                val task = downloadTasks[key]
                                val isPaused = pausedDownloadKeys.contains(key) || task?.status == DownloadTaskStatus.PAUSED
                                val mediaId = key.substringBefore("_")
                                val matchedMedia = CatalogData.getById(mediaId)
                                val epId = if (key.contains("_")) key.substringAfter("_") else null
                                val matchedEpisode = matchedMedia?.episodes?.find { it.id == epId }
                                val displayTitle = if (matchedEpisode != null && matchedMedia != null) {
                                    "${matchedMedia.title} (S1:E${matchedEpisode.episodeNumber} ${matchedEpisode.title})"
                                } else matchedMedia?.title ?: "Media Title"
                                val pct = (progress * 100).toInt()

                                val statusSubtext = when {
                                    isPaused -> "Paused • $pct%"
                                    task?.status == DownloadTaskStatus.PREPARING -> "Resolving stream... • $pct%"
                                    task != null && task.speedBytesPerSec > 0 -> {
                                        val speedStr = task.speedFormatted
                                        val etaStr = task.etaFormatted
                                        if (etaStr.isNotEmpty()) "Downloading • $pct% ($speedStr • $etaStr)" else "Downloading • $pct% ($speedStr)"
                                    }
                                    else -> "Downloading... $pct%"
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NetflixDarkGray)
                                        .border(1.dp, NetflixBorderGray, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = displayTitle,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = statusSubtext,
                                                    color = if (isPaused) Color(0xFFF59E0B) else Color(0xFF0071EB),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Pause / Resume Button
                                                IconButton(
                                                    onClick = {
                                                        if (isPaused) {
                                                            onResumeDownload(key)
                                                        } else {
                                                            onPauseDownload(key)
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White.copy(alpha = 0.12f))
                                                        .testTag("download_pause_resume_btn_$key")
                                                ) {
                                                    Icon(
                                                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                                        contentDescription = if (isPaused) "Resume" else "Pause",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                // Cancel Button
                                                IconButton(
                                                    onClick = { onCancelDownload(key) },
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White.copy(alpha = 0.12f))
                                                        .testTag("download_cancel_btn_$key")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Cancel Download",
                                                        tint = NetflixRed,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = if (isPaused) Color(0xFFF59E0B) else Color(0xFF0071EB),
                                            trackColor = Color.White.copy(alpha = 0.2f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ====================================================
                // 3. DOWNLOADS CONTENT LIST OR EMPTY STATE
                // ====================================================
                if (downloads.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = if (userSubscription.isGuest) "Never be without Netflix" else "Turn on Downloads for You",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("downloads_for_you_title")
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (userSubscription.isGuest)
                                    "Sign in to download movies and TV shows so you'll always have something to watch on the go."
                                else
                                    "We'll download movies and shows just for you, so you'll always have something to watch.",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.testTag("downloads_for_you_subtitle")
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            // Fanned posters container (centered)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Background circle
                                Box(
                                    modifier = Modifier
                                        .size(210.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1F1F1F))
                                )

                                val dynamicPosters = CatalogData.allMedia
                                    .filter { !it.posterUrl.isNullOrBlank() }
                                    .take(3)
                                    .map { it.posterUrl!! }

                                val fallbackPosters = listOf(
                                    "https://image.tmdb.org/t/p/w500/czgLh7rXNswNfWj0E2w3Kz2eePN.jpg", // Wednesday
                                    "https://image.tmdb.org/t/p/w500/uu4TgyyW259aOZHN0Ew4TEfjnUG.jpg", // Squid Game
                                    "https://image.tmdb.org/t/p/w500/reEMJA1uzsc0t396S8v97YvGOcl.jpg"  // Money Heist
                                )

                                val postersToShow = if (dynamicPosters.size >= 3) dynamicPosters else fallbackPosters

                                if (postersToShow.size >= 3) {
                                    // Left poster (tilted)
                                    AsyncImage(
                                        model = postersToShow[0],
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(95.dp)
                                            .height(142.dp)
                                            .graphicsLayer {
                                                rotationZ = -14f
                                                translationX = -130f
                                                translationY = 15f
                                            }
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    )

                                    // Right poster (tilted)
                                    AsyncImage(
                                        model = postersToShow[2],
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(95.dp)
                                            .height(142.dp)
                                            .graphicsLayer {
                                                rotationZ = 14f
                                                translationX = 130f
                                                translationY = 15f
                                            }
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    )

                                    // Center poster (straight, on top)
                                    AsyncImage(
                                        model = postersToShow[1],
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(110.dp)
                                            .height(165.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            // Action button
                            if (userSubscription.isGuest) {
                                Button(
                                    onClick = onOpenAuth,
                                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("downloads_guest_signin_btn")
                                ) {
                                    Text("Sign In to Download", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            } else {
                                Button(
                                    onClick = { showAllocationDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0071EB)),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("downloads_setup_btn")
                                ) {
                                    Text("Set Up Storage Space", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Secondary button: Find Something to Download
                            OutlinedButton(
                                onClick = onOpenSearch,
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("downloads_find_more_empty_btn")
                            ) {
                                Text(
                                    text = "Find Something to Download",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    // Header for Downloaded Titles
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Downloaded Titles (${downloads.size})",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )

                            if (!isEditMode) {
                                Text(
                                    text = "Clear All",
                                    color = NetflixRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { showClearDialog = true }
                                        .padding(4.dp)
                                        .testTag("downloads_clear_all_btn")
                                )
                            }
                        }
                    }

                    // Download Items
                    items(downloads, key = { it.downloadKey }) { download ->
                        val matchedMedia = CatalogData.getById(download.mediaId)
                        var showItemMenu by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(NetflixDarkGray)
                                .border(1.dp, NetflixBorderGray, RoundedCornerShape(12.dp))
                                .clickable {
                                    if (isEditMode) {
                                        val cur = selectedDownloadKeys[download.downloadKey] ?: false
                                        selectedDownloadKeys[download.downloadKey] = !cur
                                    } else {
                                        val mediaToPlay = matchedMedia ?: MediaItem(
                                            id = download.mediaId,
                                            title = download.mediaTitle,
                                            type = if (download.episodeTitle != null) com.example.data.model.MediaType.TV_SHOW else com.example.data.model.MediaType.MOVIE,
                                            description = download.episodeTitle ?: download.mediaTitle,
                                            tagline = download.mediaTitle,
                                            matchPercentage = 98,
                                            maturityRating = "16+",
                                            releaseYear = 2026,
                                            durationOrSeasons = if (download.episodeTitle != null) "1 Season" else "1h 45m",
                                            genres = listOf("Action", "Drama"),
                                            cast = listOf("Netflix"),
                                            director = "Netflix Original"
                                        )
                                        val matchedEpisode = mediaToPlay.episodes.find { ep ->
                                            download.downloadKey == "${mediaToPlay.id}_${ep.id}"
                                        } ?: if (download.episodeTitle != null) {
                                            Episode(
                                                id = download.downloadKey.substringAfter("${download.mediaId}_", "ep_1"),
                                                episodeNumber = 1,
                                                title = download.episodeTitle,
                                                durationMinutes = 45,
                                                description = download.episodeTitle
                                            )
                                        } else null

                                        if (!userSubscription.isActive || userSubscription.maxDownloads <= 0) {
                                            if (userSubscription.isGuest) {
                                                onShowToast("Sign in to stream and download titles.")
                                                onOpenAuth()
                                            } else {
                                                onShowToast("Offline playback requires an active plan with downloads support. Upgrade or renew membership.")
                                                onOpenSubscription()
                                            }
                                            return@clickable
                                        }
                                        onPlayMedia(mediaToPlay, matchedEpisode)
                                    }
                                }
                                .padding(12.dp)
                                .testTag("download_row_${download.downloadKey}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (isEditMode) {
                                    Checkbox(
                                        checked = selectedDownloadKeys[download.downloadKey] ?: false,
                                        onCheckedChange = { checked ->
                                            selectedDownloadKeys[download.downloadKey] = checked
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = NetflixRed,
                                            uncheckedColor = Color.White.copy(alpha = 0.6f),
                                            checkmarkColor = Color.White
                                        ),
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }

                                // Thumbnail with play button overlay
                                Box(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .height(70.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF222222)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (matchedMedia?.bannerDrawableRes != null) {
                                        Image(
                                            painter = painterResource(id = matchedMedia.bannerDrawableRes),
                                            contentDescription = download.mediaTitle,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (!matchedMedia?.backdropUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = matchedMedia?.backdropUrl,
                                            contentDescription = download.mediaTitle,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (!matchedMedia?.posterUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = matchedMedia?.posterUrl,
                                            contentDescription = download.mediaTitle,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    // Play icon overlay
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.65f))
                                            .border(1.dp, Color.White, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Title & metadata info
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = download.mediaTitle,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (!download.episodeTitle.isNullOrEmpty()) {
                                        Text(
                                            text = download.episodeTitle,
                                            color = Color.White.copy(alpha = 0.75f),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color(0xFF0071EB).copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "HD",
                                                color = Color(0xFF0071EB),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Text(
                                            text = "${download.fileSizeMb} MB",
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 11.sp
                                        )

                                        Text(
                                            text = "• Ready offline",
                                            color = NetflixGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // Item action icon
                                if (!isEditMode) {
                                    Box {
                                        IconButton(
                                            onClick = { showItemMenu = true },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Options",
                                                tint = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showItemMenu,
                                            onDismissRequest = { showItemMenu = false },
                                            modifier = Modifier
                                                .background(NetflixDarkGray)
                                                .border(1.dp, NetflixBorderGray, RoundedCornerShape(8.dp))
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Play Now", color = Color.White) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color.White
                                                    )
                                                },
                                                onClick = {
                                                    showItemMenu = false
                                                    if (matchedMedia != null) {
                                                        if (!userSubscription.isActive || userSubscription.maxDownloads <= 0) {
                                                            onShowToast("Offline playback requires an active plan with downloads support. Upgrade or renew membership.")
                                                            onOpenSubscription()
                                                            return@DropdownMenuItem
                                                        }
                                                        val matchedEpisode = matchedMedia.episodes.find { ep ->
                                                            download.downloadKey == "${matchedMedia.id}_${ep.id}"
                                                        }
                                                        onPlayMedia(matchedMedia, matchedEpisode)
                                                    }
                                                }
                                            )

                                            if (matchedMedia != null) {
                                                DropdownMenuItem(
                                                    text = { Text("Episodes & Info", color = Color.White) },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Default.Info,
                                                            contentDescription = null,
                                                            tint = Color.White
                                                        )
                                                    },
                                                    onClick = {
                                                        showItemMenu = false
                                                        onOpenMediaDetail(matchedMedia)
                                                    }
                                                )
                                            }

                                            DropdownMenuItem(
                                                text = { Text("Delete Download", color = NetflixRed) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = null,
                                                        tint = NetflixRed
                                                    )
                                                },
                                                onClick = {
                                                    showItemMenu = false
                                                    onDeleteDownload(download.downloadKey)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Find More Downloads Button
                    item {
                        OutlinedButton(
                            onClick = onOpenSearch,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_netflix_download_custom),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Find More to Download",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (showAllocationDialog) {
            var selectedAllocatedGb by remember { mutableStateOf(allocatedStorageGb) }
            AlertDialog(
                onDismissRequest = { showAllocationDialog = false },
                title = {
                    Text(
                        text = "Set Up Downloads for You",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Choose how much storage space to manually allocate for automatic offline downloads. Smart Downloads will curatedly pick top movies and TV show seasons for you without exceeding this limit.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Allocated Storage Space",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", selectedAllocatedGb)} GB",
                                color = Color(0xFF0071EB),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Slider(
                            value = selectedAllocatedGb,
                            onValueChange = { selectedAllocatedGb = it },
                            valueRange = 0.5f..10.0f,
                            steps = 18,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF0071EB),
                                activeTrackColor = Color(0xFF0071EB),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1.0f, 3.0f, 5.0f, 10.0f).forEach { gb ->
                                val isSelected = kotlin.math.abs(selectedAllocatedGb - gb) < 0.2f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF0071EB) else Color.White.copy(alpha = 0.1f))
                                        .clickable { selectedAllocatedGb = gb }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${gb.toInt()} GB",
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showAllocationDialog = false
                            onSetUpDownloadsForYouWithAllocation(selectedAllocatedGb)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0071EB))
                    ) {
                        Text("Turn On & Allocate Space", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAllocationDialog = false }) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                },
                containerColor = NetflixDarkGray
            )
        }
    }
}
