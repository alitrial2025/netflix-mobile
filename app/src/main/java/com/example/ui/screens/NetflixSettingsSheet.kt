package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.CellularDataMode
import com.example.data.model.UserProfile
import com.example.data.model.UserSubscription
import com.example.ui.components.ProfileAvatar
import com.example.ui.theme.NetflixBlue
import com.example.ui.theme.netflixProSwitchColors

@Composable
fun NetflixSettingsSheet(
    activeProfile: UserProfile, smartDownloadsEnabled: Boolean, wifiOnlyEnabled: Boolean,
    highQualityEnabled: Boolean, autoPlayNextEnabled: Boolean, autoPlayPreviewsEnabled: Boolean,
    spatialAudioEnabled: Boolean, cellularDataOption: String, diagnosticRunning: Boolean,
    diagnosticResult: String?, totalStorageUsedMb: Int, onClose: () -> Unit,
    onToggleSmartDownloads: (Boolean) -> Unit, onToggleWifiOnly: (Boolean) -> Unit,
    onToggleHighQuality: (Boolean) -> Unit, onToggleAutoPlayNext: (Boolean) -> Unit,
    onToggleAutoPlayPreviews: (Boolean) -> Unit, onToggleSpatialAudio: (Boolean) -> Unit,
    onSetCellularData: (String) -> Unit, onRunDiagnosticTest: () -> Unit,
    onClearAllDownloads: () -> Unit, onSwitchProfile: () -> Unit, onOpenTvPair: () -> Unit = {},
    currentEmail: String? = null, userSubscription: UserSubscription = UserSubscription(),
    onOpenAuth: () -> Unit = {}, onOpenSubscription: () -> Unit = {}, onEditProfile: () -> Unit = {},
    onOpenSmartDownloads: () -> Unit = {}, onSignOut: () -> Unit = {}, onResetPassword: () -> Unit = {},
    hasActiveDownloads: Boolean = false, modifier: Modifier = Modifier
) = NetflixSettingsScreen(activeProfile, smartDownloadsEnabled, wifiOnlyEnabled, highQualityEnabled,
    autoPlayNextEnabled, autoPlayPreviewsEnabled, spatialAudioEnabled, cellularDataOption, diagnosticRunning,
    diagnosticResult, totalStorageUsedMb, onClose, onToggleSmartDownloads, onToggleWifiOnly, onToggleHighQuality,
    onToggleAutoPlayNext, onToggleAutoPlayPreviews, onToggleSpatialAudio, onSetCellularData, onRunDiagnosticTest,
    onClearAllDownloads, onSwitchProfile, onOpenTvPair, currentEmail, userSubscription, onOpenAuth,
    onOpenSubscription, onEditProfile, onOpenSmartDownloads, onSignOut, onResetPassword, hasActiveDownloads, modifier)

