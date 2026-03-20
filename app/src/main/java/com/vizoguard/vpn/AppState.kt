package com.vizoguard.vpn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vizoguard.vpn.api.ApiClient
import com.vizoguard.vpn.license.DeviceId
import com.vizoguard.vpn.license.LicenseManager
import com.vizoguard.vpn.license.SecureStore
import com.vizoguard.vpn.vpn.VpnManager
import com.vizoguard.vpn.vpn.VpnState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class Screen { ACTIVATE, MAIN, ONBOARDING }

class AppState(app: Application) : AndroidViewModel(app) {

    private val store = SecureStore.create(app)
    private val api = ApiClient()
    private val deviceId = DeviceId.get(app)
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
        if (cached.isValid) {
            _screen.value = Screen.MAIN
            vpnManager.updateState(VpnState.LICENSED)
            if (store.getAutoConnect() && cached.vpnAccessUrl != null) {
                connect()
            }
        }
        viewModelScope.launch {
            licenseManager.validate()
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

    fun finishOnboarding(autoConnect: Boolean) {
        store.saveAutoConnect(autoConnect)
        _screen.value = Screen.MAIN
        if (autoConnect) connect()
    }

    fun connect() {
        val state = licenseManager.getCachedState()
        val accessUrl = state.vpnAccessUrl ?: return
        vpnManager.startVpn(accessUrl, store.getKillSwitch())
    }

    fun disconnect() {
        vpnManager.stopVpn()
    }

    fun signOut() {
        vpnManager.stopVpn()
        licenseManager.signOut()
        _screen.value = Screen.ACTIVATE
        vpnManager.updateState(VpnState.IDLE)
    }

    fun getStore() = store

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
