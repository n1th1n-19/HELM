#!/usr/bin/env bash
set -euo pipefail

REPO="n1th1n-19/HELM"
PORT=9090
INSTALL_DIR="$HOME/.local/bin"
SERVICE_DIR="$HOME/.config/systemd/user"
CONFIG_DIR="$HOME/.config/helm"
CONFIG_FILE="$CONFIG_DIR/agent.toml"

# ── Detect arch ───────────────────────────────────────────────────────────────
ARCH=$(uname -m)
case "$ARCH" in
  x86_64)  ARTIFACT="helm-linux-x86_64" ;;
  aarch64) ARTIFACT="helm-linux-aarch64" ;;
  *)
    echo "Unsupported architecture: $ARCH"
    exit 1
    ;;
esac

# ── Get latest release tag ────────────────────────────────────────────────────
echo "==> Fetching latest HELM release..."
LATEST=$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" \
  | grep '"tag_name"' | cut -d'"' -f4)

if [ -z "$LATEST" ]; then
  echo "Error: could not fetch latest release. Check your internet connection."
  exit 1
fi

echo "    Version: $LATEST"

# ── Runtime dependencies ──────────────────────────────────────────────────────
if [ "$ARCH" = "x86_64" ]; then
  echo "==> Installing runtime dependencies..."
  if command -v pacman &>/dev/null; then
    sudo pacman -S --needed --noconfirm xdotool libayatana-appindicator 2>/dev/null || \
    sudo pacman -S --needed --noconfirm xdotool 2>/dev/null || true
  elif command -v apt-get &>/dev/null; then
    sudo apt-get update -qq
    sudo apt-get install -y xdotool libayatana-appindicator3-1 2>/dev/null || \
    sudo apt-get install -y xdotool 2>/dev/null || true
  elif command -v dnf &>/dev/null; then
    sudo dnf install -y xdotool 2>/dev/null || true
  elif command -v zypper &>/dev/null; then
    sudo zypper install -y xdotool 2>/dev/null || true
  elif command -v apk &>/dev/null; then
    sudo apk add --no-cache xdotool 2>/dev/null || true
  fi

  # Binary links against libxdo.so.3; some distros ship libxdo.so.4 — create compat symlink
  for lib_dir in /usr/lib /usr/lib64 /usr/lib/x86_64-linux-gnu; do
    if [ -f "$lib_dir/libxdo.so.4" ] && [ ! -f "$lib_dir/libxdo.so.3" ]; then
      sudo ln -sf "$lib_dir/libxdo.so.4" "$lib_dir/libxdo.so.3" || true
    fi
  done
fi

# ── Stop any running instance before replacing the binary ────────────────────
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
export DBUS_SESSION_BUS_ADDRESS="${DBUS_SESSION_BUS_ADDRESS:-unix:path=/run/user/$(id -u)/bus}"
echo "==> Stopping existing HELM agent (if running)..."
for SVC in helm helm-agent; do
  if systemctl --user is-active --quiet "$SVC" 2>/dev/null; then
    systemctl --user stop "$SVC" 2>/dev/null && echo "  stopped $SVC" || true
    systemctl --user disable "$SVC" 2>/dev/null || true
  fi
done
# Kill any orphan not tracked by systemd (stale PID, manual run, etc.)
# /proc/net/tcp uses little-endian hex port; 0A = TCP_LISTEN
PORT_HEX=$(printf '%04X' "$PORT")
INODE=$(awk -v p="$PORT_HEX" 'NR>1 && toupper($2) ~ ":"p"$" && $4=="0A" {print $10}' \
  /proc/net/tcp /proc/net/tcp6 2>/dev/null | head -1)
