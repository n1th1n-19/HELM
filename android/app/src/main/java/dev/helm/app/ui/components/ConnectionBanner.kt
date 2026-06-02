package dev.helm.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.helm.app.data.websocket.ConnectionState
import dev.helm.app.ui.theme.*

@Composable
fun ConnectionBanner(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val visible = connectionState != ConnectionState.Connected
    val (bg, text) = when (connectionState) {
        ConnectionState.Disconnected -> HelmError.copy(alpha = 0.85f) to "DISCONNECTED — WAITING FOR AGENT"
        ConnectionState.Connecting   -> HelmWarning.copy(alpha = 0.85f) to "CONNECTING TO AGENT..."
        ConnectionState.Reconnecting -> HelmWarning.copy(alpha = 0.85f) to "RECONNECTING..."
        ConnectionState.Connected    -> HelmSuccess.copy(alpha = 0.85f) to ""
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bg)
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = HelmBackground,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
    }
}
