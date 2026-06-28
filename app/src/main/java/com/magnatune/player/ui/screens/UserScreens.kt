package com.magnatune.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.magnatune.player.model.Album
import com.magnatune.player.model.Artist
import com.magnatune.player.model.PlayableTrack
import com.magnatune.player.model.Song
import com.magnatune.player.ui.MagnatuneViewModel
import com.magnatune.player.ui.Routes
import com.magnatune.player.ui.components.AddToPlaylistButton
import com.magnatune.player.ui.components.AlbumListRow
import com.magnatune.player.ui.components.ArtistRow
import com.magnatune.player.ui.components.FavoriteButton
import com.magnatune.player.ui.components.SectionHeader
import com.magnatune.player.ui.components.SongRow
import com.magnatune.player.ui.theme.MagSecondary

@Composable
fun FavoritesScreen(vm: MagnatuneViewModel, nav: NavController, onPlay: OnPlay) {
    // Recompute when the favorite id-sets change.
    val favArtistIds by vm.userStore.favoriteArtistIds.collectAsStateWithLifecycle()
    val favAlbumIds by vm.userStore.favoriteAlbumIds.collectAsStateWithLifecycle()
    val favSongIds by vm.userStore.favoriteSongIds.collectAsStateWithLifecycle()

    val artists by produceState(emptyList<Artist>(), favArtistIds) { value = vm.favoriteArtists() }
    val albums by produceState(emptyList<Album>(), favAlbumIds) { value = vm.favoriteAlbums() }
    val songs by produceState(emptyList<Song>(), favSongIds) { value = vm.favoriteSongs() }
    val names by produceState<Map<Long, String>?>(null) { value = vm.artistNames() }
    val songTracks by produceState(emptyList<PlayableTrack>(), songs) { value = vm.playable(songs) }

    if (artists.isEmpty() && albums.isEmpty() && songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No favorites yet — tap the heart on any song, album, or artist.", color = MagSecondary,
                modifier = Modifier.padding(32.dp))
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (artists.isNotEmpty()) {
            item { SectionHeader("Artists") }
            items(artists, key = { "ar${it.id}" }) { a ->
                ArtistRow(a, albumName = null, onClick = { nav.navigate(Routes.artist(a.id)) },
                    trailing = { FavoriteButton(vm, "artist", a.id, compact = true) })
            }
        }
        if (albums.isNotEmpty()) {
            item { SectionHeader("Albums") }
            items(albums, key = { "al${it.id}" }) { al ->
                AlbumListRow(al, names?.get(al.artistId) ?: "") { nav.navigate(Routes.album(al.id)) }
            }
        }
        if (songs.isNotEmpty()) {
            item { SectionHeader("Songs") }
            itemsIndexed(songs, key = { _, s -> "s${s.id}" }) { idx, song ->
                SongRow(song, artistName = songTracks.getOrNull(idx)?.artistName,
                    albumName = songTracks.getOrNull(idx)?.album?.name, showArtwork = true,
                    onClick = { if (songTracks.isNotEmpty()) onPlay(songTracks, idx) },
                    trailing = {
                        AddToPlaylistButton(vm, "song", song.id, compact = true)
                        FavoriteButton(vm, "song", song.id, compact = true)
                    })
            }
        }
    }
}

@Composable
fun PlaylistsScreen(vm: MagnatuneViewModel, nav: NavController, onPlay: OnPlay) {
    val playlists by vm.userStore.playlists.collectAsStateWithLifecycle()
    if (playlists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No playlists yet — use the + button on any song.", color = MagSecondary,
                modifier = Modifier.padding(32.dp))
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(playlists, key = { it.id }) { pl ->
            ListItem(
                headlineContent = { Text(pl.name) },
                supportingContent = { Text("${pl.count} song${if (pl.count == 1) "" else "s"}") },
                trailingContent = {
                    IconButton(onClick = { vm.deletePlaylist(pl.id) }) { com.magnatune.player.ui.components.FaIcon(com.magnatune.player.ui.components.Fa.trash, "Delete", size = 20.dp) }
                },
                modifier = Modifier.clickable { nav.navigate(Routes.userPlaylist(pl.id)) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun UserPlaylistDetailScreen(vm: MagnatuneViewModel, nav: NavController, playlistId: Long, onPlay: OnPlay) {
    val playlisted by vm.userStore.playlistedSongIds.collectAsStateWithLifecycle()
    val songs by produceState(emptyList<Song>(), playlistId, playlisted) { value = vm.songsInPlaylist(playlistId) }
    val tracks by produceState(emptyList<PlayableTrack>(), songs) { value = vm.playable(songs) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            androidx.compose.material3.Button(
                onClick = { if (tracks.isNotEmpty()) onPlay(tracks, 0) },
                modifier = Modifier.padding(16.dp),
            ) { com.magnatune.player.ui.components.FaIcon(com.magnatune.player.ui.components.Fa.play, null, size = 16.dp); Text("  Play all") }
            HorizontalDivider()
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { idx, song ->
            SongRow(song, artistName = tracks.getOrNull(idx)?.artistName,
                albumName = tracks.getOrNull(idx)?.album?.name, showArtwork = true,
                onClick = { if (tracks.isNotEmpty()) onPlay(tracks, idx) },
                trailing = {
                    IconButton(onClick = { vm.removeFromPlaylist(song.id, playlistId) }) {
                        com.magnatune.player.ui.components.FaIcon(com.magnatune.player.ui.components.Fa.trash, "Remove", size = 18.dp)
                    }
                })
        }
    }
}
