package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import com.example.data.model.GameItem
import com.example.ui.components.NGamesBadge
import com.example.ui.components.NetflixNLogo
import com.example.ui.components.toBottomPosterColor
import com.example.ui.components.toTopPosterColor
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val gamesColorsCache = mutableMapOf<String, Pair<Color, Color>>()

@Composable
fun GamesScreen(
    games: List<GameItem>,
    onGameClick: (GameItem) -> Unit,
    modifier: Modifier = Modifier,
    onAmbientColorChange: (Color) -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
    listState: LazyListState = rememberLazyListState()
) {
    if (games.isEmpty()) return

    val heroGame = games.first()
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // Adaptive Hero Card Height identical to HomeScreen
    val heroCardHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp * 0.70f).dp.coerceIn(550.dp, 680.dp)
    }

    val gradientEndPx = remember(heroCardHeight, density) {
        with(density) { (90.dp + heroCardHeight + 300.dp).toPx() }
    }

    // Dynamic Color Palette Extraction from Hero Game Poster
    val cachedColors = remember(heroGame.id) { gamesColorsCache[heroGame.id] }
    val defaultTop = remember(heroGame.id) { Color(0xFF3D3E32) }
    val defaultBottom = remember(heroGame.id) { Color(0xFF20211A) }

    var extractedTopColor by remember(heroGame.id) { mutableStateOf<Color?>(cachedColors?.first) }
    var extractedBottomColor by remember(heroGame.id) { mutableStateOf<Color?>(cachedColors?.second) }

    val currentTopColor = extractedTopColor ?: defaultTop
    val currentBottomColor = extractedBottomColor ?: defaultBottom

    val imageModel = heroGame.bannerRes ?: heroGame.bannerUrl

    // Ambient color propagation for bottom navigation bar blend
    LaunchedEffect(heroGame.id, extractedBottomColor) {
        onAmbientColorChange(currentBottomColor)
    }

    LaunchedEffect(heroGame.id, imageModel) {
        if (cachedColors != null) {
            extractedTopColor = cachedColors.first
            extractedBottomColor = cachedColors.second
            onAmbientColorChange(cachedColors.second)
            return@LaunchedEffect
        }
        onAmbientColorChange(defaultBottom)
        if (imageModel != null) {
            withContext(Dispatchers.IO) {
                try {
                    val paletteReq = ImageRequest.Builder(context)
                        .data(imageModel)
                        .allowHardware(false)
                        .size(Size(80, 80))
                        .build()
                    val result = (context.imageLoader.execute(paletteReq) as? SuccessResult)?.drawable
                    val bitmap = (result as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).generate()
                        val swatchRgb = palette.dominantSwatch?.rgb
                            ?: palette.vibrantSwatch?.rgb
                            ?: palette.darkMutedSwatch?.rgb

                        if (swatchRgb != null) {
                            val baseColor = Color(swatchRgb)
                            val top = baseColor.toTopPosterColor()
                            val bottom = baseColor.toBottomPosterColor()
                            gamesColorsCache[heroGame.id] = Pair(top, bottom)
                            withContext(Dispatchers.Main) {
                                extractedTopColor = top
                                extractedBottomColor = bottom
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // fallback safely
                }
            }
        }
    }

    val maxScrollOffsetPx = remember(density) { with(density) { 90.dp.toPx() } }

    // Parallax offset computation aligned with HomeScreen using lambda provider
    val parallaxOffset = remember {
        derivedStateOf {
            (scrollState.value.toFloat() * 0.35f).coerceAtMost(maxScrollOffsetPx * 0.35f)
        }
    }

    // Dynamic ambient background gradient with draw-time scroll-fade matching HomeScreen
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .drawWithCache {
                val gradient = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to currentTopColor,
                        0.35f to currentTopColor.copy(alpha = 0.85f),
                        0.60f to currentBottomColor.copy(alpha = 0.65f),
                        0.85f to currentBottomColor.copy(alpha = 0.35f),
                        1.0f to Color.Black
                    ),
                    startY = 0f,
                    endY = gradientEndPx
                )
                onDrawBehind {
                    val fadeOutDistancePx = heroCardHeight.toPx()
                    val scrollRatio = if (fadeOutDistancePx > 0) {
                        (scrollState.value.toFloat() / fadeOutDistancePx).coerceIn(0f, 1f)
                    } else 0f
                    val scrollAlpha = (1f - scrollRatio).coerceIn(0f, 1f)

                    drawRect(brush = gradient, alpha = scrollAlpha)
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 110.dp)
        ) {
            // 1. Reduced Top Spacing: status bar + 90.dp header height (reduced from 134.dp)
            Spacer(modifier = Modifier.statusBarsPadding())
            Spacer(modifier = Modifier.height(90.dp))

            // 2. Hero Card Container with reduced bottom padding
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                GameHeroBanner(
                    game = heroGame,
                    cardHeight = heroCardHeight,
                    cardBottomBgColor = currentBottomColor,
                    onGetGameClick = {
                        openPlayStore(context, heroGame.title)
                    },
                    onDetailsClick = {
                        onGameClick(heroGame)
                    },
                    parallaxOffsetProvider = { parallaxOffset.value }
                )
            }

            // 3. Row: Top 10 Mobile Games
            Top10GamesSectionRow(
                title = "Top 10 Mobile Games Today",
                games = games.take(10),
                onGameClick = onGameClick
            )

            // 4. Row: Popular on Netflix Games
            GameSectionRow(
                title = "Popular on Netflix Games",
                games = games,
                onGameClick = onGameClick
            )

            // 5. Row: Action & Open World
            val actionGames = remember(games) {
                games.filter {
                    it.category.contains("Action", ignoreCase = true) ||
                    it.category.contains("Open World", ignoreCase = true) ||
                    it.category.contains("Arcade", ignoreCase = true) ||
                    it.category.contains("Roguelike", ignoreCase = true) ||
                    it.category.contains("Battle", ignoreCase = true)
                }
            }
            if (actionGames.isNotEmpty()) {
                GameSectionRow(
                    title = "Action & Adventure Games",
                    games = actionGames,
                    onGameClick = onGameClick
                )
            }

            // 6. Row: Must-Play Netflix Exclusives
            val exclusiveGames = remember(games) {
                games.filter {
                    it.title.contains("Netflix", ignoreCase = true) ||
                    it.title.contains("Definitive", ignoreCase = true) ||
                    it.title.contains("Squid", ignoreCase = true) ||
                    it.title.contains("Stranger", ignoreCase = true)
                }
            }
            if (exclusiveGames.isNotEmpty()) {
                GameSectionRow(
                    title = "Must-Play Netflix Exclusives",
                    games = exclusiveGames,
                    onGameClick = onGameClick
                )
            }

            // 7. Row: Puzzle, Strategy & Management
            val puzzleGames = remember(games) {
                games.filter {
                    it.category.contains("Puzzle", ignoreCase = true) ||
                    it.category.contains("Strategy", ignoreCase = true) ||
                    it.category.contains("Mystery", ignoreCase = true) ||
                    it.category.contains("Casual", ignoreCase = true)
                }
            }
            if (puzzleGames.isNotEmpty()) {
                GameSectionRow(
                    title = "Puzzle, Strategy & Mystery Games",
                    games = puzzleGames,
                    onGameClick = onGameClick
                )
            }

            // 8. Row: All Mobile Games Catalog
            val reversedGames = remember(games) { games.reversed() }
            GameSectionRow(
                title = "All Mobile Games Catalog",
                games = reversedGames,
                onGameClick = onGameClick
            )
        }
    }
}

