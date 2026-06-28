package com.magnatune.player.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Downloads and refreshes the catalog SQLite database (mirrors iOS CatalogSync).
 *
 * Flow: GET he3/info/changed.txt (a CRC). If it differs from the stored value (or no catalog
 * exists), download sqlite_normalized.db.gz, gunzip, sanity-check the SQLite header, and atomically
 * replace the on-disk catalog. A seed copy is bundled in assets so the app works on first launch.
 */
class CatalogSync(private val context: Context) {
    private val prefs = context.getSharedPreferences("catalog", Context.MODE_PRIVATE)

    companion object {
        const val CHANGED_URL = "http://he3.magnatune.com/info/changed.txt"
        const val DB_GZ_URL = "http://he3.magnatune.com/info/sqlite_normalized.db.gz"
        private const val CRC_KEY = "catalog.crc"
        private const val LAST_CHECK_KEY = "catalog.lastCheck"
        private const val DB_NAME = "magnatune_catalog.db"

        fun catalogFile(context: Context) = File(context.filesDir, DB_NAME)
    }

    fun catalogPath(): String = catalogFile(context).path

    /** Ensure a catalog exists at the target path, seeding from the bundled asset if needed. */
    fun ensureSeeded() {
        val target = catalogFile(context)
        if (target.exists()) return
        runCatching {
            context.assets.open("magnatune.db").use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
    }

    /** Check the CRC and refresh if changed. Throttled to once / 24h unless [force]. */
    suspend fun refreshIfNeeded(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!force) {
            val last = prefs.getLong(LAST_CHECK_KEY, 0)
            if (last > 0 && System.currentTimeMillis() - last < 24 * 3600_000L) return@withContext false
        }
        prefs.edit().putLong(LAST_CHECK_KEY, System.currentTimeMillis()).apply()

        val crc = fetchCrc() ?: return@withContext false
        val stored = prefs.getString(CRC_KEY, null)
        val haveFile = catalogFile(context).exists()
        if (!force && crc == stored && haveFile) return@withContext false

        if (downloadAndInstall()) {
            prefs.edit().putString(CRC_KEY, crc).apply()
            true
        } else false
    }

    /** Whether the server reports a newer CRC than the installed catalog (not throttled). */
    suspend fun updateAvailable(): Boolean = withContext(Dispatchers.IO) {
        val stored = prefs.getString(CRC_KEY, null) ?: return@withContext false
        val crc = fetchCrc() ?: return@withContext false
        crc != stored
    }

    private fun fetchCrc(): String? = runCatching {
        (URL(CHANGED_URL).openConnection() as HttpURLConnection).run {
            connectTimeout = 15000; readTimeout = 15000
            inputStream.bufferedReader().readText().trim()
        }
    }.getOrNull()

    private fun downloadAndInstall(): Boolean = runCatching {
        val staging = File(context.filesDir, "catalog_new.db")
        staging.delete()
        (URL(DB_GZ_URL).openConnection() as HttpURLConnection).run {
            connectTimeout = 20000; readTimeout = 60000
            GZIPInputStream(inputStream).use { gz -> staging.outputStream().use { gz.copyTo(it) } }
        }
        // sanity check: SQLite header (readNBytes is API 33+, so read manually)
        val header = ByteArray(15)
        val read = staging.inputStream().use { input ->
            var off = 0
            while (off < 15) { val n = input.read(header, off, 15 - off); if (n < 0) break; off += n }
            off
        }
        if (staging.length() < 100 || read < 15 || !header.contentEquals("SQLite format 3".toByteArray())) {
            staging.delete(); return false
        }
        val target = catalogFile(context)
        target.delete()
        staging.renameTo(target)
    }.getOrDefault(false)
}
