package com.magnatune.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.magnatune.player.data.AppContainer
import com.magnatune.player.db.CatalogStore
import com.magnatune.player.db.UserStore
import com.magnatune.player.model.Album
import com.magnatune.player.model.PlayableTrack
import com.magnatune.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sort order for the Albums/Artists browse lists, shown in a menu beside the filter box. */
enum class BrowseSort(val label: String) {
    POPULAR("Popular"), ALPHABETICAL("Alphabetical"), RECENT("Date")   // segmented order; "Date" = was "Recent"
}

/**
 * Shared view-model exposing the catalog (read via IO-dispatched suspend helpers) and the user
 * state (favorites/dislikes/playlists), plus actions. Mirrors the iOS AppModel's role.
 */
class MagnatuneViewModel(val container: AppContainer) : ViewModel() {
    val catalog: CatalogStore get() = container.catalog
    val userStore: UserStore get() = container.userStore
    val settings get() = container.settings
    val credentials get() = container.credentials

    private suspend fun <T> io(block: CatalogStore.() -> T): T =
        withContext(Dispatchers.IO) { container.catalog.block() }

    // ---- catalog loaders (suspend; safe to call from LaunchedEffect/produceState) ----
    suspend fun popularByGenre(perGenre: Int = 12): List<Pair<String, List<Album>>> = io {
        allGenres().mapNotNull { g ->
            val albums = newReleases(listOf(g.id), perGenre)
            if (albums.isEmpty()) null else g.name to albums
        }
    }
    suspend fun allArtists() = io { allArtists() }
    suspend fun artistNames() = io { artistNames() }
    suspend fun allAlbums() = io { albumsByPopularity() }
    suspend fun albumsSorted(sort: BrowseSort) = io {
        when (sort) {
            BrowseSort.RECENT -> albumsByRecent()
            BrowseSort.ALPHABETICAL -> allAlbums()
            BrowseSort.POPULAR -> albumsByPopularity()
        }
    }
    suspend fun artistsSorted(sort: BrowseSort) = io {
        when (sort) {
            BrowseSort.RECENT -> artistsByRecent()
            BrowseSort.ALPHABETICAL -> allArtists()
            BrowseSort.POPULAR -> artistsByPopularity()
        }
    }
    suspend fun allGenres() = io { allGenres() }
    suspend fun allTags() = io { allTags() }
    suspend fun catalogPlaylists() = io { catalogPlaylists() }

    // ---- genre filtering for the Artists/Albums browse screens ----
    /** Artist ids that have an album in the given genre (an artist is "in" a genre if they have
     *  an album in it). Drives the Artists-browse genre filter. */
    suspend fun artistIdsForGenre(genreId: Long) = io { artistIdsForGenre(genreId) }
    /** Album ids in the given genre. Drives the Albums-browse genre filter. */
    suspend fun albumIdsForGenre(genreId: Long) = io { albumIdsForGenre(genreId) }

    /** Genres represented among the given artists (by their albums). For the search-aware picker. */
    suspend fun genreIdsForArtists(artistIds: List<Long>): Set<Long> = withContext(Dispatchers.IO) {
        container.catalog.genreIDsForArtists(artistIds)
    }
    /** Genres represented among the given albums. For the search-aware picker. */
    suspend fun genreIdsForAlbums(albumIds: List<Long>): Set<Long> = withContext(Dispatchers.IO) {
        container.catalog.genreIDsForAlbums(albumIds)
    }

    // ---- per-row counts (exclude disliked items so the count matches what's shown) ----
    /** Album count per genre id. Excludes albums hidden by "Hide things I dislike" (so the count
     *  matches the visible list); when the toggle is off, suppression is empty → full totals. */
    suspend fun albumCountByGenre(): Map<Long, Int> = withContext(Dispatchers.IO) {
        val hidden = suppression.value.albums
        allGenres().associate { g -> g.id to albumsForGenre(g.id).count { it.id !in hidden } }
    }
    /** Album count per tag id, excluding disliked albums. */
    suspend fun albumCountByTag(): Map<Long, Int> = withContext(Dispatchers.IO) {
        val hidden = suppression.value.albums
        allTags().associate { t -> t.id to albumsForTag(t.id).count { it.id !in hidden } }
    }
    /** Track count per Featured (catalog) playlist id, excluding disliked songs. */
    suspend fun trackCountByCatalogPlaylist(): Map<Long, Int> = withContext(Dispatchers.IO) {
        val hidden = suppression.value.songs
        catalogPlaylists().associate { pl -> pl.id to songsForCatalogPlaylist(pl.id).count { it.id !in hidden } }
    }

