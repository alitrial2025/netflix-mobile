package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.data.SeriesEpisodeOrder
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.theme.LocalPlatformDesign
import java.util.Locale

@Composable
internal fun PrimeDetailActions(inList: Boolean, rating: String?, onTrailer: () -> Unit, onList: () -> Unit,
    onRating: (String) -> Unit, onShare: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PrimeAction("Trailer", Icons.Default.PlayArrow, false, "detail_trailer_button", onTrailer)
        PrimeAction("Watchlist", if (inList) Icons.Default.Check else Icons.Default.Add, inList, "detail_my_list_button", onList)
        PrimeAction("Like", Icons.Default.ThumbUp, rating == "LIKE", "prime_detail_like", { onRating("LIKE") })
        PrimeAction("Not for me", Icons.Default.ThumbDown, rating == "DISLIKE", "prime_detail_dislike", { onRating("DISLIKE") })
        PrimeAction("Love", Icons.Default.FavoriteBorder, rating == "DOUBLE_LIKE", "prime_detail_love", { onRating("DOUBLE_LIKE") })
        PrimeAction("Share", Icons.Default.Share, false, "prime_detail_share", onShare)
    }
}

@Composable
private fun PrimeAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean,
                        tag: String, onClick: () -> Unit) {
    val design = LocalPlatformDesign.current
    OutlinedButton(onClick, Modifier.heightIn(min = 48.dp).testTag(tag), shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp), border = BorderStroke(1.dp, design.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White,
            containerColor = if (selected) design.raised else Color.Transparent)) {
        Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(text, fontSize = 12.sp)
    }
}

@Composable
internal fun PrimeDetailFacts(media: MediaItem) {
    val design = LocalPlatformDesign.current
    HorizontalDivider(color = design.outline, modifier = Modifier.padding(top = 14.dp))
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("prime_detail_facts")) {
        val rating = if (media.voteAverage > 0 && media.voteCount > 0) String.format(Locale.US, "%.1f/5", media.voteAverage / 2) else "Not rated"
        listOf("Ratings · TMDB" to rating, "Releases" to media.releaseYear.takeIf { it > 0 }?.toString().orEmpty().ifBlank { "—" },
            (if (media.type == MediaType.TV_SHOW) "Seasons" else "Duration") to
                if (media.type == MediaType.TV_SHOW) (SeriesEpisodeOrder.seasons(media.id)?.size ?: media.totalSeasons.coerceAtLeast(1)).toString()
                else media.durationOrSeasons.ifBlank { "—" }).forEach { (label, value) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(label, color = Color.LightGray, fontSize = 12.sp)
                Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    HorizontalDivider(color = design.outline)
}
