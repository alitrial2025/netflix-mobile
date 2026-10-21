package com.example.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.data.model.HomeCategory
import com.example.data.model.PlatformAppearance
import com.example.ui.theme.platformDesign
import org.junit.Assert.*
import org.junit.Test

class PlatformDesignPolicyTest {
    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance(); val b = second.luminance()
        return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
    }
    @Test fun eachProviderHasAnIndependentAppearanceAndGenresCannotSwitchIt() {
        HomeCategory.services.forEach { category ->
            assertEquals(category.key, PlatformAppearance.fromCategory(category.label)?.key)
        }
        HomeCategory.genres.forEach { assertNull(PlatformAppearance.fromCategory(it.label)) }
        assertNull(PlatformAppearance.fromCategory(null))
        assertEquals(PlatformAppearance.ORIGINAL, PlatformAppearance.fromKey("unknown"))
    }
    @Test fun disablingPlatformsAlwaysResolvesToOriginalAndAllControlLabelsHaveContrast() {
        PlatformAppearance.entries.forEach { platform ->
            assertEquals(PlatformAppearance.ORIGINAL, PlatformAppearance.effective(false, platform))
            val design = platformDesign(platform)
            assertTrue("${platform.label} primary contrast", contrast(design.primary, design.onPrimary) >= 4.5f)
            assertTrue("${platform.label} accent contrast", contrast(design.accent, design.onAccent) >= 4.5f)
            assertTrue("${platform.label} body contrast", contrast(design.background, Color.White) >= 4.5f)
        }
    }
}
