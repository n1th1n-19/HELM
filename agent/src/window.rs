//! Window collector: active window title/class and workspace via xdotool (X11).
//!
//! Polls `xdotool` subprocesses. On Wayland or when xdotool is missing the
//! commands fail; we then publish an all-None `WindowUpdate` and keep polling.
//! State is only updated when the value actually changes, to avoid flooding tx.

use crate::config::HelmConfig;
use crate::protocol::WindowUpdate;
use crate::state::{SharedState, StateTx};
use std::time::Duration;
use tokio::time;

async fn get_active_window() -> Option<WindowUpdate> {
    // Single atomic xdotool invocation to avoid mixing title/class from different
    // windows if the active window changes between two separate calls.
    // Output: window-id\ntitle\nclassname\n
    let output = tokio::process::Command::new("xdotool")
        .args(["getactivewindow", "getwindowname", "getwindowclassname"])
        .output()
        .await
        .ok()?;
    if !output.status.success() {
        return None; // Wayland, no active window, or xdotool not installed.
    }
    let text = String::from_utf8_lossy(&output.stdout);
    let mut lines = text.lines();
    let _window_id = lines.next()?;
    let window_title = lines.next()?.to_string();
    let app_name = lines.next().unwrap_or("").to_string();

    // Current desktop / workspace number (separate call is fine — it has no
    // per-window ambiguity).
    let desktop_output = tokio::process::Command::new("xdotool")
        .args(["get_desktop"])
        .output()
        .await
        .ok()?;
    let workspace_num = String::from_utf8_lossy(&desktop_output.stdout)
        .trim()
        .parse::<i32>()
        .ok();

    Some(WindowUpdate {
        app_name: if app_name.is_empty() {
            None
        } else {
            Some(app_name)
        },
        window_title: if window_title.is_empty() {
            None
        } else {
            Some(window_title)
        },
        // KDE workspace names require a kwinscript — deferred to V2.
        workspace_name: None,
        workspace_num,
    })
}

/// Compare the salient fields of two updates for change detection.
fn same(a: &WindowUpdate, b: &WindowUpdate) -> bool {
    a.app_name == b.app_name
        && a.window_title == b.window_title
        && a.workspace_num == b.workspace_num
        && a.workspace_name == b.workspace_name
}

pub async fn run(state: SharedState, tx: StateTx, cfg: HelmConfig) {
    let interval = Duration::from_millis(cfg.poll_intervals.window_ms);
    let mut ticker = time::interval(interval);
    ticker.set_missed_tick_behavior(time::MissedTickBehavior::Skip);

    let mut last = WindowUpdate::default();
    let mut primed = false;

    loop {
        ticker.tick().await;
        // On xdotool failure (Wayland / not installed) fall back to all-None.
        let update = get_active_window().await.unwrap_or_default();

        if !primed || !same(&update, &last) {
            primed = true;
            last = update.clone();
            {
                let mut s = state.write().await;
                s.window = update;
            }
            let _ = tx.send(());
        }
    }
}
