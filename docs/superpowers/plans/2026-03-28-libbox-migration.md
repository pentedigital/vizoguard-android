# libbox Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace tun2socks with libbox (sing-box) as the sole VPN transport, enabling both Shadowsocks (direct) and VLESS/WS/TLS (obfuscated) modes with auto-detection.

**Architecture:** Config-driven VPN service receives JSON from ConnectionManager. ConfigBuilder generates sing-box configs for SS or VLESS. PlatformInterfaceImpl provides minimal Android VPN integration to libbox. ModeSelector modes (PRIVACY/STREAMING/WORK) map to transport logic internally.

**Tech Stack:** Kotlin, libbox.aar (sing-box v1.11.4), Jetpack Compose, Ktor, kotlinx.serialization

**Spec:** `docs/superpowers/specs/2026-03-28-libbox-migration-design.md`

**Java API surface (from libbox.aar inspection):**
- Factory: `Libbox.newService(configJson: String, platform: PlatformInterface): BoxService`
- Setup: `Libbox.setup(options: SetupOptions)` — must call before newService
- `BoxService.start()`, `BoxService.close()`, `BoxService.pause()`, `BoxService.wake()`
- `PlatformInterface.openTun(options: TunOptions): Int` — returns fd
- `TunOptions.getInet4Address(): RoutePrefixIterator`, `TunOptions.getMTU(): Int`, etc.
- `PlatformInterface.autoDetectInterfaceControl(fd: Int)` — call `VpnService.protect(fd)`

**All source files under:** `app/src/main/java/com/vizoguard/vpn/`
**All test files under:** `app/src/test/java/com/vizoguard/vpn/`

---

### Task 1: Switch build from tun2socks to libbox

**Files:**
- Modify: `app/build.gradle.kts:68-69`
- Modify: `app/proguard-rules.pro:32-34`

- [ ] **Step 1: Replace tun2socks dependency with libbox**

In `app/build.gradle.kts`, replace:
```kotlin
    // VPN tunnel (Outline tun2socks) — will be replaced by libbox
    implementation(files("../libs/tun2socks.aar"))

    // Obfuscated VPN transport (sing-box libbox) — ready, pending ShadowsocksService migration
    // implementation(files("../libs/libbox.aar"))  // TODO: uncomment after tun2socks→libbox migration
```

With:
```kotlin
    // VPN transport — sing-box libbox (Shadowsocks direct + VLESS obfuscated)
    implementation(files("../libs/libbox.aar"))
```

- [ ] **Step 2: Update ProGuard rules**

In `app/proguard-rules.pro`, replace:
```
# tun2socks + shadowsocks native bridge (JNI)
-keep class tun2socks.** { *; }
-keep class shadowsocks.** { *; }
```

With:
```
# Go mobile runtime (libbox JNI bridge)
-keep class go.** { *; }
```

(libbox rules already added: `-keep class io.nekohasekai.libbox.** { *; }`)

- [ ] **Step 3: Verify build compiles (expect failures in ShadowsocksService only)**

```bash
cd /root/vizoguard-android && export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" && ./gradlew assembleDebug 2>&1 | grep "Unresolved reference" | head -10
```

Expected: errors only in `ShadowsocksService.kt` (tun2socks/shadowsocks imports). All other files should compile.

- [ ] **Step 4: Commit**

