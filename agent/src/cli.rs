//! CLI argument parsing and subcommand dispatch for non-daemon operations.

use clap::{Parser, Subcommand};
use std::path::PathBuf;

#[derive(Parser)]
#[command(
    name = "helm-agent",
    version,
    about = "HELM desktop agent — serves system data to Android sidecar display"
)]
pub struct Cli {
    #[command(subcommand)]
    pub command: Option<Command>,
}

#[derive(Subcommand)]
pub enum Command {
    /// Start the agent (default when no subcommand is given)
    Run,
    /// Show whether the agent is running and on which address
    Status,
    /// Stop the running agent
    Stop,
    /// Stop then restart the agent
    Restart,
    /// Print the WiFi pairing QR code (reads config; does not start agent)
    Qr,
    /// Print the current configuration
    Config,
}

// ── PID file ─────────────────────────────────────────────────────────────────

pub fn pid_path() -> PathBuf {
    dirs::data_local_dir()
        .unwrap_or_else(|| PathBuf::from("/tmp"))
        .join("helm")
        .join("helm-agent.pid")
}

pub fn write_pid() {
    let path = pid_path();
    if let Some(parent) = path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let _ = std::fs::write(&path, std::process::id().to_string());
}

pub fn remove_pid() {
    let _ = std::fs::remove_file(pid_path());
}

pub fn read_pid() -> Option<u32> {
    std::fs::read_to_string(pid_path())
        .ok()
        .and_then(|s| s.trim().parse().ok())
}

/// Returns true if a process with `pid` is currently running.
pub fn pid_alive(pid: u32) -> bool {
    // On Linux, kill(pid, 0) checks existence without sending a signal.
    unsafe { libc::kill(pid as libc::pid_t, 0) == 0 }
}

// ── Subcommand handlers (sync, run before the async runtime starts) ──────────

pub fn cmd_status(cfg: &crate::config::HelmConfig) {
    match read_pid() {
        Some(pid) if pid_alive(pid) => {
            println!(
                "running  pid={pid}  addr={}:{}",
                cfg.bind_host, cfg.port
            );
        }
        Some(_) => {
            println!("stopped  (stale pid file)");
            remove_pid();
        }
        None => {
            println!("stopped");
        }
    }
}

pub fn cmd_stop() -> bool {
    match read_pid() {
        Some(pid) if pid_alive(pid) => {
            unsafe {
                libc::kill(pid as libc::pid_t, libc::SIGTERM);
            }
            // Wait up to 5 s for the process to exit.
            for _ in 0..50 {
                std::thread::sleep(std::time::Duration::from_millis(100));
                if !pid_alive(pid) {
                    break;
                }
            }
            if pid_alive(pid) {
                unsafe { libc::kill(pid as libc::pid_t, libc::SIGKILL); }
            }
            remove_pid();
            println!("stopped  pid={pid}");
            true
        }
        Some(_) => {
            println!("not running (stale pid file removed)");
            remove_pid();
            false
        }
        None => {
            println!("not running");
            false
        }
    }
}

pub fn cmd_qr(cfg: &crate::config::HelmConfig) {
    let wifi_mode = cfg.bind_host != "127.0.0.1";
    if !wifi_mode {
        println!("Agent is in USB-only mode (bind_host = {}).", cfg.bind_host);
        println!("Set bind_host = \"0.0.0.0\" in ~/.config/helm/agent.toml to enable WiFi.");
        return;
    }
    let lan_ip = crate::detect_lan_ip().unwrap_or_else(|| cfg.bind_host.clone());
    let url = format!("helm://{}:{}", lan_ip, cfg.port);
    println!("\nHELM WiFi pairing — scan with Android app:");
    if let Err(e) = qr2term::print_qr(&url) {
        eprintln!("Failed to render QR: {e}");
    }
    println!("{url}\n");
}

pub fn cmd_config(cfg: &crate::config::HelmConfig) {
    println!("bind_host    = {}", cfg.bind_host);
    println!("port         = {}", cfg.port);
    println!("mdns_enabled = {}", cfg.mdns_enabled);
    println!("config_file  = {}", crate::config::config_path().display());

    println!("\npoll intervals (ms):");
    println!("  cpu         = {}", cfg.poll_intervals.cpu_ms);
    println!("  memory      = {}", cfg.poll_intervals.memory_ms);
    println!("  network     = {}", cfg.poll_intervals.network_ms);
    println!("  disk        = {}", cfg.poll_intervals.disk_ms);
    println!("  temperature = {}", cfg.poll_intervals.temperature_ms);
    println!("  battery     = {}", cfg.poll_intervals.battery_ms);
    println!("  process     = {}", cfg.poll_intervals.process_ms);
    println!("  window      = {}", cfg.poll_intervals.window_ms);

    if !cfg.git_watch_paths.is_empty() {
        println!("\ngit_watch_paths:");
        for p in &cfg.git_watch_paths {
            println!("  {p}");
        }
    }

    println!("\nallowed_commands:");
    for c in &cfg.allowed_commands {
        println!("  {c}");
    }
}
