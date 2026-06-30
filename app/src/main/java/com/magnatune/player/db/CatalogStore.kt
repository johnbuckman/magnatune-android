package com.magnatune.player.db

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.magnatune.player.model.Album
import com.magnatune.player.model.Artist
import com.magnatune.player.model.CatalogPlaylist
import com.magnatune.player.model.Genre
import com.magnatune.player.model.PlayableTrack
import com.magnatune.player.model.Song
import com.magnatune.player.model.Tag

/**
 * Read-only access to the downloaded Magnatune catalog database. The file is replaced wholesale on
 * refresh; never written here. Mirrors the iOS CatalogStore raw-SQL queries 1:1.
 *
 * Open it on a background thread; SQLiteDatabase is thread-safe for reads.
 */
class CatalogStore(path: String) {
    private val db: SQLiteDatabase =
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)

    fun close() = db.close()

    // ---- cursor mappers ----
    private fun Cursor.long(col: String) = getLong(getColumnIndexOrThrow(col))
    private fun Cursor.longOrNull(col: String) = getColumnIndex(col).let { if (it < 0 || isNull(it)) null else getLong(it) }
    private fun Cursor.intOrNull(col: String) = getColumnIndex(col).let { if (it < 0 || isNull(it)) null else getInt(it) }
    private fun Cursor.str(col: String) = getColumnIndex(col).let { if (it < 0 || isNull(it)) "" else getString(it) }
    private fun Cursor.strOrNull(col: String) = getColumnIndex(col).let { if (it < 0 || isNull(it)) null else getString(it) }

    private fun toArtist(c: Cursor) = Artist(
        id = c.long("artists_id"), name = c.str("name"),
        description = c.strOrNull("description"), homepage = c.strOrNull("homepage"),
        bio = c.strOrNull("bio"), photo = c.strOrNull("photo"), society = c.strOrNull("society"),
        page = c.strOrNull("page"),
    )

    private fun toAlbum(c: Cursor) = Album(
        id = c.long("album_id"), artistId = c.long("artist_id"), name = c.str("name"),
        description = c.strOrNull("description"), sku = c.str("sku"),
        releaseDate = c.longOrNull("release_date"), popularity = c.intOrNull("popularity"),
        itunesBuyUrl = c.strOrNull("itunes_buy_url"),
    )

    private fun toSong(c: Cursor) = Song(
        id = c.long("song_id"), albumId = c.long("album_id"), name = c.str("name"),
        trackNo = c.intOrNull("track_no"), duration = c.intOrNull("duration"), mp3 = c.str("mp3"),
    )

    private fun toGenre(c: Cursor) = Genre(c.long("genre_id"), c.str("name"))
    private fun toTag(c: Cursor) = Tag(c.long("collections_id"), c.str("name"))
    private fun toCatalogPlaylist(c: Cursor) = CatalogPlaylist(
        id = c.long("playlist_id"), name = c.str("name"),
        sortOrder = c.intOrNull("sort_order"), ownerId = c.longOrNull("owner_id"),
    )

    private fun <T> query(sql: String, args: Array<String> = emptyArray(), map: (Cursor) -> T): List<T> {
        val out = ArrayList<T>()
        db.rawQuery(sql, args).use { c -> while (c.moveToNext()) out.add(map(c)) }
        return out
    }

    private fun <T> queryOne(sql: String, args: Array<String> = emptyArray(), map: (Cursor) -> T): T? {
        db.rawQuery(sql, args).use { c -> return if (c.moveToFirst()) map(c) else null }
    }

    // ---- Artists ----
    fun allArtists() = query("SELECT * FROM artists ORDER BY name COLLATE NOCASE", map = ::toArtist)
    /** Artists ordered by their newest album's release date (newest first). Artists have no date
     *  of their own, so we sort by MAX(album.release_date) across the artist's albums. */
    fun artistsByRecent() = query(
        "SELECT ar.* FROM artists ar LEFT JOIN albums a ON a.artist_id = ar.artists_id " +
            "GROUP BY ar.artists_id ORDER BY MAX(a.release_date) DESC, ar.name COLLATE NOCASE", map = ::toArtist)
    /** Artists ordered by their most-popular album (highest first), via MAX(album.popularity). */
    fun artistsByPopularity() = query(
        "SELECT ar.* FROM artists ar LEFT JOIN albums a ON a.artist_id = ar.artists_id " +
            "GROUP BY ar.artists_id ORDER BY MAX(a.popularity) DESC, ar.name COLLATE NOCASE", map = ::toArtist)
    fun artist(id: Long) = queryOne("SELECT * FROM artists WHERE artists_id = ?", arrayOf(id.toString()), ::toArtist)
    fun artistNames(): Map<Long, String> =
        query("SELECT artists_id, name FROM artists") { it.long("artists_id") to it.str("name") }.toMap()

    // ---- Albums ----
    fun allAlbums() = query("SELECT * FROM albums ORDER BY name COLLATE NOCASE", map = ::toAlbum)
    fun album(id: Long) = queryOne("SELECT * FROM albums WHERE album_id = ?", arrayOf(id.toString()), ::toAlbum)
    fun albumsForArtist(artistId: Long) =
        query("SELECT * FROM albums WHERE artist_id = ? ORDER BY release_date DESC", arrayOf(artistId.toString()), ::toAlbum)

    /** Most-popular album name for an artist — sized artist photos live in the album dirs. */
    fun firstAlbumName(artistId: Long): String? =
        queryOne("SELECT name FROM albums WHERE artist_id = ? ORDER BY popularity DESC, release_date DESC LIMIT 1",
            arrayOf(artistId.toString())) { it.str("name") }

    fun newReleases(limit: Int = 40) =
        query("SELECT * FROM albums ORDER BY release_date DESC LIMIT ?", arrayOf(limit.toString()), ::toAlbum)
    fun popularAlbums(limit: Int = 40) =
        query("SELECT * FROM albums ORDER BY popularity DESC LIMIT ?", arrayOf(limit.toString()), ::toAlbum)
    fun albumsByPopularity() =
        query("SELECT * FROM albums ORDER BY popularity DESC, name COLLATE NOCASE", map = ::toAlbum)
    fun albumsByRecent() =
        query("SELECT * FROM albums ORDER BY release_date DESC, name COLLATE NOCASE", map = ::toAlbum)

    // ---- Songs ----
    fun songsForAlbum(albumId: Long) =
        query("SELECT * FROM songs WHERE album_id = ? ORDER BY track_no", arrayOf(albumId.toString()), ::toSong)
    fun songsForArtist(artistId: Long) = albumsForArtist(artistId).flatMap { songsForAlbum(it.id) }
    fun song(id: Long) = queryOne("SELECT * FROM songs WHERE song_id = ?", arrayOf(id.toString()), ::toSong)

    // ---- Genres ----
    fun allGenres() = query("SELECT * FROM genres ORDER BY name COLLATE NOCASE", map = ::toGenre)
    fun albumsForGenre(genreId: Long) = query(
        "SELECT a.* FROM albums a JOIN genres_albums ga ON ga.album_id = a.album_id " +
            "WHERE ga.genre_id = ? ORDER BY a.popularity DESC", arrayOf(genreId.toString()), ::toAlbum)
    fun artistsForGenre(genreId: Long) = query(
        "SELECT DISTINCT ar.* FROM artists ar JOIN albums a ON a.artist_id = ar.artists_id " +
            "JOIN genres_albums ga ON ga.album_id = a.album_id WHERE ga.genre_id = ? " +
            "ORDER BY ar.name COLLATE NOCASE", arrayOf(genreId.toString()), ::toArtist)

    /** The set of artist ids that have at least one album in the given genre. Used by the
     *  Artists-browse genre filter ("an artist is in a genre if they have an album in it"). */
    fun artistIdsForGenre(genreId: Long): Set<Long> = query(
        "SELECT DISTINCT a.artist_id AS aid FROM albums a " +
            "JOIN genres_albums ga ON ga.album_id = a.album_id WHERE ga.genre_id = ?",
        arrayOf(genreId.toString())) { it.long("aid") }.toSet()

    /** The set of album ids in the given genre. Used by the Albums-browse genre filter. */
    fun albumIdsForGenre(genreId: Long): Set<Long> = query(
        "SELECT ga.album_id AS aid FROM genres_albums ga WHERE ga.genre_id = ?",
        arrayOf(genreId.toString())) { it.long("aid") }.toSet()

    /** Newest albums across one or more genres (Popular page's per-genre rows). */
    fun newReleases(genreIDs: List<Long>, limit: Int = 15): List<Album> {
        if (genreIDs.isEmpty()) return emptyList()
        val ph = genreIDs.joinToString(",") { "?" }
        val args = (genreIDs.map { it.toString() } + limit.toString()).toTypedArray()
        return query("SELECT DISTINCT a.* FROM albums a JOIN genres_albums ga ON ga.album_id = a.album_id " +
            "WHERE ga.genre_id IN ($ph) ORDER BY a.release_date DESC LIMIT ?", args, ::toAlbum)
    }

    // ---- Tags (collections) ----
    fun allTags() = query("SELECT * FROM collections ORDER BY name COLLATE NOCASE", map = ::toTag)
    fun albumsForTag(tagId: Long) = query(
        "SELECT a.* FROM albums a JOIN collections_albums ca ON ca.album_id = a.album_id " +
            "WHERE ca.collection_id = ? ORDER BY a.popularity DESC", arrayOf(tagId.toString()), ::toAlbum)

    /** The genres + tags shown as chips on an album. */
    fun genresAndTags(albumId: Long): Pair<List<Genre>, List<Tag>> {
        val genres = query("SELECT g.* FROM genres g JOIN genres_albums ga ON ga.genre_id = g.genre_id " +
            "WHERE ga.album_id = ? ORDER BY g.name COLLATE NOCASE", arrayOf(albumId.toString()), ::toGenre)
        val tags = query("SELECT c.* FROM collections c JOIN collections_albums ca ON ca.collection_id = c.collections_id " +
            "WHERE ca.album_id = ? ORDER BY c.name COLLATE NOCASE", arrayOf(albumId.toString()), ::toTag)
        return genres to tags
    }

    /** The genres + tags for an artist, aggregated across all of the artist's albums.
     *  Shown as chips on the artist page, the same way as on an album page. */
    fun genresAndTagsForArtist(artistId: Long): Pair<List<Genre>, List<Tag>> {
        val genres = query("SELECT DISTINCT g.* FROM genres g " +
            "JOIN genres_albums ga ON ga.genre_id = g.genre_id " +
            "JOIN albums a ON a.album_id = ga.album_id " +
            "WHERE a.artist_id = ? ORDER BY g.name COLLATE NOCASE", arrayOf(artistId.toString()), ::toGenre)
        val tags = query("SELECT DISTINCT c.* FROM collections c " +
            "JOIN collections_albums ca ON ca.collection_id = c.collections_id " +
            "JOIN albums a ON a.album_id = ca.album_id " +
            "WHERE a.artist_id = ? ORDER BY c.name COLLATE NOCASE", arrayOf(artistId.toString()), ::toTag)
        return genres to tags
    }

    // ---- Catalog playlists (Magnatune-curated) ----
    fun catalogPlaylists() =
        query("SELECT * FROM playlists ORDER BY sort_order, name COLLATE NOCASE", map = ::toCatalogPlaylist)
    fun songsForCatalogPlaylist(playlistId: Long) = query(
        "SELECT s.* FROM songs s JOIN playlist_songs ps ON ps.song_id = s.song_id " +
            "WHERE ps.playlist_id = ? ORDER BY ps.sort_order", arrayOf(playlistId.toString()), ::toSong)

    // ---- Search ----
    fun searchArtists(q: String, limit: Int = 50): List<Artist> {
        val like = "%$q%"
        return query("SELECT * FROM artists WHERE name LIKE ? OR description LIKE ? OR bio LIKE ? " +
            "ORDER BY name COLLATE NOCASE LIMIT ?", arrayOf(like, like, like, limit.toString()), ::toArtist)
    }
    fun searchAlbums(q: String, limit: Int = 50): List<Album> {
        val like = "%$q%"
        return query("SELECT * FROM albums WHERE name LIKE ? OR description LIKE ? " +
            "ORDER BY name COLLATE NOCASE LIMIT ?", arrayOf(like, like, limit.toString()), ::toAlbum)
    }
    fun searchSongs(q: String, limit: Int = 100): List<Song> {
        val like = "%$q%"
        return query("SELECT * FROM songs WHERE name LIKE ? ORDER BY name COLLATE NOCASE LIMIT ?",
            arrayOf(like, limit.toString()), ::toSong)
    }

    // ---- Downloaded-content lookups (offline filtering) ----
    private fun distinctIDs(ids: List<Long>, chunk: Int = 800, sql: (String) -> String): Set<Long> {
        if (ids.isEmpty()) return emptySet()
        val out = HashSet<Long>()
        var i = 0
        while (i < ids.size) {
            val slice = ids.subList(i, minOf(i + chunk, ids.size))
            val ph = slice.joinToString(",") { "?" }
            out.addAll(query(sql(ph), slice.map { it.toString() }.toTypedArray()) { it.getLong(0) })
            i += chunk
        }
        return out
    }

    fun albumAndArtistIDsForSongs(songIDs: List<Long>): Pair<Set<Long>, Set<Long>> {
        if (songIDs.isEmpty()) return emptySet<Long>() to emptySet()
        val albums = HashSet<Long>(); val artists = HashSet<Long>()
        var i = 0
        while (i < songIDs.size) {
            val slice = songIDs.subList(i, minOf(i + 800, songIDs.size))
            val ph = slice.joinToString(",") { "?" }
            query("SELECT DISTINCT s.album_id AS aid, a.artist_id AS arid FROM songs s " +
                "JOIN albums a ON a.album_id = s.album_id WHERE s.song_id IN ($ph)",
                slice.map { it.toString() }.toTypedArray()) {
                albums.add(it.long("aid")); artists.add(it.long("arid"))
            }
            i += 800
        }
        return albums to artists
    }

    fun genreIDsForAlbums(albumIDs: List<Long>) =
        distinctIDs(albumIDs) { "SELECT DISTINCT genre_id FROM genres_albums WHERE album_id IN ($it)" }
    /** Distinct genre ids represented by the given artists (via their albums). Used to build the
     *  search-aware genre picker options on the Artists browse screen. */
    fun genreIDsForArtists(artistIDs: List<Long>) =
        distinctIDs(artistIDs) {
            "SELECT DISTINCT ga.genre_id FROM genres_albums ga " +
                "JOIN albums a ON a.album_id = ga.album_id WHERE a.artist_id IN ($it)"
        }
    fun tagIDsForAlbums(albumIDs: List<Long>) =
        distinctIDs(albumIDs) { "SELECT DISTINCT collection_id FROM collections_albums WHERE album_id IN ($it)" }
    fun catalogPlaylistIDsForSongs(songIDs: List<Long>) =
        distinctIDs(songIDs) { "SELECT DISTINCT playlist_id FROM playlist_songs WHERE song_id IN ($it)" }

    // ---- Recommendations ("You might also like") ----
    // Guarded: an older catalog (pre-recommendations table) returns empty instead of throwing.

    fun recommendedAlbums(albumId: Long): List<Album> = runCatching {
        query("SELECT a.* FROM recommendations r JOIN albums a ON a.album_id = r.recommended_album_id " +
            "WHERE r.album_id = ? ORDER BY r.rank", arrayOf(albumId.toString()), ::toAlbum)
    }.getOrDefault(emptyList())

    /** Aggregate the per-album recs across all the artist's albums, tally the most-recommended
     *  OTHER artists (one vote per rec), drop the artist itself. */
    fun recommendedArtists(artistId: Long, limit: Int = 16): List<Artist> = runCatching {
        query(
            "SELECT ar.* FROM recommendations r " +
                "JOIN albums ra ON ra.album_id = r.recommended_album_id " +
                "JOIN artists ar ON ar.artists_id = ra.artist_id " +
                "WHERE r.album_id IN (SELECT album_id FROM albums WHERE artist_id = ?) " +
                "AND ra.artist_id <> ? " +
                "GROUP BY ar.artists_id ORDER BY COUNT(*) DESC, ar.name COLLATE NOCASE LIMIT ?",
            arrayOf(artistId.toString(), artistId.toString(), limit.toString()), ::toArtist)
    }.getOrDefault(emptyList())

    // ---- Playback context ----
    fun makePlayable(songs: List<Song>): List<PlayableTrack> {
        val albumCache = HashMap<Long, Album>()
        val artistCache = HashMap<Long, String>()
        val out = ArrayList<PlayableTrack>()
        for (s in songs) {
            val alb = albumCache[s.albumId] ?: album(s.albumId)?.also { albumCache[s.albumId] = it } ?: continue
            val artistName = artistCache[alb.artistId] ?: (artist(alb.artistId)?.name ?: "").also { artistCache[alb.artistId] = it }
            out.add(PlayableTrack(s, alb, artistName))
        }
        return out
    }
}
