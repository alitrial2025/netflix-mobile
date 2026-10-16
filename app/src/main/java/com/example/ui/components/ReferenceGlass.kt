package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** One translucent surface shared by the reference chips and Clips controls. */
internal fun Modifier.referenceGlass(shape: Shape, selected: Boolean = false): Modifier =
    clip(shape)
        .background(Color.White.copy(alpha = if (selected) .23f else .12f))
        .border(1.dp, Color.White.copy(alpha = if (selected) .36f else .16f), shape)
