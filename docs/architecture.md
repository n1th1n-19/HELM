# HELM Architecture

## Overview

HELM consists of two components that communicate over a local WebSocket connection:

1. **Desktop Agent** (Rust) — runs on the workstation, collects data, serves it
2. **Android App** (Kotlin/Compose) — displays data, sends commands

```
Android App ──── WebSocket ──── Desktop Agent
(Consumer)     ws://localhost    (Producer)
                  :8080
```

---

## Communication

### Transport

WebSocket over USB via ADB port forwarding:

```bash
adb forward tcp:8080 tcp:8080
```

The agent binds to `127.0.0.1:8080`. The Android app connects to `ws://localhost:8080/helm`. The ADB forward tunnels the connection over USB — no network required.

### Protocol

See [`protocol/README.md`](../protocol/README.md) for the full message schema.

**Delta model:** On connect, the agent sends a full snapshot of all state. After that, only changed fields are sent. Fields absent from a message retain their last known value on the client.

---

## Desktop Agent

### Module Map

```
agent/src/
├── main.rs          — Tokio runtime, spawns all collectors, graceful shutdown
├── config.rs        — TOML config (~/.config/helm/agent.toml)
├── protocol.rs      — Rust types matching the JSON schema contract
├── state.rs         — Shared HelmState (Arc<RwLock<>>), watch channel
├── websocket.rs     — WebSocket server, multi-client, delta broadcast
│
├── cpu.rs           — CPU usage, frequency (1s poll)
├── memory.rs        — RAM, swap (2s poll)
├── temperature.rs   — Hardware temp sensors (2s poll)
├── network.rs       — Bytes/sec up/down via /proc (1s poll)
├── disk.rs          — Bytes/sec read/write via /proc/diskstats (2s poll)
├── battery.rs       — Battery level, charging state (30s poll)
├── process.rs       — Top 10 processes by CPU (3s poll)
│
├── git.rs           — git2 repo state + notify file watcher
├── workspace.rs     — VS Code workspace detection
├── window.rs        — X11 active window via xdotool (500ms poll)
├── music.rs         — MPRIS2 media player via D-Bus/zbus
│
└── commands.rs      — Command handler (allowlist + execution)
```

### Collector Architecture

Each collector runs as an independent Tokio task:

```
Collector → writes HelmState → watch::Sender → WebSocket broadcaster → clients
```

The broadcaster is notified via a `tokio::sync::watch` channel whenever any collector updates state. It then serializes and broadcasts the current state to all connected clients.

---

## Android App

### Package Structure

```
dev.helm.app/
├── data/
│   ├── model/          — Data classes matching protocol schema
│   ├── websocket/      — HelmWebSocketClient, ConnectionManager
│   └── repository/     — HelmRepository (merges deltas into HelmState)
├── ui/
│   ├── components/     — Shared composables (HelmCard, MetricCard, etc.)
│   ├── theme/          — Colors, typography, Material3 theme
│   ├── overview/       — Overview screen + ViewModel
│   ├── system/         — System screen + ViewModel
│   ├── git/            — Git screen + ViewModel
│   ├── media/          — Media screen + ViewModel
│   ├── development/    — Development screen + ViewModel
│   ├── terminal/       — Terminal/actions screen + ViewModel
│   └── navigation/     — HelmNavigation (6-tab bottom nav)
├── service/            — HelmForegroundService
├── di/                 — Hilt DI modules
├── HelmApp.kt          — @HiltAndroidApp
├── MainActivity.kt     — Entry point
├── BootReceiver.kt     — Auto-start on boot
└── KioskManager.kt     — System bar hiding, screen-on
```

### Data Flow

```
HelmWebSocketClient (Ktor WS)
         ↓ Flow<HelmEnvelope>
ConnectionManager (backoff reconnect)
         ↓ SharedFlow<HelmEnvelope>
HelmRepository (delta merge → HelmState)
         ↓ StateFlow<HelmState>
ViewModels (per screen)
         ↓ collectAsState()
Composable screens
```

---

## Security

- Agent binds to `127.0.0.1` by default (loopback only)
- ADB port forwarding provides implicit USB-level trust
- Commands require explicit allowlist in `agent.toml`
- Dangerous commands (reboot/shutdown/suspend) require `confirmed=true` arg
- No cloud dependency, no telemetry, no analytics
