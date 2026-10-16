package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import java.util.Collections

const val loginAccountHistoryLimit = 8

fun normalizeLoginAccount(login: String): String = login.trim()

fun loginAccountKey(login: String): String =
    normalizeLoginAccount(login).lowercase()

fun loginAccountsMatch(first: String, second: String): Boolean {
    val firstKey = loginAccountKey(first)
    return firstKey.isNotEmpty() && firstKey == loginAccountKey(second)
}

class LoginAccountRecord(
    val login: String,
    val password: String? = null,
    val avatarUrl: String? = null,
    val defaultAvatarKey: String? = null,
    useCount: Int = 1,
    updatedAt: DateTime? = null,
) {
    val useCount: Int = if (useCount < 1) 1 else useCount
    val updatedAt: DateTime = updatedAt ?: DateTime.fromMillisecondsSinceEpoch(0)

    val remembersPassword: Boolean get() = password != null && password.isNotEmpty()

    fun copyWith(
        login: String? = null,
        password: String? = null,
        avatarUrl: String? = null,
        defaultAvatarKey: String? = null,
        useCount: Int? = null,
        clearPassword: Boolean = false,
        clearAvatarUrl: Boolean = false,
        clearDefaultAvatarKey: Boolean = false,
        updatedAt: DateTime? = null,
    ): LoginAccountRecord {
        return LoginAccountRecord(
            login = login ?: this.login,
            password = if (clearPassword) null else password ?: this.password,
            avatarUrl = if (clearAvatarUrl) null else avatarUrl ?: this.avatarUrl,
            defaultAvatarKey =
                if (clearDefaultAvatarKey) {
                    null
                } else {
                    defaultAvatarKey ?: this.defaultAvatarKey
                },
            useCount = useCount ?: this.useCount,
            updatedAt = updatedAt ?: this.updatedAt,
        )
    }
}

abstract class LoginAccountHistoryStore {
    abstract suspend fun read(): List<LoginAccountRecord>

    abstract suspend fun write(records: List<LoginAccountRecord>)
}

class NoopLoginAccountHistoryStore : LoginAccountHistoryStore() {
    override suspend fun read(): List<LoginAccountRecord> = emptyList()

    override suspend fun write(records: List<LoginAccountRecord>) {}
}

fun normalizeLoginAccountHistory(
    records: Iterable<LoginAccountRecord>,
    limit: Int = loginAccountHistoryLimit,
): List<LoginAccountRecord> {
    val byLogin = LinkedHashMap<String, LoginAccountRecord>()
    for (record in records) {
        val login = normalizeLoginAccount(record.login)
        val key = loginAccountKey(login)
        if (key.isEmpty()) continue
        val normalized =
            record.copyWith(
                login = login,
                clearPassword = !record.remembersPassword,
            )
        val existing = byLogin[key]
        if (existing == null || _compareLoginRecords(normalized, existing) < 0) {
            byLogin[key] = normalized
        }
    }

    val result = byLogin.values.toMutableList()
    result.sortWith(::_compareLoginRecords)
    if (result.size > limit) {
        result.subList(limit, result.size).clear()
    }
    return Collections.unmodifiableList(result)
}

fun findLoginAccountRecord(
    records: Iterable<LoginAccountRecord>,
    login: String,
): LoginAccountRecord? {
    for (record in records) {
        if (loginAccountsMatch(record.login, login)) return record
    }
    return null
}

fun lastLoginAccountRecord(records: Iterable<LoginAccountRecord>): LoginAccountRecord? {
    var latest: LoginAccountRecord? = null
    for (record in records) {
        if (normalizeLoginAccount(record.login).isEmpty()) continue
        if (latest == null ||
            record.updatedAt.isAfter(latest.updatedAt) ||
            (record.updatedAt == latest.updatedAt &&
                record.useCount > latest.useCount)
        ) {
            latest = record
        }
    }
    return latest
}

fun rememberLoginAccount(
    records: Iterable<LoginAccountRecord>,
    login: String,
    password: String,
    rememberPassword: Boolean,
    avatarUrl: String? = null,
    defaultAvatarKey: String? = null,
    updateAvatarMetadata: Boolean = false,
    now: DateTime? = null,
    limit: Int = loginAccountHistoryLimit,
): List<LoginAccountRecord> {
    val normalizedLogin = normalizeLoginAccount(login)
    if (normalizedLogin.isEmpty()) {
        return normalizeLoginAccountHistory(records, limit = limit)
    }

    val existing = findLoginAccountRecord(records, normalizedLogin)
    val applyAvatarMetadata =
        updateAvatarMetadata || avatarUrl != null || defaultAvatarKey != null
    val updated =
        LoginAccountRecord(
            login = normalizedLogin,
            password = if (rememberPassword && password.isNotEmpty()) password else null,
            avatarUrl =
                if (applyAvatarMetadata) {
                    _nonEmptyString(avatarUrl)
                } else {
                    existing?.avatarUrl
                },
            defaultAvatarKey =
                if (applyAvatarMetadata) {
                    _nonEmptyString(defaultAvatarKey)
                } else {
                    existing?.defaultAvatarKey
                },
            useCount = (existing?.useCount ?: 0) + 1,
            updatedAt = now ?: DateTime.now(),
        )

    return normalizeLoginAccountHistory(
        listOf(updated) +
            records.filter { record -> !loginAccountsMatch(record.login, normalizedLogin) },
        limit = limit,
    )
}

fun updateLoginAccountAvatarMetadata(
    records: Iterable<LoginAccountRecord>,
    accountAliases: Iterable<String>,
    avatarUrl: String?,
    defaultAvatarKey: String?,
    limit: Int = loginAccountHistoryLimit,
): List<LoginAccountRecord> {
    val aliasKeys =
        accountAliases
            .map(::loginAccountKey)
            .filter { key -> key.isNotEmpty() }
            .toSet()
    if (aliasKeys.isEmpty()) {
        return normalizeLoginAccountHistory(records, limit = limit)
    }

    val normalizedAvatarUrl = _nonEmptyString(avatarUrl)
    val normalizedDefaultAvatarKey = _nonEmptyString(defaultAvatarKey)
    return normalizeLoginAccountHistory(
        records.map { record ->
            if (!aliasKeys.contains(loginAccountKey(record.login))) {
                record
            } else {
                record.copyWith(
                    avatarUrl = normalizedAvatarUrl,
                    defaultAvatarKey = normalizedDefaultAvatarKey,
                    clearAvatarUrl = normalizedAvatarUrl == null,
                    clearDefaultAvatarKey = normalizedDefaultAvatarKey == null,
                )
            }
        },
        limit = limit,
    )
}

fun deleteLoginAccountRecord(
    records: Iterable<LoginAccountRecord>,
    login: String,
    limit: Int = loginAccountHistoryLimit,
): List<LoginAccountRecord> {
    return normalizeLoginAccountHistory(
        records.filter { record -> !loginAccountsMatch(record.login, login) },
        limit = limit,
    )
}

private fun _compareLoginRecords(first: LoginAccountRecord, second: LoginAccountRecord): Int {
    val useCount = second.useCount.compareTo(first.useCount)
    if (useCount != 0) return useCount
    val updatedAt = second.updatedAt.compareTo(first.updatedAt)
    if (updatedAt != 0) return updatedAt
    return loginAccountKey(first.login).compareTo(loginAccountKey(second.login))
}

private fun _nonEmptyString(value: String?): String? {
    val trimmed = value?.trim()
    return if (trimmed == null || trimmed.isEmpty()) null else trimmed
}
