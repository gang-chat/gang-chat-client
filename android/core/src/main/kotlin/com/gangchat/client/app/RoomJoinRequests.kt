package com.gangchat.client.app

import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.protocol.JoinRequest
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.UserSummary

enum class JoinRequestListBodyState { empty, results }

class JoinRequestCandidate(val request: JoinRequest, val busy: Boolean) {
    val canReview: Boolean get() = !busy

    val userForInfo: UserSummary
        get() = request.user.copyWith(roomRole = "pending")
}

class JoinRequestReviewActionState(
    val requests: List<JoinRequest>,
    val busyRequestIds: Set<String>,
    val changed: Boolean,
) {
    val hasPendingRequests: Boolean get() = requests.isNotEmpty()
}

fun joinRequestCandidates(
    requests: Iterable<JoinRequest>,
    busyRequestIds: Iterable<String> = emptyList(),
): List<JoinRequestCandidate> {
    val busySet = busyRequestIds.toSet()
    return requests.map { request ->
        JoinRequestCandidate(
            request = request,
            busy = busySet.contains(request.id),
        )
    }
}

fun joinRequestListBodyState(
    candidates: Iterable<JoinRequestCandidate>,
): JoinRequestListBodyState {
    if (candidates.none()) return JoinRequestListBodyState.empty
    return JoinRequestListBodyState.results
}

fun joinRequestReasonText(request: JoinRequest): String? {
    val reason = request.reason.trim()
    if (reason.isEmpty()) return null
    return reason
}

fun joinRequestDetailReasonText(request: JoinRequest): String {
    return joinRequestReasonText(request) ?: "未填写"
}

fun joinRequestFromInvitation(request: JoinRequest): Boolean {
    return request.source == "invitation" || request.inviters.isNotEmpty()
}

fun joinRequestSourceText(request: JoinRequest): String {
    if (!joinRequestFromInvitation(request)) return "公开房间搜索"
    val names =
        request.inviters
            .map(::userPrimaryName)
            .filter { name -> name.trim().isNotEmpty() }
    if (names.isEmpty()) return "邀请"
    return "${names.joinToString("、")} 的邀请"
}

fun canStartJoinRequestReview(
    requestId: String,
    busyRequestIds: Iterable<String> = emptyList(),
): Boolean {
    return !busyRequestIds.contains(requestId)
}

fun shouldRefreshJoinRequestBadgeForEvent(
    data: JsonMap,
    selectedRoomId: String?,
    canReviewJoinRequests: Boolean,
): Boolean {
    val roomId = data["room_id"] as String? ?: return false
    return shouldRefreshJoinRequestBadgeForRoom(
        roomId = roomId,
        selectedRoomId = selectedRoomId,
        canReviewJoinRequests = canReviewJoinRequests,
    )
}

fun shouldRefreshJoinRequestBadgeForRoom(
    roomId: String,
    selectedRoomId: String?,
    canReviewJoinRequests: Boolean,
): Boolean {
    return roomId == selectedRoomId && canReviewJoinRequests
}

fun joinRequestBadgeRefreshTarget(
    room: RoomDetail?,
    canReviewJoinRequests: Boolean,
): RoomDetail? {
    if (room == null) return null
    if (!canReviewJoinRequests) return null
    return room
}

fun canApplyJoinRequestBadgeRefresh(
    targetRoomId: String,
    selectedRoomId: String?,
): Boolean {
    return targetRoomId == selectedRoomId
}

fun joinRequestReviewStarted(
    requests: Iterable<JoinRequest>,
    requestId: String,
    busyRequestIds: Iterable<String> = emptyList(),
): JoinRequestReviewActionState {
    return JoinRequestReviewActionState(
        requests = requests.toList(),
        busyRequestIds = LinkedHashSet<String>().apply {
            addAll(busyRequestIds)
            add(requestId)
        },
        changed = false,
    )
}

fun joinRequestReviewSucceeded(
    requests: Iterable<JoinRequest>,
    requestId: String,
    busyRequestIds: Iterable<String> = emptyList(),
): JoinRequestReviewActionState {
    return JoinRequestReviewActionState(
        requests = requests.filter { request -> request.id != requestId },
        busyRequestIds = busyRequestIds.filterTo(LinkedHashSet()) { id -> id != requestId },
        changed = true,
    )
}

fun joinRequestReviewFailed(
    requests: Iterable<JoinRequest>,
    requestId: String,
    busyRequestIds: Iterable<String> = emptyList(),
): JoinRequestReviewActionState {
    return JoinRequestReviewActionState(
        requests = requests.toList(),
        busyRequestIds = busyRequestIds.filterTo(LinkedHashSet()) { id -> id != requestId },
        changed = false,
    )
}
