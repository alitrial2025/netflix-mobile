package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.R
import com.example.ui.theme.NetflixRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class RatingOption(
    val key: String,
    val label: String,
    val iconRes: Int
) {
    DISLIKE("DISLIKE", "Not for me", R.drawable.ic_netflix_thumbs_down),
    LIKE("LIKE", "I like this", R.drawable.ic_netflix_thumbs_up),
    DOUBLE_LIKE("DOUBLE_LIKE", "Love this!", R.drawable.ic_netflix_love_this)
}

@Composable
fun NetflixRatingAction(
    currentRating: String? = null,
    onRatingSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp, labelSize: androidx.compose.ui.unit.TextUnit = 11.sp
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedOptionKey by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Active icon & label when collapsed
    val activeIconRes = remember(currentRating) {
        when (currentRating) {
            "DOUBLE_LIKE" -> R.drawable.ic_netflix_love_this
            "DISLIKE" -> R.drawable.ic_netflix_thumbs_down
            else -> R.drawable.ic_netflix_thumbs_up
        }
    }
    val activeLabel = remember(currentRating) {
        when (currentRating) {
            "DOUBLE_LIKE" -> "Loved"
            "LIKE" -> "Liked"
            "DISLIKE" -> "Not for me"
            else -> "Rate"
        }
    }
    val activeTint = remember(currentRating) {
        if (currentRating != null) NetflixRed else Color.White
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Floating Rating Capsule Popup
        if (isExpanded) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(x = 0, y = -220),
                onDismissRequest = { isExpanded = false },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                RatingCapsuleContent(
                    onOptionClick = { option ->
                        coroutineScope.launch {
                            selectedOptionKey = option.key
                            delay(200)
                            onRatingSelect(option.key)
                            isExpanded = false
                            selectedOptionKey = null
                        }
                    }
                )
            }
        }

        // Anchor Button: Crossfades between Rate and 'X' close button
        if (isExpanded) {
            // Circular X Button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF262626))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isExpanded = false
                    }
                    .testTag("detail_rating_close_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Rating Menu",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            // Standard Rate Action Item
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isExpanded = true
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("detail_rate_button")
            ) {
                Icon(
                    painter = painterResource(id = activeIconRes),
                    contentDescription = "Rate",
                    tint = activeTint,
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = activeLabel,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = labelSize,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun RatingCapsuleContent(
    onOptionClick: (RatingOption) -> Unit
) {
    val options = listOf(
        RatingOption.DISLIKE,
        RatingOption.LIKE,
        RatingOption.DOUBLE_LIKE
    )

    // Twinkling animation for sparkle elements
    val infiniteTransition = rememberInfiniteTransition(label = "SparklePulse")
    val sparkleScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SparkleScale"
    )

    // Shiny Glass Border
    val shinyGlassBorder = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.35f),
            Color.White.copy(alpha = 0.10f)
        )
    )

    Box(
        modifier = Modifier
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(32.dp),
                ambientColor = Color.Black.copy(alpha = 0.9f),
                spotColor = Color.Black.copy(alpha = 0.95f)
            )
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF1E1E1E).copy(alpha = 0.98f))
            .border(1.dp, shinyGlassBorder, RoundedCornerShape(32.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .testTag("netflix_rating_capsule")
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, option ->
                RatingItemView(
                    option = option,
                    index = index,
                    sparkleScale = if (option == RatingOption.DOUBLE_LIKE) sparkleScale else 1f,
                    onClick = { onOptionClick(option) }
                )
            }
        }
    }
}

@Composable
private fun RatingItemView(
    option: RatingOption,
    index: Int,
    sparkleScale: Float,
    onClick: () -> Unit
) {
    var isTapped by remember { mutableStateOf(false) }

    // Entry Bounce animation
    val entryScale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        entryScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // Tap Bounce animation
    val tapScale by animateFloatAsState(
        targetValue = if (isTapped) 1.35f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "TapBounce"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isTapped = true
                onClick()
            }
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .testTag("rating_item_${option.key}")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer {
                    scaleX = entryScale.value * tapScale * sparkleScale
                    scaleY = entryScale.value * tapScale * sparkleScale
                }
        ) {
            Icon(
                painter = painterResource(id = option.iconRes),
                contentDescription = option.label,
                tint = if (isTapped) NetflixRed else Color.White,
                modifier = Modifier.size(if (option == RatingOption.DOUBLE_LIKE) 32.dp else 26.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = option.label,
            color = Color.White.copy(alpha = if (isTapped) 1.0f else 0.90f),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
