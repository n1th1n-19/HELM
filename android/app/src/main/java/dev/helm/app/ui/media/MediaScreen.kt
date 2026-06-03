package dev.helm.app.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dev.helm.app.data.model.MusicUpdate
import dev.helm.app.data.model.PlaybackState
import dev.helm.app.ui.components.StatusBadge
import dev.helm.app.ui.theme.*

@Composable
fun MediaScreen(
    modifier: Modifier = Modifier,
    viewModel: MediaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val music = state.music

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Album art
        AlbumArt(music = music, modifier = Modifier.fillMaxWidth())

        // Track info
        TrackInfo(music = music, modifier = Modifier.fillMaxWidth())

        // Progress bar
        ProgressSection(music = music, modifier = Modifier.fillMaxWidth())

        // Playback controls
        PlaybackControls(music = music, modifier = Modifier.fillMaxWidth())

        // Volume + player source row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            music.volume?.let { vol ->
                Text(
                    text = "Vol: ${"%.0f".format(vol * 100)}%",
                    color = HelmTextSecondary,
                    fontSize = 13.sp,
                )
            }
            music.player?.let { player ->
                StatusBadge(text = player, color = HelmMusic)
            }
        }
    }
}

@Composable
private fun AlbumArt(music: MusicUpdate, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(HelmCard),
        contentAlignment = Alignment.Center,
    ) {
        val artBytes = remember(music.albumArtB64) {
            music.albumArtB64?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }
        }
        if (artBytes != null) {
            AsyncImage(
                model = artBytes,
                contentDescription = "Album art",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
            )
        } else {
            Icon(
                Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = HelmMusic,
                modifier = Modifier.size(80.dp),
            )
        }
    }
}

@Composable
private fun TrackInfo(music: MusicUpdate, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = music.title ?: "Nothing playing",
            color = HelmTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (music.artist != null) {
            Text(
                text = music.artist,
                color = HelmTextSecondary,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (music.album != null) {
            Text(
                text = music.album,
                color = HelmTextTertiary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProgressSection(music: MusicUpdate, modifier: Modifier = Modifier) {
    val posMs = music.positionMs ?: 0L
    val durMs = music.durationMs ?: 0L
    val progress = if (durMs > 0) (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = HelmMusic,
            trackColor = HelmBorder,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatMs(posMs),
                color = HelmTextSecondary,
                fontSize = 12.sp,
            )
            Text(
                text = formatMs(durMs),
                color = HelmTextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PlaybackControls(music: MusicUpdate, modifier: Modifier = Modifier) {
    val isPlaying = music.state == PlaybackState.Playing

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.SkipPrevious,
            contentDescription = "Previous",
            tint = HelmTextSecondary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.width(24.dp))
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(HelmMusic, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = HelmTextPrimary,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        Icon(
            Icons.Outlined.SkipNext,
            contentDescription = "Next",
            tint = HelmTextSecondary,
            modifier = Modifier.size(40.dp),
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSecs = ms / 1000
    val min = totalSecs / 60
    val sec = totalSecs % 60
    return "%d:%02d".format(min, sec)
}
