package com.magnatune.player.model

/** Catalog records (read-only, from the downloaded Magnatune SQLite db). Mirror the iOS models. */

data class Artist(
    val id: Long,
    val name: String,
    val description: String? = null,
    val homepage: String? = null,
    val bio: String? = null,
    val photo: String? = null,
    val society: String? = null,
)

data class Album(
    val id: Long,
    val artistId: Long,
    val name: String,
    val description: String? = null,
    val sku: String,
    val releaseDate: Long? = null,
    val popularity: Int? = null,
    val itunesBuyUrl: String? = null,
)

data class Song(
    val id: Long,
    val albumId: Long,
    val name: String,
    val trackNo: Int? = null,
    val duration: Int? = null,
    val mp3: String,
) {
    val durationText: String
        get() = duration?.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60, it % 60) } ?: "--:--"
}

data class Genre(val id: Long, val name: String)

/** A "tag" in the UI = the website's "Tagged as:" values, stored in the collections table. */
data class Tag(val id: Long, val name: String)

/** A Magnatune-curated playlist from the catalog (distinct from the user's own playlists). */
data class CatalogPlaylist(
    val id: Long,
    val name: String,
    val sortOrder: Int? = null,
    val ownerId: Long? = null,
)

/** A song joined with the context needed to build URLs and Now Playing info. */
data class PlayableTrack(
    val song: Song,
    val album: Album,
    val artistName: String,
) {
    val id: Long get() = song.id
}
