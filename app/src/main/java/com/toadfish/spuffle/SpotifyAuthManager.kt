package com.toadfish.spuffle

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Handles Spotify OAuth 2.0 with PKCE (the required auth flow)
 */
object SpotifyAuthManager {

    private const val PREFS_NAME = "spuffle_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_TOKEN_EXPIRY = "token_expiry"
    private const val KEY_CODE_VERIFIER = "code_verifier"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- PKCE helpers ---

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    // --- Token storage ---

    fun saveTokens(context: Context, accessToken: String, refreshToken: String, expiresIn: Int) {
        prefs(context).edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + (expiresIn * 1000L))
            apply()
        }
    }

    fun getAccessToken(context: Context): String? = prefs(context).getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(context: Context): String? = prefs(context).getString(KEY_REFRESH_TOKEN, null)

    fun isTokenExpired(context: Context): Boolean {
        val expiry = prefs(context).getLong(KEY_TOKEN_EXPIRY, 0)
        return System.currentTimeMillis() > expiry - 60_000 // refresh 1 min early
    }

    fun isLoggedIn(context: Context): Boolean = getAccessToken(context) != null

    fun clearTokens(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun saveCodeVerifier(context: Context, verifier: String) {
        prefs(context).edit().putString(KEY_CODE_VERIFIER, verifier).apply()
    }

    fun getCodeVerifier(context: Context): String? = prefs(context).getString(KEY_CODE_VERIFIER, null)

    // --- Launch OAuth in Chrome Custom Tab ---

    fun launchAuthFlow(context: Context) {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        saveCodeVerifier(context, verifier)

        val authUrl = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", Constants.CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", Constants.REDIRECT_URI)
            .appendQueryParameter("scope", Constants.SCOPES)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()

        CustomTabsIntent.Builder().build().launchUrl(context, authUrl)
    }
}