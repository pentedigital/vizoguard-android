package com.vizoguard.vpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vizoguard.vpn.ui.theme.*
import com.vizoguard.vpn.vpn.VpnState
import com.vizoguard.vpn.vpn.VpnStatus
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    vpnStatus: VpnStatus,
    onToggle: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val isConnected = vpnStatus.state == VpnState.CONNECTED

    // Elapsed seconds since connection, updated every second to drive recomposition
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isConnected, vpnStatus.connectedSince) {
        if (isConnected && vpnStatus.connectedSince != null) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - vpnStatus.connectedSince) / 1000
                delay(1000L)
            }
        } else {
            elapsedSeconds = 0L
        }
    }
    val isConnecting = vpnStatus.state == VpnState.CONNECTING || vpnStatus.state == VpnState.RECONNECTING
    val borderColor by animateColorAsState(
        when {
            isConnected -> Teal
            vpnStatus.state == VpnState.ERROR || vpnStatus.state == VpnState.BLOCKED -> Red
            else -> Border
        }, label = "border"
    )

    Column(
        modifier = Modifier.fillMaxSize().background(Background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // VPN toggle circle
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(if (isConnected) Teal.copy(alpha = 0.05f) else Color.Transparent)
                .clickable(enabled = !isConnecting) { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            // Outer ring
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .then(
                        Modifier.background(Color.Transparent) // border handled by surface
                    )
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when {
                        isConnected -> "VPN\nON"
                        isConnecting -> "..."
                        else -> "TAP TO\nCONNECT"
                    },
                    color = if (isConnected) Teal else TextSecondary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Status text
        Text(
            text = when (vpnStatus.state) {
                VpnState.CONNECTED -> "Protected"
                VpnState.CONNECTING -> "Connecting..."
                VpnState.RECONNECTING -> "Reconnecting..."
                VpnState.BLOCKED -> "Internet Paused"
                VpnState.ERROR -> vpnStatus.errorMessage ?: "Connection Failed"
                else -> "Not Protected"
            },
            color = when {
                isConnected -> Teal
                vpnStatus.state == VpnState.ERROR || vpnStatus.state == VpnState.BLOCKED -> Red
                else -> Red
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(32.dp))

        // Stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatCard("Status", if (isConnected) "Connected" else "Disconnected")
            Spacer(Modifier.width(12.dp))
            StatCard("Duration", if (isConnected && vpnStatus.connectedSince != null) {
                formatDuration(elapsedSeconds)
            } else "--:--")
        }

        // Error action buttons
        if (vpnStatus.state == VpnState.ERROR) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onToggle,
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text("Try Again", color = Color.Black) }
        }

        if (vpnStatus.state == VpnState.BLOCKED) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onToggle) { Text("Disable Kill Switch") }
        }

        Spacer(Modifier.weight(1f))

        // Settings gear
        TextButton(onClick = onSettingsClick) {
            Text("Settings", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = TextSecondary, fontSize = 11.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
