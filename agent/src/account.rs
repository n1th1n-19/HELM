//! Claude Code account collector.
//!
//! Reads ~/.claude/.credentials.json for account info and plan details,
//! reads ~/.claude/stats-cache.json for local activity stats,
//! and makes a lightweight GET /v1/models request to retrieve
//! subscription rate-limit usage from Anthropic response headers.

use crate::config::HelmConfig;
use crate::protocol::AccountUpdate;
use crate::state::{SharedState, StateTx};
use serde::Deserialize;
use std::path::PathBuf;
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use tokio::time;
use tracing::{debug, warn};

// ── Credentials file ─────────────────────────────────────────────────────────

#[derive(Deserialize, Default)]
struct Credentials {
    #[serde(rename = "accessToken")]
    access_token: Option<String>,
    #[serde(rename = "subscriptionType")]
    subscription_type: Option<String>,
    #[serde(rename = "rateLimitTier")]
    rate_limit_tier: Option<String>,
    account: Option<CredentialAccount>,
}

#[derive(Deserialize, Default)]
struct CredentialAccount {
    email: Option<String>,
}

fn credentials_path() -> PathBuf {
    dirs::home_dir()
        .unwrap_or_else(|| PathBuf::from("/"))
        .join(".claude")
        .join(".credentials.json")
}

fn read_credentials() -> Credentials {
    std::fs::read_to_string(credentials_path())
        .ok()
        .and_then(|t| serde_json::from_str(&t).ok())
        .unwrap_or_default()
}

// ── Stats cache ───────────────────────────────────────────────────────────────

#[derive(Deserialize, Default)]
struct StatsCache {
    #[serde(rename = "dailyActivity", default)]
    daily_activity: Vec<DailyActivity>,
}

#[derive(Deserialize)]
struct DailyActivity {
    date: String,
    #[serde(rename = "messageCount", default)]
    message_count: u32,
    #[serde(rename = "sessionCount", default)]
    session_count: u32,
}

fn stats_cache_path() -> PathBuf {
    dirs::home_dir()
        .unwrap_or_else(|| PathBuf::from("/"))
        .join(".claude")
        .join("stats-cache.json")
}

fn read_stats() -> StatsCache {
    std::fs::read_to_string(stats_cache_path())
        .ok()
        .and_then(|t| serde_json::from_str(&t).ok())
        .unwrap_or_default()
}

fn compute_activity(stats: &StatsCache) -> (u32, u32, u32, u32) {
    let today = chrono::Local::now().format("%Y-%m-%d").to_string();
    let week_ago = (chrono::Local::now() - chrono::TimeDelta::days(7))
        .format("%Y-%m-%d")
        .to_string();

    let mut today_msgs = 0u32;
    let mut today_sessions = 0u32;
    let mut week_msgs = 0u32;
    let mut week_sessions = 0u32;

    for day in &stats.daily_activity {
        if day.date >= week_ago {
            week_msgs += day.message_count;
            week_sessions += day.session_count;
        }
        if day.date == today {
            today_msgs = day.message_count;
            today_sessions = day.session_count;
        }
    }

    (today_msgs, today_sessions, week_msgs, week_sessions)
}

// ── Rate limit headers ────────────────────────────────────────────────────────

struct RateLimitUsage {
    session_used_pct: Option<f32>,
    session_reset_secs: Option<i64>,
    weekly_used_pct: Option<f32>,
    weekly_reset_secs: Option<i64>,
}

fn parse_reset_timestamp(value: &str, now_secs: i64) -> Option<i64> {
    // ISO 8601 format: "2026-06-03T20:00:00Z"
    if let Ok(dt) = chrono::DateTime::parse_from_rfc3339(value) {
        let reset_secs = dt.timestamp();
        return Some((reset_secs - now_secs).max(0));
    }
    // Plain integer seconds remaining
    if let Ok(secs) = value.parse::<i64>() {
        return Some(secs.max(0));
    }
    None
}

