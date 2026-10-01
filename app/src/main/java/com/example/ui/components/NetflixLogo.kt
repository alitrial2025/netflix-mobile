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

/** Shared proportions for the outlined SVGs and their Android vector exports. */
internal object NetflixProLogoGeometry {
    const val MarkAspectRatio = 1044f / 1000f
    const val WordmarkAspectRatio = 1506f / 277f
    const val WordmarkNWidthFraction = 140.803f / 1506f
}

/**
 * Renders the outlined NetflixPro SVG vector export synchronously.
 */
@Composable
fun NetflixSvgWordmark(
    modifier: Modifier = Modifier,
    height: Dp = 26.dp,
    testTag: String = "netflix_svg_wordmark"
) {
    Image(
        painter = painterResource(R.drawable.ic_netflix_logo),
        contentDescription = "NetflixPro",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(height)
            .width(height * NetflixProLogoGeometry.WordmarkAspectRatio)
            .testTag(testTag)
    )
}

/**
 * Renders the Npro ribbon lockup SVG vector export synchronously.
 */
@Composable
fun NetflixSvgNLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    testTag: String = "netflix_svg_n_logo"
) {
    Image(
        painter = painterResource(R.drawable.ic_netflix_n),
        contentDescription = "Npro logo",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(size)
            .width(size * NetflixProLogoGeometry.MarkAspectRatio)
            .testTag(testTag)
    )
}

/**
 * Hardware-accelerated Npro ribbon lockup using the shared vector drawable.
 */
@Composable
fun NetflixNLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    testTag: String = "netflix_n_logo"
) {
    val width = size * NetflixProLogoGeometry.MarkAspectRatio
    Image(
        painter = painterResource(id = R.drawable.ic_netflix_n),
        contentDescription = "Npro logo",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(width = width, height = size)
            .testTag(testTag)
    )
}

/**
 * Full NetflixPro wordmark (outlined SVG)
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

