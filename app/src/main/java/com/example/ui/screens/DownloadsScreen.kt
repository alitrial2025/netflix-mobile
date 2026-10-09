package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.CatalogData
import com.example.data.download.*
import com.example.data.local.DownloadEntity
import com.example.data.model.*
import com.example.ui.components.DownloadAction
import com.example.ui.components.ProfileAvatar
import com.example.ui.theme.NetflixRed
import com.example.ui.viewmodel.downloadEpisodeId
import com.example.ui.viewmodel.episodeCoordinates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DownloadsScreen(
    downloads: List<DownloadEntity>, downloadingProgress: Map<String, Float> = emptyMap(),
    downloadTasks: Map<String, DownloadTaskInfo> = emptyMap(), pausedDownloadKeys: Set<String> = emptySet(),
    smartDownloadsEnabled: Boolean, allocatedStorageGb: Float = 3.0f, connectedCastDevice: CastDevice?,
    onClose: () -> Unit, onPlayMedia: (MediaItem, Episode?) -> Unit, onDeleteDownload: (String) -> Unit,
    onClearAllDownloads: () -> Unit, onToggleSmartDownloads: (Boolean) -> Unit,
    onUpdateAllocatedStorage: (Float) -> Unit = {}, onSetUpDownloadsForYouWithAllocation: ((Float) -> Unit)? = null,
    onSetUpDownloadsForYou: () -> Unit, onOpenSearch: () -> Unit, onOpenCast: () -> Unit,
    onOpenMediaDetail: (MediaItem) -> Unit, onShowToast: (String) -> Unit,
    onPauseDownload: (String) -> Unit = {}, onResumeDownload: (String) -> Unit = {}, onCancelDownload: (String) -> Unit = {},
    userSubscription: UserSubscription = UserSubscription(), onOpenSubscription: () -> Unit = {},
    onOpenAuth: () -> Unit = {}, modifier: Modifier = Modifier, catalogMedia: List<MediaItem> = CatalogData.allMedia,
    activeProfile: UserProfile = UserProfile("profile", "Home"), profiles: List<UserProfile> = listOf(activeProfile),
    downloadsForYouEnabled: Boolean = false, profileAllocations: Map<String, Float> = emptyMap(),
    onToggleDownloadsForYou: (Boolean) -> Unit = {}, onProfileAllocation: (String, Float) -> Unit = { _, _ -> },
    onOpenProfiles: () -> Unit = {},
    openSmartSettings: Boolean = false
) {
    var editing by rememberSaveable(activeProfile.id) { mutableStateOf(false) }
    var selected by remember(activeProfile.id) { mutableStateOf(emptySet<String>()) }
    var deleteKeys by remember(activeProfile.id) { mutableStateOf<Set<String>?>(null) }
    var clearAll by remember(activeProfile.id) { mutableStateOf(false) }
    var settings by rememberSaveable(activeProfile.id, openSmartSettings) { mutableStateOf(openSmartSettings) }
    var setup by rememberSaveable(activeProfile.id) { mutableStateOf(false) }
    var showId by rememberSaveable(activeProfile.id) { mutableStateOf<String?>(null) }
    val ready = remember(downloads, activeProfile.id) { downloads.filter { it.profileId == activeProfile.id && it.isComplete } }
    val readyKeys = remember(ready) { ready.map { it.downloadKey }.toSet() }
    val groups = remember(ready) { ready.groupBy { it.mediaId to it.isForYou }.values.toList() }
    val activeKeys = remember(downloadingProgress, downloadTasks, readyKeys) {
        (downloadingProgress.keys + downloadTasks.filterValues { it.status != DownloadTaskStatus.COMPLETED }.keys).filter { it !in readyKeys }.sorted()
    }
    LaunchedEffect(readyKeys) { selected = selected.intersect(readyKeys); if (readyKeys.isEmpty()) editing = false; if (ready.none { it.mediaId == showId }) showId = null }
    val context = LocalContext.current
    val storage by produceState<Pair<Long, Long>?>(null, ready) {
        value = withContext(Dispatchers.IO) { runCatching { android.os.StatFs(context.filesDir.absolutePath).let { it.totalBytes to it.availableBytes } }.getOrNull() }
    }
    val canDownload = userSubscription.isActive && userSubscription.maxDownloads > 0
    val requirePlan: () -> Unit = { if (userSubscription.isGuest) onOpenAuth() else onOpenSubscription() }
    val back: () -> Unit = {
        when {
            editing -> { editing = false; selected = emptySet() }
            showId != null -> showId = null
            else -> onClose()
        }
    }
    BackHandler(enabled = showId != null || editing, onBack = back)
    fun mediaFor(download: DownloadEntity): MediaItem = downloadedMedia(download, catalogMedia)
    fun play(download: DownloadEntity) {
        if (!canDownload) { onShowToast("Offline playback requires an active download plan."); requirePlan(); return }
        val media = mediaFor(download)
        val id = downloadEpisodeId(download.mediaId, download.downloadKey)
        val episode = id?.let { media.episodes.firstOrNull { ep -> ep.id == it } ?: Episode(it, episodeCoordinates(it).second, download.episodeTitle.orEmpty(), 45, "") }
        onPlayMedia(media, episode)
    }
    if (settings) {
        val allocations = profileAllocations + (activeProfile.id to (profileAllocations[activeProfile.id] ?: allocatedStorageGb))
        SmartDownloadsScreen(profiles.sortedByDescending { it.id == activeProfile.id }, allocations, smartDownloadsEnabled,
            downloadsForYouEnabled, storage?.second, ready.filter { it.isForYou }.sumOf { it.fileSizeMb.toLong() },
            onBack = { settings = false }, onNextEpisode = { if (userSubscription.isSmartNextEpisodeAllowed) onToggleSmartDownloads(it) else requirePlan() },
            onForYou = { if (userSubscription.isDownloadsForYouAllowed) onToggleDownloadsForYou(it) else requirePlan() }, onAllocation = onProfileAllocation,
            onSearch = onOpenSearch, onProfile = onOpenProfiles,
            nextEpisodeAllowed = userSubscription.isSmartNextEpisodeAllowed,
            forYouAllowed = userSubscription.isDownloadsForYouAllowed,
            onUpgrade = requirePlan)
        return
    }
    if (setup) {
        DownloadsForYouSetup(catalogMedia, allocatedStorageGb, onBack = { setup = false }, onConfirm = { allocation ->
            if (onSetUpDownloadsForYouWithAllocation != null) onSetUpDownloadsForYouWithAllocation(allocation)
            else { onUpdateAllocatedStorage(allocation); onToggleDownloadsForYou(true); onSetUpDownloadsForYou() }
            setup = false
        })
        return
    }
    Column(modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text(showId?.let { id -> ready.firstOrNull { it.mediaId == id }?.mediaTitle } ?: "Downloads", color = Color.White,
                fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onOpenCast) { Icon(painterResource(R.drawable.ic_outline_cast), if (connectedCastDevice != null) "Connected to TV" else "Cast", tint = Color.White) }
            IconButton(onOpenSearch) { Icon(painterResource(R.drawable.ic_outline_search), "Search", tint = Color.White) }
            IconButton(onOpenProfiles) { ProfileAvatar(activeProfile, size = 26.dp) }
        }
        LazyColumn(Modifier.weight(1f).testTag("downloads_list"), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (showId == null) item(key = "smart_settings") {
                Row(Modifier.clickable { settings = true }.testTag("downloads_smart_settings").padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    Text("Smart Downloads", color = Color.LightGray, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                    Icon(Icons.Default.ChevronRight, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }
            items(activeKeys, key = { "active:$it" }, contentType = { "transfer" }) { key ->
                Column {
                    Text(downloadTasks[key]?.let { it.mediaTitle + (it.episodeTitle?.let { ep -> " · $ep" } ?: "") } ?: "Downloading title", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                    DownloadAction(key, downloadingProgress[key], key in pausedDownloadKeys, false, downloadTasks[key], {}, { onPauseDownload(key) }, { onResumeDownload(key) }, { onCancelDownload(key) }, {})
                }
            }
            if (ready.isNotEmpty()) item(key = "profile_header") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(activeProfile, size = 24.dp)
                    Text(activeProfile.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    TextButton(onClick = { editing = !editing; selected = emptySet() }, modifier = Modifier.testTag("downloads_edit_toggle_btn")) { Text(if (editing) "Done" else "Edit", color = Color.White) }
                }
            }
            val shown = if (showId != null || editing) ready.filter { showId == null || it.mediaId == showId }.map { listOf(it) } else groups.filter { !it.first().isForYou }
            items(shown, key = { "ready:${it.first().downloadKey}" }, contentType = { "downloaded_title" }) { group ->
                val download = group.first(); val media = mediaFor(download)
                ReadyDownloadRow(download.copy(fileSizeMb = group.sumOf { it.fileSizeMb }), media, group.size, editing, download.downloadKey in selected,
                    onClick = {
                        if (editing) selected = if (download.downloadKey in selected) selected - download.downloadKey else selected + download.downloadKey
                        else if (group.size > 1) showId = download.mediaId else play(download)
                    }, onInfo = { onOpenMediaDetail(media) }, onDelete = { deleteKeys = group.map { it.downloadKey }.toSet() })
            }
            if (showId == null && !editing) {
                if (ready.any { it.isForYou }) item(key = "for_you_header") { Text("Downloads for You", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                items(groups.filter { it.first().isForYou }, key = { "for_you:${it.first().downloadKey}" }, contentType = { "downloaded_title" }) { group ->
                    val first = group.first(); val media = mediaFor(first)
                    ReadyDownloadRow(first.copy(fileSizeMb = group.sumOf { it.fileSizeMb }), media, group.size, false, false, { if (group.size > 1) showId = first.mediaId else play(first) }, { onOpenMediaDetail(media) }, { deleteKeys = group.map { it.downloadKey }.toSet() })
                }
                if (!downloadsForYouEnabled) item(key = "introducing") {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Introducing Downloads for You", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("We’ll download a selection of movies and shows so there’s always something to watch on your phone.", color = Color.LightGray, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 12.dp))
                        DownloadPosterFan(catalogMedia, Modifier.fillMaxWidth().height(230.dp))
                        Button(onClick = { if (canDownload) if (userSubscription.isDownloadsForYouAllowed) setup = true else requirePlan() else requirePlan() }, shape = RoundedCornerShape(3.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0071EB)), modifier = Modifier.fillMaxWidth().height(48.dp).testTag("downloads_for_you_setup")) {
                            Text(if (userSubscription.isGuest) "Sign in to download" else "Set up", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item(key = "discover") { TextButton(onOpenSearch, Modifier.fillMaxWidth()) { Text("Find more to download", color = Color.White) } }
            }
            if (editing && selected.isNotEmpty()) item(key = "delete_selection") { Button(onClick = { deleteKeys = selected }, colors = ButtonDefaults.buttonColors(containerColor = NetflixRed), modifier = Modifier.fillMaxWidth().testTag("downloads_delete_selected_btn")) { Text("Delete selected (${selected.size})") } }
            if (editing && ready.isNotEmpty()) item(key = "clear_all") { TextButton(onClick = { clearAll = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete all downloads", color = NetflixRed) } }
            item(key = "storage") {
                val free = storage?.second
                Text("NetflixPro · ${ready.sumOf { it.fileSizeMb.toLong() }} MB" + (free?.let { "    Free · ${String.format(java.util.Locale.US, "%.1f", it / (1024f * 1024 * 1024))} GB" } ?: ""), color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }
    if (deleteKeys != null || clearAll) AlertDialog(onDismissRequest = { deleteKeys = null; clearAll = false }, containerColor = Color(0xFF242424), title = { Text("Delete downloads?", color = Color.White) }, text = { Text("Remove ${if (clearAll) "all downloads" else "${deleteKeys?.size ?: 0} selected download(s)"} from this device?", color = Color.LightGray) }, confirmButton = { TextButton(onClick = { if (clearAll) onClearAllDownloads() else deleteKeys.orEmpty().forEach(onDeleteDownload); deleteKeys = null; clearAll = false; selected = emptySet() }) { Text("Delete", color = NetflixRed) } }, dismissButton = { TextButton(onClick = { deleteKeys = null; clearAll = false }) { Text("Cancel", color = Color.White) } })
}

@Composable
private fun ReadyDownloadRow(download: DownloadEntity, media: MediaItem, count: Int, editing: Boolean, selected: Boolean, onClick: () -> Unit, onInfo: () -> Unit, onDelete: () -> Unit) {
    var menu by remember(download.downloadKey) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("download_row_${download.downloadKey}"), verticalAlignment = Alignment.CenterVertically) {
        if (editing) Checkbox(selected, onCheckedChange = { onClick() }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF0071EB)))
        Box(Modifier.width(118.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(4.dp)).background(Color(0xFF222222)), contentAlignment = Alignment.Center) {
            AsyncImage(media.backdropUrl ?: media.posterUrl ?: media.bannerDrawableRes, media.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (count == 1) Icon(Icons.Default.PlayCircleOutline, "Play downloaded title", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(download.mediaTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (count == 1 && download.episodeTitle != null) Text(download.episodeTitle, color = Color.LightGray, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${media.maturityRating} · ${if (count > 1) "$count episodes · " else ""}${download.fileSizeMb} MB", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (!editing) Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Download options", tint = Color.White) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Episodes & info") }, onClick = { menu = false; onInfo() })
                DropdownMenuItem(text = { Text("Delete download") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun DownloadPosterFan(catalog: List<MediaItem>, modifier: Modifier = Modifier) {
    val posters = remember(catalog) { catalog.filter { !it.posterUrl.isNullOrBlank() && !it.isComingSoon }.distinctBy { it.id }.take(3) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.size(180.dp).background(Color(0xFF292929), CircleShape))
        if (posters.isEmpty()) Icon(painterResource(R.drawable.ic_outline_download), null, tint = Color.White, modifier = Modifier.size(64.dp))
        else if (posters.size == 1) AsyncImage(posters.first().posterUrl, posters.first().title, contentScale = ContentScale.Crop, modifier = Modifier.size(110.dp, 164.dp).clip(RoundedCornerShape(3.dp)))
        else listOfNotNull(posters.getOrNull(0)?.let { it to -16f }, posters.getOrNull(2)?.let { it to 16f }, posters.getOrNull(1)?.let { it to 0f }).forEach { (poster, angle) ->
            AsyncImage(poster.posterUrl, poster.title, contentScale = ContentScale.Crop, modifier = Modifier.offset(x = if (angle < 0) (-68).dp else if (angle > 0) 68.dp else 0.dp).size(110.dp, 164.dp).graphicsLayer { rotationZ = angle; shadowElevation = 8.dp.toPx() }.clip(RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun DownloadsForYouSetup(catalog: List<MediaItem>, initial: Float, onBack: () -> Unit, onConfirm: (Float) -> Unit) {
    var amount by rememberSaveable { mutableFloatStateOf(initial.coerceIn(1f, 10f)) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(20.dp).testTag("downloads_setup_screen"), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onBack, Modifier.align(Alignment.Start)) { Icon(Icons.Default.Close, "Close setup", tint = Color.White) }
        Text("Let’s get started", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
        Text("Choose how much storage to use for movies and shows selected for this profile. Downloads use Wi-Fi.", color = Color.LightGray, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 12.dp))
        DownloadPosterFan(catalog, Modifier.fillMaxWidth().height(230.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            IconButton(onClick = { amount = (amount - .5f).coerceAtLeast(1f) }, enabled = amount > 1f) { Icon(Icons.Default.RemoveCircleOutline, "Decrease storage", tint = Color.LightGray) }
            Text("${String.format(java.util.Locale.US, "%.1f", amount)} GB", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { amount = (amount + .5f).coerceAtMost(10f) }, enabled = amount < 10f) { Icon(Icons.Default.AddCircleOutline, "Increase storage", tint = Color.LightGray) }
        }
        Text("You can change this anytime in Smart Downloads.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp))
        Spacer(Modifier.weight(1f))
        Button(onClick = { onConfirm(amount) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0071EB)), shape = RoundedCornerShape(3.dp), modifier = Modifier.fillMaxWidth().height(48.dp).testTag("downloads_setup_confirm")) { Text("Turn on", fontWeight = FontWeight.Bold) }
    }
}
