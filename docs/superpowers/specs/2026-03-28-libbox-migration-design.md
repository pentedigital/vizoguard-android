# Android libbox Migration + Transport Abstraction

## Problem

The Android app uses `tun2socks.aar` for Shadowsocks-only VPN. Users in censored markets (UAE, Iran, Turkey, Russia) have no fallback when Shadowsocks is blocked by DPI. The desktop app has dual transport (direct SS + obfuscated VLESS/WS/TLS via sing-box) with auto-detection — Android needs parity.

## Solution

Replace tun2socks with libbox (sing-box v1.11.4) as the sole VPN transport. libbox handles both Shadowsocks and VLESS natively. The VPN service becomes config-driven and protocol-agnostic — it receives a JSON config string and passes it to libbox's `BoxService`.

## Key Decisions

- **Minimal PlatformInterface first** — implement only the 5 methods libbox needs for SS/VLESS (OpenTun, AutoDetectInterfaceControl, WriteLog, UsePlatformAutoDetectInterfaceControl, UseProcFS), stub the rest. Fill in as needed.
- **User-facing modes map to transport internally** — PRIVACY→auto, STREAMING→direct, WORK→auto. Users never see protocol names. ModeSelector UI unchanged.
- **Single renamed service** — `ShadowsocksService` → `VpnTunnelService`. Config-driven, protocol-agnostic. No parallel services, no tun2socks fallback.

## Architecture

```
UI Layer:        ModeSelector (PRIVACY/STREAMING/WORK) → unchanged
                        ↓
Logic Layer:     VpnManager + ConnectionManager (new)
                 - Maps modes → transport configs
                 - Auto-detection: TCP probe → fallback
                 - Per-network caching (gateway hash, 7-day TTL)
                 - Generates sing-box JSON config (SS or VLESS)
                        ↓
Service Layer:   VpnTunnelService (renamed from ShadowsocksService)
                 - Receives JSON config string
                 - Implements libbox PlatformInterface (minimal)
                 - Passes config to BoxService, manages lifecycle
```

## Files Changed/Created

| Action | File | Responsibility |
|--------|------|----------------|
| Delete | `libs/tun2socks.aar` | Replaced by libbox |
| Rename+Rewrite | `vpn/ShadowsocksService.kt` → `vpn/VpnTunnelService.kt` | libbox BoxService lifecycle + PlatformInterface |
| Create | `vpn/PlatformInterfaceImpl.kt` | Minimal PlatformInterface (5 real + 10 stubs) |
| Create | `vpn/ConfigBuilder.kt` | Generates sing-box JSON configs for SS and VLESS |
| Create | `vpn/ConnectionManager.kt` | Auto-detection, probe, fallback, per-network caching |
| Modify | `vpn/VpnManager.kt` | JSON config handoff via ConnectionManager |
| Modify | `vpn/VpnState.kt` | Replace `ShadowsocksConfig` with `VpnConfig`, add `TransportMode` |
| Modify | `api/ApiClient.kt` | Add `/api/vpn/vless` endpoint |
| Modify | `license/SecureStore.kt` | Store VLESS UUID + transport cache |
| Modify | `AppState.kt` | Wire ConnectionManager, provision VLESS UUID |
| Modify | `AndroidManifest.xml` | Rename service class |
| Modify | `receiver/BootReceiver.kt` | Update service class reference |
| Modify | `app/build.gradle.kts` | Remove tun2socks, enable libbox |
| Modify | `app/proguard-rules.pro` | Remove tun2socks keep rules |

## ConfigBuilder — JSON Generation

Two config templates, both valid sing-box JSON.

### Shadowsocks (direct)

```json
{
  "log": {"level": "warn"},
  "inbounds": [{
    "type": "tun",
    "auto_route": true,
    "strict_route": true,
    "inet4_address": "172.19.0.1/30",
    "sniff": true,
    "sniff_override_destination": false
  }],
  "outbounds": [
    {"type": "shadowsocks", "tag": "proxy", "server": "HOST", "server_port": PORT, "method": "METHOD", "password": "PASS"},
    {"type": "direct", "tag": "direct"},
    {"type": "block", "tag": "block"}
  ],
  "route": {
    "auto_detect_interface": true,
    "final": "proxy",
    "rules": [
      {"ip_cidr": ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8"], "outbound": "direct"}
    ]
  }
}
```

### VLESS (obfuscated)

