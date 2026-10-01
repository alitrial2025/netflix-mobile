package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TvSessionData
import com.example.data.model.UserProfile
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixRed

/**
 * The phone and TV each sign in with Firebase Auth. A QR code or a Firestore
 * document containing a user ID cannot authenticate the TV to an account.
 */
@Composable
fun TvPairScreen(
    activeProfile: UserProfile,
    profiles: List<UserProfile>,
    onPairTvCode: suspend (code: String, (TvSessionData) -> Unit, (String) -> Unit) -> Unit,
    pairedDevices: List<TvSessionData>,
    onUnpairDevice: (String) -> Unit,
    onSendRemoteCommand: (sessionCode: String, command: String) -> Unit = { _, _ -> },
    isSignedOut: Boolean = false,
    hasTvAccess: Boolean = true,
    onOpenAuth: () -> Unit = {},
    onOpenSubscription: () -> Unit = {},
    onBack: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = NetflixBlack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NetflixBlack)
                .statusBarsPadding()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = null,
                tint = NetflixRed
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Watch on TV",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Open NetflixPro on your TV and sign in with the same email and password as this phone. Your profiles, My List, and Continue Watching will sync through your account.",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )
            if (!hasTvAccess && !isSignedOut) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "A TV-enabled subscription is required to play titles on TV.",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            if (isSignedOut) {
                Button(onClick = onOpenAuth, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign in on phone")
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else if (!hasTvAccess) {
                Button(onClick = onOpenSubscription, modifier = Modifier.fillMaxWidth()) {
                    Text("View TV plans")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
    }
}
