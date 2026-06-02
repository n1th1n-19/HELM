//! Git collector: repository state for the active workspace.
//!
//! Watches the workspace path (from `state.vscode.workspace_path`, set by
//! `workspace.rs`) or falls back to the current working directory. Uses a
//! `notify` watcher on `.git/HEAD` and `.git/index` for instant updates on
//! commit/checkout/stage, plus a 5s poll as a fallback.

use crate::config::HelmConfig;
use crate::protocol::{CommitInfo, GitUpdate};
use crate::state::{SharedState, StateTx};
use git2::{BranchType, Repository, StatusOptions};
use notify::{Config as NotifyConfig, RecommendedWatcher, RecursiveMode, Watcher};
use std::path::{Path, PathBuf};
use std::time::Duration;
use tokio::sync::mpsc;
use tokio::time;
use tracing::{debug, warn};

fn collect_git(repo_path: String) -> anyhow::Result<GitUpdate> {
    let repo = Repository::discover(&repo_path)?;
    let workdir = repo
        .workdir()
        .ok_or_else(|| anyhow::anyhow!("bare repo"))?;
    let repo_name = workdir
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("unknown")
        .to_string();
    let repo_path_str = workdir.to_string_lossy().to_string();

    // Branch
    let head = repo.head()?;
    let branch = head.shorthand().unwrap_or("HEAD").to_string();

    // Remote tracking branch and ahead/behind
    let (remote_branch, ahead, behind) = get_tracking_info(&repo, &branch)?;

    // Working tree status
    let mut status_opts = StatusOptions::new();
    status_opts.include_untracked(true).recurse_untracked_dirs(false);
    let statuses = repo.statuses(Some(&mut status_opts))?;
    let mut modified = 0u32;
    let mut staged = 0u32;
    let mut deleted = 0u32;
    let mut untracked = 0u32;
    for entry in statuses.iter() {
        let s = entry.status();
        if s.contains(git2::Status::INDEX_NEW)
            || s.contains(git2::Status::INDEX_MODIFIED)
            || s.contains(git2::Status::INDEX_RENAMED)
            || s.contains(git2::Status::INDEX_TYPECHANGE)
        {
            staged += 1;
        }
        // Count deleted once per entry regardless of which side (index or worktree).
        if s.contains(git2::Status::INDEX_DELETED) || s.contains(git2::Status::WT_DELETED) {
            deleted += 1;
        }
        if s.contains(git2::Status::WT_MODIFIED)
            || s.contains(git2::Status::WT_RENAMED)
            || s.contains(git2::Status::WT_TYPECHANGE)
        {
            modified += 1;
        }
        if s.contains(git2::Status::WT_NEW) {
            untracked += 1;
        }
    }

    // Last 10 commits
    let mut revwalk = repo.revwalk()?;
    revwalk.push_head()?;
    revwalk.set_sorting(git2::Sort::TIME)?;
    let commits: Vec<CommitInfo> = revwalk
        .take(10)
        .filter_map(|oid| oid.ok())
        .filter_map(|oid| repo.find_commit(oid).ok())
        .map(|c| {
            let full = c.id().to_string();
            let hash = full.chars().take(7).collect::<String>();
            let message = c.summary().unwrap_or("").to_string();
            let author = c.author().name().unwrap_or("unknown").to_string();
            let ts = c.time().seconds() * 1000; // convert to ms
            CommitInfo {
                hash,
                message,
                author,
                ts,
            }
        })
        .collect();

    Ok(GitUpdate {
        repo_name: Some(repo_name),
        repo_path: Some(repo_path_str),
        branch: Some(branch),
        remote_branch,
        ahead,
        behind,
        modified: Some(modified),
        staged: Some(staged),
        deleted: Some(deleted),
        untracked: Some(untracked),
        commits: Some(commits),
    })
}

fn get_tracking_info(
    repo: &Repository,
    branch_name: &str,
) -> anyhow::Result<(Option<String>, Option<i32>, Option<i32>)> {
    let branch = match repo.find_branch(branch_name, BranchType::Local) {
        Ok(b) => b,
        Err(_) => return Ok((None, Some(0), Some(0))),
    };
    let upstream = match branch.upstream() {
        Ok(u) => u,
        Err(_) => return Ok((None, Some(0), Some(0))),
    };
    let upstream_name = upstream.name()?.map(|s| s.to_string());
    let local_oid = repo.head()?.target().unwrap_or_else(git2::Oid::zero);
    let upstream_oid = upstream.get().target().unwrap_or_else(git2::Oid::zero);
    let (ahead, behind) = repo.graph_ahead_behind(local_oid, upstream_oid)?;
    Ok((upstream_name, Some(ahead as i32), Some(behind as i32)))
}

