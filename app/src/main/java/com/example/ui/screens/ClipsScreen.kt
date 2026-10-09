package com.example.ui.screens

import com.example.discovery.recommendationTitle

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.TrailerResolver
import com.example.data.TrailerResolverCallback
import com.example.data.TrailerStream
import com.example.data.local.ReminderEntity
import com.example.data.model.CastDevice
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.NotificationItem
import com.example.data.model.UserProfile
import com.example.ui.components.NFilmBadge
import com.example.ui.components.NSeriesBadge
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
    maxVideoHeight: Int = Int.MAX_VALUE,
    spatialAudioEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    catalogMedia: List<MediaItem> = emptyList(),
    upcomingReleases: List<MediaItem> = emptyList(),
    activeProfile: UserProfile = UserProfile("profile", "Home"),
    isActive: Boolean = true
) {
    var clipsMode by rememberSaveable { mutableStateOf(ClipsMode.VERTICAL_FEED) }
    val clipsCatalog = remember(catalogMedia, upcomingReleases, activeProfile) {
        buildClipsCatalog(catalogMedia, upcomingReleases, activeProfile)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .testTag("clips_screen")
    ) {
        when (clipsMode) {
            ClipsMode.VERTICAL_FEED -> {
                if (clipsCatalog.feed.isEmpty()) {
                    Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No clips are available for this profile yet.", color = Color.LightGray, textAlign = TextAlign.Center)
                        androidx.compose.material3.TextButton(onClick = { clipsMode = ClipsMode.COMING_SOON }) { Text("Coming Soon", color = Color.White) }
                        androidx.compose.material3.TextButton(onClick = { clipsMode = ClipsMode.TOP_10 }) { Text("Top 10", color = Color.White) }
                    }
                } else {
                TikTokVerticalVideoFeed(
                    items = clipsCatalog.feed,
                    likedMedia = likedMedia,
                    connectedCastDevice = connectedCastDevice,
                    isWatchlistContains = isWatchlistContains,
                    onToggleLike = onToggleLike,
                    onWatchlistToggle = onWatchlistToggle,
                    onMediaClick = onMediaClick,
                    onPlayClick = onPlayClick,
                    onOpenCast = onOpenCast,
                    onOpenSearch = onOpenSearch,
                    onOpenNotifications = onOpenNotifications,
                    unreadNotificationsCount = notifications.count { !it.isRead },
                    onSelectMode = { clipsMode = it },
                    onShowToast = onShowToast,
                    streamingAllowed = streamingAllowed,
                    maxVideoHeight = maxVideoHeight,
                    spatialAudioEnabled = spatialAudioEnabled,
                    isActive = isActive
                )
                }
            }

            ClipsMode.COMING_SOON -> {
                ComingSoonListMode(
                    items = clipsCatalog.upcoming,
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
                    tvShows = clipsCatalog.rankedShows,
                    movies = clipsCatalog.rankedMovies,
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
        if (!streamingAllowed) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .9f)).clickable {},
            contentAlignment = Alignment.Center) {
            Text("Connect to Wi-Fi or change Cellular Data in App Settings to watch Clips.",
                color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(28.dp))
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TikTokVerticalVideoFeed(
    items: List<MediaItem>,
    likedMedia: List<MediaItem>,
    connectedCastDevice: CastDevice?,
    isWatchlistContains: (String) -> Boolean,
    onToggleLike: (String) -> Unit,
    onWatchlistToggle: (MediaItem) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onOpenCast: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    unreadNotificationsCount: Int,
    onSelectMode: (ClipsMode) -> Unit,
    onShowToast: (String) -> Unit,
    streamingAllowed: Boolean,
    maxVideoHeight: Int,
    spatialAudioEnabled: Boolean,
    isActive: Boolean
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    val context = LocalContext.current

    // Stable identities keep mute preferences with the title when the feed reorders.
    val mutedMap = remember { mutableStateMapOf<String, Boolean>() }

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
    var progress by remember { mutableFloatStateOf(0f) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!resumed) exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val playbackActive = streamingAllowed && isActive && resumed

    val currentPage = pagerState.currentPage
    val currentItem = items.getOrNull(currentPage)
    val latestItem by androidx.compose.runtime.rememberUpdatedState(currentItem)
    val latestPlaybackActive by androidx.compose.runtime.rememberUpdatedState(playbackActive)
    val isMuted = mutedMap[currentItem?.let { "${it.type}:${it.id}" }] ?: false

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    fun prepareAndPlay(stream: TrailerStream) {
        if (!latestPlaybackActive) return
        resolvedUrl = stream.url
        isResolving = false
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000).setReadTimeoutMs(10000)
            .setDefaultRequestProperties(stream.headers)
        val mime = when (stream.type) {
            "hls" -> MimeTypes.APPLICATION_M3U8
            "dash" -> MimeTypes.APPLICATION_MPD
            else -> MimeTypes.VIDEO_MP4
        }
        val mediaItem = Media3Item.Builder().setUri(stream.url).setMimeType(mime).build()
        exoPlayer.setMediaSource(DefaultMediaSourceFactory(http).createMediaSource(mediaItem))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    LaunchedEffect(currentItem?.id, currentItem?.type, playbackActive) {
        val pending = mutableListOf<TrailerResolver>()
        var cancelled = false
        // A new page must not keep playing the previous title while its trailer resolves.
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        resolvedUrl = null
        progress = 0f
        isResolving = false
        try {
            val item = currentItem
            if (!playbackActive || item == null) return@LaunchedEffect
            isResolving = true
            fun ownsRequest() = !cancelled && latestPlaybackActive &&
                latestItem?.id == item.id && latestItem?.type == item.type
            val resolver = TrailerResolver(context, item.id, if (item.type == MediaType.MOVIE) "movie" else "tv",
                object : TrailerResolverCallback {
                    override fun onResolved(stream: TrailerStream) { if (ownsRequest()) prepareAndPlay(stream) }
                    override fun onError(error: String) { if (ownsRequest()) isResolving = false }
                })
            pending += resolver
            resolver.start()
            // TrailerResolver's bounded-lifetime cache preserves MIME type and request headers.
            items.getOrNull(currentPage + 1)?.let { next ->
                val prefetch = TrailerResolver(context, next.id, if (next.type == MediaType.MOVIE) "movie" else "tv",
                    object : TrailerResolverCallback {
                        override fun onResolved(stream: TrailerStream) {}
                        override fun onError(error: String) {}
                    })
                pending += prefetch
                prefetch.start()
            }
            while (true) {
                delay(200)
                val duration = exoPlayer.duration
                if (duration > 0) progress = (exoPlayer.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
            }
        } finally {
            cancelled = true
            pending.forEach { it.cancel() }
        }
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
            val itemKey = "${item.type}:${item.id}"
            val pageMuted = mutedMap[itemKey] ?: false
            val isCurrent = pagerState.currentPage == page

            TikTokClipItemView(
                item = item,
                isCurrentPage = isCurrent,
                exoPlayer = exoPlayer,
                isResolving = if (isCurrent) isResolving else false,
                resolvedUrl = if (isCurrent) resolvedUrl else null,
                progress = if (isCurrent) progress else 0f,
                isLiked = isLiked,
                isInList = isInList,
                isMuted = pageMuted,
                onToggleMute = {
                    mutedMap[itemKey] = !pageMuted
                    onShowToast(if (!pageMuted) "Audio Muted" else "Audio Unmuted")
                },
                onToggleLike = {
                    onToggleLike(item.id)
                },
                onToggleWatchlist = {
                    onWatchlistToggle(item)
                },
                onShare = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Check out '${item.title}' on NetflixPro Clips! https://www.themoviedb.org/${if (item.type == com.example.data.model.MediaType.MOVIE) "movie" else "tv"}/${item.id}"
                        )
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Clip"))
                },
                onMediaClick = { onMediaClick(item) },
                onPlayClick = { onPlayClick(item) }
            )
        }

        // Top Floating Header Navigation Bar Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Selector Tabs (Clips | Coming Soon | Top 10)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Clips",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
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
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onSelectMode(ClipsMode.TOP_10) }
                        .padding(vertical = 4.dp)
                )
            }

            // Top Right Quick Actions (Cast, Notifications, Search)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = onOpenCast, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (connectedCastDevice != null) Icons.Default.CastConnected else Icons.Default.Cast,
                        contentDescription = "Cast",
                        tint = if (connectedCastDevice != null) Color(0xFF4CAF50) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onOpenNotifications, modifier = Modifier.size(36.dp)) {
                    Box {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        if (unreadNotificationsCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(8.dp)
                                    .background(NetflixRed, CircleShape)
                            )
                        }
                    }
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
    progress: Float,
    isLiked: Boolean,
    isInList: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onShare: () -> Unit,
    onMediaClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val context = LocalContext.current
    // Double tap heart animation state
    var showHeartAnimation by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var showPlayPauseControls by remember { mutableStateOf(false) }
    var centerIconRes by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(exoPlayer, isCurrentPage) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                if (isCurrentPage) {
                    isPlaying = playing
                }
            }
        }
        exoPlayer.addListener(listener)
        if (isCurrentPage) {
            isPlaying = exoPlayer.isPlaying
        }
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            isPlaying = false
            centerIconRes = R.drawable.ic_netflix_pause
        } else {
            exoPlayer.play()
            isPlaying = true
            centerIconRes = R.drawable.ic_netflix_play
        }
        showPlayPauseControls = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (!isLiked) onToggleLike()
                        showHeartAnimation = true
                    },
                    onTap = { togglePlayPause() }
                )
            }
    ) {
        // Ambient Background Image & Gradient Scrim for non-portrait video letterboxing
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
                    .background(Color.Black.copy(alpha = 0.85f))
            )
        }

        // Video Player Stream View (Fits video completely without cutting off edges)
        if (resolvedUrl != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (isResolving) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                NetflixSpinner(size = 44.dp)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(item.primaryColorHex),
                                Color(item.secondaryColorHex),
                                Color.Black
                            )
                        )
                    )
            )
        }

        // Dark Top & Bottom Scrim Gradients for contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.60f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.40f),
                            Color.Black.copy(alpha = 0.90f)
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
                tint = NetflixRed,
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

        // 2 & 3. Bottom Overlay Row: Left Title & Metadata (Weighted) + Right Vertical Action Column
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 112.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // Left Caption & Title Metadata Overlay (weight 1f prevents long title from pushing right vertical icons)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Genre Bullets Subtitle Line (e.g. "Period Piece • Notable Soundtrack • Keeping Secrets")
                val formattedGenres = item.genres.ifEmpty { listOf("Featured", "Trending") }.joinToString(" • ")
                Text(
                    text = formattedGenres,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Description Hook Snippet with clickable "more"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onMediaClick() }
                ) {
                    Text(
                        text = item.description.take(48) + "... ",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "more",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Right Side Stacked Action Column (2026 Netflix Clips Glass Circular Buttons)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mute / Unmute Glass Circle Button
                GlassCircleActionButton(
                    icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    onClick = onToggleMute,
                    testTag = "clip_mute_btn"
                )

                // Add / My List Glass Circle Button
                GlassCircleActionButton(
                    icon = if (isInList) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = "My List",
                    tint = if (isInList) Color(0xFF4CAF50) else Color.White,
                    onClick = onToggleWatchlist,
                    testTag = "clip_add_btn"
                )

                // Send / Share Glass Circle Button
                GlassCircleActionButton(
                    painter = painterResource(id = R.drawable.ic_netflix_share),
                    contentDescription = "Share Clip",
                    onClick = onShare,
                    testTag = "clip_send_btn"
                )

                // Poster Disc Circle Thumbnail with ALL CAPS Title
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onMediaClick() }
                        .testTag("clip_disc_btn")
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                            .background(Color.DarkGray)
                    ) {
                        AsyncImage(
                            model = item.posterUrl ?: item.backdropUrl ?: item.bannerDrawableRes,
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.title.uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(52.dp)
                    )
                }
            }
        }

        // 4. Clip Auto Playback Timeline Progress Bar
        ClipProgressBar(
            progress = progress,
            durationText = "14:00",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp, start = 16.dp, end = 16.dp)
        )
    }
}

