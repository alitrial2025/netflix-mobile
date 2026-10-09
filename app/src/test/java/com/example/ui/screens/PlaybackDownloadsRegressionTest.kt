package com.example.ui.screens

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.local.DownloadEntity
import com.example.data.model.*
import com.example.ui.theme.NetflixTheme
import com.example.ui.viewmodel.DetailLoadState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h895dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaybackDownloadsRegressionTest {
    @get:Rule val rule = createComposeRule()
    private val premium = UserSubscription(planId = "plan_premium", status = "ACTIVE", expiresAt = Long.MAX_VALUE)
    private fun download(key: String, episodeTitle: String? = null) = DownloadEntity(
        profileId = "profile", downloadKey = key, mediaId = "42", mediaTitle = "Downloaded title",
        episodeTitle = episodeTitle, fileSizeMb = 100)
    private fun title(type: MediaType = MediaType.TV_SHOW) = MediaItem("42", "Title $type", type,
        "Description", "", 90, "16+", 2026, "2 Seasons", genres = listOf("Drama"), cast = emptyList(), director = "",
        totalSeasons = 2, episodes = if (type == MediaType.TV_SHOW) listOf(Episode("ep_42_S1_1", 1, "First", 45, "")) else emptyList())

    @Composable
    private fun Library(downloads: List<DownloadEntity>, profile: UserProfile = UserProfile("profile", "Alex"),
                        onDelete: (String) -> Unit = {}, onClose: () -> Unit = {},
                        onPlay: (MediaItem, Episode?) -> Unit = { _, _ -> }, catalog: List<MediaItem> = emptyList()) {
        DownloadsScreen(downloads, smartDownloadsEnabled = false, connectedCastDevice = null, userSubscription = premium,
            activeProfile = profile, downloadsForYouEnabled = true, catalogMedia = catalog,
            onClose = onClose, onPlayMedia = onPlay, onDeleteDownload = onDelete, onClearAllDownloads = {},
            onToggleSmartDownloads = {}, onSetUpDownloadsForYou = {}, onOpenSearch = {}, onOpenCast = {},
            onOpenMediaDetail = {}, onShowToast = {})
    }

    @Test fun systemBackLeavesEditModeThenTheEpisodeGroup() {
        lateinit var back: OnBackPressedDispatcher
        var closes = 0
        rule.setContent { NetflixTheme {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            Library(listOf(download("42_ep_42_S1_1", "First"), download("42_ep_42_S1_2", "Second")), onClose = { closes++ })
        } }
        rule.onNodeWithTag("download_row_42_ep_42_S1_1").performClick()
        rule.onNodeWithText("Edit").performClick()
        rule.onNodeWithTag("download_row_42_ep_42_S1_2").performClick()
        rule.runOnIdle { back.onBackPressed() }
        rule.onNodeWithText("Edit").assertExists()
        rule.onNodeWithText("Second").assertExists()
        rule.runOnIdle { back.onBackPressed() }
        rule.onNodeWithText("2 episodes · 200 MB", substring = true).assertExists()
        assertEquals(0, closes)
    }

    @Test fun deletingSelectedDownloadsRequiresConfirmationAndUsesExactEpisodeKey() {
        val deleted = mutableListOf<String>()
        rule.setContent { NetflixTheme {
            Library(listOf(download("42_ep_42_S1_1", "First"), download("42_ep_42_S1_2", "Second")), onDelete = { deleted += it })
        } }
        rule.onNodeWithText("Edit").performClick()
        rule.onNodeWithTag("download_row_42_ep_42_S1_2").performClick()
        rule.onNodeWithTag("downloads_delete_selected_btn").performScrollTo().performClick()
        rule.onNodeWithText("Cancel").performClick()
        assertTrue(deleted.isEmpty())
        rule.onNodeWithTag("downloads_delete_selected_btn").performClick()
        rule.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        assertEquals(listOf("42_ep_42_S1_2"), deleted)
    }

    @Test fun switchingProfileDismissesPendingDeleteAndClearsEditSelection() {
        val profile = mutableStateOf(UserProfile("profile", "Alex"))
        val deleted = mutableListOf<String>()
        rule.setContent { NetflixTheme { Library(listOf(download("42")), profile.value, onDelete = { deleted += it }) } }
        rule.onNodeWithText("Edit").performClick()
        rule.onNodeWithTag("download_row_42").performClick()
        rule.onNodeWithTag("downloads_delete_selected_btn").performScrollTo().performClick()
        rule.runOnIdle { profile.value = UserProfile("other", "Sam") }
        rule.onNodeWithText("Delete downloads?").assertDoesNotExist()
        rule.onNodeWithTag("download_row_42").assertDoesNotExist()
        rule.onNodeWithTag("downloads_delete_selected_btn").assertDoesNotExist()
        assertTrue(deleted.isEmpty())
    }

    @Test fun offlineEpisodeWithoutLabelUsesTheSeriesEvenWhenMovieHasTheSameId() {
        var played: Pair<MediaItem, Episode?>? = null
        rule.setContent { NetflixTheme {
            Library(listOf(download("42_ep_42_S2_7")), onPlay = { media, episode -> played = media to episode },
                catalog = listOf(title(MediaType.MOVIE), title()))
        } }
        rule.onNodeWithTag("download_row_42_ep_42_S2_7").performClick()
        assertEquals(MediaType.TV_SHOW, played?.first?.type)
        assertEquals("ep_42_S2_7", played?.second?.id)
        assertEquals(7, played?.second?.episodeNumber)
        assertEquals(MediaType.TV_SHOW, downloadedMedia(download("42_ep_42_S2_7"), emptyList()).type)
    }

    @Test fun detailRetryRemainsAvailableAndSameIdMovieResetsSeriesTab() {
        val selected = mutableStateOf(title())
        var retries = 0
        rule.setContent { NetflixTheme {
            DetailScreen(selected.value, false, premium, downloadProgressMap = emptyMap(),
                loadState = DetailLoadState(error = "Information couldn't load"), onRetryDetails = { retries++ },
                onClose = {}, onPlayClick = { _, _ -> }, onPlayTrailerClick = { _, _ -> }, onWatchlistToggle = {},
                onDownloadClick = { _, _ -> }, onRatingSelect = {}, onSimilarMediaClick = {})
        } }
        rule.onNodeWithTag("detail_list").performScrollToNode(hasText("Try again"))
        rule.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
        rule.onNodeWithTag("detail_list").performScrollToNode(hasText("Trailers & More"))
        rule.onNodeWithText("Trailers & More").performClick()
        rule.runOnIdle { selected.value = title(MediaType.MOVIE) }
        rule.onNodeWithText("More Like This").assertIsSelected()
    }
}
