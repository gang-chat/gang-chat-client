package com.gangchat.client.app

import com.gangchat.client.protocol.RoomDetail

class CreateRoomDraft private constructor(val name: String?, val error: String?) {
    val isValid: Boolean get() = error == null

    companion object {
        /** Dart's `CreateRoomDraft.invalid(error)`. */
        fun invalid(error: String): CreateRoomDraft = CreateRoomDraft(name = null, error = error)

        /** Dart's `CreateRoomDraft.valid(name: ...)`. */
        fun valid(name: String): CreateRoomDraft = CreateRoomDraft(name = name, error = null)
    }
}

class CreateRoomDialogPatch(val busy: Boolean, val error: String?)

class RoomProfileUpdateDraft(
    val remarkName: String,
    val notificationPolicy: String,
    val roomDisplayName: String,
    val avatarAssetId: String?,
    val defaultAvatarKey: String,
)

class RoomProfileDialogStatePatch(
    val pendingAvatarAssetId: String?,
    val pendingAvatarUrl: String?,
    val usingGlobalProfile: Boolean,
    val usingProfilePresetAvatar: Boolean,
    val defaultAvatarKey: String,
    val saving: Boolean,
    val leaving: Boolean,
    val uploadingAvatar: Boolean,
    val error: String?,
    val notice: String?,
)

class RoomProfileNotificationPolicyPatch(val notificationPolicy: String)

class RoomInfoUpdateDraft private constructor(
    val name: String?,
    val description: String?,
    val visibility: String?,
    val joinPolicy: String?,
    val avatarAssetId: String?,
    val defaultAvatarKey: String?,
    val error: String?,
) {
    val isValid: Boolean get() = error == null

    companion object {
        /** Dart's `RoomInfoUpdateDraft.invalid(error)`. */
        fun invalid(error: String): RoomInfoUpdateDraft =
            RoomInfoUpdateDraft(
                name = null,
                description = null,
                visibility = null,
                joinPolicy = null,
                avatarAssetId = null,
                defaultAvatarKey = null,
                error = error,
            )

        /** Dart's `RoomInfoUpdateDraft.valid(...)`. */
        fun valid(
            name: String?,
            description: String?,
            visibility: String?,
            joinPolicy: String?,
            avatarAssetId: String?,
            defaultAvatarKey: String?,
        ): RoomInfoUpdateDraft =
            RoomInfoUpdateDraft(
                name = name,
                description = description,
                visibility = visibility,
                joinPolicy = joinPolicy,
                avatarAssetId = avatarAssetId,
                defaultAvatarKey = defaultAvatarKey,
                error = null,
            )
    }
}

enum class RoomManagementSection { info, stickers }

class RoomManagementDialogStatePatch(
    val section: RoomManagementSection,
    val room: RoomDetail,
    val pendingAvatarAssetId: String?,
    val pendingAvatarUrl: String?,
    val usingPresetAvatar: Boolean,
    val defaultAvatarKey: String,
    val uploadingAvatar: Boolean,
    val saving: Boolean,
    val deleting: Boolean,
    val changed: Boolean,
    val error: String?,
    val notice: String?,
)

class RoomManagementInfoFieldsPatch(
    val visibility: String,
    val joinPolicy: String,
)

fun canStartRoomProfileSave(saving: Boolean, leaving: Boolean): Boolean {
    return !saving && !leaving
}

fun canStartCreateRoom(busy: Boolean): Boolean {
    return !busy
}

fun createRoomSubmitStarted(): CreateRoomDialogPatch {
    return CreateRoomDialogPatch(busy = true, error = null)
}

fun createRoomSubmitFailed(failure: Any): CreateRoomDialogPatch {
    return CreateRoomDialogPatch(
        busy = true,
        error = userFacingErrorMessage(failure),
    )
}

fun createRoomSubmitFinished(error: String?): CreateRoomDialogPatch {
    return CreateRoomDialogPatch(busy = false, error = error)
}

