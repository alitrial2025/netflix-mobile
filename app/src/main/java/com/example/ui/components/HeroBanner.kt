package com.example.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Color.toTopPosterColor(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(
        android.graphics.Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        ),
        hsl
    )
    hsl[1] = (hsl[1] * 1.2f).coerceIn(0.35f, 0.85f)
    hsl[2] = (hsl[2] * 0.95f).coerceIn(0.25f, 0.48f)
    return Color(ColorUtils.HSLToColor(hsl))
}

fun Color.toBottomPosterColor(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(
        android.graphics.Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        ),
        hsl
    )
    hsl[1] = (hsl[1] * 1.0f).coerceIn(0.25f, 0.70f)
    hsl[2] = (hsl[2] * 0.75f).coerceIn(0.15f, 0.30f)
    return Color(ColorUtils.HSLToColor(hsl))
}

fun getFallbackPosterColors(media: MediaItem): Pair<Color, Color> {
    val base = if (media.primaryColorHex != 0L) {
        Color(media.primaryColorHex)
    } else {
        val hash = kotlin.math.abs(media.id.hashCode() + media.title.hashCode())
        val hue = (hash % 360).toFloat()
        val argb = ColorUtils.HSLToColor(floatArrayOf(hue, 0.35f, 0.28f))
        Color(argb)
    }
    return Pair(base.toTopPosterColor(), base.toBottomPosterColor())
}

private val heroColorsCache = mutableMapOf<String, Pair<Color, Color>>()

