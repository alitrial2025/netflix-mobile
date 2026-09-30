package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import com.example.ui.components.NetflixSpinner
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.ui.components.ProfileAvatar
import com.example.ui.theme.NetflixRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

@Composable
fun ProfileLoadingOverlay(
    profile: UserProfile,
    isWarmupFinished: Boolean = false,
    onAnimationComplete: () -> Unit
) {
    var isPhase1Loading by remember { mutableStateOf(true) }
    var assimilationProgress by remember { mutableFloatStateOf(0f) }
    var pulseProgress by remember { mutableFloatStateOf(0f) }

    val animatedAssimilationProgress by animateFloatAsState(
        targetValue = assimilationProgress,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "assimilation_progress"
    )

    val animatedPulseProgress by animateFloatAsState(
        targetValue = pulseProgress,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "pulse_progress"
    )

    val spinnerAlpha by animateFloatAsState(
        targetValue = if (isPhase1Loading) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "spinner_alpha"
    )

    var statusText by remember { mutableStateOf("Loading ${profile.name}'s Netflix...") }

    LaunchedEffect(profile.id) {
        // Phase 1: Centered avatar + loading spinner
        delay(750)

        // Phase 2: Slide down-right to My Netflix tab
        isPhase1Loading = false
        assimilationProgress = 1f
        delay(650)

        // Phase 3: Assimilation pulse
        pulseProgress = 1f
        delay(250)

        onAnimationComplete()
    }

    val bgAlpha = (1f - animatedAssimilationProgress).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgAlpha))
            .testTag("profile_loading_overlay")
    ) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        // Start at Center
        val startX = screenWidthPx / 2f
        val startY = screenHeightPx / 2f - with(density) { 30.dp.toPx() }

        // Target: My Netflix tab in bottom navigation bar
        val targetX = screenWidthPx - with(density) { 46.dp.toPx() }
        val targetY = screenHeightPx - with(density) { 42.dp.toPx() }

        val currentX = startX + (targetX - startX) * animatedAssimilationProgress
        val currentY = startY + (targetY - startY) * animatedAssimilationProgress

        val startSizeDp = 104.dp
        val targetSizeDp = 18.dp
        val currentSizeDp = startSizeDp + (targetSizeDp - startSizeDp) * animatedAssimilationProgress
        val currentSizePx = with(density) { currentSizeDp.toPx() }

        val avatarOffsetX = (currentX - currentSizePx / 2f).roundToInt()
        val avatarOffsetY = (currentY - currentSizePx / 2f).roundToInt()

        // 1. Center Spinner and Label
        if (spinnerAlpha > 0f) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 88.dp)
                    .alpha(spinnerAlpha),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NetflixSpinner(
                    size = 36.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = statusText,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 2. Assimilation Pulse Ring on My Netflix Tab
        if (animatedPulseProgress > 0f) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("assimilation_pulse")
            ) {
                val ringRadius = with(density) { 18.dp.toPx() } + (with(density) { 28.dp.toPx() } * animatedPulseProgress)
                val ringAlpha = (1f - animatedPulseProgress).coerceIn(0f, 1f)
                drawCircle(
                    color = NetflixRed.copy(alpha = ringAlpha),
                    radius = ringRadius,
                    center = androidx.compose.ui.geometry.Offset(targetX, targetY),
                    style = Stroke(width = with(density) { 3.dp.toPx() })
                )
            }
        }

        // 3. Sliding & Shrinking Profile Avatar
        Box(
            modifier = Modifier.offset { IntOffset(avatarOffsetX, avatarOffsetY) }
        ) {
            ProfileAvatar(
                profile = profile,
                size = currentSizeDp,
                isSelected = false
            )
        }
    }
}
