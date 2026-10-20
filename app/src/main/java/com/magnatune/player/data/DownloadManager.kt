package com.magnatune.player.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.magnatune.player.MagnatuneApp
import com.magnatune.player.db.CatalogStore
import com.magnatune.player.db.UserStore
import com.magnatune.player.model.PlayableTrack
import com.magnatune.player.net.StreamQuality
import com.magnatune.player.net.UrlBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Per-song state of the favorites auto-download queue, for the UI (mirrors iOS DownloadState). */
enum class DownloadState { DOWNLOADED, DOWNLOADING, QUEUED, FAILED }

/** Snapshot of the favorites auto-download queue (mirrors iOS DownloadProgress). */
data class DownloadStatus(
    /** Every song the favorites imply, in favorites order (songs, then albums, then artists). */
    val targetIds: List<Long> = emptyList(),
    val downloaded: Set<Long> = emptySet(),
    val queued: Set<Long> = emptySet(),
    val failed: Set<Long> = emptySet(),
    val downloading: Set<Long> = emptySet(),
    /** Songs are waiting but there's no network to fetch them. */
    val pausedOffline: Boolean = false,
) {
    fun state(songId: Long): DownloadState? = when (songId) {
        in downloading -> DownloadState.DOWNLOADING
        in downloaded -> DownloadState.DOWNLOADED
        in failed -> DownloadState.FAILED
        in queued -> DownloadState.QUEUED
        else -> null
    }
    val pendingCount get() = queued.size + failed.size + downloading.size
    val doneCount get() = targetIds.count { it in downloaded }

    /** Roll-up for an album/artist: (done, total, failed) over the songs the favorites keep. */
    fun summary(songIds: List<Long>): Triple<Int, Int, Int>? {
        var done = 0; var total = 0; var bad = 0
        for (id in songIds) {
            val s = state(id) ?: continue
            total++
            if (s == DownloadState.DOWNLOADED) done++ else if (s == DownloadState.FAILED) bad++
        }
        return if (total == 0) null else Triple(done, total, bad)
    }
}

/**
 * Auto-downloads favorited tracks (members only) for offline playback, mirroring iOS DownloadStore +
 * AppModel.syncAutoDownloads. Files live in filesDir/downloads/<songId>.opus (Opus, like iOS); the
 * path is recorded in the Room user DB so [com.magnatune.player.service.PlaybackController] plays the
 * local copy. Transfers run in [DownloadWorker] under WorkManager with a network constraint, so they
 * survive the app being backgrounded and pause/resume across airplane mode on their own.
 */
