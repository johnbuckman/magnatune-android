package com.magnatune.player.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magnatune.player.service.PlaybackController
import com.magnatune.player.ui.theme.MagAccent
import com.magnatune.player.ui.theme.MagCard
import com.magnatune.player.ui.theme.MagSecondary

private fun fmt(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** Floating bottom player card (matches the iOS MiniPlayer): artwork, title/artist, inline volume,
 *  accent-bordered transport buttons, and a seek row. Always visible; shows a remote-control bar
 *  when a peer is playing while we're idle, or a "Not Playing" placeholder otherwise. */
@Composable
fun MiniPlayer(vm: com.magnatune.player.ui.MagnatuneViewModel) {
    val controller = vm.playback
    val track by controller.currentTrack.collectAsStateWithLifecycle()
    val playing by controller.isPlaying.collectAsStateWithLifecycle()
    val pos by controller.positionMs.collectAsStateWithLifecycle()
    val dur by controller.durationMs.collectAsStateWithLifecycle()
    val vol by controller.volume.collectAsStateWithLifecycle()
    val remote by vm.container.remoteFocus.collectAsStateWithLifecycle()
    var showNowPlaying by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }

    val t = track
    // Remote-control mode: nothing local, a peer is playing.
    if (t == null && remote != null) { RemoteControlBar(vm, remote!!); return }

    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Artwork (or placeholder).
            if (t != null) {
                CoverImage(t.artistName, t.album.name, points = 44.dp, modifier = Modifier.size(44.dp))
            } else {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                    .border(0.5.dp, MagSecondary.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, null, tint = MagSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(enabled = t != null) { showNowPlaying = true }) {
                Text(t?.song?.name ?: "Not Playing", style = MaterialTheme.typography.bodyMedium,
                    color = if (t != null) MaterialTheme.colorScheme.onBackground else MagSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t?.artistName ?: "Magnatune", style = MaterialTheme.typography.bodySmall,
                    color = MagSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Inline volume (tablet).
            Icon(volumeIcon(vol), null, tint = MagSecondary, modifier = Modifier.size(18.dp))
            Slider(value = vol, onValueChange = { controller.setVolume(it) },
                modifier = Modifier.width(90.dp).padding(horizontal = 4.dp),
                colors = accentSlider())
            Spacer(Modifier.width(4.dp))
            TransportButton(Icons.Filled.FastRewind, "Previous", t != null) { controller.previous() }
            Spacer(Modifier.width(6.dp))
            TransportButton(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", t != null) { controller.togglePlayPause() }
            Spacer(Modifier.width(6.dp))
            TransportButton(Icons.Filled.FastForward, "Next", t != null) { controller.next() }
        }
        // Seek row.
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(fmt(pos), style = MaterialTheme.typography.labelSmall, color = MagSecondary)
            Slider(
                value = (scrubbing ?: (if (dur > 0) pos.toFloat() / dur else 0f)).coerceIn(0f, 1f),
                onValueChange = { scrubbing = it },
                onValueChangeFinished = { scrubbing?.let { controller.seekTo((it * dur).toLong()) }; scrubbing = null },
                enabled = t != null,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = accentSlider(),
            )
            Text(fmt(dur), style = MaterialTheme.typography.labelSmall, color = MagSecondary)
        }
    }

    if (showNowPlaying && t != null) NowPlayingDialog(controller) { showNowPlaying = false }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        color = MagCard, shape = RoundedCornerShape(12.dp), shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), content = content)
    }
}

@Composable
private fun accentSlider() = SliderDefaults.colors(
    thumbColor = MagAccent, activeTrackColor = MagAccent,
    inactiveTrackColor = MagSecondary.copy(alpha = 0.25f),
)

private fun volumeIcon(v: Float): ImageVector = when {
    v < 0.01f -> Icons.AutoMirrored.Filled.VolumeOff
    v < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
    else -> Icons.AutoMirrored.Filled.VolumeUp
}

/** Accent-outlined transport button (matches iOS). */
@Composable
private fun TransportButton(icon: ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(28.dp).width(48.dp)
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, if (enabled) MagAccent else MagSecondary.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (enabled) MagAccent else MagSecondary.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
    }
}

/** Bar shown when controlling another Magnatune device over the LAN. */
@Composable
private fun RemoteControlBar(
    vm: com.magnatune.player.ui.MagnatuneViewModel,
    peer: com.magnatune.player.peer.PeerService.PeerInfo,
) {
    val songName by androidx.compose.runtime.produceState<String?>(null, peer.snapshot.songId) {
        value = peer.snapshot.songId?.let { id ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vm.container.catalog.song(id)?.name }
        }
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Controlling ${peer.name}", style = MaterialTheme.typography.labelSmall, color = MagAccent,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(songName ?: "Playing…", style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TransportButton(Icons.Filled.FastRewind, "Previous", true) { vm.container.peer.sendControl(peer.id, "prev") }
            Spacer(Modifier.width(6.dp))
            TransportButton(if (peer.snapshot.state == "playing") Icons.Filled.Pause else Icons.Filled.PlayArrow,
                "Play/Pause", true) { vm.container.peer.sendControl(peer.id, "playPause") }
            Spacer(Modifier.width(6.dp))
            TransportButton(Icons.Filled.FastForward, "Next", true) { vm.container.peer.sendControl(peer.id, "next") }
        }
    }
}

@Composable
private fun NowPlayingDialog(controller: PlaybackController, onClose: () -> Unit) {
    val track by controller.currentTrack.collectAsStateWithLifecycle()
    val playing by controller.isPlaying.collectAsStateWithLifecycle()
    val pos by controller.positionMs.collectAsStateWithLifecycle()
    val dur by controller.durationMs.collectAsStateWithLifecycle()
    val vol by controller.volume.collectAsStateWithLifecycle()
    val t = track ?: return
    var scrubbing by remember { mutableStateOf<Float?>(null) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(20.dp).width(320.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
                }
                CoverImage(t.artistName, t.album.name, points = 260.dp, modifier = Modifier.size(260.dp), cap = 600)
                Spacer(Modifier.size(16.dp))
                Text(t.song.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(t.artistName, style = MaterialTheme.typography.titleMedium, color = MagSecondary)
                Spacer(Modifier.size(16.dp))
                Slider(
                    value = (scrubbing ?: (if (dur > 0) pos.toFloat() / dur else 0f)).coerceIn(0f, 1f),
                    onValueChange = { scrubbing = it },
                    onValueChangeFinished = { scrubbing?.let { controller.seekTo((it * dur).toLong()) }; scrubbing = null },
                    colors = accentSlider(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt(pos), style = MaterialTheme.typography.labelSmall, color = MagSecondary)
                    Text(fmt(dur), style = MaterialTheme.typography.labelSmall, color = MagSecondary)
                }
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { controller.previous() }) { Icon(Icons.Filled.SkipPrevious, "Previous") }
                    IconButton(onClick = { controller.togglePlayPause() }) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", modifier = Modifier.size(48.dp))
                    }
                    IconButton(onClick = { controller.next() }) { Icon(Icons.Filled.SkipNext, "Next") }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(volumeIcon(vol), null, tint = MagSecondary, modifier = Modifier.size(20.dp))
                    Slider(value = vol, onValueChange = { controller.setVolume(it) },
                        modifier = Modifier.padding(start = 8.dp), colors = accentSlider())
                }
            }
        }
    }
}
