package com.example.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.theme.NetflixTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise the actual feed controls without resolving a trailer or opening a network connection. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h780dp-mdpi", sdk = [34], application = Application::class)
class ClipDiscoveryOverlayTest {
    @get:Rule val rule = createComposeRule()

    private val title = MediaItem(
        id = "539", title = "A long title that must not push the clip controls off the screen",
        type = MediaType.MOVIE, description = "A real catalog synopsis that is longer than one caption line, with details available through more.",
        tagline = "", matchPercentage = 90, maturityRating = "PG", releaseYear = 2026,
        durationOrSeasons = "1h 30m", genres = listOf("Music", "Family Films", "Kids & Family Films"),
        cast = emptyList(), director = ""
    )

    @Test fun mutedAndSavedStateControlsCallTheRealCallbacksAndUpdateAccessibilityLabels() {
        val muted = mutableStateOf(true)
        val saved = mutableStateOf(false)
        var shareCount = 0
        var detailsCount = 0
        rule.setContent { NetflixTheme {
            ClipDiscoveryOverlay(title, saved.value, muted.value,
                onToggleMute = { muted.value = !muted.value },
                onToggleWatchlist = { saved.value = !saved.value },
                onShare = { shareCount++ }, onMediaClick = { detailsCount++ },
                modifier = Modifier.fillMaxSize())
        } }
        rule.onNodeWithTag("clip_mute_btn").assertContentDescriptionEquals("Unmute clip").performClick()
        rule.onNodeWithTag("clip_mute_btn").assertContentDescriptionEquals("Mute clip")
        rule.onNodeWithTag("clip_add_btn").assertContentDescriptionEquals("Add to My List").performClick()
        rule.onNodeWithTag("clip_add_btn").assertContentDescriptionEquals("Remove from My List").performClick()
        rule.runOnIdle { assertEquals(false, saved.value) }
        rule.onNodeWithTag("clip_send_btn").performClick()
        rule.onNodeWithTag("clip_disc_btn").performClick()
        rule.onNodeWithTag("clip_description").performClick()
        rule.runOnIdle {
            assertEquals(1, shareCount)
            assertEquals(2, detailsCount)
        }
    }

    @Test fun compactActionRailStaysAboveTheCaptionAndLeavesRoomForFloatingNavigation() {
        rule.setContent { NetflixTheme {
            ClipDiscoveryOverlay(title, false, true, {}, {}, {}, {}, Modifier.fillMaxSize())
        } }
        val railTags = listOf("clip_mute_btn", "clip_add_btn", "clip_send_btn", "clip_disc_btn")
        val bounds = railTags.map { rule.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        bounds.zipWithNext().forEach { (above, below) -> assertTrue(above.bottom <= below.top) }
        val caption = rule.onNodeWithTag("clip_description").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.last().bottom <= caption.top)
        assertTrue(caption.bottom <= 780f - 100f)
        rule.onNodeWithText(" more").assertIsDisplayed()
        rule.onNodeWithText("14:00").assertDoesNotExist()
    }

    @Test fun talkBackCanLikeAndUnlikeThroughTheTitleCustomActionWithoutExtraChrome() {
        val liked = mutableStateOf(false)
        var callbacks = 0
        rule.setContent { NetflixTheme {
            ClipDiscoveryOverlay(title, false, true, {}, {}, {}, {}, Modifier.fillMaxSize(),
                isLiked = liked.value,
                onToggleLike = { callbacks++; liked.value = !liked.value })
        } }
        fun invokeLikeAction(label: String, state: String) {
            val semantics = rule.onNodeWithTag("clip_title").fetchSemanticsNode().config
            assertEquals(state, semantics[SemanticsProperties.StateDescription])
            val action = semantics[SemanticsActions.CustomActions].single()
            assertEquals(label, action.label)
            rule.runOnIdle { assertTrue(action.action()) }
        }
        invokeLikeAction("Like ${title.title}", "Not liked")
        invokeLikeAction("Unlike ${title.title}", "Liked")
        rule.runOnIdle {
            assertEquals(false, liked.value)
            assertEquals(2, callbacks)
        }
    }

    @Test fun missingCatalogMetadataIsOmittedRatherThanInvented() {
        rule.setContent { NetflixTheme {
            ClipDiscoveryOverlay(title.copy(description = "", genres = emptyList()), false, true,
                {}, {}, {}, {}, Modifier.fillMaxSize())
        } }
        rule.onNodeWithTag("clip_title").assertExists()
        rule.onNodeWithTag("clip_description").assertDoesNotExist()
        rule.onNodeWithText("Featured • Trending").assertDoesNotExist()
        rule.onNodeWithText(" more").assertDoesNotExist()
    }
}
