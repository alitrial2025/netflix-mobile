package com.example

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.NetflixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h895dp-mdpi", sdk = [34], application = Application::class)
class SplashReadinessTest {
    @get:Rule val rule = createComposeRule()

    @Test fun animationCannotRevealApplicationBeforeEntryAdmissionCompletes() {
        val ready = mutableStateOf(false)
        var completions = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            NetflixTheme { SplashScreen(isWarmupFinished = ready.value, onSplashComplete = { completions++ }) }
        }
        rule.mainClock.advanceTimeBy(10_000)
        rule.runOnIdle { assertEquals(0, completions); ready.value = true }
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnIdle { assertEquals(1, completions) }
        rule.mainClock.advanceTimeBy(5_000)
        rule.runOnIdle { assertEquals(1, completions) }
    }

    @Test fun failedEntryOffersRetryWithoutCompletingOrGrantingAccess() {
        val ready = mutableStateOf(false)
        val error = mutableStateOf<String?>("Reconnect to verify your membership.")
        var retries = 0
        var completions = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            NetflixTheme {
                SplashScreen(
                    isWarmupFinished = ready.value,
                    startupError = error.value,
                    onRetryStartup = { retries++; error.value = null },
                    onSplashComplete = { completions++ }
                )
            }
        }
        rule.mainClock.advanceTimeBy(10_000)
        rule.onNodeWithText("Reconnect to verify your membership.").assertIsDisplayed()
        rule.onNodeWithText("Try again").performClick()
        rule.mainClock.advanceTimeBy(5_000)
        rule.runOnIdle { assertEquals(1, retries); assertEquals(0, completions); ready.value = true }
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnIdle { assertEquals(1, completions) }
    }
}
