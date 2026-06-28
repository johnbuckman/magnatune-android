package com.magnatune.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magnatune.player.service.PlaybackController
import com.magnatune.player.ui.theme.MagCard
import com.magnatune.player.ui.theme.MagSecondary

private fun fmt(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** Persistent bottom mini-player. Shows local playback, or a remote-control bar when a peer is
 *  playing while we're idle, or nothing. Tap to open Now Playing. */
@Composable
fun MiniPlayer(vm: com.magnatune.player.ui.MagnatuneViewModel) {
    val controller = vm.playback
    val track by controller.currentTrack.collectAsStateWithLifecycle()
    val playing by controller.isPlaying.collectAsStateWithLifecycle()
    val remote by vm.container.remoteFocus.collectAsStateWithLifecycle()
    val t = track
    if (t == null) {
        remote?.let { RemoteControlBar(vm, it) }
        return
    }
    var showNowPlaying by remember { mutableStateOf(false) }

    Surface(color = MagCard, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { showNowPlaying = true }.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(t.artistName, t.album.name, points = 44.dp, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.song.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artistName, style = MaterialTheme.typography.bodySmall, color = MagSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { controller.togglePlayPause() }) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause")
            }
            IconButton(onClick = { controller.next() }) { Icon(Icons.Filled.SkipNext, "Next") }
        }
    }

    if (showNowPlaying) NowPlayingDialog(controller) { showNowPlaying = false }
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
    Surface(color = MagCard, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Controlling ${peer.name}", style = MaterialTheme.typography.labelSmall, color = MagSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(songName ?: "Playing…", style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { vm.container.peer.sendControl(peer.id, "prev") }) { Icon(Icons.Filled.SkipPrevious, "Previous") }
            IconButton(onClick = { vm.container.peer.sendControl(peer.id, "playPause") }) { Icon(Icons.Filled.Pause, "Play/Pause") }
            IconButton(onClick = { vm.container.peer.sendControl(peer.id, "next") }) { Icon(Icons.Filled.SkipNext, "Next") }
        }
    }
}

@Composable
private fun NowPlayingDialog(controller: PlaybackController, onClose: () -> Unit) {
    val track by controller.currentTrack.collectAsStateWithLifecycle()
    val playing by controller.isPlaying.collectAsStateWithLifecycle()
    val pos by controller.positionMs.collectAsStateWithLifecycle()
    val dur by controller.durationMs.collectAsStateWithLifecycle()
    val t = track ?: return
    var scrubbing by remember { mutableStateOf<Float?>(null) }

    Dialog(onDismissRequest = onClose) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(20.dp).width(320.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
                }
                CoverImage(t.artistName, t.album.name, points = 260.dp, modifier = Modifier.size(260.dp), cap = 600)
                Spacer(Modifier.size(16.dp))
                Text(t.song.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(t.artistName, style = MaterialTheme.typography.titleMedium, color = MagSecondary)
                Spacer(Modifier.size(16.dp))
                val fraction = scrubbing ?: (if (dur > 0) pos.toFloat() / dur else 0f)
                Slider(
                    value = fraction.coerceIn(0f, 1f),
                    onValueChange = { scrubbing = it },
                    onValueChangeFinished = {
                        scrubbing?.let { controller.seekTo((it * dur).toLong()) }
                        scrubbing = null
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt(pos), style = MaterialTheme.typography.labelSmall, color = MagSecondary)
                    Text(fmt(dur), style = MaterialTheme.typography.labelSmall, color = MagSecondary)
                }
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { controller.previous() }) { Icon(Icons.Filled.SkipPrevious, "Previous") }
                    IconButton(onClick = { controller.togglePlayPause() }) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause",
                            modifier = Modifier.size(48.dp))
                    }
                    IconButton(onClick = { controller.next() }) { Icon(Icons.Filled.SkipNext, "Next") }
                }
                val vol by controller.volume.collectAsStateWithLifecycle()
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, "Volume", tint = MagSecondary, modifier = Modifier.size(20.dp))
                    Slider(value = vol, onValueChange = { controller.setVolume(it) }, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
