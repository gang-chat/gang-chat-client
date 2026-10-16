package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.UserSummary

enum class RoomMemberPresence { live, online, offline }

enum class RoomMemberPresenceFilter { all, live, online, offline }

enum class RoomMemberRoleFilter { all, member, admin, creator }

class RoomMemberPermissionState(
    val isSuperuser: Boolean,
    val isOwner: Boolean,
    val isAdmin: Boolean,
    val canEditRoomDisplayName: Boolean,
    val canRoleEdit: Boolean,
    val canRemoveMember: Boolean,
) {
    val canSetAdmin: Boolean get() = canRoleEdit && !isAdmin
    val canUnsetAdmin: Boolean get() = canRoleEdit && isAdmin
    val canTransferCreator: Boolean get() = canRoleEdit
    val adminActionLabel: String get() = if (isAdmin) "移除管理员" else "设为管理员"
}

class RoomMemberManagementPatch(
    val room: RoomDetail,
    val members: List<RoomMember>,
    val busyMemberIds: Set<String>,
    val changed: Boolean,
    val error: String?,
    val notice: String?,
)

class RoomMemberLoadPatch(
    val members: List<RoomMember>,
    val loading: Boolean,
    val error: String?,
)

class RoomMemberFilterPatch(
    val searchQuery: String,
    val presenceFilter: RoomMemberPresenceFilter,
    val roleFilter: RoomMemberRoleFilter,
)

class RoomMemberPresenceGroup(
    val presence: RoomMemberPresence,
    val members: List<RoomMember>,
) {
    val count: Int get() = members.size
}

class RoomMemberFilterCounts(
    val allPresence: Int,
    val live: Int,
    val online: Int,
    val offline: Int,
    val allRoles: Int,
    val roleMembers: Int,
    val admins: Int,
    val creators: Int,
)

fun canStartRoomMemberAction(
    userId: String,
    busyMemberIds: Iterable<String>,
): Boolean {
    return !busyMemberIds.contains(userId)
}

fun canOpenRoomMemberManagementFromProfile(
    room: RoomDetail,
    currentUser: CurrentUser,
    target: UserSummary,
): Boolean {
    val uid = target.uid?.trim()
    if (uid == null || uid.isEmpty() || target.id == currentUser.id) {
        return false
    }
    val role =
        target.roomRole ?: if (target.id == room.createdBy?.id) "owner" else "member"
    val permission =
        roomMemberPermissionState(
            member =
                RoomMember(
                    user = target,
                    role = role,
                    joinedAt = DateTime.fromMillisecondsSinceEpoch(0, isUtc = true),
                ),
            currentUser = currentUser,
            canEditCreatorOnly =
                room.isCreator || room.isSuperuser || currentUser.isSuperuser,
            canManageMembers = room.isAdmin || currentUser.isSuperuser,
            ownerUserId = room.createdBy?.id,
        )
    return permission.canRoleEdit || permission.canRemoveMember
}

fun roomMemberActionBusy(
    member: RoomMember,
    busyMemberIds: Iterable<String>,
): Boolean {
    return busyMemberIds.contains(member.user.id)
}

fun roomMemberRoleUpdateNotice(role: String): String {
    return if (role == "admin") "已授予管理员身份" else "已移除管理员身份"
}

fun roomMemberRoleUpdateConfirmTitle(role: String): String {
    return if (role == "admin") "设为管理员" else "移除管理员"
}

fun roomMemberRoleUpdateConfirmBody(member: RoomMember, role: String): String {
    val name = roomMemberDisplayName(member)
    return if (role == "admin") "确定要将 $name 设为管理员吗？" else "确定要移除 $name 的管理员身份吗？"
}

fun roomMemberRoleUpdateConfirmLabel(role: String): String {
    return if (role == "admin") "设为管理员" else "移除"
}

fun transferCreatorDialogTitle(): String {
    return "转让创建者"
}

fun transferCreatorConfirmBody(
    member: RoomMember,
    currentUserIsCreator: Boolean,
): String {
    val demotedCreator = if (currentUserIsCreator) "你" else "原创建者"
    return "创建者身份会转让给 ${roomMemberDisplayName(member)}，${demotedCreator}将成为管理员。"
}

fun transferCreatorConfirmLabel(): String {
    return "转让"
}

