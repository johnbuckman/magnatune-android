package com.magnatune.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.magnatune.player.model.Album
import com.magnatune.player.model.Artist
import com.magnatune.player.model.Genre
import com.magnatune.player.model.PlayableTrack
import com.magnatune.player.model.Song
import com.magnatune.player.model.Tag
import com.magnatune.player.ui.MagnatuneViewModel
import com.magnatune.player.ui.Routes
import com.magnatune.player.ui.components.Chip
import com.magnatune.player.ui.components.CoverImage
import com.magnatune.player.ui.components.FavoriteButton
import com.magnatune.player.ui.components.SongRow
import com.magnatune.player.ui.theme.MagSecondary

typealias OnPlay = (List<PlayableTrack>, Int) -> Unit

@Composable
private fun CenterLoading() {
    Box(Modifier.fillMaxSize())   // no spinner — local data loads near-instantly
}

@Composable
fun AlbumDetailScreen(vm: MagnatuneViewModel, nav: NavController, albumId: Long, onPlay: OnPlay) {
    val data by produceState<Triple<Album, String, List<Song>>?>(null, albumId) {
        val album = vm.album(albumId) ?: return@produceState
        val artistName = vm.artist(album.artistId)?.name ?: ""
        value = Triple(album, artistName, vm.songsForAlbum(albumId))
    }
    val chips by produceState<Pair<List<Genre>, List<Tag>>?>(null, albumId) { value = vm.genresAndTags(albumId) }
    val d = data ?: run { CenterLoading(); return }
    val (album, artistName, songs) = d
    val tracks by produceState(emptyList<PlayableTrack>(), albumId) { value = vm.playableForAlbum(albumId) }

    val current by vm.playback.currentTrack.collectAsStateWithLifecycle()
    val playing by vm.playback.isPlaying.collectAsStateWithLifecycle()
    val albumNowPlaying = current?.album?.id == album.id

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    CoverImage(artistName, album.name, points = 160.dp, modifier = Modifier.size(160.dp), cap = 600)
                    Spacer(Modifier.size(16.dp))
                    Column {
                        Text(album.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(artistName, style = MaterialTheme.typography.titleMedium, color = MagSecondary,
                            modifier = Modifier.padding(top = 2.dp).clickableNav { nav.navigate(Routes.artist(album.artistId)) })
                        Row(modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            PlayButton(albumNowPlaying && playing) { onPlay(tracks, 0) }
                            Button(onClick = { vm.settings.setShuffle(true); onPlay(tracks, 0) }) {
                                com.magnatune.player.ui.components.FaIcon(com.magnatune.player.ui.components.Fa.shuffle, null, size = 18.dp); Spacer(Modifier.size(4.dp)); Text("Shuffle")
                            }
                            FavoriteButton(vm, "album", album.id)
                            com.magnatune.player.ui.components.AddToPlaylistButton(vm, "album", album.id)
                            com.magnatune.player.ui.components.AlbumDownloadButton(vm, album.sku)
                        }
                    }
                }
                chips?.let { (genres, tags) ->
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        genres.forEach { g -> Chip(g.name) { nav.navigate(Routes.genre(g.id)) } }
                        tags.forEach { t -> Chip(t.name) { nav.navigate(Routes.tag(t.id)) } }
                    }
                }
                album.description?.takeIf { it.isNotBlank() }?.let {
                    com.magnatune.player.ui.components.ExpandableText(it, modifier = Modifier.padding(top = 12.dp))
                }
            }
            HorizontalDivider()
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { idx, song ->
            SongRow(song = song, isCurrent = current?.id == song.id, isPlaying = playing,
                onClick = { onPlay(tracks, idx) },
                trailing = {
                    tracks.getOrNull(idx)?.let { com.magnatune.player.ui.components.SongDownloadButton(vm, it) }
                    com.magnatune.player.ui.components.AddToPlaylistButton(vm, "song", song.id, compact = true)
                    FavoriteButton(vm, "song", song.id, compact = true)
                })
        }
    }
}

