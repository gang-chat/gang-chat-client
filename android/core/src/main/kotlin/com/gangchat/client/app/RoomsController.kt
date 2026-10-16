package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.protocol.DownloadedFile
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.protocol.JoinRequest
import com.gangchat.client.protocol.JoinRoomResult
import com.gangchat.client.protocol.LiveParticipant
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomApplication
import com.gangchat.client.protocol.RoomBlacklistEntry
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomEventNotification
import com.gangchat.client.protocol.RoomInvite
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.RoomMemberPage
import com.gangchat.client.protocol.RoomMemberProfile
import com.gangchat.client.protocol.Sticker
import com.gangchat.client.protocol.StickerPack
import com.gangchat.client.protocol.UploadedAsset
import com.gangchat.client.protocol.UserSummary
import kotlin.coroutines.cancellation.CancellationException

class RoomOpenSnapshot(
    val detail: RoomDetail,
    val messages: List<Message>,
    val live: LiveState,
)

class RoomListLoadPatch(
    val rooms: List<RoomCard>,
    val loading: Boolean,
    val error: String?,
)

class RoomCardsPatch(val rooms: List<RoomCard>)

class RoomUpdatedPatch(
    val rooms: List<RoomCard>,
    val selectedRoom: RoomDetail?,
    val shouldReloadMembers: Boolean,
)

class RoomOpenStatePatch(
    val settingsOpen: Boolean,
    val selectedRoomId: String?,
    val selectedRoom: RoomDetail?,
    val loadingRoom: Boolean,
    val error: String?,
    val selectedRoomHasPendingJoinRequests: Boolean,
    val messages: List<Message>,
    val live: LiveState?,
    val livePanelOpen: Boolean,
)

class RoomOpenSuccessEffects(
    val joinRequestBadgeRoom: RoomDetail,
    val joinLiveSource: String?,
)

class RoomLiveSnapshotPatch(
    val roomId: String,
    val rooms: List<RoomCard>,
    val selectedLive: LiveState?,
)

class RoomDeletedPatch(
    val roomId: String,
    val rooms: List<RoomCard>,
    val selectedRoomId: String?,
    val selectedRoom: RoomDetail?,
    val selectedRoomHasPendingJoinRequests: Boolean,
    val messages: List<Message>,
    val live: LiveState?,
    val livePanelOpen: Boolean,
    val settingsOpen: Boolean,
    val joinedLiveRoomId: String?,
    val wasSelected: Boolean,
    val shouldDisconnectLive: Boolean,
)

class RoomSelectedDetailPatch(
    val selectedRoom: RoomDetail,
    val rooms: List<RoomCard>,
)

class RoomRoleChangedPatch(
    val roomId: String,
    val selectedRoom: RoomDetail,
)

class RoomLiveRefreshPatch(val live: LiveState)

class RoomMembersSnapshot(
    val members: List<RoomMember>,
    val live: LiveState,
    val joinRequests: List<JoinRequest>,
    val joinRequestsError: String? = null,
)

class RoomMembersLiveSnapshot(val members: List<RoomMember>, val live: LiveState)

class RoomMembersDialogStatePatch(
    val members: List<RoomMember>,
    val requests: List<JoinRequest>,
    val live: LiveState?,
    val loading: Boolean,
    val changed: Boolean,
    val error: String?,
    val requestError: String?,
    val busyRequestIds: Set<String>,
) {
    val hasPendingRequests: Boolean get() = requests.isNotEmpty()
    val shouldNotifyPendingRequests: Boolean get() = requestError == null
}

fun shouldSkipRoomOpenRequest(
    loadingRoom: Boolean,
    selectedRoomId: String?,
    roomId: String,
): Boolean {
    return loadingRoom && selectedRoomId == roomId
}

fun canApplyRoomOpenResult(
    requestedRoomId: String,
    selectedRoomId: String?,
): Boolean {
    return selectedRoomId == requestedRoomId
}

fun shouldShowOptimisticRoomOpenRefreshFailure(
    hasOptimisticDetail: Boolean,
    requestedRoomId: String,
    selectedRoomId: String?,
): Boolean {
    return hasOptimisticDetail &&
        canApplyRoomOpenResult(
            requestedRoomId = requestedRoomId,
            selectedRoomId = selectedRoomId,
        )
}

fun shouldFinishRoomOpenLoading(
    requestedRoomId: String,
    selectedRoomId: String?,
): Boolean {
    return canApplyRoomOpenResult(
        requestedRoomId = requestedRoomId,
        selectedRoomId = selectedRoomId,
    )
}

fun canApplyLiveRefresh(
    live: LiveState,
    selectedRoomId: String?,
): Boolean {
    return live.roomId == selectedRoomId
}

fun roomOpenSucceededEffects(
    snapshot: RoomOpenSnapshot,
    joinLive: Boolean,
): RoomOpenSuccessEffects {
    return RoomOpenSuccessEffects(
        joinRequestBadgeRoom = snapshot.detail,
        joinLiveSource = if (joinLive) "room_card_speaker" else null,
    )
}

class RoomsController(val api: GangApi) {
    suspend fun loadRooms(): List<RoomCard> {
        val page = api.listRooms()
        return orderedRoomCards(page.rooms)
    }

    fun patchRoomListLoadStarted(rooms: List<RoomCard>): RoomListLoadPatch {
        return RoomListLoadPatch(rooms = rooms, loading = true, error = null)
    }

    fun patchRoomListLoadSucceeded(rooms: List<RoomCard>): RoomListLoadPatch {
        return RoomListLoadPatch(
            rooms = orderedRoomCards(rooms),
            loading = false,
            error = null,
        )
    }

    fun patchRoomListLoadFailed(
        rooms: List<RoomCard>,
        failure: Any,
    ): RoomListLoadPatch {
        return RoomListLoadPatch(
            rooms = rooms,
            loading = false,
            error = userFacingErrorMessage(failure),
        )
    }

