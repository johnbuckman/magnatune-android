package com.magnatune.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.magnatune.player.model.Album
import com.magnatune.player.ui.BrowseSort
import com.magnatune.player.ui.MagnatuneViewModel
import com.magnatune.player.ui.Routes
import com.magnatune.player.ui.components.AlbumCell
import com.magnatune.player.ui.components.ArtistRow
import com.magnatune.player.ui.components.SectionHeader

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize())   // no spinner — local data loads near-instantly
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

/** A filter text field with a segmented sort control (all three choices visible) to its right,
 *  shared by the Albums/Artists pages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseHeader(
    filter: String, onFilter: (String) -> Unit, placeholder: String,
    sort: BrowseSort, onSort: (BrowseSort) -> Unit,
) {
    // On a narrow row (phone / portrait) the 3-wide segmented control crowds the filter
    // field, so swap to a compact dropdown; keep the segmented control where there's room.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 520.dp
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = filter, onValueChange = onFilter,
                placeholder = { Text(placeholder) }, singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (compact) {
                SortDropdown(sort, onSort)
            } else {
                val opts = BrowseSort.entries
                SingleChoiceSegmentedButtonRow {
                    opts.forEachIndexed { i, s ->
                        SegmentedButton(
                            selected = sort == s,
                            onClick = { onSort(s) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = opts.size),
                        ) { Text(s.label) }
                    }
                }
            }
        }
    }
}

/** Compact sort control for narrow layouts: a button showing the current choice that opens a menu. */
@Composable
private fun SortDropdown(sort: BrowseSort, onSort: (BrowseSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("${sort.label}  ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BrowseSort.entries.forEach { s ->
                DropdownMenuItem(text = { Text(s.label) }, onClick = { onSort(s); open = false })
            }
        }
    }
}

@Composable
fun AlbumsScreen(vm: MagnatuneViewModel, nav: NavController) {
    val names by produceState<Map<Long, String>?>(null) { value = vm.artistNames() }
    var sort by rememberSaveable { mutableStateOf(BrowseSort.POPULAR) }
    var filter by rememberSaveable { mutableStateOf("") }
    val albums by produceState<List<Album>?>(null, sort) { value = vm.albumsSorted(sort) }
    Column(Modifier.fillMaxSize()) {
        BrowseHeader(filter, { filter = it }, "Filter albums", sort) { sort = it }
        if (albums == null || names == null) { Loading(); return@Column }
        val shown = if (filter.isBlank()) albums!! else albums!!.filter { it.name.contains(filter, ignoreCase = true) }
        LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
            items(shown, key = { it.id }) { album ->
                AlbumCell(album, names!![album.artistId] ?: "", onClick = { nav.navigate(Routes.album(album.id)) })
            }
        }
    }
}

@Composable
fun ArtistsScreen(vm: MagnatuneViewModel, nav: NavController) {
    var sort by rememberSaveable { mutableStateOf(BrowseSort.POPULAR) }
    var filter by rememberSaveable { mutableStateOf("") }
    val artists by produceState(initialValue = emptyList<com.magnatune.player.model.Artist>(), sort) { value = vm.artistsSorted(sort) }
    Column(Modifier.fillMaxSize()) {
        BrowseHeader(filter, { filter = it }, "Filter artists", sort) { sort = it }
        val shown = if (filter.isBlank()) artists else artists.filter { it.name.contains(filter, ignoreCase = true) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { artist ->
                ArtistRow(artist = artist, albumName = null, onClick = { nav.navigate(Routes.artist(artist.id)) })
                HorizontalDivider()
            }
        }
    }
}

/** Per-genre Font Awesome glyph, mirroring the iOS genreIcon() SF Symbol mapping. */
fun genreIcon(name: String): String = when (name) {
    "Classical" -> com.magnatune.player.ui.components.Fa.music
    "New Age" -> com.magnatune.player.ui.components.Fa.moon
    "Electronica" -> com.magnatune.player.ui.components.Fa.waveSquare
    "World" -> com.magnatune.player.ui.components.Fa.globe
    "Ambient" -> com.magnatune.player.ui.components.Fa.wind
    "Jazz" -> com.magnatune.player.ui.components.Fa.music
    "Hip Hop" -> com.magnatune.player.ui.components.Fa.microphone
    "Alt Rock" -> com.magnatune.player.ui.components.Fa.guitar
    "Electro Rock" -> com.magnatune.player.ui.components.Fa.bolt
    "Hard Rock" -> com.magnatune.player.ui.components.Fa.fire
    else -> com.magnatune.player.ui.components.Fa.music
}

@Composable
fun GenresScreen(vm: MagnatuneViewModel, nav: NavController) {
    val genres by produceState(initialValue = emptyList<com.magnatune.player.model.Genre>()) { value = vm.allGenres() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(genres, key = { it.id }) { genre ->
            ListItem(
                headlineContent = { Text(genre.name) },
                leadingContent = {
                    com.magnatune.player.ui.components.FaIcon(genreIcon(genre.name), null,
                        tint = com.magnatune.player.ui.theme.MagAccent, size = 20.dp)
                },
                modifier = Modifier.clickableRow { nav.navigate(Routes.genre(genre.id)) },
            )
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
