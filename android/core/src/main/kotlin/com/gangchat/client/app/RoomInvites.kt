package com.gangchat.client.app

import com.gangchat.client.protocol.JoinRoomResult
import com.gangchat.client.protocol.RoomInvite
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.UserSummary

enum class RoomInviteSearchBodyState { loading, prompt, empty, results }

class RoomMemberInviteDialogPatch(
    val searchResults: List<UserSummary>,
    val searching: Boolean,
    val error: String?,
    val pendingInviteUserIds: Set<String>,
    val busyUserIds: Set<String>,
)

class RoomMemberInviteSearchInputPatch(
    val dialog: RoomMemberInviteDialogPatch,
    val query: String,
    val shouldSearch: Boolean,
    val shouldCancelInFlightSearch: Boolean,
)

class RoomInviteCandidate(
    val user: UserSummary,
    val existing: Boolean,
    val pending: Boolean,
    val busy: Boolean,
) {
    val inviteActionEnabled: Boolean get() = !existing && !pending
    val canStartInvite: Boolean get() = inviteActionEnabled && !busy
}

fun roomInvitesEnabled(joinPolicy: String): Boolean {
    return joinPolicy.trim().lowercase() != "closed"
}

fun roomInviteSearchBodyState(
    searching: Boolean,
    query: String,
    candidates: Iterable<RoomInviteCandidate>,
): RoomInviteSearchBodyState {
    if (searching) return RoomInviteSearchBodyState.loading
    if (query.trim().isEmpty()) return RoomInviteSearchBodyState.prompt
    if (candidates.none()) return RoomInviteSearchBodyState.empty
    return RoomInviteSearchBodyState.results
}

class RoomInviteReviewState(
    val invites: List<RoomInvite>,
    val pendingRoomIds: Set<String>,
) {
    val hasPendingInvites: Boolean get() = invites.isNotEmpty()
}

class RoomMemberInviteActionState(
    val pendingInviteUserIds: Set<String>,
    val busyUserIds: Set<String>,
)

