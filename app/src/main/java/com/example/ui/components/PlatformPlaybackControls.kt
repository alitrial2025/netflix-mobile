package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.PlatformAppearance
import com.example.ui.theme.LocalPlatformDesign

/** Presentation only: invokes the main player's existing seek and play/pause handlers. */
@Composable
internal fun PlatformPlaybackControls(playing: Boolean, onBackTen: () -> Unit, onToggle: () -> Unit,
    onForwardTen: () -> Unit, modifier: Modifier = Modifier) {
    val design = LocalPlatformDesign.current
    Row(modifier.testTag("platform_playback_controls_${design.appearance.key}"),
        horizontalArrangement = Arrangement.spacedBy(if (design.appearance == PlatformAppearance.APPLE) 36.dp else 48.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBackTen, Modifier.size(48.dp).testTag("player_rewind_10")) {
            Icon(Icons.Default.Replay10, "Rewind 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        IconButton(onToggle, Modifier.size(64.dp).clip(CircleShape)
            .background(if (design.appearance in setOf(PlatformAppearance.APPLE, PlatformAppearance.DISNEY)) Color.Black.copy(alpha = .35f) else Color.Transparent)
            .testTag("player_play_pause_button")) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play",
                tint = Color.White, modifier = Modifier.size(48.dp))
        }
        IconButton(onForwardTen, Modifier.size(48.dp).testTag("player_forward_10")) {
            Icon(Icons.Default.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}
