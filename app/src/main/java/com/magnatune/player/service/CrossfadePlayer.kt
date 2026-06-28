package com.magnatune.player.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * A Media3 [Player] that crossfades between consecutive tracks by overlapping two [ExoPlayer]
 * instances and ramping their volumes (mirrors the iOS dual-AVPlayer engine). It is a real Player,
 * so it drives the MediaSession / notification / lock-screen exactly like a plain ExoPlayer.
 *
 * Each ExoPlayer holds a single item at a time; this class owns the playlist + index and advances
 * by swapping to the pre-buffered inactive player — near-gapless when crossfade is off, an audible
 * volume crossfade when on. Tunables are read live via [crossfadeEnabled] / [crossfadeMs].
 */
@UnstableApi
class CrossfadePlayer(
    private val context: Context,
    private val looper: Looper,
    private val crossfadeEnabled: () -> Boolean,
    private val crossfadeMs: () -> Long,
) : SimpleBasePlayer(looper) {

    private fun buildExo() = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setLooper(looper)
        .build()

    private val playersArr = arrayOf(buildExo(), buildExo())
    private var activeIdx = 0
    private val active get() = playersArr[activeIdx]
    private val inactive get() = playersArr[1 - activeIdx]

    private var items: List<MediaItem> = emptyList()
    private var uids: List<Long> = emptyList()
    private var uidSeq = 0L
    private var currentIndex = 0
    private var playWhenReadyState = false
    private var masterVolume = 1f
    private var released = false

    private var crossfading = false
    private var crossfadeStartMs = 0L
    private var nextPrepared = false   // inactive holds items[currentIndex+1]

    private val handler = Handler(looper)
    private val ticker = object : Runnable {
        override fun run() {
            if (!released) { tick(); handler.postDelayed(this, 100) }
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) { invalidateState() }
        override fun onPlayWhenReadyChanged(pwr: Boolean, reason: Int) { invalidateState() }
        override fun onIsPlayingChanged(isPlaying: Boolean) { invalidateState() }
    }

    init {
        playersArr.forEach { it.addListener(listener) }
        handler.postDelayed(ticker, 100)
    }

    private fun hasNextItem() = currentIndex + 1 < items.size

    // ---- state ----
    override fun getState(): State {
        val commands = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP,
            Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_CHANGE_MEDIA_ITEMS,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA, Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD, Player.COMMAND_SET_VOLUME, Player.COMMAND_GET_VOLUME,
            Player.COMMAND_RELEASE,
        ).build()

        val builder = State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(playWhenReadyState, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (items.isEmpty()) Player.STATE_IDLE else active.playbackState)
            .setVolume(masterVolume)

        if (items.isNotEmpty()) {
            val playlist = items.indices.map { i ->
                MediaItemData.Builder(uids[i])
                    .setMediaItem(items[i])
                    .setDurationUs(if (i == currentIndex && active.duration > 0) active.duration * 1000 else C.TIME_UNSET)
                    .setIsSeekable(true)
                    .build()
            }
            builder.setPlaylist(playlist)
                .setCurrentMediaItemIndex(currentIndex)
                .setContentPositionMs(PositionSupplier { active.currentPosition.coerceAtLeast(0) })
                .setContentBufferedPositionMs(PositionSupplier { active.bufferedPosition.coerceAtLeast(0) })
        }
        return builder.build()
    }

    // ---- command handlers ----
    override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        cancelCrossfade()
        items = mediaItems
        uids = mediaItems.map { uidSeq++ }
        currentIndex = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, mediaItems.lastIndex.coerceAtLeast(0))
        loadActive(if (startPositionMs == C.TIME_UNSET) 0 else startPositionMs)
        prefetchNext()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        playWhenReadyState = playWhenReady
        active.playWhenReady = playWhenReady
        if (crossfading) inactive.playWhenReady = playWhenReady
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        active.prepare(); inactive.prepare()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        cancelCrossfade(); active.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (items.isEmpty()) return Futures.immediateVoidFuture()
        val target = if (mediaItemIndex == C.INDEX_UNSET) currentIndex else mediaItemIndex
        if (target == currentIndex) {
            active.seekTo(if (positionMs == C.TIME_UNSET) 0 else positionMs)
        } else if (target in items.indices) {
            cancelCrossfade()
            currentIndex = target
            loadActive(if (positionMs == C.TIME_UNSET) 0 else positionMs)
            prefetchNext()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        masterVolume = volume.coerceIn(0f, 1f)
        if (!crossfading) active.volume = masterVolume
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        released = true
        handler.removeCallbacksAndMessages(null)
        playersArr.forEach { it.removeListener(listener); it.release() }
        return Futures.immediateVoidFuture()
    }

    // ---- engine ----
    private fun loadActive(positionMs: Long) {
        active.volume = masterVolume
        active.setMediaItem(items[currentIndex], positionMs)
        active.prepare()
        active.playWhenReady = playWhenReadyState
        nextPrepared = false
        invalidateState()
    }

    private fun prefetchNext() {
        if (!hasNextItem()) { nextPrepared = false; return }
        inactive.volume = 0f
        inactive.setMediaItem(items[currentIndex + 1])
        inactive.prepare()
        inactive.playWhenReady = false
        nextPrepared = true
    }

    private fun tick() {
        if (items.isEmpty()) return
        val xf = crossfadeMs()

        if (crossfading) {
            // Ramp by WALL-CLOCK time since the fade started, then swap — independent of the stream's
            // (sometimes unreliable) reported duration/position, so it always completes.
            val elapsed = android.os.SystemClock.elapsedRealtime() - crossfadeStartMs
            val f = (elapsed.toFloat() / xf).coerceIn(0f, 1f)
            active.volume = (1f - f) * masterVolume
            inactive.volume = f * masterVolume
            if (f >= 1f || active.playbackState == Player.STATE_ENDED) swapToNext(hardCut = false)
            return
        }

        // Not crossfading: a finished track hard-swaps to the prefetched next (or stops).
        if (active.playbackState == Player.STATE_ENDED) {
            if (hasNextItem()) swapToNext(hardCut = true) else { playWhenReadyState = false; invalidateState() }
            return
        }
        if (!playWhenReadyState || !crossfadeEnabled() || !hasNextItem() || !nextPrepared) return
        val dur = active.duration
        if (dur <= 0) return
        val remaining = dur - active.currentPosition
        if (remaining in 1..xf) beginCrossfade()
    }

    private fun beginCrossfade() {
        crossfading = true
        crossfadeStartMs = android.os.SystemClock.elapsedRealtime()
        inactive.volume = 0f
        inactive.seekTo(0)
        inactive.playWhenReady = playWhenReadyState
    }

    private fun swapToNext(hardCut: Boolean) {
        active.stop()
        active.clearMediaItems()
        active.volume = masterVolume
        activeIdx = 1 - activeIdx
        currentIndex += 1
        crossfading = false
        active.volume = masterVolume
        active.playWhenReady = playWhenReadyState
        prefetchNext()
        invalidateState()
    }

    private fun cancelCrossfade() {
        if (!crossfading && !nextPrepared) return
        crossfading = false
        inactive.stop()
        inactive.clearMediaItems()
        inactive.volume = masterVolume
        active.volume = masterVolume
        nextPrepared = false
    }
}
