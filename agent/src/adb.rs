//! ADB reverse-forwarding watcher for USB mode.
//!
//! Re-runs `adb reverse tcp:PORT tcp:PORT` on a short interval so that
//! USB reconnects automatically restore the tunnel within a few seconds.
//! Runs silently when no device is attached (adb exits non-zero, ignored).

use std::time::Duration;
use tokio::time;
use tracing::debug;

pub async fn maintain_reverse(port: u16) {
    let local = format!("tcp:{port}");
    let remote = format!("tcp:{port}");
    let mut ticker = time::interval(Duration::from_secs(3));
    ticker.set_missed_tick_behavior(time::MissedTickBehavior::Skip);

    loop {
        ticker.tick().await;
        let result = tokio::process::Command::new("adb")
            .args(["reverse", "--no-rebind", &local, &remote])
            .output()
            .await;
        match result {
            Ok(out) if out.status.success() => {
                debug!("adb reverse tcp:{port} tcp:{port} ok");
            }
            _ => {} // no device attached or adb not in PATH — silent
        }
    }
}
