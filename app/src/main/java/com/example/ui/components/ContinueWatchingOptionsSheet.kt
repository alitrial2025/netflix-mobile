package com.example.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.WatchProgressEntity
import com.example.data.model.MediaItem
import com.example.ui.theme.NetflixMediumGray
import com.example.ui.theme.NetflixRed

@Composable
fun ContinueWatchingOptionsSheet(
    media: MediaItem,
    progress: WatchProgressEntity,
    isInWatchlist: Boolean,
    onDismiss: () -> Unit,
    onEpisodesAndInfoClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onRemoveFromRowClick: () -> Unit,
    onWatchlistToggle: () -> Unit,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .testTag("continue_watching_options_scrim")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(Color(0xFF1E1E1E))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Prevent dismissing when clicking sheet */ }
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .testTag("continue_watching_options_sheet")
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
                    .align(Alignment.CenterHorizontally)
            )

            // Header: Media Thumbnail, Title, Details, Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini Poster Thumbnail
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 66.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2B2B2B))
                ) {
                    if (media.posterUrl != null || media.bannerDrawableRes != null) {
                        AsyncImage(
                            model = media.posterUrl ?: media.bannerDrawableRes,
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
                                            Color(media.primaryColorHex)
                                        )
                                    )
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and Meta Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = media.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${media.releaseYear} • ${media.maturityRating} • ${media.durationOrSeasons}",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    )
                    if (progress.episodeTitle != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = progress.episodeTitle,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Close X Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .testTag("close_options_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.1f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Options List
            OptionsItemRow(
                icon = Icons.Outlined.Info,
                title = "Episodes & Info",
                subtitle = "Cast, synopsis and season episodes",
                onClick = onEpisodesAndInfoClick,
                testTag = "option_episodes_info"
            )

            OptionsItemRowPainter(
                painter = painterResource(id = R.drawable.ic_netflix_download_custom),
                title = if (progress.episodeTitle != null) "Download Episode" else "Download",
                subtitle = "Save offline for watching anytime",
                onClick = onDownloadClick,
                testTag = "option_download"
            )

            OptionsItemRow(
                icon = Icons.Outlined.Cancel,
                title = "Remove from row",
                subtitle = "Hide from Continue Watching list",
                iconTint = NetflixRed,
                onClick = onRemoveFromRowClick,
                testTag = "option_remove_from_row"
            )

            OptionsItemRow(
                icon = if (isInWatchlist) Icons.Default.Check else Icons.Default.Add,
                title = if (isInWatchlist) "In My List" else "Add to My List",
                subtitle = if (isInWatchlist) "Saved to your list" else "Save for later",
                onClick = onWatchlistToggle,
                testTag = "option_toggle_watchlist"
            )

            OptionsItemRowPainter(
                painter = painterResource(id = R.drawable.ic_netflix_thumbs_up),
                title = "I Like This",
                subtitle = "Tell us what you love to get better recommendations",
                onClick = onLikeClick,
                testTag = "option_like"
            )

            OptionsItemRowPainter(
                painter = painterResource(id = R.drawable.ic_netflix_share),
                title = "Share",
                subtitle = "Recommend to friends and family",
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Check out '${media.title}' on NetflixPro!\n${media.description}"
                        )
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Share '${media.title}' via")
                    context.startActivity(shareIntent)
                },
                testTag = "option_share"
            )
        }
    }
}

@Composable
private fun OptionsItemRowPainter(
    painter: androidx.compose.ui.graphics.painter.Painter,
    title: String,
    subtitle: String? = null,
    iconTint: Color = Color.White,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painter,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.5.sp
                )
            }
        }
    }
}

@Composable
private fun OptionsItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = Color.White,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(13.dp)
        )
    }
}
