package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import com.example.ui.components.NetflixSpinner
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.CatalogData
import com.example.data.local.WatchProgressEntity
import com.example.data.model.MediaItem
import com.example.data.model.Episode
import com.example.ui.components.ContinueWatchingOptionsSheet
import com.example.ui.components.NetflixBottomNav
import com.example.ui.components.NetflixTopBar
import com.example.ui.screens.AvatarPickerSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CastDialog
import com.example.ui.screens.ClipsScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.EditProfileScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MyNetflixScreen
import com.example.ui.screens.NetflixSettingsScreen
import com.example.ui.screens.NetflixSettingsSheet
import com.example.data.model.NotificationIconType
import com.example.ui.screens.NewAndHotScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.NotificationsSheet
import com.example.ui.screens.ProfileLoadingOverlay
import com.example.ui.screens.ProfilePickerSheet
import com.example.ui.screens.SubscriptionSheet
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.TvPairScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixTheme
import com.example.ui.viewmodel.CategoryFilter
import com.example.ui.viewmodel.NavigationTab
import com.example.ui.viewmodel.NetflixViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

class MainActivity : ComponentActivity() {
    private var viewModelInstance: NetflixViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        setContent {
            NetflixTheme {
                com.example.update.UpdateGateHost {
                val viewModel: NetflixViewModel = viewModel()
                viewModelInstance = viewModel
                LaunchedEffect(intent) {
                    handleIntent(intent, viewModel)
                }
                NetflixApp(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModelInstance?.let { handleIntent(intent, it) }
    }

    private fun handleIntent(intent: android.content.Intent?, viewModel: NetflixViewModel) {
        if (intent == null) return
        if (intent.getBooleanExtra("open_downloads", false)) {
            viewModel.openDownloadsScreen(true)
        } else if (intent.getBooleanExtra("open_notifications", false)) {
            viewModel.openNotificationsSheet(true)
        } else if (intent.action == android.content.Intent.ACTION_VIEW || intent.data != null) {
            val dataStr = intent.dataString ?: intent.data?.toString()
            if (!dataStr.isNullOrBlank()) {
                val code = com.example.data.FirebaseSyncManager.extractPairingCode(dataStr)
                if (code.isNotBlank()) {
                    viewModel.openTvPairScreen(true)
                }
            }
        }
    }
}

@Composable
fun NetflixApp(viewModel: NetflixViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val selectedCategoryGenre by viewModel.selectedCategoryGenre.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val showProfilePicker by viewModel.showProfilePicker.collectAsStateWithLifecycle()
    val editingProfile by viewModel.editingProfile.collectAsStateWithLifecycle()
    val showEditProfileScreen by viewModel.showEditProfileScreen.collectAsStateWithLifecycle()
    val showAvatarPicker by viewModel.showAvatarPicker.collectAsStateWithLifecycle()
    val transitioningProfile by viewModel.transitioningProfile.collectAsStateWithLifecycle()
    val selectedMedia by viewModel.selectedMedia.collectAsStateWithLifecycle()
    val isPlayerVisible by remember(viewModel) {
        viewModel.playerState.map { it.media != null }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedSearchGenre by viewModel.selectedSearchGenre.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()
    val isSavingProfile by viewModel.isSavingProfile.collectAsStateWithLifecycle()
    val profileSaveError by viewModel.profileSaveError.collectAsStateWithLifecycle()
    val downloadsForYouEnabled by viewModel.downloadsForYouEnabled.collectAsStateWithLifecycle()
    val profileDownloadAllocations by viewModel.profileDownloadAllocations.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val continueWatchingOptions by viewModel.continueWatchingOptions.collectAsStateWithLifecycle()
    val allWatchProgress by viewModel.allWatchProgress.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val likedMedia by viewModel.likedMedia.collectAsStateWithLifecycle()
    val watchedTrailers by viewModel.watchedTrailers.collectAsStateWithLifecycle()
    val games by viewModel.displayGames.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val showNotificationsSheet by viewModel.showNotificationsSheet.collectAsStateWithLifecycle()
    val showSettingsDrawer by viewModel.showSettingsDrawer.collectAsStateWithLifecycle()
    val showCastDialog by viewModel.showCastDialog.collectAsStateWithLifecycle()
    val connectedCastDevice by viewModel.connectedCastDevice.collectAsStateWithLifecycle()
    val isSmartDownloadsEnabled by viewModel.isSmartDownloadsEnabled.collectAsStateWithLifecycle()
    val allocatedStorageGb by viewModel.allocatedStorageGb.collectAsStateWithLifecycle()
    val isWifiOnlyEnabled by viewModel.isWifiOnlyEnabled.collectAsStateWithLifecycle()
    val isHighQualityEnabled by viewModel.isHighQualityEnabled.collectAsStateWithLifecycle()
    val isAutoPlayNextEnabled by viewModel.isAutoPlayNextEnabled.collectAsStateWithLifecycle()
    val isAutoPlayPreviewsEnabled by viewModel.isAutoPlayPreviewsEnabled.collectAsStateWithLifecycle()
    val isSpatialAudioEnabled by viewModel.isSpatialAudioEnabled.collectAsStateWithLifecycle()
    val cellularDataOption by viewModel.cellularDataOption.collectAsStateWithLifecycle()
    val showDownloadsScreen by viewModel.showDownloadsScreen.collectAsStateWithLifecycle()
    val openSmartDownloadSettings by viewModel.openSmartDownloadSettings.collectAsStateWithLifecycle()
    val playbackNetwork by viewModel.playbackNetworkState.collectAsStateWithLifecycle()
    // Home does not subscribe to high-frequency transfer ticks while these destinations are hidden.
    val observeTransfers = showDownloadsScreen || showSettingsDrawer || (selectedMedia != null && !isPlayerVisible)
    val downloadingProgress by if (observeTransfers) viewModel.downloadingProgress.collectAsStateWithLifecycle()
        else remember { mutableStateOf(emptyMap<String, Float>()) }
    val downloadTasks by if (observeTransfers) viewModel.downloadTasks.collectAsStateWithLifecycle()
        else remember { mutableStateOf(emptyMap<String, com.example.data.download.DownloadTaskInfo>()) }
    val pausedDownloadKeys by if (observeTransfers) viewModel.pausedDownloadKeys.collectAsStateWithLifecycle()
        else remember { mutableStateOf(emptySet<String>()) }
    val detailLoadState by viewModel.detailLoadState.collectAsStateWithLifecycle()

    val showTvPairScreen by viewModel.showTvPairScreen.collectAsStateWithLifecycle()
    val pairedTvSessions by viewModel.pairedTvSessions.collectAsStateWithLifecycle()
    val currentUserEmail by viewModel.currentUserEmail.collectAsStateWithLifecycle()
    val showAuthScreen by viewModel.showAuthScreen.collectAsStateWithLifecycle()
    val showSubscriptionSheet by viewModel.showSubscriptionSheet.collectAsStateWithLifecycle()
    val userSubscription by viewModel.userSubscription.collectAsStateWithLifecycle()
    val diagnosticRunning by viewModel.diagnosticRunning.collectAsStateWithLifecycle()
    val diagnosticResult by viewModel.diagnosticResult.collectAsStateWithLifecycle()
    val catalogMedia by viewModel.displayCatalogMedia.collectAsStateWithLifecycle()
    val isLoadingCatalog by viewModel.isLoadingCatalog.collectAsStateWithLifecycle()
    val isWarmupFinished by viewModel.isWarmupFinished.collectAsStateWithLifecycle()
    val hasCompletedSplash by viewModel.hasCompletedSplash.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Handle System Back Press logic
    BackHandler(
        enabled = showAuthScreen ||
                showTvPairScreen ||
                showAvatarPicker ||
                showEditProfileScreen ||
                showDownloadsScreen ||
                isPlayerVisible ||
                selectedMedia != null ||
                continueWatchingOptions != null ||
                showNotificationsSheet ||
                showSettingsDrawer ||
                showCastDialog ||
                selectedTab != NavigationTab.HOME ||
                categoryFilter != CategoryFilter.ALL
    ) {
        when {
            showAuthScreen -> viewModel.openAuthScreen(false)
            showTvPairScreen -> viewModel.openTvPairScreen(false)
            showAvatarPicker -> viewModel.closeAvatarPicker()
            showEditProfileScreen -> viewModel.closeEditProfile()
            showDownloadsScreen -> viewModel.openDownloadsScreen(false)
            isPlayerVisible -> viewModel.closePlayer()
            continueWatchingOptions != null -> viewModel.closeContinueWatchingOptions()
            showNotificationsSheet -> viewModel.openNotificationsSheet(false)
            showSettingsDrawer -> viewModel.openSettingsDrawer(false)
            showCastDialog -> viewModel.openCastDialog(false)
            selectedMedia != null -> viewModel.closeDetail()
            selectedTab != NavigationTab.HOME -> viewModel.selectTab(NavigationTab.HOME)
            categoryFilter != CategoryFilter.ALL -> viewModel.setCategoryFilter(CategoryFilter.ALL, null)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize().padding(contentPadding)
            ) {
                // Tab Content Switcher
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "TabContent"
                ) { tab ->
                    when (tab) {
                        NavigationTab.HOME -> {
                            val homeListState = androidx.compose.foundation.lazy.rememberLazyListState()
                            val scrollFractionState = remember {
                                androidx.compose.runtime.derivedStateOf {
                                    if (homeListState.firstVisibleItemIndex > 0) {
                                        1f
                                    } else {
                                        val offset = homeListState.firstVisibleItemScrollOffset.toFloat()
                                        (offset / 180f).coerceIn(0f, 1f)
                                    }
                                }
                            }

                            // Stabilize callbacks to prevent cascading recompositions on parent state changes
                            val stableIsWatchlistContains = remember(viewModel) { { id: String -> viewModel.isMediaInWatchlist(id) } }
                            val stableOnMediaClick = remember(viewModel) { { item: MediaItem -> viewModel.openDetail(item) } }
                            val stableOnPlayClick = remember(viewModel) { { item: MediaItem -> viewModel.playMedia(item) } }
                            val stableOnWatchlistToggle = remember(viewModel) { { item: MediaItem -> viewModel.toggleWatchlist(item) } }
                            val stableOnToggleReminder = remember(viewModel) { { item: MediaItem -> viewModel.toggleReminder(item) } }
                            val stableOnContinueWatchingOptionsClick = remember(viewModel) {
                                { media: MediaItem, progress: WatchProgressEntity -> viewModel.openContinueWatchingOptions(media, progress) }
                            }
                            val stableOnAmbientColorChange = remember(viewModel) { { color: Color -> viewModel.updateAmbientColor(color) } }
                            val stableOnCategorySelected = remember(viewModel) {
                                { filter: CategoryFilter, genre: String? -> viewModel.setCategoryFilter(filter, genre) }
                            }
                            val stableOnNewAndHotClick = remember(viewModel) { { viewModel.selectTab(NavigationTab.NEW_HOT) } }
                            val stableOnDownloadsClick = remember(viewModel) { { viewModel.openDownloadsScreen(true) } }
                            val stableOnNotificationsClick = remember(viewModel) { { viewModel.openNotificationsSheet(true) } }

                            Box(modifier = Modifier.fillMaxSize()) {
                                if (categoryFilter == CategoryFilter.GAMES) {
                                    var selectedGame by remember { mutableStateOf<com.example.data.model.GameItem?>(null) }

                                    com.example.ui.screens.GamesScreen(
                                        games = games,
                                        onGameClick = { selectedGame = it },
                                        onAmbientColorChange = stableOnAmbientColorChange,
                                        listState = homeListState
                                    )

                                    selectedGame?.let { game ->
                                        com.example.ui.screens.GameDetailSheet(
                                            game = game,
                                            onDismiss = { selectedGame = null }
                                        )
                                    }
                                } else {
                                    HomeScreen(
                                        catalogMedia = catalogMedia,
                                        userSubscription = userSubscription,
                                        reminders = reminders,
                                        onToggleReminder = stableOnToggleReminder,
                                        isLoadingCatalog = isLoadingCatalog,
                                        onRetryCatalog = viewModel::refreshCatalog,
                                        onOpenDownloads = stableOnDownloadsClick,
                                        activeProfile = activeProfile,
                                        categoryFilter = categoryFilter,
                                        selectedGenre = selectedCategoryGenre,
                                        continueWatchingList = continueWatching,
                                        isWatchlistContains = stableIsWatchlistContains,
                                        onMediaClick = stableOnMediaClick,
                                        onPlayClick = stableOnPlayClick,
                                        onWatchlistToggle = stableOnWatchlistToggle,
                                        onContinueWatchingOptionsClick = stableOnContinueWatchingOptionsClick,
                                        onAmbientColorChange = stableOnAmbientColorChange,
                                        listState = homeListState
                                    )
                                }

                                // Transparent Top Navigation Bar overlaid on Home
                                NetflixTopBar(
                                    activeProfile = activeProfile,
                                    currentCategory = categoryFilter,
                                    selectedGenre = selectedCategoryGenre,
                                    onCategorySelected = stableOnCategorySelected,
                                    onNewAndHotClick = stableOnNewAndHotClick,
                                    onDownloadsClick = stableOnDownloadsClick,
                                    onNotificationsClick = stableOnNotificationsClick,
                                    isGamesAllowed = userSubscription.isGamesAllowed,
                                    unreadNotificationCount = notifications.count { !it.isRead },
                                    modifier = Modifier.align(Alignment.TopCenter),
                                    scrollFractionProvider = { scrollFractionState.value }
                                )
                            }
                        }

                        NavigationTab.NEW_HOT -> {
                            NewAndHotScreen(
                                reminders = reminders,
                                games = games,
                                notifications = notifications,
                                connectedCastDevice = connectedCastDevice,
                                onToggleReminder = { viewModel.toggleReminder(it) },
                                onMediaClick = { viewModel.openDetail(it) },
                                onPlayClick = { viewModel.playMedia(it) },
                                onWatchlistToggle = { viewModel.toggleWatchlist(it) },
                                isWatchlistContains = { viewModel.isMediaInWatchlist(it) },
                                onInstallGame = { viewModel.installGame(it) },
                                onLaunchGame = { viewModel.launchGame(it) },
                                onOpenNotifications = { viewModel.openNotificationsSheet(true) },
                                onOpenCast = { viewModel.openCastDialog(true) },
                                onOpenSearch = { viewModel.selectTab(NavigationTab.SEARCH) },
                                onShowToast = { viewModel.showToast(it) }
                            )
                        }

                        NavigationTab.CLIPS -> {
                            ClipsScreen(
                                streamingAllowed = com.example.data.MobilePlaybackPolicy.permitsStreaming(
                                    com.example.data.CellularDataMode.fromLabel(cellularDataOption), playbackNetwork.wifiOrEthernet),
                                maxVideoHeight = com.example.data.MobilePlaybackPolicy.maxVideoHeight(isHighQualityEnabled,
                                    com.example.data.CellularDataMode.fromLabel(cellularDataOption), playbackNetwork.wifiOrEthernet, userSubscription.planId),
                                spatialAudioEnabled = isSpatialAudioEnabled && userSubscription.isActive && userSubscription.planId == "plan_premium",
                                reminders = reminders,
                                likedMedia = likedMedia,
                                notifications = notifications,
                                connectedCastDevice = connectedCastDevice,
                                onToggleReminder = { viewModel.toggleReminder(it) },
                                onToggleLike = { viewModel.setRating(it, "LIKE") },
                                onMediaClick = { viewModel.openDetail(it) },
                                onPlayClick = { viewModel.playTrailer(it, "Teaser: ${it.title}") },
                                onWatchlistToggle = { viewModel.toggleWatchlist(it) },
                                isWatchlistContains = { viewModel.isMediaInWatchlist(it) },
                                onOpenNotifications = { viewModel.openNotificationsSheet(true) },
                                onOpenCast = { viewModel.openCastDialog(true) },
                                onOpenSearch = { viewModel.selectTab(NavigationTab.SEARCH) },
                                onShowToast = { viewModel.showToast(it) }
                            )
                        }

                        NavigationTab.SEARCH -> {
                            SearchScreen(
                                searchQuery = searchQuery,
                                selectedGenre = selectedSearchGenre,
                                searchResults = searchResults,
                                userSubscription = userSubscription,
                                onQueryChange = { viewModel.setSearchQuery(it) },
                                onGenreFilterSelect = { viewModel.setSearchGenre(it) },
                                onMediaClick = { viewModel.openDetail(it) },
                                onPlayClick = { viewModel.playMedia(it) },
                                isSearching = isSearching, searchError = searchError,
                                connectedCastDevice = connectedCastDevice,
                                onOpenCast = { viewModel.openCastDialog(true) },
                                onShowToast = { viewModel.showToast(it) }
                            )
                        }

                        NavigationTab.MY_NETFLIX -> {
                            MyNetflixScreen(
                                activeProfile = activeProfile,
                                watchlist = watchlist,
                                downloads = downloads,
                                likedMedia = likedMedia,
                                watchedTrailers = watchedTrailers,
                                notifications = notifications,
                                continueWatchingList = continueWatching,
                                watchHistory = watchHistory,
                                reminders = reminders,
                                connectedCastDevice = connectedCastDevice,
                                smartDownloadsEnabled = isSmartDownloadsEnabled,
                                onSwitchProfileClick = { viewModel.openProfilePicker(true) },
                                onMediaClick = { viewModel.openDetail(it) },
                                onPlayClick = { viewModel.playMedia(it) },
                                onPlayTrailerClick = { viewModel.playTrailer(it) },
                                onOpenDownloads = { viewModel.openDownloadsScreen(true) },
                                onDeleteDownload = { viewModel.removeDownload(it) },
                                onClearAllDownloads = { viewModel.clearAllDownloads() },
                                onWatchlistToggle = { viewModel.toggleWatchlist(it) },
                                onReminderToggle = { viewModel.toggleReminder(it) },
                                onContinueWatchingOptionsClick = { media, progress ->
                                    viewModel.openContinueWatchingOptions(media, progress)
                                },
                                onHistoryClick = { viewModel.openHistoryDetail(it) },
                                onOpenNotifications = { viewModel.openNotificationsSheet(true) },
                                onOpenSettings = { viewModel.openSettingsDrawer(true) },
                                onOpenCast = { viewModel.openCastDialog(true) },
                                onNavigateToSearch = { viewModel.selectTab(NavigationTab.SEARCH) },
                                onShowToast = { viewModel.showToast(it) },
                                onOpenTvPair = { viewModel.openTvPairScreen(true) },
                                onOpenClips = { viewModel.selectTab(NavigationTab.CLIPS) },
                                userSubscription = userSubscription,
                                onOpenAuth = { viewModel.openAuthScreen(true) }
                            )
                        }
                    }
                }

                // Floating Bottom Navigation Bar
                if (!isPlayerVisible && selectedMedia == null) {
                    val onTabSelected = remember(viewModel) { { tab: NavigationTab -> viewModel.selectTab(tab) } }
                    val ambientColorProvider = remember(viewModel) { { viewModel.ambientColor.value } }
                    NetflixBottomNav(
                        selectedTab = selectedTab,
                        activeProfile = activeProfile,
                        isClipsAllowed = userSubscription.isClipsAllowed,
                        ambientColorProvider = ambientColorProvider,
                        onTabSelected = onTabSelected,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // Modal Detail Screen Overlay
                AnimatedVisibility(
                    visible = selectedMedia != null && !isPlayerVisible,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    val media = selectedMedia
                    if (media != null) {
                        val completedDownloadKeys = remember(downloads) {
                            downloads.filter { it.isComplete }.map { it.downloadKey }.toSet()
                        }
                        DetailScreen(
                            media = media,
                            isInWatchlist = viewModel.isMediaInWatchlist(media.id),
                            userSubscription = userSubscription,
                            watchProgressList = allWatchProgress,
                            downloadProgressMap = downloadingProgress,
                            pausedDownloadKeys = pausedDownloadKeys,
                            completedDownloadKeys = completedDownloadKeys,
                            loadState = detailLoadState,
                            onRetryDetails = viewModel::retryMediaDetails,
                            downloadTasks = downloadTasks,
                            previewEnabled = isAutoPlayPreviewsEnabled && activeProfile.autoplayPreviews &&
                                com.example.data.MobilePlaybackPolicy.permitsStreaming(com.example.data.CellularDataMode.fromLabel(cellularDataOption), playbackNetwork.wifiOrEthernet),
                            kidMaxAge = activeProfile.contentMaxAge.takeIf { activeProfile.hasMaturityRestriction },
                            isActive = !isPlayerVisible && !showDownloadsScreen && !showAuthScreen && !showSubscriptionSheet,
                            onOpenDownloads = { viewModel.openDownloadsScreen(true) },
                            onOpenAuth = { viewModel.openAuthScreen(true) },
                            onOpenSubscription = { viewModel.openSubscriptionSheet(true) },
                            onClose = { viewModel.closeDetail() },
                            onPlayClick = { m, ep ->
                                viewModel.playMedia(m, ep)
                            },
                            onPlayTrailerClick = { m, title ->
                                viewModel.playTrailer(m, title)
                            },
                            onWatchlistToggle = { viewModel.toggleWatchlist(media) },
                            onDownloadClick = { m, ep -> viewModel.startDownload(m, ep) },
                            onRatingSelect = { rating -> viewModel.setRating(media.id, rating) },
                            onSimilarMediaClick = { sim -> viewModel.openDetail(sim) },
                            onSeasonSelect = { seasonNum -> viewModel.fetchTvSeasonEpisodes(media, seasonNum) },
                            onPauseDownload = { key -> viewModel.pauseDownload(key) },
                            onResumeDownload = { key -> viewModel.resumeDownload(key) },
                            onCancelDownload = { key -> viewModel.cancelDownload(key) }
                        )
                    }
                }
            }
        }

        PlayerOverlay(viewModel, isAutoPlayNextEnabled && activeProfile.autoplayNext)

        // Profile Picker Full Screen Overlay
        AnimatedVisibility(
            visible = showProfilePicker,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            ProfilePickerSheet(
                profiles = profiles,
                featuredMedia = com.example.ui.components.featuredTrendingMedia(catalogMedia),
                activeProfile = activeProfile,
                userSubscription = userSubscription,
                onSelectProfile = { viewModel.selectProfile(it) },
                onAddProfile = {
                    if (profiles.size >= userSubscription.maxProfiles) {
                        viewModel.showToast("Profile limit reached (${userSubscription.maxProfiles} profile(s) on ${userSubscription.planName} Plan). Upgrade to add more.")
                        viewModel.openSubscriptionSheet(true)
                    } else {
                        viewModel.openProfilePicker(false)
                        viewModel.openEditProfile(null)
                    }
                },
                onEditProfile = { p ->
                    viewModel.openProfilePicker(false)
                    viewModel.openEditProfile(p)
                },
                onUpgradePlan = {
                    viewModel.showToast("Upgrade subscription to create up to 5 profiles.")
                    viewModel.openSubscriptionSheet(true)
                },
                onDismiss = { viewModel.openProfilePicker(false) }
            )
        }

        // Profile Loading & Assimilation Transition Overlay
        if (transitioningProfile != null) {
            transitioningProfile?.let { prof ->
                ProfileLoadingOverlay(
                    profile = prof,
                    isWarmupFinished = isWarmupFinished,
                    onAnimationComplete = { viewModel.clearTransitioningProfile() }
                )
            }
        }

        // 2026 Edit Profile Full Screen Destination
        AnimatedVisibility(
            visible = showEditProfileScreen && editingProfile != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            editingProfile?.let { prof ->
                EditProfileScreen(
                    profile = prof,
                    onSaveProfile = { viewModel.saveProfile(it) },
                    onDeleteProfile = { viewModel.deleteProfile(it) },
                    onOpenAvatarPicker = { viewModel.openAvatarPicker() },
                    onDismiss = { viewModel.closeEditProfile() },
                    canDelete = profiles.size > 1 && profiles.any { it.id == prof.id },
                    isSaving = isSavingProfile, saveError = profileSaveError
                )
            }
        }

        // 2026 Avatar / Icon Gallery Screen Overlay
        AnimatedVisibility(
            visible = showAvatarPicker,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            AvatarPickerSheet(
                currentAvatarUrl = editingProfile?.avatarUrl,
                onSelectAvatar = { viewModel.selectAvatarUrl(it) },
                onDismiss = { viewModel.closeAvatarPicker() }
            )
        }

        // Continue Watching 3-Dots Options Bottom Sheet Overlay
        AnimatedVisibility(
            visible = continueWatchingOptions != null && !isPlayerVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            val optionData = continueWatchingOptions
            if (optionData != null) {
                val (media, progress) = optionData
                ContinueWatchingOptionsSheet(
                    media = media,
                    progress = progress,
                    isInWatchlist = viewModel.isMediaInWatchlist(media.id),
                    onDismiss = { viewModel.closeContinueWatchingOptions() },
                    onEpisodesAndInfoClick = {
                        viewModel.closeContinueWatchingOptions()
                        viewModel.openDetail(media)
                    },
                    onDownloadClick = {
                        viewModel.closeContinueWatchingOptions()
                        viewModel.startDownload(media)
                    },
                    onRemoveFromRowClick = {
                        viewModel.removeFromContinueWatching(media.id)
                    },
                    onWatchlistToggle = {
                        viewModel.toggleWatchlist(media)
                    },
                    onLikeClick = {
                        viewModel.setRating(media.id, "LIKE")
                        viewModel.closeContinueWatchingOptions()
                    }
                )
            }
        }

        // Dedicated Downloads Full Screen Overlay Destination
        AnimatedVisibility(
            visible = showDownloadsScreen,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            DownloadsScreen(
                downloads = downloads,
                catalogMedia = catalogMedia,
                downloadingProgress = downloadingProgress,
                downloadTasks = downloadTasks,
                pausedDownloadKeys = pausedDownloadKeys,
                smartDownloadsEnabled = isSmartDownloadsEnabled,
                activeProfile = activeProfile, profiles = profiles,
                openSmartSettings = openSmartDownloadSettings,
                downloadsForYouEnabled = downloadsForYouEnabled,
                profileAllocations = profileDownloadAllocations,
                onToggleDownloadsForYou = { viewModel.toggleDownloadsForYou(it) },
                onProfileAllocation = { id, gb -> viewModel.setProfileDownloadAllocation(id, gb) },
                onOpenProfiles = { viewModel.openDownloadsScreen(false); viewModel.openProfilePicker(true) },
                allocatedStorageGb = allocatedStorageGb,
                connectedCastDevice = connectedCastDevice,
                onClose = { viewModel.openDownloadsScreen(false) },
                onPlayMedia = { media, episode ->
                    viewModel.openDownloadsScreen(false)
                    viewModel.playMedia(media, episode, offlineOnly = true)
                },
                onDeleteDownload = { viewModel.removeDownload(it) },
                onClearAllDownloads = { viewModel.clearAllDownloads() },
                onToggleSmartDownloads = { viewModel.toggleSmartDownloads(it) },
                onUpdateAllocatedStorage = { viewModel.updateAllocatedStorage(it) },
                onSetUpDownloadsForYouWithAllocation = { viewModel.setupDownloadsForYouWithAllocation(it) },
                onSetUpDownloadsForYou = { viewModel.setupDownloadsForYou() },
                onOpenSearch = {
                    viewModel.openDownloadsScreen(false)
                    viewModel.selectTab(NavigationTab.SEARCH)
                },
                onOpenCast = { viewModel.openCastDialog(true) },
                onOpenMediaDetail = { media ->
                    viewModel.openDownloadsScreen(false)
                    viewModel.openDetail(media)
                },
                onShowToast = { viewModel.showToast(it) },
                onPauseDownload = { key -> viewModel.pauseDownload(key) },
                onResumeDownload = { key -> viewModel.resumeDownload(key) },
                onCancelDownload = { key -> viewModel.cancelDownload(key) },
                userSubscription = userSubscription,
                onOpenSubscription = { viewModel.openSubscriptionSheet(true) },
                onOpenAuth = { viewModel.openAuthScreen(true) }
            )
        }

        // 2026 Notifications Full Screen Destination
        AnimatedVisibility(
            visible = showNotificationsSheet,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            NotificationsScreen(
                notifications = notifications,
                onClose = { viewModel.openNotificationsSheet(false) },
                onMarkAllRead = { viewModel.markAllNotificationsAsRead() },
                onNotificationClick = { notif ->
                    viewModel.markNotificationAsRead(notif.id)
                    if (notif.iconType == NotificationIconType.DOWNLOAD) {
                        viewModel.openNotificationsSheet(false)
                        viewModel.openDownloadsScreen(true)
                    } else {
                        val matchedMedia = notif.mediaId?.let { CatalogData.getById(it) }
                        if (matchedMedia != null) {
                            viewModel.openNotificationsSheet(false)
                            viewModel.openDetail(matchedMedia)
                        }
                    }
                },
                onDeleteNotification = { viewModel.removeNotification(it) },
                onPlayMedia = { media ->
                    viewModel.openNotificationsSheet(false)
                    viewModel.playMedia(media)
                }
            )
        }

        // 2026 App Settings Screen Destination
        AnimatedVisibility(
            visible = showSettingsDrawer,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it }
        ) {
            NetflixSettingsScreen(
                activeProfile = activeProfile,
                smartDownloadsEnabled = isSmartDownloadsEnabled,
                wifiOnlyEnabled = isWifiOnlyEnabled,
                highQualityEnabled = isHighQualityEnabled,
                autoPlayNextEnabled = isAutoPlayNextEnabled && activeProfile.autoplayNext,
                autoPlayPreviewsEnabled = isAutoPlayPreviewsEnabled && activeProfile.autoplayPreviews,
                spatialAudioEnabled = isSpatialAudioEnabled,
                cellularDataOption = cellularDataOption,
                diagnosticRunning = diagnosticRunning,
                diagnosticResult = diagnosticResult,
                totalStorageUsedMb = downloads.sumOf { it.fileSizeMb },
                onClose = { viewModel.openSettingsDrawer(false) },
                onToggleSmartDownloads = { viewModel.toggleSmartDownloads(it) },
                onToggleWifiOnly = { viewModel.toggleWifiOnly(it) },
                onToggleHighQuality = { viewModel.toggleHighQuality(it) },
                onToggleAutoPlayNext = { viewModel.toggleAutoPlayNext(it) },
                onToggleAutoPlayPreviews = { viewModel.toggleAutoPlayPreviews(it) },
                onToggleSpatialAudio = { viewModel.toggleSpatialAudio(it) },
                onSetCellularData = { viewModel.setCellularDataOption(it) },
                onRunDiagnosticTest = { viewModel.runDiagnosticTest() },
                onClearAllDownloads = { viewModel.clearAllDownloads() },
                onSwitchProfile = { viewModel.openProfilePicker(true) },
                onOpenTvPair = { viewModel.openTvPairScreen(true) },
                currentEmail = currentUserEmail,
                userSubscription = userSubscription,
                onOpenAuth = { viewModel.openAuthScreen(true) },
                onOpenSubscription = { viewModel.openSubscriptionSheet(true) },
                onEditProfile = { viewModel.openEditProfile(activeProfile) },
                onOpenSmartDownloads = { viewModel.openDownloadsScreen(true, smartSettings = true) },
                onSignOut = { viewModel.signOutUser() },
                onResetPassword = { viewModel.requestPasswordReset() },
                hasActiveDownloads = downloadTasks.values.any { it.profileId == activeProfile.id }
            )
        }

        // Subscription & Membership Sheet (Lipwa Link Payhero)
        AnimatedVisibility(
            visible = showSubscriptionSheet,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            SubscriptionSheet(
                currentSubscription = userSubscription,
                userId = viewModel.getCurrentUserId(),
                onPlanSelected = { newSub ->
                    viewModel.updateSubscription(newSub)
                },
                onClose = { viewModel.openSubscriptionSheet(false) }
            )
        }

        // Auth Screen Overlay (Sign In & Sign Up)
        AnimatedVisibility(
            visible = showAuthScreen,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            AuthScreen(
                currentEmail = currentUserEmail,
                onSignIn = { email, pass, onError ->
                    viewModel.signInUser(email, pass, onSuccess = {}, onError = onError)
                },
                onSignUp = { email, pass, onError ->
                    viewModel.signUpUser(email, pass, onSuccess = {}, onError = onError)
                },
                onSignOut = { viewModel.signOutUser() },
                onClose = { viewModel.openAuthScreen(false) },
                onOpenTvPair = {
                    if (userSubscription.isGuest) {
                        viewModel.showToast("Please sign in or create an account to pair with Android TV")
                    } else {
                        viewModel.openAuthScreen(false)
                        viewModel.openTvPairScreen(true)
                    }
                }
            )
        }

        // TV Pair / QR Scanner Screen Overlay
        AnimatedVisibility(
            visible = showTvPairScreen,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            TvPairScreen(
                activeProfile = activeProfile,
                profiles = profiles,
                pairedDevices = pairedTvSessions,
                isSignedOut = currentUserEmail == null,
                hasTvAccess = userSubscription.isTvAllowed,
                onOpenAuth = {
                    viewModel.openTvPairScreen(false)
                    viewModel.openAuthScreen(true)
                },
                onOpenSubscription = {
                    viewModel.openTvPairScreen(false)
                    viewModel.openSubscriptionSheet(true)
                },
                onBack = { viewModel.openTvPairScreen(false) },
                onPairTvCode = { code, onSuccess, onError ->
                    viewModel.pairTvWithCode(code, onSuccess, onError)
                },
                onUnpairDevice = { sessionCode ->
                    viewModel.unpairTv(sessionCode)
                },
                onSendRemoteCommand = { code, cmd ->
                    viewModel.sendTvRemoteCommand(code, cmd)
                }
            )
        }

        // 2026 TV Cast Device Picker Dialog
        if (showCastDialog) {
            CastDialog(
                devices = viewModel.availableCastDevices,
                connectedDevice = connectedCastDevice,
                onConnect = { viewModel.connectCastDevice(it) },
                onDisconnect = { viewModel.disconnectCastDevice() },
                onDismiss = { viewModel.openCastDialog(false) }
            )
        }

        // Elegant full screen TMDB API loader
        if (isLoadingCatalog && hasCompletedSplash && !isPlayerVisible && selectedMedia == null &&
            !showSettingsDrawer && !showDownloadsScreen && !showAuthScreen && !showProfilePicker && !showEditProfileScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NetflixBlack),
                contentAlignment = Alignment.Center
            ) {
                NetflixSpinner(
                    size = 60.dp
                )
            }
        }

        // Splash screen overlay
        if (!hasCompletedSplash) {
            com.example.ui.screens.SplashScreen(
                isWarmupFinished = isWarmupFinished,
                onSplashComplete = { viewModel.completeSplash() }
            )
        }

        // Trailer End → Sign In / Sign Up Prompt (Guest)
        val showTrailerEndPrompt by viewModel.showTrailerEndPrompt.collectAsStateWithLifecycle()
        if (showTrailerEndPrompt) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.dismissTrailerEndPrompt() },
                containerColor = Color(0xFF1A1A1A),
                titleContentColor = Color.White,
                textContentColor = Color.White,
                title = { androidx.compose.material3.Text("Trailer Ended", color = Color.White) },
                text = {
                    androidx.compose.material3.Text(
                        "Sign in and subscribe to watch full movies, download content, and stream on TV.",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                },
                confirmButton = {
                    androidx.compose.material3.Button(
                        onClick = {
                            viewModel.dismissTrailerEndPrompt()
                            viewModel.openAuthScreen(true)
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = com.example.ui.theme.NetflixRed
                        )
                    ) {
                        androidx.compose.material3.Text("Sign In / Sign Up", color = Color.White)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.dismissTrailerEndPrompt() }
                    ) {
                        androidx.compose.material3.Text("Continue Browsing", color = Color.Gray)
                    }
                }
            )
        }
    }
}