```bash
cd /root/vizoguard-android && git add app/build.gradle.kts app/proguard-rules.pro && git commit -m "chore: switch dependency from tun2socks to libbox

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Create VpnState types (TransportMode + VpnConfig)

**Files:**
- Modify: `vpn/VpnState.kt`
- Test: `vpn/VpnStateTest.kt` (existing tests reference ShadowsocksConfig)

- [ ] **Step 1: Update VpnState.kt**

Replace the existing `ShadowsocksConfig` data class and add new types. Keep `ShadowsocksConfig` but add `TransportMode`:

In `vpn/VpnState.kt`, replace entire file with:

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
cd /root/vizoguard-android && git add app/src/main/java/com/vizoguard/vpn/vpn/VpnState.kt && git commit -m "feat: add TransportMode enum and transportMode to VpnStatus

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Create ConfigBuilder

**Files:**
- Create: `vpn/ConfigBuilder.kt`
- Create test: `vpn/ConfigBuilderTest.kt`

- [ ] **Step 1: Write ConfigBuilder tests**

Create `app/src/test/java/com/vizoguard/vpn/vpn/ConfigBuilderTest.kt`:

```kotlin
package com.vizoguard.vpn.vpn

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConfigBuilderTest {

    @Test
    fun buildShadowsocks_containsCorrectOutbound() {
        val json = ConfigBuilder.buildShadowsocks("1.2.3.4", 8388, "chacha20-ietf-poly1305", "secret")
        val config = JSONObject(json)
        val outbound = config.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("shadowsocks", outbound.getString("type"))
        assertEquals("1.2.3.4", outbound.getString("server"))
        assertEquals(8388, outbound.getInt("server_port"))
        assertEquals("chacha20-ietf-poly1305", outbound.getString("method"))
        assertEquals("secret", outbound.getString("password"))
    }

    @Test
    fun buildShadowsocks_hasTunInbound() {
        val json = ConfigBuilder.buildShadowsocks("1.2.3.4", 8388, "aes-256-gcm", "pass")
        val config = JSONObject(json)
        val inbound = config.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("tun", inbound.getString("type"))
        assertTrue(inbound.getBoolean("auto_route"))
    }

    @Test
    fun buildShadowsocks_hasPrivateNetworkBypass() {
        val json = ConfigBuilder.buildShadowsocks("1.2.3.4", 8388, "aes-256-gcm", "pass")
        val config = JSONObject(json)
        val rules = config.getJSONObject("route").getJSONArray("rules")
        assertTrue(rules.length() > 0)
        val cidrs = rules.getJSONObject(0).getJSONArray("ip_cidr")
        val cidrList = (0 until cidrs.length()).map { cidrs.getString(it) }
        assertTrue(cidrList.contains("10.0.0.0/8"))
        assertTrue(cidrList.contains("127.0.0.0/8"))
    }

    @Test
    fun buildVless_containsCorrectOutbound() {
        val json = ConfigBuilder.buildVless("test-uuid-1234", "93.184.216.34")
        val config = JSONObject(json)
        val outbound = config.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("vless", outbound.getString("type"))
        assertEquals("vizoguard.com", outbound.getString("server"))
        assertEquals(443, outbound.getInt("server_port"))
        assertEquals("test-uuid-1234", outbound.getString("uuid"))
    }

    @Test
    fun buildVless_hasTlsAndWsTransport() {
        val json = ConfigBuilder.buildVless("uuid", "1.2.3.4")
        val config = JSONObject(json)
        val outbound = config.getJSONArray("outbounds").getJSONObject(0)
        assertTrue(outbound.getJSONObject("tls").getBoolean("enabled"))
        assertEquals("ws", outbound.getJSONObject("transport").getString("type"))
        assertEquals("/ws", outbound.getJSONObject("transport").getString("path"))
    }

    @Test
    fun buildVless_hasServerIpBypass() {
        val json = ConfigBuilder.buildVless("uuid", "93.184.216.34")
        val config = JSONObject(json)
        val rules = config.getJSONObject("route").getJSONArray("rules")
        val firstRule = rules.getJSONObject(0)
        val cidrs = firstRule.getJSONArray("ip_cidr")
        assertEquals("93.184.216.34/32", cidrs.getString(0))
    }

    @Test
    fun buildVless_hasDnsServers() {
        val json = ConfigBuilder.buildVless("uuid", "1.2.3.4")
        val config = JSONObject(json)
        val servers = config.getJSONObject("dns").getJSONArray("servers")
        assertTrue(servers.length() >= 1)
    }

    @Test
    fun buildFromShadowsocksConfig_delegatesCorrectly() {
        val ssConfig = ShadowsocksConfig("host", 1234, "aes-256-gcm", "pw")
        val json = ConfigBuilder.buildShadowsocks(ssConfig)
        val config = JSONObject(json)
        val outbound = config.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("host", outbound.getString("server"))
        assertEquals(1234, outbound.getInt("server_port"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /root/vizoguard-android && export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" && ./gradlew testDebugUnitTest --tests "com.vizoguard.vpn.vpn.ConfigBuilderTest" 2>&1 | tail -5
```

Expected: compilation error — `ConfigBuilder` doesn't exist yet.

- [ ] **Step 3: Implement ConfigBuilder**

Create `app/src/main/java/com/vizoguard/vpn/vpn/ConfigBuilder.kt`:

```kotlin
package com.vizoguard.vpn.vpn

import org.json.JSONArray
import org.json.JSONObject

object ConfigBuilder {

    private const val VLESS_SERVER = "vizoguard.com"
    private const val VLESS_PORT = 443
    private const val WS_PATH = "/ws"
    private const val TUN_ADDRESS = "172.19.0.1/30"

    private val PRIVATE_CIDRS = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16",
        "127.0.0.0/8", "169.254.0.0/16"
    )

    fun buildShadowsocks(host: String, port: Int, method: String, password: String): String {
        return JSONObject().apply {
            put("log", JSONObject().put("level", "warn"))
            put("inbounds", JSONArray().put(buildTunInbound()))
            put("outbounds", JSONArray()
                .put(JSONObject().apply {
                    put("type", "shadowsocks")
                    put("tag", "proxy")
                    put("server", host)
                    put("server_port", port)
                    put("method", method)
                    put("password", password)
                })
                .put(JSONObject().put("type", "direct").put("tag", "direct"))
                .put(JSONObject().put("type", "block").put("tag", "block"))
            )
            put("route", JSONObject().apply {
                put("auto_detect_interface", true)
                put("final", "proxy")
                put("rules", JSONArray().put(buildPrivateNetworkRule()))
            })
        }.toString()
    }

    fun buildShadowsocks(config: ShadowsocksConfig): String {
        return buildShadowsocks(config.host, config.port, config.method, config.password)
    }

    fun buildVless(uuid: String, serverIp: String): String {
        return JSONObject().apply {
            put("log", JSONObject().put("level", "warn"))
            put("inbounds", JSONArray().put(buildTunInbound()))
            put("outbounds", JSONArray()
                .put(JSONObject().apply {
                    put("type", "vless")
                    put("tag", "proxy")
                    put("server", VLESS_SERVER)
                    put("server_port", VLESS_PORT)
                    put("uuid", uuid)
                    put("tls", JSONObject().apply {
                        put("enabled", true)
                        put("server_name", VLESS_SERVER)
                    })
                    put("transport", JSONObject().apply {
                        put("type", "ws")
                        put("path", WS_PATH)
                    })
                })
                .put(JSONObject().put("type", "direct").put("tag", "direct"))
                .put(JSONObject().put("type", "block").put("tag", "block"))
            )
            put("dns", JSONObject().put("servers", JSONArray()
                .put(JSONObject().apply {
                    put("address", "https://1.1.1.1/dns-query")
                    put("tag", "dns-remote")
                    put("strategy", "ipv4_only")
                })
                .put(JSONObject().apply {
                    put("address", "https://9.9.9.9/dns-query")
                    put("tag", "dns-fallback")
                    put("strategy", "ipv4_only")
                })
            ))
            put("route", JSONObject().apply {
                put("auto_detect_interface", true)
                put("final", "proxy")
                put("rules", JSONArray()
                    .put(JSONObject().apply {
                        put("ip_cidr", JSONArray().put("$serverIp/32"))
                        put("outbound", "direct")
                    })
                    .put(buildPrivateNetworkRule())
                )
            })
        }.toString()
    }

    private fun buildTunInbound(): JSONObject {
        return JSONObject().apply {
            put("type", "tun")
            put("auto_route", true)
            put("strict_route", true)
            put("inet4_address", TUN_ADDRESS)
            put("sniff", true)
            put("sniff_override_destination", false)
        }
    }

    private fun buildPrivateNetworkRule(): JSONObject {
        return JSONObject().apply {
            put("ip_cidr", JSONArray().apply { PRIVATE_CIDRS.forEach { put(it) } })
            put("outbound", "direct")
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /root/vizoguard-android && export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" && ./gradlew testDebugUnitTest --tests "com.vizoguard.vpn.vpn.ConfigBuilderTest" 2>&1 | tail -5
```

Expected: all 8 tests pass.

- [ ] **Step 5: Commit**

```bash
cd /root/vizoguard-android && git add app/src/main/java/com/vizoguard/vpn/vpn/ConfigBuilder.kt app/src/test/java/com/vizoguard/vpn/vpn/ConfigBuilderTest.kt && git commit -m "feat: add ConfigBuilder for sing-box JSON config generation

Generates valid sing-box configs for Shadowsocks (direct) and
VLESS/WS/TLS (obfuscated) transports. 8 unit tests.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Create PlatformInterfaceImpl

**Files:**
- Create: `vpn/PlatformInterfaceImpl.kt`

- [ ] **Step 1: Create PlatformInterfaceImpl**

Create `app/src/main/java/com/vizoguard/vpn/vpn/PlatformInterfaceImpl.kt`:

```kotlin
package com.vizoguard.vpn.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.vizoguard.vpn.util.Tag
import com.vizoguard.vpn.util.VizoLogger
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.NetworkInterface
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState

class PlatformInterfaceImpl(
    private val service: VpnService
) : PlatformInterface {

    private var tunFd: ParcelFileDescriptor? = null

    // === Real implementations ===

    override fun openTun(options: TunOptions): Int {
        val builder = (service as VpnTunnelService).Builder()
            .setSession("Vizoguard VPN")
            .setMtu(options.mtu)

        // Add addresses from TUN options
        val inet4Iter = options.inet4Address
        while (inet4Iter.hasNext()) {
            val prefix = inet4Iter.next()
            builder.addAddress(prefix.address(), prefix.prefix())
        }

        // Add all-traffic routes
        builder.addRoute("0.0.0.0", 0)
        builder.addRoute("::", 0)

        // DNS servers
        builder.addDnsServer("1.1.1.1")
        builder.addDnsServer("8.8.8.8")

        val fd = builder.establish()
            ?: throw Exception("VPN permission not granted")
        tunFd = fd
        return fd.fd
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        service.protect(fd)
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

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}
    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}

    override fun getInterfaces(): NetworkInterfaceIterator = object : NetworkInterfaceIterator {
        override fun hasNext(): Boolean = false
        override fun next(): NetworkInterface? = null
    }

    override fun underNetworkExtension(): Boolean = false
    override fun includeAllNetworks(): Boolean = false
    override fun readWIFIState(): WIFIState? = null
    override fun clearDNSCache() {}
    override fun sendNotification(notification: Notification) {}

    /** Close the TUN fd — called during service teardown */
    fun closeTun() {
        tunFd?.close()
        tunFd = null
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd /root/vizoguard-android && git add app/src/main/java/com/vizoguard/vpn/vpn/PlatformInterfaceImpl.kt && git commit -m "feat: add minimal PlatformInterface implementation for libbox

5 real methods (openTun, autoDetectInterfaceControl, writeLog,
usePlatformAutoDetectInterfaceControl, useProcFS) + 10 stubs.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Create VpnTunnelService (replace ShadowsocksService)

**Files:**
- Delete/Replace: `vpn/ShadowsocksService.kt` → `vpn/VpnTunnelService.kt`
- Modify: `AndroidManifest.xml:41`

- [ ] **Step 1: Delete ShadowsocksService and create VpnTunnelService**

Delete `app/src/main/java/com/vizoguard/vpn/vpn/ShadowsocksService.kt`.

Create `app/src/main/java/com/vizoguard/vpn/vpn/VpnTunnelService.kt`:

```kotlin
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
```

- [ ] **Step 2: Update AndroidManifest.xml**

Replace `android:name=".vpn.ShadowsocksService"` with `android:name=".vpn.VpnTunnelService"` in the service declaration.

- [ ] **Step 3: Commit**

```bash
cd /root/vizoguard-android && git add -A app/src/main/java/com/vizoguard/vpn/vpn/ShadowsocksService.kt app/src/main/java/com/vizoguard/vpn/vpn/VpnTunnelService.kt app/src/main/AndroidManifest.xml && git commit -m "feat: replace ShadowsocksService with VpnTunnelService (libbox)

Config-driven VPN service. Receives JSON config string, passes to
libbox BoxService via Libbox.newService(). Protocol-agnostic.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Update VpnManager for JSON config handoff

**Files:**
- Modify: `vpn/VpnManager.kt`

- [ ] **Step 1: Update VpnManager**

Key changes:
- `pendingConfig` type: `AtomicReference<ShadowsocksConfig?>` → `AtomicReference<String?>` (JSON string)
- `startVpn(configJson: String)` sets JSON directly
- Keep `parseShadowsocksUrl()` as companion util
- Update service class reference from `ShadowsocksService` to `VpnTunnelService`
- Update `serviceState` import to use `VpnTunnelService.serviceState`

Replace `vpn/VpnManager.kt` with updated version that:
1. Changes `pendingConfig` to `AtomicReference<String?>(null)`
2. Changes `startVpn` to accept `configJson: String` parameter
3. Changes service intent targets from `ShadowsocksService` to `VpnTunnelService`
4. Collects from `VpnTunnelService.serviceState` instead of `ShadowsocksService.serviceState`

- [ ] **Step 2: Commit**

```bash
cd /root/vizoguard-android && git add app/src/main/java/com/vizoguard/vpn/vpn/VpnManager.kt && git commit -m "feat: update VpnManager for JSON config handoff to VpnTunnelService

pendingConfig now holds JSON string instead of ShadowsocksConfig.
Service references updated to VpnTunnelService.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Add VLESS API endpoint + SecureStore fields

**Files:**
- Modify: `api/ApiClient.kt`
- Modify: `license/SecureStore.kt`

- [ ] **Step 1: Add VlessResponse and provisionVlessUuid to ApiClient**

In `api/ApiClient.kt`, add after `LicenseRequest` (line 21):

```kotlin
@Serializable data class VlessResponse(val uuid: String)
@Serializable data class VlessRequest(val key: String, @SerialName("device_id") val deviceId: String)
```

Add method after `checkVpnStatus()` (after line 88):

```kotlin
    suspend fun provisionVlessUuid(key: String, deviceId: String): Result<VlessResponse> {
        return executeWithRetry("/vpn/vless") {
            val response = client.post("$baseUrl/vpn/vless") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(VlessRequest(key, deviceId)))
            }
            if (response.status.isSuccess()) {
                Result.success(json.decodeFromString<VlessResponse>(response.bodyAsText()))
            } else {
                val err = parseErrorResponse(response.bodyAsText())
                Result.failure(ApiException(response.status.value, err.error, err.status))
            }
        }
    }
```

- [ ] **Step 2: Add VLESS UUID + transport cache to SecureStore**

In `license/SecureStore.kt`, add after `getNotifications()` (line 55):

```kotlin
    fun saveVlessUuid(uuid: String) = prefs.edit().putString(KEY_VLESS_UUID, uuid).apply()
    fun getVlessUuid(): String? = prefs.getString(KEY_VLESS_UUID, null)
    fun clearVlessUuid() = prefs.edit().remove(KEY_VLESS_UUID).apply()

    fun saveTransportCache(networkKey: String, mode: String) {
        prefs.edit().putString("transport_cache_$networkKey", mode)
            .putLong("transport_cache_ts_$networkKey", System.currentTimeMillis())
            .apply()
    }
    fun getTransportCache(networkKey: String): String? {
        val ts = prefs.getLong("transport_cache_ts_$networkKey", 0)
        if (System.currentTimeMillis() - ts > 7 * 24 * 60 * 60 * 1000L) return null // 7-day TTL
        return prefs.getString("transport_cache_$networkKey", null)
    }
```

Add constants in companion:
```kotlin
        private const val KEY_VLESS_UUID = "vless_uuid"
```

Update `clearLicenseData()` to also clear VLESS UUID:
```kotlin
            .remove(KEY_VLESS_UUID)
```

- [ ] **Step 3: Commit**

```bash
cd /root/vizoguard-android && git add app/src/main/java/com/vizoguard/vpn/api/ApiClient.kt app/src/main/java/com/vizoguard/vpn/license/SecureStore.kt && git commit -m "feat: add VLESS UUID provisioning API + SecureStore transport cache

POST /api/vpn/vless endpoint for per-device UUID provisioning.
SecureStore caches VLESS UUID and per-network transport mode (7-day TTL).

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Create ConnectionManager

**Files:**
- Create: `vpn/ConnectionManager.kt`
- Create test: `vpn/ConnectionManagerTest.kt`

- [ ] **Step 1: Write ConnectionManager tests**

Create `app/src/test/java/com/vizoguard/vpn/vpn/ConnectionManagerTest.kt`:

```kotlin
package com.vizoguard.vpn.vpn

import com.vizoguard.vpn.ui.ConnectionMode
import org.junit.Assert.*
import org.junit.Test

class ConnectionManagerTest {

    @Test
    fun privacyMode_mapsToAuto() {
        assertEquals(TransportMode.AUTO, TransportMode.fromConnectionMode(ConnectionMode.PRIVACY))
    }

    @Test
    fun streamingMode_mapsToDirect() {
        assertEquals(TransportMode.DIRECT, TransportMode.fromConnectionMode(ConnectionMode.STREAMING))
    }

    @Test
    fun workMode_mapsToAuto() {
        assertEquals(TransportMode.AUTO, TransportMode.fromConnectionMode(ConnectionMode.WORK))
    }
}
```

- [ ] **Step 2: Run tests**

```bash
cd /root/vizoguard-android && export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" && ./gradlew testDebugUnitTest --tests "com.vizoguard.vpn.vpn.ConnectionManagerTest" 2>&1 | tail -5
```

Expected: 3 tests pass (TransportMode.fromConnectionMode already implemented in Task 2).

- [ ] **Step 3: Implement ConnectionManager**

Create `app/src/main/java/com/vizoguard/vpn/vpn/ConnectionManager.kt`:

```kotlin
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
        // Check network cache (7-day TTL)
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

        // TCP probe to SS server (5s timeout)
        VizoLogger.d(Tag.VPN, "Transport: auto — probing direct ${ssConfig.host}:${ssConfig.port}")
        val directOk = probeDirectConnection(ssConfig.host, ssConfig.port, 5000)

        if (directOk) {
            VizoLogger.d(Tag.VPN, "Transport: direct (probe succeeded)")
            cacheKey?.let { store.saveTransportCache(it, "direct") }
            return ConfigBuilder.buildShadowsocks(ssConfig)
        }

        // Fallback to obfuscated
        if (vlessUuid != null) {
            VizoLogger.d(Tag.VPN, "Transport: obfuscated (direct probe failed)")
            cacheKey?.let { store.saveTransportCache(it, "obfuscated") }
            return ConfigBuilder.buildVless(vlessUuid, resolveServerIp("vizoguard.com"))
        }

        // No VLESS available — use SS anyway (best effort)
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
        // Simplified for initial implementation — uses null (no caching)
        // Full implementation will use ConnectivityManager gateway IP hash
        return null
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
```

- [ ] **Step 4: Commit**

```bash
cd /root/vizoguard-android && git add app/src/main/java/com/vizoguard/vpn/vpn/ConnectionManager.kt app/src/test/java/com/vizoguard/vpn/vpn/ConnectionManagerTest.kt && git commit -m "feat: add ConnectionManager with auto-detection and transport selection

TCP probe → fallback to obfuscated. Per-network caching stub.
Maps PRIVACY→auto, STREAMING→direct, WORK→auto.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: Wire AppState + BootReceiver + update references

**Files:**
- Modify: `AppState.kt`
- Modify: `receiver/BootReceiver.kt`

- [ ] **Step 1: Update AppState.connect()**

In `AppState.kt`, add ConnectionManager as a property:

```kotlin
val connectionManager = ConnectionManager(store)
```

Update the `connect()` method to:
1. After getting `accessUrl`, provision VLESS UUID (best-effort)
2. Use `connectionManager.selectTransport()` to get JSON config
3. Call `vpnManager.startVpn(configJson)` with the JSON string

The key change in `connect()` (replace lines 138-143):

```kotlin
                val accessUrl = state.vpnAccessUrl
                if (accessUrl == null) {
                    _errorMessage.value = "No VPN key available"
                    return@launch
                }
                // Provision VLESS UUID (best-effort — fallback to SS-only if fails)
                var vlessUuid = store.getVlessUuid()
                if (vlessUuid == null) {
                    try {
                        val key = store.getLicenseKey() ?: ""
                        val result = api.provisionVlessUuid(key, deviceId)
                        if (result.isSuccess) {
                            vlessUuid = result.getOrNull()?.uuid
                            vlessUuid?.let { store.saveVlessUuid(it) }
                        }
                    } catch (_: Exception) {
                        // VLESS unavailable — ConnectionManager will use SS-only
                    }
                }
                val configJson = connectionManager.selectTransport(
                    accessUrl, vlessUuid, ConnectionMode.PRIVACY // TODO: use selected mode from UI
                )
                vpnManager.startVpn(configJson)
```

- [ ] **Step 2: Update BootReceiver**

In `receiver/BootReceiver.kt`:
1. Replace `import com.vizoguard.vpn.vpn.ShadowsocksService` with `import com.vizoguard.vpn.vpn.VpnTunnelService`
2. Replace `VpnManager.parseShadowsocksUrl(accessUrl)` + `VpnManager.pendingConfig.set(config)` with:
   ```kotlin
   val config = VpnManager.parseShadowsocksUrl(accessUrl) ?: return
   val configJson = ConfigBuilder.buildShadowsocks(config)
   VpnManager.pendingConfig.set(configJson)
   ```
3. Replace `Intent(context, ShadowsocksService::class.java)` with `Intent(context, VpnTunnelService::class.java)`

- [ ] **Step 3: Commit**

```bash
cd /root/vizoguard-android && git add app/src/main/java/com/vizoguard/vpn/AppState.kt app/src/main/java/com/vizoguard/vpn/receiver/BootReceiver.kt && git commit -m "feat: wire ConnectionManager into AppState + update BootReceiver

VLESS UUID provisioned on connect (best-effort).
ConnectionManager selects transport config.
BootReceiver uses VpnTunnelService + ConfigBuilder.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: Build, test, verify

**Files:** None (verification only)

- [ ] **Step 1: Run unit tests**

```bash
cd /root/vizoguard-android && export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" && ./gradlew testDebugUnitTest 2>&1 | tail -10
```

Expected: all tests pass (existing 47 + new ConfigBuilder 8 + ConnectionManager 3 = ~58).

- [ ] **Step 2: Run lint**

```bash
cd /root/vizoguard-android && export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" && ./gradlew lintDebug 2>&1 | tail -5
```

Expected: no errors (warnings acceptable).

- [ ] **Step 3: Build debug APK**

```bash
cd /root/vizoguard-android && export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" && ./gradlew assembleDebug 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Check APK size**

```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

Expected: ~35-40MB (was 33MB with tun2socks, libbox adds ~7MB for arm64).

- [ ] **Step 5: Verify no tun2socks references remain**

```bash
grep -r "tun2socks\|shadowsocks\.Config\|shadowsocks\.Shadowsocks" app/src/ --include="*.kt" | grep -v "test/"
```

Expected: no matches.

- [ ] **Step 6: Verify libbox classes are accessible**

```bash
grep -r "io.nekohasekai.libbox" app/src/main/ --include="*.kt"
```

Expected: references in VpnTunnelService.kt and PlatformInterfaceImpl.kt.

- [ ] **Step 7: Deploy APK to downloads**

```bash
cp app/build/outputs/apk/debug/app-debug.apk /var/www/vizoguard/downloads/Vizoguard-latest.apk
curl -sI https://vizoguard.com/downloads/Vizoguard-latest.apk | head -3
```

Expected: HTTP 200.

- [ ] **Step 8: Update CLAUDE.md**

Add to the Android CLAUDE.md:
- Stack section: replace "tun2socks" with "libbox (sing-box v1.11.4)"
- Note: `VpnTunnelService` replaces `ShadowsocksService`
- Note: ConfigBuilder generates SS and VLESS configs
- Note: ConnectionManager handles transport auto-detection

- [ ] **Step 9: Commit and push**

```bash
cd /root/vizoguard-android && git add -A && git commit -m "chore: update CLAUDE.md for libbox migration

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>" && git push origin master
```
