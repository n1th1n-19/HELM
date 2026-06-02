<p align="center">
  <img src="../assets/helm.png" alt="HELM" width="140"/>
</p>

# HELM Setup Guide

## Quick Install (Recommended)

```bash
curl -fsSL https://raw.githubusercontent.com/n1th1n-19/HELM/main/install.sh | bash
```

Handles everything: binary, systemd service, ADB auto-reverse, firewall rule. Skip to [Android App](#android-app) when done.

---

## Manual Setup

### Prerequisites

**Workstation:**
- Linux (Arch, Ubuntu, Fedora tested)
- Rust 1.75+ — `curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh`
- ADB — `sudo pacman -S android-tools` / `sudo apt install adb`
- Optional: `xdotool`, `playerctl`, `fuser`

**Android device:**
- Android 10+ (API 29+)
- USB debugging enabled (Settings → Developer Options → USB Debugging)

---

### Building the Desktop Agent

```bash
cd agent
cargo build --release
```

Binary: `agent/target/release/helm-agent`

### CLI Commands

```
helm-agent run      # start agent
helm-agent status   # check if running
helm-agent stop     # stop agent
helm-agent restart  # stop + start
helm-agent qr       # print WiFi pairing QR
helm-agent config   # show current config
```

### Autostart with systemd

```bash
mkdir -p ~/.config/systemd/user
cat > ~/.config/systemd/user/helm-agent.service <<EOF
[Unit]
Description=HELM Desktop Agent
After=graphical-session.target

[Service]
Type=simple
ExecStart=$HOME/.local/bin/helm-agent
Restart=on-failure
RestartSec=5s

[Install]
WantedBy=default.target
EOF

systemctl --user daemon-reload
systemctl --user enable --now helm-agent
```

---

## Connection Modes

### USB Mode (default, zero-config)

The agent binds to `127.0.0.1:9090`. The Android app connects to `localhost:9090` on the device, which ADB tunnels over USB to the desktop.

The agent automatically maintains the ADB reverse tunnel — just plug in your device:

```bash
# Manual setup if needed:
adb reverse tcp:9090 tcp:9090
```

> **Note:** Use `adb reverse`, not `adb forward`. Reverse tunnels device→host; forward tunnels host→device.

**Auto-reverse on connect (installed by the installer):**

`/etc/udev/rules.d/99-helm-adb.rules`:
```
ACTION=="add", SUBSYSTEM=="usb", ENV{DEVTYPE}=="usb_device", RUN+="/usr/bin/adb reverse tcp:9090 tcp:9090"
```

### WiFi Mode

1. Edit `~/.config/helm/agent.toml`:
   ```toml
   bind_host = "0.0.0.0"
   port = 9090
   ```

2. Restart the agent — a QR code and pairing URL print on startup:
   ```
   HELM WiFi pairing — scan with Android app:
   [QR code]
   helm://192.168.1.x:9090
   ```
   Or print the QR any time without restarting:
   ```bash
   helm-agent qr
   ```

3. On Android: **Settings tab → Scan QR** → auto-connects.

4. Alternative — manual entry or LAN discovery:
   - Settings tab → WiFi → enter host + port → Save
   - Settings tab → Discover → tap the agent when found

**Firewall:** The installer opens the port automatically. Manually:
```bash
# UFW
sudo ufw allow 9090/tcp && sudo ufw reload

# firewalld
sudo firewall-cmd --permanent --add-port=9090/tcp && sudo firewall-cmd --reload
```

---

## Android App

### Install from release

```bash
adb install helm-app.apk
```

Download the latest APK from [github.com/n1th1n-19/HELM/releases/latest](https://github.com/n1th1n-19/HELM/releases/latest).

### Build from source

Requirements: JDK 17+, Android SDK platform 35

```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Kiosk Mode

HELM runs fullscreen by default: no status bar, no nav bar, landscape lock, screen always on. Mount your device beside your monitor.

To disable kiosk mode, modify `KioskManager.kt` (settings toggle planned for a future release).

---

## Troubleshooting

**Agent won't start / port in use:**
```bash
helm-agent status        # check if already running
helm-agent stop          # stop it
helm-agent run           # start fresh
```

**Android shows "Disconnected" (USB mode):**
- Verify USB debugging is enabled on the Android device
- Check ADB sees the device: `adb devices`
- The agent maintains `adb reverse` automatically — check: `adb reverse --list`
- Try unplugging and replugging (auto-reverse restores within 3 seconds)

**Android can't connect (WiFi mode):**
- Confirm both devices are on the same LAN
- Run `helm-agent qr` — verify the IP matches your desktop's LAN IP
- Check firewall: `sudo ufw status` — port 9090 must be allowed
- Settings tab shows target URL and last error message

**No music data:**
- Verify player supports MPRIS2: `playerctl status`
- Check D-Bus session: `echo $DBUS_SESSION_BUS_ADDRESS`

**No window/workspace data:**
- Install `xdotool`: `sudo pacman -S xdotool`
- Wayland: window detection not supported in V1

**No temperature data:**
- Not all hardware exposes sensors: `cat /sys/class/thermal/thermal_zone*/temp`

**Verbose logs:**
```bash
RUST_LOG=debug helm-agent run
# Or for systemd:
journalctl --user -u helm-agent -f
```
