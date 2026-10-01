package com.example.ui.screens

import com.example.ui.theme.netflixProSwitchColors
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.UserProfile
import com.example.ui.components.ProfileAvatar

@Composable
fun SmartDownloadsScreen(
    profiles: List<UserProfile>, allocations: Map<String, Float>, nextEpisodeEnabled: Boolean,
    forYouEnabled: Boolean, freeBytes: Long?, usedMb: Long, onBack: () -> Unit,
    onNextEpisode: (Boolean) -> Unit, onForYou: (Boolean) -> Unit,
    onAllocation: (String, Float) -> Unit, onSearch: () -> Unit = {}, onProfile: () -> Unit = {},
    nextEpisodeAllowed: Boolean = true, forYouAllowed: Boolean = true, onUpgrade: () -> Unit = {}
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().testTag("smart_downloads_screen")) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text("Smart Downloads", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onSearch) { Icon(painterResource(R.drawable.ic_outline_search), "Search", tint = Color.White) }
            profiles.firstOrNull()?.let { profile -> IconButton(onProfile) { ProfileAvatar(profile, size = 26.dp) } }
        }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                SmartDownloadsToggle("Download Next Episode", "As you watch a downloaded series, download the next episode and remove the one you’ve finished. Downloads use Wi-Fi.",
                    nextEpisodeEnabled && nextEpisodeAllowed, Icons.Default.SkipNext, "smart_next_episode", onNextEpisode,
                    nextEpisodeAllowed, "Included with Standard and Premium", onUpgrade)
            }
            item {
                SmartDownloadsToggle("Downloads for You", "Download a selection of movies and shows so you always have something to watch. Downloads use Wi-Fi.",
                    forYouEnabled && forYouAllowed, Icons.Default.DownloadDone, "smart_for_you", onForYou,
                    forYouAllowed, "Included with Premium", onUpgrade)
            }
            item {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Storage, null, tint = if (forYouEnabled) Color.White else Color.Gray, modifier = Modifier.padding(end = 14.dp))
                    Column {
                        Text("Allocate storage", color = if (forYouEnabled) Color.White else Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Choose how much space each profile can use. Your manual downloads are separate.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            items(profiles, key = { it.id }, contentType = { "allocation" }) { profile ->
                val amount = (allocations[profile.id] ?: 0f).coerceIn(0f, 10f)
                Row(Modifier.fillMaxWidth().testTag("smart_allocation_${profile.id}"), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(profile, size = 32.dp)
                    Text(profile.name, color = if (forYouEnabled) Color.White else Color.Gray, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    IconButton(onClick = { onAllocation(profile.id, (amount - .5f).coerceAtLeast(0f)) }, enabled = forYouEnabled && amount > 0,
                        modifier = Modifier.testTag("smart_allocation_minus_${profile.id}")) { Icon(Icons.Default.RemoveCircleOutline, "Decrease storage for ${profile.name}", tint = Color.Gray) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format(java.util.Locale.US, "%.1f", amount), color = if (forYouEnabled) Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("GB", color = Color.Gray, fontSize = 11.sp)
                    }
                    IconButton(onClick = { onAllocation(profile.id, (amount + .5f).coerceAtMost(10f)) }, enabled = forYouEnabled && amount < 10,
                        modifier = Modifier.testTag("smart_allocation_plus_${profile.id}")) { Icon(Icons.Default.AddCircleOutline, "Increase storage for ${profile.name}", tint = Color.Gray) }
                }
                HorizontalDivider(Modifier.padding(top = 12.dp), color = Color(0xFF202020))
            }
            item {
                val allocated = allocations.values.filter { it.isFinite() && it > 0 }.sum()
                val fraction = if (allocated > 0) (usedMb / (allocated * 1024f)).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(progress = { fraction }, color = Color(0xFF0071EB), trackColor = Color(0xFF333333), modifier = Modifier.fillMaxWidth().height(4.dp))
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${String.format(java.util.Locale.US, "%.1f", usedMb / 1024f)} GB · Downloads for You", color = Color.Gray, fontSize = 10.sp)
                    if (freeBytes != null) Text("${String.format(java.util.Locale.US, "%.1f", freeBytes / (1024f * 1024 * 1024))} GB · Free", color = Color.Gray, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun SmartDownloadsToggle(title: String, description: String, checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, onToggle: (Boolean) -> Unit,
    allowed: Boolean, upgradeLabel: String, onUpgrade: () -> Unit) {
    Column {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        if (!allowed) TextButton(onClick = onUpgrade) { Text(upgradeLabel, color = Color.White) }
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.padding(end = 14.dp))
            Text(description, color = Color.LightGray, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
            Switch(checked, onCheckedChange = onToggle, enabled = allowed, modifier = Modifier.padding(start = 12.dp).testTag(tag),
                colors = netflixProSwitchColors())
        }
    }
}
