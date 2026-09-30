package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.R
import com.example.ui.theme.NetflixRed

private val NetflixDarkRed = Color(0xFFB81D24)
private val NetflixBrightRed = Color(0xFFE50914)
private val NetflixRibbonHighlight = Color(0xFFF40612)

/**
 * Loads and renders the Netflix SVG wordmark from assets/netflix_logo.svg
 */
@Composable
fun NetflixSvgWordmark(
    modifier: Modifier = Modifier,
    height: Dp = 26.dp,
    testTag: String = "netflix_svg_wordmark"
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/netflix_logo.svg")
            .decoderFactory(SvgDecoder.Factory())
            .crossfade(true)
            .build(),
        contentDescription = "Netflix",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(height)
            .testTag(testTag)
    )
}

/**
 * Loads and renders the Netflix Ribbon 'N' SVG from assets/netflix_n.svg
 */
@Composable
fun NetflixSvgNLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    testTag: String = "netflix_svg_n_logo"
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/netflix_n.svg")
            .decoderFactory(SvgDecoder.Factory())
            .crossfade(true)
            .build(),
        contentDescription = "Netflix N Logo",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(size)
            .width(size * 0.62f)
            .testTag(testTag)
    )
}

/**
 * Hardware-accelerated Netflix 3D Ribbon 'N' Logo using vector drawable.
 */
@Composable
fun NetflixNLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    testTag: String = "netflix_n_logo"
) {
    val width = size * 0.60f
    Image(
        painter = painterResource(id = R.drawable.ic_netflix_n),
        contentDescription = "Netflix N Logo",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(width = width, height = size)
            .testTag(testTag)
    )
}

/**
 * Full Netflix Wordmark logo (SVG)
 */
@Composable
fun NetflixWordmark(
    modifier: Modifier = Modifier,
    height: Dp = 24.dp
) {
    NetflixSvgWordmark(
        modifier = modifier,
        height = height,
        testTag = "netflix_wordmark"
    )
}

@Composable
fun NSeriesBadge(
    modifier: Modifier = Modifier,
    nSize: Dp = 14.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetflixNLogo(size = nSize, modifier = Modifier.padding(end = 4.dp))
        Text(
            text = "SERIES",
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}

@Composable
fun NFilmBadge(
    modifier: Modifier = Modifier,
    nSize: Dp = 14.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetflixNLogo(size = nSize, modifier = Modifier.padding(end = 4.dp))
        Text(
            text = "FILM",
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}

@Composable
fun NGamesBadge(
    modifier: Modifier = Modifier,
    nSize: Dp = 14.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetflixNLogo(size = nSize, modifier = Modifier.padding(end = 4.dp))
        Text(
            text = "GAMES",
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}

