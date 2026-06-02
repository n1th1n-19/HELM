package dev.helm.app.ui.git

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.helm.app.data.model.GitUpdate
import dev.helm.app.ui.components.*
import dev.helm.app.ui.theme.*

@Composable
fun GitScreen(
    modifier: Modifier = Modifier,
    viewModel: GitViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val git = state.git

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BranchSection(git = git) }
        item { WorkingTreeSection(git = git) }

        val commits = git.commits
        if (!commits.isNullOrEmpty()) {
            item {
                Text(
                    "COMMITS",
                    color = HelmTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            items(commits) { commit ->
                HelmCard(modifier = Modifier.fillMaxWidth()) {
                    CommitRow(
                        hash = commit.hash,
                        message = commit.message,
                        author = commit.author,
                        timeAgo = formatRelativeTime(commit.ts),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BranchSection(git: GitUpdate, modifier: Modifier = Modifier) {
    HelmCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "GIT",
                color = HelmTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )

            // Repo name
            git.repoName?.let { name ->
                Text(
                    text = name,
                    color = HelmGit,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Local branch (large)
            Text(
                text = git.branch ?: "No branch",
                color = HelmTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Remote branch (smaller)
            git.remoteBranch?.let { remote ->
                Text(
                    text = remote,
                    color = HelmTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Ahead / behind badges
            val ahead = git.ahead ?: 0
            val behind = git.behind ?: 0
            if (ahead > 0 || behind > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ahead > 0) StatusBadge(text = "↑ $ahead ahead", color = HelmSuccess)
                    if (behind > 0) StatusBadge(text = "↓ $behind behind", color = HelmWarning)
                }
            }
        }
    }
}

@Composable
private fun WorkingTreeSection(git: GitUpdate, modifier: Modifier = Modifier) {
    val modified = git.modified ?: 0
    val staged = git.staged ?: 0
    val deleted = git.deleted ?: 0
    val untracked = git.untracked ?: 0

    // Only show if there's anything to show
    if (modified == 0 && staged == 0 && deleted == 0 && untracked == 0) return

    HelmCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "WORKING TREE",
                color = HelmTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (modified > 0) FileCountChip(count = modified, label = "modified", color = HelmWarning)
                if (staged > 0) FileCountChip(count = staged, label = "staged", color = HelmSuccess)
                if (deleted > 0) FileCountChip(count = deleted, label = "deleted", color = HelmError)
                if (untracked > 0) FileCountChip(count = untracked, label = "untracked", color = HelmTextSecondary)
            }
        }
    }
}
