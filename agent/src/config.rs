//! TOML configuration loaded from `~/.config/helm/agent.toml`.

use anyhow::Result;
use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HelmConfig {
    #[serde(default = "default_port")]
    pub port: u16,
    /// Bind address for the WebSocket server. Defaults to 127.0.0.1 (loopback
    /// only — correct for USB/ADB use). Set to "0.0.0.0" only on trusted LANs.
    #[serde(default = "default_bind_host")]
    pub bind_host: String,
    #[serde(default = "default_git_watch_paths")]
    pub git_watch_paths: Vec<String>,
    #[serde(default = "default_poll_intervals")]
    pub poll_intervals: PollIntervals,
    #[serde(default = "default_allowed_commands")]
    pub allowed_commands: Vec<String>,
    /// Advertise via mDNS when bind_host is not loopback. Set false to disable.
    #[serde(default = "default_mdns_enabled")]
    pub mdns_enabled: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PollIntervals {
    #[serde(default = "default_cpu_ms")]
    pub cpu_ms: u64,
    #[serde(default = "default_memory_ms")]
    pub memory_ms: u64,
    #[serde(default = "default_network_ms")]
    pub network_ms: u64,
    #[serde(default = "default_disk_ms")]
    pub disk_ms: u64,
    #[serde(default = "default_battery_ms")]
    pub battery_ms: u64,
    #[serde(default = "default_temperature_ms")]
    pub temperature_ms: u64,
    #[serde(default = "default_process_ms")]
    pub process_ms: u64,
    #[serde(default = "default_window_ms")]
    pub window_ms: u64,
}

fn default_port() -> u16 {
    9090
}
fn default_bind_host() -> String {
    "127.0.0.1".to_string()
}
fn default_git_watch_paths() -> Vec<String> {
    vec![]
}
fn default_cpu_ms() -> u64 {
    1000
}
fn default_memory_ms() -> u64 {
    2000
}
fn default_network_ms() -> u64 {
    1000
}
fn default_disk_ms() -> u64 {
    2000
}
fn default_battery_ms() -> u64 {
    30000
}
fn default_temperature_ms() -> u64 {
    2000
}
fn default_process_ms() -> u64 {
    3000
}
fn default_window_ms() -> u64 {
    500
}
fn default_poll_intervals() -> PollIntervals {
    PollIntervals {
        cpu_ms: default_cpu_ms(),
        memory_ms: default_memory_ms(),
        network_ms: default_network_ms(),
        disk_ms: default_disk_ms(),
        battery_ms: default_battery_ms(),
        temperature_ms: default_temperature_ms(),
        process_ms: default_process_ms(),
        window_ms: default_window_ms(),
    }
}
fn default_mdns_enabled() -> bool {
    true
}
fn default_allowed_commands() -> Vec<String> {
    vec![
        "git_pull".to_string(),
        "git_push".to_string(),
        "lock".to_string(),
    ]
}

impl Default for HelmConfig {
    fn default() -> Self {
        HelmConfig {
            port: default_port(),
            bind_host: default_bind_host(),
            git_watch_paths: default_git_watch_paths(),
            poll_intervals: default_poll_intervals(),
            allowed_commands: default_allowed_commands(),
            mdns_enabled: default_mdns_enabled(),
        }
    }
}

pub fn config_path() -> PathBuf {
    let base = dirs::config_dir().unwrap_or_else(|| PathBuf::from("~/.config"));
    base.join("helm").join("agent.toml")
}

pub fn load_config() -> Result<HelmConfig> {
    let path = config_path();
    if !path.exists() {
        return Ok(HelmConfig::default());
    }
    let content = std::fs::read_to_string(&path)?;
    let config: HelmConfig = toml::from_str(&content)?;
    Ok(config)
}
