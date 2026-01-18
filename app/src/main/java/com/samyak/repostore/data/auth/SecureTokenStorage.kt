package com.samyak.repostore.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

/**
 * Token storage using SharedPreferences with basic obfuscation.
 * F-Droid compatible - no external dependencies that trigger tracker flags.
 * 
 * Note: For a FOSS app, using regular SharedPreferences is acceptable.
 * The token is only used for API rate limits, not for accessing private data.
 */
object SecureTokenStorage {

    private const val TAG = "SecureTokenStorage"
    private const val PREFS_NAME = "github_auth_storage"
    
    // Keys
    private const val KEY_TOKEN = "at_v1"
    private const val KEY_USER_LOGIN = "user_login"
    private const val KEY_USER_AVATAR = "user_avatar"
    private const val KEY_USER_NAME = "user_name"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Simple obfuscation (base64) - not true encryption, but hides plain text
    private fun encode(value: String): String {
        return Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun decode(value: String): String {
        return String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)
    }

    // Token operations
    fun saveToken(context: Context, token: String) {
        getPrefs(context).edit().putString(KEY_TOKEN, encode(token)).apply()
    }

    fun getToken(context: Context): String? {
        val encoded = getPrefs(context).getString(KEY_TOKEN, null)
        return encoded?.let {
            try {
                decode(it)
            } catch (e: Exception) {
                // If decoding fails, might be old plain token, return as-is
                it
            }
        }
    }

    fun isSignedIn(context: Context): Boolean {
        return getToken(context) != null
    }

    // User info operations
    fun saveUser(context: Context, login: String, avatarUrl: String?, name: String?) {
        getPrefs(context).edit().apply {
            putString(KEY_USER_LOGIN, login)
            putString(KEY_USER_AVATAR, avatarUrl)
            putString(KEY_USER_NAME, name)
            apply()
        }
    }

    fun getUserLogin(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_LOGIN, null)
    }

    fun getUserAvatar(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_AVATAR, null)
    }

    fun getUserName(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_NAME, null)
    }

    // Sign out
    fun signOut(context: Context) {
        getPrefs(context).edit().apply {
            remove(KEY_TOKEN)
            remove(KEY_USER_LOGIN)
            remove(KEY_USER_AVATAR)
            remove(KEY_USER_NAME)
            apply()
        }
    }

    /**
     * Clear all data.
     */
    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
