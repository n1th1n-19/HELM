package dev.helm.app.ui.components

/** Format bytes/sec as human-readable (e.g. "1.2 MB/s", "450 KB/s"). */
fun formatBytesPerSec(bps: Long): String {
    return when {
        bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000.0)
        bps >= 1_000     -> "%.0f KB/s".format(bps / 1_000.0)
        else             -> "$bps B/s"
    }
}

/** Format uptime seconds as "3d 14h 22m" or "14h 22m" or "22m". */
fun formatUptime(secs: Long): String {
    val d = secs / 86400
    val h = (secs % 86400) / 3600
    val m = (secs % 3600) / 60
    return buildString {
        if (d > 0) append("${d}d ")
        if (h > 0 || d > 0) append("${h}h ")
        append("${m}m")
    }.trim()
}

/** Format a Unix timestamp (ms) as a relative time string. */
fun formatRelativeTime(epochMs: Long): String {
    val diff = System.currentTimeMillis() - epochMs
    val secs = diff / 1000
    return when {
        secs < 60    -> "${secs}s ago"
        secs < 3600  -> "${secs / 60}m ago"
        secs < 86400 -> "${secs / 3600}h ago"
        else         -> "${secs / 86400}d ago"
    }
}

/** Format MB as human-readable (rounds to nearest MB, shows GB if >=1024). */
fun formatMb(mb: Long): String = when {
    mb >= 1024 -> "%.1f GB".format(mb / 1024.0)
    else       -> "$mb MB"
}