@Composable
fun HeroBanner(
    media: MediaItem,
    isInWatchlist: Boolean,
    onPlayClick: () -> Unit,
    onWatchlistToggle: () -> Unit,
    onInfoClick: () -> Unit,
    onColorsExtracted: (topColor: Color, bottomColor: Color) -> Unit = { _, _ -> },
    cardHeight: Dp = 520.dp,
    parallaxOffsetProvider: () -> Float = { 0f },
    isLocked: Boolean = false,
    userPlanName: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cachedColors = remember(media.id) { heroColorsCache[media.id] }
    val (defaultTop, defaultBottom) = remember(media.id) {
        cachedColors ?: getFallbackPosterColors(media)
    }
    
    var cardBottomBgColor by remember(media.id) { mutableStateOf(defaultBottom) }

    val animatedCardBottomBg by animateColorAsState(
        targetValue = cardBottomBgColor,
        animationSpec = tween(400),
        label = "hero_card_bg"
    )

    val imageModel = media.bannerDrawableRes ?: media.backdropUrl ?: media.posterUrl

    // Background Palette extraction cached to avoid re-extracting during scroll recycling
    LaunchedEffect(media.id, imageModel) {
        if (cachedColors != null) {
            onColorsExtracted(cachedColors.first, cachedColors.second)
            return@LaunchedEffect
        }
        onColorsExtracted(defaultTop, defaultBottom)
        if (imageModel != null) {
            withContext(Dispatchers.IO) {
                try {
                    val paletteReq = ImageRequest.Builder(context)
                        .data(imageModel)
                        .allowHardware(false)
                        .size(Size(80, 80))
                        .build()
                    val result = (context.imageLoader.execute(paletteReq) as? coil.request.SuccessResult)?.drawable
                    val bitmap = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).generate()
                        val swatchRgb = palette.dominantSwatch?.rgb
                            ?: palette.vibrantSwatch?.rgb
                            ?: palette.darkMutedSwatch?.rgb
                        
                        if (swatchRgb != null) {
                            val baseColor = Color(swatchRgb)
                            val topColor = baseColor.toTopPosterColor()
                            val bottomColor = baseColor.toBottomPosterColor()
                            heroColorsCache[media.id] = Pair(topColor, bottomColor)
                            cardBottomBgColor = bottomColor
                            onColorsExtracted(topColor, bottomColor)
                        }
                    }
                } catch (_: Throwable) {
                    // Safe fallback colors
                }
            }
        }
    }

    val cardShape = remember { RoundedCornerShape(20.dp) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Actual Card Surface
        Box(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .widthIn(max = 430.dp)
                .height(cardHeight)
                .shadow(
                    elevation = 28.dp,
                    shape = cardShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.85f),
                    spotColor = Color.Black.copy(alpha = 0.95f)
                )
                .clip(cardShape)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Black.copy(alpha = 0.50f)
                        )
                    ),
                    shape = cardShape
                )
                .background(Color(0xFF0F0E14))
                .clickable { onInfoClick() }
                .testTag("hero_banner_card")
        ) {
            // Hero Background Image / Poster with parallax
            if (imageModel != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageModel)
                        .crossfade(200)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build(),
                    contentDescription = media.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = parallaxOffsetProvider()
                            scaleX = 1.08f
                            scaleY = 1.08f
                        },
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    defaultTop,
                                    defaultBottom,
                                    NetflixBlack
                                )
                            )
                        )
                )
            }

            // Multi-Layered Cinematic Gradients Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val h = size.height
                        val dimmedBgColor = Color(
                            red = animatedCardBottomBg.red * 0.2f,
                            green = animatedCardBottomBg.green * 0.2f,
                            blue = animatedCardBottomBg.blue * 0.2f,
                            alpha = 1.0f
                        )
                        
                        // Top Subtle Vignette (to highlight N logo and Top 10 badge)
                        val topVignette = Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.65f),
                            0.25f to Color.Transparent,
                            startY = 0f,
                            endY = h * 0.35f
                        )

                        // Base Shadow from bottom fading up
                        val baseShadow = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.42f to Color.Transparent,
                            0.82f to dimmedBgColor.copy(alpha = 0.92f),
                            1.0f to Color(0xFF0A090D),
                            startY = 0f,
                            endY = h
                        )

                        // Rich Color Infusion
                        val colorInfusion = Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            0.90f to animatedCardBottomBg.copy(alpha = 0.85f),
                            1.0f to animatedCardBottomBg.copy(alpha = 0.40f),
                            startY = 0f,
                            endY = h
                        )

                        onDrawBehind {
                            drawRect(topVignette)
                            drawRect(baseShadow)
                            drawRect(brush = colorInfusion)
                        }
                    }
            )

            // Top Badges Row: Red N Logo (Top-Left) and Top 10 Rank Badge (Top-Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NetflixNLogo(size = 28.dp)

                if (media.top10Rank != null) {
                    ClayBadge(
                        text = "TOP 10",
                        surfaceColor = NetflixRed,
                        textColor = Color.White,
                        shape = RoundedCornerShape(5.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Overlay Content inside the card bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // "N SERIES" or "N FILM" Badge above title, plus Lock indicator if locked
                if (isLocked) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.85f))
                            .border(1.dp, NetflixRed.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked on Plan",
                                tint = NetflixRed,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (userPlanName.isNotBlank()) "LOCKED ON ${userPlanName.uppercase()} PLAN • UPGRADE" else "LOCKED ON CURRENT PLAN",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (media.isOriginal || media.type == MediaType.TV_SHOW) {
                    NSeriesBadge(
                        nSize = 13.dp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                } else if (media.type == MediaType.MOVIE) {
                    NFilmBadge(
                        nSize = 13.dp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // Title Logo or Cinema Typography
                if (!media.logoUrl.isNullOrBlank()) {
                    var isLogoError by remember(media.logoUrl) { mutableStateOf(false) }
                    if (!isLogoError) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(media.logoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = media.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .heightIn(min = 44.dp, max = 85.dp)
                                .clickable { onInfoClick() }
                                .testTag("hero_logo_title"),
                            onError = { isLogoError = true }
                        )
                    } else {
                        Text(
                            text = media.title.uppercase(),
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = (-0.5).sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 30.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clickable { onInfoClick() }
                                .testTag("hero_title")
                        )
                    }
                } else {
                    Text(
                        text = media.title.uppercase(),
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = (-0.5).sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 30.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable { onInfoClick() }
                            .testTag("hero_title")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Metadata Badges (Match %, Release Year, Rating, Seasons/Duration, 4K)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    // Match %
                    if (media.matchPercentage > 0) {
                        Text(
                            text = "${media.matchPercentage}% Match",
                            color = Color(0xFF46D369),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Release Year
                    if (media.releaseYear > 0) {
                        Text(
                            text = "${media.releaseYear}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Maturity / Age Rating badge
                    ClayBadge(
                        text = if (media.maturityRating.isNotBlank()) media.maturityRating else "16+",
                        surfaceColor = Color(0xFF25232D),
                        textColor = Color.White.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Seasons or Duration
                    val lengthTag = media.durationOrSeasons.ifBlank {
                        if (media.type == MediaType.TV_SHOW) "1 Season" else "2h 14m"
                    }
                    Text(
                        text = lengthTag,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // 4K Ultra HD badge
                    ClayBadge(
                        text = "4K",
                        surfaceColor = Color(0xFF211F28),
                        textColor = Color.White.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(4.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Genre tags with separator dots
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    val displayGenres = media.genres.take(3)
                    displayGenres.forEachIndexed { index, genre ->
                        Text(
                            text = genre,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (index < displayGenres.size - 1) {
                            Text(
                                text = " • ",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 3.dp)
                            )
                        }
                    }
                }

                // Tactile Clay Action Buttons Row: Play, My List, and Info Details
                val playInteractionSource = remember { MutableInteractionSource() }
                val isPlayPressed by playInteractionSource.collectIsPressedAsState()
                val playScale by animateFloatAsState(
                    targetValue = if (isPlayPressed) 0.95f else 1.0f,
                    animationSpec = tween(durationMillis = 120),
                    label = "play_scale"
                )

                val listInteractionSource = remember { MutableInteractionSource() }
                val isListPressed by listInteractionSource.collectIsPressedAsState()
                val listScale by animateFloatAsState(
                    targetValue = if (isListPressed) 0.95f else 1.0f,
                    animationSpec = tween(durationMillis = 120),
                    label = "list_scale"
                )

                val infoInteractionSource = remember { MutableInteractionSource() }
                val isInfoPressed by infoInteractionSource.collectIsPressedAsState()
                val infoScale by animateFloatAsState(
                    targetValue = if (isInfoPressed) 0.94f else 1.0f,
                    animationSpec = tween(durationMillis = 120),
                    label = "info_scale"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Primary Action Button (Play or Unlock) with Tactile Claymorphism
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .weight(1.2f)
                            .graphicsLayer {
                                scaleX = playScale
                                scaleY = playScale
                            }
                            .claymorphic(
                                shape = RoundedCornerShape(12.dp),
                                surfaceColor = if (isLocked) NetflixRed else Color(0xFFF6F6F8),
                                highlightColor = Color.White,
                                shadowColor = if (isLocked) NetflixRed.copy(alpha = 0.6f) else Color.Black,
                                elevation = if (isPlayPressed) 2.dp else 8.dp,
                                strokeWidth = 1.3.dp,
                                highlightAlpha = if (isLocked) 0.55f else 0.82f,
                                depthAlpha = if (isLocked) 0.58f else 0.30f,
                                gradientCurvature = 0.22f
                            )
                            .clickable(
                                interactionSource = playInteractionSource,
                                indication = null,
                                onClick = onPlayClick
                            )
                            .testTag("hero_play_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.PlayArrow,
                                contentDescription = if (isLocked) "Locked" else "Play",
                                tint = if (isLocked) Color.White else Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isLocked) "Unlock" else "Play",
                                color = if (isLocked) Color.White else Color.Black,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Secondary Button (My List) with Dark Tactile Claymorphism
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = listScale
                                scaleY = listScale
                            }
                            .claymorphic(
                                shape = RoundedCornerShape(12.dp),
                                surfaceColor = Color(0xFF24222C),
                                highlightColor = Color.White,
                                shadowColor = Color.Black,
                                elevation = if (isListPressed) 2.dp else 6.dp,
                                strokeWidth = 1.3.dp,
                                highlightAlpha = 0.52f,
                                depthAlpha = 0.74f,
                                gradientCurvature = 0.24f
                            )
                            .clickable(
                                interactionSource = listInteractionSource,
                                indication = null,
                                onClick = onWatchlistToggle
                            )
                            .testTag("hero_watchlist_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isInWatchlist) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = "My List",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isInWatchlist) "In List" else "My List",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Info Button to open Details Sheet with Dark Tactile Claymorphism
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = infoScale
                                scaleY = infoScale
                            }
                            .claymorphic(
                                shape = RoundedCornerShape(12.dp),
                                surfaceColor = Color(0xFF24222C),
                                highlightColor = Color.White,
                                shadowColor = Color.Black,
                                elevation = if (isInfoPressed) 2.dp else 6.dp,
                                strokeWidth = 1.3.dp,
                                highlightAlpha = 0.52f,
                                depthAlpha = 0.74f,
                                gradientCurvature = 0.24f
                            )
                            .clickable(
                                interactionSource = infoInteractionSource,
                                indication = null,
                                onClick = onInfoClick
                            )
                            .testTag("hero_info_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Details",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