class DownloadManager(
    private val context: Context,
    private val credentials: Credentials,
    private val settings: Settings,
    private val userStore: UserStore,
    private val catalog: () -> CatalogStore,
    private val isOnline: StateFlow<Boolean>,
) {
    private val dir = File(context.filesDir, "downloads").apply { mkdirs() }

    private val _status = MutableStateFlow(DownloadStatus())
    val status: StateFlow<DownloadStatus> = _status
    val storageBytes = MutableStateFlow(0L)

    private fun fileFor(songId: Long) = File(dir, "$songId.opus")

    suspend fun refreshStorage() = withContext(Dispatchers.IO) {
        storageBytes.value = dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /** Local file for a downloaded song, or null (also guards against a DB row whose file is gone). */
    suspend fun localPath(songId: Long): String? =
        userStore.downloadPath(songId)?.takeIf { File(it).length() > 0 }

    /** Expand favorites → song ids, in favorites order (fav songs, then fav albums, then fav artists). */
    private fun favoriteSongIds(): List<Long> {
        val c = catalog()
        val out = LinkedHashSet<Long>()
        out.addAll(userStore.favoriteSongIds.value)
        userStore.favoriteAlbumIds.value.forEach { albumId -> c.songsForAlbum(albumId).forEach { out.add(it.id) } }
        userStore.favoriteArtistIds.value.forEach { artistId -> c.songsForArtist(artistId).forEach { out.add(it.id) } }
        return out.toList()
    }

    /** Song ids with a complete file on disk. Purges legacy AAC `.m4a` downloads (re-fetched as Opus). */
    private suspend fun onDisk(): Set<Long> {
        val rows = userStore.allDownloads()
        val have = HashSet<Long>()
        for (r in rows) {
            val f = File(r.localPath)
            if (f.extension == "opus" && f.length() > 0) have.add(r.songId)
            else { f.delete(); userStore.deleteDownload(r.songId) }
        }
        return have
    }

    /** Members-only. Reconciles downloads with the favorites: deletes files no favorite keeps, then
     *  schedules the worker to fetch the rest. Non-members → clear everything.
     *  [respectToggle]=false forces a download even when the auto-download setting is off
     *  (used by the manual "Download favorites now" button). */
    suspend fun syncAutoDownloads(respectToggle: Boolean = true) = withContext(Dispatchers.IO) {
        if (!credentials.isMember.value) { clearAll(); return@withContext }
        if (respectToggle && !settings.autoDownloadFavorites.value) {
            WorkManager.getInstance(context).cancelUniqueWork(DownloadWorker.NAME)
            _status.value = DownloadStatus()
            return@withContext
        }
        val wanted = favoriteSongIds()
        val wantedSet = wanted.toSet()
        var have = onDisk()
        for (id in have - wantedSet) { fileFor(id).delete(); userStore.deleteDownload(id) }
        have = have intersect wantedSet
        _status.update { s ->
            val failed = s.failed intersect wantedSet
            val downloading = s.downloading intersect wantedSet - have
            s.copy(targetIds = wanted, downloaded = have, failed = failed, downloading = downloading,
                queued = wantedSet - have - failed - downloading)
        }
        refreshStorage()
        updatePaused()
        if (_status.value.queued.isNotEmpty()) schedule()
    }

    /** Put failed songs back in the queue and fetch again. */
    suspend fun retryFailed() {
        _status.update { it.copy(queued = it.queued + it.failed, failed = emptySet()) }
        schedule()
    }

    fun updatePaused() {
        _status.update { it.copy(pausedOffline = !isOnline.value && it.pendingCount > 0) }
    }

    private fun schedule() {
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(DownloadWorker.NAME, ExistingWorkPolicy.KEEP, req)
    }

    /** Worker body: download every queued song until done or stopped (lost network, system stop).
     *  Returns true when the queue drained, false if it should run again later. */
    suspend fun runQueue(isStopped: () -> Boolean): Boolean = withContext(Dispatchers.IO) {
        while (!isStopped()) {
            val s = _status.value
            val next = s.targetIds.firstOrNull { it in s.queued } ?: return@withContext true
            val track = catalog().song(next)?.let { catalog().makePlayable(listOf(it)).firstOrNull() }
            if (track == null) { _status.update { it.copy(queued = it.queued - next) }; continue }
            _status.update { it.copy(queued = it.queued - next, downloading = it.downloading + next) }
            val ok = downloadOne(track)
            if (ok) userStore.saveDownload(next, fileFor(next).path)
            _status.update {
                when {
                    ok -> it.copy(downloading = it.downloading - next, downloaded = it.downloaded + next)
                    // Interrupted by losing the network (or a system stop): back in the queue, not "failed".
                    isStopped() || !isOnline.value -> it.copy(downloading = it.downloading - next, queued = it.queued + next)
                    else -> it.copy(downloading = it.downloading - next, failed = it.failed + next)
                }
            }
            if (ok) refreshStorage()
        }
        false
    }

    /** Opus at the chosen tier: High → `_hi.opus` (192k), Normal → `.opus` (96k). */
    private fun downloadUrl(t: PlayableTrack): String =
        UrlBuilder.opusDownloadUrl(t.artistName, t.album.name, t.song, high = settings.streamQuality == StreamQuality.HIGH)

    private fun downloadOne(t: PlayableTrack): Boolean = runCatching {
        val target = fileFor(t.id)
        val tmp = File(dir, "${t.id}.tmp")
        (URL(downloadUrl(t)).openConnection() as HttpURLConnection).run {
            connectTimeout = 20000; readTimeout = 60000
            // Member (no-announcement) files are Basic-auth gated on magnatune.com now (he3 was open).
            credentials.basicAuthHeader()?.let { setRequestProperty("Authorization", it) }
            if (responseCode !in 200..299) { disconnect(); return false }
            inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            disconnect()
        }
        if (tmp.length() < 1000) { tmp.delete(); return false }
        tmp.renameTo(target)
    }.getOrDefault(false)

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelUniqueWork(DownloadWorker.NAME)
        dir.listFiles()?.forEach { it.delete() }
        userStore.clearDownloads()
        storageBytes.value = 0L
        _status.value = DownloadStatus()
    }
}

/** Runs [DownloadManager.runQueue] under WorkManager (network-constrained, survives backgrounding). */
class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val dm = (applicationContext as MagnatuneApp).container.downloads
        // Cold start by WorkManager (no UI yet): rebuild the queue from the favorites first.
        if (dm.status.value.targetIds.isEmpty()) dm.syncAutoDownloads()
        return if (dm.runQueue { isStopped }) Result.success() else Result.retry()
    }

    companion object { const val NAME = "favorite-downloads" }
}
