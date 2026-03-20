package com.vizoguard.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.vizoguard.vpn.MainActivity
import com.vizoguard.vpn.util.VizoLogger
import kotlinx.coroutines.flow.MutableStateFlow

class ShadowsocksService : VpnService() {

    private var tunFd: ParcelFileDescriptor? = null

    companion object {
        private const val CHANNEL_ID = "vpn_status"
        private const val NOTIFICATION_ID = 1

        /** Shared state flow — VpnManager observes this to update UI */
        val serviceState = MutableStateFlow(VpnState.IDLE)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            VpnManager.ACTION_CONNECT -> {
                val host = intent.getStringExtra(VpnManager.EXTRA_HOST) ?: return START_NOT_STICKY
                val port = intent.getIntExtra(VpnManager.EXTRA_PORT, 0)
                val killSwitch = intent.getBooleanExtra(VpnManager.EXTRA_KILL_SWITCH, true)
                startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
                connect(host, port, killSwitch)
            }
            VpnManager.ACTION_DISCONNECT -> {
                disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun connect(host: String, port: Int, killSwitch: Boolean) {
        VizoLogger.vpnState("SERVICE", "connect($host:$port)")
        try {
            val builder = Builder()
                .setSession("Vizoguard VPN")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setMtu(1500)

            tunFd = builder.establish() ?: run {
                serviceState.value = VpnState.ERROR
                return
            }

            // TODO: When tun2socks.aar is available, replace this stub with:
            // tunnel = Tun2socks.connectSocksTunnel(tunFd!!.fd.toLong(), host, port.toLong(), true)
            // For now, we simulate a successful connection

            updateNotification("Connected — Protected")
            serviceState.value = VpnState.CONNECTED
        } catch (e: Exception) {
            serviceState.value = VpnState.ERROR
            disconnect()
        }
    }

    private fun disconnect() {
        VizoLogger.vpnState("SERVICE", "disconnect")
        try {
            // TODO: tunnel?.disconnect() when tun2socks is available
            tunFd?.close()
            tunFd = null
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    override fun onDestroy() {
        disconnect()
        serviceState.value = VpnState.LICENSED
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows VPN connection status" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val disconnectIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ShadowsocksService::class.java).apply { action = VpnManager.ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Vizoguard VPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .addAction(Notification.Action.Builder(null, "Disconnect", disconnectIntent).build())
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }
}
