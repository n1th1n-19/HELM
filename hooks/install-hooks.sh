#!/usr/bin/env bash
# Installs HELM Claude Code hooks into ~/.claude/settings.json.
# Idempotent — safe to run multiple times.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOOK_BIN="$SCRIPT_DIR/helm-claude-hook"
SETTINGS="$HOME/.claude/settings.json"

if [[ ! -f "$HOOK_BIN" ]]; then
    echo "Error: hook script not found at $HOOK_BIN" >&2
    exit 1
fi

chmod +x "$HOOK_BIN"

# Create settings file if it doesn't exist.
mkdir -p "$(dirname "$SETTINGS")"
if [[ ! -f "$SETTINGS" ]]; then
    echo '{}' > "$SETTINGS"
fi

# Use Python to merge hooks idempotently (no jq required).
python3 - "$SETTINGS" "$HOOK_BIN" <<'PYEOF'
import json, sys, pathlib

settings_path = pathlib.Path(sys.argv[1])
hook_cmd = sys.argv[2]

settings = json.loads(settings_path.read_text())
hooks = settings.setdefault("hooks", {})

EVENTS = ["UserPromptSubmit", "PreToolUse", "PostToolUse", "Stop", "SubagentStop"]

for event in EVENTS:
    entries = hooks.setdefault(event, [])
    # Check if our hook command is already present anywhere in this event's entries.
    already_present = any(
        h.get("command") == hook_cmd
        for entry in entries
        for h in entry.get("hooks", [])
    )
    if not already_present:
        entries.append({
            "hooks": [{"type": "command", "command": hook_cmd}]
        })

tmp = settings_path.with_suffix(".tmp")
tmp.write_text(json.dumps(settings, indent=2))
tmp.rename(settings_path)
print(f"Hooks installed in {settings_path}")
PYEOF
