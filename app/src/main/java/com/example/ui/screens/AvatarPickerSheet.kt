package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.data.model.ProfileIconCatalog
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixCardBg

private const val ALL_ICONS = "all"

@Composable
fun AvatarPickerSheet(
    currentAvatarUrl: String?,
    onSelectAvatar: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by rememberSaveable { mutableStateOf(ALL_ICONS) }
    val categories = ProfileIconCatalog.categories
    val category = remember(selectedCategory) { categories.firstOrNull { it.id == selectedCategory } }
    val displayedUrls = category?.icons ?: ProfileIconCatalog.allIcons
    val gridState = rememberLazyGridState()
    var previousCategory by rememberSaveable { mutableStateOf(selectedCategory) }
    LaunchedEffect(selectedCategory) {
        if (previousCategory != selectedCategory) {
            gridState.scrollToItem(0)
            previousCategory = selectedCategory
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("avatar_picker_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = "Choose an Icon",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )

            if (currentAvatarUrl != null) {
                AsyncImage(
                    model = rememberAvatarRequest(currentAvatarUrl),
                    contentDescription = "Current Icon",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
        }

        // Category Filter Chips
        LazyRow(
            modifier = Modifier.testTag("avatar_categories"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = ALL_ICONS) {
                IconCategoryPill("All Icons", ALL_ICONS, selectedCategory) { selectedCategory = ALL_ICONS }
            }
            items(categories, key = { it.id }, contentType = { "icon_category" }) { category ->
                IconCategoryPill(category.title, category.id, selectedCategory) {
                    selectedCategory = category.id
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Avatar Grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 80.dp),
            state = gridState,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f).testTag("avatar_icon_grid")
        ) {
            itemsIndexed(displayedUrls, key = { _, url -> url }, contentType = { _, _ -> "profile_icon" }) { index, url ->
                val isCurrent = url == currentAvatarUrl
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NetflixCardBg)
                        .then(
                            if (isCurrent) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                            else Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        )
                        .clickable(role = Role.Button, onClickLabel = "Use profile icon") { onSelectAvatar(url) }
                        .semantics { selected = isCurrent }
                        .testTag("avatar_icon_item")
                ) {
                    AsyncImage(
                        model = rememberAvatarRequest(url),
                        contentDescription = if (isCurrent) "Current profile icon" else "${category?.title ?: "Profile"} icon ${index + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun IconCategoryPill(title: String, id: String, selectedId: String, onClick: () -> Unit) {
    val isSelected = id == selectedId
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier.height(48.dp).testTag("avatar_category_$id"),
        label = { Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = Color.White,
            selectedContainerColor = Color.White,
            selectedLabelColor = Color.Black
        ),
        border = BorderStroke(1.dp, if (isSelected) Color.White else Color(0xFF737373)),
        shape = RoundedCornerShape(50)
    )
}

/** Coil decodes to the tile's measured size and reuses its cached request on recomposition. */
@Composable
private fun rememberAvatarRequest(url: String): ImageRequest {
    val context = LocalContext.current
    return remember(context, url) {
        ImageRequest.Builder(context)
            .data(url)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .build()
    }
}
