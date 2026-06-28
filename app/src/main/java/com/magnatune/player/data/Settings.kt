package com.magnatune.player.data

import android.content.Context
import com.magnatune.player.net.DownloadFormat
import com.magnatune.player.net.StreamQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App preferences, mirroring the iOS UserDefaults keys. Reactive toggles are exposed as StateFlows
 * so Compose recomposes on change; everything is persisted to SharedPreferences.
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = MutableStateFlow(prefs.getBoolean(key, def))
    private fun MutableStateFlow<Boolean>.set(key: String, v: Boolean) { value = v; prefs.edit().putBoolean(key, v).apply() }

    // Playback
    private val _shuffle = bool("shuffle.enabled", false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffle
    fun setShuffle(v: Boolean) = _shuffle.set("shuffle.enabled", v)

    private val _crossfade = bool("crossfade.enabled", true)
    val crossfadeEnabled: StateFlow<Boolean> = _crossfade
    fun setCrossfade(v: Boolean) = _crossfade.set("crossfade.enabled", v)

    var crossfadeDuration: Double
        get() = prefs.getFloat("crossfade.duration", 6f).toDouble().coerceIn(1.0, 10.0)
        set(v) = prefs.edit().putFloat("crossfade.duration", v.toFloat()).apply()

    private val _volume = MutableStateFlow(prefs.getFloat("audio.volume", 1f).coerceIn(0f, 1f))
    val volume: StateFlow<Float> = _volume
    fun setVolume(v: Float) { _volume.value = v.coerceIn(0f, 1f); prefs.edit().putFloat("audio.volume", _volume.value).apply() }

    var streamQuality: StreamQuality
        get() = StreamQuality.from(prefs.getString(StreamQuality.DEFAULTS_KEY, null))
        set(v) = prefs.edit().putString(StreamQuality.DEFAULTS_KEY, v.key).apply()

    var audioCacheEnabled: Boolean
        get() = prefs.getBoolean("audio.cache.enabled", true)
        set(v) = prefs.edit().putBoolean("audio.cache.enabled", v).apply()

    // Library
    private val _hideDislikes = bool("dislike.hide.enabled", true)
    val hideDislikes: StateFlow<Boolean> = _hideDislikes
    fun setHideDislikes(v: Boolean) = _hideDislikes.set("dislike.hide.enabled", v)

    private val _autoDownload = bool("autodownload.favorites", false)
    val autoDownloadFavorites: StateFlow<Boolean> = _autoDownload
    fun setAutoDownload(v: Boolean) = _autoDownload.set("autodownload.favorites", v)

    // Downloads
    var albumDownloadFormat: DownloadFormat
        get() = DownloadFormat.from(prefs.getString("download.album.format", null))
        set(v) = prefs.edit().putString("download.album.format", v.key).apply()
    var songDownloadFormat: String
        get() = prefs.getString("download.song.format", "mp3") ?: "mp3"
        set(v) = prefs.edit().putString("download.song.format", v).apply()

    // LAN peer sync
    private val _peerSharing = bool("peer.sharing.enabled", true)
    val peerSharingEnabled: StateFlow<Boolean> = _peerSharing
    fun setPeerSharing(v: Boolean) = _peerSharing.set("peer.sharing.enabled", v)

    private val _peerAutostop = bool("peer.autostop.enabled", true)
    val peerAutostopEnabled: StateFlow<Boolean> = _peerAutostop
    fun setPeerAutostop(v: Boolean) = _peerAutostop.set("peer.autostop.enabled", v)
}
