package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.CloudWatchHistoryItem
import com.example.data.local.DownloadEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.WatchProgressEntity
import com.example.data.model.CastDevice
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.NotificationItem
import com.example.data.model.SavedMoment
import com.example.data.model.TrailerItem
import com.example.data.model.UserProfile
import com.example.data.model.UserSubscription
import com.example.ui.components.NetflixNLogo
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.TrailerCard
import com.example.discovery.recommendationTitle
import com.example.ui.theme.NetflixRed

private enum class MyListFilter(val label: String) {
    ALL("All"), TV_SHOWS("Series"), MOVIES("Films")
}

/** Personal library. Rails use the active profile's actual data, including when offline. */
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
    userSubscription: UserSubscription = UserSubscription(),
    onOpenAuth: () -> Unit = {},
    modifier: Modifier = Modifier,
    reminderMedia: List<MediaItem> = emptyList(),
    isAuthenticated: Boolean = !userSubscription.isGuest,
    onOpenSubscription: () -> Unit = {},
    savedMoments: List<SavedMoment> = emptyList(),
    onPlayMoment: (SavedMoment) -> Unit = {},
    onRemoveMoment: (SavedMoment) -> Unit = {}
) {
    var showAllMoments by rememberSaveable(activeProfile.id) { mutableStateOf(false) }
    var showAllMyList by rememberSaveable(activeProfile.id) { mutableStateOf(false) }
    var showAllLiked by rememberSaveable(activeProfile.id) { mutableStateOf(false) }
    var showClearDialog by remember(activeProfile.id) { mutableStateOf(false) }
    val unreadCount = notifications.count { !it.isRead }
    val actualWatchlist = remember(watchlist) { watchlist.distinctBy { it.type to it.id } }
    val actualLiked = remember(likedMedia) { likedMedia.distinctBy { it.type to it.id } }

    if (showAllMoments) {
        SavedMomentsDialog(savedMoments, onPlayMoment, onRemoveMoment, onShowToast) { showAllMoments = false }
    }
    if (showAllMyList) {
        LibraryCollectionDialog("My List", actualWatchlist, onMediaClick, { showAllMyList = false }, onWatchlistToggle)
    }
    if (showAllLiked) {
        LibraryCollectionDialog("Series & films you liked", actualLiked, onMediaClick, { showAllLiked = false })
    }
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Delete all downloads?") },
            text = { Text("Downloaded movies and episodes will be removed from this device.") },
            containerColor = Color(0xFF202020),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCCCCCC),
            confirmButton = {
                TextButton(onClick = { showClearDialog = false; onClearAllDownloads() }) {
                    Text("Delete all", color = NetflixRed)
                }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel", color = Color.White) } }
        )
    }

    Column(
        modifier = modifier.fillMaxSize().background(Color(0xFF0B0B0B))
            .statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 112.dp)
            .testTag("my_netflix_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .clickable {
                        if (isAuthenticated) onSwitchProfileClick()
                        else { onShowToast("Sign in to create and manage profiles."); onOpenAuth() }
                    }.padding(vertical = 8.dp).testTag("my_netflix_profile_dropdown_btn"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileAvatar(profile = activeProfile, size = 32.dp)
                Spacer(Modifier.width(8.dp))
                Text(activeProfile.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch profile", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = onOpenCast, modifier = Modifier.size(42.dp).testTag("my_netflix_cast_button")) {
                Icon(if (connectedCastDevice?.isConnected == true) Icons.Default.CastConnected else Icons.Default.Cast,
                    contentDescription = connectedCastDevice?.takeIf { it.isConnected }?.let { "Connected to ${it.name}" } ?: "Cast",
                    tint = Color.White, modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = onOpenDownloads, modifier = Modifier.size(42.dp).testTag("my_netflix_header_download_button")) {
                Icon(painterResource(R.drawable.ic_netflix_download_custom), contentDescription = "Downloads", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = onOpenNotifications, modifier = Modifier.fillMaxSize().testTag("my_netflix_notifications_btn")) {
                    Icon(Icons.Default.NotificationsNone, "Notifications", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                if (unreadCount > 0) {
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 3.dp)
                        .background(NetflixRed, CircleShape).padding(horizontal = 4.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
                        Text(if (unreadCount > 99) "99+" else "$unreadCount", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (userSubscription.isGuest) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color(0xFF211517))
                    .padding(12.dp).testTag("my_netflix_guest_banner"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(if (isAuthenticated) "Choose your NetflixPro plan" else "Sign In to NetflixPro",
                        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Your account, profiles and membership", color = Color(0xFFBBBBBB),
                        fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Spacer(Modifier.width(10.dp))
                Button(onClick = if (isAuthenticated) onOpenSubscription else onOpenAuth,
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                    shape = RoundedCornerShape(5.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(if (isAuthenticated) "View Plans" else "Sign In", color = Color.White,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LibraryHeading("Moments you've saved", if (savedMoments.isNotEmpty()) ({ showAllMoments = true }) else null, "my_moments_see_all_btn")
        if (savedMoments.isEmpty()) {
            MomentsEmptyState(onOpenClips)
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("saved_moments_row")) {
                items(savedMoments, key = { it.id }) { moment ->
                    SavedMomentCard(moment, onPlayMoment, onRemoveMoment, onShowToast, Modifier.width(228.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        LibraryPosterRail("My List", actualWatchlist, "my_list_row", onMediaClick,
            onSeeAll = { showAllMyList = true }, seeAllTag = "my_list_see_all_btn",
            emptyText = "Save series and films to find them here.", onExplore = onNavigateToSearch)

        if (actualLiked.isNotEmpty()) {
            LibraryPosterRail("Series & films you liked", actualLiked, "liked_media_row", onMediaClick,
                onSeeAll = { showAllLiked = true })
        }

        if (continueWatchingList.isNotEmpty()) {
            LibraryHeading("Continue Watching")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("continue_watching_row")) {
                items(continueWatchingList, key = { "${it.first.type}:${it.first.id}" }) { (media, progress) ->
                    Column(Modifier.width(112.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF1B1B1B))) {
                        Box(Modifier.fillMaxWidth().height(164.dp).clickable { onPlayClick(media) }.testTag("continue_card_${media.id}")) {
                            LibraryArtwork(media, Modifier.fillMaxSize())
                            Box(Modifier.align(Alignment.Center).size(42.dp).background(Color.Black.copy(alpha = .55f), CircleShape)
                                .border(1.5.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, "Resume ${media.title}", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            LinearProgressIndicator(progress = {
                                if (progress.totalSeconds > 0) (progress.positionSeconds.toFloat() / progress.totalSeconds).coerceIn(0f, 1f) else 0f
                            }, color = NetflixRed, trackColor = Color(0xFF555555), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            IconButton(onClick = { onMediaClick(media) }, modifier = Modifier.size(40.dp).testTag("continue_info_${media.id}")) {
                                Text("ⓘ", color = Color.White, fontSize = 23.sp)
                            }
                            IconButton(onClick = { onContinueWatchingOptionsClick(media, progress) }, modifier = Modifier.size(40.dp).testTag("continue_options_${media.id}")) {
                                Icon(Icons.Default.MoreVert, "Options for ${media.title}", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (watchedTrailers.isNotEmpty()) {
            LibraryHeading("Trailers you've watched")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("trailers_watched_row")) {
                items(watchedTrailers, key = { it.id }) { trailer ->
                    TrailerCard(trailer = trailer,
                        isInWatchlist = watchlist.any { it.type == trailer.media.type && it.id == trailer.media.id },
                        isReminded = reminders.any { it.mediaId == trailer.media.recommendationTitle().key },
                        onPlayClick = { onPlayTrailerClick(trailer) },
                        onWatchlistToggle = { onWatchlistToggle(trailer.media) },
                        onReminderToggle = { onReminderToggle(trailer.media) },
                        onInfoClick = { onMediaClick(trailer.media) },
                        onShareClick = { onShowToast("Open the title to share ${trailer.media.title}.") })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        if (watchHistory.isNotEmpty()) {
            LibraryHeading("Watch history")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("watch_history_row")) {
                items(watchHistory.distinctBy { it.type to it.mediaId }, key = { "${it.type}:${it.mediaId}" }) { item ->
                    Column(Modifier.width(112.dp).clip(RoundedCornerShape(5.dp)).clickable { onHistoryClick(item) }.testTag("watch_history_${item.mediaId}")) {
                        Box(Modifier.fillMaxWidth().height(164.dp).background(Color(0xFF202020)), contentAlignment = Alignment.Center) {
                            if (item.posterUrl.isNotBlank()) AsyncImage(item.posterUrl, item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Text(item.title, color = Color.White, textAlign = TextAlign.Center, maxLines = 3, modifier = Modifier.padding(8.dp))
                        }
                        Text(item.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                        Text(if (item.isCompleted) "Watched" else "Started", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        val remindedMedia = remember(reminders, reminderMedia) {
            val byKey = reminderMedia.associateBy { it.recommendationTitle().key }
            reminders.mapNotNull { byKey[it.mediaId] }.distinctBy { it.type to it.id }
        }
        if (remindedMedia.isNotEmpty()) LibraryPosterRail("Reminders you've set", remindedMedia, "reminders_row", onMediaClick)

        LibraryUtilityRow("Downloads", if (downloads.isEmpty()) "Series and films you download appear here"
            else "${downloads.count { it.isComplete }} ready to watch${if (smartDownloadsEnabled) " · Smart Downloads on" else ""}",
            "my_netflix_downloads_card", onOpenDownloads) {
            Icon(painterResource(R.drawable.ic_netflix_download_custom), "Downloads", tint = Color.White, modifier = Modifier.size(25.dp))
        }
        if (downloads.isNotEmpty()) {
            TextButton(onClick = { showClearDialog = true }, modifier = Modifier.padding(start = 16.dp)) {
                Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp)); Text("Delete all downloads", color = Color(0xFFCCCCCC))
            }
        }
        LibraryUtilityRow("Sign in on TV", "Connect your NetflixPro account to your TV", "my_netflix_tv_pair_card", {
            if (isAuthenticated) onOpenTvPair() else { onShowToast("Sign in to sync with your TV."); onOpenAuth() }
        }) { Icon(Icons.Default.Cast, null, tint = Color.White, modifier = Modifier.size(25.dp)) }
        LibraryUtilityRow("App settings & diagnostics", "Downloads, playback quality and account settings", "app_settings_row_btn", onOpenSettings) {
            Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(25.dp))
        }
    }
}

@Composable
private fun LibraryHeading(title: String, onSeeAll: (() -> Unit)? = null, seeAllTag: String = "") {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (onSeeAll != null) {
            Row(Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onSeeAll).padding(start = 8.dp, top = 8.dp, bottom = 8.dp).testTag(seeAllTag), verticalAlignment = Alignment.CenterVertically) {
                Text("See all", color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.width(5.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun MomentsEmptyState(onOpenClips: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(5.dp))
        .background(Color(0xFF171717)).padding(16.dp)) {
        Text("Your favourite scenes, all together.", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("Tap Save moment in the player to keep a scene here.", color = Color(0xFFAAAAAA), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        TextButton(onClick = onOpenClips, contentPadding = PaddingValues(0.dp), modifier = Modifier.testTag("my_netflix_clips")) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(5.dp)); Text("Explore Clips", color = Color.White, fontSize = 13.sp)
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun LibraryPosterRail(
    title: String, media: List<MediaItem>, tag: String, onMediaClick: (MediaItem) -> Unit,
    onSeeAll: (() -> Unit)? = null, seeAllTag: String = "", emptyText: String = "", onExplore: (() -> Unit)? = null
) {
    LibraryHeading(title, if (media.isNotEmpty()) onSeeAll else null, seeAllTag)
    if (media.isEmpty()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(emptyText, color = Color(0xFFAAAAAA), fontSize = 13.sp)
            if (onExplore != null) TextButton(onClick = onExplore, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Find something to watch", color = Color.White, fontSize = 13.sp)
            }
        }
    } else {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = ((maxWidth - 48.dp) / 3f).coerceIn(100.dp, 140.dp)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag(tag)) {
                items(media, key = { "${it.type}:${it.id}" }) { item ->
                    Box(Modifier.width(cardWidth).height(cardWidth * 1.5f).clip(RoundedCornerShape(5.dp))
                        .clickable { onMediaClick(item) }.testTag("${tag}_${item.id}")) {
                        LibraryArtwork(item, Modifier.fillMaxSize())
                        if (item.isOriginal) NetflixNLogo(size = 15.dp, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun LibraryArtwork(media: MediaItem, modifier: Modifier = Modifier, landscape: Boolean = false) {
    Box(modifier.background(Color(0xFF202020)), contentAlignment = Alignment.Center) {
        val url = if (landscape) media.backdropUrl ?: media.posterUrl else media.posterUrl ?: media.backdropUrl
        when {
            !url.isNullOrBlank() -> AsyncImage(url, media.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            media.bannerDrawableRes != null -> Image(painterResource(media.bannerDrawableRes), media.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else -> Text(media.title, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 4, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun LibraryUtilityRow(title: String, subtitle: String, tag: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(6.dp))
        .background(Color(0xFF171717)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 15.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        icon(); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFFAAAAAA), fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun LibraryCollectionDialog(title: String, media: List<MediaItem>, onMediaClick: (MediaItem) -> Unit, onDismiss: () -> Unit, onRemove: ((MediaItem) -> Unit)? = null) {
    var filter by rememberSaveable { mutableStateOf(MyListFilter.ALL) }
    val filtered = remember(media, filter) {
        media.filter { filter == MyListFilter.ALL || (filter == MyListFilter.TV_SHOWS && it.type == MediaType.TV_SHOW) || (filter == MyListFilter.MOVIES && it.type == MediaType.MOVIE) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0B)).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close $title", tint = Color.White) }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MyListFilter.entries.forEach { option ->
                    FilterChip(selected = option == filter, onClick = { filter = option }, label = { Text(option.label, color = Color.White) })
                }
            }
            if (filtered.isEmpty()) Text("No titles here yet.", color = Color(0xFFAAAAAA), modifier = Modifier.padding(24.dp))
            LazyVerticalGrid(columns = GridCells.Adaptive(100.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered, key = { "${it.type}:${it.id}" }) { item ->
                    Column {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(5.dp)).clickable { onDismiss(); onMediaClick(item) }) {
                            LibraryArtwork(item, Modifier.fillMaxSize())
                        }
                        Text(item.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        if (onRemove != null) TextButton(onClick = { onRemove(item) }, contentPadding = PaddingValues(0.dp)) {
                            Icon(Icons.Default.Close, null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(16.dp))
                            Text("Remove", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedMomentCard(
    moment: SavedMoment,
    onPlay: (SavedMoment) -> Unit,
    onRemove: (SavedMoment) -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember(moment.id) { mutableStateOf(false) }
    val startTime = momentTimestamp(moment.positionSeconds)
    val episode = if (moment.episodeNumber != null && moment.seasonNumber != null) " | S${moment.seasonNumber}:E${moment.episodeNumber}" else ""
    Column(modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF191919)).testTag("saved_moment_${moment.id}")) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clickable { onPlay(moment) }) {
            LibraryArtwork(moment.media, Modifier.fillMaxSize(), landscape = true)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .6f)))))
            Row(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(31.dp).border(1.dp, Color.White, CircleShape).background(Color.Black.copy(alpha = .35f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, "Play saved moment", tint = Color.White, modifier = Modifier.size(23.dp))
                }
                Spacer(Modifier.width(7.dp))
                Text("Saved", color = Color.White, fontSize = 12.sp)
            }
            IconButton(onClick = {
                val message = "Watch ${moment.media.title}$episode from $startTime on NetflixPro."
                try {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                    }, "Share moment"))
                } catch (_: ActivityNotFoundException) {
                    onShowToast("No sharing app is available on this device.")
                }
            }, modifier = Modifier.align(Alignment.BottomEnd).size(40.dp).testTag("share_moment_${moment.id}")) {
                Icon(painterResource(R.drawable.ic_netflix_share), "Share saved moment", tint = Color.White, modifier = Modifier.size(23.dp))
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, "Saved moment options", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Remove moment") }, onClick = { showMenu = false; onRemove(moment) })
                }
            }
        }
        Column(Modifier.fillMaxWidth().clickable { onPlay(moment) }.padding(horizontal = 8.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Starts at $startTime", color = Color.White, fontSize = 12.sp, maxLines = 1)
            Text(moment.media.title + episode, color = Color(0xFFADADAD), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun SavedMomentsDialog(
    moments: List<SavedMoment>, onPlay: (SavedMoment) -> Unit, onRemove: (SavedMoment) -> Unit,
    onShowToast: (String) -> Unit, onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0B)).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Moments you've saved", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 21.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close saved moments", tint = Color.White) }
            }
            if (moments.isEmpty()) {
                Text("Your saved scenes will appear here.", color = Color(0xFFAAAAAA), modifier = Modifier.padding(16.dp))
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(moments, key = { it.id }) { moment ->
                    SavedMomentCard(moment, { onDismiss(); onPlay(it) }, onRemove, onShowToast, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun momentTimestamp(seconds: Int): String {
    val value = seconds.coerceAtLeast(0)
    return if (value >= 3600) "%d:%02d:%02d".format(value / 3600, value / 60 % 60, value % 60)
    else "%02d:%02d".format(value / 60, value % 60)
}
