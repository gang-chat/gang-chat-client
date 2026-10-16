package com.gangchat.client.auth

import com.gangchat.client.plugins.FlutterSecureStorage
import com.gangchat.client.plugins.SharedPreferences

/**
 * Stores the auth refresh token and the last-used API base URL.
 *
 * The refresh token is sensitive (it grants a logged-in session), so it stays
 * in the OS keychain via flutter_secure_storage. The API base URL is not
 * sensitive and lives in SharedPreferences so reading it never triggers a
 * macOS keychain authorization prompt.
 *
 * Open like the Dart class, which tests subclass with in-memory fakes.
 */
open class TokenStore {
    private val _storage: FlutterSecureStorage get() = FlutterSecureStorage()

    open suspend fun readRefreshToken(): String? = _storage.read(_refreshTokenKey)

    open suspend fun writeRefreshToken(refreshToken: String) {
        _storage.write(_refreshTokenKey, refreshToken)
    }

    open suspend fun clearRefreshToken() {
        _storage.delete(_refreshTokenKey)
    }

    open suspend fun readApiBaseUrl(): String? {
        val prefs = SharedPreferences.getInstance()
        return prefs.getString(_apiBaseUrlKey)
    }

    open suspend fun writeApiBaseUrl(baseUrl: String) {
        val prefs = SharedPreferences.getInstance()
        prefs.setString(_apiBaseUrlKey, baseUrl)
    }

    companion object {
        private const val _refreshTokenKey = "gang.refreshToken"
        private const val _apiBaseUrlKey = "gang.apiBaseUrl"
    }
}
