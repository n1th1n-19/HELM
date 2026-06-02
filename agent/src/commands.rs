//! Command handler: validates and executes commands received from Android.
//!
//! Every command is checked against `HelmConfig::allowed_commands` before
//! execution. Dangerous actions (`suspend`, `reboot`, `shutdown`) additionally
//! require an explicit `confirmed = "true"` argument.

use crate::config::HelmConfig;
use crate::protocol::{Command, CommandAck, CommandAction};
use crate::state::SharedState;
use tokio::process::Command as TokioCommand;
use tracing::{info, warn};

/// Map a [`CommandAction`] variant to the string key used in
/// `allowed_commands`.
fn action_name(action: &CommandAction) -> &'static str {
    match action {
        CommandAction::GitPull => "git_pull",
        CommandAction::GitPush => "git_push",
        CommandAction::OpenTerminal => "open_terminal",
        CommandAction::OpenProject => "open_project",
        CommandAction::Lock => "lock",
        CommandAction::Suspend => "suspend",
        CommandAction::Reboot => "reboot",
        CommandAction::Shutdown => "shutdown",
        CommandAction::RestartDevServer => "restart_dev_server",
    }
}

/// Execute a validated command and return an acknowledgement.
pub async fn execute_command(
    cmd: Command,
    cfg: &HelmConfig,
    state: &SharedState,
) -> CommandAck {
    let name = action_name(&cmd.action);

    // ── Permission check ──────────────────────────────────────────────
    if !cfg.allowed_commands.iter().any(|a| a == name) {
        warn!(action = name, "command blocked by allowed_commands policy");
        return CommandAck {
            id: cmd.id,
            success: false,
            message: Some(format!("command '{name}' is not in allowed_commands")),
        };
    }

    info!(action = name, id = %cmd.id, "executing command");

    let args = cmd.args.as_ref();

    let result = match cmd.action {
        CommandAction::GitPull => git_command(state, &["pull"]).await,
        CommandAction::GitPush => git_command(state, &["push"]).await,
        CommandAction::OpenTerminal => open_terminal().await,
        CommandAction::OpenProject => open_project(args, state).await,
        CommandAction::Lock => run_simple(&["loginctl", "lock-session"]).await,
        CommandAction::Suspend => guarded_power(args, "systemctl", "suspend").await,
        CommandAction::Reboot => guarded_power(args, "systemctl", "reboot").await,
        CommandAction::Shutdown => guarded_power(args, "systemctl", "poweroff").await,
        CommandAction::RestartDevServer => restart_dev_server(args).await,
    };

    match result {
        Ok(msg) => CommandAck {
            id: cmd.id,
            success: true,
            message: msg,
        },
        Err(e) => CommandAck {
            id: cmd.id,
            success: false,
            message: Some(e),
        },
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

type CmdResult = Result<Option<String>, String>;
type Args<'a> = Option<&'a std::collections::HashMap<String, String>>;

/// Run `git -C <repo_path> <sub_args…>`.
async fn git_command(state: &SharedState, sub_args: &[&str]) -> CmdResult {
    let repo_path = {
        let s = state.read().await;
        s.git
            .repo_path
            .clone()
            .ok_or_else(|| "no git repo_path in current state".to_string())?
    };

    let mut cmd = TokioCommand::new("git");
    cmd.arg("-C").arg(&repo_path);
    for arg in sub_args {
        cmd.arg(arg);
    }
    run_cmd(cmd).await
}

/// Open the user's preferred terminal emulator.
async fn open_terminal() -> CmdResult {
    let terminal = std::env::var("TERMINAL").unwrap_or_else(|_| "xterm".to_string());
    let mut cmd = TokioCommand::new(&terminal);
    // Detach so the terminal lives beyond the agent process.
    cmd.stdin(std::process::Stdio::null())
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null());
    cmd.spawn()
        .map_err(|e| format!("failed to spawn {terminal}: {e}"))?;
    Ok(Some(format!("opened {terminal}")))
}

/// Open a project in VS Code / Codium.
async fn open_project(
    args: Args<'_>,
    state: &SharedState,
) -> CmdResult {
    let path = match args.and_then(|a| a.get("path")) {
        Some(p) => p.clone(),
        None => {
            let s = state.read().await;
            s.vscode
                .workspace_path
                .clone()
                .or_else(|| s.git.repo_path.clone())
                .ok_or_else(|| "no path argument and no workspace in state".to_string())?
        }
    };

    // Try `code` first, fall back to `codium`.
    for editor in &["code", "codium"] {
        let status = TokioCommand::new(editor)
            .arg(&path)
            .stdin(std::process::Stdio::null())
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status()
            .await;
        if let Ok(s) = status {
            if s.success() {
                return Ok(Some(format!("opened {path} with {editor}")));
            }
        }
    }
    Err(format!("neither 'code' nor 'codium' could open {path}"))
}

/// Power-management commands that require `confirmed = "true"`.
async fn guarded_power(
    args: Args<'_>,
    program: &str,
    sub: &str,
) -> CmdResult {
    let confirmed = args
        .and_then(|a| a.get("confirmed"))
        .map(|v| v == "true")
        .unwrap_or(false);
    if !confirmed {
        return Err(format!(
            "{sub} requires args.confirmed = \"true\""
        ));
    }
    run_simple(&[program, sub]).await
}

/// Kill the process on the given port, then optionally start a replacement.
async fn restart_dev_server(args: Args<'_>) -> CmdResult {
    let port = args
        .and_then(|a| a.get("port"))
        .ok_or_else(|| "restart_dev_server requires args.port".to_string())?;

    // Kill whatever is listening on the port (best-effort).
    let _ = TokioCommand::new("fuser")
        .args(["-k", &format!("{port}/tcp")])
        .output()
        .await;

    // If a replacement command is provided, spawn it detached.
    if let Some(run_cmd_str) = args.and_then(|a| a.get("cmd")) {
        let mut child = TokioCommand::new("sh")
            .args(["-c", run_cmd_str])
            .stdin(std::process::Stdio::null())
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .spawn()
            .map_err(|e| format!("failed to spawn dev server: {e}"))?;

        // Detach — don't await the child.
        tokio::spawn(async move {
            let _ = child.wait().await;
        });

        Ok(Some(format!(
            "killed port {port} and started: {run_cmd_str}"
        )))
    } else {
        Ok(Some(format!("killed process on port {port}")))
    }
}

// ── Low-level runners ──────────────────────────────────────────────────────

/// Run a command with args and collect its output.
async fn run_cmd(mut cmd: TokioCommand) -> CmdResult {
    let output = cmd
        .output()
        .await
        .map_err(|e| format!("spawn failed: {e}"))?;
    if output.status.success() {
        let stdout = String::from_utf8_lossy(&output.stdout).trim().to_string();
        Ok(if stdout.is_empty() {
            None
        } else {
            Some(stdout)
        })
    } else {
        let stderr = String::from_utf8_lossy(&output.stderr).trim().to_string();
        Err(if stderr.is_empty() {
            format!("exited with {}", output.status)
        } else {
            stderr
        })
    }
}

/// Run a simple program with literal arguments.
async fn run_simple(argv: &[&str]) -> CmdResult {
    let (program, args) = argv
        .split_first()
        .ok_or_else(|| "empty argv".to_string())?;
    let mut cmd = TokioCommand::new(program);
    cmd.args(args);
    run_cmd(cmd).await
}