/// Resolve the repo path to watch: prefer the VS Code workspace path, fall
/// back to the agent's current working directory.
async fn resolve_repo_path(state: &SharedState) -> String {
    let from_workspace = {
        let s = state.read().await;
        s.vscode.workspace_path.clone()
    };
    from_workspace.unwrap_or_else(|| {
        std::env::current_dir()
            .map(|p| p.to_string_lossy().to_string())
            .unwrap_or_else(|_| ".".to_string())
    })
}

/// Find the `.git` directory for a working tree path so we can watch its
/// internal files. Returns `None` if no repo is discoverable.
fn git_dir_for(repo_path: &str) -> Option<PathBuf> {
    let repo = Repository::discover(repo_path).ok()?;
    Some(repo.path().to_path_buf())
}

/// Build a notify watcher that forwards relevant `.git` change events into an
/// mpsc channel. The watcher is returned so the caller can keep it alive.
fn make_watcher(
    git_dir: &Path,
    tx: mpsc::UnboundedSender<()>,
) -> anyhow::Result<RecommendedWatcher> {
    let mut watcher = RecommendedWatcher::new(
        move |res: notify::Result<notify::Event>| {
            if let Ok(event) = res {
                let relevant = event.paths.iter().any(|p| {
                    matches!(
                        p.file_name().and_then(|n| n.to_str()),
                        Some("HEAD") | Some("index")
                    )
                });
                if relevant {
                    let _ = tx.send(());
                }
            }
        },
        NotifyConfig::default(),
    )?;
    // Watch the .git dir non-recursively; HEAD and index live at its root.
    watcher.watch(git_dir, RecursiveMode::NonRecursive)?;
    Ok(watcher)
}

async fn update_state(state: &SharedState, tx: &StateTx, repo_path: &str) {
    let path = repo_path.to_string();
    let update = match tokio::task::spawn_blocking(move || collect_git(path)).await {
        Ok(Ok(u)) => u,
        Ok(Err(e)) => {
            debug!("git collect failed for {repo_path}: {e}");
            GitUpdate::default()
        }
        Err(e) => {
            debug!("git spawn_blocking panicked: {e}");
            GitUpdate::default()
        }
    };
    {
        let mut s = state.write().await;
        s.git = update;
    }
    let _ = tx.send(());
}

pub async fn run(state: SharedState, tx: StateTx, _cfg: HelmConfig) {
    let mut ticker = time::interval(Duration::from_secs(5));
    ticker.set_missed_tick_behavior(time::MissedTickBehavior::Skip);

    let mut current_path = resolve_repo_path(&state).await;
    let mut current_git_dir = git_dir_for(&current_path);

    // Channel for notify events; rebuilt whenever the watched repo changes.
    let (evt_tx, mut evt_rx) = mpsc::unbounded_channel();
    let mut _watcher: Option<RecommendedWatcher> = match &current_git_dir {
        Some(d) => match make_watcher(d, evt_tx.clone()) {
            Ok(w) => Some(w),
            Err(e) => {
                warn!("failed to watch {}: {e}", d.display());
                None
            }
        },
        None => None,
    };

    // Initial collect.
    update_state(&state, &tx, &current_path).await;

    loop {
        tokio::select! {
            _ = ticker.tick() => {
                // Re-resolve the path in case the active workspace changed.
                let new_path = resolve_repo_path(&state).await;
                if new_path != current_path {
                    current_path = new_path;
                    current_git_dir = git_dir_for(&current_path);
                    // Rebuild the watcher for the new repo.
                    _watcher = match &current_git_dir {
                        Some(d) => match make_watcher(d, evt_tx.clone()) {
                            Ok(w) => Some(w),
                            Err(e) => {
                                warn!("failed to watch {}: {e}", d.display());
                                None
                            }
                        },
                        None => None,
                    };
                }
                update_state(&state, &tx, &current_path).await;
            }
            Some(()) = evt_rx.recv() => {
                // Drain any burst of events so we collect once per quiescent change.
                while evt_rx.try_recv().is_ok() {}
                update_state(&state, &tx, &current_path).await;
            }
        }
    }
}