    suspend fun openRoom(roomId: String): RoomOpenSnapshot {
        val detail = api.getRoom(roomId)
        val messagePage = api.listMessages(roomId = roomId)
        val live = api.getLiveState(roomId)
        return RoomOpenSnapshot(
            detail = detail,
            messages = messagePage.messages,
            live = live,
        )
    }

    fun patchRoomOpenStarted(
        roomId: String,
        currentSelectedRoom: RoomDetail?,
        currentMessages: List<Message>,
        currentLive: LiveState?,
        currentLivePanelOpen: Boolean,
        joinLive: Boolean,
        optimisticDetail: RoomDetail? = null,
    ): RoomOpenStatePatch {
        return RoomOpenStatePatch(
            settingsOpen = false,
            selectedRoomId = roomId,
            selectedRoom = optimisticDetail ?: currentSelectedRoom,
            loadingRoom = true,
            error = null,
            selectedRoomHasPendingJoinRequests = false,
            messages = if (optimisticDetail == null) currentMessages else emptyList(),
            live = optimisticDetail?.live ?: currentLive,
            livePanelOpen = if (joinLive) currentLivePanelOpen else false,
        )
    }

    fun patchRoomOpenSucceeded(
        currentSettingsOpen: Boolean,
        currentLoadingRoom: Boolean,
        currentError: String?,
        currentSelectedRoomHasPendingJoinRequests: Boolean,
        snapshot: RoomOpenSnapshot,
        joinLive: Boolean,
    ): RoomOpenStatePatch {
        return RoomOpenStatePatch(
            settingsOpen = currentSettingsOpen,
            selectedRoomId = snapshot.detail.id,
            selectedRoom = snapshot.detail,
            loadingRoom = currentLoadingRoom,
            error = currentError,
            selectedRoomHasPendingJoinRequests = currentSelectedRoomHasPendingJoinRequests,
            messages = snapshot.messages,
            live = snapshot.live,
            livePanelOpen = joinLive,
        )
    }

    fun patchRoomOpenFailed(
        settingsOpen: Boolean,
        selectedRoomId: String?,
        selectedRoom: RoomDetail?,
        loadingRoom: Boolean,
        selectedRoomHasPendingJoinRequests: Boolean,
        messages: List<Message>,
        live: LiveState?,
        livePanelOpen: Boolean,
        failure: Any,
    ): RoomOpenStatePatch {
        return RoomOpenStatePatch(
            settingsOpen = settingsOpen,
            selectedRoomId = selectedRoomId,
            selectedRoom = selectedRoom,
            loadingRoom = loadingRoom,
            error = userFacingErrorMessage(failure),
            selectedRoomHasPendingJoinRequests = selectedRoomHasPendingJoinRequests,
            messages = messages,
            live = live,
            livePanelOpen = livePanelOpen,
        )
    }

    fun patchRoomOpenFinished(
        settingsOpen: Boolean,
        selectedRoomId: String?,
        selectedRoom: RoomDetail?,
        error: String?,
        selectedRoomHasPendingJoinRequests: Boolean,
        messages: List<Message>,
        live: LiveState?,
        livePanelOpen: Boolean,
    ): RoomOpenStatePatch {
        return RoomOpenStatePatch(
            settingsOpen = settingsOpen,
            selectedRoomId = selectedRoomId,
            selectedRoom = selectedRoom,
            loadingRoom = false,
            error = error,
            selectedRoomHasPendingJoinRequests = selectedRoomHasPendingJoinRequests,
            messages = messages,
            live = live,
            livePanelOpen = livePanelOpen,
        )
    }

    fun patchRoomMembersLoadStarted(
        members: List<RoomMember>,
        requests: List<JoinRequest>,
        live: LiveState?,
        changed: Boolean,
        busyRequestIds: Iterable<String>,
    ): RoomMembersDialogStatePatch {
        return RoomMembersDialogStatePatch(
            members = members,
            requests = requests,
            live = live,
            loading = true,
            changed = changed,
            error = null,
            requestError = null,
            busyRequestIds = busyRequestIds.toSet(),
        )
    }

    fun patchRoomMembersLoadSucceeded(
        snapshot: RoomMembersSnapshot,
        changed: Boolean,
        busyRequestIds: Iterable<String>,
    ): RoomMembersDialogStatePatch {
        return RoomMembersDialogStatePatch(
            members = snapshot.members,
            requests = snapshot.joinRequests,
            live = snapshot.live,
            loading = false,
            changed = changed,
            error = null,
            requestError = snapshot.joinRequestsError,
            busyRequestIds = busyRequestIds.toSet(),
        )
    }

    fun patchRoomMembersLoadFailed(
        members: List<RoomMember>,
        requests: List<JoinRequest>,
        live: LiveState?,
        changed: Boolean,
        busyRequestIds: Iterable<String>,
        failure: Any,
    ): RoomMembersDialogStatePatch {
        return RoomMembersDialogStatePatch(
            members = members,
            requests = requests,
            live = live,
            loading = false,
            changed = changed,
            error = userFacingErrorMessage(failure),
            requestError = null,
            busyRequestIds = busyRequestIds.toSet(),
        )
    }

    fun patchRoomMembersAndLiveReloadSucceeded(
        snapshot: RoomMembersLiveSnapshot,
        requests: List<JoinRequest>,
        loading: Boolean,
        changed: Boolean,
        requestError: String?,
        busyRequestIds: Iterable<String>,
    ): RoomMembersDialogStatePatch {
        return RoomMembersDialogStatePatch(
            members = snapshot.members,
            requests = requests,
            live = snapshot.live,
            loading = loading,
            changed = changed,
            error = null,
            requestError = requestError,
            busyRequestIds = busyRequestIds.toSet(),
        )
    }

