package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.CatalogData
import com.example.data.model.MediaItem
import com.example.data.model.NotificationIconType
import com.example.data.model.NotificationItem
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixBorderGray
import com.example.ui.theme.NetflixCardBg
import com.example.ui.theme.NetflixDarkGray
import com.example.ui.theme.NetflixGreen
import com.example.ui.theme.NetflixRed

enum class NotificationFilter(val label: String, val emoji: String) {
    ALL("All", "🔔"),
    NEW_ARRIVALS("New Arrivals", "🍿"),
    REMINDERS("Reminders", "⏰"),
    DOWNLOADS("Downloads", "📥")
}

/**
 * 2026 Netflix Notifications Full Screen UI
 */
@Composable
fun NotificationsScreen(
    notifications: List<NotificationItem>,
    onClose: () -> Unit,
    onMarkAllRead: () -> Unit,
    onNotificationClick: (NotificationItem) -> Unit,
    onDeleteNotification: (String) -> Unit,
    onPlayMedia: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    // Intercept system back button to close full screen
    BackHandler { onClose() }

    var selectedFilter by remember { mutableStateOf(NotificationFilter.ALL) }

    val unreadCount = remember(notifications) { notifications.count { !it.isRead } }

    val filteredNotifications = remember(notifications, selectedFilter) {
        when (selectedFilter) {
            NotificationFilter.ALL -> notifications
            NotificationFilter.NEW_ARRIVALS -> notifications.filter { it.iconType == NotificationIconType.NEW_SEASON }
            NotificationFilter.REMINDERS -> notifications.filter { it.iconType == NotificationIconType.REMINDER || it.iconType == NotificationIconType.LEAVING_SOON }
            NotificationFilter.DOWNLOADS -> notifications.filter { it.iconType == NotificationIconType.DOWNLOAD }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("notifications_screen")
    ) {
        // 1. Top Navigation Bar (Back Arrow, Title, Mark All Read)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("notifications_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "Notifications",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )

                if (unreadCount > 0) {
                    Surface(
                        color = NetflixRed,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "$unreadCount new",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (unreadCount > 0) {
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier
                        .clickable { onMarkAllRead() }
                        .testTag("mark_all_read_btn")
                ) {
                    Text(
                        text = "Mark all read",
                        color = NetflixRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 2. Category Filter Pills Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NotificationFilter.values().forEach { filter ->
                val isSelected = selectedFilter == filter
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else Color(0xFF262626),
                    label = "notif_pill_bg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.Black else Color.White.copy(alpha = 0.9f),
                    label = "notif_pill_text"
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = bgColor,
                    border = if (!isSelected) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)) else null,
                    modifier = Modifier
                        .clickable { selectedFilter = filter }
                        .testTag("filter_${filter.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = filter.emoji, fontSize = 12.sp)
                        Text(
                            text = filter.label,
                            color = textColor,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Notification List or Empty State
        if (filteredNotifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222222)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = "You're all caught up!",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "We'll let you know when new movies, seasons, or downloads arrive.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onClose,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Browse Home", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredNotifications, key = { it.id }) { notif ->
                    val matchedMedia = notif.mediaId?.let { CatalogData.getById(it) }

                    NotificationCard(
                        notification = notif,
                        matchedMedia = matchedMedia,
                        onItemClick = { onNotificationClick(notif) },
                        onDeleteClick = { onDeleteNotification(notif.id) },
                        onPlayClick = {
                            if (matchedMedia != null) {
                                onPlayMedia(matchedMedia)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Backwards compatibility alias for NotificationsSheet
 */
@Composable
fun NotificationsSheet(
    notifications: List<NotificationItem>,
    onClose: () -> Unit,
    onMarkAllRead: () -> Unit,
    onNotificationClick: (NotificationItem) -> Unit,
    onDeleteNotification: (String) -> Unit,
    onPlayMedia: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    NotificationsScreen(
        notifications = notifications,
        onClose = onClose,
        onMarkAllRead = onMarkAllRead,
        onNotificationClick = onNotificationClick,
        onDeleteNotification = onDeleteNotification,
        onPlayMedia = onPlayMedia,
        modifier = modifier
    )
}

@Composable
private fun NotificationCard(
    notification: NotificationItem,
    matchedMedia: MediaItem?,
    onItemClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val isUnread = !notification.isRead

    val containerBg = if (isUnread) Color(0xFF1E1E1E) else Color(0xFF141414)
    val borderColor = if (isUnread) NetflixRed.copy(alpha = 0.5f) else NetflixBorderGray

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerBg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onItemClick() }
            .padding(14.dp)
            .testTag("notif_card_${notification.id}"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Icon Badge
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    when (notification.iconType) {
                        NotificationIconType.NEW_SEASON -> NetflixRed
                        NotificationIconType.REMINDER -> Color(0xFF00ACC1)
                        NotificationIconType.DOWNLOAD -> NetflixGreen
                        NotificationIconType.LEAVING_SOON -> Color(0xFFFF9800)
                        NotificationIconType.BELL -> Color(0xFF444444)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (notification.iconType) {
                    NotificationIconType.NEW_SEASON -> Icons.Default.FiberNew
                    NotificationIconType.REMINDER -> Icons.Default.NotificationsActive
                    NotificationIconType.DOWNLOAD -> Icons.Default.DownloadDone
                    NotificationIconType.LEAVING_SOON -> Icons.Default.Timer
                    NotificationIconType.BELL -> Icons.Default.Notifications
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Middle Text Info
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = notification.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isUnread) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(NetflixRed)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = notification.message,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = notification.timestamp,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp
            )

            // Direct Action Row if media is linked
            if (matchedMedia != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlayClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NetflixRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Surface(
                        color = Color(0xFF2A2A2A),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .clickable { onItemClick() }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "View Details",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Right side: Widescreen Artwork Thumbnail (if matched) or Delete Icon
        if (matchedMedia != null) {
            Box(
                modifier = Modifier
                    .size(width = 90.dp, height = 55.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NetflixCardBg)
                    .clickable { onPlayClick() }
            ) {
                if (matchedMedia.backdropUrl != null || matchedMedia.posterUrl != null || matchedMedia.bannerDrawableRes != null) {
                    AsyncImage(
                        model = matchedMedia.backdropUrl ?: matchedMedia.posterUrl ?: matchedMedia.bannerDrawableRes,
                        contentDescription = matchedMedia.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(matchedMedia.secondaryColorHex),
                                        Color(matchedMedia.primaryColorHex).copy(alpha = 0.5f)
                                    )
                                )
                            )
                    )
                }

                // Play Center Overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else {
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
