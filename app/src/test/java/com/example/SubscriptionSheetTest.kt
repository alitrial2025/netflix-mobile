package com.example

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.model.UserSubscription
import com.example.ui.screens.SubscriptionSheet
import com.example.ui.theme.NetflixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h895dp-mdpi", sdk = [34], application = Application::class)
class SubscriptionSheetTest {
    @get:Rule val rule = createComposeRule()
    @Test fun suspendedAccountSeesSupportAndCannotOpenCheckoutWhileSignedOut() {
        val sub = UserSubscription(status = "SUSPENDED", planId = "plan_premium", planName = "Premium", expiresAt = 1_800_000_000_000L)
        rule.setContent { NetflixTheme { SubscriptionSheet(sub, "", {}, {}) } }
        rule.onNodeWithText("SUSPENDED").assertIsDisplayed()
        rule.onNodeWithText("Membership suspended • Contact support before paying").assertIsDisplayed()
        rule.onNodeWithText("1. Pay KES 1350 via Pay Sub").performScrollTo().assertIsNotEnabled()
    }
}
