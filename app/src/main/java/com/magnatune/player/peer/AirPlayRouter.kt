package com.magnatune.player.peer

import android.util.Log

/**
 * Owns the active [RaopSession] (if any) and bridges the ExoPlayer PCM tap to it. Held by the
 * AppContainer and handed to the CrossfadePlayer so decoded audio can be teed to an AirPlay device.
 */
class AirPlayRouter {
    @Volatile private var session: RaopSession? = null

    val isActive: Boolean get() = session?.isActive == true

    /** Connect to an AirPlay device. [onResult] fires true once streaming has started. */
    fun connect(host: String, port: Int, initialVolume: Float, onResult: (Boolean) -> Unit) {
        disconnect()
        // AirPlay-2 receivers (port 7000) can't be driven by legacy RAOP — exercise the WIP AP2
        // sender (currently: transient pair-setup) and report how far it gets.
        if (port == 7000) {
            kotlin.concurrent.thread(name = "airplay2", isDaemon = true) {
                val ok = AirPlay2Session(host, port).pairSetupTransient()
                Log.i("AirPlayRouter", "AP2 pair-setup ok=$ok")
                onResult(ok)
            }
            return
        }
        if (!RaopNative.available) { Log.e("AirPlayRouter", "raopjni unavailable"); onResult(false); return }
        val s = RaopSession(host, port)
        session = s
        s.start(initialVolume) { ok -> if (!ok) session = null; onResult(ok) }
    }

    /** Called from the audio tap (only while a session is active). */
    fun enqueuePcm(buf: ByteArray, off: Int, len: Int) { session?.enqueue(buf, off, len) }

    fun setVolume(v: Float) { session?.setVolume(v) }

    fun disconnect() { session?.stop(); session = null }
}
