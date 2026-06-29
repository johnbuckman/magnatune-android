package com.magnatune.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.magnatune.player.model.PlayableTrack
import com.magnatune.player.model.Song
import com.magnatune.player.ui.MagnatuneViewModel
import com.magnatune.player.ui.Routes
import com.magnatune.player.ui.components.Fa
import com.magnatune.player.ui.components.FaIcon
import com.magnatune.player.ui.components.FavoriteButton
import com.magnatune.player.ui.components.SongRow
import com.magnatune.player.ui.theme.MagSecondary

/**
 * Dedicated Songs browse page: a song-title search over the whole catalog (~24,000 tracks),
 * mirroring the native iOS "Songs" section. Separate from the combined Search screen.
 */
@Composable
fun SongsScreen(vm: MagnatuneViewModel, nav: NavController, onPlay: OnPlay) {
    var query by remember { mutableStateOf("") }
    val songs by produceState<List<Song>?>(null, query) {
        value = if (query.length < 2) null else vm.searchSongsOnly(query)
    }
    val tracksRaw by produceState(emptyList<PlayableTrack>(), songs) {
        value = songs?.let { vm.playable(it) } ?: emptyList()
    }
    val supp by vm.suppression.collectAsStateWithLifecycle()
    val tracks = tracksRaw.filter { it.song.id !in supp.songs }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search songs by title") },
            singleLine = true,
            leadingIcon = { FaIcon(Fa.magnifyingGlass, null, tint = MagSecondary, size = 16.dp) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        FaIcon(Fa.xmark, "Clear search", tint = MagSecondary, size = 16.dp)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        if (query.length < 2) {
            Text(
                "There are ~24,000 tracks. Type to search by title.",
                color = MagSecondary, modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(tracks, key = { _, t -> "s${t.song.id}" }) { idx, t ->
                    SongRow(
                        song = t.song,
                        artistName = t.artistName,
                        albumName = t.album.name,
                        showArtwork = true,
                        onAlbumClick = { nav.navigate(Routes.albumSong(t.album.id, t.song.id)) },
                        onArtistClick = { nav.navigate(Routes.artist(t.album.artistId)) },
                        onClick = { onPlay(tracks, idx) },
                        trailing = { FavoriteButton(vm, "song", t.song.id, compact = true) },
                    )
                }
            }
        }
    }
}
