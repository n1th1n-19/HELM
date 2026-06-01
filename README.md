# HELM

> Take control of your development environment.

HELM transforms any Android device into a dedicated developer command center — a sidecar display mounted beside your workstation showing real-time system monitoring, git insights, music controls, and development environment status.

**Think:** Mission Control + Developer Dashboard + Stream Deck + System Monitor.

## Features

- Real-time system monitoring (CPU, RAM, temperature, network, disk)
- Git repository insights (branch, status, ahead/behind, commits)
- Music controls (MPRIS2 — Spotify, MPV, VLC, Firefox, any compatible player)
- Active workspace and window information
- Quick workstation actions (restart dev server, git pull/push, lock, suspend)
- Always-on sidecar display via USB (ADB port forwarding, no Wi-Fi needed)
- Local-first, no cloud, no accounts, no telemetry

## Architecture

HELM has two components:

- **Desktop Agent** (Rust) — runs on your workstation, collects system data, serves it via WebSocket
- **Android App** (Kotlin + Compose) — connects via USB, displays data, sends commands

Communication uses ADB port forwarding over USB for a reliable, low-latency, local-only connection.

## Requirements

- Android 10+ device (phone, tablet, or foldable)
- Linux workstation (KDE/X11 recommended for full feature set)
- ADB installed

## Quick Start

```bash
# 1. Build and run the desktop agent
cd agent
cargo build --release
./target/release/helm-agent

# 2. Forward the port via ADB
adb forward tcp:8080 tcp:8080

# 3. Install and open the Android app on your device
```

## Project Status

🚧 Under active development.

## License

MIT — see [LICENSE](LICENSE)
