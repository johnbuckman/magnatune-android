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
import androidx.compose.runtime.setValue
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

    val artistsRaw by produceState(emptyList<Artist>(), favArtistIds) { value = vm.favoriteArtists() }
    val albumsRaw by produceState(emptyList<Album>(), favAlbumIds) { value = vm.favoriteAlbums() }
    val songsRaw by produceState(emptyList<Song>(), favSongIds) { value = vm.favoriteSongs() }
    val names by produceState<Map<Long, String>?>(null) { value = vm.artistNames() }
    val songTracksRaw by produceState(emptyList<PlayableTrack>(), songsRaw) { value = vm.playable(songsRaw) }
    // Hide anything caught by "Hide things I dislike" (e.g. a favorite album's disliked-genre songs).
    val supp by vm.suppression.collectAsStateWithLifecycle()
    val artists = artistsRaw.filter { it.id !in supp.artists }
    val albums = albumsRaw.filter { it.id !in supp.albums }
    val songs = songsRaw.filter { it.id !in supp.songs }
    val songTracks = songTracksRaw.filter { it.song.id !in supp.songs }

    // Per-section Play-all queues (all songs across the favorite artists / albums), with disliked
    // songs filtered out so playback matches the visible list.
    val artistTracksRaw by produceState(emptyList<PlayableTrack>(), artists) { value = vm.playableForArtists(artists.map { it.id }) }
    val albumTracksRaw by produceState(emptyList<PlayableTrack>(), albums) { value = vm.playableForAlbums(albums.map { it.id }) }
    val artistTracks = artistTracksRaw.filter { it.song.id !in supp.songs }
    val albumTracks = albumTracksRaw.filter { it.song.id !in supp.songs }

    if (artists.isEmpty() && albums.isEmpty() && songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No favorites yet — tap the heart on any song, album, or artist.", color = MagSecondary,
                modifier = Modifier.padding(32.dp))
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (artists.isNotEmpty()) {
            item { SectionHeaderWithPlayAll("Artists") { if (artistTracks.isNotEmpty()) onPlay(artistTracks, 0) } }
            items(artists, key = { "ar${it.id}" }) { a ->
                ArtistRow(a, albumName = null, onClick = { nav.navigate(Routes.artist(a.id)) },
                    trailing = { FavoriteButton(vm, "artist", a.id, compact = true) })
            }
        }
        if (albums.isNotEmpty()) {
            item { SectionHeaderWithPlayAll("Albums") { if (albumTracks.isNotEmpty()) onPlay(albumTracks, 0) } }
            items(albums, key = { "al${it.id}" }) { al ->
                AlbumListRow(al, names?.get(al.artistId) ?: "") { nav.navigate(Routes.album(al.id)) }
            }
        }
        if (songs.isNotEmpty()) {
            item { SectionHeaderWithPlayAll("Songs") { if (songTracks.isNotEmpty()) onPlay(songTracks, 0) } }
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

/** Section header with a trailing Play-all action, used on the Favorites sections. Matches the
 *  UserPlaylistDetailScreen play-all (Fa.play + "Play all"). */
@Composable
private fun SectionHeaderWithPlayAll(title: String, onPlayAll: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { SectionHeader(title) }
        androidx.compose.material3.TextButton(onClick = onPlayAll) {
            com.magnatune.player.ui.components.FaIcon(com.magnatune.player.ui.components.Fa.play, null, size = 14.dp)
            Text("  Play all")
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
    val playlists by vm.userStore.playlists.collectAsStateWithLifecycle()
    val songsRaw by produceState(emptyList<Song>(), playlistId, playlisted) { value = vm.songsInPlaylist(playlistId) }
    val tracksRaw by produceState(emptyList<PlayableTrack>(), songsRaw) { value = vm.playable(songsRaw) }
    val supp by vm.suppression.collectAsStateWithLifecycle()
    val songs = songsRaw.filter { it.id !in supp.songs }
    val tracks = tracksRaw.filter { it.song.id !in supp.songs }
    val playlistName = playlists.firstOrNull { it.id == playlistId }?.name ?: ""

    var renaming by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (renaming) {
        RenamePlaylistDialog(current = playlistName, onDismiss = { renaming = false }) { newName ->
            vm.renamePlaylist(playlistId, newName); renaming = false
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Button(onClick = { if (tracks.isNotEmpty()) onPlay(tracks, 0) }) {
                    com.magnatune.player.ui.components.FaIcon(com.magnatune.player.ui.components.Fa.play, null, size = 16.dp); Text("  Play all")
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                // Rename action — matches the create/delete playlist controls' icon-button style.
                IconButton(onClick = { renaming = true }) {
                    com.magnatune.player.ui.components.FaIcon(com.magnatune.player.ui.components.Fa.penToSquare, "Rename playlist", size = 18.dp)
                }
            }
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

/** Rename dialog for a user playlist — a text field prefilled with the current name (mirrors iOS). */
@Composable
private fun RenamePlaylistDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(current) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename playlist") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name, onValueChange = { name = it },
                singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onRename(name) }, enabled = name.trim().isNotEmpty(),
            ) { Text("Rename") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
