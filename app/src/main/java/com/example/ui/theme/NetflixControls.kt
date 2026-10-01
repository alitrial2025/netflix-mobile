package com.example.ui.theme

import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Shared switch treatment for settings, profiles and Smart Downloads. */
@Composable
fun netflixProSwitchColors() = SwitchDefaults.colors(
    checkedTrackColor = Color(0xFF0071EB),
    checkedThumbColor = Color.White,
    checkedBorderColor = Color.Transparent,
    uncheckedTrackColor = Color(0xFF606060),
    uncheckedThumbColor = Color.LightGray,
    uncheckedBorderColor = Color.Transparent
)
