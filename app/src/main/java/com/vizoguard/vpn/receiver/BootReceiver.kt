package com.vizoguard.vpn.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vizoguard.vpn.license.SecureStore
import com.vizoguard.vpn.util.Tag
import com.vizoguard.vpn.util.VizoLogger
import com.vizoguard.vpn.vpn.VpnManager
import com.vizoguard.vpn.vpn.ConfigBuilder
import com.vizoguard.vpn.vpn.VpnTunnelService
import com.vizoguard.vpn.worker.LicenseCheckWorker
import java.time.Instant

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = SecureStore.create(context)
        VizoLogger.init(context)

        // Ensure license check worker is scheduled after every boot
        LicenseCheckWorker.schedule(context)

        VizoLogger.systemEvent("Boot received, autoConnect=${store.getAutoConnect()}")
        val accessUrl = store.getVpnAccessUrl()
        if (store.getAutoConnect() && accessUrl != null) {
            // Validate cached license before starting VPN service
            val status = store.getLicenseStatus()
            if (status != "active" && status != "cancelled") {
                VizoLogger.systemEvent("Boot auto-connect skipped: license status is '${status ?: "null"}', not active/cancelled")
                return
            }
            val expiryStr = store.getLicenseExpiry()
            if (expiryStr != null) {
                try {
                    val expiry = Instant.parse(expiryStr)
                    if (Instant.now().isAfter(expiry)) {
                        VizoLogger.systemEvent("Boot auto-connect skipped: license expired at $expiryStr")
                        return
                    }
                } catch (e: Exception) {
                    VizoLogger.systemEvent("Boot auto-connect skipped: invalid expiry format '$expiryStr'")
                    return
                }
            }

            val ssConfig = VpnManager.parseShadowsocksUrl(accessUrl) ?: return
            val configJson = ConfigBuilder.buildShadowsocks(ssConfig)
            VpnManager.pendingConfig.set(configJson)
            val vpnIntent = Intent(context, VpnTunnelService::class.java).apply {
                action = VpnManager.ACTION_CONNECT
            }
            try {
                context.startForegroundService(vpnIntent)
            } catch (e: Exception) {
                VizoLogger.e(Tag.SYSTEM, "Boot auto-connect failed: ${e.message}")
            }
        }
    }
}
