package com.vizoguard.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vizoguard.vpn.license.LicenseManager
import com.vizoguard.vpn.ui.theme.*

@Composable
fun SettingsSheet(
    autoConnect: Boolean,
    killSwitch: Boolean,
    notifications: Boolean,
    licenseKey: String?,
    expiresAt: String?,
    onAutoConnectChange: (Boolean) -> Unit,
    onKillSwitchChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onOpenDebug: (() -> Unit)? = null
) {
    var tapCount by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Surface.copy(alpha = 0.95f))
            .padding(24.dp)
    ) {
        // Drag handle indicator
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 12.dp)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Border)
        )

        Text(
            "Settings",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable {
                tapCount++
                if (tapCount >= 5) {
                    tapCount = 0
                    onOpenDebug?.invoke()
                }
            }
        )
        Spacer(Modifier.height(20.dp))

        SettingRow("Auto-connect", autoConnect, onAutoConnectChange)
        PremiumDivider()
        SettingRow("Kill switch", killSwitch, onKillSwitchChange)
        PremiumDivider()
        SettingRow("Notifications", notifications, onNotificationsChange)
        PremiumDivider()

        // License info
        Spacer(Modifier.height(16.dp))
        Text("License", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(
            licenseKey?.let { LicenseManager.maskKey(it) } ?: "Not activated",
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Spacer(Modifier.height(8.dp))

        if (expiresAt != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Expires", color = TextSecondary, fontSize = 13.sp)
                Text(expiresAt.take(10), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(28.dp))
        TextButton(
            onClick = onSignOut,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text("Sign out", color = Red, fontSize = 13.sp) }
    }
}

@Composable
private fun PremiumDivider() {
    HorizontalDivider(
        color = GlassBorder,
        thickness = 0.5.dp,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 15.sp)
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextPrimary,
                checkedTrackColor = Accent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Border
            )
        )
    }
}
