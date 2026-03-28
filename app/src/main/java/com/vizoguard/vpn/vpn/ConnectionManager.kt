package com.vizoguard.vpn.vpn

import com.vizoguard.vpn.license.SecureStore
import com.vizoguard.vpn.ui.ConnectionMode
import com.vizoguard.vpn.util.Tag
import com.vizoguard.vpn.util.VizoLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class ConnectionManager(private val store: SecureStore) {

    suspend fun selectTransport(
        ssUrl: String,
        vlessUuid: String?,
        mode: ConnectionMode
    ): String {
        val transportMode = TransportMode.fromConnectionMode(mode)
        val ssConfig = VpnManager.parseShadowsocksUrl(ssUrl)
            ?: throw IllegalArgumentException("Invalid Shadowsocks URL")

        return when (transportMode) {
            TransportMode.DIRECT -> {
                VizoLogger.d(Tag.VPN, "Transport: direct (user-selected)")
                ConfigBuilder.buildShadowsocks(ssConfig)
            }
            TransportMode.OBFUSCATED -> {
                requireNotNull(vlessUuid) { "VLESS UUID required for obfuscated mode" }
                val serverIp = resolveServerIp("vizoguard.com")
                VizoLogger.d(Tag.VPN, "Transport: obfuscated (user-selected)")
                ConfigBuilder.buildVless(vlessUuid, serverIp)
            }
            TransportMode.AUTO -> autoSelect(ssConfig, vlessUuid)
        }
    }

    private suspend fun autoSelect(
        ssConfig: ShadowsocksConfig,
        vlessUuid: String?
    ): String {
        val cacheKey = getNetworkCacheKey()
        val cached = cacheKey?.let { store.getTransportCache(it) }
        if (cached != null) {
            VizoLogger.d(Tag.VPN, "Transport: cached=$cached for network=$cacheKey")
            return when (cached) {
                "obfuscated" -> if (vlessUuid != null) {
                    ConfigBuilder.buildVless(vlessUuid, resolveServerIp("vizoguard.com"))
                } else ConfigBuilder.buildShadowsocks(ssConfig)
                else -> ConfigBuilder.buildShadowsocks(ssConfig)
            }
        }

        VizoLogger.d(Tag.VPN, "Transport: auto — probing direct ${ssConfig.host}:${ssConfig.port}")
        val directOk = probeDirectConnection(ssConfig.host, ssConfig.port, 5000)

        if (directOk) {
            VizoLogger.d(Tag.VPN, "Transport: direct (probe succeeded)")
            cacheKey?.let { store.saveTransportCache(it, "direct") }
            return ConfigBuilder.buildShadowsocks(ssConfig)
        }

        if (vlessUuid != null) {
            VizoLogger.d(Tag.VPN, "Transport: obfuscated (direct probe failed)")
            cacheKey?.let { store.saveTransportCache(it, "obfuscated") }
            return ConfigBuilder.buildVless(vlessUuid, resolveServerIp("vizoguard.com"))
        }

        VizoLogger.w(Tag.VPN, "Transport: direct fallback (no VLESS UUID, direct probe failed)")
        return ConfigBuilder.buildShadowsocks(ssConfig)
    }

    private suspend fun probeDirectConnection(host: String, port: Int, timeoutMs: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun getNetworkCacheKey(): String? {
        return null // Simplified for initial implementation
    }

    private suspend fun resolveServerIp(hostname: String): String {
        return withContext(Dispatchers.IO) {
            try {
                InetAddress.getByName(hostname).hostAddress ?: hostname
            } catch (_: Exception) {
                hostname
            }
        }
    }
}
