package com.magnatune.player.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magnatune.player.service.PlaybackController
import com.magnatune.player.ui.theme.MagAccent
import com.magnatune.player.ui.theme.MagBg
import com.magnatune.player.ui.theme.MagCard
import com.magnatune.player.ui.theme.magCardShadow
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
fun MiniPlayer(vm: com.magnatune.player.ui.MagnatuneViewModel, nav: androidx.navigation.NavController) {
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
                    FaIcon(Fa.music, null, tint = MagSecondary, size = 20.dp)
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
            // Volume: mute / slider / full grouped inside a capsule pill (option 3).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MagBg)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            ) {
                FaIcon(Fa.volumeOff, "Mute", tint = MagSecondary, size = 16.dp,
                    modifier = Modifier.clickable { controller.setVolume(0f) })
                SeekSlider(value = vol, enabled = true, onValueChange = { controller.setVolume(it) },
                    modifier = Modifier.width(76.dp).padding(horizontal = 6.dp))
                FaIcon(Fa.volumeHigh, "Full volume", tint = MagSecondary, size = 16.dp,
                    modifier = Modifier.clickable { controller.setVolume(1f) })
            }
            Spacer(Modifier.width(12.dp))
            AirPlayButton(vm)
            Spacer(Modifier.width(8.dp))
            CastButton(Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            TransportButton(Fa.prev, "Previous", t != null) { controller.previous() }
            Spacer(Modifier.width(6.dp))
            TransportButton(if (playing) Fa.pause else Fa.play, "Play/Pause", t != null) { controller.togglePlayPause() }
            Spacer(Modifier.width(6.dp))
            TransportButton(Fa.next, "Next", t != null) { controller.next() }
        }
        // Seek row.
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(fmt(pos), style = MaterialTheme.typography.labelSmall, color = MagSecondary)
            SeekSlider(
                value = (scrubbing ?: (if (dur > 0) pos.toFloat() / dur else 0f)).coerceIn(0f, 1f),
                onValueChange = { scrubbing = it },
                onValueChangeFinished = { scrubbing?.let { controller.seekTo((it * dur).toLong()) }; scrubbing = null },
                enabled = t != null,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(fmt(dur), style = MaterialTheme.typography.labelSmall, color = MagSecondary)
        }
    }

    if (showNowPlaying && t != null) NowPlayingDialog(controller, nav, vm) { showNowPlaying = false }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    // Alignment mirrors the iOS regularLayout fix: bottom-align the player card with the sidebar
    // card (both 8dp bottom inset) and make the left gutter equal the bottom gutter (8dp), keeping
    // the right gutter (10dp).
    Surface(
        color = MagCard, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 8.dp)
            .magCardShadow(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), content = content)
    }
}

@Composable
private fun accentSlider() = SliderDefaults.colors(
    thumbColor = MagAccent, activeTrackColor = MagAccent,
    inactiveTrackColor = MagSecondary.copy(alpha = 0.25f),
)

/** Slider with a short thumb. Play-position bar uses the vertical-bar thumb; the volume control
 *  uses a round dot thumb ([dotThumb] = true). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SeekSlider(
    value: Float, enabled: Boolean,
    onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
    dotThumb: Boolean = false,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Slider(
        value = value, onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished,
        enabled = enabled, interactionSource = interaction, colors = accentSlider(), modifier = modifier,
        thumb = {
            if (dotThumb) {
                Box(Modifier.size(14.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MagAccent))
            } else {
                SliderDefaults.Thumb(
                    interactionSource = interaction,
                    colors = accentSlider(),
                    enabled = enabled,
                    thumbSize = androidx.compose.ui.unit.DpSize(4.dp, 22.dp),
                )
            }
        },
    )
}

/** Accent-outlined transport button (matches iOS), Font Awesome glyph. */
@Composable
private fun TransportButton(
    glyph: String, desc: String, enabled: Boolean,
    width: Dp = 48.dp, height: Dp = 28.dp, iconSize: Dp = 18.dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier.height(height).width(width)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (enabled) MagAccent else MagSecondary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        FaIcon(glyph, desc, tint = if (enabled) MagAccent else MagSecondary.copy(alpha = 0.4f), size = iconSize)
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
            TransportButton(Fa.prev, "Previous", true) { vm.container.peer.sendControl(peer.id, "prev") }
            Spacer(Modifier.width(6.dp))
            TransportButton(if (peer.snapshot.state == "playing") Fa.pause else Fa.play,
                "Play/Pause", true) { vm.container.peer.sendControl(peer.id, "playPause") }
            Spacer(Modifier.width(6.dp))
            TransportButton(Fa.next, "Next", true) { vm.container.peer.sendControl(peer.id, "next") }
        }
    }
}

