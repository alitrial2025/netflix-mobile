package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.NetflixSpinner
import com.example.ui.components.NetflixNLogo
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixBorderGray
import com.example.ui.theme.NetflixDarkGray
import com.example.ui.theme.NetflixRed
import kotlinx.coroutines.launch

data class OnboardingPageData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badge: String
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    currentEmail: String?,
    onSignIn: (email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onSignUp: (email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    onClose: () -> Unit,
    onOpenTvPair: () -> Unit
) {
    val pages = remember {
        listOf(
            OnboardingPageData(
                title = "Unlimited films, TV programmes & more",
                subtitle = "Watch anywhere. Cancel at any time.",
                icon = Icons.Default.Movie,
                badge = "4K ULTRA HD & HDR"
            ),
            OnboardingPageData(
                title = "Download and watch offline",
                subtitle = "Save your favourites easily and always have something to watch.",
                icon = Icons.Default.Download,
                badge = "SMART DOWNLOADS"
            ),
            OnboardingPageData(
                title = "No annoying contracts",
                subtitle = "Join today, cancel at any time online in one click.",
                icon = Icons.Default.Check,
                badge = "FLEXIBLE MEMBERSHIP"
            ),
            OnboardingPageData(
                title = "Watch everywhere",
                subtitle = "Stream on your phone, tablet, laptop, and Android TV without paying more.",
                icon = Icons.Default.Devices,
                badge = "MULTI-SCREEN READY"
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    // Modals & Bottom Sheets State
    var showAuthModal by remember { mutableStateOf(false) }
    var authRequestRunning by remember { mutableStateOf(false) }
    var authModalInitialTab by remember { mutableIntStateOf(0) } // 0 = Sign In, 1 = Sign Up / Get Started
    var showPrivacySheet by remember { mutableStateOf(false) }
    var showFaqSheet by remember { mutableStateOf(false) }
    var showHelpSheet by remember { mutableStateOf(false) }

    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !authRequestRunning || it != SheetValue.Hidden }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .testTag("netflix_onboarding_landing_screen")
    ) {
        // 1. Cinematic Background Poster Wall Image
        Image(
            painter = painterResource(id = R.drawable.img_onboarding_bg_1788023799582),
            contentDescription = "NetflixPro Posters Collage",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 2. Multi-stop Deep Blue & Black Gradient Overlay (Authentic Netflix Onboarding Atmosphere)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.82f),
                            Color(0xBB020A1A), // Deep navy blue tint
                            Color(0xD9020F2B), // Atmospheric blue midtone
                            Color(0xF2010817), // Dark blue base
                            Color(0xFF000511), // Midnight black
                            Color.Black        // Solid base for bottom sheet & controls
                        )
                    )
                )
        )

        // 3. Subtle Radial / Circular Blue Ambient Glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x330071EB), // Netflix Blue glow center
                            Color.Transparent
                        ),
                        radius = 800f
                    )
                )
        )

        // 4. Main Foreground Content Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP BAR: N Logo (Top-Left) and Action Items (PRIVACY, FAQS, HELP, SIGN IN) (Top-Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top-Left: Netflix 'N' Logo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        // Return to page 0 on logo tap
                        coroutineScope.launch { pagerState.animateScrollToPage(0) }
                    }
                ) {
                    NetflixNLogo(
                        size = 38.dp,
                        modifier = Modifier.testTag("onboarding_netflix_n_logo")
                    )
                }

                // Top-Right: PRIVACY, FAQS, HELP, and SIGN IN
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // PRIVACY
                    Text(
                        text = "PRIVACY",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showPrivacySheet = true }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .testTag("onboarding_privacy_btn")
                    )

                    // FAQS
                    Text(
                        text = "FAQS",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showFaqSheet = true }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .testTag("onboarding_faqs_btn")
                    )

                    // HELP
                    Text(
                        text = "HELP",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showHelpSheet = true }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .testTag("onboarding_help_btn")
                    )

                    // SIGN IN (Red / Dark Accent Pill)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NetflixRed)
                            .clickable {
                                authModalInitialTab = 0
                                showAuthModal = true
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("onboarding_top_sign_in_btn")
                    ) {
                        Text(
                            text = "SIGN IN",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.15f))

            // CENTER: 4-Page Horizontal Carousel
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("onboarding_horizontal_pager"),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) { pageIndex ->
                val pageData = pages[pageIndex]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Feature Icon / Badge Box
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0x33E50914))
                            .border(1.5.dp, Color(0x66E50914), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = pageData.icon,
                            contentDescription = pageData.title,
                            tint = NetflixRed,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pill Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(Color(0x33FFFFFF))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = pageData.badge,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Main Big Heading
                    Text(
                        text = pageData.title,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 34.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Subtitle Text
                    Text(
                        text = pageData.subtitle,
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                }
            }

            // BOTTOM CONTROLS: 4 Indicator Dots, GET STARTED Button & Browse as Guest
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 4-Dot Page Indicator
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 22.dp)
                ) {
                    repeat(pages.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 8.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "dot_width"
                        )
                        val color by animateColorAsState(
                            targetValue = if (isSelected) NetflixRed else Color.White.copy(alpha = 0.35f),
                            animationSpec = tween(300),
                            label = "dot_color"
                        )

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                        )
                    }
                }

                // Primary Action Button: "GET STARTED"
                Button(
                    onClick = {
                        authModalInitialTab = 1 // Open Get Started / Sign Up
                        showAuthModal = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("onboarding_get_started_btn")
                ) {
                    Text(
                        text = "GET STARTED",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Secondary Links: Browse as Guest & Pair TV
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Explore / Guest Browse
                    Text(
                        text = "Browse as Guest",
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onClose() }
                            .padding(vertical = 6.dp, horizontal = 8.dp)
                            .testTag("onboarding_browse_guest_btn")
                    )

                    // TV Unlock / QR Pair Link
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                onClose()
                                onOpenTvPair()
                            }
                            .padding(vertical = 6.dp, horizontal = 8.dp)
                            .testTag("onboarding_pair_tv_link")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.70f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Pair TV App",
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // ==========================================
    // 5. SIGN IN / SIGN UP BOTTOM SHEET MODAL
    // ==========================================
    if (showAuthModal) {
        ModalBottomSheet(
            onDismissRequest = { if (!authRequestRunning) showAuthModal = false },
            sheetState = bottomSheetState,
            containerColor = Color(0xFF141414),
            contentColor = Color.White,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        ) {
            AuthModalContent(
                initialTab = authModalInitialTab,
                onLoadingChanged = { authRequestRunning = it },
                currentEmail = currentEmail,
                onSignIn = onSignIn,
                onSignUp = onSignUp,
                onSignOut = onSignOut,
                onClose = {
                    showAuthModal = false
                    onClose()
                },
                onOpenTvPair = {
                    showAuthModal = false
                    onClose()
                    onOpenTvPair()
                }
            )
        }
    }

    // ==========================================
    // 6. PRIVACY POLICY MODAL SHEET
    // ==========================================
    if (showPrivacySheet) {
        ModalBottomSheet(
            onDismissRequest = { showPrivacySheet = false },
            containerColor = Color(0xFF141414),
            contentColor = Color.White,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        ) {
            PrivacySheetContent(onClose = { showPrivacySheet = false })
        }
    }

    // ==========================================
    // 7. FAQS & HELP MODAL SHEET
    // ==========================================
    if (showFaqSheet || showHelpSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showFaqSheet = false
                showHelpSheet = false
            },
            containerColor = Color(0xFF141414),
            contentColor = Color.White,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        ) {
            FaqSheetContent(onClose = {
                showFaqSheet = false
                showHelpSheet = false
            })
        }
    }
}

