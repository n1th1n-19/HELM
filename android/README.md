<p align="center">
  <img src="../assets/helm.png" alt="HELM" width="120"/>
</p>

# HELM Android App

Kotlin/Jetpack Compose sidecar dashboard that connects to the desktop agent and displays real-time developer data.

## Requirements

- Android 10+ (API 29+)
- JDK 17+
- Android SDK platform 35

## Build

```bash
# Debug APK
./gradlew assembleDebug

# Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk

# Or install + launch in one step
./gradlew installDebug
```

## Connection

**USB (default):**
- Plug in device via USB with USB debugging enabled
- The desktop agent maintains `adb reverse tcp:9090 tcp:9090` automatically
- App connects to `ws://localhost:9090/helm`

**WiFi:**
- Open **Settings tab** in the app
- Tap **[Scan QR]** and scan the QR printed by `helm qr`
- Or tap **[Discover]** to find agents on the LAN via mDNS
- Or manually enter host + port and tap **[Save]**

## Package Structure

```
dev.helm.app/
├── data/
│   ├── model/          — HelmState, SystemUpdate, GitUpdate, MusicUpdate, …
│   ├── prefs/          — ConnectionPreferences (DataStore: mode, host, port)
│   ├── nsd/            — NsdDiscovery (mDNS LAN agent discovery)
│   ├── websocket/      — HelmWebSocketClient (Ktor), ConnectionManager
│   └── repository/     — HelmRepository (delta merge, reconnect)
├── ui/
│   ├── overview/       — Dashboard: system, git, dev, media, Claude cards
│   ├── system/         — CPU, RAM, network, disk, processes
│   ├── git/            — Branch, working tree, commit list
│   ├── media/          — Album art, playback controls, MPRIS2
│   ├── development/    — VS Code workspace, active file
│   ├── terminal/       — Quick actions, system commands
│   ├── claude/         — Claude Code session info
│   ├── settings/       — Connection mode, QR pairing, mDNS discovery
│   ├── components/     — HelmCard, MetricCard, SparklineGraph, …
│   ├── theme/          — Dark color palette, Material 3 tokens
│   └── navigation/     — 7-tab NavigationRail
├── service/            — HelmForegroundService (keeps connection alive)
├── di/                 — Hilt modules (HttpClient, DataStore)
├── HelmApp.kt
├── MainActivity.kt
├── BootReceiver.kt     — Auto-start on boot (kiosk mode)
└── KioskManager.kt     — Fullscreen, landscape lock, screen-on
```

## Design Tokens

| Color | Hex | Usage |
|-------|-----|-------|
| Background | `#0B0F14` | App background |
| Surface | `#121A24` | Navigation rail |
| Card | `#1A2430` | All cards |
| Border | `#243244` | Card borders |
| CPU | `#22C55E` | CPU metrics |
| RAM | `#3B82F6` | Memory metrics |
| Temp | `#F59E0B` | Temperature |
| Network | `#06B6D4` | Network / WiFi |
| Git | `#EF4444` | Git status |
| Music | `#A855F7` | Media player |
| Claude | `#FF7A00` | Claude Code |

## Key Dependencies

| Library | Purpose |
|---------|---------|
| Jetpack Compose + Material 3 | UI |
| Ktor Client (OkHttp) | WebSocket |
| Hilt | Dependency injection |
| DataStore Preferences | Connection settings persistence |
| Kotlinx Serialization | JSON |
| Coil | Image loading (album art) |
| ZXing Android Embedded | QR code scanning |

## Architecture

See [docs/architecture.md](../docs/architecture.md) for the full data flow.
