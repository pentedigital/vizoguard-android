package com.vizoguard.vpn.vpn

enum class VpnState {
    IDLE, LICENSED, CONNECTING, CONNECTED, RECONNECTING,
    BLOCKED,  // Reserved for future use (e.g., network-level VPN blocking detection)
    ERROR
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
    val serverHost: String? = null
)
