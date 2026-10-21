package com.example.ui.screens

import com.example.discovery.recommendationTitle

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.annotation.OptIn
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
import com.example.ui.components.referenceGlass
import com.example.data.CatalogData
import com.example.data.TrailerResolver
import com.example.data.TrailerResolverCallback
import com.example.data.TrailerStream
import com.example.data.local.ReminderEntity
import com.example.data.model.CastDevice
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.NotificationItem
import com.example.ui.components.NetflixSpinner
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixBorderGray
import com.example.ui.theme.NetflixCardBg
import com.example.ui.theme.NetflixRed
import kotlinx.coroutines.delay

enum class ClipsMode {
    VERTICAL_FEED, // TikTok style 9:16 vertical short clips
    COMING_SOON,    // Standard Coming Soon timeline
    TOP_10          // Top 10 list
}

/**
 * 2026 Netflix "Clips" Vertical Video Feed Screen (TikTok-style Immersive Short Content)
 */
@Composable
fun ClipsScreen(
    reminders: List<ReminderEntity> = emptyList(),
    likedMedia: List<MediaItem> = emptyList(),
    notifications: List<NotificationItem> = emptyList(),
    connectedCastDevice: CastDevice? = null,
    onToggleReminder: (MediaItem) -> Unit = {},
    onToggleLike: (String) -> Unit = {},
    onMediaClick: (MediaItem) -> Unit = {},
    onPlayClick: (MediaItem) -> Unit = {},
    onWatchlistToggle: (MediaItem) -> Unit = {},
    isWatchlistContains: (String) -> Boolean = { false },
    onOpenNotifications: () -> Unit = {},
    onOpenCast: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onShowToast: (String) -> Unit = {},
    streamingAllowed: Boolean = true,
    streamingBlockedMessage: String = "Connect to Wi-Fi or change Cellular Data in App Settings to watch Clips.",
    maxVideoHeight: Int = Int.MAX_VALUE,
    spatialAudioEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val platformDesign = com.example.ui.theme.LocalPlatformDesign.current
    var clipsMode by remember { mutableStateOf(ClipsMode.VERTICAL_FEED) }
    val clipItems = remember {
        (CatalogData.getTrending() + CatalogData.getComingSoon()).distinctBy { "${it.type}:${it.id}" }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(platformDesign.background)
            .testTag("clips_screen")
    ) {
        if (!streamingAllowed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(streamingBlockedMessage, color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(28.dp))
            }
        } else when (clipsMode) {
            ClipsMode.VERTICAL_FEED -> {
                TikTokVerticalVideoFeed(
                    items = clipItems,
                    likedMedia = likedMedia,
                    isWatchlistContains = isWatchlistContains,
                    onToggleLike = onToggleLike,
                    onWatchlistToggle = onWatchlistToggle,
                    onMediaClick = onMediaClick,
                    onShowToast = onShowToast,
                    streamingAllowed = streamingAllowed,
                    maxVideoHeight = maxVideoHeight,
                    spatialAudioEnabled = spatialAudioEnabled
                )
            }

            ClipsMode.COMING_SOON -> {
                ComingSoonListMode(
                    items = CatalogData.getComingSoon(),
                    reminders = reminders,
                    onToggleReminder = onToggleReminder,
                    onMediaClick = onMediaClick,
                    onPlayClick = onPlayClick,
                    onOpenCast = onOpenCast,
                    onOpenSearch = onOpenSearch,
                    onOpenNotifications = onOpenNotifications,
                    unreadNotificationsCount = notifications.count { !it.isRead },
                    onSelectMode = { clipsMode = it }
                )
            }

            ClipsMode.TOP_10 -> {
                Top10ListMode(
                    tvShows = CatalogData.getTop10TvShows(),
                    movies = CatalogData.getTop10Movies(),
                    onMediaClick = onMediaClick,
                    onPlayClick = onPlayClick,
                    onOpenCast = onOpenCast,
                    onOpenSearch = onOpenSearch,
                    onOpenNotifications = onOpenNotifications,
                    unreadNotificationsCount = notifications.count { !it.isRead },
                    onSelectMode = { clipsMode = it }
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TikTokVerticalVideoFeed(
    items: List<MediaItem>,
    likedMedia: List<MediaItem>,
    isWatchlistContains: (String) -> Boolean,
    onToggleLike: (String) -> Unit,
    onWatchlistToggle: (MediaItem) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onShowToast: (String) -> Unit,
    streamingAllowed: Boolean,
    maxVideoHeight: Int,
    spatialAudioEnabled: Boolean
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isForeground by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var playbackRequested by remember { mutableStateOf(true) }

    // A clip starts muted, matching the discovery feed and avoiding surprise audio.
    // Local mute states per page index
    val mutedMap = remember { mutableStateMapOf<Int, Boolean>() }

    // Stream URL memory cache to eliminate repeat network requests and enable instant preloading
    val urlCache = remember { mutableStateMapOf<String, TrailerStream>() }

    // Single shared ExoPlayer instance with optimized load control for short video clips
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = false
        }
    }
    LaunchedEffect(maxVideoHeight, spatialAudioEnabled) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(Int.MAX_VALUE, maxVideoHeight).build()
        exoPlayer.setAudioAttributes(androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .setSpatializationBehavior(if (spatialAudioEnabled) androidx.media3.common.C.SPATIALIZATION_BEHAVIOR_AUTO
                else androidx.media3.common.C.SPATIALIZATION_BEHAVIOR_NEVER).build(), true)
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    var isResolving by remember { mutableStateOf(false) }
    var resolvedUrl by remember { mutableStateOf<String?>(null) }

    val currentPage = pagerState.currentPage
    val latestPage by androidx.compose.runtime.rememberUpdatedState(currentPage)
    val latestStreamingAllowed by androidx.compose.runtime.rememberUpdatedState(streamingAllowed)
    val isMuted = mutedMap[currentPage] ?: true

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    val latestPlaybackRequested by rememberUpdatedState(playbackRequested)
    val latestAllowed by rememberUpdatedState(streamingAllowed)
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isForeground = true
                    exoPlayer.playWhenReady = latestPlaybackRequested && latestAllowed
                }
                Lifecycle.Event.ON_PAUSE -> {
                    isForeground = false
                    exoPlayer.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Smart stream loader & preloader function
    fun prepareAndPlay(stream: TrailerStream) {
        if (!latestStreamingAllowed) return
        val url = stream.url
        resolvedUrl = url
        isResolving = false

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(stream.headers)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000)

        val isHls = stream.type.equals("hls", ignoreCase = true) || url.contains(".m3u8", ignoreCase = true)
        val mediaItem = Media3Item.Builder()
            .setUri(url)
            .apply {
                if (isHls) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                } else if (url.contains(".mpd", ignoreCase = true)) {
                    setMimeType(MimeTypes.APPLICATION_MPD)
                } else {
                    setMimeType(MimeTypes.VIDEO_MP4)
                }
            }
            .build()

        val mediaSource = if (isHls) {
            HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
        }

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = isForeground && playbackRequested
    }

    LaunchedEffect(currentPage, items, streamingAllowed) {
        val pending = mutableListOf<TrailerResolver>()
        try {
            if (!streamingAllowed) { exoPlayer.stop(); isResolving = false; return@LaunchedEffect }
            if (currentPage in items.indices) {
                val currentItem = items[currentPage]
                isResolving = true
                resolvedUrl = null
                playbackRequested = true
                exoPlayer.stop()

                val currentKey = "${currentItem.type}:${currentItem.id}"
                val cachedUrl = urlCache[currentKey]
                if (cachedUrl != null) {
                    prepareAndPlay(cachedUrl)
                } else {
                    val mediaTypeStr = if (currentItem.type == MediaType.MOVIE) "movie" else "tv"
                    val resolver = TrailerResolver(
                        context = context,
                        tmdbId = currentItem.id,
                        mediaType = mediaTypeStr,
                        callback = object : TrailerResolverCallback {
                            override fun onResolved(stream: TrailerStream) {
                                urlCache[currentKey] = stream
                                if (latestPage == currentPage && latestStreamingAllowed) prepareAndPlay(stream)
                            }

                            override fun onError(error: String) {
                                if (latestPage == currentPage && latestStreamingAllowed) isResolving = false
                            }
                        }
                    )
                    pending += resolver
                    resolver.start()
                }

                // Pre-resolve next item stream URL into cache for instant TikTok-style swipe without bloating RAM/WiFi
                val nextItem = items.getOrNull(currentPage + 1)
                if (nextItem != null && urlCache["${nextItem.type}:${nextItem.id}"] == null) {
                    val nextMediaType = if (nextItem.type == MediaType.MOVIE) "movie" else "tv"
                    val prefetch = TrailerResolver(
                        context = context,
                        tmdbId = nextItem.id,
                        mediaType = nextMediaType,
                        callback = object : TrailerResolverCallback {
                            override fun onResolved(stream: TrailerStream) {
                                urlCache["${nextItem.type}:${nextItem.id}"] = stream
                            }
                            override fun onError(error: String) {}
                        }
                    )
                    pending += prefetch
                    prefetch.start()
                }

                // Keep resolution jobs alive until this page leaves; callbacks are cancelled
                // with the effect rather than requesting another plan or update check.
                kotlinx.coroutines.awaitCancellation()
            }
        } finally { pending.forEach { it.cancel() } }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { page -> "${items[page].type}:${items[page].id}" },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items[page]
            val isLiked = likedMedia.any { it.id == item.id }
            val isInList = isWatchlistContains(item.id)
            val pageMuted = mutedMap[page] ?: true
            val isCurrent = pagerState.currentPage == page

            TikTokClipItemView(
                item = item,
                isCurrentPage = isCurrent,
                exoPlayer = exoPlayer,
                isResolving = if (isCurrent) isResolving else false,
                resolvedUrl = if (isCurrent) resolvedUrl else null,
                isLiked = isLiked,
                isInList = isInList,
                isMuted = pageMuted,
                onToggleMute = {
                    mutedMap[page] = !pageMuted
                    onShowToast(if (!pageMuted) "Audio Muted" else "Audio Unmuted")
                },
                onToggleLike = {
                    onToggleLike(item.id)
                },
                onToggleWatchlist = {
                    onWatchlistToggle(item)
                },
                onShare = {
                    val mediaType = if (item.type == MediaType.MOVIE) "movie" else "tv"
                    val titleUrl = "https://www.themoviedb.org/$mediaType/${item.id}"
                    val sendIntent = Intent(Intent.ACTION_SEND)
                    sendIntent.type = "text/plain"
                    sendIntent.putExtra(
                        Intent.EXTRA_TEXT,
                        "Check out '${item.title}' on NetflixPro Clips! $titleUrl"
                    )
                    context.startActivity(Intent.createChooser(sendIntent, "Share Clip"))
                },
                onMediaClick = { onMediaClick(item) },
                onPlaybackRequested = { playbackRequested = it }
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TikTokClipItemView(
    item: MediaItem,
    isCurrentPage: Boolean,
    exoPlayer: ExoPlayer,
    isResolving: Boolean,
    resolvedUrl: String?,
    isLiked: Boolean,
    isInList: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onShare: () -> Unit,
    onMediaClick: () -> Unit,
    onPlaybackRequested: (Boolean) -> Unit
) {
    val platformDesign = com.example.ui.theme.LocalPlatformDesign.current
    // Double tap heart animation state
    var showHeartAnimation by remember { mutableStateOf(false) }
    var showPlayPauseControls by remember { mutableStateOf(false) }
    var centerIconRes by remember { mutableStateOf<Int?>(null) }


    fun togglePlayPause() {
        if (exoPlayer.playWhenReady) {
            onPlaybackRequested(false)
            exoPlayer.pause()
            centerIconRes = R.drawable.ic_netflix_pause
        } else {
            onPlaybackRequested(true)
            exoPlayer.play()
            centerIconRes = R.drawable.ic_netflix_play
        }
        showPlayPauseControls = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLiked, isCurrentPage, resolvedUrl, onToggleLike, onPlaybackRequested) {
                detectTapGestures(
                    onDoubleTap = {
                        if (!isLiked) onToggleLike()
                        showHeartAnimation = true
                    },
                    onTap = { if (isCurrentPage && resolvedUrl != null) togglePlayPause() }
                )
            }
    ) {
        // Real title artwork stays visible until its genuine trailer is ready.
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.backdropUrl ?: item.posterUrl ?: item.bannerDrawableRes,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        }

        // Fill the portrait viewport exactly as the reference feed does.
        if (resolvedUrl != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view -> view.player = if (isCurrentPage) exoPlayer else null }
            )
        } else if (isResolving) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                NetflixSpinner(size = 44.dp)
            }
        } else {
            Text(
                text = "This clip is unavailable. Swipe up for the next title.",
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp)
            )
        }

        // Dark Top & Bottom Scrim Gradients for contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.12f),
                            Color.Black.copy(alpha = 0.82f)
                        )
                    )
                )
        )

        // Floating Double Tap Big Heart Burst
        AnimatedVisibility(
            visible = showHeartAnimation,
            enter = fadeIn() + scaleIn(animationSpec = spring()),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            LaunchedEffect(showHeartAnimation) {
                delay(800)
                showHeartAnimation = false
            }
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Liked",
                tint = platformDesign.accent,
                modifier = Modifier.size(110.dp)
            )
        }

        // Floating Center Play/Pause Control Overlay (Appears only on tap with auto fade-out)
        AnimatedVisibility(
            visible = showPlayPauseControls,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            LaunchedEffect(showPlayPauseControls) {
                if (showPlayPauseControls) {
                    delay(800)
                    showPlayPauseControls = false
                }
            }
            centerIconRes?.let { iconRes ->
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .clickable { togglePlayPause() }
                        .testTag("clip_center_play_pause_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = "Play Pause Feedback",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        ClipDiscoveryOverlay(
            item = item,
            isInList = isInList,
            isMuted = isMuted,
            onToggleMute = onToggleMute,
            onToggleWatchlist = onToggleWatchlist,
            onShare = onShare,
            onMediaClick = onMediaClick,
            modifier = Modifier.fillMaxSize(),
            isLiked = isLiked,
            onToggleLike = onToggleLike
        )
    }
}