async fn fetch_rate_limits(token: &str) -> RateLimitUsage {
    let mut usage = RateLimitUsage {
        session_used_pct: None,
        session_reset_secs: None,
        weekly_used_pct: None,
        weekly_reset_secs: None,
    };

    let client = match reqwest::Client::builder()
        .timeout(Duration::from_secs(10))
        .build()
    {
        Ok(c) => c,
        Err(e) => {
            warn!("account: failed to build HTTP client: {e}");
            return usage;
        }
    };

    let resp = match client
        .get("https://api.anthropic.com/v1/models")
        .header("Authorization", format!("Bearer {token}"))
        .header("anthropic-version", "2023-06-01")
        .send()
        .await
    {
        Ok(r) => r,
        Err(e) => {
            warn!("account: rate limit probe failed: {e}");
            return usage;
        }
    };

    let now_secs = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs() as i64;

    let mut session_limit: Option<f64> = None;
    let mut session_remaining: Option<f64> = None;
    let mut weekly_limit: Option<f64> = None;
    let mut weekly_remaining: Option<f64> = None;

    for (name, value) in resp.headers().iter() {
        let n = name.as_str();
        let v = value.to_str().unwrap_or("");
        if !n.starts_with("anthropic-ratelimit") {
            continue;
        }
        debug!("account: ratelimit header {} = {}", n, v);

        let is_session = n.contains("session");
        let is_weekly = n.contains("week");

        if n.ends_with("-used-percent") || n.ends_with("-percent") {
            let pct = v.parse::<f32>().ok();
            if is_session { usage.session_used_pct = pct; }
            else if is_weekly { usage.weekly_used_pct = pct; }
        } else if n.ends_with("-remaining") {
            let val = v.parse::<f64>().ok();
            if is_session { session_remaining = val; }
            else if is_weekly { weekly_remaining = val; }
        } else if n.ends_with("-limit") {
            let val = v.parse::<f64>().ok();
            if is_session { session_limit = val; }
            else if is_weekly { weekly_limit = val; }
        } else if n.ends_with("-reset") {
            let secs = parse_reset_timestamp(v, now_secs);
            if is_session { usage.session_reset_secs = secs; }
            else if is_weekly { usage.weekly_reset_secs = secs; }
        }
    }

    // Derive percentages from limit/remaining if not provided directly.
    if usage.session_used_pct.is_none() {
        if let (Some(limit), Some(remaining)) = (session_limit, session_remaining) {
            if limit > 0.0 {
                usage.session_used_pct = Some(((limit - remaining) / limit * 100.0) as f32);
            }
        }
    }
    if usage.weekly_used_pct.is_none() {
        if let (Some(limit), Some(remaining)) = (weekly_limit, weekly_remaining) {
            if limit > 0.0 {
                usage.weekly_used_pct = Some(((limit - remaining) / limit * 100.0) as f32);
            }
        }
    }

    usage
}

// ── Collector loop ────────────────────────────────────────────────────────────

pub async fn run(state: SharedState, tx: StateTx, _cfg: HelmConfig) {
    // Poll every 5 minutes — rate limit data doesn't change faster than that.
    let mut ticker = time::interval(Duration::from_secs(300));
    ticker.set_missed_tick_behavior(time::MissedTickBehavior::Skip);

    loop {
        ticker.tick().await;
        let update = collect().await;
        {
            let mut s = state.write().await;
            s.account = update;
        }
        let _ = tx.send(());
    }
}

async fn collect() -> AccountUpdate {
    let creds = read_credentials();
    let stats = read_stats();
    let (today_msgs, today_sessions, week_msgs, week_sessions) = compute_activity(&stats);

    let plan = creds.subscription_type.as_deref().map(|s| match s {
        "claude_pro"   => "Claude Pro".to_string(),
        "claude_team"  => "Claude Team".to_string(),
        "enterprise"   => "Enterprise".to_string(),
        "claude_free"  => "Free".to_string(),
        other          => other.to_string(),
    });

    let rate_limits = if let Some(token) = &creds.access_token {
        fetch_rate_limits(token).await
    } else {
        RateLimitUsage {
            session_used_pct: None,
            session_reset_secs: None,
            weekly_used_pct: None,
            weekly_reset_secs: None,
        }
    };

    AccountUpdate {
        email: creds.account.and_then(|a| a.email),
        plan,
        rate_limit_tier: creds.rate_limit_tier,
        session_used_pct: rate_limits.session_used_pct,
        session_reset_secs: rate_limits.session_reset_secs,
        weekly_used_pct: rate_limits.weekly_used_pct,
        weekly_reset_secs: rate_limits.weekly_reset_secs,
        today_messages: Some(today_msgs),
        today_sessions: Some(today_sessions),
        week_messages: Some(week_msgs),
        week_sessions: Some(week_sessions),
    }
}
