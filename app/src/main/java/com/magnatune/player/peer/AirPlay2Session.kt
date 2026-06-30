package com.magnatune.player.peer

import android.util.Log
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * AirPlay-2 realtime audio sender (ported from the verified ap2s.py prototype). Streams 44100/16/
 * stereo PCM (fed via [enqueue] from the ExoPlayer tap) to AirPlay-2 receivers (Sonos, macOS,
 * HomePod, AppleTV — the port-7000 devices). NTP timing only (no PTP), so it works on stock Android.
 *
 * Flow: transient pair-setup → ChaCha20-Poly1305 control channel → event channel → SETUP(session)
 * → RECORD → SETUP(stream, realtime ALAC) → volume → stream ALAC/ChaCha RTP audio over UDP with
 * an NTP timing responder, control-channel sync, and packet-loss retransmit.
 */
class AirPlay2Session(private val host: String, private val port: Int) {

    companion object {
        private const val TAG = "AirPlay2"
        private val N = BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74" +
            "020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F1437" +
            "4FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
            "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF05" +
            "98DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB" +
            "9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
            "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF695581718" +
            "3995497CEA956AE515D2261898FA051015728E5A8AAAC42DAD33170D04507A33" +
            "A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" +
            "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864" +
            "D87602733EC86A64521F2B18177B200CBBE117577A615D6C770988C0BAD946E2" +
            "08E24FA074E5AB3143DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF", 16)
        private val G = BigInteger.valueOf(5)
        private const val N_LEN = 384
        private const val SPF = 352
        private const val CHUNK_BYTES = SPF * 4           // 352 frames * 16-bit stereo
        private const val RING_BYTES = 44100 * 4 * 2      // ~2s jitter buffer
        private const val START_RTP = 88200L   // exactly as the working Python prototype
    }

    @Volatile private var running = false
    @Volatile var volume = 1f; private set
    private val threads = ArrayList<Thread>()
    private var sock: Socket? = null
    private var inp: BufferedInputStream? = null

    // encrypted control channel
    private lateinit var ctrlWriteKey: ByteArray
    private lateinit var ctrlReadKey: ByteArray
    private lateinit var audioKey: ByteArray
    private var wc = 0L; private var rc = 0L
    private val chLock = Object()   // serializes the encrypted RTSP channel (send+recv atomic)
    private var sessionUrl = "*"     // RTSP session URL (set after SETUP), used by setVolume
    private var cseq = 0

    // PCM jitter buffer (fed by the tap)
    private val lock = Object()
    private val ring = ByteArray(RING_BYTES)
    private var wpos = 0; private var rpos = 0; private var filled = 0

    val isActive: Boolean get() = running

    // ===== crypto helpers =====
    private fun sha512(vararg p: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-512"); for (x in p) md.update(x); return md.digest()
    }
    private fun hkdf(ikm: ByteArray, salt: String, info: String, len: Int = 32): ByteArray {
        val h = HKDFBytesGenerator(SHA512Digest())
        h.init(HKDFParameters(ikm, salt.toByteArray(), info.toByteArray()))
        return ByteArray(len).also { h.generateBytes(it, 0, len) }
    }
    private fun chacha(encrypt: Boolean, key: ByteArray, nonce: ByteArray, input: ByteArray, aad: ByteArray): ByteArray {
        val c = ChaCha20Poly1305()
        c.init(encrypt, AEADParameters(KeyParameter(key), 128, nonce, aad))
        val out = ByteArray(c.getOutputSize(input.size))
        var n = c.processBytes(input, 0, input.size, out, 0)
        n += c.doFinal(out, n)
        return if (n == out.size) out else out.copyOf(n)
    }
    private fun nonce(counter: Long): ByteArray {
        val b = ByteArray(12)
        for (i in 0 until 8) b[4 + i] = ((counter shr (8 * i)) and 0xFF).toByte()  // LE in bytes 4..11
        return b
    }
    private fun unsigned(x: BigInteger): ByteArray {
        val b = x.toByteArray(); return if (b.size > 1 && b[0].toInt() == 0) b.copyOfRange(1, b.size) else b
    }
    private fun pad(x: BigInteger): ByteArray {
        val b = unsigned(x); return if (b.size >= N_LEN) b else ByteArray(N_LEN - b.size) + b
    }

    // ===== public API =====
    fun start(initialVolume: Float, onResult: (Boolean) -> Unit) {
        volume = initialVolume
        thread(name = "airplay2-connect", isDaemon = true) {
            val ok = runCatching { connectAndStream() }.getOrElse { Log.e(TAG, "AP2 start failed", it); false }
            onResult(ok)
        }
    }

    fun enqueue(data: ByteArray, offset: Int, len: Int) {
        synchronized(lock) {
            var o = offset; var n = len
            if (n > RING_BYTES) { o += n - RING_BYTES; n = RING_BYTES }
            val over = filled + n - RING_BYTES
            if (over > 0) { rpos = (rpos + over) % RING_BYTES; filled -= over }
            val first = minOf(n, RING_BYTES - wpos)
            System.arraycopy(data, o, ring, wpos, first)
            if (n > first) System.arraycopy(data, o + first, ring, 0, n - first)
            wpos = (wpos + n) % RING_BYTES; filled += n
            (lock as java.lang.Object).notifyAll()
        }
    }

    private fun drain(out: ByteArray) {
        synchronized(lock) {
            if (filled >= CHUNK_BYTES) {
                val first = minOf(CHUNK_BYTES, RING_BYTES - rpos)
                System.arraycopy(ring, rpos, out, 0, first)
                if (CHUNK_BYTES > first) System.arraycopy(ring, 0, out, first, CHUNK_BYTES - first)
                rpos = (rpos + CHUNK_BYTES) % RING_BYTES; filled -= CHUNK_BYTES
            } else out.fill(0)
        }
    }

    fun setVolume(v: Float) {
        volume = v
        // MUST run off the caller's thread: setVolume is invoked from the main thread, and doing
        // socket I/O there throws NetworkOnMainThreadException mid-write — leaving a partial frame
        // that corrupts the encrypted channel and makes the Sonos FIN. SET_PARAMETER also must
        // target the session URL (not "*"). The chLock in ereq serializes against feedback/setup.
        if (running) spawn("ap2-vol") {
            runCatching { ereq("SET_PARAMETER", "volume: ${-30f + 30f * v}\r\n".toByteArray(), "text/parameters", sessionUrl) }
        }
    }

    fun stop() {
        running = false
        threads.forEach { it.interrupt() }
        runCatching { sock?.close() }
        synchronized(lock) { wpos = 0; rpos = 0; filled = 0 }
    }

    // ===== the flow =====
    private fun connectAndStream(): Boolean {
        val s = Socket(); s.connect(InetSocketAddress(host, port), 5000); s.tcpNoDelay = true
        sock = s; inp = BufferedInputStream(s.getInputStream())
        val k = pairSetupTransient() ?: return false
        ctrlWriteKey = hkdf(k, "Control-Salt", "Control-Write-Encryption-Key")
        ctrlReadKey = hkdf(k, "Control-Salt", "Control-Read-Encryption-Key")
        audioKey = hkdf(k, "Events-Salt", "Events-Write-Encryption-Key")
        running = true   // gate the worker threads — must be set BEFORE spawning them

        val dev = InetAddress.getByName(host)
        // NTP timing responder
        val timingSock = DatagramSocket(0)
        spawn("ap2-timing") {
            val buf = ByteArray(64)
            while (running) {
                val pkt = DatagramPacket(buf, buf.size)
                try { timingSock.receive(pkt) } catch (_: Exception) { break }
                if (pkt.length >= 32) {
                    val d = pkt.data; val r = ByteArray(32)
                    r[0] = 0x80.toByte(); r[1] = 0xd3.toByte(); r[2] = d[2]; r[3] = d[3]
                    System.arraycopy(d, 24, r, 8, 8)            // originate = client transmit
                    ntpNow().copyInto(r, 16); ntpNow().copyInto(r, 24)
                    timingSock.send(DatagramPacket(r, 32, pkt.address, pkt.port))
                }
            }
        }
        val sid = (SecureRandom().nextInt() .toLong() and 0xFFFFFFFFL).toString()
        val url = "rtsp://${s.localAddress.hostAddress}/$sid"
        sessionUrl = url   // so setVolume() (called from the player thread) targets the session, not "*"

        // SETUP(session)
        val base = linkedMapOf<String, Any?>(
            "deviceID" to "00:11:22:33:44:55", "sessionUUID" to java.util.UUID.randomUUID().toString().uppercase(),
            "timingPort" to timingSock.localPort, "timingProtocol" to "NTP",
        )
        val baseResp = BPlist.decode(ereq("SETUP", BPlist.encode(base), "application/x-apple-binary-plist", url)) as Map<*, *>
        val eventPort = (baseResp["eventPort"] as Number).toInt()
        Log.i(TAG, "SETUP session ok, eventPort=$eventPort")
        // event channel (must connect or RECORD hangs)
        val evt = Socket(); evt.connect(InetSocketAddress(host, eventPort), 5000); threadsCloseable(evt)

        ereq("RECORD", ByteArray(0), null, url, "RTP-Info: seq=0;rtptime=$START_RTP\r\n")
        Log.i(TAG, "RECORD ok")

        // SETUP(stream) realtime ALAC
        val ctrlSock = DatagramSocket(0)
        val scid = SecureRandom().nextInt().toLong() and 0xFFFFFFFFL
        val stream = linkedMapOf<String, Any?>("streams" to listOf(linkedMapOf<String, Any?>(
            "audioFormat" to 0x40000, "audioMode" to "default", "controlPort" to ctrlSock.localPort,
            "ct" to 2, "isMedia" to true, "latencyMax" to 88200, "latencyMin" to 11025,
            "shk" to audioKey, "spf" to SPF, "sr" to 44100, "type" to 0x60, "streamConnectionID" to scid,
        )))
        val sResp = BPlist.decode(ereq("SETUP", BPlist.encode(stream), "application/x-apple-binary-plist", url)) as Map<*, *>
        val st0 = (sResp["streams"] as List<*>)[0] as Map<*, *>
        val dataPort = (st0["dataPort"] as Number).toInt()
        val devCtrlPort = (st0["controlPort"] as Number).toInt()
        Log.i(TAG, "SETUP stream ok, dataPort=$dataPort")

        runCatching { ereq("SET_PARAMETER", "volume: ${-30f + 30f * volume}\r\n".toByteArray(), "text/parameters", url) }

        // feedback keep-alive
        spawn("ap2-feedback") {
            while (running) { try { Thread.sleep(2000) } catch (_: Exception) { break }; runCatching { ereq("POST", ByteArray(0), null, "/feedback") } }
        }

        // audio + control sync + retransmit run on their own thread so connect returns now
        // (so onResult(true) fires → the player mutes local + starts feeding the PCM tap)
        spawn("ap2-audio") { runCatching { streamAudio(dev, dataPort, devCtrlPort, ctrlSock, scid) } }
        return true
    }

    private fun streamAudio(dev: InetAddress, dataPort: Int, devCtrlPort: Int, ctrlSock: DatagramSocket, scid: Long) {
        val audioSock = DatagramSocket()
        var seq = SecureRandom().nextInt() and 0xFFFF
        var rtp = START_RTP
        val curRtp = java.util.concurrent.atomic.AtomicLong(rtp)
        val latencyFrames = 44100L   // device plays 1s behind head → 1s jitter margin (was 11025)
        val backlog = HashMap<Int, ByteArray>()
        val blk = Object()

        // control sync every 1s — explicit rtp↔NTP map (rtp small, NTP = real wall clock)
        spawn("ap2-sync") {
            var first = true
            while (running) {
                val nt = ntpNow()
                val h = ByteArray(20)
                h[0] = if (first) 0x90.toByte() else 0x80.toByte(); h[1] = 0xd4.toByte(); h[2] = 0; h[3] = 7
                putBE(h, 4, (curRtp.get() - latencyFrames) and 0xFFFFFFFFL, 4)   // rtp playing now (2s behind head)
                System.arraycopy(nt, 0, h, 8, 8)                                 // NTP = now
                putBE(h, 16, curRtp.get() and 0xFFFFFFFFL, 4)                    // rtp head
                runCatching { ctrlSock.send(DatagramPacket(h, 20, dev, devCtrlPort)) }
                first = false
                try { Thread.sleep(1000) } catch (_: Exception) { break }
            }
        }
        // retransmit listener (device requests on our controlPort)
        spawn("ap2-resend") {
            val buf = ByteArray(64)
            while (running) {
                val pkt = DatagramPacket(buf, buf.size)
                try { ctrlSock.receive(pkt) } catch (_: Exception) { break }
                val d = pkt.data
                if (pkt.length >= 8 && (d[1].toInt() and 0x7f) == 0x55) {
                    val firstSeq = ((d[4].toInt() and 0xFF) shl 8) or (d[6 - 1].toInt() and 0xFF)
                    val cnt = ((d[6].toInt() and 0xFF) shl 8) or (d[7].toInt() and 0xFF)
                    synchronized(blk) {
                        for (i in 0 until cnt) {
                            val p = backlog[(firstSeq + i) and 0xFFFF] ?: continue
                            val out = ByteArray(4 + p.size)
                            out[0] = 0x80.toByte(); out[1] = 0xd6.toByte(); out[2] = 0; out[3] = 1
                            System.arraycopy(p, 0, out, 4, p.size)
                            runCatching { ctrlSock.send(DatagramPacket(out, out.size, pkt.address, pkt.port)) }
                        }
                    }
                }
            }
        }

        Log.i(TAG, "streaming audio to $dataPort")
        val chunk = ByteArray(CHUNK_BYTES)
        val per = SPF.toDouble() / 44100.0
        val lead = 2.0   // burst ~2s upfront for more jitter margin (was 1.0)
        // Pace on the SAME nanoTime base that ntpNow() is anchored to, so the rtp timeline (cur) and
        // the sync/timing NTP are perfectly locked and high-resolution (one clock, like Python).
        val t0n = System.nanoTime()
        var pk = 0L
        var actr = 0L
        while (running) {
            drain(chunk)   // live music from the tap
            val alac = pcmToAlacRaw(chunk)
            val hdr = ByteArray(12)
            hdr[0] = 0x80.toByte(); hdr[1] = if (pk == 0L) 0xe0.toByte() else 0x60.toByte()
            putBE(hdr, 2, (seq and 0xFFFF).toLong(), 2); putBE(hdr, 4, rtp and 0xFFFFFFFFL, 4); putBE(hdr, 8, scid, 4)
            val enc = chacha(true, audioKey, nonce(actr), alac, hdr.copyOfRange(4, 12))
            val pktBytes = ByteArray(12 + enc.size + 8)
            System.arraycopy(hdr, 0, pktBytes, 0, 12); System.arraycopy(enc, 0, pktBytes, 12, enc.size)
            for (i in 0 until 8) pktBytes[12 + enc.size + i] = ((actr shr (8 * i)) and 0xFF).toByte()
            runCatching { audioSock.send(DatagramPacket(pktBytes, pktBytes.size, dev, dataPort)) }
            synchronized(blk) { backlog[seq and 0xFFFF] = pktBytes; if (backlog.size > 1024) backlog.remove(backlog.keys.first()) }
            actr++; seq = (seq + 1) and 0xFFFF; rtp += SPF; curRtp.set(rtp); pk++
            val dtNs = ((pk * per - lead) * 1e9).toLong() - (System.nanoTime() - t0n)   // stay ~lead s ahead
            if (dtNs > 0) try { Thread.sleep(dtNs / 1_000_000L, (dtNs % 1_000_000L).toInt()) } catch (_: InterruptedException) { break }
        }
    }

    // Real wall-clock NTP (epoch 1900) — what the working Python prototype + iOS senders send.
    // HIGH RESOLUTION: currentTimeMillis is only 1ms-precise; the Sonos's NTP clock-sync needs
    // sub-ms timestamps (RTT ~4ms) or it can't lock and tears the stream down after a few queries.
    // So anchor the wall epoch once, then advance with nanoTime for nanosecond fractional precision.
    private val clkAnchorMs = System.currentTimeMillis()
    private val clkAnchorNano = System.nanoTime()
    private fun ntpNow(): ByteArray {
        val nowUnixNs = clkAnchorMs * 1_000_000L + (System.nanoTime() - clkAnchorNano)
        val secs = nowUnixNs / 1_000_000_000L + 2208988800L
        val frac = ((nowUnixNs % 1_000_000_000L) shl 32) / 1_000_000_000L   // <1e9<<32 ≈ 4.3e18 < Long max
        val b = ByteArray(8); putBE(b, 0, secs and 0xFFFFFFFFL, 4); putBE(b, 4, frac and 0xFFFFFFFFL, 4); return b
    }
    private fun putBE(b: ByteArray, off: Int, v: Long, size: Int) {
        for (i in 0 until size) b[off + i] = ((v shr (8 * (size - 1 - i))) and 0xFF).toByte()
    }

    // uncompressed-ALAC frame (ported from libcodecs pcm_to_alac_raw); pcm = 352 frames LE 16-bit stereo
    private fun pcmToAlacRaw(pcm: ByteArray, frames: Int = SPF): ByteArray {
        val bsize = frames
        val out = ByteArray(bsize * 4 + 16); var p = 0
        fun u32(i: Int): Long = (pcm[i * 4].toLong() and 0xFF) or ((pcm[i * 4 + 1].toLong() and 0xFF) shl 8) or
            ((pcm[i * 4 + 2].toLong() and 0xFF) shl 16) or ((pcm[i * 4 + 3].toLong() and 0xFF) shl 24)
        out[p++] = (1 shl 5).toByte(); out[p++] = 0
        out[p++] = ((1 shl 4) or (1 shl 1) or ((bsize ushr 31) and 1)).toByte()
        out[p++] = ((((bsize and 0x7f800000) shl 1) ushr 24) and 0xFF).toByte()
        out[p++] = ((((bsize and 0x007f8000) shl 1) ushr 16) and 0xFF).toByte()
        out[p++] = ((((bsize and 0x00007f80) shl 1) ushr 8) and 0xFF).toByte()
        var lb = ((bsize and 0x7f) shl 1) and 0xFF
        lb = lb or ((u32(0).toInt() and 0x8000) ushr 15)
        out[p++] = (lb and 0xFF).toByte()
        for (i in 0 until frames - 1) {
            val v = u32(i); val nx = u32(i + 1)
            out[p++] = (((v and 0x00007f80) ushr 7).toInt() and 0xFF).toByte()
            out[p++] = ((((v and 0x7f) shl 1) or ((v and 0x80000000) ushr 31)).toInt() and 0xFF).toByte()
            out[p++] = (((v and 0x7f800000) ushr 23).toInt() and 0xFF).toByte()
            out[p++] = ((((v and 0x007f0000) ushr 15) or ((nx and 0x8000) ushr 15)).toInt() and 0xFF).toByte()
        }
        val v = u32(frames - 1)
        out[p++] = (((v and 0x00007f80) ushr 7).toInt() and 0xFF).toByte()
        out[p++] = ((((v and 0x7f) shl 1) or ((v and 0x80000000) ushr 31)).toInt() and 0xFF).toByte()
        out[p++] = (((v and 0x7f800000) ushr 23).toInt() and 0xFF).toByte()
        out[p++] = ((((v and 0x007f0000) ushr 15)).toInt() and 0xFF).toByte()
        out[p - 1] = (out[p - 1].toInt() or 1).toByte()
        out[p++] = ((7 shr 1) shl 6).toByte()
        return out.copyOf(p)
    }

    // ===== pair-setup transient (verified) =====
    private fun pairSetupTransient(): ByteArray? {
        val t2 = Tlv8.decode(plainPost("/pair-setup", Tlv8.encode(listOf(
            Tlv8.STATE to byteArrayOf(1), Tlv8.METHOD to byteArrayOf(0), Tlv8.FLAGS to byteArrayOf(0x10),
        ))))
        t2[Tlv8.ERROR]?.let { Log.e(TAG, "M2 error=${it.firstOrNull()}"); return null }
        val salt = t2[Tlv8.SALT] ?: return null
        val b = BigInteger(1, t2[Tlv8.PUBLIC_KEY] ?: return null)
        val a = BigInteger(1, ByteArray(32).also { SecureRandom().nextBytes(it) })
        val aPub = G.modPow(a, N)
        val k = BigInteger(1, sha512(unsigned(N), pad(G)))
        val u = BigInteger(1, sha512(pad(aPub), pad(b)))
        val x = BigInteger(1, sha512(salt, sha512("Pair-Setup:3939".toByteArray())))
        val s2 = b.subtract(k.multiply(G.modPow(x, N))).mod(N).modPow(a.add(u.multiply(x)), N)
        val kKey = sha512(unsigned(s2))
        val hN = sha512(unsigned(N)); val hG = sha512(unsigned(G))
        val hXor = ByteArray(hN.size) { (hN[it].toInt() xor hG[it].toInt()).toByte() }
        val m1 = sha512(hXor, sha512("Pair-Setup".toByteArray()), salt, pad(aPub), pad(b), kKey)
        val t4 = Tlv8.decode(plainPost("/pair-setup", Tlv8.encode(listOf(
            Tlv8.STATE to byteArrayOf(3), Tlv8.PUBLIC_KEY to pad(aPub), Tlv8.PROOF to m1,
        ))))
        t4[Tlv8.ERROR]?.let { Log.e(TAG, "M4 error=${it.firstOrNull()}"); return null }
        val serverProof = t4[Tlv8.PROOF] ?: return null
        if (!serverProof.contentEquals(sha512(pad(aPub), m1, kKey))) { Log.e(TAG, "SRP verify failed"); return null }
        Log.i(TAG, "pair-setup transient OK")
        return kKey
    }

    // ===== RTSP I/O =====
    private fun plainPost(uri: String, body: ByteArray): ByteArray {
        val out = sock!!.getOutputStream()
        val h = "POST $uri RTSP/1.0\r\nCSeq: ${cseq++}\r\nX-Apple-HKP: 4\r\n" +
            "Content-Type: application/octet-stream\r\nContent-Length: ${body.size}\r\n\r\n"
        out.write(h.toByteArray()); out.write(body); out.flush()
        return readPlainBody()
    }
    private fun readPlainBody(): ByteArray {
        val bin = inp!!; val hbuf = ByteArrayOutputStream()
        while (true) { val c = bin.read(); if (c < 0) break; hbuf.write(c)
            val a = hbuf.toByteArray()
            if (a.size >= 4 && a[a.size-4]=='\r'.code.toByte() && a[a.size-3]=='\n'.code.toByte() && a[a.size-2]=='\r'.code.toByte() && a[a.size-1]=='\n'.code.toByte()) break }
        val clen = Regex("Content-Length:\\s*(\\d+)", RegexOption.IGNORE_CASE).find(String(hbuf.toByteArray()))?.groupValues?.get(1)?.toInt() ?: 0
        val body = ByteArray(clen); var r = 0; while (r < clen) { val n = bin.read(body, r, clen - r); if (n < 0) break; r += n }
        return body
    }

    /** Encrypted RTSP exchange; returns the response body. */
    private fun ereq(method: String, body: ByteArray, ctype: String?, uri: String = "*", extra: String = ""): ByteArray {
        val sb = StringBuilder("$method $uri RTSP/1.0\r\nCSeq: ${cseq++}\r\nUser-Agent: AirPlay/665.13.1\r\n" +
            "DACP-ID: 0000000000000001\r\nActive-Remote: 1\r\nClient-Instance: 0000000000000001\r\n$extra")
        if (ctype != null) sb.append("Content-Type: $ctype\r\n")
        sb.append("Content-Length: ${body.size}\r\n\r\n")
        // Serialize ALL channel access — ereq is called from the connect thread, the feedback thread,
        // and the player's setVolume thread. Without this lock concurrent frames interleave and the
        // shared ChaCha wc/rc counters corrupt, so the Sonos gets a bad-nonce frame and FINs the
        // connection (Python guards the same way with `with chlock:`).
        synchronized(chLock) {
            encSend(sb.toString().toByteArray() + body)
            return encRecvMessage()
        }
    }
    private fun encSend(plain: ByteArray) {
        val out = sock!!.getOutputStream()
        var off = 0
        while (off < plain.size) {
            val len = minOf(0x400, plain.size - off)
            val aad = byteArrayOf((len and 0xFF).toByte(), ((len shr 8) and 0xFF).toByte())  // LE length
            val ct = chacha(true, ctrlWriteKey, nonce(wc), plain.copyOfRange(off, off + len), aad); wc++
            out.write(aad); out.write(ct); off += len
        }
        out.flush()
    }
    private fun encRecvMessage(): ByteArray {
        val bin = inp!!
        fun rd(n: Int): ByteArray { val d = ByteArray(n); var r = 0; while (r < n) { val k = bin.read(d, r, n - r); if (k < 0) throw java.io.EOFException(); r += k }; return d }
        val acc = ByteArrayOutputStream()
        fun full(): Boolean {
            val b = acc.toByteArray(); val sep = "\r\n\r\n".toByteArray()
            val hi = indexOf(b, sep); if (hi < 0) return false
            val headers = String(b, 0, hi)
            val cl = Regex("Content-Length:\\s*(\\d+)", RegexOption.IGNORE_CASE).find(headers)?.groupValues?.get(1)?.toInt() ?: 0
            return b.size >= hi + 4 + cl
        }
        while (!full()) {
            val lenB = rd(2); val len = (lenB[0].toInt() and 0xFF) or ((lenB[1].toInt() and 0xFF) shl 8)
            val ctTag = rd(len + 16)
            acc.write(chacha(false, ctrlReadKey, nonce(rc), ctTag, lenB)); rc++
        }
        val b = acc.toByteArray(); val hi = indexOf(b, "\r\n\r\n".toByteArray())
        return b.copyOfRange(hi + 4, b.size)
    }
    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) { for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer; return i }
        return -1
    }

    private fun spawn(name: String, block: () -> Unit) { threads.add(thread(name = name, isDaemon = true, block = block)) }
    private fun threadsCloseable(s: Socket) { spawn("ap2-keep") { try { while (running) Thread.sleep(1000) } catch (_: Exception) {}; runCatching { s.close() } } }
}
