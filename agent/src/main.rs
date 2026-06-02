mod adb;
mod cli;
mod config;
mod mdns;
mod protocol;
mod state;
mod websocket;

// Collector stubs (to be implemented in Tasks 4 and 5).
mod battery;
mod cpu;
mod disk;
mod git;
mod memory;
mod music;
mod network;
mod process;
mod temperature;
mod window;
mod workspace;

// Command handler stub (Task 6).
mod claude;
mod commands;

mod security;
#[cfg(feature = "tray")]
mod tray;

use anyhow::Result;
use clap::Parser;
use std::net::SocketAddr;
use std::sync::Arc;
use std::sync::atomic::AtomicUsize;
use tracing::{info, warn};

#[tokio::main]
async fn main() -> Result<()> {
    let args = cli::Cli::parse();
    let cfg = config::load_config()?;

    // Dispatch non-daemon subcommands synchronously before starting the runtime.
    match args.command {
        Some(cli::Command::Status) => {
            cli::cmd_status(&cfg);
            return Ok(());
        }
        Some(cli::Command::Stop) => {
            cli::cmd_stop();
            return Ok(());
        }
        Some(cli::Command::Restart) => {
            cli::cmd_stop();
            // Fall through to run.
        }
        Some(cli::Command::Qr) => {
            cli::cmd_qr(&cfg);
            return Ok(());
        }
        Some(cli::Command::Config) => {
            cli::cmd_config(&cfg);
            return Ok(());
        }
        Some(cli::Command::Run) | None => {
            // Fall through to start the daemon.
        }
    }

    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::from_default_env()
                .add_directive("helm_agent=info".parse()?),
        )
        .init();

    cli::write_pid();

    info!("Starting HELM agent v{}", env!("CARGO_PKG_VERSION"));

    // Bind the WebSocket port FIRST — fail fast before spawning any collectors.
    let addr: SocketAddr = format!("{}:{}", cfg.bind_host, cfg.port).parse()?;
    let listener = tokio::net::TcpListener::bind(addr).await
        .map_err(|e| anyhow::anyhow!("cannot bind {}:{} — {e}", cfg.bind_host, cfg.port))?;
    info!("HELM agent listening on {}", addr);

    let (state, state_tx, state_rx) = state::new_shared_state();

    // Populate system_info on startup.
    {
        let mut s = state.write().await;
        s.system_info = Some(collect_system_info());
    }

    // Notify initial state.
    let _ = state_tx.send(());

    // Spawn system collectors. Each gets its own sysinfo instance to avoid
    // lock contention between collectors with different poll intervals.
    tokio::spawn(cpu::run(state.clone(), state_tx.clone(), cfg.clone()));
    tokio::spawn(memory::run(state.clone(), state_tx.clone(), cfg.clone()));
    tokio::spawn(temperature::run(state.clone(), state_tx.clone(), cfg.clone()));
    tokio::spawn(network::run(state.clone(), state_tx.clone(), cfg.clone()));
    tokio::spawn(disk::run(state.clone(), state_tx.clone(), cfg.clone()));
    tokio::spawn(battery::run(state.clone(), state_tx.clone(), cfg.clone()));
    tokio::spawn(process::run(state.clone(), state_tx.clone(), cfg.clone()));

    // Spawn dev-environment collectors.
    tokio::spawn(workspace::run(state.clone(), state_tx.clone(), cfg.clone()));
    tokio::spawn(git::run(state.clone(), state_tx.clone(), cfg.clone()));
    tokio::spawn(window::run(state.clone(), state_tx.clone(), cfg.clone()));
    tokio::spawn(music::run(state.clone(), state_tx.clone(), cfg.clone()));
    tokio::spawn(claude::run(state.clone(), state_tx.clone(), cfg.clone()));

    let wifi_mode = cfg.bind_host != "127.0.0.1";

    // Security context — WiFi mode only. USB mode binds to loopback; no TLS needed.
    let (security, rate_limiter) = if wifi_mode {
        match security::load_or_create(&cfg) {
            Ok(ctx) => {
                info!("TLS cert fingerprint: {}", ctx.cert_fingerprint);
                (Some(Arc::new(ctx)), Arc::new(security::RateLimiter::new()))
            }
            Err(e) => anyhow::bail!("Failed to initialize security context: {e}"),
        }
    } else {
        (None, Arc::new(security::RateLimiter::new()))
    };

    // Shared client count: websocket.rs increments/decrements, tray.rs reads.
    let client_count = Arc::new(AtomicUsize::new(0));

    // Tray shutdown channel. Always created; only populated when tray feature fires.
    let (shutdown_tx, mut shutdown_rx) = tokio::sync::mpsc::channel::<()>(1);

    // Keep adb reverse alive in both modes — USB reconnects restore the tunnel
    // automatically, and WiFi mode can still accept USB simultaneously.
    tokio::spawn(adb::maintain_reverse(cfg.port));

    if wifi_mode {
        if let Some(ref ctx) = security {
            if let Some(lan_ip) = detect_lan_ip() {
                let pairing_url = format!(
                    "helms://{}:{}?token={}&cert={}",
                    lan_ip, cfg.port, ctx.token, ctx.cert_fingerprint
                );
                println!("\nHELM WiFi pairing — scan with Android app:");
                if let Err(e) = qr2term::print_qr(&pairing_url) {
                    warn!("Failed to print QR code: {e}");
                }
                println!("{pairing_url}\n");
                info!(
                    "WiFi pairing URL: helms://{}:{}?cert={}",
                    lan_ip, cfg.port, ctx.cert_fingerprint
                );
                tracing::debug!("WiFi pairing URL (full): {pairing_url}");
            } else {
                warn!("WiFi mode: could not detect LAN IP — enter agent IP manually in the app");
            }
        }

        if cfg.mdns_enabled {
            let hostname = sysinfo::System::host_name()
                .unwrap_or_else(|| "helm-agent".to_string());
            tokio::spawn(mdns::advertise(cfg.port, hostname));
        }
    }

    // System tray (feature-gated; logs a warning and continues if init fails).
    #[cfg(feature = "tray")]
    {
        let client_count_tray = client_count.clone();
        let tx = shutdown_tx.clone();
        std::thread::spawn(move || {
            if let Err(e) = tray::run_tray(client_count_tray, tx) {
                warn!("System tray unavailable: {e}");
            }
        });
    }

    // Graceful shutdown: Ctrl-C, SIGTERM, or tray Stop/Restart button.
    let shutdown = async {
        use tokio::signal::unix::{signal, SignalKind};
        let mut sigterm = signal(SignalKind::terminate())
            .expect("failed to register SIGTERM handler");
        tokio::select! {
            _ = tokio::signal::ctrl_c() => {}
            _ = sigterm.recv() => {}
            _ = shutdown_rx.recv() => {}
        }
        info!("Shutting down HELM agent");
    };

    tokio::select! {
        result = websocket::run_server(
            listener,
            state,
            state_rx,
            cfg,
            security,
            rate_limiter,
            client_count,
        ) => {
            result?;
        }
        _ = shutdown => {}
    }

    cli::remove_pid();
    Ok(())
}

