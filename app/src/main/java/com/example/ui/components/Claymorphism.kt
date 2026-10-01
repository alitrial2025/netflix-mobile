package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NetflixRed

/**
 * High-performance Claymorphic modifier for Jetpack Compose.
 *
 * Claymorphism is characterized by:
 * 1. Plump, inflated 3D tactile form with rounded geometries.
 * 2. Subtle directional light reflection (inner specular rim highlight along top/left).
 * 3. Soft ambient drop shadow underneath providing layered depth without expensive blurs.
 * 4. Rich, matte gradient body fill that gives volume and curvature.
 *
 * Optimized for 60/120 FPS scrolling via [drawWithCache] and GPU-accelerated [Modifier.shadow].
 */
fun Modifier.claymorphic(
    shape: Shape = RoundedCornerShape(16.dp),
    surfaceColor: Color = Color(0xFF24222C),
    highlightColor: Color = Color.White,
    shadowColor: Color = Color.Black,
    elevation: Dp = 8.dp,
    strokeWidth: Dp = 1.2.dp,
    highlightAlpha: Float = 0.50f,
    depthAlpha: Float = 0.72f,
    gradientCurvature: Float = 0.22f
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = shadowColor.copy(alpha = 0.60f),
        spotColor = shadowColor.copy(alpha = 0.88f)
    )
    .clip(shape)
    .drawWithCache {
        val w = size.width
        val h = size.height
        val strokePx = strokeWidth.toPx()

        // 1. Inflated matte gradient body: lighter towards top-left, base in center, deeper at bottom-right
        val bodyBrush = Brush.linearGradient(
            colors = listOf(
                surfaceColor.lighten(gradientCurvature * 1.15f),
                surfaceColor,
                surfaceColor.darken(gradientCurvature * 1.35f)
            ),
            start = Offset(0f, 0f),
            end = Offset(w * 0.75f, h)
        )

        // 2. Beveled perimeter stroke: bright light catcher at top-left fading to deep shadow bevel at bottom-right
        val rimStrokeBrush = Brush.linearGradient(
            colorStops = arrayOf(
                0.0f to highlightColor.copy(alpha = highlightAlpha),
                0.30f to highlightColor.copy(alpha = highlightAlpha * 0.45f),
                0.65f to Color.Transparent,
                1.0f to shadowColor.copy(alpha = depthAlpha)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )

        // 3. Volumetric top-left specular sheen with weighted curved spread
        val specularBrush = Brush.linearGradient(
            colors = listOf(
                highlightColor.copy(alpha = highlightAlpha * 0.55f),
                highlightColor.copy(alpha = highlightAlpha * 0.18f),
                Color.Transparent
            ),
            start = Offset(0f, 0f),
            end = Offset(w * 0.6f, h * 0.55f)
        )

        // 4. Inset ambient curvature shadow along bottom-right for deep volumetric roundness
        val bottomCurveShadowBrush = Brush.linearGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.60f to Color.Transparent,
                1.0f to shadowColor.copy(alpha = depthAlpha * 0.42f)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )

        val outline = shape.createOutline(size, layoutDirection, this)
        val rimStyle = Stroke(width = strokePx)
        onDrawBehind {
            // Draw inflated body fill
            drawRect(brush = bodyBrush)

            // Draw top specular sheen
            drawRect(brush = specularBrush)

            // Draw bottom-right volumetric curvature shadow
            drawRect(brush = bottomCurveShadowBrush)

            // Draw clay rim bevel
            if (strokePx > 0f) {
                drawOutline(
                    outline = outline,
                    brush = rimStrokeBrush,
                    style = rimStyle
                )
            }
        }
    }

/**
 * Tactile Clay Pill Component for Category Filters and Navigation Chips.
 */
@Composable
fun ClayPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailingIconRotation: Float = 0f,
    testTag: String = "clay_pill_${label.lowercase().replace(" ", "_").replace("&", "and")}",
    shape: Shape = RoundedCornerShape(16.dp),
    height: Dp = 42.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "clay_pill_scale"
    )

    // Vibrant selected clay vs sleek dark tactile clay
    val surfaceColor = if (isSelected) {
        Color(0xFFE8E8EC)
    } else {
        Color(0xFF222028)
    }

    val contentColor = if (isSelected) {
        Color(0xFF101014)
    } else {
        Color.White
    }

    val highlightAlpha = if (isSelected) 0.75f else 0.48f
    val depthAlpha = if (isSelected) 0.42f else 0.78f
    val elevation = if (isPressed) 2.dp else if (isSelected) 8.dp else 5.dp

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .claymorphic(
                shape = shape,
                surfaceColor = surfaceColor,
                highlightColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.90f),
                shadowColor = Color.Black,
                elevation = elevation,
                strokeWidth = 1.3.dp,
                highlightAlpha = highlightAlpha,
                depthAlpha = depthAlpha,
                gradientCurvature = if (isSelected) 0.16f else 0.24f
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .height(height)
            .padding(
                horizontal = if (leadingIcon != null && label.isEmpty()) 12.dp else 16.dp
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                if (label.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }

            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    letterSpacing = 0.2.sp
                )
            }

            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = if (isSelected) 0.9f else 0.75f),
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            rotationZ = trailingIconRotation
                        }
                )
            }
        }
    }
}

