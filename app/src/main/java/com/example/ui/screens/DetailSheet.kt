package com.example.ui.screens

import com.example.data.SeriesEpisodeOrder
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem as VideoItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.TrailerResolver
import com.example.data.TrailerResolverCallback
import com.example.data.TrailerStream
import com.example.data.download.DownloadTaskInfo
import com.example.data.local.WatchProgressEntity
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.NetflixRed
import com.example.ui.util.FocusedPreviewPolicy
import com.example.ui.viewmodel.*
import kotlinx.coroutines.delay

@Composable
fun DetailScreen(
    media: MediaItem, isInWatchlist: Boolean, userSubscription: UserSubscription = UserSubscription(),
    watchProgressList: List<WatchProgressEntity> = emptyList(), downloadProgressMap: Map<String, Float>,
    pausedDownloadKeys: Set<String> = emptySet(), completedDownloadKeys: Set<String> = emptySet(),
    onClose: () -> Unit, onPlayClick: (MediaItem, Episode?) -> Unit, onPlayTrailerClick: (MediaItem, String) -> Unit,
    onWatchlistToggle: () -> Unit, onDownloadClick: (MediaItem, Episode?) -> Unit, onRatingSelect: (String) -> Unit,
    onSimilarMediaClick: (MediaItem) -> Unit, onSeasonSelect: (Int) -> Unit = {}, onPauseDownload: (String) -> Unit = {},
    onResumeDownload: (String) -> Unit = {}, onCancelDownload: (String) -> Unit = {}, onOpenAuth: () -> Unit = {},
    onOpenSubscription: () -> Unit = {}, modifier: Modifier = Modifier, loadState: DetailLoadState? = null,
    onRetryDetails: () -> Unit = {}, onOpenDownloads: () -> Unit = {}, downloadTasks: Map<String, DownloadTaskInfo> = emptyMap(),
    previewEnabled: Boolean = false, isActive: Boolean = true, currentRating: String? = null, kidMaxAge: Int? = null
) {
    val platformDesign = com.example.ui.theme.LocalPlatformDesign.current
    val prime = platformDesign.appearance == com.example.data.model.PlatformAppearance.PRIME
    val context = LocalContext.current
    val latestProgress = remember(media.id, watchProgressList) { watchProgressList.filter { it.mediaId == media.id }.maxByOrNull { it.lastWatchedTimestamp } }
    val resume = latestProgress?.takeIf { it.canResume() }
    var localSeason by rememberSaveable(media.id) { mutableIntStateOf(resume?.let { SeriesEpisodeOrder.displaySeason(media.id, it.season, it.episode) } ?: 1) }
    val season = loadState?.season ?: localSeason
    var seasonInfo by remember(media.id) { mutableStateOf(false) }
    var expanded by rememberSaveable(media.id) { mutableStateOf(false) }
    var tab by rememberSaveable(media.id, media.type) { mutableIntStateOf(0) }
    val series = media.type == MediaType.TV_SHOW
    val tabs = if (prime) { if (series) listOf("Episodes", "Details", "Explore", "Related") else listOf("Details", "Explore", "Related") } else if (series) listOf("Episodes", "More Like This", "Trailers & More") else listOf("More Like This", "Trailers & More")
    val tabIndex = tab.coerceIn(tabs.indices)
    val episodes = remember(media.episodes, season) { media.episodes.filter { episodeCoordinates(it.id).let { coordinates -> SeriesEpisodeOrder.displaySeason(media.id, coordinates.first, coordinates.second) } == season }.sortedBy { it.episodeNumber } }
    val progressByEpisode = remember(media.id, watchProgressList) { watchProgressList.filter { it.mediaId == media.id }.groupBy { it.episodeId }.mapValues { (_, entries) -> entries.maxBy { it.lastWatchedTimestamp } } }
    val target = if (series) episodes.firstOrNull { it.id == resume?.episodeId } ?: episodes.firstOrNull() else null
    val recommendations = remember(media.similarMedia, media.id, kidMaxAge) { media.similarMedia.filter { it.id != media.id && !it.isComingSoon && (kidMaxAge == null || it.isKidSafe(kidMaxAge)) }.distinctBy { it.type to it.id }.take(12) }
    val locked = userSubscription.isMediaLocked(media.id, media.title)
    val share: () -> Unit = {
        val type = if (series) "tv" else "movie"
        val reference = media.id.toIntOrNull()?.let { " https://www.themoviedb.org/$type/$it" }.orEmpty()
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { this.type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Watch ${media.title} on NetflixPro.$reference") }, "Share title"))
    }
    val backdropGradient = remember(media.id, media.secondaryColorHex, platformDesign) {
        Brush.verticalGradient(listOf(Color(media.secondaryColorHex).copy(alpha = .32f), platformDesign.surfaceColor(Color(0xFF101010))), endY = 1000f)
    }
    val listState = rememberLazyListState()
    val previewArtworkVisible by remember(listState, media.id) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val artwork = layout.visibleItemsInfo.firstOrNull { it.key == "artwork_${media.id}" }
            artwork != null && !listState.isScrollInProgress &&
                (minOf(artwork.offset + artwork.size, layout.viewportEndOffset) -
                    maxOf(artwork.offset, layout.viewportStartOffset)).coerceAtLeast(0) >= artwork.size / 2
        }
    }
    Box(modifier.fillMaxSize().background(platformDesign.surfaceColor(Color(0xFF101010))).background(backdropGradient)) {
    LazyColumn(Modifier.fillMaxSize().testTag("detail_list"), state = listState, contentPadding = PaddingValues(bottom = 36.dp)) {
        item(key = "artwork_${media.id}", contentType = "artwork") {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).heightIn(max = 380.dp).clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).background(platformDesign.background)) {
                AsyncImage(media.backdropUrl ?: media.posterUrl ?: media.bannerDrawableRes, media.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (previewEnabled && isActive && previewArtworkVisible) DetailPreview(media, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .4f), .45f to Color.Transparent, 1f to platformDesign.surfaceColor(Color(0xFF101010)))))
                if (prime) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(.8f).padding(horizontal = 16.dp, vertical = 12.dp)) { DetailTitle(media) }
            }
        }
        item(key = "title_${media.id}", contentType = "title") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (prime) Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (locked) Icons.Default.Lock else Icons.Default.CheckCircle, null, tint = platformDesign.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp)); Text(if (locked) "NetflixPro preview" else "Available with your NetflixPro membership", color = Color.White, fontSize = 12.sp)
                }
                else if (platformDesign.originalLayout) NetflixWordmark(height = 16.dp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                else Text(platformDesign.brandTitle, color = platformDesign.accent, fontSize = 12.sp,
                    fontWeight = platformDesign.titleWeight, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                if (!prime) DetailTitle(media)
                if (!prime) Row(Modifier.padding(top = 10.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (media.releaseYear > 0) Text("${media.releaseYear}", color = Color.LightGray, fontSize = 12.sp)
                    Text(media.maturityRating, color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.background(platformDesign.surfaceColor(Color(0xFF333333)), RoundedCornerShape(2.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
                    Text(media.durationOrSeasons, color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                val key = if (target != null) "${media.id}_${target.id}" else media.id
                val compactDownload = !platformDesign.originalLayout && (!series || target != null) &&
                    key !in completedDownloadKeys && key !in pausedDownloadKeys && downloadProgressMap[key] == null && downloadTasks[key] == null
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onPlayClick(media, target) }, enabled = !series || target != null || locked, colors = ButtonDefaults.buttonColors(containerColor = platformDesign.primary, contentColor = platformDesign.onPrimary), shape = RoundedCornerShape(if (platformDesign.originalLayout) 5.dp else platformDesign.controlRadius), modifier = Modifier.weight(1f).height(48.dp).testTag("detail_play_button")) {
                    Icon(if (locked) Icons.Default.Lock else Icons.Default.PlayArrow, null, Modifier.size(28.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (locked) "Play trailer" else if (resume != null && (!series || target?.id == resume.episodeId)) "Resume" else if (prime && target != null) "Play S$season E${target.episodeNumber}" else "Play", fontWeight = FontWeight.Bold)
                }
                    if (compactDownload) IconButton(onClick = { onDownloadClick(media, target) },
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(platformDesign.cardRadius))
                            .background(platformDesign.raised).testTag("detail_platform_download")) {
                        Icon(painterResource(R.drawable.ic_netflix_download_custom), "Download", tint = Color.White)
                    }
                }
                if (!compactDownload) {
                    Spacer(Modifier.height(10.dp))
                    if (!series || target != null) DownloadAction(key, downloadProgressMap[key], key in pausedDownloadKeys, key in completedDownloadKeys, downloadTasks[key], { onDownloadClick(media, target) }, { onPauseDownload(key) }, { onResumeDownload(key) }, { onCancelDownload(key) }, onOpenDownloads)
                }
                if (prime) {
                    PrimeDetailActions(isInWatchlist, currentRating,
                        { onPlayTrailerClick(media, "Official trailer: ${media.title}") }, onWatchlistToggle, onRatingSelect, share)
                    Text((listOf(media.maturityRating) + media.genres.take(3)).joinToString("   "), color = Color.LightGray,
                        fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                } else Spacer(Modifier.height(18.dp))
                if (resume != null && (!series || target?.id == resume.episodeId)) {
                    if (series) Text(resume.episodeTitle?.takeIf { it.isNotBlank() } ?: "Episode ${resume.episode}", color = Color.LightGray, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearProgressIndicator(progress = { (resume.positionSeconds.toFloat() / resume.totalSeconds).coerceIn(0f, 1f) }, modifier = Modifier.weight(1f).height(4.dp), color = platformDesign.accent, trackColor = Color.Gray)
                        Text("${((resume.totalSeconds - resume.positionSeconds) / 60).coerceAtLeast(1)} min remaining", color = Color.Gray, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                val synopsis = target?.description?.takeIf { resume?.episodeId == target.id && it.isNotBlank() } ?: media.description
                Text(synopsis.ifBlank { "A new story awaits." }, color = Color.White.copy(alpha = .88f), fontSize = 15.sp, lineHeight = 22.sp, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { expanded = !expanded })
                if (!prime && media.cast.isNotEmpty()) Text("Cast: ${media.cast.joinToString(", ")}", color = Color.Gray, fontSize = 12.sp, maxLines = if (expanded) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp).clickable { expanded = !expanded })
                if (!prime && media.director.isNotBlank()) Text("${if (series) "Creator" else "Director"}: ${media.director}", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                if (!prime) Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onWatchlistToggle, Modifier.testTag("detail_my_list_button")) { Icon(if (isInWatchlist) Icons.Default.Check else Icons.Default.Add, "My List", tint = Color.White, modifier = Modifier.size(30.dp)) }
                        Text("My List", color = Color.LightGray, fontSize = 11.sp)
                    }
                    NetflixRatingAction(currentRating, onRatingSelect, iconSize = 28.dp, labelSize = 12.sp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(share) { Icon(painterResource(R.drawable.ic_netflix_share), "Share title", tint = Color.White, modifier = Modifier.size(28.dp)) }; Text("Share", color = Color.LightGray, fontSize = 11.sp) }
                }
                if (prime) PrimeDetailFacts(media)
                if (loadState?.error != null) DetailRetry(loadState.error, onRetryDetails)
            }
        }
        if (prime && series) item(key = "prime_season", contentType = "season") {
            DetailSeasonSelector(media, season, if (loadState?.seasonLoading == true) null else episodes.size,
                onSelect = { localSeason = it; onSeasonSelect(it) }, onInfo = { seasonInfo = true }, onDownloads = onOpenDownloads)
        }
        item(key = "tabs_${media.type}", contentType = "tabs") {
            ScrollableTabRow(tabIndex, containerColor = Color.Transparent, contentColor = Color.White, edgePadding = 16.dp, divider = {}, indicator = { positions -> Box(Modifier.tabIndicatorOffset(positions[tabIndex]).fillMaxHeight().wrapContentHeight(Alignment.Top).height(3.dp).background(platformDesign.accent)) }) {
                tabs.forEachIndexed { index, title -> Tab(tabIndex == index, onClick = { tab = index }, text = { Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }) }
            }
        }
        when (tabs[tabIndex]) {
            "Episodes" -> {
                if (!prime) item(key = "season", contentType = "season") {
                    DetailSeasonSelector(media, season, null, onSelect = { localSeason = it; onSeasonSelect(it) },
                        onInfo = { seasonInfo = true }, onDownloads = onOpenDownloads)
                }
                if (loadState?.seasonLoading == true) item(key = "episode_loading") { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { NetflixSpinner(size = 28.dp) } }
                else if (episodes.isEmpty()) item(key = "episode_error") { DetailRetry(loadState?.seasonError ?: "Episodes are unavailable. Reconnect to load this season.") { onSeasonSelect(season) } }
                items(episodes, key = { it.id }, contentType = { "episode" }) { episode ->
                    val key = "${media.id}_${episode.id}"
                    val progress = progressByEpisode[episode.id]
                    val compactEpisodeDownload = prime && (key in completedDownloadKeys || (key !in pausedDownloadKeys && downloadProgressMap[key] == null && downloadTasks[key] == null))
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).testTag("detail_episode_${episode.id}")) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onPlayClick(media, episode) }) {
                            Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(platformDesign.corner(5.dp))).background(platformDesign.surfaceColor(Color(0xFF252525))), contentAlignment = Alignment.Center) {
                                AsyncImage(episode.stillUrl ?: media.backdropUrl ?: media.posterUrl, episode.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                Icon(if (locked) Icons.Default.Lock else Icons.Default.PlayArrow, "Play episode ${episode.episodeNumber}", tint = Color.White, modifier = if (prime) Modifier.align(Alignment.BottomStart).padding(3.dp).size(20.dp) else Modifier.size(36.dp).background(Color.Black.copy(alpha = .45f), CircleShape).padding(5.dp))
                                if (progress != null && progress.totalSeconds > 0) LinearProgressIndicator(progress = { (progress.positionSeconds.toFloat() / progress.totalSeconds).coerceIn(0f, 1f) }, color = platformDesign.accent, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp))
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("${episode.episodeNumber}. ${episode.title}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); Text("${episode.durationMinutes} min", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                            if (compactEpisodeDownload) IconButton(onClick = { if (key in completedDownloadKeys) onOpenDownloads() else onDownloadClick(media, episode) }, modifier = Modifier.testTag("prime_episode_download_${episode.id}")) {
                                Icon(if (key in completedDownloadKeys) Icons.Default.DownloadDone else Icons.Default.FileDownload, if (key in completedDownloadKeys) "Downloaded episode" else "Download episode", tint = Color.White)
                            }
                        }
                        if (episode.description.isNotBlank()) Text(episode.description, color = Color.LightGray, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 8.dp))
                        if (!compactEpisodeDownload) DownloadAction(key, downloadProgressMap[key], key in pausedDownloadKeys, key in completedDownloadKeys, downloadTasks[key], { onDownloadClick(media, episode) }, { onPauseDownload(key) }, { onResumeDownload(key) }, { onCancelDownload(key) }, onOpenDownloads)
                    }
                }
            }
            "More Like This", "Related" -> {
                if (recommendations.isEmpty()) item { Text("More titles will appear when recommendations are available.", color = Color.Gray, modifier = Modifier.padding(20.dp)) }
                items(recommendations.chunked(3), key = { "${it.first().type}:${it.first().id}" }, contentType = { "recommendations" }) { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { item -> Box(Modifier.weight(1f).aspectRatio(2f / 3f).clip(RoundedCornerShape(platformDesign.corner(5.dp))).background(platformDesign.surfaceColor(Color(0xFF252525))).clickable { onSimilarMediaClick(item) }) { AsyncImage(item.posterUrl ?: item.backdropUrl ?: item.bannerDrawableRes, item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            "Trailers & More", "Explore" -> item(key = "official_trailer") {
                ListItem(headlineContent = { Text("Official trailer", color = Color.White) }, supportingContent = { Text(media.title, color = Color.Gray) }, leadingContent = { Icon(Icons.Default.PlayArrow, null, tint = Color.White) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable { onPlayTrailerClick(media, "Official trailer: ${media.title}") }.testTag(if (prime) "prime_explore_trailer" else "detail_trailer_button"))
            }
            "Details" -> item(key = "credits") { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { DetailInfo("Genres", media.genres.joinToString(" · ")); DetailInfo("Cast", media.cast.joinToString(", ")); DetailInfo(if (series) "Creator" else "Director", media.director); DetailInfo("Maturity rating", media.maturityRating) } }
        }
    }
    if (seasonInfo) AlertDialog(onDismissRequest = { seasonInfo = false }, containerColor = platformDesign.surfaceColor(Color(0xFF202020)),
        title = { Text("${media.title} · Season $season", color = Color.White) },
        text = { Text("${episodes.size} loaded episodes · ${media.maturityRating}", color = Color.LightGray) },
        confirmButton = { TextButton(onClick = { seasonInfo = false }) { Text("Done", color = Color.White) } })
    IconButton(onClose, Modifier.align(if (prime) Alignment.TopStart else Alignment.TopEnd).statusBarsPadding().padding(top = 12.dp, end = 12.dp).size(48.dp)
        .background(Color.Black.copy(alpha = .65f), CircleShape).testTag("detail_close_button")) {
        Icon(if (prime) Icons.Default.ArrowBack else Icons.Default.Close, "Close details", tint = Color.White)
    }
    }
}

@Composable
private fun DetailTitle(media: MediaItem) {
    val fallback: @Composable () -> Unit = {
        Text(media.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
    if (media.logoUrl.isNullOrBlank()) fallback()
    else coil.compose.SubcomposeAsyncImage(
        model = media.logoUrl, contentDescription = media.title,
        alignment = Alignment.CenterStart, contentScale = ContentScale.Fit,
        loading = { fallback() }, error = { fallback() },
        modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth().height(64.dp).testTag("detail_title_logo")
    )
}

@Composable
private fun DetailInfo(label: String, value: String) { if (value.isNotBlank()) Column { Text(label, color = Color.Gray, fontSize = 12.sp); Text(value, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) } }
@Composable
private fun DetailRetry(message: String, onRetry: () -> Unit) { Column(Modifier.fillMaxWidth().padding(20.dp)) { Text(message, color = Color.LightGray, fontSize = 13.sp); TextButton(onClick = onRetry) { Text("Try again", color = Color.White) } } }

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun DetailPreview(media: MediaItem, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (!resumed) return
    val focusedAt = remember(media.id) { android.os.SystemClock.uptimeMillis() }
    var start by remember(media.id) { mutableStateOf(false) }
    LaunchedEffect(media.id) { delay(FocusedPreviewPolicy.FOCUS_SETTLE_MS); start = true }
    if (!start) return
    val player = remember(context, media.id) {
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_000, 5_000, FocusedPreviewPolicy.START_BUFFER_MS, 1_000)
            .setBackBuffer(0, false).build()
        ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_OFF
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setMaxVideoSize(960, 540).setMaxVideoBitrate(1_500_000).build()
        }
    }
    var ready by remember(media.id) { mutableStateOf(false) }
    var abandoned by remember(media.id) { mutableStateOf(false) }
    var activeResolver by remember(media.id) { mutableStateOf<TrailerResolver?>(null) }
    LaunchedEffect(media.id, player) {
        delay(FocusedPreviewPolicy.remainingStartupMs(focusedAt, android.os.SystemClock.uptimeMillis()))
        if (!ready) {
            abandoned = true
            activeResolver?.cancel()
            player.stop()
            player.clearMediaItems()
        }
    }
    var muted by remember(media.id) { mutableStateOf(true) }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    DisposableEffect(media.id, player) {
        var disposed = false
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { if (!disposed && !abandoned) ready = true }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { if (!disposed) { ready = false; abandoned = true; activeResolver?.cancel(); player.stop(); player.clearMediaItems() } }
        }
        player.addListener(listener)
        val resolver = TrailerResolver(context, media.id, if (media.type == MediaType.MOVIE) "movie" else "tv", object : TrailerResolverCallback {
            override fun onResolved(stream: TrailerStream) {
                if (disposed || abandoned) return
                val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(stream.headers)
                val mime = when (stream.type) { "hls" -> MimeTypes.APPLICATION_M3U8; "dash" -> MimeTypes.APPLICATION_MPD; else -> MimeTypes.VIDEO_MP4 }
                player.setMediaSource(DefaultMediaSourceFactory(http).setLoadErrorHandlingPolicy(
                    androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(0)
                ).createMediaSource(VideoItem.Builder().setUri(stream.url).setMimeType(mime).build()))
                player.prepare(); player.play()
            }
            override fun onError(error: String) { if (!disposed) ready = false }
        })
        activeResolver = resolver
        resolver.start()
        onDispose { disposed = true; activeResolver = null; resolver.cancel(); player.removeListener(listener); player.release() }
    }
    Box(modifier) {
        AndroidView(factory = { PlayerView(it).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; this.player = player } }, update = { it.player = player }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (ready) 1f else 0f })
        if (ready) IconButton(onClick = { muted = !muted }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 24.dp).background(Color.Black.copy(alpha = .6f), CircleShape)) {
            Icon(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, if (muted) "Unmute preview" else "Mute preview", tint = Color.White)
        }
    }
}

