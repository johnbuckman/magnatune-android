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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val supp by vm.suppression.collectAsStateWithLifecycle()
    if (rows == null || names == null) { Loading(); return }
    // Drop disliked-genre albums; a row whose albums are all gone (a disliked genre) vanishes.
    val visibleRows = rows!!.map { (g, a) -> g to a.filter { it.id !in supp.albums } }.filter { it.second.isNotEmpty() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(visibleRows, key = { it.first }) { (genre, albums) ->
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
 *  shared by the Albums/Artists pages. An optional genre picker (styled like the compact sort
 *  control) sits between them when [genres] is supplied: null selection means "Genres" = all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseHeader(
    filter: String, onFilter: (String) -> Unit, placeholder: String,
    sort: BrowseSort, onSort: (BrowseSort) -> Unit,
    genres: List<com.magnatune.player.model.Genre>? = null,
    selectedGenre: Long? = null, onGenre: (Long?) -> Unit = {},
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
            if (genres != null) GenrePicker(genres, selectedGenre, onGenre)
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

/** Genre filter for the Albums/Artists browse screens: a button showing the current choice
 *  ("Genres" = all) that opens a menu of the supplied genres. Styled like [SortDropdown]. */
@Composable
private fun GenrePicker(
    genres: List<com.magnatune.player.model.Genre>, selected: Long?, onSelect: (Long?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val label = selected?.let { id -> genres.firstOrNull { it.id == id }?.name } ?: "All Genres"
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label  ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("All Genres") }, onClick = { onSelect(null); open = false })
            genres.forEach { g ->
                DropdownMenuItem(text = { Text(g.name) }, onClick = { onSelect(g.id); open = false })
            }
        }
    }
}

