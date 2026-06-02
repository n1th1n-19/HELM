#!/usr/bin/env bash
# Removes HELM components installed by install.sh.
# Idempotent — safe to run multiple times.

set -euo pipefail

PATH_LINE='export PATH="$HOME/.local/bin:$PATH"'

echo "Uninstalling HELM..."

# 1. Stop + disable systemd service
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
export DBUS_SESSION_BUS_ADDRESS="${DBUS_SESSION_BUS_ADDRESS:-unix:path=/run/user/$(id -u)/bus}"
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

# 6. Remove firewall rule (ufw / firewalld / iptables)
PORT=9090
if systemctl is-active --quiet ufw 2>/dev/null; then
    sudo ufw delete allow "$PORT/tcp" > /dev/null 2>&1 || true
    sudo ufw reload > /dev/null 2>&1 || true
    echo "  removed ufw rule for port $PORT"
elif systemctl is-active --quiet firewalld 2>/dev/null; then
    sudo firewall-cmd --permanent --remove-port="$PORT/tcp" > /dev/null 2>&1 || true
    sudo firewall-cmd --reload > /dev/null 2>&1 || true
    echo "  removed firewalld rule for port $PORT"
fi

# 7. Remove PATH entries added by install.sh (all shells)
for RC in "$HOME/.bashrc" "$HOME/.profile" "$HOME/.zshrc"; do
    if [ -f "$RC" ] && grep -qF "$PATH_LINE" "$RC" 2>/dev/null; then
        grep -vF "$PATH_LINE" "$RC" > "$RC.tmp" && mv "$RC.tmp" "$RC"
        echo "  removed PATH entry from $RC"
    fi
done
FISH_PATH="$HOME/.config/fish/conf.d/helm-path.fish"
if [ -f "$FISH_PATH" ]; then
    rm -f "$FISH_PATH"
    echo "  removed $FISH_PATH"
fi

echo ""
echo "Done. HELM uninstalled."
