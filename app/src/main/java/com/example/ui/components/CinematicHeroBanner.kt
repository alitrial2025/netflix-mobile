package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaItem

/** Wide artwork fills the window; foreground actions keep the existing admission callbacks. */
@Composable
internal fun CinematicHeroBanner(
    media: MediaItem,
    isInWatchlist: Boolean,
    onPlayClick: () -> Unit,
    onWatchlistToggle: () -> Unit,
    onInfoClick: () -> Unit,
    height: Dp,
    isLocked: Boolean,
    onColorsExtracted: (Color, Color) -> Unit,
    artworkNetworkAvailable: Boolean = true
) {
    val colors by rememberPosterColors(media)
    LaunchedEffect(media.id, colors) { onColorsExtracted(colors.first, colors.second) }
    var backdropFailed by remember(media.id, media.backdropUrl, artworkNetworkAvailable) { mutableStateOf(false) }
    var logoFailed by remember(media.id, media.logoUrl, artworkNetworkAvailable) { mutableStateOf(false) }
    val artwork = if (backdropFailed) posterModel(media)
        else media.backdropUrl?.takeIf(String::isNotBlank) ?: posterModel(media)
    BoxWithConstraints(Modifier.fillMaxWidth().height(height).background(colors.second)
        .clickable(onClick = onInfoClick).testTag("home_cinematic_hero")) {
        val image = rememberPosterRequest(artwork, maxWidth, height)
        val compact = height < 380.dp
        val contentWidth = (maxWidth * .50f).coerceIn(240.dp, 500.dp).coerceAtMost(maxWidth - 48.dp)
        val logoHeight = if (compact) 64.dp else 106.dp
        val logo = rememberPosterRequest(media.logoUrl, contentWidth, logoHeight)
        AsyncImage(image, contentDescription = media.title, contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd, onError = { backdropFailed = true },
            modifier = Modifier.fillMaxSize().testTag("cinematic_backdrop"))
        // Scrims are local to the controls and the rail transition, leaving the
        // centre/right of the backdrop clear. No extra player or animation loop.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
            Color.Black.copy(alpha = .66f), Color.Black.copy(alpha = .16f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = .28f), .30f to Color.Transparent,
            .68f to Color.Transparent, 1f to Color.Black)))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 24.dp,
            bottom = if (compact) 16.dp else 28.dp).width(contentWidth),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)) {
            if (!media.logoUrl.isNullOrBlank() && !logoFailed) {
                AsyncImage(logo, contentDescription = media.title, contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart, onError = { logoFailed = true },
                    modifier = Modifier.fillMaxWidth().height(logoHeight).testTag("cinematic_title"))
            } else {
                Text(media.title, color = Color.White, fontWeight = FontWeight.Black,
                    fontSize = if (compact) 27.sp else 38.sp, maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("cinematic_title"))
            }
            if (!compact && media.genres.isNotEmpty()) {
                Text(media.genres.take(3).joinToString(" • "), color = Color.White,
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onPlayClick, Modifier.weight(1f).heightIn(min = 48.dp).testTag("hero_play"),
                    shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(horizontal = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                    Icon(if (isLocked) Icons.Default.Lock else Icons.Default.PlayArrow, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isLocked) "Unlock" else "Play", fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Button(onWatchlistToggle, Modifier.weight(1f).heightIn(min = 48.dp).testTag("hero_my_list"),
                    shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(horizontal = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = .22f), contentColor = Color.White)) {
                    Icon(if (isInWatchlist) Icons.Default.Check else Icons.Default.Add, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("My List", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}
