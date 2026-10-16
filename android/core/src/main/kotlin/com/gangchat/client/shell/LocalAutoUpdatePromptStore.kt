package com.gangchat.client.shell

import com.gangchat.client.app.AutoUpdatePromptStore
import com.gangchat.client.app.defaultAutoUpdatePromptEnabled
import com.gangchat.client.plugins.SharedPreferences

class LocalAutoUpdatePromptStore : AutoUpdatePromptStore() {
    override suspend fun read(): Boolean {
        val prefs = SharedPreferences.getInstance()
        return prefs.getBool(_autoUpdatePromptKey) ?: defaultAutoUpdatePromptEnabled
    }

    override suspend fun write(enabled: Boolean) {
        val prefs = SharedPreferences.getInstance()
        prefs.setBool(_autoUpdatePromptKey, enabled)
    }

    override suspend fun readIgnoredVersion(): String? {
        val prefs = SharedPreferences.getInstance()
        val version = prefs.getString(_ignoredVersionKey)?.trim()
        if (version == null || version.isEmpty()) return null
        return version
    }

    override suspend fun writeIgnoredVersion(version: String?) {
        val prefs = SharedPreferences.getInstance()
        val normalized = version?.trim()
        if (normalized == null || normalized.isEmpty()) {
            prefs.remove(_ignoredVersionKey)
            return
        }
        prefs.setString(_ignoredVersionKey, normalized)
    }

    private companion object {
        const val _autoUpdatePromptKey = "gang.autoUpdatePrompt"
        const val _ignoredVersionKey = "gang.autoUpdateIgnoredVersion"
    }
}
