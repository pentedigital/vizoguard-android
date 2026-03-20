package com.vizoguard.vpn.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vizoguard.vpn.ui.theme.*
import com.vizoguard.vpn.vpn.VpnStatus
import kotlinx.coroutines.delay

@Composable
fun EngineView(
    vpnStatus: VpnStatus,
    elapsedSeconds: Long
) {
    var expanded by remember { mutableStateOf(false) }

    // "What Just Happened?" rotating messages
    val messages = listOf(
        "Your real IP address is hidden from websites",
        "All traffic is encrypted end-to-end",
        "DNS queries are private and secure"
    )
    var messageIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        while (true) {
            delay(4000L)
            messageIndex = (messageIndex + 1) % messages.size
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "engineGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val shape = RoundedCornerShape(20.dp)
    val borderColor = Teal.copy(alpha = glowAlpha)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .background(GlassSurface)
            .clickable { expanded = !expanded }
            .padding(16.dp)
    ) {
        // Collapsed header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Teal)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Secure tunnel active",
                    color = Teal,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = if (expanded) "^" else "v",
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Expanded details
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                EngineStatRow(
                    icon = "[E]",
                    label = "Encryption",
                    value = vpnStatus.encryptionMethod ?: "---"
                )
                Spacer(Modifier.height(8.dp))
                EngineStatRow(
                    icon = "[S]",
                    label = "Server",
                    value = vpnStatus.serverHost ?: "---"
                )
                Spacer(Modifier.height(8.dp))
                EngineStatRow(
                    icon = "[T]",
                    label = "Uptime",
                    value = formatDuration(elapsedSeconds)
                )
                Spacer(Modifier.height(8.dp))
                EngineStatRow(
                    icon = "[M]",
                    label = "IP Masked",
                    value = "Yes"
                )
                Spacer(Modifier.height(8.dp))
                EngineStatRow(
                    icon = "[N]",
                    label = "DNS",
                    value = "Encrypted (1.1.1.1)"
                )

                // "What Just Happened?" section
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassSurfaceDark)
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "What Just Happened?",
                            color = Teal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = messages[messageIndex],
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineStatRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            color = Teal,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}
