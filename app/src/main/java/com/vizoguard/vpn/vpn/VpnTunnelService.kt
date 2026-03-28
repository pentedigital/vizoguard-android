package com.vizoguard.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import com.vizoguard.vpn.R
import com.vizoguard.vpn.MainActivity
import com.vizoguard.vpn.license.SecureStore
import com.vizoguard.vpn.util.Tag
import com.vizoguard.vpn.util.VizoLogger
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class VpnTunnelService : VpnService() {

    private var boxService: BoxService? = null
    private var platformImpl: PlatformInterfaceImpl? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectJob: Job? = null
    private var lastConfigJson: String? = null

    companion object {
        private const val CHANNEL_ID = "vpn_status"
        private const val NOTIFICATION_ID = 1
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private val RECONNECT_DELAYS_MS = longArrayOf(1000, 2000, 4000, 8000, 15000)

        val serviceState = MutableStateFlow(VpnState.IDLE)
        val serviceError = MutableStateFlow<String?>(null)

        private var libboxSetupDone = false
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupLibbox()
    }

    private fun setupLibbox() {
        if (libboxSetupDone) return
        val options = SetupOptions().apply {
            basePath = filesDir.absolutePath
            workingPath = "${filesDir.absolutePath}/sing-box"
            tempPath = cacheDir.absolutePath
            fixAndroidStack = true
        }
        java.io.File(options.workingPath).mkdirs()
        Libbox.setup(options)
        libboxSetupDone = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            VizoLogger.systemEvent("Service restarted by system with null intent — stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            VpnManager.ACTION_CONNECT -> {
                val currentState = serviceState.value
                if (currentState == VpnState.CONNECTED || currentState == VpnState.CONNECTING || currentState == VpnState.RECONNECTING) {
                    VizoLogger.d(Tag.SERVICE, "Duplicate ACTION_CONNECT ignored — already $currentState")
                    return START_STICKY
                }
                var configJson = VpnManager.pendingConfig.getAndSet(null)
                if (configJson == null) {
                    VizoLogger.w(Tag.SERVICE, "pendingConfig null — attempting recovery from store")
                    configJson = recoverConfigFromStore()
                    if (configJson == null) {
                        VizoLogger.w(Tag.SERVICE, "No cached config — cannot recover")
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }
                startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
                connect(configJson)
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

    private fun recoverConfigFromStore(): String? {
        val store = SecureStore.create(applicationContext)
        val vpnUrl = store.getVpnAccessUrl() ?: return null
        val ssConfig = VpnManager.parseShadowsocksUrl(vpnUrl) ?: return null
        VizoLogger.d(Tag.SERVICE, "Recovered SS config from SecureStore")
        return ConfigBuilder.buildShadowsocks(ssConfig)
    }

    private fun connect(configJson: String) {
        lastConfigJson = configJson
        serviceError.value = null
        serviceState.value = VpnState.CONNECTING

        val oldJob = connectJob
        connectJob = serviceScope.launch(Dispatchers.IO) {
            oldJob?.cancelAndJoin()
            disconnect()

            try {
                val platform = PlatformInterfaceImpl(this@VpnTunnelService)
                platformImpl = platform
                val service = Libbox.newService(configJson, platform)
                boxService = service
                service.start()

                VizoLogger.vpnState(Tag.SERVICE, "tunnel connected via libbox")
                withContext(Dispatchers.Main) {
                    updateNotification("Connected — Protected")
                }
                serviceState.value = VpnState.CONNECTED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VizoLogger.e(Tag.SERVICE, "connect failed", e)
                reconnectLoop(configJson)
            }
        }
    }

    private suspend fun reconnectLoop(configJson: String) {
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

            withContext(NonCancellable) { disconnect() }

            try {
                val platform = PlatformInterfaceImpl(this@VpnTunnelService)
                platformImpl = platform
                val service = Libbox.newService(configJson, platform)
                boxService = service
                service.start()

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

        VizoLogger.e(Tag.SERVICE, "reconnect failed after $MAX_RECONNECT_ATTEMPTS attempts")
        serviceError.value = "Connection failed after $MAX_RECONNECT_ATTEMPTS attempts"
        serviceState.value = VpnState.ERROR
        withContext(NonCancellable) { disconnect() }
    }

    private fun disconnect() {
        val hadConnection = boxService != null
        try {
            boxService?.close()
            boxService = null
            platformImpl?.closeTun()
            platformImpl = null
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
        serviceError.value = "VPN permission revoked"
        serviceState.value = VpnState.ERROR
        disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        connectJob?.cancel()
        disconnect()
        if (serviceState.value != VpnState.ERROR) {
            serviceState.value = VpnState.IDLE
            serviceError.value = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "VPN Status", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows VPN connection status" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val disconnectIntent = PendingIntent.getService(
            this, 1,
            Intent(this, VpnTunnelService::class.java).apply { action = VpnManager.ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Vizoguard VPN")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
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
