package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.MobileSettingsStore
import com.example.data.model.PlatformAppearance

@Immutable
data class PlatformDesign(
    val appearance: PlatformAppearance = PlatformAppearance.ORIGINAL,
    val accent: Color = NetflixRed,
    val onAccent: Color = Color.White,
    val background: Color = NetflixBlack,
    val surface: Color = NetflixDarkGray,
    val raised: Color = NetflixCardBg,
    val outline: Color = NetflixBorderGray,
    val cardRadius: Dp = 4.dp,
    val controlRadius: Dp = 6.dp,
    val wideCards: Boolean = false,
    val titleWeight: FontWeight = FontWeight.Bold
) {
    val originalLayout: Boolean get() = appearance == PlatformAppearance.ORIGINAL || appearance == PlatformAppearance.NETFLIX
    val primary: Color get() = if (appearance == PlatformAppearance.PEACOCK) accent else Color.White
    val onPrimary: Color get() = if (appearance == PlatformAppearance.PEACOCK) onAccent else Color.Black
    val brandTitle: String get() = if (appearance == PlatformAppearance.HOTSTAR) "JioHotstar" else appearance.label
    val cardAspectRatio: Float get() = if (wideCards) 16f / 9f else 2f / 3f
    fun surfaceColor(original: Color): Color {
        if (originalLayout) return original
        return when (original) {
            Color.Black, Color(0xFF0B0B0B), Color(0xFF0C0C0C), Color(0xFF101010), Color(0xFF141414) -> background
            Color(0xFF333333), Color(0xFF292929), Color(0xFF2A2A2A) -> outline
            else -> surface
        }
    }
    fun corner(original: Dp): Dp = if (originalLayout) original else cardRadius
    fun controlColor(original: Color): Color = if (originalLayout) original else accent
    fun shapes() = Shapes(extraSmall = RoundedCornerShape(cardRadius / 2), small = RoundedCornerShape(cardRadius),
        medium = RoundedCornerShape(controlRadius), large = RoundedCornerShape(cardRadius * 2), extraLarge = RoundedCornerShape(cardRadius * 3))
}

private val platformDesigns = mapOf(
    PlatformAppearance.ORIGINAL to PlatformDesign(),
    PlatformAppearance.NETFLIX to PlatformDesign(appearance = PlatformAppearance.NETFLIX),
    PlatformAppearance.PRIME to PlatformDesign(PlatformAppearance.PRIME, Color(0xFF00A8E1), Color(0xFF00131C),
        Color(0xFF00050D), Color(0xFF101C29), Color(0xFF1A2B3D), Color(0xFF34465A), 8.dp, 8.dp, false),
    PlatformAppearance.DISNEY to PlatformDesign(PlatformAppearance.DISNEY, Color(0xFF02D6E8), Color(0xFF002329),
        Color(0xFF16141B), Color(0xFF24222C), Color(0xFF302D3A), Color(0xFF4A4656), 6.dp, 6.dp, false, FontWeight.SemiBold),
    PlatformAppearance.HOTSTAR to PlatformDesign(PlatformAppearance.HOTSTAR, Color(0xFF0865D6), Color.White,
        Color(0xFF0F1014), Color(0xFF191D29), Color(0xFF252B3B), Color(0xFF3B4253), 16.dp, 8.dp, false, FontWeight.ExtraBold),
    PlatformAppearance.APPLE to PlatformDesign(PlatformAppearance.APPLE, Color(0xFF0071E3), Color.White,
        Color(0xFF000000), Color(0xFF1C1C1E), Color(0xFF2C2C2E), Color(0xFF3A3A3C), 14.dp, 50.dp, true, FontWeight.SemiBold),
    PlatformAppearance.PEACOCK to PlatformDesign(PlatformAppearance.PEACOCK, Color(0xFFFCCC12), Color(0xFF111111),
        Color(0xFF202020), Color(0xFF292929), Color(0xFF333333), Color(0xFF505050), 14.dp, 50.dp, false)
)
fun platformDesign(appearance: PlatformAppearance): PlatformDesign = platformDesigns.getValue(appearance)
val LocalPlatformDesign = staticCompositionLocalOf { platformDesign(PlatformAppearance.ORIGINAL) }

/** One preference listener at the root; updates in place without restarting navigation or players. */
@Composable
fun MobileAppTheme(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("netflix_prefs", Context.MODE_PRIVATE) }
    PlatformPreferenceTheme(prefs, content)
}

@Composable
internal fun PlatformPreferenceTheme(prefs: SharedPreferences, content: @Composable () -> Unit) {
    val store = remember(prefs) { MobileSettingsStore(prefs) }
    var appearance by remember(prefs) { mutableStateOf(store.effectivePlatform) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == com.example.data.MobileSetting.PLATFORMS.key || key == "pref_platform_style")
                appearance = store.effectivePlatform
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        appearance = store.effectivePlatform
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    NetflixTheme(platform = appearance, content = content)
}
