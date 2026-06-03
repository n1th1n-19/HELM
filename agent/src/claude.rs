//! Claude Code collector: reads session state written by the Claude Code hook script.
//!
//! The hook script writes `~/.local/share/helm/claude_state.json` on each Claude Code
//! event (UserPromptSubmit, PreToolUse, PostToolUse, Stop). This collector watches that
//! file via `notify` for instant updates, with a 5s poll fallback that also keeps the
//! `session_duration_secs` field incrementing live.

use crate::config::HelmConfig;
use crate::protocol::ClaudeUpdate;
use crate::state::{SharedState, StateTx};
use notify::{Config as NotifyConfig, RecommendedWatcher, RecursiveMode, Watcher};
use serde::Deserialize;
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use tokio::sync::mpsc;
use tokio::time;
use tracing::{debug, warn};

/// Raw state file schema — superset of `ClaudeUpdate` (includes fields not sent over wire).
#[derive(Deserialize, Default)]
struct ClaudeStateFile {
    status: Option<String>,
    task: Option<String>,
    current_file: Option<String>,
    #[allow(dead_code)]
    session_id: Option<String>,
    session_start_ms: Option<u64>,
    tokens_used: Option<u64>,
    tokens_max: Option<u64>,
    context_percent: Option<f32>,
}

fn state_file_path() -> PathBuf {
    dirs::data_local_dir()
        .unwrap_or_else(|| {
            dirs::home_dir()
                .map(|h| h.join(".local").join("share"))
                .unwrap_or_else(|| PathBuf::from("/tmp/helm"))
        })
        .join("helm")
        .join("claude_state.json")
}

fn read_state_file(path: &Path) -> Option<ClaudeStateFile> {
    let text = std::fs::read_to_string(path).ok()?;
    serde_json::from_str(&text).ok()
}

fn file_to_update(f: &ClaudeStateFile) -> ClaudeUpdate {
    let session_duration_secs = f.session_start_ms.map(|start_ms| {
        let now_ms = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as u64;
        now_ms.saturating_sub(start_ms) / 1000
    });

    ClaudeUpdate {
        status: f.status.clone(),
        task: f.task.clone(),
        current_file: f.current_file.clone(),
        session_duration_secs,
        tokens_used: f.tokens_used,
        tokens_max: f.tokens_max,
        context_percent: f.context_percent,
    }
}

async fn update_state(state: &SharedState, tx: &StateTx, path: &Path) {
    let update = match read_state_file(path) {
        Some(f) => file_to_update(&f),
        None => ClaudeUpdate::default(),
    };
    state.write().await.claude = update;
    let _ = tx.send(());
}

fn make_watcher(
    watch_dir: &Path,
    state_file_name: &str,
    tx: mpsc::UnboundedSender<()>,
) -> anyhow::Result<RecommendedWatcher> {
    let name = state_file_name.to_string();
    let mut watcher = RecommendedWatcher::new(
        move |res: notify::Result<notify::Event>| {
            if let Ok(event) = res {
                let relevant = event.paths.iter().any(|p| {
                    p.file_name()
                        .and_then(|n| n.to_str())
                        .map(|n| n == name || n == format!("{}.tmp", name))
                        .unwrap_or(false)
                });
                if relevant {
                    let _ = tx.send(());
                }
            }
        },
        NotifyConfig::default(),
    )?;
    watcher.watch(watch_dir, RecursiveMode::NonRecursive)?;
    Ok(watcher)
}

pub async fn run(state: SharedState, tx: StateTx, _cfg: HelmConfig) {
    let state_path = state_file_path();
    let watch_dir = state_path.parent().map(PathBuf::from).unwrap_or_else(|| {
        dirs::data_local_dir()
            .unwrap_or_else(|| {
                dirs::home_dir()
                    .map(|h| h.join(".local").join("share"))
                    .unwrap_or_else(|| PathBuf::from("/tmp/helm"))
            })
            .join("helm")
    });

    // Ensure the directory exists so we can watch it even before the hook runs.
    if let Err(e) = std::fs::create_dir_all(&watch_dir) {
        warn!("claude: could not create state dir {}: {e}", watch_dir.display());
    }

    let state_file_name = state_path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("claude_state.json")
        .to_string();

    let (evt_tx, mut evt_rx) = mpsc::unbounded_channel();
    let _watcher = match make_watcher(&watch_dir, &state_file_name, evt_tx) {
        Ok(w) => {
            debug!("claude: watching {}", watch_dir.display());
            Some(w)
        }
        Err(e) => {
            warn!("claude: failed to watch {}: {e}", watch_dir.display());
            None
        }
    };

    // 5s poll: fallback reliability + keeps session_duration_secs incrementing.
    let mut ticker = time::interval(Duration::from_secs(5));
    ticker.set_missed_tick_behavior(time::MissedTickBehavior::Skip);

    // Initial read.
    update_state(&state, &tx, &state_path).await;

    loop {
        tokio::select! {
            _ = ticker.tick() => {
                update_state(&state, &tx, &state_path).await;
            }
            Some(()) = evt_rx.recv() => {
                // Drain burst events (rename + create fire together on atomic write).
                while evt_rx.try_recv().is_ok() {}
                update_state(&state, &tx, &state_path).await;
            }
        }
    }
}