/**
 * Exact Hero Card matching the HomeScreen HeroBanner's form factor, width proportion,
 * shape, ambient glow, typography, and button actions.
 */
@Composable
fun GameHeroBanner(
    game: GameItem,
    onGetGameClick: () -> Unit,
    onDetailsClick: () -> Unit,
    cardHeight: Dp = 520.dp,
    cardBottomBgColor: Color = Color(0xFF20211A),
    parallaxOffsetProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val animatedCardBottomBg by animateColorAsState(
        targetValue = cardBottomBgColor,
        animationSpec = tween(400),
        label = "games_hero_card_bg"
    )

    val cardShape = remember { RoundedCornerShape(24.dp) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Ambient glow behind the card matching HomeScreen HeroBanner
        Box(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .widthIn(max = 410.dp)
                .height(cardHeight)
                .graphicsLayer {
                    scaleX = 1.25f
                    scaleY = 1.15f
                    translationY = 80f
                }
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animatedCardBottomBg.copy(alpha = 0.90f),
                                animatedCardBottomBg.copy(alpha = 0.40f),
                                Color.Transparent
                            ),
                            center = Offset(this.size.width / 2f, this.size.height / 2f),
                            radius = this.size.width * 0.85f
                        )
                    )
                }
        )

        // Hero Card container matching HomeScreen HeroBanner
        Box(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .widthIn(max = 410.dp)
                .height(cardHeight)
                .shadow(
                    elevation = 24.dp,
                    shape = cardShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.9f),
                    spotColor = Color.Black.copy(alpha = 0.95f)
                )
                .clip(cardShape)
                .border(1.dp, Color.White.copy(alpha = 0.10f), cardShape)
                .background(Color(0xFF0F0E14))
                .testTag("games_hero_banner")
        ) {
            // Hero Background Image / Poster with full hardware bitmap rendering
            if (game.bannerRes != null || game.bannerUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(game.bannerRes ?: game.bannerUrl)
                        .crossfade(150)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build(),
                    contentDescription = game.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = parallaxOffsetProvider()
                            scaleX = 1.12f
                            scaleY = 1.12f
                        },
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF1E1B2E),
                                    Color(0xFF14121F),
                                    NetflixBlack
                                )
                            )
                        )
                )
            }

            // Cinematic Gradients Overlay matching HomeScreen HeroBanner
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val cardHeightPx = this.size.height
                        val dimmedBgColor = Color(
                            red = animatedCardBottomBg.red * 0.2f,
                            green = animatedCardBottomBg.green * 0.2f,
                            blue = animatedCardBottomBg.blue * 0.2f,
                            alpha = 1.0f
                        )

                        val baseShadow = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.35f to Color.Transparent,
                            1.0f to dimmedBgColor,
                            startY = 0f,
                            endY = cardHeightPx
                        )

                        val colorInfusion = Brush.verticalGradient(
                            0.60f to Color.Transparent,
                            1.0f to animatedCardBottomBg.copy(alpha = 0.95f),
                            startY = 0f,
                            endY = cardHeightPx
                        )

                        val textGlow = Brush.radialGradient(
                            colors = listOf(
                                animatedCardBottomBg.copy(alpha = 0.70f),
                                Color.Transparent
                            ),
                            center = Offset(this.size.width / 2f, this.size.height * 0.75f),
                            radius = this.size.width * 0.65f
                        )

                        val vignette = Brush.verticalGradient(
                            0.85f to Color.Transparent,
                            1.0f to dimmedBgColor,
                            startY = 0f,
                            endY = cardHeightPx
                        )

                        onDrawBehind {
                            drawRect(baseShadow)
                            drawRect(brush = colorInfusion)
                            drawRect(brush = textGlow, blendMode = androidx.compose.ui.graphics.BlendMode.Screen)
                            drawRect(vignette)
                        }
                    }
            )

            // Red N Logo inside the card (Top-Left corner)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                NetflixNLogo(size = 28.dp)
            }

            // Overlay Content inside card bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // "N GAMES" Badge above title
                NGamesBadge(
                    nSize = 13.dp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Title Text
                Text(
                    text = game.title.uppercase(),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-0.5).sp,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            offset = Offset(0f, 4f),
                            blurRadius = 8f
                        )
                    ),
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable { onDetailsClick() }
                        .testTag("games_hero_title")
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Game Tags with dot separators
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    val tags = listOf(game.category, game.maturityRating, game.sizeDisplay)
                    tags.forEachIndexed { index, tag ->
                        Text(
                            text = tag,
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (index < tags.size - 1) {
                            Text(
                                text = " • ",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                // Action Buttons Row: Side-by-Side Get Game (Solid White) and Details (Glassy Dark)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Primary "Get Game" Button (Solid White)
                    Button(
                        onClick = onGetGameClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .weight(1f)
                            .testTag("games_hero_get_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GetApp,
                                contentDescription = "Get Game",
                                tint = Color.Black,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Get Game",
                                color = Color.Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Details Button
                    val listGlassBorder = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.10f)
                        )
                    )

                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF212121).copy(alpha = 0.80f))
                            .border(
                                width = 1.dp,
                                brush = listGlassBorder,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onDetailsClick() }
                            .testTag("games_hero_details_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Details",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Details",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top 10 Games section row featuring authentic Netflix-styled 3D ranked numbers
 */
@Composable
fun Top10GamesSectionRow(
    title: String,
    games: List<GameItem>,
    onGameClick: (GameItem) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(games, key = { index, item -> "top10_${item.id}_$index" }) { index, game ->
                Top10GamePosterCard(
                    rank = index + 1,
                    game = game,
                    onClick = { onGameClick(game) }
                )
            }
        }
    }
}

