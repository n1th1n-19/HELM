//! WiFi-mode security: TLS cert management, PSK token, and rate limiting.
//! Never called in USB mode (bind_host == "127.0.0.1").

use anyhow::{Context, Result};
use rcgen::{generate_simple_self_signed, CertifiedKey};
use rustls::ServerConfig;
use rustls_pemfile::{certs, pkcs8_private_keys};
use sha2::{Digest, Sha256};
use std::collections::HashMap;
use std::io::BufReader;
use std::net::IpAddr;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::Instant;
use tracing::info;

// ── Public types ──────────────────────────────────────────────────────────────

pub struct SecurityContext {
    pub tls_config: Arc<ServerConfig>,
    pub token: String,
    pub cert_fingerprint: String,
}

// ── File paths ────────────────────────────────────────────────────────────────

pub fn config_dir() -> PathBuf {
    dirs::config_dir()
        .unwrap_or_else(|| PathBuf::from("~/.config"))
        .join("helm")
}

pub fn cert_path() -> PathBuf { config_dir().join("cert.pem") }
pub fn key_path()  -> PathBuf { config_dir().join("key.pem") }
pub fn token_path() -> PathBuf { config_dir().join("token") }

// ── Entry point ───────────────────────────────────────────────────────────────

/// Load existing cert/token from disk, or generate and persist new ones.
pub fn load_or_create(_cfg: &crate::config::HelmConfig) -> Result<SecurityContext> {
    let dir = config_dir();
    std::fs::create_dir_all(&dir)
        .with_context(|| format!("creating config dir {}", dir.display()))?;

    // ── Cert + key ────────────────────────────────────────────────────────────

    // Bug 3: bind paths once to avoid repeated recomputation and to make the
    // atomic-write logic below easier to read.
    let cert_path = cert_path();
    let key_path  = key_path();

    let (cert_pem, key_pem) = if cert_path.exists() && key_path.exists() {
        let cert = std::fs::read_to_string(&cert_path)
            .with_context(|| format!("reading {}", cert_path.display()))?;
        let key = std::fs::read_to_string(&key_path)
            .with_context(|| format!("reading {}", key_path.display()))?;
        (cert, key)
    } else {
        info!("Generating self-signed TLS certificate for WiFi mode");
        let CertifiedKey { cert, key_pair } =
            generate_simple_self_signed(vec!["helm-agent".to_string()])
                .context("generating self-signed cert")?;
        let cert_pem = cert.pem();
        let key_pem = key_pair.serialize_pem();

        // Bug 3: write both files to .tmp paths first, then rename atomically so
        // a crash between the two writes can never leave a mismatched cert/key pair.
        let cert_tmp = cert_path.with_extension("pem.tmp");
        let key_tmp  = key_path.with_extension("pem.tmp");

        std::fs::write(&cert_tmp, &cert_pem)
            .with_context(|| format!("writing {}", cert_tmp.display()))?;
        {
            use std::io::Write;
            use std::os::unix::fs::OpenOptionsExt;
            let mut f = std::fs::OpenOptions::new()
                .write(true)
                .create(true)
                .truncate(true)
                .mode(0o600)
                .open(&key_tmp)
                .with_context(|| format!("opening {}", key_tmp.display()))?;
            f.write_all(key_pem.as_bytes())
                .with_context(|| format!("writing {}", key_tmp.display()))?;
            f.sync_all()
                .with_context(|| format!("syncing {}", key_tmp.display()))?;
        }
        std::fs::rename(&cert_tmp, &cert_path)
            .with_context(|| format!("renaming {} -> {}", cert_tmp.display(), cert_path.display()))?;
        std::fs::rename(&key_tmp, &key_path)
            .with_context(|| format!("renaming {} -> {}", key_tmp.display(), key_path.display()))?;

        (cert_pem, key_pem)
    };

    // ── PSK token ─────────────────────────────────────────────────────────────

    let token = if token_path().exists() {
        std::fs::read_to_string(token_path())
            .with_context(|| format!("reading {}", token_path().display()))?
            .trim()
            .to_string()
    } else {
        info!("Generating PSK token for WiFi mode");
        let bytes = rand::random::<[u8; 32]>();
        let hex: String = bytes.iter().map(|b| format!("{b:02x}")).collect();
        {
            use std::os::unix::fs::OpenOptionsExt;
            use std::io::Write;
            std::fs::OpenOptions::new()
                .write(true).create(true).truncate(true)
                .mode(0o600)
                .open(token_path())
                .and_then(|mut f| f.write_all(hex.as_bytes()))
                .with_context(|| format!("writing {}", token_path().display()))?;
        }
        hex
    };

    // ── Build rustls ServerConfig ─────────────────────────────────────────────

    let cert_ders: Vec<_> = certs(&mut BufReader::new(cert_pem.as_bytes()))
        .collect::<Result<_, _>>()
        .context("parsing cert PEM")?;

    let first_cert = cert_ders.first()
        .context("cert.pem contains no valid certificates")?;
    let fingerprint: String = Sha256::digest(first_cert.as_ref())
        .iter()
        .map(|b| format!("{b:02x}"))
        .collect();

    let key_ders: Vec<_> = pkcs8_private_keys(&mut BufReader::new(key_pem.as_bytes()))
        .collect::<Result<_, _>>()
        .context("parsing key PEM")?;
    let key_der = key_ders
        .into_iter()
        .next()
        .context("no PKCS#8 private key found in key.pem")?;

    let tls_config = ServerConfig::builder()
        .with_no_client_auth()
        .with_single_cert(
            cert_ders,
            rustls::pki_types::PrivateKeyDer::Pkcs8(key_der),
        )
        .context("building rustls ServerConfig")?;

    Ok(SecurityContext {
        tls_config: Arc::new(tls_config),
        token,
        cert_fingerprint: fingerprint,
    })
}

