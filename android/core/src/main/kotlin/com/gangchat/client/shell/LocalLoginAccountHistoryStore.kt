package com.gangchat.client.shell

import com.gangchat.client.app.LoginAccountHistoryStore
import com.gangchat.client.app.LoginAccountRecord
import com.gangchat.client.app.loginAccountKey
import com.gangchat.client.app.normalizeLoginAccount
import com.gangchat.client.app.normalizeLoginAccountHistory
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.asJsonMap
import com.gangchat.client.foundation.base64UrlEncode
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.foundation.utf8Encode
import com.gangchat.client.plugins.FlutterSecureStorage
import com.gangchat.client.plugins.SharedPreferences

class LocalLoginAccountHistoryStore : LoginAccountHistoryStore() {
    // Dart passed macOS keychain options here; they do not apply on Android.
    private val _storage: FlutterSecureStorage get() = FlutterSecureStorage()

    override suspend fun read(): List<LoginAccountRecord> {
        val prefs = SharedPreferences.getInstance()
        val metadata = _readMetadata(prefs)
        val records = mutableListOf<LoginAccountRecord>()
        for (item in metadata) {
            val password =
                if (item.remembersPassword) {
                    _storage.read(_passwordKey(item.login))
                } else {
                    null
                }
            records.add(
                LoginAccountRecord(
                    login = item.login,
                    password = if (password == null || password.isEmpty()) null else password,
                    avatarUrl = item.avatarUrl,
                    defaultAvatarKey = item.defaultAvatarKey,
                    useCount = item.useCount,
                    updatedAt = item.updatedAt,
                ),
            )
        }
        return normalizeLoginAccountHistory(records)
    }

    override suspend fun write(records: List<LoginAccountRecord>) {
        val prefs = SharedPreferences.getInstance()
        val oldMetadata = _readMetadata(prefs)
        val normalized = normalizeLoginAccountHistory(records)
        val activePasswordKeys = LinkedHashSet<String>()

        for (record in normalized) {
            val key = _passwordKey(record.login)
            activePasswordKeys.add(key)
            if (record.remembersPassword) {
                _storage.write(key, record.password)
            } else {
                _storage.delete(key)
            }
        }

        for (item in oldMetadata) {
            val key = _passwordKey(item.login)
            if (!activePasswordKeys.contains(key)) {
                _storage.delete(key)
            }
        }

        if (normalized.isEmpty()) {
            prefs.remove(_accountsKey)
            return
        }
        prefs.setString(
            _accountsKey,
            jsonEncode(
                normalized.map { record ->
                    linkedMapOf<String, Any?>(
                        "login" to record.login,
                        "updatedAt" to record.updatedAt.toIso8601String(),
                        "remembersPassword" to record.remembersPassword,
                        "avatarUrl" to record.avatarUrl,
                        "defaultAvatarKey" to record.defaultAvatarKey,
                        "useCount" to record.useCount,
                    )
                },
            ),
        )
    }

    private fun _readMetadata(prefs: SharedPreferences): List<_LoginAccountMetadata> {
        val encoded = prefs.getString(_accountsKey)
        if (encoded == null || encoded.isEmpty()) return emptyList()
        return try {
            val decoded = jsonDecode(encoded)
            if (decoded !is List<*>) return emptyList()
            decoded
                .filterIsInstance<Map<*, *>>()
                .map { json -> _LoginAccountMetadata.fromJson(json.asJsonMap()) }
                .filterIsInstance<_LoginAccountMetadata>()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun _passwordKey(login: String): String {
        val key = base64UrlEncode(utf8Encode(loginAccountKey(login)))
        return "$_passwordKeyPrefix$key"
    }

    private companion object {
        const val _accountsKey = "gang.loginAccounts"
        const val _passwordKeyPrefix = "gang.loginAccountPassword."
    }
}

private class _LoginAccountMetadata(
    val login: String,
    val updatedAt: DateTime,
    val remembersPassword: Boolean,
    val avatarUrl: String?,
    val defaultAvatarKey: String?,
    val useCount: Int,
) {
    companion object {
        // The `as String?` casts throw on a wrong type, like Dart's, and
        // _readMetadata then drops the whole list.
        fun fromJson(json: Map<String, Any?>): _LoginAccountMetadata? {
            val login = json["login"] as String?
            if (login == null || normalizeLoginAccount(login).isEmpty()) return null
            val updatedAtText = json["updatedAt"] as String?
            return _LoginAccountMetadata(
                login = normalizeLoginAccount(login),
                updatedAt =
                    DateTime.tryParse(updatedAtText ?: "")
                        ?: DateTime.fromMillisecondsSinceEpoch(0),
                remembersPassword = json["remembersPassword"] == true,
                avatarUrl = _nonEmptyString(json["avatarUrl"] as String?),
                defaultAvatarKey = _nonEmptyString(json["defaultAvatarKey"] as String?),
                useCount = _positiveInt(json["useCount"]),
            )
        }
    }
}

private fun _nonEmptyString(value: String?): String? {
    val trimmed = value?.trim()
    return if (trimmed == null || trimmed.isEmpty()) null else trimmed
}

private fun _positiveInt(value: Any?): Int {
    // Decoded JSON integers are Long (Dart `int`), fractions Double (`num`).
    if (value is Long && value > 0) return value.toInt()
    if (value is Number && value.toDouble() > 0) return value.toInt()
    if (value is String) {
        // Dart's int.tryParse ignores surrounding whitespace.
        val parsed = value.trim().toLongOrNull()
        if (parsed != null && parsed > 0) return parsed.toInt()
    }
    return 1
}
