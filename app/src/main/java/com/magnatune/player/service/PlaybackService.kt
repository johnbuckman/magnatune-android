package com.magnatune.player.service

import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.android.gms.cast.framework.CastContext

/**
 * Foreground Media3 service hosting the ExoPlayer + MediaSession. Gives background audio, the media
 * notification, and lock-screen / bluetooth transport controls. The app talks to it via a
 * MediaController (see [PlaybackController]).
 *
 * On devices with Google Play Services, a [CastPlayer] takes over the session while a Cast session
 * is connected (Phase 8). On non-GMS devices the Cast init is caught and ignored.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var localPlayer: ExoPlayer? = null
    private var castPlayer: CastPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        localPlayer = player
        session = MediaSession.Builder(this, player).build()
        setUpCast()
    }

    /** Wire Cast handoff. Guarded — CastContext init throws on non-GMS devices; we ignore it. */
    private fun setUpCast() {
        try {
            val castContext = CastContext.getSharedInstance(this)
            val cp = CastPlayer(castContext)
            cp.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() = switchTo(cp)
                override fun onCastSessionUnavailable() = localPlayer?.let { switchTo(it) } ?: Unit
            })
            castPlayer = cp
            if (cp.isCastSessionAvailable) switchTo(cp)
        } catch (_: Throwable) {
            // No Google Play Services / Cast unavailable — local playback only.
        }
    }

    /** Move the queue + position to [target] and make it the session player. */
    private fun switchTo(target: Player) {
        val current = session?.player ?: return
        if (current === target) return
        val items = (0 until current.mediaItemCount).map { current.getMediaItemAt(it) }
        val index = current.currentMediaItemIndex.coerceAtLeast(0)
        val pos = current.currentPosition
        val playing = current.playWhenReady
        current.pause()
        if (items.isNotEmpty()) {
            target.setMediaItems(items, index, pos)
            target.prepare()
            target.playWhenReady = playing
        }
        session?.player = target
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Stop playback + service if the player isn't actively playing when the task is swiped away.
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.release()
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        localPlayer?.release()
        session = null; castPlayer = null; localPlayer = null
        super.onDestroy()
    }
}
