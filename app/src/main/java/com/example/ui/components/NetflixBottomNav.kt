package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.UserProfile
import com.example.ui.glass.GlassPanel
import com.example.ui.viewmodel.NavigationTab

@Composable
fun NetflixProfileIcon(size: Dp = 22.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE50914)), contentAlignment = Alignment.Center) {
        Text("⌣", color = Color.White, fontSize = (size.value * .75f).sp)
    }
}

@Composable
fun NetflixBottomNav(
    selectedTab: NavigationTab,
    activeProfile: UserProfile,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier,
    isClipsAllowed: Boolean = false,
    ambientColorProvider: () -> Color = { Color.Black }
) {
    val design = com.example.ui.theme.LocalPlatformDesign.current
    if (!design.originalLayout) {
        PlatformBottomNavigation(selectedTab, activeProfile, onTabSelected, modifier, isClipsAllowed)
        return
    }
    val tabs = remember(isClipsAllowed) {
        if (isClipsAllowed) listOf(NavigationTab.HOME, NavigationTab.CLIPS, NavigationTab.SEARCH)
        else listOf(NavigationTab.HOME, NavigationTab.SEARCH)
    }
    val density = LocalDensity.current
    val height = navigationControlHeight()
    BoxWithConstraints(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center) {
        val totalWidth = maxWidth.coerceAtMost(if (isClipsAllowed) 414.dp else 346.dp)
        val gap = 10.dp
        val pillWidth = totalWidth - height - gap
        val tabWidth = (pillWidth - 8.dp) / tabs.size
        val selectedIndex = tabs.indexOf(selectedTab)
        val highlightX = animateDpAsState(tabWidth * selectedIndex.coerceAtLeast(0),
            spring(dampingRatio = .86f, stiffness = 550f), label = "glassTabSelection")
        Row(Modifier.width(totalWidth).height(height).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            GlassPanel(Modifier.width(pillWidth).fillMaxHeight().testTag("nav_main_glass_pill")) {
                Box(Modifier.padding(4.dp).width(tabWidth).fillMaxHeight()
                    .graphicsLayer { translationX = with(density) { highlightX.value.toPx() }; alpha = if (selectedIndex >= 0) 1f else 0f }
                    .clip(RoundedCornerShape(50)).drawWithCache {
                        val fill = Brush.verticalGradient(listOf(Color.White.copy(alpha = .14f), Color.White.copy(alpha = .07f)))
                        onDrawBehind { drawRect(fill) }
                    })
                Row(Modifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    tabs.forEach { tab ->
                        val selected = selectedTab == tab
                        val title = when (tab) { NavigationTab.HOME -> "Home"; NavigationTab.CLIPS -> "Clips"; else -> "Search" }
                        Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50))
                            .selectable(selected, role = Role.Tab, onClick = { onTabSelected(tab) })
                            .testTag("nav_tab_${title.lowercase()}"), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Icon(painterResource(when(tab) { NavigationTab.HOME -> R.drawable.ic_nav_home
                                NavigationTab.CLIPS -> R.drawable.ic_nav_clips; else -> R.drawable.ic_nav_search }),
                                null, tint = if (selected) Color.White else Color(0xFFE4E1E6), modifier = Modifier.size(23.dp))
                            Spacer(Modifier.height(3.dp))
                            Text(title, color = Color.White, fontSize = 10.sp, lineHeight = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
            }
            GlassPanel(Modifier.size(height).testTag("nav_profile_glass_circle"), CircleShape) {
                Column(Modifier.fillMaxSize().clip(CircleShape)
                    .background(if (selectedTab == NavigationTab.MY_NETFLIX) Color.White.copy(alpha = .16f) else Color.Transparent)
                    .selectable(selectedTab == NavigationTab.MY_NETFLIX, role = Role.Tab,
                        onClick = { onTabSelected(NavigationTab.MY_NETFLIX) }).testTag("nav_tab_my_netflix"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    ProfileAvatar(activeProfile, size = 26.dp, modifier = Modifier.clip(CircleShape))
                    Spacer(Modifier.height(2.dp))
                    Text("My Netflix", color = Color.White, fontSize = 9.sp, lineHeight = 10.sp,
                        fontWeight = FontWeight.Medium, maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 6.dp))
                }
            }
        }
    }
}

@Composable
internal fun navigationControlHeight(): Dp = if (LocalDensity.current.fontScale > 1.5f) 80.dp else 64.dp

@Composable
internal fun navigationContentClearance(): Dp {
    val density = LocalDensity.current
    val inset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val design = com.example.ui.theme.LocalPlatformDesign.current
    return (if (design.originalLayout) navigationControlHeight() else platformNavigationHeight()) + 32.dp + inset
}
