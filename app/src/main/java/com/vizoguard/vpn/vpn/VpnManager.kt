package com.vizoguard.vpn.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import com.vizoguard.vpn.util.Tag
import com.vizoguard.vpn.util.VizoLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VpnManager(private val context: Context, private val scope: CoroutineScope) {

    private val _status = MutableStateFlow(VpnStatus())
    val status: StateFlow<VpnStatus> = _status

    /** Guards against rapid-fire connect calls clearing pendingConfig */
    private val connectLock = Any()

    init {
        scope.launch {
            ShadowsocksService.serviceState.collect { state ->
                // Map IDLE from service to LICENSED if we have a license (post-disconnect)
                val mappedState = if (state == VpnState.IDLE && _status.value.state != VpnState.IDLE) {
                    VpnState.LICENSED
                } else {
                    state
                }
                val errorMsg = if (mappedState == VpnState.ERROR) ShadowsocksService.serviceError.value else null
                updateState(mappedState, errorMsg)
            }
        }
    }

    fun updateState(state: VpnState, errorMessage: String? = null) {
        VizoLogger.vpnState(_status.value.state.name, state.name)
        _status.value = VpnStatus(
            state = state,
            errorMessage = errorMessage,
            connectedSince = if (state == VpnState.CONNECTED) {
                _status.value.connectedSince ?: System.currentTimeMillis()
            } else if (state == VpnState.LICENSED || state == VpnState.IDLE) {
                null
            } else {
                _status.value.connectedSince
            },
            serverLocation = _status.value.serverLocation,
            encryptionMethod = _status.value.encryptionMethod,
            serverHost = _status.value.serverHost
        )
    }

    fun startVpn(accessUrl: String, killSwitch: Boolean) {
        val config = parseShadowsocksUrl(accessUrl) ?: run {
            updateState(VpnState.ERROR, "Invalid VPN configuration")
            return
        }

        synchronized(connectLock) {
            // Reject rapid-fire connect if already connecting
            if (_status.value.state == VpnState.CONNECTING) {
                VizoLogger.w(Tag.VPN, "startVpn ignored — already connecting")
                return
            }

            // Atomic update: set server info and state together to avoid race
            _status.value = _status.value.copy(
                state = VpnState.CONNECTING,
                serverHost = config.host,
                encryptionMethod = config.method
            )
            // Store parsed config in companion for service to read (avoids password in Intent extras)
            pendingConfig.set(config)
        }
        val intent = Intent(context, ShadowsocksService::class.java).apply {
            action = ACTION_CONNECT
            putExtra(EXTRA_KILL_SWITCH, killSwitch)
        }
        context.startForegroundService(intent)
    }

    fun stopVpn() {
        val intent = Intent(context, ShadowsocksService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        context.startService(intent)
        // Don't set LICENSED immediately — let service emit IDLE via serviceState,
        // then the collector maps it to LICENSED
    }

    /** Called by AppState after disconnect intent is sent */
    fun markLicensed() {
        updateState(VpnState.LICENSED)
    }

    companion object {
        const val ACTION_CONNECT = "com.vizoguard.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.vizoguard.vpn.DISCONNECT"
        const val EXTRA_KILL_SWITCH = "kill_switch"

        /** In-process config handoff — atomic to prevent race between VpnManager and BootReceiver */
        val pendingConfig = AtomicReference<ShadowsocksConfig?>(null)

        fun parseShadowsocksUrl(url: String): ShadowsocksConfig? {
            if (!url.startsWith("ss://")) return null
            return try {
                val withoutScheme = url.removePrefix("ss://")
                val atIndex = withoutScheme.lastIndexOf('@')
                if (atIndex == -1) return null

                val encoded = withoutScheme.substring(0, atIndex)
                val hostPort = withoutScheme.substring(atIndex + 1).split("/?")[0]

                val decoded = try {
                    String(Base64.getUrlDecoder().decode(encoded))
                } catch (_: IllegalArgumentException) {
                    String(Base64.getDecoder().decode(encoded))
                }
                val colonIndex = decoded.indexOf(':')
                if (colonIndex == -1) return null

                val method = decoded.substring(0, colonIndex)
                val password = decoded.substring(colonIndex + 1)

                val parts = hostPort.split(':')
                if (parts.size != 2) return null

                ShadowsocksConfig(
                    host = parts[0],
                    port = parts[1].toInt(),
                    method = method,
                    password = password
                )
            } catch (e: Exception) {
                VizoLogger.e(Tag.VPN, "Failed to parse SS URL", e)
                null
            }
        }
    }
}
