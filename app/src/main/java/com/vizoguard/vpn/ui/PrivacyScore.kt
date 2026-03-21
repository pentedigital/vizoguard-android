package com.vizoguard.vpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vizoguard.vpn.ui.theme.*
import com.vizoguard.vpn.vpn.VpnState

@Composable
fun PrivacyScore(vpnState: VpnState) {
    val isError = vpnState == VpnState.ERROR || vpnState == VpnState.BLOCKED
    val isConnecting = vpnState == VpnState.CONNECTING || vpnState == VpnState.RECONNECTING
    val isConnected = vpnState == VpnState.CONNECTED
    val isIdle = vpnState == VpnState.IDLE || vpnState == VpnState.LICENSED

    val activeBars = when {
        isConnected -> 3
        isConnecting -> 2
        isIdle -> 1
        isError -> 3
        else -> 1
    }

    val barColor by animateColorAsState(
        targetValue = when {
            isConnected -> Teal
            isConnecting -> Amber
            isError -> Red
            isIdle -> TextSecondary
            else -> Red
        },
        animationSpec = tween(400),
        label = "barColor"
    )

    val inactiveColor = Border

    // Pulse for error state
    val infiniteTransition = rememberInfiniteTransition(label = "privacyPulse")
    val errorPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "errorPulse"
    )
    val alphaModifier = if (isError) errorPulse else 1f

    val label = when {
        isConnected -> "Fully Protected"
        isConnecting -> "Connecting..."
        isError -> "At Risk"
        isIdle -> "Ready"
        else -> "Exposed"
    }

    val labelColor by animateColorAsState(
        targetValue = when {
            isConnected -> Teal
            isConnecting -> Amber
            isIdle -> TextSecondary
            else -> Red
        },
        animationSpec = tween(400),
        label = "labelColor"
    )

    Column(
        modifier = Modifier
            .width(60.dp)
            .height(70.dp)
            .alpha(alphaModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Bar 3 (top, largest)
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (activeBars >= 3) barColor else inactiveColor)
        )
        Spacer(Modifier.height(4.dp))
        // Bar 2 (middle)
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (activeBars >= 2) barColor else inactiveColor)
        )
        Spacer(Modifier.height(4.dp))
        // Bar 1 (bottom, smallest)
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (activeBars >= 1) barColor else inactiveColor)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = labelColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
