package dev.helm.app.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.helm.app.data.model.AccountUpdate
import dev.helm.app.ui.components.HelmCard
import dev.helm.app.ui.theme.*

@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val account = state.account

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccountInfoCard(account)
        UsageLimitsCard(account)
        ActivityCard(account)
    }
}

@Composable
private fun AccountInfoCard(account: AccountUpdate) {
    HelmCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("ACCOUNT")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                InfoItem("Email", account.email ?: "--")
                InfoItem("Plan", account.plan ?: "--")
                account.rateLimitTier?.let { InfoItem("Tier", it) }
            }
        }
    }
}

@Composable
private fun UsageLimitsCard(account: AccountUpdate) {
    HelmCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionLabel("USAGE LIMITS")

            UsageBar(
                label = "Session (5hr)",
                pct = account.sessionUsedPct,
                resetSecs = account.sessionResetSecs,
            )
            UsageBar(
                label = "Weekly (7 day)",
                pct = account.weeklyUsedPct,
                resetSecs = account.weeklyResetSecs,
            )
        }
    }
}

@Composable
private fun UsageBar(label: String, pct: Float?, resetSecs: Long?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = HelmTextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pct != null) {
                    Text(
                        "${pct.toInt()}%",
                        color = if (pct > 80f) HelmWarning else HelmClaude,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (resetSecs != null) {
                    Text(
                        "Resets in ${formatReset(resetSecs)}",
                        color = HelmTextTertiary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        val fraction = (pct ?: 0f) / 100f
        val barColor = if ((pct ?: 0f) > 80f) HelmWarning else HelmClaude
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = barColor,
            trackColor = HelmBorder,
        )
    }
}

@Composable
private fun ActivityCard(account: AccountUpdate) {
    HelmCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("LOCAL ACTIVITY")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                InfoItem("Today msgs", account.todayMessages?.toString() ?: "--")
                InfoItem("Today sessions", account.todaySessions?.toString() ?: "--")
                InfoItem("Week msgs", account.weekMessages?.toString() ?: "--")
                InfoItem("Week sessions", account.weekSessions?.toString() ?: "--")
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
        color = HelmTextTertiary,
    )
}

@Composable
private fun InfoItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 9.sp, color = HelmTextTertiary, letterSpacing = 0.6.sp)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HelmTextPrimary)
    }
}

private fun formatReset(secs: Long): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else  -> "<1m"
    }
}
