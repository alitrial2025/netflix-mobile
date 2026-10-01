package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer

/** Pixel motion stays in this sibling's draw layer, outside Home's composition/layout. */
@Composable
internal fun HomeBackdrop(top: Color, bottom: Color, gradientEndPx: Float,
    scrollOffset: () -> Float, modifier: Modifier = Modifier) {
    val ambient = remember(top, bottom, gradientEndPx) {
        Brush.verticalGradient(colorStops = arrayOf(
            0f to top, .20f to top.copy(alpha = .88f), .38f to bottom.copy(alpha = .72f),
            .54f to bottom.copy(alpha = .50f), .68f to bottom.copy(alpha = .30f),
            .80f to bottom.copy(alpha = .15f), .90f to bottom.copy(alpha = .05f),
            .97f to bottom.copy(alpha = .01f), 1f to Color.Black), endY = gradientEndPx)
    }
    val edge = remember { Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .55f), Color.Transparent)) }
    Box(modifier.fillMaxSize().graphicsLayer().drawWithCache {
        onDrawBehind {
            val offset = scrollOffset()
            if (offset < gradientEndPx) clipRect {
                withTransform({ translate(0f, -offset) }) {
                    drawRect(ambient, size = Size(size.width, gradientEndPx))
                }
            }
            drawRect(edge)
        }
    })
}

/** Only the short gradient region needs offsets; deep rows share the same black backdrop. */
internal fun homeBackdropOffset(index: Int, offset: Int, keys: List<String>,
    heights: Map<String, Int>, gradientEndPx: Float): Float {
    var total = offset.toFloat()
    for (i in 0 until index.coerceAtMost(keys.size)) {
        total += heights[keys[i]] ?: return gradientEndPx // Unmeasured programmatic jump.
        if (total >= gradientEndPx) return gradientEndPx
    }
    return total.coerceIn(0f, gradientEndPx)
}
