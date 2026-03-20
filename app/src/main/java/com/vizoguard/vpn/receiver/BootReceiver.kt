package com.vizoguard.vpn.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vizoguard.vpn.license.SecureStore
import com.vizoguard.vpn.util.VizoLogger
import com.vizoguard.vpn.vpn.VpnManager
import com.vizoguard.vpn.vpn.ShadowsocksService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = SecureStore.create(context)
        VizoLogger.init(context)
        VizoLogger.systemEvent("Boot received, autoConnect=${store.getAutoConnect()}")
        val accessUrl = store.getVpnAccessUrl()
        if (store.getAutoConnect() && accessUrl != null) {
            val config = VpnManager.parseShadowsocksUrl(accessUrl) ?: return
            val vpnIntent = Intent(context, ShadowsocksService::class.java).apply {
                action = VpnManager.ACTION_CONNECT
                putExtra(VpnManager.EXTRA_HOST, config.host)
                putExtra(VpnManager.EXTRA_PORT, config.port)
                putExtra(VpnManager.EXTRA_METHOD, config.method)
                putExtra(VpnManager.EXTRA_PASSWORD, config.password)
                putExtra(VpnManager.EXTRA_KILL_SWITCH, store.getKillSwitch())
            }
            context.startForegroundService(vpnIntent)
        }
    }
}
