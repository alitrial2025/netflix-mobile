package com.example.ui.screens

import android.content.Intent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.CatalogData
import com.example.data.TrailerResolver
import com.example.data.TrailerResolverCallback
import com.example.data.TrailerStream
import com.example.data.local.WatchProgressEntity
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.UserSubscription
import com.example.ui.components.MediaPosterCard
import com.example.ui.components.NFilmBadge
import com.example.ui.components.NSeriesBadge
import com.example.ui.components.NetflixRatingAction
import com.example.ui.components.NetflixSpinner
import com.example.ui.components.claymorphic
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixBorderGray
import com.example.ui.theme.NetflixCardBg
import com.example.ui.theme.NetflixDarkGray
import com.example.ui.theme.NetflixGreen
import com.example.ui.theme.NetflixRed

@OptIn(UnstableApi::class)
@Composable
fun DetailScreen(
    media: MediaItem,
    isInWatchlist: Boolean,
    userSubscription: UserSubscription = UserSubscription(),
    watchProgressList: List<WatchProgressEntity> = emptyList(),
    downloadProgressMap: Map<String, Float>,
    pausedDownloadKeys: Set<String> = emptySet(),
    completedDownloadKeys: Set<String> = emptySet(),
    onClose: () -> Unit,
    onPlayClick: (MediaItem, Episode?) -> Unit,
    onPlayTrailerClick: (MediaItem, String) -> Unit,
    onWatchlistToggle: () -> Unit,
    onDownloadClick: (MediaItem, Episode?) -> Unit,
    onRatingSelect: (String) -> Unit,
    onSimilarMediaClick: (MediaItem) -> Unit,
    onSeasonSelect: (Int) -> Unit = {},
    onPauseDownload: (String) -> Unit = {},
    onResumeDownload: (String) -> Unit = {},
    onCancelDownload: (String) -> Unit = {},
    onOpenAuth: () -> Unit = {},
    onOpenSubscription: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(true) }
    var selectedTabIdx by remember { mutableStateOf(0) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    val mediaProgressList = remember(watchProgressList, media.id) {
        watchProgressList.filter { it.mediaId == media.id }
    }
    val latestProgress = remember(mediaProgressList) {
        mediaProgressList.maxByOrNull { it.lastWatchedTimestamp }
    }

    var selectedSeason by remember(media.id, latestProgress) {
        mutableStateOf("Season ${latestProgress?.season ?: 1}")
    }
    var showSeasonDropdown by remember { mutableStateOf(false) }

    val selectedSeasonNumber = remember(selectedSeason) {
        selectedSeason.removePrefix("Season ").trim().toIntOrNull() ?: 1
    }

    LaunchedEffect(media.id, selectedSeasonNumber) {
        if (media.type == MediaType.TV_SHOW && (media.episodes.isEmpty() || !media.episodes.any { it.id.contains("_S${selectedSeasonNumber}_") })) {
            onSeasonSelect(selectedSeasonNumber)
        }
    }

    // Dynamic dominant accent color blending
    val dominantColor = remember(media.primaryColorHex) {
        val c = Color(media.primaryColorHex)
        if (c == Color.Unspecified || c == Color.Black) Color(0xFFE50914) else c
    }

    // Live Trailer Player in Detail Header
    var isTrailerResolving by remember { mutableStateOf(true) }
    var trailerStreamUrl by remember { mutableStateOf<String?>(null) }
    var trailerHeaders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
            volume = 0f
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(media.id) {
        isTrailerResolving = true
        val mediaTypeStr = if (media.type == MediaType.MOVIE) "movie" else "tv"
        val resolver = TrailerResolver(
            context = context,
            tmdbId = media.id,
            mediaType = mediaTypeStr,
            callback = object : TrailerResolverCallback {
                override fun onResolved(stream: TrailerStream) {
                    trailerStreamUrl = stream.url
                    trailerHeaders = stream.headers
                    isTrailerResolving = false

                    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true)
                        .setConnectTimeoutMs(15000)
                        .setReadTimeoutMs(15000)

                    stream.headers.forEach { (k, v) ->
                        httpDataSourceFactory.setDefaultRequestProperties(mapOf(k to v))
                    }

                    val mediaItem = Media3Item.Builder()
                        .setUri(stream.url)
                        .apply {
                            if (stream.url.contains(".m3u8", ignoreCase = true) || stream.type == "hls") {
                                setMimeType(MimeTypes.APPLICATION_M3U8)
                            } else if (stream.type == "dash") {
                                setMimeType(MimeTypes.APPLICATION_MPD)
                            } else {
                                setMimeType(MimeTypes.VIDEO_MP4)
                            }
                        }
                        .build()

                    val mediaSource = if (stream.url.contains(".m3u8", ignoreCase = true) || stream.type == "hls") {
                        HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
                    } else {
                        ProgressiveMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
                    }

                    exoPlayer.setMediaSource(mediaSource)
                    exoPlayer.prepare()
                    exoPlayer.play()
                }

                override fun onError(error: String) {
                    isTrailerResolving = false
                }
            }
        )
        resolver.start()
    }

    val scrollState = rememberScrollState()

    val similarItems = if (media.similarMedia.isNotEmpty()) media.similarMedia else remember(media) {
        CatalogData.allMedia.filter { it.id != media.id && it.genres.any { g -> media.genres.contains(g) } }.take(9)
    }

    val handlePlay = remember(onPlayClick, selectedSeasonNumber, latestProgress) {
        { m: MediaItem, ep: Episode? ->
            exoPlayer.pause()
            val targetEpisode = ep ?: if (m.type == MediaType.TV_SHOW) {
                val resumeSeason = latestProgress?.season ?: selectedSeasonNumber
                val resumeEpisodeId = latestProgress?.episodeId
                m.episodes.firstOrNull { it.id == resumeEpisodeId } ?:
                m.episodes.firstOrNull { e ->
                    e.id.contains("_S${resumeSeason}_")
                } ?: if (m.episodes.isNotEmpty() && resumeSeason == 1) {
                    m.episodes.first()
                } else {
                    Episode(
                        id = "ep_${m.id}_S${resumeSeason}_1",
                        episodeNumber = 1,
                        title = "Episode 1",
                        durationMinutes = 45,
                        description = "Season $resumeSeason, Episode 1"
                    )
                }
            } else {
                null
            }
            onPlayClick(m, targetEpisode)
        }
    }

    val onPlayEp = remember(handlePlay, media) { { ep: Episode -> handlePlay(media, ep) } }
    val onDownloadEp = remember(onDownloadClick, media) { { ep: Episode -> onDownloadClick(media, ep) } }
    val onPauseEp = remember(onPauseDownload) { { key: String -> onPauseDownload(key) } }
    val onResumeEp = remember(onResumeDownload) { { key: String -> onResumeDownload(key) } }
    val onCancelEp = remember(onCancelDownload) { { key: String -> onCancelDownload(key) } }

    val backdropModel = media.backdropUrl ?: media.posterUrl ?: media.bannerDrawableRes

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070709))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ==========================================
            // 1. HERO TRAILER PLAYER / VIDEO PREVIEW
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(236.dp)
                    .background(Color.Black)
            ) {
                // High-resolution backdrop image fallback always behind player
                if (backdropModel != null) {
                    AsyncImage(
                        model = backdropModel,
                        contentDescription = media.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Live ExoPlayer Trailer Stream View
                if (trailerStreamUrl != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                player = exoPlayer
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { handlePlay(media, null) }
                    )
                } else if (isTrailerResolving) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        NetflixSpinner(size = 38.dp)
                    }
                }

                // Top & Bottom Vignette Gradients for Flawless Icon and Card Blending
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f),
                                    Color(0xFF0C0C10)
                                )
                            )
                        )
                )

                // Top Bar: Tactile Clay Floating Controls (Close, Audio, Share)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .claymorphic(
                                shape = CircleShape,
                                surfaceColor = Color(0xFF201E29),
                                highlightColor = Color.White,
                                shadowColor = Color.Black,
                                elevation = 6.dp,
                                strokeWidth = 1.2.dp,
                                highlightAlpha = 0.55f,
                                depthAlpha = 0.78f,
                                gradientCurvature = 0.22f
                            )
                            .clickable { onClose() }
                            .testTag("detail_close_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Top Right Actions Row (Mute/Unmute & Share)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Share Button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .claymorphic(
                                    shape = CircleShape,
                                    surfaceColor = Color(0xFF201E29),
                                    highlightColor = Color.White,
                                    shadowColor = Color.Black,
                                    elevation = 6.dp,
                                    strokeWidth = 1.2.dp,
                                    highlightAlpha = 0.55f,
                                    depthAlpha = 0.78f,
                                    gradientCurvature = 0.22f
                                )
                                .clickable {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Check out '${media.title}' on Netflix! ${media.tagline}"
                                        )
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Title"))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_netflix_share),
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Mute / Unmute Button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .claymorphic(
                                    shape = CircleShape,
                                    surfaceColor = Color(0xFF201E29),
                                    highlightColor = Color.White,
                                    shadowColor = Color.Black,
                                    elevation = 6.dp,
                                    strokeWidth = 1.2.dp,
                                    highlightAlpha = 0.55f,
                                    depthAlpha = 0.78f,
                                    gradientCurvature = 0.22f
                                )
                                .clickable { isMuted = !isMuted },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) "Unmute Trailer" else "Mute Trailer",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Broadcast Style "PREVIEW" Badge in Bottom-Left: Tactile white pill with black text (no dot)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 22.dp)
                        .claymorphic(
                            shape = RoundedCornerShape(14.dp),
                            surfaceColor = Color.White,
                            highlightColor = Color.White,
                            shadowColor = Color.Black,
                            elevation = 6.dp,
                            strokeWidth = 1.dp,
                            highlightAlpha = 0.88f,
                            depthAlpha = 0.25f,
                            gradientCurvature = 0.12f
                        )
                        .padding(horizontal = 10.dp, vertical = 4.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (trailerStreamUrl != null) "PREVIEW" else "TRAILER",
                        color = Color.Black,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                // Center Play Button Overlay on Trailer
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .align(Alignment.Center)
                        .claymorphic(
                            shape = CircleShape,
                            surfaceColor = Color(0xFF24222E),
                            highlightColor = Color.White,
                            shadowColor = Color.Black,
                            elevation = 8.dp,
                            strokeWidth = 1.4.dp,
                            highlightAlpha = 0.65f,
                            depthAlpha = 0.80f,
                            gradientCurvature = 0.24f
                        )
                        .clickable { handlePlay(media, null) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // =========================================================================
            // 2. SCROLLABLE CARD ROUNDED AT TOP JUST BELOW TRAILER WITH CLAYMORPHISM BG
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .offset(y = (-14).dp) // Seamless overlap under bottom edge of trailer
                    .claymorphic(
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        surfaceColor = Color(0xFF191822),
                        highlightColor = Color.White,
                        shadowColor = Color.Black,
                        elevation = 14.dp,
                        strokeWidth = 1.4.dp,
                        highlightAlpha = 0.55f,
                        depthAlpha = 0.82f,
                        gradientCurvature = 0.24f
                    )
            ) {
                // Ambient luminous glow at top of sheet
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    dominantColor.copy(alpha = 0.22f),
                                    NetflixRed.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                center = Offset(x = 500f, y = 0f),
                                radius = 700f
                            )
                        )
                )

                // Scrollable Content Column inside Card
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 72.dp)
                ) {
                    // Tactile pill handle at top
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp, bottom = 12.dp)
                            .width(42.dp)
                            .height(5.dp)
                            .claymorphic(
                                shape = CircleShape,
                                surfaceColor = Color(0xFF383546),
                                highlightColor = Color.White,
                                shadowColor = Color.Black,
                                elevation = 3.dp,
                                strokeWidth = 1.dp,
                                highlightAlpha = 0.60f,
                                depthAlpha = 0.65f,
                                gradientCurvature = 0.20f
                            )
                    )

                    // Netflix N-Series/Film Badge + Top 10 Rank
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        if (media.isOriginal) {
                            if (media.type == MediaType.TV_SHOW) NSeriesBadge() else NFilmBadge()
                        }
                        if (media.top10Rank != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(NetflixRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "#${media.top10Rank}",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (media.type == MediaType.TV_SHOW) "#${media.top10Rank} in TV Shows Today" else "#${media.top10Rank} in Movies Today",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Cinematic Title (Logo or Bold Typography)
                    var isLogoError by remember(media.logoUrl) { mutableStateOf(false) }
                    if (!media.logoUrl.isNullOrBlank() && !isLogoError) {
                        AsyncImage(
                            model = media.logoUrl,
                            contentDescription = media.title,
                            contentScale = ContentScale.Fit,
                            onError = { isLogoError = true },
                            modifier = Modifier
                                .heightIn(max = 64.dp)
                                .padding(vertical = 4.dp)
                        )
                    } else {
                        Text(
                            text = media.title.ifBlank { "Untitled" },
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = (-0.5).sp,
                            lineHeight = 30.sp
                        )
                    }

                    // Tagline (Italic display quote)
                    if (media.tagline.isNotBlank()) {
                        Text(
                            text = "“${media.tagline}”",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Metadata Badges Row (Match Pill, Year, Rating, Seasons, Resolution)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        // Glowing Emerald Match Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF0F3620))
                                .border(0.8.dp, Color(0xFF46D369).copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${media.matchPercentage}% Match",
                                color = Color(0xFF46D369),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "${media.releaseYear}",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Age Rating Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = media.maturityRating,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = media.durationOrSeasons,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Quality Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .border(0.8.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = "4K UHD",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Genre Pills Row
                    if (media.genres.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            media.genres.take(4).forEach { genre ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 10.dp, vertical = 3.5.dp)
                                ) {
                                    Text(
                                        text = genre,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Resume Watching Banner (if user already started watching)
                    val isPartiallyWatched = latestProgress != null && latestProgress.positionSeconds > 10
                    if (isPartiallyWatched && latestProgress != null) {
                        val remainingMin = maxOf(1, (latestProgress.totalSeconds - latestProgress.positionSeconds) / 60)
                        val frac = (latestProgress.positionSeconds.toFloat() / maxOf(1, latestProgress.totalSeconds).toFloat()).coerceIn(0.05f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .claymorphic(
                                    shape = RoundedCornerShape(12.dp),
                                    surfaceColor = Color(0xFF201E2B),
                                    highlightColor = Color.White,
                                    shadowColor = Color.Black,
                                    elevation = 6.dp,
                                    strokeWidth = 1.2.dp,
                                    highlightAlpha = 0.50f,
                                    depthAlpha = 0.78f,
                                    gradientCurvature = 0.22f
                                )
                                .clickable { handlePlay(media, null) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (media.type == MediaType.TV_SHOW)
                                            "Resume S${latestProgress.season}:E${latestProgress.episode} • ${latestProgress.episodeTitle ?: "Episode ${latestProgress.episode}"}"
                                        else "Resume ${media.title}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$remainingMin min remaining",
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 11.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { frac },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.5.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = NetflixRed,
                                        trackColor = Color.White.copy(alpha = 0.15f)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Resume Watching",
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Subscription Locked Alert
                    val isLocked = remember(userSubscription, media.id, media.title) {
                        userSubscription.isMediaLocked(media.id, media.title)
                    }
                    if (isLocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(NetflixRed.copy(alpha = 0.15f))
                                .border(1.dp, NetflixRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .clickable {
                                    if (userSubscription.isGuest) onOpenAuth() else onOpenSubscription()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("detail_lock_banner")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = NetflixRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (userSubscription.isGuest) "LOCKED FOR GUESTS • SIGN IN TO STREAM FULL TITLE"
                                    else "LOCKED ON ${userSubscription.planName.uppercase()} PLAN • UPGRADE TO WATCH",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Big High-Contrast Primary Play / Resume Button
                    Button(
                        onClick = {
                            if (isLocked) {
                                if (userSubscription.isGuest) onOpenAuth() else onOpenSubscription()
                            } else {
                                handlePlay(media, null)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLocked) NetflixRed else Color.White,
                            contentColor = if (isLocked) Color.White else Color.Black
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("detail_play_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.PlayArrow,
                                contentDescription = if (isLocked) "Locked" else "Play",
                                tint = if (isLocked) Color.White else Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isLocked) {
                                    if (userSubscription.isGuest) "Sign In to Stream Full Title"
                                    else "Unlock Title (${userSubscription.planName} Plan)"
                                }
                                else if (isPartiallyWatched) "Resume"
                                else "Play",
                                color = if (isLocked) Color.White else Color.Black,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary Action: Download Button & Controls
                    val isDownloaded = completedDownloadKeys.contains(media.id)
                    val isDownloading = downloadProgressMap.containsKey(media.id) && !pausedDownloadKeys.contains(media.id)
                    val isPaused = downloadProgressMap.containsKey(media.id) && pausedDownloadKeys.contains(media.id)
                    val progressVal = downloadProgressMap[media.id] ?: 0f

                    if (isDownloading || isPaused) {
                        val pct = (progressVal * 100).toInt()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(NetflixDarkGray)
                                .border(1.dp, NetflixBorderGray, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                                .testTag("detail_download_progress_card")
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        NetflixSpinner(
                                            size = 20.dp,
                                            percentage = pct
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = if (isPaused) "Paused • $pct%" else "Downloading... $pct%",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Pause / Resume Button
                                        Button(
                                            onClick = {
                                                if (isPaused) {
                                                    onResumeDownload(media.id)
                                                } else {
                                                    onPauseDownload(media.id)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White.copy(alpha = 0.15f),
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .height(32.dp)
                                                .testTag("detail_pause_resume_button")
                                        ) {
                                            Icon(
                                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                                contentDescription = if (isPaused) "Resume" else "Pause",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isPaused) "Resume" else "Pause",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Cancel Button
                                        Button(
                                            onClick = { onCancelDownload(media.id) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = NetflixRed.copy(alpha = 0.2f),
                                                contentColor = NetflixRed
                                            ),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .height(32.dp)
                                                .testTag("detail_cancel_download_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Cancel",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Cancel",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { progressVal },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = if (isPaused) Color(0xFFF59E0B) else Color(0xFF0071EB),
                                    trackColor = Color.White.copy(alpha = 0.2f)
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = { onDownloadClick(media, null) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDownloaded) Color(0xFF1E293B) else Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("detail_download_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isDownloaded) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Downloaded",
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_netflix_download_custom),
                                        contentDescription = "Download",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isDownloaded) "Downloaded (Delete)" else "Download",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Synopsis Description (Expandable)
                    Text(
                        text = media.description,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (media.description.length > 120) {
                        Text(
                            text = if (isDescriptionExpanded) "Show Less" else "Show More",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                .padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Cast & Creators Two-Tone Text
                    if (media.cast.isNotEmpty()) {
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                text = "Starring: ",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = media.cast.joinToString(", "),
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                    if (media.director.isNotBlank()) {
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                text = "Creators: ",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = media.director,
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Icons Row: My List | Rate | Share
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .claymorphic(
                                shape = RoundedCornerShape(14.dp),
                                surfaceColor = Color(0xFF201E2B),
                                highlightColor = Color.White,
                                shadowColor = Color.Black,
                                elevation = 6.dp,
                                strokeWidth = 1.2.dp,
                                highlightAlpha = 0.50f,
                                depthAlpha = 0.76f,
                                gradientCurvature = 0.22f
                            )
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // My List
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { onWatchlistToggle() }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .testTag("detail_watchlist_button")
                        ) {
                            if (isInWatchlist) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "My List",
                                    tint = NetflixRed,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_netflix_plus),
                                    contentDescription = "My List",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isInWatchlist) "In List" else "My List",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Rate Action with Netflix Floating Pill
                        NetflixRatingAction(
                            onRatingSelect = onRatingSelect
                        )

                        // Share
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Check out '${media.title}' on Netflix! ${media.tagline}")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Title"))
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .testTag("detail_share_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_netflix_share),
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Share",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // ==========================================
                    // 3. TABS: Episodes, More Like This, Trailers, Details
                    // ==========================================
                    TabRow(
                        selectedTabIndex = selectedTabIdx,
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIdx]),
                                color = NetflixRed,
                                height = 3.dp
                            )
                        },
                        divider = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.12f))
                            )
                        }
                    ) {
                        if (media.type == MediaType.TV_SHOW) {
                            Tab(
                                selected = selectedTabIdx == 0,
                                onClick = { selectedTabIdx = 0 },
                                text = { Text("Episodes", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            )
                        }
                        Tab(
                            selected = (media.type != MediaType.TV_SHOW && selectedTabIdx == 0) || (media.type == MediaType.TV_SHOW && selectedTabIdx == 1),
                            onClick = { selectedTabIdx = if (media.type == MediaType.TV_SHOW) 1 else 0 },
                            text = { Text("More Like This", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        )
                        Tab(
                            selected = (media.type != MediaType.TV_SHOW && selectedTabIdx == 1) || (media.type == MediaType.TV_SHOW && selectedTabIdx == 2),
                            onClick = { selectedTabIdx = if (media.type == MediaType.TV_SHOW) 2 else 1 },
                            text = { Text("Trailers & More", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        )
                        Tab(
                            selected = (media.type != MediaType.TV_SHOW && selectedTabIdx == 2) || (media.type == MediaType.TV_SHOW && selectedTabIdx == 3),
                            onClick = { selectedTabIdx = if (media.type == MediaType.TV_SHOW) 3 else 2 },
                            text = { Text("About", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tab Content Switcher
                    when {
                        // EPISODES TAB
                        media.type == MediaType.TV_SHOW && selectedTabIdx == 0 -> {
                            // Season Dropdown Selector Pill
                            Box(modifier = Modifier.padding(vertical = 8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .claymorphic(
                                            shape = RoundedCornerShape(8.dp),
                                            surfaceColor = Color(0xFF242230),
                                            highlightColor = Color.White,
                                            shadowColor = Color.Black,
                                            elevation = 4.dp,
                                            strokeWidth = 1.1.dp,
                                            highlightAlpha = 0.50f,
                                            depthAlpha = 0.75f,
                                            gradientCurvature = 0.20f
                                        )
                                        .clickable { showSeasonDropdown = true }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = selectedSeason,
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Season",
                                        tint = Color.White
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSeasonDropdown,
                                    onDismissRequest = { showSeasonDropdown = false },
                                    modifier = Modifier.background(NetflixDarkGray)
                                ) {
                                    val seasonCount = maxOf(1, media.totalSeasons)
                                    (1..seasonCount).forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text("Season $s", color = Color.White, fontWeight = FontWeight.SemiBold) },
                                            onClick = {
                                                selectedSeason = "Season $s"
                                                showSeasonDropdown = false
                                                onSeasonSelect(s)
                                            }
                                        )
                                    }
                                }
                            }

                            if (media.episodes.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    NetflixSpinner(size = 32.dp)
                                }
                            } else {
                                media.episodes.forEach { episode ->
                                    val epKey = remember(media.id, episode.id) { "${media.id}_${episode.id}" }
                                    val isEpDownloading = remember(downloadProgressMap, pausedDownloadKeys, epKey) {
                                        downloadProgressMap.containsKey(epKey) && !pausedDownloadKeys.contains(epKey)
                                    }
                                    val isEpPaused = remember(downloadProgressMap, pausedDownloadKeys, epKey) {
                                        downloadProgressMap.containsKey(epKey) && pausedDownloadKeys.contains(epKey)
                                    }
                                    val isEpDownloaded = remember(completedDownloadKeys, epKey) {
                                        completedDownloadKeys.contains(epKey)
                                    }
                                    val progress = downloadProgressMap[epKey] ?: 0f

                                    val epWatchProgress = remember(mediaProgressList, episode.id) {
                                        val ent = mediaProgressList.find { it.episodeId == episode.id }
                                        if (ent != null && ent.totalSeconds > 0) {
                                            ent.positionSeconds.toFloat() / ent.totalSeconds.toFloat()
                                        } else null
                                    }

                                    EpisodeItemRow(
                                        episode = episode,
                                        epKey = epKey,
                                        fallbackPosterUrl = media.backdropUrl ?: media.posterUrl,
                                        isDownloading = isEpDownloading,
                                        isPaused = isEpPaused,
                                        isDownloaded = isEpDownloaded,
                                        downloadProgress = progress,
                                        watchProgress = epWatchProgress,
                                        isLocked = isLocked,
                                        onPlayEpisode = onPlayEp,
                                        onDownloadEpisode = onDownloadEp,
                                        onPauseEpisode = onPauseEp,
                                        onResumeEpisode = onResumeEp,
                                        onCancelEpisode = onCancelEp
                                    )
                                }
                            }
                        }

                        // MORE LIKE THIS TAB
                        (media.type != MediaType.TV_SHOW && selectedTabIdx == 0) ||
                        (media.type == MediaType.TV_SHOW && selectedTabIdx == 1) -> {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                // 3 Columns Grid of Recommended Posters
                                val rows = similarItems.chunked(3)
                                rows.forEach { rowItems ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowItems.forEach { sim ->
                                            MediaPosterCard(
                                                media = sim,
                                                width = 108.dp,
                                                height = 154.dp,
                                                onClick = { onSimilarMediaClick(sim) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        // Fill empty spaces in row if count < 3
                                        repeat(3 - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        // TRAILERS & MORE TAB
                        (media.type != MediaType.TV_SHOW && selectedTabIdx == 1) ||
                        (media.type == MediaType.TV_SHOW && selectedTabIdx == 2) -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                TrailerPreviewItem(
                                    title = "Official Teaser: ${media.title}",
                                    duration = "1m 45s",
                                    backdropUrl = media.backdropUrl ?: media.posterUrl,
                                    onPlay = { onPlayTrailerClick(media, "Official Teaser: ${media.title}") }
                                )
                                TrailerPreviewItem(
                                    title = "Behind The Scenes & Director Commentary",
                                    duration = "3m 12s",
                                    backdropUrl = media.backdropUrl ?: media.posterUrl,
                                    onPlay = { onPlayTrailerClick(media, "Behind The Scenes: ${media.title}") }
                                )
                            }
                        }

                        // ABOUT / DETAILS TAB
                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SpecItem(label = "Audio", value = "English [Original] (Dolby Atmos, 5.1), Spanish, French, German, Italian, Japanese")
                                SpecItem(label = "Subtitles", value = "English [CC], Spanish, French, German, Italian, Simplified Chinese, Traditional Chinese")
                                SpecItem(label = "Genres", value = media.genres.joinToString(", "))
                                SpecItem(label = "This Title Is", value = "Mind-bending, Suspenseful, Atmospheric, Dystopian")
                                SpecItem(label = "Maturity Rating", value = "${media.maturityRating} • Recommended for ages 16 and up")
                                SpecItem(label = "Content Advisory", value = "Violence, language, frightening scenes, mature themes")
                                SpecItem(label = "Copyright", value = "© ${media.releaseYear} Netflix Studios, LLC. All rights reserved.")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun EpisodeItemRow(
    episode: Episode,
    epKey: String,
    fallbackPosterUrl: String? = null,
    isDownloading: Boolean,
    isPaused: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Float,
    watchProgress: Float? = null,
    isLocked: Boolean = false,
    onPlayEpisode: (Episode) -> Unit,
    onDownloadEpisode: (Episode) -> Unit,
    onPauseEpisode: (String) -> Unit = {},
    onResumeEpisode: (String) -> Unit = {},
    onCancelEpisode: (String) -> Unit = {}
) {
    val imageUrl = remember(episode.stillUrl, fallbackPosterUrl) { episode.stillUrl ?: fallbackPosterUrl }
    val pct = remember(downloadProgress) { (downloadProgress * 100).toInt() }

    val onClickPlay = remember(episode, onPlayEpisode) { { onPlayEpisode(episode) } }
    val onClickDownload = remember(episode, onDownloadEpisode) { { onDownloadEpisode(episode) } }
    val onClickPauseResume = remember(isPaused, epKey, onResumeEpisode, onPauseEpisode) {
        { if (isPaused) onResumeEpisode(epKey) else onPauseEpisode(epKey) }
    }
    val onClickCancel = remember(epKey, onCancelEpisode) { { onCancelEpisode(epKey) } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClickPlay)
            .padding(vertical = 12.dp)
            .testTag("episode_item_${episode.episodeNumber}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 16:9 Thumbnail with Rounded Corners & Play Button Overlay
                Box(
                    modifier = Modifier
                        .width(122.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NetflixCardBg)
                        .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                ) {
                    if (!imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = episode.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    // Tactile Clay Play Circle
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .claymorphic(
                                shape = CircleShape,
                                surfaceColor = Color(0xFF22202C).copy(alpha = 0.90f),
                                highlightColor = Color.White,
                                shadowColor = Color.Black,
                                elevation = 4.dp,
                                strokeWidth = 1.1.dp,
                                highlightAlpha = 0.65f,
                                depthAlpha = 0.72f,
                                gradientCurvature = 0.22f
                            )
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.PlayArrow,
                            contentDescription = if (isLocked) "Locked on Plan" else "Play Episode",
                            tint = if (isLocked) NetflixRed else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // Netflix Red watch progress bar
                    if (watchProgress != null && watchProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(watchProgress)
                                .height(3.5.dp)
                                .background(NetflixRed)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "${episode.episodeNumber}. ${episode.title}",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${episode.durationMinutes}m",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Episode Download Controls
            if (isDownloading || isPaused) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NetflixSpinner(
                        size = 18.dp,
                        percentage = pct
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onClickPauseResume,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .testTag("ep_pause_resume_${episode.episodeNumber}")
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume Episode Download" else "Pause Episode Download",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onClickCancel,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .testTag("ep_cancel_${episode.episodeNumber}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Episode Download",
                            tint = NetflixRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = onClickDownload,
                    modifier = Modifier.testTag("download_episode_${episode.episodeNumber}")
                ) {
                    if (isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Sign in to download",
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(20.dp)
                        )
                    } else if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_netflix_download_custom),
                            contentDescription = "Download Episode",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = episode.description,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun TrailerPreviewItem(
    title: String,
    duration: String,
    backdropUrl: Any? = null,
    onPlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .claymorphic(
                shape = RoundedCornerShape(12.dp),
                surfaceColor = Color(0xFF1E1C27),
                highlightColor = Color.White,
                shadowColor = Color.Black,
                elevation = 6.dp,
                strokeWidth = 1.2.dp,
                highlightAlpha = 0.50f,
                depthAlpha = 0.75f,
                gradientCurvature = 0.20f
            )
            .clickable { onPlay() }
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NetflixCardBg),
            contentAlignment = Alignment.Center
        ) {
            if (backdropUrl != null) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .claymorphic(
                        shape = CircleShape,
                        surfaceColor = Color(0xFF22202C).copy(alpha = 0.90f),
                        highlightColor = Color.White,
                        shadowColor = Color.Black,
                        elevation = 6.dp,
                        strokeWidth = 1.2.dp,
                        highlightAlpha = 0.65f,
                        depthAlpha = 0.75f,
                        gradientCurvature = 0.22f
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = duration,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
    }
}
