package com.vizoguard.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vizoguard.vpn.ui.theme.*

enum class ConnectionMode { PRIVACY, STREAMING, WORK }

@Composable
fun ModeSelector(
    selectedMode: ConnectionMode,
    onModeSelected: (ConnectionMode) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.Center
    ) {
        ModePill(
            icon = "[P]",
            title = "Privacy",
            subtitle = "Maximum security",
            selected = selectedMode == ConnectionMode.PRIVACY,
            onClick = { onModeSelected(ConnectionMode.PRIVACY) }
        )
        Spacer(Modifier.width(8.dp))
        ModePill(
            icon = "[S]",
            title = "Streaming",
            subtitle = "Optimized speed",
            selected = selectedMode == ConnectionMode.STREAMING,
            onClick = { onModeSelected(ConnectionMode.STREAMING) }
        )
        Spacer(Modifier.width(8.dp))
        ModePill(
            icon = "[W]",
            title = "Work",
            subtitle = "Balanced",
            selected = selectedMode == ConnectionMode.WORK,
            onClick = { onModeSelected(ConnectionMode.WORK) }
        )
    }
}

@Composable
private fun ModePill(
    icon: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val bgColor = if (selected) SubtleTeal else androidx.compose.ui.graphics.Color.Transparent
    val borderColor = if (selected) Teal else Border

    Column(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = icon,
            color = if (selected) Teal else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = title,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = subtitle,
            color = TextSecondary,
            fontSize = 9.sp
        )
    }
}