fun transferCreatorSuccessNotice(): String {
    return "创建者已转让"
}

fun removeRoomMemberConfirmTitle(): String {
    return "踢出此用户"
}

fun removeRoomMemberConfirmBody(member: RoomMember): String {
    return "确定要将 ${roomMemberDisplayName(member)} 从房间中移除吗？"
}

fun removeRoomMemberConfirmLabel(): String {
    return "踢出"
}

fun removeRoomMemberSuccessNotice(member: RoomMember): String {
    return "已踢出 ${roomMemberDisplayName(member)}"
}

fun roomMembersLoadStarted(members: Iterable<RoomMember>): RoomMemberLoadPatch {
    return RoomMemberLoadPatch(
        members = members.toList(),
        loading = true,
        error = null,
    )
}

fun roomMembersLoadSucceeded(members: Iterable<RoomMember>): RoomMemberLoadPatch {
    return RoomMemberLoadPatch(
        members = members.toList(),
        loading = false,
        error = null,
    )
}

fun roomMembersLoadFailed(
    members: Iterable<RoomMember>,
    failure: Any,
): RoomMemberLoadPatch {
    return RoomMemberLoadPatch(
        members = members.toList(),
        loading = false,
        error = userFacingErrorMessage(failure),
    )
}

fun roomMemberPresenceFilterChanged(
    searchQuery: String,
    roleFilter: RoomMemberRoleFilter,
    presenceFilter: RoomMemberPresenceFilter,
): RoomMemberFilterPatch {
    return RoomMemberFilterPatch(
        searchQuery = searchQuery,
        presenceFilter = presenceFilter,
        roleFilter = roleFilter,
    )
}

fun roomMemberRoleFilterChanged(
    searchQuery: String,
    presenceFilter: RoomMemberPresenceFilter,
    roleFilter: RoomMemberRoleFilter,
): RoomMemberFilterPatch {
    return RoomMemberFilterPatch(
        searchQuery = searchQuery,
        presenceFilter = presenceFilter,
        roleFilter = roleFilter,
    )
}

fun roomMemberSearchQueryChanged(
    searchQuery: String,
    presenceFilter: RoomMemberPresenceFilter,
    roleFilter: RoomMemberRoleFilter,
): RoomMemberFilterPatch {
    return RoomMemberFilterPatch(
        searchQuery = searchQuery,
        presenceFilter = presenceFilter,
        roleFilter = roleFilter,
    )
}

fun roomMemberManagementActionStarted(
    room: RoomDetail,
    members: Iterable<RoomMember>,
    changed: Boolean,
    userId: String,
    busyMemberIds: Iterable<String>,
): RoomMemberManagementPatch {
    return RoomMemberManagementPatch(
        room = room,
        members = members.toList(),
        busyMemberIds = LinkedHashSet<String>().apply {
            addAll(busyMemberIds)
            add(userId)
        },
        changed = changed,
        error = null,
        notice = null,
    )
}

fun roomMemberRoleUpdateSucceeded(
    room: RoomDetail,
    members: Iterable<RoomMember>,
    updated: RoomMember,
    role: String,
    busyMemberIds: Iterable<String>,
): RoomMemberManagementPatch {
    return RoomMemberManagementPatch(
        room = room,
        members = replaceRoomMember(members, updated),
        busyMemberIds = _withoutId(busyMemberIds, updated.user.id),
        changed = true,
        error = null,
        notice = roomMemberRoleUpdateNotice(role),
    )
}

fun roomMemberRoleUpdateFailed(
    room: RoomDetail,
    members: Iterable<RoomMember>,
    changed: Boolean,
    userId: String,
    busyMemberIds: Iterable<String>,
    failure: Any,
): RoomMemberManagementPatch {
    return RoomMemberManagementPatch(
        room = room,
        members = members.toList(),
        busyMemberIds = _withoutId(busyMemberIds, userId),
        changed = changed,
        error = userFacingErrorMessage(failure),
        notice = null,
    )
}

fun roomMemberRemovedSucceeded(
    room: RoomDetail,
    members: Iterable<RoomMember>,
    removed: RoomMember,
    busyMemberIds: Iterable<String>,
): RoomMemberManagementPatch {
    return RoomMemberManagementPatch(
        room = room,
        members = members.filter { member -> member.user.id != removed.user.id },
        busyMemberIds = _withoutId(busyMemberIds, removed.user.id),
        changed = true,
        error = null,
        notice = removeRoomMemberSuccessNotice(removed),
    )
}

