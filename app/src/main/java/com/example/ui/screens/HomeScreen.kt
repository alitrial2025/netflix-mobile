package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import com.example.data.model.GameItem
import com.example.data.model.MediaSection
import com.example.data.model.MediaType
import com.example.data.model.UserProfile
import com.example.data.model.UserSubscription
import com.example.ui.components.ContinueWatchingSectionRow
import com.example.ui.components.HomeBackdrop
import com.example.ui.components.homeBackdropOffset
import com.example.ui.components.CinematicHeroBanner
import com.example.ui.components.HeroBanner
import com.example.ui.components.MediaSectionRow
import com.example.ui.components.HomeGamesRow
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
    onOpenDownloads: () -> Unit = {},
    onContinueWatchingOptionsClick: (MediaItem, WatchProgressEntity) -> Unit = { _, _ -> },
    onAmbientColorChange: (Color) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    games: List<GameItem> = emptyList(),
    onGameClick: (GameItem) -> Unit = {},
    onOpenGames: () -> Unit = {},
    onHeroVisible: suspend (MediaItem) -> Unit = {},
    artworkNetworkAvailable: Boolean = true,
    catalogIsScoped: Boolean = false,
    categoryLoading: Boolean = false,
    categoryMessage: String? = null,
    onRetryCategory: () -> Unit = {},
    onClearCategory: () -> Unit = {}
) {
    val filtering = selectedGenre != null || categoryFilter == CategoryFilter.TV_SHOWS || categoryFilter == CategoryFilter.MOVIES
    val homeCatalog = remember(catalogMedia, categoryFilter, selectedGenre, catalogIsScoped) {
        catalogMedia.filter { media ->
            (categoryFilter != CategoryFilter.TV_SHOWS || media.type == MediaType.TV_SHOW) &&
            (categoryFilter != CategoryFilter.MOVIES || media.type == MediaType.MOVIE) &&
            (selectedGenre == null || catalogIsScoped || media.matchesGenre(selectedGenre))
        }
    }
    val visibleContinueWatching = remember(continueWatchingList, homeCatalog, filtering) {
        if (!filtering) continueWatchingList else {
            val keys = homeCatalog.map { it.type to it.id }.toSet()
            continueWatchingList.filter { (it.first.type to it.first.id) in keys }
        }
    }
    val heroMedia = remember(homeCatalog) {
        val released = homeCatalog.filterNot { it.isComingSoon }
        com.example.ui.components.featuredTrendingMedia(released)
            ?: released.firstOrNull { !it.backdropUrl.isNullOrBlank() }
            ?: released.firstOrNull()
    }
    var previousSelection by remember { mutableStateOf(categoryFilter to selectedGenre) }
    LaunchedEffect(categoryFilter, selectedGenre) {
        val selection = categoryFilter to selectedGenre
        if (previousSelection != selection) {
            previousSelection = selection
            listState.scrollToItem(0)
        }
    }

    if (heroMedia == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(NetflixBlack),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                if (categoryLoading || (isLoadingCatalog && homeCatalog.isEmpty())) NetflixSpinner(size = 50.dp)
                else if (filtering) {
                    androidx.compose.material3.Text("No matching titles", color = Color.White, fontSize = 22.sp,
                        modifier = Modifier.testTag("home_category_empty"))
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.Text(categoryMessage ?: "No titles match this category for the current profile.",
                        color = Color.White.copy(alpha = .7f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (selectedGenre != null) androidx.compose.material3.TextButton(onClick = onRetryCategory) {
                        androidx.compose.material3.Text("Retry")
                    }
                    androidx.compose.material3.Button(onClick = onClearCategory) { androidx.compose.material3.Text("Clear filter") }
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

    LaunchedEffect(heroMedia.id, heroMedia.type, artworkNetworkAvailable) {
        if (artworkNetworkAvailable) onHeroVisible(heroMedia)
    }

    // Extracted dynamic background colors from hero poster
    var extractedTopColor by remember(heroMedia.id) { mutableStateOf<Color?>(null) }
    var extractedBottomColor by remember(heroMedia.id) { mutableStateOf<Color?>(null) }

    val defaultTop = remember(heroMedia.id) { Color(0xFF3D3E32) }
    val defaultBottom = remember(heroMedia.id) { Color(0xFF20211A) }

    val currentTopColor = extractedTopColor ?: defaultTop
    val currentBottomColor = extractedBottomColor ?: defaultBottom
    val showGames = games.isNotEmpty() && userSubscription.isGamesAllowed && categoryFilter == CategoryFilter.ALL && selectedGenre == null

    // Ambient color propagation for bottom navigation bar blend (called once on settle, not per-frame)
    LaunchedEffect(heroMedia.id, extractedBottomColor) {
        onAmbientColorChange(currentBottomColor)
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var viewportHeightDp by remember(configuration.screenHeightDp) {
        mutableStateOf(configuration.screenHeightDp.toFloat())
    }
    var viewportWidthDp by remember(configuration.screenWidthDp) {
        mutableStateOf(configuration.screenWidthDp.toFloat())
    }
    val statusBarHeightDp = WindowInsets.statusBars.getTop(density) / density.density
    // Header + hero occupy 85% of the actual viewport. The first rail's title
    // and the top of its posters peek above the floating navigation, like the
    // reference. Width alone made the card too short on taller phones.
    val heroCardHeight = remember(viewportHeightDp, statusBarHeightDp, configuration.fontScale) {
        val minimumHeight = 320f + (configuration.fontScale - 1f).coerceAtLeast(0f) * 64f
        (viewportHeightDp * .85f - statusBarHeightDp - 116f - 14f)
            .coerceAtLeast(minimumHeight).dp
    }
    // Use window bounds, not device identity: folding, rotation and multi-window
    // can all change presentation without recreating the activity or its list.
    val cinematic = viewportWidthDp >= 600f || viewportWidthDp > viewportHeightDp
    val cinematicHeight = minOf(viewportWidthDp * 9f / 16f, viewportHeightDp * .76f)
        .coerceAtLeast(minOf(280f, viewportHeightDp * .76f)).dp
    val railCardWidth = if (cinematic) (viewportWidthDp / 6.2f).dp.coerceIn(100.dp, 180.dp)
        else ((viewportWidthDp - 48) / 3.35f).dp.coerceAtLeast(92.dp)

    val gradientEndPx = remember(heroCardHeight, density) {
        with(density) { (134.dp + heroCardHeight + 580.dp).toPx() }
    }

    // Cache categorized rows for performance and consistency (Offloaded to Dispatchers.Default to prevent UI thread lag)
    val sectionState = androidx.compose.runtime.key(categoryFilter, selectedGenre) { produceState(emptyList<MediaSection>(), homeCatalog, personalizedMedia,
        categoryFilter, selectedGenre, activeProfile.name, railCardWidth) {
        value = withContext(Dispatchers.Default) {
            val rawSections = mutableListOf<Triple<String, String, List<MediaItem>>>()
            val movies = homeCatalog.filter { it.type == MediaType.MOVIE && !it.isComingSoon }
            val tvShows = homeCatalog.filter { it.type == MediaType.TV_SHOW && !it.isComingSoon }
            val top10 = homeCatalog.filter { it.top10Rank != null }.sortedBy { it.top10Rank }
            val trending = homeCatalog.filter { it.isTrending && !it.isComingSoon }
            val originals = homeCatalog.filter { it.isOriginal && !it.isComingSoon }
            val comingSoon = homeCatalog.filter { it.isComingSoon }
            val podcasts = homeCatalog.filter { media ->
                !media.isComingSoon && media.genres.any { genre ->
                    genre.equals("Podcast", ignoreCase = true) || genre.equals("Podcasts", ignoreCase = true)
                }
            }
            val newReleases = homeCatalog.filter {
                !it.isComingSoon && com.example.discovery.ReleasePolicy.isNew(it.releaseDate)
            }.sortedByDescending { it.releaseDate }

            val usedMediaIds = mutableSetOf<Pair<MediaType, String>>()
            heroMedia?.let { usedMediaIds.add(it.type to it.id) }

            fun filterFresh(list: List<MediaItem>, minCount: Int = 4): List<MediaItem> {
                val fresh = list.filter { !usedMediaIds.contains(it.type to it.id) }
                val result = if (fresh.size >= minCount) fresh else list.distinctBy { it.type to it.id }
                result.take(10).forEach { usedMediaIds.add(it.type to it.id) }
                return result
            }

            when (categoryFilter) {
                CategoryFilter.ALL, CategoryFilter.GAMES -> {
                    // The reference starts with a compact release rail under the
                    // hero. Only actual dated releases can receive that label.
                    if (newReleases.isNotEmpty()) {
                        rawSections.add(Triple("section_new_releases", "New on NetflixPro", filterFresh(newReleases)))
                    }
                    if (podcasts.isNotEmpty()) {
                        rawSections.add(Triple("section_podcasts", "Podcasts: Now on NetflixPro", filterFresh(podcasts)))
                    }
                    val allowed = homeCatalog.map { it.type to it.id }.toSet()
                    val personalized = personalizedMedia.filter { (it.type to it.id) in allowed }
                    if (personalized.isNotEmpty()) {
                        rawSections.add(Triple("section_personalized", "Your next watch", filterFresh(personalized)))
                    }
                    if (top10.isNotEmpty()) {
                        rawSections.add(Triple("section_top10", "Top 10 Today in Your Country", top10))
                        top10.take(5).forEach { usedMediaIds.add(it.type to it.id) }
                    }
                    if (trending.isNotEmpty()) rawSections.add(Triple("section_trending", "Trending Now", filterFresh(trending)))
                    if (comingSoon.isNotEmpty()) rawSections.add(Triple("section_coming_soon", "Worth the Wait / Coming Soon", comingSoon))
                    if (originals.isNotEmpty()) rawSections.add(Triple("section_originals", "Only on NetflixPro", filterFresh(originals)))
                    
                    val actionSciFi = homeCatalog.filter { it.matchesGenre("Action") || it.matchesGenre("Sci-Fi") }
                    if (actionSciFi.isNotEmpty()) rawSections.add(Triple("section_action_scifi", "Action & Sci-Fi Thrillers", filterFresh(actionSciFi)))
                    
                    val crimeSuspense = homeCatalog.filter { it.matchesGenre("Crime Thrillers") || it.matchesGenre("Crime") }
                    if (crimeSuspense.isNotEmpty()) rawSections.add(Triple("section_crime", "Crime TV Shows & Mystery Thrillers", filterFresh(crimeSuspense)))

                    val drama = homeCatalog.filter { it.matchesGenre("Drama") }
                    if (drama.isNotEmpty()) rawSections.add(Triple("section_drama", "Critically Acclaimed Dramas", filterFresh(drama)))

                    val comedy = homeCatalog.filter { it.matchesGenre("Comedy") }
                    if (comedy.isNotEmpty()) rawSections.add(Triple("section_comedy", "Comedies & Feel-Good", filterFresh(comedy)))

                    val anime = homeCatalog.filter { it.matchesGenre("Anime") || it.matchesGenre("Animation") }
                    if (anime.isNotEmpty()) rawSections.add(Triple("section_anime", "Anime & Animation Hits", filterFresh(anime)))

                    val korean = homeCatalog.filter { it.matchesGenre("K-Dramas") }
                    if (korean.isNotEmpty()) rawSections.add(Triple("section_korean", "Korean Dramas & Global Sensations", filterFresh(korean)))

                    val award = homeCatalog.filter { it.matchPercentage > 85 }
                    if (award.isNotEmpty()) rawSections.add(Triple("section_award_winning", "Award-Winning & Top Rated", filterFresh(award)))

                    val doc = homeCatalog.filter { it.matchesGenre("Documentary") }
                    if (doc.isNotEmpty()) rawSections.add(Triple("section_documentary", "Gripping Documentaries", filterFresh(doc)))

                    if (tvShows.isNotEmpty()) rawSections.add(Triple("section_binge", "Binge-Worthy TV Series", filterFresh(tvShows)))
                    if (movies.isNotEmpty()) rawSections.add(Triple("section_blockbusters", "Blockbuster Movies", filterFresh(movies)))
                }
                CategoryFilter.TV_SHOWS -> {
                    val tvFiltered = tvShows

                    val tvTop10 = tvFiltered.filter { it.top10Rank != null }
                    if (tvTop10.isNotEmpty()) {
                        rawSections.add(Triple("section_tv_top10", "Top 10 TV Shows Today", tvTop10))
                        tvTop10.take(4).forEach { usedMediaIds.add(it.type to it.id) }
                    }
                    
                    val tvTitle = if (selectedGenre != null) "Popular $selectedGenre TV Shows" else "Popular TV Shows"
                    rawSections.add(Triple("section_tv_popular", tvTitle, filterFresh(tvFiltered)))
                    
                    val tvDrama = tvFiltered.filter { it.matchesGenre("Drama") }
                    if (tvDrama.isNotEmpty()) rawSections.add(Triple("section_tv_drama", "Binge-Worthy TV Dramas", filterFresh(tvDrama)))

                    val tvCrime = tvFiltered.filter { it.matchesGenre("Crime") }
                    if (tvCrime.isNotEmpty()) rawSections.add(Triple("section_tv_crime", "Crime TV Shows & Thrillers", filterFresh(tvCrime)))

                    val tvSciFi = tvFiltered.filter { it.matchesGenre("Sci-Fi") || it.matchesGenre("Fantasy") }
                    if (tvSciFi.isNotEmpty()) rawSections.add(Triple("section_tv_scifi", "Sci-Fi & Supernatural Series", filterFresh(tvSciFi)))

                    val tvComedy = tvFiltered.filter { it.matchesGenre("Comedy") }
                    if (tvComedy.isNotEmpty()) rawSections.add(Triple("section_tv_comedy", "Sitcoms & Comedies", filterFresh(tvComedy)))

                    val tvOriginals = tvFiltered.filter { it.isOriginal }
                    if (tvOriginals.isNotEmpty()) rawSections.add(Triple("section_tv_originals", "NetflixPro Original Series", filterFresh(tvOriginals)))

                    val tvNew = tvFiltered.filter { com.example.discovery.ReleasePolicy.isNew(it.releaseDate) }.sortedByDescending { it.releaseDate }
                    if (tvNew.isNotEmpty()) rawSections.add(Triple("section_tv_new", "New TV Shows", filterFresh(tvNew)))
                }
                CategoryFilter.MOVIES -> {
                    val movFiltered = movies

                    val movTop10 = movFiltered.filter { it.top10Rank != null }
                    if (movTop10.isNotEmpty()) {
                        rawSections.add(Triple("section_movies_top10", "Top 10 Movies Today", movTop10))
                        movTop10.take(4).forEach { usedMediaIds.add(it.type to it.id) }
                    }
                    
                    val movTitle = if (selectedGenre != null) "Popular $selectedGenre Movies" else "Popular Movies"
                    rawSections.add(Triple("section_movies_popular", movTitle, filterFresh(movFiltered)))

                    val movAction = movFiltered.filter { it.matchesGenre("Action") }
                    if (movAction.isNotEmpty()) rawSections.add(Triple("section_movies_action", "Blockbuster Action & Adventure", filterFresh(movAction)))

                    val movCrime = movFiltered.filter { it.matchesGenre("Crime") }
                    if (movCrime.isNotEmpty()) rawSections.add(Triple("section_movies_crime", "Crime Movies & Mystery Thrillers", filterFresh(movCrime)))

                    val movSciFi = movFiltered.filter { it.matchesGenre("Sci-Fi") }
                    if (movSciFi.isNotEmpty()) rawSections.add(Triple("section_movies_scifi", "Mind-Bending Sci-Fi & Dystopian", filterFresh(movSciFi)))

                    val movDrama = movFiltered.filter { it.matchesGenre("Drama") }
                    if (movDrama.isNotEmpty()) rawSections.add(Triple("section_movies_drama", "Critically Acclaimed Dramas", filterFresh(movDrama)))

                    val movComedy = movFiltered.filter { it.matchesGenre("Comedy") }
                    if (movComedy.isNotEmpty()) rawSections.add(Triple("section_movies_comedy", "Comedies", movComedy))

                    val movOriginals = movFiltered.filter { it.isOriginal }
                    if (movOriginals.isNotEmpty()) rawSections.add(Triple("section_movies_originals", "NetflixPro Original Movies", movOriginals))

                    val movNew = movFiltered.filter { com.example.discovery.ReleasePolicy.isNew(it.releaseDate) }.sortedByDescending { it.releaseDate }
                    if (movNew.isNotEmpty()) rawSections.add(Triple("section_movies_new", "New Releases", movNew))
                }
                CategoryFilter.CATEGORIES -> {
                    val genreFilter = selectedGenre ?: "All"
                    val filtered = homeCatalog

                    val catTop10 = filtered.filter { it.top10Rank != null }.sortedBy { it.top10Rank }
                    val catTrending = filtered.filter { it.isTrending }
                    val catTvShows = filtered.filter { it.type == MediaType.TV_SHOW }
                    val catMovies = filtered.filter { it.type == MediaType.MOVIE }
                    val catOriginals = filtered.filter { it.isOriginal }
                    val catNew = filtered.filter { com.example.discovery.ReleasePolicy.isNew(it.releaseDate) }.sortedByDescending { it.releaseDate }
                    val catAcclaimed = filtered.filter { it.matchPercentage >= 85 }

                    rawSections.add(Triple("section_category_highlights", "$genreFilter Highlights", filtered))
                    
                    if (catTop10.isNotEmpty()) {
                        rawSections.add(Triple("section_cat_top10", "Top 10 in $genreFilter Today", catTop10))
                    }
                    if (catTrending.isNotEmpty()) {
                        rawSections.add(Triple("section_cat_trending", "Trending in $genreFilter", catTrending))
                    }
                    if (catTvShows.isNotEmpty()) {
                        rawSections.add(Triple("section_category_tv", "Binge-Worthy $genreFilter TV Shows", catTvShows))
                    }
                    if (catMovies.isNotEmpty()) {
                        rawSections.add(Triple("section_category_movies", "$genreFilter Movies & Blockbusters", catMovies))
                    }
                    if (catOriginals.isNotEmpty()) {
                        rawSections.add(Triple("section_category_originals", "Only on NetflixPro • $genreFilter", catOriginals))
                    }
                    if (catAcclaimed.isNotEmpty()) {
                        rawSections.add(Triple("section_cat_acclaimed", "Critically Acclaimed $genreFilter", catAcclaimed))
                    }
                    if (catNew.isNotEmpty()) {
                        rawSections.add(Triple("section_category_new", "New Releases in $genreFilter", catNew))
                    }
                }
            }
            
            rawSections.filter { it.third.isNotEmpty() }.map { (key, title, items) ->
                val isTop10 = key.contains("top10")
                MediaSection(
                    id = key,
                    title = title,
                    items = items,
                    isTop10 = isTop10,
                    cardWidth = railCardWidth,
                    cardHeight = railCardWidth * 1.5f
                )
            }
        }
    }

    }

    // Read once per composition: lazy interval counts, keys and game placement
    // must share the same catalog snapshot while background row building completes.
    val uiSections = sectionState.value

    // Heights change only on layout, not on each scroll frame. Retain the hero's
    // height after lazy disposal so its gradient continues through the first rows.
    val itemHeights = remember(categoryFilter, selectedGenre) { mutableStateMapOf<String, Int>() }
    // Guard writes: only mutate state when the height actually changed.
    // Without this, every onSizeChanged (fired when a row enters viewport)
    // writes the same value → triggers derivedStateOf → recomposes backdrop → invalidates all visible rows.
    val setItemHeight = remember(itemHeights) { { key: String, height: Int ->
        if (itemHeights[key] != height) itemHeights[key] = height
    } }
    val itemKeys = remember(uiSections, visibleContinueWatching.isNotEmpty(), showGames, selectedGenre, categoryLoading, categoryMessage) {
        listOf("hero") + (if (selectedGenre != null && (categoryLoading || categoryMessage != null)) listOf("category_status") else emptyList()) + (if (visibleContinueWatching.isNotEmpty()) listOf("continue_watching") else emptyList()) +
            uiSections.flatMapIndexed { index, section ->
                if (showGames && index == 0) listOf(section.id, "mobile_games") else listOf(section.id)
            } + if (showGames && uiSections.isEmpty()) listOf("mobile_games") else emptyList()
    }
    val backdropOffset = remember(itemKeys, gradientEndPx, categoryFilter, selectedGenre) {
        derivedStateOf {
            homeBackdropOffset(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset,
                itemKeys, itemHeights, gradientEndPx)
        }
    }
    val isMediaLocked = remember(userSubscription) {
        { media: MediaItem -> userSubscription.isMediaLocked(media.id, media.title) }
    }
    Box(modifier = modifier.fillMaxSize().background(Color.Black).onSizeChanged {
        val measuredHeight = it.height / density.density
        val measuredWidth = it.width / density.density
        if (measuredHeight > 0f && measuredHeight != viewportHeightDp) viewportHeightDp = measuredHeight
        if (measuredWidth > 0f && measuredWidth != viewportWidthDp) viewportWidthDp = measuredWidth
    }) {
        // A separate draw layer: scrolling this shader cannot invalidate the row subtree.
        HomeBackdrop(if (cinematic) Color.Black else currentTopColor,
            if (cinematic) Color.Black else currentBottomColor, gradientEndPx, { backdropOffset.value })
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("home_vertical_list"), contentPadding = PaddingValues(bottom = com.example.ui.components.navigationContentClearance())) {
            item(key = "hero", contentType = "hero") {
            Column(Modifier.onSizeChanged { setItemHeight("hero", it.height) }) {
            // Top Spacing matching status bar + NetflixTopBar height with breathing room
            if (!cinematic) {
                Spacer(modifier = Modifier.statusBarsPadding())
                Spacer(modifier = Modifier.height(116.dp))
            }

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
                if (cinematic) {
                    CinematicHeroBanner(
                        media = heroMedia,
                        isInWatchlist = isInWatchlist,
                        onPlayClick = onHeroPlay,
                        onWatchlistToggle = onHeroToggle,
                        onInfoClick = onHeroInfo,
                        height = cinematicHeight,
                        artworkNetworkAvailable = artworkNetworkAvailable,
                        isLocked = userSubscription.isMediaLocked(heroMedia.id, heroMedia.title),
                        onColorsExtracted = { top, bottom ->
                            extractedTopColor = top
                            extractedBottomColor = bottom
                        }
                    )
                } else HeroBanner(
                    media = heroMedia,
                    isInWatchlist = isInWatchlist,
                    onPlayClick = onHeroPlay,
                    onWatchlistToggle = onHeroToggle,
                    onInfoClick = onHeroInfo,
                    cardHeight = heroCardHeight,
                    artworkNetworkAvailable = artworkNetworkAvailable,
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
            if (selectedGenre != null && (categoryLoading || categoryMessage != null)) {
                item(key = "category_status", contentType = "category_status") {
                    Column(Modifier.fillMaxWidth().onSizeChanged { setItemHeight("category_status", it.height) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        androidx.compose.material3.Text(categoryMessage ?: "Loading more $selectedGenre titles…",
                            color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
                        if (!categoryLoading && categoryMessage != null) androidx.compose.material3.TextButton(onClick = onRetryCategory) {
                            androidx.compose.material3.Text("Retry")
                        }
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
            uiSections.forEachIndexed { index, section ->
              item(key = section.id, contentType = if (section.isTop10) "ranked_row" else "poster_row") {
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
              if (showGames && index == 0) {
                  item(key = "mobile_games", contentType = "games_row") {
                      HomeGamesRow(games, onGameClick, onOpenGames,
                          Modifier.onSizeChanged { setItemHeight("mobile_games", it.height) })
                  }
              }
            }
            if (showGames && uiSections.isEmpty()) {
                item(key = "mobile_games", contentType = "games_row") {
                    HomeGamesRow(games, onGameClick, onOpenGames,
                        Modifier.onSizeChanged { setItemHeight("mobile_games", it.height) })
                }
            }
        }
    }
}
