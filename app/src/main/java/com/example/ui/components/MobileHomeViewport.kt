package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Retain a portrait Home composition when a tablet/foldable window grows wide. */
@Composable
internal fun MobileHomeViewport(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val contentWidth = minOf(maxWidth, 600.dp, (maxHeight * .5f).coerceAtLeast(320.dp))
        Box(Modifier.width(contentWidth).fillMaxHeight(), content = content)
    }
}
