package com.magnatune.player.peer

/** JNI surface for the bundled libraop AirPlay-1 sender (app/src/main/cpp/raop). */
internal object RaopNative {
    @Volatile private var loaded = false
    val available: Boolean
        get() {
            if (!loaded) synchronized(this) {
                if (!loaded) runCatching { System.loadLibrary("raopjni") }.onSuccess { loaded = true }
            }
            return loaded
        }

    /** RTSP-connect to [host]:[port]; returns an opaque handle (0 on failure). Blocks — call off-main. */
    external fun nativeConnect(host: String, port: Int, key: ByteArray, iv: ByteArray, rsaaeskey: String, volume: Float): Long
    /** True when the device is ready to accept one more chunk (paces to real time). */
    external fun nativeAcceptFrames(handle: Long): Boolean
    /** Send one chunk of [frames] interleaved 16-bit stereo PCM frames (frames*4 bytes in [pcm]). */
    external fun nativeSendChunk(handle: Long, pcm: ByteArray, frames: Int): Boolean
    external fun nativeSetVolume(handle: Long, vol: Float)
    external fun nativeDisconnect(handle: Long)
    external fun nativeLatencyMs(handle: Long): Int
}
