package com.vizoguard.vpn.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Base64
import com.vizoguard.vpn.util.VizoLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VpnManager(private val context: Context, private val scope: CoroutineScope) {

    private val _status = MutableStateFlow(VpnStatus())
    val status: StateFlow<VpnStatus> = _status

    init {
        scope.launch {
            ShadowsocksService.serviceState.collect { state ->
                updateState(state)
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
            }
        )
    }

    fun startVpn(accessUrl: String, killSwitch: Boolean) {
        val config = parseShadowsocksUrl(accessUrl) ?: run {
            updateState(VpnState.ERROR, "Invalid VPN configuration")
            return
        }
        updateState(VpnState.CONNECTING)
        val intent = Intent(context, ShadowsocksService::class.java).apply {
            action = ACTION_CONNECT
            putExtra(EXTRA_HOST, config.host)
            putExtra(EXTRA_PORT, config.port)
            putExtra(EXTRA_METHOD, config.method)
            putExtra(EXTRA_PASSWORD, config.password)
            putExtra(EXTRA_KILL_SWITCH, killSwitch)
        }
        context.startForegroundService(intent)
    }

    fun stopVpn() {
        val intent = Intent(context, ShadowsocksService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        context.startService(intent)
        updateState(VpnState.LICENSED)
    }

    companion object {
        const val ACTION_CONNECT = "com.vizoguard.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.vizoguard.vpn.DISCONNECT"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_METHOD = "method"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_KILL_SWITCH = "kill_switch"

        fun parseShadowsocksUrl(url: String): ShadowsocksConfig? {
            if (!url.startsWith("ss://")) return null
            return try {
                val withoutScheme = url.removePrefix("ss://")
                val atIndex = withoutScheme.lastIndexOf('@')
                if (atIndex == -1) return null

                val encoded = withoutScheme.substring(0, atIndex)
                val hostPort = withoutScheme.substring(atIndex + 1).split("/?")[0]

                val decoded = String(Base64.decode(encoded, Base64.DEFAULT))
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
            } catch (e: Exception) { null }
        }
    }
}