    fun patchRoomMembersAndLiveReloadFailed(
        members: List<RoomMember>,
        requests: List<JoinRequest>,
        live: LiveState?,
        loading: Boolean,
        changed: Boolean,
        requestError: String?,
        busyRequestIds: Iterable<String>,
        failure: Any,
    ): RoomMembersDialogStatePatch {
        return RoomMembersDialogStatePatch(
            members = members,
            requests = requests,
            live = live,
            loading = loading,
            changed = changed,
            error = userFacingErrorMessage(failure),
            requestError = requestError,
            busyRequestIds = busyRequestIds.toSet(),
        )
    }

    fun patchRoomJoinRequestsReloadSucceeded(
        members: List<RoomMember>,
        live: LiveState?,
        requests: List<JoinRequest>,
        loading: Boolean,
        changed: Boolean,
        busyRequestIds: Iterable<String>,
    ): RoomMembersDialogStatePatch {
        return RoomMembersDialogStatePatch(
            members = members,
            requests = requests,
            live = live,
            loading = loading,
            changed = changed,
            error = null,
            requestError = null,
            busyRequestIds = busyRequestIds.toSet(),
        )
    }

    fun patchRoomJoinRequestsReloadFailed(
        members: List<RoomMember>,
        requests: List<JoinRequest>,
        live: LiveState?,
        loading: Boolean,
        changed: Boolean,
        busyRequestIds: Iterable<String>,
        failure: Any,
    ): RoomMembersDialogStatePatch {
        return RoomMembersDialogStatePatch(
            members = members,
            requests = requests,
            live = live,
            loading = loading,
            changed = changed,
            error = null,
            requestError = userFacingErrorMessage(failure),
            busyRequestIds = busyRequestIds.toSet(),
        )
    }

    fun patchJoinRequestReviewStarted(
        members: List<RoomMember>,
        requests: List<JoinRequest>,
        live: LiveState?,
        loading: Boolean,
        changed: Boolean,
        requestId: String,
        busyRequestIds: Iterable<String>,
    ): RoomMembersDialogStatePatch {
        val started =
            joinRequestReviewStarted(
                requests = requests,
                requestId = requestId,
                busyRequestIds = busyRequestIds,
            )
        return RoomMembersDialogStatePatch(
            members = members,
            requests = started.requests,
            live = live,
            loading = loading,
            changed = changed,
            error = null,
            requestError = null,
            busyRequestIds = started.busyRequestIds,
        )
    }

    fun patchJoinRequestReviewSucceeded(
        members: List<RoomMember>,
        requests: List<JoinRequest>,
        live: LiveState?,
        loading: Boolean,
        changed: Boolean,
        error: String?,
        requestError: String?,
        requestId: String,
        busyRequestIds: Iterable<String>,
    ): RoomMembersDialogStatePatch {
        val succeeded =
            joinRequestReviewSucceeded(
                requests = requests,
                requestId = requestId,
                busyRequestIds = busyRequestIds,
            )
        return RoomMembersDialogStatePatch(
            members = members,
            requests = succeeded.requests,
            live = live,
            loading = loading,
            changed = changed || succeeded.changed,
            error = error,
            requestError = requestError,
            busyRequestIds = succeeded.busyRequestIds,
        )
    }

    fun patchJoinRequestReviewFailed(
        members: List<RoomMember>,
        requests: List<JoinRequest>,
        live: LiveState?,
        loading: Boolean,
        changed: Boolean,
        error: String?,
        requestId: String,
        busyRequestIds: Iterable<String>,
        failure: Any,
    ): RoomMembersDialogStatePatch {
        val failed =
            joinRequestReviewFailed(
                requests = requests,
                requestId = requestId,
                busyRequestIds = busyRequestIds,
            )
        return RoomMembersDialogStatePatch(
            members = members,
            requests = failed.requests,
            live = live,
            loading = loading,
            changed = changed,
            error = error,
            requestError = userFacingErrorMessage(failure),
            busyRequestIds = failed.busyRequestIds,
        )
    }

    suspend fun getRoom(roomId: String): RoomDetail {
        return api.getRoom(roomId)
    }

    suspend fun getRoomMemberProfile(
        roomId: String,
        userId: String,
    ): RoomMemberProfile {
        return api.getRoomMemberProfile(roomId = roomId, userId = userId)
    }

    suspend fun getUserProfile(userId: String): UserSummary {
        return api.getUserProfile(userId)
    }

    suspend fun createRoom(
        name: String,
        description: String? = null,
        visibility: String? = null,
        joinPolicy: String? = null,
        aiVoiceAnnouncementsEnabled: Boolean? = null,
        avatarAssetId: String? = null,
        defaultAvatarKey: String? = null,
    ): RoomDetail {
        return api.createRoom(
            name = name,
            description = description,
            visibility = visibility,
            joinPolicy = joinPolicy,
            aiVoiceAnnouncementsEnabled = aiVoiceAnnouncementsEnabled,
            avatarAssetId = avatarAssetId,
            defaultAvatarKey = defaultAvatarKey,
        )
    }

    suspend fun uploadImageAsset(
        bytes: ByteArray,
        filename: String,
        purpose: String = "avatar",
    ): UploadedAsset {
        return api.uploadImageAsset(
            bytes = bytes,
            filename = filename,
            purpose = purpose,
        )
    }

    suspend fun updateMyRoomSettings(
        roomId: String,
        remarkName: String? = null,
        notificationPolicy: String? = null,
        roomDisplayName: String? = null,
        isPinned: Boolean? = null,
        aiVoiceAnnouncementsEnabled: Boolean? = null,
    ): RoomDetail {
        return api.updateMyRoomSettings(
            roomId = roomId,
            remarkName = remarkName,
            notificationPolicy = notificationPolicy,
            roomDisplayName = roomDisplayName,
            isPinned = isPinned,
            aiVoiceAnnouncementsEnabled = aiVoiceAnnouncementsEnabled,
        )
    }

    suspend fun leaveRoom(
        roomId: String,
        confirmDeleteIfEmpty: Boolean = false,
    ) {
        api.leaveRoom(
            roomId = roomId,
            confirmDeleteIfEmpty = confirmDeleteIfEmpty,
        )
    }

