package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.LiveParticipant
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomMembership
import com.gangchat.client.protocol.UserSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDisplayTest {
    @Test
    fun `liveParticipantByUserId returns matching participant`() {
        val live = _live(listOf("alice", "bob"))

        assertEquals("User bob", liveParticipantByUserId(live, "bob")?.user?.displayName)
        assertNull(liveParticipantByUserId(live, "missing"))
        assertNull(liveParticipantByUserId(null, "bob"))
    }

    @Test
    fun `liveParticipantDisplayName uses fallback for missing live user`() {
        val live = _live(listOf("alice"))

        assertEquals("User alice", liveParticipantDisplayName(live, "alice"))
        assertEquals("", liveParticipantDisplayName(live, "missing"))
        assertEquals(
            "Unknown",
            liveParticipantDisplayName(null, "missing", fallback = "Unknown"),
        )
    }

    @Test
    fun `liveParticipantDisplayName prefers room display name`() {
        val live =
            _liveWithParticipants(
                listOf(
                    _participant("alice", roomDisplayName = "Room Alice"),
                ),
            )

        assertEquals("Room Alice", liveParticipantDisplayName(live, "alice"))
    }

    @Test
    fun `visible live participants hide local user until join is ready`() {
        val live = _live(listOf("alice", "bob"))

        assertEquals(
            listOf("bob"),
            visibleLiveParticipantsForStage(
                live.participants,
                currentUserId = "alice",
                localParticipantReady = false,
            ).map { participant -> participant.user.id },
        )
        assertEquals(
            listOf("alice", "bob"),
            visibleLiveParticipantsForStage(
                live.participants,
                currentUserId = "alice",
                localParticipantReady = true,
            ).map { participant -> participant.user.id },
        )
        assertEquals(
            listOf("alice", "bob"),
            visibleLiveParticipantsForStage(
                live.participants,
                currentUserId = "missing",
                localParticipantReady = false,
            ).map { participant -> participant.user.id },
        )
    }

    @Test
    fun `visible live participants hide joining users`() {
        val live =
            _liveWithParticipants(
                listOf(
                    _participant("alice", connectionState = "joining"),
                    _participant("bob", connectionState = "online"),
                ),
            )

        assertEquals(
            listOf("bob"),
            visibleLiveParticipantsForStage(
                live.participants,
                currentUserId = "missing",
                localParticipantReady = true,
            ).map { participant -> participant.user.id },
        )
    }

    @Test
    fun `visible live participants show joining users already in LiveKit`() {
        val live =
            _liveWithParticipants(
                listOf(
                    _participant("alice", connectionState = "joining"),
                    _participant("bob", connectionState = "online"),
                ),
            )

        assertEquals(
            listOf("alice", "bob"),
            visibleLiveParticipantsForStage(
                live.participants,
                currentUserId = "missing",
                localParticipantReady = true,
                connectedParticipantIds = setOf("alice"),
            ).map { participant -> participant.user.id },
        )
    }

    @Test
    fun `visible live participants keep reconnecting users in the roster`() {
        val live =
            _liveWithParticipants(
                listOf(
                    _participant("alice", connectionState = "reconnecting"),
                    _participant("bob", connectionState = "online"),
                ),
            )

        assertEquals(
            listOf("alice", "bob"),
            visibleLiveParticipantsForStage(
                live.participants,
                currentUserId = "missing",
                localParticipantReady = true,
            ).map { participant -> participant.user.id },
        )
    }

    @Test
    fun `authoritative presence keeps reconnecting and excludes joining`() {
        val live =
            _liveWithParticipants(
                listOf(
                    _participant("joining", connectionState = "joining"),
                    _participant("online", connectionState = "online"),
                    _participant("reconnecting", connectionState = "reconnecting"),
                    _participant("left", connectionState = "left"),
                ),
            )

        assertEquals(setOf("online", "reconnecting"), authoritativeLivePresenceUserIds(live))
    }

    @Test
    fun `authoritative presence changes ignore a reconnect transition`() {
        val reconnecting =
            authoritativeLivePresenceChanges(
                before = setOf("alice", "bob"),
                after = setOf("alice", "bob"),
            )
        val reconciled =
            authoritativeLivePresenceChanges(
                before = setOf("alice", "bob"),
                after = setOf("alice"),
            )

        assertTrue(reconnecting.joined.isEmpty())
        assertTrue(reconnecting.left.isEmpty())
        assertTrue(reconciled.joined.isEmpty())
        assertEquals(setOf("bob"), reconciled.left)
    }

    @Test
    fun `visible live participants show connected muted users after reconnect`() {
        val live =
            _liveWithParticipants(
                listOf(
                    _participant("alice", connectionState = "joining", micMuted = true),
                    _participant("bob", connectionState = "online"),
                ),
            )

        assertEquals(
            listOf("alice", "bob"),
            visibleLiveParticipantsForStage(
                live.participants,
                currentUserId = "missing",
                localParticipantReady = true,
                connectedParticipantIds = setOf("alice"),
            ).map { participant -> participant.user.id },
        )
    }

    @Test
    fun `live state detects connected LiveKit users missing from server state`() {
        val live = _live(listOf("alice"))

        assertFalse(
            liveStateMissingConnectedParticipants(
                live,
                connectedParticipantIds = setOf("alice"),
            ),
        )
        assertTrue(
            liveStateMissingConnectedParticipants(
                live,
                connectedParticipantIds = setOf("alice", "bob"),
            ),
        )
    }

    @Test
    fun `pickLiveStageShare prefers remote screen share then local fallback`() {
        val localShare = _Track("local", isScreenShare = true, isLocal = true)
        val remoteShare = _Track("remote", isScreenShare = true, isLocal = false)
        val camera = _Track("camera", isScreenShare = false, isLocal = false)

        assertSame(
            remoteShare,
            pickLiveStageShare(
                listOf(camera, localShare, remoteShare),
                isScreenShare = { track -> track.isScreenShare },
                isLocal = { track -> track.isLocal },
            ),
        )
        assertSame(
            localShare,
            pickLiveStageShare(
                listOf(camera, localShare),
                isScreenShare = { track -> track.isScreenShare },
                isLocal = { track -> track.isLocal },
            ),
        )
        assertNull(
            pickLiveStageShare(
                listOf(camera),
                isScreenShare = { track -> track.isScreenShare },
                isLocal = { track -> track.isLocal },
            ),
        )
    }

    @Test
    fun `stage share label and fullscreen guard are UI independent`() {
        assertEquals("Alice 的屏幕", liveScreenShareStageLabel("Alice"))
        assertEquals("屏幕共享", liveScreenShareStageLabel(""))
        assertTrue(
            canOpenLiveStageShareFullScreen(
                stageShareIdentity = "alice",
                localUserId = "bob",
            ),
        )
        assertFalse(
            canOpenLiveStageShareFullScreen(
                stageShareIdentity = "alice",
                localUserId = "alice",
            ),
        )
    }

    @Test
    fun `screen share viewers require an active share and exclude broadcaster`() {
        val broadcaster =
            _participant(
                "alice",
                screenSharing = true,
                screenViewers = listOf(_user("alice"), _user("bob")),
            )
        val live = _liveWithParticipants(listOf(broadcaster))

        assertEquals(
            listOf("bob"),
            liveScreenShareViewers(live, "alice").map { user -> user.id },
        )
        assertTrue(liveScreenShareViewers(live, "missing").isEmpty())
        assertTrue(
            liveScreenShareViewers(
                _liveWithParticipants(
                    listOf(
                        _participant("alice", screenViewers = listOf(_user("bob"))),
                    ),
                ),
                "alice",
            ).isEmpty(),
        )
    }

    @Test
    fun `live notices and failure messages stay outside UI`() {
        assertEquals("你已被移出语音", liveForciblyRemovedNotice())
        assertEquals("无法连接语音频道", liveVoiceConnectFailureMessage("network"))
        assertEquals("无法打开摄像头", liveCameraOpenFailureMessage("denied"))
        assertEquals("无法共享屏幕", liveScreenShareFailureMessage("denied"))
    }

    @Test
    fun `liveScreenShareByIdentity resolves active fullscreen share`() {
        val screenShare = _Track("alice", isScreenShare = true, isLocal = false)
        val camera = _Track("alice", isScreenShare = false, isLocal = false)

        assertSame(
            screenShare,
            liveScreenShareByIdentity(
                listOf(camera, screenShare),
                identity = "alice",
                trackIdentity = { track -> track.id },
                isScreenShare = { track -> track.isScreenShare },
            ),
        )
        assertNull(
            liveScreenShareByIdentity(
                listOf(screenShare),
                identity = null,
                trackIdentity = { track -> track.id },
                isScreenShare = { track -> track.isScreenShare },
            ),
        )
        assertNull(
            liveScreenShareByIdentity(
                listOf(camera),
                identity = "alice",
                trackIdentity = { track -> track.id },
                isScreenShare = { track -> track.isScreenShare },
            ),
        )
    }

    @Test
    fun `shouldExitMissingFullScreenShare exits only missing tracked share`() {
        val share = Any()

        assertTrue(
            shouldExitMissingFullScreenShare(
                fullScreenShareIdentity = "alice",
                fullScreenShare = null,
            ),
        )
        assertFalse(
            shouldExitMissingFullScreenShare(
                fullScreenShareIdentity = "alice",
                fullScreenShare = share,
            ),
        )
        assertFalse(
            shouldExitMissingFullScreenShare(
                fullScreenShareIdentity = null,
                fullScreenShare = null,
            ),
        )
    }

    @Test
    fun `ended local screen share patch gate follows joined selected room`() {
        assertTrue(
            shouldPatchEndedLocalScreenShare(
                localScreenSharing = true,
                sessionScreenSharing = false,
                joinedLiveRoomId = "room_1",
                selectedRoomId = "room_1",
            ),
        )
        assertFalse(
            shouldPatchEndedLocalScreenShare(
                localScreenSharing = false,
                sessionScreenSharing = false,
                joinedLiveRoomId = "room_1",
                selectedRoomId = "room_1",
            ),
        )
        assertFalse(
            shouldPatchEndedLocalScreenShare(
                localScreenSharing = true,
                sessionScreenSharing = true,
                joinedLiveRoomId = "room_1",
                selectedRoomId = "room_1",
            ),
        )
        assertFalse(
            shouldPatchEndedLocalScreenShare(
                localScreenSharing = true,
                sessionScreenSharing = false,
                joinedLiveRoomId = "room_2",
                selectedRoomId = "room_1",
            ),
        )
        assertFalse(
            shouldPatchEndedLocalScreenShare(
                localScreenSharing = true,
                sessionScreenSharing = false,
                joinedLiveRoomId = null,
                selectedRoomId = "room_1",
            ),
        )
    }

    @Test
    fun `joined live room summary prefers selected detail then room list`() {
        val selected =
            _roomDetail(
                id = "room_1",
                name = "Alpha",
                remarkName = "Project Alpha",
                defaultAvatarKey = "green-2",
            )
        val rooms =
            listOf(
                _roomCard(id = "room_1", name = "Old Alpha", defaultAvatarKey = "blue-3"),
                _roomCard(
                    id = "room_2",
                    name = "Beta",
                    remarkName = "Room Beta",
                    defaultAvatarKey = "amber-2",
                ),
            )

        val fromSelected =
            joinedLiveRoomSummary(
                joinedLiveRoomId = "room_1",
                selectedRoom = selected,
                rooms = rooms,
            )

        assertEquals("room_1", fromSelected?.roomId)
        assertEquals("Project Alpha", fromSelected?.displayName)
        assertEquals("Alpha", fromSelected?.avatarLabel)
        assertEquals("green-2", fromSelected?.defaultAvatarKey)

        val fromList =
            joinedLiveRoomSummary(
                joinedLiveRoomId = "room_2",
                selectedRoom = selected,
                rooms = rooms,
            )

        assertEquals("room_2", fromList?.roomId)
        assertEquals("Room Beta", fromList?.displayName)
        assertEquals("Beta", fromList?.avatarLabel)
        assertEquals("amber-2", fromList?.defaultAvatarKey)
    }

    @Test
    fun `joined live room summary hides when room cannot be resolved`() {
        assertNull(
            joinedLiveRoomSummary(
                joinedLiveRoomId = null,
                selectedRoom = null,
                rooms = emptyList(),
            ),
        )
        assertNull(
            joinedLiveRoomSummary(
                joinedLiveRoomId = "missing",
                selectedRoom = null,
                rooms = listOf(_roomCard(id = "room_1", name = "Alpha")),
            ),
        )
    }

    @Test
    fun `screen source selection keeps existing source or falls back to first`() {
        val screen = _Track("screen", isScreenShare = true, isLocal = true)
        val window = _Track("window", isScreenShare = true, isLocal = true)

        assertEquals(
            "window",
            reconcileLiveScreenSourceSelection(
                listOf(screen, window),
                selectedId = "window",
                sourceId = { source -> source.id },
            ),
        )
        assertEquals(
            "screen",
            reconcileLiveScreenSourceSelection(
                listOf(screen, window),
                selectedId = "missing",
                sourceId = { source -> source.id },
            ),
        )
        assertNull(
            reconcileLiveScreenSourceSelection(
                emptyList<_Track>(),
                selectedId = "missing",
                sourceId = { source -> source.id },
            ),
        )
    }

    @Test
    fun `screen source lookup and confirmation require a selected id`() {
        val screen = _Track("screen", isScreenShare = true, isLocal = true)

        assertSame(
            screen,
            liveScreenSourceById(
                listOf(screen),
                selectedId = "screen",
                sourceId = { source -> source.id },
            ),
        )
        assertNull(
            liveScreenSourceById(
                listOf(screen),
                selectedId = "missing",
                sourceId = { source -> source.id },
            ),
        )
        assertNull(
            liveScreenSourceById<_Track>(
                null,
                selectedId = "screen",
                sourceId = { source -> source.id },
            ),
        )
        assertTrue(canConfirmLiveScreenSourceSelection("screen"))
        assertFalse(canConfirmLiveScreenSourceSelection(null))
    }

    @Test
    fun `screen source picker state tracks loading selection and failures`() {
        val screen = _Track("screen", isScreenShare = true, isLocal = true)
        val window = _Track("window", isScreenShare = true, isLocal = true)
        val initial = LiveScreenSourcePickerState<_Track>()

        assertTrue(canLoadLiveScreenSources(initial))

        val started = liveScreenSourceLoadStarted(initial)

        assertTrue(started.loading)
        assertNull(started.error)
        assertFalse(canLoadLiveScreenSources(started))

        val loaded =
            liveScreenSourceLoadSucceeded(
                state = started,
                sources = listOf(screen, window),
                sourceId = { source -> source.id },
            )

        assertFalse(loaded.loading)
        assertEquals(listOf(screen, window), loaded.sources)
        assertEquals("screen", loaded.selectedId)
        assertNull(loaded.error)

        val selected = liveScreenSourceSelectedChanged(loaded, "window")

        assertEquals("window", selected.selectedId)

        val reloaded =
            liveScreenSourceLoadSucceeded(
                state = selected,
                sources = listOf(screen, window),
                sourceId = { source -> source.id },
            )

        assertEquals("window", reloaded.selectedId)

        val failed =
            liveScreenSourceLoadFailed(
                state = liveScreenSourceLoadStarted(reloaded),
                failure = IllegalStateException("sources failed"),
            )

        assertFalse(failed.loading)
        assertEquals(listOf(screen, window), failed.sources)
        assertEquals("window", failed.selectedId)
        assertEquals("加载语音频道失败", failed.error)
    }

    @Test
    fun `screen source list helpers expose body selected and thumbnail states`() {
        val screen = _Track("screen", isScreenShare = true, isLocal = true)

        assertEquals(
            LiveScreenSourceListBodyState.loading,
            liveScreenSourceListBodyState<_Track>(null),
        )
        assertEquals(
            LiveScreenSourceListBodyState.empty,
            liveScreenSourceListBodyState(emptyList<_Track>()),
        )
        assertEquals(
            LiveScreenSourceListBodyState.results,
            liveScreenSourceListBodyState(listOf(screen)),
        )

        assertTrue(
            liveScreenSourceSelected(
                screen,
                selectedId = "screen",
                sourceId = { source -> source.id },
            ),
        )
        assertFalse(
            liveScreenSourceSelected(
                screen,
                selectedId = "window",
                sourceId = { source -> source.id },
            ),
        )

        assertEquals(
            listOf(1, 2),
            visibleLiveScreenSourceThumbnail(
                thumbnail = listOf(1, 2),
                imageError = null,
            ),
        )
        assertNull(
            visibleLiveScreenSourceThumbnail(
                thumbnail = emptyList<Int>(),
                imageError = null,
            ),
        )
        assertNull(
            visibleLiveScreenSourceThumbnail(
                thumbnail = listOf(1),
                imageError = Any(),
            ),
        )
    }

    @Test
    fun `liveParticipantTileState highlights speaking or broadcasting users`() {
        val idle =
            liveParticipantTileState(
                _participant("alice"),
                speaking = false,
            )
        assertFalse(idle.broadcasting)
        assertFalse(idle.highlighted)
        assertFalse(idle.micMutedForDisplay)
        assertFalse(idle.micActive)

        val speaking =
            liveParticipantTileState(
                _participant("alice"),
                speaking = true,
            )
        assertTrue(speaking.highlighted)
        assertTrue(speaking.micActive)

        val broadcasting =
            liveParticipantTileState(
                _participant("alice", cameraOn = true),
                speaking = false,
            )
        assertTrue(broadcasting.broadcasting)
        assertTrue(broadcasting.highlighted)
        assertFalse(broadcasting.micActive)
    }

    @Test
    fun `liveParticipantTileState treats voice-blocked users as force-muted`() {
        val state =
            liveParticipantTileState(
                _participant("alice", voiceBlocked = true),
                speaking = true,
            )

        assertTrue(state.micMutedForDisplay)
        assertFalse(state.micActive)
        assertTrue(state.highlighted)
    }

    @Test
    fun `liveParticipantTileState keeps logical mute authoritative`() {
        val state =
            liveParticipantTileState(
                _participant("alice", micMuted = true),
                speaking = false,
                liveKitMicMuted = false,
            )

        assertTrue(state.micMutedForDisplay)
        assertFalse(state.micActive)

        val trackMuted =
            liveParticipantTileState(
                _participant("alice"),
                speaking = true,
                liveKitMicMuted = true,
            )

        assertTrue(trackMuted.micMutedForDisplay)
        assertFalse(trackMuted.micActive)

        val moderated =
            liveParticipantTileState(
                _participant("alice", micMuted = true, voiceBlocked = true),
                speaking = true,
                liveKitMicMuted = false,
            )

        assertTrue(moderated.micMutedForDisplay)
        assertFalse(moderated.micActive)
    }

    @Test
    fun `liveParticipantTileState ignores provisional LiveKit mute while joining`() {
        val joining =
            liveParticipantTileState(
                _participant("alice", connectionState = "joining"),
                speaking = false,
                liveKitMicMuted = true,
            )
        val online =
            liveParticipantTileState(
                _participant("alice", connectionState = "online"),
                speaking = false,
                liveKitMicMuted = true,
            )
        val logicallyMuted =
            liveParticipantTileState(
                _participant("alice", connectionState = "joining", micMuted = true),
                speaking = false,
                liveKitMicMuted = true,
            )

        assertFalse(joining.micMutedForDisplay)
        assertTrue(online.micMutedForDisplay)
        assertTrue(logicallyMuted.micMutedForDisplay)
    }

    @Test
    fun `live control display helpers describe toggled states`() {
        val mutedMic = liveMicControlState(micMuted = true, voiceBlocked = false)
        assertTrue(mutedMic.mutedForDisplay)
        assertFalse(mutedMic.active)
        assertTrue(mutedMic.enabled)

        val liveMic = liveMicControlState(micMuted = false, voiceBlocked = false)
        assertFalse(liveMic.mutedForDisplay)
        assertTrue(liveMic.active)
        assertTrue(liveMic.enabled)

        val blockedMic = liveMicControlState(micMuted = false, voiceBlocked = true)
        assertTrue(blockedMic.mutedForDisplay)
        assertFalse(blockedMic.active)
        assertFalse(blockedMic.enabled)
    }

    private fun _live(userIds: List<String>): LiveState {
        return _liveWithParticipants(
            userIds.map { id -> _participant(id, micMuted = true) },
        )
    }

    private fun _liveWithParticipants(participants: List<LiveParticipant>): LiveState {
        return LiveState(
            roomId = "room_1",
            participantCount = participants.size,
            participants = participants,
            updatedAt = DateTime.utc(2026, 6, 5),
        )
    }

    private fun _participant(
        id: String,
        micMuted: Boolean = false,
        voiceBlocked: Boolean = false,
        cameraOn: Boolean = false,
        screenSharing: Boolean = false,
        screenViewers: List<UserSummary> = emptyList(),
        connectionState: String = "connected",
        roomDisplayName: String? = null,
    ): LiveParticipant {
        return LiveParticipant(
            liveSessionId = "live_$id",
            user =
                UserSummary(
                    id = id,
                    username = "user_$id",
                    displayName = "User $id",
                    avatarUrl = null,
                    defaultAvatarKey = "blue-3",
                    roomDisplayName = roomDisplayName,
                ),
            joinedAt = DateTime.utc(2026, 6, 5),
            micMuted = micMuted,
            headphonesMuted = false,
            voiceBlocked = voiceBlocked,
            cameraOn = cameraOn,
            screenSharing = screenSharing,
            screenViewers = screenViewers,
            connectionState = connectionState,
        )
    }

    private fun _user(id: String): UserSummary {
        return UserSummary(
            id = id,
            username = "user_$id",
            displayName = "User $id",
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
        )
    }

    private class _Track(val id: String, val isScreenShare: Boolean, val isLocal: Boolean)

    private fun _roomCard(
        id: String,
        name: String,
        remarkName: String? = null,
        defaultAvatarKey: String = "blue-3",
    ): RoomCard {
        return RoomCard(
            id = id,
            name = name,
            remarkName = remarkName,
            avatarUrl = null,
            defaultAvatarKey = defaultAvatarKey,
            memberCount = 2,
            liveParticipantCount = 0,
            liveAvatarPreview = emptyList(),
            lastMessage = null,
            unreadCount = 0,
            updatedAt = DateTime.utc(2026, 6, 5),
        )
    }

    private fun _roomDetail(
        id: String,
        name: String,
        remarkName: String? = null,
        defaultAvatarKey: String = "blue-3",
    ): RoomDetail {
        return RoomDetail(
            id = id,
            name = name,
            remarkName = remarkName,
            avatarUrl = null,
            defaultAvatarKey = defaultAvatarKey,
            memberCount = 2,
            myMembership =
                RoomMembership(
                    joinedAt = DateTime.utc(2026, 6, 5),
                    role = "member",
                ),
            live = _live(emptyList()),
            createdAt = DateTime.utc(2026, 6, 5),
            updatedAt = DateTime.utc(2026, 6, 5),
        )
    }
}
