package com.example.ui.screens

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.model.AvatarUrls
import com.example.data.model.ProfileIconCatalog
import com.example.ui.theme.NetflixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-mdpi", sdk = [34], application = Application::class)
class AvatarPickerInteractionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun themedPillsSelectTheCorrespondingArtworkAndBackKeepsTheDraft() {
        val classic = ProfileIconCatalog.categories.first { it.id == "classic_icons" }
        val wednesday = ProfileIconCatalog.categories.first { it.id == "wednesday" }
        val picked = mutableListOf<String>()
        var dismissed = false
        rule.setContent { NetflixTheme {
            AvatarPickerSheet(classic.icons.first(), { picked += it }, { dismissed = true })
        } }
        rule.onNodeWithTag("avatar_category_classic_icons").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithContentDescription("Current profile icon").assertIsSelected()
        rule.onNodeWithContentDescription("Classic Icons icon 2").performClick()
        assertEquals(listOf(classic.icons[1]), picked)

        rule.onNodeWithTag("avatar_categories").performScrollToIndex(
            ProfileIconCatalog.categories.indexOf(wednesday) + 1)
        rule.onNodeWithTag("avatar_category_wednesday").performClick().assertIsSelected()
        rule.onNodeWithContentDescription("Wednesday icon 1").performClick()
        assertEquals(wednesday.icons.first(), picked.last())
        rule.onNodeWithTag("avatar_picker_back_button").performClick()
        assertEquals(true, dismissed)
        assertEquals(2, picked.size)
    }

    @Test fun allIconsRetainsExistingMobileAvatarsAndMarksTheCurrentSelection() {
        val legacy = AvatarUrls.urls.first()
        rule.setContent { NetflixTheme { AvatarPickerSheet(legacy, {}, {}) } }
        rule.onNodeWithTag("avatar_category_all").assertIsSelected()
        rule.onNodeWithTag("avatar_icon_grid").performScrollToKey(legacy)
        rule.onNodeWithContentDescription("Current profile icon").assertIsSelected()
    }
}
