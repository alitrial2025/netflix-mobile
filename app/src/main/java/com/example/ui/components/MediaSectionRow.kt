package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ReminderEntity
import com.example.data.local.WatchProgressEntity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.data.model.MediaItem
import com.example.data.model.MediaSection
import com.example.ui.theme.NetflixRed

@Composable
fun MediaSectionRow(
    section: MediaSection,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    reminders: List<ReminderEntity> = emptyList(),
    onToggleReminder: ((MediaItem) -> Unit)? = null,
    isMediaLocked: ((MediaItem) -> Boolean)? = null,
    onMediaClick: (MediaItem) -> Unit
) {
    MediaSectionRow(
        title = section.title,
        items = section.items,
        modifier = modifier,
        isTop10 = section.isTop10,
        cardWidth = section.cardWidth,
        cardHeight = section.cardHeight,
        lazyListState = lazyListState,
        reminders = reminders,
        onToggleReminder = onToggleReminder,
        isMediaLocked = isMediaLocked,
        onMediaClick = onMediaClick
    )
}

@Composable
fun MediaSectionRow(
    title: String,
    items: List<MediaItem>,
    modifier: Modifier = Modifier,
    isTop10: Boolean = false,
    cardWidth: Dp? = null,
    cardHeight: Dp? = null,
    lazyListState: LazyListState = rememberLazyListState(),
    reminders: List<ReminderEntity> = emptyList(),
    onToggleReminder: ((MediaItem) -> Unit)? = null,
    isMediaLocked: ((MediaItem) -> Boolean)? = null,
    onMediaClick: (MediaItem) -> Unit
) {
    if (items.isEmpty()) return

    val isOnlyFromNetflix = title.contains("Only", ignoreCase = true)
    val isComingSoonRow = title.contains("Coming Soon", ignoreCase = true) || title.contains("Worth the Wait", ignoreCase = true) || title.contains("Remind", ignoreCase = true)
    
    val defaultWidth = if (isOnlyFromNetflix) 210.dp else 115.dp
    val defaultHeight = if (isOnlyFromNetflix) 330.dp else 165.dp

    val effectiveWidth = cardWidth ?: defaultWidth
    val effectiveHeight = cardHeight ?: defaultHeight
    val rowHeight = if (isTop10) effectiveHeight.coerceAtLeast(172.dp) else effectiveHeight

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-0.3).sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (isTop10) {
            LazyRow(
                state = lazyListState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .testTag("section_row_${title.lowercase().replace(" ", "_")}")
            ) {
                itemsIndexed(
                    items = items,
                    key = { _, item -> item.id },
                    contentType = { _, _ -> "top10_media_item" }
                ) { index, item ->
                    val isReminded = remember(reminders, item.id) { reminders.any { it.mediaId == item.id } }
                    val isItemComingSoon = item.isComingSoon || isComingSoonRow
                    val isLocked = isMediaLocked?.invoke(item) ?: false
                    val onClick = remember(item, onMediaClick, onToggleReminder, isItemComingSoon) {
                        {
                            if (isItemComingSoon && onToggleReminder != null) {
                                onToggleReminder(item)
                            } else {
                                onMediaClick(item)
                            }
                        }
                    }
                    Top10PosterCard(
                        rank = index + 1,
                        media = item,
                        isLocked = isLocked,
                        onClick = onClick
                    )
                }
            }
        } else {
            LazyRow(
                state = lazyListState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .testTag("section_row_${title.lowercase().replace(" ", "_")}")
            ) {
                items(
                    items = items,
                    key = { it.id },
                    contentType = { "media_item" }
                ) { item ->
                    val isReminded = remember(reminders, item.id) { reminders.any { it.mediaId == item.id } }
                    val isItemComingSoon = item.isComingSoon || isComingSoonRow
                    val isLocked = isMediaLocked?.invoke(item) ?: false
                    val onClick = remember(item, onMediaClick, onToggleReminder, isItemComingSoon) {
                        {
                            if (isItemComingSoon && onToggleReminder != null) {
                                onToggleReminder(item)
                            } else {
                                onMediaClick(item)
                            }
                        }
                    }
                    MediaPosterCard(
                        media = item,
                        width = effectiveWidth,
                        height = effectiveHeight,
                        showRank = true,
                        isReminded = isReminded,
                        isLocked = isLocked,
                        onClick = onClick
                    )
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingSectionRow(
    title: String = "Continue Watching",
    items: List<Pair<MediaItem, WatchProgressEntity>>,
    modifier: Modifier = Modifier,
    onPlayClick: (MediaItem) -> Unit,
    onInfoClick: (MediaItem) -> Unit,
    onOptionsClick: (MediaItem, WatchProgressEntity) -> Unit = { _, _ -> }
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-0.3).sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(215.dp)
                .testTag("continue_watching_row")
        ) {
            items(
                items = items,
                key = { it.first.id },
                contentType = { "continue_watching_item" }
            ) { (media, progress) ->
                val progressFraction = if (progress.totalSeconds > 0) {
                    progress.positionSeconds.toFloat() / progress.totalSeconds.toFloat()
                } else 0.5f

                val onPlay = remember(media, onPlayClick) { { onPlayClick(media) } }
                val onInfo = remember(media, onInfoClick) { { onInfoClick(media) } }
                val onOptions = remember(media, progress, onOptionsClick) { { onOptionsClick(media, progress) } }

                ContinueWatchingCard(
                    media = media,
                    progressFraction = progressFraction,
                    episodeTitle = progress.episodeTitle,
                    onPlayClick = onPlay,
                    onInfoClick = onInfo,
                    onOptionsClick = onOptions
                )
            }
        }
    }
}
