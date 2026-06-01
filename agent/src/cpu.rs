//! CPU collector: global usage, average frequency, and uptime.

use crate::config::HelmConfig;
use crate::state::{SharedState, StateTx};
use std::time::Duration;
use sysinfo::System;
use tokio::time;

fn collect(sys: &mut System) -> (f32, Option<f64>, u64) {
    sys.refresh_cpu_all();
    let cpus = sys.cpus();
    let cpu_percent = if cpus.is_empty() {
        0.0
    } else {
        cpus.iter().map(|c| c.cpu_usage()).sum::<f32>() / cpus.len() as f32
    };
    let avg_freq = if cpus.iter().any(|c| c.frequency() > 0) {
        Some(cpus.iter().map(|c| c.frequency() as f64).sum::<f64>() / cpus.len() as f64)
    } else {
        None
    };
    let uptime = System::uptime();
    (cpu_percent, avg_freq, uptime)
}

pub async fn run(state: SharedState, tx: StateTx, cfg: HelmConfig) {
    let interval = Duration::from_millis(cfg.poll_intervals.cpu_ms);
    let mut ticker = time::interval(interval);
    ticker.set_missed_tick_behavior(time::MissedTickBehavior::Skip);

    let mut sys = System::new();

    loop {
        ticker.tick().await;
        let (cpu_percent, avg_freq, uptime) = collect(&mut sys);
        {
            let mut s = state.write().await;
            s.system.cpu_percent = Some(cpu_percent);
            s.system.cpu_freq_mhz = avg_freq;
            s.system.uptime_secs = Some(uptime);
        }
        let _ = tx.send(());
    }
}