    suspend fun updateRoom(
        roomId: String,
        name: String? = null,
        description: String? = null,
        visibility: String? = null,
        joinPolicy: String? = null,
        aiVoiceAnnouncementsEnabled: Boolean? = null,
        gangBotEnabled: Boolean? = null,
        avatarAssetId: String? = null,
        defaultAvatarKey: String? = null,
    ): RoomDetail {
        return api.updateRoom(
            roomId = roomId,
            name = name,
            description = description,
            visibility = visibility,
            joinPolicy = joinPolicy,
            aiVoiceAnnouncementsEnabled = aiVoiceAnnouncementsEnabled,
            gangBotEnabled = gangBotEnabled,
            avatarAssetId = avatarAssetId,
            defaultAvatarKey = defaultAvatarKey,
        )
    }

    suspend fun deleteRoom(
        roomId: String,
        confirmName: String,
    ) {
        api.deleteRoom(roomId = roomId, confirmName = confirmName)
    }

    suspend fun updateRoomMemberRole(
        roomId: String,
        userId: String,
        role: String,
    ): RoomMember {
        return api.updateRoomMemberRole(roomId = roomId, userId = userId, role = role)
    }

    suspend fun updateRoomMemberRoomDisplayName(
        roomId: String,
        userId: String,
        roomDisplayName: String,
    ): RoomMember {
        return api.updateRoomMemberRoomDisplayName(
            roomId = roomId,
            userId = userId,
            roomDisplayName = roomDisplayName,
        )
    }

    suspend fun removeRoomMember(
        roomId: String,
        userId: String,
    ) {
        api.removeRoomMember(roomId = roomId, userId = userId)
    }

    suspend fun transferRoomCreator(
        roomId: String,
        userId: String,
    ): RoomDetail {
        return api.transferRoomCreator(roomId = roomId, userId = userId)
    }

    suspend fun listRoomStickerPacks(roomId: String): List<StickerPack> {
        return api.listStickerPacks(scope = "room", roomId = roomId)
    }

    suspend fun createRoomStickerPack(
        roomId: String,
        name: String,
        sortOrder: Int? = null,
    ): StickerPack {
        return api.createStickerPack(
            name = name,
            scope = "room",
            roomId = roomId,
            sortOrder = sortOrder,
        )
    }

    suspend fun addRoomSticker(
        roomId: String,
        packId: String,
        assetId: String,
        name: String,
        sortOrder: Int? = null,
    ) {
        api.addSticker(
            packId = packId,
            assetId = assetId,
            name = name,
            sortOrder = sortOrder,
            scope = "room",
            roomId = roomId,
        )
    }

    suspend fun deleteRoomSticker(
        roomId: String,
        packId: String,
        stickerId: String,
    ) {
        api.deleteSticker(
            packId = packId,
            stickerId = stickerId,
            scope = "room",
            roomId = roomId,
        )
    }

    suspend fun updateSticker(
        packId: String,
        stickerId: String,
        name: String? = null,
        sortOrder: Int? = null,
    ): Sticker {
        return api.updateSticker(
            packId = packId,
            stickerId = stickerId,
            name = name,
            sortOrder = sortOrder,
        )
    }

    suspend fun reorderStickers(
        packId: String,
        stickerIds: List<String>,
    ): StickerPack {
        return api.reorderStickers(packId = packId, stickerIds = stickerIds)
    }

    suspend fun downloadStickers(stickerIds: List<String>): DownloadedFile {
        return api.downloadStickers(stickerIds = stickerIds)
    }

    suspend fun searchRooms(query: String): List<PublicRoom> {
        return api.searchRooms(query = query)
    }

    suspend fun joinRoom(roomId: String, reason: String? = null): JoinRoomResult {
        return api.joinRoom(roomId, reason = reason)
    }

    suspend fun listRoomInvites(status: String = "pending"): List<RoomInvite> {
        return api.listRoomInvites(status = status)
    }

    suspend fun listRoomApplications(
        status: String = "pending",
    ): List<RoomApplication> {
        return api.listRoomApplications(status = status)
    }

    suspend fun listRoomNotifications(): List<RoomEventNotification> {
        return api.listRoomNotifications()
    }

    suspend fun deleteRoomNotification(
        notificationType: String,
        notificationId: String,
    ) {
        api.deleteRoomNotification(
            notificationType = notificationType,
            notificationId = notificationId,
        )
    }

    suspend fun markRoomNotificationsRead() {
        api.markRoomNotificationsRead()
    }

    suspend fun listRoomMembers(
        roomId: String,
        limit: Int = 100,
        cursor: String? = null,
    ): RoomMemberPage {
        return api.listRoomMembers(roomId, limit = limit, cursor = cursor)
    }

    suspend fun loadAllRoomMembers(
        roomId: String,
        pageLimit: Int = 100,
        maxPages: Int = 50,
    ): List<RoomMember> {
        val members = mutableListOf<RoomMember>()
        var cursor: String? = null
        var pageCount = 0
        do {
            val page =
                api.listRoomMembers(
                    roomId,
                    limit = pageLimit,
                    cursor = cursor,
                )
            members.addAll(page.members)
            cursor = _nonEmpty(page.nextCursor)
            pageCount += 1
        } while (cursor != null && pageCount < maxPages)
        return members
    }

    suspend fun loadRoomMembersAndLive(
        roomId: String,
        fallbackLive: LiveState,
    ): RoomMembersLiveSnapshot {
        val members = loadAllRoomMembers(roomId)
        val live = getLiveStateOr(roomId, fallbackLive)
        return RoomMembersLiveSnapshot(members = members, live = live)
    }

