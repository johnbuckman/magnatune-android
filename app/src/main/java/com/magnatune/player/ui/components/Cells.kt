package com.magnatune.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.magnatune.player.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magnatune.player.model.Album
import com.magnatune.player.model.Artist
import com.magnatune.player.model.Song
import com.magnatune.player.ui.MagnatuneViewModel
import com.magnatune.player.ui.theme.MagAccent
import com.magnatune.player.ui.theme.MagCard
import com.magnatune.player.ui.theme.MagSecondary

/** Heart + broken-heart reaction buttons for a song/album/artist (mirrors iOS FavoriteButton). */
@Composable
fun FavoriteButton(vm: MagnatuneViewModel, kind: String, id: Long, compact: Boolean = false) {
    val favSongs by vm.userStore.favoriteSongIds.collectAsStateWithLifecycle()
    val favAlbums by vm.userStore.favoriteAlbumIds.collectAsStateWithLifecycle()
    val favArtists by vm.userStore.favoriteArtistIds.collectAsStateWithLifecycle()
    val disSongs by vm.userStore.dislikedSongIds.collectAsStateWithLifecycle()
    val disAlbums by vm.userStore.dislikedAlbumIds.collectAsStateWithLifecycle()
    val disArtists by vm.userStore.dislikedArtistIds.collectAsStateWithLifecycle()

    val isFav = when (kind) { "song" -> favSongs; "album" -> favAlbums; else -> favArtists }.contains(id)
    val isDis = when (kind) { "song" -> disSongs; "album" -> disAlbums; else -> disArtists }.contains(id)

    val sz = if (compact) 32.dp else 40.dp
    Row {
        IconButton(onClick = { vm.toggleFavorite(kind, id) }, modifier = Modifier.size(sz)) {
            Icon(
                painterResource(if (isFav) R.drawable.ic_heart_solid else R.drawable.ic_heart_light),
                contentDescription = "Favorite", tint = if (isFav) MagAccent else MagSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = { vm.toggleDislike(kind, id) }, modifier = Modifier.size(sz)) {
            Icon(
                painterResource(if (isDis) R.drawable.ic_dislike_solid else R.drawable.ic_dislike_light),
                contentDescription = "Dislike", tint = if (isDis) MagAccent else MagSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Standalone broken-heart dislike button (e.g. on a Genres row). Reuses the generic
 *  dislikes table via kind; "genre" needs no migration. */
@Composable
fun DislikeButton(vm: MagnatuneViewModel, kind: String, id: Long, compact: Boolean = false) {
    val disSongs by vm.userStore.dislikedSongIds.collectAsStateWithLifecycle()
    val disAlbums by vm.userStore.dislikedAlbumIds.collectAsStateWithLifecycle()
    val disArtists by vm.userStore.dislikedArtistIds.collectAsStateWithLifecycle()
    val disGenres by vm.userStore.dislikedGenreIds.collectAsStateWithLifecycle()
    val isDis = when (kind) {
        "song" -> disSongs; "album" -> disAlbums; "artist" -> disArtists; else -> disGenres
    }.contains(id)
    val sz = if (compact) 32.dp else 40.dp
    IconButton(onClick = { vm.toggleDislike(kind, id) }, modifier = Modifier.size(sz)) {
        Icon(
            painterResource(if (isDis) R.drawable.ic_dislike_solid else R.drawable.ic_dislike_light),
            contentDescription = "Dislike", tint = if (isDis) MagAccent else MagSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Album grid cell: cover + album name + artist name. */
@Composable
fun AlbumCell(album: Album, artistName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clickable(onClick = onClick).padding(6.dp)) {
        CoverImage(
            artistName = artistName, albumName = album.name, points = 160.dp,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Spacer(Modifier.size(6.dp))
        Text(album.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(artistName, style = MaterialTheme.typography.bodySmall, color = MagSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Artist row: circular photo + name. */
@Composable
fun ArtistRow(artist: Artist, albumName: String?, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistPhoto(artist.name, albumName, artist.photo, points = 48.dp)
        Spacer(Modifier.width(12.dp))
        Text(artist.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

/** Song row: track no / artwork, name + optional subtitle, duration, trailing controls.
 *
 * Search-nav mode: pass [onAlbumClick] (and optionally [onArtistClick]). Then tapping the
 * artwork / song name / album chip navigates to the album (with this song highlighted) and
 * the artist chip navigates to the artist — instead of the whole row playing. [isHighlighted]
 * briefly flashes the row (the song just deep-linked from search). */
@Composable
fun SongRow(
    song: Song,
    artistName: String? = null,
    albumName: String? = null,
    showArtwork: Boolean = false,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isHighlighted: Boolean = false,
    onAlbumClick: (() -> Unit)? = null,
    onArtistClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val navMode = onAlbumClick != null
    val rowBg = when {
        isCurrent -> MagAccent.copy(alpha = 0.12f)
        isHighlighted -> MagAccent.copy(alpha = 0.28f)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(rowBg)
            .let { if (navMode) it else it.clickable(onClick = onClick) }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val leadMod = if (navMode) Modifier.clickable(onClick = onAlbumClick!!) else Modifier
        if (isCurrent) {
            Box(Modifier.size(if (showArtwork) 40.dp else 26.dp), contentAlignment = Alignment.Center) {
                FaIcon(if (isPlaying) Fa.volumeHigh else Fa.volumeLow, "Now playing", tint = MagAccent, size = 20.dp)
            }
        } else if (showArtwork && artistName != null && albumName != null) {
            CoverImage(artistName, albumName, points = 40.dp, modifier = Modifier.size(40.dp).then(leadMod))
        } else {
            Box(Modifier.size(if (showArtwork) 40.dp else 26.dp), contentAlignment = Alignment.Center) {
                Text(song.trackNo?.toString() ?: "•", style = MaterialTheme.typography.bodyMedium, color = MagSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) MagAccent else MaterialTheme.colorScheme.onBackground,
                modifier = if (navMode) Modifier.clickable(onClick = onAlbumClick!!) else Modifier)
            if (navMode) {
                // Tappable chips under the song name: artist → artist, album → album+highlight.
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (artistName != null && onArtistClick != null) SubChip(artistName, Fa.user, onArtistClick)
                    if (albumName != null) SubChip(albumName, Fa.compactDisc, onAlbumClick!!)
                }
            } else if (artistName != null) {
                Text(artistName, style = MaterialTheme.typography.bodySmall, color = MagSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(song.durationText, style = MaterialTheme.typography.bodySmall, color = MagSecondary)
        trailing()
    }
}

/** Small icon+label pill used for the artist / album links under a search song row. */
@Composable
private fun SubChip(text: String, glyph: String, onClick: () -> Unit) {
    Surface(
        color = MagCard, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FaIcon(glyph, null, tint = MagSecondary, size = 11.dp)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MagSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Album list row (cover + album name + artist subtitle) used in search/list contexts. */
@Composable
fun AlbumListRow(album: Album, artistName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(artistName, album.name, points = 44.dp, modifier = Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(album.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artistName, style = MaterialTheme.typography.bodySmall, color = MagSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Small rounded chip used for genre/tag links. */
@Composable
fun Chip(text: String, onClick: () -> Unit) {
    Surface(
        color = MagCard, shape = RoundedCornerShape(14.dp),
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MagAccent,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/** Collapsible bio/description text with a Show more / Show less toggle (mirrors iOS ExpandableText). */
@Composable
fun ExpandableText(text: String, collapsedLines: Int = 4, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(
            text, style = MaterialTheme.typography.bodyMedium, color = MagSecondary,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) hasOverflow = it.hasVisualOverflow },
        )
        if (hasOverflow || expanded) {
            Text(
                if (expanded) "Show less" else "Show more",
                style = MaterialTheme.typography.labelMedium, color = MagAccent,
                modifier = Modifier.clickable { expanded = !expanded }.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}
