//! Process collector: top 10 processes by CPU usage.

use crate::config::HelmConfig;
use crate::protocol::ProcessInfo;
use crate::state::{SharedState, StateTx};
use std::time::Duration;
use sysinfo::{ProcessesToUpdate, System};
use tokio::time;

const MIB: f64 = 1_048_576.0;
const TOP_N: usize = 10;

fn collect(sys: &mut System) -> Vec<ProcessInfo> {
    sys.refresh_processes(ProcessesToUpdate::All, true);
    let cpu_count = sys.cpus().len().max(1) as f32;

    let mut procs: Vec<ProcessInfo> = sys
        .processes()
        .iter()
        .map(|(pid, p)| ProcessInfo {
            pid: pid.as_u32(),
            name: p.name().to_string_lossy().to_string(),
            // Normalize per-process usage to 0-100 of total system capacity.
            cpu_percent: p.cpu_usage() / cpu_count,
            mem_mb: p.memory() as f64 / MIB,
        })
        .collect();

    procs.sort_by(|a, b| {
        b.cpu_percent
            .partial_cmp(&a.cpu_percent)
            .unwrap_or(std::cmp::Ordering::Equal)
    });
    procs.truncate(TOP_N);
    procs
}

pub async fn run(state: SharedState, tx: StateTx, cfg: HelmConfig) {
    let interval = Duration::from_millis(cfg.poll_intervals.process_ms);
    let mut ticker = time::interval(interval);
    ticker.set_missed_tick_behavior(time::MissedTickBehavior::Skip);

    let mut sys = System::new();
    // Prime CPU usage baseline; the first refresh always reports 0%.
    sys.refresh_processes(ProcessesToUpdate::All, true);

    loop {
        ticker.tick().await;
        let processes = collect(&mut sys);
        {
            let mut s = state.write().await;
            s.process.processes = processes;
        }
        let _ = tx.send(());
    }
}
