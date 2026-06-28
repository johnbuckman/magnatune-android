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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    suspend fun allGenres() = io { allGenres() }
    suspend fun allTags() = io { allTags() }
    suspend fun catalogPlaylists() = io { catalogPlaylists() }

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

    // ---- downloads / offline ----
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
