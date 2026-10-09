package com.example.ui.screens

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.CatalogData
import com.example.data.model.*
import com.example.ui.components.BrowseTabs
import com.example.ui.components.MediaSectionRow
import com.example.ui.theme.NetflixTheme
import com.example.ui.viewmodel.CategoryFilter
import com.example.ui.viewmodel.NavigationTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h895dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrowseScreenRegressionTest {
    @get:Rule val rule = createComposeRule()
    private val previousCatalog = CatalogData.allMedia
    @After fun restoreCatalog() { CatalogData.allMedia = previousCatalog }
    private val profile = UserProfile("profile", "Alex")
    private fun media(id: String, type: MediaType = MediaType.MOVIE, genres: List<String> = listOf("Drama")) =
        MediaItem(id, "Title $id", type, "", "", 99, "16+", 2026, "2h", isTrending = true,
            genres = genres, cast = emptyList(), director = "")

    @Test fun anEmptyHomeFilterExplainsItsStateAndCanBeCleared() {
        var cleared = false
        rule.setContent { NetflixTheme {
            HomeScreen(profile, CategoryFilter.CATEGORIES, "Comedy", emptyList(), { false }, {}, {}, {},
                catalogMedia = listOf(media("drama")), onClearFilters = { cleared = true })
        } }
        rule.waitUntil(10_000) {
            ShadowLooper.idleMainLooper()
            rule.onAllNodesWithText("No titles in this category").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("You’re offline").assertDoesNotExist()
        rule.onNodeWithText("Browse all titles").performClick()
        assertTrue(cleared)
    }

    @Test fun homePositionSurvivesTabChangesAndRecreationButResetsForAnotherProfile() {
        val tab = mutableStateOf(NavigationTab.HOME)
        val owner = mutableStateOf("account:profile")
        val catalog = (0..23).map { media("$it", genres = listOf("Drama", "Comedy", "Action", "Crime", "Sci-Fi", "Animation", "Documentary")) }
        lateinit var list: LazyListState
        lateinit var scope: CoroutineScope
        val restoration = StateRestorationTester(rule)
        restoration.setContent { NetflixTheme {
            BrowseTabs(tab.value, owner.value) { destination ->
                if (destination == NavigationTab.HOME) {
                    list = rememberLazyListState()
                    scope = rememberCoroutineScope()
                    HomeScreen(profile, CategoryFilter.ALL, null, emptyList(), { false }, {}, {}, {}, catalogMedia = catalog, listState = list)
                } else Text("Search destination")
            }
        } }
        fun waitForRows() = rule.waitUntil(10_000) { ShadowLooper.idleMainLooper(); list.layoutInfo.totalItemsCount > 5 }
        waitForRows()
        rule.runOnIdle { scope.launch { list.scrollToItem(3) } }
        rule.waitForIdle()
        assertEquals(3, list.firstVisibleItemIndex)
        rule.runOnIdle { tab.value = NavigationTab.SEARCH }
        rule.waitForIdle()
        rule.runOnIdle { tab.value = NavigationTab.HOME }
        waitForRows()
        rule.waitForIdle()
        assertEquals(3, list.firstVisibleItemIndex)
        restoration.emulateSavedInstanceStateRestore()
        waitForRows()
        rule.waitForIdle()
        assertEquals(3, list.firstVisibleItemIndex)
        rule.runOnIdle { owner.value = "account:other-profile" }
        waitForRows()
        rule.waitForIdle()
        assertEquals(0, list.firstVisibleItemIndex)
    }

    @Test fun emptyPersonalListsNeverShowTitlesFromTheGlobalCatalog() {
        CatalogData.allMedia = listOf(media("unrelated"))
        rule.setContent { NetflixTheme {
            MyNetflixScreen(profile, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                emptyList(), null, false, {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})
        } }
        rule.onNodeWithTag("my_list_empty").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("liked_media_row").assertDoesNotExist()
        rule.onAllNodesWithContentDescription("Title unrelated").assertCountEquals(0)
    }

    @Test fun posterRowsCanDisplayAMovieAndSeriesWithTheSameNumericId() {
        val items = listOf(media("42"), media("42", MediaType.TV_SHOW))
        rule.setContent { NetflixTheme { MediaSectionRow("Titles", items, onMediaClick = {}) } }
        rule.onAllNodesWithTag("media_card_42").assertCountEquals(2)
    }

    @Test fun emptyClipsDoesNotReadTheGlobalCatalog() {
        CatalogData.allMedia = listOf(media("unrelated"))
        rule.setContent { NetflixTheme { ClipsScreen(isActive = false) } }
        rule.onNodeWithText("No clips are available for this profile yet.").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Title unrelated").assertCountEquals(0)
    }

    @Test fun systemBackClosesSubscriptionInsteadOfNavigatingTheUnderlyingTab() {
        var closes = 0
        lateinit var dispatcher: androidx.activity.OnBackPressedDispatcher
        rule.setContent { NetflixTheme {
            dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SubscriptionSheet(UserSubscription(), "", {}, { closes++ })
        } }
        rule.runOnIdle { dispatcher.onBackPressed() }
        assertEquals(1, closes)
    }
}
