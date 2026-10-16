package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.UserProfile
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
    val shape = RoundedCornerShape(32.dp)
    BoxWithConstraints(
        modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.width((maxWidth * if (isClipsAllowed) .88f else .80f)
                .coerceAtMost(if (isClipsAllowed) 360.dp else 312.dp))
                .height(60.dp).clip(shape)
                .drawBehind {
                    // Read the poster's ambient state only during drawing. The tint follows
                    // the hero/clip without recomposing the complete navigation on each frame.
                    val ambient = ambientColorProvider()
                    val tint = if (ambient == Color.Unspecified) Color.Black else ambient.copy(alpha = 1f)
                    val glass = lerp(Color(0xFF2C292D), tint, .19f)
                    drawRect(
                        Brush.verticalGradient(
                            listOf(lerp(glass, Color.White, .045f).copy(alpha = .97f), glass.copy(alpha = .96f))
                        )
                    )
                }
                .border(1.dp, Color.White.copy(alpha = .10f), shape)
                .padding(horizontal = 5.dp, vertical = 4.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = if (isClipsAllowed) {
                listOf(NavigationTab.HOME, NavigationTab.CLIPS, NavigationTab.SEARCH, NavigationTab.MY_NETFLIX)
            } else {
                listOf(NavigationTab.HOME, NavigationTab.SEARCH, NavigationTab.MY_NETFLIX)
            }
            tabs.forEach { tab ->
                val selected = selectedTab == tab
                val title = when (tab) {
                    NavigationTab.HOME -> "Home"
                    NavigationTab.CLIPS -> "Clips"
                    NavigationTab.SEARCH -> "Search"
                    else -> "My Netflix"
                }
                val tag = when (tab) {
                    NavigationTab.HOME -> "home"
                    NavigationTab.CLIPS -> "clips"
                    NavigationTab.SEARCH -> "search"
                    else -> "my_netflix"
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(26.dp))
                        .selectable(selected = selected, role = Role.Tab, onClick = { onTabSelected(tab) })
                        .testTag("nav_tab_$tag"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .background(if (selected) Color.White.copy(alpha = .10f) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (tab == NavigationTab.MY_NETFLIX) {
                            ProfileAvatar(activeProfile, size = 24.dp, modifier = Modifier.clip(CircleShape))
                        } else {
                            Icon(
                                painterResource(
                                    when (tab) {
                                        NavigationTab.HOME -> R.drawable.ic_nav_home
                                        NavigationTab.CLIPS -> R.drawable.ic_nav_clips
                                        else -> R.drawable.ic_nav_search
                                    }
                                ),
                                contentDescription = null,
                                tint = if (selected) Color.White else Color(0xFFB5B0B5),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(1.dp))
                    Text(
                        title, color = if (selected) Color.White else Color(0xFFCDC9CE),
                        fontSize = 9.sp, lineHeight = 11.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
