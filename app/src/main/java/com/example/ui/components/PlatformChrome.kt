package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.PlatformAppearance
import com.example.data.model.UserProfile
import com.example.ui.glass.GlassPanel
import com.example.ui.theme.LocalPlatformDesign
import com.example.ui.viewmodel.NavigationTab

@Composable
internal fun PlatformToolbar(title: String, onBack: (() -> Unit)?, modifier: Modifier,
                             backTag: String, actions: @Composable RowScope.() -> Unit) {
    val design = LocalPlatformDesign.current
    Row(modifier.fillMaxWidth().background(design.background).padding(horizontal = 12.dp, vertical = 8.dp)
        .testTag("platform_toolbar_${design.appearance.key}"), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onBack, Modifier.size(48.dp).testTag(backTag)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = design.accent)
        }
        Text(title, color = Color.White, fontSize = if (design.appearance == PlatformAppearance.APPLE) 28.sp else 23.sp,
            fontWeight = design.titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
internal fun PlatformHomeHeader(onHome: () -> Unit, onCast: () -> Unit, onDownloads: () -> Unit,
                                onNotifications: () -> Unit, unread: Int) {
    val design = LocalPlatformDesign.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp)
        .testTag("platform_home_header_${design.appearance.key}"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(role = Role.Button, onClick = onHome).padding(vertical = 6.dp)) {
            Text(design.brandTitle, color = if (design.appearance == PlatformAppearance.PRIME) design.accent else Color.White,
                fontSize = 25.sp, fontWeight = design.titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("NETFLIXPRO", color = Color.White.copy(alpha = .65f), fontSize = 8.sp, letterSpacing = 1.4.sp)
        }
        IconButton(onCast, Modifier.size(48.dp).testTag("top_bar_cast_button")) { Icon(Icons.Outlined.Cast, "Cast", tint = Color.White) }
        IconButton(onDownloads, Modifier.size(48.dp).testTag("top_bar_download_button")) { Icon(Icons.Outlined.Download, "Downloads", tint = Color.White) }
        IconButton(onNotifications, Modifier.size(48.dp).testTag("top_bar_notifications_button")) {
            BadgedBox(badge = { if (unread > 0) Badge(containerColor = design.accent, contentColor = design.onAccent) { Text(unread.coerceAtMost(99).toString()) } }) {
                Icon(Icons.Outlined.NotificationsNone, "Notifications", tint = Color.White)
            }
        }
    }
}

@Composable
internal fun PlatformBottomNavigation(selected: NavigationTab, profile: UserProfile,
    onSelect: (NavigationTab) -> Unit, modifier: Modifier, clipsAllowed: Boolean) {
    val design = LocalPlatformDesign.current
    val tabs = remember(clipsAllowed) { buildList {
        add(NavigationTab.HOME); if (clipsAllowed) add(NavigationTab.CLIPS)
        add(NavigationTab.SEARCH); add(NavigationTab.MY_NETFLIX)
    } }
    val floating = false
    val shell = if (floating) Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        .widthIn(max = 600.dp) else Modifier.fillMaxWidth()
    Box(modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
        GlassPanel(shell.heightIn(min = navigationControlHeight()).testTag("platform_navigation_${design.appearance.key}"),
            RoundedCornerShape(if (floating) 28.dp else 0.dp)) {
            Row(Modifier.fillMaxWidth().height(navigationControlHeight()).selectableGroup().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { tab ->
                    val active = tab == selected
                    val tint by animateColorAsState(if (active) Color.White else Color(0xFFB4BCCB), label = "platform_tab_tint")
                    val name = when (tab) { NavigationTab.HOME -> "Home"; NavigationTab.CLIPS -> "Clips"
                        NavigationTab.SEARCH -> "Search"; else -> "My Netflix" }
                    val key = if (tab == NavigationTab.MY_NETFLIX) "my_netflix" else name.lowercase()
                    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(design.controlRadius))
                        .selectable(active, role = Role.Tab, onClick = { onSelect(tab) }).testTag("nav_tab_$key"),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        if (design.appearance == PlatformAppearance.PRIME) Box(Modifier.width(24.dp).height(2.dp)
                            .background(if (active) design.accent else Color.Transparent))
                        Box(Modifier.height(29.dp).width(54.dp).clip(RoundedCornerShape(design.controlRadius))
                            .background(if (active && design.appearance == PlatformAppearance.APPLE) design.raised else Color.Transparent),
                            contentAlignment = Alignment.Center) {
                            if (tab == NavigationTab.MY_NETFLIX) ProfileAvatar(profile, size = 23.dp)
                            else Icon(painterResource(when (tab) { NavigationTab.HOME -> R.drawable.ic_nav_home
                                NavigationTab.CLIPS -> R.drawable.ic_nav_clips; else -> R.drawable.ic_nav_search }),
                                null, tint = tint, modifier = Modifier.size(23.dp))
                        }
                        Text(name, color = tint, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                        if (design.appearance == PlatformAppearance.PEACOCK) Box(Modifier.padding(top = 3.dp).width(16.dp).height(2.dp)
                            .background(if (active) design.accent else Color.Transparent))
                    }
                }
            }
        }
    }
}