fun canStartRoomLeave(saving: Boolean, leaving: Boolean): Boolean {
    return !saving && !leaving
}

fun roomProfileCopySucceeded(
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingGlobalProfile: Boolean,
    usingProfilePresetAvatar: Boolean,
    defaultAvatarKey: String,
    saving: Boolean,
    leaving: Boolean,
    uploadingAvatar: Boolean,
    label: String,
): RoomProfileDialogStatePatch {
    return RoomProfileDialogStatePatch(
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingGlobalProfile = usingGlobalProfile,
        usingProfilePresetAvatar = usingProfilePresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        saving = saving,
        leaving = leaving,
        uploadingAvatar = uploadingAvatar,
        error = null,
        notice = roomCopySuccessNotice(label),
    )
}

fun roomProfileNotificationPolicyChanged(
    notificationPolicy: String,
): RoomProfileNotificationPolicyPatch {
    return RoomProfileNotificationPolicyPatch(
        notificationPolicy = normalizeRoomNotificationPolicy(notificationPolicy),
    )
}

fun roomProfileCopyFailed(
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingGlobalProfile: Boolean,
    usingProfilePresetAvatar: Boolean,
    defaultAvatarKey: String,
    saving: Boolean,
    leaving: Boolean,
    uploadingAvatar: Boolean,
    notice: String?,
    failure: Any,
): RoomProfileDialogStatePatch {
    return RoomProfileDialogStatePatch(
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingGlobalProfile = usingGlobalProfile,
        usingProfilePresetAvatar = usingProfilePresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        saving = saving,
        leaving = leaving,
        uploadingAvatar = uploadingAvatar,
        error = roomCopyFailureMessage(failure),
        notice = notice,
    )
}

fun roomProfileAvatarPickFailed(
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingGlobalProfile: Boolean,
    usingProfilePresetAvatar: Boolean,
    defaultAvatarKey: String,
    saving: Boolean,
    leaving: Boolean,
    uploadingAvatar: Boolean,
    notice: String?,
    failure: Any,
): RoomProfileDialogStatePatch {
    return RoomProfileDialogStatePatch(
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingGlobalProfile = usingGlobalProfile,
        usingProfilePresetAvatar = usingProfilePresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        saving = saving,
        leaving = leaving,
        uploadingAvatar = uploadingAvatar,
        error = userFacingErrorMessage(failure),
        notice = notice,
    )
}

fun roomProfileAvatarUploadStarted(
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingGlobalProfile: Boolean,
    usingProfilePresetAvatar: Boolean,
    defaultAvatarKey: String,
    saving: Boolean,
    leaving: Boolean,
): RoomProfileDialogStatePatch {
    return RoomProfileDialogStatePatch(
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingGlobalProfile = usingGlobalProfile,
        usingProfilePresetAvatar = usingProfilePresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        saving = saving,
        leaving = leaving,
        uploadingAvatar = true,
        error = null,
        notice = null,
    )
}

fun roomProfileAvatarUploadSucceeded(
    assetId: String,
    assetUrl: String,
    defaultAvatarKey: String,
    saving: Boolean,
    leaving: Boolean,
): RoomProfileDialogStatePatch {
    return RoomProfileDialogStatePatch(
        pendingAvatarAssetId = assetId,
        pendingAvatarUrl = assetUrl,
        usingGlobalProfile = false,
        usingProfilePresetAvatar = false,
        defaultAvatarKey = defaultAvatarKey,
        saving = saving,
        leaving = leaving,
        uploadingAvatar = false,
        error = null,
        notice = null,
    )
}

fun roomProfileAvatarUploadFailed(
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingGlobalProfile: Boolean,
    usingProfilePresetAvatar: Boolean,
    defaultAvatarKey: String,
    saving: Boolean,
    leaving: Boolean,
    failure: Any,
): RoomProfileDialogStatePatch {
    return RoomProfileDialogStatePatch(
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingGlobalProfile = usingGlobalProfile,
        usingProfilePresetAvatar = usingProfilePresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        saving = saving,
        leaving = leaving,
        uploadingAvatar = false,
        error = userFacingErrorMessage(failure),
        notice = null,
    )
}

