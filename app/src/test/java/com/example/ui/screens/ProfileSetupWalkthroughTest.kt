package com.example.ui.screens

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.model.UserProfile
import com.example.ui.theme.NetflixTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w393dp-h852dp-mdpi",sdk=[34],application=Application::class)
class ProfileSetupWalkthroughTest {
    @get:Rule val rule=createComposeRule()
    @Test fun completeSetupValidatesPinAndKeepsEveryPreference() {
        var saved:UserProfile?=null
        var iconOpens=0
        rule.setContent { NetflixTheme {
            ProfileSetupWalkthroughScreen(UserProfile("new","",avatarUrl=null),{saved=it},{iconOpens++},{})
        } }
        rule.onNodeWithTag("setup_continue").performClick()
        rule.onNodeWithText("Enter a profile name.").assertExists()
        rule.onNodeWithTag("setup_name").performTextInput("Chris")
        rule.onNodeWithTag("setup_avatar").performClick();assertEquals(1,iconOpens)
        rule.onNode(isToggleable()).performClick()
        capture("identity")
        rule.onNodeWithTag("setup_continue").performClick();rule.waitForIdle()
        rule.onNodeWithText("Swahili").performClick()
        rule.onNodeWithTag("setup_pin").performTextInput("12")
        rule.onNodeWithTag("setup_continue").performClick()
        rule.onNodeWithText("Use exactly four digits for your PIN.").assertExists()
        rule.onNodeWithTag("setup_pin").performTextClearance()
        rule.onNodeWithTag("setup_pin").performTextInput("1234")
        capture("preferences")
        rule.onNodeWithTag("setup_continue").performClick();rule.waitForIdle()
        rule.onNodeWithText("Family").performClick();rule.onNodeWithText("Comedy").performClick()
        capture("taste")
        rule.onNodeWithTag("setup_continue").performClick();rule.waitForIdle()
        val profile=requireNotNull(saved)
        assertEquals("Chris",profile.name);assertEquals("Swahili",profile.language)
        assertTrue(profile.isKids);assertEquals(7,profile.maxAge);assertEquals("1234",profile.pin)
        assertEquals(setOf("Family","Comedy"),profile.favoriteGenres.toSet())
        assertTrue(profile.autoplayNext && profile.autoplayPreviews)
    }
    @Test fun backKeepsDraftAndCancelDoesNotCreateAProfile() {
        var saved=0;var cancelled=0
        rule.setContent { NetflixTheme {
            ProfileSetupWalkthroughScreen(UserProfile("new","Home",avatarUrl=null),{saved++},{},{cancelled++})
        } }
        rule.onNodeWithTag("setup_name").performTextClearance();rule.onNodeWithTag("setup_name").performTextInput("Edited")
        rule.onNodeWithTag("setup_continue").performClick();rule.waitForIdle()
        rule.onNodeWithContentDescription("Back").performClick();rule.waitForIdle()
        rule.onNodeWithTag("setup_name").assertTextContains("Edited")
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1,cancelled);assertEquals(0,saved)
    }
    @Test fun pendingSaveDisablesSubmissionAndKeepsTheServerErrorVisible() {
        rule.setContent { NetflixTheme {
            ProfileSetupWalkthroughScreen(UserProfile("new","Home",avatarUrl=null),{},{},{},true,"Try again")
        } }
        rule.onNodeWithTag("setup_continue").assertIsNotEnabled()
        rule.onNodeWithTag("setup_name").assertIsNotEnabled()
        rule.onNodeWithText("Try again").assertExists()
    }
    private fun capture(name:String) {
        val dir=File("build/reports/profile-walkthrough").apply{mkdirs()}
        rule.onRoot().captureRoboImage(File(dir,"$name.png").path)
    }
}
