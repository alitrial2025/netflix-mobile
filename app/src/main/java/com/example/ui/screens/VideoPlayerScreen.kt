package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.example.data.model.Episode
import com.example.data.model.MediaType
import coil.compose.AsyncImage
import com.example.ui.components.NetflixNLogo
import com.example.ui.components.NetflixSpinner
import com.example.ui.theme.NetflixCardBg
import com.example.ui.theme.NetflixDarkGray
import com.example.ui.theme.NetflixRed
import com.example.ui.viewmodel.PlayerState
import kotlinx.coroutines.ensureActive

data class PlayerTrackOption(
    val id: String,
    val name: String,
    val language: String,
    val isSelected: Boolean,
    val group: androidx.media3.common.Tracks.Group? = null,
    val trackIndex: Int = 0
)

private fun formatTimeString(seconds: Int): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        "%d:%02d:%02d".format(hrs, mins, secs)
    } else {
        "%d:%02d".format(mins, secs)
    }
}

@Composable
private fun DoubleThumbsUpIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Icon(
        painter = painterResource(id = R.drawable.ic_netflix_two_thumbs),
        contentDescription = "Love It",
        tint = tint,
        modifier = modifier.size(20.dp)
    )
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    playerState: PlayerState,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onSkipForward10: () -> Unit,
    onSkipBackward10: () -> Unit,
    onSkipIntro: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetAudio: (String) -> Unit,
    onSetSubtitle: (String) -> Unit,
    onToggleLock: () -> Unit,
    onToggleControls: () -> Unit,
    onShowAudioSubtitles: (Boolean) -> Unit,
    onShowEpisodesDrawer: (Boolean) -> Unit,
    onPlayNextEpisode: () -> Unit,
    onSelectEpisode: (Episode) -> Unit,
    onOpenCast: (() -> Unit)? = null,
    onSetRating: ((String) -> Unit)? = null,
    currentRating: String? = null,
    onUpdateProgress: ((Int, Int) -> Unit)? = null,
    onTrailerEnded: (() -> Unit)? = null,
    autoPlayNext: Boolean = false,
    onContentEnded: () -> Unit = {},
    onReplay: () -> Unit = {},
    onRetryNext: () -> Unit = {},
    recommendations: List<com.example.data.model.MediaItem> = emptyList(),
    onRecommendationClick: (com.example.data.model.MediaItem) -> Unit = {},
    onSetIntroWindow: (com.example.ui.viewmodel.IntroWindow?) -> Unit = {},
    onPersistProgress: () -> Unit = {},
    onPlaybackFailed: () -> Unit = {},
    spatialAudioEnabled: Boolean = true,
    maxVideoHeight: Int = Int.MAX_VALUE,
    streamingAllowed: Boolean = true,
    modifier: Modifier = Modifier
) {
    val media = playerState.media ?: return
    var showSpeedDialog by remember { mutableStateOf(false) }
    var brightnessLevel by remember { mutableFloatStateOf(0.75f) }
    var isBuffering by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val latestPlayerState by androidx.compose.runtime.rememberUpdatedState(playerState)
    val latestProgressCallback by rememberUpdatedState(onUpdateProgress)
    val latestTrailerEnded by rememberUpdatedState(onTrailerEnded)
    val latestTogglePlayPause by rememberUpdatedState(onTogglePlayPause)
    val latestNextEpisode by rememberUpdatedState(onPlayNextEpisode)
    val latestPlaybackFailed by rememberUpdatedState(onPlaybackFailed)
    val latestContentEnded by rememberUpdatedState(onContentEnded)
    val latestPersistProgress by rememberUpdatedState(onPersistProgress)
    val latestSetIntroWindow by rememberUpdatedState(onSetIntroWindow)
    val lifecycleOwner = LocalLifecycleOwner.current
    var playbackActive by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var slowLookup by remember(media.id, playerState.episode?.id) { mutableStateOf(false) }
    LaunchedEffect(media.id, playerState.episode?.id, playerState.isResolving) {
        slowLookup = false
        if (playerState.isResolving) {
            kotlinx.coroutines.delay(15_000L)
            slowLookup = true
        }
    }


    var thumbnailCues by remember(media.id, playerState.episode?.id) { mutableStateOf<List<SeekThumbnail>>(emptyList()) }
    var scrubPosition by remember(media.id, playerState.episode?.id) { mutableStateOf<Int?>(null) }
    var thumbnailRequested by remember(media.id, playerState.episode?.id) { mutableStateOf(false) }
    val thumbnailCaption = remember(playerState.captions) { playerState.captions.firstOrNull { it.type == "thumbnails" && it.url.startsWith("https://") } }
    LaunchedEffect(thumbnailRequested, thumbnailCaption?.url, media.id, playerState.episode?.id) {
        val caption = thumbnailCaption ?: return@LaunchedEffect
        if (!thumbnailRequested || playerState.isResolving) return@LaunchedEffect
        try {
            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val source = com.example.data.ScopedPlaybackHttp.factory(playerState.resolveHeaders, caption.url).createDataSource()
                try {
                    source.open(androidx.media3.datasource.DataSpec(android.net.Uri.parse(caption.url)))
                    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val count = source.read(buffer, 0, buffer.size)
                        if (count < 0) break
                        require(output.size() + count <= 1024 * 1024)
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                } finally { source.close() }
            }
            thumbnailCues = parseSeekThumbnails(text, caption.url)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { /* Optional thumbnail metadata never blocks the video. */ }
    }

    var availableAudioTracks by remember { mutableStateOf<List<PlayerTrackOption>>(emptyList()) }
    var availableSubtitleTracks by remember { mutableStateOf<List<PlayerTrackOption>>(emptyList()) }

    // Initialize ExoPlayer
    val exoPlayer = remember(context) {
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(3_000, 15_000, 900, 1_500)
            .setTargetBufferBytes(16 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguage(com.example.data.model.playbackLanguageCode(playerState.audioLanguage))
                .setPreferredTextLanguage(com.example.data.model.playbackLanguageCode(playerState.subtitleLanguage))
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, playerState.subtitleLanguage == "Off")
                .build()
        }
    }

    LaunchedEffect(spatialAudioEnabled) {
        exoPlayer.setAudioAttributes(androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setSpatializationBehavior(if (spatialAudioEnabled) C.SPATIALIZATION_BEHAVIOR_AUTO else C.SPATIALIZATION_BEHAVIOR_NEVER)
            .build(), true)
    }
    LaunchedEffect(maxVideoHeight) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(Int.MAX_VALUE, maxVideoHeight).build()
    }
    val loadingPercentage = com.example.ui.components.rememberPlayerLoadingPercentage(exoPlayer,
        resolving = playerState.isResolving || playerState.resolvedUrl.isNullOrBlank(), buffering = isBuffering,
        startupBufferMs = 900, rebufferMs = 1_500)

    fun reportProgress() {
        val state = latestPlayerState
        val expectedId = "${state.media?.id}:${state.episode?.id.orEmpty()}"
        if (state.media == null || exoPlayer.currentMediaItem?.mediaId != expectedId) return
        val duration = exoPlayer.duration
        if (duration > 0L && duration != C.TIME_UNSET) {
            latestProgressCallback?.invoke((exoPlayer.currentPosition.coerceAtLeast(0L) / 1000L).toInt(),
                (duration / 1000L).toInt())
        }
    }

    LaunchedEffect(media.id, playerState.episode?.id, playerState.captions, playerState.sourceId) {
        if (media.type != MediaType.TV_SHOW || playerState.sourceId == "Trailer") return@LaunchedEffect
        val caption = playerState.captions.firstOrNull {
            it.type != "thumbnails" && it.language.contains("English", true) &&
                it.url.startsWith("https://") && !it.url.contains(".m3u8", true)
        } ?: return@LaunchedEffect
        try {
            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val source = com.example.data.ScopedPlaybackHttp.factory(playerState.resolveHeaders, caption.url).createDataSource()
                try {
                    source.open(androidx.media3.datasource.DataSpec(android.net.Uri.parse(caption.url)))
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val count = source.read(buffer, 0, buffer.size)
                        if (count < 0) break
                        require(output.size() + count <= 256 * 1024)
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                } finally { source.close() }
            }
            latestSetIntroWindow(com.example.ui.viewmodel.EpisodePlaybackPolicy.detectIntro(text, latestPlayerState.durationSec))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Optional caption analysis must never interrupt playback.
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val state = latestPlayerState
                if (exoPlayer.currentMediaItem?.mediaId != "${state.media?.id}:${state.episode?.id.orEmpty()}") return
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_ENDED) {
                    reportProgress()
                    if (state.sourceId == "Trailer") {
                        latestTrailerEnded?.invoke()
                    } else latestContentEnded()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val expectedId = "${latestPlayerState.media?.id}:${latestPlayerState.episode?.id.orEmpty()}"
                if (exoPlayer.currentMediaItem?.mediaId != expectedId) return
                val limited = generateSequence<Throwable>(error) { it.cause }.take(16)
                    .filterIsInstance<com.example.data.PlaybackRateLimitedException>().firstOrNull()
                latestPlaybackFailed()
                playbackError = if (limited != null) {
                    "Playback is busy. Please wait ${((limited.retryAfterMs ?: 60_000L) + 999L) / 1_000L} seconds before trying again."
                } else "This title cannot be played. Try again later."
                isBuffering = false
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            }

            override fun onTracksChanged(tracks: Tracks) {
                val audioList = mutableListOf<PlayerTrackOption>()
                val subList = mutableListOf<PlayerTrackOption>()

                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until group.length) {
                            if (!group.isTrackSupported(i)) continue
                            val format = group.getTrackFormat(i)
                            val lang = format.language ?: ""
                            val rawLabel = format.label?.takeIf { it.isNotBlank() }
                            val displayLang = if (lang.isNotBlank()) {
                                try {
                                    java.util.Locale.forLanguageTag(lang).getDisplayLanguage(java.util.Locale.ENGLISH).ifBlank { lang }
                                } catch (_: Exception) { lang }
                            } else "English"

                            val label = when {
                                rawLabel != null -> rawLabel
                                displayLang.equals("English", ignoreCase = true) -> "English [Original]"
                                else -> displayLang
                            }
                            val isSelected = group.isTrackSelected(i)
                            val id = "audio_${group.mediaTrackGroup.id}_$i"

                            if (audioList.none { it.name == label }) {
                                audioList.add(
                                    PlayerTrackOption(
                                        id = id,
                                        name = label,
                                        language = lang,
                                        isSelected = isSelected,
                                        group = group,
                                        trackIndex = i
                                    )
                                )
                            }
                        }
                    } else if (group.type == C.TRACK_TYPE_TEXT) {
                        for (i in 0 until group.length) {
                            if (!group.isTrackSupported(i)) continue
                            val format = group.getTrackFormat(i)
                            val lang = format.language ?: ""
                            val rawLabel = format.label?.takeIf { it.isNotBlank() }
                            val displayLang = if (lang.isNotBlank()) {
                                try {
                                    java.util.Locale.forLanguageTag(lang).getDisplayLanguage(java.util.Locale.ENGLISH).ifBlank { lang }
                                } catch (_: Exception) { lang }
                            } else "English"
                            val label = rawLabel ?: displayLang
                            val isSelected = group.isTrackSelected(i)
                            val id = "sub_${group.mediaTrackGroup.id}_$i"

                            if (subList.none { it.name == label }) {
                                subList.add(
                                    PlayerTrackOption(
                                        id = id,
                                        name = label,
                                        language = lang,
                                        isSelected = isSelected,
                                        group = group,
                                        trackIndex = i
                                    )
                                )
                            }
                        }
                    }
                }


                val isAnySubSelected = subList.any { it.isSelected }
                val offOption = PlayerTrackOption(
                    id = "off",
                    name = "Off",
                    language = "",
                    isSelected = !isAnySubSelected
                )

                if (latestPlayerState.captions.isNotEmpty()) {
                    latestPlayerState.captions.filter { it.type != "thumbnails" }.forEachIndexed { idx, cap ->
                        val label = cap.language
                        if (subList.none { it.name.equals(label, ignoreCase = true) }) {
                            subList.add(
                                PlayerTrackOption(
                                    id = "caption_$idx",
                                    name = label,
                                    language = cap.languageCode,
                                    isSelected = false
                                )
                            )
                        }
                    }
                }

                availableAudioTracks = audioList
                availableSubtitleTracks = listOf(offOption) + subList
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            reportProgress()
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Load stream whenever resolvedUrl, resolveHeaders or captions change
    LaunchedEffect(media.id, playerState.episode?.id, playerState.resolvedUrl, playerState.resolveHeaders, playerState.captions) {
        playbackError = null
        val url = playerState.resolvedUrl
        if (!url.isNullOrEmpty()) {
            val headers = playerState.resolveHeaders
            val httpDataSourceFactory = com.example.data.ScopedPlaybackHttp.factory(headers, url)

            val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)

            val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
                .setCache(com.example.NetflixApplication.downloadCache)
                .setUpstreamDataSourceFactory(defaultDataSourceFactory)
                .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

            val mediaSourceFactory = DefaultMediaSourceFactory(com.example.data.GuardedPlaybackDataSourceFactory(cacheDataSourceFactory, defaultDataSourceFactory, manifestHeaders = headers))
                .setLoadErrorHandlingPolicy(com.example.data.PlaybackLoadErrorPolicy())
            val uri = if (url.startsWith("/")) {
                android.net.Uri.fromFile(java.io.File(url))
            } else {
                android.net.Uri.parse(url)
            }

            val subtitleConfigs = playerState.captions
                .filter { it.type != "thumbnails" }
                .map { caption ->
                    val mimeType = if (caption.type == "srt" || caption.url.endsWith(".srt", ignoreCase = true)) {
                        MimeTypes.APPLICATION_SUBRIP
                    } else {
                        MimeTypes.TEXT_VTT
                    }
                    Media3Item.SubtitleConfiguration.Builder(android.net.Uri.parse(caption.url))
                        .setMimeType(mimeType)
                        .setLanguage(caption.languageCode.ifEmpty { "en" })
                        .setLabel(caption.language)
                        .setSelectionFlags(if (caption.language.contains("English", ignoreCase = true)) C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                }

            val mediaItem = Media3Item.Builder()
                .setUri(uri)
                .setMediaId("${media.id}:${playerState.episode?.id.orEmpty()}")
                .apply {
                    if (url.contains(".m3u8") || url.contains("playlist") || url.contains("hls")) {
                        setMimeType(MimeTypes.APPLICATION_M3U8)
                    } else if (url.endsWith(".mp4")) {
                        setMimeType(MimeTypes.APPLICATION_MP4)
                    }
                }
                .setSubtitleConfigurations(subtitleConfigs)
                .build()

            val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO).clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setPreferredAudioLanguage(com.example.data.model.playbackLanguageCode(playerState.audioLanguage))
                .setPreferredTextLanguage(com.example.data.model.playbackLanguageCode(playerState.subtitleLanguage))
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, playerState.subtitleTrack == "Off").build()
            exoPlayer.setMediaSource(mediaSource)
            if (streamingAllowed) exoPlayer.prepare()
            if (playerState.isPlaying && playbackActive && streamingAllowed) {
                exoPlayer.play()
            } else {
                exoPlayer.pause()
            }
            if (playerState.currentPositionSec > 0) {
                exoPlayer.seekTo(playerState.currentPositionSec * 1000L)
            }
        } else {
            // A new title with no resolved URL must immediately release the previous stream.
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            availableAudioTracks = emptyList()
            availableSubtitleTracks = emptyList()
            isBuffering = false
        }
    }

    // Sync Play/Pause
    LaunchedEffect(playerState.isPlaying, playbackActive, streamingAllowed) {
        if (playerState.isPlaying && playbackActive && streamingAllowed) {
            if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.currentMediaItem != null) exoPlayer.prepare()
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0)
            }
            exoPlayer.play()
        } else {
            if (!streamingAllowed) exoPlayer.stop() else exoPlayer.pause()
        }
    }

    // Sync Playback Speed
    LaunchedEffect(playerState.playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playerState.playbackSpeed)
    }

    // Continuously sync ExoPlayer progress to ViewModel
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying && playbackActive) reportProgress()
            kotlinx.coroutines.delay(500)
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> playbackActive = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    playbackActive = false
                    reportProgress()
                    latestPersistProgress()
                    exoPlayer.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Force landscape orientation, immersive full screen and keep screen awake (Wakelock)
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }

        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = originalOrientation
            if (window != null && originalBrightness != null) {
                window.attributes = window.attributes.apply { screenBrightness = originalBrightness }
            }
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Auto-hide controls & lock pill after 3.5 seconds of playing to leave a clear screen
    LaunchedEffect(playerState.showControls, playerState.isPlaying, playerState.isLocked) {
        if (playerState.showControls && playerState.isPlaying) {
            kotlinx.coroutines.delay(3500)
            if (playerState.showControls) {
                onToggleControls()
            }
        }
    }

    // Apply brightness to window
    LaunchedEffect(brightnessLevel) {
        val activity = context as? Activity
        val window = activity?.window
        if (window != null) {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = brightnessLevel
            window.attributes = layoutParams
        }
    }

    // Speed Selection Dialog
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetSpeed(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = playerState.playbackSpeed == speed,
                                onClick = {
                                    onSetSpeed(speed)
                                    showSpeedDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = NetflixRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            },
            containerColor = NetflixDarkGray,
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Done", color = NetflixRed)
                }
            }
        )
    }

    // Audio & Subtitles Dialog
    if (playerState.showAudioSubtitleDialog) {
        val currentAudioTracks = availableAudioTracks
        val currentSubTracks = if (availableSubtitleTracks.isNotEmpty()) availableSubtitleTracks else listOf(
            PlayerTrackOption("off", "Off", "", true)
        )

        AlertDialog(
            onDismissRequest = { onShowAudioSubtitles(false) },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Audio & Subtitles", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { onShowAudioSubtitles(false) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            },
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = "AUDIO",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (currentAudioTracks.isEmpty()) item { Text("Audio tracks will appear when available.", color = Color.Gray, fontSize = 12.sp) }
                            items(currentAudioTracks, key = { it.id }) { track ->
                                val isSelected = track.isSelected
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (track.group != null) {
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                    .buildUpon()
                                                    .setOverrideForType(
                                                        TrackSelectionOverride(
                                                            track.group.mediaTrackGroup,
                                                            listOf(track.trackIndex)
                                                        )
                                                    )
                                                    .setPreferredAudioLanguage(track.language.ifEmpty { "en" })
                                                    .build()
                                            } else if (track.language.isNotEmpty()) {
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                    .buildUpon()
                                                    .setPreferredAudioLanguage(track.language)
                                                    .build()
                                            }
                                            availableAudioTracks = availableAudioTracks.map {
                                                it.copy(isSelected = it.id == track.id)
                                            }
                                            onSetAudio(track.name)
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = NetflixRed,
                                            unselectedColor = Color.Gray
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = track.name,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = "SUBTITLES",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(currentSubTracks) { track ->
                                val isSelected = if (track.id == "off" || track.name == "Off") {
                                    playerState.subtitleTrack == "Off" || (!currentSubTracks.any { it.id != "off" && it.isSelected } && playerState.subtitleTrack != "Off")
                                } else {
                                    playerState.subtitleTrack == track.name || track.isSelected
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (track.id == "off" || track.name == "Off") {
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                    .buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                                    .build()
                                                availableSubtitleTracks = availableSubtitleTracks.map {
                                                    it.copy(isSelected = it.id == "off" || it.name == "Off")
                                                }
                                                onSetSubtitle("Off")
                                            } else {
                                                if (track.group != null) {
                                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                        .buildUpon()
                                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                        .setOverrideForType(
                                                            TrackSelectionOverride(
                                                                track.group.mediaTrackGroup,
                                                                listOf(track.trackIndex)
                                                            )
                                                        )
                                                        .setPreferredTextLanguage(track.language.ifEmpty { "en" })
                                                        .build()
                                                } else if (track.language.isNotEmpty()) {
                                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                        .buildUpon()
                                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                                        .setPreferredTextLanguage(track.language)
                                                        .build()
                                                }
                                                availableSubtitleTracks = availableSubtitleTracks.map {
                                                    it.copy(isSelected = it.id == track.id)
                                                }
                                                onSetSubtitle(track.name)
                                            }
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = NetflixRed,
                                            unselectedColor = Color.Gray
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = track.name,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            containerColor = NetflixDarkGray,
            shape = RoundedCornerShape(12.dp),
            confirmButton = {
                TextButton(onClick = { onShowAudioSubtitles(false) }) {
                    Text("Apply", color = NetflixRed, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Episodes Bottom Sheet
    if (playerState.showEpisodeDrawer && media.type == MediaType.TV_SHOW) {
        ModalBottomSheet(
            onDismissRequest = { onShowEpisodesDrawer(false) },
            containerColor = NetflixDarkGray
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Episodes: ${media.title}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(media.episodes) { ep ->
                        val isCurrent = playerState.episode?.id == ep.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isCurrent) NetflixCardBg else Color.Transparent)
                                .clickable {
                                    onSelectEpisode(ep)
                                    onShowEpisodesDrawer(false)
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val epImageUrl = ep.stillUrl ?: media.backdropUrl ?: media.posterUrl
                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NetflixCardBg),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!epImageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = epImageUrl,
                                        contentDescription = ep.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                if (isCurrent) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Playing", tint = NetflixRed, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "E${ep.episodeNumber}. ${ep.title}",
                                    color = if (isCurrent) NetflixRed else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${ep.durationMinutes} min • ${ep.description}",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val handlePlayPauseToggle: () -> Unit = {
        if (!streamingAllowed) {
            exoPlayer.pause()
        } else if (playerState.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0)
            }
            exoPlayer.play()
        }
        if (streamingAllowed) onTogglePlayPause()
    }
    val handleSeek: (Int) -> Unit = { sec ->
        exoPlayer.seekTo(sec * 1000L)
        onSeek(sec)
    }
    val handleSkipBackward10: () -> Unit = {
        val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
        exoPlayer.seekTo(target)
        onSkipBackward10()
    }
    val handleSkipForward10: () -> Unit = {
        val maxDur = if (exoPlayer.duration > 0) exoPlayer.duration else (playerState.durationSec * 1000L)
        val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(maxDur)
        exoPlayer.seekTo(target)
        onSkipForward10()
    }
    val handleSkipIntro: () -> Unit = {
        playerState.introWindow?.let {
            exoPlayer.seekTo(it.endSec * 1000L)
            onSkipIntro()
        }
    }

    // Main Player Viewport
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(playerState.isResolving) {
                if (!playerState.isResolving) {
                    detectTapGestures(onTap = { onToggleControls() })
                }
            }
            .testTag("video_player_container")
    ) {
        // Real Video Stream PlayerView (Always attached cleanly to prevent surface recreation)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                    isClickable = false
                    isFocusable = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    subtitleView?.apply {
                        setStyle(
                            CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                                android.graphics.Color.BLACK,
                                null
                            )
                        )
                        setUserDefaultStyle()
                        setUserDefaultTextSize()
                        setBottomPaddingFraction(0.10f)
                    }
                }
            },
            update = { view ->
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )

        // Loading state: strictly ONLY the circle spinner on black/translucent canvas (no splash screen, no posters)
        val visiblePlaybackError = if (!streamingAllowed)
            "Connect to Wi-Fi or change Cellular Data in App Settings to continue playback."
            else playbackError ?: playerState.resolveError
        if (visiblePlaybackError != null) {
            Column(Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(visiblePlaybackError, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onClose) { Text("Back to title", color = Color.White) }
            }
        }
        if (visiblePlaybackError == null && (playerState.isResolving || isBuffering)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("player_loading_overlay")
                    .background(if (playerState.isResolving) Color.Black else Color.Transparent)
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NetflixSpinner(size = 72.dp, percentage = loadingPercentage)
                    if (slowLookup && playerState.isResolving) {
                        Text("Still finding your video…", color = Color.White)
                    }
                }
            }
        }

        // Controls overlay (Only visible once resolved)
        AnimatedVisibility(
            visible = visiblePlaybackError == null && !playerState.isResolving && !isBuffering && playerState.showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (playerState.isLocked) Color.Transparent else Color.Black.copy(alpha = 0.6f))
            ) {
                if (playerState.isLocked) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(start = 28.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(1.5.dp, Color.White, RoundedCornerShape(30.dp))
                            .clickable { onToggleLock() }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                            .testTag("player_unlock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Unlock",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Unlock",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // TOP BAR: Left (Logo + Title) | Centre (Transparent Thumbs) | Right (Cast + Close)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        // Left: Logo + Title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth(0.35f)
                        ) {
                            NetflixNLogo(size = 32.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = media.title,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (playerState.episode != null) {
                                    Text(
                                        text = "S1:E${playerState.episode.episodeNumber} ${playerState.episode.title}",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                            }
                        }

                        // Center Top: Reaction Thumbs (Dislike, Like, Double Like) - Transparent & No Enclosing Container
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            IconButton(
                                onClick = { onSetRating?.invoke("DISLIKE") },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("reaction_dislike")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_netflix_thumbs_down),
                                    contentDescription = "Dislike",
                                    tint = if (currentRating == "DISLIKE") NetflixRed else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(
                                onClick = { onSetRating?.invoke("LIKE") },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("reaction_like")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_netflix_thumbs_up),
                                    contentDescription = "Like",
                                    tint = if (currentRating == "LIKE") NetflixRed else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(
                                onClick = { onSetRating?.invoke("DOUBLE_LIKE") },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("reaction_double_like")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_netflix_love_this),
                                    contentDescription = "Love It",
                                    tint = if (currentRating == "DOUBLE_LIKE") NetflixRed else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Right: Cast & Close
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            IconButton(
                                onClick = { onOpenCast?.invoke() },
                                modifier = Modifier.testTag("player_cast_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cast,
                                    contentDescription = "Cast",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.testTag("player_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // LEFT SIDE: Brightness Vertical Slider Indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 28.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.LightMode,
                                contentDescription = "Brightness",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(bottom = 6.dp)
                            )
                            var barHeightPx by remember { mutableFloatStateOf(140f) }
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(130.dp)
                                    .onGloballyPositioned { coords ->
                                        barHeightPx = coords.size.height.toFloat().coerceAtLeast(1f)
                                    }
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White.copy(alpha = 0.25f))
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures { change, dragAmount ->
                                            change.consume()
                                            val deltaNormalized = -dragAmount / barHeightPx
                                            brightnessLevel = (brightnessLevel + deltaNormalized).coerceIn(0.1f, 1.0f)
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(brightnessLevel)
                                        .align(Alignment.BottomCenter)
                                        .background(Color.White)
                                )
                            }
                        }
                    }

                    // CENTER PLAYBACK CONTROLS
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = handleSkipBackward10,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("player_rewind_10")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_netflix_rewind10),
                                contentDescription = "Rewind 10 seconds",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        IconButton(
                            onClick = handlePlayPauseToggle,
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("player_play_pause_button")
                        ) {
                            Icon(
                                painter = painterResource(id = if (playerState.isPlaying) R.drawable.ic_netflix_pause else R.drawable.ic_netflix_play),
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        IconButton(
                            onClick = handleSkipForward10,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("player_forward_10")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_netflix_forward10),
                                contentDescription = "Forward 10 seconds",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    // BOTTOM CONTROLS & TIMELINE
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        // Timeline Scrubber & Duration Text matching SVG progress indicator
                        PlayerProgressBar(
                            currentSec = playerState.currentPositionSec,
                            durationSec = playerState.durationSec,
                            onSeek = handleSeek,
                            onScrub = { value -> scrubPosition = value; if (value != null) thumbnailRequested = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("player_timeline_slider")
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Bottom Actions Row: Speed (1x) | Lock | Audio & Subtitles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showSpeedDialog = true }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .testTag("player_speed_button")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_netflix_speed),
                                    contentDescription = "Speed",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Speed (${if (playerState.playbackSpeed == 1.0f) "1x" else "${playerState.playbackSpeed}x"})",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleLock() }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .testTag("player_lock_button")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_netflix_lock),
                                    contentDescription = "Lock",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Lock",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onShowAudioSubtitles(true) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .testTag("player_audio_subtitles_button")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_netflix_subtitles),
                                    contentDescription = "Audio & Subtitles",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Audio & Subtitles",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (media.type == MediaType.TV_SHOW && media.episodes.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(16.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onShowEpisodesDrawer(true) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .testTag("player_episodes_drawer_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ViewList,
                                        contentDescription = "Episodes",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Episodes",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val scrub = scrubPosition
        if (scrub != null && !playerState.isLocked) {
            val cue = remember(scrub, thumbnailCues) { seekThumbnailAt(thumbnailCues, scrub * 1000L) }
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp).background(Color(0xFF202020), RoundedCornerShape(6.dp)).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (cue != null) {
                    SeekThumbnailFrame(cue, playerState.resolveHeaders, "Preview at ${formatTimeString(scrub)}")
                }
                Text(formatTimeString(scrub), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(5.dp))
            }
        }

        var watchCredits by remember(media.id, playerState.episode?.id) { mutableStateOf(false) }
        val nearEnd = playerState.durationSec > 60 && playerState.currentPositionSec >= playerState.durationSec - 30
        LaunchedEffect(nearEnd) { if (!nearEnd) watchCredits = false }
        if (playerState.hasEnded && playerState.sourceId != "Trailer" && !playerState.isLocked) {
            PostPlayOverlay(playerState, recommendations,
                onReplay = { watchCredits = false; exoPlayer.seekTo(0); onSeek(0); onReplay() },
                onClose = onClose, onNext = { reportProgress(); onPlayNextEpisode() }, onRetryNext = onRetryNext,
                onRecommendation = { reportProgress(); onRecommendationClick(it) }, onRating = { onSetRating?.invoke(it) })
        }
        if (playerState.nextEpisode != null && playerState.sourceId != "Trailer" && !playerState.isLocked &&
            !playerState.isResolving && !watchCredits && (nearEnd || playerState.hasEnded)) {
            NextEpisodeCard(playerState, autoPlayNext, playbackActive && streamingAllowed,
                onNext = { reportProgress(); onPlayNextEpisode() }, onWatchCredits = { watchCredits = true },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 24.dp, bottom = 48.dp))
        }

        // Animated "Skip Intro" pop-up on bottom-right
        AnimatedVisibility(
            visible = playerState.showSkipIntro && !playerState.isLocked,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 24.dp, bottom = 80.dp)
        ) {
            Button(
                onClick = handleSkipIntro,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black.copy(alpha = 0.8f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                    .testTag("skip_intro_button")
            ) {
                Text(
                    text = "SKIP INTRO",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun PlayerProgressBar(
    currentSec: Int,
    durationSec: Int,
    onSeek: (Int) -> Unit,
    onScrub: (Int?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val latestSeek by rememberUpdatedState(onSeek)
    val latestScrub by rememberUpdatedState(onScrub)
    val progress = if (durationSec > 0) (currentSec.toFloat() / durationSec.toFloat()).coerceIn(0f, 1f) else 0f

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        var isDragging by remember { mutableStateOf(false) }
        var dragProgress by remember { mutableFloatStateOf(0f) }
        val displayProgress = if (isDragging) dragProgress else progress

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .pointerInput(durationSec) {
                    detectTapGestures { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        latestSeek((newProgress * durationSec).toInt())
                    }
                }
                .pointerInput(durationSec) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragProgress = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            latestScrub((dragProgress * durationSec).toInt())
                        },
                        onDragEnd = {
                            isDragging = false
                            latestSeek((dragProgress * durationSec).toInt())
                            latestScrub(null)
                        },
                        onDragCancel = {
                            isDragging = false
                            latestScrub(null)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val widthPx = size.width.toFloat().coerceAtLeast(1f)
                            dragProgress = (dragProgress + dragAmount / widthPx).coerceIn(0f, 1f)
                            latestScrub((dragProgress * durationSec).toInt())
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidth = maxWidth
            val activeWidth = totalWidth * displayProgress
            val thumbRadius = 12.dp

            // Inactive Track: #737373, height 4.dp, rx 2.dp (matches SVG)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF737373))
            )

            // Active Track: #D22F26 (NetflixRed), height 4.dp, rx 2.dp
            Box(
                modifier = Modifier
                    .width(activeWidth)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NetflixRed)
            )

            // Red Circle Thumb Knob: r = 12.dp (size 24.dp), fill #D22F26
            val thumbOffset = (activeWidth - thumbRadius).coerceIn(0.dp, (totalWidth - thumbRadius * 2).coerceAtLeast(0.dp))
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(thumbRadius * 2)
                    .background(NetflixRed, CircleShape)
            )
        }

        // Duration / Remaining text on the right
        val remainingSec = (durationSec - (displayProgress * durationSec).toInt()).coerceAtLeast(0)
        Text(
            text = formatTimeString(remainingSec),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
