# HELM Security + System Tray — Design Spec

**Date:** 2026-06-02  
**Branch:** feature/security  
**Scope:** WiFi-mode security (TLS/WSS + PSK auth + rate limiting) and cross-platform system tray icon

---

## 1. Threat Model

HELM in USB/ADB mode binds to `127.0.0.1` — already safe, no changes needed.

HELM in WiFi mode (`bind_host != "127.0.0.1"`) exposes the agent to the LAN. Threats:

- **Unauthorized control** — any LAN device issues commands (git push, reboot, shutdown)
- **Passive eavesdropping** — LAN attacker reads system stats, git state, music, file paths

Both threats are addressed by this spec. USB mode is explicitly excluded from all security machinery.

---

## 2. Security Architecture

Three independent layers, applied in order at connection time:

```
TCP accept
    │
    ▼
[1. Rate limiter] ── excess failures → drop TCP (no TLS cost)
    │
    ▼
[2. TLS handshake] ── attacker sees encrypted stream; bad cert → reject
    │
    ▼
[3. WS upgrade + PSK token] ── wrong/missing token → HTTP 401
    │
    ▼
handle_connection (existing, unchanged)
```

Security is **opt-in by operation mode**: WiFi mode activates all three layers automatically. USB mode skips all three.

---

## 3. Agent: Certificate + Token Generation

### New file: `agent/src/security.rs`

Responsible for:
- Generating and loading the TLS cert/key pair
- Generating and loading the PSK token
- Computing the cert fingerprint
- Exposing the rate limiter

**Cert generation** (first run, WiFi mode only):

- Algorithm: RSA-2048 via `rcgen`
- Self-signed, subject CN=`helm-agent`
- Validity: 10 years (this is a device identity, not a CA)
- Stored: `~/.config/helm/cert.pem`, `~/.config/helm/key.pem`
- If files exist: load them. If corrupted: exit with error message pointing to the files.

**Token generation** (first run, WiFi mode only):

- 32 random bytes via `rand::random::<[u8; 32]>()`
- Hex-encoded (64 chars), stored at `~/.config/helm/token`
- If file exists: load it. Never regenerate without explicit user action.

**Fingerprint**:

- SHA-256 of DER-encoded cert bytes
- Hex-encoded, computed at startup from loaded cert
- Logged at INFO level on startup
- Encoded into QR pairing URL

**Public interface:**

```rust
pub struct SecurityContext {
    pub tls_config: Arc<ServerConfig>,   // rustls ServerConfig
    pub token: String,                    // hex PSK
    pub cert_fingerprint: String,         // sha256 hex of DER cert
}

pub fn load_or_create(cfg: &HelmConfig) -> Result<SecurityContext>
```

Called from `main.rs` in WiFi mode only. Not called in USB mode.

---

## 4. Agent: WSS Server

### Modified: `agent/src/websocket.rs`

`run_server` receives an `Option<SecurityContext>`. When `Some`, it uses the TLS path; when `None` (USB mode), it uses the existing plain WS path.

**TLS accept path:**

```rust
let acceptor = TlsAcceptor::from(Arc::clone(&ctx.tls_config));
let tls_stream = acceptor.accept(tcp_stream).await?;
accept_hdr_async(tls_stream, |req, resp| {
    let token = req.headers()
        .get("X-Helm-Token")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("");
    if token != ctx.token {
        rate_limiter.record_failure(peer_ip);
        return Err(resp.status(StatusCode::UNAUTHORIZED).body(None).unwrap());
    }
    rate_limiter.record_success(peer_ip);
    Ok(resp)
}).await?
```

`handle_connection` signature does not change — it receives an already-upgraded WebSocket stream regardless of transport.

---

## 5. Agent: Rate Limiter

### Defined in `security.rs`

```rust
pub struct RateLimiter {
    failures: Mutex<HashMap<IpAddr, (u32, Instant)>>,
}

impl RateLimiter {
    // Returns true if connection is allowed, false if rate-limited.
    pub fn check(&self, ip: IpAddr) -> bool
    pub fn record_failure(&self, ip: IpAddr)
    pub fn record_success(&self, ip: IpAddr)
}
```

Policy:
- Window: 60 seconds
- Threshold: 5 failures within window → block all connections from that IP
- Block duration: until window expires (sliding reset on first failure in new window)
- `check()` called at TCP accept, before TLS handshake — cheap rejection

A blocked IP receives an immediate TCP close (no TLS, no HTTP, no WS). This prevents TLS handshake CPU cost under brute force.

---

## 6. Agent: QR Code + CLI

### Modified: `agent/src/cli.rs`

WiFi mode QR URL scheme changes:

| Mode | Before | After |
|------|--------|-------|
| USB | `helm://127.0.0.1:8080` | unchanged |
| WiFi | `helm://192.168.1.5:8080` | `helms://192.168.1.5:8080?token=<hex64>&cert=<sha256hex>` |

`helms://` signals to Android: use WSS + cert pinning + token.  
`helm://` signals: plain WS, no auth (USB only).

`helm-agent qr` command regenerates and displays the QR. `helm-agent config` output includes the fingerprint and token path.

---

## 7. Agent: New Cargo Dependencies

```toml
rcgen = "0.13"
tokio-rustls = "0.26"
rustls-pemfile = "2"
rand = { version = "0.8", features = ["getrandom"] }
sha2 = "0.10"
```

Remove `rustls-tls-native-roots` feature from `tokio-tungstenite` (not needed — we supply our own cert).

---

## 8. Android: ConnectionPreferences

### Modified: `ConnectionPreferences.kt`