```json
{
  "log": {"level": "warn"},
  "inbounds": [{
    "type": "tun",
    "auto_route": true,
    "strict_route": true,
    "inet4_address": "172.19.0.1/30",
    "sniff": true,
    "sniff_override_destination": false
  }],
  "outbounds": [
    {"type": "vless", "tag": "proxy", "server": "vizoguard.com", "server_port": 443, "uuid": "UUID",
     "tls": {"enabled": true, "server_name": "vizoguard.com"},
     "transport": {"type": "ws", "path": "/ws"}},
    {"type": "direct", "tag": "direct"},
    {"type": "block", "tag": "block"}
  ],
  "dns": {
    "servers": [
      {"address": "https://1.1.1.1/dns-query", "tag": "dns-remote", "strategy": "ipv4_only"},
      {"address": "https://9.9.9.9/dns-query", "tag": "dns-fallback", "strategy": "ipv4_only"}
    ]
  },
  "route": {
    "auto_detect_interface": true,
    "final": "proxy",
    "rules": [
      {"ip_cidr": ["RESOLVED_SERVER_IP/32"], "outbound": "direct"},
      {"ip_cidr": ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8", "169.254.0.0/16"], "outbound": "direct"}
    ]
  }
}
```

`ConfigBuilder.buildShadowsocks(host, port, method, password)` and `ConfigBuilder.buildVless(uuid, serverIp)` return JSON strings. Both include private network bypass rules.

## ConnectionManager — Transport Selection

Ported from desktop `connection-manager.js`. Lives in `vpn/ConnectionManager.kt`.

```kotlin
class ConnectionManager(private val store: SecureStore) {

    enum class TransportMode { AUTO, DIRECT, OBFUSCATED }

    fun modeForConnectionMode(mode: ConnectionMode): TransportMode = when (mode) {
        ConnectionMode.PRIVACY -> TransportMode.AUTO
        ConnectionMode.STREAMING -> TransportMode.DIRECT
        ConnectionMode.WORK -> TransportMode.AUTO
    }

    suspend fun selectTransport(ssUrl: String, vlessUuid: String?, mode: ConnectionMode): String {
        val transportMode = modeForConnectionMode(mode)
        val ssConfig = VpnManager.parseShadowsocksUrl(ssUrl)
            ?: throw IllegalArgumentException("Invalid SS URL")

        return when (transportMode) {
            TransportMode.DIRECT -> ConfigBuilder.buildShadowsocks(ssConfig)
            TransportMode.OBFUSCATED -> {
                requireNotNull(vlessUuid) { "VLESS UUID required for obfuscated mode" }
                val serverIp = resolveServerIp("vizoguard.com")
                ConfigBuilder.buildVless(vlessUuid, serverIp)
            }
            TransportMode.AUTO -> autoSelect(ssConfig, vlessUuid)
        }
    }

    private suspend fun autoSelect(ssConfig: ShadowsocksConfig, vlessUuid: String?): String {
        // Check network cache (7-day TTL, keyed by gateway IP hash)
        val cacheKey = getNetworkCacheKey()
        val cached = cacheKey?.let { store.getTransportCache(it) }
        if (cached != null) {
            return when (cached) {
                "obfuscated" -> if (vlessUuid != null) {
                    ConfigBuilder.buildVless(vlessUuid, resolveServerIp("vizoguard.com"))
                } else ConfigBuilder.buildShadowsocks(ssConfig)
                else -> ConfigBuilder.buildShadowsocks(ssConfig)
            }
        }

        // TCP probe to SS server (5s timeout)
        val directOk = probeDirectConnection(ssConfig.host, ssConfig.port, 5000)
        if (directOk) {
            cacheKey?.let { store.saveTransportCache(it, "direct") }
            return ConfigBuilder.buildShadowsocks(ssConfig)
        }

        // Fallback to obfuscated
        if (vlessUuid != null) {
            cacheKey?.let { store.saveTransportCache(it, "obfuscated") }
            return ConfigBuilder.buildVless(vlessUuid, resolveServerIp("vizoguard.com"))
        }

        // No VLESS available — use SS anyway (best effort)
        return ConfigBuilder.buildShadowsocks(ssConfig)
    }

    private suspend fun probeDirectConnection(host: String, port: Int, timeoutMs: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
                    true
                }
            } catch (_: Exception) { false }
        }
    }

    private fun getNetworkCacheKey(): String? {
        // Hash the default gateway IP for per-network caching
        // Android: use ConnectivityManager to get gateway
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            // Simplified: use WiFi SSID or mobile network as cache key
            // Full implementation uses gateway IP like desktop
            null // TODO: implement gateway detection
        } catch (_: Exception) { null }
    }

    private suspend fun resolveServerIp(hostname: String): String {
        return withContext(Dispatchers.IO) {
            java.net.InetAddress.getByName(hostname).hostAddress ?: hostname
        }
    }
}
```

## VpnTunnelService — Config-Driven Service

Renamed from `ShadowsocksService`. Receives JSON config string, passes to libbox `BoxService`.

