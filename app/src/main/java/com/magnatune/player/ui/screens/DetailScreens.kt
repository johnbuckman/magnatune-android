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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
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
fun AlbumDetailScreen(vm: MagnatuneViewModel, nav: NavController, albumId: Long, onPlay: OnPlay, highlightSongId: Long? = null) {
    val data by produceState<Triple<Album, String, List<Song>>?>(null, albumId) {
        val album = vm.album(albumId) ?: return@produceState
        val artistName = vm.artist(album.artistId)?.name ?: ""
        value = Triple(album, artistName, vm.songsForAlbum(albumId))
    }
    val chips by produceState<Pair<List<Genre>, List<Tag>>?>(null, albumId) { value = vm.genresAndTags(albumId) }
    val d = data ?: run { CenterLoading(); return }
    val (album, artistName, allSongs) = d
    val allTracks by produceState(emptyList<PlayableTrack>(), albumId) { value = vm.playableForAlbum(albumId) }
    val supp by vm.suppression.collectAsStateWithLifecycle()
    // Hide disliked-genre songs; keep songs + tracks parallel so play indices stay aligned.
    val songs = allSongs.filter { it.id !in supp.songs }
    val tracks = allTracks.filter { it.song.id !in supp.songs }

    val current by vm.playback.currentTrack.collectAsStateWithLifecycle()
    val playing by vm.playback.isPlaying.collectAsStateWithLifecycle()
    val albumNowPlaying = current?.album?.id == album.id
    val recAlbumsRaw by produceState(emptyList<Album>(), albumId) { value = vm.recommendedAlbums(albumId) }
    val recAlbums = recAlbumsRaw.filter { it.id !in supp.albums }
    val recNames by produceState<Map<Long, String>?>(null) { value = vm.artistNames() }

    // Deep-linked from a search result: scroll the song into view and flash-highlight it (no
    // auto-play). The header is LazyColumn item 0, so the song sits at its songs-index + 1.
    val listState = rememberLazyListState()
    var highlightedId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(highlightSongId, songs.size) {
        val target = highlightSongId ?: return@LaunchedEffect
        val sIdx = songs.indexOfFirst { it.id == target }
        if (sIdx < 0) return@LaunchedEffect
        listState.animateScrollToItem(sIdx + 1)
        highlightedId = target
        delay(2200)
        highlightedId = null
    }

    var showCover by remember { mutableStateOf(false) }
    if (showCover) {
        val (hi, lo) = com.magnatune.player.ui.components.coverFullScreenUrls(artistName, album.name)
        com.magnatune.player.ui.components.FullScreenImage(hi, lo) { showCover = false }
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    CoverImage(artistName, album.name, points = 160.dp, modifier = Modifier.size(160.dp), cap = 600,
                        onClick = { showCover = true })
                    Spacer(Modifier.size(16.dp))
                    Column {
                        Text(album.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(artistName, style = MaterialTheme.typography.titleMedium, color = MagSecondary,
                            modifier = Modifier.padding(top = 2.dp).clickableNav { nav.navigate(Routes.artist(album.artistId)) })
                        formatReleaseDate(album.releaseDate)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MagSecondary,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                        Row(modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            PlayButton(albumNowPlaying && playing) { onPlay(tracks, 0) }
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
                isHighlighted = highlightedId == song.id,
                onClick = { onPlay(tracks, idx) },
                trailing = {
                    tracks.getOrNull(idx)?.let { com.magnatune.player.ui.components.SongDownloadButton(vm, it) }
                    com.magnatune.player.ui.components.AddToPlaylistButton(vm, "song", song.id, compact = true)
                    FavoriteButton(vm, "song", song.id, compact = true)
                })
        }
        if (recAlbums.isNotEmpty()) {
            item {
                com.magnatune.player.ui.components.SectionHeader("You might also like")
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    items(recAlbums, key = { it.id }) { rec ->
                        com.magnatune.player.ui.components.AlbumCell(
                            rec, recNames?.get(rec.artistId) ?: "",
                            onClick = { nav.navigate(Routes.album(rec.id)) }, modifier = Modifier.width(150.dp),
                        )
                    }
                }
            }
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
    val albumsRaw by produceState(emptyList<Album>(), artistId) { value = vm.albumsForArtist(artistId) }
    val artistTracksRaw by produceState(emptyList<PlayableTrack>(), artistId) { value = vm.playableForArtist(artistId) }
    val recArtistsRaw by produceState(emptyList<Artist>(), artistId) { value = vm.recommendedArtists(artistId) }
    val chips by produceState<Pair<List<Genre>, List<Tag>>?>(null, artistId) { value = vm.genresAndTagsForArtist(artistId) }
    val supp by vm.suppression.collectAsStateWithLifecycle()
    val albums = albumsRaw.filter { it.id !in supp.albums }
    val artistTracks = artistTracksRaw.filter { it.song.id !in supp.songs }
    val recArtists = recArtistsRaw.filter { it.id !in supp.artists }
    val a = artist ?: run { CenterLoading(); return }
    val current by vm.playback.currentTrack.collectAsStateWithLifecycle()
    val playing by vm.playback.isPlaying.collectAsStateWithLifecycle()
    val artistNowPlaying = current?.album?.artistId == artistId

    var showPhoto by remember { mutableStateOf(false) }
    if (showPhoto) {
        val (hi, lo) = com.magnatune.player.ui.components.artistFullScreenUrls(a.name, firstAlbum, a.photo)
        if (hi.isNotEmpty()) com.magnatune.player.ui.components.FullScreenImage(hi, lo) { showPhoto = false }
        else showPhoto = false
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    com.magnatune.player.ui.components.ArtistPhoto(a.name, firstAlbum, a.photo, points = 120.dp,
                        onClick = { showPhoto = true })
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
                chips?.let { (genres, tags) ->
                    if (genres.isNotEmpty() || tags.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            genres.forEach { g -> Chip(g.name) { nav.navigate(Routes.genre(g.id)) } }
                            tags.forEach { t -> Chip(t.name) { nav.navigate(Routes.tag(t.id)) } }
                        }
                    }
                }
            }
            HorizontalDivider()
            com.magnatune.player.ui.components.SectionHeader("Albums")
        }
        itemsIndexed(albums, key = { _, al -> al.id }) { _, album ->
            SongRowAlbumEntry(vm, album, a.name) { nav.navigate(Routes.album(album.id)) }
        }
        if (recArtists.isNotEmpty()) {
            item {
                com.magnatune.player.ui.components.SectionHeader("You might also like")
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    items(recArtists, key = { it.id }) { other ->
                        ArtistRecCell(other) { nav.navigate(Routes.artist(other.id)) }
                    }
                }
            }
        }
    }
}