fun roomMemberRemoveFailed(
    room: RoomDetail,
    members: Iterable<RoomMember>,
    changed: Boolean,
    userId: String,
    busyMemberIds: Iterable<String>,
    failure: Any,
): RoomMemberManagementPatch {
    return RoomMemberManagementPatch(
        room = room,
        members = members.toList(),
        busyMemberIds = _withoutId(busyMemberIds, userId),
        changed = changed,
        error = userFacingErrorMessage(failure),
        notice = null,
    )
}

fun transferCreatorSucceeded(
    updatedRoom: RoomDetail,
    members: Iterable<RoomMember>,
    userId: String,
    busyMemberIds: Iterable<String>,
): RoomMemberManagementPatch {
    return RoomMemberManagementPatch(
        room = updatedRoom,
        members = members.toList(),
        busyMemberIds = _withoutId(busyMemberIds, userId),
        changed = true,
        error = null,
        notice = transferCreatorSuccessNotice(),
    )
}

fun transferCreatorFailed(
    room: RoomDetail,
    members: Iterable<RoomMember>,
    changed: Boolean,
    userId: String,
    busyMemberIds: Iterable<String>,
    failure: Any,
): RoomMemberManagementPatch {
    return RoomMemberManagementPatch(
        room = room,
        members = members.toList(),
        busyMemberIds = _withoutId(busyMemberIds, userId),
        changed = changed,
        error = userFacingErrorMessage(failure),
        notice = null,
    )
}

/** Dart's `{for (final item in busyMemberIds) if (item != userId) item}`. */
private fun _withoutId(busyMemberIds: Iterable<String>, userId: String): Set<String> {
    val result = LinkedHashSet<String>()
    for (item in busyMemberIds) {
        if (item != userId) result.add(item)
    }
    return result
}

fun roomMemberPresenceLabel(presence: RoomMemberPresence): String {
    return when (presence) {
        RoomMemberPresence.live -> "语音"
        RoomMemberPresence.online -> "在线"
        RoomMemberPresence.offline -> "离线"
    }
}

fun roomMemberFilterCounts(
    members: Iterable<RoomMember>,
    live: LiveState,
    ownerUserId: String? = null,
): RoomMemberFilterCounts {
    var allPresence = 0
    var liveCount = 0
    var online = 0
    var offline = 0
    var allRoles = 0
    var roleMembers = 0
    var admins = 0
    var creators = 0

    for (member in members) {
        allPresence += 1
        allRoles += 1

        val presence = roomMemberPresence(member, live = live)
        when (presence) {
            RoomMemberPresence.live -> {
                liveCount += 1
                online += 1
            }
            RoomMemberPresence.online -> online += 1
            RoomMemberPresence.offline -> offline += 1
        }

        if (_matchesRoleFilter(
                member,
                RoomMemberRoleFilter.member,
                ownerUserId = ownerUserId,
            )
        ) {
            roleMembers += 1
        }
        if (_matchesRoleFilter(
                member,
                RoomMemberRoleFilter.admin,
                ownerUserId = ownerUserId,
            )
        ) {
            admins += 1
        }
        if (_matchesRoleFilter(
                member,
                RoomMemberRoleFilter.creator,
                ownerUserId = ownerUserId,
            )
        ) {
            creators += 1
        }
    }

    return RoomMemberFilterCounts(
        allPresence = allPresence,
        live = liveCount,
        online = online,
        offline = offline,
        allRoles = allRoles,
        roleMembers = roleMembers,
        admins = admins,
        creators = creators,
    )
}

fun roomMemberPresenceFilterLabel(
    filter: RoomMemberPresenceFilter,
    counts: RoomMemberFilterCounts,
): String {
    val (label, count) =
        when (filter) {
            RoomMemberPresenceFilter.all -> Pair("全部", counts.allPresence)
            RoomMemberPresenceFilter.live -> Pair("语音", counts.live)
            RoomMemberPresenceFilter.online -> Pair("在线", counts.online)
            RoomMemberPresenceFilter.offline -> Pair("离线", counts.offline)
        }
    return "$label $count"
}

