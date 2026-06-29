package com.magnatune.player.peer

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * AirPlay-2 audio sender (work in progress). Targets receivers advertising transient pairing
 * (Sonos, macOS) that legacy RAOP can't drive.
 *
 * Stage 1 (DONE, verified vs Sonos): transient pair-setup over RTSP. Uses the `X-Apple-HKP: 4`
 * header and SRP-6a (RFC-5054 3072-bit group, SHA-512, username "Pair-Setup", PIN "3939") with
 * the HAP M1 formula — NOT BouncyCastle's SRP6Client (whose evidence message differs). On success
 * the shared key K = H(S) is kept for deriving the encrypted-channel keys (Stage 2).
 */
class AirPlay2Session(private val host: String, private val port: Int) {

    companion object {
        private const val TAG = "AirPlay2"
        // RFC 5054 3072-bit group.
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
        private const val N_LEN = 384  // 3072 bits
    }

    private var sock: Socket? = null
    private var inp: BufferedInputStream? = null
    private var cseq = 0

    /** SRP shared key K = SHA-512(S), set after a successful pair-setup; feeds HKDF for Stage 2. */
    var sessionKey: ByteArray? = null
        private set

    private fun sha512(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-512")
        for (p in parts) md.update(p)
        return md.digest()
    }
    private fun unsigned(x: BigInteger): ByteArray {
        val b = x.toByteArray()
        return if (b.size > 1 && b[0].toInt() == 0) b.copyOfRange(1, b.size) else b
    }
    private fun pad(x: BigInteger): ByteArray {
        val b = unsigned(x)
        return if (b.size >= N_LEN) b else ByteArray(N_LEN - b.size) + b
    }

    fun pairSetupTransient(): Boolean {
        return try {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 5000)
            s.tcpNoDelay = true
            sock = s
            inp = BufferedInputStream(s.getInputStream())

            // M1
            val t2 = Tlv8.decode(rtspPost("/pair-setup",
                Tlv8.encode(listOf(
                    Tlv8.STATE to byteArrayOf(1),
                    Tlv8.METHOD to byteArrayOf(0),
                    Tlv8.FLAGS to byteArrayOf(0x10),
                ))))
            t2[Tlv8.ERROR]?.let { Log.e(TAG, "M2 error=${it.firstOrNull()}"); return false }
            val salt = t2[Tlv8.SALT] ?: run { Log.e(TAG, "M2 missing salt"); return false }
            val b = BigInteger(1, t2[Tlv8.PUBLIC_KEY] ?: run { Log.e(TAG, "M2 missing B"); return false })

            // SRP-6a (HAP variant)
            val a = BigInteger(1, ByteArray(32).also { SecureRandom().nextBytes(it) })
            val aPub = G.modPow(a, N)
            val k = BigInteger(1, sha512(unsigned(N), pad(G)))
            val u = BigInteger(1, sha512(pad(aPub), pad(b)))
            val x = BigInteger(1, sha512(salt, sha512("Pair-Setup:3939".toByteArray())))
            val s2 = b.subtract(k.multiply(G.modPow(x, N))).mod(N).modPow(a.add(u.multiply(x)), N)
            val kKey = sha512(unsigned(s2))
            val hN = sha512(unsigned(N)); val hG = sha512(unsigned(G))  // H(g) over minimal bytes (0x05)
            val hXor = ByteArray(hN.size) { (hN[it].toInt() xor hG[it].toInt()).toByte() }
            val m1 = sha512(hXor, sha512("Pair-Setup".toByteArray()), salt, pad(aPub), pad(b), kKey)

            // M3
            val t4 = Tlv8.decode(rtspPost("/pair-setup",
                Tlv8.encode(listOf(
                    Tlv8.STATE to byteArrayOf(3),
                    Tlv8.PUBLIC_KEY to pad(aPub),
                    Tlv8.PROOF to m1,
                ))))
            t4[Tlv8.ERROR]?.let { Log.e(TAG, "M4 error=${it.firstOrNull()} (pairing rejected)"); return false }
            val serverProof = t4[Tlv8.PROOF] ?: run { Log.e(TAG, "M4 missing proof"); return false }
            val expected = sha512(pad(aPub), m1, kKey)
            val ok = serverProof.contentEquals(expected)
            if (ok) sessionKey = kKey
            Log.i(TAG, "pair-setup transient: verify=$ok K=${sessionKey?.size}B")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "pair-setup failed", e); false
        }
    }

    private fun rtspPost(uri: String, body: ByteArray): ByteArray {
        val out = sock!!.getOutputStream()
        val head = "POST $uri RTSP/1.0\r\n" +
            "CSeq: ${cseq++}\r\n" +
            "X-Apple-HKP: 4\r\n" +                       // transient pairing — required, else 403
            "User-Agent: AirPlay/665.13.1\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "Content-Length: ${body.size}\r\n\r\n"
        out.write(head.toByteArray()); out.write(body); out.flush()
        return readResponse()
    }

    private fun readResponse(): ByteArray {
        val bin = inp!!
        val hbuf = ByteArrayOutputStream()
        while (true) {
            val c = bin.read(); if (c < 0) break
            hbuf.write(c)
            val a = hbuf.toByteArray()
            if (a.size >= 4 && a[a.size - 4] == '\r'.code.toByte() && a[a.size - 3] == '\n'.code.toByte() &&
                a[a.size - 2] == '\r'.code.toByte() && a[a.size - 1] == '\n'.code.toByte()) break
        }
        val headers = String(hbuf.toByteArray())
        val status = headers.lineSequence().firstOrNull() ?: ""
        val clen = Regex("Content-Length:\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(headers)?.groupValues?.get(1)?.toInt() ?: 0
        val body = ByteArray(clen); var read = 0
        while (read < clen) { val n = bin.read(body, read, clen - read); if (n < 0) break; read += n }
        Log.i(TAG, "<- $status (${body.size}B)")
        return body
    }

    fun close() { runCatching { sock?.close() } }
}
