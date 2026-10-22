package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
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
    BoxWithConstraints(modifier.fillMaxWidth().background(design.background)) {
        val titleSize = if (maxWidth < 400.dp) 18.sp else 20.sp
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("platform_toolbar_${design.appearance.key}"),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) GlassPanel(Modifier.size(48.dp).testTag("platform_header_back"), CircleShape) {
                IconButton(onBack, Modifier.fillMaxSize().testTag(backTag)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = design.accent)
                }
            }
            Box(Modifier.weight(1f)) {
                GlassPanel(Modifier.widthIn(max = 320.dp).heightIn(min = 48.dp).testTag("platform_header_title"), RoundedCornerShape(24.dp)) {
                Text(title, color = Color.White, fontSize = titleSize,
                    fontWeight = design.titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 10.dp, vertical = 12.dp))
            }
            }
            GlassPanel(Modifier.testTag("platform_header_actions"), RoundedCornerShape(24.dp)) {
                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}

@Composable
internal fun PlatformHomeHeader(onHome: () -> Unit, onCast: () -> Unit, onDownloads: () -> Unit,
                                onNotifications: () -> Unit, unread: Int) {
    val design = LocalPlatformDesign.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 56.dp)
        .testTag("platform_home_header_${design.appearance.key}"),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            GlassPanel(Modifier.widthIn(max = 320.dp).heightIn(min = 48.dp).testTag("platform_header_title"), RoundedCornerShape(24.dp)) {
            Column(Modifier.clickable(role = Role.Button, onClick = onHome).padding(horizontal = 12.dp, vertical = 8.dp)) {
                PlatformBrandLogo(height = 26.dp)
                Text("NETFLIXPRO", color = Color.White.copy(alpha = .65f), fontSize = 8.sp, letterSpacing = 1.sp)
            }
        }
        }
        GlassPanel(Modifier.testTag("platform_header_actions"), RoundedCornerShape(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onCast, Modifier.size(48.dp).testTag("top_bar_cast_button")) { Icon(Icons.Outlined.Cast, "Cast", tint = Color.White) }
                IconButton(onDownloads, Modifier.size(48.dp).testTag("top_bar_download_button")) { Icon(Icons.Outlined.Download, "Downloads", tint = Color.White) }
                IconButton(onNotifications, Modifier.size(48.dp).testTag("top_bar_notifications_button")) {
                    BadgedBox(badge = { if (unread > 0) Badge(containerColor = design.accent, contentColor = design.onAccent) { Text(unread.coerceAtMost(99).toString()) } }) {
                        Icon(Icons.Outlined.NotificationsNone, "Notifications", tint = Color.White)
                    }
                }
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
    val height = platformNavigationHeight()
    val shape = RoundedCornerShape(if (design.appearance == PlatformAppearance.APPLE) 34.dp else 24.dp)
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.widthIn(max = 560.dp).fillMaxWidth().height(height)
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = .16f), spotColor = Color.Black.copy(alpha = .2f))
            .testTag("platform_navigation_${design.appearance.key}"), shape) {
            Row(Modifier.fillMaxSize().selectableGroup().padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { tab ->
                    val active = tab == selected
                    val selection by animateColorAsState(if (active) design.raised else Color.Transparent, label = "platform_tab_selection")
                    val tint by animateColorAsState(if (active) Color.White else Color(0xFFB4BCCB), label = "platform_tab_tint")
                    val name = when (tab) { NavigationTab.HOME -> "Home"; NavigationTab.CLIPS -> "Clips"
                        NavigationTab.SEARCH -> "Search"; else -> "My Netflix" }
                    val key = if (tab == NavigationTab.MY_NETFLIX) "my_netflix" else name.lowercase()
                    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(selection)
                        .selectable(active, role = Role.Tab, onClick = { onSelect(tab) }).testTag("nav_tab_$key"),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        if (design.appearance == PlatformAppearance.PRIME) Box(Modifier.width(24.dp).height(2.dp).clip(RoundedCornerShape(50))
                            .background(if (active) design.accent else Color.Transparent))
                        Box(Modifier.height(28.dp).width(48.dp),
                            contentAlignment = Alignment.Center) {
                            if (tab == NavigationTab.MY_NETFLIX) ProfileAvatar(profile, size = 23.dp)
                            else Icon(painterResource(when (tab) { NavigationTab.HOME -> R.drawable.ic_nav_home
                                NavigationTab.CLIPS -> R.drawable.ic_nav_clips; else -> R.drawable.ic_nav_search }),
                                null, tint = tint, modifier = Modifier.size(23.dp))
                        }
                        Text(name, color = tint, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 3.dp))
                        if (design.appearance == PlatformAppearance.PEACOCK) Box(Modifier.padding(top = 3.dp).width(16.dp).height(2.dp).clip(RoundedCornerShape(50))
                            .background(if (active) design.accent else Color.Transparent))
                    }
                }
            }
        }
    }
}

@Composable
internal fun platformNavigationHeight() = with(LocalDensity.current) { (12.sp.toDp() * 2 + 48.dp).coerceAtLeast(68.dp) }