fun roomMemberRoleFilterLabel(
    filter: RoomMemberRoleFilter,
    counts: RoomMemberFilterCounts,
): String {
    val (label, count) =
        when (filter) {
            RoomMemberRoleFilter.all -> Pair("所有身份", counts.allRoles)
            RoomMemberRoleFilter.member -> Pair("成员", counts.roleMembers)
            RoomMemberRoleFilter.admin -> Pair("管理员", counts.admins)
            RoomMemberRoleFilter.creator -> Pair("创建者", counts.creators)
        }
    return "$label $count"
}

fun visibleRoomMembers(
    members: Iterable<RoomMember>,
    live: LiveState,
    presenceFilter: RoomMemberPresenceFilter,
    roleFilter: RoomMemberRoleFilter,
    query: String,
    ownerUserId: String? = null,
): List<RoomMember> {
    val visible =
        filteredRoomMembers(
            members = members,
            live = live,
            presenceFilter = presenceFilter,
            roleFilter = roleFilter,
            query = query,
            ownerUserId = ownerUserId,
        ).toMutableList()
    val normalizedQuery = query.trim().lowercase()
    visible.sortWith { a, b ->
        compareRoomMembers(
            a,
            b,
            live = live,
            query = normalizedQuery,
            ownerUserId = ownerUserId,
        )
    }
    return visible
}

fun filteredRoomMembers(
    members: Iterable<RoomMember>,
    live: LiveState,
    presenceFilter: RoomMemberPresenceFilter,
    roleFilter: RoomMemberRoleFilter,
    query: String,
    ownerUserId: String? = null,
): List<RoomMember> {
    val normalizedQuery = query.trim().lowercase()
    return members.filter { member ->
        val presence = roomMemberPresence(member, live = live)
        if (!_matchesPresenceFilter(presence, presenceFilter)) {
            return@filter false
        }
        if (!_matchesRoleFilter(member, roleFilter, ownerUserId = ownerUserId)) {
            return@filter false
        }
        if (normalizedQuery.isEmpty()) return@filter true
        roomMemberSearchRank(member, normalizedQuery) < 99
    }
}

fun orderRoomMembersByUserIds(
    members: Iterable<RoomMember>,
    orderedUserIds: Iterable<String>,
): List<RoomMember> {
    val remaining = LinkedHashMap<String, RoomMember>()
    for (member in members) remaining[member.user.id] = member
    val ordered = mutableListOf<RoomMember>()
    for (userId in orderedUserIds) {
        val member = remaining.remove(userId)
        if (member != null) ordered.add(member)
    }
    ordered.addAll(remaining.values)
    return ordered
}

fun roomMemberPresenceGroups(
    members: Iterable<RoomMember>,
    live: LiveState,
): List<RoomMemberPresenceGroup> {
    val buckets = LinkedHashMap<RoomMemberPresence, MutableList<RoomMember>>()
    for (presence in RoomMemberPresence.entries) buckets[presence] = mutableListOf()
    for (member in members) {
        buckets[roomMemberPresence(member, live = live)]!!.add(member)
    }
    val groups = mutableListOf<RoomMemberPresenceGroup>()
    for (presence in RoomMemberPresence.entries) {
        if (buckets[presence]!!.isNotEmpty()) {
            groups.add(
                RoomMemberPresenceGroup(
                    presence = presence,
                    members = buckets[presence]!!.toList(),
                ),
            )
        }
    }
    return groups
}

private fun _matchesPresenceFilter(
    presence: RoomMemberPresence,
    filter: RoomMemberPresenceFilter,
): Boolean {
    return when (filter) {
        RoomMemberPresenceFilter.all -> true
        RoomMemberPresenceFilter.live -> presence == RoomMemberPresence.live
        RoomMemberPresenceFilter.online -> presence != RoomMemberPresence.offline
        RoomMemberPresenceFilter.offline -> presence == RoomMemberPresence.offline
    }
}

fun roomMembersWithCurrentUserPresence(
    members: Iterable<RoomMember>,
    currentUserId: String,
): List<RoomMember> {
    return members.map { member ->
        roomMemberWithCurrentUserPresence(member, currentUserId = currentUserId)
    }
}

