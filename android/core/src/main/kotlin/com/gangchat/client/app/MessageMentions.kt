package com.gangchat.client.app

import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.UserSummary

const val defaultMessageMentionOptionLimit: Int = 8

class MessageMentionQuery(
    val start: Int,
    val end: Int,
    val query: String,
)

class MessageMentionRange(val start: Int, val end: Int)

enum class MessageMentionKind { user, everyone, admins, bot }

class MessageMentionOption(
    val member: RoomMember? = null,
    val label: String,
    val roleLabel: String,
    val kind: MessageMentionKind = MessageMentionKind.user,
) {
    val isUser: Boolean get() = kind == MessageMentionKind.user && member != null
}

fun activeMessageMentionQuery(
    text: String,
    cursorOffset: Int,
): MessageMentionQuery? {
    if (cursorOffset < 0 || cursorOffset > text.length) return null
    var atIndex = -1
    for (index in cursorOffset - 1 downTo 0) {
        val codeUnit = text[index].code
        if (text[index] == '@') {
            atIndex = index
            break
        }
        if (_isMentionTerminator(codeUnit)) break
    }
    if (atIndex < 0) return null
    val query = text.substring(atIndex + 1, cursorOffset)
    if (query.contains('@') || query.contains('\n') || query.contains('\r')) {
        return null
    }
    if (_looksLikeEmailAddress(text, atIndex = atIndex, end = cursorOffset)) {
        return null
    }
    return MessageMentionQuery(start = atIndex, end = cursorOffset, query = query)
}

fun messageMentionRanges(
    text: String,
    labels: Iterable<String> = emptyList(),
): List<MessageMentionRange> {
    val ranges = _messageMentionRangesForKnownLabels(text, labels)
    var index = 0
    while (index < text.length) {
        val atIndex = text.indexOf('@', index)
        if (atIndex < 0) break
        var end = atIndex + 1
        while (end < text.length && !_isMentionTerminator(text[end].code)) {
            if (text[end] == '@') break
            end++
        }
        if (_looksLikeEmailAddress(text, atIndex = atIndex, end = end)) {
            index = if (end <= atIndex) atIndex + 1 else end
            continue
        }
        val range = MessageMentionRange(start = atIndex, end = end)
        if (end > atIndex + 1 && !_overlapsAnyMentionRange(range, ranges)) {
            ranges.add(range)
        }
        index = if (end <= atIndex) atIndex + 1 else end
    }
    ranges.sortWith { a, b -> a.start.compareTo(b.start) }
    return ranges
}

fun messageMentionOptions(
    members: Iterable<RoomMember>,
    query: String,
    ownerUserId: String?,
    excludedUserId: String? = null,
    limit: Int = defaultMessageMentionOptionLimit,
    includeGangBot: Boolean = false,
): List<MessageMentionOption> {
    val normalizedQuery = query.trim().lowercase()
    val specialOptions =
        _specialMentionOptions(
            normalizedQuery,
            includeGangBot = includeGangBot,
        )
    if (specialOptions.size >= limit) {
        return specialOptions.take(limit)
    }
    val ranked = mutableListOf<_RankedMentionOption>()
    for (member in members) {
        if (excludedUserId != null && member.user.id == excludedUserId) {
            continue
        }
        val label = roomMemberDisplayName(member).trim()
        if (label.isEmpty()) continue
        val rank = _mentionSearchRank(member, label, normalizedQuery) ?: continue
        ranked.add(
            _RankedMentionOption(
                option =
                    MessageMentionOption(
                        member = member,
                        label = label,
                        roleLabel =
                            roomRoleLabel(
                                member.user,
                                ownerUserId = ownerUserId,
                            ),
                    ),
                rank = rank,
                roleRank =
                    roomMemberRoleRank(
                        member,
                        ownerUserId = ownerUserId,
                    ),
            ),
        )
    }
    ranked.sortWith { a, b ->
        val rankCompare = a.rank.compareTo(b.rank)
        if (rankCompare != 0) return@sortWith rankCompare
        val roleCompare = a.roleRank.compareTo(b.roleRank)
        if (roleCompare != 0) return@sortWith roleCompare
        a.option.label.lowercase().compareTo(b.option.label.lowercase())
    }
    return specialOptions +
        ranked
            .take((limit - specialOptions.size).coerceIn(0, limit))
            .map { entry -> entry.option }
}

fun messageMentionInsertText(option: MessageMentionOption): String = "@${option.label} "

