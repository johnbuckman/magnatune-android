package com.magnatune.player.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.magnatune.player.data.Credentials
import com.magnatune.player.data.Settings
import com.magnatune.player.db.UserStore
import com.magnatune.player.model.PlayableTrack
import com.magnatune.player.net.UrlBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App-side wrapper around a Media3 [MediaController] connected to [PlaybackService]. Resolves the
 * correct stream URL per track (member/non-member + quality, with lossless→normal fallback),
 * builds MediaItems with Now-Playing metadata, and exposes playback state as StateFlows for the UI.
 *
 * NOTE: crossfade + next-track prefetch (iOS dual-AVPlayer behaviour) are NOT yet implemented —
 * a single ExoPlayer gives queue/gapless/background/lock-screen. Crossfade needs a custom
 * dual-player Media3 Player and is tracked as a follow-up.
 */
class PlaybackController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val credentials: Credentials,
    private val settings: Settings,
    private val userStore: UserStore,
    private val downloadPath: suspend (Long) -> String? = { null },
) {
    private var controller: MediaController? = null
    private var queueTracks: List<PlayableTrack> = emptyList()

    val currentTrack = MutableStateFlow<PlayableTrack?>(null)
    val isPlaying = MutableStateFlow(false)
    val positionMs = MutableStateFlow(0L)
    val durationMs = MutableStateFlow(0L)

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) { isPlaying.value = playing }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = syncCurrent()
        override fun onPlaybackStateChanged(state: Int) = syncCurrent()
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                controller = future.get().also { it.addListener(listener) }
                syncCurrent()
            } catch (e: Exception) {
                android.util.Log.e("Magnatune", "MediaController connect failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
        // Position ticker.
        scope.launch {
            while (true) {
                delay(500)
                withContext(Dispatchers.Main) {
                    controller?.let {
                        positionMs.value = it.currentPosition.coerceAtLeast(0)
                        durationMs.value = it.duration.coerceAtLeast(0)
                    }
                }
            }
        }
    }

    private fun syncCurrent() {
        val c = controller ?: return
        val idx = c.currentMediaItemIndex
        val track = queueTracks.getOrNull(idx)
        if (track != null && track.id != currentTrack.value?.id) {
            scope.launch { userStore.recordPlay(track.id) }
        }
        currentTrack.value = track
        isPlaying.value = c.isPlaying
    }

    /** Resolve a stream URL + build a MediaItem with Now-Playing metadata for one track. */
    private suspend fun buildItem(t: PlayableTrack): MediaItem {
        val local = downloadPath(t.id)
        val uri = local ?: UrlBuilder.resolvedStreamUrl(
            t.artistName, t.album.name, t.song,
            credentials.isMember.value, settings.streamQuality,
        )
        val art = UrlBuilder.coverUrl(t.artistName, t.album.name, 300)
        val meta = MediaMetadata.Builder()
            .setTitle(t.song.name).setArtist(t.artistName).setAlbumTitle(t.album.name)
            .setArtworkUri(Uri.parse(art))
            .build()
        return MediaItem.Builder()
            .setMediaId(t.id.toString()).setUri(uri).setMediaMetadata(meta).build()
    }

    /** Play a list of tracks starting at [startAt]. Honors the persistent shuffle mode (chosen
     *  track first, the rest randomized — matching iOS). */
    fun play(tracks: List<PlayableTrack>, startAt: Int) {
        if (tracks.isEmpty()) return
        scope.launch {
            var ordered = tracks
            var start = startAt.coerceIn(0, tracks.lastIndex)
            if (settings.shuffleEnabled.value) {
                val chosen = ordered[start]
                ordered = (ordered.toMutableList().also { it.removeAt(start) }.shuffled())
                    .let { listOf(chosen) + it }
                start = 0
            }
            val items = ordered.map { buildItem(it) }
            withContext(Dispatchers.Main) {
                queueTracks = ordered
                val c = controller ?: return@withContext
                c.setMediaItems(items, start, 0L)
                c.prepare()
                c.play()
                syncCurrent()
            }
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun setVolume(v: Float) { controller?.volume = v.coerceIn(0f, 1f) }
}