Two new nullable fields alongside existing `host`/`port`:

```kotlin
var token: String?           // null → USB mode, no auth
var certFingerprint: String? // null → USB mode, no pinning
```

Serialized to DataStore alongside existing prefs.

**QR parser** — `parseConnectionUrl(url: String)`:
- `helm://host:port` → set host/port, null token/fingerprint
- `helms://host:port?token=T&cert=F` → set all four fields
- Malformed URL or missing params on `helms://` → show error, do not save

---

## 9. Android: WebSocket Client

### Modified: `HelmWebSocketClient.kt`

**Mode selection** at connection time:

```
token == null → plain ws://, default TrustManager, no auth header
token != null → wss://, pinned TrustManager, X-Helm-Token header
```

**Cert pinning TrustManager:**

```kotlin
class PinnedTrustManager(private val expectedFingerprint: String) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(chain[0].encoded)
            .joinToString("") { "%02x".format(it) }
        if (actual != expectedFingerprint) {
            throw CertificateException("Cert fingerprint mismatch: expected $expectedFingerprint, got $actual")
        }
    }
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
```

`PinnedTrustManager` is passed to the OkHttp engine used by Ktor. No system CA chain consulted — TOFU: trust the cert whose fingerprint was scanned.

**Token header** added to Ktor WebSocket request headers:

```kotlin
headers { append("X-Helm-Token", token) }
```

---

## 10. Android: Settings UI

### Modified: `SettingsScreen.kt` / `SettingsViewModel.kt`

- QR scanner already exists; extend `parseConnectionUrl` in ViewModel
- Show a "Secured" chip/badge when `certFingerprint != null`
- Show "USB Mode" badge when fingerprint is null
- No other UX changes; pairing flow identical to current

---

## 11. System Tray Icon

### New file: `agent/src/tray.rs`

Cross-platform system tray icon using the `tray-icon` crate (supports Linux X11/Wayland via `libayatana-appindicator`, macOS, Windows).

**Icon states:**
- Connected (≥1 client): HELM icon, full opacity
- Disconnected (0 clients): HELM icon, dimmed
- Error/startup: HELM icon with warning overlay

**Right-click context menu:**

| Item | Action | Condition |
|------|--------|-----------|
| `HELM Agent v0.x.x` | (label, disabled) | always |
| `● Connected (N clients)` or `○ Disconnected` | (status, disabled) | always |
| — separator — | | |
| `Show QR Code` | print QR to stdout / open terminal | WiFi mode only |
| `Restart` | `helm-agent restart` (re-exec self) | always |
| `Stop` | graceful shutdown (sends SIGTERM to self) | always |

**Implementation:**

- `tray.rs` exposes `run_tray(state: SharedState, shutdown_tx: mpsc::Sender<()>)`
- Spawned as a dedicated OS thread (tray-icon requires a non-async thread with an event loop on some platforms)
- Watches `SharedState` client count via a lightweight polling interval (500ms) to update icon/status label
- "Stop" sends on `shutdown_tx`, which signals the existing graceful shutdown future in `main.rs`
- "Restart" re-execs via `std::process::Command::new(current_exe()).spawn()` then exits

**New Cargo dependency:**

```toml
tray-icon = { version = "0.17", optional = true }
```

Exposed as a Cargo feature `tray` (default on). Headless/server installs can build with `--no-default-features` to omit the tray entirely. On Linux, requires `libayatana-appindicator3` or `libappindicator3` at runtime; document in README.

**Icon asset:** `assets/helm-icon.png` (already in repo), embedded via `include_bytes!` — no runtime file dependency.

**Tray init failure:** If `tray-icon` fails to initialize (headless display, unsupported compositor), log a warning and continue. Agent operates normally without tray presence.

### Modified: `agent/src/main.rs`

```rust
let (shutdown_tx, mut shutdown_rx) = mpsc::channel::<()>(1);
std::thread::spawn(|| tray::run_tray(state.clone(), shutdown_tx));

// Shutdown future also listens on shutdown_rx:
tokio::select! {
    _ = shutdown_rx.recv() => {}  // tray Stop clicked
    _ = ctrl_c() => {}
    _ = sigterm.recv() => {}
}
```

---

## 12. Files Changed Summary

### Agent (Rust)

| File | Change |
|------|--------|
| `src/security.rs` | **new** — cert gen, token gen, fingerprint, rate limiter |
| `src/tray.rs` | **new** — cross-platform system tray |
| `src/websocket.rs` | WSS path, token header check, rate limiter integration |
| `src/main.rs` | SecurityContext init, tray thread spawn, shutdown_tx wiring |
| `src/cli.rs` | `helms://` QR URL, fingerprint in `config` output |
| `Cargo.toml` | Add rcgen, tokio-rustls, rustls-pemfile, rand, sha2, tray-icon |

### Android (Kotlin)

| File | Change |
|------|--------|
| `ConnectionPreferences.kt` | Add token, certFingerprint fields + QR parser |
| `HelmWebSocketClient.kt` | WSS path, PinnedTrustManager, token header |
| `SettingsViewModel.kt` | parseConnectionUrl handles helms:// |
| `SettingsScreen.kt` | Secured/USB badge |

---

## 13. Out of Scope

- USB/ADB mode auth (loopback is safe, no change)
- Token rotation UI (user can delete `~/.config/helm/token` to force regeneration; re-scanning QR on Android required after)
- Multi-device support (same token/cert for all paired Androids — personal tool, one user)
- Cert expiry handling (10-year validity, not worth the UX complexity)
- Wayland-specific tray fallback (tray-icon handles this internally)
