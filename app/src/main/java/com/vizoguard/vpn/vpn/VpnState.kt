package com.vizoguard.vpn.vpn

import com.vizoguard.vpn.ui.ConnectionMode

enum class VpnState {
    IDLE, LICENSED, CONNECTING, CONNECTED, RECONNECTING,
    BLOCKED,  // Reserved for future use (e.g., network-level VPN blocking detection)
    ERROR
}

enum class TransportMode {
    AUTO, DIRECT, OBFUSCATED;

    companion object {
        fun fromConnectionMode(mode: ConnectionMode): TransportMode = when (mode) {
            ConnectionMode.PRIVACY -> AUTO
            ConnectionMode.STREAMING -> DIRECT
            ConnectionMode.WORK -> AUTO
        }
    }
}

data class ShadowsocksConfig(
    val host: String,
    val port: Int,
    val method: String,
    val password: String
)

data class VpnStatus(
    val state: VpnState = VpnState.IDLE,
    val errorMessage: String? = null,
    val connectedSince: Long? = null,
    val serverLocation: String? = null,
    val encryptionMethod: String? = null,
    val serverHost: String? = null,
    val transportMode: String? = null
)
