package com.example.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.model.ProfilePin
import com.example.data.model.UserProfile
import com.example.data.model.UserSubscription
import com.example.ui.components.NetflixBottomNav
import com.example.ui.theme.NetflixTheme
import com.example.ui.viewmodel.NavigationTab
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h895dp-mdpi", sdk = [34], application = Application::class)
class MobilePinAndClipsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun correctHashedPinUnlocksOnTheFourthDigitWithoutShowingAnError() {
        val profile = UserProfile("locked", "Alex", pin = ProfilePin.hash("2468"))
        var selections = 0
        rule.setContent { NetflixTheme {
            ProfilePickerSheet(listOf(profile), profile, onSelectProfile = { selections++ }, onDismiss = {})
        } }
        rule.onNodeWithText("Alex").performClick()
        rule.onNodeWithTag("profile_pin_input").performTextReplacement("246")
        rule.onNodeWithTag("profile_pin_unlock").assertIsNotEnabled()
        rule.onNodeWithText("Incorrect PIN. Please try again.").assertDoesNotExist()
        rule.onNodeWithTag("profile_pin_input").performTextReplacement("2468")
        rule.onNodeWithTag("profile_pin_input").assertDoesNotExist()
        rule.onNodeWithText("Incorrect PIN. Please try again.").assertDoesNotExist()
        assertEquals(1, selections)
    }

    @Test fun wrongPinStaysLockedAndEditingItUnlocksALegacyProfile() {
        val profile = UserProfile("legacy", "Alex", pin = "1357")
        var selections = 0
        rule.setContent { NetflixTheme {
            ProfilePickerSheet(listOf(profile), profile, onSelectProfile = { selections++ }, onDismiss = {})
        } }
        rule.onNodeWithText("Alex").performClick()
        rule.onNodeWithTag("profile_pin_input").performTextReplacement("1111")
        rule.onNodeWithText("Incorrect PIN. Please try again.").assertExists()
        rule.onNodeWithTag("profile_pin_unlock").performClick()
        assertEquals(0, selections)
        rule.onNodeWithTag("profile_pin_input").performTextReplacement("1357")
        rule.onNodeWithText("Incorrect PIN. Please try again.").assertDoesNotExist()
        assertEquals(1, selections)
    }

    @Test fun clipsSitsBetweenHomeAndSearchOnlyWhilePremiumIsActive() {
        val membership = mutableStateOf(UserSubscription(planId = "plan_premium", status = "ACTIVE", expiresAt = Long.MAX_VALUE))
        var selected: NavigationTab? = null
        rule.setContent { NetflixTheme {
            NetflixBottomNav(NavigationTab.HOME, UserProfile("p", "Alex"), { selected = it },
                isClipsAllowed = membership.value.isClipsAllowed)
        } }
        fun left(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.left
        assertTrue(left("nav_tab_home") < left("nav_tab_clips"))
        assertTrue(left("nav_tab_clips") < left("nav_tab_search"))
        rule.onNodeWithTag("nav_tab_clips").performClick()
        assertEquals(NavigationTab.CLIPS, selected)
        for (plan in listOf("plan_mobile", "plan_basic", "plan_standard", "plan_guest")) {
            rule.runOnIdle { membership.value = membership.value.copy(planId = plan) }
            rule.onNodeWithTag("nav_tab_clips").assertDoesNotExist()
            rule.onNodeWithTag("nav_tab_home").assertExists()
            rule.onNodeWithTag("nav_tab_search").assertExists()
        }
        rule.runOnIdle { membership.value = membership.value.copy(planId = "plan_premium", expiresAt = 0) }
        rule.onNodeWithTag("nav_tab_clips").assertDoesNotExist()
    }
}
