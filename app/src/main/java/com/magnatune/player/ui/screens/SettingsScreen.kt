package com.magnatune.player.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magnatune.player.net.DownloadFormat
import com.magnatune.player.net.StreamQuality
import com.magnatune.player.ui.MagnatuneViewModel
import com.magnatune.player.ui.theme.MagSecondary

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Column(Modifier.padding(top = 8.dp)) { content() }
    }
    HorizontalDivider()
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsScreen(vm: MagnatuneViewModel, onShowHelp: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        MembershipSection(vm)
        PlaybackSection(vm)
        if (vm.credentials.isMember.collectAsStateWithLifecycle().value) DownloadSection(vm)
        LibrarySection(vm)
        StorageSection(vm)
        AboutSection(onShowHelp)
    }
}

/** Human-readable byte size, e.g. "12.3 MB" / "—" for zero. */
private fun fmtBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.2f GB".format(mb / 1024) else "%.1f MB".format(mb)
}

@Composable
private fun StorageRow(label: String, value: String, onClear: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = MagSecondary, style = MaterialTheme.typography.bodyMedium)
        if (onClear != null) {
            androidx.compose.material3.IconButton(onClick = onClear, modifier = Modifier.padding(start = 8.dp).then(Modifier)) {
                com.magnatune.player.ui.components.FaIcon(com.magnatune.player.ui.components.Fa.trash, "Clear", size = 16.dp, tint = MagSecondary)
            }
        }
    }
}

@Composable
private fun StorageSection(vm: MagnatuneViewModel) {
    val downloadBytes by vm.downloads.storageBytes.collectAsStateWithLifecycle()
    val downloading by vm.downloads.downloading.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Coil's disk image cache (album art + artist photos). Recomputed after a clear via [tick].
    var tick by remember { mutableStateOf(0) }
    val imageBytes by androidx.compose.runtime.produceState(0L, tick, downloadBytes) {
        value = coil.Coil.imageLoader(context).diskCache?.size ?: 0L
    }
    Section("Storage") {
        // Breakdown of the caches the app actually keeps on device. Android has no separate
        // streamed-music cache (unlike iOS AudioCache), so the two real categories are the
        // image disk cache and the offline downloads dir.
        StorageRow("Cached art & photos", fmtBytes(imageBytes), onClear = if (imageBytes > 0) {
            {
                coil.Coil.imageLoader(context).diskCache?.clear()
                coil.Coil.imageLoader(context).memoryCache?.clear()
                tick++
            }
        } else null)
        StorageRow("Downloaded music", fmtBytes(downloadBytes), onClear = if (downloadBytes > 0) {
            { vm.clearDownloads() }
        } else null)
        if (downloading) Text("Downloading…", color = MagSecondary, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { vm.syncDownloadsNow() }) { Text("Download favorites now") }
        }
    }
}

