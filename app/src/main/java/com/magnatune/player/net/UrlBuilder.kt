package com.magnatune.player.net

import android.net.Uri
import com.magnatune.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Member streaming quality tier (mirrors the web player's Normal/High tiers). Members only — the
 *  free stream is always the `_spoken` announcement file. Maps to a filename suffix + extension on
 *  the no-voice stem: Normal = 185k AAC (`.m4a`, universal), High = 192k Opus (`_hi.opus`). */
enum class StreamQuality(val key: String, val label: String, val detail: String,
                         val memberSuffix: String, val memberExt: String) {
    NORMAL("normal", "Normal", "185 kbps AAC", "", ".m4a"),
    HIGH("high", "High", "192 kbps Opus", "_hi", ".opus");

    companion object {
        const val DEFAULTS_KEY = "stream.quality"
        fun from(key: String?): StreamQuality = when (key) {
            // Legacy stored value: the old "Lossless" (256k AAC) tier was retired server-side and
            // replaced by 192k Opus — carry those users over to High rather than dropping to Normal.
            "lossless" -> HIGH
            else -> entries.firstOrNull { it.key == key } ?: NORMAL
        }
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
 * Builds all Magnatune URLs (streams, cover art, artist photos, downloads).
 *
 * Everything is now served SAME-ORIGIN over HTTPS by navim4's `/music/` handler on magnatune.com
 * (the retired `he3.magnatune.com` / `download.magnatune.com` hosts are gone). Cover art, notes and
 * `_spoken` advert audio are free; clean member audio and album `.zip` downloads are gated behind
 * HTTP Basic auth (see [com.magnatune.player.data.Credentials.basicAuthHeader]).
 */
object UrlBuilder {
    const val HOST = "magnatune.com"

    private fun media(path: String): String =
        Uri.Builder().scheme("https").authority(HOST)
            .appendEncodedPath(path.trimStart('/')).build().toString()

    private fun stem(mp3: String): String = if (mp3.endsWith(".mp3")) mp3.dropLast(4) else mp3

    /** Audio stream. Members → the no-announcement file at the chosen quality (`<stem>.m4a` 185k AAC
     *  / `<stem>_hi.opus` 192k Opus); non-members → the free `<stem>_spoken.m4a` advert stream.
     *  Member files require HTTP Basic auth on magnatune.com; the advert file is free. */
    fun streamUrl(artistName: String, albumName: String, song: Song,
                  isMember: Boolean, quality: StreamQuality = StreamQuality.NORMAL): String {
        val file = if (isMember) "${stem(song.mp3)}${quality.memberSuffix}${quality.memberExt}"
                   else "${stem(song.mp3)}_spoken.m4a"
        return media("/music/$artistName/$albumName/$file")
    }

    /** Album cover thumbnail. Sizes: 50,75,100,150,200,300,400,600,800,1400 (jpg). Free (no auth). */
    fun coverUrl(artistName: String, albumName: String, size: Int): String =
        media("/music/$artistName/$albumName/cover_$size.jpg")

    /** Sized artist thumbnail in an album dir (artist_<N>.jpg: 50,200,420,840) — tiny, preferred. */
    fun artistPhotoUrl(artistName: String, albumName: String, size: Int): String =
        media("/music/$artistName/$albumName/artist_$size.jpg")

    /** Full-resolution original from `artists.photo` (large) — fallback only. */
    fun artistPhotoOriginal(photo: String?): String? =
        photo?.takeIf { it.isNotEmpty() }?.let { media(it) }

    /** Whole-album download via the same-origin membership endpoint (member-auth handled by the
     *  browser tab that opens it). */
    fun albumMembershipDownloadUrl(sku: String, format: String): String =
        Uri.Builder().scheme("https").authority(HOST).path("/membership/download3")
            .appendQueryParameter("sku", sku).appendQueryParameter("format", format).build().toString()

    /** Single-song open download (ext ∈ mp3/ogg/wav/flac/m4a). Member-gated by the /music handler. */
    fun songDownloadUrl(artistName: String, albumName: String, song: Song, ext: String): String =
        media("/music/$artistName/$albumName/${stem(song.mp3)}.$ext")

    /**
     * Best stream URL, transparently falling back from High (192k `_hi.opus`) to the Normal member
     * AAC when the Opus file isn't on the server (album not yet re-encoded). Only High members
     * trigger a HEAD probe; Normal / non-member URLs always exist so they're returned directly.
     * [authHeader] is the member's HTTP Basic credential — required for the probe to see the
     * member-gated file (a probe without it would 401 and always fall back).
     */
    suspend fun resolvedStreamUrl(artistName: String, albumName: String, song: Song,
                                  isMember: Boolean, quality: StreamQuality,
                                  authHeader: String?): String {
        if (!isMember || quality != StreamQuality.HIGH) {
            return streamUrl(artistName, albumName, song, isMember, quality)
        }
        val hi = streamUrl(artistName, albumName, song, true, StreamQuality.HIGH)
        val normal = streamUrl(artistName, albumName, song, true, StreamQuality.NORMAL)
        return if (StreamProbe.exists(hi, authHeader)) hi else normal
    }
}

/** Session cache of which High (`_hi.opus`) URLs exist, so we fall back without re-probing. */
object StreamProbe {
    private val known = HashMap<String, Boolean>()

    suspend fun exists(url: String, authHeader: String?): Boolean = withContext(Dispatchers.IO) {
        known[url]?.let { return@withContext it }
        val ok = try {
            (URL(url).openConnection() as HttpURLConnection).run {
                requestMethod = "HEAD"; connectTimeout = 8000; readTimeout = 8000
                authHeader?.let { setRequestProperty("Authorization", it) }
                val code = responseCode
                disconnect()
                code in 200..399
            }
        } catch (_: Exception) { false }
        known[url] = ok
        ok
    }
}
