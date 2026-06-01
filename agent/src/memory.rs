//! Memory collector: RAM and swap usage.

use crate::config::HelmConfig;
use crate::state::{SharedState, StateTx};
use std::time::Duration;
use sysinfo::System;
use tokio::time;

const MIB: u64 = 1_048_576;

fn collect(sys: &mut System) -> (u64, u64, u64, u64) {
    sys.refresh_memory();
    (
        sys.used_memory() / MIB,
        sys.total_memory() / MIB,
        sys.used_swap() / MIB,
        sys.total_swap() / MIB,
    )
}

pub async fn run(state: SharedState, tx: StateTx, cfg: HelmConfig) {
    let interval = Duration::from_millis(cfg.poll_intervals.memory_ms);
    let mut ticker = time::interval(interval);
    ticker.set_missed_tick_behavior(time::MissedTickBehavior::Skip);

    let mut sys = System::new();

    loop {
        ticker.tick().await;
        let (ram_used, ram_total, swap_used, swap_total) = collect(&mut sys);
        {
            let mut s = state.write().await;
            s.system.ram_used_mb = Some(ram_used);
            s.system.ram_total_mb = Some(ram_total);
            s.system.swap_used_mb = Some(swap_used);
            s.system.swap_total_mb = Some(swap_total);
        }
        let _ = tx.send(());
    }
}
