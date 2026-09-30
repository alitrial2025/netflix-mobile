package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.CatalogData
import com.example.data.model.CastDevice
import com.example.data.model.GameItem
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.components.MediaPosterCard
import com.example.ui.components.NFilmBadge
import com.example.ui.components.NSeriesBadge
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixBorderGray
import com.example.ui.theme.NetflixCardBg
import com.example.ui.theme.NetflixDarkGray
import com.example.ui.theme.NetflixRed

import com.example.data.model.UserSubscription

enum class SearchCategory(val title: String, val genreFilter: String?) {
    ALL("All", null),
    TV_SHOWS("TV Shows", "TV Shows"),
    MOVIES("Movies", "Movies"),
    GAMES("Games", "Games"),
    TOP_10("Top 10", "Top 10"),
    ACTION("Action", "Action"),
    SCI_FI("Sci-Fi", "Sci-Fi"),
    ANIME("Anime", "Anime"),
    DRAMA("Drama", "Drama"),
    HORROR("Horror", "Horror"),
    K_DRAMA("K-Drama", "K-Drama")
}

data class GenreExploreCard(
    val title: String,
    val genreKey: String,
    val gradientColors: List<Color>,
    val iconRes: Int? = null
)

@Composable
fun SearchScreen(
    searchQuery: String,
    selectedGenre: String?,
    searchResults: List<MediaItem>,
    userSubscription: UserSubscription = UserSubscription(),
    connectedCastDevice: CastDevice? = null,
    games: List<GameItem> = emptyList(),
    onQueryChange: (String) -> Unit,
    onGenreFilterSelect: (String?) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onInstallGame: (String) -> Unit = {},
    onLaunchGame: (GameItem) -> Unit = {},
    onOpenCast: () -> Unit = {},
    onShowToast: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isGridView by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf(SearchCategory.ALL) }
    val activeGames = if (games.isNotEmpty()) games else CatalogData.gamesList

    val exploreCategories = remember {
        listOf(
            GenreExploreCard("Action & Thrillers", "Action", listOf(Color(0xFFB71C1C), Color(0xFF3E2723))),
            GenreExploreCard("Sci-Fi & Cyberpunk", "Sci-Fi", listOf(Color(0xFF0D47A1), Color(0xFF1A237E))),
            GenreExploreCard("Korean Dramas", "K-Drama", listOf(Color(0xFF4A148C), Color(0xFF880E4F))),
            GenreExploreCard("Anime & Fantasy", "Anime", listOf(Color(0xFFE65100), Color(0xFFBF360C))),
            GenreExploreCard("Horror & Supernatural", "Horror", listOf(Color(0xFF263238), Color(0xFF000000))),
            GenreExploreCard("Critically Acclaimed", "Drama", listOf(Color(0xFF1B5E20), Color(0xFF004D40))),
            GenreExploreCard("Comedies & Sitcoms", "Comedy", listOf(Color(0xFFF57F17), Color(0xFFE65100))),
            GenreExploreCard("True Crime & Docs", "Crime", listOf(Color(0xFF37474F), Color(0xFF212121)))
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .statusBarsPadding()
    ) {
        // 2026 Netflix Search Bar & Cast Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = "Search movies, series, games, cast...",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("search_clear_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Search",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                onShowToast("Listening... Speak title to search")
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Search",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF262626),
                    unfocusedContainerColor = Color(0xFF1E1E1E),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .testTag("search_text_field")
            )

            // Cast Action
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
        }

        // Horizontal Category Capsule Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchCategory.values().forEach { cat ->
                val isSelected = (selectedCategory == cat && selectedGenre == cat.genreFilter) ||
                        (cat == SearchCategory.ALL && selectedGenre == null && selectedCategory == SearchCategory.ALL)
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else Color(0xFF262626),
                    label = "search_pill_bg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.Black else Color.White.copy(alpha = 0.9f),
                    label = "search_pill_text"
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = bgColor,
                    border = if (!isSelected) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)) else null,
                    modifier = Modifier
                        .clickable {
                            selectedCategory = cat
                            onGenreFilterSelect(cat.genreFilter)
                        }
                        .testTag("search_pill_${cat.name.lowercase()}")
                ) {
                    Text(
                        text = cat.title,
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // MAIN CONTENT AREA
        if (searchQuery.isBlank() && selectedGenre == null && selectedCategory == SearchCategory.ALL) {
            // ==========================================
            // DEFAULT EXPLORE VIEW (Top Searches & Genres)
            // ==========================================
            LazyColumn(
                contentPadding = PaddingValues(bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Searches Header
                item {
                    Text(
                        text = "Top Searches",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // Top Search Item Rows
                items(CatalogData.getTrending().take(6), key = { "top_${it.id}" }) { item ->
                    val isLocked = remember(userSubscription, item.id, item.title) {
                        userSubscription.isMediaLocked(item.id, item.title)
                    }
                    TopSearchWidescreenRow(
                        media = item,
                        isLocked = isLocked,
                        onItemClick = { onMediaClick(item) },
                        onPlayClick = { onPlayClick(item) }
                    )
                }

                // Explore Categories Grid Section
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "Explore by Category",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 2x4 Categories Grid
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            exploreCategories.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    pair.forEach { card ->
                                        ExploreCategoryTile(
                                            card = card,
                                            onClick = {
                                                onGenreFilterSelect(card.genreKey)
                                                onQueryChange(card.title)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Trending Mobile Games Row
                item {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SportsEsports,
                                    contentDescription = "Games",
                                    tint = NetflixRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Trending Games",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(activeGames, key = { "game_search_${it.id}" }) { game ->
                                GameSearchMiniCard(
                                    game = game,
                                    onInstall = { onInstallGame(game.id) },
                                    onLaunch = { onLaunchGame(game) }
                                )
                            }
                        }
                    }
                }
            }
        } else if (selectedCategory == SearchCategory.GAMES) {
            // Games Filter Active
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Text(
                        text = "Mobile Games (${activeGames.size})",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                items(activeGames, key = { it.id }) { game ->
                    GameSearchMiniCardFull(
                        game = game,
                        onInstall = { onInstallGame(game.id) },
                        onLaunch = { onLaunchGame(game) }
                    )
                }
            }
        } else {
            // ==========================================
            // SEARCH RESULTS VIEW (Grid / List Mode)
            // ==========================================
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with Result Count and Grid/List View Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "Results for \"$searchQuery\" (${searchResults.size})"
                        else "${selectedGenre ?: "Filtered"} (${searchResults.size})",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { isGridView = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = "Grid View",
                                tint = if (isGridView) Color.White else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { isGridView = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = "List View",
                                tint = if (!isGridView) Color.White else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (searchResults.isEmpty()) {
                    // Empty Search Fallback with Recommendations
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "No direct matches for \"$searchQuery\"",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Explore these popular suggestions instead:",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Recommended for You",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(CatalogData.getTrending().take(5), key = { "rec_${it.id}" }) { item ->
                            val isLocked = remember(userSubscription, item.id, item.title) {
                                userSubscription.isMediaLocked(item.id, item.title)
                            }
                            TopSearchWidescreenRow(
                                media = item,
                                isLocked = isLocked,
                                onItemClick = { onMediaClick(item) },
                                onPlayClick = { onPlayClick(item) }
                            )
                        }
                    }
                } else if (isGridView) {
                    // 3-Column Poster Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 110.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("search_results_grid")
                    ) {
                        items(searchResults, key = { it.id }) { item ->
                            val isLocked = remember(userSubscription, item.id, item.title) {
                                userSubscription.isMediaLocked(item.id, item.title)
                            }
                            Box(modifier = Modifier.clickable { onMediaClick(item) }) {
                                MediaPosterCard(
                                    media = item,
                                    width = 112.dp,
                                    height = 165.dp,
                                    isLocked = isLocked,
                                    onClick = { onMediaClick(item) }
                                )
                            }
                        }
                    }
                } else {
                    // Detailed List View
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(searchResults, key = { "list_${it.id}" }) { item ->
                            val isLocked = remember(userSubscription, item.id, item.title) {
                                userSubscription.isMediaLocked(item.id, item.title)
                            }
                            TopSearchWidescreenRow(
                                media = item,
                                isLocked = isLocked,
                                onItemClick = { onMediaClick(item) },
                                onPlayClick = { onPlayClick(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Top Search Widescreen Row (Netflix Authentic Layout)
// -------------------------------------------------------------
@Composable
private fun TopSearchWidescreenRow(
    media: MediaItem,
    isLocked: Boolean = false,
    onItemClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1F1F1F))
            .clickable { onItemClick() }
            .testTag("top_search_row_${media.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 16:9 Thumbnail
        Box(
            modifier = Modifier
                .width(125.dp)
                .height(72.dp)
                .background(NetflixCardBg)
        ) {
            if (media.backdropUrl != null || media.posterUrl != null || media.bannerDrawableRes != null) {
                AsyncImage(
                    model = media.backdropUrl ?: media.posterUrl ?: media.bannerDrawableRes,
                    contentDescription = media.title,
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
                                    Color(media.secondaryColorHex),
                                    Color(media.primaryColorHex).copy(alpha = 0.5f)
                                )
                            )
                        )
                )
            }

            if (isLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.40f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked on Plan",
                        tint = NetflixRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (media.isOriginal) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    if (media.type == MediaType.TV_SHOW) NSeriesBadge() else NFilmBadge()
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Metadata
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = media.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${media.matchPercentage}% Match",
                    color = Color(0xFF46D369),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "•",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp
                )
                Text(
                    text = media.durationOrSeasons,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        }

        // Circular Play / Lock Button
        IconButton(
            onClick = onPlayClick,
            modifier = Modifier
                .padding(end = 6.dp)
                .testTag("top_search_play_${media.id}")
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.PlayCircleOutline,
                contentDescription = if (isLocked) "Locked on Plan" else "Play",
                tint = if (isLocked) NetflixRed else Color.White,
                modifier = Modifier.size(if (isLocked) 24.dp else 32.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// Explore Category Gradient Tile
// -------------------------------------------------------------
@Composable
private fun ExploreCategoryTile(
    card: GenreExploreCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(card.gradientColors))
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Text(
            text = card.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 17.sp
        )
    }
}

// -------------------------------------------------------------
// Mini Game Card for Horizontal Row
// -------------------------------------------------------------
@Composable
private fun GameSearchMiniCard(
    game: GameItem,
    onInstall: () -> Unit,
    onLaunch: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = Modifier
            .width(200.dp)
            .testTag("game_mini_${game.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NetflixRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = game.title,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = game.category,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = if (game.isInstalled) onLaunch else onInstall,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (game.isInstalled) Color.White else Color(0xFF333333),
                    contentColor = if (game.isInstalled) Color.Black else Color.White
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                Text(
                    text = if (game.isInstalled) "Play" else "Get Game",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Full Row Game Card
// -------------------------------------------------------------
@Composable
private fun GameSearchMiniCardFull(
    game: GameItem,
    onInstall: () -> Unit,
    onLaunch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E1E1E))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NetflixRed),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = game.title,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${game.developer} • ${game.category}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
            Text(
                text = "${game.sizeDisplay} • ${game.maturityRating}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }

        Button(
            onClick = if (game.isInstalled) onLaunch else onInstall,
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (game.isInstalled) Color.White else Color(0xFF333333),
                contentColor = if (game.isInstalled) Color.Black else Color.White
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (game.isInstalled) "Play" else "Get Game",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
