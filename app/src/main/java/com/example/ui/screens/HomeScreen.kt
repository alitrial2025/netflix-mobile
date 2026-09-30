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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ReminderEntity
import com.example.data.local.WatchProgressEntity
import com.example.data.model.MediaItem
import com.example.data.model.MediaSection
import com.example.data.model.MediaType
import com.example.data.model.UserProfile
import com.example.data.model.UserSubscription
import com.example.ui.components.ContinueWatchingSectionRow
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
    userSubscription: UserSubscription = UserSubscription(),
    reminders: List<ReminderEntity> = emptyList(),
    onToggleReminder: ((MediaItem) -> Unit)? = null,
    isLoadingCatalog: Boolean = false,
    onRetryCatalog: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onContinueWatchingOptionsClick: (MediaItem, WatchProgressEntity) -> Unit = { _, _ -> },
    onAmbientColorChange: (Color) -> Unit = {},
    listState: LazyListState = rememberLazyListState()
) {
    // Determine Hero and Catalog Sections based on category reactively
    val heroMedia = remember(catalogMedia, categoryFilter, selectedGenre) {
        if (catalogMedia.isEmpty()) null
        else {
            when (categoryFilter) {
                CategoryFilter.MOVIES -> {
                    val movList = if (selectedGenre != null) {
                        catalogMedia.filter { it.type == MediaType.MOVIE && it.matchesGenre(selectedGenre) && !it.isComingSoon }
                    } else catalogMedia.filter { it.type == MediaType.MOVIE && !it.isComingSoon }
                    movList.firstOrNull { it.backdropUrl?.isNotBlank() == true }
                        ?: movList.firstOrNull()
                        ?: catalogMedia.firstOrNull { it.type == MediaType.MOVIE }
                        ?: catalogMedia.firstOrNull()
                }
                CategoryFilter.TV_SHOWS -> {
                    val tvList = if (selectedGenre != null) {
                        catalogMedia.filter { it.type == MediaType.TV_SHOW && it.matchesGenre(selectedGenre) && !it.isComingSoon }
                    } else catalogMedia.filter { it.type == MediaType.TV_SHOW && !it.isComingSoon }
                    tvList.firstOrNull { it.backdropUrl?.isNotBlank() == true }
                        ?: tvList.firstOrNull()
                        ?: catalogMedia.firstOrNull { it.type == MediaType.TV_SHOW }
                        ?: catalogMedia.firstOrNull()
                }
                CategoryFilter.CATEGORIES -> {
                    if (selectedGenre != null) {
                        val matching = catalogMedia.filter { it.matchesGenre(selectedGenre) }
                        matching.firstOrNull { it.backdropUrl?.isNotBlank() == true }
                            ?: matching.firstOrNull()
                            ?: catalogMedia.firstOrNull()
                    } else catalogMedia.firstOrNull()
                }
                CategoryFilter.ALL, CategoryFilter.GAMES -> {
                    com.example.ui.components.featuredTrendingMedia(catalogMedia)
                        ?: catalogMedia.firstOrNull { it.backdropUrl?.isNotBlank() == true }
                        ?: catalogMedia.firstOrNull()
                }
            }
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
                if (isLoadingCatalog) NetflixSpinner(size = 50.dp)
                else {
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
    var extractedTopColor by remember(heroMedia.id) { mutableStateOf<Color?>(null) }
    var extractedBottomColor by remember(heroMedia.id) { mutableStateOf<Color?>(null) }

    val defaultTop = remember(heroMedia.id) { Color(0xFF3D3E32) }
    val defaultBottom = remember(heroMedia.id) { Color(0xFF20211A) }

    val currentTopColor = extractedTopColor ?: defaultTop
    val currentBottomColor = extractedBottomColor ?: defaultBottom

    // Ambient color propagation for bottom navigation bar blend (called once on settle, not per-frame)
    LaunchedEffect(heroMedia.id, extractedBottomColor) {
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

    // Cache categorized rows for performance and consistency (Offloaded to Dispatchers.Default to prevent UI thread lag)
    val uiSections by produceState(
        initialValue = emptyList<MediaSection>(),
        key1 = catalogMedia,
        key2 = categoryFilter,
        key3 = selectedGenre
    ) {
        value = withContext(Dispatchers.Default) {
            val rawSections = mutableListOf<Triple<String, String, List<MediaItem>>>()
            val movies = catalogMedia.filter { it.type == MediaType.MOVIE && !it.isComingSoon }
            val tvShows = catalogMedia.filter { it.type == MediaType.TV_SHOW && !it.isComingSoon }
            val top10 = catalogMedia.filter { it.top10Rank != null }.sortedBy { it.top10Rank }
            val trending = catalogMedia.filter { it.isTrending && !it.isComingSoon }
            val originals = catalogMedia.filter { it.isOriginal && !it.isComingSoon }
            val comingSoon = catalogMedia.filter { it.isComingSoon }

            val usedMediaIds = mutableSetOf<String>()
            heroMedia?.let { usedMediaIds.add(it.id) }

            fun filterFresh(list: List<MediaItem>, minCount: Int = 4): List<MediaItem> {
                val fresh = list.filter { !usedMediaIds.contains(it.id) }
                val result = if (fresh.size >= minCount) fresh else list.distinctBy { it.id }
                result.take(10).forEach { usedMediaIds.add(it.id) }
                return result
            }

            when (categoryFilter) {
                CategoryFilter.ALL, CategoryFilter.GAMES -> {
                    if (top10.isNotEmpty()) {
                        rawSections.add(Triple("section_top10", "Top 10 Today in Your Country", top10))
                        top10.take(5).forEach { usedMediaIds.add(it.id) }
                    }
                    if (trending.isNotEmpty()) rawSections.add(Triple("section_trending", "Trending Now", filterFresh(trending)))
                    if (comingSoon.isNotEmpty()) rawSections.add(Triple("section_coming_soon", "Worth the Wait / Coming Soon", comingSoon))
                    if (originals.isNotEmpty()) rawSections.add(Triple("section_originals", "Only on Netflix", filterFresh(originals)))
                    
                    val actionSciFi = catalogMedia.filter { it.matchesGenre("Action") || it.matchesGenre("Sci-Fi") }
                    if (actionSciFi.isNotEmpty()) rawSections.add(Triple("section_action_scifi", "Action & Sci-Fi Thrillers", filterFresh(actionSciFi)))
                    
                    val crimeSuspense = catalogMedia.filter { it.matchesGenre("Crime Thrillers") || it.matchesGenre("Crime") }
                    if (crimeSuspense.isNotEmpty()) rawSections.add(Triple("section_crime", "Crime TV Shows & Mystery Thrillers", filterFresh(crimeSuspense)))

                    val drama = catalogMedia.filter { it.matchesGenre("Drama") }
                    if (drama.isNotEmpty()) rawSections.add(Triple("section_drama", "Critically Acclaimed Dramas", filterFresh(drama)))

                    val comedy = catalogMedia.filter { it.matchesGenre("Comedy") }
                    if (comedy.isNotEmpty()) rawSections.add(Triple("section_comedy", "Comedies & Feel-Good", filterFresh(comedy)))

                    val newReleases = catalogMedia.sortedByDescending { it.releaseYear }
                    if (newReleases.isNotEmpty()) rawSections.add(Triple("section_new_releases", "New Releases & Fresh Arrivals", filterFresh(newReleases)))

                    val anime = catalogMedia.filter { it.matchesGenre("Anime") || it.matchesGenre("Animation") }
                    if (anime.isNotEmpty()) rawSections.add(Triple("section_anime", "Anime & Animation Hits", filterFresh(anime)))

                    val korean = catalogMedia.filter { it.matchesGenre("K-Dramas") }
                    if (korean.isNotEmpty()) rawSections.add(Triple("section_korean", "Korean Dramas & Global Sensations", filterFresh(korean)))

                    val award = catalogMedia.filter { it.matchPercentage > 85 }
                    if (award.isNotEmpty()) rawSections.add(Triple("section_award_winning", "Award-Winning & Top Rated", filterFresh(award)))

                    val doc = catalogMedia.filter { it.matchesGenre("Documentary") }
                    if (doc.isNotEmpty()) rawSections.add(Triple("section_documentary", "Gripping Documentaries", filterFresh(doc)))

                    if (tvShows.isNotEmpty()) rawSections.add(Triple("section_binge", "Binge-Worthy TV Series", filterFresh(tvShows)))
                    if (movies.isNotEmpty()) rawSections.add(Triple("section_blockbusters", "Blockbuster Movies", filterFresh(movies)))
                }
                CategoryFilter.TV_SHOWS -> {
                    val tvFiltered = if (selectedGenre != null) {
                        tvShows.filter { it.matchesGenre(selectedGenre) }
                    } else tvShows

                    val tvTop10 = tvFiltered.filter { it.top10Rank != null }
                    if (tvTop10.isNotEmpty()) {
                        rawSections.add(Triple("section_tv_top10", "Top 10 TV Shows Today", tvTop10))
                        tvTop10.take(4).forEach { usedMediaIds.add(it.id) }
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
                    if (tvOriginals.isNotEmpty()) rawSections.add(Triple("section_tv_originals", "Netflix Original Series", filterFresh(tvOriginals)))

                    val tvNew = tvFiltered.sortedByDescending { it.releaseYear }
                    if (tvNew.isNotEmpty()) rawSections.add(Triple("section_tv_new", "New TV Shows", filterFresh(tvNew)))
                }
                CategoryFilter.MOVIES -> {
                    val movFiltered = if (selectedGenre != null) {
                        movies.filter { it.matchesGenre(selectedGenre) }
                    } else movies

                    val movTop10 = movFiltered.filter { it.top10Rank != null }
                    if (movTop10.isNotEmpty()) {
                        rawSections.add(Triple("section_movies_top10", "Top 10 Movies Today", movTop10))
                        movTop10.take(4).forEach { usedMediaIds.add(it.id) }
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
                    if (movOriginals.isNotEmpty()) rawSections.add(Triple("section_movies_originals", "Netflix Original Movies", movOriginals))

                    val movNew = movFiltered.sortedByDescending { it.releaseYear }
                    if (movNew.isNotEmpty()) rawSections.add(Triple("section_movies_new", "New Releases", movNew))
                }
                CategoryFilter.CATEGORIES -> {
                    val genreFilter = selectedGenre ?: "All"
                    val filtered = if (selectedGenre == null) catalogMedia else {
                        val matches = catalogMedia.filter { it.matchesGenre(selectedGenre) }
                        if (matches.isNotEmpty()) matches else catalogMedia
                    }

                    val catTop10 = filtered.filter { it.top10Rank != null }.sortedBy { it.top10Rank }
                    val catTrending = filtered.filter { it.isTrending }
                    val catTvShows = filtered.filter { it.type == MediaType.TV_SHOW }
                    val catMovies = filtered.filter { it.type == MediaType.MOVIE }
                    val catOriginals = filtered.filter { it.isOriginal }
                    val catNew = filtered.sortedByDescending { it.releaseYear }
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
                        rawSections.add(Triple("section_category_originals", "Only on Netflix • $genreFilter", catOriginals))
                    }
                    if (catAcclaimed.isNotEmpty()) {
                        rawSections.add(Triple("section_cat_acclaimed", "Critically Acclaimed $genreFilter", catAcclaimed))
                    }
                    if (catNew.isNotEmpty()) {
                        rawSections.add(Triple("section_category_new", "New Releases in $genreFilter", catNew))
                    }
                    if (filtered.size < 6 && trending.isNotEmpty()) {
                        rawSections.add(Triple("section_cat_popular_more", "Popular on Netflix", trending))
                    }
                }
            }
            
            rawSections.filter { it.third.isNotEmpty() }.map { (key, title, items) ->
                val isTop10 = key.contains("top10")
                val isOriginals = key.contains("originals")
                MediaSection(
                    id = key,
                    title = title,
                    items = items,
                    isTop10 = isTop10,
                    cardWidth = if (isOriginals) 210.dp else 115.dp,
                    cardHeight = if (isOriginals) 330.dp else 165.dp
                )
            }
        }
    }

    val maxScrollOffsetPx = remember(density) { with(density) { 134.dp.toPx() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .drawBehind {
                val scrollY = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else gradientEndPx
                val currentStartY = -scrollY
                val currentEndY = gradientEndPx - scrollY

                val gradient = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to currentTopColor,
                        0.20f to currentTopColor.copy(alpha = 0.88f),
                        0.38f to currentBottomColor.copy(alpha = 0.72f),
                        0.54f to currentBottomColor.copy(alpha = 0.50f),
                        0.68f to currentBottomColor.copy(alpha = 0.30f),
                        0.80f to currentBottomColor.copy(alpha = 0.15f),
                        0.90f to currentBottomColor.copy(alpha = 0.05f),
                        0.97f to currentBottomColor.copy(alpha = 0.01f),
                        1.00f to Color.Black
                    ),
                    startY = currentStartY,
                    endY = currentEndY
                )
                drawRect(brush = gradient)
                // The reference header shades the left edge while retaining the
                // poster's ambient color behind the buttons on the right.
                drawRect(brush = Brush.horizontalGradient(
                    listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                ))
            }
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
            item(key = "hero") {
            // Top Spacing matching status bar + NetflixTopBar height with breathing room
            Spacer(modifier = Modifier.statusBarsPadding())
            Spacer(modifier = Modifier.height(136.dp))

            // Hero Banner with poster color extraction and adaptive phone height
            val onHeroPlay = remember(heroMedia, onPlayClick) { { onPlayClick(heroMedia) } }
            val onHeroToggle = remember(heroMedia, onWatchlistToggle) { { onWatchlistToggle(heroMedia) } }
            val onHeroInfo = remember(heroMedia, onMediaClick) { { onMediaClick(heroMedia) } }
            val isInWatchlist = isWatchlistContains(heroMedia.id)
            
            val parallaxOffset = remember {
                derivedStateOf {
                    (listState.firstVisibleItemScrollOffset.toFloat() * 0.35f).coerceAtMost(maxScrollOffsetPx * 0.35f)
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
            // Continue Watching Row (only if present)
            if (continueWatchingList.isNotEmpty()) {
                item(key = "continue_watching") {
                ContinueWatchingSectionRow(
                    title = "Continue Watching for ${activeProfile.name}",
                    items = continueWatchingList,
                    onPlayClick = onPlayClick,
                    onInfoClick = onMediaClick,
                    onOptionsClick = onContinueWatchingOptionsClick
                )
                Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Catalog Section Rows based on selected category filter
            items(uiSections, key = { it.id }) { section ->
                val rowState = rememberLazyListState()
                MediaSectionRow(
                    section = section,
                    lazyListState = rowState,
                    reminders = reminders,
                    onToggleReminder = onToggleReminder,
                    isMediaLocked = { media -> userSubscription.isMediaLocked(media.id, media.title) },
                    onMediaClick = onMediaClick,
                    modifier = Modifier
                )
            }
        }
    }
}
