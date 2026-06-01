# HELM Protocol

The WebSocket protocol between the HELM desktop agent and Android app.

## Transport

WebSocket on `ws://localhost:8080/helm` via ADB port forwarding (`adb forward tcp:8080 tcp:8080`).

## Message Format

Every message follows the envelope schema:

```json
{
  "type": "<message_type>",
  "ts": 1717200000000,
  "payload": { ... }
}
```

`ts` is Unix timestamp in milliseconds.

## Delta Model

On connect, the agent sends:
1. A `system_info` message (full static snapshot)
2. Full snapshots of all current state (system_update, git_update, music_update, window_update, vscode_update, process_update)

After that, only changed fields are sent. Fields absent from a delta message retain their last known value on the client.

## Message Types

| Type | Direction | Description |
|------|-----------|-------------|
| `system_update` | agent → android | CPU, RAM, temp, network, disk, battery, uptime |
| `git_update` | agent → android | Git repo state, branch, status, commits |
| `music_update` | agent → android | MPRIS2 music player state |
| `window_update` | agent → android | Active window and workspace (X11 only) |
| `vscode_update` | agent → android | VS Code active workspace |
| `process_update` | agent → android | Top processes by CPU % |
| `system_info` | agent → android | Static system info (sent once on connect) |
| `command` | android → agent | Execute an action on the workstation |
| `command_ack` | agent → android | Result of a command |
| `ping` / `pong` | both | Keepalive |

## Schema Files

See `schema/` directory for full JSON Schema definitions of each payload type.