@Composable
private fun GlassCircleActionButton(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    testTag: String
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
            .clickable { onClick() }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun GlassCircleActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    testTag: String
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
            .clickable { onClick() }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
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
            items(items, key = { "${it.type}:${it.id}" }) { item ->
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NetflixCardBg)
            .border(1.dp, NetflixBorderGray, RoundedCornerShape(10.dp))
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
                color = NetflixRed,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )

            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 48.dp)
                    .clip(RoundedCornerShape(6.dp))
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

@Composable
private fun ClipProgressBar(
    progress: Float,
    durationText: String = "14:00",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidth = maxWidth
            val activeWidth = totalWidth * progress.coerceIn(0f, 1f)
            val thumbRadius = 8.dp

            // Inactive Track: #4B4B4B, height 4.dp, rx 2.dp (matches SVG)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF4B4B4B))
            )

            // Active Track: #D22F26 (NetflixRed), height 4.dp, rx 2.dp
            Box(
                modifier = Modifier
                    .width(activeWidth)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NetflixRed)
            )

            // Red Circle Thumb Knob: r = 8.dp (size 16.dp), fill #D22F26
            val thumbOffset = (activeWidth - thumbRadius).coerceIn(0.dp, (totalWidth - thumbRadius * 2).coerceAtLeast(0.dp))
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(thumbRadius * 2)
                    .background(NetflixRed, CircleShape)
            )
        }

        if (durationText.isNotEmpty()) {
            Text(
                text = durationText,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
