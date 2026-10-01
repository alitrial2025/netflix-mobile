package com.example.ui.screens

import com.example.ui.components.NetflixProLogoGeometry

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.NetflixBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SplashScreen(
    isWarmupFinished: Boolean = false,
    onSplashComplete: () -> Unit
) {
    val currentIsWarmupFinished by androidx.compose.runtime.rememberUpdatedState(isWarmupFinished)
    val reusableWipePath = remember { Path() }
    var logoSize by remember { mutableStateOf(IntSize.Zero) }

    // Mathematically calculated proportions of "N" within the NETFLIXPRO wordmark
    val nLeftPercent = 0f
    val nWidthPercent = NetflixProLogoGeometry.WordmarkNWidthFraction
    val nRightPercent = nLeftPercent + nWidthPercent
    val nCenterPercent = nLeftPercent + nWidthPercent / 2f

    // Animation states
    val wipeProgress = remember { Animatable(1f) }
    val slideOffset = remember { Animatable(0f) }
    val crossfadeAlpha = remember { Animatable(0f) }
    val spinnerAlpha = remember { Animatable(0f) }
    val zoomAlpha = remember { Animatable(1f) }
    val tadumScale = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // 1. Pause briefly on the full logo
        delay(800)

        // 2. Wipe ETFLIX from right to left, leaving ONLY the N intact at the end
        wipeProgress.animateTo(
            targetValue = nRightPercent,
            animationSpec = tween(durationMillis = 1100, easing = LinearEasing)
        )

        // 3. Slide N to the exact center smoothly while crossfading/morphing to the standalone N logo
        val targetShift = if (logoSize.width > 0) (logoSize.width * (0.50f - nCenterPercent)) else 300f
        launch {
            crossfadeAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
            )
        }
        slideOffset.animateTo(
            targetValue = targetShift,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
        
        // Fade in the spinner below
        launch {
            spinnerAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300)
            )
        }
        
        // 4. Stand under standalone N logo for a smooth visual beat (600ms)
        delay(600)

        // 5. Play the dramatic Netflix "Tadum" zoom effect!
        launch {
            spinnerAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200)
            )
        }
        
        // Rapid zoom in (scale up to 12x) to mimic zooming into the red ribbon portal
        launch {
            tadumScale.animateTo(
                targetValue = 12f,
                animationSpec = tween(durationMillis = 750, easing = CubicBezierEasing(0.5f, 0f, 0.1f, 1f))
            )
        }
        
        // Start fading out shortly after zoom starts
        delay(100)
        zoomAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
        )
        
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = zoomAlpha.value
            }
            .background(NetflixBlack),
        contentAlignment = Alignment.Center
    ) {
        // Container for sliding
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = slideOffset.value
                    scaleX = tadumScale.value
                    scaleY = tadumScale.value
                    transformOrigin = TransformOrigin(nCenterPercent, 0.5f)
                },
            contentAlignment = Alignment.Center
        ) {
            // NETFLIXPRO wordmark with right-to-left Zigzag / Scissors wipe clip mask
            Image(
                painter = painterResource(id = R.drawable.ic_netflix_logo),
                contentDescription = "NetflixPro logo",
                modifier = Modifier
                    .width(280.dp)
                    .height(76.dp)
                    .onSizeChanged { logoSize = it }
                    .graphicsLayer {
                        alpha = 1f - crossfadeAlpha.value
                    }
                    .drawWithContent {
                        if (wipeProgress.value < 1f) {
                            val wipeX = size.width * wipeProgress.value
                            
                            // Smoothly reduce zigzag tooth amplitude as we approach the end
                            // so that the final "N" left-behind edge is perfectly clean and straight
                            val currentToothWidth = if (wipeProgress.value > 0.22f) {
                                32f // Jagged zigzag amplitude in pixels
                            } else {
                                val ratio = (wipeProgress.value - nRightPercent) / (0.22f - nRightPercent)
                                32f * ratio.coerceIn(0f, 1f)
                            }

                            val visiblePath = reusableWipePath.apply {
                                rewind()
                                moveTo(0f, 0f)
                                val steps = 12
                                val stepHeight = size.height / steps
                                for (i in 0..steps) {
                                    val y = i * stepHeight
                                    // Scissors / Zigzag jagged pattern
                                    val x = if (i % 2 == 0) {
                                        wipeX + currentToothWidth
                                    } else {
                                        wipeX - currentToothWidth
                                    }
                                    lineTo(x, y)
                                }
                                lineTo(0f, size.height)
                                close()
                            }
                            
                            drawContext.canvas.save()
                            drawContext.canvas.clipPath(visiblePath)
                            drawContent()
                            drawContext.canvas.restore()
                        } else {
                            drawContent()
                        }
                    }
            )

            // Npro lockup uses its natural aspect ratio and the same slide anchor.

            Box(
                modifier = Modifier
                    .width(280.dp)
                    .height(76.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_netflix_n),
                    contentDescription = "Npro logo",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (280f * nCenterPercent - 76f * NetflixProLogoGeometry.MarkAspectRatio / 2f).dp)
                        .width(76.dp * NetflixProLogoGeometry.MarkAspectRatio)
                        .height(76.dp)
                        .alpha(crossfadeAlpha.value),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // The Spinner below the logo
        com.example.ui.components.NetflixSpinner(
            modifier = Modifier
                .offset(y = 100.dp)
                .graphicsLayer {
                    alpha = spinnerAlpha.value
                },
            size = 50.dp
        )
    }
}
