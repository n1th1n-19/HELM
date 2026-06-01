//! Disk collector: aggregate read/write throughput in bytes/second.
//!
//! sysinfo 0.32 does not expose per-disk I/O counters (added in 0.33), so we
//! aggregate per-process disk usage instead. `DiskUsage::read_bytes` /
//! `written_bytes` are the deltas accumulated since the previous refresh.

use crate::config::HelmConfig;
use crate::state::{SharedState, StateTx};
use std::time::Duration;
use sysinfo::{ProcessesToUpdate, System};
use tokio::time;

pub async fn run(state: SharedState, tx: StateTx, cfg: HelmConfig) {
    let interval = Duration::from_millis(cfg.poll_intervals.disk_ms);
    let interval_secs = cfg.poll_intervals.disk_ms as f64 / 1000.0;
    let mut ticker = time::interval(interval);
    ticker.set_missed_tick_behavior(time::MissedTickBehavior::Skip);

    let mut sys = System::new();
    // Prime the process disk-usage baseline so the first sampled delta is sane.
    sys.refresh_processes(ProcessesToUpdate::All, true);

    loop {
        ticker.tick().await;
        sys.refresh_processes(ProcessesToUpdate::All, true);
        let mut read_delta: u64 = 0;
        let mut write_delta: u64 = 0;
        for process in sys.processes().values() {
            let usage = process.disk_usage();
            read_delta = read_delta.saturating_add(usage.read_bytes);
            write_delta = write_delta.saturating_add(usage.written_bytes);
        }
        let read_bps = (read_delta as f64 / interval_secs) as u64;
        let write_bps = (write_delta as f64 / interval_secs) as u64;
        {
            let mut s = state.write().await;
            s.system.disk_read_bps = Some(read_bps);
            s.system.disk_write_bps = Some(write_bps);
        }
        let _ = tx.send(());
    }
}