fun messageMentionDescriptors(
    text: String,
    members: Iterable<RoomMember>,
    confirmedLabels: Iterable<String> = emptyList(),
): List<JsonMap> {
    val out = mutableListOf<JsonMap>()
    val seen = LinkedHashSet<String>()
    val labels =
        confirmedLabels
            .map { label -> label.trim() }
            .filter { label -> label.isNotEmpty() }
    if (labels.isNotEmpty()) {
        for (label in labels) {
            _addMentionDescriptorForLabel(
                out = out,
                seen = seen,
                label = label,
                members = members,
            )
        }
        return out
    }
    for (range in messageMentionRanges(text)) {
        _addMentionDescriptorForLabel(
            out = out,
            seen = seen,
            label = text.substring(range.start + 1, range.end),
            members = members,
        )
    }
    return out
}

private fun _addMentionDescriptorForLabel(
    out: MutableList<JsonMap>,
    seen: MutableSet<String>,
    label: String,
    members: Iterable<RoomMember>,
) {
    when (messageMentionKindForLabel(label)) {
        MessageMentionKind.everyone -> {
            if (seen.add("kind:all")) out.add(linkedMapOf("type" to "all", "label" to label))
        }
        MessageMentionKind.admins -> {
            if (seen.add("kind:admins")) {
                out.add(linkedMapOf("type" to "admins", "label" to label))
            }
        }
        MessageMentionKind.bot -> {
            if (seen.add("kind:bot")) out.add(linkedMapOf("type" to "bot", "label" to label))
        }
        MessageMentionKind.user -> {
            val member =
                resolveMessageMentionMember(
                    label = label,
                    members = members,
                )
            if (member != null && seen.add("user:${member.user.id}")) {
                out.add(linkedMapOf("type" to "user", "user_id" to member.user.id, "label" to label))
            }
        }
    }
}

fun messageMentionsUser(
    text: String,
    mentions: List<JsonMap> = emptyList(),
    user: UserSummary,
    ownerUserId: String?,
): Boolean {
    for (mention in mentions) {
        val type = ((mention["type"] as String?) ?: "").trim().lowercase()
        when (type) {
            "all" -> return true
            "admins" -> if (_userIsAdminLike(user, ownerUserId = ownerUserId)) return true
            "user" -> {
                val userId = mention["user_id"] as String?
                val uid = mention["uid"] as String?
                if (userId != null && userId == user.id) return true
                if (uid != null && uid == user.uid) return true
            }
        }
    }
    return messageMentionRanges(text).any { range ->
        val label = text.substring(range.start + 1, range.end)
        messageMentionLabelTargetsUser(
            label = label,
            user = user,
            ownerUserId = ownerUserId,
        )
    }
}

fun messageMentionLabelTargetsUser(
    label: String,
    user: UserSummary,
    ownerUserId: String?,
): Boolean {
    return when (messageMentionKindForLabel(label)) {
        MessageMentionKind.everyone -> true
        MessageMentionKind.admins -> _userIsAdminLike(user, ownerUserId = ownerUserId)
        MessageMentionKind.bot -> false
        MessageMentionKind.user -> {
            val normalized = _normalizeMentionLabel(label)
            _mentionLabelsForUser(user)
                .map(::_normalizeMentionLabel)
                .any { candidate -> candidate == normalized }
        }
    }
}

fun messageMentionKindForLabel(label: String): MessageMentionKind {
    val normalized = _normalizeMentionLabel(label)
    if (normalized == _normalizeMentionLabel(kMentionEveryoneLabel)) {
        return MessageMentionKind.everyone
    }
    if (normalized == _normalizeMentionLabel(kMentionAdminsLabel)) {
        return MessageMentionKind.admins
    }
    if (normalized == _normalizeMentionLabel(kMentionGangBotLabel)) {
        return MessageMentionKind.bot
    }
    return MessageMentionKind.user
}

fun resolveMessageMentionMember(
    label: String,
    members: Iterable<RoomMember>,
): RoomMember? {
    val normalized = _normalizeMentionLabel(label)
    for (member in members) {
        val labels = _mentionLabelsForMember(member).map(::_normalizeMentionLabel)
        if (labels.any { candidate -> candidate == normalized }) return member
    }
    return null
}

