package com.magnatune.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.magnatune.player.net.UrlBuilder
import com.magnatune.player.ui.theme.MagHairline

private val COVER_TIERS = intArrayOf(50, 75, 100, 150, 200, 300, 400, 600, 800, 1400)
private val ARTIST_TIERS = intArrayOf(50, 200, 420, 840)

/**
 * Full-screen, pinch-to-zoom + pan image viewer (album art / artist photos). Shows the already-
 * cached lower-res [placeholderUrl] instantly, then the hi-res [url] fades in over it. Tap (or
 * back) dismisses. Mirrors the iOS FullScreenImage. */
@Composable
fun FullScreenImage(url: String, placeholderUrl: String? = null, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        val state = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            // Pan only meaningful when zoomed in.
            if (scale > 1f) { offsetX += pan.x; offsetY += pan.y } else { offsetX = 0f; offsetY = 0f }
        }
        val imgMod = Modifier
            .fillMaxSize()
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .transformable(state)
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
            contentAlignment = Alignment.Center,
        ) {
            // Base layer: cached lower-res, shown instantly.
            if (placeholderUrl != null) {
                AsyncImage(model = placeholderUrl, contentDescription = null,
                    modifier = imgMod, contentScale = ContentScale.Fit)
            }
            // Top layer: hi-res, fades in over the cached one once downloaded.
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                contentDescription = null, modifier = imgMod, contentScale = ContentScale.Fit,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                FaIcon(Fa.xmark, "Close", tint = Color.White, size = 24.dp)
            }
        }
    }
}

/** Hi-res + cached-placeholder cover URLs for the full-screen viewer (iOS uses 1400 / 600). */
fun coverFullScreenUrls(artistName: String, albumName: String): Pair<String, String> =
    UrlBuilder.coverUrl(artistName, albumName, 1400) to UrlBuilder.coverUrl(artistName, albumName, 600)

/** Hi-res + cached-placeholder artist-photo URLs (sized in an album dir; falls back to original). */
fun artistFullScreenUrls(artistName: String, albumName: String?, originalPhoto: String?): Pair<String, String?> {
    val hi = if (albumName != null) UrlBuilder.artistPhotoUrl(artistName, albumName, 840)
        else UrlBuilder.artistPhotoOriginal(originalPhoto) ?: ""
    val lo = albumName?.let { UrlBuilder.artistPhotoUrl(artistName, it, 200) }
    return hi to lo
}

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
    onClick: (() -> Unit)? = null,
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
            .border(0.5.dp, MagHairline, RoundedCornerShape(6.dp))
            .let { if (onClick != null) it.pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) } else it },
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
    onClick: (() -> Unit)? = null,
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
            .border(0.5.dp, MagHairline, CircleShape)
            .let { if (onClick != null) it.pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) } else it },
    )
}
