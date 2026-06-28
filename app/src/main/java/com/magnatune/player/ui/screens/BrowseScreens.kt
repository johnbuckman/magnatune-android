package com.magnatune.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.magnatune.player.model.Album
import com.magnatune.player.ui.MagnatuneViewModel
import com.magnatune.player.ui.Routes
import com.magnatune.player.ui.components.AlbumCell
import com.magnatune.player.ui.components.ArtistRow
import com.magnatune.player.ui.components.SectionHeader

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun PopularScreen(vm: MagnatuneViewModel, nav: NavController) {
    val names by produceState<Map<Long, String>?>(null) { value = vm.artistNames() }
    val rows by produceState<List<Pair<String, List<Album>>>?>(null) { value = vm.popularByGenre() }
    if (rows == null || names == null) { Loading(); return }
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows!!, key = { it.first }) { (genre, albums) ->
            SectionHeader(genre)
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
                items(albums, key = { it.id }) { album ->
                    AlbumCell(
                        album = album, artistName = names!![album.artistId] ?: "",
                        onClick = { nav.navigate(Routes.album(album.id)) },
                        modifier = Modifier.width(150.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumsScreen(vm: MagnatuneViewModel, nav: NavController) {
    val names by produceState<Map<Long, String>?>(null) { value = vm.artistNames() }
    val albums by produceState<List<Album>?>(null) { value = vm.allAlbums() }
    if (albums == null || names == null) { Loading(); return }
    LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
        items(albums!!, key = { it.id }) { album ->
            AlbumCell(album, names!![album.artistId] ?: "", onClick = { nav.navigate(Routes.album(album.id)) })
        }
    }
}

@Composable
fun ArtistsScreen(vm: MagnatuneViewModel, nav: NavController) {
    val artists by produceState(initialValue = emptyList<com.magnatune.player.model.Artist>()) { value = vm.allArtists() }
    if (artists.isEmpty()) { Loading(); return }
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.id }) { artist ->
            ArtistRow(artist = artist, albumName = null, onClick = { nav.navigate(Routes.artist(artist.id)) })
            HorizontalDivider()
        }
    }
}

@Composable
fun GenresScreen(vm: MagnatuneViewModel, nav: NavController) {
    val genres by produceState(initialValue = emptyList<com.magnatune.player.model.Genre>()) { value = vm.allGenres() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(genres, key = { it.id }) { genre ->
            ListItem(headlineContent = { Text(genre.name) },
                modifier = Modifier.clickableRow { nav.navigate(Routes.genre(genre.id)) })
            HorizontalDivider()
        }
    }
}

@Composable
fun TagsScreen(vm: MagnatuneViewModel, nav: NavController) {
    val tags by produceState(initialValue = emptyList<com.magnatune.player.model.Tag>()) { value = vm.allTags() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(tags, key = { it.id }) { tag ->
            ListItem(headlineContent = { Text(tag.name) },
                modifier = Modifier.clickableRow { nav.navigate(Routes.tag(tag.id)) })
            HorizontalDivider()
        }
    }
}

@Composable
fun FeaturedScreen(vm: MagnatuneViewModel, nav: NavController) {
    val playlists by produceState(initialValue = emptyList<com.magnatune.player.model.CatalogPlaylist>()) { value = vm.catalogPlaylists() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(playlists, key = { it.id }) { pl ->
            ListItem(headlineContent = { Text(pl.name) },
                modifier = Modifier.clickableRow { nav.navigate(Routes.catalogPlaylist(pl.id)) })
            HorizontalDivider()
        }
    }
}

@Composable
fun GenreDetailScreen(vm: MagnatuneViewModel, nav: NavController, genreId: Long) {
    val names by produceState<Map<Long, String>?>(null) { value = vm.artistNames() }
    val albums by produceState<List<Album>?>(null, genreId) { value = vm.albumsForGenre(genreId) }
    if (albums == null || names == null) { Loading(); return }
    LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
        items(albums!!, key = { it.id }) { album ->
            AlbumCell(album, names!![album.artistId] ?: "", onClick = { nav.navigate(Routes.album(album.id)) })
        }
    }
}

@Composable
fun TagDetailScreen(vm: MagnatuneViewModel, nav: NavController, tagId: Long) {
    val names by produceState<Map<Long, String>?>(null) { value = vm.artistNames() }
    val albums by produceState<List<Album>?>(null, tagId) { value = vm.albumsForTag(tagId) }
    if (albums == null || names == null) { Loading(); return }
    LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
        items(albums!!, key = { it.id }) { album ->
            AlbumCell(album, names!![album.artistId] ?: "", onClick = { nav.navigate(Routes.album(album.id)) })
        }
    }
}

/** Small helper to make a row clickable. */
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable { onClick() }