/** Play button that flips to "Now Playing" with a speaker icon while this list is playing. */
@Composable
private fun PlayButton(nowPlaying: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick) {
        com.magnatune.player.ui.components.FaIcon(if (nowPlaying) com.magnatune.player.ui.components.Fa.volumeHigh else com.magnatune.player.ui.components.Fa.play, null, size = 18.dp)
        Spacer(Modifier.size(4.dp))
        Text(if (nowPlaying) "Now Playing" else "Play")
    }
}

@Composable
fun ArtistDetailScreen(vm: MagnatuneViewModel, nav: NavController, artistId: Long, onPlay: OnPlay) {
    val artist by produceState<Artist?>(null, artistId) { value = vm.artist(artistId) }
    val firstAlbum by produceState<String?>(null, artistId) { value = vm.firstAlbumName(artistId) }
    val albums by produceState(emptyList<Album>(), artistId) { value = vm.albumsForArtist(artistId) }
    val artistTracks by produceState(emptyList<PlayableTrack>(), artistId) { value = vm.playableForArtist(artistId) }
    val a = artist ?: run { CenterLoading(); return }
    val current by vm.playback.currentTrack.collectAsStateWithLifecycle()
    val playing by vm.playback.isPlaying.collectAsStateWithLifecycle()
    val artistNowPlaying = current?.album?.artistId == artistId

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    com.magnatune.player.ui.components.ArtistPhoto(a.name, firstAlbum, a.photo, points = 120.dp)
                    Spacer(Modifier.size(16.dp))
                    Column {
                        Text(a.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            PlayButton(artistNowPlaying && playing) { onPlay(artistTracks, 0) }
                            FavoriteButton(vm, "artist", a.id)
                            com.magnatune.player.ui.components.AddToPlaylistButton(vm, "artist", a.id)
                        }
                    }
                }
                (a.bio ?: a.description)?.takeIf { it.isNotBlank() }?.let {
                    com.magnatune.player.ui.components.ExpandableText(it, modifier = Modifier.padding(top = 12.dp))
                }
            }
            HorizontalDivider()
            com.magnatune.player.ui.components.SectionHeader("Albums")
        }
        itemsIndexed(albums, key = { _, al -> al.id }) { _, album ->
            SongRowAlbumEntry(vm, album, a.name) { nav.navigate(Routes.album(album.id)) }
        }
    }
}

@Composable
private fun SongRowAlbumEntry(vm: MagnatuneViewModel, album: Album, artistName: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickableNav(onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CoverImage(artistName, album.name, points = 48.dp, modifier = Modifier.size(48.dp))
        Spacer(Modifier.size(12.dp))
        Text(album.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
fun CatalogPlaylistDetailScreen(vm: MagnatuneViewModel, nav: NavController, playlistId: Long, onPlay: OnPlay) {
    val songs by produceState<List<Song>?>(null, playlistId) { value = vm.songsForCatalogPlaylist(playlistId) }
    val tracks by produceState(emptyList<PlayableTrack>(), playlistId) {
        value = vm.playable(vm.songsForCatalogPlaylist(playlistId))
    }
    val list = songs ?: run { CenterLoading(); return }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Button(onClick = { onPlay(tracks, 0) }, modifier = Modifier.padding(16.dp)) {
                com.magnatune.player.ui.components.FaIcon(com.magnatune.player.ui.components.Fa.play, null, size = 16.dp); Spacer(Modifier.size(4.dp)); Text("Play all")
            }
            HorizontalDivider()
        }
        itemsIndexed(list, key = { _, s -> s.id }) { idx, song ->
            SongRow(song = song, artistName = tracks.getOrNull(idx)?.artistName,
                albumName = tracks.getOrNull(idx)?.album?.name, showArtwork = true,
                onClick = { onPlay(tracks, idx) },
                trailing = { FavoriteButton(vm, "song", song.id, compact = true) })
        }
    }
}

private fun Modifier.clickableNav(onClick: () -> Unit): Modifier =
    this.clickable { onClick() }
