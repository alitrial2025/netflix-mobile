package com.example.ui.screens

import com.example.discovery.recommendationTitle

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.CatalogData
import com.example.data.local.ReminderEntity
import com.example.data.model.CastDevice
import com.example.data.model.GameItem
import com.example.data.model.MediaItem
import com.example.data.model.NotificationItem
import com.example.data.model.UserProfile
import com.example.ui.components.NFilmBadge
import com.example.ui.components.NSeriesBadge
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixBorderGray
import com.example.ui.theme.NetflixCardBg
import com.example.ui.theme.NetflixRed

enum class NewAndHotTab(val title: String, val emoji: String) {
    COMING_SOON("Coming Soon", "🍿"),
    EVERYONES_WATCHING("Everyone's Watching", "🔥"),
    TOP_10_TV("Top 10 TV Shows", "🔟"),
    TOP_10_MOVIES("Top 10 Movies", "🔟"),
    GAMES("Games", "🎮")
}

@Composable
fun NewAndHotScreen(
    reminders: List<ReminderEntity>,
    games: List<GameItem> = emptyList(),
    notifications: List<NotificationItem> = emptyList(),
    connectedCastDevice: CastDevice? = null,
    onToggleReminder: (MediaItem) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onWatchlistToggle: (MediaItem) -> Unit,
    isWatchlistContains: (String) -> Boolean,
    onInstallGame: (String) -> Unit = {},
    onLaunchGame: (GameItem) -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenCast: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onShowToast: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    upcomingReleases: List<MediaItem> = emptyList(),
    recentReleases: List<MediaItem> = emptyList()
) {
    var selectedTab by remember { mutableStateOf(NewAndHotTab.COMING_SOON) }
    val context = LocalContext.current

    // Local sound mute toggles for trailer previews
    val mutedStates = remember { mutableStateMapOf<String, Boolean>() }

    val comingSoonList = upcomingReleases
    val everyonesWatchingList = recentReleases
    val top10TvShows = recentReleases.filter { it.type == com.example.data.model.MediaType.TV_SHOW }.take(10)
    val top10Movies = recentReleases.filter { it.type == com.example.data.model.MediaType.MOVIE }.take(10)
    val activeGames = if (games.isNotEmpty()) games else CatalogData.gamesList

    val unreadNotificationsCount = notifications.count { !it.isRead }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .statusBarsPadding()
    ) {
        // 2026 Netflix Top Bar with App Branding & Quick Action Suite
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "New & Hot",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // TV Cast Icon
                IconButton(
                    onClick = onOpenCast,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (connectedCastDevice != null) Icons.Default.CastConnected else Icons.Default.Cast,
                        contentDescription = "Cast",
                        tint = if (connectedCastDevice != null) Color(0xFF4CAF50) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Notifications Bell with Live Badge
                IconButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        if (unreadNotificationsCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(10.dp)
                                    .background(NetflixRed, CircleShape)
                            )
                        }
                    }
                }

                // Search Icon
                IconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 2026 Netflix Horizontal Pill Category Bar
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NewAndHotTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else Color(0xFF262626),
                    label = "tab_bg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.Black else Color.White.copy(alpha = 0.9f),
                    label = "tab_text"
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = bgColor,
                    border = if (!isSelected) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)) else null,
                    modifier = Modifier
                        .clickable { selectedTab = tab }
                        .testTag("tab_${tab.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = tab.emoji,
                            fontSize = 13.sp
                        )
                        Text(
                            text = tab.title,
                            color = textColor,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Dynamic Tab Content
        when (selectedTab) {
            NewAndHotTab.COMING_SOON -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = comingSoonList,
                        key = { "${it.type}:${it.id}" },
                        contentType = { "coming_soon_card" }
                    ) { item ->
                        val isReminded = reminders.any { it.mediaId == item.recommendationTitle().key }
                        val isMuted = mutedStates[item.id] ?: true

                        ComingSoonFeedCard(
                            item = item,
                            isReminded = isReminded,
                            isMuted = isMuted,
                            onToggleMute = {
                                mutedStates[item.id] = !isMuted
                                onShowToast(if (isMuted) "Audio unmuted" else "Audio muted")
                            },
                            onReminderToggle = { onToggleReminder(item) },
                            onPlayTrailer = { onPlayClick(item) },
                            onShareClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Watch '${item.title}' coming soon to NetflixPro! https://www.themoviedb.org/${if (item.type == com.example.data.model.MediaType.MOVIE) "movie" else "tv"}/${item.id}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Title"))
                            },
                            onItemClick = { onMediaClick(item) }
                        )
                    }
                }
            }

            NewAndHotTab.EVERYONES_WATCHING -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = everyonesWatchingList,
                        key = { "${it.type}:${it.id}" },
                        contentType = { "everyones_watching_card" }
                    ) { item ->
                        val isMuted = mutedStates[item.id] ?: true
                        EveryonesWatchingFeedCard(
                            item = item,
                            isInWatchlist = isWatchlistContains(item.id),
                            isMuted = isMuted,
                            onToggleMute = {
                                mutedStates[item.id] = !isMuted
                                onShowToast(if (isMuted) "Audio unmuted" else "Audio muted")
                            },
                            onPlayClick = { onPlayClick(item) },
                            onWatchlistToggle = { onWatchlistToggle(item) },
                            onShareClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Stream '${item.title}' now on NetflixPro! https://www.themoviedb.org/${if (item.type == com.example.data.model.MediaType.MOVIE) "movie" else "tv"}/${item.id}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Title"))
                            },
                            onItemClick = { onMediaClick(item) }
                        )
                    }
                }
            }

            NewAndHotTab.TOP_10_TV -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = top10TvShows,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> "top10_tv_card" }
                    ) { index, item ->
                        Top10RankFeedCard(
                            rank = index + 1,
                            item = item,
                            categoryLabel = "TV Shows",
                            isInWatchlist = isWatchlistContains(item.id),
                            onPlayClick = { onPlayClick(item) },
                            onWatchlistToggle = { onWatchlistToggle(item) },
                            onShareClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "#${index + 1} TV Show on NetflixPro: '${item.title}'! https://www.themoviedb.org/${if (item.type == com.example.data.model.MediaType.MOVIE) "movie" else "tv"}/${item.id}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share"))
                            },
                            onItemClick = { onMediaClick(item) }
                        )
                    }
                }
            }

            NewAndHotTab.TOP_10_MOVIES -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = top10Movies,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> "top10_movies_card" }
                    ) { index, item ->
                        Top10RankFeedCard(
                            rank = index + 1,
                            item = item,
                            categoryLabel = "Movies",
                            isInWatchlist = isWatchlistContains(item.id),
                            onPlayClick = { onPlayClick(item) },
                            onWatchlistToggle = { onWatchlistToggle(item) },
                            onShareClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "#${index + 1} Movie on NetflixPro: '${item.title}'! https://www.themoviedb.org/${if (item.type == com.example.data.model.MediaType.MOVIE) "movie" else "tv"}/${item.id}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share"))
                            },
                            onItemClick = { onMediaClick(item) }
                        )
                    }
                }
            }

            NewAndHotTab.GAMES -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Games Header Banner
                    item(contentType = "games_header") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(NetflixRed, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SportsEsports,
                                        contentDescription = "Games",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Mobile Games on NetflixPro",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Included with membership • No ads • No extra fees",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    items(
                        items = activeGames,
                        key = { "game:${it.id}" },
                        contentType = { "game_card" }
                    ) { game ->
                        GameFeedCard(
                            game = game,
                            onInstall = { onInstallGame(game.id) },
                            onLaunch = { onLaunchGame(game) }
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 1. Coming Soon Card (100% Netflix 2026 Layout)
// -------------------------------------------------------------
@Composable
fun ComingSoonFeedCard(
    item: MediaItem,
    isReminded: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onReminderToggle: () -> Unit,
    onPlayTrailer: () -> Unit,
    onShareClick: () -> Unit,
    onItemClick: () -> Unit
) {
    val dateParts = (item.releaseDateBadge ?: "AUG 28").split(" ")
    val month = dateParts.firstOrNull() ?: "AUG"
    val day = dateParts.getOrNull(1) ?: "28"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("coming_soon_${item.id}")
    ) {
        // Date Timeline Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(48.dp)
        ) {
            Text(
                text = month.uppercase(),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = day,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Main Card
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onItemClick() }
        ) {
            // 16:9 Widescreen Video Trailer Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NetflixCardBg)
            ) {
                if (item.backdropUrl != null || item.posterUrl != null || item.bannerDrawableRes != null) {
                    AsyncImage(
                        model = item.backdropUrl ?: item.posterUrl ?: item.bannerDrawableRes,
                        contentDescription = item.title,
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
                                        Color(item.secondaryColorHex),
                                        Color(item.primaryColorHex).copy(alpha = 0.6f),
                                        Color.Black
                                    )
                                )
                            )
                    )
                }

                // Netflix Series / Film Badge
                if (item.isOriginal) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        if (item.type == com.example.data.model.MediaType.TV_SHOW) NSeriesBadge() else NFilmBadge()
                    }
                }

                // Center Play Teaser Overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                        .clickable { onPlayTrailer() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Trailer",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Bottom Right Sound Mute/Unmute Toggle
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .clickable { onToggleMute() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Mute Toggle",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Suite Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Remind Me Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onReminderToggle() }
                            .testTag("remind_btn_${item.id}")
                    ) {
                        Icon(
                            imageVector = if (isReminded) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                            contentDescription = "Remind Me",
                            tint = if (isReminded) NetflixRed else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isReminded) "Reminded" else "Remind Me",
                            color = if (isReminded) NetflixRed else Color.White.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontWeight = if (isReminded) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    // Share Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onShareClick() }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_netflix_share),
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Share",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        )
                    }

                    // Info Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onItemClick() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Info",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Coming Date Callout
            Text(
                text = "Coming ${item.releaseDateBadge ?: "Soon"}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Description
            Text(
                text = item.description,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Genres
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item.genres.take(3).forEachIndexed { i, genre ->
                    if (i > 0) {
                        Text(
                            text = "•",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = genre,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. Everyone's Watching Card (100% Netflix 2026 Layout)
// -------------------------------------------------------------
@Composable
private fun EveryonesWatchingFeedCard(
    item: MediaItem,
    isInWatchlist: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onPlayClick: () -> Unit,
    onWatchlistToggle: () -> Unit,
    onShareClick: () -> Unit,
    onItemClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onItemClick() }
            .testTag("everyones_watching_${item.id}")
    ) {
        // 16:9 Widescreen Backdrop
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NetflixCardBg)
        ) {
            if (item.backdropUrl != null || item.posterUrl != null || item.bannerDrawableRes != null) {
                AsyncImage(
                    model = item.backdropUrl ?: item.posterUrl ?: item.bannerDrawableRes,
                    contentDescription = item.title,
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
                                    Color(item.secondaryColorHex),
                                    Color(item.primaryColorHex).copy(alpha = 0.6f),
                                    Color.Black
                                )
                            )
                        )
                )
            }

            // N Badge
            if (item.isOriginal) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    if (item.type == com.example.data.model.MediaType.TV_SHOW) NSeriesBadge() else NFilmBadge()
                }
            }

            // Sound Toggle Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    .clickable { onToggleMute() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Mute Toggle",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Title and Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Share
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onShareClick() }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_netflix_share),
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Share", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                }

                // My List
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onWatchlistToggle() }
                ) {
                    Icon(
                        imageVector = if (isInWatchlist) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = "My List",
                        tint = if (isInWatchlist) NetflixRed else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isInWatchlist) "In List" else "My List",
                        color = if (isInWatchlist) NetflixRed else Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )
                }

                // Play Button
                Button(
                    onClick = onPlayClick,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Metadata badges
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${item.matchPercentage}% Match",
                color = Color(0xFF46D369),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Surface(
                color = Color(0xFF333333),
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    text = item.maturityRating,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            Text(
                text = item.durationOrSeasons,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
            Surface(
                color = Color.Transparent,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    text = "HD",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }
            Surface(
                color = Color.Transparent,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    text = "Spatial Audio",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Description
        Text(
            text = item.description,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Genres
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item.genres.take(4).forEachIndexed { idx, genre ->
                if (idx > 0) {
                    Text("•", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                }
                Text(
                    text = genre,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 3 & 4. Top 10 Ranked Feed Card (Giant Number + Backdrop)
// -------------------------------------------------------------
@Composable
private fun Top10RankFeedCard(
    rank: Int,
    item: MediaItem,
    categoryLabel: String,
    isInWatchlist: Boolean,
    onPlayClick: () -> Unit,
    onWatchlistToggle: () -> Unit,
    onShareClick: () -> Unit,
    onItemClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onItemClick() }
            .testTag("top10_${rank}_${item.id}")
    ) {
        // Netflix Giant Rank Number on Left
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$rank",
                color = Color.White,
                fontSize = if (rank == 10) 42.sp else 54.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Right Preview & Details
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NetflixCardBg)
            ) {
                if (item.backdropUrl != null || item.posterUrl != null || item.bannerDrawableRes != null) {
                    AsyncImage(
                        model = item.backdropUrl ?: item.posterUrl ?: item.bannerDrawableRes,
                        contentDescription = item.title,
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
                                        Color(item.secondaryColorHex),
                                        Color(item.primaryColorHex).copy(alpha = 0.5f),
                                        Color.Black
                                    )
                                )
                            )
                    )
                }

                // Top 10 Red Badge
                Surface(
                    color = NetflixRed,
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "TOP 10",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title & Action Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "#$rank in $categoryLabel Today",
                        color = NetflixRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Share
                    IconButton(
                        onClick = onShareClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_netflix_share),
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // My List
                    IconButton(
                        onClick = onWatchlistToggle,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isInWatchlist) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = "My List",
                            tint = if (isInWatchlist) NetflixRed else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Play
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White, CircleShape)
                            .clickable { onPlayClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.description,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -------------------------------------------------------------
// 5. Mobile Game Feed Card (2026 Netflix Games)
// -------------------------------------------------------------
@Composable
private fun GameFeedCard(
    game: GameItem,
    onInstall: () -> Unit,
    onLaunch: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("game_${game.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 16:9 Cinematic Gameplay Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(Color(0xFF222222))
            ) {
                if (game.bannerUrl != null || game.bannerRes != null) {
                    AsyncImage(
                        model = game.bannerUrl ?: game.bannerRes,
                        contentDescription = game.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Netflix N Games Badge
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        com.example.ui.components.NetflixNLogo(size = 14.dp)
                        Text("GAMES", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }

            // Game Details & Install Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Squircle Game Avatar
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NetflixRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = game.title,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and Studio
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = game.developer,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${game.category} • ${game.sizeDisplay}",
                        color = Color(0xFF46D369),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Get Game / Install / Open Button
                if (game.downloadProgress != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(86.dp)
                    ) {
                        Text(
                            text = "${(game.downloadProgress * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { game.downloadProgress },
                            color = NetflixRed,
                            trackColor = Color(0xFF333333),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                } else if (game.isInstalled) {
                    Button(
                        onClick = onLaunch,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Play", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = onInstall,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Get Game", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Description
            Text(
                text = game.description,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            )
        }
    }
}
