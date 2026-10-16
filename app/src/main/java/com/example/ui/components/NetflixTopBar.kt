package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.UserProfile
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
    scrollFractionProvider: () -> Float = { 0f },
    onCastClick: () -> Unit = {}
) {
    var showCategoriesDropdown by remember { mutableStateOf(false) }
    val allGenres = remember {
        listOf(
            "Action & Adventure", "Crime Thrillers", "Sci-Fi & Cyberpunk",
            "Dramas", "Anime & Animation", "Comedies", "Documentaries",
            "Horror & Suspense", "International & K-Dramas", "Kids & Family",
            "Romance", "Fantasy & Supernatural"
        )
    }
    val isFilterActive = currentCategory != CategoryFilter.ALL || selectedGenre != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val fraction = scrollFractionProvider().coerceIn(0f, 1f)
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = .24f + fraction * .66f),
                            Color.Black.copy(alpha = fraction * .90f)
                        )
                    )
                )
            }
            .statusBarsPadding()
            .padding(top = 4.dp, bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { onCategorySelected(CategoryFilter.ALL, null) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentCategory == CategoryFilter.GAMES) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                NetflixNLogo(size = 34.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        currentCategory == CategoryFilter.TV_SHOWS -> "Series"
                        currentCategory == CategoryFilter.MOVIES -> "Films"
                        currentCategory == CategoryFilter.GAMES -> "Games"
                        selectedGenre != null -> selectedGenre
                        else -> "Home"
                    },
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("top_bar_title_home")
                )
            }
            IconButton(onClick = onCastClick, modifier = Modifier.size(48.dp).testTag("top_bar_cast_button")) {
                Icon(Icons.Default.Cast, "Cast", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onDownloadsClick, modifier = Modifier.size(48.dp).testTag("top_bar_download_button")) {
                Icon(
                    painterResource(R.drawable.ic_netflix_download_custom), "Downloads",
                    tint = Color.White, modifier = Modifier.size(22.dp)
                )
            }
            IconButton(
                onClick = onNotificationsClick,
                modifier = Modifier.size(48.dp).testTag("top_bar_notifications_button")
                    .semantics {
                        contentDescription = if (unreadNotificationCount > 0) {
                            "Notifications, $unreadNotificationCount unread"
                        } else "Notifications"
                    }
            ) {
                Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.NotificationsNone, null, tint = Color.White, modifier = Modifier.size(23.dp))
                    if (unreadNotificationCount > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd).size(7.dp)
                                .background(NetflixRed, CircleShape)
                                .border(1.dp, Color(0xFF242124), CircleShape)
                        )
                    }
                }
            }
        }

        if (currentCategory != CategoryFilter.GAMES) {
            // Collapse the measured row as well as its opacity, so a scrolled header
            // cannot leave an invisible strip intercepting touches over the catalog.
            Box(
                Modifier.fillMaxWidth().clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val fraction = scrollFractionProvider().coerceIn(0f, 1f)
                        layout(placeable.width, (placeable.height * (1f - fraction)).toInt()) {
                            placeable.placeRelative(0, -(placeable.height * fraction).toInt())
                        }
                    }
                    .graphicsLayer { alpha = (1f - scrollFractionProvider() * 1.5f).coerceIn(0f, 1f) }
            ) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFilterActive) {
                        CategoryPill(
                            label = "", isSelected = false,
                            onClick = { onCategorySelected(CategoryFilter.ALL, null) },
                            leadingIcon = Icons.Default.Close, testTag = "category_pill_clear",
                            endShape = RoundedCornerShape(24.dp)
                        )
                    }
                    CategoryPill(
                        label = "Series",
                        endShape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 13.dp, bottomEnd = 13.dp),
                        isSelected = currentCategory == CategoryFilter.TV_SHOWS && selectedGenre == null,
                        onClick = {
                            onCategorySelected(
                                if (currentCategory == CategoryFilter.TV_SHOWS && selectedGenre == null) CategoryFilter.ALL
                                else CategoryFilter.TV_SHOWS, null
                            )
                        }
                    )
                    CategoryPill(
                        label = "Films",
                        isSelected = currentCategory == CategoryFilter.MOVIES && selectedGenre == null,
                        onClick = {
                            onCategorySelected(
                                if (currentCategory == CategoryFilter.MOVIES && selectedGenre == null) CategoryFilter.ALL
                                else CategoryFilter.MOVIES, null
                            )
                        }
                    )
                    if (isGamesAllowed) {
                        CategoryPill("Games", currentCategory == CategoryFilter.GAMES, {
                            onCategorySelected(CategoryFilter.GAMES, null)
                        })
                    }
                    CategoryPill("New & hot", false, onNewAndHotClick, testTag = "category_pill_new_and_hot")
                    Box {
                        val rotation by animateFloatAsState(
                            if (showCategoriesDropdown) 180f else 0f,
                            tween(180, easing = FastOutSlowInEasing), label = "category_chevron"
                        )
                        CategoryPill(
                            label = selectedGenre ?: "Categories",
                            isSelected = selectedGenre != null || currentCategory == CategoryFilter.CATEGORIES,
                            onClick = { showCategoriesDropdown = !showCategoriesDropdown },
                            trailingIcon = Icons.Default.ArrowDropDown,
                            trailingIconRotation = rotation,
                            testTag = "category_pill_categories",
                            endShape = RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp, topEnd = 24.dp, bottomEnd = 24.dp)
                        )
                        DropdownMenu(
                            expanded = showCategoriesDropdown,
                            onDismissRequest = { showCategoriesDropdown = false },
                            modifier = Modifier.width(248.dp).heightIn(max = 360.dp),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color(0xFF242226),
                            tonalElevation = 0.dp
                        ) {
                            (listOf("All Categories") + allGenres).forEach { genre ->
                                val genreSelected = if (genre == "All Categories") selectedGenre == null else selectedGenre == genre
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            genre, color = if (genreSelected) Color.White else Color(0xFFD1CDD2),
                                            fontWeight = if (genreSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                    },
                                    onClick = {
                                        showCategoriesDropdown = false
                                        onCategorySelected(
                                            if (genre == "All Categories") CategoryFilter.ALL else CategoryFilter.CATEGORIES,
                                            genre.takeUnless { it == "All Categories" }
                                        )
                                    },
                                    modifier = Modifier.semantics { selected = genreSelected }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Each independent glass chip keeps a 48 dp touch target around its 40 dp surface. */
@Composable
private fun CategoryPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailingIconRotation: Float = 0f,
    testTag: String = "category_pill_${label.lowercase().replace(" ", "_").replace("&", "and")}",
    endShape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(13.dp)
) {
    val shape = endShape
    Box(
        Modifier.heightIn(min = 48.dp).clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                selected = isSelected
                if (label.isEmpty()) contentDescription = "Clear category filter"
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.height(40.dp).referenceGlass(shape, isSelected)
                .padding(horizontal = if (label.isEmpty()) 14.dp else 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                if (label.isNotEmpty()) Spacer(Modifier.width(5.dp))
            }
            if (label.isNotEmpty()) {
                Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            if (trailingIcon != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    trailingIcon, null, tint = Color.White.copy(alpha = .90f),
                    modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = trailingIconRotation }
                )
            }
        }
    }
}