@Composable
private fun DetailSeasonSelector(media: MediaItem, season: Int, episodeCount: Int?, onSelect: (Int) -> Unit,
                                 onInfo: () -> Unit, onDownloads: () -> Unit) {
    val design = com.example.ui.theme.LocalPlatformDesign.current
    val prime = design.appearance == com.example.data.model.PlatformAppearance.PRIME
    var menu by remember(media.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            TextButton(onClick = { menu = true }, modifier = Modifier.background(design.raised, RoundedCornerShape(design.corner(4.dp))).testTag("detail_season_selector")) {
                Text("Season $season" + if (prime && episodeCount != null) " ($episodeCount episode${if (episodeCount == 1) "" else "s"})" else "", color = Color.White, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White)
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                (SeriesEpisodeOrder.seasons(media.id) ?: (1..maxOf(1, media.totalSeasons, season)).toList()).forEach { value ->
                    DropdownMenuItem(text = { Text("Season $value") }, onClick = { menu = false; onSelect(value) })
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (prime) TextButton(onClick = onDownloads, modifier = Modifier.testTag("prime_detail_open_downloads")) {
            Icon(painterResource(R.drawable.ic_netflix_download_custom), null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp)); Text("Downloads", color = Color.White, fontSize = 12.sp)
        } else IconButton(onInfo) { Icon(Icons.Default.Info, "Season information", tint = Color.White) }
    }
}
