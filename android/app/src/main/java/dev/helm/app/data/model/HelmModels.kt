package dev.helm.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ── Envelope ────────────────────────────────────────────────────────────────

@Serializable
data class HelmEnvelope(
    val type: String,
    val ts: Long,
    val payload: JsonElement,
)

// ── System metrics ───────────────────────────────────────────────────────────

@Serializable
data class SystemUpdate(
    @SerialName("cpu_percent") val cpuPercent: Float? = null,
    @SerialName("cpu_freq_mhz") val cpuFreqMhz: Double? = null,
    @SerialName("cpu_temp_c") val cpuTempC: Double? = null,
    @SerialName("ram_used_mb") val ramUsedMb: Long? = null,
    @SerialName("ram_total_mb") val ramTotalMb: Long? = null,
    @SerialName("swap_used_mb") val swapUsedMb: Long? = null,
    @SerialName("swap_total_mb") val swapTotalMb: Long? = null,
    @SerialName("net_up_bps") val netUpBps: Long? = null,
    @SerialName("net_down_bps") val netDownBps: Long? = null,
    @SerialName("disk_read_bps") val diskReadBps: Long? = null,
    @SerialName("disk_write_bps") val diskWriteBps: Long? = null,
    @SerialName("battery_percent") val batteryPercent: Float? = null,
    @SerialName("battery_charging") val batteryCharging: Boolean? = null,
    @SerialName("uptime_secs") val uptimeSecs: Long? = null,
)

// ── Git ──────────────────────────────────────────────────────────────────────

@Serializable
data class CommitInfo(
    val hash: String,
    val message: String,
    val author: String,
    val ts: Long,
)

@Serializable
data class GitUpdate(
    @SerialName("repo_name") val repoName: String? = null,
    @SerialName("repo_path") val repoPath: String? = null,
    val branch: String? = null,
    @SerialName("remote_branch") val remoteBranch: String? = null,
    val ahead: Int? = null,
    val behind: Int? = null,
    val modified: Int? = null,
    val staged: Int? = null,
    val deleted: Int? = null,
    val untracked: Int? = null,
    val commits: List<CommitInfo>? = null,
)

// ── Music ────────────────────────────────────────────────────────────────────

@Serializable
enum class PlaybackState { Playing, Paused, Stopped }

@Serializable
data class MusicUpdate(
    val player: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("album_art_b64") val albumArtB64: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("position_ms") val positionMs: Long? = null,
    val volume: Float? = null,
    val state: PlaybackState? = null,
)

// ── Window / VS Code ─────────────────────────────────────────────────────────

@Serializable
data class WindowUpdate(
    @SerialName("app_name") val appName: String? = null,
    @SerialName("window_title") val windowTitle: String? = null,
    @SerialName("workspace_name") val workspaceName: String? = null,
    @SerialName("workspace_num") val workspaceNum: Int? = null,
)

@Serializable
data class VscodeUpdate(
    @SerialName("workspace_path") val workspacePath: String? = null,
    @SerialName("project_name") val projectName: String? = null,
    @SerialName("active_file") val activeFile: String? = null,
    val branch: String? = null,
)

// ── Processes ────────────────────────────────────────────────────────────────

@Serializable
data class ProcessInfo(
    val pid: Int,
    val name: String,
    @SerialName("cpu_percent") val cpuPercent: Float,
    @SerialName("mem_mb") val memMb: Double,
)

@Serializable
data class ProcessUpdate(
    val processes: List<ProcessInfo> = emptyList(),
)

// ── System info (static, sent once on connect) ───────────────────────────────

@Serializable
data class SystemInfo(
    val os: String,
    val kernel: String,
    val hostname: String,
    @SerialName("agent_version") val agentVersion: String,
    val de: String? = null,
    val shell: String? = null,
    val resolution: String? = null,
)

// ── Claude AI ────────────────────────────────────────────────────────────────

@Serializable
data class ClaudeUpdate(
    val status: String? = null,
    val task: String? = null,
    @SerialName("current_file") val currentFile: String? = null,
    @SerialName("session_duration_secs") val sessionDurationSecs: Long? = null,
    @SerialName("tokens_used") val tokensUsed: Long? = null,
    @SerialName("tokens_max") val tokensMax: Long? = null,
    @SerialName("context_percent") val contextPercent: Float? = null,
    @SerialName("total_output_tokens") val totalOutputTokens: Long? = null,
    @SerialName("total_cache_creation_tokens") val totalCacheCreationTokens: Long? = null,
    @SerialName("total_cache_read_tokens") val totalCacheReadTokens: Long? = null,
    @SerialName("total_sessions") val totalSessions: Int? = null,
)

// ── Dashboard events (client-side, never serialized) ─────────────────────────

enum class EventCategory { Build, Test, Claude, Git, System }

data class DashboardEvent(
    val timeLabel: String,
    val message: String,
    val category: EventCategory,
)

// ── Terminal status (client-side, never serialized) ──────────────────────────

data class TerminalStatus(
    val lastCommand: String? = null,
    val running: Boolean = false,
    val elapsedSecs: Long? = null,
)

// ── Commands ─────────────────────────────────────────────────────────────────

@Serializable
enum class CommandAction {
    @SerialName("restart_dev_server") RestartDevServer,
    @SerialName("git_pull") GitPull,
    @SerialName("git_push") GitPush,
    @SerialName("open_terminal") OpenTerminal,
    @SerialName("open_project") OpenProject,
    @SerialName("lock") Lock,
    @SerialName("suspend") Suspend,
    @SerialName("reboot") Reboot,
    @SerialName("shutdown") Shutdown,
}

@Serializable
data class HelmCommand(
    val id: String,
    val action: CommandAction,
    val args: Map<String, String>? = null,
)

@Serializable
data class CommandAck(
    val id: String,
    val success: Boolean,
    val message: String? = null,
)

// ── Full aggregated state (client-side, never serialized) ────────────────────

data class HelmState(
    val system: SystemUpdate = SystemUpdate(),
    val git: GitUpdate = GitUpdate(),
    val music: MusicUpdate = MusicUpdate(),
    val window: WindowUpdate = WindowUpdate(),
    val vscode: VscodeUpdate = VscodeUpdate(),
    val process: ProcessUpdate = ProcessUpdate(),
    val systemInfo: SystemInfo? = null,
    val commandAcks: Map<String, CommandAck> = emptyMap(),
    val claude: ClaudeUpdate = ClaudeUpdate(),
    val events: List<DashboardEvent> = emptyList(),
    val terminal: TerminalStatus = TerminalStatus(),
)