@Composable
fun AlbumsScreen(vm: MagnatuneViewModel, nav: NavController) {
    val names by produceState<Map<Long, String>?>(null) { value = vm.artistNames() }
    var sort by rememberSaveable { mutableStateOf(BrowseSort.POPULAR) }
    var filter by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf<Long?>(null) }
    val albums by produceState<List<Album>?>(null, sort) { value = vm.albumsSorted(sort) }
    val allGenres by produceState(initialValue = emptyList<com.magnatune.player.model.Genre>()) { value = vm.allGenres() }
    val supp by vm.suppression.collectAsStateWithLifecycle()

    // Search-aware genre options: only genres represented among albums whose NAME matches the
    // search text (computed across the FULL list, independent of the selected genre). Empty text
    // → all genres. Always keep the currently-selected genre present even if no name-matches.
    // Disliked genres (when "Hide things I dislike" is on) are excluded, like on GenresScreen.
    val genreOptions by produceState(emptyList<com.magnatune.player.model.Genre>(), albums, filter, allGenres, genre, supp) {
        val all = albums
        val base = if (filter.isBlank() || all == null) {
            allGenres
        } else {
            val matchIds = all.filter { it.name.contains(filter, ignoreCase = true) }.map { it.id }
            val repIds = vm.genreIdsForAlbums(matchIds)
            allGenres.filter { it.id in repIds || it.id == genre }
        }
        value = base.filter { it.id !in supp.genres }
    }
    // Album ids in the selected genre (null = all genres).
    val genreAlbumIds by produceState<Set<Long>?>(null, genre) {
        value = genre?.let { vm.albumIdsForGenre(it) }
    }

    Column(Modifier.fillMaxSize()) {
        BrowseHeader(filter, { filter = it }, "Filter albums", sort, { sort = it },
            genres = genreOptions, selectedGenre = genre, onGenre = { genre = it })
        if (albums == null || names == null) { Loading(); return@Column }
        val shown = albums!!.filter {
            it.id !in supp.albums &&
                (genreAlbumIds == null || it.id in genreAlbumIds!!) &&
                (filter.isBlank() || it.name.contains(filter, ignoreCase = true))
        }
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
    var genre by rememberSaveable { mutableStateOf<Long?>(null) }
    val artists by produceState(initialValue = emptyList<com.magnatune.player.model.Artist>(), sort) { value = vm.artistsSorted(sort) }
    val allGenres by produceState(initialValue = emptyList<com.magnatune.player.model.Genre>()) { value = vm.allGenres() }
    val supp by vm.suppression.collectAsStateWithLifecycle()

    // Search-aware genre options: only genres represented among artists whose NAME matches the
    // search text (across the FULL list, independent of the selection). Empty text → all genres.
    // Always keep the currently-selected genre present even if no name-matches. Disliked genres
    // (when "Hide things I dislike" is on) are excluded, like on GenresScreen.
    val genreOptions by produceState(emptyList<com.magnatune.player.model.Genre>(), artists, filter, allGenres, genre, supp) {
        val base = if (filter.isBlank()) {
            allGenres
        } else {
            val matchIds = artists.filter { it.name.contains(filter, ignoreCase = true) }.map { it.id }
            val repIds = vm.genreIdsForArtists(matchIds)
            allGenres.filter { it.id in repIds || it.id == genre }
        }
        value = base.filter { it.id !in supp.genres }
    }
    // Artist ids that have an album in the selected genre (null = all genres).
    val genreArtistIds by produceState<Set<Long>?>(null, genre) {
        value = genre?.let { vm.artistIdsForGenre(it) }
    }

    Column(Modifier.fillMaxSize()) {
        BrowseHeader(filter, { filter = it }, "Filter artists", sort, { sort = it },
            genres = genreOptions, selectedGenre = genre, onGenre = { genre = it })
        val shown = artists.filter {
            it.id !in supp.artists &&
                (genreArtistIds == null || it.id in genreArtistIds!!) &&
                (filter.isBlank() || it.name.contains(filter, ignoreCase = true))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { artist ->
                ArtistRow(artist = artist, albumName = null, onClick = { nav.navigate(Routes.artist(artist.id)) },
                    trailing = {
                        com.magnatune.player.ui.components.AddToPlaylistButton(vm, "artist", artist.id, compact = true)
                        com.magnatune.player.ui.components.FavoriteButton(vm, "artist", artist.id, compact = true)
                    })
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
    val supp by vm.suppression.collectAsStateWithLifecycle()
    // Album count per genre, recomputed when suppression changes so it matches the visible list.
    val counts by produceState(emptyMap<Long, Int>(), supp) { value = vm.albumCountByGenre() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(genres.filter { it.id !in supp.genres }, key = { it.id }) { genre ->
            val n = counts[genre.id] ?: 0
            ListItem(
                headlineContent = { Text(genre.name) },
                supportingContent = { Text("$n album${if (n == 1) "" else "s"}") },
                leadingContent = {
                    com.magnatune.player.ui.components.FaIcon(genreIcon(genre.name), null,
                        tint = com.magnatune.player.ui.theme.MagAccent, size = 20.dp)
                },
                // Disliking a genre hides it here + in Popular and hides its songs everywhere
                // (albums/songs/playlists), plus artists/tags/featured left empty.
                trailingContent = { com.magnatune.player.ui.components.DislikeButton(vm, "genre", genre.id) },
                modifier = Modifier.clickableRow { nav.navigate(Routes.genre(genre.id)) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun TagsScreen(vm: MagnatuneViewModel, nav: NavController) {
    val tags by produceState(initialValue = emptyList<com.magnatune.player.model.Tag>()) { value = vm.allTags() }
    val supp by vm.suppression.collectAsStateWithLifecycle()
    // Album count per tag, recomputed when suppression changes so it matches the visible list.
    val counts by produceState(emptyMap<Long, Int>(), supp) { value = vm.albumCountByTag() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(tags.filter { it.id !in supp.tags }, key = { it.id }) { tag ->
            val n = counts[tag.id] ?: 0
            ListItem(headlineContent = { Text(tag.name) },
                supportingContent = { Text("$n album${if (n == 1) "" else "s"}") },
                modifier = Modifier.clickableRow { nav.navigate(Routes.tag(tag.id)) })
            HorizontalDivider()
        }
    }
}

@Composable
fun FeaturedScreen(vm: MagnatuneViewModel, nav: NavController) {
    val playlists by produceState(initialValue = emptyList<com.magnatune.player.model.CatalogPlaylist>()) { value = vm.catalogPlaylists() }
    val supp by vm.suppression.collectAsStateWithLifecycle()
    // Track count per Featured playlist, recomputed when suppression changes.
    val counts by produceState(emptyMap<Long, Int>(), supp) { value = vm.trackCountByCatalogPlaylist() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(playlists.filter { it.id !in supp.playlists }, key = { it.id }) { pl ->
            val n = counts[pl.id] ?: 0
            ListItem(headlineContent = { Text(pl.name) },
                supportingContent = { Text("$n track${if (n == 1) "" else "s"}") },
                modifier = Modifier.clickableRow { nav.navigate(Routes.catalogPlaylist(pl.id)) })
            HorizontalDivider()
        }
    }
}

/** Genre detail = a LIST of the artists in that genre (an artist is "in" a genre if they have an
 *  album in it), matching the iOS GenreArtistsView. Tapping a row opens the artist's detail page
 *  (their albums). Each row carries the same inline favorite + add-to-playlist buttons as the
 *  Artists browse list. */
@Composable
fun GenreDetailScreen(vm: MagnatuneViewModel, nav: NavController, genreId: Long) {
    val artists by produceState<List<com.magnatune.player.model.Artist>?>(null, genreId) { value = vm.artistsForGenre(genreId) }
    val supp by vm.suppression.collectAsStateWithLifecycle()
    if (artists == null) { Loading(); return }
    val shown = artists!!.filter { it.id !in supp.artists }
    LazyColumn(Modifier.fillMaxSize()) {
        items(shown, key = { it.id }) { artist ->
            ArtistRow(artist = artist, albumName = null, onClick = { nav.navigate(Routes.artist(artist.id)) },
                trailing = {
                    com.magnatune.player.ui.components.AddToPlaylistButton(vm, "artist", artist.id, compact = true)
                    com.magnatune.player.ui.components.FavoriteButton(vm, "artist", artist.id, compact = true)
                })
            HorizontalDivider()
        }
    }
}

@Composable
fun TagDetailScreen(vm: MagnatuneViewModel, nav: NavController, tagId: Long) {
    val names by produceState<Map<Long, String>?>(null) { value = vm.artistNames() }
    val albums by produceState<List<Album>?>(null, tagId) { value = vm.albumsForTag(tagId) }
    val supp by vm.suppression.collectAsStateWithLifecycle()
    if (albums == null || names == null) { Loading(); return }
    LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
        items(albums!!.filter { it.id !in supp.albums }, key = { it.id }) { album ->
            AlbumCell(album, names!![album.artistId] ?: "", onClick = { nav.navigate(Routes.album(album.id)) })
        }
    }
}

/** Small helper to make a row clickable. */
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable { onClick() }
