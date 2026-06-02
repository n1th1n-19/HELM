# HELM

> Take control of your development environment.

HELM transforms any Android device into a dedicated developer command center — a sidecar display mounted beside your workstation showing real-time system monitoring, git insights, music controls, and active development environment status.

**Think:** Mission Control + Developer Dashboard + Stream Deck + System Monitor — built for developers.

---

## Features

- **System monitoring** — CPU usage, RAM, temperature, network I/O, disk I/O, battery
- **Git insights** — branch, ahead/behind, working tree status, recent commits
- **Music controls** — MPRIS2 integration (Spotify, MPV, VLC, Firefox, any compatible player)
- **Active workspace** — VS Code workspace detection, current file, active window
- **Quick actions** — restart dev server, git pull/push, open terminal, lock screen
- **Always-on sidecar** — USB connection via ADB port forwarding (no Wi-Fi required)
- **Local-first** — no cloud, no accounts, no telemetry
- **Kiosk mode** — fullscreen, landscape lock, keep screen on

---

## Architecture

```
┌─────────────────────┐     WebSocket     ┌──────────────────────┐
│   Android Device    │◄─────────────────►│  Desktop Agent       │
│   (Kotlin/Compose)  │   ws://localhost   │  (Rust/Tokio)        │
│                     │      :8080         │                       │
│  • 6-tab dashboard  │                   │  • System metrics     │
│  • Responsive UI    │  ADB port forward │  • Git collector      │
│  • Connection mgmt  │  tcp:8080:8080    │  • MPRIS2 music       │
│  • Kiosk mode       │                   │  • VS Code workspace  │
└─────────────────────┘                   │  • Window detection   │
                                          │  • Command executor   │
                                          └──────────────────────┘
```

The desktop agent collects system data and pushes delta updates to the Android client over WebSocket. The Android device connects via USB using ADB port forwarding — no Wi-Fi or internet required.

---

## Requirements

**Workstation:**
- Linux (KDE/X11 recommended for full feature set; Wayland degrades gracefully)
- Rust 1.75+
- ADB installed

**Android device:**
- Android 10+ (API 29+)
- Phone, tablet, or foldable

**Optional for full feature set:**
- `xdotool` — active window detection (X11)
- `playerctl` / MPRIS2-compatible players — music controls
- `fuser` — dev server restart
- VS Code or VSCodium

---

## Quick Start

### 1. Build and run the desktop agent

```bash
cd agent
cargo build --release
./target/release/helm-agent
# Agent listens on 127.0.0.1:8080 by default
```

### 2. Forward the port via ADB

```bash
# Run this whenever you connect your Android device
adb forward tcp:8080 tcp:8080
```

### 3. Install the Android app

```bash
# Build an APK
cd android
./gradlew assembleRelease

# Install on connected device
adb install app/build/outputs/apk/release/app-release.apk
```

Or sideload via Android Studio.

### 4. Enable kiosk mode (optional)

In the Android app, the kiosk mode is enabled by default. The app hides the status bar and navigation bar, keeps the screen on, and locks to landscape. Mount your device beside your monitor.

---

## Configuration

The agent reads `~/.config/helm/agent.toml` (created automatically with defaults if absent):

```toml
# Network
port = 8080
bind_host = "127.0.0.1"   # Change to "0.0.0.0" for LAN access (not recommended)

# Allowed commands (from Android)
# Uncomment actions you want to permit:
allowed_commands = [
    "git_pull",
    "git_push",
    "lock",
    # "open_terminal",
    # "open_project",
    # "restart_dev_server",
    # "suspend",
    # "reboot",
    # "shutdown",
]

# Poll intervals (milliseconds)
[poll_intervals]
cpu_ms = 1000
memory_ms = 2000
network_ms = 1000
disk_ms = 2000
battery_ms = 30000
temperature_ms = 2000
process_ms = 3000
window_ms = 500
```

---

## Protocol

HELM uses a WebSocket-based delta protocol. On connect, the agent sends a full snapshot of all state. After that, only changed fields are sent. See [`protocol/README.md`](protocol/README.md) for the full message schema.

---

## Project Structure

```
helm/
├── android/          # Android app (Kotlin, Jetpack Compose, Material 3)
├── agent/            # Desktop agent (Rust, Tokio, WebSocket)
├── protocol/         # WebSocket message schema definitions (JSON Schema)
├── docs/             # Architecture and setup documentation
├── plugins/          # Plugin system (V2, coming soon)
└── examples/         # Usage examples
```

---

## Performance Targets

| Target | Goal |
|--------|------|
| Android RAM | < 100 MB |
| Android CPU | < 5% |
| Android frame rate | 60 FPS |
| Agent RAM | < 20 MB |
| Agent CPU at idle | ~0% |

---

## Roadmap

- [ ] Album art display in media screen
- [ ] Wayland active window support (via wlr-foreign-toplevel or KDE DBus)
- [ ] Plugin system (Docker, Kubernetes, GitHub, GitLab, Ollama, Claude Code)
- [ ] Linux / macOS client
- [ ] Wi-Fi connection mode

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT — see [LICENSE](LICENSE)
