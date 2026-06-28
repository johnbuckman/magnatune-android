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

/**
 * Stores the Magnatune membership login in EncryptedSharedPreferences (never plaintext/source).
 * Membership is verified once at launch and on credential change — never per media request; he3
 * serves the right file with no auth (member → clean `.m4a`, non-member → `_spoken.m4a`).
 * Mirrors the iOS Credentials class.
 */
class Credentials(context: Context) {

    enum class MembershipStatus { MEMBER, NOT_MEMBER, UNREACHABLE }

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

    fun password(): String? = prefs.getString("password", null)?.takeIf { it.isNotEmpty() }
    fun hasPassword(): Boolean = !password().isNullOrEmpty()

    /** Save after a successful server verify (Settings sign-in flow only). */
    fun save(username: String, password: String) {
        _username.value = username
        prefs.edit().putString("username", username).putString("password", password).apply()
        _isMember.value = username.isNotEmpty() && password.isNotEmpty()
    }

    fun clear() {
        _username.value = ""
        prefs.edit().remove("username").remove("password").apply()
        _isMember.value = false
    }

    /** Re-verify stored membership; a network blip leaves the previous status untouched. */
    suspend fun refreshMembership() {
        val user = _username.value
        val pw = password()
        if (user.isEmpty() || pw.isNullOrEmpty()) { _isMember.value = false; return }
        when (membershipStatus(user, pw)) {
            MembershipStatus.MEMBER -> _isMember.value = true
            MembershipStatus.NOT_MEMBER -> _isMember.value = false
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
        /** 200 ⇒ MEMBER, other ⇒ NOT_MEMBER, network failure ⇒ UNREACHABLE. */
        suspend fun membershipStatus(username: String, password: String): MembershipStatus =
            withContext(Dispatchers.IO) {
                if (username.isEmpty() || password.isEmpty()) return@withContext MembershipStatus.NOT_MEMBER
                try {
                    val conn = URL("http://download.magnatune.com/buy/membership_free_dl_xml")
                        .openConnection() as HttpURLConnection
                    val token = Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Authorization", "Basic $token")
                    conn.connectTimeout = 20000; conn.readTimeout = 20000
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code == 200) MembershipStatus.MEMBER else MembershipStatus.NOT_MEMBER
                } catch (_: Exception) {
                    MembershipStatus.UNREACHABLE
                }
            }

        suspend fun verify(username: String, password: String): Boolean =
            membershipStatus(username, password) == MembershipStatus.MEMBER
    }
}
