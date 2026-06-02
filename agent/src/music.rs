//! Music collector: media player state via MPRIS2 over the session D-Bus.
//!
//! Connects once at startup to the session bus, finds the first MPRIS2 player,
//! and reads playback status, metadata, position, and volume. Polls every 1s.
//! When no player is active (or there is no session bus, e.g. headless), it
//! publishes an all-None `MusicUpdate`.
//!
//! Album art is left as `None` for now (the Android app handles null art).

use crate::config::HelmConfig;
use crate::protocol::{MusicUpdate, PlaybackState};
use crate::state::{SharedState, StateTx};
use base64::Engine;
use std::collections::HashMap;
use std::time::Duration;
use tokio::time;
use tracing::{debug, warn};
use zbus::zvariant::{Array, OwnedValue, Value};
use zbus::Connection;

type Metadata = HashMap<String, OwnedValue>;

/// Find the first available MPRIS2 player on the bus.
async fn get_active_player(conn: &Connection) -> Option<String> {
    let dbus = zbus::fdo::DBusProxy::new(conn).await.ok()?;
    let names = dbus.list_names().await.ok()?;
    names
        .into_iter()
        .map(|n| n.to_string())
        .find(|n| n.starts_with("org.mpris.MediaPlayer2."))
}

fn get_str_from_metadata(m: &Metadata, key: &str) -> Option<String> {
    let owned = m.get(key)?;
    let v: &Value = owned;
    v.downcast_ref::<&str>().ok().map(|s| s.to_string())
}

/// MPRIS `xesam:artist` is an array of strings; return the first entry.
fn get_str_array_from_metadata(m: &Metadata, key: &str) -> Option<String> {
    let owned = m.get(key)?;
    let v: &Value = owned;
    let arr: &Array = v.downcast_ref::<&Array>().ok()?;
    arr.inner()
        .iter()
        .find_map(|e| e.downcast_ref::<&str>().ok().map(|s| s.to_string()))
}

fn get_i64_from_metadata(m: &Metadata, key: &str) -> Option<i64> {
    let owned = m.get(key)?;
    let v: &Value = owned;
    v.downcast_ref::<i64>().ok()
}

async fn get_music_update(conn: &Connection) -> Option<MusicUpdate> {
    let player_name = get_active_player(conn).await?;

    let proxy = zbus::Proxy::new(
        conn,
        player_name.clone(),
        "/org/mpris/MediaPlayer2",
        "org.mpris.MediaPlayer2.Player",
    )
    .await
    .ok()?;

    // PlaybackStatus
    let status: String = proxy.get_property("PlaybackStatus").await.ok()?;
    let state = match status.as_str() {
        "Playing" => PlaybackState::Playing,
        "Paused" => PlaybackState::Paused,
        _ => PlaybackState::Stopped,
    };

    // Metadata
    let metadata: Metadata = proxy.get_property("Metadata").await.ok()?;

    let title = get_str_from_metadata(&metadata, "xesam:title");
    let artist = get_str_array_from_metadata(&metadata, "xesam:artist");
    let album = get_str_from_metadata(&metadata, "xesam:album");

    // Position (microseconds -> milliseconds). Not all players expose it.
    let position_ms: Option<u64> = proxy
        .get_property::<i64>("Position")
        .await
        .ok()
        .map(|us| (us / 1000).max(0) as u64);

    // Duration from metadata (microseconds -> milliseconds).
    let duration_ms: Option<u64> = get_i64_from_metadata(&metadata, "mpris:length")
        .map(|us| (us / 1000).max(0) as u64);

    // Volume
    let volume: Option<f32> = proxy
        .get_property::<f64>("Volume")
        .await
        .ok()
        .map(|v| v as f32);

    // Album art: read from mpris:artUrl if it is a local file.
    let album_art_b64 = get_str_from_metadata(&metadata, "mpris:artUrl")
        .and_then(|url| encode_album_art(&url));

    // Player display name (strip the MPRIS prefix).
    let player_display = player_name
        .trim_start_matches("org.mpris.MediaPlayer2.")
        .to_string();

    Some(MusicUpdate {
        player: Some(player_display),
        title,
        artist,
        album,
        album_art_b64,
        duration_ms,
        position_ms,
        volume,
        state: Some(state),
    })
}

/// Maximum album art file size we are willing to base64-encode (500 KB).
const MAX_ART_BYTES: u64 = 500 * 1024;

/// Read a local `file://` art URL, detect JPEG/PNG via magic bytes, and return
/// a base64-encoded data-URI string.  Returns `None` for HTTP(S) URLs, files
/// that are too large, or unrecognised formats.
fn encode_album_art(art_url: &str) -> Option<String> {
    // Only handle local files for now.
    let path = if let Some(stripped) = art_url.strip_prefix("file://") {
        stripped.to_string()
    } else {
        // http/https — skip without logging (expected).
        return None;
    };

    let meta = std::fs::metadata(&path).ok()?;
    if meta.len() > MAX_ART_BYTES {
        debug!(path, "album art too large, skipping");
        return None;
    }

    let data = std::fs::read(&path).ok()?;

    // Detect image type from magic bytes.
    let mime = if data.starts_with(&[0x89, 0x50, 0x4E, 0x47]) {
        "image/png"
    } else if data.starts_with(&[0xFF, 0xD8, 0xFF]) {
        "image/jpeg"
    } else {
        debug!(path, "unknown image format, skipping album art");
        return None;
    };

    let b64 = base64::engine::general_purpose::STANDARD.encode(&data);
    Some(format!("data:{mime};base64,{b64}"))
}

pub async fn run(state: SharedState, tx: StateTx, _cfg: HelmConfig) {
    let mut ticker = time::interval(Duration::from_secs(1));
    ticker.set_missed_tick_behavior(time::MissedTickBehavior::Skip);

    // Connect once at startup. Retry lazily inside the loop if it failed.
    let mut conn: Option<Connection> = match Connection::session().await {
        Ok(c) => Some(c),
        Err(e) => {
            warn!("no session D-Bus, music collector idle: {e}");
            None
        }
    };

    loop {
        ticker.tick().await;

        // Attempt to (re)establish the connection if we don't have one.
        if conn.is_none() {
            conn = Connection::session().await.ok();
        }

        let update = match &conn {
            Some(c) => get_music_update(c).await.unwrap_or_default(),
            None => MusicUpdate::default(),
        };

        debug!(player = ?update.player, "music update");
        {
            let mut s = state.write().await;
            s.music = update;
        }
        let _ = tx.send(());
    }
}