@Composable
fun Top10GamePosterCard(
    rank: Int,
    game: GameItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(6.dp)

    Box(
        modifier = Modifier
            .width(135.dp)
            .height(165.dp)
            .clickable { onClick() }
            .testTag("top_10_game_$rank")
    ) {
        // Giant Stylized Rank Number (authentic Netflix stroked 3D block typography)
        Text(
            text = "$rank",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                fontSize = 96.sp,
                color = Color(0xFF595959),
                shadow = Shadow(
                    color = Color.Black,
                    offset = Offset(3f, 3f),
                    blurRadius = 0f
                )
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-4).dp, y = 12.dp)
        )

        // Poster Card positioned slightly to the right
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(105.dp)
                .height(155.dp)
                .clip(cardShape)
                .border(0.8.dp, Color.White.copy(alpha = 0.15f), cardShape)
                .background(Color(0xFF1E1E24))
        ) {
            if (game.bannerRes != null || game.bannerUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(game.bannerRes ?: game.bannerUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = game.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF262530)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Top-left mini N badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                NetflixNLogo(size = 14.dp)
            }
        }
    }
}

@Composable
fun GameSectionRow(
    title: String,
    games: List<GameItem>,
    onGameClick: (GameItem) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 17.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(games, key = { it.id }) { game ->
                GamePosterCard(game = game, onClick = { onGameClick(game) })
            }
        }
    }
}