/** The reference's caption and action rail, separated from playback for accessible interaction tests. */
@Composable
internal fun ClipDiscoveryOverlay(
    item: MediaItem,
    isInList: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onShare: () -> Unit,
    onMediaClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLiked: Boolean = false,
    onToggleLike: () -> Unit = {}
) {
    val platformDesign = com.example.ui.theme.LocalPlatformDesign.current
    Box(modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 74.dp, bottom = com.example.ui.components.navigationControlHeight() + 40.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable(onClick = onMediaClick)
                    .semantics {
                        stateDescription = if (isLiked) "Liked" else "Not liked"
                        customActions = listOf(
                            CustomAccessibilityAction(
                                label = if (isLiked) "Unlike ${item.title}" else "Like ${item.title}"
                            ) {
                                onToggleLike()
                                true
                            }
                        )
                    }
                    .testTag("clip_title")
            )
            if (item.genres.isNotEmpty()) {
                Text(
                    text = item.genres.joinToString(" • "),
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.description.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = "More about ${item.title}", onClick = onMediaClick)
                        .testTag("clip_description"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.description,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = " more",
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 158.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            ClipCircleActionButton(
                icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (isMuted) "Unmute clip" else "Mute clip",
                onClick = onToggleMute,
                testTag = "clip_mute_btn"
            )
            ClipCircleActionButton(
                icon = if (isInList) Icons.Default.Check else Icons.Default.Add,
                contentDescription = if (isInList) "Remove from My List" else "Add to My List",
                onClick = onToggleWatchlist,
                testTag = "clip_add_btn",
                modifier = Modifier.semantics { stateDescription = if (isInList) "Saved" else "Not saved" }
            )
            ClipCircleActionButton(
                painter = painterResource(id = R.drawable.ic_netflix_share),
                contentDescription = "Share clip",
                onClick = onShare,
                testTag = "clip_send_btn"
            )
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(platformDesign.surfaceColor(Color(0xFF252525)))
                    .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                    .clickable(role = Role.Button, onClick = onMediaClick)
                    .testTag("clip_disc_btn")
            ) {
                AsyncImage(
                    model = item.posterUrl ?: item.backdropUrl ?: item.bannerDrawableRes,
                    contentDescription = "Details for ${item.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun ClipCircleActionButton(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .referenceGlass(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(painter = painter, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ClipCircleActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .referenceGlass(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ComingSoonListMode(
    items: List<MediaItem>,
    reminders: List<ReminderEntity>,
    onToggleReminder: (MediaItem) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onOpenCast: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    unreadNotificationsCount: Int,
    onSelectMode: (ClipsMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Mode Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Clips",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onSelectMode(ClipsMode.VERTICAL_FEED) }
                        .padding(vertical = 4.dp)
                )

                Text(
                    text = "Coming Soon",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clickable { onSelectMode(ClipsMode.COMING_SOON) }
                        .padding(vertical = 4.dp)
                )

                Text(
                    text = "Top 10",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onSelectMode(ClipsMode.TOP_10) }
                        .padding(vertical = 4.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = onOpenCast, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = "Cast",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onOpenSearch, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items, key = { it.id }) { item ->
                val isReminded = reminders.any { it.mediaId == item.recommendationTitle().key }
                ComingSoonFeedCard(
                    item = item,
                    isReminded = isReminded,
                    isMuted = true,
                    onToggleMute = {},
                    onReminderToggle = { onToggleReminder(item) },
                    onPlayTrailer = { onPlayClick(item) },
                    onShareClick = {},
                    onItemClick = { onMediaClick(item) }
                )
            }
        }
    }
}

@Composable
private fun Top10ListMode(
    tvShows: List<MediaItem>,
    movies: List<MediaItem>,
    onMediaClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onOpenCast: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    unreadNotificationsCount: Int,
    onSelectMode: (ClipsMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Mode Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Clips",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onSelectMode(ClipsMode.VERTICAL_FEED) }
                        .padding(vertical = 4.dp)
                )

                Text(
                    text = "Coming Soon",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onSelectMode(ClipsMode.COMING_SOON) }
                        .padding(vertical = 4.dp)
                )

                Text(
                    text = "Top 10",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clickable { onSelectMode(ClipsMode.TOP_10) }
                        .padding(vertical = 4.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = onOpenCast, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = "Cast",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onOpenSearch, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "Top 10 TV Shows Today",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            itemsIndexed(tvShows) { index, item ->
                Top10RankCard(
                    rank = index + 1,
                    item = item,
                    onClick = { onMediaClick(item) },
                    onPlay = { onPlayClick(item) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Top 10 Movies Today",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            itemsIndexed(movies) { index, item ->
                Top10RankCard(
                    rank = index + 1,
                    item = item,
                    onClick = { onMediaClick(item) },
                    onPlay = { onPlayClick(item) }
                )
            }
        }
    }
}

@Composable
private fun Top10RankCard(
    rank: Int,
    item: MediaItem,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    val platformDesign = com.example.ui.theme.LocalPlatformDesign.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(platformDesign.corner(10.dp)))
            .background(platformDesign.raised)
            .border(1.dp, platformDesign.outline, RoundedCornerShape(platformDesign.corner(10.dp)))
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "#$rank",
                color = platformDesign.accent,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )

            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 48.dp)
                    .clip(RoundedCornerShape(platformDesign.corner(6.dp)))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = item.backdropUrl ?: item.posterUrl ?: item.bannerDrawableRes,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Column {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val firstGenre = item.genres.firstOrNull() ?: "Featured"
                Text(
                    text = "${item.matchPercentage}% Match • $firstGenre",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        }

        IconButton(onClick = onPlay) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
