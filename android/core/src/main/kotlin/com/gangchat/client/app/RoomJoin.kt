package com.gangchat.client.app

import com.gangchat.client.protocol.JoinRoomResult
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomInvite

class RoomJoinSearchPatch(
    val results: List<PublicRoom>,
    val searching: Boolean,
    val error: String?,
)

class RoomJoinSearchInputPatch(
    val search: RoomJoinSearchPatch,
    val query: String,
    val shouldSearch: Boolean,
    val shouldCancelInFlightSearch: Boolean,
)

class RoomJoinInvitesPatch(
    val invites: List<RoomInvite>,
    val loading: Boolean,
    val error: String?,
)

class RoomJoinPublicActionPatch(
    val busyRoomId: String?,
    val error: String?,
    val pendingRoomIds: Set<String>,
)

class RoomJoinInviteDecisionPatch(
    val invites: List<RoomInvite>,
    val pendingRoomIds: Set<String>,
    val busyInviteId: String?,
    val error: String?,
)

fun hasRoomSearchQuery(query: String): Boolean {
    return query.trim().isNotEmpty()
}

fun shouldShowRoomInviteSection(
    loadingInvites: Boolean,
    invites: Iterable<RoomInvite>,
): Boolean {
    return loadingInvites || invites.any()
}

fun canStartPublicRoomAction(busyRoomId: String?): Boolean {
    return busyRoomId == null
}

fun canStartRoomInviteDecision(
    busyInviteId: String?,
    busyRoomId: String?,
): Boolean {
    return busyInviteId == null && busyRoomId == null
}

fun joinRequestReasonValue(rawReason: String): String? {
    val reason = rawReason.trim()
    if (reason.isEmpty()) return null
    return reason
}

fun publicRoomJoinSubtitle(room: PublicRoom): String {
    val rid = room.rid.trim()
    if (rid.isEmpty()) return "${room.memberCount} 名成员"
    return "$rid · ${room.memberCount} 名成员"
}

fun pendingRoomInviteSubtitle(invite: RoomInvite): String {
    val rid = invite.room.rid.trim()
    if (rid.isEmpty()) return "邀请你加入"
    return "邀请你加入 · RID $rid"
}

class PublicRoomJoinCandidate(
    val room: PublicRoom,
    val pending: Boolean,
    val busy: Boolean,
) {
    val opensJoinedRoom: Boolean get() = room.joined

    val actionable: Boolean
        get() = publicRoomJoinActionable(room, pending = pending)

    val actionEnabled: Boolean get() = actionable && !busy
}

fun publicRoomJoinCandidates(
    rooms: Iterable<PublicRoom>,
    pendingRoomIds: Iterable<String> = emptyList(),
    busyRoomId: String? = null,
): List<PublicRoomJoinCandidate> {
    val pendingSet = pendingRoomIds.toSet()
    return rooms.map { room ->
        PublicRoomJoinCandidate(
            room = room,
            pending = room.joinState == "pending" || pendingSet.contains(room.id),
            busy = busyRoomId == room.id,
        )
    }
}

fun pendingRoomIdsAfterJoinResult(
    pendingRoomIds: Iterable<String>,
    room: PublicRoom,
    result: JoinRoomResult,
): Set<String> {
    val next = pendingRoomIds.toMutableSet()
    if (result.pending) next.add(room.id)
    return next
}

fun roomJoinSearchCleared(): RoomJoinSearchPatch {
    return RoomJoinSearchPatch(results = emptyList(), searching = false, error = null)
}

fun roomJoinSearchQueryChanged(
    rawQuery: String,
    results: Iterable<PublicRoom>,
    error: String?,
): RoomJoinSearchInputPatch {
    val query = rawQuery.trim()
    if (!hasRoomSearchQuery(query)) {
        return RoomJoinSearchInputPatch(
            search = roomJoinSearchCleared(),
            query = "",
            shouldSearch = false,
            shouldCancelInFlightSearch = true,
        )
    }
    return RoomJoinSearchInputPatch(
        search = roomJoinSearchStarted(results = results, error = error),
        query = query,
        shouldSearch = true,
        shouldCancelInFlightSearch = true,
    )
}

fun roomJoinSearchStarted(
    results: Iterable<PublicRoom>,
    error: String?,
): RoomJoinSearchPatch {
    return RoomJoinSearchPatch(
        results = results.toList(),
        searching = true,
        error = error,
    )
}

fun roomJoinSearchSucceeded(results: Iterable<PublicRoom>): RoomJoinSearchPatch {
    return RoomJoinSearchPatch(
        results = results.toList(),
        searching = false,
        error = null,
    )
}

