//! Network collector: aggregate upload/download throughput in bytes/second.

use crate::config::HelmConfig;
use crate::state::{SharedState, StateTx};
use std::time::Duration;
use sysinfo::Networks;
use tokio::time;

pub async fn run(state: SharedState, tx: StateTx, cfg: HelmConfig) {
    let interval = Duration::from_millis(cfg.poll_intervals.network_ms);
    let interval_secs = cfg.poll_intervals.network_ms as f64 / 1000.0;
    let mut ticker = time::interval(interval);
    ticker.set_missed_tick_behavior(time::MissedTickBehavior::Skip);

    let mut networks = Networks::new_with_refreshed_list();
    let mut prev_tx: u64 = networks.iter().map(|(_, n)| n.total_transmitted()).sum();
    let mut prev_rx: u64 = networks.iter().map(|(_, n)| n.total_received()).sum();

    loop {
        ticker.tick().await;
        networks.refresh();
        let total_tx: u64 = networks.iter().map(|(_, n)| n.total_transmitted()).sum();
        let total_rx: u64 = networks.iter().map(|(_, n)| n.total_received()).sum();
        let up_bps = (total_tx.saturating_sub(prev_tx) as f64 / interval_secs) as u64;
        let down_bps = (total_rx.saturating_sub(prev_rx) as f64 / interval_secs) as u64;
        prev_tx = total_tx;
        prev_rx = total_rx;
        {
            let mut s = state.write().await;
            s.system.net_up_bps = Some(up_bps);
            s.system.net_down_bps = Some(down_bps);
        }
        let _ = tx.send(());
    }
}
