package com.vizoguard.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vizoguard.vpn.license.LicenseManager
import com.vizoguard.vpn.ui.*
import com.vizoguard.vpn.ui.theme.VizoguardTheme
import com.vizoguard.vpn.vpn.VpnState

class MainActivity : ComponentActivity() {

    private var pendingVpnConnect: (() -> Unit)? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingVpnConnect?.invoke()
        }
        pendingVpnConnect = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)

        setContent {
            VizoguardTheme {
                val appState: AppState = viewModel()
                val screen by appState.screen.collectAsState()
                val vpnStatus by appState.vpnManager.status.collectAsState()
                val isLoading by appState.isLoading.collectAsState()
                val errorMessage by appState.errorMessage.collectAsState()
                var showSettings by remember { mutableStateOf(false) }
                val store = appState.licenseManager.getCachedState()

                when (screen) {
                    Screen.ACTIVATE -> ActivateScreen(
                        onActivate = { key -> appState.activate(key) },
                        isLoading = isLoading,
                        errorMessage = errorMessage
                    )
                    Screen.ONBOARDING -> OnboardingSheet(
                        onChoice = { auto -> appState.finishOnboarding(auto) }
                    )
                    Screen.MAIN -> {
                        MainScreen(
                            vpnStatus = vpnStatus,
                            onToggle = {
                                if (vpnStatus.state == VpnState.CONNECTED ||
                                    vpnStatus.state == VpnState.CONNECTING) {
                                    appState.disconnect()
                                } else {
                                    requestVpnPermissionAndConnect { appState.connect() }
                                }
                            },
                            onSettingsClick = { showSettings = true }
                        )

                        if (showSettings) {
                            ModalBottomSheet(onDismissRequest = { showSettings = false }) {
                                SettingsSheet(
                                    autoConnect = appState.getStore().getAutoConnect(),
                                    killSwitch = appState.getStore().getKillSwitch(),
                                    notifications = appState.getStore().getNotifications(),
                                    licenseKey = store.key,
                                    expiresAt = store.expires,
                                    onAutoConnectChange = { appState.getStore().saveAutoConnect(it) },
                                    onKillSwitchChange = { appState.getStore().saveKillSwitch(it) },
                                    onNotificationsChange = { appState.getStore().saveNotifications(it) },
                                    onSignOut = { showSettings = false; appState.signOut() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "vizoguard-vpn" && uri.host == "activate") {
            val key = uri.getQueryParameter("key")
            if (key != null && LicenseManager.isValidKeyFormat(key)) {
                val appState = ViewModelProvider(this)[AppState::class.java]
                appState.activate(key)
            }
        }
    }

    private fun requestVpnPermissionAndConnect(onGranted: () -> Unit) {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            pendingVpnConnect = onGranted
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            onGranted()
        }
    }
}
