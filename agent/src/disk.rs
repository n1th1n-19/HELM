//! Disk collector: system-wide read/write throughput from /proc/diskstats.
//!
//! Reads sector counts from /proc/diskstats for whole-disk devices only
//! (skips partition entries to avoid double-counting). Each sector is 512 bytes.

use crate::config::HelmConfig;
use crate::state::{SharedState, StateTx};
use std::time::{Duration, Instant};
use tokio::time;
use tracing::warn;

/// Returns (total_bytes_read, total_bytes_written) across all whole-disk devices.
fn read_diskstats() -> std::io::Result<(u64, u64)> {
    let content = std::fs::read_to_string("/proc/diskstats")?;
    let mut sectors_read: u64 = 0;
    let mut sectors_written: u64 = 0;

    for line in content.lines() {
        let fields: Vec<&str> = line.split_whitespace().collect();
        if fields.len() < 10 {
            continue;
        }
        let name = fields[2];
        if !is_whole_disk(name) {
            continue;
        }
        sectors_read = sectors_read.saturating_add(fields[5].parse::<u64>().unwrap_or(0));
        sectors_written = sectors_written.saturating_add(fields[9].parse::<u64>().unwrap_or(0));
    }

    // /proc/diskstats uses 512-byte sectors
    Ok((sectors_read.saturating_mul(512), sectors_written.saturating_mul(512)))
}

/// Returns true for whole-disk devices, false for partitions.
/// Examples: sda ✓, sda1 ✗, nvme0n1 ✓, nvme0n1p1 ✗, mmcblk0 ✓, mmcblk0p1 ✗
/// mmcblk0boot0, mmcblk0boot1, mmcblk0rpmb are excluded (partition-like, end in digit).
fn is_whole_disk(name: &str) -> bool {
    if name.starts_with("nvme") || name.starts_with("mmcblk") {
        // partitions end in a digit (nvme0n1p1, mmcblk0p1, mmcblk0boot0, mmcblk0rpmb)
        !name.chars().last().map_or(true, |c| c.is_ascii_digit())
    } else {
        // sd/vd/hd: whole disk ends in letter (sda), partition ends in digit (sda1)
        name.chars().last().map_or(false, |c| c.is_ascii_alphabetic())
    }
}

pub async fn run(state: SharedState, tx: StateTx, cfg: HelmConfig) {
    let interval = Duration::from_millis(cfg.poll_intervals.disk_ms);
    let mut ticker = time::interval(interval);
    ticker.set_missed_tick_behavior(time::MissedTickBehavior::Skip);

    let mut prev_read: u64 = 0;
    let mut prev_write: u64 = 0;
    let mut last_tick = Instant::now();

    // Prime baseline
    if let Ok((r, w)) = read_diskstats() {
        prev_read = r;
        prev_write = w;
    }

    loop {
        ticker.tick().await;
        let elapsed = last_tick.elapsed().as_secs_f64().max(0.001);
        last_tick = Instant::now();

        match read_diskstats() {
            Ok((read_bytes, write_bytes)) => {
                let read_bps = (read_bytes.saturating_sub(prev_read) as f64 / elapsed) as u64;
                let write_bps = (write_bytes.saturating_sub(prev_write) as f64 / elapsed) as u64;
                prev_read = read_bytes;
                prev_write = write_bytes;
                {
                    let mut s = state.write().await;
                    s.system.disk_read_bps = Some(read_bps);
                    s.system.disk_write_bps = Some(write_bps);
                }
                let _ = tx.send(());
            }
            Err(e) => {
                warn!("disk collector: failed to read /proc/diskstats: {}", e);
            }
        }
    }
}
