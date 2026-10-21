package com.example.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.dp
import com.example.ui.components.ReferenceGlassTint
import kotlin.math.sin

/** Simulated frost: no screen capture, blur pass, refraction shader or video-surface change. */
@Stable
class GlassThemeState(
    val glassy: Boolean,
    val ambientColorProvider: () -> Color = { Color.Black }
) {
    // Draw-only, bounded scroll position. Never consume or redirect the gesture.
    internal val travel = mutableFloatStateOf(0f)
    internal val scrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val delta = consumed.x + consumed.y
            if (glassy && delta.isFinite() && delta != 0f) {
                travel.floatValue = (travel.floatValue + delta * .008f) % (2f * kotlin.math.PI.toFloat())
            }
            return Offset.Zero
        }
    }
}
val LocalGlassTheme = compositionLocalOf<GlassThemeState?> { null }

/** Fixed soft gradients imply diffusion. Cached geometry and tracked draw reads keep updates cheap. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(50),
    content: @Composable BoxScope.() -> Unit
) {
    val theme = LocalGlassTheme.current
    val design = com.example.ui.theme.LocalPlatformDesign.current
    val frosted = theme?.glassy == true
    if (!design.originalLayout) {
        val platformShape = if (shape == RoundedCornerShape(50)) RoundedCornerShape(design.controlRadius) else shape
        val fill = remember(frosted, design) { Brush.verticalGradient(if (frosted) listOf(design.raised.copy(alpha = .96f), design.surface.copy(alpha = .98f))
            else listOf(design.surface, design.surface)) }
        val rim = remember(frosted, design) { Brush.verticalGradient(if (frosted) listOf(Color.White.copy(alpha = .24f), design.outline.copy(alpha = .55f))
            else listOf(design.outline.copy(alpha = .35f), design.outline.copy(alpha = .35f))) }
        Box(modifier.clip(platformShape).background(fill).border(if (frosted) 1.dp else .5.dp, rim, platformShape), content = content)
        return
    }
    Box(modifier.shadow(if (frosted) 8.dp else 5.dp, shape,
        ambientColor = Color.Black.copy(alpha = .20f), spotColor = Color.Black.copy(alpha = .26f)).clip(shape)) {
        Box(Modifier.matchParentSize().drawWithCache {
            val raw = if (frosted) theme?.ambientColorProvider?.invoke() ?: Color.Black else Color.Black
            val ambient = if (raw == Color.Unspecified) Color.Black else raw.copy(alpha = 1f)
            val base = if (frosted) Color.Black.copy(alpha = .78f)
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
            val stroke = (if (frosted) 1.8.dp else 1.dp).toPx()
            val rim = Brush.linearGradient(listOf(Color.White.copy(alpha = if (frosted) .78f else .20f),
                Color.White.copy(alpha = .06f), Color.White.copy(alpha = .10f),
                Color.White.copy(alpha = if (frosted) .58f else .16f)), Offset.Zero, Offset(size.width, size.height))
            val lensRadius = size.width.coerceAtLeast(size.height) * .65f
            val lens = Brush.radialGradient(listOf(Color.White.copy(alpha = .055f), Color.Transparent),
                center = Offset.Zero, radius = lensRadius)
            val innerRim = Brush.verticalGradient(listOf(Color.White.copy(alpha = .20f), Color.Transparent,
                Color.Black.copy(alpha = .26f), Color.White.copy(alpha = .16f)))
            onDrawBehind {
                drawRect(base)
                if (frosted) {
                    drawRect(ReferenceGlassTint)
                    drawRect(bloom); drawRect(bottomGlow)
                    // A moving sheen suggests a lens; it does not magnify or
                    // sample underlying pixels. Read travel only while drawing.
                    val shift = .5f + sin(theme?.travel?.floatValue ?: 0f) * .28f
                    withTransform({ translate(size.width * shift, -size.height * .55f) }) {
                        drawCircle(lens, lensRadius, Offset.Zero)
                    }
                    val inset = stroke * 1.5f
                    drawRoundRect(innerRim, Offset(inset, inset),
                        androidx.compose.ui.geometry.Size((size.width - inset * 2).coerceAtLeast(0f),
                            (size.height - inset * 2).coerceAtLeast(0f)),
                        CornerRadius((size.height / 2 - inset).coerceAtLeast(0f)), style = Stroke(.7.dp.toPx()))
                }
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
