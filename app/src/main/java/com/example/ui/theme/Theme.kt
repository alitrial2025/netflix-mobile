package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NetflixColorScheme = darkColorScheme(
    primary = NetflixRed,
    onPrimary = NetflixWhite,
    primaryContainer = NetflixRedDark,
    onPrimaryContainer = NetflixWhite,
    secondary = NetflixWhite,
    onSecondary = NetflixBlack,
    secondaryContainer = NetflixMediumGray,
    onSecondaryContainer = NetflixWhite,
    tertiary = NetflixGold,
    onTertiary = NetflixBlack,
    background = NetflixBlack,
    onBackground = NetflixWhite,
    surface = NetflixDarkGray,
    onSurface = NetflixWhite,
    surfaceVariant = NetflixCardBg,
    onSurfaceVariant = NetflixOffWhite,
    outline = NetflixBorderGray,
    surfaceContainer = NetflixMediumGray,
    surfaceContainerHigh = NetflixCardBg
)

@Composable
fun NetflixTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NetflixColorScheme,
        typography = Typography,
        content = content
    )
}
