package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil.request.ImageRequest
import coil.size.Precision
import kotlin.math.roundToInt

/** Stable, display-sized requests. Reuse a sufficiently large cached poster across rows. */
@Composable
internal fun rememberPosterRequest(model: Any?, width: Dp, height: Dp): ImageRequest {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    return remember(context, model, width, height, density) {
        ImageRequest.Builder(context).data(model)
            .size((width.value * density).roundToInt().coerceAtLeast(1),
                (height.value * density).roundToInt().coerceAtLeast(1))
            .precision(Precision.INEXACT).crossfade(false).build()
    }
}
