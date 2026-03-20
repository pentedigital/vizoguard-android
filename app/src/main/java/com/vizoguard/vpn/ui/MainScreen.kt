package com.vizoguard.vpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
    val isConnecting = vpnStatus.state == VpnState.CONNECTING
    val isReconnecting = vpnStatus.state == VpnState.RECONNECTING
    val isError = vpnStatus.state == VpnState.ERROR
    val isBlocked = vpnStatus.state == VpnState.BLOCKED
    val isBusy = isConnecting || isReconnecting

    // Mode selector state (UI-only)
    var selectedMode by remember { mutableStateOf(ConnectionMode.PRIVACY) }

    // Elapsed seconds since connection
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

    // --- Animations ---

    // Connect button scale pulse when connecting
    val infiniteTransition = rememberInfiniteTransition(label = "mainPulse")
    val connectingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "connectingScale"
    )
    val buttonScale = if (isBusy) connectingScale else 1f

    // Outer ring glow pulse when connected
    val outerRingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outerRingAlpha"
    )

    // Breathing pulse dot
    val slowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slowPulse"
    )
    val fastPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fastPulse"
    )

    // Animated colors
    val borderColor by animateColorAsState(
        when {
            isConnected -> Teal
            isError || isBlocked -> Red
            isBusy -> Amber
            else -> Border
        }, label = "border"
    )

    val buttonBgColor by animateColorAsState(
        when {
            isConnected -> SubtleTeal
            isBusy -> Amber.copy(alpha = 0.05f)
            isError || isBlocked -> Red.copy(alpha = 0.05f)
            else -> Surface
        }, label = "buttonBg"
    )

    // Pulse dot color and alpha
    val pulseDotColor = when {
        isConnected -> Teal
        isReconnecting -> Amber
        isError || isBlocked -> Red
        else -> TextSecondary
    }
    val pulseDotAlpha = when {
        isConnected -> slowPulse
        isReconnecting || isConnecting -> fastPulse
        isError || isBlocked -> 1f
        else -> 0.4f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Privacy Score (top-left)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            PrivacyScore(vpnState = vpnStatus.state)
        }

        // Settings gear icon (top-right)
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text(
                text = "Settings",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // Main content centered
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(1f))

            // Big connect button with outer ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.scale(buttonScale)
            ) {
                // Outer ring (subtle glow when connected)
                if (isConnected) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(CircleShape)
                            .border(
                                width = 2.dp,
                                color = Teal.copy(alpha = outerRingAlpha),
                                shape = CircleShape
                            )
                    )
                }

                // Main circle button
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(buttonBgColor)
                        .border(
                            width = if (isConnected) 2.dp else 1.dp,
                            color = borderColor,
                            shape = CircleShape
                        )
                        .clickable(enabled = !isBusy) { onToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            isConnected -> "VPN\nON"
                            isBusy -> "..."
                            else -> "TAP TO\nCONNECT"
                        },
                        color = when {
                            isConnected -> Teal
                            isBusy -> Amber
                            else -> TextSecondary
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Reconnecting / Blocked amber banner
            if (isReconnecting || isBlocked) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(AmberSubtle)
                        .border(1.dp, Amber.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Protection paused -- reconnecting...",
                        color = Amber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // Status label with pulse dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Pulse dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(pulseDotColor.copy(alpha = pulseDotAlpha))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (vpnStatus.state) {
                        VpnState.CONNECTED -> "Protected"
                        VpnState.CONNECTING -> "Securing your connection..."
                        VpnState.RECONNECTING -> "Optimizing connection..."
                        VpnState.BLOCKED -> "Protection paused"
                        VpnState.ERROR -> vpnStatus.errorMessage ?: "Connection Failed"
                        else -> "Tap to get protected"
                    },
                    color = when {
                        isConnected -> Teal
                        isReconnecting -> Amber
                        isError || isBlocked -> Red
                        isBusy -> Amber
                        else -> Red
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Duration timer (only when connected)
            if (isConnected && vpnStatus.connectedSince != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = formatDuration(elapsedSeconds),
                    color = TextSecondary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light
                )
            }

            Spacer(Modifier.height(16.dp))

            // Mode Selector
            ModeSelector(
                selectedMode = selectedMode,
                onModeSelected = { selectedMode = it }
            )

            Spacer(Modifier.height(24.dp))

            // Error action buttons
            if (isError) {
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Try Again", color = Color.Black) }
                Spacer(Modifier.height(12.dp))
            }

            if (isBlocked) {
                OutlinedButton(
                    onClick = onToggle,
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Disable Kill Switch") }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.weight(1f))

            // Engine View (only when connected)
            if (isConnected) {
                EngineView(
                    vpnStatus = vpnStatus,
                    elapsedSeconds = elapsedSeconds
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

internal fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