fun isMessageMentionRangeForUser(
    text: String,
    range: MessageMentionRange,
    mentions: List<JsonMap> = emptyList(),
    user: UserSummary,
    ownerUserId: String?,
): Boolean {
    if (range.start < 0 || range.end > text.length || range.start >= range.end) {
        return false
    }
    val label = text.substring(range.start + 1, range.end)
    for (mention in mentions) {
        val mentionLabel = mention["label"] as String?
        if (mentionLabel == null ||
            _normalizeMentionLabel(mentionLabel) != _normalizeMentionLabel(label)
        ) {
            continue
        }
        val type = ((mention["type"] as String?) ?: "").trim().lowercase()
        when (type) {
            "all" -> return true
            "admins" -> if (_userIsAdminLike(user, ownerUserId = ownerUserId)) return true
            "user" -> {
                val userId = mention["user_id"] as String?
                val uid = mention["uid"] as String?
                if (userId != null && userId == user.id) return true
                if (uid != null && uid == user.uid) return true
            }
        }
    }
    return messageMentionLabelTargetsUser(
        label = label,
        user = user,
        ownerUserId = ownerUserId,
    )
}

const val kMentionEveryoneLabel = "所有人"
const val kMentionAdminsLabel = "管理员"

/** gang-bot, the room AI. Mentioning it sends `{type: "bot"}`. */
const val kMentionGangBotLabel = "gang-bot"

private fun _specialMentionOptions(
    normalizedQuery: String,
    includeGangBot: Boolean = false,
): List<MessageMentionOption> {
    val options = mutableListOf<MessageMentionOption>()

    fun addIfMatches(
        label: String,
        kind: MessageMentionKind,
        roleLabel: String = "",
    ) {
        if (!_specialMentionMatches(label, normalizedQuery)) return
        options.add(
            MessageMentionOption(label = label, roleLabel = roleLabel, kind = kind),
        )
    }

    if (includeGangBot) {
        addIfMatches(
            label = kMentionGangBotLabel,
            kind = MessageMentionKind.bot,
            roleLabel = "AI",
        )
    }
    addIfMatches(label = kMentionEveryoneLabel, kind = MessageMentionKind.everyone)
    addIfMatches(label = kMentionAdminsLabel, kind = MessageMentionKind.admins)
    return options
}

private fun _specialMentionMatches(label: String, normalizedQuery: String): Boolean {
    if (normalizedQuery.isEmpty()) return true
    val normalizedLabel = _normalizeMentionLabel(label)
    if (normalizedLabel.contains(normalizedQuery)) return true
    if (label == kMentionEveryoneLabel) {
        return "all".contains(normalizedQuery) ||
            "everyone".contains(normalizedQuery)
    }
    if (label == kMentionAdminsLabel) {
        return "admin".contains(normalizedQuery) ||
            "admins".contains(normalizedQuery)
    }
    if (label == kMentionGangBotLabel) {
        return listOf(
            "bot",
            "ai",
            "gangbot",
            "机器人",
        ).any { alias -> alias.contains(normalizedQuery) }
    }
    return false
}

private fun _messageMentionRangesForKnownLabels(
    text: String,
    labels: Iterable<String>,
): MutableList<MessageMentionRange> {
    val knownLabels =
        labels
            .map { label -> label.trim() }
            .filter { label -> label.isNotEmpty() }
            .toSet()
            .toMutableList()
    knownLabels.sortWith { a, b -> b.length.compareTo(a.length) }
    if (knownLabels.isEmpty()) return mutableListOf()

    val ranges = mutableListOf<MessageMentionRange>()
    var index = 0
    while (index < text.length) {
        val atIndex = text.indexOf('@', index)
        if (atIndex < 0) break
        var matched: MessageMentionRange? = null
        for (label in knownLabels) {
            val end = atIndex + 1 + label.length
            if (end > text.length) continue
            if (text.substring(atIndex + 1, end) != label) continue
            if (!_isKnownMentionBoundary(text, end)) continue
            matched = MessageMentionRange(start = atIndex, end = end)
            break
        }
        if (matched != null) {
            ranges.add(matched)
            index = matched.end
        } else {
            index = atIndex + 1
        }
    }
    return ranges
}

private fun _overlapsAnyMentionRange(
    range: MessageMentionRange,
    ranges: List<MessageMentionRange>,
): Boolean {
    for (existing in ranges) {
        if (range.start < existing.end && range.end > existing.start) return true
    }
    return false
}

private fun _isKnownMentionBoundary(text: String, end: Int): Boolean {
    if (end >= text.length) return true
    return _isMentionTerminator(text[end].code)
}

