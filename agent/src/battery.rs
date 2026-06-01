//! Battery collector: charge percentage and charging state.

use crate::config::HelmConfig;
use crate::state::{SharedState, StateTx};
use battery::Manager;
use std::time::Duration;
use tokio::time;
use tracing::error;

/// Returns `(percent, charging)` for the first battery, or `None` on a
/// system without a battery (e.g. a desktop).
fn collect_battery() -> Option<(f32, bool)> {
    let manager = Manager::new().ok()?;
    let battery = manager.batteries().ok()?.next()?.ok()?;
    let percent = battery.state_of_charge().value * 100.0;
    let charging = battery.state() == battery::State::Charging;
    Some((percent, charging))
}

pub async fn run(state: SharedState, tx: StateTx, cfg: HelmConfig) {
    let interval = Duration::from_millis(cfg.poll_intervals.battery_ms);
    let mut ticker = time::interval(interval);
    ticker.set_missed_tick_behavior(time::MissedTickBehavior::Skip);

    loop {
        ticker.tick().await;
        // `collect_battery` runs blocking sysfs reads; keep them off the async
        // executor by hopping to a blocking thread.
        let result = tokio::task::spawn_blocking(collect_battery).await;
        match result {
            Ok(Some((percent, charging))) => {
                let mut s = state.write().await;
                s.system.battery_percent = Some(percent);
                s.system.battery_charging = Some(charging);
            }
            Ok(None) => {
                let mut s = state.write().await;
                s.system.battery_percent = None;
                s.system.battery_charging = None;
            }
            Err(e) => {
                error!("battery collector join error: {}", e);
                continue;
            }
        }
        let _ = tx.send(());
    }
}
