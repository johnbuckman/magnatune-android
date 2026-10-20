package com.magnatune.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.magnatune.player.data.DownloadState
import com.magnatune.player.data.DownloadStatus
import com.magnatune.player.ui.theme.MagSecondary

/** Favorites auto-download status, provided once by RootScreen so every [SongRow] can show it. */
val LocalDownloadStatus = compositionLocalOf { DownloadStatus() }
/** Put failed downloads back in the queue (provided alongside [LocalDownloadStatus]). */
val LocalRetryDownloads = staticCompositionLocalOf<() -> Unit> { {} }

/** Offline-download status of one favorited song (mirrors iOS DownloadStatusIcon): downloaded,
 *  downloading, queued (paused while offline), or failed (tap to retry). Nothing for other songs. */
@Composable
fun DownloadStatusIcon(songId: Long) {
    val status = LocalDownloadStatus.current
    val retry = LocalRetryDownloads.current
    when (status.state(songId)) {
        DownloadState.DOWNLOADED ->
            Icon(Icons.Filled.DownloadForOffline, "Downloaded", tint = MagSecondary, modifier = Modifier.padding(start = 6.dp).size(16.dp))
        DownloadState.DOWNLOADING ->
            CircularProgressIndicator(strokeWidth = 2.dp, color = MagSecondary, modifier = Modifier.padding(start = 6.dp).size(14.dp))
        DownloadState.QUEUED ->
            Icon(if (status.pausedOffline) Icons.Outlined.PauseCircle else Icons.Outlined.Schedule,
                if (status.pausedOffline) "Waiting for network" else "Waiting to download",
                tint = MagSecondary, modifier = Modifier.padding(start = 6.dp).size(16.dp))
        DownloadState.FAILED ->
            IconButton(onClick = retry, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Warning, "Download failed — tap to retry", tint = Color(0xFFE08A00), modifier = Modifier.size(16.dp))
            }
        null -> {}
    }
}

/** Album/artist roll-up ("12 of 14 songs downloaded"); nothing when no favorite keeps these songs. */
@Composable
fun DownloadSummaryText(songIds: List<Long>, modifier: Modifier = Modifier) {
    val (done, total, failed) = LocalDownloadStatus.current.summary(songIds) ?: return
    val text = when {
        done == total && total == 1 -> "Downloaded"
        done == total -> "All $total songs downloaded"
        else -> "$done of $total songs downloaded" + if (failed > 0) " · $failed failed" else ""
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Filled.DownloadForOffline, null, tint = MagSecondary, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MagSecondary)
    }
}

/** Slim strip shown above the content while offline (mirrors iOS OfflineBanner). */
@Composable
fun OfflineBanner(downloadedCount: Int) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 6.dp),
        contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.WifiOff, null, tint = MagSecondary, modifier = Modifier.size(14.dp))
            Text(if (downloadedCount == 0) "Offline — nothing downloaded yet"
                 else "Offline — showing your $downloadedCount downloaded song${if (downloadedCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall, color = MagSecondary)
        }
    }
}
