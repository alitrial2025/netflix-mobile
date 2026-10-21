package com.example.ui.theme

import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Shared switch treatment for settings, profiles and Smart Downloads. */
@Composable
fun netflixProSwitchColors(): androidx.compose.material3.SwitchColors {
    val platform = LocalPlatformDesign.current
    return SwitchDefaults.colors(
    checkedTrackColor = if (platform.originalLayout) Color(0xFF0071EB) else platform.accent,
    checkedThumbColor = if (platform.originalLayout) Color.White else platform.onAccent,
    checkedBorderColor = Color.Transparent,
    uncheckedTrackColor = Color(0xFF606060),
    uncheckedThumbColor = Color.LightGray,
    uncheckedBorderColor = Color.Transparent
)

}