fun roomProfileUseGlobalProfile(
    currentUserDefaultAvatarKey: String,
    saving: Boolean,
    leaving: Boolean,
    uploadingAvatar: Boolean,
): RoomProfileDialogStatePatch {
    return RoomProfileDialogStatePatch(
        pendingAvatarAssetId = null,
        pendingAvatarUrl = null,
        usingGlobalProfile = true,
        usingProfilePresetAvatar = false,
        defaultAvatarKey = currentUserDefaultAvatarKey,
        saving = saving,
        leaving = leaving,
        uploadingAvatar = uploadingAvatar,
        error = null,
        notice = roomUseGlobalProfileNotice(),
    )
}

fun roomProfileUsePresetAvatar(
    defaultAvatarKey: String,
    saving: Boolean,
    leaving: Boolean,
    uploadingAvatar: Boolean,
    error: String?,
    notice: String?,
): RoomProfileDialogStatePatch {
    return RoomProfileDialogStatePatch(
        pendingAvatarAssetId = null,
        pendingAvatarUrl = null,
        usingGlobalProfile = false,
        usingProfilePresetAvatar = true,
        defaultAvatarKey = defaultAvatarKey,
        saving = saving,
        leaving = leaving,
        uploadingAvatar = uploadingAvatar,
        error = error,
        notice = notice,
    )
}

fun roomProfileSaveStarted(
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingGlobalProfile: Boolean,
    usingProfilePresetAvatar: Boolean,
    defaultAvatarKey: String,
    leaving: Boolean,
    uploadingAvatar: Boolean,
): RoomProfileDialogStatePatch {
    return RoomProfileDialogStatePatch(
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingGlobalProfile = usingGlobalProfile,
        usingProfilePresetAvatar = usingProfilePresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        saving = true,
        leaving = leaving,
        uploadingAvatar = uploadingAvatar,
        error = null,
        notice = null,
    )
}

fun roomProfileSaveFailed(
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingGlobalProfile: Boolean,
    usingProfilePresetAvatar: Boolean,
    defaultAvatarKey: String,
    leaving: Boolean,
    uploadingAvatar: Boolean,
    failure: Any,
): RoomProfileDialogStatePatch {
    return RoomProfileDialogStatePatch(
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingGlobalProfile = usingGlobalProfile,
        usingProfilePresetAvatar = usingProfilePresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        saving = false,
        leaving = leaving,
        uploadingAvatar = uploadingAvatar,
        error = userFacingErrorMessage(failure),
        notice = null,
    )
}

fun roomProfileLeaveStarted(
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingGlobalProfile: Boolean,
    usingProfilePresetAvatar: Boolean,
    defaultAvatarKey: String,
    saving: Boolean,
    uploadingAvatar: Boolean,
): RoomProfileDialogStatePatch {
    return RoomProfileDialogStatePatch(
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingGlobalProfile = usingGlobalProfile,
        usingProfilePresetAvatar = usingProfilePresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        saving = saving,
        leaving = true,
        uploadingAvatar = uploadingAvatar,
        error = null,
        notice = null,
    )
}

fun roomProfileLeaveFailed(
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingGlobalProfile: Boolean,
    usingProfilePresetAvatar: Boolean,
    defaultAvatarKey: String,
    saving: Boolean,
    uploadingAvatar: Boolean,
    failure: Any,
): RoomProfileDialogStatePatch {
    return RoomProfileDialogStatePatch(
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingGlobalProfile = usingGlobalProfile,
        usingProfilePresetAvatar = usingProfilePresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        saving = saving,
        leaving = false,
        uploadingAvatar = uploadingAvatar,
        error = userFacingErrorMessage(failure),
        notice = null,
    )
}

fun roomInfoManagementBusy(saving: Boolean, deleting: Boolean): Boolean {
    return saving || deleting
}

