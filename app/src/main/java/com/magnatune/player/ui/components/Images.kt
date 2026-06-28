package com.magnatune.player.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.magnatune.player.net.UrlBuilder
import com.magnatune.player.ui.theme.MagHairline

private val COVER_TIERS = intArrayOf(50, 75, 100, 150, 200, 300, 400, 600, 800, 1400)
private val ARTIST_TIERS = intArrayOf(50, 200, 420, 840)

/** Steps through a list of candidate URLs, advancing to the next on load error. */
@Composable
fun FallbackAsyncImage(
    urls: List<String>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var idx by remember(urls) { mutableIntStateOf(0) }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(urls.getOrNull(idx))
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onError = { if (idx < urls.lastIndex) idx++ },
    )
}

/** Album cover sized to [points] dp; picks the smallest tier ≥ points×density, then larger tiers
 *  as fallbacks. Hairline-bordered, rounded corners (mirrors the iOS CoverImage). */
@Composable
fun CoverImage(
    artistName: String,
    albumName: String,
    points: Dp,
    modifier: Modifier = Modifier,
    cap: Int = 600,
) {
    val density = LocalDensity.current.density
    val target = (points.value * density).toInt()
    val chosen = COVER_TIERS.firstOrNull { it >= target && it <= cap } ?: cap
    val chain = COVER_TIERS.filter { it in chosen..maxOf(cap, chosen) }
        .map { UrlBuilder.coverUrl(artistName, albumName, it) }
    FallbackAsyncImage(
        urls = chain,
        contentDescription = albumName,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(0.5.dp, MagHairline, RoundedCornerShape(6.dp)),
    )
}

/** Circular artist photo. Resolves a representative album dir for the sized thumbnail; falls back
 *  to larger tiers then the full-res original. */
@Composable
fun ArtistPhoto(
    artistName: String,
    albumName: String?,
    originalPhoto: String?,
    points: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val target = (points.value * density).toInt()
    val chosen = ARTIST_TIERS.firstOrNull { it >= target } ?: 840
    val sized = if (albumName != null)
        ARTIST_TIERS.filter { it >= chosen }.map { UrlBuilder.artistPhotoUrl(artistName, albumName, it) }
    else emptyList()
    val chain = sized + listOfNotNull(UrlBuilder.artistPhotoOriginal(originalPhoto))
    FallbackAsyncImage(
        urls = chain,
        contentDescription = artistName,
        modifier = modifier
            .size(points)
            .clip(CircleShape)
            .border(0.5.dp, MagHairline, CircleShape),
    )
}
