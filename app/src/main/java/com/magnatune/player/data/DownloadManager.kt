package com.magnatune.player.data

import android.content.Context
import com.magnatune.player.db.CatalogStore
import com.magnatune.player.db.UserStore
import com.magnatune.player.model.PlayableTrack
import com.magnatune.player.net.StreamQuality
import com.magnatune.player.net.UrlBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Auto-downloads favorited tracks (members only) for offline playback, mirroring iOS DownloadStore +
 * AppModel.syncAutoDownloads. Files live in filesDir/downloads/<songId>.m4a; the path is recorded in
 * the Room user DB so [com.magnatune.player.service.PlaybackController] plays the local copy.
 */
class DownloadManager(
    private val context: Context,
    private val credentials: Credentials,
    private val settings: Settings,
    private val userStore: UserStore,
    private val catalog: () -> CatalogStore,
) {
    private val dir = File(context.filesDir, "downloads").apply { mkdirs() }

    val downloading = MutableStateFlow(false)
    val storageBytes = MutableStateFlow(0L)

    private fun fileFor(songId: Long) = File(dir, "$songId.m4a")

    suspend fun refreshStorage() = withContext(Dispatchers.IO) {
        storageBytes.value = dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /** Expand favorites → song ids (fav songs + all songs of fav albums + fav artists). */
    private fun favoriteSongIds(): Set<Long> {
        val c = catalog()
        val out = HashSet<Long>()
        out.addAll(userStore.favoriteSongIds.value)
        userStore.favoriteAlbumIds.value.forEach { albumId -> c.songsForAlbum(albumId).forEach { out.add(it.id) } }
        userStore.favoriteArtistIds.value.forEach { artistId -> c.songsForArtist(artistId).forEach { out.add(it.id) } }
        return out
    }

    /** Members-only. Downloads any favorited track not yet on disk. Non-members → clear everything.
     *  [respectToggle]=false forces a download even when the auto-download setting is off
     *  (used by the manual "Download favorites now" button). */
    suspend fun syncAutoDownloads(respectToggle: Boolean = true) = withContext(Dispatchers.IO) {
        if (!credentials.isMember.value) { clearAll(); return@withContext }
        if (respectToggle && !settings.autoDownloadFavorites.value) return@withContext
        downloading.value = true
        try {
            val wanted = favoriteSongIds()
            val have = userStore.allDownloads().associateBy { it.songId }
            val missing = wanted.filter { it !in have || !File(have[it]!!.localPath).exists() }
            val tracks: List<PlayableTrack> = catalog().makePlayable(missing.mapNotNull { catalog().song(it) })
            for (t in tracks) {
                if (downloadOne(t)) userStore.saveDownload(t.id, fileFor(t.id).path)
            }
            refreshStorage()
        } finally {
            downloading.value = false
        }
    }

    private fun downloadOne(t: PlayableTrack): Boolean = runCatching {
        val url = UrlBuilder.streamUrl(t.artistName, t.album.name, t.song, isMember = true, quality = StreamQuality.NORMAL)
        val target = fileFor(t.id)
        val tmp = File(dir, "${t.id}.tmp")
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 20000; readTimeout = 60000
            if (responseCode !in 200..299) { disconnect(); return false }
            inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            disconnect()
        }
        if (tmp.length() < 1000) { tmp.delete(); return false }
        tmp.renameTo(target)
    }.getOrDefault(false)

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        userStore.clearDownloads()
        storageBytes.value = 0L
    }
}
