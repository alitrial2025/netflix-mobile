package com.example.ui.screens

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.model.UserProfile
import com.example.data.model.UserSubscription
import com.example.ui.theme.NetflixTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h895dp-mdpi", sdk = [34], application = Application::class)
class SettingsInteractionTest {
    @get:Rule val rule = createComposeRule()
    private fun settings(events: MutableList<String>, paid: Boolean = true, hasActiveDownloads: Boolean = false, usedMb: Int = 50) {
        rule.setContent { NetflixTheme {
            NetflixSettingsScreen(UserProfile("p", "Alex"), true, true, true, true, true, true,
                "Automatic (Balanced)", false, null, usedMb,
                onClose = { events += "close" }, onToggleSmartDownloads = { events += "smart:$it" },
                onToggleWifiOnly = { events += "wifi:$it" }, onToggleHighQuality = { events += "quality:$it" },
                onToggleAutoPlayNext = { events += "next:$it" }, onToggleAutoPlayPreviews = { events += "previews:$it" },
                onToggleSpatialAudio = { events += "spatial:$it" }, onSetCellularData = { events += "cellular:$it" },
                onRunDiagnosticTest = { events += "diagnostic" }, onClearAllDownloads = { events += "delete" },
                onSwitchProfile = { events += "switch" }, onOpenTvPair = { events += "tv" },
                currentEmail = "member@example.test", userSubscription = UserSubscription(planId = if (paid) "plan_premium" else "plan_basic",
                    status = "ACTIVE", expiresAt = Long.MAX_VALUE),
                onOpenSubscription = { events += "membership" }, onEditProfile = { events += "edit" },
                onOpenSmartDownloads = { events += "downloads" }, onSignOut = { events += "signout" },
                onResetPassword = { events += "reset" }, hasActiveDownloads = hasActiveDownloads)
        } }
    }

    @Test fun navigationClosesSettingsAndOpensTheRespectiveDestination() {
        val events = mutableListOf<String>()
        settings(events)
        File("build/outputs/settings-previews").mkdirs()
        rule.onRoot().captureRoboImage("build/outputs/settings-previews/settings-top.png")
        for ((tag, destination) in listOf("settings_edit_profile" to "edit", "settings_switch_profile" to "switch",
            "settings_membership" to "membership", "pair_tv_settings_btn" to "tv", "settings_smart_downloads" to "downloads")) {
            events.clear()
            rule.onNodeWithTag(tag).performScrollTo().performClick()
            assertEquals(listOf("close", destination), events)
        }
    }

    @Test fun cellularQualityAndAllPlaybackDownloadSwitchesHaveCallbacks() {
        val events = mutableListOf<String>()
        settings(events)
        rule.onNodeWithTag("settings_cellular_data").performScrollTo().performClick()
        rule.onNodeWithTag("settings_cellular_SAVE_DATA").performClick()
        assertEquals("cellular:Save Data", events.last())
        rule.onNodeWithTag("settings_video_quality").performScrollTo().performClick()
        rule.onNodeWithTag("settings_quality_standard").performClick()
        assertEquals("quality:false", events.last())
        for ((title, kind) in listOf("Auto-Play Next Episode" to "next", "Auto-Play Previews" to "previews",
            "Spatial Audio" to "spatial", "Wi-Fi Only Downloads" to "wifi", "Download Next Episode" to "smart")) {
            rule.onNodeWithTag("settings_toggle_$title").performScrollTo().performClick()
            assertEquals("$kind:false", events.last())
        }
        rule.onRoot().captureRoboImage("build/outputs/settings-previews/settings-downloads.png")
        rule.onNodeWithTag("settings_network_test").performScrollTo().performClick()
        assertEquals("diagnostic", events.last())
    }

    @Test fun deletingActiveWorkAndSigningOutRequireConfirmationAndResetPasswordIsConnected() {
        val events = mutableListOf<String>()
        settings(events, hasActiveDownloads = true, usedMb = 0)
        rule.onNodeWithTag("settings_delete_downloads").performScrollTo().performClick()
        assertFalse(events.contains("delete"))
        rule.onNodeWithTag("settings_confirm_delete").performClick()
        assertEquals("delete", events.last())
        rule.onNodeWithTag("settings_account").performScrollTo().performClick()
        rule.onNodeWithTag("settings_reset_password").performClick()
        assertEquals("reset", events.last())
        rule.onNodeWithTag("settings_account").performClick()
        rule.onNodeWithTag("settings_sign_out").performClick()
        assertFalse(events.contains("signout"))
        rule.onNodeWithTag("settings_confirm_sign_out").performClick()
        assertEquals(listOf("close", "signout"), events.takeLast(2))
    }

    @Test fun basicMembershipCannotEnablePremiumSpatialAudio() {
        val events = mutableListOf<String>()
        settings(events, paid = false)
        rule.onNodeWithTag("settings_toggle_Spatial Audio").performScrollTo().assertIsNotEnabled().assertIsOff()
        assertTrue(events.isEmpty())
    }
}
