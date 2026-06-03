//! Async WebSocket server: serves a full snapshot on connect, then broadcasts
//! state deltas to every connected client.
//!
//! In USB mode (security=None): plain WS over TCP.
//! In WiFi mode (security=Some): TLS handshake first, then PSK token check in
//! the WS upgrade header. Rate limiter fires before TLS to avoid CPU cost.

use crate::config::HelmConfig;
use crate::security::{RateLimiter, SecurityContext};
use crate::state::{SharedState, StateRx};
use anyhow::Result;
use futures_util::stream::SplitSink;
use futures_util::{SinkExt, StreamExt};
use std::net::SocketAddr;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::broadcast;
use tokio_tungstenite::tungstenite::Message;
use tokio_tungstenite::WebSocketStream;
use tracing::{error, info, warn};

pub type BroadcastTx = broadcast::Sender<String>;

pub async fn run_server(
    listener: TcpListener,
    state: SharedState,
    mut state_rx: StateRx,
    cfg: HelmConfig,
    security: Option<Arc<SecurityContext>>,
    rate_limiter: Arc<RateLimiter>,
    client_count: Arc<AtomicUsize>,
) -> Result<()> {
    let (broadcast_tx, _) = broadcast::channel::<String>(256);
    let broadcast_tx = Arc::new(broadcast_tx);

    // Broadcaster: watches for state changes, serializes and fans out to clients.
    {
        let state = state.clone();
        let broadcast_tx = broadcast_tx.clone();
        tokio::spawn(async move {
            loop {
                if state_rx.changed().await.is_err() {
                    break;
                }
                let msgs = {
                    let s = state.read().await;
                    build_update_messages(&s)
                };
                for msg in msgs {
                    let _ = broadcast_tx.send(msg);
                }
            }
        });
    }

    loop {
        match listener.accept().await {
            Ok((stream, peer_addr)) => {
                // Rate-limit check happens at TCP accept, before TLS handshake cost.
                if !rate_limiter.check(peer_addr.ip()) {
                    info!("Rate-limited: dropping connection from {}", peer_addr);
                    drop(stream);
                    continue;
                }

                info!("New connection from {}", peer_addr);
                let state = state.clone();
                let broadcast_tx = broadcast_tx.clone();
                let cfg = cfg.clone();
                let security = security.clone();
                let rate_limiter = rate_limiter.clone();
                let client_count = client_count.clone();

                tokio::spawn(async move {
                    if let Err(e) = handle_connection(
                        stream, peer_addr, state, broadcast_tx, cfg,
                        security, rate_limiter, client_count,
                    )
                    .await
                    {
                        warn!("Connection {} error: {}", peer_addr, e);
                    }
                });
            }
            Err(e) => error!("Accept error: {}", e),
        }
    }
}

async fn handle_connection(
    stream: TcpStream,
    peer_addr: SocketAddr,
    state: SharedState,
    broadcast_tx: Arc<BroadcastTx>,
    cfg: HelmConfig,
    security: Option<Arc<SecurityContext>>,
    rate_limiter: Arc<RateLimiter>,
    client_count: Arc<AtomicUsize>,
) -> Result<()> {
    match security {
        None => {
            // USB mode: plain WebSocket, no auth.
            let ws = tokio_tungstenite::accept_async(stream).await?;
            handle_ws(ws, peer_addr, state, broadcast_tx, cfg, client_count).await
        }
        Some(ctx) => {
            // WiFi mode: TLS first, then PSK token check during WS upgrade.
            let ip = peer_addr.ip();
            let acceptor = tokio_rustls::TlsAcceptor::from(Arc::clone(&ctx.tls_config));
            let tls_stream = match acceptor.accept(stream).await {
                Ok(s) => s,
                Err(e) => {
                    rate_limiter.record_failure(ip);
                    return Err(e.into());
                }
            };
            let token = ctx.token.clone();
            let rl = rate_limiter.clone();
            let ws = tokio_tungstenite::accept_hdr_async(
                tls_stream,
                move |req: &tokio_tungstenite::tungstenite::handshake::server::Request,
                      resp: tokio_tungstenite::tungstenite::handshake::server::Response| {
                    let provided = req
                        .headers()
                        .get("X-Helm-Token")
                        .and_then(|v| v.to_str().ok())
                        .unwrap_or("");
                    use subtle::ConstantTimeEq;
                    if provided.as_bytes().ct_eq(token.as_bytes()).unwrap_u8() != 1 {
                        rl.record_failure(ip);
                        return Err(
                            tokio_tungstenite::tungstenite::http::Response::builder()
                                .status(
                                    tokio_tungstenite::tungstenite::http::StatusCode::UNAUTHORIZED,
                                )
                                .body(None)
                                .unwrap(),
                        );
                    }
                    rl.record_success(ip);
                    Ok(resp)
                },
            )
            .await?;
            handle_ws(ws, peer_addr, state, broadcast_tx, cfg, client_count).await
        }
    }
}

