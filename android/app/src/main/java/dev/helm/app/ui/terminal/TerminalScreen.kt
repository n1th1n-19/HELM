package dev.helm.app.ui.terminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.helm.app.data.model.CommandAction
import dev.helm.app.data.model.CommandAck
import dev.helm.app.ui.components.HelmCard
import dev.helm.app.ui.theme.*

private data class ActionButton(
    val label: String,
    val action: CommandAction,
    val needsPortDialog: Boolean = false,
    val isDangerous: Boolean = false,
)

private val quickActions = listOf(
    ActionButton("Restart Dev Server", CommandAction.RestartDevServer, needsPortDialog = true),
    ActionButton("Git Pull", CommandAction.GitPull),
    ActionButton("Git Push", CommandAction.GitPush),
    ActionButton("Open Terminal", CommandAction.OpenTerminal),
    ActionButton("Open Project", CommandAction.OpenProject),
)

private val systemActions = listOf(
    ActionButton("Lock Screen", CommandAction.Lock),
    ActionButton("Suspend", CommandAction.Suspend, isDangerous = true),
    ActionButton("Reboot", CommandAction.Reboot, isDangerous = true),
    ActionButton("Shutdown", CommandAction.Shutdown, isDangerous = true),
)

@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val lastAck by viewModel.lastAck.collectAsState()

    // Port dialog state
    var showPortDialog by remember { mutableStateOf(false) }
    var portInput by remember { mutableStateOf("3000") }

    // Dangerous action confirmation state
    var pendingDangerousAction by remember { mutableStateOf<ActionButton?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Quick Actions
        HelmCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "QUICK ACTIONS",
                    color = HelmTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                )
                ActionGrid(
                    actions = quickActions,
                    onAction = { btn ->
                        if (btn.needsPortDialog) {
                            showPortDialog = true
                        } else {
                            viewModel.executeCommand(btn.action)
                        }
                    },
                )
            }
        }

        // System Actions
        HelmCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "SYSTEM ACTIONS",
                    color = HelmError,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                )
                ActionGrid(
                    actions = systemActions,
                    onAction = { btn ->
                        if (btn.isDangerous) {
                            pendingDangerousAction = btn
                        } else {
                            viewModel.executeCommand(btn.action)
                        }
                    },
                    dangerous = true,
                )
            }
        }

        // Command status
        lastAck?.let { ack ->
            CommandStatusCard(ack = ack)
        }
    }

    // Port dialog for Restart Dev Server
    if (showPortDialog) {
        AlertDialog(
            onDismissRequest = { showPortDialog = false },
            containerColor = HelmCard,
            title = {
                Text("Restart Dev Server", color = HelmTextPrimary, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the port to restart:", color = HelmTextSecondary, fontSize = 14.sp)
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it },
                        label = { Text("Port", color = HelmTextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = HelmTextPrimary,
                            unfocusedTextColor = HelmTextPrimary,
                            focusedBorderColor = HelmCpu,
                            unfocusedBorderColor = HelmBorder,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.executeCommand(
                            CommandAction.RestartDevServer,
                            mapOf("port" to portInput),
                        )
                        showPortDialog = false
                    }
                ) {
                    Text("Restart", color = HelmCpu, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPortDialog = false }) {
                    Text("Cancel", color = HelmTextSecondary)
                }
            },
        )
    }

    // Dangerous action confirmation dialog
    pendingDangerousAction?.let { btn ->
        AlertDialog(
            onDismissRequest = { pendingDangerousAction = null },
            containerColor = HelmCard,
            title = {
                Text("Confirm: ${btn.label}", color = HelmError, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    "Are you sure you want to ${btn.label.lowercase()}? This cannot be undone.",
                    color = HelmTextSecondary,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.executeCommand(btn.action)
                        pendingDangerousAction = null
                    }
                ) {
                    Text(btn.label, color = HelmError, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDangerousAction = null }) {
                    Text("Cancel", color = HelmTextSecondary)
                }
            },
        )
    }
}

@Composable
private fun ActionGrid(
    actions: List<ActionButton>,
    onAction: (ActionButton) -> Unit,
    dangerous: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val accent = if (dangerous) HelmError else HelmCpu
    // 2-column grid using chunked rows
    actions.chunked(2).forEach { row ->
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { btn ->
                Button(
                    onClick = { onAction(btn) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent.copy(alpha = 0.15f),
                        contentColor = accent,
                    ),
                ) {
                    Text(
                        text = btn.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
            // Fill remaining cell if odd number
            if (row.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CommandStatusCard(ack: CommandAck, modifier: Modifier = Modifier) {
    val (color, prefix) = if (ack.success) HelmSuccess to "OK" else HelmError to "ERR"
    HelmCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = prefix,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = ack.message ?: if (ack.success) "Command executed" else "Command failed",
                color = HelmTextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}
