# Connection Modes — Design Spec

## Summary

Wire the existing mode selector UI (Privacy/Streaming/Work) to real client-side behavior using a Hybrid approach: all mode settings are deferred to the next connect. Switching modes while connected shows a reconnect prompt.

## Decision Record

- **Approach**: D) Hybrid — presets applied on next connect
- **Backend**: C) No backend support — client-side only, same SS tunnel
- **Persistence**: A) Store mode only — no per-setting overrides in v1
- **Future path**: `overrides` field can be added later without migration

## Core Model

`ConnectionMode` is a named configuration profile and the single source of truth for all behavioral settings. All settings are tunnel-dependent and take effect on the next connect (because `setBlocking`, DNS, and MTU are all configured at `VpnService.Builder` creation time).

```
ConnectionMode (enum — moved from ui/ to vpn/ package)
 ├── identity: PRIVACY | STREAMING | WORK
 └── config (all deferred — applied at tunnel creation)
      ├── dnsServers: List<String>
      ├── mtu: Int
      ├── killSwitch: Boolean
      ├── autoReconnect: Boolean  (placeholder, no runtime effect in v1)
      ├── title: String
      └── subtitle: String
```

## Mode Profiles

| Setting | Privacy | Streaming | Work |
|---------|---------|-----------|------|
| DNS | `1.1.1.1` only | `8.8.8.8`, `8.8.4.4` | `1.1.1.1`, `8.8.8.8` |
| MTU | 1500 | 1400 | 1500 |
| Kill switch | ON (enforced) | OFF | ON |
| Auto-reconnect | `true` (placeholder) | `true` (placeholder) | `true` (placeholder) |
| UI title | "Privacy" | "Streaming" | "Work" |
| UI subtitle | "Maximum protection" | "Optimized for streaming" | "Stable & balanced" |

## Data Flow

### Mode switch while disconnected

1. User taps mode pill → `selectedMode` updates in `SecureStore`
2. All settings derived from new mode
3. Next `connect()` uses new DNS/MTU/killSwitch

### Mode switch while connected

1. User taps mode pill → `selectedMode` updates in `SecureStore`
2. `selectedMode != activeTunnelMode` detected by UI
3. Banner appears: "Mode changed — reconnect for full effect"
4. "Reconnect now" button in banner
5. If user taps reconnect → disconnect + connect with new mode
6. If user ignores → current tunnel continues, new mode applies on next natural connect

### Boot / auto-connect

1. `BootReceiver` reads `selectedMode` from `SecureStore`
2. Calls `ConnectionMode.toConfig()` to derive DNS/MTU/killSwitch
3. Passes to `ShadowsocksService` via intent extras

## Architecture Changes

### New: `vpn/ConnectionModeConfig.kt`

- Relocate `ConnectionMode` enum from `ui/ModeSelector.kt` to this file (update imports in `ModeSelector.kt`)
- `data class ModeConfig(dnsServers: List<String>, mtu: Int, killSwitch: Boolean, autoReconnect: Boolean, title: String, subtitle: String)`
- `fun ConnectionMode.toConfig(): ModeConfig` — single mapping function, the only place mode→settings translation lives
- Both `VpnManager` and `BootReceiver` call `toConfig()` to derive settings (shared logic, no duplication)

### Modified: `vpn/VpnState.kt`

- Add `activeTunnelMode: ConnectionMode? = null` field to `VpnStatus` data class
- Set at connect time, cleared on disconnect (IDLE/LICENSED)
- Used by UI to detect selectedMode != activeTunnelMode → show reconnect banner

### Modified: `license/SecureStore.kt`

- Replace `kill_switch` key with `selected_mode` (String, default `"PRIVACY"`)
- Add `getSelectedMode(): ConnectionMode` and `saveSelectedMode(mode: ConnectionMode)`
- Remove `getKillSwitch()` / `saveKillSwitch()` — kill switch is mode-derived
- Keep `getAutoConnect()` — top-level setting (whether to connect on boot), not mode-specific

### Modified: `vpn/VpnManager.kt`

- `startVpn(accessUrl, mode: ConnectionMode)` replaces `startVpn(accessUrl, killSwitch: Boolean)`
- Resolves `ModeConfig` via `mode.toConfig()`, passes DNS/MTU/killSwitch to service intent extras
- Sets `activeTunnelMode` on `VpnStatus` when transitioning to CONNECTING