fun roomJoinSearchFailed(
    results: Iterable<PublicRoom>,
    failure: Any,
): RoomJoinSearchPatch {
    return RoomJoinSearchPatch(
        results = results.toList(),
        searching = false,
        error = userFacingErrorMessage(failure),
    )
}

fun roomJoinInvitesLoadStarted(invites: Iterable<RoomInvite>): RoomJoinInvitesPatch {
    return RoomJoinInvitesPatch(
        invites = invites.toList(),
        loading = true,
        error = null,
    )
}

fun roomJoinInvitesLoadSucceeded(invites: Iterable<RoomInvite>): RoomJoinInvitesPatch {
    return RoomJoinInvitesPatch(
        invites = invites.toList(),
        loading = false,
        error = null,
    )
}

fun roomJoinInvitesLoadFailed(
    invites: Iterable<RoomInvite>,
    failure: Any,
): RoomJoinInvitesPatch {
    return RoomJoinInvitesPatch(
        invites = invites.toList(),
        loading = false,
        error = userFacingErrorMessage(failure),
    )
}

fun roomJoinPublicActionStarted(
    roomId: String,
    pendingRoomIds: Iterable<String>,
): RoomJoinPublicActionPatch {
    return RoomJoinPublicActionPatch(
        busyRoomId = roomId,
        error = null,
        pendingRoomIds = pendingRoomIds.toSet(),
    )
}

fun roomJoinPublicActionPending(
    busyRoomId: String?,
    error: String?,
    pendingRoomIds: Iterable<String>,
    room: PublicRoom,
    result: JoinRoomResult,
): RoomJoinPublicActionPatch {
    return RoomJoinPublicActionPatch(
        busyRoomId = busyRoomId,
        error = error,
        pendingRoomIds =
            pendingRoomIdsAfterJoinResult(
                pendingRoomIds = pendingRoomIds,
                room = room,
                result = result,
            ),
    )
}

fun roomJoinPublicActionFailed(
    busyRoomId: String?,
    pendingRoomIds: Iterable<String>,
    failure: Any,
): RoomJoinPublicActionPatch {
    return RoomJoinPublicActionPatch(
        busyRoomId = busyRoomId,
        error = userFacingErrorMessage(failure),
        pendingRoomIds = pendingRoomIds.toSet(),
    )
}

fun roomJoinPublicActionFinished(
    error: String?,
    pendingRoomIds: Iterable<String>,
): RoomJoinPublicActionPatch {
    return RoomJoinPublicActionPatch(
        busyRoomId = null,
        error = error,
        pendingRoomIds = pendingRoomIds.toSet(),
    )
}

fun roomJoinInviteDecisionStarted(
    invites: Iterable<RoomInvite>,
    pendingRoomIds: Iterable<String>,
    inviteId: String,
): RoomJoinInviteDecisionPatch {
    return RoomJoinInviteDecisionPatch(
        invites = invites.toList(),
        pendingRoomIds = pendingRoomIds.toSet(),
        busyInviteId = inviteId,
        error = null,
    )
}

fun roomJoinInviteDecisionSucceeded(
    invites: Iterable<RoomInvite>,
    pendingRoomIds: Iterable<String>,
    invite: RoomInvite,
    accept: Boolean,
    result: JoinRoomResult,
): RoomJoinInviteDecisionPatch {
    val next =
        reviewedRoomInvites(
            invites = invites,
            pendingRoomIds = pendingRoomIds,
            invite = invite,
            accept = accept,
            result = result,
        )
    return RoomJoinInviteDecisionPatch(
        invites = next.invites,
        pendingRoomIds = next.pendingRoomIds,
        busyInviteId = null,
        error = null,
    )
}

fun roomJoinInviteDecisionFailed(
    invites: Iterable<RoomInvite>,
    pendingRoomIds: Iterable<String>,
    busyInviteId: String?,
    failure: Any,
): RoomJoinInviteDecisionPatch {
    return RoomJoinInviteDecisionPatch(
        invites = invites.toList(),
        pendingRoomIds = pendingRoomIds.toSet(),
        busyInviteId = busyInviteId,
        error = userFacingErrorMessage(failure),
    )
}

fun roomJoinInviteDecisionFinished(
    invites: Iterable<RoomInvite>,
    pendingRoomIds: Iterable<String>,
    error: String?,
): RoomJoinInviteDecisionPatch {
    return RoomJoinInviteDecisionPatch(
        invites = invites.toList(),
        pendingRoomIds = pendingRoomIds.toSet(),
        busyInviteId = null,
        error = error,
    )
}
