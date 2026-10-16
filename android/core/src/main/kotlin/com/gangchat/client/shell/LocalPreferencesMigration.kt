package com.gangchat.client.shell

import com.gangchat.client.app.defaultAudioVolume
import com.gangchat.client.foundation.VisibleForTesting
import com.gangchat.client.plugins.SharedPreferences
import kotlin.math.abs

/**
 * Migrates device-wide preferences from historical application identities.
 *
 * Windows derives its SharedPreferences directory from the executable's
 * CompanyName + ProductName metadata. Gang Chat used `client` as ProductName
 * before the visible product rename, so an in-place update appeared to reset
 * every preference even though the old JSON file was still present. Android's
 * application id and macOS's bundle id have remained stable and need no file
 * migration.
 */
class LocalPreferencesMigration {
    /**
     * PORT: the Dart method returns at once unless it runs on Windows
     * (`if (kIsWeb || !HostPlatform.isWindows) return;`); the Windows-only
     * legacy-file branch is not ported, so on Android this is a no-op.
     */
    suspend fun migrate() {}

    companion object {
        private const val _flutterStorageKeyPrefix = "flutter."
        private val _audioVolumeKeys: Set<String> =
            setOf(
                "gang.audioInputVolume",
                "gang.audioOutputVolume",
                "gang.musicBoxVolume",
                "gang.screenShareVolume",
            )

        @VisibleForTesting
        suspend fun migrateLegacyPreferences(
            preferences: SharedPreferences,
            legacyValues: Map<String, Any?>,
        ): Int {
            var migrated = 0
            for ((key, value) in legacyValues) {
                val storageKey =
                    if (key.startsWith(_flutterStorageKeyPrefix)) {
                        key.substring(_flutterStorageKeyPrefix.length)
                    } else {
                        key
                    }
                if (!_isSafeSharedPreferenceKey(storageKey)) {
                    continue
                }
                val hasCurrentValue = preferences.containsKey(storageKey)
                if (hasCurrentValue &&
                    !_shouldRecoverLegacyDefault(
                        storageKey,
                        preferences.get(storageKey),
                    )
                ) {
                    continue
                }
                if (_writeSupportedValue(preferences, storageKey, value)) {
                    migrated += 1
                }
            }
            return migrated
        }

        private fun _isSafeSharedPreferenceKey(key: String): Boolean {
            if (!key.startsWith("gang.")) return false
            // Authentication secrets and remembered passwords belong to
            // flutter_secure_storage and must never be copied into plaintext JSON.
            return key != "gang.refreshToken" &&
                !key.startsWith("gang.loginAccountPassword.")
        }

        private fun _shouldRecoverLegacyDefault(key: String, currentValue: Any?): Boolean {
            if (!_audioVolumeKeys.contains(key) || currentValue !is Number) return false
            return abs(currentValue.toDouble() - defaultAudioVolume) < 0.000001
        }

        private suspend fun _writeSupportedValue(
            preferences: SharedPreferences,
            key: String,
            value: Any?,
        ): Boolean {
            if (value is Boolean) return preferences.setBool(key, value)
            // Decoded JSON integers are Long (Dart's 64-bit `int`).
            if (value is Long) return preferences.setLong(key, value)
            if (value is Int) return preferences.setInt(key, value)
            if (value is Double) return preferences.setDouble(key, value)
            if (value is String) return preferences.setString(key, value)
            if (value is List<*> && value.all { item -> item is String }) {
                return preferences.setStringList(key, value.map { item -> item as String })
            }
            return false
        }
    }
}
