//! Async WebSocket server: serves a full snapshot on connect, then broadcasts
//! state deltas to every connected client.

use crate::state::{SharedState, StateRx};
use anyhow::Result;
use futures_util::stream::SplitSink;
use futures_util::{SinkExt, StreamExt};
use std::net::SocketAddr;
use std::sync::Arc;
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::broadcast;
use tokio_tungstenite::tungstenite::Message;
use tokio_tungstenite::WebSocketStream;
use tracing::{error, info, warn};

pub type BroadcastTx = broadcast::Sender<String>;

type WsSink = SplitSink<WebSocketStream<TcpStream>, Message>;

pub async fn run_server(addr: SocketAddr, state: SharedState, mut state_rx: StateRx) -> Result<()> {
    let listener = TcpListener::bind(addr).await?;
    info!("HELM agent listening on {}", addr);

    let (broadcast_tx, _) = broadcast::channel::<String>(256);
    let broadcast_tx = Arc::new(broadcast_tx);

    // Broadcaster task: watches for state changes, serializes and broadcasts.
    {
        let state = state.clone();
        let broadcast_tx = broadcast_tx.clone();
        tokio::spawn(async move {
            loop {
                // Wait for a state change notification.
                if state_rx.changed().await.is_err() {
                    break;
                }
                let snapshot = {
                    let s = state.read().await;
                    build_update_messages(&s)
                };
                for msg in snapshot {
                    let _ = broadcast_tx.send(msg);
                }
            }
        });
    }

    loop {
        match listener.accept().await {
            Ok((stream, peer_addr)) => {
                info!("New connection from {}", peer_addr);
                let state = state.clone();
                let broadcast_tx = broadcast_tx.clone();
                tokio::spawn(async move {
                    if let Err(e) =
                        handle_connection(stream, peer_addr, state, broadcast_tx).await
                    {
                        warn!("Connection {} error: {}", peer_addr, e);
                    }
                });
            }
            Err(e) => {
                error!("Accept error: {}", e);
            }
        }
    }
}

async fn handle_connection(
    stream: TcpStream,
    peer_addr: SocketAddr,
    state: SharedState,
    broadcast_tx: Arc<BroadcastTx>,
) -> Result<()> {
    let ws_stream = tokio_tungstenite::accept_async(stream).await?;
    let (mut ws_tx, mut ws_rx) = ws_stream.split();

    // Send full initial snapshot.
    {
        let s = state.read().await;
        let msgs = build_full_snapshot(&s);
        for msg in msgs {
            ws_tx.send(Message::Text(msg.into())).await?;
        }
    }

    let mut broadcast_rx = broadcast_tx.subscribe();

    loop {
        tokio::select! {
            // Forward broadcasts to this client.
            result = broadcast_rx.recv() => {
                match result {
                    Ok(msg) => {
                        if ws_tx.send(Message::Text(msg.into())).await.is_err() {
                            break;
                        }
                    }
                    Err(broadcast::error::RecvError::Lagged(n)) => {
                        warn!("Client {} lagged by {} messages", peer_addr, n);
                    }
                    Err(_) => break,
                }
            }
            // Handle messages from Android (commands, pings).
            msg = ws_rx.next() => {
                match msg {
                    Some(Ok(Message::Text(text))) => {
                        handle_client_message(&text, &mut ws_tx).await;
                    }
                    Some(Ok(Message::Ping(data))) => {
                        let _ = ws_tx.send(Message::Pong(data)).await;
                    }
                    Some(Ok(Message::Close(_))) | None => break,
                    _ => {}
                }
            }
        }
    }

    info!("Connection {} closed", peer_addr);
    Ok(())
}

async fn handle_client_message(text: &str, ws_tx: &mut WsSink) {
    // Parse ping/pong or command — commands handled by commands.rs in Task 6.
    if let Ok(val) = serde_json::from_str::<serde_json::Value>(text) {
        if val.get("type").and_then(|t| t.as_str()) == Some("ping") {
            let pong = serde_json::json!({"type": "pong", "ts": now_ms(), "payload": {}});
            let _ = ws_tx.send(Message::Text(pong.to_string().into())).await;
        }
    }
}

fn build_full_snapshot(state: &crate::state::HelmState) -> Vec<String> {
    let mut msgs = Vec::new();
    if let Some(ref info) = state.system_info {
        msgs.push(make_envelope("system_info", info));
    }
    msgs.push(make_envelope("system_update", &state.system));
    msgs.push(make_envelope("git_update", &state.git));
    msgs.push(make_envelope("music_update", &state.music));
    msgs.push(make_envelope("window_update", &state.window));
    msgs.push(make_envelope("vscode_update", &state.vscode));
    msgs.push(make_envelope("process_update", &state.process));
    msgs
}

fn build_update_messages(state: &crate::state::HelmState) -> Vec<String> {
    // In future: diff against previous state and send only changes.
    // For now: send all state (collectors set only changed fields via Option).
    build_full_snapshot(state)
}

pub fn make_envelope(msg_type: &str, payload: &impl serde::Serialize) -> String {
    let envelope = serde_json::json!({
        "type": msg_type,
        "ts": now_ms(),
        "payload": payload,
    });
    envelope.to_string()
}

pub fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64
}