async fn handle_ws<S>(
    ws_stream: WebSocketStream<S>,
    peer_addr: SocketAddr,
    state: SharedState,
    broadcast_tx: Arc<BroadcastTx>,
    cfg: HelmConfig,
    client_count: Arc<AtomicUsize>,
) -> Result<()>
where
    S: tokio::io::AsyncRead + tokio::io::AsyncWrite + Unpin + Send + 'static,
{
    client_count.fetch_add(1, Ordering::Relaxed);
    let (mut ws_tx, mut ws_rx) = ws_stream.split();
    let mut broadcast_rx = broadcast_tx.subscribe();

    let result: Result<()> = async {
        // Send full initial snapshot.
        {
            let s = state.read().await;
            for msg in build_full_snapshot(&s) {
                ws_tx.send(Message::Text(msg.into())).await?;
            }
        }

        let mut disconnected = false;
        loop {
            tokio::select! {
                result = broadcast_rx.recv() => {
                    match result {
                        Ok(msg) => {
                            if ws_tx.send(Message::Text(msg.into())).await.is_err() {
                                break;
                            }
                        }
                        Err(broadcast::error::RecvError::Lagged(n)) => {
                            warn!("Client {} lagged by {} messages, resyncing", peer_addr, n);
                            // Send a full snapshot so the client is back in sync.
                            let msgs = {
                                let s = state.read().await;
                                build_full_snapshot(&s)
                            };
                            for msg in msgs {
                                if ws_tx.send(Message::Text(msg.into())).await.is_err() {
                                    disconnected = true;
                                    break;
                                }
                            }
                            if disconnected {
                                break;
                            }
                        }
                        Err(_) => break,
                    }
                }
                msg = ws_rx.next() => {
                    match msg {
                        Some(Ok(Message::Text(text))) => {
                            handle_client_message(&text, &mut ws_tx, &cfg, &state).await;
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
        Ok(())
    }
    .await;

    client_count.fetch_sub(1, Ordering::Relaxed);
    info!("Connection {} closed", peer_addr);
    result
}

async fn handle_client_message<S>(
    text: &str,
    ws_tx: &mut SplitSink<WebSocketStream<S>, Message>,
    cfg: &HelmConfig,
    state: &SharedState,
) where
    S: tokio::io::AsyncRead + tokio::io::AsyncWrite + Unpin + Send + 'static,
{
    let val = match serde_json::from_str::<serde_json::Value>(text) {
        Ok(v) => v,
        Err(_) => return,
    };

    match val.get("type").and_then(|t| t.as_str()) {
        Some("ping") => {
            let pong = serde_json::json!({"type": "pong", "ts": now_ms(), "payload": {}});
            let _ = ws_tx.send(Message::Text(pong.to_string().into())).await;
        }
        Some("command") => {
            if let Some(payload) = val.get("payload") {
                match serde_json::from_value::<crate::protocol::Command>(payload.clone()) {
                    Ok(cmd) => {
                        let ack = crate::commands::execute_command(cmd, cfg, state).await;
                        let envelope = make_envelope("command_ack", &ack);
                        let _ = ws_tx
                            .send(Message::Text(envelope.into()))
                            .await;
                    }
                    Err(e) => warn!("malformed command payload: {e}"),
                }
            }
        }
        _ => {}
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
    msgs.push(make_envelope("claude_update", &state.claude));
    msgs.push(make_envelope("process_update", &state.process));
    msgs.push(make_envelope("account_update", &state.account));
    msgs
}

fn build_update_messages(state: &crate::state::HelmState) -> Vec<String> {
    // Full-snapshot stand-in: see comment in original file re: true delta diffing.
    build_full_snapshot(state)
}

pub fn make_envelope(msg_type: &str, payload: &impl serde::Serialize) -> String {
    serde_json::json!({
        "type": msg_type,
        "ts": now_ms(),
        "payload": payload,
    })
    .to_string()
}

pub fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64
}
