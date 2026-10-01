package com.example.ui.screens

import com.example.ui.theme.netflixProSwitchColors
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import com.example.data.model.profilePlaybackLanguages
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.data.model.UserProfile
import com.example.ui.components.ProfileAvatar
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixCardBg
import com.example.ui.theme.NetflixDarkGray
import com.example.ui.theme.NetflixRed

@Composable
fun EditProfileScreen(
    profile: UserProfile,
    onSaveProfile: (UserProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onOpenAvatarPicker: () -> Unit,
    onDismiss: () -> Unit,
    canDelete: Boolean = true, isSaving: Boolean = false, saveError: String? = null
) {
    var profileName by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var isKids by rememberSaveable(profile.id) { mutableStateOf(profile.isKids) }
    var maxAge by rememberSaveable(profile.id) { mutableStateOf(profile.maxAge) }
    var maturityRating by remember(profile.id) {
        mutableStateOf(
            when {
                profile.maxAge <= 7 -> "TV-Y"
                profile.maxAge <= 12 -> "PG"
                profile.maxAge <= 16 -> "16+"
                else -> "TV-MA"
            }
        )
    }
    var displayLanguage by remember(profile.id) { mutableStateOf(profile.language) }
    var audioLanguage by rememberSaveable(profile.id) { mutableStateOf(profile.audioLanguage) }
    var subtitleLanguage by rememberSaveable(profile.id) { mutableStateOf(profile.subtitleLanguage) }
    var settingsPage by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    var autoPlayNext by remember(profile.id) { mutableStateOf(profile.autoplayNext) }
    var autoPlayPreviews by remember(profile.id) { mutableStateOf(profile.autoplayPreviews) }
    var gameHandle by rememberSaveable(profile.id) { mutableStateOf(profile.gameHandle.orEmpty()) }
    var profilePin by remember(profile.id) { mutableStateOf(profile.pin) }
    var isProfileLocked by remember(profile.id) { mutableStateOf(!profile.pin.isNullOrBlank()) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showMaturityDialog by remember { mutableStateOf(false) }

    val dirty = profileName != profile.name || isKids != profile.isKids || maxAge != profile.maxAge ||
        audioLanguage != profile.audioLanguage || subtitleLanguage != profile.subtitleLanguage ||
        displayLanguage != profile.language || autoPlayNext != profile.autoplayNext || autoPlayPreviews != profile.autoplayPreviews ||
        gameHandle != profile.gameHandle.orEmpty() || profilePin != profile.pin
    val requestDismiss: () -> Unit = { if (!isSaving) { if (dirty) showDiscard = true else onDismiss() } }
    BackHandler { if (settingsPage != null) settingsPage = null else requestDismiss() }
    if (settingsPage != null) {
        ProfilePreferencesScreen(settingsPage!!, audioLanguage, subtitleLanguage, displayLanguage, gameHandle,
            onBack = { settingsPage = null }, onAudio = { audioLanguage = it }, onSubtitle = { subtitleLanguage = it },
            onDisplay = { displayLanguage = it }, onHandle = { gameHandle = it; settingsPage = null })
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = requestDismiss,
                    modifier = Modifier.testTag("edit_profile_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Cancel",
                        tint = Color.White
                    )
                }

                Text(
                    text = "Edit Profile",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )

                TextButton(
                    onClick = {
                        val updated = profile.copy(
                            name = profileName.trim().filterNot { it.isISOControl() }.take(25).ifBlank { profile.name },
                            isKids = isKids,
                            maxAge = if (isKids) maxAge.coerceAtMost(12) else maxAge,
                            pin = if (isProfileLocked) profilePin?.takeIf { it.isNotBlank() } else null,
                            language = displayLanguage,
                            autoplayNext = autoPlayNext,
                            autoplayPreviews = autoPlayPreviews,
                            gameHandle = gameHandle.trim().takeIf { it.isNotBlank() },
                            audioLanguage = audioLanguage, subtitleLanguage = subtitleLanguage
                        )
                        onSaveProfile(updated)
                    },
                    enabled = profileName.trim().isNotBlank() && !isSaving,
                    modifier = Modifier.testTag("edit_profile_save_button")
                ) {
                    Text(
                        text = if (isSaving) "Saving…" else "Save",
                        color = if (profileName.isNotBlank()) NetflixRed else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            // Scrollable Settings Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                if (saveError != null) Text(saveError, color = NetflixRed, modifier = Modifier.padding(bottom = 12.dp))
                // Avatar Header with Edit Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp, bottom = 24.dp)
                        .clickable { onOpenAvatarPicker() }
                        .testTag("edit_profile_avatar_touch")
                ) {
                    ProfileAvatar(
                        profile = profile,
                        size = 110.dp,
                        isSelected = false
                    )

                    // Floating Pencil Badge
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.BottomEnd)
                            .background(Color.White, CircleShape)
                            .border(2.dp, NetflixBlack, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Change Avatar",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = "CHANGE ICON",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { onOpenAvatarPicker() }
                        .padding(bottom = 28.dp)
                )

                // Profile Name Section
                Text(
                    text = "PROFILE NAME",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it.filterNot { char -> char.isISOControl() }.take(25) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_name_input"),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    singleLine = true,
                    trailingIcon = {
                        if (profileName.isNotEmpty()) {
                            IconButton(onClick = { profileName = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Color.Gray
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NetflixDarkGray,
                        unfocusedContainerColor = NetflixDarkGray,
                        focusedBorderColor = NetflixRed,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = NetflixRed
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Content Restrictions / For Kids
                SectionHeader(title = "CONTENT RESTRICTIONS")

                // For Kids Toggle Row
                SwitchRow(
                    title = "Kids Profile",
                    subtitle = "Only show movies and TV shows rated for kids 12 and under",
                    checked = isKids,
                    onCheckedChange = {
                        isKids = it
                        if (it) {
                            maxAge = 12
                            maturityRating = "PG"
                        } else {
                            maxAge = 18
                            maturityRating = "TV-MA"
                        }
                    },
                    testTag = "kids_profile_switch"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Maturity Rating Selection Row
                SettingItemRow(
                    title = "Maturity Rating",
                    subtitle = "$maturityRating • ${if (isKids || maxAge <= 12) "Ages $maxAge and below" else if (maxAge >= 18) "All maturity ratings" else "Ages $maxAge and below"}",
                    onClick = { showMaturityDialog = true },
                    testTag = "maturity_rating_item"
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Display & Audio Preferences
                SectionHeader(title = "DISPLAY & AUDIO")

                SettingItemRow(
                    title = "Display Language",
                    subtitle = displayLanguage,
                    onClick = { settingsPage = "display" },
                    testTag = "display_language_item"
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingItemRow(
                    title = "Audio & Subtitles",
                    subtitle = "$audioLanguage audio · $subtitleLanguage subtitles",
                    onClick = { settingsPage = "playback" },
                    testTag = "audio_subtitles_item"
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Autoplay Controls
                SectionHeader(title = "AUTOPLAY CONTROLS")

                SwitchRow(
                    title = "Autoplay next episode",
                    subtitle = "Automatically start the next available episode",
                    checked = autoPlayNext,
                    onCheckedChange = { autoPlayNext = it },
                    testTag = "autoplay_next_switch"
                )

                Spacer(modifier = Modifier.height(8.dp))

                SwitchRow(
                    title = "Autoplay previews",
                    subtitle = "Play trailers while browsing this profile",
                    checked = autoPlayPreviews,
                    onCheckedChange = { autoPlayPreviews = it },
                    testTag = "autoplay_previews_switch"
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Netflix Games Handle
                SectionHeader(title = "GAME HANDLE")

                SettingItemRow(
                    title = "Game Handle",
                    subtitle = gameHandle.ifBlank { "Create your handle" },
                    onClick = { settingsPage = "handle" },
                    testTag = "game_handle_item"
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Profile Lock
                SectionHeader(title = "PROFILE LOCK")

                SwitchRow(
                    title = "Require PIN to access profile",
                    subtitle = if (isProfileLocked && !profilePin.isNullOrBlank()) "PIN Protected (••••)" else if (isProfileLocked) "Lock enabled" else "Off",
                    checked = isProfileLocked,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (profilePin.isNullOrBlank()) {
                                pinInput = ""
                                pinError = null
                                showSetPinDialog = true
                            } else {
                                isProfileLocked = true
                            }
                        } else {
                            isProfileLocked = false
                            profilePin = null
                        }
                    },
                    testTag = "profile_lock_switch"
                )

                if (isProfileLocked && !profilePin.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E1E))
                            .clickable {
                                pinInput = ""
                                pinError = null
                                showSetPinDialog = true
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = NetflixRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Change 4-Digit PIN",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Delete Profile Button
                Button(
                    onClick = { showDeleteConfirmDialog = true },
                    enabled = canDelete && !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("delete_profile_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = NetflixRed
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NetflixRed.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Delete Profile",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        if (showDiscard) AlertDialog(onDismissRequest = { showDiscard = false },
            title = { Text("Discard changes?") }, text = { Text("Your profile changes haven't been saved.") },
            confirmButton = { TextButton(onClick = { showDiscard = false; onDismiss() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Keep editing") } })
        // Delete Confirmation Dialog
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = {
                    Text(
                        text = "Delete Profile?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "This profile's history—including My List, ratings, and recommendations—will be permanently deleted.",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            onDeleteProfile(profile.id)
                        }
                    ) {
                        Text(text = "DELETE", color = NetflixRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text(text = "CANCEL", color = Color.White)
                    }
                },
                containerColor = NetflixCardBg
            )
        }

        // Set / Change PIN Dialog
        if (showSetPinDialog) {
            AlertDialog(
                onDismissRequest = {
                    showSetPinDialog = false
                    if (profilePin.isNullOrBlank()) {
                        isProfileLocked = false
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = NetflixRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (profilePin.isNullOrBlank()) "Set Profile Lock PIN" else "Change Profile PIN",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Create a 4-digit PIN to lock ${profileName.ifBlank { "this profile" }}. This PIN will be required when switching to this profile.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { input ->
                                val filtered = input.filter { it in '0'..'9' }.take(4)
                                pinInput = filtered
                                pinError = null
                            },
                            placeholder = { Text("4-digit PIN (e.g. 1234)", color = Color.Gray) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword
                            ),
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NetflixRed,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (pinError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = pinError!!,
                                color = NetflixRed,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (pinInput.length == 4) {
                                profilePin = pinInput
                                isProfileLocked = true
                                showSetPinDialog = false
                            } else {
                                pinError = "PIN must be exactly 4 digits."
                            }
                        }
                    ) {
                        Text(text = "SAVE PIN", color = NetflixRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showSetPinDialog = false
                            if (profilePin.isNullOrBlank()) {
                                isProfileLocked = false
                            }
                        }
                    ) {
                        Text(text = "CANCEL", color = Color.White)
                    }
                },
                containerColor = NetflixCardBg
            )
        }

        // Maturity Rating Selection Dialog
        if (showMaturityDialog) {
            AlertDialog(
                onDismissRequest = { showMaturityDialog = false },
                title = {
                    Text(
                        text = "Maturity Rating",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val ratingTiers = listOf(
                            Triple("TV-Y (Ages 7 & below)", 7, "TV-Y"),
                            Triple("PG (Ages 10 & below)", 10, "PG"),
                            Triple("PG (Ages 12 & below)", 12, "PG"),
                            Triple("16+ (Ages 16 & below)", 16, "16+"),
                            Triple("TV-MA (All Ratings / 18+)", 18, "TV-MA")
                        )
                        ratingTiers.forEach { (label, age, ratingCode) ->
                            val isSelected = maxAge == age
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        maxAge = age
                                        maturityRating = ratingCode
                                        isKids = age <= 12
                                        showMaturityDialog = false
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        maxAge = age
                                        maturityRating = ratingCode
                                        isKids = age <= 12
                                        showMaturityDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = NetflixRed,
                                        unselectedColor = Color.Gray
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = label, color = Color.White, fontSize = 15.sp)
                            }
                        }
                    }
                },
                confirmButton = {},
                containerColor = NetflixCardBg
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun SettingItemRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NetflixDarkGray)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NetflixDarkGray)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = netflixProSwitchColors()
        )
    }
}

@Composable
private fun ProfilePreferencesScreen(
    page: String, audio: String, subtitles: String, display: String, handle: String,
    onBack: () -> Unit, onAudio: (String) -> Unit, onSubtitle: (String) -> Unit,
    onDisplay: (String) -> Unit, onHandle: (String) -> Unit
) {
    var handleDraft by rememberSaveable { mutableStateOf(handle) }
    val validHandle = handleDraft.isBlank() || handleDraft.matches(Regex("[a-zA-Z0-9_]{3,16}"))
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack, Modifier.testTag("profile_preferences_back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text(when(page) { "display" -> "Display language"; "playback" -> "Audio & subtitles"; else -> "Game handle" },
                color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (page == "handle") TextButton(onClick = { onHandle(handleDraft.trim()) }, enabled = validHandle,
                modifier = Modifier.testTag("profile_handle_apply")) { Text("Done", color = if (validHandle) Color.White else Color.Gray) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
            when (page) {
                "handle" -> item {
                    Text("Choose a handle for this profile. Use 3–16 letters, numbers or underscores.", color = Color.LightGray)
                    OutlinedTextField(handleDraft, onValueChange = { handleDraft = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(16) },
                        label = { Text("Game handle") }, isError = !validHandle, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("profile_handle_input"))
                }
                "display" -> {
                    item { Text("The app interface is available in English.", color = Color.LightGray, modifier = Modifier.padding(bottom = 16.dp)) }
                    item { ProfileLanguageRow("English", display == "English", "profile_display_English") { onDisplay("English") } }
                }
                else -> {
                    item { Text("Your preferred languages are selected when available in the video. You can change tracks in the player.", color = Color.LightGray, fontSize = 13.sp, modifier = Modifier.padding(bottom = 24.dp)) }
                    item { Text("Audio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    items(listOf("Original") + profilePlaybackLanguages.keys, key = { "audio:$it" }) { language ->
                        ProfileLanguageRow(language, audio == language, "profile_audio_$language") { onAudio(language) }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 24.dp), color = Color(0xFF333333)); Text("Subtitles", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    items(listOf("Off") + profilePlaybackLanguages.keys, key = { "subtitle:$it" }) { language ->
                        ProfileLanguageRow(language, subtitles == language, "profile_subtitle_$language") { onSubtitle(language) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileLanguageRow(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 16.sp)
        if (selected) Icon(Icons.Default.Check, "Selected", tint = Color.White)
    }
}
