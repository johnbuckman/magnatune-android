package com.magnatune.player.peer

import android.util.Base64
import android.util.Log
import java.math.BigInteger
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher
import kotlin.concurrent.thread

/**
 * Streams 44100 Hz / 16-bit / stereo PCM to an AirPlay-1 (RAOP) device via the bundled libraop.
 *
 * The AES-128-CBC key/iv are generated here and the key is RSA-OAEP-encrypted (java.security)
 * with the well-known AirPort public key, then handed to native, so the C side carries no crypto
 * library. PCM is fed in via [enqueue] (from the ExoPlayer tap) and drained by a sender thread
 * that respects libraop's real-time pacing; underruns are filled with silence to hold sync.
 */
class RaopSession(private val host: String, private val port: Int) {

    companion object {
        private const val TAG = "RaopSession"
        // Well-known AirPort Express RSA public modulus (base64); exponent = AQAB (65537).
        private const val AIRPORT_MODULUS_B64 =
            "59dE8qLieItsH1WgjrcFRKj6eUWqi+bGLOX1HL3U3GhC/j0Qg90u3sG/1CUtwC" +
            "5vOYvfDmFI6oSFXi5ELabWJmT2dKHzBJKa3k9ok+8t9ucRqMd6DZHJ2YCCLlDR" +
            "KSKv6kDqnw4UwPdpOMXziC/AMj3Z/lUVX1G7WSHCAWKf1zNS1eLvqr+boEjXuB" +
            "OitnZ/bDzPHrTOZz0Dew0uowxf/+sG+NCK3eQJVxqcaJ/vEHKIVd2M+5qL71yJ" +
            "Q+87X6oV3eaYvt3zWZYD6z5vYTcrtij2VZ9Zmni/UAaHqn9JdsBWLUEpVviYnh" +
            "imNVvYFZeCXg/IdTQ+x4IRdiXNv5hEew=="
        private const val FRAMES_PER_CHUNK = 352
        private const val CHUNK_BYTES = FRAMES_PER_CHUNK * 4   // 16-bit * 2ch
        private const val RING_BYTES = 44100 * 4 * 2           // ~2s jitter buffer
    }

    @Volatile private var handle = 0L
    @Volatile private var running = false
    @Volatile var volume = 1f
        private set
    private var sender: Thread? = null

    private val lock = Object()
    private val ring = ByteArray(RING_BYTES)
    private var writePos = 0
    private var readPos = 0
    private var filled = 0

    val isActive: Boolean get() = running && handle != 0L

    /** Connect on a background thread; [onResult] reports success/failure (true once streaming). */
    fun start(initialVolume: Float, onResult: (Boolean) -> Unit) {
        volume = initialVolume
        sender = thread(name = "raop-sender", isDaemon = true) {
            val key = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val rsaaeskey = runCatching { rsaEncryptKey(key) }.getOrElse {
                Log.e(TAG, "RSA encrypt failed", it); onResult(false); return@thread
            }
            handle = runCatching { RaopNative.nativeConnect(host, port, key, iv, rsaaeskey, volume) }
                .getOrElse { Log.e(TAG, "nativeConnect threw", it); 0L }
            if (handle == 0L) { Log.e(TAG, "connect to $host:$port failed"); onResult(false); return@thread }

            running = true
            Log.i(TAG, "connected to $host:$port (latency ${RaopNative.nativeLatencyMs(handle)}ms)")
            onResult(true)

            val chunk = ByteArray(CHUNK_BYTES)
            var sendN = 0
            while (running) {
                if (RaopNative.nativeAcceptFrames(handle)) {
                    val nz = drain(chunk)
                    if (sendN++ % 200 == 0) {
                        Log.i(TAG, "send chunk #$sendN filled=$filled nonzero=$nz")
                    }
                    RaopNative.nativeSendChunk(handle, chunk, FRAMES_PER_CHUNK)
                } else {
                    try { Thread.sleep(2) } catch (_: InterruptedException) { break }
                }
            }
            runCatching { RaopNative.nativeDisconnect(handle) }
            handle = 0
            Log.i(TAG, "session ended")
        }
    }

    /** Feed decoded PCM (16-bit stereo LE). Drops oldest audio if the jitter buffer overflows. */
    fun enqueue(data: ByteArray, offset: Int, len: Int) {
        synchronized(lock) {
            var o = offset
            var n = len
            if (n > RING_BYTES) { o += n - RING_BYTES; n = RING_BYTES }
            // make room by discarding oldest
            val over = filled + n - RING_BYTES
            if (over > 0) { readPos = (readPos + over) % RING_BYTES; filled -= over }
            val first = minOf(n, RING_BYTES - writePos)
            System.arraycopy(data, o, ring, writePos, first)
            if (n > first) System.arraycopy(data, o + first, ring, 0, n - first)
            writePos = (writePos + n) % RING_BYTES
            filled += n
        }
    }

    private fun drain(out: ByteArray): Int {
        synchronized(lock) {
            if (filled >= CHUNK_BYTES) {
                val first = minOf(CHUNK_BYTES, RING_BYTES - readPos)
                System.arraycopy(ring, readPos, out, 0, first)
                if (CHUNK_BYTES > first) System.arraycopy(ring, 0, out, first, CHUNK_BYTES - first)
                readPos = (readPos + CHUNK_BYTES) % RING_BYTES
                filled -= CHUNK_BYTES
                return 1
            } else {
                out.fill(0)   // underrun → silence (holds RAOP timing)
                return 0
            }
        }
    }

    fun setVolume(v: Float) {
        volume = v
        val h = handle
        if (h != 0L) runCatching { RaopNative.nativeSetVolume(h, v) }
    }

    fun stop() {
        running = false
        sender?.interrupt()
        sender?.join(800)
        sender = null
        synchronized(lock) { writePos = 0; readPos = 0; filled = 0 }
    }

    private fun rsaEncryptKey(aesKey: ByteArray): String {
        val modulus = BigInteger(1, Base64.decode(AIRPORT_MODULUS_B64, Base64.DEFAULT))
        val exponent = BigInteger(1, Base64.decode("AQAB", Base64.DEFAULT))
        val pub = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, pub)
        val enc = cipher.doFinal(aesKey)
        // match the C side: base64, no wrapping, '=' padding stripped
        return Base64.encodeToString(enc, Base64.NO_WRAP).trimEnd('=')
    }
}
