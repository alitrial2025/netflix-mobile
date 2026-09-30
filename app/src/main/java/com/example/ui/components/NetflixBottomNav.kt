package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.UserProfile
import com.example.ui.viewmodel.NavigationTab
import kotlin.math.roundToInt

@Composable
fun NetflixProfileIcon(size: Dp = 22.dp) {
    val smilePath = remember { Path() }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFE50914))
            .graphicsLayer { },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            // Draw eyes
            drawCircle(
                color = Color.Black,
                radius = w * 0.08f,
                center = Offset(w * 0.35f, h * 0.38f)
            )
            drawCircle(
                color = Color.Black,
                radius = w * 0.08f,
                center = Offset(w * 0.65f, h * 0.38f)
            )
            // Draw smile curve
            val strokeWidth = w * 0.08f
            smilePath.reset()
            smilePath.moveTo(w * 0.30f, h * 0.62f)
            smilePath.quadraticTo(w * 0.50f, h * 0.80f, w * 0.70f, h * 0.62f)
            drawPath(
                path = smilePath,
                color = Color.Black,
                style = Stroke(
                    width = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
    }
}

/**
 * Tactile Dark Clay Navigation Bar with rich volumetric weight,
 * sculpted clay capsule containers, smooth sliding indicator cushion, and tactile press physics.
 */
@Composable
fun NetflixBottomNav(
    selectedTab: NavigationTab,
    activeProfile: UserProfile,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier,
    isClipsAllowed: Boolean = true,
    ambientColorProvider: () -> Color = { Color.Black }
) {
    val groupedTabs = remember(isClipsAllowed) { 
        if (isClipsAllowed) listOf(NavigationTab.HOME, NavigationTab.CLIPS, NavigationTab.SEARCH) 
        else listOf(NavigationTab.HOME, NavigationTab.SEARCH) 
    }

    // Backdrop shadow and ambient lighting
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { }
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Soft ambient gradient blur shadow reacting to scroll purely in Draw phase
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .drawBehind {
                    val color = ambientColorProvider()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                color.copy(alpha = 0.25f),
                                color.copy(alpha = 0.75f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
                }
        )

        // Navigation Bar Layout Container
        Row(
            modifier = Modifier
                .padding(bottom = 12.dp, start = 12.dp, end = 12.dp)
                .fillMaxWidth(0.96f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Main Tactile Dark Clay Pill Container for (Home, Clips, Search)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .claymorphic(
                        shape = CircleShape,
                        surfaceColor = Color(0xFF1B1A22),
                        highlightColor = Color.White,
                        shadowColor = Color.Black,
                        elevation = 14.dp,
                        strokeWidth = 1.6.dp,
                        highlightAlpha = 0.65f,
                        depthAlpha = 0.88f,
                        gradientCurvature = 0.30f
                    )
                    .padding(4.dp)
            ) {
                val selectedIndex = groupedTabs.indexOf(selectedTab).coerceAtLeast(0)
                val totalTabs = groupedTabs.size

                // Snappy, lightweight native spring animation
                val targetOffsetRatio = selectedIndex.toFloat()
                val animatedOffsetRatio by animateFloatAsState(
                    targetValue = targetOffsetRatio,
                    animationSpec = spring(
                        dampingRatio = 0.75f,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "clay_indicator_offset"
                )

                // Active Tactile Clay Selected Indicator Pill on GPU layer
                if (selectedTab != NavigationTab.MY_NETFLIX) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(1f / totalTabs)
                            .fillMaxHeight()
                            .graphicsLayer {
                                translationX = animatedOffsetRatio * size.width
                            }
                            .padding(2.dp)
                            .claymorphic(
                                shape = CircleShape,
                                surfaceColor = Color(0xFF353140),
                                highlightColor = Color.White,
                                shadowColor = Color.Black,
                                elevation = 6.dp,
                                strokeWidth = 1.3.dp,
                                highlightAlpha = 0.75f,
                                depthAlpha = 0.78f,
                                gradientCurvature = 0.25f
                            )
                    )
                }

                // Row of Interactive Nav Items
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Home Tab
                    val onHomeClick = remember(onTabSelected) { { onTabSelected(NavigationTab.HOME) } }
                    LiquidNavItem(
                        label = "Home",
                        selected = selectedTab == NavigationTab.HOME,
                        iconRes = R.drawable.ic_nav_home,
                        onClick = onHomeClick,
                        testTag = "nav_tab_home",
                        modifier = Modifier.weight(1f)
                    )

                    // 2. Clips Tab
                    if (isClipsAllowed) {
                        val onClipsClick = remember(onTabSelected) { { onTabSelected(NavigationTab.CLIPS) } }
                        LiquidNavItem(
                            label = "Clips",
                            selected = selectedTab == NavigationTab.CLIPS,
                            iconRes = R.drawable.ic_nav_new_hot,
                            onClick = onClipsClick,
                            testTag = "nav_tab_clips",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 3. Search Tab
                    val onSearchClick = remember(onTabSelected) { { onTabSelected(NavigationTab.SEARCH) } }
                    LiquidNavItem(
                        label = "Search",
                        selected = selectedTab == NavigationTab.SEARCH,
                        iconRes = R.drawable.ic_nav_search,
                        onClick = onSearchClick,
                        testTag = "nav_tab_search",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Standalone Sculpted Clay Orb for "My Netflix"
            val isMyNetflixSelected = selectedTab == NavigationTab.MY_NETFLIX
            
            val orbInteractionSource = remember { MutableInteractionSource() }
            val isOrbPressed by orbInteractionSource.collectIsPressedAsState()

            val orbScale by animateFloatAsState(
                targetValue = when {
                    isOrbPressed -> 0.90f
                    isMyNetflixSelected -> 1.06f
                    else -> 0.96f
                },
                animationSpec = spring(
                    dampingRatio = 0.75f,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "orb_magnification"
            )

            val onMyNetflixClick = remember(onTabSelected) { { onTabSelected(NavigationTab.MY_NETFLIX) } }

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .graphicsLayer {
                        scaleX = orbScale
                        scaleY = orbScale
                    }
                    .claymorphic(
                        shape = CircleShape,
                        surfaceColor = if (isMyNetflixSelected) Color(0xFF383544) else Color(0xFF1B1A22),
                        highlightColor = Color.White,
                        shadowColor = Color.Black,
                        elevation = if (isOrbPressed) 4.dp else if (isMyNetflixSelected) 14.dp else 11.dp,
                        strokeWidth = 1.6.dp,
                        highlightAlpha = if (isMyNetflixSelected) 0.82f else 0.65f,
                        depthAlpha = 0.88f,
                        gradientCurvature = 0.30f
                    )
                    .clickable(
                        interactionSource = orbInteractionSource,
                        indication = null
                    ) { onMyNetflixClick() }
                    .testTag("nav_tab_my_netflix"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    ProfileAvatar(
                        profile = activeProfile,
                        size = 18.dp,
                        isSelected = false
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = "My Netflix",
                        color = if (isMyNetflixSelected) Color.White else Color.White.copy(alpha = 0.65f),
                        fontSize = 8.5.sp,
                        fontWeight = if (isMyNetflixSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Individual Nav Item with lightweight GPU-accelerated spring scale and tactile depression.
 */
@Composable
private fun LiquidNavItem(
    label: String,
    selected: Boolean,
    iconRes: Int,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val itemScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.90f
            selected -> 1.08f
            else -> 0.96f
        },
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "item_magnification"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            }
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = if (selected) Color.White else Color.White.copy(alpha = 0.60f),
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = label,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

