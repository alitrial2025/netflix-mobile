package com.example

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.components.MembershipDialog
import com.example.ui.theme.NetflixTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h895dp-mdpi", sdk = [34], application = Application::class)
class MembershipDialogTest {
    @get:Rule val rule = createComposeRule()
    @Test fun unpaidViewerExplicitlyChoosesTrailerOrSubscription() {
        var subscriptions = 0; var trailers = 0
        rule.setContent { NetflixTheme {
            MembershipDialog("Lanterns", "Subscribe to watch the full show.", { subscriptions++ }, { trailers++ }, {})
        } }
        rule.onNodeWithText("Subscribe to a plan").assertIsDisplayed()
        rule.onNodeWithText("Continue watching trailer").assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, subscriptions); assertEquals(0, trailers) }
        rule.onNodeWithText("Continue watching trailer").performClick()
        rule.runOnIdle { assertEquals(0, subscriptions); assertEquals(1, trailers) }
        rule.onNodeWithText("Subscribe to a plan").performClick()
        rule.runOnIdle { assertEquals(1, subscriptions); assertEquals(1, trailers) }
    }
}