@Composable
fun NetflixSettingsScreen(
    activeProfile: UserProfile, smartDownloadsEnabled: Boolean, wifiOnlyEnabled: Boolean,
    highQualityEnabled: Boolean, autoPlayNextEnabled: Boolean, autoPlayPreviewsEnabled: Boolean,
    spatialAudioEnabled: Boolean, cellularDataOption: String, diagnosticRunning: Boolean,
    diagnosticResult: String?, totalStorageUsedMb: Int, onClose: () -> Unit,
    onToggleSmartDownloads: (Boolean) -> Unit, onToggleWifiOnly: (Boolean) -> Unit,
    onToggleHighQuality: (Boolean) -> Unit, onToggleAutoPlayNext: (Boolean) -> Unit,
    onToggleAutoPlayPreviews: (Boolean) -> Unit, onToggleSpatialAudio: (Boolean) -> Unit,
    onSetCellularData: (String) -> Unit, onRunDiagnosticTest: () -> Unit,
    onClearAllDownloads: () -> Unit, onSwitchProfile: () -> Unit, onOpenTvPair: () -> Unit = {},
    currentEmail: String? = null, userSubscription: UserSubscription = UserSubscription(),
    onOpenAuth: () -> Unit = {}, onOpenSubscription: () -> Unit = {}, onEditProfile: () -> Unit = {},
    onOpenSmartDownloads: () -> Unit = {}, onSignOut: () -> Unit = {}, onResetPassword: () -> Unit = {},
    hasActiveDownloads: Boolean = false, modifier: Modifier = Modifier
) {
    var dialog by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val storage = remember(totalStorageUsedMb) { android.os.StatFs(context.filesDir.absolutePath) }
    val spatialAllowed = userSubscription.isActive && userSubscription.planId == "plan_premium"
    fun navigate(action: () -> Unit) { dialog = null; onClose(); action() }

    if (dialog != null) AlertDialog(
        onDismissRequest = { dialog = null }, containerColor = Color(0xFF242424),
        titleContentColor = Color.White, textContentColor = Color.White,
        title = { Text(when (dialog) {
            "cellular" -> "Cellular Data Usage"; "quality" -> "Video Quality"
            "delete" -> "Delete all downloads?"; "signout" -> "Sign out?"; else -> "Account"
        }, fontWeight = FontWeight.Bold) },
        text = { when (dialog) {
            "cellular" -> Column { CellularDataMode.entries.forEach { mode ->
                SettingChoice(mode.label, cellularDataOption == mode.label, "settings_cellular_${mode.name}") {
                    onSetCellularData(mode.label); dialog = null
                }
            } }
            "quality" -> Column {
                SettingChoice("Standard", !highQualityEnabled, "settings_quality_standard") { onToggleHighQuality(false); dialog = null }
                SettingChoice("High", highQualityEnabled, "settings_quality_high") { onToggleHighQuality(true); dialog = null }
                Text("High uses more data and download space. Available resolution depends on your plan and the title.",
                    color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            }
            "delete" -> Text("Remove downloads and active download tasks for ${activeProfile.name}. Other profiles keep their downloads.")
            "signout" -> Text("Sign out of NetflixPro on this phone? Your account will remain signed in on other devices.")
            else -> Column {
                Text(currentEmail.orEmpty(), color = Color.White, fontSize = 14.sp)
                Text("${userSubscription.planName} • ${if (userSubscription.isInRenewalGrace) "Renewal overdue" else if (userSubscription.isActive) "Active" else "No active membership"}",
                    color = Color.LightGray, modifier = Modifier.padding(vertical = 12.dp))
                TextButton(onClick = { navigate(onOpenSubscription) }) { Text("Manage membership", color = Color.White) }
                TextButton(onClick = { dialog = null; onResetPassword() }, modifier = Modifier.testTag("settings_reset_password")) {
                    Text("Reset password", color = Color.White)
                }
                TextButton(onClick = { dialog = "signout" }, modifier = Modifier.testTag("settings_sign_out")) {
                    Text("Sign out", color = Color.White)
                }
            }
        } },
        confirmButton = {
            if (dialog in listOf("delete", "signout")) TextButton(
                onClick = { if (dialog == "delete") { dialog = null; onClearAllDownloads() } else navigate(onSignOut) },
                modifier = Modifier.testTag(if (dialog == "delete") "settings_confirm_delete" else "settings_confirm_sign_out")
            ) { Text(if (dialog == "delete") "Delete" else "Sign out", color = Color.White) }
        },
        dismissButton = { TextButton(onClick = { dialog = null }) { Text(if (dialog == "account") "Done" else "Cancel", color = Color.White) } }
    )

    Column(modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()
        .testTag("netflix_settings_screen")) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("close_settings_btn")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White)
            }
            Text("App Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            ProfileAvatar(activeProfile, size = 26.dp)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            SettingsSection("Account")
            SettingsRow(if (currentEmail == null) "Sign In" else "Account", currentEmail ?: "Sign in to manage your membership",
                Icons.Outlined.PersonOutline, "settings_account") {
                if (currentEmail == null) navigate(onOpenAuth) else dialog = "account"
            }
            SettingsRow("Membership", if (userSubscription.isInRenewalGrace) "Renew today • Two-day renewal allowance" else if (userSubscription.isActive) "${userSubscription.planName} • Active" else "View plans",
                Icons.Outlined.CreditCard, "settings_membership") { navigate(onOpenSubscription) }
            SettingsRow("Manage Profile", activeProfile.name, Icons.Outlined.Edit, "settings_edit_profile") { navigate(onEditProfile) }
            SettingsRow("Switch Profile", null, Icons.Outlined.SwapHoriz, "settings_switch_profile") { navigate(onSwitchProfile) }
            SettingsRow("Watch on TV", "Sign in with your NetflixPro account on TV", Icons.Outlined.Tv,
                "pair_tv_settings_btn") { navigate(onOpenTvPair) }

            SettingsSection("Video Playback")
            SettingsRow("Cellular Data Usage", cellularDataOption, Icons.Outlined.CellTower, "settings_cellular_data") { dialog = "cellular" }
            SettingsRow("Video Quality", if (highQualityEnabled) "High" else "Standard", Icons.Outlined.HighQuality,
                "settings_video_quality") { dialog = "quality" }
            SettingsToggle("Auto-Play Next Episode", "For ${activeProfile.name}", autoPlayNextEnabled, onToggleAutoPlayNext)
            SettingsToggle("Auto-Play Previews", "For ${activeProfile.name}", autoPlayPreviewsEnabled, onToggleAutoPlayPreviews)
            SettingsToggle("Spatial Audio", if (spatialAllowed) "On supported devices and audio tracks" else "Included with Premium",
                spatialAudioEnabled && spatialAllowed, onToggleSpatialAudio, enabled = spatialAllowed)

            SettingsSection("Downloads")
            SettingsToggle("Wi-Fi Only Downloads", "Applies to downloads on this phone", wifiOnlyEnabled, onToggleWifiOnly)
            SettingsToggle("Download Next Episode", if (userSubscription.isSmartNextEpisodeAllowed) "Replace watched episodes on Wi-Fi"
                else "Included with Standard and Premium", smartDownloadsEnabled && userSubscription.isSmartNextEpisodeAllowed,
                onToggleSmartDownloads, enabled = userSubscription.isSmartNextEpisodeAllowed)
            SettingsRow("Smart Downloads", "Next episode, Downloads for You and storage", Icons.Outlined.DownloadForOffline,
                "settings_smart_downloads") { navigate(onOpenSmartDownloads) }
            SettingsRow("Delete All Downloads", "For ${activeProfile.name}", Icons.Outlined.DeleteOutline,
                "settings_delete_downloads", enabled = totalStorageUsedMb > 0 || hasActiveDownloads) { dialog = "delete" }
            SettingsStorage(totalStorageUsedMb, storage.totalBytes, storage.availableBytes)

            SettingsSection("Diagnostics")
            SettingsRow(if (diagnosticRunning) "Checking Connection…" else "Check Network", diagnosticResult ?: "Test internet access and request latency",
                Icons.Outlined.NetworkCheck, "settings_network_test", enabled = !diagnosticRunning, onClick = onRunDiagnosticTest)
            Text("NetflixPro ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Color(0xFF808080), fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 28.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    HorizontalDivider(color = Color(0xFF292929), modifier = Modifier.padding(top = 20.dp))
    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp))
}

