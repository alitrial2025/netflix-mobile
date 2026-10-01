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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
    val shape = remember { RoundedCornerShape(16.dp) }
    val footerColor = lerp(colors.second, Color.Black, .32f)
    val rim = remember { Brush.verticalGradient(listOf(
        Color.White.copy(alpha = .38f), Color.White.copy(alpha = .12f), Color.White.copy(alpha = .24f)
    )) }
    val heroWidth = (LocalConfiguration.current.screenWidthDp - 52).coerceIn(1, 540).dp
    val posterRequest = rememberPosterRequest(posterModel(media), heroWidth, cardHeight)
    val logoRequest = rememberPosterRequest(media.logoUrl, heroWidth * .85f, 62.dp)
    val footerBrush = remember(footerColor, colors.second) { Brush.verticalGradient(
        0f to Color.Black.copy(alpha = .10f), .45f to Color.Transparent,
        .62f to footerColor.copy(alpha = .35f), .72f to footerColor.copy(alpha = .99f),
        .78f to footerColor, .90f to footerColor, 1f to colors.second
    ) }
    val genreLabel = remember(media.genres) { media.genres.take(4).joinToString(" • ") }
    Box(modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 12.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().widthIn(max = 540.dp).height(cardHeight)
                .shadow(8.dp, shape, clip = false, ambientColor = colors.first.copy(alpha = .25f), spotColor = Color.Black.copy(alpha = .55f))
                .clip(shape).border(1.dp, rim, shape)
                .background(colors.second).clickable(onClick = onInfoClick).testTag("hero_banner_card")
        ) {
            AsyncImage(
                model = posterRequest, contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { translationY = parallaxOffsetProvider().coerceIn(-12f, 12f) }
            )
            Box(Modifier.fillMaxSize().background(footerBrush))
            if (media.isOriginal) NetflixNLogo(size = 27.dp, modifier = Modifier.padding(14.dp).align(Alignment.TopStart))
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!media.logoUrl.isNullOrBlank()) {
                    AsyncImage(model = logoRequest, contentDescription = media.title,
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth(.85f).height(62.dp))
                } else {
                    Text(media.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(20.dp))
                Text(genreLabel, color = Color.White.copy(alpha = .8f),
                    fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
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