/// Detect the LAN IP by connecting a UDP socket to an external address (no
/// data is sent — this just reveals which local interface the OS would use).
pub(crate) fn detect_lan_ip() -> Option<String> {
    use std::net::UdpSocket;
    let socket = UdpSocket::bind("0.0.0.0:0").ok()?;
    socket.connect("8.8.8.8:80").ok()?;
    let addr = socket.local_addr().ok()?;
    let ip = addr.ip();
    if ip.is_loopback() {
        return None;
    }
    Some(ip.to_string())
}

fn collect_system_info() -> protocol::SystemInfo {
    use sysinfo::System;
    protocol::SystemInfo {
        os: System::long_os_version().unwrap_or_else(|| "Unknown".to_string()),
        kernel: System::kernel_version().unwrap_or_else(|| "Unknown".to_string()),
        hostname: System::host_name().unwrap_or_else(|| "unknown".to_string()),
        agent_version: env!("CARGO_PKG_VERSION").to_string(),
        de: std::env::var("XDG_CURRENT_DESKTOP").ok(),
        shell: std::env::var("SHELL").ok().and_then(|s| {
            std::path::Path::new(&s)
                .file_name()?
                .to_str()
                .map(|s| s.to_string())
        }),
        resolution: None, // Will be filled by window.rs in Task 5.
    }
}
