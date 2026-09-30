package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.CatalogData
import com.example.ui.screens.ProfilePickerSheet
import com.example.ui.theme.NetflixTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class ProfilePickerScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun profile_picker_screenshot() {
    val sampleProfiles = listOf(
        com.example.data.model.UserProfile(id = "1", name = "Alex", avatarColorHex = 0xFFE50914L, isKids = false),
        com.example.data.model.UserProfile(id = "2", name = "Kids", avatarColorHex = 0xFFF5B300L, isKids = true, avatarType = com.example.data.model.AvatarType.KIDS)
    )
    composeTestRule.setContent {
        NetflixTheme {
            ProfilePickerSheet(
                profiles = sampleProfiles,
                activeProfile = sampleProfiles.first(),
                featuredMedia = null,
                onSelectProfile = {},
                onDismiss = {}
            )
        }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/profile_picker.png")
  }
}
