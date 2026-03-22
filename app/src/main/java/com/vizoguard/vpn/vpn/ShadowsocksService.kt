package com.vizoguard.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.vizoguard.vpn.MainActivity
import com.vizoguard.vpn.util.Tag
import com.vizoguard.vpn.util.VizoLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import tun2socks.Tun2socks
import tun2socks.Tunnel

class ShadowsocksService : VpnService() {

    private var tunFd: ParcelFileDescriptor? = null
    private var tunnel: Tunnel? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectJob: Job? = null
    private var lastConnectParams: ConnectParams? = null

    private data class ConnectParams(
        val host: String, val port: Int, val method: String,
        val password: String, val killSwitch: Boolean
    )

    companion object {
        private const val CHANNEL_ID = "vpn_status"
        private const val NOTIFICATION_ID = 1
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private val RECONNECT_DELAYS_MS = longArrayOf(1000, 2000, 4000, 8000, 15000)

        /** Shared state flow — VpnManager observes this to update UI */
        val serviceState = MutableStateFlow(VpnState.IDLE)

        /** Error detail flow — provides specific error messages to UI */
        val serviceError = MutableStateFlow<String?>(null)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Null intent means system restarted the service after kill — clean up
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            VpnManager.ACTION_CONNECT -> {
                val config = VpnManager.pendingConfig.getAndSet(null)
                if (config == null) {
                    VizoLogger.w(Tag.SERVICE, "ACTION_CONNECT received but pendingConfig is null — ignoring (possible rapid-fire connect race)")
                    return START_NOT_STICKY
                }
                val killSwitch = intent.getBooleanExtra(VpnManager.EXTRA_KILL_SWITCH, true)
                startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
                connect(config.host, config.port, config.method, config.password, killSwitch)
            }
            VpnManager.ACTION_DISCONNECT -> {
                connectJob?.cancel()
                disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun connect(host: String, port: Int, method: String, password: String, killSwitch: Boolean) {
        // Cancel any in-flight connect coroutine, then tear down existing tunnel
        connectJob?.cancel()
        disconnect()

        lastConnectParams = ConnectParams(host, port, method, password, killSwitch)
        serviceError.value = null

        val redactedHost = if (host.length > 4) "${host.take(4)}***" else "***"
        VizoLogger.vpnState(Tag.SERVICE, "connect($redactedHost:$port, cipher=$method)")

        // Builder.establish() must run on the main thread (VpnService context-affine)
        val fd = establishTun(killSwitch)
        if (fd == null) {
            VizoLogger.e(Tag.SERVICE, "VPN permission not granted")
            serviceError.value = "VPN permission not granted"
            serviceState.value = VpnState.ERROR
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        tunFd = fd

        // Only the Shadowsocks handshake (network I/O) runs on the IO dispatcher
        connectJob = serviceScope.launch {
            try {
                connectTunnel(fd, host, port, method, password)
                VizoLogger.vpnState(Tag.SERVICE, "tunnel connected")
                withContext(Dispatchers.Main) {
                    updateNotification("Connected — Protected")
                }
                serviceState.value = VpnState.CONNECTED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VizoLogger.e(Tag.SERVICE, "connect failed", e)
                // Attempt auto-reconnect
                reconnectLoop(host, port, method, password, killSwitch)
            }
        }
    }

    private fun establishTun(killSwitch: Boolean): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("Vizoguard VPN")
            .addAddress("10.0.0.2", 32)
            .addAddress("fd00::2", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .setMtu(1500)

        if (killSwitch && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setBlocking(true)
        }

        return builder.establish()
    }

    private fun connectTunnel(
        fd: ParcelFileDescriptor, host: String, port: Int, method: String, password: String
    ) {
        val ssConfig = shadowsocks.Config().apply {
            setHost(host)
            setPort(port.toLong())
            setCipherName(method)
            setPassword(password)
        }
        val ssClient = shadowsocks.Shadowsocks.newClient(ssConfig)
        tunnel = Tun2socks.connectShadowsocksTunnel(fd.fd.toLong(), ssClient, true)
    }

    private suspend fun reconnectLoop(
        host: String, port: Int, method: String, password: String, killSwitch: Boolean
    ) {
        for (attempt in 1..MAX_RECONNECT_ATTEMPTS) {
            serviceState.value = VpnState.RECONNECTING
            withContext(Dispatchers.Main) {
                updateNotification("Reconnecting (attempt $attempt)...")
            }
            val delayMs = RECONNECT_DELAYS_MS[
                (attempt - 1).coerceAtMost(RECONNECT_DELAYS_MS.lastIndex)
            ]
            VizoLogger.vpnState(Tag.SERVICE, "reconnect attempt $attempt in ${delayMs}ms")
            delay(delayMs)

            // Tear down old tunnel, re-establish TUN
            withContext(NonCancellable) { disconnect() }
            val fd = withContext(Dispatchers.Main) { establishTun(killSwitch) }
            if (fd == null) {
                VizoLogger.e(Tag.SERVICE, "reconnect: VPN permission lost")
                serviceError.value = "VPN permission revoked"
                serviceState.value = VpnState.ERROR
                withContext(Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return
            }
            tunFd = fd

            try {
                connectTunnel(fd, host, port, method, password)
                VizoLogger.vpnState(Tag.SERVICE, "reconnect success on attempt $attempt")
                withContext(Dispatchers.Main) {
                    updateNotification("Connected — Protected")
                }
                serviceState.value = VpnState.CONNECTED
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VizoLogger.e(Tag.SERVICE, "reconnect attempt $attempt failed", e)
            }
        }

        // All retries exhausted
        VizoLogger.e(Tag.SERVICE, "reconnect failed after $MAX_RECONNECT_ATTEMPTS attempts")
        serviceError.value = "Connection failed after $MAX_RECONNECT_ATTEMPTS attempts"
        serviceState.value = VpnState.ERROR
        withContext(NonCancellable) { disconnect() }
    }

    /**
     * Tears down tunnel and TUN fd. Safe to call when nothing is connected
     * (no-ops silently without logging noise).
     */
    private fun disconnect() {
        val hadConnection = tunnel != null || tunFd != null
        try {
            tunnel?.disconnect()
            tunnel = null
            tunFd?.close()
            tunFd = null
        } catch (e: Exception) {
            VizoLogger.e(Tag.SERVICE, "disconnect error", e)
        }
        if (hadConnection) {
            VizoLogger.vpnState(Tag.SERVICE, "disconnect")
        }
    }

    override fun onRevoke() {
        VizoLogger.vpnState(Tag.SERVICE, "VPN revoked by system")
        connectJob?.cancel()
        disconnect()
        serviceState.value = VpnState.IDLE
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        connectJob?.cancel()
        disconnect()
        serviceState.value = VpnState.IDLE
        serviceScope.cancel()
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
