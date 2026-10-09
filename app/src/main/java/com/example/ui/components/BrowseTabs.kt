package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.example.ui.viewmodel.NavigationTab

/** Retain each destination's browse position, scoped to the account and profile. */
@Composable
internal fun BrowseTabs(tab: NavigationTab, owner: String, content: @Composable (NavigationTab) -> Unit) {
    key(owner) {
        val holder = rememberSaveableStateHolder()
        AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "TabContent") { destination ->
            holder.SaveableStateProvider(destination.name) { content(destination) }
        }
    }
}
