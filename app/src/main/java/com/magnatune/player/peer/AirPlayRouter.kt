package com.magnatune.player.peer

import android.util.Log

/**
 * Owns the active AirPlay streaming session and bridges the ExoPlayer PCM tap to it. AirPlay-1
 * (RAOP) devices stream via the native [RaopSession]; AirPlay-2 devices (port 7000: Sonos, macOS,
 * HomePod, AppleTV) stream via the Kotlin [AirPlay2Session]. Held by the AppContainer.
 */
class AirPlayRouter {
    @Volatile private var raop: RaopSession? = null
    @Volatile private var ap2: AirPlay2Session? = null

    val isActive: Boolean get() = raop?.isActive == true || ap2?.isActive == true

    fun connect(host: String, port: Int, initialVolume: Float, onResult: (Boolean) -> Unit) {
        disconnect()
        if (port == 7000) {  // AirPlay 2 (NTP realtime)
            val s = AirPlay2Session(host, port)
            ap2 = s
            s.start(initialVolume) { ok -> if (!ok) ap2 = null; onResult(ok) }
            return
        }
        if (!RaopNative.available) { Log.e("AirPlayRouter", "raopjni unavailable"); onResult(false); return }
        val s = RaopSession(host, port)
        raop = s
        s.start(initialVolume) { ok -> if (!ok) raop = null; onResult(ok) }
    }

    /** Called from the audio tap (only the active session, if any, consumes it). */
    fun enqueuePcm(buf: ByteArray, off: Int, len: Int) {
        raop?.enqueue(buf, off, len)
        ap2?.enqueue(buf, off, len)
    }

    fun setVolume(v: Float) { raop?.setVolume(v); ap2?.setVolume(v) }

    fun disconnect() {
        raop?.stop(); raop = null
        ap2?.stop(); ap2 = null
    }
}
