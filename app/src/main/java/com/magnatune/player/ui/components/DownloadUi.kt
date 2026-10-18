package com.magnatune.player.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magnatune.player.model.PlayableTrack
import com.magnatune.player.net.UrlBuilder
import com.magnatune.player.ui.MagnatuneViewModel
import com.magnatune.player.ui.theme.MagSecondary

/** Member-only album download — opens the membership download endpoint in the browser, which
 *  handles the member login (mirrors iOS). Hidden for non-members. */
@Composable
fun AlbumDownloadButton(vm: MagnatuneViewModel, sku: String) {
    val isMember by vm.credentials.isMember.collectAsStateWithLifecycle()
    if (!isMember) return
    val context = LocalContext.current
    IconButton(onClick = {
        val url = UrlBuilder.albumMembershipDownloadUrl(sku, vm.settings.albumDownloadFormat.key)
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }) {
        Icon(androidx.compose.ui.res.painterResource(com.magnatune.player.R.drawable.ic_download),
            "Download album", tint = MagSecondary, modifier = Modifier.size(20.dp))
    }
}

/** Non-member "Buy album — $8" — opens the PayPal album checkout on magnatune.com in the browser
 *  (100% goes to the musician). Hidden for members, who get [AlbumDownloadButton] instead. */
@Composable
fun AlbumBuyButton(vm: MagnatuneViewModel, sku: String) {
    val isMember by vm.credentials.isMember.collectAsStateWithLifecycle()
    if (isMember) return
    val context = LocalContext.current
    androidx.compose.material3.OutlinedButton(
        onClick = {
            val url = "https://magnatune.com/buy/album?sku=" + java.net.URLEncoder.encode(sku, "UTF-8")
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.semantics { contentDescription = "Buy this album for $8; 100% goes to the musician" },
    ) { androidx.compose.material3.Text("Buy album — $8", maxLines = 1) }
}

/** Member-only single-song download — opens the per-track file on magnatune.com in the browser. */
@Composable
fun SongDownloadButton(vm: MagnatuneViewModel, track: PlayableTrack) {
    val isMember by vm.credentials.isMember.collectAsStateWithLifecycle()
    if (!isMember) return
    val context = LocalContext.current
    IconButton(onClick = {
        val url = UrlBuilder.songDownloadUrl(track.artistName, track.album.name, track.song, vm.settings.songDownloadFormat)
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }, modifier = Modifier.size(32.dp)) {
        Icon(androidx.compose.ui.res.painterResource(com.magnatune.player.R.drawable.ic_download),
            "Download song", tint = MagSecondary, modifier = Modifier.size(18.dp))
    }
}
