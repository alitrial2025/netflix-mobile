package com.example.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.example.ui.components.HomeBackdrop
import com.example.ui.components.homeBackdropOffset
import com.example.ui.components.rememberPosterRequest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.example.ui.screens.HomeScreen
import com.example.ui.viewmodel.CategoryFilter
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.UserProfile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h895dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScrollTest {
    @get:Rule val rule = createComposeRule()

    @Test fun gradientContinuesAcrossHeroDisposalAndStopsInvalidatingBelowItsEnd() {
        val keys = listOf("hero", "continue", "trending", "originals")
        val heights = mapOf("hero" to 800, "continue" to 250, "trending" to 210)
        assertEquals(799f, homeBackdropOffset(0, 799, keys, heights, 1400f))
        assertEquals(800f, homeBackdropOffset(1, 0, keys, heights, 1400f))
        assertEquals(1050f, homeBackdropOffset(2, 0, keys, heights, 1400f))
        assertEquals(1400f, homeBackdropOffset(3, 900, keys, heights, 1400f))
        assertEquals(1400f, homeBackdropOffset(3, 0, keys, emptyMap(), 1400f))
    }

    @Test fun backdropPixelMotionDoesNotRecomposeOrRemeasureForeground() {
        val scroll = mutableFloatStateOf(0f)
        var compositions = 0
        var foregroundMeasures = 0
        rule.setContent {
            SideEffect { compositions++ }
            Box(Modifier.fillMaxSize()) {
                HomeBackdrop(androidx.compose.ui.graphics.Color.Red, androidx.compose.ui.graphics.Color.Blue,
                    1400f, { scroll.floatValue })
                Box(Modifier.fillMaxSize().layout { measurable, constraints ->
                    foregroundMeasures++
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                })
            }
        }
        rule.waitForIdle()
        val beforeCompositions = compositions
        val beforeDraws = foregroundMeasures
        assertTrue(beforeDraws > 0)
        repeat(20) { frame ->
            rule.runOnIdle { scroll.floatValue = (frame + 1) * 5f }
            rule.waitForIdle()
        }
        assertEquals(beforeCompositions, compositions)
        assertEquals(beforeDraws, foregroundMeasures)
    }

    @Test fun posterRequestIsDisplaySizedStableAndReusesLargerCachedImages() {
        val trigger = mutableFloatStateOf(0f)
        val seen = mutableListOf<ImageRequest>()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) {
                val request = rememberPosterRequest("https://image.example/poster.jpg", 118.dp, 168.dp)
                trigger.floatValue // Force parent recomposition, as unrelated root state can do.
                SideEffect { seen += request }
            }
        }
        rule.runOnIdle { trigger.floatValue = 1f }
        rule.waitForIdle()
        assertTrue(seen.size >= 2)
        assertSame(seen.first(), seen.last())
        assertEquals(Size(236, 336), runBlocking { seen.last().sizeResolver.size() })
        assertEquals(Precision.INEXACT, seen.last().precision)
    }

    @Test fun verticalRowDisposalPreservesHorizontalBrowsePosition() {
        lateinit var list: LazyListState
        lateinit var scope: CoroutineScope
        val catalog = (0..23).map { index ->
            MediaItem("media$index", "Title $index", MediaType.MOVIE, "", "", 96, "16+", 2026, "2h",
                top10Rank = if (index < 10) index + 1 else null, isTrending = true,
                genres = listOf("Action", "Crime", "Drama", "Comedy", "Sci-Fi", "Animation", "K-Dramas", "Documentary"),
                cast = emptyList(), director = "")
        }
        rule.setContent {
            list = rememberLazyListState()
            scope = rememberCoroutineScope()
            HomeScreen(UserProfile("profile", "Home"), CategoryFilter.ALL, null, emptyList(),
                { false }, {}, {}, {}, catalogMedia = catalog, listState = list)
        }
        rule.waitUntil(10_000) {
            // Section derivation completes on Default; deliver its result to Robolectric's paused Main looper.
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            list.layoutInfo.totalItemsCount > 9
        }
        rule.runOnIdle { scope.launch { list.scrollToItem(2) } }
        rule.waitForIdle()
        rule.onNodeWithTag("section_row_trending_now").performScrollToIndex(6)
        rule.onNodeWithTag("media_card_media11").assertIsDisplayed()
        rule.runOnIdle { scope.launch { list.scrollToItem(8) } }
        rule.waitForIdle()
        rule.runOnIdle { scope.launch { list.scrollToItem(2) } }
        rule.waitForIdle()
        rule.onNodeWithTag("media_card_media11").assertIsDisplayed()
    }
}
