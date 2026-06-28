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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HeartBroken
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
import androidx.compose.ui.text.font.FontWeight
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

    Row {
        IconButton(onClick = { vm.toggleFavorite(kind, id) }, modifier = Modifier.size(if (compact) 32.dp else 40.dp)) {
            Icon(
                if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFav) MagAccent else MagSecondary,
            )
        }
        IconButton(onClick = { vm.toggleDislike(kind, id) }, modifier = Modifier.size(if (compact) 32.dp else 40.dp)) {
            Icon(
                if (isDis) Icons.Filled.HeartBroken else Icons.Outlined.HeartBroken,
                contentDescription = "Dislike",
                tint = if (isDis) MagAccent else MagSecondary,
            )
        }
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

/** Song row: track no / artwork, name + optional subtitle, duration, trailing controls. */
@Composable
fun SongRow(
    song: Song,
    artistName: String? = null,
    albumName: String? = null,
    showArtwork: Boolean = false,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArtwork && artistName != null && albumName != null) {
            CoverImage(artistName, albumName, points = 40.dp, modifier = Modifier.size(40.dp))
        } else {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Text(song.trackNo?.toString() ?: "•", style = MaterialTheme.typography.bodyMedium, color = MagSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (artistName != null) {
                Text(artistName, style = MaterialTheme.typography.bodySmall, color = MagSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(song.durationText, style = MaterialTheme.typography.bodySmall, color = MagSecondary)
        trailing()
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
