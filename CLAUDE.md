# Vizoguard VPN — Android

Android VPN client using sing-box (libbox v1.11.4). Supports Shadowsocks (direct) and VLESS/WS/TLS (obfuscated) transport with auto-detection. Kotlin + Jetpack Compose + Material3.

## Build & Test

JAVA_HOME **must** be set to Android Studio's bundled JBR — system Java will not work.

```bash
# VPS (this server — no Android Studio)
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
# SDK at /opt/android-sdk, local.properties: sdk.dir=/opt/android-sdk

# Windows
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="$HOME/AppData/Local/Android/Sdk"

# Linux (if Android SDK installed)
# export JAVA_HOME="/opt/android-studio/jbr"
# export ANDROID_HOME="$HOME/Android/Sdk"

./gradlew assembleDebug      # Build
./gradlew lintDebug           # Lint (0 warnings expected)
./gradlew testDebugUnitTest   # Unit tests (47 tests)
./gradlew installDebug        # Install on emulator/device
```

Useful dev commands:
```bash
./gradlew testDebugUnitTest --tests "com.vizoguard.vpn.**.<TestClass>"  # Single test class
adb logcat -s SYSTEM:V SERVICE:V API:V LICENSE:V  # Filter app logs (VizoLogger tags)
./gradlew assembleRelease      # Release APK (requires signing config)
```

Emulator:
```bash
"$ANDROID_HOME/emulator/emulator" -avd Medium_Phone_API_36.1 &
adb shell am start -n com.vizoguard.vpn/.MainActivity
```

## Architectural Rules
- `connect()` validates license with server before every VPN connection — cached credentials never trusted
- `connect()` falls back to `canConnectOffline()` on network errors — allows VPN with cached credentials during grace period
- VPN access URL cleared from SecureStore on suspension/expiry via `clearVpnAccessUrl()`
- `getCachedState().isValid` accepts both `"active"` and `"cancelled"` status
- `LicenseResponse.expires` is nullable (`String?`) — server may send null
- Grace period (`firstFailureTimestamp`) only starts on network errors, NOT on 403 server rejections
- `onRevoke()` emits `VpnState.ERROR` not `IDLE` — user sees error, not ready state
- PrivacyScore shows 0 bars on ERROR state (not 3)
- `pendingConfig` null recovery: reads VPN URL from SecureStore after process kill
- BootReceiver: `startForegroundService` wrapped in try-catch (Android 12+ crash guard)
- No `foregroundServiceType="specialUse"` — VPN services exempt, removed for Play Store
- `cancelAndJoin()` used in connect() to prevent native race with old coroutine
- `RECONNECTING` state guarded in `onStartCommand` duplicate-start check (alongside CONNECTED/CONNECTING)
- Kill switch opens Android system VPN settings (`ACTION_VPN_SETTINGS`) — no in-app toggle (Android requires system-level enforcement)
- Certificate pinning: leaf cert SPKI + R10/R11 intermediate (not root CA) — must update on cert renewal, use `/cert-check`
- DebugScreen uses `maskKey()` for license key and receives URL length (not raw URL)
- VizoLogger `sanitize()` redacts license keys, ss:// URLs, and IP addresses

## Gotchas

- **`libs/libbox.aar`** (sing-box v1.11.4) is a local file dependency, not from Maven. If it's missing the build fails silently at link time. Do not delete `libs/`.
- **AGP + compileSdk 36**: Currently on AGP 8.9.2. Some newer AndroidX libs require matching AGP versions — check compatibility before bumping dependencies.
- **ProGuard**: Tink (from security-crypto) and Ktor need explicit keep/dontwarn rules — see `app/proguard-rules.pro`. Adding new Ktor features may need new rules.
- **`testOptions { unitTests.isReturnDefaultValues = true }`** — Android framework methods return defaults (0/null/false) in unit tests instead of throwing. Be aware mocks may hide real issues.
- **BootReceiver** validates cached license (status + expiry) before starting VPN service on boot — expired/invalid licenses skip auto-connect
- **VpnManager.connect()** uses `synchronized(connectLock)` to prevent rapid-fire calls from racing on `pendingConfig`
- **VpnTunnelService** logs a warning if `pendingConfig` is null in `onStartCommand` (race condition diagnostic)
- **LicenseCheckWorker** checks cached license expiry on network error — if expired, stops VPN instead of retrying for 24h
- **AppState** auto-connect only fires after server validation confirms license is valid (not from stale cache)

## Architecture

All source under `app/src/main/java/com/vizoguard/vpn/`:

- `VizoguardApp.kt` — Application class (initialization)
- `AppState.kt` — Global app state (license, VPN status, screen routing)
- `MainActivity.kt` — Single-activity Compose host with deep links
- `api/ApiClient.kt` — Ktor client, base URL: `https://vizoguard.com/api`
- `license/` — License validation, EncryptedSharedPreferences (SecureStore), device ID
- `vpn/` — VPN service (VpnTunnelService), state machine (VpnManager), ConnectionManager, ConfigBuilder, PlatformInterfaceImpl, VpnState enum
- `ui/` — Compose screens: Main, Activate, Settings, Debug, Onboarding, EngineView, ModeSelector, PrivacyScore
- `worker/` — LicenseCheckWorker (24h periodic check)
- `receiver/` — BootReceiver (auto-connect on boot)
- `util/` — VizoLogger (tagged logging), LogExporter (share logs)

## Key Patterns

- VPN state machine: `IDLE → LICENSED → CONNECTING → CONNECTED` (also: `RECONNECTING`, `BLOCKED`, `ERROR`)
- Encrypted prefs via `androidx.security:security-crypto` (EncryptedSharedPreferences)
- License key format: `VIZO-XXXX-XXXX-XXXX-XXXX`
- Deep link: `vizoguard-vpn://activate?key=...`
- API endpoints: `/license`, `/vpn/create`, `/vpn/get`, `/vpn/status`, `/health`

## Testing

- Unit tests in `app/src/test/` — 47 tests across: AppState, ApiClient, LicenseManager, SecureStore, VpnManager
- Emulator: AVD `Medium_Phone_API_36.1` (API 36)
- No instrumented tests yet (`app/src/androidTest/` is empty)

## Git

- Remote: `origin` → `pentedigital/vizoguard-android` (private)
- Branch: `master`
- Commit style: conventional commits (`feat:`, `fix:`, `chore:`)
- GitHub CLI: `gh` installed, authenticated as `pentedigital`

## Distribution
- APK hosted at `vizoguard.com/downloads/Vizoguard-latest.apk` (nginx serves from `/var/www/vizoguard/downloads/`)
- `download.html` links directly to APK with sideloading instructions + Outline fallback
- `thank-you.html` detects Android via UA, shows APK download + intent QR code
- Deep link: `vizoguard-vpn://activate?key=VIZO-XXXX` — auto-activates license on install
- Intent URL fallback: if app not installed, Android falls back to APK download
- Deploy new APK: use `/android-release` skill (builds, copies to downloads, verifies)

## Claude Automations
- **Skills**: `/build-test`, `/deploy-emulator`, `/run-tests`, `/release-apk`, `/android-release`, `/gen-test`, `/api-check`
- **Subagents**: `security-reviewer`, `android-reviewer`, `test-coverage-analyzer`, `proguard-checker`
- **Hooks**: Credential/key file edit guard (PreToolUse), libbox.aar deletion guard (PreToolUse), related test suggestion on source edit (PostToolUse), lint suggestion on Kotlin edit (PostToolUse)