@Composable
private fun PlayerOverlay(viewModel: NetflixViewModel, autoPlayNext: Boolean) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val highQuality by viewModel.isHighQualityEnabled.collectAsStateWithLifecycle()
    val spatialAudio by viewModel.isSpatialAudioEnabled.collectAsStateWithLifecycle()
    val cellularData by viewModel.cellularDataOption.collectAsStateWithLifecycle()
    val network by viewModel.playbackNetworkState.collectAsStateWithLifecycle()
    val subscription by viewModel.userSubscription.collectAsStateWithLifecycle()
    val cellularMode = com.example.data.CellularDataMode.fromLabel(cellularData)
    val offline = playerState.resolvedUrl?.let { it.startsWith("/") || it.startsWith("file:") } == true
    val safeCatalog by viewModel.displayCatalogMedia.collectAsStateWithLifecycle()
    val recommendations = remember(playerState.media, safeCatalog) {
        playerState.media?.let { com.example.ui.viewmodel.postPlayCandidates(it, safeCatalog) }.orEmpty()
    }
    // Full Screen Video Player Overlay
    AnimatedVisibility(
        visible = playerState.media != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        VideoPlayerScreen(
            onPlaybackFailed = viewModel::evictFailedPlayback,
            playerState = playerState,
            modifier = Modifier.fillMaxSize(),
            onClose = { viewModel.closePlayer() },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onSeek = { viewModel.seekTo(it) },
            onSkipForward10 = { viewModel.skipForward10() },
            onSkipBackward10 = { viewModel.skipBackward10() },
            onSkipIntro = { viewModel.skipIntro() },
            onSetSpeed = { viewModel.setPlaybackSpeed(it) },
            onSetAudio = { viewModel.setAudioTrack(it) },
            onSetSubtitle = { viewModel.setSubtitleTrack(it) },
            onToggleLock = { viewModel.toggleLock() },
            onToggleControls = { viewModel.toggleControlsVisibility() },
            onShowAudioSubtitles = { viewModel.showAudioSubtitleDialog(it) },
            onShowEpisodesDrawer = { viewModel.showEpisodeDrawer(it) },
            onPlayNextEpisode = { viewModel.playNextEpisode() },
            autoPlayNext = autoPlayNext,
            spatialAudioEnabled = spatialAudio && subscription.isActive && subscription.planId == "plan_premium",
            maxVideoHeight = if (offline) Int.MAX_VALUE else com.example.data.MobilePlaybackPolicy.maxVideoHeight(
                highQuality, cellularMode, network.wifiOrEthernet, subscription.planId),
            streamingAllowed = offline || com.example.data.MobilePlaybackPolicy.permitsStreaming(cellularMode, network.wifiOrEthernet),
            onContentEnded = viewModel::onContentPlaybackEnded,
            onReplay = viewModel::replayCurrent,
            onRetryNext = { viewModel.prepareNextEpisode(retry = true) },
            recommendations = recommendations,
            onRecommendationClick = { viewModel.playMedia(it) },
            onSetIntroWindow = { viewModel.updateIntroWindow(it) },
            onPersistProgress = { viewModel.savePlayerProgress() },
            onSelectEpisode = { ep ->
                playerState.media?.let { media -> viewModel.playMedia(media, ep) }
            },
            onOpenCast = { viewModel.openCastDialog(true) },
            onSetRating = { rating ->
                playerState.media?.let { media -> viewModel.setRating(media.id, rating) }
            },
            onUpdateProgress = { current, duration -> viewModel.updatePlayerProgress(current, duration) },
            onTrailerEnded = { viewModel.onTrailerPlaybackEnded() }
        )
    }
}
