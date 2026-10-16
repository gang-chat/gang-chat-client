package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomMembership
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomFormsTest {
    @Test
    fun `createRoomDraftFromForm validates and trims the room name`() {
        assertEquals("房间名不能为空", createRoomDraftFromForm(name = " ").error)

        val draft = createRoomDraftFromForm(name = "  General  ")

        assertTrue(draft.isValid)
        assertEquals("General", draft.name)
    }

    @Test
    fun `create room submit patches preserve dialog busy and error state`() {
        assertTrue(canStartCreateRoom(busy = false))
        assertFalse(canStartCreateRoom(busy = true))

        val started = createRoomSubmitStarted()
        assertTrue(started.busy)
        assertNull(started.error)

        val failed = createRoomSubmitFailed(failure = "create failed")
        assertTrue(failed.busy)
        assertEquals("操作失败，请稍后重试", failed.error)

        val finished = createRoomSubmitFinished(error = failed.error)
        assertFalse(finished.busy)
        assertEquals("操作失败，请稍后重试", finished.error)
    }

    @Test
    fun `roomProfileUpdateDraftFromForm trims room profile fields`() {
        val draft =
            roomProfileUpdateDraftFromForm(
                remarkName = "  Ops  ",
                notificationPolicy = "mention_only",
                usingGlobalProfile = false,
                roomDisplayName = "  Room Logan  ",
                pendingAvatarAssetId = "asset_1",
                usingProfilePresetAvatar = false,
                defaultAvatarKey = "green-2",
            )

        assertEquals("Ops", draft.remarkName)
        assertEquals("silent", draft.notificationPolicy)
        assertEquals("Room Logan", draft.roomDisplayName)
        assertEquals("asset_1", draft.avatarAssetId)
        assertEquals("green-2", draft.defaultAvatarKey)
    }

    @Test
    fun `room profile draft preserves clear semantics for global and preset`() {
        val global =
            roomProfileUpdateDraftFromForm(
                remarkName = "",
                notificationPolicy = "all",
                usingGlobalProfile = true,
                roomDisplayName = "Room Logan",
                pendingAvatarAssetId = "asset_1",
                usingProfilePresetAvatar = false,
                defaultAvatarKey = "green-2",
            )

        assertEquals("", global.roomDisplayName)
        assertEquals("", global.avatarAssetId)
        assertEquals("", global.defaultAvatarKey)

        assertEquals(
            "",
            roomProfileAvatarAssetIdForSave(
                usingGlobalProfile = false,
                usingProfilePresetAvatar = true,
            ),
        )
        assertNull(
            roomProfileAvatarAssetIdForSave(
                usingGlobalProfile = false,
                usingProfilePresetAvatar = false,
            ),
        )
    }

    @Test
    fun `roomInfoUpdateDraftFromForm validates trims and normalizes fields`() {
        assertEquals(
            "房间名不能为空",
            roomInfoUpdateDraftFromForm(
                name = "",
                description = "",
                visibility = "public",
                joinPolicy = "open",
                usingPresetAvatar = false,
                defaultAvatarKey = "room-1",
            ).error,
        )

        val draft =
            roomInfoUpdateDraftFromForm(
                name = "  General  ",
                description = "  Team room  ",
                visibility = "PRIVATE",
                joinPolicy = "allow_anyone",
                pendingAvatarAssetId = "asset_2",
                usingPresetAvatar = false,
                defaultAvatarKey = "room-2",
            )

        assertTrue(draft.isValid)
        assertEquals("General", draft.name)
        assertEquals("Team room", draft.description)
        assertEquals("private", draft.visibility)
        assertEquals("open", draft.joinPolicy)
        assertEquals("asset_2", draft.avatarAssetId)
        assertEquals("room-2", draft.defaultAvatarKey)
    }

    @Test
    fun `room avatar draft clears uploaded avatar only when preset is selected`() {
        assertEquals(
            "asset_3",
            roomAvatarAssetIdForSave(
                pendingAvatarAssetId = "asset_3",
                usingPresetAvatar = true,
            ),
        )
        assertEquals("", roomAvatarAssetIdForSave(usingPresetAvatar = true))
        assertNull(roomAvatarAssetIdForSave(usingPresetAvatar = false))
    }

    @Test
    fun `room action gates share busy semantics across profile and management`() {
        assertTrue(canStartRoomProfileSave(saving = false, leaving = false))
        assertFalse(canStartRoomProfileSave(saving = true, leaving = false))
        assertFalse(canStartRoomLeave(saving = false, leaving = true))

        assertFalse(roomInfoManagementBusy(saving = false, deleting = false))
        assertTrue(roomInfoManagementBusy(saving = true, deleting = false))
        assertTrue(canStartRoomInfoSave(saving = false, deleting = false))
        assertFalse(canStartRoomInfoSave(saving = false, deleting = true))

        assertTrue(
            canStartRoomDeletion(
                canDeleteRoom = true,
                saving = false,
                deleting = false,
            ),
        )
        assertFalse(
            canStartRoomDeletion(
                canDeleteRoom = false,
                saving = false,
                deleting = false,
            ),
        )
        assertFalse(
            canStartRoomDeletion(
                canDeleteRoom = true,
                saving = true,
                deleting = false,
            ),
        )
    }

    @Test
    fun `room profile dialog patches copy and global profile state`() {
        val copied =
            roomProfileCopySucceeded(
                pendingAvatarAssetId = "asset_1",
                pendingAvatarUrl = "/avatar.png",
                usingGlobalProfile = false,
                usingProfilePresetAvatar = false,
                defaultAvatarKey = "blue-1",
                saving = false,
                leaving = false,
                uploadingAvatar = false,
                label = "RID",
            )
        assertEquals("asset_1", copied.pendingAvatarAssetId)
        assertEquals("/avatar.png", copied.pendingAvatarUrl)
        assertFalse(copied.usingGlobalProfile)
        assertFalse(copied.usingProfilePresetAvatar)
        assertEquals("blue-1", copied.defaultAvatarKey)
        assertFalse(copied.saving)
        assertFalse(copied.leaving)
        assertFalse(copied.uploadingAvatar)
        assertNull(copied.error)
        assertEquals("RID 已复制", copied.notice)

        val copyFailed =
            roomProfileCopyFailed(
                pendingAvatarAssetId = "asset_1",
                pendingAvatarUrl = "/avatar.png",
                usingGlobalProfile = false,
                usingProfilePresetAvatar = false,
                defaultAvatarKey = "blue-1",
                saving = false,
                leaving = false,
                uploadingAvatar = false,
                notice = "kept",
                failure = Exception("clipboard"),
            )
        assertEquals("kept", copyFailed.notice)
        assertEquals("无法复制", copyFailed.error)

        val global =
            roomProfileUseGlobalProfile(
                currentUserDefaultAvatarKey = "green-2",
                saving = false,
                leaving = false,
                uploadingAvatar = false,
            )
        assertNull(global.pendingAvatarAssetId)
        assertNull(global.pendingAvatarUrl)
        assertTrue(global.usingGlobalProfile)
        assertFalse(global.usingProfilePresetAvatar)
        assertEquals("green-2", global.defaultAvatarKey)
        assertNull(global.error)
        assertEquals("保存后将使用全局默认用户名和默认头像", global.notice)

        val preset =
            roomProfileUsePresetAvatar(
                defaultAvatarKey = "purple-2",
                saving = false,
                leaving = false,
                uploadingAvatar = false,
                error = "kept error",
                notice = "kept notice",
            )
        assertNull(preset.pendingAvatarAssetId)
        assertNull(preset.pendingAvatarUrl)
        assertFalse(preset.usingGlobalProfile)
        assertTrue(preset.usingProfilePresetAvatar)
        assertEquals("purple-2", preset.defaultAvatarKey)
        assertEquals("kept error", preset.error)
        assertEquals("kept notice", preset.notice)
    }

    @Test
    fun `room profile notification policy patch normalizes values`() {
        val mentions =
            roomProfileNotificationPolicyChanged(
                notificationPolicy = "mention_only",
            )
        val fallback =
            roomProfileNotificationPolicyChanged(
                notificationPolicy = "unexpected",
            )

        assertEquals("silent", mentions.notificationPolicy)
        assertEquals("all", fallback.notificationPolicy)
    }

    @Test
    fun `room profile dialog patches avatar upload lifecycle`() {
        val started =
            roomProfileAvatarUploadStarted(
                pendingAvatarAssetId = null,
                pendingAvatarUrl = null,
                usingGlobalProfile = true,
                usingProfilePresetAvatar = false,
                defaultAvatarKey = "blue-1",
                saving = false,
                leaving = false,
            )
        assertTrue(started.uploadingAvatar)
        assertNull(started.error)
        assertNull(started.notice)
        assertTrue(started.usingGlobalProfile)

        val succeeded =
            roomProfileAvatarUploadSucceeded(
                assetId = "asset_1",
                assetUrl = "/asset.png",
                defaultAvatarKey = "blue-1",
                saving = false,
                leaving = false,
            )
        assertEquals("asset_1", succeeded.pendingAvatarAssetId)
        assertEquals("/asset.png", succeeded.pendingAvatarUrl)
        assertFalse(succeeded.usingGlobalProfile)
        assertFalse(succeeded.usingProfilePresetAvatar)
        assertFalse(succeeded.uploadingAvatar)
        assertNull(succeeded.error)

        val failed =
            roomProfileAvatarUploadFailed(
                pendingAvatarAssetId = "old_asset",
                pendingAvatarUrl = "/old.png",
                usingGlobalProfile = false,
                usingProfilePresetAvatar = true,
                defaultAvatarKey = "blue-1",
                saving = false,
                leaving = false,
                failure = Exception("upload failed"),
            )
        assertEquals("old_asset", failed.pendingAvatarAssetId)
        assertEquals("/old.png", failed.pendingAvatarUrl)
        assertTrue(failed.usingProfilePresetAvatar)
        assertFalse(failed.uploadingAvatar)
        assertEquals("操作失败，请稍后重试", failed.error)
        assertNull(failed.notice)
    }

    @Test
    fun `room profile dialog patches save and leave failures`() {
        val saveStarted =
            roomProfileSaveStarted(
                pendingAvatarAssetId = "asset_1",
                pendingAvatarUrl = "/avatar.png",
                usingGlobalProfile = false,
                usingProfilePresetAvatar = false,
                defaultAvatarKey = "blue-1",
                leaving = false,
                uploadingAvatar = false,
            )
        assertTrue(saveStarted.saving)
        assertFalse(saveStarted.leaving)
        assertNull(saveStarted.error)
        assertNull(saveStarted.notice)

        val saveFailed =
            roomProfileSaveFailed(
                pendingAvatarAssetId = "asset_1",
                pendingAvatarUrl = "/avatar.png",
                usingGlobalProfile = false,
                usingProfilePresetAvatar = false,
                defaultAvatarKey = "blue-1",
                leaving = false,
                uploadingAvatar = false,
                failure = Exception("save failed"),
            )
        assertFalse(saveFailed.saving)
        assertEquals("操作失败，请稍后重试", saveFailed.error)

        val leaveStarted =
            roomProfileLeaveStarted(
                pendingAvatarAssetId = "asset_1",
                pendingAvatarUrl = "/avatar.png",
                usingGlobalProfile = false,
                usingProfilePresetAvatar = false,
                defaultAvatarKey = "blue-1",
                saving = false,
                uploadingAvatar = false,
            )
        assertFalse(leaveStarted.saving)
        assertTrue(leaveStarted.leaving)
        assertNull(leaveStarted.error)
        assertNull(leaveStarted.notice)

        val leaveFailed =
            roomProfileLeaveFailed(
                pendingAvatarAssetId = "asset_1",
                pendingAvatarUrl = "/avatar.png",
                usingGlobalProfile = false,
                usingProfilePresetAvatar = false,
                defaultAvatarKey = "blue-1",
                saving = false,
                uploadingAvatar = false,
                failure = Exception("leave failed"),
            )
        assertFalse(leaveFailed.leaving)
        assertEquals("操作失败，请稍后重试", leaveFailed.error)
        assertNull(leaveFailed.notice)
    }

    @Test
    fun `room management dialog patches avatar upload lifecycle`() {
        val room = _room("room_1")

        val started =
            roomManagementAvatarUploadStarted(
                section = RoomManagementSection.info,
                room = room,
                pendingAvatarAssetId = null,
                pendingAvatarUrl = null,
                usingPresetAvatar = true,
                defaultAvatarKey = "room-1",
                saving = false,
                deleting = false,
                changed = false,
            )
        assertEquals(RoomManagementSection.info, started.section)
        assertSame(room, started.room)
        assertTrue(started.uploadingAvatar)
        assertTrue(started.usingPresetAvatar)
        assertNull(started.error)
        assertNull(started.notice)

        val succeeded =
            roomManagementAvatarUploadSucceeded(
                section = RoomManagementSection.info,
                room = room,
                assetId = "asset_1",
                assetUrl = "/room.png",
                defaultAvatarKey = "room-1",
                saving = false,
                deleting = false,
                changed = true,
            )
        assertEquals("asset_1", succeeded.pendingAvatarAssetId)
        assertEquals("/room.png", succeeded.pendingAvatarUrl)
        assertFalse(succeeded.usingPresetAvatar)
        assertFalse(succeeded.uploadingAvatar)
        assertTrue(succeeded.changed)
        assertNull(succeeded.error)

        val failed =
            roomManagementAvatarUploadFailed(
                section = RoomManagementSection.info,
                room = room,
                pendingAvatarAssetId = "old_asset",
                pendingAvatarUrl = "/old.png",
                usingPresetAvatar = false,
                defaultAvatarKey = "room-1",
                saving = false,
                deleting = false,
                changed = false,
                failure = Exception("upload failed"),
            )
        assertEquals("old_asset", failed.pendingAvatarAssetId)
        assertEquals("/old.png", failed.pendingAvatarUrl)
        assertFalse(failed.usingPresetAvatar)
        assertFalse(failed.uploadingAvatar)
        assertFalse(failed.changed)
        assertEquals("操作失败，请稍后重试", failed.error)
        assertNull(failed.notice)

        val preset =
            roomManagementUsePresetAvatar(
                section = RoomManagementSection.info,
                room = room,
                defaultAvatarKey = "room-3",
                uploadingAvatar = false,
                saving = false,
                deleting = false,
                changed = true,
                error = "kept error",
                notice = "kept notice",
            )
        assertSame(room, preset.room)
        assertNull(preset.pendingAvatarAssetId)
        assertNull(preset.pendingAvatarUrl)
        assertTrue(preset.usingPresetAvatar)
        assertEquals("room-3", preset.defaultAvatarKey)
        assertTrue(preset.changed)
        assertEquals("kept error", preset.error)
        assertEquals("kept notice", preset.notice)
    }

    @Test
    fun `room management section changes can clear transient messages`() {
        val room = _room("room_1")

        val kept =
            roomManagementSectionChanged(
                section = RoomManagementSection.info,
                room = room,
                pendingAvatarAssetId = "asset_1",
                pendingAvatarUrl = "/room.png",
                usingPresetAvatar = false,
                defaultAvatarKey = "room-1",
                uploadingAvatar = false,
                saving = false,
                deleting = false,
                changed = true,
                error = "kept error",
                notice = "kept notice",
            )

        assertEquals(RoomManagementSection.info, kept.section)
        assertSame(room, kept.room)
        assertEquals("asset_1", kept.pendingAvatarAssetId)
        assertEquals("/room.png", kept.pendingAvatarUrl)
        assertFalse(kept.usingPresetAvatar)
        assertTrue(kept.changed)
        assertEquals("kept error", kept.error)
        assertEquals("kept notice", kept.notice)

        val cleared =
            roomManagementSectionChanged(
                section = RoomManagementSection.stickers,
                room = room,
                pendingAvatarAssetId = "asset_1",
                pendingAvatarUrl = "/room.png",
                usingPresetAvatar = false,
                defaultAvatarKey = "room-1",
                uploadingAvatar = false,
                saving = false,
                deleting = false,
                changed = true,
                error = "old error",
                notice = "old notice",
            )

        assertEquals(RoomManagementSection.stickers, cleared.section)
        assertEquals("asset_1", cleared.pendingAvatarAssetId)
        assertEquals("/room.png", cleared.pendingAvatarUrl)
        assertTrue(cleared.changed)
        assertNull(cleared.error)
        assertNull(cleared.notice)
    }

    @Test
    fun `room management info field patches preserve sibling fields`() {
        val visibility =
            roomManagementVisibilityChanged(
                visibility = "private",
                joinPolicy = "open",
            )

        assertEquals("private", visibility.visibility)
        assertEquals("open", visibility.joinPolicy)

        val joinPolicy =
            roomManagementJoinPolicyChanged(
                visibility = visibility.visibility,
                joinPolicy = "unknown",
            )

        assertEquals("private", joinPolicy.visibility)
        assertEquals("approval_required", joinPolicy.joinPolicy)
    }

    @Test
    fun `room management dialog patches save invalid success and failure`() {
        val room = _room("room_1")
        val updatedRoom = _room("room_1", avatarUrl = "/saved.png")

        val invalid =
            roomManagementInfoDraftInvalid(
                section = RoomManagementSection.info,
                room = room,
                pendingAvatarAssetId = "asset_1",
                pendingAvatarUrl = "/room.png",
                usingPresetAvatar = false,
                defaultAvatarKey = "room-1",
                uploadingAvatar = false,
                saving = false,
                deleting = false,
                changed = false,
                notice = "kept",
                error = "房间名不能为空",
            )
        assertEquals(RoomManagementSection.info, invalid.section)
        assertSame(room, invalid.room)
        assertEquals("asset_1", invalid.pendingAvatarAssetId)
        assertFalse(invalid.saving)
        assertEquals("房间名不能为空", invalid.error)
        assertEquals("kept", invalid.notice)

        val started =
            roomManagementInfoSaveStarted(
                section = RoomManagementSection.info,
                room = room,
                pendingAvatarAssetId = "asset_1",
                pendingAvatarUrl = "/room.png",
                usingPresetAvatar = false,
                defaultAvatarKey = "room-1",
                uploadingAvatar = false,
                deleting = false,
                changed = false,
            )
        assertTrue(started.saving)
        assertFalse(started.deleting)
        assertNull(started.error)
        assertNull(started.notice)

        val succeeded =
            roomManagementInfoSaveSucceeded(
                section = RoomManagementSection.info,
                updatedRoom = updatedRoom,
                defaultAvatarKey = "room-1",
                uploadingAvatar = false,
                deleting = false,
            )
        assertSame(updatedRoom, succeeded.room)
        assertNull(succeeded.pendingAvatarAssetId)
        assertNull(succeeded.pendingAvatarUrl)
        assertFalse(succeeded.usingPresetAvatar)
        assertFalse(succeeded.saving)
        assertTrue(succeeded.changed)
        assertNull(succeeded.error)
        assertEquals("房间信息已保存", succeeded.notice)

        val failed =
            roomManagementInfoSaveFailed(
                section = RoomManagementSection.info,
                room = room,
                pendingAvatarAssetId = "asset_1",
                pendingAvatarUrl = "/room.png",
                usingPresetAvatar = false,
                defaultAvatarKey = "room-1",
                uploadingAvatar = false,
                deleting = false,
                changed = true,
                failure = Exception("save failed"),
            )
        assertSame(room, failed.room)
        assertEquals("asset_1", failed.pendingAvatarAssetId)
        assertFalse(failed.saving)
        assertTrue(failed.changed)
        assertEquals("操作失败，请稍后重试", failed.error)
        assertNull(failed.notice)
    }

    @Test
    fun `room management dialog patches deletion lifecycle`() {
        val room = _room("room_1")

        val started =
            roomManagementDeletionStarted(
                section = RoomManagementSection.info,
                room = room,
                pendingAvatarAssetId = "asset_1",
                pendingAvatarUrl = "/room.png",
                usingPresetAvatar = false,
                defaultAvatarKey = "room-1",
                uploadingAvatar = false,
                saving = false,
                changed = true,
            )
        assertSame(room, started.room)
        assertTrue(started.deleting)
        assertFalse(started.saving)
        assertTrue(started.changed)
        assertNull(started.error)
        assertNull(started.notice)

        val failed =
            roomManagementDeletionFailed(
                section = RoomManagementSection.info,
                room = room,
                pendingAvatarAssetId = "asset_1",
                pendingAvatarUrl = "/room.png",
                usingPresetAvatar = false,
                defaultAvatarKey = "room-1",
                uploadingAvatar = false,
                saving = false,
                changed = true,
                failure = Exception("delete failed"),
            )
        assertSame(room, failed.room)
        assertFalse(failed.deleting)
        assertTrue(failed.changed)
        assertEquals("操作失败，请稍后重试", failed.error)
        assertNull(failed.notice)
    }

    private fun _room(id: String, avatarUrl: String? = null): RoomDetail {
        return RoomDetail(
            id = id,
            name = id,
            avatarUrl = avatarUrl,
            defaultAvatarKey = "room-1",
            memberCount = 3,
            myMembership =
                RoomMembership(
                    joinedAt = DateTime.utc(2026, 6, 5),
                    role = "admin",
                ),
            live =
                LiveState(
                    roomId = id,
                    participantCount = 0,
                    participants = emptyList(),
                    updatedAt = DateTime.utc(2026, 6, 5),
                ),
            createdAt = DateTime.utc(2026, 6, 5),
            updatedAt = DateTime.utc(2026, 6, 5),
        )
    }
}
