//! mDNS service advertisement for WiFi discovery.
//!
//! Advertises `_helm._tcp.local.` so the Android app can discover the agent
//! without manual IP entry. Only active when bind_host is not loopback.

use anyhow::Result;
use mdns_sd::{ServiceDaemon, ServiceInfo};
use tracing::{info, warn};

pub async fn advertise(port: u16, hostname: String) -> Result<()> {
    // DNS labels must not contain dots; replace with hyphens.
    let hostname = hostname.replace('.', "-");

    let daemon = match ServiceDaemon::new() {
        Ok(d) => d,
        Err(e) => {
            warn!("mDNS: failed to create daemon: {e}");
            return Ok(());
        }
    };

    let instance_name = format!("helm-{hostname}");
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

    // Capture the fullname before register() consumes the ServiceInfo.
    let service_fullname = service.get_fullname().to_owned();

    match daemon.register(service) {
        Ok(_) => info!("mDNS: advertising {instance_name} on port {port}"),
        Err(e) => {
            warn!("mDNS: failed to register service: {e}");
            return Ok(());
        }
    };

    // Keep the daemon alive until shutdown. On Ctrl-C send an mDNS goodbye
    // packet so peers remove the record promptly rather than waiting for TTL.
    tokio::select! {
        _ = tokio::signal::ctrl_c() => {
            let _ = daemon.unregister(&service_fullname);
        }
        _ = std::future::pending::<()>() => {}
    }
    Ok(())
}
