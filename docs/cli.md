<p align="center">
  <img src="../assets/helm.png" alt="HELM" width="120"/>
</p>

# HELM Agent CLI Reference

## Synopsis

```
helm-agent [COMMAND] [OPTIONS]
```

When called with no command, `helm-agent` starts the daemon (same as `helm-agent run`).

---

## Commands

### `run`

Start the agent daemon.

```bash
helm-agent run
helm-agent        # equivalent
```

**What it does:**
- Binds the WebSocket server on `bind_host:port` (from config)
- Writes a PID file to `~/.local/share/helm/helm-agent.pid`
- Spawns all system collectors (CPU, RAM, network, git, music, …)
- Maintains `adb reverse tcp:PORT tcp:PORT` every 3 seconds
- In WiFi mode (`bind_host = "0.0.0.0"`): prints pairing QR code and starts mDNS advertisement
- Runs until SIGINT or SIGTERM

**WiFi mode output on startup:**
```
HELM WiFi pairing — scan with Android app:
[QR code]
helms://192.168.1.9:9090?token=a3f1...&cert=b72c...
```

The `helms://` scheme signals the Android app to use WSS + cert pinning + PSK token. USB mode still uses plain `ws://localhost`.

---

### `status`

Show whether the agent is running and where it is listening.

```bash
helm-agent status
```

**Output:**
```
running  pid=12345  addr=0.0.0.0:9090
```
or:
```
stopped
```

If the PID file exists but the process is gone, prints `stopped (stale pid file removed)` and cleans up.

---

### `stop`

Stop the running agent.

```bash
helm-agent stop
```

Sends SIGTERM to the running process and waits up to 5 seconds for a clean exit. If the process does not exit within 5 seconds, sends SIGKILL. Removes the PID file.

**Output:**
```
stopped  pid=12345
```

---

### `restart`

Stop the running agent, then start a new one.

```bash
helm-agent restart
```

Equivalent to `helm-agent stop && helm-agent run`. Useful after editing `~/.config/helm/agent.toml`.

---

### `qr`

Print the WiFi pairing QR code without starting the agent.

```bash
helm-agent qr
```

Reads the current config, detects the LAN IP, and prints:

```
HELM WiFi pairing — scan with Android app:
[QR code]
helm://192.168.1.9:9090
```

Scan this in the Android app: **Settings tab → Scan QR**. The app auto-configures TLS + token from the URL — no manual cert setup needed.

If `bind_host` is `127.0.0.1` (USB-only mode), prints a message explaining how to enable WiFi mode.

---

### `config`

Print the current effective configuration.

```bash
helm-agent config
```

**Output:**
```
bind_host    = 0.0.0.0
port         = 9090
mdns_enabled = true
config_file  = /home/user/.config/helm/agent.toml

poll intervals (ms):
  cpu         = 1000
  memory      = 2000
  network     = 1000
  disk        = 2000
  temperature = 2000
  battery     = 30000
  process     = 3000
  window      = 500

allowed_commands:
  git_pull
  git_push
  lock

security:
  cert_fingerprint = b72c3a...
  cert_path        = /home/user/.config/helm/cert.pem
  key_path         = /home/user/.config/helm/key.pem
  token_path       = /home/user/.config/helm/token
```

The `security:` section only appears in WiFi mode (`bind_host != "127.0.0.1"`).

---

## Global Flags

| Flag | Description |
|------|-------------|
| `-h`, `--help` | Print help |
| `-V`, `--version` | Print version |

---

## Config File

`~/.config/helm/agent.toml` — created with defaults on first run.

```toml
# Bind address
# "127.0.0.1" = USB only (default)
# "0.0.0.0"   = USB + WiFi
bind_host = "127.0.0.1"
port = 9090

# Advertise via mDNS when in WiFi mode
mdns_enabled = true

# Commands the Android app may trigger
allowed_commands = ["git_pull", "git_push", "lock"]

[poll_intervals]
cpu_ms         = 1000
memory_ms      = 2000
network_ms     = 1000
disk_ms        = 2000
temperature_ms = 2000
battery_ms     = 30000
process_ms     = 3000
window_ms      = 500
```

---

## PID File

Location: `~/.local/share/helm/helm-agent.pid`

Written on `run`, removed on clean shutdown or `stop`. Used by `status`, `stop`, and `restart` to locate the running daemon.

---

## Logging

```bash
# Default (INFO level)
helm-agent run

# Verbose
RUST_LOG=debug helm-agent run

# Via systemd
journalctl --user -u helm-agent -f
journalctl --user -u helm-agent --since "1 hour ago"
```