@Composable
private fun MembershipSection(vm: MagnatuneViewModel) {
    val isMember by vm.credentials.isMember.collectAsStateWithLifecycle()
    val username by vm.credentials.username.collectAsStateWithLifecycle()
    Section("Membership") {
        if (isMember) {
            Text("Signed in as $username", color = MagSecondary)
            OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.padding(top = 8.dp)) { Text("Sign out") }
        } else {
            var user by remember { mutableStateOf("") }
            var pw by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }
            var busy by remember { mutableStateOf(false) }
            OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pw, { pw = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = {
                    busy = true; error = null
                    vm.login(user.trim(), pw) { ok -> busy = false; if (!ok) error = "Sign-in failed — check your credentials." }
                },
                enabled = !busy && user.isNotBlank() && pw.isNotBlank(),
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(if (busy) "Signing in…" else "Sign in") }
            Text("Magnatune members stream without the spoken announcement and can download albums.",
                color = MagSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun PlaybackSection(vm: MagnatuneViewModel) {
    val isMember by vm.credentials.isMember.collectAsStateWithLifecycle()
    val crossfade by vm.settings.crossfadeEnabled.collectAsStateWithLifecycle()
    Section("Playback") {
        ToggleRow("Crossfade between songs", crossfade) { vm.settings.setCrossfade(it) }
        // Crossfade duration: 1–10s slider with a live label (PlaybackService reads it live).
        // Shown only when crossfade is on, matching iOS.
        if (crossfade) {
            var duration by remember { mutableFloatStateOf(vm.settings.crossfadeDuration.toFloat()) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("Duration", style = MaterialTheme.typography.bodyMedium, color = MagSecondary)
                Slider(
                    value = duration, onValueChange = { duration = it },
                    onValueChangeFinished = { vm.settings.crossfadeDuration = duration.toDouble() },
                    valueRange = 1f..10f, steps = 8,   // integer seconds 1..10
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text("${duration.toInt()}s", style = MaterialTheme.typography.bodyMedium, color = MagSecondary)
            }
        }
        if (isMember) {
            var quality by remember { mutableStateOf(vm.settings.streamQuality) }
            Text("Audio quality", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState())) {
                StreamQuality.entries.forEach { q ->
                    FilterChip(selected = quality == q, onClick = { quality = q; vm.settings.streamQuality = q },
                        label = { Text("${q.label} — ${q.detail}") })
                }
            }
        }
    }
}

@Composable
private fun DownloadSection(vm: MagnatuneViewModel) {
    Section("Download formats") {
        var album by remember { mutableStateOf(vm.settings.albumDownloadFormat) }
        Text("Album", style = MaterialTheme.typography.labelLarge)
        // Full iOS set: VBR, MP3, AAC, ALAC, FLAC, OGG, WAV (album zip endpoint supports all 7).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth().horizontalScroll(rememberScrollState())) {
            DownloadFormat.entries.forEach { f ->
                FilterChip(selected = album == f, onClick = { album = f; vm.settings.albumDownloadFormat = f },
                    label = { Text(f.key.uppercase()) })
            }
        }
        var song by remember { mutableStateOf(vm.settings.songDownloadFormat) }
        Text("Song", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        // Full iOS set: mp3, ogg, flac, wav (per-song he3 ext).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth().horizontalScroll(rememberScrollState())) {
            listOf("mp3", "ogg", "flac", "wav").forEach { f ->
                FilterChip(selected = song == f, onClick = { song = f; vm.settings.songDownloadFormat = f },
                    label = { Text(f.uppercase()) })
            }
        }
    }
}

@Composable
private fun LibrarySection(vm: MagnatuneViewModel) {
    val hideDislikes by vm.settings.hideDislikes.collectAsStateWithLifecycle()
    val autoDownload by vm.settings.autoDownloadFavorites.collectAsStateWithLifecycle()
    val isMember by vm.credentials.isMember.collectAsStateWithLifecycle()
    Section("Library") {
        ToggleRow("Hide things I dislike", hideDislikes) { vm.settings.setHideDislikes(it) }
        if (isMember) ToggleRow("Auto-download favorites", autoDownload) { vm.settings.setAutoDownload(it) }
    }
}

@Composable
private fun AboutSection(onShowHelp: () -> Unit = {}) {
    var showWhyNotEvil by remember { mutableStateOf(false) }
    var showFoundersRant by remember { mutableStateOf(false) }
    Section("About") {
        Text("Magnatune Player 0.1 — we are not evil.", color = MagSecondary)
        Text("Music licensed Creative Commons by Magnatune (magnatune.com).",
            color = MagSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            // In-app scrollable content (mirrors iOS InfoSheet) — no browser.
            OutlinedButton(onClick = { showWhyNotEvil = true }) { Text("Why not evil") }
            OutlinedButton(onClick = { showFoundersRant = true }) { Text("Founder's rant") }
        }
        OutlinedButton(onClick = onShowHelp, modifier = Modifier.padding(top = 8.dp)) { Text("App Help") }
    }
    if (showWhyNotEvil) {
        InfoSheet("Why we are not evil", onDismiss = { showWhyNotEvil = false }) {
            Text("Why Magnatune is not evil", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            whyNotEvilPoints.forEach { InfoBullet(it) }
        }
    }
    if (showFoundersRant) {
        InfoSheet("Founder's Rant", onDismiss = { showFoundersRant = false }) {
            Text(foundersRantText, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
