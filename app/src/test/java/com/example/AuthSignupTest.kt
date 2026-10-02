package com.example

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.NetflixTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h895dp-mdpi", sdk = [34], application = Application::class)
class AuthSignupTest {
    @get:Rule val rule = createComposeRule()

    @Test fun signupPreventsDuplicateRequestsAndAllowsRetryAfterError() {
        var submissions = 0
        var submittedEmail = ""
        var failure: ((String) -> Unit)? = null
        var success: (() -> Unit)? = null
        rule.setContent {
            NetflixTheme {
                AuthScreen(null,
                    onSignIn = { _, _, _, _ -> fail("Unexpected sign in") },
                    onSignUp = { email, _, onSuccess, onError ->
                        submissions++; submittedEmail = email; failure = onError; success = onSuccess
                    }, onSignOut = {}, onClose = {}, onOpenTvPair = {})
            }
        }
        rule.onNodeWithTag("onboarding_get_started_btn").performClick()
        rule.onNodeWithTag("auth_email_field").performTextInput("  new@example.com  ")
        rule.onNodeWithTag("auth_password_field").performTextInput("secret123")
        rule.onNodeWithTag("auth_confirm_password_field").performTextInput("secret123")
        rule.onNodeWithTag("auth_submit_button").performScrollTo().performClick()
        rule.onNodeWithTag("auth_submit_button").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("auth_email_field").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(1, submissions); assertEquals("new@example.com", submittedEmail); failure!!("Network unavailable") }
        rule.onNodeWithText("Network unavailable").assertExists()
        rule.onNodeWithTag("auth_submit_button").assertIsEnabled().performScrollTo().performClick()
        rule.runOnIdle { assertEquals(2, submissions); success!!() }
        rule.onNodeWithTag("auth_submit_button").assertIsEnabled()
    }

    @Test fun mismatchedPasswordsNeverCreateAnAccount() {
        var submissions = 0
        rule.setContent {
            NetflixTheme {
                AuthScreen(null,
                    onSignIn = { _, _, _, _ -> submissions++ },
                    onSignUp = { _, _, _, _ -> submissions++ },
                    onSignOut = {}, onClose = {}, onOpenTvPair = {})
            }
        }
        rule.onNodeWithTag("onboarding_get_started_btn").performClick()
        rule.onNodeWithTag("auth_email_field").performTextInput("new@example.com")
        rule.onNodeWithTag("auth_password_field").performTextInput("secret123")
        rule.onNodeWithTag("auth_confirm_password_field").performTextInput("different")
        rule.onNodeWithTag("auth_submit_button").performScrollTo().performClick()
        rule.onNodeWithText("Passwords do not match.").assertExists()
        rule.runOnIdle { assertEquals(0, submissions) }
    }
}
