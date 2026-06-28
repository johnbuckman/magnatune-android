package com.magnatune.player.net

import android.net.Uri
import com.magnatune.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Member streaming quality tier (mirrors iOS StreamQuality). Members only — the free stream is
 *  always the `_spoken` announcement file. Maps to a filename suffix on the no-voice AAC stem. */
enum class StreamQuality(val key: String, val label: String, val detail: String, val memberSuffix: String) {
    NORMAL("normal", "Normal", "~160 kbps AAC", ""),
    LOSSLESS("lossless", "Lossless", "256 kbps AAC-LC", "_256");

    companion object {
        const val DEFAULTS_KEY = "stream.quality"
        fun from(key: String?): StreamQuality = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

/** Whole-album membership download format (opens in a browser, which handles the member login). */
enum class DownloadFormat(val key: String, val label: String) {
    VBR("vbr", "MP3 VBR (high quality)"),
    MP3("mp3", "MP3 (128k, compatible)"),
    AAC("aac", "AAC"),
    ALAC("alac", "ALAC (lossless)"),
    FLAC("flac", "FLAC (lossless)"),
    OGG("ogg", "OGG Vorbis"),
    WAV("wav", "WAV (lossless, large)");

    companion object {
        fun from(key: String?): DownloadFormat = entries.firstOrNull { it.key == key } ?: VBR
    }
}

/**
 * Builds all Magnatune URLs (streams, cover art, artist photos, downloads). All HTTP — the whole
 * Magnatune estate is HTTP-only (the network-security-config whitelists *.magnatune.com cleartext).
 */
object UrlBuilder {
    const val HE3 = "he3.magnatune.com"
    const val DOWNLOAD = "download.magnatune.com"
    const val WWW = "magnatune.com"

    private fun url(host: String, path: String): String =
        Uri.Builder().scheme("http").authority(host)
            .appendEncodedPath(path.trimStart('/')).build().toString()

    private fun stem(mp3: String): String = if (mp3.endsWith(".mp3")) mp3.dropLast(4) else mp3

    /** AAC (.m4a) stream. Members → no-announcement file at the chosen quality (`<stem>.m4a` /
     *  `<stem>_256.m4a`); non-members → free `<stem>_spoken.m4a`. Both served by he3, no auth. */
    fun streamUrl(artistName: String, albumName: String, song: Song,
                  isMember: Boolean, quality: StreamQuality = StreamQuality.NORMAL): String {
        val suffix = if (isMember) quality.memberSuffix else "_spoken"
        return url(HE3, "/music/$artistName/$albumName/${stem(song.mp3)}$suffix.m4a")
    }

    /** Album cover thumbnail. Sizes: 50,75,100,150,200,300,400,600,800,1400 (jpg). */
    fun coverUrl(artistName: String, albumName: String, size: Int): String =
        url(HE3, "/music/$artistName/$albumName/cover_$size.jpg")

    /** Sized artist thumbnail in an album dir (artist_<N>.jpg: 50,200,420,840) — tiny, preferred. */
    fun artistPhotoUrl(artistName: String, albumName: String, size: Int): String =
        url(HE3, "/music/$artistName/$albumName/artist_$size.jpg")

    /** Full-resolution original from `artists.photo` (large) — fallback only. */
    fun artistPhotoOriginal(photo: String?): String? =
        photo?.takeIf { it.isNotEmpty() }?.let { url(HE3, it) }

    /** Whole-album download via the membership endpoint (member-auth handled by the browser). */
    fun albumMembershipDownloadUrl(sku: String, format: String): String =
        Uri.Builder().scheme("http").authority(DOWNLOAD).path("/membership/download3")
            .appendQueryParameter("sku", sku).appendQueryParameter("format", format).build().toString()

    /** Single-song open download on he3 (ext ∈ mp3/ogg/wav/flac/m4a). */
    fun songDownloadUrl(artistName: String, albumName: String, song: Song, ext: String): String =
        url(HE3, "/music/$artistName/$albumName/${stem(song.mp3)}.$ext")

    /**
     * Best stream URL, transparently falling back from Lossless (`_256.m4a`) to the Normal member
     * AAC when the 256k file isn't on the server. Only Lossless members trigger a HEAD probe.
     */
    suspend fun resolvedStreamUrl(artistName: String, albumName: String, song: Song,
                                  isMember: Boolean, quality: StreamQuality): String {
        if (!isMember || quality != StreamQuality.LOSSLESS) {
            return streamUrl(artistName, albumName, song, isMember, quality)
        }
        val lossless = streamUrl(artistName, albumName, song, true, StreamQuality.LOSSLESS)
        val normal = streamUrl(artistName, albumName, song, true, StreamQuality.NORMAL)
        return if (LosslessProbe.exists(lossless)) lossless else normal
    }
}

/** Session cache of which Lossless (`_256.m4a`) URLs exist, so we fall back without re-probing. */
object LosslessProbe {
    private val known = HashMap<String, Boolean>()

    suspend fun exists(url: String): Boolean = withContext(Dispatchers.IO) {
        known[url]?.let { return@withContext it }
        val ok = try {
            (URL(url).openConnection() as HttpURLConnection).run {
                requestMethod = "HEAD"; connectTimeout = 8000; readTimeout = 8000
                val code = responseCode
                disconnect()
                code in 200..399
            }
        } catch (_: Exception) { false }
        known[url] = ok
        ok
    }
}
