package com.gangchat.client.app

import com.gangchat.client.config.AppConfig
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.kDebugMode
import kotlin.time.Duration.Companion.hours

// PORT: Dart read these as compile-time constants (`--dart-define`, the
// `dart.vm.product` / `dart.vm.profile` flags, and a timestamp the release
// tool writes into the source). Here they come from `AppConfig.environment`
// and `kDebugMode`, which the app fills from `BuildConfig` at startup, so they
// are read-only properties instead of `const`. An empty define counts as
// unset, like the empty `BuildConfig` fields the build writes for unset
// variables.
private val _gangChatClientBuildVersion: String
    get() = AppConfig.environment["GANG_CHAT_VERSION"]?.takeIf { it.isNotEmpty() } ?: "1.0.0"

private val _gangChatClientDebugBuild: Boolean get() = kDebugMode

val gangChatClientVersion: String
    get() = if (_gangChatClientDebugBuild) "1.0.0" else _gangChatClientBuildVersion

val gangChatClientReleaseTimestamp: String
    get() =
        AppConfig.environment["GANG_CHAT_RELEASE_TIMESTAMP"]?.takeIf { it.isNotEmpty() }
            ?: "2026-07-12T20:34:27.126Z"

const val gangChatOfficialTimeZoneLabel = "UTC+08:00"
const val gangChatClientInstallInfoFileName = "gang_chat_install_info.txt"
const val gangChatSupportEmail = "gang-chat@outlook.com"
const val defaultAutoUpdatePromptEnabled = true

fun gangChatOfficialTime(value: DateTime): DateTime {
    return value.toUtc().add(8.hours)
}

fun officialDateTimeLabel(value: DateTime?): String {
    if (value == null) return "暂无"
    val official = gangChatOfficialTime(value)
    val year = official.year.toString().padStart(4, '0')
    val month = official.month.toString().padStart(2, '0')
    val day = official.day.toString().padStart(2, '0')
    val hour = official.hour.toString().padStart(2, '0')
    val minute = official.minute.toString().padStart(2, '0')
    return "$year/$month/$day $hour:$minute $gangChatOfficialTimeZoneLabel"
}

fun officialVersionTimeLabel(
    value: String?,
    fallback: String = gangChatClientReleaseTimestamp,
): String {
    val timestamp = DateTime.tryParse(value?.trim() ?: "")
    val fallbackTimestamp = DateTime.tryParse(fallback)
    return officialDateTimeLabel(timestamp ?: fallbackTimestamp)
}

fun installTimeLabel(
    value: String?,
    fallback: String = gangChatClientReleaseTimestamp,
): String {
    val normalized = value?.trim() ?: ""
    val timestamp = DateTime.tryParse(normalized)
    if (timestamp != null) return localDateTimeLabel(timestamp)
    if (_legacyInstallDatePattern.matches(normalized)) {
        return normalized
    }
    return localDateTimeLabel(DateTime.tryParse(fallback))
}

fun localDateTimeLabel(value: DateTime?): String {
    if (value == null) return "暂无"
    val local = value.toLocal()
    val year = local.year.toString().padStart(4, '0')
    val month = local.month.toString().padStart(2, '0')
    val day = local.day.toString().padStart(2, '0')
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "$year/$month/$day $hour:$minute"
}

fun appVersionLabel(version: String): String {
    val normalized = version.trim()
    if (normalized.isEmpty()) return "v0.0.0"
    return if (normalized.startsWith("v")) normalized else "v$normalized"
}

fun appVersionNumberLabel(version: String): String {
    val normalized = version.trim()
    if (normalized.isEmpty()) return "0.0.0"
    return if (normalized.startsWith("v")) normalized.substring(1) else normalized
}

fun updateCheckSucceededText(
    currentVersion: String,
    latestVersion: String,
): String {
    val comparison = compareAppVersions(currentVersion, latestVersion)
    if (comparison >= 0) return "当前已是最新版本"
    return "发现新版本 ${appVersionLabel(latestVersion)}"
}

fun feedbackMailSubject(version: String): String {
    return "Gang Chat 意见反馈 (${appVersionLabel(version)})"
}

fun feedbackMailBody(
    senderEmail: String,
    currentVersion: String,
): String {
    return listOf(
        "发件人（绑定邮箱）：$senderEmail",
        "当前版本：${appVersionLabel(currentVersion)}",
        "",
        "请在这里填写你的意见反馈：",
    ).joinToString("\n")
}

fun boundEmailForFeedback(email: String?): String? {
    val trimmed = email?.trim()
    if (trimmed == null || trimmed.isEmpty()) return null
    return trimmed
}

open class AutoUpdatePromptStore {
    open suspend fun read(): Boolean {
        throw NotImplementedError("AutoUpdatePromptStore.read must be implemented.")
    }

    open suspend fun write(enabled: Boolean) {
        throw NotImplementedError(
            "AutoUpdatePromptStore.write must be implemented.",
        )
    }

    open suspend fun readIgnoredVersion(): String? {
        throw NotImplementedError(
            "AutoUpdatePromptStore.readIgnoredVersion must be implemented.",
        )
    }

    open suspend fun writeIgnoredVersion(version: String?) {
        throw NotImplementedError(
            "AutoUpdatePromptStore.writeIgnoredVersion must be implemented.",
        )
    }
}

fun compareAppVersions(left: String, right: String): Int {
    val leftParts = _versionParts(left)
    val rightParts = _versionParts(right)
    val length =
        if (leftParts.size > rightParts.size) {
            leftParts.size
        } else {
            rightParts.size
        }
    for (index in 0 until length) {
        val leftValue = if (index < leftParts.size) leftParts[index] else 0L
        val rightValue = if (index < rightParts.size) rightParts[index] else 0L
        if (leftValue != rightValue) return leftValue.compareTo(rightValue)
    }
    return 0
}

// Matched against the whole string: Java's `$` would also match before a
// trailing line break, Dart's does not.
private val _legacyInstallDatePattern = Regex("\\d{4}/\\d{2}/\\d{2}")

// `\z` for the same reason as above.
private val _versionSuffixPattern = Regex("[^0-9].*\\z")

/** Version parts as Dart's 64-bit `int`s. */
private fun _versionParts(version: String): List<Long> {
    val withoutBuild = version.trim().split("+").first()
    val withoutPrefix =
        if (withoutBuild.startsWith("v")) {
            withoutBuild.substring(1)
        } else {
            withoutBuild
        }
    return withoutPrefix
        .split(".")
        .map { part -> part.replace(_versionSuffixPattern, "").toLongOrNull() }
        .map { part -> part ?: 0L }
}
