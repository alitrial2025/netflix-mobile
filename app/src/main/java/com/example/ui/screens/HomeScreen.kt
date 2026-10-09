package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ReminderEntity
import com.example.data.local.WatchProgressEntity
import com.example.data.model.MediaItem
import com.example.data.model.UserProfile
import com.example.data.model.UserSubscription
import com.example.ui.components.ContinueWatchingSectionRow
import com.example.ui.components.HomeBackdrop
import com.example.ui.components.homeBackdropOffset
import com.example.ui.components.HeroBanner
import com.example.ui.components.MediaSectionRow
import com.example.ui.components.NetflixSpinner
import com.example.ui.theme.NetflixBlack
import com.example.ui.viewmodel.CategoryFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    activeProfile: UserProfile,
    categoryFilter: CategoryFilter,
    selectedGenre: String?,
    continueWatchingList: List<Pair<MediaItem, WatchProgressEntity>>,
    isWatchlistContains: (String) -> Boolean,
    onMediaClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onWatchlistToggle: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    catalogMedia: List<MediaItem> = emptyList(),
    personalizedMedia: List<MediaItem> = emptyList(),
    watchlistIds: Set<String>? = null,
    userSubscription: UserSubscription = UserSubscription(),
    reminders: List<ReminderEntity> = emptyList(),
    onToggleReminder: ((MediaItem) -> Unit)? = null,
    isLoadingCatalog: Boolean = false,
    onRetryCatalog: () -> Unit = {},
    onClearFilters: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onContinueWatchingOptionsClick: (MediaItem, WatchProgressEntity) -> Unit = { _, _ -> },
    onAmbientColorChange: (Color) -> Unit = {},
    listState: LazyListState = rememberLazyListState()
) {
    val homeCatalog by key(categoryFilter, selectedGenre, activeProfile) {
        produceState<HomeCatalog?>(if (catalogMedia.isEmpty()) HomeCatalog(null, emptyList()) else null, catalogMedia, personalizedMedia) {
            value = if (catalogMedia.isEmpty()) HomeCatalog(null, emptyList()) else withContext(Dispatchers.Default) {
                buildHomeCatalog(catalogMedia, personalizedMedia, categoryFilter, selectedGenre, activeProfile)
            }
        }
    }
    val heroMedia = homeCatalog?.hero
    val uiSections = homeCatalog?.sections.orEmpty()

    val visibleContinueWatching = remember(continueWatchingList, categoryFilter, selectedGenre, activeProfile) {
        continueWatchingList.filter { (media, _) ->
            matchesHomeFilter(media, categoryFilter, selectedGenre, activeProfile)
        }
    }
    if (heroMedia == null && uiSections.isEmpty() && visibleContinueWatching.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(NetflixBlack),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                if (homeCatalog == null || (isLoadingCatalog && catalogMedia.isEmpty())) NetflixSpinner(size = 50.dp)
                else if (catalogMedia.isNotEmpty()) {
                    androidx.compose.material3.Text("No titles in this category", color = Color.White, fontSize = 22.sp)
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.Text("Try another category to explore more titles.", color = Color.White.copy(alpha = .7f))
                    androidx.compose.material3.TextButton(onClick = onClearFilters) { androidx.compose.material3.Text("Browse all titles") }
                } else {
                    androidx.compose.material3.Text("You’re offline", color = Color.White, fontSize = 22.sp)
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.Text("Your downloads are still available. Reconnect to browse more titles.", color = Color.White.copy(alpha = .7f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    androidx.compose.material3.Button(onClick = onOpenDownloads) { androidx.compose.material3.Text("Open downloads") }
                    androidx.compose.material3.TextButton(onClick = onRetryCatalog) { androidx.compose.material3.Text("Try again") }
                }
            }
        }
        return
    }

    // Extracted dynamic background colors from hero poster
    var extractedTopColor by remember(heroMedia?.id, heroMedia?.type) { mutableStateOf<Color?>(null) }
    var extractedBottomColor by remember(heroMedia?.id, heroMedia?.type) { mutableStateOf<Color?>(null) }

    val defaultTop = remember(heroMedia?.id, heroMedia?.type) { Color(0xFF3D3E32) }
    val defaultBottom = remember(heroMedia?.id, heroMedia?.type) { Color(0xFF20211A) }

    val currentTopColor = extractedTopColor ?: defaultTop
    val currentBottomColor = extractedBottomColor ?: defaultBottom

    // Ambient color propagation for bottom navigation bar blend (called once on settle, not per-frame)
    LaunchedEffect(heroMedia?.id, heroMedia?.type, extractedBottomColor) {
        onAmbientColorChange(currentBottomColor)
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // Dynamically scale hero card with comfortable height across all screen devices
    val heroCardHeight = remember(configuration.screenWidthDp) {
        ((configuration.screenWidthDp - 52) * 1.50f).dp.coerceIn(420.dp, 650.dp)
    }

    val gradientEndPx = remember(heroCardHeight, density) {
        with(density) { (134.dp + heroCardHeight + 580.dp).toPx() }
    }

    // Heights change only on layout, not on each scroll frame. Retain the hero's
    // height after lazy disposal so its gradient continues through the first rows.
    val itemHeights = remember(categoryFilter, selectedGenre) { mutableStateMapOf<String, Int>() }
    // Guard writes: only mutate state when the height actually changed.
    // Without this, every onSizeChanged (fired when a row enters viewport)
    // writes the same value → triggers derivedStateOf → recomposes backdrop → invalidates all visible rows.
    val setItemHeight = remember(itemHeights) { { key: String, height: Int ->
        if (itemHeights[key] != height) itemHeights[key] = height
    } }
    val itemKeys = remember(uiSections, heroMedia != null, visibleContinueWatching.isNotEmpty()) {
        listOf(if (heroMedia != null) "hero" else "header_spacer") + (if (visibleContinueWatching.isNotEmpty()) listOf("continue_watching") else emptyList()) + uiSections.map { it.id }
    }
    val backdropOffset = remember(listState, itemKeys, itemHeights, gradientEndPx, categoryFilter, selectedGenre) {
        derivedStateOf {
            homeBackdropOffset(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset,
                itemKeys, itemHeights, gradientEndPx)
        }
    }
    val isMediaLocked = remember(userSubscription) {
        { media: MediaItem -> userSubscription.isMediaLocked(media.id, media.title) }
    }
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // A separate draw layer: scrolling this shader cannot invalidate the row subtree.
        HomeBackdrop(currentTopColor, currentBottomColor, gradientEndPx, { backdropOffset.value })
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("home_vertical_list"), contentPadding = PaddingValues(bottom = 110.dp)) {
            if (heroMedia == null) item(key = "header_spacer") {
                Column(Modifier.onSizeChanged { setItemHeight("header_spacer", it.height) }) {
                    Spacer(Modifier.statusBarsPadding())
                    Spacer(Modifier.height(136.dp))
                }
            }
            if (heroMedia != null) item(key = "hero", contentType = "hero") {
            Column(Modifier.onSizeChanged { setItemHeight("hero", it.height) }) {
            // Top Spacing matching status bar + NetflixTopBar height with breathing room
            Spacer(modifier = Modifier.statusBarsPadding())
            Spacer(modifier = Modifier.height(136.dp))

            // Hero Banner with poster color extraction and adaptive phone height
            val onHeroPlay = remember(heroMedia, onPlayClick) { { onPlayClick(heroMedia) } }
            val onHeroToggle = remember(heroMedia, onWatchlistToggle) { { onWatchlistToggle(heroMedia) } }
            val onHeroInfo = remember(heroMedia, onMediaClick) { { onMediaClick(heroMedia) } }
            val isInWatchlist = watchlistIds?.contains(heroMedia.id) ?: isWatchlistContains(heroMedia.id)
            
            val parallaxOffset = remember(listState) {
                derivedStateOf {
                    if (listState.firstVisibleItemIndex == 0)
                        (listState.firstVisibleItemScrollOffset * .35f).coerceAtMost(12f)
                    else 12f
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                HeroBanner(
                    media = heroMedia,
                    isInWatchlist = isInWatchlist,
                    onPlayClick = onHeroPlay,
                    onWatchlistToggle = onHeroToggle,
                    onInfoClick = onHeroInfo,
                    cardHeight = heroCardHeight,
                    parallaxOffsetProvider = { parallaxOffset.value },
                    isLocked = userSubscription.isMediaLocked(heroMedia.id, heroMedia.title),
                    userPlanName = userSubscription.planName,
                    onColorsExtracted = { top, bottom ->
                        extractedTopColor = top
                        extractedBottomColor = bottom
                    }
                )
            }
            }
            }
            // Continue Watching Row (only if present)
            if (visibleContinueWatching.isNotEmpty()) {
                item(key = "continue_watching", contentType = "continue_watching") {
                Column(Modifier.onSizeChanged { setItemHeight("continue_watching", it.height) }) {
                ContinueWatchingSectionRow(
                    title = "Continue Watching for ${activeProfile.name}",
                    items = visibleContinueWatching,
                    onPlayClick = onPlayClick,
                    onInfoClick = onMediaClick,
                    onOptionsClick = onContinueWatchingOptionsClick
                )
                Spacer(modifier = Modifier.height(8.dp))
                }
                }
            }

            // Catalog Section Rows based on selected category filter
            items(uiSections, key = { it.id }, contentType = { if (it.isTop10) "ranked_row" else "poster_row" }) { section ->
                val rowState = rememberLazyListState()
                MediaSectionRow(
                    section = section,
                    lazyListState = rowState,
                    reminders = reminders,
                    onToggleReminder = onToggleReminder,
                    isMediaLocked = isMediaLocked,
                    onMediaClick = onMediaClick,
                    modifier = Modifier.onSizeChanged { setItemHeight(section.id, it.height) }
                )
            }
        }
    }
}
