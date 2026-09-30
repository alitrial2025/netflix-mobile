package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaItem

@Composable
fun HeroBanner(
    media: MediaItem,
    isInWatchlist: Boolean,
    onPlayClick: () -> Unit,
    onWatchlistToggle: () -> Unit,
    onInfoClick: () -> Unit,
    onColorsExtracted: (Color, Color) -> Unit = { _, _ -> },
    cardHeight: Dp = 520.dp,
    parallaxOffsetProvider: () -> Float = { 0f },
    isLocked: Boolean = false,
    userPlanName: String = "",
    modifier: Modifier = Modifier
) {
    val colors by rememberPosterColors(media)
    LaunchedEffect(media.id, colors) { onColorsExtracted(colors.first, colors.second) }
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().widthIn(max = 540.dp).height(cardHeight)
                .clip(shape).border(1.dp, Color.White.copy(alpha = .28f), shape)
                .background(colors.second).clickable(onClick = onInfoClick).testTag("hero_banner_card")
        ) {
            AsyncImage(
                model = posterModel(media), contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { translationY = parallaxOffsetProvider().coerceIn(-12f, 12f) }
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Color.Black.copy(alpha = .10f), .45f to Color.Transparent,
                .68f to Color.Black.copy(alpha = .12f), .87f to Color.Black.copy(alpha = .75f),
                1f to Color(0xFF1B1B19)
            )))
            if (media.isOriginal) NetflixNLogo(size = 27.dp, modifier = Modifier.padding(14.dp).align(Alignment.TopStart))
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!media.logoUrl.isNullOrBlank()) {
                    AsyncImage(model = media.logoUrl, contentDescription = media.title,
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth(.85f).height(62.dp))
                } else {
                    Text(media.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(20.dp))
                Text(media.genres.take(4).joinToString(" • "), color = Color.White.copy(alpha = .8f),
                    fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onPlayClick, modifier = Modifier.weight(1f).height(44.dp).testTag("hero_play"),
                        shape = RoundedCornerShape(4.dp), contentPadding = PaddingValues(horizontal = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                        Icon(if (isLocked) Icons.Default.Lock else Icons.Default.PlayArrow, null, Modifier.size(27.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isLocked) "Unlock" else "Play", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Button(onClick = onWatchlistToggle, modifier = Modifier.weight(1f).height(44.dp).testTag("hero_my_list"),
                        shape = RoundedCornerShape(4.dp), contentPadding = PaddingValues(horizontal = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = .19f), contentColor = Color.White)) {
                        Icon(if (isInWatchlist) Icons.Default.Check else Icons.Default.Add, null, Modifier.size(25.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("My List", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
