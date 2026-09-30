package com.example.ui.components

import android.widget.Toast
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.model.UserProfile
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixBorderGray
import com.example.ui.theme.NetflixDarkGray
import com.example.ui.theme.NetflixRed
import com.example.ui.viewmodel.CategoryFilter

@Composable
fun NetflixTopBar(
    activeProfile: UserProfile,
    currentCategory: CategoryFilter,
    selectedGenre: String?,
    onCategorySelected: (CategoryFilter, String?) -> Unit,
    onNewAndHotClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier,
    isGamesAllowed: Boolean = true,
    unreadNotificationCount: Int = 0,
    scrollFractionProvider: () -> Float = { 0f }
) {
    var showCategoriesDropdown by remember { mutableStateOf(false) }
    var pillWidth by remember { mutableStateOf(0.dp) }
    val context = LocalContext.current

    val allGenres = listOf(
        "Action & Adventure", "Crime Thrillers", "Sci-Fi & Cyberpunk",
        "Dramas", "Anime & Animation", "Comedies", "Documentaries",
        "Horror & Suspense", "International & K-Dramas", "Kids & Family",
        "Romance", "Fantasy & Supernatural"
    )

    val isFilterActive = currentCategory != CategoryFilter.ALL || selectedGenre != null

    val bgGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.65f),
                Color.Black.copy(alpha = 0.25f),
                Color.Transparent
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val scrollFraction = scrollFractionProvider()
                val collapseAmount = 48.dp.toPx() * scrollFraction
                val drawHeight = (size.height - collapseAmount).coerceAtLeast(0f)

                // Smoothly fade out the gradient and fade in the solid glassy background with bottom border line
                drawRect(
                    brush = bgGradient,
                    size = size.copy(height = drawHeight),
                    alpha = (1f - scrollFraction).coerceIn(0f, 1f)
                )
                drawRect(
                    color = Color(0xFF0F0E13).copy(alpha = (scrollFraction * 0.96f).coerceIn(0f, 0.96f)),
                    size = size.copy(height = drawHeight),
                    alpha = scrollFraction
                )
                if (scrollFraction > 0.05f) {
                    drawLine(
                        color = Color.White.copy(alpha = (scrollFraction * 0.12f).coerceIn(0f, 0.12f)),
                        start = Offset(0f, drawHeight),
                        end = Offset(size.width, drawHeight),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            .statusBarsPadding()
            .padding(top = 4.dp, bottom = 6.dp)
    ) {
        // Main Header Row: [N Home] ... [Cast] [Download] [Notifications]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Netflix Ribbon N Logo and "Home" title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCategorySelected(CategoryFilter.ALL, null) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                if (currentCategory == CategoryFilter.GAMES) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 6.dp)
                    )
                }
                NetflixNLogo(size = 40.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        currentCategory == CategoryFilter.TV_SHOWS -> "Series"
                        currentCategory == CategoryFilter.MOVIES -> "Films"
                        currentCategory == CategoryFilter.GAMES -> "Games"
                        selectedGenre != null -> selectedGenre
                        else -> "Home"
                    },
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("top_bar_title_home")
                )
            }

            // Right: Cast, Download, Notifications icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Download Button
                IconButton(
                    onClick = onDownloadsClick,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("top_bar_download_button")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_netflix_download_custom),
                        contentDescription = "Downloads",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Notification Bell with Badge "5"
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clickable { onNotificationsClick() }
                        .testTag("top_bar_notifications_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )

                    if (unreadNotificationCount > 0) Box(
                        Modifier.align(Alignment.TopEnd).size(18.dp).background(NetflixRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(unreadNotificationCount.coerceAtMost(99).toString(), color = Color.White, fontSize = 9.sp)
                    }
                }
            }
        }

        if (currentCategory != CategoryFilter.GAMES) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val scrollFraction = scrollFractionProvider()
                        // Translate upwards smoothly on scroll
                        translationY = -scrollFraction * 48.dp.toPx()
                        alpha = (1f - scrollFraction * 1.5f).coerceIn(0f, 1f)
                    }
            ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))

                // Categories/Pills Row: Shows (rounded left, flat right), Middle tabs (flat edges), Last tab (flat left, rounded right) with even spacing
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val firstTabShape = RoundedCornerShape(24.dp)
                    val middleTabShape = RoundedCornerShape(16.dp)
                    val lastTabShape = RoundedCornerShape(16.dp)

                    // Optional Clear Filter ("X") pill if a filter is active
                    if (isFilterActive) {
                        CategoryPill(
                            label = "",
                            isSelected = false,
                            onClick = { onCategorySelected(CategoryFilter.ALL, null) },
                            leadingIcon = Icons.Default.Close,
                            testTag = "category_pill_clear",
                            tabShape = firstTabShape
                        )
                    }

                    // 1. Shows Pill (First Tab: rounded at left edge, flat at right edge)
                    CategoryPill(
                        label = "Series",
                        isSelected = currentCategory == CategoryFilter.TV_SHOWS && selectedGenre == null,
                        onClick = {
                            if (currentCategory == CategoryFilter.TV_SHOWS && selectedGenre == null) {
                                onCategorySelected(CategoryFilter.ALL, null)
                            } else {
                                onCategorySelected(CategoryFilter.TV_SHOWS, null)
                            }
                        },
                        tabShape = if (isFilterActive) middleTabShape else firstTabShape
                    )

                    // 2. Movies Pill (Middle Tab: flat at edges)
                    CategoryPill(
                        label = "Films",
                        isSelected = currentCategory == CategoryFilter.MOVIES && selectedGenre == null,
                        onClick = {
                            if (currentCategory == CategoryFilter.MOVIES && selectedGenre == null) {
                                onCategorySelected(CategoryFilter.ALL, null)
                            } else {
                                onCategorySelected(CategoryFilter.MOVIES, null)
                            }
                        },
                        tabShape = middleTabShape
                    )

                    // 3. Games Pill (Middle Tab: flat at edges)
                    if (isGamesAllowed) {
                        CategoryPill(
                            label = "Games",
                            isSelected = currentCategory == CategoryFilter.GAMES && selectedGenre == null,
                            onClick = {
                                if (currentCategory == CategoryFilter.GAMES && selectedGenre == null) {
                                    onCategorySelected(CategoryFilter.ALL, null)
                                } else {
                                    onCategorySelected(CategoryFilter.GAMES, null)
                                }
                            },
                            tabShape = middleTabShape
                        )
                    }

                    // 4. New & Hot Pill (Middle Tab: flat at edges)
                    CategoryPill(
                        label = "New & Hot",
                        isSelected = false,
                        onClick = onNewAndHotClick,
                        tabShape = middleTabShape
                    )

                    // 5. Categories Dropdown Pill with Chevron (Last Tab: flat at left edge, rounded at right edge)
                    val rotationAngle by animateFloatAsState(
                        targetValue = if (showCategoriesDropdown) 180f else 0f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    )
                    val density = LocalDensity.current

                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            pillWidth = with(density) { coords.size.width.toDp() }
                        }
                    ) {
                        // Show the default pill ONLY when the dropdown is closed to prevent duplication/overlapping
                        if (!showCategoriesDropdown) {
                            CategoryPill(
                                label = if (selectedGenre != null) selectedGenre else "Categories",
                                isSelected = selectedGenre != null || currentCategory == CategoryFilter.CATEGORIES,
                                trailingIcon = Icons.Default.ArrowDropDown,
                                trailingIconRotation = rotationAngle,
                                onClick = { showCategoriesDropdown = true },
                                tabShape = lastTabShape
                            )
                        } else {
                            // Invisible placeholder so the parent Row maintains its exact spacing
                            Spacer(
                                modifier = Modifier
                                    .height(44.dp)
                                    .width(pillWidth)
                            )
                        }

                        if (showCategoriesDropdown) {
                            val isSelected = selectedGenre != null || currentCategory == CategoryFilter.CATEGORIES
                            
                            // High-contrast, solid modern charcoal background to guarantee high readability over any movie poster
                            val solidBgColor = Color(0xFF201E26)
                            val solidBorderBrush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.28f),
                                    Color.White.copy(alpha = 0.12f)
                                )
                            )

                            val combinedShape = RoundedCornerShape(
                                topStart = 16.dp, // Fully matches the pill's topStart for a unified look
                                topEnd = 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp
                            )

                            Popup(
                                onDismissRequest = { showCategoriesDropdown = false },
                                properties = PopupProperties(focusable = true),
                                offset = IntOffset(0, 0)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .width(pillWidth)
                                        .claymorphic(
                                            shape = combinedShape,
                                            surfaceColor = Color(0xFF22202A),
                                            highlightColor = Color.White,
                                            shadowColor = Color.Black,
                                            elevation = 14.dp,
                                            strokeWidth = 1.3.dp,
                                            highlightAlpha = 0.52f,
                                            depthAlpha = 0.82f,
                                            gradientCurvature = 0.22f
                                        )
                                ) {
                                    // Unified Tab Header within the popup container (same background & style as container)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .clickable { showCategoriesDropdown = false }
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = if (selectedGenre != null) selectedGenre else "Categories",
                                            color = Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier
                                                .size(18.dp)
                                                .graphicsLayer {
                                                    rotationZ = rotationAngle
                                                }
                                        )
                                    }

                                    // Crisp border separator
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(0.8.dp)
                                            .background(Color.White.copy(alpha = 0.12f))
                                    )

                                    // Category List directly flowing inside the unified solid card
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                            .verticalScroll(rememberScrollState())
                                            .padding(vertical = 4.dp)
                                    ) {
                                        val genresToDisplay = listOf("All Categories") + allGenres
                                        genresToDisplay.forEach { genre ->
                                            val isGenreSelected = (genre == "All Categories" && selectedGenre == null) || (selectedGenre == genre)
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(38.dp)
                                                    .clickable {
                                                        showCategoriesDropdown = false
                                                        if (genre == "All Categories") {
                                                            onCategorySelected(CategoryFilter.ALL, null)
                                                        } else {
                                                            onCategorySelected(CategoryFilter.CATEGORIES, genre)
                                                        }
                                                    }
                                                    .padding(horizontal = 16.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Text(
                                                    text = genre,
                                                    color = if (isGenreSelected) Color.White else Color.White.copy(alpha = 0.75f),
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (isGenreSelected) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

/**
 * Tactile Claymorphic Netflix Category Tab component.
 * Features an inflated matte clay body with top specular highlight, subtle depth bevel,
 * and responsive spring press physics for maximum tactile delight and smooth 60fps scrolling.
 */
@Composable
private fun CategoryPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailingIconRotation: Float = 0f,
    testTag: String = "category_pill_${label.lowercase().replace(" ", "_").replace("&", "and")}",
    tabShape: Shape = RoundedCornerShape(16.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "pill_scale"
    )

    // Porcelain white clay when active vs sleek deep charcoal clay when inactive
    val surfaceColor = if (isSelected) Color(0xFFF2F2F6) else Color(0xFF22202A)
    val contentColor = Color.White
    val elevation = if (isPressed) 2.dp else if (isSelected) 8.dp else 5.dp
    val highlightAlpha = if (isSelected) 0.78f else 0.48f
    val depthAlpha = if (isSelected) 0.38f else 0.80f

    Box(
        modifier = Modifier
            .height(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(tabShape)
            .background(if (isSelected) Color.White.copy(alpha = .28f) else Color.White.copy(alpha = .12f))
            .border(1.dp, Color.White.copy(alpha = .18f), tabShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(
                horizontal = if (leadingIcon != null && label.isEmpty()) 12.dp else 16.dp
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                if (label.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }

            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    letterSpacing = 0.2.sp
                )
            }

            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = if (isSelected) 0.9f else 0.80f),
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            rotationZ = trailingIconRotation
                        }
                )
            }
        }
    }
}



