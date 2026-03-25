package com.vizoguard.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vizoguard.vpn.license.LicenseManager
import com.vizoguard.vpn.ui.theme.*
import com.vizoguard.vpn.util.VizoLogger
import com.vizoguard.vpn.vpn.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun DebugScreen(
    vpnStatus: VpnStatus,
    licenseKey: String?,
    licenseStatus: String?,
    licenseExpiry: String?,
    vpnAccessUrlLen: Int?,
    onExportLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var logText by remember { mutableStateOf("Loading logs...") }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    // Load logs on background thread, refresh every 3s or on clear
    LaunchedEffect(refreshKey) {
        while (isActive) {
            logText = withContext(Dispatchers.IO) {
                VizoLogger.getAllLogText(context)
            }
            delay(3000L)
        }
    }

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Debug", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDismiss) { Text("Close", color = TextSecondary) }
        }

        Spacer(Modifier.height(12.dp))

        // State info
        DebugRow("VPN State", vpnStatus.state.name)
        DebugRow("Error", vpnStatus.errorMessage ?: "none")
        DebugRow("License", licenseKey?.let { LicenseManager.maskKey(it) } ?: "none")
        DebugRow("Status", licenseStatus ?: "none")
        DebugRow("Expires", licenseExpiry?.take(10) ?: "none")
        DebugRow("VPN URL", if (vpnAccessUrlLen != null) "cached ($vpnAccessUrlLen chars)" else "none")

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExportLogs, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                Text("Export Logs", color = Surface, fontSize = 12.sp)
            }
            OutlinedButton(onClick = {
                onClearLogs()
                refreshKey++ // trigger immediate reload
            }) {
                Text("Clear Logs", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Log viewer
        Text("Recent Logs", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Surface, shape = RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Text(
                text = logText.takeLast(5000),
                color = TextSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp,
                modifier = Modifier.verticalScroll(scrollState)
            )
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 11.sp)
        Text(value, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}