### Modified: `vpn/ShadowsocksService.kt`

- New intent extras: `EXTRA_DNS_SERVERS` (StringArray), `EXTRA_MTU` (Int)
- `connect()` iterates DNS list via `builder.addDnsServer()` instead of hardcoded values
- `connect()` uses `intent.getIntExtra(EXTRA_MTU, 1500)` instead of hardcoded 1500
- Kill switch via `setBlocking` reads from `EXTRA_KILL_SWITCH` (already exists)

### Modified: `AppState.kt`

- New: `selectedMode: StateFlow<ConnectionMode>` — initialized from `SecureStore`, observed by UI
- New: `switchMode(mode: ConnectionMode)` — saves to `SecureStore`, updates flow
- `connect()` passes `selectedMode.value` to `VpnManager.startVpn()`
- Exposes `currentModeConfig: StateFlow<ModeConfig>` derived from `selectedMode` for UI consumption

### Modified: `ui/MainScreen.kt`

- `selectedMode` changes from local `remember { mutableStateOf(ConnectionMode.PRIVACY) }` to prop from `AppState` flow
- `onModeSelected` calls `appState.switchMode()` instead of local state update
- New: reconnect banner composable — shown when `vpnStatus.activeTunnelMode != null && vpnStatus.activeTunnelMode != selectedMode`
- Banner contains text + "Reconnect now" button that calls `appState.disconnect()` then `appState.connect()`

### Modified: `ui/ModeSelector.kt`

- Remove `ConnectionMode` enum (moved to `vpn/ConnectionModeConfig.kt`)
- Update import to `com.vizoguard.vpn.vpn.ConnectionMode`
- Subtitles updated to match spec: "Maximum protection", "Optimized for streaming", "Stable & balanced"

### Modified: `ui/SettingsSheet.kt`

- Kill switch toggle: `enabled = false`, `checked` reflects `currentModeConfig.killSwitch`
- On tap of disabled toggle: show brief message "Kill switch is controlled by your connection mode"
- Remove `onKillSwitchChange` callback parameter (replaced with mode-derived read-only state)
- Add `modeName: String` parameter to display "Controlled by Privacy mode" hint

### Modified: `ui/EngineView.kt`

- Replace hardcoded `"Encrypted (1.1.1.1)"` DNS display with active mode's DNS servers from `vpnStatus`
- Add mode name row: `[M] Mode: Privacy` (using `activeTunnelMode`)

### Modified: `receiver/BootReceiver.kt`

- Reads mode via `SecureStore.getSelectedMode()`
- Calls `mode.toConfig()` to get kill switch, DNS, MTU
- Passes DNS array and MTU as intent extras alongside existing host/port/method/password
- Note: BootReceiver constructs intent directly (bypasses VpnManager) — `toConfig()` is the shared logic preventing drift

### Logging

- `AppState.switchMode()` logs: `VizoLogger.systemEvent("Mode switched to $mode")`
- `VpnManager.startVpn()` logs mode name in existing vpnState transition

## Scope Exclusions

- No backend changes — same Shadowsocks tunnel config
- No per-setting overrides (future `overrides` field extension point)
- No auto-reconnect timer implementation (`autoReconnect` field is a Boolean placeholder with no runtime effect)
- No DNS-over-HTTPS — standard DNS via VPN tunnel
- No `ModeSelector` visual redesign — existing pill layout is kept
- No ProGuard changes needed — `ModeConfig` is not used with reflection/serialization

## Migration

- On first launch after update, `selected_mode` key absent → defaults to `PRIVACY`
- Existing `kill_switch` pref is ignored once mode system is active
- No data loss — `auto_connect` key is preserved as-is

## Testing

- Unit tests: `ConnectionMode.toConfig()` returns correct DNS/MTU/killSwitch for all 3 modes
- Unit tests: `SecureStore` round-trips mode correctly (save STREAMING, read STREAMING)
- Unit tests: `VpnManager.startVpn()` passes mode-derived extras to intent
- Unit tests: reconnect banner logic — `activeTunnelMode != selectedMode` detection
- Manual: switch mode while disconnected → next connect uses new DNS/MTU
- Manual: switch mode while connected → banner appears with "Reconnect now"
- Manual: tap "Reconnect now" → disconnects and reconnects with new mode
- Manual: Settings sheet → kill switch toggle disabled, shows mode hint
- Manual: boot auto-connect → uses persisted mode's settings
