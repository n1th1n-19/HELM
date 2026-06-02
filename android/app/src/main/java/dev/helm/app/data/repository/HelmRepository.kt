package dev.helm.app.data.repository

import dev.helm.app.data.model.ClaudeUpdate
import dev.helm.app.data.model.CommandAck
import dev.helm.app.data.model.GitUpdate
import dev.helm.app.data.model.HelmCommand
import dev.helm.app.data.model.HelmEnvelope
import dev.helm.app.data.model.HelmState
import dev.helm.app.data.model.MusicUpdate
import dev.helm.app.data.model.ProcessUpdate
import dev.helm.app.data.model.SystemInfo
import dev.helm.app.data.model.SystemUpdate
import dev.helm.app.data.model.VscodeUpdate
import dev.helm.app.data.model.WindowUpdate
import dev.helm.app.data.websocket.ConnectionManager
import dev.helm.app.data.websocket.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HelmRepository @Inject constructor(
    private val connectionManager: ConnectionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _state = MutableStateFlow(HelmState())
    val state: StateFlow<HelmState> = _state.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState

    init {
        scope.launch {
            connectionManager.messages.collect { envelope ->
                _state.value = applyDelta(_state.value, envelope)
            }
        }
    }

    fun connect() = connectionManager.start()
    fun disconnect() = connectionManager.stop()

    suspend fun sendCommand(command: HelmCommand) {
        val envelope = json.encodeToString(
            buildJsonObject {
                put("type", "command")
                put("ts", System.currentTimeMillis())
                put("payload", json.encodeToJsonElement(command))
            }
        )
        connectionManager.send(envelope)
    }

    private fun applyDelta(current: HelmState, envelope: HelmEnvelope): HelmState {
        return try {
            when (envelope.type) {
                "system_update" -> {
                    val delta = json.decodeFromJsonElement<SystemUpdate>(envelope.payload)
                    current.copy(system = current.system.merge(delta))
                }
                "git_update" -> {
                    val delta = json.decodeFromJsonElement<GitUpdate>(envelope.payload)
                    current.copy(git = current.git.merge(delta))
                }
                "music_update" -> {
                    val delta = json.decodeFromJsonElement<MusicUpdate>(envelope.payload)
                    current.copy(music = current.music.merge(delta))
                }
                "window_update" -> {
                    val delta = json.decodeFromJsonElement<WindowUpdate>(envelope.payload)
                    current.copy(window = current.window.merge(delta))
                }
                "vscode_update" -> {
                    val delta = json.decodeFromJsonElement<VscodeUpdate>(envelope.payload)
                    current.copy(vscode = current.vscode.merge(delta))
                }
                "process_update" -> {
                    val delta = json.decodeFromJsonElement<ProcessUpdate>(envelope.payload)
                    current.copy(process = delta) // always full list
                }
                "system_info" -> {
                    val info = json.decodeFromJsonElement<SystemInfo>(envelope.payload)
                    current.copy(systemInfo = info)
                }
                "claude_update" -> {
                    val delta = json.decodeFromJsonElement<ClaudeUpdate>(envelope.payload)
                    current.copy(claude = current.claude.merge(delta))
                }
                "command_ack" -> {
                    val ack = json.decodeFromJsonElement<CommandAck>(envelope.payload)
                    // Cap at 50 entries to prevent unbounded growth.
                    val updated = (current.commandAcks + (ack.id to ack))
                        .let { map -> if (map.size > 50) map.entries.drop(map.size - 50).associate { it.toPair() } else map }
                    current.copy(commandAcks = updated)
                }
                else -> current
            }
        } catch (e: Exception) {
            current // ignore malformed payloads
        }
    }
}

// ── Delta merge helpers ──────────────────────────────────────────────────────
// "null in delta" means "field unchanged", not "field is now null"
// (The agent uses skip_serializing_if = None, so absent fields are unchanged)

fun SystemUpdate.merge(delta: SystemUpdate) = SystemUpdate(
    cpuPercent = delta.cpuPercent ?: cpuPercent,
    cpuFreqMhz = delta.cpuFreqMhz ?: cpuFreqMhz,
    cpuTempC = delta.cpuTempC ?: cpuTempC,
    ramUsedMb = delta.ramUsedMb ?: ramUsedMb,
    ramTotalMb = delta.ramTotalMb ?: ramTotalMb,
    swapUsedMb = delta.swapUsedMb ?: swapUsedMb,
    swapTotalMb = delta.swapTotalMb ?: swapTotalMb,
    netUpBps = delta.netUpBps ?: netUpBps,
    netDownBps = delta.netDownBps ?: netDownBps,
    diskReadBps = delta.diskReadBps ?: diskReadBps,
    diskWriteBps = delta.diskWriteBps ?: diskWriteBps,
    batteryPercent = delta.batteryPercent ?: batteryPercent,
    batteryCharging = delta.batteryCharging ?: batteryCharging,
    uptimeSecs = delta.uptimeSecs ?: uptimeSecs,
)

fun GitUpdate.merge(delta: GitUpdate) = GitUpdate(
    repoName = delta.repoName ?: repoName,
    repoPath = delta.repoPath ?: repoPath,
    branch = delta.branch ?: branch,
    remoteBranch = delta.remoteBranch ?: remoteBranch,
    ahead = delta.ahead ?: ahead,
    behind = delta.behind ?: behind,
    modified = delta.modified ?: modified,
    staged = delta.staged ?: staged,
    deleted = delta.deleted ?: deleted,
    untracked = delta.untracked ?: untracked,
    commits = delta.commits ?: commits,
)

fun MusicUpdate.merge(delta: MusicUpdate) = MusicUpdate(
    player = delta.player ?: player,
    title = delta.title ?: title,
    artist = delta.artist ?: artist,
    album = delta.album ?: album,
    albumArtB64 = delta.albumArtB64 ?: albumArtB64,
    durationMs = delta.durationMs ?: durationMs,
    positionMs = delta.positionMs ?: positionMs,
    volume = delta.volume ?: volume,
    state = delta.state ?: state,
)

fun WindowUpdate.merge(delta: WindowUpdate) = WindowUpdate(
    appName = delta.appName ?: appName,
    windowTitle = delta.windowTitle ?: windowTitle,
    workspaceName = delta.workspaceName ?: workspaceName,
    workspaceNum = delta.workspaceNum ?: workspaceNum,
)

fun VscodeUpdate.merge(delta: VscodeUpdate) = VscodeUpdate(
    workspacePath = delta.workspacePath ?: workspacePath,
    projectName = delta.projectName ?: projectName,
    activeFile = delta.activeFile ?: activeFile,
    branch = delta.branch ?: branch,
)

fun ClaudeUpdate.merge(delta: ClaudeUpdate) = ClaudeUpdate(
    status = delta.status ?: status,
    task = delta.task ?: task,
    currentFile = delta.currentFile ?: currentFile,
    sessionDurationSecs = delta.sessionDurationSecs ?: sessionDurationSecs,
    tokensUsed = delta.tokensUsed ?: tokensUsed,
    tokensMax = delta.tokensMax ?: tokensMax,
    contextPercent = delta.contextPercent ?: contextPercent,
)
