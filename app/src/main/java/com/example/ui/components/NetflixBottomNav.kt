package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
    isClipsAllowed: Boolean = true,
    ambientColorProvider: () -> Color = { Color.Black }
) {
    val shape = RoundedCornerShape(40.dp)
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.widthIn(max = 330.dp).fillMaxWidth(.80f).height(70.dp).clip(shape)
            .background(Color(0xFF2B2B2B)).border(1.dp, Color.White.copy(alpha = .14f), shape).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            listOf(NavigationTab.HOME, NavigationTab.SEARCH, NavigationTab.MY_NETFLIX).forEach { tab ->
                val selected = selectedTab == tab
                val title = when (tab) { NavigationTab.HOME -> "Home"; NavigationTab.SEARCH -> "Search"; else -> "My Netflix" }
                val tag = when (tab) { NavigationTab.HOME -> "home"; NavigationTab.SEARCH -> "search"; else -> "my_netflix" }
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(30.dp))
                    .background(if (selected) Color.White.copy(alpha = .06f) else Color.Transparent)
                    .clickable { onTabSelected(tab) }.testTag("nav_tab_$tag"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (tab == NavigationTab.MY_NETFLIX) ProfileAvatar(activeProfile, size = 26.dp)
                    else Icon(painterResource(if (tab == NavigationTab.HOME) R.drawable.ic_nav_home else R.drawable.ic_nav_search),
                        contentDescription = title, tint = if (selected) Color.White else Color(0xFF999999), modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(title, color = if (selected) Color.White else Color(0xFFBBBBBB), fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
