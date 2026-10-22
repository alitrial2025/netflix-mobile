package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Actual player track identities remain the selection authority, including duplicate language variants. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerAudioSubtitleSheet(audio: List<PlayerTrackOption>, subtitles: List<PlayerTrackOption>, offsetMs: Int,
    onAudio: (PlayerTrackOption) -> Unit, onSubtitle: (PlayerTrackOption) -> Unit,
    onOffset: (Int) -> Unit, onDismiss: () -> Unit) {
    val design = com.example.ui.theme.LocalPlatformDesign.current
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = design.surface,
        contentColor = Color.White, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            .testTag("player_tracks_sheet"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Audio & Subtitles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("player_tracks_done")) { Text("Done", color = design.accent) }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TrackChoices("Audio", audio, onAudio, Modifier.weight(1f), "audio")
                TrackChoices("Subtitles", subtitles, onSubtitle, Modifier.weight(1f), "subtitle")
            }
            HorizontalDivider(color = Color.White.copy(alpha = .16f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1.3f)) {
                    Text("Subtitle timing", style = MaterialTheme.typography.labelMedium)
                    Text(String.format(java.util.Locale.US, "%+.2f s", offsetMs / 1000.0), style = MaterialTheme.typography.labelMedium,
                        color = Color.LightGray, modifier = Modifier.testTag("subtitle_offset_value"))
                }
                TextButton(onClick = { onOffset((offsetMs - 250).coerceAtLeast(-30_000)) }, modifier = Modifier.weight(1f).testTag("subtitle_earlier")) { Text("−0.25 s", color = Color.White) }
                TextButton(onClick = { onOffset(0) }, modifier = Modifier.weight(1f).testTag("subtitle_timing_reset")) { Text("Reset", color = design.accent) }
                TextButton(onClick = { onOffset((offsetMs + 250).coerceAtMost(30_000)) }, modifier = Modifier.weight(1f).testTag("subtitle_later")) { Text("+0.25 s", color = Color.White) }
            }
        }
    }
}

@Composable
private fun TrackChoices(title: String, tracks: List<PlayerTrackOption>, onChoose: (PlayerTrackOption) -> Unit,
    modifier: Modifier, tag: String) {
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
        LazyColumn(Modifier.fillMaxSize().testTag("player_${tag}_tracks")) {
            if (tracks.isEmpty()) item { Text("No alternate tracks available", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
            items(tracks, key = { it.id }) { track ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .background(if (track.isSelected) Color.White.copy(alpha = .08f) else Color.Transparent, RoundedCornerShape(12.dp))
                    .selectable(track.isSelected, role = Role.RadioButton, onClick = { onChoose(track) })
                    .padding(horizontal = 4.dp, vertical = 4.dp).testTag("player_${tag}_${track.id}"), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(track.isSelected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = com.example.ui.theme.LocalPlatformDesign.current.accent))
                    Text(track.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
