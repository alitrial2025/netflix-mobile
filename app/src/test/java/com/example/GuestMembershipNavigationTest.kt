package com.example

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.model.UserProfile
import com.example.data.model.UserSubscription
import com.example.ui.components.DownloadMembershipDialog
import com.example.ui.screens.MyNetflixScreen
import com.example.ui.theme.NetflixTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h895dp-mdpi", sdk = [34], application = Application::class)
class GuestMembershipNavigationTest {
    @get:Rule val rule = createComposeRule()
    private fun showAccount(signedIn: Boolean, onAuth: () -> Unit, onPlans: () -> Unit) {
        rule.setContent { NetflixTheme {
            MyNetflixScreen(activeProfile = UserProfile("home", "Home"), watchlist = emptyList(),
                downloads = emptyList(), likedMedia = emptyList(), watchedTrailers = emptyList(),
                notifications = emptyList(), continueWatchingList = emptyList(), watchHistory = emptyList(),
                reminders = emptyList(), connectedCastDevice = null, smartDownloadsEnabled = false,
                onSwitchProfileClick = {}, onMediaClick = {}, onPlayClick = {}, onPlayTrailerClick = {},
                onOpenDownloads = {}, onDeleteDownload = {}, onClearAllDownloads = {},
                onWatchlistToggle = {}, onReminderToggle = {}, onContinueWatchingOptionsClick = { _, _ -> },
                onHistoryClick = {}, onOpenNotifications = {}, onOpenSettings = {}, onOpenCast = {},
                onNavigateToSearch = {}, onShowToast = {}, userSubscription = UserSubscription(),
                isAuthenticated = signedIn, onOpenAuth = onAuth, onOpenSubscription = onPlans)
        } }
    }
    @Test fun signedInGuestSeesPlansRatherThanAnotherSignIn() {
        var auth = 0; var plans = 0
        showAccount(true, { auth++ }, { plans++ })
        rule.onNodeWithText("Sign In to NetflixPro").assertDoesNotExist()
        rule.onNodeWithText("View Plans").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(0, auth); assertEquals(1, plans) }
    }
    @Test fun signedOutViewerCanStillSignIn() {
        var auth = 0; var plans = 0
        showAccount(false, { auth++ }, { plans++ })
        rule.onNodeWithText("Sign In").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, auth); assertEquals(0, plans) }
    }
    @Test fun downloadUpgradePromptDoesNotNavigateUntilViewerChoosesPlans() {
        var plans = 0; var dismissed = 0
        rule.setContent { NetflixTheme { DownloadMembershipDialog({ plans++ }, { dismissed++ }) } }
        rule.onNodeWithText("Upgrade to download").assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, plans) }
        rule.onNodeWithText("Not now").performClick()
        rule.runOnIdle { assertEquals(1, dismissed); assertEquals(0, plans) }
        rule.onNodeWithText("View plans").performClick()
        rule.runOnIdle { assertEquals(1, plans) }
    }
}