fun canStartRoomInfoSave(saving: Boolean, deleting: Boolean): Boolean {
    return !roomInfoManagementBusy(saving = saving, deleting = deleting)
}

fun canStartRoomDeletion(
    canDeleteRoom: Boolean,
    saving: Boolean,
    deleting: Boolean,
): Boolean {
    return canDeleteRoom &&
        !roomInfoManagementBusy(saving = saving, deleting = deleting)
}

fun roomManagementAvatarPickFailed(
    section: RoomManagementSection,
    room: RoomDetail,
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingPresetAvatar: Boolean,
    defaultAvatarKey: String,
    uploadingAvatar: Boolean,
    saving: Boolean,
    deleting: Boolean,
    changed: Boolean,
    notice: String?,
    failure: Any,
): RoomManagementDialogStatePatch {
    return RoomManagementDialogStatePatch(
        section = section,
        room = room,
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingPresetAvatar = usingPresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        uploadingAvatar = uploadingAvatar,
        saving = saving,
        deleting = deleting,
        changed = changed,
        error = userFacingErrorMessage(failure),
        notice = notice,
    )
}

fun roomManagementAvatarUploadStarted(
    section: RoomManagementSection,
    room: RoomDetail,
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingPresetAvatar: Boolean,
    defaultAvatarKey: String,
    saving: Boolean,
    deleting: Boolean,
    changed: Boolean,
): RoomManagementDialogStatePatch {
    return RoomManagementDialogStatePatch(
        section = section,
        room = room,
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingPresetAvatar = usingPresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        uploadingAvatar = true,
        saving = saving,
        deleting = deleting,
        changed = changed,
        error = null,
        notice = null,
    )
}

fun roomManagementAvatarUploadSucceeded(
    section: RoomManagementSection,
    room: RoomDetail,
    assetId: String,
    assetUrl: String,
    defaultAvatarKey: String,
    saving: Boolean,
    deleting: Boolean,
    changed: Boolean,
): RoomManagementDialogStatePatch {
    return RoomManagementDialogStatePatch(
        section = section,
        room = room,
        pendingAvatarAssetId = assetId,
        pendingAvatarUrl = assetUrl,
        usingPresetAvatar = false,
        defaultAvatarKey = defaultAvatarKey,
        uploadingAvatar = false,
        saving = saving,
        deleting = deleting,
        changed = changed,
        error = null,
        notice = null,
    )
}

fun roomManagementAvatarUploadFailed(
    section: RoomManagementSection,
    room: RoomDetail,
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingPresetAvatar: Boolean,
    defaultAvatarKey: String,
    saving: Boolean,
    deleting: Boolean,
    changed: Boolean,
    failure: Any,
): RoomManagementDialogStatePatch {
    return RoomManagementDialogStatePatch(
        section = section,
        room = room,
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingPresetAvatar = usingPresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        uploadingAvatar = false,
        saving = saving,
        deleting = deleting,
        changed = changed,
        error = userFacingErrorMessage(failure),
        notice = null,
    )
}

fun roomManagementUsePresetAvatar(
    section: RoomManagementSection,
    room: RoomDetail,
    defaultAvatarKey: String,
    uploadingAvatar: Boolean,
    saving: Boolean,
    deleting: Boolean,
    changed: Boolean,
    error: String?,
    notice: String?,
): RoomManagementDialogStatePatch {
    return RoomManagementDialogStatePatch(
        section = section,
        room = room,
        pendingAvatarAssetId = null,
        pendingAvatarUrl = null,
        usingPresetAvatar = true,
        defaultAvatarKey = defaultAvatarKey,
        uploadingAvatar = uploadingAvatar,
        saving = saving,
        deleting = deleting,
        changed = changed,
        error = error,
        notice = notice,
    )
}

