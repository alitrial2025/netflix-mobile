package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SubscriptionPlans
import com.example.data.model.UserSubscription
import com.example.data.network.PayheroVerifier
import com.google.firebase.auth.FirebaseAuth
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixBorderGray
import com.example.ui.theme.NetflixDarkGray
import com.example.ui.theme.NetflixRed
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionSheet(
    currentSubscription: UserSubscription,
    userId: String,
    onPlanSelected: (UserSubscription) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val platformDesign = com.example.ui.theme.LocalPlatformDesign.current
    val context = LocalContext.current
    val initialPlanId = remember(currentSubscription) {
        if (currentSubscription.isGuest || SubscriptionPlans.PLANS.none { it.id == currentSubscription.planId }) {
            SubscriptionPlans.PLANS.find { it.isPopular }?.id ?: "plan_standard"
        } else {
            currentSubscription.planId
        }
    }
    var selectedPlanId by remember(userId) { mutableStateOf(initialPlanId) }
    val selectedPlan = remember(selectedPlanId) { SubscriptionPlans.getById(selectedPlanId) }
    val signedInUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
    val canPay = userId.isNotBlank() && signedInUser?.uid == userId && signedInUser?.isAnonymous == false
    var paymentReference by remember(userId, selectedPlanId) {
        mutableStateOf(if (canPay) PayheroVerifier.checkoutReference(context, userId, selectedPlanId) else "")
    }

    var mpesaCodeInput by remember(userId, selectedPlanId) { mutableStateOf("") }
    var isVerifying by remember(userId) { mutableStateOf(false) }
    var verificationCooldownSeconds by remember(userId) { mutableStateOf(0) }
    var verificationError by remember(userId, selectedPlanId) { mutableStateOf<String?>(null) }
    var showSuccessBanner by remember(userId, selectedPlanId) { mutableStateOf(false) }
    var successMessage by remember(userId, selectedPlanId) { mutableStateOf("Membership Activated! Unlocked across Phone & TV.") }
    var showPaySubWebView by remember(userId) { mutableStateOf(false) }

    val minuteTick by produceState(0L) {
        while (true) { kotlinx.coroutines.delay(60_000L); value += 1L }
    }
    val currentStatusLabel = remember(currentSubscription, minuteTick) { currentSubscription.statusLabel }
    val currentSummary = remember(currentSubscription, minuteTick) {
        when {
            currentSubscription.isGuest -> "Free guest preview • Upgrade for full playback"
            currentSubscription.isInRenewalGrace -> "Renewal overdue • Access ends after the two-day allowance"
            currentSubscription.status.equals("SUSPENDED", true) -> "Membership suspended • Contact support before paying"
            !currentSubscription.isActive -> "Membership inactive • Choose a plan or renew to watch"
            else -> "KES ${currentSubscription.amount} / 30 days • ${currentSubscription.daysRemaining} days remaining"
        }
    }
    var pendingActivation by remember(userId, selectedPlanId) { mutableStateOf<UserSubscription?>(null) }
    LaunchedEffect(currentSubscription, pendingActivation, canPay) {
        val pending = pendingActivation ?: return@LaunchedEffect
        if (canPay && currentSubscription.isActive && currentSubscription.planId == pending.planId &&
            currentSubscription.mpesaReceipt == pending.mpesaReceipt && currentSubscription.paymentReference == pending.paymentReference) {
            PayheroVerifier.clearCheckoutReference(context, userId, pending.planId, paymentReference)
            paymentReference = PayheroVerifier.checkoutReference(context, userId, pending.planId)
            mpesaCodeInput = ""
            pendingActivation = null
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(platformDesign.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = platformDesign.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Membership & Plans",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Current Plan Status Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(platformDesign.corner(12.dp)))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF221F1F), platformDesign.surfaceColor(Color(0xFF141414)))
                        )
                    )
                    .border(1.dp, platformDesign.outline, RoundedCornerShape(platformDesign.corner(12.dp)))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CURRENT STATUS",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(platformDesign.corner(4.dp)))
                                    .background(if (currentSubscription.isActive) Color(0xFF46D369).copy(alpha = 0.2f) else platformDesign.accent.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = currentStatusLabel,
                                    color = if (currentSubscription.isActive) Color(0xFF46D369) else platformDesign.accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${currentSubscription.planName} Plan",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentSummary,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Choose the plan that's right for you",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = "Each payment adds 30 days. Renew the same plan early to keep your remaining time. A plan change starts a new 30-day period.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (currentSubscription.isActive && currentSubscription.daysRemaining > 0 && selectedPlanId != currentSubscription.planId) {
                Text("Changing plans now replaces your remaining ${currentSubscription.daysRemaining} days. Renew your current plan to keep that time.",
                    color = Color(0xFFFFD166), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Plan Cards Carousel / List
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SubscriptionPlans.PLANS.forEach { plan ->
                    val isSelected = plan.id == selectedPlanId
                    PlanCard(
                        plan = plan,
                        isSelected = isSelected,
                        onClick = { if (!isVerifying) selectedPlanId = plan.id }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Payment / Pay Sub Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(platformDesign.corner(14.dp)))
                    .background(platformDesign.surfaceColor(Color(0xFF1B1B1B)))
                    .border(1.dp, platformDesign.surfaceColor(Color(0xFF333333)), RoundedCornerShape(platformDesign.corner(14.dp)))
                    .padding(18.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00AA85).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payment,
                                contentDescription = null,
                                tint = Color(0xFF00AA85),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "M-Pesa / Pay Sub Checkout",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Pay KES ${selectedPlan.priceKes} for ${selectedPlan.name} Plan",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!canPay) {
                        Text("Sign in to your account on this phone before paying or activating a membership.",
                            color = Color.White, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Step 1: In-App Web Checkout Button
                    Button(
                        onClick = {
                            showPaySubWebView = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00AA85)),
                        shape = RoundedCornerShape(platformDesign.corner(8.dp)),
                        enabled = canPay && !isVerifying && pendingActivation == null && !currentSubscription.status.equals("SUSPENDED", true),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CreditCard, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "1. Pay KES ${selectedPlan.priceKes} via Pay Sub",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "2. Enter M-Pesa Confirmation Code to Activate:",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = mpesaCodeInput,
                        onValueChange = {
                            mpesaCodeInput = it.uppercase(Locale.ROOT)
                            verificationError = null
                        },
                        placeholder = { Text("e.g. SKL892HJ9K", color = Color.Gray) },
                        singleLine = true,
                        enabled = canPay && !isVerifying,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00AA85),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isVerifying && verificationCooldownSeconds > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Verification will retry automatically in $verificationCooldownSeconds seconds. Keep this code; do not pay again.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }

                    if (verificationError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = verificationError!!,
                            color = platformDesign.accent,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val coroutineScope = rememberCoroutineScope()
                    Button(
                        onClick = {
                            if (isVerifying) return@Button
                            val code = mpesaCodeInput.trim().uppercase(Locale.ROOT)
                            if (!Regex("^[A-Z0-9]{8,12}$").matches(code)) {
                                verificationError = "Enter the 8–12 character M-Pesa code from your confirmation SMS."
                                return@Button
                            }
                            val planToVerify = selectedPlan
                            val ownerToVerify = userId
                            val referenceToVerify = paymentReference
                            isVerifying = true
                            verificationError = null
                            coroutineScope.launch {
                                try {
                                    val result = PayheroVerifier.verifyPayment(
                                        receiptCode = code,
                                        planPrice = planToVerify.priceKes,
                                        planId = planToVerify.id,
                                        userId = ownerToVerify,
                                        paymentReference = referenceToVerify,
                                        onCooldown = { verificationCooldownSeconds = it }
                                    )
                                    when (result) {
                                        is com.example.data.network.VerificationResult.Success -> {
                                            if (FirebaseAuth.getInstance().currentUser?.uid != ownerToVerify) {
                                                verificationError = "Sign in to the account used for this payment to see its membership."
                                                return@launch
                                            }
                                            pendingActivation = result.subscription
                                            onPlanSelected(result.subscription)
                                            successMessage = result.message
                                            showSuccessBanner = true
                                        }
                                        is com.example.data.network.VerificationResult.Error -> {
                                            verificationError = result.message
                                        }
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    verificationError = "Unable to finish verification. Retry the same code; do not pay again."
                                } finally {
                                    verificationCooldownSeconds = 0
                                    isVerifying = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = platformDesign.accent, contentColor = platformDesign.onAccent),
                        shape = RoundedCornerShape(platformDesign.corner(8.dp)),
                        enabled = canPay && !isVerifying,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(color = platformDesign.onAccent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = platformDesign.onAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (pendingActivation != null) "Retry membership refresh" else "Activate ${selectedPlan.name} Membership",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = platformDesign.onAccent
                            )
                        }
                    }

                    AnimatedVisibility(visible = showSuccessBanner) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(platformDesign.corner(8.dp)))
                                .background(Color(0xFF46D369).copy(alpha = 0.2f))
                                .border(1.dp, Color(0xFF46D369), RoundedCornerShape(platformDesign.corner(8.dp)))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF46D369))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (pendingActivation != null) "Payment verified. Refreshing membership access. Reconnect and retry this same code if needed; do not pay again." else successMessage,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // In-App Web Bottom Sheet for Pay Sub
    if (showPaySubWebView && canPay) {
        PaySubWebBottomSheet(
            url = PayheroVerifier.checkoutUrl(selectedPlan, paymentReference),
            planName = selectedPlan.name,
            priceKes = selectedPlan.priceKes,
            onDismiss = { showPaySubWebView = false },
            onPaymentDone = {
                showPaySubWebView = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaySubWebBottomSheet(
    url: String,
    planName: String,
    priceKes: Int,
    onDismiss: () -> Unit,
    onPaymentDone: () -> Unit
) {
    val platformDesign = com.example.ui.theme.LocalPlatformDesign.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var webProgress by remember { mutableFloatStateOf(0f) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.stopLoading()
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = platformDesign.surfaceColor(Color(0xFF141414)),
        dragHandle = null,
        modifier = Modifier.fillMaxHeight(0.94f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(platformDesign.surfaceColor(Color(0xFF1F1F1F)))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00AA85).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Secure",
                            tint = Color(0xFF00AA85),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Pay Sub Secure Checkout",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$planName Plan • KES $priceKes",
                            color = Color(0xFF00AA85),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { webViewInstance?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White.copy(alpha = 0.7f))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

            // Web Loading Linear Progress Indicator
            if (webProgress < 1f) {
                LinearProgressIndicator(
                    progress = { webProgress },
                    color = Color(0xFF00AA85),
                    trackColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                )
            }

            // Embedded Android WebView
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewInstance = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = false
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    webProgress = newProgress / 100f
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                    view?.destroy()
                                    webViewInstance = null
                                    onDismiss()
                                    return true
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val reqUrl = request?.url?.toString() ?: return false
                                    val destination = Uri.parse(reqUrl)
                                    val host = destination.host.orEmpty().lowercase(Locale.ROOT)
                                    if (destination.scheme == "https" &&
                                        (host == "lipwa.link" || host.endsWith(".lipwa.link") ||
                                         host == "payhero.co.ke" || host.endsWith(".payhero.co.ke") ||
                                         host == "payhero.africa" || host.endsWith(".payhero.africa"))) {
                                        return false
                                    }
                                    if (request?.isForMainFrame != true || destination.scheme !in setOf("https", "tel", "sms", "mailto")) return true
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(reqUrl))
                                        ctx.startActivity(intent)
                                    } catch (_: Exception) {}
                                    return true
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bottom Action Bar
            Surface(
                color = platformDesign.surfaceColor(Color(0xFF1A1A1A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Complete payment on the Pay Sub prompt above, then tap Done to enter your confirmation code.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onPaymentDone,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00AA85)),
                        shape = RoundedCornerShape(platformDesign.corner(8.dp)),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Done — Enter M-Pesa Code",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanCard(
    plan: SubscriptionPlan,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val platformDesign = com.example.ui.theme.LocalPlatformDesign.current
    val borderColor = if (isSelected) platformDesign.accent else Color(0xFF2E2E2E)
    val bgColor = if (isSelected) Color(0xFF221F1F) else platformDesign.surfaceColor(Color(0xFF161616))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(platformDesign.corner(12.dp)))
            .background(bgColor)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(platformDesign.corner(12.dp)))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plan.name,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (plan.isPopular) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(platformDesign.corner(4.dp)))
                                .background(platformDesign.accent)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "MOST POPULAR",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Text(
                    text = "KES ${plan.priceKes} / 30 days",
                    color = if (isSelected) platformDesign.accent else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("${plan.maxDownloads} offline titles per profile • ${plan.catalogAccess}",
                color = Color.LightGray, fontSize = 12.sp)
            val extras = buildList {
                if (plan.smartNextEpisode) add("Download Next Episode")
                if (plan.downloadsForYou) add("Downloads for You")
                if (plan.games) add("Games")
                if (plan.clips) add("Clips")
                if (plan.spatialAudio) add("Spatial Audio")
                if (plan.platforms) add("Platforms")
            }
            if (extras.isNotEmpty()) Text(extras.joinToString(" • "), color = Color.White,
                fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            if (plan.id == "plan_premium") Text("4K, HDR and spatial audio require a supported title and device.",
                color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(modifier = Modifier.height(12.dp))

            if (plan.id in setOf("plan_mobile", "plan_basic")) Text(
                "One registered device. Contact support before changing devices.",
                color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)
            )
            // Details Grid
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column {
                    Text("Quality", color = Color.Gray, fontSize = 11.sp)
                    Text(plan.resolution, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("Profiles", color = Color.Gray, fontSize = 11.sp)
                    Text("${plan.maxProfiles} profile${if (plan.maxProfiles > 1) "s" else ""}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("Offline", color = Color.Gray, fontSize = 11.sp)
                    Text("${plan.maxDownloads} titles", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("Devices", color = Color.Gray, fontSize = 11.sp)
                    Text(if (plan.id == "plan_mobile") "Phone/Tablet" else "TV/Phone/Tablet", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
