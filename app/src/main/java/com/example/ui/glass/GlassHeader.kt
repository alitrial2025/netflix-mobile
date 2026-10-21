package com.example.ui.glass

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GlassHeader(title: String, onBack: (() -> Unit)? = null, modifier: Modifier = Modifier,
                backTag: String = "glass_header_back", actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) GlassPanel(Modifier.size(48.dp), CircleShape) {
            IconButton(onBack, Modifier.fillMaxSize().testTag(backTag)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White)
            }
        }
        GlassPanel(Modifier.weight(1f).heightIn(min = 48.dp)) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp, vertical = 10.dp))
        }
        GlassPanel { Row(Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically, content = actions) }
    }
}