    suspend fun artist(id: Long) = io { artist(id) }
    suspend fun albumsForArtist(id: Long) = io { albumsForArtist(id) }
    suspend fun firstAlbumName(artistId: Long) = io { firstAlbumName(artistId) }
    suspend fun album(id: Long) = io { album(id) }
    suspend fun songsForAlbum(id: Long) = io { songsForAlbum(id) }
    suspend fun artistsForGenre(id: Long) = io { artistsForGenre(id) }
    suspend fun albumsForGenre(id: Long) = io { albumsForGenre(id) }
    suspend fun albumsForTag(id: Long) = io { albumsForTag(id) }
    suspend fun songsForCatalogPlaylist(id: Long) = io { songsForCatalogPlaylist(id) }
    suspend fun genresAndTags(albumId: Long) = io { genresAndTags(albumId) }
    suspend fun genresAndTagsForArtist(artistId: Long) = io { genresAndTagsForArtist(artistId) }
    suspend fun recommendedAlbums(albumId: Long) = io { recommendedAlbums(albumId) }
    suspend fun recommendedArtists(artistId: Long) = io { recommendedArtists(artistId) }

    /** Song-title search for the dedicated Songs browse page (mirrors iOS SongsView). */
    suspend fun searchSongsOnly(q: String): List<Song> = withContext(Dispatchers.IO) {
        container.catalog.searchSongs(q, 200)
    }

    suspend fun search(q: String): SearchResults = withContext(Dispatchers.IO) {
        val c = container.catalog
        SearchResults(c.searchArtists(q, 30), c.searchAlbums(q, 30), c.searchSongs(q, 60))
    }

    /** Build PlayableTracks for a list of songs (resolves album + artist names). */
    suspend fun playable(songs: List<Song>): List<PlayableTrack> = io { makePlayable(songs) }
    suspend fun playableForAlbum(albumId: Long): List<PlayableTrack> = io { makePlayable(songsForAlbum(albumId)) }
    suspend fun playableForArtist(artistId: Long): List<PlayableTrack> = io { makePlayable(songsForArtist(artistId)) }

    // ---- user actions ----
    fun toggleFavorite(kind: String, id: Long) = viewModelScope.launch { userStore.toggleFavorite(kind, id); deduplicateFavorites() }
    fun toggleDislike(kind: String, id: Long) = viewModelScope.launch { userStore.toggleDislike(kind, id) }

