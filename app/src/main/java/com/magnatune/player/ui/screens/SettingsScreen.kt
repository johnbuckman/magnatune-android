package com.magnatune.player.ui.screens

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
fun SettingsScreen(vm: MagnatuneViewModel) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        MembershipSection(vm)
        PlaybackSection(vm)
        if (vm.credentials.isMember.collectAsStateWithLifecycle().value) DownloadSection(vm)
        LibrarySection(vm)
        StorageSection(vm)
        AboutSection()
    }
}

@Composable
private fun StorageSection(vm: MagnatuneViewModel) {
    val bytes by vm.downloads.storageBytes.collectAsStateWithLifecycle()
    val downloading by vm.downloads.downloading.collectAsStateWithLifecycle()
    val mb = bytes / (1024.0 * 1024.0)
    Section("Storage") {
        Text("Downloaded music: %.1f MB".format(mb), color = MagSecondary)
        if (downloading) Text("Downloading…", color = MagSecondary, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { vm.syncDownloadsNow() }) { Text("Download favorites now") }
            if (bytes > 0) OutlinedButton(onClick = { vm.clearDownloads() }) { Text("Clear downloads") }
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
        ToggleRow("Crossfade", crossfade) { vm.settings.setCrossfade(it) }
        if (isMember) {
            var quality by remember { mutableStateOf(vm.settings.streamQuality) }
            Text("Audio quality", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                StreamQuality.entries.forEach { q ->
                    FilterChip(selected = quality == q, onClick = { quality = q; vm.settings.streamQuality = q },
                        label = { Text(q.label) })
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp).fillMaxWidth()) {
            listOf(DownloadFormat.VBR, DownloadFormat.MP3, DownloadFormat.FLAC).forEach { f ->
                FilterChip(selected = album == f, onClick = { album = f; vm.settings.albumDownloadFormat = f },
                    label = { Text(f.key.uppercase()) })
            }
        }
        var song by remember { mutableStateOf(vm.settings.songDownloadFormat) }
        Text("Song", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            listOf("mp3", "ogg", "wav").forEach { f ->
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
private fun AboutSection() {
    Section("About") {
        Text("Magnatune — we are not evil.", color = MagSecondary)
        Text("magnatune.com", color = MagSecondary, style = MaterialTheme.typography.bodySmall)
    }
}
