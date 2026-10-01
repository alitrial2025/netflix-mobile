package com.example.ui.screens

import com.example.discovery.recommendationTitle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.CatalogData
import com.example.data.CloudWatchHistoryItem
import com.example.data.local.DownloadEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.WatchProgressEntity
import com.example.data.model.CastDevice
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.NotificationItem
import com.example.data.model.TrailerItem
import com.example.data.model.UserProfile
import com.example.ui.components.MediaPosterCard
import com.example.ui.components.NetflixNLogo
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.TrailerCard
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixBlue
import com.example.ui.theme.NetflixBorderGray
import com.example.ui.theme.NetflixCardBg
import com.example.ui.theme.NetflixDarkGray
import com.example.ui.theme.NetflixGreen
import com.example.ui.theme.NetflixRed

private enum class MyListFilter {
    ALL, TV_SHOWS, MOVIES
}

@Composable
fun MyNetflixScreen(
    activeProfile: UserProfile,
    watchlist: List<MediaItem>,
    downloads: List<DownloadEntity>,
    likedMedia: List<MediaItem>,
    watchedTrailers: List<TrailerItem>,
    notifications: List<NotificationItem>,
    continueWatchingList: List<Pair<MediaItem, WatchProgressEntity>>,
    watchHistory: List<CloudWatchHistoryItem>,
    reminders: List<ReminderEntity>,
    connectedCastDevice: CastDevice?,
    smartDownloadsEnabled: Boolean,
    onSwitchProfileClick: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onPlayTrailerClick: (TrailerItem) -> Unit,
    onOpenDownloads: () -> Unit,
    onDeleteDownload: (String) -> Unit,
    onClearAllDownloads: () -> Unit,
    onWatchlistToggle: (MediaItem) -> Unit,
    onReminderToggle: (MediaItem) -> Unit,
    onContinueWatchingOptionsClick: (MediaItem, WatchProgressEntity) -> Unit,
    onHistoryClick: (CloudWatchHistoryItem) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCast: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onShowToast: (String) -> Unit,
    onOpenTvPair: () -> Unit = {},
    onOpenClips: () -> Unit = {},
    userSubscription: com.example.data.model.UserSubscription = com.example.data.model.UserSubscription(),
    onOpenAuth: () -> Unit = {},
    modifier: Modifier = Modifier,
    reminderMedia: List<MediaItem> = emptyList()
) {
    val scrollState = rememberScrollState()
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedListFilter by remember { mutableStateOf(MyListFilter.ALL) }
    var showAllMyList by remember { mutableStateOf(false) }

    val unreadNotificationsCount = notifications.count { !it.isRead }

    val filteredWatchlist = remember(watchlist, selectedListFilter) {
        when (selectedListFilter) {
            MyListFilter.ALL -> watchlist
            MyListFilter.TV_SHOWS -> watchlist.filter { it.type == MediaType.TV_SHOW }
            MyListFilter.MOVIES -> watchlist.filter { it.type == MediaType.MOVIE }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Delete All Downloads?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("This will remove all downloaded movies and episodes from your device.", color = Color.White.copy(alpha = 0.8f)) },
            containerColor = NetflixDarkGray,
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearAllDownloads()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed)
                ) {
                    Text("Delete All", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(bottom = 110.dp)
    ) {
        // =========================================================================
        // 1. TOP HEADER BAR: [Red Avatar + "Home ▾"] ... [Downloads] [Notifications(5)]
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Profile avatar + Home / Name with dropdown arrow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        if (userSubscription.isGuest) {
                            onShowToast("Sign in to create and manage profiles.")
                            onOpenAuth()
                        } else {
                            onSwitchProfileClick()
                        }
                    }
                    .padding(4.dp)
                    .testTag("my_netflix_profile_dropdown_btn")
            ) {
                // Profile Avatar rounded square (red smiling icon)
                ProfileAvatar(
                    profile = activeProfile,
                    size = 38.dp,
                    isSelected = false
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = activeProfile.name,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Switch Profile",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Right: Download icon + Bell icon with red badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Download Button -> opens Downloads Screen
                IconButton(
                    onClick = onOpenDownloads,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("my_netflix_header_download_button")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_netflix_download_custom),
                        contentDescription = "Downloads",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Notification Bell with Red Badge "5"
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onOpenNotifications() }
                        .testTag("my_netflix_notifications_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )

                    // Blue circular badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(NetflixBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$unreadNotificationsCount",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (userSubscription.isGuest) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                NetflixRed.copy(alpha = 0.22f),
                                Color(0xFF1E1E1E)
                            )
                        )
                    )
                    .border(1.dp, NetflixRed.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .clickable { onOpenAuth() }
                    .padding(14.dp)
                    .testTag("my_netflix_guest_banner")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sign In to NetflixPro",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Unlock movies, TV shows, downloads, and your personalized list.",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = onOpenAuth,
                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Sign In", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // =========================================================================
        // 2. DOWNLOADS ENTRY CARD: [Icon] "Downloads" / "Series and films..." [ > ]
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NetflixDarkGray)
                .border(1.dp, NetflixBorderGray, RoundedCornerShape(12.dp))
                .clickable { onOpenDownloads() }
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .testTag("my_netflix_downloads_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Download icon
                    Icon(
                        painter = painterResource(id = R.drawable.ic_netflix_download_custom),
                        contentDescription = "Downloads",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "Downloads",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Series and films that you download appear here.",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Open Downloads",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onOpenClips,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("my_netflix_clips")
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Explore Clips", color = Color.White)
        }

        // TV Sign In Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            NetflixBlue.copy(alpha = 0.18f),
                            NetflixDarkGray
                        )
                    )
                )
                .border(1.dp, NetflixBlue.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .clickable {
                    if (userSubscription.isGuest) {
                        onShowToast("Sign in to sync with your TV.")
                        onOpenAuth()
                    } else {
                        onOpenTvPair()
                    }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("my_netflix_tv_pair_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NetflixBlue.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = "Watch on TV",
                            tint = NetflixBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Sign in on TV",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Use the same account email and password on TV",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Watch on TV",
                    tint = NetflixBlue.copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))        // =========================================================================
        // 3. "CONTINUE WATCHING" SECTION WITH POSTER, PLAY OVERLAY, (i) AND (⋮)
        // =========================================================================
        if (continueWatchingList.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Continue Watching",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.testTag("continue_watching_row")
                ) {
                    items(continueWatchingList, key = { it.first.id }) { (media, progress) ->
                    val progressFraction = (progress.positionSeconds.toFloat() / progress.totalSeconds.toFloat()).coerceIn(0.15f, 0.95f)

                    Box(
                        modifier = Modifier
                            .width(135.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF141414))
                            .border(1.dp, NetflixBorderGray, RoundedCornerShape(6.dp))
                            .clickable { onPlayClick(media) }
                            .testTag("continue_card_${media.id}")
                    ) {
                        Column {
                            // Poster container
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            ) {
                                if (media.bannerDrawableRes != null) {
                                    Image(
                                        painter = painterResource(id = media.bannerDrawableRes),
                                        contentDescription = media.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (!media.posterUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = media.posterUrl,
                                        contentDescription = media.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        Color(0xFF222226),
                                                        Color(0xFF141416),
                                                        Color(0xFF0D0D0F)
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = media.title,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(6.dp)
                                        )
                                    }
                                }

                                // Dark subtle vignette
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.2f))
                                )

                                // Centered translucent play button overlay
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .border(1.5.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                // "New Episode / Watch Now" Badge if applicable
                                if (media.id.hashCode() % 2 == 0) {
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(NetflixRed)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "New Episode",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color.White)
                                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "Watch Now",
                                                color = Color.Black,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Progress bar along bottom of poster
                                LinearProgressIndicator(
                                    progress = { progressFraction },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(3.dp),
                                    color = NetflixRed,
                                    trackColor = Color(0xFF333333)
                                )
                            }

                            // Bottom Action Bar: Info (i) on left, 3 Dots (⋮) on right
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .background(Color(0xFF161616))
                                    .padding(horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Circular Info Button (i)
                                IconButton(
                                    onClick = { onMediaClick(media) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("continue_info_${media.id}")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "i",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // 3 Dots Options Button (⋮)
                                IconButton(
                                    onClick = { onContinueWatchingOptionsClick(media, progress) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("continue_options_${media.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (watchHistory.isNotEmpty()) {
            Text(
                text = "Watch History",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("watch_history_row")
            ) {
                items(watchHistory, key = { it.mediaId }) { item ->
                    Column(
                        modifier = Modifier
                            .width(120.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onHistoryClick(item) }
                            .testTag("watch_history_${item.mediaId}")
                    ) {
                        if (item.posterUrl.isNotBlank()) {
                            AsyncImage(
                                model = item.posterUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(170.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .background(NetflixCardBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (item.isCompleted) "Watched" else "Started",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // =========================================================================
        // 4. "SERIES & FILMS YOU'VE LIKED" SECTION WITH SHARE BUTTON BELOW POSTER
        // =========================================================================
        val displayLikedMedia = if (likedMedia.isNotEmpty()) {
            likedMedia
        } else {
            CatalogData.allMedia.filter { it.matchPercentage >= 90 }.take(6)
        }

        if (displayLikedMedia.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Series & Films You've Liked",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.testTag("liked_media_row")
                ) {
                    items(displayLikedMedia, key = { it.id }) { media ->
                        Column(
                            modifier = Modifier.width(120.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Poster Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NetflixCardBg)
                                    .clickable { onMediaClick(media) }
                            ) {
                                if (media.bannerDrawableRes != null) {
                                    Image(
                                        painter = painterResource(id = media.bannerDrawableRes),
                                        contentDescription = media.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (!media.posterUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = media.posterUrl,
                                        contentDescription = media.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                if (media.isOriginal) {
                                    NetflixNLogo(
                                        size = 18.dp,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Share Pill Button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF222222))
                                    .clickable { onShowToast("Sharing ${media.title}...") }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_netflix_share),
                                        contentDescription = "Share",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Share",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // =========================================================================
        // 5. "MY LIST" SECTION WITH "SEE ALL >"
        // =========================================================================
        val displayWatchlist = if (filteredWatchlist.isNotEmpty()) {
            filteredWatchlist
        } else {
            CatalogData.allMedia.take(6)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My List",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { showAllMyList = !showAllMyList }
                        .padding(4.dp)
                        .testTag("my_list_see_all_btn")
                ) {
                    Text(
                        text = "See All",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "See All",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("my_list_row")
            ) {
                items(displayWatchlist, key = { it.id }) { item ->
                    MediaPosterCard(
                        media = item,
                        onClick = { onMediaClick(item) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // =========================================================================
        // 6. "TRAILERS YOU'VE WATCHED" (2026 SIGNATURE SECTION)
        // =========================================================================
        if (watchedTrailers.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trailers you've watched",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .testTag("trailers_watched_row")
                ) {
                    items(watchedTrailers, key = { it.id }) { trailer ->
                        val inWatchlist = watchlist.any { it.id == trailer.media.id }
                        val isReminded = reminders.any { it.mediaId == trailer.media.recommendationTitle().key }
                        TrailerCard(
                            trailer = trailer,
                            isInWatchlist = inWatchlist,
                            isReminded = isReminded,
                            onPlayClick = { onPlayTrailerClick(trailer) },
                            onWatchlistToggle = { onWatchlistToggle(trailer.media) },
                            onReminderToggle = { onReminderToggle(trailer.media) },
                            onInfoClick = { onMediaClick(trailer.media) },
                            onShareClick = { onShowToast("Shared ${trailer.trailerTitle}") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // =========================================================================
        // 7. "REMINDERS SET" SECTION
        // =========================================================================
        if (reminders.isNotEmpty()) {
            val remindedMediaItems = remember(reminders, reminderMedia) {
                val byKey = reminderMedia.associateBy { it.recommendationTitle().key }
                reminders.mapNotNull { byKey[it.mediaId] }
            }
            if (remindedMediaItems.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color(0xFF00ACC1),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reminders Set (${remindedMediaItems.size})",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        items(remindedMediaItems, key = { it.id }) { media ->
                            MediaPosterCard(
                                media = media,
                                onClick = { onMediaClick(media) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // =========================================================================
        // 8. APP SETTINGS & HELP ENTRY POINT ROW
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NetflixDarkGray)
                .border(1.dp, NetflixBorderGray, RoundedCornerShape(10.dp))
                .clickable { onOpenSettings() }
                .padding(14.dp)
                .testTag("app_settings_row_btn")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "App Settings & Diagnostics",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Smart downloads, playback quality, network test",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 10.sp
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
