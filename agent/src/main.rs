mod config;
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
mod commands;

use anyhow::Result;
use std::net::SocketAddr;
use tracing::info;

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::from_default_env()
                .add_directive("helm_agent=info".parse()?),
        )
        .init();

    let cfg = config::load_config()?;
    info!("Starting HELM agent v{}", env!("CARGO_PKG_VERSION"));

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

    let addr: SocketAddr = format!("0.0.0.0:{}", cfg.port).parse()?;

    // Graceful shutdown on SIGINT or SIGTERM.
    let shutdown = async {
        use tokio::signal::unix::{signal, SignalKind};
        let mut sigterm = signal(SignalKind::terminate()).expect("failed to register SIGTERM handler");
        tokio::select! {
            _ = tokio::signal::ctrl_c() => {}
            _ = sigterm.recv() => {}
        }
        info!("Shutting down HELM agent");
    };

    tokio::select! {
        result = websocket::run_server(addr, state, state_rx, cfg) => {
            result?;
        }
        _ = shutdown => {}
    }

    Ok(())
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
