# Vizoguard VPN — Android

Android VPN client using Shadowsocks (tun2socks). Kotlin + Jetpack Compose + Material3.

## Build & Test

JAVA_HOME **must** be set to Android Studio's bundled JBR — system Java will not work.

```bash
# Windows
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="$HOME/AppData/Local/Android/Sdk"

# Linux (if Android SDK installed)
# export JAVA_HOME="/opt/android-studio/jbr"
# export ANDROID_HOME="$HOME/Android/Sdk"

./gradlew assembleDebug      # Build
./gradlew lintDebug           # Lint (0 warnings expected)
./gradlew testDebugUnitTest   # Unit tests (16 tests)
./gradlew installDebug        # Install on emulator/device
```

Emulator:
```bash
"$ANDROID_HOME/emulator/emulator" -avd Medium_Phone_API_36.1 &
adb shell am start -n com.vizoguard.vpn/.MainActivity
```

## Gotchas

- **`libs/tun2socks.aar`** is a local file dependency, not from Maven. If it's missing the build fails silently at link time. Do not delete `libs/`.
- **AGP + compileSdk 36**: Currently on AGP 8.9.2. Some newer AndroidX libs require matching AGP versions — check compatibility before bumping dependencies.
- **ProGuard**: Tink (from security-crypto) and Ktor need explicit keep/dontwarn rules — see `app/proguard-rules.pro`. Adding new Ktor features may need new rules.
- **`testOptions { unitTests.isReturnDefaultValues = true }`** — Android framework methods return defaults (0/null/false) in unit tests instead of throwing. Be aware mocks may hide real issues.

## Architecture

All source under `app/src/main/java/com/vizoguard/vpn/`:

- `VizoguardApp.kt` — Application class (initialization)
- `AppState.kt` — Global app state (license, VPN status, screen routing)
- `MainActivity.kt` — Single-activity Compose host with deep links
- `api/ApiClient.kt` — Ktor client, base URL: `https://vizoguard.com/api`
- `license/` — License validation, EncryptedSharedPreferences (SecureStore), device ID
- `vpn/` — VPN service (ShadowsocksService), state machine (VpnManager), VpnState enum
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

- Unit tests in `app/src/test/` — 28 tests across: AppState, ApiClient, LicenseManager, SecureStore, VpnManager
- Emulator: AVD `Medium_Phone_API_36.1` (API 36)
- No instrumented tests yet (`app/src/androidTest/` is empty)

## Git

- Remote: `origin` → `pentedigital/vizoguard-android` (private)
- Branch: `master`
- Commit style: conventional commits (`feat:`, `fix:`, `chore:`)
- GitHub CLI: `gh` installed, authenticated as `pentedigital`

## Claude Automations
- **Skills**: `/build-test`, `/deploy-emulator`, `/run-tests`, `/release-apk`
- **Subagents**: `security-reviewer`, `android-reviewer`
- **Hooks**: Credential/key file edit guard (PreToolUse), related test suggestion on source edit (PostToolUse)
