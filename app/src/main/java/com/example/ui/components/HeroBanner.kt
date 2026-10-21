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
    modifier: Modifier = Modifier,
    artworkNetworkAvailable: Boolean = true
) {
    val colors by rememberPosterColors(media)
    LaunchedEffect(media.id, colors) { onColorsExtracted(colors.first, colors.second) }
    val shape = remember { RoundedCornerShape(12.dp) }
    val footerColor = colors.second
    val rim = remember { Brush.verticalGradient(listOf(
        Color.White.copy(alpha = .16f), Color.White.copy(alpha = .04f), Color.White.copy(alpha = .10f)
    )) }
    val heroWidth = (LocalConfiguration.current.screenWidthDp - 52).coerceIn(1, 540).dp
    val logoHeight = (cardHeight * .18f).coerceIn(64.dp, 104.dp)
    val logoRequest = rememberPosterRequest(media.logoUrl, heroWidth * .82f, logoHeight)
    var logoFailed by remember(media.id, media.logoUrl, artworkNetworkAvailable) { mutableStateOf(false) }
    val showLogo = !media.logoUrl.isNullOrBlank() && !logoFailed
    // Pair the actual TMDB wordmark with textless artwork. Keep ordinary posters
    // for rails and for the no-logo/error fallback, rather than hiding the logo.
    val heroModel = if (showLogo) media.heroPosterUrl?.takeIf { it.isNotBlank() }
        ?: media.backdropUrl?.takeIf { it.isNotBlank() } ?: posterModel(media) else posterModel(media)
    var artworkFailed by remember(media.id, heroModel, artworkNetworkAvailable) { mutableStateOf(false) }
    val posterRequest = rememberPosterRequest(if (artworkFailed) posterModel(media) else heroModel, heroWidth, cardHeight)
    val needsTitleOverlay = media.posterUrl.isNullOrBlank() && media.bannerDrawableRes == null
    val footerBrush = remember(footerColor) { Brush.verticalGradient(
        0f to Color.Transparent, .62f to Color.Transparent,
        .76f to footerColor.copy(alpha = .10f), .88f to footerColor.copy(alpha = .38f),
        1f to footerColor.copy(alpha = .68f)
    ) }
    val genreLabel = remember(media.genres) {
        media.genres.filter { it.isNotBlank() }.distinct().take(3).joinToString(" • ")
    }
    Box(modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 12.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.widthIn(max = 540.dp).fillMaxWidth().height(cardHeight)
                .shadow(5.dp, shape, clip = false, ambientColor = colors.first.copy(alpha = .15f), spotColor = Color.Black.copy(alpha = .40f))
                .clip(shape).border(1.dp, rim, shape)
                .background(colors.second).clickable(onClick = onInfoClick).testTag("hero_banner_card")
        ) {
            AsyncImage(
                model = posterRequest, contentDescription = media.title,
                contentScale = ContentScale.Crop,
                onError = { artworkFailed = true },
                modifier = Modifier.fillMaxSize().graphicsLayer { translationY = parallaxOffsetProvider().coerceIn(-12f, 12f) }
            )
            Box(Modifier.fillMaxSize().background(footerBrush))
            if (media.isOriginal) NetflixNLogo(size = 26.dp, modifier = Modifier.padding(10.dp).align(Alignment.TopStart))
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showLogo) {
                    AsyncImage(model = logoRequest, contentDescription = media.title,
                        contentScale = ContentScale.Fit, onError = { logoFailed = true },
                        modifier = Modifier.fillMaxWidth(.82f).height(logoHeight).testTag("hero_title_logo"))
                } else if (needsTitleOverlay) {
                    Text(media.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (genreLabel.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(genreLabel, color = Color.White.copy(alpha = .92f),
                        fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
