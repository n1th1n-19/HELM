package dev.helm.app.data.repository

import dev.helm.app.data.model.ClaudeUpdate
import dev.helm.app.data.model.CommandAck
import dev.helm.app.data.model.DashboardEvent
import dev.helm.app.data.model.EventCategory
import dev.helm.app.data.model.GitUpdate
import dev.helm.app.data.model.HelmCommand
import dev.helm.app.data.model.HelmEnvelope
import dev.helm.app.data.model.HelmState
import dev.helm.app.data.model.MusicUpdate
import dev.helm.app.data.model.PlaybackState
import dev.helm.app.data.model.ProcessUpdate
import dev.helm.app.data.model.SystemInfo
import dev.helm.app.data.model.SystemUpdate
import dev.helm.app.data.model.TerminalStatus
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_EVENTS = 50

@Singleton
class HelmRepository @Inject constructor(
    private val connectionManager: ConnectionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _state = MutableStateFlow(HelmState())
    val state: StateFlow<HelmState> = _state.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState
    val lastError: StateFlow<String?> = connectionManager.lastError

    init {
        scope.launch {
            connectionManager.messages.collect { envelope ->
                _state.update { applyDelta(it, envelope) }
            }
        }
    }

    fun connect() = connectionManager.start()
    fun disconnect() = connectionManager.stop()
    fun reconnect() { connectionManager.stop(); connectionManager.start() }

    /** Called by TerminalViewModel before sendCommand so the Overview shows the running state. */
    fun startCommand(label: String) {
        _state.update { it.copy(terminal = TerminalStatus(lastCommand = label, running = true)) }
    }

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
            val newEvents = deriveEvents(current, envelope)
            val next = when (envelope.type) {
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
                    current.copy(process = delta)
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
                    val updated = (current.commandAcks + (ack.id to ack))
                        .let { map -> if (map.size > 50) map.entries.drop(map.size - 50).associate { it.toPair() } else map }
                    current.copy(
                        commandAcks = updated,
                        terminal = current.terminal.copy(running = false),
                    )
                }
                else -> current
            }
            if (newEvents.isEmpty()) next
            else next.copy(events = (newEvents + next.events).take(MAX_EVENTS))
        } catch (e: Exception) {
            current
        }
    }

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private fun deriveEvents(current: HelmState, envelope: HelmEnvelope): List<DashboardEvent> {
        val t = LocalTime.now().format(timeFmt)
        return try {
            when (envelope.type) {
                "claude_update" -> {
                    val delta = json.decodeFromJsonElement<ClaudeUpdate>(envelope.payload)
                    val prevStatus = current.claude.status
                    val nextStatus = delta.status ?: prevStatus
                    when {
                        prevStatus != "active" && nextStatus == "active" ->
                            listOf(DashboardEvent(t, "Claude session started", EventCategory.Claude))
                        prevStatus == "active" && (nextStatus == "idle" || nextStatus == null) ->
                            listOf(DashboardEvent(t, "Claude session ended", EventCategory.Claude))
                        else -> emptyList()
                    }
                }
                "git_update" -> {
                    val delta = json.decodeFromJsonElement<GitUpdate>(envelope.payload)
                    buildList {
                        if (delta.branch != null && delta.branch != current.git.branch && current.git.branch != null)
                            add(DashboardEvent(t, "Switched to ${delta.branch}", EventCategory.Git))
                        val newHead = delta.commits?.firstOrNull()?.hash
                        val oldHead = current.git.commits?.firstOrNull()?.hash
                        if (newHead != null && newHead != oldHead && oldHead != null)
                            add(DashboardEvent(t, delta.commits!!.first().message.take(50), EventCategory.Git))
                    }
                }
                "command_ack" -> {
                    val ack = json.decodeFromJsonElement<CommandAck>(envelope.payload)
                    val label = current.terminal.lastCommand ?: "Command"
                    val msg = if (ack.success) "$label OK" else "$label failed: ${ack.message ?: "error"}"
                    val cat = if (ack.success) EventCategory.System else EventCategory.System
                    listOf(DashboardEvent(t, msg, cat))
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
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

fun MusicUpdate.merge(delta: MusicUpdate): MusicUpdate {
    val merged = MusicUpdate(
        player = delta.player ?: player,
        state = delta.state ?: state,
        title = delta.title ?: title,
        artist = delta.artist ?: artist,
        album = delta.album ?: album,
        albumArtB64 = delta.albumArtB64 ?: albumArtB64,
        durationMs = delta.durationMs ?: durationMs,
        positionMs = delta.positionMs ?: positionMs,
        volume = delta.volume ?: volume,
    )
    // If playback stopped or player is gone, clear track metadata so stale
    // song info doesn't linger on screen after the agent sends state=Stopped.
    return if (merged.state == PlaybackState.Stopped || merged.player == null) {
        merged.copy(title = null, artist = null, album = null, albumArtB64 = null, positionMs = null, durationMs = null)
    } else merged
}

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

fun ClaudeUpdate.merge(delta: ClaudeUpdate): ClaudeUpdate {
    val merged = ClaudeUpdate(
        status = delta.status ?: status,
        task = delta.task ?: task,
        currentFile = delta.currentFile ?: currentFile,
        sessionDurationSecs = delta.sessionDurationSecs ?: sessionDurationSecs,
        tokensUsed = delta.tokensUsed ?: tokensUsed,
        tokensMax = delta.tokensMax ?: tokensMax,
        contextPercent = delta.contextPercent ?: contextPercent,
    )
    // When Claude goes idle or status clears, wipe session-specific fields so
    // stale task/file/token data doesn't linger on screen.
    return if (merged.status == "idle" || merged.status == null) {
        merged.copy(task = null, currentFile = null, tokensUsed = null, tokensMax = null, contextPercent = null, sessionDurationSecs = null)
    } else merged
}
