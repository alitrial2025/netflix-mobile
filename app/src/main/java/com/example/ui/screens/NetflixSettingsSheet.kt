package com.example.ui.screens

import com.example.ui.theme.netflixProSwitchColors
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CellWifi
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HighQuality
import com.example.ui.theme.NetflixRed
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.UserProfile
import com.example.data.model.UserSubscription
import com.example.ui.components.NetflixSpinner
import com.example.ui.components.ProfileAvatar
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixBlue
import com.example.ui.theme.NetflixBorderGray
import com.example.ui.theme.NetflixDarkGray
import com.example.ui.theme.NetflixGreen

@Composable
fun NetflixSettingsSheet(
    activeProfile: UserProfile,
    smartDownloadsEnabled: Boolean,
    wifiOnlyEnabled: Boolean,
    highQualityEnabled: Boolean,
    autoPlayNextEnabled: Boolean,
    autoPlayPreviewsEnabled: Boolean,
    spatialAudioEnabled: Boolean,
    cellularDataOption: String,
    diagnosticRunning: Boolean,
    diagnosticResult: String?,
    totalStorageUsedMb: Int,
    onClose: () -> Unit,
    onToggleSmartDownloads: (Boolean) -> Unit,
    onToggleWifiOnly: (Boolean) -> Unit,
    onToggleHighQuality: (Boolean) -> Unit,
    onToggleAutoPlayNext: (Boolean) -> Unit,
    onToggleAutoPlayPreviews: (Boolean) -> Unit,
    onToggleSpatialAudio: (Boolean) -> Unit,
    onSetCellularData: (String) -> Unit,
    onRunDiagnosticTest: () -> Unit,
    onClearAllDownloads: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenTvPair: () -> Unit = {},
    currentEmail: String? = null,
    userSubscription: UserSubscription = UserSubscription(),
    onOpenAuth: () -> Unit = {},
    onOpenSubscription: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    NetflixSettingsScreen(
        activeProfile = activeProfile,
        smartDownloadsEnabled = smartDownloadsEnabled,
        wifiOnlyEnabled = wifiOnlyEnabled,
        highQualityEnabled = highQualityEnabled,
        autoPlayNextEnabled = autoPlayNextEnabled,
        autoPlayPreviewsEnabled = autoPlayPreviewsEnabled,
        spatialAudioEnabled = spatialAudioEnabled,
        cellularDataOption = cellularDataOption,
        diagnosticRunning = diagnosticRunning,
        diagnosticResult = diagnosticResult,
        totalStorageUsedMb = totalStorageUsedMb,
        onClose = onClose,
        onToggleSmartDownloads = onToggleSmartDownloads,
        onToggleWifiOnly = onToggleWifiOnly,
        onToggleHighQuality = onToggleHighQuality,
        onToggleAutoPlayNext = onToggleAutoPlayNext,
        onToggleAutoPlayPreviews = onToggleAutoPlayPreviews,
        onToggleSpatialAudio = onToggleSpatialAudio,
        onSetCellularData = onSetCellularData,
        onRunDiagnosticTest = onRunDiagnosticTest,
        onClearAllDownloads = onClearAllDownloads,
        onSwitchProfile = onSwitchProfile,
        onOpenTvPair = onOpenTvPair,
        currentEmail = currentEmail,
        userSubscription = userSubscription,
        onOpenAuth = onOpenAuth,
        onOpenSubscription = onOpenSubscription,
        modifier = modifier
    )
}

