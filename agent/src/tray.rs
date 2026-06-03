//! Cross-platform system tray icon using the `tray-icon` crate.
//!
//! Runs on a dedicated OS thread. Polls `client_count` every 500 ms to update
//! the status label. Sends on `shutdown_tx` when Stop or Restart is selected,
//! which triggers graceful shutdown in main.rs.

use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;
use tray_icon::{
    menu::{Menu, MenuEvent, MenuItem, PredefinedMenuItem},
    Icon, TrayIconBuilder,
};

static ICON_BYTES: &[u8] = include_bytes!("../../assets/helm-icon.png");

fn load_icon() -> anyhow::Result<Icon> {
    let img = image::load_from_memory(ICON_BYTES)?.into_rgba8();
    let (w, h) = img.dimensions();
    Icon::from_rgba(img.into_raw(), w, h).map_err(|e| anyhow::anyhow!("tray icon error: {e}"))
}

pub fn run_tray(
    client_count: Arc<AtomicUsize>,
    shutdown_tx: tokio::sync::mpsc::Sender<()>,
) -> anyhow::Result<()> {
    let icon = load_icon()?;

    let version_item = MenuItem::new(
        format!("HELM Agent v{}", env!("CARGO_PKG_VERSION")),
        false,
        None,
    );
    let status_item = MenuItem::new("○ Disconnected", false, None);
    let stop_item    = MenuItem::new("Stop", true, None);
    let restart_item = MenuItem::new("Restart", true, None);

    let menu = Menu::new();
    menu.append_items(&[
        &version_item,
        &status_item,
        &PredefinedMenuItem::separator(),
        &stop_item,
        &restart_item,
    ])?;

    let _tray = TrayIconBuilder::new()
        .with_icon(icon)
        .with_menu(Box::new(menu))
        .with_tooltip("HELM Agent")
        .build()?;

    let stop_id    = stop_item.id().clone();
    let restart_id = restart_item.id().clone();
    let receiver   = MenuEvent::receiver();
    let mut last_count = usize::MAX;

    loop {
        // Update status label when connected client count changes.
        let count = client_count.load(Ordering::Relaxed);
        if count != last_count {
            let label = if count == 0 {
                "○ Disconnected".to_string()
            } else {
                format!(
                    "● Connected ({} client{})",
                    count,
                    if count == 1 { "" } else { "s" }
                )
            };
            status_item.set_text(label);
            last_count = count;
        }

        // Drain menu events (non-blocking).
        while let Ok(event) = receiver.try_recv() {
            if event.id == stop_id {
                let _ = shutdown_tx.blocking_send(());
                return Ok(());
            } else if event.id == restart_id {
                // Signal shutdown first so the old server releases its port
                // before the new process starts, avoiding a bind race.
                let _ = shutdown_tx.blocking_send(());
                std::thread::sleep(std::time::Duration::from_millis(500));
                if let Ok(exe) = std::env::current_exe() {
                    let _ = std::process::Command::new(exe).spawn();
                }
                return Ok(());
            }
        }

        // On Linux the tray backend is GTK/libappindicator; pump the GLib
        // event loop so menu-click signals are dispatched to the receiver.
        #[cfg(all(target_os = "linux", feature = "tray"))]
        {
            gtk::main_iteration_do(false);
        }

        std::thread::sleep(std::time::Duration::from_millis(500));
    }
}
