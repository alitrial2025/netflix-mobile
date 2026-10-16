package com.example

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    private var previousFramePause = false
    private var previousFrameDelay = java.time.Duration.ZERO

    // Foundation 1.7 prefetch can repost forever with synchronous zero-delay
    // Robolectric vsync. Keep real frame intervals; UI assertions stay intact.
    @org.junit.Before fun useBoundedNativeFrames() {
        previousFramePause = org.robolectric.shadows.ShadowChoreographer.isPaused()
        previousFrameDelay = org.robolectric.shadows.ShadowChoreographer.getFrameDelay()
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
    }

    @org.junit.After fun restoreNativeFrames() {
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(previousFrameDelay)
        org.robolectric.shadows.ShadowChoreographer.setPaused(previousFramePause)
    }

    private val loadedArtwork = ConcurrentHashMap.newKeySet<String>()
    private val artworkSuccesses = ConcurrentHashMap<String, AtomicInteger>()
    private val profile = UserProfile("reference-profile", "Mike", avatarColorHex = 0xFFE50914L)
    private val membership = UserSubscription(planId = "plan_premium", status = "ACTIVE", expiresAt = Long.MAX_VALUE)

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
            posterUrl = fixture("squid-game.jpg"), backdropUrl = fixture("squid-game.jpg"),
            logoUrl = fixture("squid-game-logo.png"), isOriginal = true, isTrending = true)
    }
    private val movie by lazy {
        show.copy(id = "reference-film", title = "Umthetho", type = MediaType.MOVIE, isTrending = false,
            posterUrl = fixture("umthetho.jpg"), backdropUrl = fixture("umthetho.jpg"),
            logoUrl = fixture("umthetho-logo.png"))
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

    @Test
    @Config(qualifiers = "w840dp-h1100dp-mdpi")
    fun unfoldedHomeKeepsThePortraitCompositionAndPlayableControls() {
        var played = false
        rule.setContent { NetflixTheme {
            com.example.ui.components.MobileHomeViewport {
                HomeScreen(profile, CategoryFilter.ALL, null, emptyList(), { false }, {},
                    { played = true }, {}, catalogMedia = listOf(show, movie), userSubscription = membership)
                NetflixTopBar(profile, CategoryFilter.ALL, null, { _, _ -> }, {}, {}, {})
                NetflixBottomNav(NavigationTab.HOME, profile, {}, Modifier.align(Alignment.BottomCenter), isClipsAllowed = true)
            }
        } }
        artworkReady(show.posterUrl)
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val hero = rule.onNodeWithTag("hero_banner_card").fetchSemanticsNode().boundsInRoot
        assertTrue("A wide window must retain a portrait hero", hero.height > hero.width)
        assertTrue("Home must remain a centered mobile column", hero.width < root.width * .75f)
        assertTrue(kotlin.math.abs(hero.center.x - root.center.x) < 2f)
        rule.onNodeWithTag("hero_play").performClick()
        rule.runOnIdle { assertTrue(played) }
        capture("home-unfolded-840")
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
        artworkReady(show.posterUrl)
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
        artworkReady(show.posterUrl)
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
