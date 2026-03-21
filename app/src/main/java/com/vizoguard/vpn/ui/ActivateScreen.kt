package com.vizoguard.vpn.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.vizoguard.vpn.license.LicenseManager
import com.vizoguard.vpn.ui.theme.*

private val ALPHANUMERIC_REGEX = "[^A-Z0-9]".toRegex()

@Composable
fun ActivateScreen(
    onActivate: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?
) {
    var keyInput by remember { mutableStateOf("") }
    var qrError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { scanned ->
            // Extract key from deep link or raw key
            val key = if (scanned.startsWith("vizoguard-vpn://activate?key=")) {
                java.net.URLDecoder.decode(scanned.substringAfter("key="), "UTF-8")
            } else if (scanned.startsWith("VIZO-")) {
                scanned
            } else null
            if (key != null && LicenseManager.isValidKeyFormat(key)) {
                qrError = null
                onActivate(key)
            } else {
                qrError = "This QR code doesn't contain a valid Vizoguard license key"
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to", color = TextSecondary, fontSize = 16.sp)
        Text(
            "Vizoguard VPN",
            color = Accent,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(32.dp))

        Text(
            "Enter your license key to get started",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // Key input
        OutlinedTextField(
            value = keyInput,
            onValueChange = { input ->
                // Auto-format: VIZO-XXXX-XXXX-XXXX-XXXX
                val clean = input.uppercase().replace(ALPHANUMERIC_REGEX, "").take(20)
                val formatted = buildString {
                    clean.forEachIndexed { i, c ->
                        if (i == 4 || i == 8 || i == 12 || i == 16) append('-')
                        if (length < 24) append(c)
                    }
                }
                keyInput = formatted
            },
            placeholder = { Text("VIZO-XXXX-XXXX-XXXX-XXXX", color = Border) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                if (LicenseManager.isValidKeyFormat(keyInput)) onActivate(keyInput)
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Border,
                cursorColor = Accent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        // Activate button
        Button(
            onClick = { if (LicenseManager.isValidKeyFormat(keyInput)) onActivate(keyInput) },
            enabled = LicenseManager.isValidKeyFormat(keyInput) && !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
            } else {
                Text("Activate", color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
        }

        // Error message
        val displayError = errorMessage ?: qrError
        if (displayError != null) {
            Spacer(Modifier.height(12.dp))
            Text(displayError, color = Red, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(16.dp))
        Text("or", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        // QR scan button
        OutlinedButton(
            onClick = {
                val options = ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt("Scan your Vizoguard QR code")
                    setCameraId(0)
                }
                qrLauncher.launch(options)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Scan QR Code", fontSize = 13.sp) }

        Spacer(Modifier.height(24.dp))
        Text(
            "No key? Visit vizoguard.com",
            color = TextSecondary,
            fontSize = 11.sp
        )
    }
}
