package com.vizoguard.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            .background(Surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(24.dp)
    ) {
        Text(
            "Settings",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable {
                tapCount++
                if (tapCount >= 5) {
                    tapCount = 0
                    onOpenDebug?.invoke()
                }
            }
        )
        Spacer(Modifier.height(16.dp))

        SettingRow("Auto-connect", autoConnect, onAutoConnectChange)
        HorizontalDivider(color = Border)
        SettingRow("Kill switch", killSwitch, onKillSwitchChange)
        HorizontalDivider(color = Border)
        SettingRow("Notifications", notifications, onNotificationsChange)
        HorizontalDivider(color = Border)

        // License info
        Spacer(Modifier.height(12.dp))
        Text("License", color = TextSecondary, fontSize = 13.sp)
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
                Text(expiresAt.take(10), color = Accent, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(
            onClick = onSignOut,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text("Sign out", color = Red, fontSize = 13.sp) }
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp)
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
