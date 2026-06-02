#!/usr/bin/env bash
set -e

REPO="n1th1n-19/HELM"
PORT=9090
INSTALL_DIR="$HOME/.local/bin"
SERVICE_DIR="$HOME/.config/systemd/user"

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

# ── Download binary ───────────────────────────────────────────────────────────
mkdir -p "$INSTALL_DIR"
BINARY="$INSTALL_DIR/helm"
URL="https://github.com/$REPO/releases/download/$LATEST/$ARTIFACT"

echo "==> Downloading helm ($ARCH)..."
curl -fsSL "$URL" -o "$BINARY"
chmod +x "$BINARY"
echo "    Installed to $BINARY"

# ── Ensure ~/.local/bin is in PATH ────────────────────────────────────────────
if ! echo "$PATH" | grep -q "$HOME/.local/bin"; then
  echo "    Adding ~/.local/bin to PATH in ~/.bashrc and ~/.profile"
  echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$HOME/.bashrc"
  echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$HOME/.profile"
fi

# ── Systemd user service ──────────────────────────────────────────────────────
echo "==> Setting up systemd user service..."
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

# curl | bash strips D-Bus env vars; restore them so systemctl --user works
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
export DBUS_SESSION_BUS_ADDRESS="${DBUS_SESSION_BUS_ADDRESS:-unix:path=/run/user/$(id -u)/bus}"

systemctl --user daemon-reload
systemctl --user enable helm
systemctl --user restart helm || systemctl --user start helm || true

# ── Udev rule for ADB auto-reverse ────────────────────────────────────────────
echo "==> Installing udev rule for ADB auto-reverse (requires sudo)..."
echo "ACTION==\"add\", SUBSYSTEM==\"usb\", ENV{DEVTYPE}==\"usb_device\", RUN+=\"/usr/bin/adb reverse tcp:$PORT tcp:$PORT\"" \
  | sudo tee /etc/udev/rules.d/99-helm-adb.rules > /dev/null
sudo udevadm control --reload-rules

# ── Firewall: open agent port for WiFi connectivity ───────────────────────────
if systemctl is-active --quiet ufw 2>/dev/null; then
  echo "==> Opening port $PORT in UFW for WiFi connectivity..."
  sudo ufw allow "$PORT/tcp" > /dev/null
  sudo ufw reload > /dev/null
  echo "    ufw: port $PORT/tcp allowed"
elif systemctl is-active --quiet firewalld 2>/dev/null; then
  echo "==> Opening port $PORT in firewalld for WiFi connectivity..."
  sudo firewall-cmd --permanent --add-port="$PORT/tcp" > /dev/null
  sudo firewall-cmd --reload > /dev/null
  echo "    firewalld: port $PORT/tcp allowed"
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