if [ -n "$INODE" ]; then
  ORPHAN_PID=$(grep -rl "socket:\[$INODE\]" /proc/*/fd 2>/dev/null | head -1 | cut -d/ -f3)
  if [ -n "$ORPHAN_PID" ]; then
    echo "  killing orphan pid=$ORPHAN_PID holding port $PORT"
    kill "$ORPHAN_PID" 2>/dev/null || true
    sleep 1
    kill -9 "$ORPHAN_PID" 2>/dev/null || true
  fi
fi
# Remove old service units and binaries so stale artifacts don't linger
for SVC in helm helm-agent; do
  UNIT="$HOME/.config/systemd/user/$SVC.service"
  [ -f "$UNIT" ] && rm -f "$UNIT" && echo "  removed $UNIT"
done
for OLD_BIN in "$INSTALL_DIR/helm" "$INSTALL_DIR/helm-agent"; do
  [ -f "$OLD_BIN" ] && rm -f "$OLD_BIN" && echo "  removed $OLD_BIN"
done

# ── Write default config (skip if already exists) ─────────────────────────────
if [ ! -f "$CONFIG_FILE" ]; then
  echo "==> Writing default config..."
  mkdir -p "$CONFIG_DIR"
  cat > "$CONFIG_FILE" <<EOF
bind_host = "0.0.0.0"
port = $PORT
EOF
fi

# ── Download binary ───────────────────────────────────────────────────────────
mkdir -p "$INSTALL_DIR"
BINARY="$INSTALL_DIR/helm"
URL="https://github.com/$REPO/releases/download/$LATEST/$ARTIFACT"

echo "==> Downloading helm ($ARCH)..."
curl -fsSL "$URL" -o "$BINARY"
chmod +x "$BINARY"
echo "    Installed to $BINARY"

# ── Ensure ~/.local/bin is in PATH (all shells) ───────────────────────────────
PATH_LINE='export PATH="$HOME/.local/bin:$PATH"'
PATH_ADDED=0
if ! echo "$PATH" | grep -q "$HOME/.local/bin"; then
  PATH_ADDED=1
  echo "    Configuring PATH..."

  grep -qxF "$PATH_LINE" "$HOME/.bashrc"  2>/dev/null || echo "$PATH_LINE" >> "$HOME/.bashrc"
  grep -qxF "$PATH_LINE" "$HOME/.profile" 2>/dev/null || echo "$PATH_LINE" >> "$HOME/.profile"
  grep -qxF "$PATH_LINE" "$HOME/.zshrc"   2>/dev/null || echo "$PATH_LINE" >> "$HOME/.zshrc" || true

  if command -v fish &>/dev/null; then
    mkdir -p "$HOME/.config/fish/conf.d"
    echo 'fish_add_path $HOME/.local/bin' > "$HOME/.config/fish/conf.d/helm-path.fish"
  fi
fi

# ── Systemd user service ──────────────────────────────────────────────────────
if command -v systemctl &>/dev/null && systemctl --user daemon-reload &>/dev/null 2>&1; then
  echo "==> Installing systemd user service..."
  mkdir -p "$SERVICE_DIR"
  cat > "$SERVICE_DIR/helm.service" <<EOF
[Unit]
Description=HELM Desktop Agent
After=graphical-session.target

[Service]
Type=simple
ExecStart=$BINARY
Restart=on-failure
RestartSec=5s

[Install]
WantedBy=default.target
EOF
  systemctl --user daemon-reload
  systemctl --user enable helm
  systemctl --user restart helm 2>/dev/null || systemctl --user start helm 2>/dev/null || true
else
  echo "    systemd not available — start manually: helm"
fi

# ── Udev rule for ADB auto-reverse ────────────────────────────────────────────
ADB_BIN=$(command -v adb 2>/dev/null || echo "/usr/bin/adb")
if [ -d /etc/udev/rules.d ]; then
  echo "==> Installing udev rule for ADB auto-reverse..."
  echo "ACTION==\"add\", SUBSYSTEM==\"usb\", ENV{DEVTYPE}==\"usb_device\", RUN+=\"$ADB_BIN reverse tcp:$PORT tcp:$PORT\"" \
    | sudo tee /etc/udev/rules.d/99-helm-adb.rules > /dev/null
  sudo udevadm control --reload-rules 2>/dev/null || true
fi

# ── Firewall: open agent port for WiFi connectivity ───────────────────────────
if systemctl is-active --quiet ufw 2>/dev/null; then
  echo "==> Opening port $PORT in UFW..."
  sudo ufw allow "$PORT/tcp" > /dev/null && sudo ufw reload > /dev/null || true
elif systemctl is-active --quiet firewalld 2>/dev/null; then
  echo "==> Opening port $PORT in firewalld..."
  sudo firewall-cmd --permanent --add-port="$PORT/tcp" > /dev/null && sudo firewall-cmd --reload > /dev/null || true
fi

# ── Done ──────────────────────────────────────────────────────────────────────
echo ""
echo "╔════════════════════════════════════════╗"
echo "║  HELM installed successfully           ║"
echo "╚════════════════════════════════════════╝"
echo ""
echo "  Agent:   $BINARY"
echo "  Version: $LATEST"
echo "  Port:    $PORT"
echo ""
echo "  Status:  systemctl --user status helm"
echo "  Logs:    journalctl --user -u helm -f"
echo ""
echo "  Android: adb reverse tcp:$PORT tcp:$PORT"
echo "           (runs automatically on USB connect)"
echo ""
echo "  Install Android APK from:"
echo "  https://github.com/$REPO/releases/latest"
if [ "$PATH_ADDED" = "1" ]; then
  echo ""
  echo "  PATH updated — open a new terminal or run:"
  echo "    bash/zsh:  source ~/.bashrc"
  echo "    fish:      exec fish"
fi
