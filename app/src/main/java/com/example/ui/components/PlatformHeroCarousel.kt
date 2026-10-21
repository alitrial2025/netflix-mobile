package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.MediaItem
import com.example.ui.theme.LocalPlatformDesign
import kotlinx.coroutines.launch

/** Real carousel indicators follow selection; only the visible page requests additional artwork. */
@Composable
internal fun PlatformHeroCarousel(items: List<MediaItem>, scope: String?, height: Dp, wide: Boolean,
    networkAvailable: Boolean, inList: (MediaItem) -> Boolean, isLocked: (MediaItem) -> Boolean,
    onPlay: (MediaItem) -> Unit, onList: (MediaItem) -> Unit, onInfo: (MediaItem) -> Unit,
    onVisible: suspend (MediaItem) -> Unit) {
    val design = LocalPlatformDesign.current
    key(scope, design.appearance) {
        val pager = rememberPagerState(pageCount = { items.size })
        val coroutine = rememberCoroutineScope()
        val current = items.getOrNull(pager.currentPage)
        LaunchedEffect(current?.id, current?.type, networkAvailable) {
            if (networkAvailable && current != null) onVisible(current)
        }
        Column {
            HorizontalPager(pager, key = { "${items[it].type}:${items[it].id}" },
                modifier = Modifier.fillMaxWidth().testTag("platform_hero_carousel")) { index ->
                val media = items[index]
                PlatformHeroBanner(media, height, wide, inList(media), isLocked(media), networkAvailable,
                    { onPlay(media) }, { onList(media) }, { onInfo(media) })
            }
            if (items.size > 1) Row(Modifier.fillMaxWidth().height(32.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                items.indices.forEach { index ->
                    val active = pager.currentPage == index
                    Box(Modifier.size(32.dp).clickable(role = Role.Tab) { coroutine.launch { pager.animateScrollToPage(index) } }
                        .semantics { selected = active }.testTag("platform_hero_page_$index"), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(if (active) 14.dp else 5.dp, 5.dp).background(
                            if (active) Color.White else Color.White.copy(alpha = .4f), CircleShape))
                    }
                }
            }
        }
    }
}
