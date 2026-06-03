#!/usr/bin/env bash
# Removes HELM components installed by install.sh.
# Idempotent — safe to run multiple times.

set -euo pipefail

PATH_LINE='export PATH="$HOME/.local/bin:$PATH"'
PORT=9090

echo "Uninstalling HELM..."

# 1. Stop + disable systemd service (handle both current and legacy names)
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
export DBUS_SESSION_BUS_ADDRESS="${DBUS_SESSION_BUS_ADDRESS:-unix:path=/run/user/$(id -u)/bus}"
for SVC in helm helm-agent; do
    if systemctl --user is-active --quiet "$SVC" 2>/dev/null; then
        systemctl --user stop "$SVC" 2>/dev/null && echo "  stopped $SVC service" || true
    fi
    systemctl --user disable "$SVC" 2>/dev/null || true
done

# Kill any orphan not tracked by systemd (covers port 9090 current + 8080 legacy).
_kill_port() {
    local port_hex
    port_hex=$(printf '%04X' "$1")
    local inode
    inode=$(awk -v p="$port_hex" \
      'NR>1 && toupper($2) ~ ":"p"$" && $4=="0A" {print $10}' \
      /proc/net/tcp /proc/net/tcp6 2>/dev/null | head -1)
    [ -z "$inode" ] && return
    local pid
    pid=$(grep -rl "socket:\[$inode\]" /proc/*/fd 2>/dev/null | head -1 | cut -d/ -f3)
    [ -z "$pid" ] && return
    echo "  killing orphan pid=$pid on port $1"
    kill "$pid" 2>/dev/null || true
    sleep 1
    kill -9 "$pid" 2>/dev/null || true
}
_kill_port "$PORT"
_kill_port 8080

# 2. Remove systemd units + reload daemon
UNIT_DIR="$HOME/.config/systemd/user"
REMOVED_UNIT=0
for SVC in helm helm-agent; do
    UNIT="$UNIT_DIR/$SVC.service"
    if [ -f "$UNIT" ]; then
        rm -f "$UNIT"
        echo "  removed $UNIT"
        REMOVED_UNIT=1
    fi
done
[ "$REMOVED_UNIT" = "1" ] && systemctl --user daemon-reload

# 3. Remove binaries (current name + legacy name)
for BIN in "$HOME/.local/bin/helm" "$HOME/.local/bin/helm-agent"; do
    if [ -f "$BIN" ]; then
        rm -f "$BIN"
        echo "  removed $BIN"
    fi
done

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
