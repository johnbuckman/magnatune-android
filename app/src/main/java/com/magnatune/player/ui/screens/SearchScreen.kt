package com.magnatune.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.magnatune.player.model.PlayableTrack
import com.magnatune.player.ui.MagnatuneViewModel
import com.magnatune.player.ui.Routes
import com.magnatune.player.ui.SearchResults
import com.magnatune.player.ui.components.ArtistRow
import com.magnatune.player.ui.components.CoverImage
import com.magnatune.player.ui.components.FavoriteButton
import com.magnatune.player.ui.components.SectionHeader
import com.magnatune.player.ui.components.SongRow

@Composable
fun SearchScreen(vm: MagnatuneViewModel, nav: NavController, onPlay: OnPlay) {
    var query by remember { mutableStateOf("") }
    val results by produceState<SearchResults?>(null, query) {
        value = if (query.length < 2) null else vm.search(query)
    }
    val names by produceState<Map<Long, String>?>(null) { value = vm.artistNames() }
    val songTracks by produceState(emptyList<PlayableTrack>(), results) {
        value = results?.songs?.let { vm.playable(it) } ?: emptyList()
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search artists, albums, songs") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        val r = results
        if (r != null) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (r.artists.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    items(r.artists, key = { "ar${it.id}" }) { artist ->
                        ArtistRow(artist, albumName = null, onClick = { nav.navigate(Routes.artist(artist.id)) })
                    }
                }
                if (r.albums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    items(r.albums, key = { "al${it.id}" }) { album ->
                        com.magnatune.player.ui.components.AlbumListRow(
                            album, names?.get(album.artistId) ?: "",
                        ) { nav.navigate(Routes.album(album.id)) }
                    }
                }
                if (r.songs.isNotEmpty()) {
                    item { SectionHeader("Songs") }
                    itemsIndexed(r.songs, key = { _, s -> "s${s.id}" }) { idx, song ->
                        SongRow(song = song,
                            artistName = songTracks.getOrNull(idx)?.artistName,
                            albumName = songTracks.getOrNull(idx)?.album?.name,
                            showArtwork = true,
                            onClick = { if (songTracks.isNotEmpty()) onPlay(songTracks, idx) },
                            trailing = { FavoriteButton(vm, "song", song.id, compact = true) })
                    }
                }
            }
        }
    }
}