    suspend fun loadRoomMembersSnapshot(
        roomId: String,
        fallbackLive: LiveState,
        includeJoinRequests: Boolean,
    ): RoomMembersSnapshot {
        val membersAndLive =
            loadRoomMembersAndLive(
                roomId = roomId,
                fallbackLive = fallbackLive,
            )

        var joinRequests: List<JoinRequest> = emptyList()
        var joinRequestsError: String? = null
        if (includeJoinRequests) {
            try {
                joinRequests = listJoinRequests(roomId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                joinRequestsError = e.toString()
            }
        }

        return RoomMembersSnapshot(
            members = membersAndLive.members,
            live = membersAndLive.live,
            joinRequests = joinRequests,
            joinRequestsError = joinRequestsError,
        )
    }

    suspend fun inviteMember(
        roomId: String,
        userId: String,
    ): RoomInvite {
        return api.inviteMember(roomId = roomId, userId = userId)
    }

    suspend fun listRoomBlacklist(roomId: String): List<RoomBlacklistEntry> {
        return api.listRoomBlacklist(roomId)
    }

    suspend fun blockRoomUser(
        roomId: String,
        userId: String,
    ): RoomBlacklistEntry {
        return api.blockRoomUser(roomId = roomId, userId = userId)
    }

    suspend fun unblockRoomUser(
        roomId: String,
        userId: String,
    ) {
        api.unblockRoomUser(roomId = roomId, userId = userId)
    }

    suspend fun searchUsers(
        query: String,
        limit: Int = 20,
    ): List<UserSummary> {
        return api.searchUsers(query = query, limit = limit)
    }

    suspend fun listJoinRequests(
        roomId: String,
        status: String = "pending",
    ): List<JoinRequest> {
        return api.listJoinRequests(roomId, status = status)
    }

    suspend fun reviewRoomInvite(
        inviteId: String,
        accept: Boolean,
        reason: String? = null,
    ): JoinRoomResult {
        return api.reviewRoomInvite(
            inviteId = inviteId,
            accept = accept,
            reason = reason,
        )
    }

    suspend fun withdrawRoomApplication(requestId: String): RoomApplication {
        return api.withdrawRoomApplication(requestId = requestId)
    }

    suspend fun reviewJoinRequest(
        roomId: String,
        requestId: String,
        approve: Boolean,
    ) {
        api.reviewJoinRequest(
            roomId = roomId,
            requestId = requestId,
            approve = approve,
        )
    }

    suspend fun hasPendingRoomInvites(): Boolean {
        val invites = api.listRoomInvites()
        return invites.isNotEmpty()
    }

    suspend fun hasPendingJoinRequests(
        room: RoomDetail,
        canReviewJoinRequests: Boolean,
    ): Boolean {
        if (!canReviewJoinRequests) return false
        val requests = api.listJoinRequests(room.id)
        return requests.isNotEmpty()
    }

    suspend fun getLiveState(roomId: String): LiveState {
        return api.getLiveState(roomId)
    }

    suspend fun getLiveStateOr(
        roomId: String,
        fallbackLive: LiveState,
    ): LiveState {
        return try {
            api.getLiveState(roomId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            fallbackLive
        }
    }

    fun roomCardFromSnapshot(data: JsonMap): RoomCard? {
        if (data["id"] !is String) return null
        return try {
            RoomCard.fromJson(data)
        } catch (_: Exception) {
            null
        }
    }

    fun upsertRoomCard(rooms: List<RoomCard>, room: RoomCard): List<RoomCard> {
        val idx = rooms.indexOfFirst { item -> item.id == room.id }
        if (idx < 0) return orderedRoomCards(listOf(room) + rooms)
        val next = rooms.toMutableList()
        next[idx] = room
        return orderedRoomCards(next)
    }

    fun orderedRoomCards(rooms: List<RoomCard>): List<RoomCard> {
        val indexed = rooms.withIndex().toMutableList()
        indexed.sortWith { a, b ->
            val pinned = _roomPinnedRank(a.value).compareTo(_roomPinnedRank(b.value))
            if (pinned != 0) return@sortWith pinned

            val unread = _roomUnreadRank(a.value).compareTo(_roomUnreadRank(b.value))
            if (unread != 0) return@sortWith unread

            val live = _roomLiveRank(a.value).compareTo(_roomLiveRank(b.value))
            if (live != 0) return@sortWith live

            val activity =
                _roomActivityTime(
                    b.value,
                ).compareTo(_roomActivityTime(a.value))
            if (activity != 0) return@sortWith activity

            a.index.compareTo(b.index)
        }
        return indexed.map { item -> item.value }
    }

    private fun _roomPinnedRank(room: RoomCard): Int {
        return if (room.isPinned) 0 else 1
    }

    private fun _roomUnreadRank(room: RoomCard): Int {
        if (room.unreadCount <= 0) return 2
        val policy = normalizeRoomNotificationPolicy(room.notificationPolicy)
        if (policy == "silent") return 1
        if (policy == "blocked") return 2
        return 0
    }

    private fun _roomLiveRank(room: RoomCard): Int {
        return if (room.liveParticipantCount > 0) 0 else 1
    }

    private fun _roomActivityTime(room: RoomCard): DateTime {
        if (normalizeRoomNotificationPolicy(room.notificationPolicy) == "blocked") {
            return room.updatedAt
        }
        return room.lastMessage?.createdAt ?: room.updatedAt
    }

    fun patchRoomCardsRefreshed(rooms: List<RoomCard>): RoomCardsPatch {
        return RoomCardsPatch(rooms = orderedRoomCards(rooms))
    }

    fun patchRoomCardUpserted(
        rooms: List<RoomCard>,
        room: RoomCard,
    ): RoomCardsPatch {
        return RoomCardsPatch(rooms = upsertRoomCard(rooms, room))
    }

    fun patchRoomCardUpdated(
        rooms: List<RoomCard>,
        incoming: RoomCard,
        clearUnread: Boolean = false,
    ): RoomCardsPatch {
        return RoomCardsPatch(
            rooms = mergeRoomUpdated(rooms, incoming, clearUnread = clearUnread),
        )
    }

    fun patchRoomUpdated(
        rooms: List<RoomCard>,
        incoming: RoomCard,
        selectedRoom: RoomDetail?,
    ): RoomUpdatedPatch {
        val nextRooms =
            patchRoomCardUpdated(
                rooms = rooms,
                incoming = incoming,
            ).rooms
        val current = selectedRoom
        if (current == null || current.id != incoming.id) {
            return RoomUpdatedPatch(
                rooms = nextRooms,
                selectedRoom = current,
                shouldReloadMembers = false,
            )
        }
        return RoomUpdatedPatch(
            rooms = nextRooms,
            selectedRoom = _mergeSelectedRoomSnapshot(current, incoming),
            shouldReloadMembers =
                current.memberCount != incoming.memberCount ||
                    current.onlineMemberCount != incoming.onlineMemberCount,
        )
    }

    /**
     * PORT: like the Dart constructor call, the merged card for an existing
     * room does not copy `gangBotAvailable` / `gangBotEnabled`, so it falls
     * back to their defaults.
     */
    fun patchRoomDetailApplied(
        rooms: List<RoomCard>,
        detail: RoomDetail,
    ): RoomSelectedDetailPatch {
        val existingIndex = rooms.indexOfFirst { room -> room.id == detail.id }
        val existing = if (existingIndex < 0) null else rooms[existingIndex]
        val card = detail.toCard()
        return RoomSelectedDetailPatch(
            selectedRoom = detail,
            rooms =
                upsertRoomCard(
                    rooms,
                    if (existing == null) {
                        card
                    } else {
                        RoomCard(
                            id = card.id,
                            name = card.name,
                            rid = card.rid,
                            visibility = card.visibility,
                            remarkName = card.remarkName,
                            description = card.description,
                            notificationPolicy = card.notificationPolicy,
                            isPinned = card.isPinned,
                            avatarUrl = card.avatarUrl,
                            defaultAvatarKey = card.defaultAvatarKey,
                            memberCount = card.memberCount,
                            onlineMemberCount = card.onlineMemberCount,
                            liveParticipantCount = card.liveParticipantCount,
                            liveAvatarPreview = card.liveAvatarPreview,
                            lastMessage = existing.lastMessage,
                            unreadCount = existing.unreadCount,
                            hasUnreadCount = existing.hasUnreadCount,
                            unreadMentionCount = existing.unreadMentionCount,
                            hasUnreadMentionCount = existing.hasUnreadMentionCount,
                            hasPendingJoinRequests = existing.hasPendingJoinRequests,
                            aiVoiceAnnouncementsEnabled = card.aiVoiceAnnouncementsEnabled,
                            updatedAt = card.updatedAt,
                        )
                    },
                ),
        )
    }

    fun patchRoomUnreadCleared(
        rooms: List<RoomCard>,
        roomId: String,
    ): RoomCardsPatch {
        val idx = rooms.indexOfFirst { room -> room.id == roomId }
        if (idx < 0 ||
            (rooms[idx].unreadCount == 0 && rooms[idx].unreadMentionCount == 0)
        ) {
            return RoomCardsPatch(rooms = rooms)
        }
        val next = rooms.toMutableList()
        next[idx] = next[idx].copyWith(unreadCount = 0, unreadMentionCount = 0)
        return RoomCardsPatch(rooms = orderedRoomCards(next))
    }

    fun patchSelectedRoomDetailRefreshed(
        rooms: List<RoomCard>,
        selectedRoomId: String?,
        detail: RoomDetail,
    ): RoomSelectedDetailPatch? {
        if (selectedRoomId != detail.id) return null
        return patchRoomDetailApplied(rooms = rooms, detail = detail)
    }

    fun patchSelectedLiveRefreshed(
        live: LiveState,
        selectedRoomId: String?,
        currentLive: LiveState? = null,
    ): RoomLiveRefreshPatch? {
        if (!canApplyLiveRefresh(live = live, selectedRoomId = selectedRoomId)) {
            return null
        }
        if (_isOlderLiveSnapshot(live, currentLive)) return null
        return RoomLiveRefreshPatch(live = live)
    }

    fun removeRoomCard(rooms: List<RoomCard>, roomId: String): List<RoomCard> {
        return rooms.filter { room -> room.id != roomId }
    }

    fun patchRoomDeleted(
        rooms: List<RoomCard>,
        selectedRoomId: String?,
        selectedRoom: RoomDetail?,
        selectedRoomHasPendingJoinRequests: Boolean,
        messages: List<Message>,
        live: LiveState?,
        livePanelOpen: Boolean,
        settingsOpen: Boolean,
        joinedLiveRoomId: String?,
        data: JsonMap,
    ): RoomDeletedPatch? {
        val roomId = data["room_id"] as String? ?: return null

        val wasSelected = selectedRoomId == roomId
        val shouldDisconnectLive = joinedLiveRoomId == roomId
        return RoomDeletedPatch(
            roomId = roomId,
            rooms = removeRoomCard(rooms, roomId),
            selectedRoomId = if (wasSelected) null else selectedRoomId,
            selectedRoom = if (wasSelected) null else selectedRoom,
            selectedRoomHasPendingJoinRequests =
                if (wasSelected) false else selectedRoomHasPendingJoinRequests,
            messages = if (wasSelected) emptyList() else messages,
            live = if (wasSelected) null else live,
            livePanelOpen = if (wasSelected) false else livePanelOpen,
            settingsOpen = if (wasSelected) false else settingsOpen,
            joinedLiveRoomId = if (shouldDisconnectLive) null else joinedLiveRoomId,
            wasSelected = wasSelected,
            shouldDisconnectLive = shouldDisconnectLive,
        )
    }

    fun patchRoomRoleChanged(
        selectedRoom: RoomDetail?,
        data: JsonMap,
    ): RoomRoleChangedPatch? {
        val roomId = data["room_id"] as String?
        val role = data["role"] as String?
        val current = selectedRoom
        if (roomId == null || role == null || current == null) return null
        if (current.id != roomId) return null
        return RoomRoleChangedPatch(
            roomId = roomId,
            selectedRoom = current.copyWithRole(role),
        )
    }

    fun removeRoomInvite(invites: List<RoomInvite>, inviteId: String): List<RoomInvite> {
        return invites.filter { invite -> invite.id != inviteId }
    }

    fun removeJoinRequest(
        requests: List<JoinRequest>,
        requestId: String,
    ): List<JoinRequest> {
        return requests.filter { request -> request.id != requestId }
    }

    fun mergeRoomUpdated(
        rooms: List<RoomCard>,
        incoming: RoomCard,
        clearUnread: Boolean = false,
    ): List<RoomCard> {
        val idx = rooms.indexOfFirst { room -> room.id == incoming.id }
        if (idx < 0) return upsertRoomCard(rooms, incoming)

        val existing = rooms[idx]
        val next = rooms.toMutableList()
        // Public room snapshots do not carry local per-user fields, so keep the
        // local unread count, but bump it when a fresh last_message arrives for a
        // room the user is not currently reading.
        val unreadCount =
            if (clearUnread) 0 else _mergedUnreadCount(existing = existing, incoming = incoming)
        val unreadMentionCount =
            if (clearUnread) {
                0
            } else {
                _mergedUnreadMentionCount(existing = existing, incoming = incoming)
            }
        next[idx] =
            _mergeRoomCard(
                existing,
                incoming,
                unreadCount = unreadCount,
                unreadMentionCount = unreadMentionCount,
            )
        return orderedRoomCards(next)
    }

    private fun _mergedUnreadCount(
        existing: RoomCard,
        incoming: RoomCard,
    ): Int {
        if (incoming.hasUnreadCount) return incoming.unreadCount
        val incomingLastMessage = incoming.lastMessage ?: return existing.unreadCount
        if (normalizeRoomNotificationPolicy(existing.notificationPolicy) == "blocked") {
            return 0
        }
        if (existing.lastMessage?.id == incomingLastMessage.id) {
            return existing.unreadCount
        }
        return existing.unreadCount + 1
    }

    private fun _mergedUnreadMentionCount(
        existing: RoomCard,
        incoming: RoomCard,
    ): Int {
        if (incoming.hasUnreadMentionCount) return incoming.unreadMentionCount
        return existing.unreadMentionCount
    }

    private fun _mergeRoomCard(
        existing: RoomCard,
        incoming: RoomCard,
        unreadCount: Int,
        unreadMentionCount: Int,
    ): RoomCard {
        return RoomCard(
            id = incoming.id,
            name = incoming.name,
            rid = incoming.rid,
            visibility = incoming.visibility,
            remarkName = existing.remarkName,
            description = incoming.description,
            notificationPolicy = existing.notificationPolicy,
            isPinned = existing.isPinned,
            avatarUrl = incoming.avatarUrl,
            defaultAvatarKey = incoming.defaultAvatarKey,
            memberCount = incoming.memberCount,
            onlineMemberCount = incoming.onlineMemberCount,
            liveParticipantCount = incoming.liveParticipantCount,
            liveAvatarPreview = incoming.liveAvatarPreview,
            lastMessage = incoming.lastMessage,
            unreadCount = unreadCount,
            hasUnreadCount = true,
            unreadMentionCount = unreadMentionCount,
            hasUnreadMentionCount = true,
            hasPendingJoinRequests = incoming.hasPendingJoinRequests,
            aiVoiceAnnouncementsEnabled = incoming.aiVoiceAnnouncementsEnabled,
            gangBotAvailable = incoming.gangBotAvailable,
            gangBotEnabled = incoming.gangBotEnabled,
            updatedAt = incoming.updatedAt,
        )
    }

    private fun _mergeSelectedRoomSnapshot(room: RoomDetail, incoming: RoomCard): RoomDetail {
        return RoomDetail(
            id = room.id,
            name = incoming.name,
            rid = incoming.rid,
            visibility = incoming.visibility,
            description = incoming.description,
            joinPolicy = room.joinPolicy,
            remarkName = room.remarkName,
            notificationPolicy = room.notificationPolicy,
            isPinned = room.isPinned,
            personalProfile = room.personalProfile,
            aiVoiceAnnouncementsEnabled = incoming.aiVoiceAnnouncementsEnabled,
            messageRecallPolicy = room.messageRecallPolicy,
            messageRecallWindowSeconds = room.messageRecallWindowSeconds,
            canDeleteRoom = room.canDeleteRoom,
            gangBotAvailable = incoming.gangBotAvailable,
            gangBotEnabled = incoming.gangBotEnabled,
            avatarUrl = incoming.avatarUrl,
            defaultAvatarKey = incoming.defaultAvatarKey,
            memberCount = incoming.memberCount,
            onlineMemberCount = incoming.onlineMemberCount,
            createdBy = room.createdBy,
            myMembership = room.myMembership,
            live = room.live,
            createdAt = room.createdAt,
            updatedAt = incoming.updatedAt,
        )
    }

    /**
     * PORT: like the Dart constructor call, the count-patched card does not
     * copy `gangBotAvailable` / `gangBotEnabled`, so it falls back to their
     * defaults.
     */
    fun patchLiveSnapshot(
        rooms: List<RoomCard>,
        selectedRoomId: String?,
        data: JsonMap,
        joinedLiveRoomId: String? = null,
        currentUserId: String? = null,
        previousLive: LiveState? = null,
        localParticipantConnected: Boolean = false,
    ): RoomLiveSnapshotPatch? {
        val roomId = data["room_id"] as String? ?: return null

        @Suppress("UNCHECKED_CAST")
        val liveJson = data["live"] as Map<String, Any?>?
        val count = (data["participant_count"] as Number?)?.toInt()
        val previewJson = data["preview"] as List<*>?
        @Suppress("UNCHECKED_CAST")
        val preview = previewJson?.map { item -> UserSummary.fromJson(item as Map<String, Any?>) }
        val parsedSelectedLive =
            if (selectedRoomId == roomId && liveJson != null) LiveState.fromJson(liveJson) else null
        val staleSelectedLive =
            parsedSelectedLive != null &&
                _isOlderLiveSnapshot(parsedSelectedLive, previousLive)

        var nextRooms = rooms
        val idx = rooms.indexOfFirst { room -> room.id == roomId }
        if (idx >= 0 && count != null) {
            val existing = rooms[idx]
            val effectiveCount =
                if (staleSelectedLive) previousLive!!.participantCount else count
            val patched = rooms.toMutableList()
            patched[idx] =
                RoomCard(
                    id = existing.id,
                    name = existing.name,
                    rid = existing.rid,
                    visibility = existing.visibility,
                    remarkName = existing.remarkName,
                    description = existing.description,
                    notificationPolicy = existing.notificationPolicy,
                    isPinned = existing.isPinned,
                    avatarUrl = existing.avatarUrl,
                    defaultAvatarKey = existing.defaultAvatarKey,
                    memberCount = existing.memberCount,
                    onlineMemberCount = existing.onlineMemberCount,
                    liveParticipantCount = effectiveCount,
                    liveAvatarPreview =
                        if (staleSelectedLive) {
                            existing.liveAvatarPreview
                        } else {
                            preview ?: existing.liveAvatarPreview
                        },
                    lastMessage = existing.lastMessage,
                    unreadCount = existing.unreadCount,
                    hasUnreadCount = existing.hasUnreadCount,
                    unreadMentionCount = existing.unreadMentionCount,
                    hasUnreadMentionCount = existing.hasUnreadMentionCount,
                    hasPendingJoinRequests = existing.hasPendingJoinRequests,
                    aiVoiceAnnouncementsEnabled = existing.aiVoiceAnnouncementsEnabled,
                    updatedAt = existing.updatedAt,
                )
            nextRooms = orderedRoomCards(patched)
        }

        var selectedLive = if (staleSelectedLive) null else parsedSelectedLive
        if (selectedLive != null && previousLive?.roomId == roomId) {
            selectedLive =
                _mergeLiveParticipantUsers(
                    selectedLive,
                    previousLive = previousLive,
                )
        }

        // Self-preservation: a snapshot computed server-side before our own join
        // (SSE delivery and the LiveKit webhook can lag/reorder) can arrive right
        // after we join and drop us from the roster — making us briefly invisible
        // in our own channel. While we're joined to this room, if the snapshot
        // omits us but our prior state had us, re-insert our own participant from
        // that prior state. A later, correct snapshot reconciles.
        if (selectedLive != null &&
            joinedLiveRoomId == roomId &&
            currentUserId != null &&
            localParticipantConnected
        ) {
            val present =
                selectedLive.participants.any { p -> p.user.id == currentUserId }
            if (!present) {
                var self: LiveParticipant? = null
                if (previousLive?.roomId == roomId) {
                    for (p in previousLive.participants) {
                        if (p.user.id == currentUserId) {
                            self = p
                            break
                        }
                    }
                }
                if (self != null) {
                    selectedLive =
                        LiveState(
                            roomId = selectedLive.roomId,
                            participantCount = selectedLive.participantCount + 1,
                            participants = selectedLive.participants + self,
                            updatedAt = selectedLive.updatedAt,
                        )
                }
            }
        }

        // The selected-room roster may have been reconciled above (most notably
        // by retaining a locally connected participant while an older server
        // snapshot is in flight). Derive the room-card count and preview from that
        // exact same snapshot so the outside and inside views cannot diverge.
        if (selectedLive != null) {
            nextRooms =
                com.gangchat.client.app.patchRoomLiveCount(
                    rooms = nextRooms,
                    roomId = roomId,
                    live = selectedLive,
                )
        }

        return RoomLiveSnapshotPatch(
            roomId = roomId,
            rooms = nextRooms,
            selectedLive = selectedLive,
        )
    }

    private fun _isOlderLiveSnapshot(incoming: LiveState, current: LiveState?): Boolean {
        return current?.roomId == incoming.roomId &&
            incoming.updatedAt.isBefore(current.updatedAt)
    }

    private fun _mergeLiveParticipantUsers(
        live: LiveState,
        previousLive: LiveState,
    ): LiveState {
        val previousByUserId = LinkedHashMap<String, LiveParticipant>()
        for (participant in previousLive.participants) {
            previousByUserId[participant.user.id] = participant
        }
        var changed = false
        val participants =
            live.participants.map { participant ->
                val previous = previousByUserId[participant.user.id]
                if (previous != null) {
                    _mergeLiveParticipantUser(
                        participant,
                        previous,
                        changed = { changed = true },
                    )
                } else {
                    participant
                }
            }
        if (!changed) return live
        return LiveState(
            roomId = live.roomId,
            participantCount = live.participantCount,
            participants = participants,
            updatedAt = live.updatedAt,
        )
    }

    private fun _mergeLiveParticipantUser(
        participant: LiveParticipant,
        previous: LiveParticipant,
        changed: () -> Unit,
    ): LiveParticipant {
        val mergedUser = participant.user.mergeMissing(previous.user)
        if (mergedUser === participant.user) return participant
        changed()
        return participant.copyWith(user = mergedUser)
    }

    fun patchRoomLiveCount(
        rooms: List<RoomCard>,
        roomId: String,
        live: LiveState,
    ): List<RoomCard> {
        // Qualified so it calls the room_live_state function, not this method.
        return com.gangchat.client.app.patchRoomLiveCount(
            rooms = rooms,
            roomId = roomId,
            live = live,
        )
    }

    fun withLive(room: RoomCard, live: LiveState): RoomCard {
        return roomCardWithLive(room, live)
    }
}

private fun _nonEmpty(value: String?): String? {
    val text = value?.trim()
    if (text == null || text.isEmpty()) return null
    return text
}