fun roomManagementSectionChanged(
    section: RoomManagementSection,
    room: RoomDetail,
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingPresetAvatar: Boolean,
    defaultAvatarKey: String,
    uploadingAvatar: Boolean,
    saving: Boolean,
    deleting: Boolean,
    changed: Boolean,
    error: String?,
    notice: String?,
): RoomManagementDialogStatePatch {
    val clearTransientMessages = section == RoomManagementSection.stickers
    return RoomManagementDialogStatePatch(
        section = section,
        room = room,
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingPresetAvatar = usingPresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        uploadingAvatar = uploadingAvatar,
        saving = saving,
        deleting = deleting,
        changed = changed,
        error = if (clearTransientMessages) null else error,
        notice = if (clearTransientMessages) null else notice,
    )
}

fun roomManagementVisibilityChanged(
    visibility: String,
    joinPolicy: String,
): RoomManagementInfoFieldsPatch {
    return RoomManagementInfoFieldsPatch(
        visibility = normalizeRoomVisibility(visibility),
        joinPolicy = joinPolicy,
    )
}

fun roomManagementJoinPolicyChanged(
    visibility: String,
    joinPolicy: String,
): RoomManagementInfoFieldsPatch {
    return RoomManagementInfoFieldsPatch(
        visibility = visibility,
        joinPolicy = normalizeRoomJoinPolicy(joinPolicy),
    )
}

fun roomManagementInfoDraftInvalid(
    section: RoomManagementSection,
    room: RoomDetail,
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingPresetAvatar: Boolean,
    defaultAvatarKey: String,
    uploadingAvatar: Boolean,
    saving: Boolean,
    deleting: Boolean,
    changed: Boolean,
    notice: String?,
    error: String?,
): RoomManagementDialogStatePatch {
    return RoomManagementDialogStatePatch(
        section = section,
        room = room,
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingPresetAvatar = usingPresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        uploadingAvatar = uploadingAvatar,
        saving = saving,
        deleting = deleting,
        changed = changed,
        error = error,
        notice = notice,
    )
}

fun roomManagementInfoSaveStarted(
    section: RoomManagementSection,
    room: RoomDetail,
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingPresetAvatar: Boolean,
    defaultAvatarKey: String,
    uploadingAvatar: Boolean,
    deleting: Boolean,
    changed: Boolean,
): RoomManagementDialogStatePatch {
    return RoomManagementDialogStatePatch(
        section = section,
        room = room,
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingPresetAvatar = usingPresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        uploadingAvatar = uploadingAvatar,
        saving = true,
        deleting = deleting,
        changed = changed,
        error = null,
        notice = null,
    )
}

fun roomManagementInfoSaveSucceeded(
    section: RoomManagementSection,
    updatedRoom: RoomDetail,
    defaultAvatarKey: String,
    uploadingAvatar: Boolean,
    deleting: Boolean,
): RoomManagementDialogStatePatch {
    return RoomManagementDialogStatePatch(
        section = section,
        room = updatedRoom,
        pendingAvatarAssetId = null,
        pendingAvatarUrl = null,
        usingPresetAvatar = updatedRoom.avatarUrl == null,
        defaultAvatarKey = defaultAvatarKey,
        uploadingAvatar = uploadingAvatar,
        saving = false,
        deleting = deleting,
        changed = true,
        error = null,
        notice = roomInfoSavedNotice(),
    )
}

fun roomManagementInfoSaveFailed(
    section: RoomManagementSection,
    room: RoomDetail,
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingPresetAvatar: Boolean,
    defaultAvatarKey: String,
    uploadingAvatar: Boolean,
    deleting: Boolean,
    changed: Boolean,
    failure: Any,
): RoomManagementDialogStatePatch {
    return RoomManagementDialogStatePatch(
        section = section,
        room = room,
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingPresetAvatar = usingPresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        uploadingAvatar = uploadingAvatar,
        saving = false,
        deleting = deleting,
        changed = changed,
        error = userFacingErrorMessage(failure),
        notice = null,
    )
}

