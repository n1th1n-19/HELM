//! Wire protocol types for the HELM agent.
//!
//! These structs mirror the JSON schemas in `protocol/schema/`. Every message
//! is serialized inside an [`Envelope`] (`{ "type", "ts", "payload" }`).

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", content = "payload", rename_all = "snake_case")]
pub enum HelmMessage {
    SystemUpdate(SystemUpdate),
    GitUpdate(GitUpdate),
    MusicUpdate(MusicUpdate),
    WindowUpdate(WindowUpdate),
    VscodeUpdate(VscodeUpdate),
    ProcessUpdate(ProcessUpdate),
    SystemInfo(SystemInfo),
    Command(Command),
    CommandAck(CommandAck),
    Ping,
    Pong,
}

/// Outer wrapper for every WebSocket message.
#[derive(Debug, Serialize, Deserialize)]
pub struct Envelope {
    #[serde(rename = "type")]
    pub msg_type: String,
    pub ts: i64,
    pub payload: serde_json::Value,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct SystemUpdate {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub cpu_percent: Option<f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub cpu_freq_mhz: Option<f64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub cpu_temp_c: Option<f64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub ram_used_mb: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub ram_total_mb: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub swap_used_mb: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub swap_total_mb: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub net_up_bps: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub net_down_bps: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub disk_read_bps: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub disk_write_bps: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub battery_percent: Option<f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub battery_charging: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub uptime_secs: Option<u64>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct GitUpdate {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub repo_name: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub repo_path: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub branch: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub remote_branch: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub ahead: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub behind: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub modified: Option<u32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub staged: Option<u32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub deleted: Option<u32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub untracked: Option<u32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub commits: Option<Vec<CommitInfo>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommitInfo {
    pub hash: String,
    pub message: String,
    pub author: String,
    pub ts: i64,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct MusicUpdate {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub player: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub title: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub artist: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub album: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub album_art_b64: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub duration_ms: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub position_ms: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub volume: Option<f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub state: Option<PlaybackState>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "PascalCase")]
pub enum PlaybackState {
    Playing,
    Paused,
    Stopped,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct WindowUpdate {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub app_name: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub window_title: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub workspace_name: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub workspace_num: Option<i32>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct VscodeUpdate {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub workspace_path: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub project_name: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub active_file: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub branch: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProcessInfo {
    pub pid: u32,
    pub name: String,
    pub cpu_percent: f32,
    pub mem_mb: f64,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct ProcessUpdate {
    pub processes: Vec<ProcessInfo>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SystemInfo {
    pub os: String,
    pub kernel: String,
    pub hostname: String,
    pub agent_version: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub de: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub shell: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub resolution: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum CommandAction {
    RestartDevServer,
    GitPull,
    GitPush,
    OpenTerminal,
    OpenProject,
    Lock,
    Suspend,
    Reboot,
    Shutdown,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Command {
    pub id: String,
    pub action: CommandAction,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub args: Option<std::collections::HashMap<String, String>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommandAck {
    pub id: String,
    pub success: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub message: Option<String>,
}
