//! Workspace collector: detects the active VS Code workspace.
//!
//! Reads VS Code's `globalStorage/storage.json` to find the most recently
//! active folder, supporting Code, VSCodium, and Code - OSS. Polls every 3s
//! and writes the result into `state.vscode` so `git.rs` can pick it up.

use crate::config::HelmConfig;
use crate::protocol::VscodeUpdate;
use crate::state::{SharedState, StateTx};
use serde_json::Value;
use std::path::Path;
use std::time::Duration;
use tokio::time;

/// Percent-decode a URI path component (handles all `%XX` sequences).
fn percent_decode(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let bytes = s.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            if let Ok(hex_str) = std::str::from_utf8(&bytes[i + 1..i + 3]) {
                if let Ok(byte) = u8::from_str_radix(hex_str, 16) {
                    out.push(byte as char);
                    i += 3;
                    continue;
                }
            }
        }
        out.push(bytes[i] as char);
        i += 1;
    }
    out
}

/// Decode a `file://` URI into a filesystem path, handling `file:///path`,
/// `file://localhost/path`, and arbitrary percent-encoded characters.
fn uri_to_path(uri: &str) -> Option<String> {
    let rest = uri.strip_prefix("file://")?;
    let path_str = if rest.starts_with('/') {
        // file:///path
        rest
    } else if let Some(after_host) = rest.strip_prefix("localhost") {
        // file://localhost/path
        after_host
    } else {
        return None;
    };
    Some(percent_decode(path_str))
}

fn project_name_of(path: &str) -> Option<String> {
    Path::new(path)
        .file_name()
        .and_then(|n| n.to_str())
        .map(|s| s.to_string())
}

fn get_vscode_workspace() -> Option<VscodeUpdate> {
    let home = dirs::home_dir()?;
    let storage_paths = [
        home.join(".config/Code/User/globalStorage/storage.json"),
        home.join(".config/VSCodium/User/globalStorage/storage.json"),
        home.join(".config/Code - OSS/User/globalStorage/storage.json"),
    ];

    for storage_path in &storage_paths {
        let content = match std::fs::read_to_string(storage_path) {
            Ok(c) => c,
            Err(_) => continue,
        };
        let json: Value = match serde_json::from_str(&content) {
            Ok(j) => j,
            Err(_) => continue,
        };

        // Prefer the explicit lastActiveFolder key when present.
        let folder = json
            .get("lastActiveFolder")
            .and_then(|v| v.as_str())
            // Fall back to the active window's workspace folder URI.
            .or_else(|| {
                json.pointer("/windowsState/lastActiveWindow/folder")
                    .and_then(|v| v.as_str())
            });

        if let Some(folder) = folder {
            if let Some(path) = uri_to_path(folder) {
                let project_name = project_name_of(&path);
                return Some(VscodeUpdate {
                    workspace_path: Some(path),
                    project_name,
                    active_file: None,
                    branch: None,
                });
            }
        }
    }
    None
}

pub async fn run(state: SharedState, tx: StateTx, _cfg: HelmConfig) {
    let mut ticker = time::interval(Duration::from_secs(3));
    ticker.set_missed_tick_behavior(time::MissedTickBehavior::Skip);

    let mut last: Option<String> = None;

    loop {
        ticker.tick().await;
        let update = get_vscode_workspace().unwrap_or_default();

        // Only write/signal when the active workspace path actually changed.
        if update.workspace_path != last {
            last = update.workspace_path.clone();
            {
                let mut s = state.write().await;
                s.vscode = update;
            }
            let _ = tx.send(());
        }
    }
}