/** Compact circular artist cell for the artist-page "You might also like" row. */
@Composable
private fun ArtistRecCell(artist: Artist, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(96.dp).clickableNav(onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        com.magnatune.player.ui.components.ArtistPhoto(artist.name, null, artist.photo, points = 80.dp)
        Spacer(Modifier.size(6.dp))
        Text(artist.name, style = MaterialTheme.typography.bodySmall, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MagSecondary)
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
    val tracksRaw by produceState(emptyList<PlayableTrack>(), playlistId) {
        value = vm.playable(vm.songsForCatalogPlaylist(playlistId))
    }
    val supp by vm.suppression.collectAsStateWithLifecycle()
    val list = songs?.filter { it.id !in supp.songs } ?: run { CenterLoading(); return }
    val tracks = tracksRaw.filter { it.song.id !in supp.songs }
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

/** Format the album release date (epoch SECONDS) as an abbreviated date, e.g. "Jan 5, 2020",
 *  matching iOS's `.formatted(date: .abbreviated, time: .omitted)`. Null/0 → no date. */
private fun formatReleaseDate(epochSeconds: Long?): String? {
    val secs = epochSeconds?.takeIf { it > 0 } ?: return null
    val fmt = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
    return fmt.format(java.util.Date(secs * 1000))
}
