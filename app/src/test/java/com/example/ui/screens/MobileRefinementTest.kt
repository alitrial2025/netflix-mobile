package com.example.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.download.DownloadTaskInfo
import com.example.data.download.DownloadTaskStatus
import com.example.data.local.DownloadEntity
import com.example.data.model.*
import com.example.ui.components.DownloadAction
import com.example.ui.theme.NetflixTheme
import com.example.ui.viewmodel.PlayerState
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h895dp-mdpi", sdk = [34], application = Application::class)
class MobileRefinementTest {
    @get:Rule val rule = createComposeRule()
    private val premium = UserSubscription(planId = "plan_premium", status = "ACTIVE", expiresAt = Long.MAX_VALUE)
    private fun media(type: MediaType = MediaType.TV_SHOW) = MediaItem("mr_robot", "Mr. Robot", type,
        "A cybersecurity engineer becomes involved in a hidden world.", "Every revolution starts somewhere.", 96, "16+", 2015,
        if (type == MediaType.TV_SHOW) "4 Seasons" else "2h 10m", genres = listOf("Drama", "Thriller"), cast = listOf("Rami Malek"), director = "Sam Esmail",
        episodes = if (type == MediaType.TV_SHOW) (1..36).map { Episode("ep_mr_robot_S1_$it", it, "Chapter $it", 45, "An episode description.") } else emptyList(), totalSeasons = 4)
    @Test fun episodesAreLazyAndDownloadSelectionKeepsTheEpisodeKey() {
        var selected: String? = null
        rule.setContent { NetflixTheme { DetailScreen(media(), false, premium, downloadProgressMap = emptyMap(),
            onClose = {}, onPlayClick = { _, _ -> }, onPlayTrailerClick = { _, _ -> }, onWatchlistToggle = {},
            onDownloadClick = { _, ep -> selected = ep?.id }, onRatingSelect = {}, onSimilarMediaClick = {}) } }
        rule.onNodeWithTag("detail_episode_ep_mr_robot_S1_36").assertDoesNotExist()
        rule.onNodeWithTag("detail_list").performScrollToNode(hasTestTag("detail_episode_ep_mr_robot_S1_7"))
        rule.onNodeWithTag("download_action_mr_robot_ep_mr_robot_S1_7").performClick()
        assertEquals("ep_mr_robot_S1_7", selected)
    }
    @Test fun switchingSeriesToMovieResetsTheTabSelection() {
        val selected = mutableStateOf(media())
        rule.setContent { NetflixTheme { DetailScreen(selected.value, false, premium, downloadProgressMap = emptyMap(),
            onClose = {}, onPlayClick = { _, _ -> }, onPlayTrailerClick = { _, _ -> }, onWatchlistToggle = {},
            onDownloadClick = { _, _ -> }, onRatingSelect = {}, onSimilarMediaClick = {}) } }
        rule.onNodeWithTag("detail_list").performScrollToNode(hasText("Trailers & More"))
        rule.onNodeWithText("Trailers & More").performClick()
        rule.runOnIdle { selected.value = media(MediaType.MOVIE).copy(id = "movie") }
        rule.onNodeWithText("More Like This").assertIsSelected()
    }
    @Test fun completedDownloadOpensLibraryInsteadOfDeletingOrDownloadingAgain() {
        var opened = false; var started = false
        rule.setContent { NetflixTheme { DownloadAction("movie", null, false, true,
            onStart = { started = true }, onPause = {}, onResume = {}, onCancel = {}, onCompleted = { opened = true }) } }
        rule.onNodeWithText("Downloaded · View").performClick()
        assertTrue(opened); assertFalse(started)
    }
    @Test fun failedTransferOffersRetryAndCancel() {
        var retried = false; var cancelled = false
        rule.setContent { NetflixTheme { DownloadAction("transfer", .42f, false, false,
            DownloadTaskInfo("transfer", "movie", "Title", status = DownloadTaskStatus.ERROR),
            onStart = {}, onPause = {}, onResume = { retried = true }, onCancel = { cancelled = true }, onCompleted = {}) } }
        rule.onNodeWithContentDescription("Retry download").performClick()
        rule.onNodeWithContentDescription("Cancel download").performClick()
        assertTrue(retried); assertTrue(cancelled)
    }
    @Test fun offlineEpisodePlaybackPreservesSavedSeasonAndZeroStorageDoesNotCrash() {
        var selectedEpisode: Episode? = null
        val downloaded = DownloadEntity(profileId = "profile", downloadKey = "mr_robot_ep_mr_robot_S3_7", mediaId = "mr_robot", mediaTitle = "Mr. Robot", episodeTitle = "S3:E7 A saved episode", fileSizeMb = 185)
        rule.setContent { NetflixTheme { DownloadsScreen(listOf(downloaded), smartDownloadsEnabled = false, connectedCastDevice = null,
            userSubscription = premium, onClose = {}, onPlayMedia = { _, ep -> selectedEpisode = ep }, onDeleteDownload = {}, onClearAllDownloads = {},
            onToggleSmartDownloads = {}, onSetUpDownloadsForYou = {}, onOpenSearch = {}, onOpenCast = {}, onOpenMediaDetail = {}, onShowToast = {}, catalogMedia = emptyList()) } }
        rule.onNodeWithTag("download_row_mr_robot_ep_mr_robot_S3_7").performClick()
        assertEquals("ep_mr_robot_S3_7", selectedEpisode?.id)
        assertEquals(7, selectedEpisode?.episodeNumber)
    }
    @Test @Config(qualifiers = "w895dp-h412dp-mdpi") fun completionActionsUseCurrentTitleAndRecommendations() {
        var replayed = false; var selected: String? = null
        val recommendation = media(MediaType.MOVIE).copy(id = "next", title = "Next story")
        rule.setContent { NetflixTheme { PostPlayOverlay(PlayerState(media = media(MediaType.MOVIE), hasEnded = true), listOf(recommendation),
            onReplay = { replayed = true }, onClose = {}, onNext = {}, onRetryNext = {}, onRecommendation = { selected = it.id }, onRating = {}) } }
        rule.onNodeWithTag("replay_button").performClick()
        rule.onNodeWithTag("post_play_next").performClick()
        assertTrue(replayed); assertEquals("next", selected)
    }
    private fun advanceSeconds(seconds: Int) {
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        repeat(seconds) {
            rule.mainClock.advanceTimeBy(1000)
            org.robolectric.shadows.ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS)
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
        }
    }
    @Test fun playerTapControlsRecoverAfterStreamResolution() {
        val state = mutableStateOf(PlayerState(media = media(MediaType.MOVIE), isResolving = true, showControls = false))
        var toggles = 0
        rule.setContent { NetflixTheme { VideoPlayerScreen(state.value,
            onClose = {}, onTogglePlayPause = {}, onSeek = {}, onSkipForward10 = {}, onSkipBackward10 = {}, onSkipIntro = {},
            onSetSpeed = {}, onSetAudio = {}, onSetSubtitle = {}, onToggleLock = {}, onToggleControls = { toggles++ },
            onShowAudioSubtitles = {}, onShowEpisodesDrawer = {}, onPlayNextEpisode = {}, onSelectEpisode = {}) } }
        rule.runOnIdle { state.value = state.value.copy(isResolving = false) }
        rule.onNodeWithTag("video_player_container").performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(1, toggles) }
    }
    @Test fun nextEpisodeCountdownPausesInBackgroundAndTriggersOnlyOnce() {
        rule.mainClock.autoAdvance = false
        val active = mutableStateOf(false)
        var nextCount = 0
        val episode = media().episodes[1]
        rule.setContent { NetflixTheme { NextEpisodeCard(PlayerState(media = media(), nextEpisode = episode, isPlaying = true),
            autoPlay = true, active = active.value, onNext = { nextCount++ }, onWatchCredits = {}) } }
        advanceSeconds(35)
        rule.runOnIdle { assertEquals(0, nextCount); active.value = true }
        advanceSeconds(35)
        rule.runOnIdle { assertEquals(1, nextCount) }
        advanceSeconds(35)
        rule.runOnIdle { assertEquals(1, nextCount) }
    }
    @Test fun watchCreditsCancelsTheCountdownAndManualPlayWorksWithAutoplayDisabled() {
        rule.mainClock.autoAdvance = false
        val watchCredits = mutableStateOf(false)
        val autoPlay = mutableStateOf(true)
        var nextCount = 0
        rule.setContent { NetflixTheme {
            if (!watchCredits.value) NextEpisodeCard(PlayerState(media = media(), nextEpisode = media().episodes[1]),
                autoPlay = autoPlay.value, active = true, onNext = { nextCount++ }, onWatchCredits = { watchCredits.value = true })
        } }
        rule.onNodeWithTag("watch_credits_button").performClick()
        advanceSeconds(35)
        rule.runOnIdle { assertEquals(0, nextCount); autoPlay.value = false; watchCredits.value = false }
        advanceSeconds(35)
        rule.runOnIdle { assertEquals(0, nextCount) }
        rule.onNodeWithTag("next_episode_button").performClick()
        rule.runOnIdle { assertEquals(1, nextCount) }
    }
    @Test fun renderDetailsAndDownloadsForReview() {
        // UI review only; actual interaction/correctness checks are above.
        val output = File("/workspace/artifacts/mobile-refinement/ui").apply { mkdirs() }
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(context).build())
        val directory = File(context.cacheDir, "refinement-artwork").apply { mkdirs() }
        val image = File(directory, "squid-game.jpg")
        javaClass.getResourceAsStream("/artwork/squid-game.jpg")!!.use { input -> image.outputStream().use { input.copyTo(it) } }
        val logo = File(directory, "squid-game-logo.png")
        javaClass.getResourceAsStream("/artwork/squid-game-logo.png")!!.use { input -> logo.outputStream().use { input.copyTo(it) } }
        val source = media().copy(logoUrl = logo.toURI().toString(), title = "Squid Game", releaseYear = 2021, durationOrSeasons = "3 Seasons", totalSeasons = 3,
            tagline = "The game never ends.", description = "Hundreds of cash-strapped players accept a strange invitation to compete in children's games. Inside, a tempting prize awaits — with deadly high stakes.",
            cast = listOf("Lee Jung-jae", "Lee Byung-hun"), director = "Hwang Dong-hyuk",
            episodes = listOf(Episode("ep_mr_robot_S1_1", 1, "The Invitation", 60, "A mysterious invitation brings Gi-hun to a competition that promises a life-changing prize.")),
            backdropUrl = image.toURI().toString(), posterUrl = image.toURI().toString())
        val route = mutableStateOf(false)
        rule.setContent { NetflixTheme {
            if (!route.value) DetailScreen(source, true, premium, downloadProgressMap = emptyMap(), onClose = {}, onPlayClick = { _, _ -> }, onPlayTrailerClick = { _, _ -> }, onWatchlistToggle = {}, onDownloadClick = { _, _ -> }, onRatingSelect = {}, onSimilarMediaClick = {})
            else DownloadsScreen(listOf(DownloadEntity(downloadKey = "mr_robot_ep_mr_robot_S1_1", mediaId = "mr_robot", mediaTitle = "Squid Game", episodeTitle = "S1:E1 The Invitation", fileSizeMb = 185)),
                smartDownloadsEnabled = true, connectedCastDevice = null, userSubscription = premium,
                onClose = {}, onPlayMedia = { _, _ -> }, onDeleteDownload = {}, onClearAllDownloads = {}, onToggleSmartDownloads = {}, onSetUpDownloadsForYou = {}, onOpenSearch = {}, onOpenCast = {}, onOpenMediaDetail = {}, onShowToast = {}, catalogMedia = listOf(source))
        } }
        rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(250)
        rule.onRoot().captureRoboImage(File(output, "details.png").path)
        rule.runOnIdle { route.value = true }
        rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(250)
        rule.onRoot().captureRoboImage(File(output, "downloads.png").path)
    }
}
