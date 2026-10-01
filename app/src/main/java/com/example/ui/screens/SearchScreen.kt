package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.*

@Composable
fun SearchScreen(
    searchQuery: String, selectedGenre: String?, searchResults: List<MediaItem>,
    userSubscription: UserSubscription = UserSubscription(), connectedCastDevice: CastDevice? = null,
    games: List<GameItem> = emptyList(), onQueryChange: (String) -> Unit,
    onGenreFilterSelect: (String?) -> Unit, onMediaClick: (MediaItem) -> Unit, onPlayClick: (MediaItem) -> Unit,
    onInstallGame: (String) -> Unit = {}, onLaunchGame: (GameItem) -> Unit = {}, onOpenCast: () -> Unit = {},
    onShowToast: (String) -> Unit = {}, modifier: Modifier = Modifier,
    isSearching: Boolean = false, searchError: String? = null
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var type by rememberSaveable { mutableStateOf("All") }
    val results = remember(searchResults, type) { searchResults.filter { type == "All" || (type == "Series" && it.type == MediaType.TV_SHOW) || (type == "Films" && it.type == MediaType.MOVIE) }.distinctBy { it.type to it.id } }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onQueryChange)
    }
    Column(modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Search", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onOpenCast) { Icon(painterResource(R.drawable.ic_outline_cast), if (connectedCastDevice != null) "Connected to TV" else "Cast", tint = Color.White) }
        }
        TextField(searchQuery, onValueChange = onQueryChange, placeholder = { Text("Shows, movies and more", color = Color(0xFFAAAAAA), fontSize = 14.sp) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_outline_search), null, tint = Color.LightGray) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) IconButton(onClick = { onQueryChange("") }, modifier = Modifier.testTag("search_clear")) { Icon(Icons.Default.Close, "Clear search", tint = Color.LightGray) }
                else IconButton(onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT, "Find a movie or show")
                    try { voice.launch(intent) } catch (_: android.content.ActivityNotFoundException) { onShowToast("Voice search isn't available on this device.") }
                }) { Icon(Icons.Default.MicNone, "Voice search", tint = Color.LightGray) }
            }, singleLine = true, shape = RoundedCornerShape(4.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF282828), unfocusedContainerColor = Color(0xFF282828), focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("search_text_field"))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
            items(listOf("All", "Series", "Films"), key = { "type:$it" }) { label ->
                FilterChip(type == label, onClick = { type = label }, label = { Text(label) }, shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(labelColor = Color.LightGray, selectedContainerColor = Color.White, selectedLabelColor = Color.Black), modifier = Modifier.testTag("search_filter_$label"))
            }
            items(listOf("Drama", "Comedy", "Action", "Thriller"), key = { "genre:$it" }) { label ->
                FilterChip(selectedGenre == label, onClick = { onGenreFilterSelect(label) }, label = { Text(label) }, shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(labelColor = Color.LightGray, selectedContainerColor = Color.White, selectedLabelColor = Color.Black))
            }
        }
        if (searchError != null) Text(searchError, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("search_status"))
        if (isSearching) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp).testTag("search_loading")) }
        } else if (results.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (searchQuery.isBlank()) "Find your next favorite" else "No matches found", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(if (searchQuery.isBlank()) "Search for a title or explore a category." else "Try another title, a shorter phrase, or a different category.", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            }
        } else if (searchQuery.isBlank() && selectedGenre == null && type == "All") {
            LazyColumn(Modifier.weight(1f).testTag("search_top_list"), contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item { Text("Top Searches", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 21.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                items(results.sortedByDescending { it.isTrending }.take(24), key = { "${it.type}:${it.id}" }, contentType = { "search_title" }) { media ->
                    Row(Modifier.fillMaxWidth().background(Color(0xFF1B1B1B)).clickable { onMediaClick(media) }.testTag("top_search_${media.id}"), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(media.backdropUrl ?: media.posterUrl ?: media.bannerDrawableRes, media.title, contentScale = ContentScale.Crop, modifier = Modifier.width(132.dp).height(76.dp))
                        Text(media.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        IconButton(onClick = { onPlayClick(media) }, modifier = Modifier.testTag("top_search_play_${media.id}")) {
                            Icon(if (userSubscription.isMediaLocked(media.id, media.title)) Icons.Default.Lock else Icons.Default.PlayCircleOutline,
                                "Play ${media.title}", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        } else {
            Text("Movies & Shows (${results.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            LazyVerticalGrid(GridCells.Fixed(3), modifier = Modifier.weight(1f).testTag("search_results_grid"), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                gridItems(results, key = { "${it.type}:${it.id}" }, contentType = { "poster" }) { media ->
                    Column(Modifier.clickable { onMediaClick(media) }.testTag("search_result_${media.id}")) {
                        AsyncImage(media.posterUrl ?: media.backdropUrl ?: media.bannerDrawableRes, media.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(4.dp)).background(Color(0xFF252525)))
                        Text(media.title, color = Color.LightGray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
        }
    }
}
