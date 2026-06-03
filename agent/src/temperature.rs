//! Temperature collector: CPU temperature from hardware components.

use crate::config::HelmConfig;
use crate::state::{SharedState, StateTx};
use std::time::Duration;
use sysinfo::Components;
use tokio::time;

fn find_cpu_temp(components: &Components) -> Option<f64> {
    components
        .iter()
        .filter(|c| {
            let label = c.label().to_lowercase();
            label.contains("cpu")
                || label.contains("core 0")
                || label.contains("package id 0")
                || label.contains("tdie")
                || label.contains("tctl")
        })
        .filter_map(|c| {
            let t = c.temperature() as f64;
            if t.is_finite() && t > 0.0 { Some(t) } else { None }
        })
        .next()
}

pub async fn run(state: SharedState, tx: StateTx, cfg: HelmConfig) {
    let interval = Duration::from_millis(cfg.poll_intervals.temperature_ms);
    let mut ticker = time::interval(interval);
    ticker.set_missed_tick_behavior(time::MissedTickBehavior::Skip);

    let mut components = Components::new_with_refreshed_list();

    loop {
        ticker.tick().await;
        components.refresh();
        let temp = find_cpu_temp(&components);
        {
            let mut s = state.write().await;
            s.system.cpu_temp_c = temp;
        }
        let _ = tx.send(());
    }
}
