# HELM Setup Guide

## Prerequisites

### Workstation

- Linux (tested on Arch, Ubuntu, Fedora)
- Rust 1.75+ (`curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh`)
- ADB (`sudo pacman -S android-tools` / `sudo apt install adb`)
- Optional: `xdotool`, `playerctl`, `fuser`

### Android Device

- Android 10+
- USB debugging enabled (Settings → Developer Options → USB Debugging)

---

## Building the Desktop Agent

```bash
cd agent
cargo build --release
```

The binary will be at `agent/target/release/helm-agent`.

### Running the agent

```bash
./target/release/helm-agent
```

The agent will log to stdout. By default it binds to `127.0.0.1:8080`.

### Autostart with systemd

Create `~/.config/systemd/user/helm-agent.service`:

```ini
[Unit]
Description=HELM Desktop Agent
After=graphical-session.target

[Service]
Type=simple
ExecStart=%h/path/to/helm-agent
Restart=on-failure
RestartSec=5s

[Install]
WantedBy=default.target
```

Enable and start:
```bash
systemctl --user enable helm-agent
systemctl --user start helm-agent
```

---

## Setting Up ADB Reverse Tunnel

The Android app connects to `localhost:9090` on the device. ADB **reverse** tunneling forwards this to port 9090 on your workstation (where the agent runs).

> **Important:** Use `adb reverse`, not `adb forward`. The Android app connects *to* the workstation, so reverse tunneling is required.

```bash
adb reverse tcp:9090 tcp:9090
adb reverse --list  # verify it shows
```

This command needs to be run each time the device is connected. You can automate it with a udev rule.

### Automatic reverse tunnel with udev (optional)

Create `/etc/udev/rules.d/99-helm-adb.rules`:
```
ACTION=="add", SUBSYSTEM=="usb", RUN+="/usr/bin/adb reverse tcp:9090 tcp:9090"
```

---

## Building the Android App

Requirements: Android Studio or `sdkmanager` with:
- Android SDK platform 35
- Build tools 35.0.0
- JDK 17+

```bash
cd android
./gradlew assembleDebug
```

Install on device:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Kiosk Mode

HELM is designed to run in kiosk mode on a dedicated device mounted beside your monitor.

The app automatically:
- Hides the status bar and navigation bar
- Keeps the screen on
- Locks to landscape orientation

To disable kiosk mode, you can modify `KioskManager.kt` or add a settings toggle (coming in a future release).

### Recommended mounting

A cheap phone stand or arm mount works well. The device should be in landscape orientation at eye level beside your monitor.

---

## Troubleshooting

**Agent won't start:**
- Check `~/.config/helm/agent.toml` for syntax errors
- Try `RUST_LOG=debug ./helm-agent` for verbose logging

**Android shows "Disconnected":**
- Verify `adb forward tcp:8080 tcp:8080` has been run
- Verify the agent is running (`ps aux | grep helm-agent`)
- Try disconnecting and reconnecting the USB cable

**No music data:**
- Ensure your player supports MPRIS2 (`playerctl status` should work)
- Check D-Bus session is available (`echo $DBUS_SESSION_BUS_ADDRESS`)

**No window/workspace data:**
- Install `xdotool` (`sudo pacman -S xdotool`)
- Note: Wayland is not supported for window detection in V1

**No temperature data:**
- Not all hardware exposes temperature sensors
- Try `cat /sys/class/thermal/thermal_zone*/temp`