@Composable
fun GamePosterCard(
    game: GameItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(6.dp)

    Column(
        modifier = Modifier
            .width(118.dp)
            .clickable { onClick() }
            .testTag("game_card_${game.id}")
    ) {
        Box(
            modifier = Modifier
                .width(118.dp)
                .height(165.dp)
                .clip(cardShape)
                .border(0.8.dp, Color.White.copy(alpha = 0.12f), cardShape)
                .background(Color(0xFF1E1E24))
        ) {
            if (game.bannerRes != null || game.bannerUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(game.bannerRes ?: game.bannerUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = game.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF262530)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Top-left mini N badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                NetflixNLogo(size = 14.dp)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = game.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = game.category,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Netflix-styled bottom sheet detailing game info, artwork, and Play Store installation link.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailSheet(
    game: GameItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141414),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
        ) {
            // Header Image & Controls
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                if (game.bannerRes != null || game.bannerUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(game.bannerRes ?: game.bannerUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = game.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF22222A))
                    )
                }

                // Vignette at bottom of image
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF141414)),
                                startY = 300f
                            )
                        )
                )

                // Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // N GAMES badge on image
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 8.dp)
                ) {
                    NGamesBadge(nSize = 16.dp)
                }
            }

            // Info Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = game.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Developer: ${game.developer}",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )

                // Metadata Badges Row
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF2E2E38), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = game.maturityRating,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "•",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp
                    )

                    Text(
                        text = game.category,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "•",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp
                    )

                    Text(
                        text = game.sizeDisplay,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                }

                // Primary Red "Get Game" Button linking to Google Play Store
                Button(
                    onClick = {
                        openPlayStore(context, game.title)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GetApp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Get Game",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Game Description
                Text(
                    text = game.description,
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Feature Highlights
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1D24), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    FeatureChip(label = "No Ads", sublabel = "Included with Netflix")
                    FeatureChip(label = "No In-App Purchases", sublabel = "Unlimited Access")
                    FeatureChip(label = "Cloud Saves", sublabel = "Play anywhere")
                }
            }
        }
    }
}

@Composable
private fun FeatureChip(label: String, sublabel: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = sublabel,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 9.5.sp
        )
    }
}

private fun openPlayStore(context: android.content.Context, gameTitle: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://search?q=${Uri.encode(gameTitle)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/search?q=${Uri.encode(gameTitle)}&c=apps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
        } catch (_: Throwable) {
            Toast.makeText(context, "Opening Google Play Store for $gameTitle", Toast.LENGTH_SHORT).show()
        }
    }
}
