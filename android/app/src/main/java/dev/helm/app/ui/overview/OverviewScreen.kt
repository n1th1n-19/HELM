package dev.helm.app.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dev.helm.app.data.model.*
import dev.helm.app.ui.components.*
import dev.helm.app.ui.theme.*

@Composable
fun OverviewScreen(
    windowWidthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    val columns = when (windowWidthSizeClass) {
        WindowWidthSizeClass.Compact -> 1
        WindowWidthSizeClass.Medium  -> 2
        else                         -> 3 // Expanded
    }

    Column(modifier = modifier.fillMaxSize()) {
        ConnectionBanner(connectionState = connectionState)

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Top metrics row — always spans all columns
            item(span = { GridItemSpan(maxLineSpan) }) {
                TopMetricsRow(system = state.system)
            }

            // Project card (VS Code workspace)
            item {
                ProjectCard(vscode = state.vscode, git = state.git)
            }

            // Git status card
            item {
                GitStatusCard(git = state.git)
            }

            // Music card
            item {
                MusicCard(music = state.music)
            }

            // Active window card
            item {
                ActiveWindowCard(window = state.window)
            }

            // Recent commits card — spans 2 cols in medium/expanded
            item(span = { GridItemSpan(if (maxLineSpan >= 2) 2 else 1) }) {
                RecentCommitsCard(commits = state.git.commits ?: emptyList())
            }

            // System info card
            item {
                SystemInfoCard(info = state.systemInfo)
            }
        }
    }
}

@Composable
fun TopMetricsRow(system: SystemUpdate, modifier: Modifier = Modifier) {
    val cpuText = system.cpuPercent?.let { "%.0f".format(it) } ?: "--"
    val ramText = if (system.ramUsedMb != null && system.ramTotalMb != null)
        formatMb(system.ramUsedMb) else "--"
    val tempText = system.cpuTempC?.let { "%.0f".format(it) } ?: "--"
    val netUp = system.netUpBps?.let { formatBytesPerSec(it) } ?: "--"
    val netDown = system.netDownBps?.let { formatBytesPerSec(it) } ?: "--"
    val batteryText = system.batteryPercent?.let { "%.0f".format(it) } ?: "--"

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetricCard(label = "CPU", value = cpuText, unit = "%", accent = HelmCpu, modifier = Modifier.weight(1f))
        MetricCard(label = "RAM", value = ramText, accent = HelmRam, modifier = Modifier.weight(1f))
        MetricCard(label = "TEMP", value = tempText, unit = "°C", accent = HelmTemperature, modifier = Modifier.weight(1f))
        MetricCard(label = "↑", value = netUp, accent = HelmNetwork, modifier = Modifier.weight(1f))
        MetricCard(label = "↓", value = netDown, accent = HelmNetwork, modifier = Modifier.weight(1f))
        MetricCard(label = "BAT", value = batteryText, unit = "%", accent = HelmSuccess, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ProjectCard(vscode: VscodeUpdate, git: GitUpdate, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "PROJECT",
                color = HelmTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )
            Text(
                text = vscode.projectName ?: git.repoName ?: "No project",
                color = HelmTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            git.branch?.let { branch ->
                StatusBadge(text = branch, color = HelmGit)
            }
            vscode.activeFile?.let { file ->
                Text(
                    text = file.substringAfterLast('/'),
                    color = HelmTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun GitStatusCard(git: GitUpdate, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "GIT",
                    color = HelmTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    git.ahead?.let { if (it > 0) StatusBadge("↑$it", HelmSuccess) }
                    git.behind?.let { if (it > 0) StatusBadge("↓$it", HelmWarning) }
                }
            }
            Text(
                text = git.branch ?: "No branch",
                color = HelmTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                git.modified?.let { if (it > 0) FileCountChip(count = it, label = "modified", color = HelmWarning) }
                git.staged?.let { if (it > 0) FileCountChip(count = it, label = "staged", color = HelmSuccess) }
                git.deleted?.let { if (it > 0) FileCountChip(count = it, label = "deleted", color = HelmError) }
                git.untracked?.let { if (it > 0) FileCountChip(count = it, label = "untracked", color = HelmTextSecondary) }
            }
        }
    }
}

@Composable
fun MusicCard(music: MusicUpdate, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Album art or placeholder
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(HelmBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (music.albumArtB64 != null) {
                    AsyncImage(
                        model = "data:image/png;base64,${music.albumArtB64}",
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Icon(
                        Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = HelmMusic,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "MUSIC",
                    color = HelmTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    text = music.title ?: "Nothing playing",
                    color = HelmTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = music.artist ?: music.player ?: "",
                    color = HelmTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                music.state?.let { playbackState ->
                    val (icon, color) = when (playbackState) {
                        PlaybackState.Playing -> "▶" to HelmSuccess
                        PlaybackState.Paused  -> "⏸" to HelmWarning
                        PlaybackState.Stopped -> "■" to HelmTextTertiary
                    }
                    Text(text = icon, color = color, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ActiveWindowCard(window: WindowUpdate, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "ACTIVE WINDOW",
                color = HelmTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )
            Text(
                text = window.appName ?: "Unknown",
                color = HelmTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = window.windowTitle ?: "",
                color = HelmTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            window.workspaceNum?.let {
                StatusBadge(text = "WS $it", color = HelmNetwork)
            }
        }
    }
}

@Composable
fun RecentCommitsCard(commits: List<CommitInfo>, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "RECENT COMMITS",
                color = HelmTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )
            if (commits.isEmpty()) {
                Text("No commits", color = HelmTextTertiary, fontSize = 12.sp)
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

@Composable
fun SystemInfoCard(info: SystemInfo?, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "SYSTEM",
                color = HelmTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )
            if (info == null) {
                Text("Waiting for agent...", color = HelmTextTertiary, fontSize = 12.sp)
            } else {
                InfoRow("OS", info.os)
                InfoRow("KERNEL", info.kernel)
                info.de?.let { InfoRow("DE", it) }
                info.shell?.let { InfoRow("SHELL", it) }
                info.resolution?.let { InfoRow("RES", it) }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            color = HelmTextTertiary,
            fontSize = 11.sp,
            modifier = Modifier.width(56.dp),
        )
        Text(value, color = HelmTextPrimary, fontSize = 11.sp)
    }
}
