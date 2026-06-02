<p align="center">
  <img src="../assets/helm.png" alt="HELM" width="120"/>
</p>

# HELM Desktop Agent

Rust/Tokio WebSocket server that collects system metrics and pushes them to the Android app.

## Quick Start

```bash
# Install via one-liner (recommended)
curl -fsSL https://raw.githubusercontent.com/n1th1n-19/HELM/main/install.sh | bash

# Or build from source
cargo build --release
./target/release/helm-agent run
```

## CLI

```
helm-agent run      # start daemon
helm-agent status   # show running status + address
helm-agent stop     # stop daemon
helm-agent restart  # stop + start
helm-agent qr       # print WiFi pairing QR code
helm-agent config   # dump current config
```

Full CLI reference: [docs/cli.md](../docs/cli.md)

## Configuration

`~/.config/helm/agent.toml` — created with defaults on first run.

```toml
bind_host = "127.0.0.1"   # USB only; set "0.0.0.0" for WiFi
port = 9090
mdns_enabled = true

allowed_commands = ["git_pull", "git_push", "lock"]

[poll_intervals]
cpu_ms = 1000
memory_ms = 2000
network_ms = 1000
```

Full config reference: [docs/setup.md](../docs/setup.md)

## Module Map

```
src/
├── main.rs        — startup, bind-first, graceful shutdown
├── cli.rs         — clap subcommands
├── config.rs      — TOML config loader
├── protocol.rs    — wire types (matches Android HelmModels.kt)
├── state.rs       — Arc<RwLock<HelmState>>, watch channel
├── websocket.rs   — multi-client WebSocket server
├── adb.rs         — adb reverse auto-maintenance
├── mdns.rs        — mDNS _helm._tcp advertisement
│
├── cpu.rs         — CPU usage + frequency (1s)
├── memory.rs      — RAM + swap (2s)
├── temperature.rs — hardware sensors (2s)
├── network.rs     — bytes/sec up/down (1s)
├── disk.rs        — bytes/sec read/write (2s)
├── battery.rs     — battery level + charging (30s)
├── process.rs     — top 10 processes by CPU (3s)
│
├── git.rs         — git2 repo state + file watcher
├── workspace.rs   — VS Code workspace detection
├── window.rs      — X11 active window via xdotool (500ms)
├── music.rs       — MPRIS2 via D-Bus/zbus
├── claude.rs      — Claude Code session monitoring
└── commands.rs    — allowlisted command execution
```

## Architecture

See [docs/architecture.md](../docs/architecture.md) for the full data flow and design.

## Dependencies

| Crate | Purpose |
|-------|---------|
| `tokio` | Async runtime |
| `tokio-tungstenite` | WebSocket server |
| `sysinfo` | CPU, RAM, network, disk, processes |
| `git2` | Git repository state |
| `zbus` | D-Bus / MPRIS2 music |
| `battery` | Battery info |
| `notify` | File system watcher (git) |
| `mdns-sd` | mDNS service advertisement |
| `qr2term` | Terminal QR code rendering |
| `clap` | CLI argument parsing |
| `serde` / `serde_json` | JSON serialization |
| `tracing` | Structured logging |