fun roomMemberWithCurrentUserPresence(
    member: RoomMember,
    currentUserId: String,
): RoomMember {
    if (currentUserId.isEmpty() || member.user.id != currentUserId) {
        return member
    }
    if (member.isOnline == true && member.user.isOnline == true) {
        return member
    }
    return RoomMember(
        user = member.user.copyWith(isOnline = true),
        role = member.role,
        joinedAt = member.joinedAt,
        roomDisplayName = member.roomDisplayName,
        remarkName = member.remarkName,
        textMutedUntil = member.textMutedUntil,
        isOnline = true,
    )
}

fun roomMemberPresence(
    member: RoomMember,
    live: LiveState,
): RoomMemberPresence {
    val inLive = live.participants.any { p -> p.user.id == member.user.id }
    if (inLive) return RoomMemberPresence.live
    if (member.isOnline ?: false) return RoomMemberPresence.online
    return RoomMemberPresence.offline
}

fun compareRoomMembers(
    a: RoomMember,
    b: RoomMember,
    live: LiveState,
    query: String,
    ownerUserId: String? = null,
): Int {
    val presence =
        _presenceRank(roomMemberPresence(a, live = live)) -
            _presenceRank(roomMemberPresence(b, live = live))
    if (presence != 0) return presence

    val role =
        roomMemberRoleRank(a, ownerUserId = ownerUserId) -
            roomMemberRoleRank(b, ownerUserId = ownerUserId)
    if (role != 0) return role

    if (query.isNotEmpty()) {
        val search =
            roomMemberSearchRank(a, query) - roomMemberSearchRank(b, query)
        if (search != 0) return search
    }

    val name =
        roomMemberDisplayName(a).lowercase().compareTo(roomMemberDisplayName(b).lowercase())
    if (name != 0) return name
    return (a.user.uid ?: a.user.id).compareTo(b.user.uid ?: b.user.id)
}

fun roomMemberRoleRank(member: RoomMember, ownerUserId: String? = null): Int {
    if (isSuperuserRoomMember(member)) return 0
    if (isOwnerRoomMember(member, ownerUserId = ownerUserId)) return 1
    if (isAdminRoomMember(member)) return 2
    return 3
}

fun isSuperuserRoomMember(member: RoomMember): Boolean {
    val role = member.role.lowercase()
    return member.user.isSuperuser || role == "superuser"
}

fun isOwnerRoomMember(member: RoomMember, ownerUserId: String? = null): Boolean {
    val role = member.role.lowercase()
    return member.user.id == ownerUserId || role == "owner" || role == "creator"
}

fun isAdminRoomMember(member: RoomMember): Boolean {
    val role = member.role.lowercase()
    return role == "admin" || role == "administrator"
}

fun roomMemberPermissionState(
    member: RoomMember,
    currentUser: CurrentUser,
    canEditCreatorOnly: Boolean,
    canManageMembers: Boolean = false,
    ownerUserId: String? = null,
): RoomMemberPermissionState {
    val isSuperuser = isSuperuserRoomMember(member)
    val isOwner = isOwnerRoomMember(member, ownerUserId = ownerUserId)
    val isAdmin = isAdminRoomMember(member)
    val currentUserIsSuperuser = currentUser.isSuperuser
    val currentRank =
        currentUserPrivilegeRank(
            currentUser = currentUser,
            canEditCreatorOnly = canEditCreatorOnly,
            canManageMembers = canManageMembers,
        )
    val targetRank = roomMemberPrivilegeRank(member, ownerUserId = ownerUserId)
    val canEditRoomDisplayName =
        canManageMembers &&
            member.user.id != currentUser.id &&
            currentRank > targetRank
    val canRoleEdit =
        canEditCreatorOnly &&
            !isSuperuser &&
            !isOwner &&
            member.user.id != currentUser.id
    val canRemoveMember =
        canManageMembers &&
            !isSuperuser &&
            (!isOwner || currentUserIsSuperuser) &&
            member.user.id != currentUser.id &&
            (canEditCreatorOnly || !isAdmin)
    return RoomMemberPermissionState(
        isSuperuser = isSuperuser,
        isOwner = isOwner,
        isAdmin = isAdmin,
        canEditRoomDisplayName = canEditRoomDisplayName,
        canRoleEdit = canRoleEdit,
        canRemoveMember = canRemoveMember,
    )
}

