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
    }) { Icon(Icons.Filled.Download, "Download album", tint = MagSecondary) }
}

/** Member-only single-song download — opens the open he3 per-track file in the browser. */
@Composable
fun SongDownloadButton(vm: MagnatuneViewModel, track: PlayableTrack) {
    val isMember by vm.credentials.isMember.collectAsStateWithLifecycle()
    if (!isMember) return
    val context = LocalContext.current
    IconButton(onClick = {
        val url = UrlBuilder.songDownloadUrl(track.artistName, track.album.name, track.song, vm.settings.songDownloadFormat)
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Download, "Download song", tint = MagSecondary) }
}
