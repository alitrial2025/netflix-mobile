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
    platform: com.example.data.model.PlatformAppearance = com.example.data.model.PlatformAppearance.ORIGINAL,
    content: @Composable () -> Unit
) {
    val design = platformDesign(platform)
    val scheme = if (design.originalLayout) NetflixColorScheme else darkColorScheme(
        primary = design.accent, onPrimary = design.onAccent,
        primaryContainer = design.raised, onPrimaryContainer = Color.White,
        secondary = design.accent, onSecondary = design.onAccent,
        secondaryContainer = design.raised, onSecondaryContainer = Color.White,
        tertiary = design.accent, onTertiary = design.onAccent,
        background = design.background, onBackground = Color.White,
        surface = design.surface, onSurface = Color.White,
        surfaceVariant = design.raised, onSurfaceVariant = Color(0xFFD4D8E1),
        outline = design.outline, surfaceContainer = design.surface, surfaceContainerHigh = design.raised)
    val typography = if (design.originalLayout) Typography else Typography.copy(
        displayLarge = Typography.displayLarge.copy(fontWeight = design.titleWeight),
        displayMedium = Typography.displayMedium.copy(fontWeight = design.titleWeight),
        titleLarge = Typography.titleLarge.copy(fontWeight = design.titleWeight),
        titleMedium = Typography.titleMedium.copy(fontWeight = design.titleWeight))
    androidx.compose.runtime.CompositionLocalProvider(LocalPlatformDesign provides design) {
        MaterialTheme(colorScheme = scheme, typography = typography,
            shapes = if (design.originalLayout) androidx.compose.material3.Shapes() else design.shapes(), content = content)
    }
}
