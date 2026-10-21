package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Let Home use its actual window, including unfolded, landscape and split-screen bounds. */
@Composable
internal fun MobileHomeViewport(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), content = content)
}