```kotlin
class VpnTunnelService : VpnService() {
    private var boxService: io.nekohasekai.libbox.BoxService? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            VpnManager.ACTION_CONNECT -> {
                val configJson = VpnManager.pendingConfig.getAndSet(null)
                    ?: recoverConfigFromStore()
                    ?: run { stopSelf(); return START_NOT_STICKY }
                startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
                connect(configJson)
            }
            VpnManager.ACTION_DISCONNECT -> {
                disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun connect(configJson: String) {
        connectJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val platform = PlatformInterfaceImpl(this@VpnTunnelService)
                boxService = io.nekohasekai.libbox.BoxService(configJson, platform)
                boxService!!.start()
                serviceState.value = VpnState.CONNECTED
            } catch (e: Exception) {
                reconnectLoop(configJson)
            }
        }
    }

    private fun disconnect() {
        try {
            boxService?.close()
            boxService = null
        } catch (e: Exception) {
            VizoLogger.e(Tag.SERVICE, "disconnect error", e)
        }
    }
}
```

## PlatformInterfaceImpl — Minimal Implementation

```kotlin
class PlatformInterfaceImpl(
    private val service: VpnService
) : io.nekohasekai.libbox.PlatformInterface {

    // === Real implementations ===

    override fun openTun(options: io.nekohasekai.libbox.TunOptions): Int {
        val builder = service.Builder()
            .setSession("Vizoguard VPN")
            .addAddress(options.inet4Address, options.inet4Prefix)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .setMtu(options.mtu)
        val fd = builder.establish()
            ?: throw Exception("VPN permission not granted")
        return fd.fd
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        service.protect(fd)  // VpnService.protect() — excludes fd from VPN tunnel
    }

    override fun writeLog(message: String) {
        VizoLogger.d(Tag.SERVICE, "[libbox] $message")
    }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true
    override fun useProcFS(): Boolean = false

    // === Stubs (safe defaults) ===

    override fun findConnectionOwner(ipProtocol: Int, srcAddr: String, srcPort: Int, dstAddr: String, dstPort: Int): Int = -1
    override fun packageNameByUid(uid: Int): String = ""
    override fun uidByPackageName(packageName: String): Int = -1
    override fun startDefaultInterfaceMonitor(listener: io.nekohasekai.libbox.InterfaceUpdateListener) {}
    override fun closeDefaultInterfaceMonitor(listener: io.nekohasekai.libbox.InterfaceUpdateListener) {}
    override fun getInterfaces(): io.nekohasekai.libbox.NetworkInterfaceIterator = EmptyNetworkInterfaceIterator()
    override fun underNetworkExtension(): Boolean = false
    override fun includeAllNetworks(): Boolean = false
    override fun readWIFIState(): io.nekohasekai.libbox.WIFIState? = null
    override fun clearDNSCache() {}
    override fun sendNotification(notification: io.nekohasekai.libbox.Notification) {}
}
```

## VpnManager Changes

- `pendingConfig` type changes from `AtomicReference<ShadowsocksConfig?>` to `AtomicReference<String?>` (JSON config string)
- `startVpn(accessUrl: String)` calls `connectionManager.selectTransport(accessUrl, vlessUuid, currentMode)` to get the JSON config
- `parseShadowsocksUrl()` stays as a companion util — used by `ConfigBuilder` and `ConnectionManager`

## VLESS UUID Provisioning

Add to `ApiClient.kt`:

```kotlin
@Serializable
data class VlessResponse(val uuid: String)

suspend fun provisionVlessUuid(key: String, deviceId: String): String {
    val response: VlessResponse = post("/vpn/vless", mapOf("key" to key, "device_id" to deviceId))
    return response.uuid
}
```

Called in `AppState.connect()` after license validation, before `connectionManager.selectTransport()`. If provisioning fails (network error, server error), VLESS is unavailable — ConnectionManager falls back to SS-only. UUID cached in SecureStore.

## AppState.connect() Flow

```
1. Validate license (existing)
2. Get cached VPN access URL (existing)
3. Provision VLESS UUID if not cached (new — best-effort, non-blocking)
4. ConnectionManager.selectTransport(ssUrl, vlessUuid, mode) → JSON config
5. VpnManager.startVpn(configJson) → sends to VpnTunnelService
```

## What Doesn't Change

- VpnState enum values
- ModeSelector UI (PRIVACY/STREAMING/WORK pills)
- License validation flow
- 24-hour LicenseCheckWorker
- Deep link activation
- Notification channel and foreground service pattern
- Reconnect loop logic (5 attempts, exponential backoff)

## Testing

- Unit tests for `ConfigBuilder` — verify SS and VLESS JSON output
- Unit tests for `ConnectionManager.modeForConnectionMode()` mapping
- Unit tests for `VpnManager.parseShadowsocksUrl()` (existing, unchanged)
- Integration: build APK, install on emulator, verify VPN connects with SS config
- Manual: test on real device in non-censored network (direct mode)
- Manual: verify VLESS config generation matches desktop `obfuscated.js` output structure
