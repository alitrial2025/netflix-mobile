package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.theme.NetflixCardBg
import com.example.ui.theme.NetflixRed
import kotlinx.coroutines.delay

private val StandardCardShape = RoundedCornerShape(4.dp)
private val TallCardShape = RoundedCornerShape(6.dp)
private val BadgeShape = RoundedCornerShape(2.dp)
private val CardVignetteOverlay = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
)

/**
 * High performance Netflix media poster card with hardware acceleration
 */
@Composable
fun MediaPosterCard(
    media: MediaItem,
    modifier: Modifier = Modifier,
    width: Dp = 115.dp,
    height: Dp = 165.dp,
    showRank: Boolean = false,
    isReminded: Boolean = false,
    isLocked: Boolean = false,
    onClick: () -> Unit
) {
    val isTallCard = height >= 300.dp
    val context = LocalContext.current
    val imageModel = media.posterUrl ?: media.bannerDrawableRes
    val cardShape = if (isTallCard) TallCardShape else StandardCardShape

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(cardShape)
            .background(Color(0xFF141414))
            .clickable { onClick() }
            .testTag("media_card_${media.id}")
    ) {
        if (imageModel == null) {
            Text(
                text = media.title,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = if (isTallCard) 14.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 6.dp)
            )
        } else {
            AsyncImage(
                model = remember(context, imageModel, width, height, androidx.compose.ui.platform.LocalDensity.current.density) {
                    ImageRequest.Builder(context).data(imageModel)
                        .size((width.value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1),
                            (height.value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1))
                        .crossfade(false)
                        .memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED).build()
                },
                contentDescription = media.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Lock dimming overlay if locked
        if (isLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        }

        // Top badges: Netflix 'N' Logo on top-left, Lock badge or TOP 10 badge on top-right
        val hasTopLeftBadge = media.isOriginal
        val hasTopRightBadge = isLocked || (showRank && media.top10Rank != null)
        if (hasTopLeftBadge || hasTopRightBadge) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isTallCard) 8.dp else 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                if (hasTopLeftBadge) {
                    NetflixNLogo(size = if (isTallCard) 24.dp else 18.dp)
                } else {
                    Spacer(modifier = Modifier.size(if (isTallCard) 24.dp else 18.dp))
                }

                if (isLocked) {
                    Box(
                        modifier = Modifier
                            .clip(BadgeShape)
                            .background(Color.Black.copy(alpha = 0.85f))
                            .border(1.dp, NetflixRed.copy(alpha = 0.9f), BadgeShape)
                            .padding(horizontal = if (isTallCard) 6.dp else 4.dp, vertical = if (isTallCard) 3.dp else 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked on Plan",
                                tint = NetflixRed,
                                modifier = Modifier.size(if (isTallCard) 11.dp else 9.dp)
                            )
                            Text(
                                text = "LOCKED",
                                color = Color.White,
                                fontSize = if (isTallCard) 9.sp else 7.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (hasTopRightBadge) {
                    Box(
                        modifier = Modifier
                            .clip(BadgeShape)
                            .background(NetflixRed)
                            .padding(horizontal = if (isTallCard) 6.dp else 4.dp, vertical = if (isTallCard) 3.dp else 2.dp)
                    ) {
                        Text(
                            text = "TOP\n10",
                            color = Color.White,
                            fontSize = if (isTallCard) 9.sp else 7.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = if (isTallCard) 9.sp else 7.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Bottom pill badge for Remind Me, release date badge, or Top 10
        val showBellBadge = media.isComingSoon || isReminded
        if (showBellBadge || media.releaseDateBadge != null || (showRank && media.top10Rank != null)) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(CardVignetteOverlay)
                    .padding(bottom = if (isTallCard) 8.dp else 4.dp),
                contentAlignment = Alignment.Center
            ) {
                ClayBadge(
                    text = when {
                        isReminded -> "Reminded"
                        media.releaseDateBadge != null -> media.releaseDateBadge!!
                        media.isComingSoon -> "Remind Me"
                        else -> "Top 10"
                    },
                    surfaceColor = if (isReminded) NetflixRed else Color(0xFF1C1B24).copy(alpha = 0.92f),
                    textColor = Color.White,
                    shape = BadgeShape,
                    leadingIcon = if (showBellBadge) (if (isReminded) Icons.Default.NotificationsActive else Icons.Default.Notifications) else null,
                    fontSize = if (isTallCard) 10.sp else 8.sp,
                    strokeWidth = 0.8.dp
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    media: MediaItem,
    progressFraction: Float,
    episodeTitle: String?,
    modifier: Modifier = Modifier,
    width: Dp = 118.dp,
    posterHeight: Dp = 168.dp,
    onPlayClick: () -> Unit,
    onInfoClick: () -> Unit,
    onOptionsClick: () -> Unit = {}
) {
    val episodeTag = remember(episodeTitle, media.type) { formatEpisodeBadge(episodeTitle, media) }
    val context = LocalContext.current
    val imageModel = media.posterUrl ?: media.bannerDrawableRes ?: media.backdropUrl

    val cardShape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .width(width)
            .claymorphic(
                shape = cardShape,
                surfaceColor = Color(0xFF181720),
                highlightColor = Color.White,
                shadowColor = Color.Black,
                elevation = 6.dp,
                strokeWidth = 1.2.dp,
                highlightAlpha = 0.42f,
                depthAlpha = 0.76f,
                gradientCurvature = 0.20f
            )
            .testTag("continue_card_${media.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(posterHeight)
                .clickable { onPlayClick() }
        ) {
            if (imageModel == null) {
                // Placeholder / background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF141414))
                ) {
                    Text(
                        text = media.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageModel)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = media.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Dark vignette overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
            )

            // Centered tactile clay circular Play button
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .align(Alignment.Center)
                    .claymorphic(
                        shape = CircleShape,
                        surfaceColor = Color(0xFF22202C).copy(alpha = 0.92f),
                        highlightColor = Color.White,
                        shadowColor = Color.Black,
                        elevation = 8.dp,
                        strokeWidth = 1.4.dp,
                        highlightAlpha = 0.65f,
                        depthAlpha = 0.78f,
                        gradientCurvature = 0.25f
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Top-left Red Netflix "N" Logo
            if (media.isOriginal) {
                NetflixNLogo(
                    size = 18.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                )
            }

            // Bottom gradient overlay with Episode label (e.g. S1:E1)
            val episodeGradient = remember {
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .background(episodeGradient)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (episodeTag.isNotEmpty()) {
                    Text(
                        text = episodeTag,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Thin Red Progress Bar
        LinearProgressIndicator(
            progress = { progressFraction.coerceIn(0.05f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = NetflixRed,
            trackColor = Color(0xFF333333)
        )

        // Bottom Action Bar with Info (i) on left and 3 Dots (⋮) on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(Color(0xFF16151D))
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("continue_info_${media.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Info",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onOptionsClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("continue_options_${media.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatEpisodeBadge(episodeTitle: String?, media: MediaItem): String {
    if (!episodeTitle.isNullOrBlank()) {
        val sRegex = Regex("""(S\d+[:\s]*E\d+)""", RegexOption.IGNORE_CASE)
        val match = sRegex.find(episodeTitle)
        if (match != null) {
            return match.value.uppercase().replace(" ", "")
        }
        val epRegex = Regex("""(Episode\s*\d+)""", RegexOption.IGNORE_CASE)
        val epMatch = epRegex.find(episodeTitle)
        if (epMatch != null) {
            val num = epMatch.value.filter { it.isDigit() }
            return "S1:E$num"
        }
        if (episodeTitle.contains("Chapter", ignoreCase = true)) {
            val num = episodeTitle.filter { it.isDigit() }.ifEmpty { "1" }
            return "S1:E$num"
        }
        return episodeTitle
    }
    return ""
}
