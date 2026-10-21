package com.example.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Simulated frost: no screen capture, blur pass, refraction shader or video-surface change. */
@Stable
class GlassThemeState(
    val glassy: Boolean,
    val ambientColorProvider: () -> Color = { Color.Black }
)
val LocalGlassTheme = compositionLocalOf<GlassThemeState?> { null }

/** Fixed soft gradients imply diffusion. Cached geometry and tracked draw reads keep updates cheap. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(50),
    content: @Composable BoxScope.() -> Unit
) {
    val theme = LocalGlassTheme.current
    val frosted = theme?.glassy == true
    Box(modifier.shadow(if (frosted) 8.dp else 5.dp, shape,
        ambientColor = Color.Black.copy(alpha = .20f), spotColor = Color.Black.copy(alpha = .26f)).clip(shape)) {
        Box(Modifier.matchParentSize().drawWithCache {
            val raw = if (frosted) theme?.ambientColorProvider?.invoke() ?: Color.Black else Color.Black
            val ambient = if (raw == Color.Unspecified) Color.Black else raw.copy(alpha = 1f)
            val muted = Color(ambient.red * .45f, ambient.green * .45f, ambient.blue * .45f)
            val base = if (frosted) lerp(Color(0xFF454750), muted, .06f).copy(alpha = .97f)
                else Color(0xFF242529).copy(alpha = .97f)
            val bloom = Brush.radialGradient(listOf(ambient.copy(alpha = .035f), Color.Transparent),
                center = Offset(size.width * .1f, -size.height * .65f), radius = size.width.coerceAtLeast(size.height) * .85f)
            val milk = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (frosted) .035f else .012f),
                Color.White.copy(alpha = if (frosted) .012f else 0f), Color.Transparent))
            val bottomGlow = Brush.radialGradient(listOf(Color.White.copy(alpha = .015f), Color.Transparent),
                center = Offset(size.width * .88f, size.height * 1.5f), radius = size.height * 2.4f)
            // A deterministic, cached fine grain gives the material a frosted texture.
            val panelSize = size
            val grain = if (frosted) buildList {
                val spacing = 7.dp.toPx()
                var y = spacing / 2
                var row = 0
                while (y < panelSize.height) {
                    var x = spacing / 2 + (row % 3) * spacing * .23f
                    while (x < panelSize.width) { add(Offset(x, y)); x += spacing }
                    y += spacing; row++
                }
            } else emptyList()
            val stroke = 1.dp.toPx()
            val rim = Brush.linearGradient(listOf(Color.White.copy(alpha = if (frosted) .40f else .20f),
                Color.White.copy(alpha = .06f), Color.White.copy(alpha = .10f),
                Color.White.copy(alpha = if (frosted) .26f else .16f)), Offset.Zero, Offset(size.width, size.height))
            onDrawBehind {
                drawRect(base)
                if (frosted) { drawRect(bloom); drawRect(bottomGlow) }
                drawRect(milk)
                if (grain.isNotEmpty()) drawPoints(grain, PointMode.Points, Color.White.copy(alpha = .012f),
                    strokeWidth = .55.dp.toPx())
                drawRoundRect(rim, topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(size.height / 2), style = Stroke(stroke))
            }
        })
        content()
    }
}
