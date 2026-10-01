package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.ui.theme.netflixProSwitchColors

/** Native profile setup: identity, viewing controls, then taste. No bottom navigation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupWalkthroughScreen(profile: UserProfile, onComplete: (UserProfile) -> Unit,
    onChooseIcon: () -> Unit, onCancel: () -> Unit, isSaving: Boolean = false, saveError: String? = null) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var step by rememberSaveable(profile.id) { mutableIntStateOf(0) }
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var kids by rememberSaveable(profile.id) { mutableStateOf(profile.isKids) }
    var age by rememberSaveable(profile.id) { mutableIntStateOf(profile.maxAge) }
    var language by rememberSaveable(profile.id) { mutableStateOf(profile.language) }
    var next by rememberSaveable(profile.id) { mutableStateOf(profile.autoplayNext) }
    var previews by rememberSaveable(profile.id) { mutableStateOf(profile.autoplayPreviews) }
    var pin by rememberSaveable(profile.id) { mutableStateOf(profile.pin?.takeIf { it.length == 4 }.orEmpty()) }
    var genres by rememberSaveable(profile.id) { mutableStateOf(profile.favoriteGenres) }
    var error by remember { mutableStateOf<String?>(null) }
    val titles = listOf("Make it yours", "Your viewing preferences", "What do you love?")
    fun back() { if (!isSaving) { if (step > 0) { step--; error = null } else onCancel() } }
    BackHandler { back() }
    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().imePadding().testTag("profile_setup_walkthrough")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { back() }, enabled = !isSaving) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            com.example.ui.components.NetflixWordmark(height=22.dp)
            Spacer(Modifier.weight(1f))
            Text("${step + 1} of 3", color = Color(0xFFAAAAAA), modifier = Modifier.padding(end = 16.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { index -> Box(Modifier.weight(1f).height(3.dp).background(if(index <= step) Color.White else Color(0xFF333333))) }
        }
        AnimatedContent(step, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
            label = "profile_setup_step", modifier = Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(titles[page], color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(when(page) { 0 -> "A profile for your favorites, your history and your next great watch."
                    1 -> "Choose how NetflixPro works for you. You can change these in profile settings."
                    else -> "Pick a few favorites. Your recommendations will learn as you watch." },
                    color = Color(0xFFAAAAAA), fontSize = 15.sp)
                Spacer(Modifier.height(28.dp))
                when(page) {
                    0 -> {
                        Box(Modifier.size(104.dp).clip(RoundedCornerShape(16.dp)).background(Color(profile.avatarColorHex),RoundedCornerShape(16.dp))
                            .clickable(enabled = !isSaving, onClick = { focusManager.clearFocus(); keyboard?.hide(); onChooseIcon() }).testTag("setup_avatar")) {
                            if (!profile.avatarUrl.isNullOrBlank()) AsyncImage(profile.avatarUrl, "Profile icon", modifier = Modifier.fillMaxSize())
                            else Text(name.firstOrNull()?.uppercaseChar()?.toString() ?: "?", color=Color.White,fontSize=36.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.Center))
                            Icon(Icons.Default.Edit, "Choose icon", tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd)
                                .background(Color.Black.copy(alpha=.7f),RoundedCornerShape(8.dp)).padding(8.dp))
                        }
                        Spacer(Modifier.height(24.dp))
                        OutlinedTextField(name, { name=it.take(25); error=null }, label = { Text("Profile name") }, singleLine=true,
                            enabled = !isSaving, modifier = Modifier.fillMaxWidth().testTag("setup_name"),
                            colors = setupTextFieldColors())
                        Spacer(Modifier.height(20.dp))
                        SetupSwitch("Kids experience", "A space for younger viewers.", kids, !isSaving) {
                            kids=it; age=if(it) 7 else 18
                        }
                    }
                    1 -> {
                        Text("Display language", color=Color.White,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("English","Swahili","Hindi","Spanish","French","Korean","Japanese","Arabic").forEach { option ->
                                SetupPill(option, language==option, !isSaving) { language=option }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("Allowed maturity", color=Color.White,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (if(kids) listOf(7,10,12) else listOf(7,10,12,16,18)).forEach { value ->
                                SetupPill("$value+", age==value, !isSaving) { age=value }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        OutlinedTextField(pin, { pin=it.filter(Char::isDigit).take(4); error=null }, label={Text("Profile PIN (optional)")},
                            supportingText={Text("Use four digits to lock this profile.")}, singleLine=true, enabled=!isSaving,
                            visualTransformation=PasswordVisualTransformation(), keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),
                            modifier=Modifier.fillMaxWidth().testTag("setup_pin"),colors=setupTextFieldColors())
                        Spacer(Modifier.height(12.dp))
                        SetupSwitch("Autoplay next episode", "Continue when an episode ends.", next,!isSaving) { next=it }
                        SetupSwitch("Autoplay previews", "Play previews while browsing.",previews,!isSaving) { previews=it }
                    }
                    2 -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            (if(kids) listOf("Animation","Family","Adventure","Comedy","Fantasy") else
                                listOf("Action","Adventure","Comedy","Crime","Documentary","Drama","Fantasy","Horror","Mystery","Romance","Sci-Fi","Thriller","Animation"))
                                .forEach { genre -> SetupPill(genre,genre in genres,!isSaving) {
                                    genres=if(genre in genres) genres-genre else genres+genre
                                } }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(if(genres.isEmpty()) "You can start with popular picks and choose later." else "${genres.size} favorite genres selected",
                            color=Color(0xFFAAAAAA),fontSize=14.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        (error ?: saveError)?.let { Text(it,color=Color(0xFFFF6B6B),modifier=Modifier.padding(horizontal=24.dp,vertical=8.dp).testTag("setup_error")) }
        Button(onClick = {
            if(name.trim().isEmpty()) { error="Enter a profile name."; step=0 }
            else if(step==1 && pin.isNotEmpty() && pin.length!=4) error="Use exactly four digits for your PIN."
            else if(step<2) { focusManager.clearFocus(); keyboard?.hide(); step++; error=null }
            else onComplete(profile.copy(name=name.trim(),isKids=kids,maxAge=age.coerceAtMost(if (kids) 12 else 18),language=language,
                autoplayNext=next,autoplayPreviews=previews,pin=pin.takeIf { it.isNotBlank() } ?: profile.pin,
                favoriteGenres=genres.filter { !kids || it in listOf("Animation","Family","Adventure","Comedy","Fantasy") }))
        },enabled=!isSaving,shape=RoundedCornerShape(6.dp),colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Color.Black),
            modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=16.dp).height(52.dp).testTag("setup_continue")) {
            if(isSaving) CircularProgressIndicator(color=Color.Black,strokeWidth=2.dp,modifier=Modifier.size(22.dp))
            else Text(if(step<2) "Continue" else "Start watching",fontSize=16.sp,fontWeight=FontWeight.Bold)
        }
    }
}
@Composable private fun SetupPill(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(selected=selected,onClick=onClick,enabled=enabled,label={Text(label)},shape=RoundedCornerShape(8.dp),
        colors=FilterChipDefaults.filterChipColors(containerColor=Color(0xFF242424),labelColor=Color.White,
            selectedContainerColor=Color.White,selectedLabelColor=Color.Black),modifier=Modifier.testTag("setup_option_$label"))
}
@Composable private fun SetupSwitch(title: String, subtitle: String, value: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=16.dp)) {
            Text(title,color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Medium)
            Spacer(Modifier.height(4.dp));Text(subtitle,color=Color(0xFF999999),fontSize=13.sp)
        }
        Switch(value,onChange,enabled=enabled,colors=netflixProSwitchColors())
    }
}
@Composable private fun setupTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedBorderColor=Color.White,
    unfocusedBorderColor=Color(0xFF666666),focusedLabelColor=Color.White,unfocusedLabelColor=Color(0xFFAAAAAA),cursorColor=Color.White)
