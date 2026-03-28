package com.vizoguard.vpn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vizoguard.vpn.api.ApiClient
import com.vizoguard.vpn.license.DeviceId
import com.vizoguard.vpn.license.LicenseManager
import com.vizoguard.vpn.license.SecureStore
import com.vizoguard.vpn.util.Tag
import com.vizoguard.vpn.util.VizoLogger
import com.vizoguard.vpn.vpn.ConfigBuilder
import com.vizoguard.vpn.vpn.VpnManager
import com.vizoguard.vpn.vpn.VpnState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class Screen { ACTIVATE, MAIN, ONBOARDING }

class AppState(app: Application) : AndroidViewModel(app) {

    private val store = SecureStore.create(app)
    private val api = ApiClient()
    private val deviceId = DeviceId.get(store)
    val licenseManager = LicenseManager(store, api, deviceId)
    val vpnManager = VpnManager(app, viewModelScope)

    private val _screen = MutableStateFlow(Screen.ACTIVATE)
    val screen: StateFlow<Screen> = _screen

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        val cached = licenseManager.getCachedState()
        VizoLogger.systemEvent("AppState init: valid=${cached.isValid}, autoConnect=${store.getAutoConnect()}, hasVpnUrl=${cached.vpnAccessUrl != null}")

        if (cached.isValid) {
            // Don't set screen to MAIN yet — wait for async validation to confirm
            vpnManager.updateState(VpnState.LICENSED)
        }

        // Validate license async, then set screen and auto-connect only on confirmed success
        viewModelScope.launch {
            if (!cached.isValid) return@launch // Already on ACTIVATE screen

            val result = licenseManager.validate()
            if (result.isSuccess) {
                val state = licenseManager.getCachedState()
                if (state.isValid) {
                    _screen.value = Screen.MAIN
                    if (store.getAutoConnect() && state.vpnAccessUrl != null) {
                        VizoLogger.systemEvent("Auto-connecting after validation")
                        connect()
                    }
                } else {
                    VizoLogger.systemEvent("License invalidated by server — disconnecting")
                    vpnManager.stopVpn()
                    _screen.value = Screen.ACTIVATE
                }
            } else {
                // Network error — trust cached license, show MAIN
                VizoLogger.systemEvent("License validation failed (network error) — using cached state")
                _screen.value = Screen.MAIN
            }
        }
    }

    fun activate(key: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = licenseManager.activate(key)
            _isLoading.value = false
            result.fold(
                onSuccess = { state ->
                    if (state.isValid) {
                        _screen.value = Screen.ONBOARDING
                        vpnManager.updateState(VpnState.LICENSED)
                    }
                },
                onFailure = { e ->
                    _errorMessage.value = userFriendlyError(e)
                }
            )
        }
    }

    /** Called from deep link — guards against replacing an active license */
    fun activateFromDeepLink(key: String) {
        val existing = licenseManager.getCachedState()
        if (existing.isValid) {
            VizoLogger.systemEvent("Deep link activation blocked — license already active")
            return
        }
        activate(key)
    }

    fun finishOnboarding(autoConnect: Boolean) {
        store.saveAutoConnect(autoConnect)
        _screen.value = Screen.MAIN
        if (autoConnect) connect()
    }

    fun connect() {
        viewModelScope.launch {
            try {
                // Always validate before connecting — single source of truth
                val result = licenseManager.validate()
                if (result.isFailure) {
                    val ex = result.exceptionOrNull()
                    if (ex is com.vizoguard.vpn.api.ApiException && ex.httpStatus in 400..499) {
                        // Server explicitly rejected — stop VPN
                        vpnManager.stopVpn()
                        _errorMessage.value = "License validation failed"
                        return@launch
                    }
                    // Network error — try offline grace period before giving up
                    if (licenseManager.canConnectOffline()) {
                        val state = licenseManager.getCachedState()
                        val accessUrl = state.vpnAccessUrl
                        if (accessUrl != null) {
                            val ssConfig = VpnManager.parseShadowsocksUrl(accessUrl)
                            if (ssConfig != null) {
                                vpnManager.startVpn(ConfigBuilder.buildShadowsocks(ssConfig))
                                return@launch
                            }
                        }
                    }
                    _errorMessage.value = "Can't reach server. Check your internet connection."
                    return@launch
                }
                val state = licenseManager.getCachedState()
                if (!state.isValid) {
                    vpnManager.stopVpn()
                    _errorMessage.value = "License is ${state.status}"
                    return@launch
                }
                val accessUrl = state.vpnAccessUrl
                if (accessUrl == null) {
                    _errorMessage.value = "No VPN key available"
                    return@launch
                }
                val ssConfig = VpnManager.parseShadowsocksUrl(accessUrl)
                if (ssConfig == null) {
                    _errorMessage.value = "Invalid VPN configuration"
                    return@launch
                }
                vpnManager.startVpn(ConfigBuilder.buildShadowsocks(ssConfig))
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Connection failed"
            }
        }
    }

    fun disconnect() {
        vpnManager.stopVpn()
    }

    fun signOut() {
        vpnManager.stopVpn()
        licenseManager.signOut()
        _screen.value = Screen.ACTIVATE
        // Don't force IDLE — let service lifecycle handle state transition
    }

    fun getStore() = store

    override fun onCleared() {
        super.onCleared()
        api.close()
    }

    private fun userFriendlyError(e: Throwable): String {
        if (e is com.vizoguard.vpn.api.ApiException) {
            return when (e.status) {
                "expired" -> "Your plan has expired. Renew at vizoguard.com"
                "suspended" -> "Payment issue. Update payment at vizoguard.com"
                "device_mismatch" -> "This key is active on another device. Contact support@vizoguard.com"
                else -> e.message ?: "Something went wrong"
            }
        }
        return "Can't reach server. Check your internet connection."
    }

    companion object {
        fun screenForState(vpnState: VpnState, hasLicense: Boolean): Screen {
            if (!hasLicense) return Screen.ACTIVATE
            return Screen.MAIN
        }

        fun planDisplayName(apiValue: String?): String = when (apiValue) {
            "vpn" -> "Basic"
            "security_vpn" -> "Pro"
            else -> "Unknown"
        }
    }
}
