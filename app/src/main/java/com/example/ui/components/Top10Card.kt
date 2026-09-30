package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem

@Composable
fun Top10PosterCard(
    rank: Int,
    media: MediaItem,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .width(if (rank >= 10) 150.dp else 138.dp)
            .height(170.dp)
            .clickable { onClick() }
            .testTag("top_10_item_$rank")
    ) {
        // Giant Stylized Rank Number with Netflix 3D outline stroke
        Text(
            text = "$rank",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                fontSize = if (rank >= 10) 88.sp else 98.sp,
                color = Color(0xFF2E2E32),
                shadow = Shadow(
                    color = Color.Black,
                    offset = Offset(4f, 4f),
                    blurRadius = 8f
                )
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-4).dp, y = 14.dp)
        )

        // Accent outline layer with specular highlight for clay-like volumetric depth
        Text(
            text = "$rank",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                fontSize = if (rank >= 10) 88.sp else 98.sp,
                color = Color(0xFF62606C),
                shadow = Shadow(
                    color = Color(0xFFC4C2CF),
                    offset = Offset(-1f, -1f),
                    blurRadius = 2f
                )
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-2).dp, y = 12.dp)
        )

        // Actual Media Poster layered on top to the right
        MediaPosterCard(
            media = media,
            width = 108.dp,
            height = 160.dp,
            showRank = false,
            isLocked = isLocked,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 4.dp),
            onClick = onClick
        )
    }
}