fun currentUserPrivilegeRank(
    currentUser: CurrentUser,
    canEditCreatorOnly: Boolean,
    canManageMembers: Boolean,
): Int {
    if (currentUser.isSuperuser) return 4
    if (canEditCreatorOnly) return 3
    if (canManageMembers) return 2
    return 1
}

fun roomMemberPrivilegeRank(member: RoomMember, ownerUserId: String? = null): Int {
    if (isSuperuserRoomMember(member)) return 4
    if (isOwnerRoomMember(member, ownerUserId = ownerUserId)) return 3
    if (isAdminRoomMember(member)) return 2
    return 1
}

fun roomMemberDisplayName(member: RoomMember): String {
    return _nonEmpty(member.roomDisplayName)
        ?: _nonEmpty(member.user.roomDisplayName)
        ?: _nonEmpty(member.user.displayName)
        ?: member.user.username
}

fun roomMemberDefaultDisplayName(member: RoomMember): String {
    return _nonEmpty(member.user.displayName) ?: member.user.username
}

fun roomMemberRoomDisplayNameValue(member: RoomMember): String {
    return _nonEmpty(member.roomDisplayName)
        ?: _nonEmpty(member.user.roomDisplayName)
        ?: ""
}

fun roomMemberRoomDisplayNameOriginalLabel(member: RoomMember): String {
    return roomMemberDisplayName(member)
}

fun roomMemberRoomDisplayNameUpdatedNotice(member: RoomMember): String {
    return "已修改 ${roomMemberDefaultDisplayName(member)} 的房间内用户名"
}

fun roomMemberSearchRank(member: RoomMember, query: String): Int {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) return 0

    fun contains(value: String?): Boolean {
        val text = _nonEmpty(value)?.lowercase()
        return text != null && text.contains(normalizedQuery)
    }

    if (contains(member.user.uid) || contains(member.user.id)) return 0
    if (contains(member.user.displayName) || contains(member.user.username)) {
        return 1
    }
    if (contains(member.remarkName)) return 2
    return 99
}

fun roomMemberMeta(member: RoomMember): String {
    val uid = member.user.uid ?: member.user.id
    val remark = _nonEmpty(member.remarkName)
    val parts = mutableListOf(uid, "@${member.user.username}")
    if (remark != null) parts.add("备注 $remark")
    return parts.joinToString(" · ")
}

fun replaceRoomMember(
    members: Iterable<RoomMember>,
    updated: RoomMember,
): List<RoomMember> {
    return members.map { member ->
        if (member.user.id == updated.user.id) updated else member
    }
}

fun roomMemberWithRole(member: RoomMember, role: String): RoomMember {
    return RoomMember(
        user = member.user.copyWith(roomRole = role),
        role = role,
        joinedAt = member.joinedAt,
        roomDisplayName = member.roomDisplayName,
        remarkName = member.remarkName,
        textMutedUntil = member.textMutedUntil,
        isOnline = member.isOnline,
    )
}

private fun _matchesRoleFilter(
    member: RoomMember,
    filter: RoomMemberRoleFilter,
    ownerUserId: String? = null,
): Boolean {
    return when (filter) {
        RoomMemberRoleFilter.all -> true
        RoomMemberRoleFilter.member ->
            !isSuperuserRoomMember(member) &&
                !isOwnerRoomMember(member, ownerUserId = ownerUserId) &&
                !isAdminRoomMember(member)
        RoomMemberRoleFilter.admin ->
            isSuperuserRoomMember(member) ||
                isOwnerRoomMember(member, ownerUserId = ownerUserId) ||
                isAdminRoomMember(member)
        RoomMemberRoleFilter.creator ->
            !isSuperuserRoomMember(member) &&
                isOwnerRoomMember(member, ownerUserId = ownerUserId)
    }
}

private fun _presenceRank(value: RoomMemberPresence): Int {
    return when (value) {
        RoomMemberPresence.live -> 0
        RoomMemberPresence.online -> 1
        RoomMemberPresence.offline -> 2
    }
}

private fun _nonEmpty(value: String?): String? {
    val text = value?.trim()
    if (text == null || text.isEmpty()) return null
    return text
}
