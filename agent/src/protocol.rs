//! Wire protocol types for the HELM agent.
//!
//! These structs mirror the JSON schemas in `protocol/schema/`.
//! Outbound serialization uses `websocket::make_envelope` + `serde_json::json!`.
//! `HelmMessage` is used for inbound deserialization (Android → agent commands).

use serde::{Deserialize, Serialize};

/// Inbound message envelope — used to deserialize messages arriving from Android.
/// Outbound messages are serialized via `websocket::make_envelope`.
#[allow(dead_code)]
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", content = "payload", rename_all = "snake_case")]
pub enum HelmMessage {
    SystemUpdate(SystemUpdate),
    GitUpdate(GitUpdate),
    MusicUpdate(MusicUpdate),
    WindowUpdate(WindowUpdate),
    VscodeUpdate(VscodeUpdate),
    ClaudeUpdate(ClaudeUpdate),
    ProcessUpdate(ProcessUpdate),
    SystemInfo(SystemInfo),
    Command(Command),
    CommandAck(CommandAck),
    Ping,
    Pong,
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
pub struct ClaudeUpdate {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub status: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub task: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub current_file: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub session_duration_secs: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub tokens_used: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub tokens_max: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub context_percent: Option<f32>,
    // Account-level cumulative usage (scanned from ~/.claude/projects/).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub total_output_tokens: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub total_cache_creation_tokens: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub total_cache_read_tokens: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub total_sessions: Option<u32>,
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

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct AccountUpdate {
    // Static account info (from ~/.claude/.credentials.json)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub email: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub plan: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub rate_limit_tier: Option<String>,

    // Subscription rate limit usage (from API response headers)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub session_used_pct: Option<f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub session_reset_secs: Option<i64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub weekly_used_pct: Option<f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub weekly_reset_secs: Option<i64>,

    // Local activity (from ~/.claude/stats-cache.json)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub today_messages: Option<u32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub today_sessions: Option<u32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub week_messages: Option<u32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub week_sessions: Option<u32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommandAck {
    pub id: String,
    pub success: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub message: Option<String>,
}
