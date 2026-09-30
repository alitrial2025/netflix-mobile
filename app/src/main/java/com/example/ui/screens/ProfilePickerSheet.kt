package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MediaItem
import com.example.data.model.UserProfile
import com.example.data.model.UserSubscription
import com.example.ui.components.NetflixNLogo
import com.example.ui.components.NetflixWordmark
import com.example.ui.components.ProfileAvatar
import com.example.ui.theme.NetflixRed

@Composable
fun ProfilePickerSheet(
    profiles: List<UserProfile>,
    activeProfile: UserProfile,
    featuredMedia: MediaItem? = null,
    userSubscription: UserSubscription = UserSubscription(),
    onSelectProfile: (UserProfile) -> Unit,
    onAddProfile: (() -> Unit)? = null,
    onEditProfile: ((UserProfile?) -> Unit)? = null,
    onUpgradePlan: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isManageMode by remember { mutableStateOf(false) }
    var ambientColor by remember(featuredMedia?.id) { mutableStateOf(Color(0xFF160B0F)) }
    var lockedProfileToUnlock by remember { mutableStateOf<UserProfile?>(null) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    val animatedAmbientColor by animateColorAsState(
        targetValue = ambientColor,
        animationSpec = tween(durationMillis = 600),
        label = "ambient_color"
    )

    val imageModel = featuredMedia?.backdropUrl ?: featuredMedia?.posterUrl ?: featuredMedia?.bannerDrawableRes

    val imageRequest = remember(imageModel) {
        ImageRequest.Builder(context)
            .data(imageModel)
            .allowHardware(false)
            .crossfade(true)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Atmospheric Background: Media poster / backdrop with multi-stop dark vignette
        if (imageModel != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { state ->
                    val bitmap = (state.result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        Palette.from(bitmap).generate { palette ->
                            if (palette != null) {
                                val swatchRgb = palette.darkVibrantSwatch?.rgb
                                    ?: palette.dominantSwatch?.rgb
                                    ?: palette.vibrantSwatch?.rgb
                                    ?: palette.darkMutedSwatch?.rgb
                                if (swatchRgb != null) {
                                    val c = Color(swatchRgb)
                                    ambientColor = Color(
                                        red = (c.red * 0.45f).coerceIn(0.08f, 0.25f),
                                        green = (c.green * 0.35f).coerceIn(0.04f, 0.15f),
                                        blue = (c.blue * 0.45f).coerceIn(0.08f, 0.25f),
                                        alpha = 1f
                                    )
                                }
                            }
                        }
                    }
                }
            )

            // Deep cinematic vignette overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.55f),
                            0.35f to Color.Black.copy(alpha = 0.78f),
                            0.70f to animatedAmbientColor.copy(alpha = 0.92f),
                            1.0f to Color(0xFF0A0A0C)
                        )
                    )
            )
        } else {
            // Default ambient theatrical glow
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xFF14070A),
                            0.5f to Color(0xFF0D0D11),
                            1f to Color.Black
                        )
                    )
            )
        }

        // Soft spotlight radial glow directly behind the profiles
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .align(Alignment.Center)
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animatedAmbientColor.copy(alpha = 0.85f),
                                animatedAmbientColor.copy(alpha = 0.35f),
                                Color.Transparent
                            ),
                            center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.5f),
                            radius = size.width * 0.65f
                        )
                    )
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Netflix N Wordmark, Manage Profiles Toggle & Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NetflixNLogo(size = 28.dp)
                    NetflixWordmark(height = 18.dp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Manage / Done Toggle Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isManageMode) NetflixRed else Color.White.copy(alpha = 0.12f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isManageMode) NetflixRed else Color.White.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                isManageMode = !isManageMode
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("manage_profiles_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isManageMode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = if (isManageMode) "Done" else "Manage Profiles",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isManageMode) "Done" else "Manage",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .testTag("profile_picker_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.6f))

            // Dynamic Title Area
            Text(
                text = if (isManageMode) "Manage Profiles" else "Who's Watching?",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle / Profile Plan Allocation Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isManageMode) "Tap a profile to edit" else "${profiles.size}/${userSubscription.maxProfiles} Profiles • ${userSubscription.planName} Plan",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Profiles Grid
            val columns = if (profiles.size <= 2) 2 else 3
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .testTag("profile_picker_grid")
            ) {
                items(profiles) { profile ->
                    ProfileItem(
                        profile = profile,
                        isSelected = profile.id == activeProfile.id && !isManageMode,
                        isEditMode = isManageMode,
                        onClick = {
                            if (isManageMode) {
                                onEditProfile?.invoke(profile)
                            } else if (!profile.pin.isNullOrBlank()) {
                                lockedProfileToUnlock = profile
                                enteredPin = ""
                                pinError = null
                            } else {
                                onSelectProfile(profile)
                            }
                        }
                    )
                }

                // Add "Add Profile" button if allowed by subscription
                if (profiles.size < userSubscription.maxProfiles) {
                    item {
                        ProfileItem(
                            profile = null,
                            name = "Add Profile",
                            isAddMode = true,
                            onClick = { onAddProfile?.invoke() }
                        )
                    }
                } else if (profiles.size < 5) {
                    // Profile quota limit reached -> show Upgrade Profile card
                    item {
                        ProfileItem(
                            profile = null,
                            name = "Add Profile",
                            isAddMode = true,
                            isLocked = true,
                            onClick = {
                                onUpgradePlan?.invoke() ?: onAddProfile?.invoke()
                            }
                        )
                    }
                }

                // "Edit" item maintained for legacy/test tag compatibility
                if (!isManageMode && onEditProfile != null) {
                    item {
                        ProfileItem(
                            profile = null,
                            name = "Edit",
                            isEditMode = true,
                            onClick = {
                                isManageMode = true
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        // Elegant Profile PIN Unlock Modal
        if (lockedProfileToUnlock != null) {
            val targetProfile = lockedProfileToUnlock!!
            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(targetProfile.id) {
                try {
                    focusRequester.requestFocus()
                } catch (_: Throwable) {}
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1A1A1E))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                        .testTag("pin_unlock_dialog")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Avatar with glowing border
                        ProfileAvatar(
                            profile = targetProfile,
                            size = 76.dp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = NetflixRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Profile Lock",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Enter your 4-digit PIN to access ${targetProfile.name}'s profile.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // 4 Discrete PIN Indicator Boxes
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(4) { index ->
                                val isFilled = index < enteredPin.length
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isFilled) Color.White.copy(alpha = 0.15f)
                                            else Color.White.copy(alpha = 0.05f)
                                        )
                                        .border(
                                            width = if (isFilled) 2.dp else 1.dp,
                                            color = when {
                                                pinError != null -> NetflixRed
                                                isFilled -> Color.White
                                                else -> Color.White.copy(alpha = 0.2f)
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isFilled) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(if (pinError != null) NetflixRed else Color.White)
                                        )
                                    }
                                }
                            }
                        }

                        // Invisible actual text field to receive numeric soft keyboard input
                        BasicTextField(
                            value = enteredPin,
                            onValueChange = { input ->
                                val digits = input.filter { it.isDigit() }.take(4)
                                enteredPin = digits
                                pinError = null
                                if (digits.length == 4) {
                                    if (digits == targetProfile.pin) {
                                        val toSelect = targetProfile
                                        lockedProfileToUnlock = null
                                        onSelectProfile(toSelect)
                                    } else {
                                        pinError = "Incorrect PIN. Please try again."
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (com.example.data.model.ProfilePin.matches(targetProfile.pin, enteredPin)) {
                                        val toSelect = targetProfile
                                        lockedProfileToUnlock = null
                                        onSelectProfile(toSelect)
                                    } else {
                                        pinError = "Incorrect PIN. Please try again."
                                    }
                                }
                            ),
                            modifier = Modifier
                                .size(1.dp)
                                .focusRequester(focusRequester)
                        )

                        if (pinError != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = pinError!!,
                                color = NetflixRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Action buttons: Cancel & Unlock
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    lockedProfileToUnlock = null
                                    enteredPin = ""
                                    pinError = null
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = "CANCEL", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (com.example.data.model.ProfilePin.matches(targetProfile.pin, enteredPin)) {
                                        val toSelect = targetProfile
                                        lockedProfileToUnlock = null
                                        onSelectProfile(toSelect)
                                    } else {
                                        pinError = "Incorrect PIN. Please try again."
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NetflixRed,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = "UNLOCK", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileItem(
    profile: UserProfile?,
    name: String? = null,
    isSelected: Boolean = false,
    isEditMode: Boolean = false,
    isAddMode: Boolean = false,
    isLocked: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .testTag("profile_item_${profile?.id ?: if (isAddMode) "add" else "edit"}")
    ) {
        ProfileAvatar(
            profile = profile,
            size = 94.dp,
            isSelected = isSelected,
            isEditMode = isEditMode,
            isAddMode = isAddMode,
            isLocked = isLocked
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = name ?: profile?.name ?: "",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
