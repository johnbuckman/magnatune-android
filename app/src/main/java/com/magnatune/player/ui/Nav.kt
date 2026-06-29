package com.magnatune.player.ui

/** Navigation routes. Top-level destinations + parameterized detail pages. */
object Routes {
    const val POPULAR = "popular"
    const val ARTISTS = "artists"
    const val ALBUMS = "albums"
    const val GENRES = "genres"
    const val TAGS = "tags"
    const val FEATURED = "featured"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val PLAYLISTS = "playlists"
    const val SETTINGS = "settings"
    const val HELP = "help"

    const val ARTIST = "artist/{id}"
    const val ALBUM = "album/{id}"
    const val GENRE = "genre/{id}"
    const val TAG = "tag/{id}"
    const val CATALOG_PLAYLIST = "cplaylist/{id}"
    const val USER_PLAYLIST = "uplaylist/{id}"

    fun artist(id: Long) = "artist/$id"
    fun album(id: Long) = "album/$id"
    fun genre(id: Long) = "genre/$id"
    fun tag(id: Long) = "tag/$id"
    fun catalogPlaylist(id: Long) = "cplaylist/$id"
    fun userPlaylist(id: Long) = "uplaylist/$id"
}

/** Top-level nav entries shown in the sidebar (tablet) / bottom bar (phone). */
enum class NavTab(val route: String, val title: String, val icon: String) {
    POPULAR(Routes.POPULAR, "Popular", "star"),
    ARTISTS(Routes.ARTISTS, "Artists", "person"),
    ALBUMS(Routes.ALBUMS, "Albums", "album"),
    GENRES(Routes.GENRES, "Genres", "category"),
    TAGS(Routes.TAGS, "Tags", "tag"),
    FEATURED(Routes.FEATURED, "Featured", "playlist"),
    SEARCH(Routes.SEARCH, "Search", "search"),
    FAVORITES(Routes.FAVORITES, "Favorites", "heart"),
    PLAYLISTS(Routes.PLAYLISTS, "Playlists", "queue"),
    SETTINGS(Routes.SETTINGS, "Settings", "settings"),
}