@Composable
private fun NowPlayingDialog(controller: PlaybackController, nav: androidx.navigation.NavController, vm: com.magnatune.player.ui.MagnatuneViewModel, onClose: () -> Unit) {
    val track by controller.currentTrack.collectAsStateWithLifecycle()
    val playing by controller.isPlaying.collectAsStateWithLifecycle()
    val pos by controller.positionMs.collectAsStateWithLifecycle()
    val dur by controller.durationMs.collectAsStateWithLifecycle()
    val vol by controller.volume.collectAsStateWithLifecycle()
    val t = track ?: return
    var scrubbing by remember { mutableStateOf<Float?>(null) }

    fun openAlbum() { onClose(); nav.navigate(com.magnatune.player.ui.Routes.album(t.album.id)) }
    fun openArtist() { onClose(); nav.navigate(com.magnatune.player.ui.Routes.artist(t.album.artistId)) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.width(340.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CoverImage(t.artistName, t.album.name, points = 200.dp,
                        modifier = Modifier.size(200.dp).clickable { openAlbum() }, cap = 600)
                    Spacer(Modifier.size(12.dp))
                    Text(t.song.name, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable { openAlbum() }
                            .padding(vertical = 2.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.size(4.dp))
                    Text(t.artistName, style = MaterialTheme.typography.titleMedium, color = MagAccent,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable { openArtist() }
                            .padding(vertical = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.size(4.dp))
                    Text(t.album.name, style = MaterialTheme.typography.bodyMedium, color = MagSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable { openAlbum() }
                            .padding(vertical = 2.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.size(12.dp))
                    SeekSlider(
                        value = (scrubbing ?: (if (dur > 0) pos.toFloat() / dur else 0f)).coerceIn(0f, 1f),
                        enabled = true,
                        onValueChange = { scrubbing = it },
                        onValueChangeFinished = { scrubbing?.let { controller.seekTo((it * dur).toLong()) }; scrubbing = null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmt(pos), style = MaterialTheme.typography.labelSmall, color = MagSecondary)
                        Text(fmt(dur), style = MaterialTheme.typography.labelSmall, color = MagSecondary)
                    }
                    Spacer(Modifier.size(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TransportButton(Fa.prev, "Previous", true, width = 64.dp, height = 40.dp, iconSize = 24.dp) { controller.previous() }
                        TransportButton(if (playing) Fa.pause else Fa.play, "Play/Pause", true, width = 80.dp, height = 44.dp, iconSize = 28.dp) { controller.togglePlayPause() }
                        TransportButton(Fa.next, "Next", true, width = 64.dp, height = 40.dp, iconSize = 24.dp) { controller.next() }
                    }
                    Spacer(Modifier.size(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        FaIcon(Fa.volumeOff, "Mute", tint = MagSecondary, size = 20.dp,
                            modifier = Modifier.clickable { controller.setVolume(0f) })
                        SeekSlider(value = vol, enabled = true, onValueChange = { controller.setVolume(it) },
                            modifier = Modifier.width(140.dp).padding(horizontal = 10.dp))
                        FaIcon(Fa.volumeHigh, "Full volume", tint = MagSecondary, size = 20.dp,
                            modifier = Modifier.clickable { controller.setVolume(1f) })
                    }
                }
                // AirPlay + Cast buttons overlaid top-left (mirror the X); each hidden when no
                // device of that type is found.
                Row(Modifier.align(Alignment.TopStart).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AirPlayButton(vm)
                    Spacer(Modifier.width(10.dp))
                    CastButton(Modifier.size(28.dp))
                }
                // Close button overlaid in the top-right corner (~10dp from edges).
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(28.dp)) {
                    FaIcon(Fa.xmark, "Close", tint = MagSecondary, size = 18.dp)
                }
            }
        }
    }
}