    // ---- dislike suppression (mirrors iOS AppModel.recomputeDislikeSuppression) ----
    // Reactive set of everything hidden while "Hide things I dislike" is on. A disliked
    // artist hides its albums; a disliked genre hides every album in it; both drag the
    // songs along. An artist whose entire catalog is in disliked genres is hidden too, and
    // a tag / Featured playlist left with no visible item is hidden. When the toggle is off
    // nothing is suppressed. Screens collect this and filter their lists, so disliking
    // updates the UI live.
    val suppression: StateFlow<Suppression> = combine(
        userStore.dislikedArtistIds,
        userStore.dislikedAlbumIds,
        userStore.dislikedSongIds,
        userStore.dislikedGenreIds,
        settings.hideDislikes,
    ) { dArtists, dAlbums, dSongs, dGenres, hide ->
        if (!hide) Suppression()
        else withContext(Dispatchers.IO) { computeSuppression(dArtists, dAlbums, dSongs, dGenres) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Suppression())

    private fun computeSuppression(
        dArtists: Set<Long>, dAlbums: Set<Long>, dSongs: Set<Long>, dGenres: Set<Long>,
    ): Suppression {
        val c = container.catalog
        val artists = dArtists.toMutableSet()
        val albums = dAlbums.toMutableSet()
        val songs = dSongs.toMutableSet()
        for (aid in dArtists) for (al in c.albumsForArtist(aid)) albums.add(al.id)
        // A disliked genre hides every album in it; remember those albums + their artists.
        val genreAlbums = mutableSetOf<Long>()
        val candidates = mutableSetOf<Long>()
        for (gid in dGenres) for (al in c.albumsForGenre(gid)) {
            albums.add(al.id); genreAlbums.add(al.id); candidates.add(al.artistId)
        }
        // Hide an artist whose every album is in a disliked genre.
        for (aid in candidates) if (aid !in artists) {
            val theirs = c.albumsForArtist(aid)
            if (theirs.isNotEmpty() && theirs.all { it.id in genreAlbums }) artists.add(aid)
        }
        // Every suppressed album drags its songs along.
        for (alid in albums) for (s in c.songsForAlbum(alid)) songs.add(s.id)
        // A Featured playlist with no surviving song, or a tag with no surviving album, hides.
        val emptyPlaylists = c.catalogPlaylists()
            .filter { pl -> c.songsForCatalogPlaylist(pl.id).all { it.id in songs } }
            .map { it.id }.toSet()
        val emptyTags = c.allTags()
            .filter { tag -> c.albumsForTag(tag.id).all { it.id in albums } }
            .map { it.id }.toSet()
        return Suppression(artists, albums, songs, dGenres.toSet(), emptyTags, emptyPlaylists)
    }

    // ---- favorites resolution ----
    suspend fun favoriteArtists(): List<com.magnatune.player.model.Artist> = withContext(Dispatchers.IO) {
        userStore.favoriteArtistIds.value.mapNotNull { container.catalog.artist(it) }.sortedBy { it.name.lowercase() }
    }
    suspend fun favoriteAlbums(): List<Album> = withContext(Dispatchers.IO) {
        userStore.favoriteAlbumIds.value.mapNotNull { container.catalog.album(it) }.sortedBy { it.name.lowercase() }
    }
    suspend fun favoriteSongs(): List<Song> = withContext(Dispatchers.IO) {
        userStore.favoriteSongIds.value.mapNotNull { container.catalog.song(it) }
    }

    /** Playable tracks for every song across the given albums (in album then track order).
     *  Used by the Favorites "Albums" section Play-all. */
    suspend fun playableForAlbums(albumIds: List<Long>): List<PlayableTrack> = io {
        makePlayable(albumIds.flatMap { songsForAlbum(it) })
    }
    /** Playable tracks for every song across the given artists. Used by Favorites "Artists" Play-all. */
    suspend fun playableForArtists(artistIds: List<Long>): List<PlayableTrack> = io {
        makePlayable(artistIds.flatMap { songsForArtist(it) })
    }

    /** Expand a favorite/target into its song ids (song→itself, album→its songs, artist→all songs). */
    suspend fun songIdsFor(kind: String, id: Long): List<Long> = withContext(Dispatchers.IO) {
        when (kind) {
            "song" -> listOf(id)
            "album" -> container.catalog.songsForAlbum(id).map { it.id }
            "artist" -> container.catalog.songsForArtist(id).map { it.id }
            else -> emptyList()
        }
    }

    // ---- playlists ----
    fun createPlaylist(name: String, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = userStore.createPlaylist(name); onCreated(id)
    }
    fun deletePlaylist(id: Long) = viewModelScope.launch { userStore.deletePlaylist(id) }
    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) userStore.renamePlaylist(id, trimmed)
    }
    fun addToPlaylist(playlistId: Long, kind: String, id: Long) = viewModelScope.launch {
        songIdsFor(kind, id).forEach { userStore.addSong(it, playlistId) }
    }
    fun removeFromAllPlaylists(kind: String, id: Long) = viewModelScope.launch {
        userStore.removeSongsFromAllPlaylists(songIdsFor(kind, id))
    }
    suspend fun songsInPlaylist(playlistId: Long): List<Song> = withContext(Dispatchers.IO) {
        userStore.songIds(inPlaylist = playlistId).mapNotNull { container.catalog.song(it) }
    }
    fun removeFromPlaylist(songId: Long, playlistId: Long) = viewModelScope.launch { userStore.removeItem(songId, playlistId) }

    /** Drop redundant favorites — keep the broadest (a favorited song whose album/artist is also
     *  favorited is removed; a favorited album whose artist is favorited is removed). Mirrors iOS. */
    fun deduplicateFavorites() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val artistIds = userStore.favoriteArtistIds.value
            val albumIds = userStore.favoriteAlbumIds.value
            val songIds = userStore.favoriteSongIds.value
            val toRemove = mutableListOf<Pair<String, Long>>()
            for (alb in albumIds) {
                val a = container.catalog.album(alb) ?: continue
                if (a.artistId in artistIds) toRemove.add("album" to alb)
            }
            for (s in songIds) {
                val song = container.catalog.song(s) ?: continue
                val alb = container.catalog.album(song.albumId)
                if (song.albumId in albumIds || (alb != null && alb.artistId in artistIds)) toRemove.add("song" to s)
            }
            if (toRemove.isNotEmpty()) userStore.removeFavorites(toRemove)
        }
    }

    // ---- membership ----
    fun login(username: String, password: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = com.magnatune.player.data.Credentials.verify(username, password)
        if (ok) credentials.save(username, password)
        onResult(ok)
    }
    fun logout() = credentials.clear()

    // ---- playback / downloads / offline ----
    val playback get() = container.playback
    val downloads get() = container.downloads
    val isOnline get() = container.isOnline
    fun clearDownloads() = viewModelScope.launch { container.downloads.clearAll() }
    fun syncDownloadsNow() = viewModelScope.launch { container.downloads.syncAutoDownloads(respectToggle = false) }

    // ---- catalog status ----
    suspend fun catalogUpdateAvailable() = container.catalogSync.updateAvailable()
    fun refreshCatalog(onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val updated = container.catalogSync.refreshIfNeeded(force = true)
        if (updated) container.reopenCatalog()
        onDone(updated)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MagnatuneViewModel(container) as T
    }
}

data class SearchResults(
    val artists: List<com.magnatune.player.model.Artist>,
    val albums: List<Album>,
    val songs: List<Song>,
)

/** Everything currently hidden by "Hide things I dislike" (empty when the toggle is off). */
data class Suppression(
    val artists: Set<Long> = emptySet(),
    val albums: Set<Long> = emptySet(),
    val songs: Set<Long> = emptySet(),
    val genres: Set<Long> = emptySet(),
    val tags: Set<Long> = emptySet(),
    val playlists: Set<Long> = emptySet(),
)