fun roomInviteCandidates(
    searchResults: Iterable<UserSummary>,
    members: Iterable<RoomMember>,
    query: String,
    pendingInviteUserIds: Iterable<String> = emptyList(),
    busyUserIds: Iterable<String> = emptyList(),
    invitesEnabled: Boolean = true,
): List<RoomInviteCandidate> {
    val normalizedQuery = query.trim().lowercase()
    val pendingSet = pendingInviteUserIds.toSet()
    val busySet = busyUserIds.toSet()
    val membersByUserId = LinkedHashMap<String, RoomMember>()
    for (member in members) membersByUserId[member.user.id] = member
    val users = mutableListOf<UserSummary>()
    val seenUserIds = LinkedHashSet<String>()

    fun add(user: UserSummary) {
        if (user.isSuperuser) return
        if (seenUserIds.add(user.id)) users.add(user)
    }

    for (user in searchResults) {
        if (_userIdentityMatchesQuery(user, normalizedQuery)) {
            add(user)
        }
    }
    if (normalizedQuery.isNotEmpty()) {
        for (member in members) {
            if (roomMemberSearchRank(member, normalizedQuery) < 99) {
                add(member.user)
            }
        }
    }

    return users.map { user ->
        RoomInviteCandidate(
            user = user,
            existing = !invitesEnabled || membersByUserId.containsKey(user.id),
            pending = pendingSet.contains(user.id),
            busy = busySet.contains(user.id),
        )
    }
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

fun canStartRoomInvite(
    userId: String,
    members: Iterable<RoomMember>,
    pendingInviteUserIds: Iterable<String> = emptyList(),
    busyUserIds: Iterable<String> = emptyList(),
    isSuperuser: Boolean = false,
    invitesEnabled: Boolean = true,
): Boolean {
    if (!invitesEnabled) return false
    if (isSuperuser) return false
    for (member in members) {
        if (member.user.id == userId) return false
    }
    return !pendingInviteUserIds.contains(userId) &&
        !busyUserIds.contains(userId)
}

fun roomMemberInviteSearchCleared(
    pendingInviteUserIds: Iterable<String>,
    busyUserIds: Iterable<String>,
): RoomMemberInviteDialogPatch {
    return RoomMemberInviteDialogPatch(
        searchResults = emptyList(),
        searching = false,
        error = null,
        pendingInviteUserIds = pendingInviteUserIds.toSet(),
        busyUserIds = busyUserIds.toSet(),
    )
}

fun roomMemberInviteSearchQueryChanged(
    rawQuery: String,
    searchResults: Iterable<UserSummary>,
    pendingInviteUserIds: Iterable<String>,
    busyUserIds: Iterable<String>,
): RoomMemberInviteSearchInputPatch {
    val query = rawQuery.trim()
    if (query.isEmpty()) {
        return RoomMemberInviteSearchInputPatch(
            dialog =
                roomMemberInviteSearchCleared(
                    pendingInviteUserIds = pendingInviteUserIds,
                    busyUserIds = busyUserIds,
                ),
            query = "",
            shouldSearch = false,
            shouldCancelInFlightSearch = true,
        )
    }
    return RoomMemberInviteSearchInputPatch(
        dialog =
            roomMemberInviteSearchStarted(
                searchResults = searchResults,
                pendingInviteUserIds = pendingInviteUserIds,
                busyUserIds = busyUserIds,
            ),
        query = query,
        shouldSearch = true,
        shouldCancelInFlightSearch = true,
    )
}

fun roomMemberInviteSearchStarted(
    searchResults: Iterable<UserSummary>,
    pendingInviteUserIds: Iterable<String>,
    busyUserIds: Iterable<String>,
): RoomMemberInviteDialogPatch {
    return RoomMemberInviteDialogPatch(
        searchResults = searchResults.toList(),
        searching = true,
        error = null,
        pendingInviteUserIds = pendingInviteUserIds.toSet(),
        busyUserIds = busyUserIds.toSet(),
    )
}

fun roomMemberInviteSearchSucceeded(
    searchResults: Iterable<UserSummary>,
    pendingInviteUserIds: Iterable<String>,
    busyUserIds: Iterable<String>,
): RoomMemberInviteDialogPatch {
    return RoomMemberInviteDialogPatch(
        searchResults = searchResults.toList(),
        searching = false,
        error = null,
        pendingInviteUserIds = pendingInviteUserIds.toSet(),
        busyUserIds = busyUserIds.toSet(),
    )
}

fun roomMemberInviteSearchFailed(
    searchResults: Iterable<UserSummary>,
    pendingInviteUserIds: Iterable<String>,
    busyUserIds: Iterable<String>,
    failure: Any,
): RoomMemberInviteDialogPatch {
    return RoomMemberInviteDialogPatch(
        searchResults = searchResults.toList(),
        searching = false,
        error = userFacingErrorMessage(failure),
        pendingInviteUserIds = pendingInviteUserIds.toSet(),
        busyUserIds = busyUserIds.toSet(),
    )
}

fun roomMemberInviteActionStarted(
    userId: String,
    pendingInviteUserIds: Iterable<String> = emptyList(),
    busyUserIds: Iterable<String> = emptyList(),
): RoomMemberInviteActionState {
    return RoomMemberInviteActionState(
        pendingInviteUserIds = pendingInviteUserIds.toSet(),
        busyUserIds = LinkedHashSet<String>().apply {
            addAll(busyUserIds)
            add(userId)
        },
    )
}

fun roomMemberInviteActionSucceeded(
    userId: String,
    pendingInviteUserIds: Iterable<String> = emptyList(),
    busyUserIds: Iterable<String> = emptyList(),
): RoomMemberInviteActionState {
    return RoomMemberInviteActionState(
        pendingInviteUserIds = LinkedHashSet<String>().apply {
            addAll(pendingInviteUserIds)
            add(userId)
        },
        busyUserIds = busyUserIds.filterTo(LinkedHashSet()) { item -> item != userId },
    )
}

fun roomMemberInviteActionFailed(
    userId: String,
    pendingInviteUserIds: Iterable<String> = emptyList(),
    busyUserIds: Iterable<String> = emptyList(),
): RoomMemberInviteActionState {
    return RoomMemberInviteActionState(
        pendingInviteUserIds = pendingInviteUserIds.toSet(),
        busyUserIds = busyUserIds.filterTo(LinkedHashSet()) { item -> item != userId },
    )
}

fun roomMemberInviteStarted(
    searchResults: Iterable<UserSummary>,
    searching: Boolean,
    userId: String,
    pendingInviteUserIds: Iterable<String>,
    busyUserIds: Iterable<String>,
): RoomMemberInviteDialogPatch {
    val state =
        roomMemberInviteActionStarted(
            userId = userId,
            pendingInviteUserIds = pendingInviteUserIds,
            busyUserIds = busyUserIds,
        )
    return RoomMemberInviteDialogPatch(
        searchResults = searchResults.toList(),
        searching = searching,
        error = null,
        pendingInviteUserIds = state.pendingInviteUserIds,
        busyUserIds = state.busyUserIds,
    )
}

fun roomMemberInviteSucceeded(
    searchResults: Iterable<UserSummary>,
    searching: Boolean,
    error: String?,
    userId: String,
    pendingInviteUserIds: Iterable<String>,
    busyUserIds: Iterable<String>,
): RoomMemberInviteDialogPatch {
    val state =
        roomMemberInviteActionSucceeded(
            userId = userId,
            pendingInviteUserIds = pendingInviteUserIds,
            busyUserIds = busyUserIds,
        )
    return RoomMemberInviteDialogPatch(
        searchResults = searchResults.toList(),
        searching = searching,
        error = error,
        pendingInviteUserIds = state.pendingInviteUserIds,
        busyUserIds = state.busyUserIds,
    )
}

fun roomMemberInviteFailed(
    searchResults: Iterable<UserSummary>,
    searching: Boolean,
    userId: String,
    pendingInviteUserIds: Iterable<String>,
    busyUserIds: Iterable<String>,
    failure: Any,
): RoomMemberInviteDialogPatch {
    val state =
        roomMemberInviteActionFailed(
            userId = userId,
            pendingInviteUserIds = pendingInviteUserIds,
            busyUserIds = busyUserIds,
        )
    return RoomMemberInviteDialogPatch(
        searchResults = searchResults.toList(),
        searching = searching,
        error = userFacingErrorMessage(failure),
        pendingInviteUserIds = state.pendingInviteUserIds,
        busyUserIds = state.busyUserIds,
    )
}

fun reviewedRoomInvites(
    invites: Iterable<RoomInvite>,
    pendingRoomIds: Iterable<String>,
    invite: RoomInvite,
    accept: Boolean,
    result: JoinRoomResult,
): RoomInviteReviewState {
    val nextPendingRoomIds = pendingRoomIds.toMutableSet()
    if (accept && result.pending) {
        nextPendingRoomIds.add(invite.room.id)
    }
    val acceptedRoomId = if (accept) invite.room.id else null
    return RoomInviteReviewState(
        invites =
            invites.filter { item ->
                if (accept) item.room.id != acceptedRoomId else item.id != invite.id
            },
        pendingRoomIds = nextPendingRoomIds,
    )
}
