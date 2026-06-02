#!/usr/bin/env bash
set -e

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BINARY="$REPO/agent/target/release/helm-agent"
SERVICE_DIR="$HOME/.config/systemd/user"
SERVICE_FILE="$SERVICE_DIR/helm-agent.service"
UDEV_RULE="/etc/udev/rules.d/99-helm-adb.rules"
PORT=9090

echo "==> Building HELM agent (release)..."
cd "$REPO/agent"
cargo build --release

echo "==> Installing systemd user service..."
mkdir -p "$SERVICE_DIR"
cat > "$SERVICE_FILE" <<EOF
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
systemctl --user enable helm-agent
systemctl --user restart helm-agent

echo "==> Installing udev rule for ADB auto-reverse (requires sudo)..."
echo "ACTION==\"add\", SUBSYSTEM==\"usb\", ENV{DEVTYPE}==\"usb_device\", RUN+=\"/usr/bin/adb reverse tcp:$PORT tcp:$PORT\"" \
  | sudo tee "$UDEV_RULE" > /dev/null
sudo udevadm control --reload-rules

echo ""
echo "==> Done."
echo "    Agent:  systemctl --user status helm-agent"
echo "    Port:   $PORT (adb reverse tcp:$PORT tcp:$PORT)"
echo "    Logs:   journalctl --user -u helm-agent -f"
