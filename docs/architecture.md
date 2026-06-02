<p align="center">
  <img src="../assets/helm.png" alt="HELM" width="140"/>
</p>

# HELM Architecture

## Overview

HELM has two components communicating over a local WebSocket connection:

1. **Desktop Agent** (Rust) — runs on the workstation, collects data, serves it
2. **Android App** (Kotlin/Compose) — displays data, sends commands

```
Android App ──── WebSocket ──── Desktop Agent
(Consumer)     ws://localhost    (Producer)
                  :9090  (USB)
              ws://LAN_IP:9090
                  (WiFi)
```

---

## Transport

### USB Mode (default)

Agent binds `127.0.0.1:9090`. The Android app connects to `localhost:9090` on the device. ADB reverse tunnels this over USB to the desktop.

```bash
adb reverse tcp:9090 tcp:9090   # device:9090 → host:9090
```

The agent maintains this rule automatically using a background task that re-runs `adb reverse` every 3 seconds — USB reconnects restore within one interval.

### WiFi Mode

Agent binds `0.0.0.0:9090`. Android connects directly to the desktop's LAN IP.

**Pairing options:**
- **QR code** — printed to terminal on startup, scanned in Settings tab
- **mDNS discovery** — agent advertises `_helm._tcp.local.`, Android discovers via NsdManager
- **Manual entry** — host + port typed directly in Settings tab

Both transports are active simultaneously when `bind_host = "0.0.0.0"`.

---

## Desktop Agent

### Module Map

```
agent/src/
├── main.rs          — Tokio runtime, bind-first startup, graceful shutdown
├── cli.rs           — clap subcommands: run/status/stop/restart/qr/config
├── config.rs        — TOML config (~/.config/helm/agent.toml)
├── protocol.rs      — Rust types matching the JSON schema contract
├── state.rs         — Shared HelmState (Arc<RwLock<>>), watch channel
├── websocket.rs     — WebSocket server, multi-client broadcast
├── adb.rs           — adb reverse maintenance (3s interval, both modes)
├── mdns.rs          — mDNS service advertisement (_helm._tcp, WiFi mode)
│
├── cpu.rs           — CPU usage, frequency (1s)
├── memory.rs        — RAM, swap (2s)
├── temperature.rs   — Hardware sensors (2s)
├── network.rs       — Bytes/sec up/down (1s)
├── disk.rs          — Bytes/sec read/write (2s)
├── battery.rs       — Battery level, charging (30s)
├── process.rs       — Top 10 processes by CPU (3s)
│
├── git.rs           — git2 repo state + notify file watcher
├── workspace.rs     — VS Code workspace detection
├── window.rs        — X11 active window via xdotool (500ms)
├── music.rs         — MPRIS2 media player via D-Bus/zbus
├── claude.rs        — Claude Code session monitoring
│
└── commands.rs      — Command handler (allowlist + execution)
```

### Startup Sequence

```
parse CLI args
load config
bind TCP socket         ← fail fast before spawning anything
write PID file
spawn collectors        ← cpu, memory, network, disk, battery, ...
spawn adb watcher       ← maintains adb reverse in background
spawn mDNS advertiser   ← WiFi mode only
print QR code           ← WiFi mode only
run WebSocket server
```

Binding the socket before spawning collectors prevents the zbus/D-Bus panic that occurred when the runtime shut down mid-startup due to a port conflict.

### Collector Architecture

```
Collector → writes HelmState → watch::Sender
                                     ↓
                           WebSocket broadcaster
                                     ↓
                            all connected clients
```

Each collector is an independent Tokio task writing to `Arc<RwLock<HelmState>>`. A `tokio::sync::watch` channel notifies the broadcaster of changes. The broadcaster serializes and sends the full current state to every client on each change.

### PID File

`~/.local/share/helm/helm-agent.pid` — written on `run`, removed on clean exit or `stop`. Used by `status`, `stop`, and `restart` to communicate with the running daemon.

---

## Android App

### Package Structure

```
dev.helm.app/
├── data/
│   ├── model/          — Data classes matching protocol schema
│   ├── prefs/          — DataStore connection preferences (mode, host, port)
│   ├── nsd/            — NsdDiscovery (mDNS LAN agent discovery)
│   ├── websocket/      — HelmWebSocketClient, ConnectionManager
│   └── repository/     — HelmRepository (delta merge → HelmState)
├── ui/
│   ├── components/     — HelmCard, MetricCard, SparklineGraph, etc.
│   ├── theme/          — Colors, typography, Material 3 theme
│   ├── overview/       — Overview screen + ViewModel
│   ├── system/         — System metrics screen + ViewModel
│   ├── git/            — Git screen + ViewModel
│   ├── media/          — Media/music screen + ViewModel
│   ├── development/    — VS Code workspace screen + ViewModel
│   ├── terminal/       — Quick actions screen + ViewModel
│   ├── claude/         — Claude Code session screen + ViewModel
│   ├── settings/       — Connection settings (mode, QR, discovery) + ViewModel
│   └── navigation/     — 7-tab NavigationRail
├── service/            — HelmForegroundService
├── di/                 — Hilt DI modules (HttpClient, DataStore)
├── HelmApp.kt
├── MainActivity.kt
├── BootReceiver.kt
└── KioskManager.kt
```

### Data Flow

```
HelmWebSocketClient (Ktor WS)
       ↓  resolves URL from ConnectionPreferences at connect time
ConnectionManager (backoff reconnect, exposes lastError)
       ↓  SharedFlow<HelmEnvelope>
HelmRepository (delta merge → HelmState)
       ↓  StateFlow<HelmState>
ViewModels (per screen)
       ↓  collectAsState()
Composable screens
```

### Connection URL Resolution

`HelmWebSocketClient.resolveUrl()` reads from DataStore on every `connect()` call:

- **USB mode:** `ws://localhost:{port}/helm`
- **WiFi mode:** `ws://{host}:{port}/helm`

Settings changes call `repository.reconnect()` → `stop()` + `start()`, so the next connection attempt uses the new URL immediately.

### WiFi Pairing Flow

```
User taps [Scan QR]
  → camera permission check
  → zxing QR scanner opens
  → scans helm://IP:PORT
  → Uri.parse extracts host + port
  → DataStore.setWifiHost / setWifiPort / setMode(WIFI)
  → repository.reconnect()
  → ConnectionManager starts new WebSocket to ws://IP:PORT/helm
```

### mDNS Discovery Flow

```
User taps [Discover]
  → NsdManager.discoverServices("_helm._tcp", PROTOCOL_DNS_SD)
  → onServiceFound → NsdManager.resolveService (fresh listener per call)
  → onServiceResolved → emits DiscoveredAgent(name, host, port)
  → user taps [Use] → saves to DataStore → reconnects
```

---

## Security

- Agent binds `127.0.0.1` by default (loopback only)
- WiFi mode (`0.0.0.0`) is opt-in via config
- ADB reverse provides implicit USB-level trust
- Commands require explicit allowlist in `agent.toml`
- Dangerous commands (reboot/shutdown/suspend) require `confirmed=true`
- No cloud, no telemetry, no analytics, no user accounts

---

## Design Language

| Token | Value |
|-------|-------|
| Background | `#0B0F14` |
| Surface | `#121A24` |
| Card | `#1A2430` |
| Border | `#243244` |
| CPU accent | `#22C55E` |
| RAM accent | `#3B82F6` |
| Temp accent | `#F59E0B` |
| Network accent | `#06B6D4` |
| Git accent | `#EF4444` |
| Music accent | `#A855F7` |
| Claude accent | `#FF7A00` |
