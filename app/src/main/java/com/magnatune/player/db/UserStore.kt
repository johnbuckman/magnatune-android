package com.magnatune.player.db

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Reactive facade over the Room user DB, mirroring the iOS UserStore: exposes the favorite /
 * disliked / playlisted id sets as StateFlows and applies the mutual-exclusion rules
 * (favoriting removes a dislike and vice-versa).
 */
class UserStore(private val dao: UserDao, private val scope: CoroutineScope) {

    private fun idSet(flow: kotlinx.coroutines.flow.Flow<List<Long>>): StateFlow<Set<Long>> =
        flow.map { it.toSet() }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    val favoriteSongIds = idSet(dao.favoriteIdsFlow("song"))
    val favoriteAlbumIds = idSet(dao.favoriteIdsFlow("album"))
    val favoriteArtistIds = idSet(dao.favoriteIdsFlow("artist"))

    val dislikedSongIds = idSet(dao.dislikeIdsFlow("song"))
    val dislikedAlbumIds = idSet(dao.dislikeIdsFlow("album"))
    val dislikedArtistIds = idSet(dao.dislikeIdsFlow("artist"))
    val dislikedGenreIds = idSet(dao.dislikeIdsFlow("genre"))

    val playlistedSongIds = idSet(dao.playlistedSongIdsFlow())
    val playlists: StateFlow<List<PlaylistSummary>> =
        dao.playlistsFlow().stateIn(scope, SharingStarted.Eagerly, emptyList())

    private fun now() = System.currentTimeMillis() / 1000

    fun isFavorite(kind: String, id: Long) = when (kind) {
        "song" -> favoriteSongIds.value.contains(id)
        "album" -> favoriteAlbumIds.value.contains(id)
        "artist" -> favoriteArtistIds.value.contains(id)
        else -> false
    }

    fun isDisliked(kind: String, id: Long) = when (kind) {
        "song" -> dislikedSongIds.value.contains(id)
        "album" -> dislikedAlbumIds.value.contains(id)
        "artist" -> dislikedArtistIds.value.contains(id)
        "genre" -> dislikedGenreIds.value.contains(id)
        else -> false
    }

    suspend fun toggleFavorite(kind: String, id: Long) {
        if (isFavorite(kind, id)) dao.deleteFavorite(kind, id)
        else {
            dao.insertFavorite(Favorite(kind, id, now()))
            dao.deleteDislike(kind, id)        // can't both love and dislike
        }
    }

    suspend fun toggleDislike(kind: String, id: Long) {
        if (isDisliked(kind, id)) dao.deleteDislike(kind, id)
        else {
            dao.insertDislike(Dislike(kind, id, now()))
            dao.deleteFavorite(kind, id)       // opposite reactions can't coexist
        }
    }

    suspend fun removeFavorites(items: List<Pair<String, Long>>) {
        items.forEach { (kind, id) -> dao.deleteFavorite(kind, id) }
    }

    suspend fun favoriteIds(kind: String) = dao.favoriteIds(kind)
    suspend fun dislikeIds(kind: String) = dao.dislikeIds(kind)

    suspend fun recordPlay(songId: Long) = dao.insertPlay(PlayHistory(songId = songId, playedAt = now()))
    suspend fun recentlyPlayed(limit: Int = 30) = dao.recentlyPlayed(limit)

    // Playlists
    suspend fun createPlaylist(name: String): Long = dao.insertPlaylist(PlaylistEntity(name = name, createdAt = now()))
    suspend fun deletePlaylist(id: Long) { dao.deletePlaylistItems(id); dao.deletePlaylist(id) }
    suspend fun addSong(songId: Long, toPlaylist: Long) =
        dao.insertItem(PlaylistItem(playlistId = toPlaylist, songId = songId, position = dao.nextPosition(toPlaylist)))
    suspend fun songIds(inPlaylist: Long) = dao.songIdsInPlaylist(inPlaylist)
    suspend fun removeItem(songId: Long, fromPlaylist: Long) = dao.removeItem(fromPlaylist, songId)
    suspend fun removeSongsFromAllPlaylists(ids: List<Long>) { if (ids.isNotEmpty()) dao.removeSongsEverywhere(ids) }
    fun isOnAnyPlaylist(id: Long) = playlistedSongIds.value.contains(id)

    // Downloads
    suspend fun saveDownload(songId: Long, path: String) = dao.insertDownload(DownloadRow(songId, path, now()))
    suspend fun downloadPath(songId: Long) = dao.downloadPath(songId)
    suspend fun allDownloads() = dao.allDownloads()
    suspend fun deleteDownload(songId: Long) = dao.deleteDownload(songId)
    suspend fun clearDownloads() = dao.clearDownloads()
}