@Composable
private fun SettingsRow(title: String, subtitle: String?, icon: ImageVector, tag: String,
    enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).testTag(tag)
        .heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (enabled) Color.White else Color.Gray, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, color = if (enabled) Color.White else Color.Gray, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(it, color = Color(0xFF999999), fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(top = 3.dp)) }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Color(0xFF999999), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SettingsToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled, Role.Switch, onChange)
        .testTag("settings_toggle_$title").heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, color = if (enabled) Color.White else Color.Gray, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFF999999), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Switch(checked, onCheckedChange = null, enabled = enabled, colors = netflixProSwitchColors())
    }
}

@Composable
private fun SettingChoice(title: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = NetflixBlue, unselectedColor = Color.LightGray))
        Text(title, color = Color.White, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SettingsStorage(downloadsMb: Int, totalBytes: Long, availableBytes: Long) {
    val total = totalBytes.coerceAtLeast(1L)
    val free = availableBytes.coerceIn(0L, total)
    val downloads = (downloadsMb.coerceAtLeast(0).toLong() * 1024 * 1024).coerceAtMost(total - free)
    val other = total - free - downloads
    Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
        Text("Internal Storage", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth().height(5.dp).padding(top = 0.dp).background(Color(0xFF333333))) {
            listOf(downloads to NetflixBlue, other to Color(0xFF737373), free to Color(0xFFD9D9D9)).forEach { (bytes, color) ->
                if (bytes > 0) Spacer(Modifier.weight((bytes.toDouble() / total).toFloat().coerceAtLeast(.000001f)).fillMaxHeight().background(color))
            }
        }
        Text("Downloads: $downloadsMb MB • Free: ${"%.1f".format(free / (1024.0 * 1024 * 1024))} GB",
            color = Color(0xFF999999), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
