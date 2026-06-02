#!/usr/bin/env bash
# Removes HELM agent components installed by install.sh.
# Idempotent — safe to run multiple times.

set -euo pipefail

echo "Uninstalling HELM agent..."

# 1. Stop + disable systemd service
systemctl --user stop helm 2>/dev/null && echo "  stopped helm service" || true
systemctl --user disable helm 2>/dev/null || true

# 2. Remove systemd unit + reload daemon
UNIT="$HOME/.config/systemd/user/helm.service"
if [ -f "$UNIT" ]; then
    rm -f "$UNIT"
    systemctl --user daemon-reload
    echo "  removed $UNIT"
fi

# 3. Remove binary
BIN="$HOME/.local/bin/helm"
if [ -f "$BIN" ]; then
    rm -f "$BIN"
    echo "  removed $BIN"
fi

# 4. Remove config dir (cert.pem, key.pem, token, agent.toml)
CONFIG_DIR="$HOME/.config/helm"
if [ -d "$CONFIG_DIR" ]; then
    rm -rf "$CONFIG_DIR"
    echo "  removed $CONFIG_DIR"
fi

# 5. Remove udev rule + reload (requires sudo; non-fatal if absent)
UDEV_RULE="/etc/udev/rules.d/99-helm-adb.rules"
if [ -f "$UDEV_RULE" ]; then
    sudo rm -f "$UDEV_RULE"
    sudo udevadm control --reload-rules 2>/dev/null || true
    echo "  removed $UDEV_RULE"
fi

# 6. Remove firewall rule (ufw or firewalld; skip if neither active)
if systemctl is-active --quiet ufw 2>/dev/null; then
    sudo ufw delete allow 9090/tcp > /dev/null 2>&1 || true
    sudo ufw reload > /dev/null 2>&1 || true
    echo "  removed ufw rule for port 9090"
elif systemctl is-active --quiet firewalld 2>/dev/null; then
    sudo firewall-cmd --permanent --remove-port=9090/tcp > /dev/null 2>&1 || true
    sudo firewall-cmd --reload > /dev/null 2>&1 || true
    echo "  removed firewalld rule for port 9090"
fi

echo "Done. HELM agent uninstalled."
echo "Note: PATH exports in .bashrc/.profile were not removed."
