package com.samyak.repostore.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure token storage using EncryptedSharedPreferences.
 * Provides encrypted storage for sensitive data like OAuth tokens.
 * 
 * Features:
 * - AES-256 encryption for keys and values
 * - Automatic migration from legacy plain SharedPreferences
 * - Thread-safe singleton access
 */
object SecureTokenStorage {

    private const val TAG = "SecureTokenStorage"
    private const val ENCRYPTED_PREFS_NAME = "github_auth_secure"
    private const val LEGACY_PREFS_NAME = "github_auth"
    
    // Keys
    private const val KEY_TOKEN = "access_token"
    private const val KEY_USER_LOGIN = "user_login"
    private const val KEY_USER_AVATAR = "user_avatar"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_MIGRATED = "migrated_from_legacy"

    @Volatile
    private var encryptedPrefs: SharedPreferences? = null

    /**
     * Get or create the encrypted SharedPreferences instance.
     * Also handles migration from legacy unencrypted prefs.
     */
    fun getEncryptedPrefs(context: Context): SharedPreferences {
        return encryptedPrefs ?: synchronized(this) {
            encryptedPrefs ?: createEncryptedPrefs(context).also { 
                encryptedPrefs = it
                migrateFromLegacyIfNeeded(context, it)
            }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted prefs, falling back to regular prefs", e)
            // Fallback to regular SharedPreferences if encryption fails
            context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Migrate tokens from legacy unencrypted SharedPreferences.
     * This ensures existing users don't lose their login.
     */
    private fun migrateFromLegacyIfNeeded(context: Context, encryptedPrefs: SharedPreferences) {
        if (encryptedPrefs.getBoolean(KEY_MIGRATED, false)) {
            return // Already migrated
        }

        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyToken = legacyPrefs.getString(KEY_TOKEN, null)

        if (legacyToken != null) {
            Log.d(TAG, "Migrating token from legacy storage to encrypted storage")
            
            encryptedPrefs.edit().apply {
                putString(KEY_TOKEN, legacyToken)
                putString(KEY_USER_LOGIN, legacyPrefs.getString(KEY_USER_LOGIN, null))
                putString(KEY_USER_AVATAR, legacyPrefs.getString(KEY_USER_AVATAR, null))
                putString(KEY_USER_NAME, legacyPrefs.getString(KEY_USER_NAME, null))
                putBoolean(KEY_MIGRATED, true)
                apply()
            }

            // Clear legacy prefs after successful migration
            legacyPrefs.edit().clear().apply()
            Log.d(TAG, "Migration complete, legacy prefs cleared")
        } else {
            // No legacy data, just mark as migrated
            encryptedPrefs.edit().putBoolean(KEY_MIGRATED, true).apply()
        }
    }

    // Token operations
    fun saveToken(context: Context, token: String) {
        getEncryptedPrefs(context).edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(context: Context): String? {
        return getEncryptedPrefs(context).getString(KEY_TOKEN, null)
    }

    fun isSignedIn(context: Context): Boolean {
        return getToken(context) != null
    }

    // User info operations
    fun saveUser(context: Context, login: String, avatarUrl: String?, name: String?) {
        getEncryptedPrefs(context).edit().apply {
            putString(KEY_USER_LOGIN, login)
            putString(KEY_USER_AVATAR, avatarUrl)
            putString(KEY_USER_NAME, name)
            apply()
        }
    }

    fun getUserLogin(context: Context): String? {
        return getEncryptedPrefs(context).getString(KEY_USER_LOGIN, null)
    }

    fun getUserAvatar(context: Context): String? {
        return getEncryptedPrefs(context).getString(KEY_USER_AVATAR, null)
    }

    fun getUserName(context: Context): String? {
        return getEncryptedPrefs(context).getString(KEY_USER_NAME, null)
    }

    // Sign out
    fun signOut(context: Context) {
        getEncryptedPrefs(context).edit().apply {
            remove(KEY_TOKEN)
            remove(KEY_USER_LOGIN)
            remove(KEY_USER_AVATAR)
            remove(KEY_USER_NAME)
            apply()
        }
    }

    /**
     * Clear all data including migration flag.
     * Use with caution - mainly for testing.
     */
    fun clearAll(context: Context) {
        getEncryptedPrefs(context).edit().clear().apply()
    }
}
