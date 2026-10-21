package com.example

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.model.GameItem
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.SavedMoment
import com.example.data.model.UserProfile
import com.example.data.model.UserSubscription
import com.example.ui.components.NetflixBottomNav
import com.example.ui.components.NetflixTopBar
import com.example.ui.screens.ClipDiscoveryOverlay
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MyNetflixScreen
import com.example.ui.theme.NetflixTheme
import com.example.ui.viewmodel.CategoryFilter
import com.example.ui.viewmodel.NavigationTab
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/** Actual production screens with test-only artwork; no account, resolver or player is created. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h895dp-mdpi", sdk = [34], application = Application::class)
class ReferenceMobileScreensTest {
    @get:Rule val rule = createComposeRule()



    private val loadedArtwork = ConcurrentHashMap.newKeySet<String>()
    private val artworkSuccesses = ConcurrentHashMap<String, AtomicInteger>()
    private val profile = UserProfile("reference-profile", "Mike", avatarColorHex = 0xFFE50914L)
    private val membership = UserSubscription(planId = "plan_premium", status = "ACTIVE", expiresAt = UserSubscription.clock() + 30L * 86_400_000L)

    @Before fun localArtworkLoaderBelongsToTheCurrentApplication() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        Coil.setImageLoader(ImageLoader.Builder(context).eventListener(object : EventListener {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                loadedArtwork.add(request.data.toString())
                artworkSuccesses.computeIfAbsent(request.data.toString()) { AtomicInteger() }.incrementAndGet()
            }
        }).build())
    }

    @After fun releaseTheApplicationScopedImageLoader() { Coil.reset() }

    private fun fixture(name: String): String {
        val file = File("build/reports/reference-fixtures", name)
        file.parentFile!!.mkdirs()
        javaClass.getResourceAsStream("/artwork/$name")!!.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return file.toURI().toString()
    }

    private val show by lazy {
        MediaItem("reference-show", "Squid Game", MediaType.TV_SHOW,
            "A new game changes everything. Discover the people behind each choice.", "", 96, "16+", 2026, "3 Seasons",
            genres = listOf("Suspenseful", "Thriller", "Korean"), cast = emptyList(), director = "",
            posterUrl = fixture("squid-game.jpg"), backdropUrl = fixture("squid-game-backdrop.jpg"),
            logoUrl = fixture("squid-game-logo.png"), heroPosterUrl = fixture("squid-game-textless.jpg"), isOriginal = true, isTrending = true)
    }
    private val movie by lazy {
        show.copy(id = "reference-film", title = "Umthetho", type = MediaType.MOVIE, isTrending = false,
            posterUrl = fixture("umthetho.jpg"), backdropUrl = fixture("umthetho.jpg"),
            logoUrl = fixture("umthetho-logo.png"), heroPosterUrl = null)
    }
    private val games by lazy {
        listOf(GameItem("reference-game", "Squid Game: Unleashed", "Netflix Games", "Action", "18+", "420 MB",
            "Local catalog fixture", iconUrl = show.posterUrl))
    }
    private val episodeMoment by lazy {
        SavedMoment("episode-moment", show, positionSeconds = 125, durationSeconds = 15, createdAt = 1L,
            episodeId = "episode-s2e3", episodeNumber = 3, seasonNumber = 2, episodeTitle = "001")
    }
    private val filmMoment by lazy {
        SavedMoment("film-moment", movie, positionSeconds = 387, durationSeconds = 120, createdAt = 2L)
    }

    private fun artworkReady(vararg models: String?, minimumRequestsPerModel: Int = 1) {
        rule.waitUntil(10_000) {
            ShadowLooper.idleMainLooper()
            models.filterNotNull().all { it in loadedArtwork && (artworkSuccesses[it]?.get() ?: 0) >= minimumRequestsPerModel }
        }
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, dialog: Boolean = false) {
        val directory = File("build/reports/reference-screenshots").apply { mkdirs() }
        val node = if (dialog) rule.onNode(isDialog()) else rule.onRoot()
        // These are disposable review artifacts, not tracked golden images. Ordinary
        // test tasks otherwise default Roborazzi to None and silently skip captures.
        node.captureRoboImage(File(directory, "$name.png").path,
            RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    @Composable private fun FloatingNavigation(tab: NavigationTab, onSelect: (NavigationTab) -> Unit = {}) {
        Box(Modifier.fillMaxSize()) {
            NetflixBottomNav(tab, profile, onSelect, Modifier.align(Alignment.BottomCenter), isClipsAllowed = true)
        }
    }

    private fun assertFloatingNavigationHasSeparateTargets() {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val bounds = listOf("home", "clips", "search", "my_netflix").map { key ->
            rule.onNodeWithTag("nav_tab_$key").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        bounds.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
        assertTrue("Floating navigation needs side margins", bounds.first().left > root.left && bounds.last().right < root.right)
        assertTrue("Floating navigation must leave bottom breathing room", bounds.first().bottom <= root.bottom - 8f)
        bounds.forEach { assertTrue("Each tab needs a usable tap target", it.width >= 48f && it.height >= 48f) }
    }

    @Test fun platformHomeDesignsRetainPlayListAndNavigationAcrossEveryService() {
        var appearance by mutableStateOf(com.example.data.model.PlatformAppearance.PRIME)
        var played = 0
        var listed = 0
        var navigated: NavigationTab? = null
        rule.setContent { NetflixTheme(platform = appearance) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(profile, CategoryFilter.CATEGORIES, appearance.label, emptyList(), { false }, {},
                    { played++ }, { listed++ }, catalogMedia = listOf(show, movie), catalogIsScoped = true, userSubscription = membership)
                NetflixTopBar(profile, CategoryFilter.CATEGORIES, appearance.label, { _, _ -> }, {}, {}, {})
                NetflixBottomNav(NavigationTab.HOME, profile, { navigated = it }, Modifier.align(Alignment.BottomCenter), isClipsAllowed = true)
            }
        } }
        val styles = com.example.data.model.PlatformAppearance.entries.filter { !com.example.ui.theme.platformDesign(it).originalLayout }
        styles.forEachIndexed { index, style ->
            rule.runOnIdle { appearance = style }
            rule.onNodeWithTag("platform_hero_${style.key}").assertIsDisplayed()
            rule.onNodeWithTag("platform_navigation_${style.key}").assertIsDisplayed()
            artworkReady(show.logoUrl, show.backdropUrl, show.posterUrl)
            assertPlatformChromeGeometry()
            capture("platform-${style.key}-home-412")
            rule.onNodeWithTag("hero_play").performClick()
            rule.onNodeWithTag("hero_my_list").performClick()
            rule.onNodeWithTag("nav_tab_search").performClick()
            rule.runOnIdle { assertEquals(index + 1, played); assertEquals(index + 1, listed); assertEquals(NavigationTab.SEARCH, navigated) }
        }
    }

    private fun assertPlatformChromeGeometry() {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val title = rule.onNodeWithTag("platform_header_title").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val actions = rule.onNodeWithTag("platform_header_actions").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Header groups must have a visible gap", title.right + 6f <= actions.left)
        val tabs = listOf("home", "clips", "search", "my_netflix").map {
            rule.onNodeWithTag("nav_tab_$it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        tabs.forEach { assertTrue("Every tab needs a full touch target", it.width >= 48f && it.height >= 48f) }
        tabs.zipWithNext().forEach { (a, b) -> assertTrue(a.right <= b.left) }
        assertTrue("Dock must leave side margins", tabs.first().left >= root.left + 16f && tabs.last().right <= root.right - 16f)
        assertTrue("Dock must stay bounded on tablets", tabs.last().right - tabs.first().left <= 560f)
        listOf("Home", "Clips", "Search", "My Netflix").forEach { text ->
            rule.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { get ->
                val result = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                get(result)
                assertTrue("$text must fit without truncation: ${result.map { "size=${it.size}, lines=${it.lineCount}, height=${it.didOverflowHeight}, width=${it.didOverflowWidth}, paragraph=${it.multiParagraph.height}" }}", result.all { !it.hasVisualOverflow })
            }
        }
    }

    private fun polishedChromeFits(glassy: Boolean, largeText: Boolean = false) {
        var selected by mutableStateOf(NavigationTab.HOME)
        var downloads = 0
        rule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, if (largeText) 2f else density.fontScale),
                com.example.ui.glass.LocalGlassTheme provides remember { com.example.ui.glass.GlassThemeState(glassy) }) {
                NetflixTheme(platform = com.example.data.model.PlatformAppearance.PRIME) {
                    Box(Modifier.fillMaxSize()) {
                        NetflixTopBar(profile, CategoryFilter.ALL, null, { _, _ -> }, { downloads++ }, {}, {})
                        NetflixBottomNav(selected, profile, { selected = it }, Modifier.align(Alignment.BottomCenter), isClipsAllowed = true)
                    }
                }
            }
        }
        assertPlatformChromeGeometry()
        rule.onNodeWithTag("top_bar_download_button").performClick()
        rule.onNodeWithTag("nav_tab_my_netflix").performClick()
        rule.onNodeWithTag("nav_tab_my_netflix").assertIsSelected()
        rule.runOnIdle { assertEquals(1, downloads); assertEquals(NavigationTab.MY_NETFLIX, selected) }
        capture("platform-polish-${if (largeText) "large-text" else if (glassy) "glass" else "normal"}-${rule.onRoot().fetchSemanticsNode().size.width}")
    }

    @Test @Config(qualifiers = "w360dp-h800dp-mdpi")
    fun platformPolishFitsCompactPhones() = polishedChromeFits(glassy = false)

    @Test @Config(qualifiers = "w840dp-h600dp-mdpi")
    fun platformPolishBoundsTheDockOnTablets() = polishedChromeFits(glassy = true)

    @Test @Config(qualifiers = "w360dp-h800dp-mdpi")
    fun platformPolishKeepsLargeTextLabelsAndControlsVisible() = polishedChromeFits(glassy = true, largeText = true)

    @Test fun persistedPlatformsToggleChangesTheMountedAppWithoutLosingState() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val prefs = context.getSharedPreferences("platform-theme-root-test", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = com.example.data.MobileSettingsStore(prefs)
        var clicks = 0
        rule.setContent { com.example.ui.theme.PlatformPreferenceTheme(prefs) {
            var retained by remember { mutableStateOf(0) }
            val design = com.example.ui.theme.LocalPlatformDesign.current
            androidx.compose.material3.Button(onClick = { retained++; clicks = retained },
                modifier = Modifier.testTag("retained_${design.appearance.key}")) { androidx.compose.material3.Text("State $retained") }
        } }
        rule.onNodeWithTag("retained_original").performClick()
        rule.runOnIdle { store.setPlatformsEnabled(true, "Prime Video") }
        rule.onNodeWithTag("retained_prime").performClick()
        rule.runOnIdle { store.selectPlatformCategory("Disney+") }
        rule.onNodeWithTag("retained_disney").performClick()
        rule.runOnIdle { store.setPlatformsEnabled(false) }
        rule.onNodeWithTag("retained_original").assertIsDisplayed()
        rule.runOnIdle { assertEquals(3, clicks) }
        rule.onNodeWithText("State 3").assertIsDisplayed()
    }

    @Test fun platformSecondaryScreensAndPlayerControlsKeepTheirExistingActions() {
        var appearance by mutableStateOf(com.example.data.model.PlatformAppearance.PRIME)
        var screen by mutableStateOf(0)
        var played = 0
        var downloaded = 0
        var seeks = 0
        var toggles = 0
        rule.setContent { NetflixTheme(platform = appearance) {
            when (screen) {
                0 -> com.example.ui.screens.SearchScreen("Squid", null, listOf(show, movie), userSubscription = membership,
                    onQueryChange = {}, onGenreFilterSelect = {}, onMediaClick = {}, onPlayClick = {})
                1 -> com.example.ui.screens.DetailScreen(movie, false, userSubscription = membership, downloadProgressMap = emptyMap(),
                    onClose = {}, onPlayClick = { _, _ -> played++ }, onPlayTrailerClick = { _, _ -> },
                    onWatchlistToggle = {}, onDownloadClick = { _, _ -> downloaded++ }, onRatingSelect = {}, onSimilarMediaClick = {})
                2 -> com.example.ui.screens.DownloadsScreen(downloads = emptyList(), smartDownloadsEnabled = false,
                    connectedCastDevice = null, onClose = {}, onPlayMedia = { _, _ -> }, onDeleteDownload = {},
                    onClearAllDownloads = {}, onToggleSmartDownloads = {}, onSetUpDownloadsForYou = {},
                    onOpenSearch = {}, onOpenCast = {}, onOpenMediaDetail = {}, onShowToast = {}, userSubscription = membership,
                    catalogMedia = listOf(show, movie), activeProfile = profile)
                3 -> com.example.ui.screens.NetflixSettingsScreen(profile, true, true, true, true, true, true,
                    "Automatic (Balanced)", false, null, 50, onClose = {}, onToggleSmartDownloads = {},
                    onToggleWifiOnly = {}, onToggleHighQuality = {}, onToggleAutoPlayNext = {}, onToggleAutoPlayPreviews = {},
                    onToggleSpatialAudio = {}, onSetCellularData = {}, onRunDiagnosticTest = {}, onClearAllDownloads = {},
                    onSwitchProfile = {}, userSubscription = membership, platformsEnabled = true)
                4 -> PersonalLibrary(watchlist = listOf(show, movie), liked = listOf(show))
                5 -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    com.example.ui.components.PlatformPlaybackControls(false, { seeks-- }, { toggles++ }, { seeks++ })
                }
            }
        } }
        val styles = com.example.data.model.PlatformAppearance.entries.filter { !com.example.ui.theme.platformDesign(it).originalLayout }
        styles.forEachIndexed { index, style ->
            rule.runOnIdle { appearance = style }
            for (destination in 0..5) {
                rule.runOnIdle { screen = destination }
                rule.waitForIdle()
                when (destination) {
                    0 -> rule.onNodeWithTag("search_results_grid").assertIsDisplayed()
                    1 -> {
                        rule.onNodeWithTag("detail_play_button").performScrollTo().performClick()
                        rule.onNodeWithTag("detail_platform_download").performClick()
                    }
                    2 -> rule.onNodeWithTag("downloads_list").assertIsDisplayed()
                    3 -> rule.onNodeWithTag("settings_toggle_Platforms").performScrollTo().assertIsOn()
                    4 -> rule.onNodeWithTag("my_netflix_screen").assertIsDisplayed()
                    5 -> {
                        rule.onNodeWithTag("player_rewind_10").performClick()
                        rule.onNodeWithTag("player_play_pause_button").performClick()
                        rule.onNodeWithTag("player_forward_10").performClick()
                    }
                }
                capture("platform-${style.key}-screen-$destination-412")
            }
            rule.runOnIdle { assertEquals(index + 1, played); assertEquals(index + 1, downloaded); assertEquals(0, seeks); assertEquals(index + 1, toggles) }
        }
    }

    @Test @Config(qualifiers = "w840dp-h412dp-mdpi")
    fun platformCarouselOnWideScreensSelectsAndPlaysTheActualVisibleTitle() {
        var played: MediaItem? = null
        rule.setContent { NetflixTheme(platform = com.example.data.model.PlatformAppearance.APPLE) {
            HomeScreen(profile, CategoryFilter.CATEGORIES, "Apple TV", emptyList(), { false }, {}, { played = it }, {},
                catalogMedia = listOf(show, movie), catalogIsScoped = true, userSubscription = membership)
        } }
        rule.onNodeWithTag("platform_hero_page_1").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("platform_hero_page_1").assertIsSelected()
        rule.onNodeWithTag("hero_play").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(movie.id, played?.id) }
        capture("platform-apple-home-840")
    }

    @Test fun primeDetailsUsesActualSeasonAndKeepsDownloadAndTrailerActions() {
        val episode = com.example.data.model.Episode("reference-show_S1_e1", 1, "The first game", 46, "A real episode synopsis.", show.backdropUrl)
        val series = show.copy(episodes = listOf(episode), totalSeasons = 3, voteAverage = 8.2, voteCount = 100)
        var downloaded: String? = null
        var trailer = 0
        var list = 0
        rule.setContent { NetflixTheme(platform = com.example.data.model.PlatformAppearance.PRIME) {
            com.example.ui.screens.DetailScreen(series, false, userSubscription = membership, downloadProgressMap = emptyMap(),
                onClose = {}, onPlayClick = { _, _ -> }, onPlayTrailerClick = { _, _ -> trailer++ },
                onWatchlistToggle = { list++ }, onDownloadClick = { _, ep -> downloaded = ep?.id }, onRatingSelect = {}, onSimilarMediaClick = {})
        } }
        artworkReady(show.backdropUrl, show.logoUrl)
        capture("platform-prime-details-top-412")
        rule.onNodeWithTag("detail_my_list_button").performScrollTo().performClick()
        rule.onNodeWithTag("detail_trailer_button").performClick()
        rule.onNodeWithTag("prime_detail_facts").performScrollTo()
        rule.onNodeWithText("4.1/5").assertExists()
        rule.onNodeWithTag("prime_episode_download_${episode.id}").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(episode.id, downloaded); assertEquals(1, trailer); assertEquals(1, list) }
        capture("platform-prime-details-episodes-412")
        rule.onNodeWithText("Details", useUnmergedTree = true).performScrollTo().performClick()
        rule.onNodeWithText("Maturity rating").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Explore", useUnmergedTree = true).performScrollTo().performClick()
        rule.onNodeWithTag("prime_explore_trailer").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(2, trailer) }
    }

    @Test fun primeDownloadsGroupsSeasonsAndPlaysOnlyTheSelectedEpisode() {
        val episodes = listOf(1, 2).map { season -> com.example.data.model.Episode("reference-show_S${season}_e1", 1, "Episode in season $season", 46, "") }
        val series = show.copy(episodes = episodes, totalSeasons = 2)
        val downloads = episodes.map { episode -> com.example.data.local.DownloadEntity(downloadKey = "${series.id}_${episode.id}", mediaId = series.id, mediaTitle = series.title,
            episodeTitle = "S${if (episode == episodes[0]) 1 else 2}:E1 ${episode.title}", fileSizeMb = 512) }
        var played: String? = null
        var searched = false
        rule.setContent { NetflixTheme(platform = com.example.data.model.PlatformAppearance.PRIME) {
            com.example.ui.screens.DownloadsScreen(downloads, smartDownloadsEnabled = true,
                connectedCastDevice = null, onClose = {}, onPlayMedia = { _, ep -> played = ep?.id }, onDeleteDownload = {},
                onClearAllDownloads = {}, onToggleSmartDownloads = {}, onSetUpDownloadsForYou = {},
                onOpenSearch = { searched = true }, onOpenCast = {}, onOpenMediaDetail = {}, onShowToast = {}, userSubscription = membership,
                catalogMedia = listOf(series), activeProfile = profile)
        } }
        artworkReady(show.backdropUrl)
        rule.onNodeWithText("2 videos  1h 32min  1.0 GB").assertExists()
        rule.onNodeWithText("Squid Game - Season 1").assertIsDisplayed()
        rule.onNodeWithText("Squid Game - Season 2").assertIsDisplayed()
        capture("platform-prime-downloads-populated-412")
        rule.onNodeWithTag("prime_download_more").performClick()
        rule.runOnIdle { assertTrue(searched) }
        rule.onNodeWithTag("download_row_${downloads[1].downloadKey}").performClick()
        rule.onNodeWithText("Squid Game - Season 1").assertDoesNotExist()
        rule.onNodeWithText("1 video  46min  512 MB").assertExists()
        rule.onNodeWithTag("download_row_${downloads[1].downloadKey}").performClick()
        rule.runOnIdle { assertEquals(episodes[1].id, played) }
        rule.onNodeWithTag("downloads_edit_toggle_btn").performClick()
        rule.onNodeWithTag("download_row_${downloads[1].downloadKey}").performClick()
        rule.onNodeWithTag("downloads_delete_selected_btn").performScrollTo().performClick()
        rule.onNodeWithText("Delete downloads?").assertIsDisplayed()
    }

    @Test fun emptyGenreNeverShowsAnUnrelatedHeroOrContinueWatching() {
        val unrelated = show.copy(genreIds = listOf(18), genres = listOf("Drama"))
        rule.setContent { NetflixTheme {
            HomeScreen(profile, CategoryFilter.CATEGORIES, "Documentaries", listOf(unrelated to com.example.data.local.WatchProgressEntity(mediaId = unrelated.id, positionSeconds = 20, totalSeconds = 100)), { false }, {}, {}, {},
                catalogMedia = listOf(unrelated), userSubscription = membership)
        } }
        rule.onNodeWithTag("home_category_empty").assertIsDisplayed()
        rule.onNodeWithTag("hero_title_logo").assertDoesNotExist()
        rule.onNodeWithText("Continue Watching").assertDoesNotExist()
    }

    @Test fun selectedProviderHeroAlsoRespectsTheFilmsChip() {
        var played: MediaItem? = null
        rule.setContent { NetflixTheme {
            HomeScreen(profile, CategoryFilter.MOVIES, "Disney+", emptyList(), { false }, {}, { played = it }, {},
                catalogMedia = listOf(show.copy(isTrending = true), movie), catalogIsScoped = true, userSubscription = membership)
        } }
        artworkReady(movie.logoUrl, movie.backdropUrl)
        rule.onNodeWithText("Play").performClick()
        rule.runOnIdle { assertEquals(movie.id, played?.id); assertEquals(MediaType.MOVIE, played?.type) }
    }

    @Test fun providerDropdownKeepsSeriesSelectionAndGenreSurvivesFilmsSelection() {
        var selection: Pair<CategoryFilter, String?>? = null
        rule.setContent { NetflixTheme {
            NetflixTopBar(profile, CategoryFilter.TV_SHOWS, "Netflix", { filter, genre -> selection = filter to genre }, {}, {}, {})
        } }
        rule.onNodeWithTag("category_pill_categories").performScrollTo().performClick()
        rule.onNodeWithTag("category_menu_netflix").assertExists()
        rule.onNodeWithTag("category_menu_prime").assertExists()
        rule.onNodeWithTag("category_menu_disney").assertExists()
        rule.onNodeWithTag("category_menu_hotstar").assertExists()
        rule.onNodeWithTag("category_menu_apple").assertExists()
        rule.onNodeWithTag("category_menu_peacock").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(CategoryFilter.TV_SHOWS to "Peacock", selection) }
        rule.onNodeWithText("Films").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(CategoryFilter.MOVIES to "Netflix", selection) }
    }

    @Test
    @Config(qualifiers = "w840dp-h1100dp-mdpi")
    fun unfoldedHomeUsesTheEntireWindowAndCinematicArtwork() {
        var played = false
        var listed = false
        rule.setContent { NetflixTheme {
            com.example.ui.components.MobileHomeViewport {
                HomeScreen(profile, CategoryFilter.ALL, null, emptyList(), { false }, {},
                    { played = true }, { listed = true }, catalogMedia = listOf(show, movie), userSubscription = membership)
                NetflixTopBar(profile, CategoryFilter.ALL, null, { _, _ -> }, {}, {}, {})
                NetflixBottomNav(NavigationTab.HOME, profile, {}, Modifier.align(Alignment.BottomCenter), isClipsAllowed = true)
            }
        } }
        artworkReady(show.posterUrl, show.backdropUrl, show.logoUrl)
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val hero = rule.onNodeWithTag("home_cinematic_hero").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("hero_banner_card").assertDoesNotExist()
        assertEquals("Cinematic artwork must reach both edges", root.width, hero.width, 1f)
        assertEquals(root.left, hero.left, 1f)
        assertTrue("Use a wide image instead of a portrait card", hero.width > hero.height)
        val play = rule.onNodeWithTag("hero_play").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Actions belong on the left", play.center.x < root.center.x)
        rule.onNodeWithTag("hero_play").performClick()
        rule.onNodeWithTag("hero_my_list").performClick()
        rule.runOnIdle { assertTrue(played); assertTrue(listed) }
        capture("home-unfolded-840")
    }

    @Test
    @Config(qualifiers = "w840dp-h412dp-mdpi")
    fun landscapeHomeKeepsHeroControlsAboveNavigationAndRowsBelow() = assertLandscapeHome()

    @Test
    @Config(qualifiers = "w560dp-h360dp-mdpi")
    fun narrowLandscapeAlsoUsesCinematicHome() = assertLandscapeHome()

    private fun assertLandscapeHome() {
        rule.setContent { NetflixTheme {
            com.example.ui.components.MobileHomeViewport {
                HomeScreen(profile, CategoryFilter.ALL, null, emptyList(), { false }, {}, {}, {},
                    catalogMedia = listOf(show, movie), userSubscription = membership)
                NetflixTopBar(profile, CategoryFilter.ALL, null, { _, _ -> }, {}, {}, {})
                FloatingNavigation(NavigationTab.HOME)
            }
        } }
        artworkReady(show.posterUrl, show.backdropUrl, show.logoUrl)
        val hero = rule.onNodeWithTag("home_cinematic_hero").fetchSemanticsNode().boundsInRoot
        val play = rule.onNodeWithTag("hero_play").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val nav = rule.onNodeWithTag("nav_main_glass_pill").fetchSemanticsNode().boundsInRoot
        val chips = rule.onNodeWithTag("category_pill_series").fetchSemanticsNode().boundsInRoot
        val logo = rule.onNodeWithTag("cinematic_title", useUnmergedTree = true)
            .assertContentDescriptionEquals(show.title).assertIsDisplayed()
        val title = logo.fetchSemanticsNode().boundsInRoot
        assertTrue(title.top >= chips.bottom)
        assertTrue(play.bottom < nav.top)
        assertTrue(hero.bottom < nav.top)
        rule.onNodeWithText("Trending Now").assertIsDisplayed()
        capture("home-landscape-${hero.width.toInt()}")
    }

    @Test
    @Config(qualifiers = "w840dp-h1100dp-mdpi")
    fun resizingAnOpenHomeSwitchesLayoutsAndRetainsItsActions() {
        val wide = mutableStateOf(false)
        val listed = mutableStateOf(false)
        rule.setContent { NetflixTheme {
            Box(Modifier.size(if (wide.value) 840.dp else 412.dp, if (wide.value) 600.dp else 895.dp)) {
                com.example.ui.components.MobileHomeViewport {
                    HomeScreen(profile, CategoryFilter.ALL, null, emptyList(), { listed.value }, {}, {},
                        { listed.value = !listed.value }, catalogMedia = listOf(show, movie), userSubscription = membership)
                }
            }
        } }
        artworkReady(show.posterUrl, show.heroPosterUrl, show.logoUrl)
        rule.onNodeWithTag("hero_banner_card").assertIsDisplayed()
        rule.onNodeWithTag("home_cinematic_hero").assertDoesNotExist()
        rule.runOnIdle { wide.value = true }
        artworkReady(show.backdropUrl, show.logoUrl)
        rule.onNodeWithTag("home_cinematic_hero").assertIsDisplayed()
        rule.onNodeWithTag("hero_banner_card").assertDoesNotExist()
        rule.onNodeWithTag("hero_my_list").performClick()
        rule.runOnIdle { assertTrue(listed.value); wide.value = false }
        rule.onNodeWithTag("hero_banner_card").assertIsDisplayed()
        rule.onNodeWithTag("home_cinematic_hero").assertDoesNotExist()
        rule.onNodeWithTag("hero_my_list").performClick()
        rule.runOnIdle { assertTrue(!listed.value) }
    }

    @Test fun failedPortraitLogoRetriesTheSameUrlWhenTheNetworkReturns() = assertFailedLogoRecovers(false)

    @Test
    @Config(qualifiers = "w840dp-h600dp-mdpi")
    fun failedCinematicLogoRetriesTheSameUrlWhenTheNetworkReturns() = assertFailedLogoRecovers(true)

    private fun assertFailedLogoRecovers(cinematic: Boolean) {
        val online = mutableStateOf(false)
        val logoFile = File("build/reports/reference-fixtures/retry-logo.png").apply { parentFile!!.mkdirs(); delete() }
        val media = show.copy(logoUrl = logoFile.toURI().toString())
        rule.setContent { NetflixTheme {
            HomeScreen(profile, CategoryFilter.TV_SHOWS, null, emptyList(), { false }, {}, {}, {},
                catalogMedia = listOf(media), userSubscription = membership, artworkNetworkAvailable = online.value)
        } }
        artworkReady(show.posterUrl)
        if (cinematic) {
            rule.waitUntil(10_000) {
                rule.onAllNodes(hasTestTag("cinematic_title") and hasText(show.title), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } else {
            rule.waitUntil(10_000) {
                rule.onAllNodesWithTag("hero_title_logo", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
            }
        }
        javaClass.getResourceAsStream("/artwork/squid-game-logo.png")!!.use { input ->
            logoFile.outputStream().use { input.copyTo(it) }
        }
        rule.runOnIdle { online.value = true }
        artworkReady(media.logoUrl)
        rule.onNodeWithTag(if (cinematic) "cinematic_title" else "hero_title_logo", useUnmergedTree = true)
            .assertContentDescriptionEquals(show.title).assertIsDisplayed()
    }

    @Test fun artworkLookupCancelsOfflineAndRetriesWhenConnectivityReturns() {
        val online = mutableStateOf(true)
        var requests = 0
        var cancelled = 0
        rule.setContent { NetflixTheme {
            HomeScreen(profile, CategoryFilter.TV_SHOWS, null, emptyList(), { false }, {}, {}, {},
                catalogMedia = listOf(show.copy(logoUrl = null)), userSubscription = membership,
                artworkNetworkAvailable = online.value, onHeroVisible = {
                    requests++
                    try { kotlinx.coroutines.awaitCancellation() } finally { cancelled++ }
                })
        } }
        artworkReady(show.posterUrl)
        rule.runOnIdle { assertEquals(1, requests); online.value = false }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(1, cancelled); assertEquals(1, requests); online.value = true }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(2, requests) }
    }

    @Test fun seriesHeroRequestsItsOwnArtworkBeyondTheFirstFifteenCatalogItems() {
        var requested: MediaItem? = null
        val catalog = (1..20).map { movie.copy(id = "movie-$it") } + show
        rule.setContent { NetflixTheme {
            HomeScreen(profile, CategoryFilter.TV_SHOWS, null, emptyList(), { false }, {}, {}, {},
                catalogMedia = catalog, userSubscription = membership, onHeroVisible = { requested = it })
        } }
        artworkReady(show.posterUrl, show.heroPosterUrl, show.logoUrl)
        rule.runOnIdle { assertEquals(show.id, requested?.id); assertEquals(MediaType.TV_SHOW, requested?.type) }
        rule.onNodeWithTag("hero_title_logo", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun frostedHomePreviewRetainsCompactHeaderAndFloatingControls() {
        rule.setContent { NetflixTheme {
            val theme = remember { com.example.ui.glass.GlassThemeState(true) { Color(0xFF686348) } }
            androidx.compose.runtime.CompositionLocalProvider(com.example.ui.glass.LocalGlassTheme provides theme) {
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(profile, CategoryFilter.ALL, null, emptyList(), { false }, {}, {}, {},
                        catalogMedia = listOf(show, movie), userSubscription = membership)
                    NetflixTopBar(profile, CategoryFilter.ALL, null, { _, _ -> }, {}, {}, {}, unreadNotificationCount = 3)
                    FloatingNavigation(NavigationTab.HOME)
                }
            }
        } }
        artworkReady(show.posterUrl, show.heroPosterUrl, show.logoUrl)
        rule.onNodeWithTag("hero_title_logo", useUnmergedTree = true).assertIsDisplayed()
        val brand = rule.onNodeWithTag("top_bar_brand_glass").fetchSemanticsNode().boundsInRoot
        assertTrue(brand.width < 165f)
        assertFloatingNavigationHasSeparateTargets()
        capture("home-frosted-412")
    }

    @Test fun openingHomeCapturesTheReferenceAndKeepsCategoryAndNavigationActionsIndependent() {
        var played: MediaItem? = null
        var listed: MediaItem? = null
        var category: Pair<CategoryFilter, String?>? = null
        var newAndHot = 0
        val selectedTab = mutableStateOf(NavigationTab.HOME)
        rule.setContent { NetflixTheme {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(profile, CategoryFilter.ALL, null, emptyList(), { false }, {},
                    { played = it }, { listed = it }, catalogMedia = listOf(show, movie), userSubscription = membership)
                NetflixTopBar(profile, CategoryFilter.ALL, null, { filter, genre -> category = filter to genre },
                    { newAndHot++ }, {}, {}, isGamesAllowed = true, unreadNotificationCount = 3)
                FloatingNavigation(selectedTab.value) { selectedTab.value = it }
            }
        } }
        artworkReady(show.posterUrl, show.heroPosterUrl, show.logoUrl)
        val chips = listOf("series", "films", "games", "new_and_hot").map {
            rule.onNodeWithTag("category_pill_$it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        chips.zipWithNext().forEach { (left, right) -> assertTrue("Category pills must remain separate", left.right < right.left) }
        assertFloatingNavigationHasSeparateTargets()
        val play = rule.onNodeWithTag("hero_play").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val navigation = rule.onNodeWithTag("nav_tab_home").fetchSemanticsNode().boundsInRoot
        assertTrue("Hero actions must remain above the floating navigation", play.bottom <= navigation.top)
        capture("home-412")

        rule.onNodeWithTag("hero_play").performClick()
        rule.onNodeWithTag("hero_my_list").performClick()
        rule.runOnIdle { assertEquals(show, played); assertEquals(show, listed) }
        rule.onNodeWithTag("category_pill_films").performClick()
        rule.runOnIdle { assertEquals(CategoryFilter.MOVIES to null, category) }
        rule.onNodeWithTag("category_pill_games").performClick()
        rule.runOnIdle { assertEquals(CategoryFilter.GAMES to null, category) }
        rule.onNodeWithTag("category_pill_new_and_hot").performClick()
        rule.runOnIdle { assertEquals(1, newAndHot) }
        listOf("home" to NavigationTab.HOME, "clips" to NavigationTab.CLIPS,
            "search" to NavigationTab.SEARCH, "my_netflix" to NavigationTab.MY_NETFLIX).forEach { (tag, tab) ->
            rule.onNodeWithTag("nav_tab_$tag").performClick().assertIsSelected()
            rule.runOnIdle { assertEquals(tab, selectedTab.value) }
        }
    }

    @Test fun homeGamesRailUsesTheCatalogGameAndExploreCallbacks() {
        var opened: GameItem? = null
        var explore = 0
        rule.setContent { NetflixTheme {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(profile, CategoryFilter.ALL, null, emptyList(), { false }, {}, {}, {},
                    catalogMedia = listOf(show, movie), userSubscription = membership,
                    games = games, onGameClick = { opened = it }, onOpenGames = { explore++ })
                NetflixTopBar(profile, CategoryFilter.ALL, null, { _, _ -> }, {}, {}, {},
                    isGamesAllowed = true, scrollFractionProvider = { 1f })
                FloatingNavigation(NavigationTab.HOME)
            }
        } }
        rule.onNodeWithTag("home_vertical_list").performScrollToNode(hasTestTag("home_mobile_games"))
        rule.onNodeWithTag("home_game_reference-game").assertIsDisplayed().performClick()
        rule.onNodeWithTag("home_open_games").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(games.single(), opened); assertEquals(1, explore) }
        artworkReady(show.posterUrl, show.heroPosterUrl, show.logoUrl)
        capture("home-games-412")
    }

    @Test fun clipsReferenceHasUsableActionsAndCaptionAboveAllFourNavigationTabs() {
        val muted = mutableStateOf(true)
        val saved = mutableStateOf(false)
        var shares = 0
        var details = 0
        rule.setContent { NetflixTheme {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(show.posterUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .7f)))))
                ClipDiscoveryOverlay(show, saved.value, muted.value,
                    { muted.value = !muted.value }, { saved.value = !saved.value }, { shares++ }, { details++ }, Modifier.fillMaxSize())
                FloatingNavigation(NavigationTab.CLIPS)
            }
        } }
        artworkReady(show.posterUrl, minimumRequestsPerModel = 2)
        val caption = rule.onNodeWithTag("clip_description").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val disc = rule.onNodeWithTag("clip_disc_btn").fetchSemanticsNode().boundsInRoot
        val navigation = rule.onNodeWithTag("nav_tab_clips").fetchSemanticsNode().boundsInRoot
        assertTrue("The action rail must not cover the caption", disc.bottom <= caption.top)
        assertTrue("Caption must stay above the floating navigation", caption.bottom < navigation.top)
        assertFloatingNavigationHasSeparateTargets()
        capture("clips-412")
        rule.onNodeWithTag("clip_mute_btn").performClick().assertContentDescriptionEquals("Mute clip")
        rule.onNodeWithTag("clip_add_btn").performClick().assertContentDescriptionEquals("Remove from My List")
        rule.onNodeWithTag("clip_send_btn").performClick()
        rule.onNodeWithTag("clip_description").performClick()
        rule.runOnIdle { assertEquals(1, shares); assertEquals(1, details) }
    }

    @Composable private fun PersonalLibrary(
        moments: List<SavedMoment> = emptyList(),
        watchlist: List<MediaItem> = emptyList(),
        liked: List<MediaItem> = emptyList(),
        onPlayMoment: (SavedMoment) -> Unit = {},
        onRemoveMoment: (SavedMoment) -> Unit = {},
        onRemoveFromList: (MediaItem) -> Unit = {},
        onOpenClips: () -> Unit = {},
        onSearch: () -> Unit = {},
        onDownloads: () -> Unit = {}
    ) {
        Box(Modifier.fillMaxSize()) {
            MyNetflixScreen(activeProfile = profile, watchlist = watchlist, downloads = emptyList(), likedMedia = liked,
                watchedTrailers = emptyList(), notifications = emptyList(), continueWatchingList = emptyList(), watchHistory = emptyList(),
                reminders = emptyList(), connectedCastDevice = null, smartDownloadsEnabled = false,
                onSwitchProfileClick = {}, onMediaClick = {}, onPlayClick = {}, onPlayTrailerClick = {}, onOpenDownloads = onDownloads,
                onDeleteDownload = {}, onClearAllDownloads = {}, onWatchlistToggle = onRemoveFromList, onReminderToggle = {},
                onContinueWatchingOptionsClick = { _, _ -> }, onHistoryClick = {}, onOpenNotifications = {}, onOpenSettings = {},
                onOpenCast = {}, onNavigateToSearch = onSearch, onShowToast = {}, userSubscription = membership,
                savedMoments = moments, onPlayMoment = onPlayMoment, onRemoveMoment = onRemoveMoment, onOpenClips = onOpenClips)
            FloatingNavigation(NavigationTab.MY_NETFLIX)
        }
    }

    @Test fun personalLibraryOpensTheExactEpisodeMomentAndSupportsSeeAllAndRemoval() {
        val moments = mutableStateOf(listOf(episodeMoment, filmMoment))
        val watchlist = mutableStateOf(listOf(show, movie))
        var played: SavedMoment? = null
        var removedFromList: MediaItem? = null
        rule.setContent { NetflixTheme {
            PersonalLibrary(moments.value, watchlist.value, listOf(movie, show),
                onPlayMoment = { played = it }, onRemoveMoment = { selected -> moments.value = moments.value.filterNot { it.id == selected.id } },
                onRemoveFromList = { selected -> removedFromList = selected; watchlist.value = watchlist.value.filterNot { it.id == selected.id } })
        } }
        artworkReady(show.posterUrl, movie.posterUrl)
        rule.onNodeWithText("Starts at 02:05").assertIsDisplayed()
        rule.onNodeWithText("Squid Game | S2:E3").assertIsDisplayed()
        rule.onAllNodesWithText("Saved").onFirst().assertIsDisplayed()
        rule.onNodeWithText("15s").assertDoesNotExist()
        assertFloatingNavigationHasSeparateTargets()
        capture("my-netflix-loaded-412")
        rule.onNodeWithText("Starts at 02:05").performClick()
        rule.runOnIdle { assertEquals(episodeMoment, played); assertEquals("episode-s2e3", played!!.episodeId); assertEquals(125, played!!.positionSeconds) }

        rule.onNodeWithTag("my_moments_see_all_btn").performClick()
        rule.onNodeWithContentDescription("Close saved moments").assertIsDisplayed()
        capture("saved-moments-gallery-412", dialog = true)
        rule.onAllNodes(hasContentDescription("Saved moment options") and hasAnyAncestor(isDialog())).onFirst().performClick()
        rule.onNodeWithText("Remove moment").performClick()
        rule.runOnIdle { assertEquals(listOf(filmMoment), moments.value) }
        rule.onNodeWithContentDescription("Close saved moments").performClick()

        rule.onNodeWithTag("my_list_see_all_btn").performClick()
        rule.onNodeWithText("Films").performClick()
        // The same film also appears in the underlying saved-moment rail. The
        // interaction here belongs specifically to the open My List gallery.
        rule.onNode(hasText(movie.title) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        capture("my-list-gallery-412", dialog = true)
        rule.onNodeWithText("Remove").performClick()
        rule.runOnIdle { assertEquals(movie, removedFromList); assertEquals(listOf(show), watchlist.value) }
        rule.onNodeWithText("No titles here yet.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Close My List").performClick()
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-mdpi")
    fun compactEmptyLibraryStaysHonestAndOffersClipsSearchAndDownloads() {
        var clips = 0
        var searches = 0
        var downloads = 0
        rule.setContent { NetflixTheme {
            PersonalLibrary(onOpenClips = { clips++ }, onSearch = { searches++ }, onDownloads = { downloads++ })
        } }
        rule.onNodeWithTag("saved_moments_row").assertDoesNotExist()
        rule.onNodeWithTag("my_moments_see_all_btn").assertDoesNotExist()
        rule.onAllNodesWithText("Starts at", substring = true).assertCountEquals(0)
        assertFloatingNavigationHasSeparateTargets()
        val headerButtons = listOf("my_netflix_cast_button", "my_netflix_header_download_button", "my_netflix_notifications_btn")
            .map { rule.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        headerButtons.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
        capture("my-netflix-empty-360")
        rule.onNodeWithTag("my_netflix_clips").performClick()
        rule.onNodeWithText("Find something to watch").performClick()
        rule.onNodeWithTag("my_netflix_header_download_button").performClick()
        rule.runOnIdle { assertEquals(1, clips); assertEquals(1, searches); assertEquals(1, downloads) }
    }
}
