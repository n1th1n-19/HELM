package dev.helm.app.ui.claude

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.helm.app.data.model.ClaudeUpdate
import dev.helm.app.ui.components.HelmCard
import dev.helm.app.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun ClaudeScreen(
    modifier: Modifier = Modifier,
    viewModel: ClaudeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val claude = state.claude

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(HelmBackground)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Left panel — stats and context
        Column(
            modifier = Modifier.weight(4f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClaudeStatusPanel(claude = claude, modifier = Modifier.weight(1f).fillMaxWidth())
            ClaudeContextPanel(claude = claude, modifier = Modifier.weight(1f).fillMaxWidth())
        }

        // Right panel — task, file info, account usage
        Column(
            modifier = Modifier.weight(8f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClaudeTaskPanel(claude = claude, modifier = Modifier.fillMaxWidth())
            ClaudeSessionPanel(claude = claude, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ── Left panels ───────────────────────────────────────────────────────────────

@Composable
private fun ClaudeStatusPanel(claude: ClaudeUpdate, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenLabel("STATUS")

            val isActive = claude.status == "active" || claude.status == "thinking"
            val statusColor = when (claude.status) {
                "active"   -> HelmSuccess
                "thinking" -> HelmClaude
                "idle"     -> HelmTextTertiary
                else       -> HelmTextTertiary
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(statusColor, CircleShape))
                Text(
                    text = (claude.status ?: "idle").replaceFirstChar { it.uppercaseChar() },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                )
            }

            Spacer(Modifier.weight(1f))

            // Token usage
            StatRow("Tokens Used", claude.tokensUsed?.let { formatTokens(it) } ?: "--")
            StatRow("Context Max", claude.tokensMax?.let { formatTokens(it) } ?: "--")
            StatRow("Session", claude.sessionDurationSecs?.let { formatDuration(it) } ?: "--")
        }
    }
}

@Composable
private fun ClaudeContextPanel(claude: ClaudeUpdate, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScreenLabel("CONTEXT USAGE")

            val pct = claude.contextPercent ?: 0f
            val barColor = if (pct > 80f) HelmWarning else HelmClaude

            Text(
                text = "${pct.toInt()}%",
                style = HelmMonoLarge,
                color = barColor,
            )

            // Block progress bar — 20 characters wide
            val filled = ((pct / 100f) * 20).roundToInt().coerceIn(0, 20)
            Text(
                text = "${"█".repeat(filled)}${"░".repeat(20 - filled)}",
                style = HelmMonoSmall,
                color = barColor,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))

            // Warning at high usage
            if (pct > 80f) {
                Text(
                    text = "Context window nearly full",
                    fontSize = 10.sp,
                    color = HelmWarning,
                    letterSpacing = 0.3.sp,
                )
            }
        }
    }
}

// ── Right panels ──────────────────────────────────────────────────────────────

@Composable
private fun ClaudeTaskPanel(claude: ClaudeUpdate, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .heightIn(min = 80.dp)
                    .background(HelmClaude),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ScreenLabel("CURRENT TASK")
                Text(
                    text = claude.task ?: "No active task",
                    color = if (claude.task != null) HelmTextPrimary else HelmTextTertiary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                claude.currentFile?.let { file ->
                    HorizontalDivider(color = HelmBorder, thickness = 1.dp)
                    Text(
                        text = file,
                        style = HelmMonoSmall,
                        color = HelmTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClaudeSessionPanel(claude: ClaudeUpdate, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScreenLabel("SESSION DETAILS")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                DetailItem("Session Duration", claude.sessionDurationSecs?.let { formatDuration(it) } ?: "--")
                DetailItem("Tokens Used", claude.tokensUsed?.let { formatTokens(it) } ?: "--")
                DetailItem("Context Max", claude.tokensMax?.let { formatTokens(it) } ?: "--")
                DetailItem("Context %", claude.contextPercent?.let { "${it.toInt()}%" } ?: "--")
            }
        }
    }
}

@Composable
private fun ClaudeAccountPanel(claude: ClaudeUpdate, modifier: Modifier = Modifier) {
    if (claude.totalSessions == null && claude.totalOutputTokens == null) return
    HelmCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScreenLabel("ACCOUNT USAGE")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                DetailItem("Sessions", claude.totalSessions?.toString() ?: "--")
                DetailItem("Output Tokens", claude.totalOutputTokens?.let { formatTokens(it) } ?: "--")
                DetailItem("Cache Created", claude.totalCacheCreationTokens?.let { formatTokens(it) } ?: "--")
                DetailItem("Cache Read", claude.totalCacheReadTokens?.let { formatTokens(it) } ?: "--")
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun ScreenLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
        color = HelmTextTertiary,
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 11.sp,
            color = HelmTextSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            style = HelmMonoSmall,
            color = HelmTextPrimary,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
            color = HelmTextTertiary,
        )
        Text(value, style = HelmMonoMedium, color = HelmTextPrimary)
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