// ==========================================
// AUTH MODAL CONTENT (SIGN IN & SIGN UP)
// ==========================================
@Composable
private fun AuthModalContent(
    initialTab: Int,
    onLoadingChanged: (Boolean) -> Unit,
    currentEmail: String?,
    onSignIn: (email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onSignUp: (email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    onClose: () -> Unit,
    onOpenTvPair: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) } // 0 = Sign In, 1 = Sign Up
    var emailInput by remember { mutableStateOf(currentEmail ?: "") }
    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .verticalScroll(scrollState)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Modal Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NetflixNLogo(size = 28.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedTab == 0) "Sign In" else "Get Started",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(onClick = onClose, enabled = !isLoading) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Toggle Pill Row: Sign In vs Sign Up
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF222222))
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selectedTab == 0) NetflixRed else Color.Transparent)
                    .clickable(enabled = !isLoading) {
                        selectedTab = 0
                        errorMessage = null
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sign In",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selectedTab == 1) NetflixRed else Color.Transparent)
                    .clickable(enabled = !isLoading) {
                        selectedTab = 1
                        errorMessage = null
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sign Up",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Error Message Banner
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(NetflixRed.copy(alpha = 0.2f))
                    .border(1.dp, NetflixRed, RoundedCornerShape(6.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Email Field
        OutlinedTextField(
            enabled = !isLoading,
            value = emailInput,
            onValueChange = {
                emailInput = it
                errorMessage = null
            },
            label = { Text("Email Address") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f)
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF262626),
                unfocusedContainerColor = Color(0xFF262626),
                focusedBorderColor = NetflixRed,
                unfocusedBorderColor = Color(0xFF404040),
                focusedLabelColor = NetflixRed,
                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_email_field")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Password Field
        OutlinedTextField(
            enabled = !isLoading,
            value = passwordInput,
            onValueChange = {
                passwordInput = it
                errorMessage = null
            },
            label = { Text("Password") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f)
                )
            },
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle password visibility",
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (selectedTab == 1) ImeAction.Next else ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF262626),
                unfocusedContainerColor = Color(0xFF262626),
                focusedBorderColor = NetflixRed,
                unfocusedBorderColor = Color(0xFF404040),
                focusedLabelColor = NetflixRed,
                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_password_field")
        )

        // Confirm Password Field (Only on Sign Up)
        if (selectedTab == 1) {
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                enabled = !isLoading,
                value = confirmPasswordInput,
                onValueChange = {
                    confirmPasswordInput = it
                    errorMessage = null
                },
                label = { Text("Confirm Password") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF262626),
                    unfocusedContainerColor = Color(0xFF262626),
                    focusedBorderColor = NetflixRed,
                    unfocusedBorderColor = Color(0xFF404040),
                    focusedLabelColor = NetflixRed,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_confirm_password_field")
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Submit Button
        Button(
            enabled = !isLoading,
            onClick = {
                focusManager.clearFocus()
                if (emailInput.isBlank() || !emailInput.contains("@")) {
                    errorMessage = "Please enter a valid email address."
                    return@Button
                }
                if (passwordInput.length < 6) {
                    errorMessage = "Password must be at least 6 characters."
                    return@Button
                }
                if (selectedTab == 1 && passwordInput != confirmPasswordInput) {
                    errorMessage = "Passwords do not match."
                    return@Button
                }

                if (isLoading) return@Button
                isLoading = true
                onLoadingChanged(true)
                errorMessage = null

                val onSuccess = {
                    isLoading = false
                    onLoadingChanged(false)
                }
                val onError: (String) -> Unit = { err ->
                    isLoading = false
                    onLoadingChanged(false)
                    errorMessage = err
                }
                if (selectedTab == 0) {
                    onSignIn(emailInput.trim(), passwordInput, onSuccess, onError)
                } else {
                    onSignUp(emailInput.trim(), passwordInput, onSuccess, onError)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("auth_submit_button")
        ) {
            if (isLoading) {
                NetflixSpinner(size = 24.dp, color = Color.White)
            } else {
                Text(
                    text = if (selectedTab == 0) "Sign In" else "Get Started",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // TV App Unlock Link
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF222222))
                .clickable(enabled = !isLoading) { onOpenTvPair() }
                .padding(vertical = 12.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = NetflixRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Use this account on Android TV",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Guest Browse Link
        Text(
            text = "Skip for now & Browse as Guest",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(enabled = !isLoading) { onClose() }
                .padding(vertical = 6.dp, horizontal = 10.dp)
        )
    }
}

// ==========================================
// PRIVACY POLICY CONTENT
// ==========================================
@Composable
private fun PrivacySheetContent(onClose: () -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .verticalScroll(scrollState)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = NetflixRed,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Privacy Statement",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "NetflixPro is committed to protecting your personal information and streaming privacy.",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        PrivacyBulletItem(
            icon = Icons.Default.Lock,
            title = "Encrypted connections",
            description = "Your sign-in and account requests use encrypted HTTPS connections."
        )

        PrivacyBulletItem(
            icon = Icons.Default.Person,
            title = "Profile Independence",
            description = "Each profile has its own personalized recommendations, watchlists, PIN protections, and maturity filters."
        )

        PrivacyBulletItem(
            icon = Icons.Default.Policy,
            title = "Zero Ad Tracking",
            description = "We do not sell your personal watching history or telemetry to third-party advertising brokers."
        )

        PrivacyBulletItem(
            icon = Icons.Default.Devices,
            title = "Authorized TV Streaming",
            description = "Mobile is tied to one phone or tablet. Basic is tied to one device. Standard and Premium support more simultaneous screens."
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Got It", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun PrivacyBulletItem(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x22E50914)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NetflixRed,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

// ==========================================
// FAQS & HELP CONTENT
// ==========================================
@Composable
private fun FaqSheetContent(onClose: () -> Unit) {
    val scrollState = rememberScrollState()

    val faqs = remember {
        listOf(
            "What is NetflixPro?" to "NetflixPro is a streaming service that offers a wide variety of award-winning TV programmes, films, anime, documentaries and more on thousands of internet-connected devices.\n\nYou can watch as much as you want, whenever you want – all for one low monthly price. There's always something new to discover.",
            "How much does NetflixPro cost?" to "Watch NetflixPro on your smartphone, tablet, Smart TV, laptop, or streaming device, all for one fixed monthly fee. Plans range from KES 300 to KES 1,100 a month. No extra costs, no contracts.",
            "Where can I watch?" to "Watch anywhere, anytime. Sign in with your NetflixPro account to watch instantly on the web or from any internet-connected device that offers the NetflixPro app, including Smart TVs, smartphones, tablets, streaming media players and game consoles.\n\nYou can also download your favourite shows with the Android app to watch while you're on the go without an internet connection.",
            "How do I cancel?" to "NetflixPro is flexible. There are no annoying contracts and no commitments. You can easily cancel your account online in two clicks. There are no cancellation fees – start or stop your account anytime.",
            "What can I watch on NetflixPro?" to "NetflixPro has an extensive library of feature films, documentaries, TV programmes, anime, award-winning NetflixPro originals, and more. Watch as much as you want, anytime you want.",
            "Is NetflixPro good for kids?" to "The NetflixPro Kids experience is included in your membership to give parents control while kids enjoy family-friendly TV shows and films in their own space.\n\nKids profiles come with PIN-protected parental controls that let you restrict the maturity rating of content kids can watch and block specific titles."
        )
    }

    var expandedIndex by remember { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .verticalScroll(scrollState)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.QuestionAnswer,
                    contentDescription = null,
                    tint = NetflixRed,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Frequently Asked Questions",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        faqs.forEachIndexed { index, (question, answer) ->
            val isExpanded = expandedIndex == index

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF222222))
                    .clickable {
                        expandedIndex = if (isExpanded) -1 else index
                    }
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = question,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFF333333))
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = answer,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Done", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