fun roomManagementDeletionStarted(
    section: RoomManagementSection,
    room: RoomDetail,
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingPresetAvatar: Boolean,
    defaultAvatarKey: String,
    uploadingAvatar: Boolean,
    saving: Boolean,
    changed: Boolean,
): RoomManagementDialogStatePatch {
    return RoomManagementDialogStatePatch(
        section = section,
        room = room,
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingPresetAvatar = usingPresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        uploadingAvatar = uploadingAvatar,
        saving = saving,
        deleting = true,
        changed = changed,
        error = null,
        notice = null,
    )
}

fun roomManagementDeletionFailed(
    section: RoomManagementSection,
    room: RoomDetail,
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    usingPresetAvatar: Boolean,
    defaultAvatarKey: String,
    uploadingAvatar: Boolean,
    saving: Boolean,
    changed: Boolean,
    failure: Any,
): RoomManagementDialogStatePatch {
    return RoomManagementDialogStatePatch(
        section = section,
        room = room,
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        usingPresetAvatar = usingPresetAvatar,
        defaultAvatarKey = defaultAvatarKey,
        uploadingAvatar = uploadingAvatar,
        saving = saving,
        deleting = false,
        changed = changed,
        error = userFacingErrorMessage(failure),
        notice = null,
    )
}

fun createRoomDraftFromForm(name: String): CreateRoomDraft {
    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) {
        return CreateRoomDraft.invalid("房间名不能为空")
    }
    return CreateRoomDraft.valid(name = trimmedName)
}

fun roomProfileUpdateDraftFromForm(
    remarkName: String,
    notificationPolicy: String,
    usingGlobalProfile: Boolean,
    roomDisplayName: String,
    pendingAvatarAssetId: String? = null,
    usingProfilePresetAvatar: Boolean,
    defaultAvatarKey: String,
): RoomProfileUpdateDraft {
    return RoomProfileUpdateDraft(
        remarkName = remarkName.trim(),
        notificationPolicy = normalizeRoomNotificationPolicy(notificationPolicy),
        roomDisplayName = if (usingGlobalProfile) "" else roomDisplayName.trim(),
        avatarAssetId =
            roomProfileAvatarAssetIdForSave(
                usingGlobalProfile = usingGlobalProfile,
                pendingAvatarAssetId = pendingAvatarAssetId,
                usingProfilePresetAvatar = usingProfilePresetAvatar,
            ),
        defaultAvatarKey = if (usingGlobalProfile) "" else defaultAvatarKey,
    )
}

fun roomProfileAvatarAssetIdForSave(
    usingGlobalProfile: Boolean,
    pendingAvatarAssetId: String? = null,
    usingProfilePresetAvatar: Boolean,
): String? {
    if (usingGlobalProfile) return ""
    val pending = pendingAvatarAssetId
    if (pending != null) return pending
    if (usingProfilePresetAvatar) return ""
    return null
}

fun roomInfoUpdateDraftFromForm(
    name: String,
    description: String,
    visibility: String,
    joinPolicy: String,
    pendingAvatarAssetId: String? = null,
    usingPresetAvatar: Boolean,
    defaultAvatarKey: String,
): RoomInfoUpdateDraft {
    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) {
        return RoomInfoUpdateDraft.invalid("房间名不能为空")
    }
    return RoomInfoUpdateDraft.valid(
        name = trimmedName,
        description = description.trim(),
        visibility = normalizeRoomVisibility(visibility),
        joinPolicy = normalizeRoomJoinPolicy(joinPolicy),
        avatarAssetId =
            roomAvatarAssetIdForSave(
                pendingAvatarAssetId = pendingAvatarAssetId,
                usingPresetAvatar = usingPresetAvatar,
            ),
        defaultAvatarKey = defaultAvatarKey,
    )
}

fun roomAvatarAssetIdForSave(
    pendingAvatarAssetId: String? = null,
    usingPresetAvatar: Boolean,
): String? {
    val pending = pendingAvatarAssetId
    if (pending != null) return pending
    if (usingPresetAvatar) return ""
    return null
}
