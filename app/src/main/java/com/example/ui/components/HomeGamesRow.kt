package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.GameItem

/** The compact discovery rail uses the same real catalog and callbacks as Games. */
@Composable
internal fun HomeGamesRow(
    games: List<GameItem>,
    onGameClick: (GameItem) -> Unit,
    onOpenGames: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (games.isEmpty()) return
    val iconSize = ((LocalConfiguration.current.screenWidthDp - 48) / 3.35f)
        .dp.coerceIn(92.dp, 132.dp)
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("home_mobile_games")) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Mobile Games", color = Color.White, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Row(Modifier.height(48.dp).clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenGames).padding(horizontal = 8.dp)
                .testTag("home_open_games"), verticalAlignment = Alignment.CenterVertically) {
                Text("Explore", color = Color.White, fontSize = 13.sp)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White,
                    modifier = Modifier.size(22.dp))
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(games.distinctBy { it.id }, key = { it.id }, contentType = { "home_game" }) { game ->
                Column(Modifier.width(iconSize).clip(RoundedCornerShape(4.dp))
                    .clickable { onGameClick(game) }.testTag("home_game_${game.id}")) {
                    Box(Modifier.size(iconSize).clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF252525)), contentAlignment = Alignment.Center) {
                        val model = game.iconUrl?.takeIf { it.isNotBlank() }
                            ?: game.bannerUrl?.takeIf { it.isNotBlank() } ?: game.bannerRes
                        if (model != null) {
                            AsyncImage(rememberPosterRequest(model, iconSize, iconSize),
                                contentDescription = game.title, modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.SportsEsports, null, tint = Color.White.copy(alpha = .5f),
                                modifier = Modifier.size(36.dp))
                        }
                        NetflixNLogo(size = 15.dp,
                            modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(game.title, color = Color.White, fontSize = 12.sp, lineHeight = 15.sp,
                        maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(game.category, color = Color.White.copy(alpha = .56f), fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
