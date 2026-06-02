package dev.helm.app.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dev.helm.app.data.model.*
import dev.helm.app.data.websocket.ConnectionState
import dev.helm.app.ui.components.*
import dev.helm.app.ui.theme.*
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun OverviewScreen(
    windowWidthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HelmBackground)
            .padding(8.dp),
    ) {
        HelmTopStatusBar(
            system = state.system,
            connectionState = connectionState,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Left column — System + Git
            Column(
                modifier = Modifier.weight(3f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SystemCard(system = state.system, modifier = Modifier.weight(1f).fillMaxWidth())
                GitCard(git = state.git, modifier = Modifier.weight(1f).fillMaxWidth())
            }

            // Middle column — Development + Media
            Column(
                modifier = Modifier.weight(3f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DevelopmentCard(
                    vscode = state.vscode,
                    git = state.git,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                MediaCard(music = state.music, modifier = Modifier.weight(1f).fillMaxWidth())
            }

            // Right column — Claude (dominant) + bottom row
            Column(
                modifier = Modifier.weight(6f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ClaudeCard(claude = state.claude, modifier = Modifier.weight(5f).fillMaxWidth())
                Row(
                    modifier = Modifier.weight(3f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EventsCard(
                        events = state.events,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    RecentCommitsCard(
                        commits = state.git.commits ?: emptyList(),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    TerminalStatusCard(
                        terminal = state.terminal,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

// ── Top Status Bar ────────────────────────────────────────────────────────────

@Composable
fun HelmTopStatusBar(
    system: SystemUpdate,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
) {
    var timeText by remember { mutableStateOf(currentTimeString()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            timeText = currentTimeString()
        }
    }

    val (dotColor, connLabel) = when (connectionState) {
        ConnectionState.Connected    -> HelmSuccess to "CONNECTED"
        ConnectionState.Disconnected -> HelmError to "DISCONNECTED"
        else                         -> HelmWarning to "CONNECTING"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(HelmSurface, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Connection status
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(dotColor, CircleShape),
            )
            Text(
                text = connLabel,
                style = HelmMonoSmall,
                color = dotColor,
            )
        }

        // Clock + battery
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            system.batteryPercent?.let { pct ->
                val batColor = when {
                    pct > 50f -> HelmSuccess
                    pct > 20f -> HelmWarning
                    else      -> HelmError
                }
                Text(
                    text = "BAT ${pct.toInt()}%",
                    style = HelmMonoSmall,
                    color = batColor,
                )
                Text("·", color = HelmTextTertiary, fontSize = 11.sp)
            }
            Text(
                text = timeText,
                style = HelmMonoMedium,
                color = HelmTextPrimary,
            )
        }
    }
}

private fun currentTimeString(): String =
    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))

// ── System Card ───────────────────────────────────────────────────────────────

@Composable
fun SystemCard(system: SystemUpdate, modifier: Modifier = Modifier) {
    val cpuVal = system.cpuPercent?.let { "%.0f".format(it) } ?: "--"
    val ramVal = if (system.ramUsedMb != null) formatMb(system.ramUsedMb) else "--"
    val tempVal = system.cpuTempC?.let { "%.0f".format(it) } ?: "--"
    val netVal = system.netDownBps?.let { formatBytesPerSec(it) } ?: "--"

    HelmCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CardLabel("SYSTEM")
            Spacer(Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricItem("CPU", cpuVal, "%", HelmCpu, Modifier.weight(1f))
                MetricItem("RAM", ramVal, "", HelmRam, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricItem("TEMP", tempVal, "°C", HelmTemperature, Modifier.weight(1f))
                MetricItem("NET↓", netVal, "", HelmNetwork, Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, unit: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
            color = HelmTextTertiary,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = value,
                style = HelmMonoLarge,
                color = accent,
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    fontSize = 10.sp,
                    color = HelmTextTertiary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

// ── Git Card ──────────────────────────────────────────────────────────────────

@Composable
fun GitCard(git: GitUpdate, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CardLabel("GIT")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    git.ahead?.let { if (it > 0) StatusBadge("↑$it", HelmSuccess) }
                    git.behind?.let { if (it > 0) StatusBadge("↓$it", HelmWarning) }
                }
            }
            git.branch?.let { branch ->
                Text(
                    text = branch,
                    style = HelmMonoMedium,
                    color = HelmGit,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } ?: Text("No branch", style = HelmMonoMedium, color = HelmTextTertiary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                git.modified?.let { if (it > 0) FileCountChip(count = it, label = "mod", color = HelmWarning) }
                git.staged?.let { if (it > 0) FileCountChip(count = it, label = "stg", color = HelmSuccess) }
                git.deleted?.let { if (it > 0) FileCountChip(count = it, label = "del", color = HelmError) }
                git.untracked?.let { if (it > 0) FileCountChip(count = it, label = "new", color = HelmTextSecondary) }
            }
        }
    }
}

// ── Development Card ──────────────────────────────────────────────────────────

@Composable
fun DevelopmentCard(vscode: VscodeUpdate, git: GitUpdate, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CardLabel("PROJECT")
            Text(
                text = vscode.projectName ?: git.repoName ?: "No project",
                color = HelmTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            git.branch?.let { StatusBadge(text = it, color = HelmGit) }
            vscode.activeFile?.let { file ->
                Text(
                    text = file.substringAfterLast('/'),
                    style = HelmMonoSmall,
                    color = HelmTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            // Dev server status placeholder
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).background(HelmSuccess, CircleShape))
                Text("Dev server", fontSize = 11.sp, color = HelmTextSecondary)
            }
        }
    }
}

// ── Media Card ────────────────────────────────────────────────────────────────

@Composable
fun MediaCard(music: MusicUpdate, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardLabel("MEDIA")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(HelmBorder, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (music.albumArtB64 != null) {
                        AsyncImage(
                            model = "data:image/png;base64,${music.albumArtB64}",
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                        )
                    } else {
                        Icon(
                            Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = HelmMusic,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = music.title ?: "Nothing playing",
                        color = HelmTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = music.artist ?: music.player ?: "",
                        color = HelmTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    music.state?.let { playbackState ->
                        val (icon, color) = when (playbackState) {
                            PlaybackState.Playing -> "▶" to HelmSuccess
                            PlaybackState.Paused  -> "⏸" to HelmWarning
                            PlaybackState.Stopped -> "■" to HelmTextTertiary
                        }
                        Text(text = icon, color = color, fontSize = 11.sp)
                    }
                }
            }
            // Progress bar if position/duration available
            if (music.positionMs != null && music.durationMs != null && music.durationMs > 0) {
                val progress = (music.positionMs.toFloat() / music.durationMs.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = HelmMusic,
                    trackColor = HelmBorder,
                )
            }
        }
    }
}

// ── Claude Card (most visually important) ─────────────────────────────────────

@Composable
fun ClaudeCard(claude: ClaudeUpdate, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Orange left accent stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(HelmClaude),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CardLabel("CLAUDE AI")
                    val isActive = claude.status == "active" || claude.status == "thinking"
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(if (isActive) HelmClaude else HelmTextTertiary, CircleShape),
                        )
                        Text(
                            text = (claude.status ?: "idle").uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.6.sp,
                            color = if (isActive) HelmClaude else HelmTextTertiary,
                        )
                    }
                }

                Text(
                    text = claude.task ?: "No active task",
                    color = if (claude.task != null) HelmTextPrimary else HelmTextTertiary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                claude.currentFile?.let { file ->
                    Text(
                        text = file,
                        style = HelmMonoSmall,
                        color = HelmTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.weight(1f))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    claude.sessionDurationSecs?.let { secs ->
                        ClaudeStatItem("SESSION", formatDuration(secs))
                    }
                    claude.tokensUsed?.let { tokens ->
                        ClaudeStatItem("TOKENS", formatTokens(tokens))
                    }
                    claude.tokensMax?.let { max ->
                        ClaudeStatItem("CTX MAX", formatTokens(max))
                    }
                }

                // Context usage progress
                claude.contextPercent?.let { pct ->
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "CONTEXT",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.6.sp,
                            color = HelmTextTertiary,
                        )
                        Text(
                            "${pct.toInt()}%",
                            style = HelmMonoSmall,
                            color = if (pct > 80f) HelmWarning else HelmClaude,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    // Block-character context bar (JetBrains Mono renders block chars natively)
                    val filled = ((pct / 100f) * 20).roundToInt().coerceIn(0, 20)
                    Text(
                        text = "${"█".repeat(filled)}${"░".repeat(20 - filled)}",
                        style = HelmMonoSmall,
                        color = if (pct > 80f) HelmWarning else HelmClaude,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ClaudeStatItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
            color = HelmTextTertiary,
        )
        Text(
            text = value,
            style = HelmMonoSmall,
            color = HelmTextPrimary,
        )
    }
}

private fun formatDuration(secs: Long): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatTokens(tokens: Long): String = when {
    tokens >= 1_000_000 -> "%.1fM".format(tokens / 1_000_000.0)
    tokens >= 1_000     -> "%.1fK".format(tokens / 1_000.0)
    else                -> tokens.toString()
}

// ── Events Card ───────────────────────────────────────────────────────────────

@Composable
fun EventsCard(events: List<DashboardEvent>, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            CardLabel("EVENTS")
            Spacer(Modifier.height(6.dp))
            if (events.isEmpty()) {
                Text("No events", color = HelmTextTertiary, fontSize = 11.sp)
            } else {
                events.take(8).forEach { event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = event.timeLabel,
                            style = HelmMonoSmall,
                            color = HelmTextTertiary,
                            modifier = Modifier.width(38.dp),
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(event.category.color, CircleShape),
                        )
                        Text(
                            text = event.message,
                            fontSize = 11.sp,
                            color = HelmTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private val EventCategory.color: Color
    get() = when (this) {
        EventCategory.Build  -> HelmSuccess
        EventCategory.Test   -> HelmSuccess
        EventCategory.Claude -> HelmClaude
        EventCategory.Git    -> HelmGit
        EventCategory.System -> HelmNetwork
    }

// ── Recent Commits Card ───────────────────────────────────────────────────────

@Composable
fun RecentCommitsCard(commits: List<CommitInfo>, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CardLabel("COMMITS")
            Spacer(Modifier.height(4.dp))
            if (commits.isEmpty()) {
                Text("No commits", color = HelmTextTertiary, fontSize = 11.sp)
            } else {
                commits.take(5).forEach { commit ->
                    CommitRow(
                        hash = commit.hash,
                        message = commit.message,
                        author = commit.author,
                        timeAgo = formatRelativeTime(commit.ts),
                    )
                }
            }
        }
    }
}

// ── Terminal Status Card ──────────────────────────────────────────────────────

@Composable
fun TerminalStatusCard(terminal: TerminalStatus, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CardLabel("TERMINAL")
            terminal.lastCommand?.let { cmd ->
                Text(
                    text = "$ $cmd",
                    style = HelmMonoSmall,
                    color = HelmTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } ?: Text(
                "No recent command",
                style = HelmMonoSmall,
                color = HelmTextTertiary,
            )
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (terminal.running) HelmSuccess else HelmTextTertiary, CircleShape),
                )
                Text(
                    text = if (terminal.running) "Running" else "Idle",
                    fontSize = 11.sp,
                    color = HelmTextSecondary,
                )
                terminal.elapsedSecs?.let { secs ->
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatDuration(secs),
                        style = HelmMonoSmall,
                        color = HelmTextTertiary,
                    )
                }
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun CardLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
        color = HelmTextTertiary,
    )
}