/**
 * Tactile Clay Button for Primary and Secondary Hero Actions.
 */
@Composable
fun ClayButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    isDestructive: Boolean = false,
    shape: Shape = RoundedCornerShape(12.dp),
    height: Dp = 44.dp,
    testTag: String = "clay_button"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "clay_button_scale"
    )

    val surfaceColor = when {
        isDestructive -> NetflixRed
        isPrimary -> Color(0xFFF3F3F5)
        else -> Color(0xFF26242E)
    }

    val contentColor = when {
        isDestructive -> Color.White
        isPrimary -> Color(0xFF0F0E13)
        else -> Color.White
    }

    val elevation = if (isPressed) 2.dp else if (isPrimary) 8.dp else 5.dp
    val highlightAlpha = if (isPrimary) 0.65f else 0.38f
    val depthAlpha = if (isPrimary) 0.25f else 0.65f

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .claymorphic(
                shape = shape,
                surfaceColor = surfaceColor,
                highlightColor = Color.White,
                shadowColor = if (isDestructive) NetflixRed.copy(alpha = 0.6f) else Color.Black,
                elevation = elevation,
                strokeWidth = 1.2.dp,
                highlightAlpha = highlightAlpha,
                depthAlpha = depthAlpha,
                gradientCurvature = 0.18f
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .height(height)
            .padding(horizontal = 14.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                if (text.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    color = contentColor,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}

/**
 * Tactile Clay Circle Button for Play Controls and Quick Action Overlays.
 */
@Composable
fun ClayCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    iconSize: Dp = 24.dp,
    surfaceColor: Color = Color(0xFF26242E),
    iconTint: Color = Color.White,
    elevation: Dp = 6.dp,
    testTag: String = "clay_circle_button"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "clay_circle_scale"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .claymorphic(
                shape = CircleShape,
                surfaceColor = surfaceColor,
                highlightColor = Color.White,
                shadowColor = Color.Black,
                elevation = if (isPressed) 2.dp else elevation,
                strokeWidth = 1.2.dp,
                highlightAlpha = 0.40f,
                depthAlpha = 0.65f,
                gradientCurvature = 0.22f
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Tactile Clay Badge (e.g. for TOP 10 rank or 4K/HDR badges).
 */
@Composable
fun ClayBadge(
    text: String,
    modifier: Modifier = Modifier,
    surfaceColor: Color = Color(0xFF2B2933),
    textColor: Color = Color.White,
    shape: Shape = RoundedCornerShape(6.dp),
    leadingIcon: ImageVector? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = 10.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    strokeWidth: Dp = 0.8.dp
) {
    Box(
        modifier = modifier
            .claymorphic(
                shape = shape,
                surfaceColor = surfaceColor,
                highlightColor = Color.White,
                shadowColor = Color.Black,
                elevation = 2.dp,
                strokeWidth = strokeWidth,
                highlightAlpha = 0.35f,
                depthAlpha = 0.50f,
                gradientCurvature = 0.15f
            )
            .padding(horizontal = 7.dp, vertical = 2.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.5.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size((fontSize.value + 2).dp)
                )
            }
            Text(
                text = text,
                color = textColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                letterSpacing = 0.4.sp
            )
        }
    }
}

// Color arithmetic helpers for gentle gradient bevel generation
private fun Color.lighten(factor: Float): Color {
    val r = (red + (1f - red) * factor).coerceIn(0f, 1f)
    val g = (green + (1f - green) * factor).coerceIn(0f, 1f)
    val b = (blue + (1f - blue) * factor).coerceIn(0f, 1f)
    return Color(r, g, b, alpha)
}

private fun Color.darken(factor: Float): Color {
    val r = (red * (1f - factor)).coerceIn(0f, 1f)
    val g = (green * (1f - factor)).coerceIn(0f, 1f)
    val b = (blue * (1f - factor)).coerceIn(0f, 1f)
    return Color(r, g, b, alpha)
}
