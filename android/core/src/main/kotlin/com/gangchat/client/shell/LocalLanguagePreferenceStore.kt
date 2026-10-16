package com.gangchat.client.shell

import com.gangchat.client.app.LanguagePreferenceStore
import com.gangchat.client.app.normalizeLanguagePreference
import com.gangchat.client.plugins.SharedPreferences

class LocalLanguagePreferenceStore : LanguagePreferenceStore() {
    override suspend fun read(): String {
        val prefs = SharedPreferences.getInstance()
        return normalizeLanguagePreference(prefs.getString(_languageKey))
    }

    override suspend fun write(language: String) {
        val prefs = SharedPreferences.getInstance()
        prefs.setString(_languageKey, normalizeLanguagePreference(language))
    }

    private companion object {
        const val _languageKey = "gang.language"
    }
}
