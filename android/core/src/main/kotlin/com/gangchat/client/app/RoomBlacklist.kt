package com.gangchat.client.app

import com.gangchat.client.protocol.RoomBlacklistEntry
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.UserSummary

enum class RoomBlacklistSearchBodyState { loading, prompt, empty, results }

class RoomBlacklistCandidate(
    val user: UserSummary,
    val member: Boolean,
    val blocked: Boolean,
    val superuser: Boolean,
    val busy: Boolean,
) {
    val blockActionEnabled: Boolean get() = !member && !superuser
    val canBlock: Boolean get() = blockActionEnabled && !blocked && !busy
    val canUnblock: Boolean get() = blocked && !busy
}

fun roomBlacklistSearchBodyState(
    searching: Boolean,
    query: String,
    candidates: Iterable<RoomBlacklistCandidate>,
): RoomBlacklistSearchBodyState {
    if (searching) return RoomBlacklistSearchBodyState.loading
    if (query.trim().isEmpty()) return RoomBlacklistSearchBodyState.prompt
    if (candidates.none()) return RoomBlacklistSearchBodyState.empty
    return RoomBlacklistSearchBodyState.results
}

fun roomBlacklistActionLabel(
    member: Boolean,
    superuser: Boolean,
    blocked: Boolean,
): String {
    if (member) return "在房间内"
    if (superuser) return "超级用户"
    if (blocked) return "取消拉黑"
    return "拉黑"
}

fun roomBlacklistCandidates(
    searchResults: Iterable<UserSummary>,
    blacklist: Iterable<RoomBlacklistEntry>,
    members: Iterable<RoomMember>,
    query: String,
    busyUserIds: Iterable<String> = emptyList(),
): List<RoomBlacklistCandidate> {
    val normalizedQuery = query.trim().lowercase()
    val membersByUserId = LinkedHashMap<String, RoomMember>()
    for (member in members) membersByUserId[member.user.id] = member
    val blockedByUserId = LinkedHashMap<String, RoomBlacklistEntry>()
    for (entry in blacklist) blockedByUserId[entry.user.id] = entry
    val busySet = busyUserIds.toSet()
    val users = mutableListOf<UserSummary>()
    val seenUserIds = LinkedHashSet<String>()

    fun add(user: UserSummary) {
        if (seenUserIds.add(user.id)) users.add(user)
    }

    for (user in searchResults) {
        if (_userIdentityMatchesQuery(user, normalizedQuery)) {
            add(user)
        }
    }
    if (normalizedQuery.isNotEmpty()) {
        for (entry in blacklist) {
            if (_userIdentityMatchesQuery(entry.user, normalizedQuery)) {
                add(entry.user)
            }
        }
        for (member in members) {
            if (roomMemberSearchRank(member, normalizedQuery) < 99) {
                add(member.user)
            }
        }
    }

    return users.map { user ->
        RoomBlacklistCandidate(
            user = user,
            member = membersByUserId.containsKey(user.id),
            blocked = blockedByUserId.containsKey(user.id),
            superuser = user.isSuperuser,
            busy = busySet.contains(user.id),
        )
    }
}

fun upsertRoomBlacklistEntry(
    entries: Iterable<RoomBlacklistEntry>,
    entry: RoomBlacklistEntry,
): List<RoomBlacklistEntry> {
    return listOf(entry) + entries.filter { item -> item.user.id != entry.user.id }
}

fun removeRoomBlacklistEntry(
    entries: Iterable<RoomBlacklistEntry>,
    userId: String,
): List<RoomBlacklistEntry> {
    return entries.filter { item -> item.user.id != userId }
}

private fun _userIdentityMatchesQuery(user: UserSummary, normalizedQuery: String): Boolean {
    if (normalizedQuery.isEmpty()) return true

    fun contains(value: String?): Boolean {
        val text = value?.trim()?.lowercase()
        return text != null && text.isNotEmpty() && text.contains(normalizedQuery)
    }

    return contains(user.uid) ||
        contains(user.id) ||
        contains(user.username) ||
        contains(user.displayName)
}
