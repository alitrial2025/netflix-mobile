package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AvatarType
import com.example.data.model.UserProfile

@Composable
fun ProfileAvatar(
    profile: UserProfile?,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    isSelected: Boolean = false,
    isEditMode: Boolean = false,
    isAddMode: Boolean = false,
    isLocked: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val cornerRadius = (size * 0.15f).coerceIn(8.dp, 16.dp)
    val cardShape = RoundedCornerShape(cornerRadius)

    val baseModifier = modifier
        .size(size)
        .clip(cardShape)
        .then(
            if (profile == null) {
                if (isLocked) {
                    Modifier
                        .background(Color(0xFF141416))
                        .border(1.dp, Color(0xFF333336), cardShape)
                } else {
                    Modifier
                        .background(Color(0xFF1C1C20))
                        .border(1.5.dp, Color.White.copy(alpha = 0.2f), cardShape)
                }
            } else if (profile.avatarType == AvatarType.KIDS) {
                Modifier.background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFE91E63),
                            Color(0xFFFF9800),
                            Color(0xFF4CAF50),
                            Color(0xFF00BCD4),
                            Color(0xFF2196F3)
                        )
                    )
                )
            } else {
                Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(profile.avatarColorHex),
                            Color(profile.avatarColorHex).copy(alpha = 0.78f)
                        )
                    )
                )
            }
        )
        .then(
            if (isSelected) {
                Modifier.border(2.5.dp, Color.White, cardShape)
            } else {
                Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), cardShape)
            }
        )
        .then(
            if (onClick != null) {
                Modifier
                    .clickable { onClick() }
                    .testTag("profile_avatar_${profile?.id ?: if (isAddMode) "add" else "edit"}")
            } else Modifier
        )

    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center
    ) {
        if (isAddMode) {
            if (isLocked) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Plan Upgrade Required",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(size * 0.38f)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Profile",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.45f)
                )
            }
        } else if (profile != null) {
            if (profile.avatarUrl != null) {
                coil.compose.AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = profile.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                when (profile.avatarType) {
                    AvatarType.SMILEY -> {
                        NetflixSmiley(size, Color.White.copy(alpha = 0.95f))
                    }
                    AvatarType.KIDS -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "KIDS",
                                color = Color.White,
                                fontSize = (size.value * 0.28).sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    AvatarType.CUSTOM -> {
                        NetflixSmiley(size, Color.White.copy(alpha = 0.95f))
                    }
                }
            }

            // Specular top highlight for glass/3D tactile effect
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.22f),
                            0.35f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.25f)
                        )
                    )
            )
        }

        // Edit Mode overlay with pencil icon badge
        if (isEditMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.62f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(size * 0.46f)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.5.dp, Color.Black.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = Color.Black,
                        modifier = Modifier.size(size * 0.26f)
                    )
                }
            }
        }

        // PIN Lock indicator badge
        if (profile?.pin != null && profile.pin.isNotBlank() && !isAddMode && !isEditMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size((size * 0.28f).coerceAtLeast(20.dp))
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked Profile",
                    tint = Color.White,
                    modifier = Modifier.size((size * 0.16f).coerceAtLeast(12.dp))
                )
            }
        }
    }
}

@Composable
fun NetflixSmiley(size: Dp, color: Color) {
    Canvas(modifier = Modifier.size(size)) {
        val w = size.toPx()
        val h = size.toPx()

        // Eyes
        drawCircle(
            color = color,
            radius = w * 0.06f,
            center = androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.35f)
        )
        drawCircle(
            color = color,
            radius = w * 0.06f,
            center = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.35f)
        )

        // Mouth (curved path)
        val path = Path().apply {
            moveTo(w * 0.25f, h * 0.6f)
            quadraticTo(
                w * 0.5f, h * 0.85f,
                w * 0.75f, h * 0.6f
            )
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = w * 0.08f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}
