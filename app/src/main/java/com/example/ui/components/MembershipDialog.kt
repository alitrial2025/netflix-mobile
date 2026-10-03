package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MembershipDialog(title: String, message: String, onSubscribe: () -> Unit, onTrailer: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xFF191919),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCCCCCC),
        icon = { Icon(Icons.Default.Lock, null, tint = Color(0xFFE50914), modifier = Modifier.size(32.dp)) },
        title = { Text("Watch the full story", fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(message)
            Text("You can watch the official trailer while you decide.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button(onClick = onSubscribe, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))) {
            Text("Subscribe to a plan")
        } },
        dismissButton = { TextButton(onClick = onTrailer) { Text("Continue watching trailer", color = Color.White) } }
    )
}