private fun _mentionSearchRank(
    member: RoomMember,
    label: String,
    normalizedQuery: String,
): Int? {
    if (normalizedQuery.isEmpty()) return 100
    val candidates =
        linkedSetOf(
            label,
            member.roomDisplayName ?: "",
            member.remarkName ?: "",
            member.user.roomDisplayName ?: "",
            member.user.displayName,
            member.user.username,
            member.user.uid ?: "",
            member.user.id,
        )
            .filter { value -> value.trim().isNotEmpty() }
            .map { value -> value.lowercase() }
    var bestRank = 1 shl 30
    for (candidate in candidates) {
        if (candidate == normalizedQuery) {
            bestRank = if (bestRank < 0) bestRank else 0
        } else if (candidate.startsWith(normalizedQuery)) {
            bestRank = if (bestRank < 1) bestRank else 1
        } else if (candidate.contains(normalizedQuery)) {
            bestRank = if (bestRank < 2) bestRank else 2
        }
    }
    return if (bestRank == 1 shl 30) null else bestRank
}

private fun _mentionLabelsForMember(member: RoomMember): Sequence<String> =
    sequence {
        val label = roomMemberDisplayName(member).trim()
        if (label.isNotEmpty()) yield(label)
        val values =
            listOf(
                member.roomDisplayName,
                member.remarkName,
                member.user.roomDisplayName,
                member.user.displayName,
                member.user.username,
                member.user.uid,
                member.user.id,
            )
        for (value in values) {
            val text = value?.trim()
            if (text != null && text.isNotEmpty()) yield(text)
        }
    }

private fun _mentionLabelsForUser(user: UserSummary): Sequence<String> =
    sequence {
        val values =
            listOf(
                user.roomDisplayName,
                user.displayName,
                user.username,
                user.uid,
                user.id,
            )
        for (value in values) {
            val text = value?.trim()
            if (text != null && text.isNotEmpty()) yield(text)
        }
    }

private fun _normalizeMentionLabel(value: String): String {
    return value.trim().lowercase()
}

private fun _userIsAdminLike(user: UserSummary, ownerUserId: String?): Boolean {
    if (user.isSuperuser || (ownerUserId != null && user.id == ownerUserId)) {
        return true
    }
    return when ((user.roomRole ?: "").lowercase()) {
        "owner", "creator", "admin", "administrator" -> true
        else -> false
    }
}

private fun _isMentionTerminator(codeUnit: Int): Boolean =
    codeUnit <= 0x20 || codeUnit == 0x3000

private fun _looksLikeEmailAddress(
    text: String,
    atIndex: Int,
    end: Int,
): Boolean {
    if (atIndex <= 0 || end <= atIndex + 1 || end > text.length) return false
    val domain = text.substring(atIndex + 1, end)
    if (!domain.contains('.')) return false
    if (domain.startsWith('.') || domain.endsWith('.')) return false
    if (!domain.all { _isEmailDomainCodeUnit(it.code) }) return false

    var localStart = atIndex - 1
    while (localStart >= 0 &&
        _isEmailLocalCodeUnit(text[localStart].code)
    ) {
        localStart--
    }
    localStart++
    if (localStart >= atIndex) return false
    val local = text.substring(localStart, atIndex)
    return local.isNotEmpty() &&
        !local.startsWith('.') &&
        !local.endsWith('.') &&
        local.all { _isEmailLocalCodeUnit(it.code) }
}

private fun _isEmailLocalCodeUnit(codeUnit: Int): Boolean {
    return _isAsciiLetterOrDigit(codeUnit) ||
        codeUnit == 0x2e ||
        codeUnit == 0x5f ||
        codeUnit == 0x25 ||
        codeUnit == 0x2b ||
        codeUnit == 0x2d
}

private fun _isEmailDomainCodeUnit(codeUnit: Int): Boolean {
    return _isAsciiLetterOrDigit(codeUnit) ||
        codeUnit == 0x2e ||
        codeUnit == 0x2d
}

private fun _isAsciiLetterOrDigit(codeUnit: Int): Boolean {
    return (codeUnit in 0x30..0x39) ||
        (codeUnit in 0x41..0x5a) ||
        (codeUnit in 0x61..0x7a)
}

private class _RankedMentionOption(
    val option: MessageMentionOption,
    val rank: Int,
    val roleRank: Int,
)
