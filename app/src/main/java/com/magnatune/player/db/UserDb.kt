package com.magnatune.player.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Read-write store for the user's own data. Separate file from the catalog so a catalog refresh
 *  never destroys personal data. Mirrors the iOS UserStore schema (favorites/dislikes/playlists/
 *  play_history/download). */

@Entity(tableName = "favorites", primaryKeys = ["kind", "ref_id"])
data class Favorite(
    val kind: String,                       // "song" | "album" | "artist"
    @ColumnInfo(name = "ref_id") val refId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "dislikes", primaryKeys = ["kind", "ref_id"])
data class Dislike(
    val kind: String,
    @ColumnInfo(name = "ref_id") val refId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "playlist")
data class PlaylistEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "playlist_item")
data class PlaylistItem(
    @androidx.room.PrimaryKey(autoGenerate = true) val rowid: Long = 0,
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "song_id") val songId: Long,
    val position: Int,
)

@Entity(tableName = "play_history")
data class PlayHistory(
    @androidx.room.PrimaryKey(autoGenerate = true) val rowid: Long = 0,
    @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "played_at") val playedAt: Long,
)

@Entity(tableName = "download", primaryKeys = ["song_id"])
data class DownloadRow(
    @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "local_path") val localPath: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Lightweight projection for the playlists list (name + item count). */
data class PlaylistSummary(val id: Long, val name: String, val count: Int)

@Dao
interface UserDao {
    // Favorites
    @Query("SELECT ref_id FROM favorites WHERE kind = :kind")
    fun favoriteIdsFlow(kind: String): Flow<List<Long>>
    @Query("SELECT ref_id FROM favorites WHERE kind = :kind ORDER BY created_at DESC")
    suspend fun favoriteIds(kind: String): List<Long>
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE kind = :kind AND ref_id = :id)")
    suspend fun isFavorite(kind: String, id: Long): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFavorite(f: Favorite)
    @Query("DELETE FROM favorites WHERE kind = :kind AND ref_id = :id") suspend fun deleteFavorite(kind: String, id: Long)

    // Dislikes
    @Query("SELECT ref_id FROM dislikes WHERE kind = :kind")
    fun dislikeIdsFlow(kind: String): Flow<List<Long>>
    @Query("SELECT ref_id FROM dislikes WHERE kind = :kind ORDER BY created_at DESC")
    suspend fun dislikeIds(kind: String): List<Long>
    @Query("SELECT EXISTS(SELECT 1 FROM dislikes WHERE kind = :kind AND ref_id = :id)")
    suspend fun isDisliked(kind: String, id: Long): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDislike(d: Dislike)
    @Query("DELETE FROM dislikes WHERE kind = :kind AND ref_id = :id") suspend fun deleteDislike(kind: String, id: Long)

    // Play history
    @Insert suspend fun insertPlay(p: PlayHistory)
    @Query("SELECT song_id FROM play_history GROUP BY song_id ORDER BY MAX(played_at) DESC LIMIT :limit")
    suspend fun recentlyPlayed(limit: Int): List<Long>

    // Playlists
    @Query("SELECT p.id AS id, p.name AS name, COUNT(pi.song_id) AS count FROM playlist p " +
        "LEFT JOIN playlist_item pi ON pi.playlist_id = p.id GROUP BY p.id ORDER BY p.created_at DESC")
    fun playlistsFlow(): Flow<List<PlaylistSummary>>
    @Query("SELECT p.id AS id, p.name AS name, COUNT(pi.song_id) AS count FROM playlist p " +
        "LEFT JOIN playlist_item pi ON pi.playlist_id = p.id GROUP BY p.id ORDER BY p.created_at DESC")
    suspend fun playlists(): List<PlaylistSummary>
    @Insert suspend fun insertPlaylist(p: PlaylistEntity): Long
    @Query("DELETE FROM playlist WHERE id = :id") suspend fun deletePlaylist(id: Long)
    @Query("DELETE FROM playlist_item WHERE playlist_id = :id") suspend fun deletePlaylistItems(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_item WHERE playlist_id = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int
    @Insert suspend fun insertItem(item: PlaylistItem)
    @Query("SELECT song_id FROM playlist_item WHERE playlist_id = :playlistId ORDER BY position")
    suspend fun songIdsInPlaylist(playlistId: Long): List<Long>
    @Query("DELETE FROM playlist_item WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun removeItem(playlistId: Long, songId: Long)
    @Query("DELETE FROM playlist_item WHERE song_id IN (:ids)") suspend fun removeSongsEverywhere(ids: List<Long>)
    @Query("SELECT DISTINCT song_id FROM playlist_item") fun playlistedSongIdsFlow(): Flow<List<Long>>

    // Downloads
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDownload(d: DownloadRow)
    @Query("SELECT * FROM download") suspend fun allDownloads(): List<DownloadRow>
    @Query("SELECT local_path FROM download WHERE song_id = :songId") suspend fun downloadPath(songId: Long): String?
    @Query("DELETE FROM download WHERE song_id = :songId") suspend fun deleteDownload(songId: Long)
    @Query("DELETE FROM download") suspend fun clearDownloads()
}

@Database(
    entities = [Favorite::class, Dislike::class, PlaylistEntity::class, PlaylistItem::class,
        PlayHistory::class, DownloadRow::class],
    version = 1,
    exportSchema = false,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun dao(): UserDao
}