@Composable
fun NetflixSettingsScreen(
    activeProfile: UserProfile,
    smartDownloadsEnabled: Boolean,
    wifiOnlyEnabled: Boolean,
    highQualityEnabled: Boolean,
    autoPlayNextEnabled: Boolean,
    autoPlayPreviewsEnabled: Boolean,
    spatialAudioEnabled: Boolean,
    cellularDataOption: String,
    diagnosticRunning: Boolean,
    diagnosticResult: String?,
    totalStorageUsedMb: Int,
    onClose: () -> Unit,
    onToggleSmartDownloads: (Boolean) -> Unit,
    onToggleWifiOnly: (Boolean) -> Unit,
    onToggleHighQuality: (Boolean) -> Unit,
    onToggleAutoPlayNext: (Boolean) -> Unit,
    onToggleAutoPlayPreviews: (Boolean) -> Unit,
    onToggleSpatialAudio: (Boolean) -> Unit,
    onSetCellularData: (String) -> Unit,
    onRunDiagnosticTest: () -> Unit,
    onClearAllDownloads: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenTvPair: () -> Unit = {},
    currentEmail: String? = null,
    userSubscription: UserSubscription = UserSubscription(),
    onOpenAuth: () -> Unit = {},
    onOpenSubscription: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("netflix_settings_screen")
    ) {
        // Full Screen Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("close_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "App Settings",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = NetflixBlue,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        HorizontalDivider(color = NetflixBorderGray)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Account Card with Blue Accent
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(activeProfile.avatarColorHex).copy(alpha = 0.25f),
                                Color(0xFF1E1E1E)
                            )
                        )
                    )
                    .border(1.dp, NetflixBorderGray, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        ProfileAvatar(profile = activeProfile, size = 48.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = activeProfile.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentEmail ?: (if (userSubscription.isGuest) "Guest (Not signed in)" else "Member"),
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp
                            )
                            Text(
                                text = if (userSubscription.isGuest) "Guest Mode • Preview Only" else if (userSubscription.isActive) "${userSubscription.planName} • Active" else "${userSubscription.planName} • Unpaid",
                                color = if (userSubscription.isGuest) NetflixRed else if (userSubscription.isActive) NetflixGreen else Color(0xFFF59E0B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                onClose()
                                onOpenAuth()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NetflixBlue),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(if (currentEmail != null) "Account" else "Sign In", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = {
                                onClose()
                                onSwitchProfile()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Switch", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }

            // Section: Membership & Plans (Lipwa Link Payhero)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "MEMBERSHIP & BILLING",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, NetflixBorderGray, RoundedCornerShape(12.dp))
                        .clickable {
                            onClose()
                            onOpenSubscription()
                        }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(NetflixRed.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CreditCard, contentDescription = null, tint = NetflixRed, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Membership & Plans", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (userSubscription.isGuest) "Free guest preview • Streaming locked" else "${userSubscription.planName} Plan • Pay with M-Pesa",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Button(
                            onClick = {
                                onClose()
                                onOpenSubscription()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(if (userSubscription.isGuest) "Unlock" else "Manage", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Section: TV Sign In & Pairing (Blue Accent Theme)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TV & EXTERNAL SCREENS",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    NetflixBlue.copy(alpha = 0.18f),
                                    Color(0xFF1F1F1F)
                                )
                            )
                        )
                        .border(1.dp, NetflixBlue.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clickable {
                            onClose()
                            onOpenTvPair()
                        }
                        .padding(14.dp)
                        .testTag("pair_tv_settings_btn")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(NetflixBlue.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = "Watch on TV",
                                    tint = NetflixBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "Sign In on TV",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Use the same account email and password on TV",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onClose()
                                onOpenTvPair()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NetflixBlue),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Scan", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Section: Video Playback & Quality
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "VIDEO PLAYBACK & AUDIO",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, NetflixBorderGray, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    SettingsToggleRow(
                        icon = Icons.Default.HighQuality,
                        title = "4K Ultra HD & HDR",
                        subtitle = "Stream in crystal-clear 4K resolution with Dolby Vision",
                        checked = highQualityEnabled,
                        onCheckedChange = onToggleHighQuality
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    SettingsToggleRow(
                        icon = Icons.Default.SurroundSound,
                        title = "Spatial Audio & Dolby Atmos",
                        subtitle = "Immersive 3D audio experience on headphones and speakers",
                        checked = spatialAudioEnabled,
                        onCheckedChange = onToggleSpatialAudio
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    SettingsToggleRow(
                        icon = Icons.Default.PlayCircle,
                        title = "Auto-Play Next Episode",
                        subtitle = "Automatically load the next episode in a series",
                        checked = autoPlayNextEnabled,
                        onCheckedChange = onToggleAutoPlayNext
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    SettingsToggleRow(
                        icon = Icons.Default.CellWifi,
                        title = "Auto-Play Previews",
                        subtitle = "Play video previews while browsing titles",
                        checked = autoPlayPreviewsEnabled,
                        onCheckedChange = onToggleAutoPlayPreviews
                    )
                }
            }

            // Section: Downloads & Storage (Blue Accent Theme)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "DOWNLOADS & STORAGE",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, NetflixBorderGray, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsToggleRowPainter(
                        painter = painterResource(id = R.drawable.ic_netflix_download_custom),
                        title = "Smart Downloads",
                        subtitle = "Auto-download next episodes and delete watched ones",
                        checked = smartDownloadsEnabled,
                        onCheckedChange = onToggleSmartDownloads
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    SettingsToggleRow(
                        icon = Icons.Default.Wifi,
                        title = "Wi-Fi Only Downloads",
                        subtitle = "Only download media when connected to Wi-Fi",
                        checked = wifiOnlyEnabled,
                        onCheckedChange = onToggleWifiOnly
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    // Storage breakdown with Blue progress bar
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Device Storage Used",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${totalStorageUsedMb} MB of 64 GB",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (totalStorageUsedMb / 5000f).coerceIn(0.06f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = NetflixBlue,
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                    }

                    if (totalStorageUsedMb > 0) {
                        Button(
                            onClick = onClearAllDownloads,
                            colors = ButtonDefaults.buttonColors(containerColor = NetflixBlue.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = NetflixBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete All Downloads", color = NetflixBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Section: Diagnostics & Network
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "DIAGNOSTICS & SYSTEM",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, NetflixBorderGray, RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Network Speed & Health Check",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Verify connection to streaming CDN servers",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Button(
                            onClick = onRunDiagnosticTest,
                            enabled = !diagnosticRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = NetflixBlue),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            if (diagnosticRunning) {
                                NetflixSpinner(
                                    size = 14.dp,
                                    color = Color.White
                                )
                            } else {
                                Text("Test", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    if (diagnosticResult != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(NetflixBlue.copy(alpha = 0.15f))
                                .border(1.dp, NetflixBlue.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = diagnosticResult,
                                color = NetflixBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // App Version Footer
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "App Settings v2026.4.1",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
                Text(
                    text = "Blue & White Settings Layout",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsToggleRowPainter(
    painter: androidx.compose.ui.graphics.painter.Painter,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = NetflixBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = netflixProSwitchColors()
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = netflixProSwitchColors()
        )
    }
}
