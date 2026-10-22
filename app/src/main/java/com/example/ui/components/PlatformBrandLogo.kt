package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.PlatformAppearance
import com.example.ui.theme.LocalPlatformDesign

/** Bundled outlined SVG exports: native vector rendering, with no network or image decode. */
@Composable
internal fun PlatformBrandLogo(modifier: Modifier = Modifier, height: Dp = 26.dp,
                               appearance: PlatformAppearance = LocalPlatformDesign.current.appearance) {
    val (drawable, aspect) = when (appearance) {
        PlatformAppearance.APPLE -> R.drawable.ic_platform_apple to 1.938f
        PlatformAppearance.PRIME -> R.drawable.ic_platform_prime to 3.139f
        PlatformAppearance.DISNEY -> R.drawable.ic_platform_disney to 1.821f
        PlatformAppearance.HOTSTAR -> R.drawable.ic_platform_hotstar to 3.644f
        PlatformAppearance.PEACOCK -> R.drawable.ic_platform_peacock to 3.135f
        PlatformAppearance.NETFLIX -> R.drawable.ic_platform_netflix to 3.549f
        PlatformAppearance.ORIGINAL -> R.drawable.ic_netflix_logo to NetflixProLogoGeometry.WordmarkAspectRatio
    }
    Image(painterResource(drawable), if (appearance == PlatformAppearance.HOTSTAR) "JioHotstar" else appearance.label,
        contentScale = ContentScale.Fit, alignment = androidx.compose.ui.Alignment.CenterStart,
        modifier = modifier.width((height * aspect).coerceAtMost(144.dp)).height(height)
            .testTag("platform_brand_logo_${appearance.key}"))
}
