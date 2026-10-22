package com.example

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.screens.PlayerAudioSubtitleSheet
import com.example.ui.screens.PlayerTrackOption
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h360dp-land-mdpi", sdk = [34], application = Application::class)
class PlayerAudioSubtitleSheetTest {
    @get:Rule val rule = createComposeRule()
    @Test fun landscapeTracksScrollIndependentlyUseExactIdentitiesAndKeepTimingBelowThem() {
        var audio by mutableStateOf(listOf(PlayerTrackOption("en", "English", "en", true), PlayerTrackOption("es", "Spanish", "es", false)))
        var subs by mutableStateOf(listOf(PlayerTrackOption("off", "Off", "", true)) + (1..16).map { PlayerTrackOption("s$it", "English variant $it", "en", false) })
        var offset by mutableIntStateOf(0)
        var chosen: String? = null; var closed = 0
        rule.setContent { MaterialTheme { PlayerAudioSubtitleSheet(audio, subs, offset,
            { selected -> audio = audio.map { it.copy(isSelected = it.id == selected.id) } },
            { selected -> chosen = selected.id; subs = subs.map { it.copy(isSelected = it.id == selected.id) } },
            { offset = it }, { closed++ }) } }
        assertTrue("landscape track list has room to browse", rule.onNodeWithTag("player_subtitle_tracks").fetchSemanticsNode().boundsInRoot.height >= 72f)
        rule.onNodeWithTag("player_subtitle_tracks").performScrollToIndex(16)
        rule.onNodeWithTag("player_subtitle_s16").assertIsDisplayed().performClick().assertIsSelected()
        assertEquals("s16", chosen)
        rule.onNodeWithTag("player_audio_es").performClick().assertIsSelected()
        rule.onNodeWithTag("player_audio_en").assertIsNotSelected()
        rule.onNodeWithTag("subtitle_later").assertIsDisplayed().performClick()
        rule.onNodeWithTag("subtitle_offset_value").assertTextEquals("+0.25 s")
        rule.onNodeWithTag("subtitle_earlier").performClick()
        rule.onNodeWithTag("subtitle_offset_value").assertTextEquals("+0.00 s")
        rule.onNodeWithTag("player_subtitle_tracks").performScrollToIndex(0)
        rule.onNodeWithTag("player_subtitle_off").performClick().assertIsSelected()
        rule.onNodeWithTag("player_tracks_done").performClick(); assertEquals(1, closed)
    }
    @Test @Config(qualifiers = "w360dp-h740dp-port-mdpi")
    fun narrowSheetKeepsTimingButtonsInsideItsBoundsAndResetIsAccessible() {
        var offset by mutableIntStateOf(500)
        rule.setContent { MaterialTheme { PlayerAudioSubtitleSheet(emptyList(), listOf(PlayerTrackOption("off", "Off", "", true)), offset, {}, {}, { offset = it }, {}) } }
        val panel = rule.onNodeWithTag("player_tracks_sheet").fetchSemanticsNode().boundsInRoot
        for (tag in listOf("subtitle_earlier", "subtitle_timing_reset", "subtitle_later", "player_tracks_done")) {
            val button = rule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(tag, button.left >= panel.left && button.right <= panel.right && button.bottom <= panel.bottom)
        }
        rule.onNodeWithTag("subtitle_timing_reset").performClick()
        rule.onNodeWithTag("subtitle_offset_value").assertTextEquals("+0.00 s")
        rule.onNodeWithText("No alternate tracks available").assertIsDisplayed()
    }
}
