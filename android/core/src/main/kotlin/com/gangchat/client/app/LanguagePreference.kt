package com.gangchat.client.app

import com.gangchat.client.protocol.defaultUserLanguage

const val defaultLanguagePreference: String = defaultUserLanguage

fun normalizeLanguagePreference(language: String?): String {
    return when (language?.trim()) {
        "zh-Hans" -> "zh-Hans"
        "zh-Hant" -> "zh-Hant"
        "en" -> "en"
        else -> defaultLanguagePreference
    }
}

open class LanguagePreferenceStore {
    open suspend fun read(): String {
        throw NotImplementedError(
            "LanguagePreferenceStore.read must be implemented.",
        )
    }

    open suspend fun write(language: String) {
        throw NotImplementedError(
            "LanguagePreferenceStore.write must be implemented.",
        )
    }
}
