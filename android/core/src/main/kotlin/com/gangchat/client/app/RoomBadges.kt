package com.gangchat.client.app

class RoomNavigationBadgePatch(
    val hasPendingRoomInvites: Boolean,
    val selectedRoomHasPendingJoinRequests: Boolean,
    val pendingRoomNotificationCount: Int? = null,
)

fun roomNavigationBadgesReset(
    currentHasPendingRoomInvites: Boolean,
    currentSelectedRoomHasPendingJoinRequests: Boolean,
): RoomNavigationBadgePatch? {
    if (!currentHasPendingRoomInvites &&
        !currentSelectedRoomHasPendingJoinRequests
    ) {
        return null
    }
    return _clearedBadgesPatch
}

fun roomInviteBadgeUpdated(
    currentHasPendingRoomInvites: Boolean,
    currentSelectedRoomHasPendingJoinRequests: Boolean,
    hasPending: Boolean,
    currentPendingRoomNotificationCount: Int? = null,
    pendingRoomNotificationCount: Int? = null,
): RoomNavigationBadgePatch? {
    if (currentHasPendingRoomInvites == hasPending &&
        (pendingRoomNotificationCount == null ||
            currentPendingRoomNotificationCount ==
            pendingRoomNotificationCount)
    ) {
        return null
    }
    return RoomNavigationBadgePatch(
        hasPendingRoomInvites = hasPending,
        selectedRoomHasPendingJoinRequests =
            currentSelectedRoomHasPendingJoinRequests,
        pendingRoomNotificationCount = pendingRoomNotificationCount,
    )
}

fun selectedJoinRequestBadgeUpdated(
    currentHasPendingRoomInvites: Boolean,
    currentSelectedRoomHasPendingJoinRequests: Boolean,
    hasPending: Boolean,
): RoomNavigationBadgePatch? {
    if (currentSelectedRoomHasPendingJoinRequests == hasPending) return null
    return RoomNavigationBadgePatch(
        hasPendingRoomInvites = currentHasPendingRoomInvites,
        selectedRoomHasPendingJoinRequests = hasPending,
    )
}

// One shared instance, like Dart's canonicalized `const` patch.
private val _clearedBadgesPatch =
    RoomNavigationBadgePatch(
        hasPendingRoomInvites = false,
        selectedRoomHasPendingJoinRequests = false,
        pendingRoomNotificationCount = 0,
    )
