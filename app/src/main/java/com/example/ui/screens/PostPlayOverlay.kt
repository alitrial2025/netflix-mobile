package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.components.NetflixRatingAction
import com.example.ui.theme.NetflixRed
import com.example.ui.viewmodel.PlayerState
import kotlinx.coroutines.delay

/** Countdown only runs while the viewer can see it and has permitted automatic episodes. */
@Composable
internal fun NextEpisodeCard(
    state: PlayerState, autoPlay: Boolean, active: Boolean,
    onNext: () -> Unit, onWatchCredits: () -> Unit, modifier: Modifier = Modifier
) {
    var remaining by remember(state.media?.id, state.episode?.id) { mutableIntStateOf(30) }
    var triggered by remember(state.media?.id, state.episode?.id) { mutableStateOf(false) }
    val latestNext by rememberUpdatedState(onNext)
    val countdownActive = autoPlay && active && !state.isLocked && !state.showAudioSubtitleDialog && !state.showEpisodeDrawer && (state.isPlaying || state.hasEnded)
    LaunchedEffect(countdownActive) {
        if (!countdownActive) return@LaunchedEffect
        while (remaining > 0) { delay(1000); remaining-- }
        if (!triggered) { triggered = true; latestNext() }
    }
    Column(modifier.widthIn(max = 360.dp).background(Color(0xFF181818).copy(alpha = .97f), RoundedCornerShape(8.dp)).padding(16.dp)) {
        Text("Up next", color = Color.Gray, fontSize = 12.sp)
        Text(state.nextEpisode?.title.orEmpty(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onWatchCredits, modifier = Modifier.testTag("watch_credits_button")) { Text("Watch credits", color = Color.White) }
            Button(onClick = { if (!triggered) { triggered = true; onNext() } }, shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), modifier = Modifier.testTag("next_episode_button")) {
                Text(if (autoPlay) "Play in $remaining" else "Play next", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun PostPlayOverlay(
    state: PlayerState, recommendations: List<MediaItem>, onReplay: () -> Unit, onClose: () -> Unit,
    onNext: () -> Unit, onRetryNext: () -> Unit, onRecommendation: (MediaItem) -> Unit,
    onRating: (String) -> Unit, modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().background(Color.Black.copy(alpha = .95f)).padding(horizontal = 32.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                com.example.ui.components.NetflixWordmark(height = 14.dp)
                Text(if (state.media?.type == MediaType.TV_SHOW) { if (state.nextEpisodeChecked && state.nextEpisode == null && state.nextEpisodeError == null) "You’re all caught up" else "Episode finished" } else "Thanks for watching", color = Color.White,
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                Text(state.media?.title.orEmpty(), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
            }
            NetflixRatingAction(onRatingSelect = onRating)
        }
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onReplay, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shape = RoundedCornerShape(4.dp), modifier = Modifier.testTag("replay_button")) {
                Icon(Icons.Default.PlayArrow, null); Text("Watch again", fontWeight = FontWeight.Bold)
            }
            if (state.nextEpisode != null) TextButton(onNext) { Text("Next episode", color = Color.White) }
            else if (state.nextEpisodeLoading) Text("Checking next episode…", color = Color.LightGray, modifier = Modifier.align(Alignment.CenterVertically))
            else if (state.nextEpisodeError != null) TextButton(onRetryNext) { Text("Retry next episode", color = Color.White) }
            TextButton(onClose, modifier = Modifier.testTag("post_play_close")) { Text("Back to browse", color = Color.White) }
        }
        if (recommendations.isNotEmpty()) {
            Text("Your next watch", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recommendations, key = { "${it.type}:${it.id}" }) { item ->
                    Row(Modifier.width(220.dp).height(92.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF222222)).clickable { onRecommendation(item) }.testTag("post_play_${item.id}"), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(item.posterUrl ?: item.backdropUrl ?: item.bannerDrawableRes, item.title, contentScale = ContentScale.Crop, modifier = Modifier.width(64.dp).fillMaxHeight())
                        Text(item.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp))
                    }
                }
            }
        }
    }
}
