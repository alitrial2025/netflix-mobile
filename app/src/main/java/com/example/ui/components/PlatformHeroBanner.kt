package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.data.model.PlatformAppearance
import com.example.ui.theme.LocalPlatformDesign

/** Service-specific composition using the same metadata and admission callbacks as ordinary Home. */
@Composable
internal fun PlatformHeroBanner(media: MediaItem, height: Dp, wide: Boolean, inList: Boolean, locked: Boolean,
    networkAvailable: Boolean, onPlay: () -> Unit, onList: () -> Unit, onInfo: () -> Unit) {
    val design = LocalPlatformDesign.current
    val compact = wide && height < 340.dp
    val logoHeight = if (compact) 32.dp else if (wide) 76.dp else 88.dp
    val centered = !wide && design.appearance in setOf(PlatformAppearance.DISNEY,
        PlatformAppearance.APPLE, PlatformAppearance.PEACOCK)
    val insetCard = !wide && design.appearance in setOf(PlatformAppearance.PRIME, PlatformAppearance.HOTSTAR, PlatformAppearance.PEACOCK)
    var failedBackdrop by remember(media.type, media.id, media.backdropUrl, networkAvailable) { mutableStateOf(false) }
    var failedLogo by remember(media.type, media.id, media.logoUrl, networkAvailable) { mutableStateOf(false) }
    val portrait = !wide && design.appearance != PlatformAppearance.PRIME
    val artwork = if (failedBackdrop) posterModel(media) else if (portrait)
        media.heroPosterUrl ?: media.backdropUrl ?: posterModel(media) else media.backdropUrl ?: posterModel(media)
    val frame = Modifier.fillMaxWidth().height(height)
        .then(if (insetCard) Modifier.padding(start = 16.dp, end = 16.dp, top = 132.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), bottom = 12.dp)
            .clip(RoundedCornerShape(design.cardRadius)) else Modifier)
    BoxWithConstraints(frame.background(design.surface).clickable(onClick = onInfo)
        .testTag("platform_hero_${design.appearance.key}")) {
        AsyncImage(rememberPosterRequest(artwork, maxWidth, maxHeight), media.title,
            contentScale = ContentScale.Crop, alignment = Alignment.Center,
            onError = { failedBackdrop = true }, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to design.background.copy(alpha = if (insetCard) 0f else .6f),
            .3f to Color.Transparent, .52f to Color.Transparent, .82f to design.background.copy(alpha = .82f),
            1f to design.background)))
        if (!centered) Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
            design.background.copy(alpha = .6f), Color.Transparent))))
        val contentWidth = if (!wide && design.appearance == PlatformAppearance.HOTSTAR) maxWidth - 100.dp else if (wide) (maxWidth * .55f).coerceIn(260.dp, 520.dp) else maxWidth
        Column(Modifier.align(if (centered) Alignment.BottomCenter else Alignment.BottomStart)
            .width(contentWidth).padding(horizontal = 24.dp, vertical = if (compact) 16.dp else if (wide) 20.dp else 22.dp),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            if (!media.logoUrl.isNullOrBlank() && !failedLogo) AsyncImage(
                rememberPosterRequest(media.logoUrl, contentWidth - 48.dp, logoHeight), media.title,
                contentScale = ContentScale.Fit, alignment = if (centered) Alignment.Center else Alignment.CenterStart,
                onError = { failedLogo = true }, modifier = Modifier.fillMaxWidth().height(logoHeight)
                    .testTag("platform_title_logo"))
            else Text(media.title, color = Color.White, fontSize = if (compact) 24.sp else if (wide) 30.sp else 32.sp,
                fontWeight = design.titleWeight, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis,
                textAlign = if (centered) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start)
            if (!compact) Text((listOfNotNull(media.releaseYear.takeIf { it > 0 }?.toString()) + media.genres.take(2)).joinToString("  •  "),
                color = Color.White.copy(alpha = .82f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!centered && height >= 400.dp && design.appearance != PlatformAppearance.HOTSTAR) Text(media.description, color = Color.White.copy(alpha = .85f),
                fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (wide || design.appearance != PlatformAppearance.HOTSTAR) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, if (centered) Alignment.CenterHorizontally else Alignment.Start)) {
                Button(onPlay, (if (design.appearance == PlatformAppearance.HOTSTAR) Modifier.size(52.dp)
                    else if (wide) Modifier.width(180.dp).height(48.dp)
                    else if (design.appearance == PlatformAppearance.APPLE && !wide) Modifier.width(144.dp).height(48.dp)
                    else Modifier.weight(1f).heightIn(min = 48.dp)).testTag("hero_play"),
                    colors = ButtonDefaults.buttonColors(containerColor = design.primary, contentColor = design.onPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(if (design.appearance == PlatformAppearance.HOTSTAR) 50.dp else design.controlRadius)) {
                    Icon(if (locked) Icons.Default.Lock else Icons.Default.PlayArrow, null, Modifier.size(24.dp))
                    if (design.appearance != PlatformAppearance.HOTSTAR) {
                        Spacer(Modifier.width(6.dp)); Text(if (locked) "Unlock" else "Play", maxLines = 1, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(onList, (if (!wide && design.appearance == PlatformAppearance.APPLE) Modifier.size(48.dp)
                    else Modifier.size(48.dp)).testTag("hero_my_list"), contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(if (design.appearance in setOf(PlatformAppearance.HOTSTAR, PlatformAppearance.APPLE)) 50.dp else design.controlRadius), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .5f))) {
                    Icon(if (inList) Icons.Default.Check else Icons.Default.Add, "My List")
                }
            }
        }
        if (!wide && design.appearance == PlatformAppearance.HOTSTAR) {
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onList, Modifier.size(50.dp).testTag("hero_my_list"), shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                    Icon(if (inList) Icons.Default.Check else Icons.Default.Add, "My List")
                }
                Button(onPlay, Modifier.size(50.dp).testTag("hero_play"), shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                    Icon(if (locked) Icons.Default.Lock else Icons.Default.PlayArrow, if (locked) "Unlock" else "Play")
                }
            }
        }
    }
}
