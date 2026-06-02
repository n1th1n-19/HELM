<p align="center">
  <img src="../assets/helm.png" alt="HELM" width="120"/>
</p>

# HELM Examples

Common setups and configurations for HELM.

---

## USB Sidecar (Default)

Minimal config — plug in and go:

```toml
# ~/.config/helm/agent.toml
port = 9090
bind_host = "127.0.0.1"
allowed_commands = ["git_pull", "git_push", "lock"]
```

```bash
helm-agent run
# Android app connects automatically via adb reverse
```

---

## WiFi Sidecar

Wireless, no USB cable:

```toml
# ~/.config/helm/agent.toml
bind_host = "0.0.0.0"
port = 9090
mdns_enabled = true
```

```bash
helm-agent run
# QR code prints — scan in Android Settings tab
```

---

## USB + WiFi Simultaneously

Same config as WiFi mode. The agent accepts both:
- USB: Android connects via `adb reverse tcp:9090 tcp:9090`
- WiFi: Android connects via LAN IP

```toml
bind_host = "0.0.0.0"
port = 9090
```

---

## Locked-Down Command Set

Only allow safe, non-destructive commands:

```toml
allowed_commands = [
    "git_pull",
    "git_push",
]
```

---

## Full Command Set

Allow all commands including system actions:

```toml
allowed_commands = [
    "git_pull",
    "git_push",
    "lock",
    "open_terminal",
    "open_project",
    "restart_dev_server",
    "suspend",
    "reboot",
    "shutdown",
]
```

> Reboot and shutdown require `confirmed=true` in the Android app.

---

## Fast Polling (High-frequency Dev Monitoring)

```toml
[poll_intervals]
cpu_ms         = 500
memory_ms      = 1000
network_ms     = 500
disk_ms        = 1000
temperature_ms = 1000
battery_ms     = 30000
process_ms     = 2000
window_ms      = 250
```

Trades slightly higher CPU for more responsive updates.

---

## Slow Polling (Battery / CPU Friendly)

```toml
[poll_intervals]
cpu_ms         = 2000
memory_ms      = 5000
network_ms     = 2000
disk_ms        = 5000
temperature_ms = 5000
battery_ms     = 60000
process_ms     = 10000
window_ms      = 1000
```

---

## Autostart with systemd

```bash
# Installed by install.sh — or set up manually:
systemctl --user enable helm-agent
systemctl --user start helm-agent

# Check status
helm-agent status
journalctl --user -u helm-agent -f
```

---

## Non-Standard Port

If port 9090 is in use:

```toml
port = 9191
```

Android Settings tab → change port to match, or re-scan QR code.

---

## Git Repo Watching

Watch specific paths for instant git updates (instead of polling):

```toml
git_watch_paths = [
    "/home/user/projects/myapp",
    "/home/user/projects/mylib",
]
```
