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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Path
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
import com.example.data.model.ProfilePin
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
    var lockedProfileToUnlock by remember { mutableStateOf<UserProfile?>(null) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    val colors by com.example.ui.components.rememberPosterColors(featuredMedia)
    val animatedAmbientColor by animateColorAsState(colors.second, tween(500), label = "profile_poster_color")
    val imageModel = com.example.ui.components.posterModel(featuredMedia)
    androidx.activity.compose.BackHandler { if (isManageMode) isManageMode = false else onDismiss() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val footerHeight = (maxHeight * .35f).coerceIn(300.dp, 390.dp)
        if (imageModel != null) {
            AsyncImage(model = imageModel, contentDescription = featuredMedia?.title,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = .05f), .42f to Color.Transparent,
            .57f to Color.Black.copy(alpha = .35f), .70f to Color.Black.copy(alpha = .88f),
            1f to Color.Black
        )))
        featuredMedia?.let { media ->
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = footerHeight + 40.dp, start = 32.dp, end = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (!media.logoUrl.isNullOrBlank()) {
                    AsyncImage(model = media.logoUrl, contentDescription = media.title,
                        modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(.78f).height(70.dp), contentScale = ContentScale.Fit)
                } else {
                    Text(media.title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center, maxLines = 2)
                }
                media.top10Rank?.let { rank ->
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("TOP\n10", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black,
                            lineHeight = 10.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.background(NetflixRed, RoundedCornerShape(2.dp)).padding(3.dp))
                        Text("No. $rank in ${if (media.type == com.example.data.model.MediaType.TV_SHOW) "Series" else "Films"} Today",
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(footerHeight)) {
            val curve = 28.dp.toPx()
            val dome = Path().apply {
                moveTo(0f, curve)
                quadraticTo(size.width / 2f, -curve, size.width, curve)
                lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            drawPath(dome, Brush.verticalGradient(listOf(animatedAmbientColor, animatedAmbientColor.copy(alpha = .94f))))
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(footerHeight).navigationBarsPadding()
            .padding(top = 4.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (isManageMode) "Tap a profile to edit" else "Choose your profile", color = Color.White.copy(alpha = .75f), fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (profiles.size <= 2) 2 else 3),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().weight(1f).testTag("profile_picker_grid")
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileItem(profile = profile, isEditMode = isManageMode, onClick = {
                        if (isManageMode) onEditProfile?.invoke(profile)
                        else if (!profile.pin.isNullOrBlank()) {
                            lockedProfileToUnlock = profile; enteredPin = ""; pinError = null
                        } else onSelectProfile(profile)
                    })
                }
                if (profiles.size < 5 && onAddProfile != null) {
                    item(key = "add") {
                        ProfileItem(profile = null, name = "Add Profile", isAddMode = true,
                            isLocked = profiles.size >= userSubscription.maxProfiles,
                            onClick = {
                                if (profiles.size < userSubscription.maxProfiles) onAddProfile()
                                else onUpgradePlan?.invoke()
                            })
                    }
                }
                if (onEditProfile != null) {
                    item(key = "edit") {
                        ProfileItem(profile = null, name = if (isManageMode) "Done" else "Edit", isEditMode = true,
                            onClick = { isManageMode = !isManageMode })
                    }
                }
            }
        }

        // Elegant Profile PIN Unlock Modal
        if (lockedProfileToUnlock != null) {
            val targetProfile = lockedProfileToUnlock!!
            val focusRequester = remember { FocusRequester() }
            val unlockProfile = {
                if (enteredPin.length == 4) {
                    if (ProfilePin.matches(targetProfile.pin, enteredPin)) {
                        lockedProfileToUnlock = null
                        enteredPin = ""
                        pinError = null
                        onSelectProfile(targetProfile)
                    } else {
                        pinError = "Incorrect PIN. Please try again."
                    }
                }
            }

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
                                if (digits.length == 4) unlockProfile()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { unlockProfile() }
                            ),
                            modifier = Modifier
                                .size(1.dp)
                                .focusRequester(focusRequester)
                                .testTag("profile_pin_input")
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
                                onClick = { unlockProfile() },
                                enabled = enteredPin.length == 4,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NetflixRed,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).testTag("profile_pin_unlock")
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
            size = 76.dp,
            isSelected = isSelected,
            isEditMode = isEditMode,
            isAddMode = isAddMode,
            isLocked = isLocked
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = name ?: profile?.name ?: "",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
