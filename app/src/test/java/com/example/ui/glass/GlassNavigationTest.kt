package com.example.ui.glass

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.model.UserProfile
import com.example.ui.components.NetflixBottomNav
import com.example.ui.components.NetflixTopBar
import com.example.ui.theme.NetflixTheme
import com.example.ui.viewmodel.CategoryFilter
import com.example.ui.viewmodel.NavigationTab
import com.github.takahirom.roborazzi.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h900dp-mdpi", application = Application::class)
class GlassNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun capture(tag: String, name: String): File {
        val file = File("build/reports/glass-review/$name.png").apply { parentFile!!.mkdirs() }
        rule.onNodeWithTag(tag).captureRoboImage(file.path, RoborazziOptions(taskType = RoborazziTaskType.Record))
        return file
    }
    private fun scene(width: Int, frosted: Boolean, fontScale: Float = 1f, clips: Boolean = true) {
        rule.setContent {
            var selected by remember { mutableStateOf(NavigationTab.HOME) }
            val theme = remember(frosted) { GlassThemeState(frosted) { Color(0xFF5070A0) } }
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale), LocalGlassTheme provides theme) {
                NetflixTheme {
                    Box(Modifier.width(width.dp).height(760.dp).testTag("glass_scene")) {
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3176BA), Color(0xFFC76568), Color(0xFF203020)))))
                        NetflixTopBar(UserProfile("p", "Alex"), CategoryFilter.ALL, null, { _, _ -> }, {}, {}, {})
                        NetflixBottomNav(selected, UserProfile("p", "Alex"), { selected = it },
                            Modifier.align(Alignment.BottomCenter), isClipsAllowed = clips)
                    }
                }
            }
        }
        rule.waitForIdle()
    }
    @Test fun frostedMaterialKeepsCompactBrandingSeparateProfileAndWorkingTabs() {
        scene(412, true)
        val brand = rule.onNodeWithTag("top_bar_brand_glass").fetchSemanticsNode().boundsInRoot
        assertTrue("Home branding must wrap its contents", brand.width < 165f)
        val pill = rule.onNodeWithTag("nav_main_glass_pill").fetchSemanticsNode().boundsInRoot
        val circle = rule.onNodeWithTag("nav_profile_glass_circle").fetchSemanticsNode().boundsInRoot
        assertTrue(circle.left > pill.right)
        assertEquals(circle.width, circle.height, .5f)
        for (tab in listOf("clips", "search", "my_netflix", "home"))
            rule.onNodeWithTag("nav_tab_$tab").performClick().assertIsSelected()
        capture("glass_scene", "frosted-412")
    }
    @Test fun normalThemeKeepsTheSameSplitLayoutOnSmallPhones() {
        scene(320, false)
        rule.onNodeWithTag("nav_tab_my_netflix").assertIsDisplayed().performClick().assertIsSelected()
        rule.onNodeWithTag("top_bar_download_button").assertIsDisplayed()
        rule.onNodeWithTag("top_bar_notifications_button").assertIsDisplayed()
        capture("glass_scene", "normal-320")
    }
    @Test fun largeFontsAndTabletWidthsKeepTargetsSeparateAndClipsEntitlementIntact() {
        scene(600, true, fontScale = 1.8f, clips = false)
        rule.onNodeWithTag("nav_tab_clips").assertDoesNotExist()
        val home = rule.onNodeWithTag("nav_tab_home").fetchSemanticsNode().boundsInRoot
        val search = rule.onNodeWithTag("nav_tab_search").fetchSemanticsNode().boundsInRoot
        val profile = rule.onNodeWithTag("nav_tab_my_netflix").fetchSemanticsNode().boundsInRoot
        assertTrue(home.height >= 48 && search.height >= 48 && profile.height >= 48)
        assertTrue(home.right <= search.left && search.right < profile.left)
        rule.onNodeWithTag("nav_tab_search").performClick().assertIsSelected()
    }
    @Test fun whiteImageryAndTheBrightestTintStillKeepSmallLabelsReadable() {
        rule.setContent {
            CompositionLocalProvider(LocalGlassTheme provides remember { GlassThemeState(true) { Color.White } }) {
                Box(Modifier.size(320.dp, 120.dp).background(Color.White)) {
                    GlassPanel(Modifier.size(300.dp, 64.dp).align(Alignment.Center).testTag("contrast_panel")) {
                        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .14f)))
                    }
                }
            }
        }
        rule.waitForIdle()
        val file = capture("contrast_panel", "contrast-white")
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.path)
        val background = androidx.core.graphics.ColorUtils.compositeColors(
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2), android.graphics.Color.WHITE)
        assertTrue("Small white labels require AA contrast", androidx.core.graphics.ColorUtils.calculateContrast(android.graphics.Color.WHITE, background) >= 4.5)
        bitmap.recycle()
    }
    @Test fun paletteChangesRedrawMaterialWithoutRecomposingItsContent() {
        val ambient = mutableStateOf(Color.Blue)
        var compositions = 0
        rule.setContent {
            val theme = remember { GlassThemeState(true) { ambient.value } }
            CompositionLocalProvider(LocalGlassTheme provides theme) {
                GlassPanel(Modifier.size(300.dp, 64.dp).testTag("palette_panel")) {
                    SideEffect { compositions++ }
                }
            }
        }
        rule.waitForIdle()
        val initial = compositions
        val first = capture("palette_panel", "palette-blue").readBytes()
        rule.runOnIdle { ambient.value = Color.Red }
        rule.waitForIdle()
        val second = capture("palette_panel", "palette-red").readBytes()
        assertEquals("Tint updates belong to drawing, not content composition", initial, compositions)
        assertFalse("The frosted tint must actually follow the palette", first.contentEquals(second))
    }
}
