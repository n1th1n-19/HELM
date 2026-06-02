//! mDNS service advertisement for WiFi discovery.
//!
//! Advertises `_helm._tcp.local.` so the Android app can discover the agent
//! without manual IP entry. Only active when bind_host is not loopback.

use anyhow::Result;
use mdns_sd::{ServiceDaemon, ServiceInfo};
use tracing::{info, warn};

pub async fn advertise(port: u16, hostname: String) -> Result<()> {
    let daemon = match ServiceDaemon::new() {
        Ok(d) => d,
        Err(e) => {
            warn!("mDNS: failed to create daemon: {e}");
            return Ok(());
        }
    };

    let instance_name = format!("helm-agent-{hostname}");
    let service_type = "_helm._tcp.local.";
    let host_fqdn = format!("{hostname}.local.");

    let service = match ServiceInfo::new(
        service_type,
        &instance_name,
        &host_fqdn,
        "",
        port,
        None,
    ) {
        Ok(s) => s,
        Err(e) => {
            warn!("mDNS: failed to create service info: {e}");
            return Ok(());
        }
    };

    match daemon.register(service) {
        Ok(_) => info!("mDNS: advertising {instance_name} on port {port}"),
        Err(e) => warn!("mDNS: failed to register service: {e}"),
    }

    // Keep the daemon alive; it runs until this future is cancelled.
    std::future::pending::<()>().await;
    Ok(())
}
