package com.magnatune.player.data

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Stores the Magnatune membership login in EncryptedSharedPreferences (never plaintext/source).
 * Membership is verified at launch and on credential change (against the same-origin check.php
 * endpoint). The stored credential is also sent as an HTTP Basic header on every member media
 * request — magnatune.com now gates the no-announcement / high-quality files (member → clean
 * `.m4a`/`_hi.opus`, non-member → the free `_spoken.m4a`). Mirrors the iOS Credentials class.
 */
class Credentials(context: Context) {

    enum class MembershipStatus { MEMBER, NOT_MEMBER, UNREACHABLE }

    /** check.php verdict: [error] is the server's user-facing message (if any), [reason] its
     *  machine code — e.g. `"ended"` for a lapsed membership. */
    data class MembershipResult(
        val status: MembershipStatus,
        val error: String? = null,
        val reason: String? = null,
    )

    private val prefs = run {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "membership", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _username = MutableStateFlow(prefs.getString("username", "") ?: "")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _isMember = MutableStateFlow(
        !(_username.value.isEmpty()) && !(password().isNullOrEmpty())
    )
    /** Optimistic at init from stored creds; the launch re-verify downgrades an expired membership. */
    val isMember: StateFlow<Boolean> = _isMember.asStateFlow()

    private val _membershipMessage = MutableStateFlow<String?>(null)
    /** Server message from the launch re-verify when the stored membership has ended (shown in
     *  Settings → Membership); null otherwise. The credentials are kept so they can rejoin. */
    val membershipMessage: StateFlow<String?> = _membershipMessage.asStateFlow()

    fun password(): String? = prefs.getString("password", null)?.takeIf { it.isNotEmpty() }
    fun hasPassword(): Boolean = !password().isNullOrEmpty()

    /** Save after a successful server verify (Settings sign-in flow only). */
    fun save(username: String, password: String) {
        _username.value = username
        prefs.edit().putString("username", username).putString("password", password).apply()
        _isMember.value = username.isNotEmpty() && password.isNotEmpty()
        _membershipMessage.value = null
    }

    fun clear() {
        _username.value = ""
        prefs.edit().remove("username").remove("password").apply()
        _isMember.value = false
        _membershipMessage.value = null
    }

    /** Re-verify stored membership; a network blip leaves the previous status untouched. */
    suspend fun refreshMembership() {
        val user = _username.value
        val pw = password()
        if (user.isEmpty() || pw.isNullOrEmpty()) { _isMember.value = false; return }
        val result = membershipStatus(user, pw)
        when (result.status) {
            MembershipStatus.MEMBER -> { _isMember.value = true; _membershipMessage.value = null }
            MembershipStatus.NOT_MEMBER -> {
                _isMember.value = false
                // Lapsed membership: keep the stored login (rejoining reuses it) and say why.
                _membershipMessage.value = if (result.reason == "ended")
                    result.error ?: "Your Magnatune membership has ended." else null
            }
            MembershipStatus.UNREACHABLE -> {}
        }
    }

    /** Authorization header for HTTP Basic auth, or null if not a member. */
    fun basicAuthHeader(): String? {
        val user = _username.value
        val pw = password() ?: return null
        if (user.isEmpty() || pw.isEmpty()) return null
        val token = Base64.encodeToString("$user:$pw".toByteArray(), Base64.NO_WRAP)
        return "Basic $token"
    }

    companion object {
        /** `{"ok":true}` ⇒ MEMBER, any other JSON ⇒ NOT_MEMBER (carrying the server's `error` /
         *  `reason`), network failure ⇒ UNREACHABLE. Uses the same-origin, purpose-built SPA
         *  endpoint (navim4 `m3_check_member`): POST the credentials as a form, read the tiny JSON
         *  verdict. Reuses the same MySQL membership check that gates the actual /music files, so
         *  the UI unlock matches what can be streamed. */
        suspend fun membershipStatus(username: String, password: String): MembershipResult =
            withContext(Dispatchers.IO) {
                if (username.isEmpty() || password.isEmpty()) return@withContext MembershipResult(MembershipStatus.NOT_MEMBER)
                try {
                    val conn = URL("https://magnatune.com/membership/check.php")
                        .openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    conn.connectTimeout = 20000; conn.readTimeout = 20000
                    val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
                    conn.outputStream.use { it.write("user=${enc(username)}&pw=${enc(password)}".toByteArray()) }
                    val code = conn.responseCode
                    val body = if (code in 200..299)
                        conn.inputStream.bufferedReader().use { it.readText() } else ""
                    conn.disconnect()
                    parseCheckResponse(code, body)
                } catch (_: Exception) {
                    MembershipResult(MembershipStatus.UNREACHABLE)
                }
            }

        /** Pure parse of a check.php reply, e.g. `{"ok":false,"reason":"ended","error":"…"}`. */
        fun parseCheckResponse(code: Int, body: String): MembershipResult {
            if (code != 200) return MembershipResult(MembershipStatus.NOT_MEMBER)
            val json = runCatching { org.json.JSONObject(body) }.getOrNull()
                ?: return MembershipResult(MembershipStatus.NOT_MEMBER)
            if (json.optBoolean("ok", false)) return MembershipResult(MembershipStatus.MEMBER)
            val str = { k: String -> json.optString(k, "").trim().takeIf { it.isNotEmpty() && !json.isNull(k) } }
            return MembershipResult(MembershipStatus.NOT_MEMBER, error = str("error"), reason = str("reason"))
        }

        suspend fun verify(username: String, password: String): MembershipResult =
            membershipStatus(username, password)
    }
}
