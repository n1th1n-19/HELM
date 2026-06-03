//! Shared mutable state written by collectors and read by the WebSocket server.

use crate::protocol::*;
use std::sync::Arc;
use tokio::sync::{watch, RwLock};

#[derive(Debug, Clone, Default)]
pub struct HelmState {
    pub system: SystemUpdate,
    pub git: GitUpdate,
    pub music: MusicUpdate,
    pub window: WindowUpdate,
    pub vscode: VscodeUpdate,
    pub claude: ClaudeUpdate,
    pub process: ProcessUpdate,
    pub system_info: Option<SystemInfo>,
    pub account: AccountUpdate,
}

pub type SharedState = Arc<RwLock<HelmState>>;
pub type StateTx = watch::Sender<()>;
pub type StateRx = watch::Receiver<()>;

pub fn new_shared_state() -> (SharedState, StateTx, StateRx) {
    let state = Arc::new(RwLock::new(HelmState::default()));
    let (tx, rx) = watch::channel(());
    (state, tx, rx)
}
