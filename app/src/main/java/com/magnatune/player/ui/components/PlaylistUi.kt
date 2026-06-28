package com.magnatune.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magnatune.player.ui.MagnatuneViewModel
import com.magnatune.player.ui.theme.MagAccent
import com.magnatune.player.ui.theme.MagSecondary

/** Add-to-playlist toggle: shows + normally; for a song already on a playlist shows − (removes from
 *  all). Album/artist always add. Tapping + opens the picker sheet. */
@Composable
fun AddToPlaylistButton(vm: MagnatuneViewModel, kind: String, id: Long, compact: Boolean = false) {
    val playlisted by vm.userStore.playlistedSongIds.collectAsStateWithLifecycle()
    val onPlaylist = kind == "song" && playlisted.contains(id)
    var showSheet by remember { mutableStateOf(false) }

    IconButton(
        onClick = { if (onPlaylist) vm.removeFromAllPlaylists(kind, id) else showSheet = true },
        modifier = Modifier.size(if (compact) 32.dp else 40.dp),
    ) {
        Icon(
            androidx.compose.ui.res.painterResource(
                if (onPlaylist) com.magnatune.player.R.drawable.ic_playlist_remove
                else com.magnatune.player.R.drawable.ic_playlist_add),
            contentDescription = "Add to playlist", tint = if (onPlaylist) MagAccent else MagSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
    if (showSheet) AddToPlaylistSheet(vm, kind, id) { showSheet = false }
}

@Composable
private fun AddToPlaylistSheet(vm: MagnatuneViewModel, kind: String, id: Long, onClose: () -> Unit) {
    val playlists by vm.userStore.playlists.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onClose) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text("Add to playlist", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.padding(top = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        placeholder = { Text("New playlist") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val name = newName.trim()
                            if (name.isNotEmpty()) vm.createPlaylist(name) { pid -> vm.addToPlaylist(pid, kind, id) }
                            onClose()
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("Create") }
                }
                if (playlists.isNotEmpty()) {
                    Text("Existing", style = MaterialTheme.typography.labelMedium, color = MagSecondary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(playlists, key = { it.id }) { pl ->
                            Text("${pl.name}  (${pl.count})",
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { vm.addToPlaylist(pl.id, kind, id); onClose() }
                                    .padding(vertical = 12.dp))
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
