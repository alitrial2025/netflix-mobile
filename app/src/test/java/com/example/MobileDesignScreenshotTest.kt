package com.example

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.data.local.WatchProgressEntity
import com.example.data.model.AvatarType
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.UserProfile
import com.example.data.model.UserSubscription
import com.example.ui.components.NetflixBottomNav
import com.example.ui.components.NetflixTopBar
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProfilePickerSheet
import com.example.ui.theme.NetflixTheme
import com.example.ui.viewmodel.CategoryFilter
import com.example.ui.viewmodel.NavigationTab
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h895dp-mdpi", sdk = [34], application = Application::class)
class MobileDesignScreenshotTest {
    @get:Rule val rule = createComposeRule()
    @Before fun freshImageLoaderForRobolectricApplication() {
        // Robolectric replaces the Application between tests; Coil's singleton otherwise
        // retains the previous sandbox's main-thread dispatcher and stalls new requests.
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(context).build())
    }
    private val names = listOf("Jeff", "Brian", "Mom", "Home", "Kids")
    private val colors = listOf(0xFF454545L, 0xFFE2B80CL, 0xFF006477L, 0xFFE50914L, 0xFF00A6BAL)
    private val profiles = names.mapIndexed { index, name -> UserProfile(id = "p$index", name = name,
        avatarColorHex = colors[index], avatarType = if (index == 4) AvatarType.KIDS else AvatarType.SMILEY, isKids = index == 4) }
    private fun fixture(name: String): String {
        val directory = File("build/reports/test-fixtures").apply { mkdirs() }
        val file = File(directory, name)
        javaClass.getResourceAsStream("/artwork/$name")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        return file.toURI().toString()
    }
    private val show by lazy { MediaItem("42", "Squid Game", MediaType.TV_SHOW, "", "", 96, "16+", 2026, "3 Seasons",
        top10Rank = 1, genres = listOf("Violent", "Suspenseful", "Thriller", "Korean"), cast = emptyList(), director = "",
        posterUrl = fixture("squid-game.jpg"), logoUrl = fixture("squid-game-logo.png"), isTrending = true) }
    private val membership = UserSubscription(planId = "plan_premium", status = "ACTIVE", expiresAt = Long.MAX_VALUE)

    private fun capture(name: String) {
        val directory = File(System.getProperty("screenshot.output", "build/reports/design-screenshots"))
        directory.mkdirs()
        rule.onRoot().captureRoboImage(File(directory, "$name.png").path)
    }

    @Test fun profilePickerKeepsEditAndProfileSelectionAccessible() {
        var selected: UserProfile? = null
        val featured = show.copy(title = "Umthetho", posterUrl = fixture("umthetho.jpg"), logoUrl = fixture("umthetho-logo.png"))
        rule.setContent {
            NetflixTheme {
                ProfilePickerSheet(profiles, profiles[3], featured,
                    membership, onSelectProfile = { selected = it }, onEditProfile = {}, onDismiss = {})
            }
        }
        rule.onNodeWithText("Choose your profile").assertExists()
        try {
            rule.waitUntil(10_000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                com.example.ui.components.posterColorsLoaded(featured)
            }
        } catch (failure: Exception) {
            org.robolectric.shadows.ShadowLog.getLogsForTag("PosterColors").forEach { log ->
                System.err.println(log.msg)
                log.throwable?.printStackTrace()
            }
            throw failure
        }
        rule.mainClock.advanceTimeBy(600)
        capture("profile-picker")
        rule.onNodeWithText("Brian").performClick()
        assertTrue(selected?.name == "Brian")
        rule.onNodeWithText("Edit").performClick()
        rule.onNodeWithText("Tap a profile to edit").assertExists()
    }

    @Test fun homeReferenceLayout() {
        rule.setContent {
            NetflixTheme {
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(profiles[3], CategoryFilter.ALL, null,
                        listOf(show to WatchProgressEntity(profileId = "p3", mediaId = "42", positionSeconds = 60, totalSeconds = 1200)),
                        isWatchlistContains = { false }, onMediaClick = {}, onPlayClick = {}, onWatchlistToggle = {},
                        catalogMedia = listOf(show), userSubscription = membership)
                    NetflixTopBar(profiles[3], CategoryFilter.ALL, null, { _, _ -> }, {}, {}, {}, unreadNotificationCount = 5)
                    NetflixBottomNav(NavigationTab.HOME, profiles[3], {}, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        rule.onNodeWithText("Play").assertExists()
        rule.onNodeWithText("My List").assertExists()
        rule.waitUntil(10_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            com.example.ui.components.posterColorsLoaded(show)
        }
        rule.mainClock.advanceTimeBy(600)
        capture("home")
    }

    @Test fun offlineEmptyCatalogOffersDownloadsInsteadOfEndlessSpinner() {
        var downloadsOpened = false
        rule.setContent {
            NetflixTheme {
                HomeScreen(profiles[3], CategoryFilter.ALL, null, emptyList(), { false }, {}, {}, {},
                    onOpenDownloads = { downloadsOpened = true })
            }
        }
        rule.onNodeWithText("Open downloads").performClick()
        assertTrue(downloadsOpened)
        capture("offline-home")
    }
}