// ── Rate limiter ──────────────────────────────────────────────────────────────

pub struct RateLimiter {
    failures: Mutex<HashMap<IpAddr, (u32, Instant)>>,
}

impl RateLimiter {
    pub fn new() -> Self {
        RateLimiter { failures: Mutex::new(HashMap::new()) }
    }

    /// Returns `false` if this IP has ≥5 failures within the current 60-second window.
    pub fn check(&self, ip: IpAddr) -> bool {
        let mut failures = self.failures.lock().unwrap();

        // Bug 2: evict entries older than 120 s to prevent unbounded HashMap growth
        // when an attacker cycles through IPv6 addresses.
        failures.retain(|_, (_, t)| t.elapsed().as_secs_f64() < 120.0);

        if let Some((count, first_seen)) = failures.get_mut(&ip) {
            if first_seen.elapsed().as_secs_f64() >= 60.0 {
                // Window expired — reset so the next window starts clean.
                *count = 0;
                *first_seen = Instant::now();
                return true;
            }
            if *count >= 5 {
                return false;
            }
        }
        true
    }

    pub fn record_failure(&self, ip: IpAddr) {
        let mut failures = self.failures.lock().unwrap();
        let entry = failures.entry(ip).or_insert((0, Instant::now()));
        // Bug 1: always increment the count; only reset the window timer on expiry.
        // Previously reset count to 1 on expiry, allowing an attacker spacing failures
        // exactly 60 s apart to be never blocked (threshold is 5).
        let elapsed = entry.1.elapsed().as_secs_f64();
        entry.0 += 1;
        if elapsed >= 60.0 {
            entry.1 = Instant::now();
        }
    }

    pub fn record_success(&self, ip: IpAddr) {
        self.failures.lock().unwrap().remove(&ip);
    }
}

// ── Tests ─────────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::Ipv4Addr;

    fn ip(last: u8) -> IpAddr {
        IpAddr::V4(Ipv4Addr::new(10, 0, 0, last))
    }

    #[test]
    fn rate_limiter_allows_four_failures() {
        let rl = RateLimiter::new();
        for _ in 0..4 { rl.record_failure(ip(1)); }
        assert!(rl.check(ip(1)));
    }

    #[test]
    fn rate_limiter_blocks_at_five() {
        let rl = RateLimiter::new();
        for _ in 0..5 { rl.record_failure(ip(2)); }
        assert!(!rl.check(ip(2)));
    }

    #[test]
    fn rate_limiter_success_clears_block() {
        let rl = RateLimiter::new();
        for _ in 0..5 { rl.record_failure(ip(3)); }
        rl.record_success(ip(3));
        assert!(rl.check(ip(3)));
    }

    #[test]
    fn rate_limiter_different_ips_are_independent() {
        let rl = RateLimiter::new();
        for _ in 0..5 { rl.record_failure(ip(4)); }
        assert!(rl.check(ip(5)), "ip(5) unaffected by ip(4) failures");
        assert!(!rl.check(ip(4)));
    }
}
