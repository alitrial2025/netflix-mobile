package com.example.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.model.*
import com.example.ui.theme.NetflixTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h895dp-mdpi", sdk = [34], application = Application::class)
class ProfileAndSearchTest {
    @get:Rule val rule = createComposeRule()
    private fun media(id: String, type: MediaType) = MediaItem(id, if (type == MediaType.TV_SHOW) "Mr. Robot" else "Interstellar", type,
        "A story", "", 90, "16+", 2015, "2h", genres = listOf("Drama"), cast = emptyList(), director = "")

    @Test fun audioPreferenceIsEditableAndSavedWithOtherDraftChanges() {
        var saved: UserProfile? = null
        rule.setContent { NetflixTheme { EditProfileScreen(UserProfile("p1", "Alex"), { saved = it }, {}, {}, {}, canDelete = false) } }
        rule.onNodeWithTag("edit_profile_name_input").performTextReplacement("Alicia")
        rule.onNodeWithTag("audio_subtitles_item").performScrollTo().performClick()
        rule.onNodeWithTag("profile_audio_French").performClick()
        rule.onNodeWithTag("profile_preferences_back").performClick()
        rule.onNodeWithTag("edit_profile_save_button").performClick()
        assertEquals("Alicia", saved!!.name)
        assertEquals("French", saved!!.audioLanguage)
        assertEquals("Off", saved!!.subtitleLanguage)
    }

    @Test fun gameHandleIsWiredAndTheOnlyProfileCannotBeDeleted() {
        var saved: UserProfile? = null
        rule.setContent { NetflixTheme { EditProfileScreen(UserProfile("p1", "Alex"), { saved = it }, {}, {}, {}, canDelete = false) } }
        rule.onNodeWithTag("game_handle_item").performScrollTo().performClick()
        rule.onNodeWithTag("profile_handle_input").performTextReplacement("pro_player")
        rule.onNodeWithTag("profile_handle_apply").performClick()
        rule.onNodeWithTag("delete_profile_button").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("edit_profile_save_button").performClick()
        assertEquals("pro_player", saved!!.gameHandle)
    }

    @Test fun searchFiltersRealResultsAndClearQueryIsConnected() {
        var query: String? = null
        var opened: MediaItem? = null
        rule.setContent { NetflixTheme { SearchScreen("robot", null, listOf(media("show", MediaType.TV_SHOW), media("movie", MediaType.MOVIE)),
            onQueryChange = { query = it }, onGenreFilterSelect = {}, onMediaClick = { opened = it }, onPlayClick = {}) } }
        rule.onNodeWithTag("search_filter_Series").performClick()
        rule.onNodeWithTag("search_result_movie").assertDoesNotExist()
        rule.onNodeWithTag("search_result_show").performClick()
        assertEquals("show", opened!!.id)
        rule.onNodeWithTag("search_clear").performClick()
        assertEquals("", query)
    }

    @Test fun smartDownloadSwitchesAndStorageAllocationHaveIndependentCallbacks() {
        var next: Boolean? = null; var forYou: Boolean? = null; var allocation: Float? = null
        rule.setContent { NetflixTheme { SmartDownloadsScreen(listOf(UserProfile("p1", "Alex")), mapOf("p1" to 3f),
            false, true, 24L * 1024 * 1024 * 1024, 0, {}, { next = it }, { forYou = it }, { _, gb -> allocation = gb }) } }
        rule.onNodeWithTag("smart_next_episode").performClick()
        assertEquals(true, next); assertNull(forYou)
        rule.onNodeWithTag("smart_allocation_plus_p1").performClick()
        assertEquals(3.5f, allocation!!)
        rule.onNodeWithTag("smart_for_you").performClick()
        assertEquals(false, forYou)
    }

    @Test fun renderProfileSearchAndSmartDownloadsForReview() {
        val route = mutableStateOf(0)
        val output = File("/workspace/artifacts/mobile-refinement/ui").apply { mkdirs() }
        rule.setContent { NetflixTheme {
            when (route.value) {
                0 -> EditProfileScreen(UserProfile("p1", "Alex"), {}, {}, {}, {}, canDelete = false)
                1 -> SearchScreen("", null, listOf(media("show", MediaType.TV_SHOW), media("movie", MediaType.MOVIE)), onQueryChange = {}, onGenreFilterSelect = {}, onMediaClick = {}, onPlayClick = {})
                else -> SmartDownloadsScreen(listOf(UserProfile("p1", "Alex"), UserProfile("p2", "Kids", isKids = true)),
                    mapOf("p1" to 3f, "p2" to 1f), true, true, 24L * 1024 * 1024 * 1024, 0, {}, {}, {}, { _, _ -> })
            }
        } }
        for ((index, filename) in listOf("profile-edit.png", "search.png", "smart-downloads.png").withIndex()) {
            rule.runOnIdle { route.value = index }
            rule.waitForIdle()
            rule.onRoot().captureRoboImage(File(output, filename).path)
        }
    }
}
