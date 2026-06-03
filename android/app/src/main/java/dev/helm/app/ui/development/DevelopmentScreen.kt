package dev.helm.app.ui.development

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Icon
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
import dev.helm.app.data.model.VscodeUpdate
import dev.helm.app.ui.components.HelmCard
import dev.helm.app.ui.components.StatusBadge
import dev.helm.app.ui.theme.*

@Composable
fun DevelopmentScreen(
    modifier: Modifier = Modifier,
    viewModel: DevelopmentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val vscode = state.vscode
    val git = state.git

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Active project card
        if (vscode.projectName != null || git.repoName != null) {
            ActiveProjectCard(vscode = vscode, gitBranch = git.branch)
        } else {
            NoProjectCard()
        }

        // VS Code workspace info
        if (vscode.workspacePath != null || vscode.activeFile != null) {
            WorkspaceCard(vscode = vscode)
        }
    }
}

@Composable
private fun ActiveProjectCard(
    vscode: VscodeUpdate,
    gitBranch: String?,
    modifier: Modifier = Modifier,
) {
    HelmCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "ACTIVE PROJECT",
                color = HelmTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )

            // Project name
            Text(
                text = vscode.projectName ?: "Unknown project",
                color = HelmTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Workspace path
            vscode.workspacePath?.let { path ->
                Text(
                    text = path,
                    color = HelmTextTertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Branch badge
            val branch = vscode.branch ?: gitBranch
            if (branch != null) {
                StatusBadge(text = branch, color = HelmGit)
            }

            // Current file
            vscode.activeFile?.let { file ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Code,
                        contentDescription = null,
                        tint = HelmTextSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = file,
                        color = HelmTextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceCard(
    vscode: VscodeUpdate,
    modifier: Modifier = Modifier,
) {
    HelmCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "VS CODE WORKSPACE",
                color = HelmTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )

            vscode.workspacePath?.let { path ->
                InfoRow(label = "PATH", value = path)
            }
            vscode.activeFile?.let { file ->
                InfoRow(label = "FILE", value = file)
            }
            vscode.branch?.let { branch ->
                InfoRow(label = "BRANCH", value = branch)
            }
        }
    }
}

@Composable
private fun NoProjectCard(modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.Code,
                    contentDescription = null,
                    tint = HelmTextTertiary,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    "No project detected",
                    color = HelmTextTertiary,
                    fontSize = 14.sp,
                )
                Text(
                    "Open a project in VS Code or a git repo",
                    color = HelmTextTertiary,
                    fontSize = 12.sp,
                )
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
        Text(
            value,
            color = HelmTextPrimary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
