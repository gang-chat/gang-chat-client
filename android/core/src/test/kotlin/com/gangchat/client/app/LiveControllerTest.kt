package com.gangchat.client.app

import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.complete
import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.foundation.utf8Decode
import com.gangchat.client.protocol.ApiException
import com.gangchat.client.protocol.GangApiClient
import com.gangchat.client.protocol.LiveJoinResult
import com.gangchat.client.protocol.LiveKitConnectionInfo
import com.gangchat.client.protocol.LiveParticipant
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.MockClient
import com.gangchat.client.testing.pumpEventQueue
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

class LiveControllerTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `live state request queue preserves mutation order`() = gang.runTest {
        val queue = LiveStateRequestQueue()
        val firstGate = Completer<Unit>()
        val started = mutableListOf<Int>()

        val first =
            queue.run {
                started.add(1)
                firstGate.future.await()
                "first"
            }
        val second =
            queue.run {
                started.add(2)
                "second"
            }

        pumpEventQueue()
        assertEquals(listOf(1), started)
        assertFalse(queue.isIdle)

        firstGate.complete()
        assertEquals("first", first.await())
        assertEquals("second", second.await())
        assertEquals(listOf(1, 2), started)
        assertTrue(queue.isIdle)
    }

    @Test
    fun `live state request queue continues after a failed mutation`() = gang.runTest {
        val queue = LiveStateRequestQueue()

        val failed = queue.run<Unit> { throw IllegalStateException("failed") }
        val recovered = queue.run { 2 }

        var failure: Throwable? = null
        try {
            failed.await()
        } catch (error: IllegalStateException) {
            failure = error
        }
        assertNotNull(failure)
        assertEquals(2, recovered.await())
        assertTrue(queue.isIdle)
    }

    @Test
    fun `live room guards keep room switching and stale async work explicit`() {
        assertNull(
            joinedLiveRoomToDisconnectBeforeJoin(
                joinedLiveRoomId = null,
                targetRoomId = "room_1",
            ),
        )
        assertNull(
            joinedLiveRoomToDisconnectBeforeJoin(
                joinedLiveRoomId = "room_1",
                targetRoomId = "room_1",
            ),
        )
        assertEquals(
            "room_1",
            joinedLiveRoomToDisconnectBeforeJoin(
                joinedLiveRoomId = "room_1",
                targetRoomId = "room_2",
            ),
        )

        assertTrue(
            canPatchSelectedLiveState(
                joinedLiveRoomId = "room_1",
                selectedRoomId = "room_1",
            ),
        )
        assertFalse(
            canPatchSelectedLiveState(
                joinedLiveRoomId = null,
                selectedRoomId = "room_1",
            ),
        )
        assertFalse(
            canPatchSelectedLiveState(
                joinedLiveRoomId = "room_2",
                selectedRoomId = "room_1",
            ),
        )

        assertTrue(
            canApplyPickedScreenShareSource(
                pickedForRoomId = "room_1",
                joinedLiveRoomId = "room_1",
                selectedRoomId = "room_1",
            ),
        )
        assertFalse(
            canApplyPickedScreenShareSource(
                pickedForRoomId = "room_1",
                joinedLiveRoomId = "room_2",
                selectedRoomId = "room_1",
            ),
        )
        assertFalse(
            canApplyPickedScreenShareSource(
                pickedForRoomId = "room_1",
                joinedLiveRoomId = "room_1",
                selectedRoomId = "room_2",
            ),
        )
    }

    @Test
    fun `409 live state patch errors are treated as benign departure races`() {
        assertTrue(
            isBenignGoneLiveStatePatch(
                ApiException(
                    "gone",
                    statusCode = 409,
                    code = "participant_not_found",
                    requestId = null,
                ),
            ),
        )
        assertFalse(
            isBenignGoneLiveStatePatch(
                ApiException(
                    "server error",
                    statusCode = 500,
                    code = "request_failed",
                    requestId = null,
                ),
            ),
        )
        assertFalse(isBenignGoneLiveStatePatch(IllegalStateException("boom")))
    }

    @Test
    fun `live participant JSON keeps mirror and screen viewers backward compatible`() {
        val legacy =
            LiveParticipant.fromJson(
                _participantJson(user = _userJson("alice")),
            )
        val withViewer =
            LiveParticipant.fromJson(
                _participantJson(user = _userJson("alice")) +
                    mapOf(
                        "camera_mirrored" to true,
                        "screen_sharing" to true,
                        "screen_viewers" to listOf(_userJson("bob")),
                    ),
            )

        assertTrue(legacy.screenViewers.isEmpty())
        assertFalse(legacy.cameraMirrored)
        assertTrue(withViewer.cameraMirrored)
        assertEquals(listOf("bob"), withViewer.screenViewers.map { user -> user.id })
    }

    @Test
    fun `join state patches keep LiveKit side effects out of UI flags`() = gang.runTest {
        val api = _reducerApi()
        val controller = LiveController(api = api)
        val alice = _user("alice")
        val bob = _user("bob")

        val started = controller.patchJoinStarted(joinedLiveRoomId = "room_0")
        assertEquals("room_0", started.joinedLiveRoomId)
        assertTrue(started.joiningLive)
        assertTrue(started.livePanelOpen)
        assertNull(started.error)

        val disconnected =
            controller.patchJoinPreviousRoomDisconnected(
                live = _live("room_0", listOf(_participant(alice), _participant(bob))),
                rooms =
                    listOf(
                        _roomCard("room_0", liveCount = 2, livePreview = listOf(alice, bob)),
                    ),
                previousRoomId = "room_0",
                userId = "alice",
                livePanelOpen = false,
                error = "keep existing pane error",
            )
        assertEquals(listOf("bob"), disconnected.live?.participants?.map { item -> item.user.id })
        assertEquals(1, disconnected.rooms.single().liveParticipantCount)
        assertEquals(
            listOf("bob"),
            disconnected.rooms.single().liveAvatarPreview.map { user -> user.id },
        )
        assertNull(disconnected.joinedLiveRoomId)
        assertTrue(disconnected.joiningLive)
        assertFalse(disconnected.livePanelOpen)
        assertEquals("keep existing pane error", disconnected.error)

        val connected =
            controller.patchJoinConnected(
                roomId = "room_1",
                livePanelOpen = true,
                error = "preserved",
            )
        assertEquals("room_1", connected.joinedLiveRoomId)
        assertTrue(connected.joiningLive)
        assertTrue(connected.livePanelOpen)
        assertEquals("preserved", connected.error)

        val finished =
            controller.patchJoinFinished(
                joinedLiveRoomId = "room_1",
                livePanelOpen = true,
                error = null,
            )
        assertEquals("room_1", finished.joinedLiveRoomId)
        assertFalse(finished.joiningLive)
        assertTrue(finished.livePanelOpen)
        assertNull(finished.error)
        api.close()
    }

    @Test
    fun `removeUserFromLive updates selected live state and room preview`() = gang.runTest {
        val api = _reducerApi()
        val controller = LiveController(api = api)

        val alice = _user("alice")
        val bob = _user("bob")
        val patch =
            controller.removeUserFromLive(
                live = _live("room_1", listOf(_participant(alice), _participant(bob))),
                rooms =
                    listOf(
                        _roomCard("room_1", liveCount = 2, livePreview = listOf(alice, bob)),
                        _roomCard("room_2", liveCount = 1, livePreview = listOf(alice)),
                    ),
                roomId = "room_1",
                userId = "alice",
            )

        assertEquals(1, patch.live?.participantCount)
        assertEquals(listOf("bob"), patch.live?.participants?.map { item -> item.user.id })
        assertEquals(1, patch.rooms[0].liveParticipantCount)
        assertEquals(listOf("bob"), patch.rooms[0].liveAvatarPreview.map { user -> user.id })
        assertEquals(1, patch.rooms[1].liveParticipantCount)
        assertEquals(listOf("alice"), patch.rooms[1].liveAvatarPreview.map { user -> user.id })
        api.close()
    }

    @Test
    fun `patchLocalDeparture clears joined local state and removes self`() = gang.runTest {
        val api = _reducerApi()
        val controller = LiveController(api = api)
        val alice = _user("alice")
        val bob = _user("bob")

        val patch =
            controller.patchLocalDeparture(
                live = _live("room_1", listOf(_participant(alice), _participant(bob))),
                rooms =
                    listOf(
                        _roomCard("room_1", liveCount = 2, livePreview = listOf(alice, bob)),
                    ),
                joinedLiveRoomId = "room_1",
                userId = "alice",
                joiningLive = true,
            )

        assertNull(patch.joinedLiveRoomId)
        assertTrue(patch.joiningLive)
        assertFalse(patch.cameraOn)
        assertFalse(patch.screenSharing)
        assertFalse(patch.voiceBlocked)
        assertEquals(listOf("bob"), patch.live?.participants?.map { item -> item.user.id })
        assertEquals(1, patch.rooms.single().liveParticipantCount)
        assertEquals(
            listOf("bob"),
            patch.rooms.single().liveAvatarPreview.map { user -> user.id },
        )

        val live = _live("room_2", listOf(_participant(alice)))
        val rooms =
            listOf(
                _roomCard("room_2", liveCount = 1, livePreview = listOf(alice)),
            )
        val noJoinedRoomPatch =
            controller.patchLocalDeparture(
                live = live,
                rooms = rooms,
                joinedLiveRoomId = null,
                userId = "alice",
                joiningLive = false,
            )
        assertSame(live, noJoinedRoomPatch.live)
        assertSame(rooms, noJoinedRoomPatch.rooms)
        assertNull(noJoinedRoomPatch.joinedLiveRoomId)
        assertFalse(noJoinedRoomPatch.joiningLive)
        api.close()
    }

    @Test
    fun `patchPublishPermission mirrors LiveKit publish permission`() = gang.runTest {
        val api = _reducerApi()
        val controller = LiveController(api = api)

        var patch =
            controller.patchPublishPermission(
                canPublish = false,
                micMuted = false,
            )
        assertTrue(patch.voiceBlocked)
        assertTrue(patch.micMuted)

        patch = controller.patchPublishPermission(canPublish = true, micMuted = true)
        assertFalse(patch.voiceBlocked)
        assertTrue(patch.micMuted)
        api.close()
    }

    @Test
    fun `live output mute toggle flips local headphones state`() {
        assertTrue(
            liveOutputMuteToggled(headphonesMuted = false).headphonesMuted,
        )
        assertFalse(
            liveOutputMuteToggled(headphonesMuted = true).headphonesMuted,
        )
    }

    @Test
    fun `leaveLive reports a left connection state to the server`() = gang.runTest {
        var body: Any? = null
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("PATCH", request.method)
                        assertEquals("/api/v1/rooms/room_1/live/me", request.url.path)
                        body = jsonDecode(utf8Decode(request.bodyBytes))
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "participant" to
                                        _participantJson(
                                            user = _userJson("alice"),
                                            connectionState = "left",
                                        ),
                                ),
                            ),
                            200,
                            headers = mapOf("content-type" to "application/json; charset=utf-8"),
                        )
                    },
            )

        val participant =
            LiveController(
                api = api,
            ).leaveLive(roomId = "room_1")

        assertEquals(mapOf("connection_state" to "left"), body)
        assertEquals("left", participant.connectionState)
        api.close()
    }

    @Test
    fun `camera mirror updates use the existing live state endpoint`() = gang.runTest {
        var body: Any? = null
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("PATCH", request.method)
                        assertEquals("/api/v1/rooms/room_1/live/me", request.url.path)
                        body = jsonDecode(utf8Decode(request.bodyBytes))
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "participant" to
                                        _participantJson(user = _userJson("alice")) +
                                        mapOf(
                                            "camera_on" to true,
                                            "camera_mirrored" to true,
                                        ),
                                ),
                            ),
                            200,
                            headers = mapOf("content-type" to "application/json; charset=utf-8"),
                        )
                    },
            )

        val participant =
            LiveController(
                api = api,
            ).updateMyState(roomId = "room_1", cameraMirrored = true)

        assertEquals(mapOf("camera_mirrored" to true), body)
        assertTrue(participant.cameraMirrored)
        api.close()
    }

    @Test
    fun `updateMyLiveScreenView sends the watched broadcaster or clears it`() = gang.runTest {
        val bodies = mutableListOf<Any?>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("PATCH", request.method)
                        assertEquals("/api/v1/rooms/room_1/live/me/screen-view", request.url.path)
                        bodies.add(
                            jsonDecode(utf8Decode(request.bodyBytes)),
                        )
                        HttpResponse(
                            jsonEncode(mapOf("broadcaster_user_id" to null)),
                            200,
                            headers = mapOf("content-type" to "application/json; charset=utf-8"),
                        )
                    },
            )
        val controller = LiveController(api = api)

        controller.updateMyLiveScreenView(
            roomId = "room_1",
            broadcasterUserId = "alice",
        )
        controller.updateMyLiveScreenView(roomId = "room_1")

        assertEquals(
            listOf(
                mapOf("broadcaster_user_id" to "alice"),
                mapOf("broadcaster_user_id" to ""),
            ),
            bodies,
        )
        api.close()
    }

    @Test
    fun `kickParticipant uses live moderation kick action`() = gang.runTest {
        var body: Any? = null
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals(
                            "/api/v1/rooms/room_1/live/participants/bob/moderation",
                            request.url.path,
                        )
                        body = jsonDecode(utf8Decode(request.bodyBytes))
                        HttpResponse(
                            jsonEncode(mapOf("ok" to true)),
                            200,
                            headers = mapOf("content-type" to "application/json; charset=utf-8"),
                        )
                    },
            )

        LiveController(
            api = api,
        ).kickParticipant(roomId = "room_1", userId = "bob")

        assertEquals(mapOf("action" to "kick"), body)
        api.close()
    }

    @Test
    fun `patchJoinResult mirrors participant flags and room live preview`() = gang.runTest {
        val api = _reducerApi()
        val controller = LiveController(api = api)
        val alice = _user("alice")
        val bob = _user("bob")
        val participant =
            _participant(
                alice,
                micMuted = true,
                cameraOn = true,
                screenSharing = true,
                voiceBlocked = true,
            )
        val live = _live("room_1", listOf(participant, _participant(bob)))

        val patch =
            controller.patchJoinResult(
                rooms =
                    listOf(
                        _roomCard("room_1", liveCount = 0),
                        _roomCard("room_2", liveCount = 3),
                    ),
                result = _liveJoinResult(participant = participant, live = live),
            )

        assertTrue(patch.micMuted)
        assertFalse(patch.headphonesMuted)
        assertFalse(patch.cameraOn)
        assertTrue(patch.screenSharing)
        assertTrue(patch.voiceBlocked)
        assertFalse(patch.live.participants.first().cameraOn)
        assertTrue(patch.live.participants.first().screenSharing)
        assertEquals(2, patch.rooms[0].liveParticipantCount)
        assertEquals(
            listOf("alice", "bob"),
            patch.rooms[0].liveAvatarPreview.map { user -> user.id },
        )
        assertEquals(3, patch.rooms[1].liveParticipantCount)
        api.close()
    }

    @Test
    fun `patchJoinResult can show the joined mic unmuted when allowed`() = gang.runTest {
        val api = _reducerApi()
        val controller = LiveController(api = api)
        val alice = _user("alice")
        val participant = _participant(alice, micMuted = true)
        val live = _live("room_1", listOf(participant))

        val patch =
            controller.patchJoinResult(
                rooms = listOf(_roomCard("room_1", liveCount = 0)),
                result = _liveJoinResult(participant = participant, live = live),
                showMicUnmutedWhenAllowed = true,
            )

        assertFalse(patch.micMuted)
        assertFalse(patch.live.participants.single().micMuted)

        val blocked =
            _participant(
                alice,
                micMuted = true,
                micBlocked = true,
                voiceBlocked = true,
            )
        val blockedPatch =
            controller.patchJoinResult(
                rooms = listOf(_roomCard("room_1", liveCount = 0)),
                result =
                    _liveJoinResult(
                        participant = blocked,
                        live = _live("room_1", listOf(blocked)),
                    ),
                showMicUnmutedWhenAllowed = true,
            )

        assertTrue(blockedPatch.micMuted)
        assertTrue(blockedPatch.live.participants.single().micMuted)
        api.close()
    }

    @Test
    fun `patchStateUpdate mirrors participant flags and merges live roster`() = gang.runTest {
        val api = _reducerApi()
        val controller = LiveController(api = api)
        val alice = _participant(_user("alice"))
        val bob =
            _participant(
                _user("bob"),
                micMuted = true,
                voiceBlocked = true,
                cameraOn = true,
                screenSharing = true,
            )

        val patch =
            controller.patchStateUpdate(
                live = _live("room_1", listOf(alice)),
                participant = bob,
            )

        assertTrue(patch.micMuted)
        assertFalse(patch.headphonesMuted)
        assertTrue(patch.voiceBlocked)
        assertFalse(patch.cameraOn)
        assertTrue(patch.screenSharing)
        assertEquals(2, patch.live?.participantCount)
        assertEquals(
            listOf("alice", "bob"),
            patch.live?.participants?.map { item -> item.user.id },
        )
        api.close()
    }

    @Test
    fun `patchStateUpdate preserves room display names from the live roster`() = gang.runTest {
        val api = _reducerApi()
        val controller = LiveController(api = api)
        val existing =
            _participant(
                _user("alice").copyWith(roomDisplayName = "Room Alice"),
            )
        val incoming = _participant(_user("alice"), micMuted = true)

        val patch =
            controller.patchStateUpdate(
                live = _live("room_1", listOf(existing)),
                participant = incoming,
            )

        assertEquals(
            "Room Alice",
            patch.live?.participants?.single()?.user?.roomDisplayName,
        )
        assertEquals(true, patch.live?.participants?.single()?.micMuted)
        api.close()
    }

    @Test
    fun `patchModeratedParticipant keeps mic and headphones actions separate`() = gang.runTest {
        val api = _reducerApi()
        val controller = LiveController(api = api)
        val alice = _user("alice")
        val initial = _participant(alice)

        val headphonesMuted =
            controller
                .patchModeratedParticipant(
                    live = _live("room_1", listOf(initial)),
                    participant = initial,
                    action = LiveModerationAction.blockVoice,
                )!!
                .participants
                .single()
        assertFalse(headphonesMuted.micMuted)
        assertFalse(headphonesMuted.micBlocked)
        assertFalse(headphonesMuted.voiceBlocked)
        assertTrue(headphonesMuted.headphonesMuted)
        assertTrue(headphonesMuted.headphonesBlocked)
        assertFalse(headphonesMuted.headphonesListening)

        val micRestored =
            controller
                .patchModeratedParticipant(
                    live = _live("room_1", listOf(headphonesMuted)),
                    participant =
                        headphonesMuted.copyWith(
                            micMuted = true,
                            micBlocked = true,
                            voiceBlocked = true,
                        ),
                    action = LiveModerationAction.restoreVoice,
                )!!
                .participants
                .single()
        assertFalse(micRestored.micMuted)
        assertFalse(micRestored.micBlocked)
        assertFalse(micRestored.voiceBlocked)
        assertTrue(micRestored.headphonesMuted)
        assertTrue(micRestored.headphonesBlocked)

        val headphonesRestored =
            controller
                .patchModeratedParticipant(
                    live = _live("room_1", listOf(micRestored)),
                    participant =
                        micRestored.copyWith(
                            micMuted = true,
                            micBlocked = true,
                            voiceBlocked = true,
                        ),
                    action = LiveModerationAction.restoreHeadphones,
                )!!
                .participants
                .single()
        assertTrue(headphonesRestored.micMuted)
        assertTrue(headphonesRestored.micBlocked)
        assertTrue(headphonesRestored.voiceBlocked)
        assertFalse(headphonesRestored.headphonesMuted)
        assertFalse(headphonesRestored.headphonesBlocked)
        assertTrue(headphonesRestored.headphonesListening)
        api.close()
    }

    @Test
    fun `live kit mic sync is needed only when server changed mic state`() {
        assertFalse(
            shouldSyncLiveKitMicAfterServerPatch(
                requestedMicMuted = null,
                serverMicMuted = true,
            ),
        )
        assertFalse(
            shouldSyncLiveKitMicAfterServerPatch(
                requestedMicMuted = false,
                serverMicMuted = false,
            ),
        )
        assertTrue(
            shouldSyncLiveKitMicAfterServerPatch(
                requestedMicMuted = false,
                serverMicMuted = true,
            ),
        )
    }

    @Test
    fun `removeUserFromLive leaves unrelated state untouched`() = gang.runTest {
        val api = _reducerApi()
        val controller = LiveController(api = api)
        val alice = _user("alice")
        val live = _live("room_2", listOf(_participant(alice)))
        val rooms = listOf(_roomCard("room_1", liveCount = 0))

        val patch =
            controller.removeUserFromLive(
                live = live,
                rooms = rooms,
                roomId = "room_1",
                userId = "alice",
            )

        assertSame(live, patch.live)
        assertSame(rooms, patch.rooms)
        api.close()
    }

    @Test
    fun `mergeParticipant replaces existing session or appends new one`() = gang.runTest {
        val api = _reducerApi()
        val controller = LiveController(api = api)
        val alice = _participant(_user("alice"))
        val bob = _participant(_user("bob"))
        val live = _live("room_1", listOf(alice))

        val replaced =
            controller.mergeParticipant(
                live,
                _participant(_user("alice"), micMuted = true),
            )!!
        assertEquals(1, replaced.participantCount)
        assertEquals("alice", replaced.participants.single().user.id)
        assertTrue(replaced.participants.single().micMuted)
        assertEquals(live.updatedAt, replaced.updatedAt)

        val appended = controller.mergeParticipant(live, bob)!!
        assertEquals(2, appended.participantCount)
        assertEquals(listOf("alice", "bob"), appended.participants.map { item -> item.user.id })
        assertEquals(live.updatedAt, appended.updatedAt)

        assertNull(controller.mergeParticipant(null, bob))
        api.close()
    }

    @Test
    fun `local participant patch does not reject newer reconnect snapshot`() = gang.runTest {
        val api = _reducerApi()
        val liveController = LiveController(api = api)
        val roomsController = RoomsController(api = api)
        val initial =
            _live(
                "room_1",
                listOf(
                    _participant(_user("me")),
                    _participant(_user("other"), micMuted = true, headphonesMuted = false),
                ),
            )

        val locallyPatched =
            liveController.mergeParticipant(
                initial,
                _participant(_user("me"), micMuted = true),
            )!!
        val reconnectSnapshot =
            LiveState(
                roomId = initial.roomId,
                participantCount = initial.participantCount,
                participants =
                    listOf(
                        _participant(_user("me"), micMuted = true),
                        _participant(
                            _user("other"),
                            micMuted = false,
                            headphonesMuted = true,
                            headphonesListening = false,
                            cameraOn = true,
                        ).copyWith(
                            cameraMirrored = true,
                            connectionState = "online",
                            screenViewers = listOf(_user("viewer")),
                        ),
                    ),
                updatedAt = initial.updatedAt.add(1.milliseconds),
            )

        val patch =
            roomsController.patchSelectedLiveRefreshed(
                live = reconnectSnapshot,
                selectedRoomId = "room_1",
                currentLive = locallyPatched,
            )

        assertNotNull(patch)
        val other =
            patch!!.live.participants.single { participant -> participant.user.id == "other" }
        assertFalse(other.micMuted)
        assertTrue(other.headphonesMuted)
        assertFalse(other.headphonesListening)
        assertTrue(other.cameraOn)
        assertTrue(other.cameraMirrored)
        assertFalse(other.screenSharing)
        assertEquals("online", other.connectionState)
        assertEquals(listOf("viewer"), other.screenViewers.map { viewer -> viewer.id })
        api.close()
    }

    /** The reducer tests' API: any request fails the test, like Dart's `fail(...)`. */
    private fun _reducerApi(): GangApiClient {
        val httpClient: HttpClient =
            MockClient { request ->
                fail("Reducer test should not call the API: ${request.url}")
                throw AssertionError()
            }
        return GangApiClient(
            baseUrl = "http://example.test/api/v1",
            accessTokenProvider = { "token" },
            httpClient = httpClient,
        )
    }

    private fun _userJson(id: String): JsonMap {
        return mapOf(
            "id" to id,
            "username" to id,
            "display_name" to "User $id",
            "default_avatar_key" to "blue-3",
        )
    }

    private fun _participantJson(
        user: JsonMap,
        connectionState: String = "connected",
    ): JsonMap {
        return linkedMapOf(
            "live_session_id" to "live_${user["id"]}",
            "user" to user,
            "joined_at" to "2026-06-05T00:00:00Z",
            "mic_muted" to false,
            "headphones_muted" to false,
            "voice_blocked" to false,
            "camera_on" to false,
            "screen_sharing" to false,
            "connection_state" to connectionState,
        )
    }

    private fun _user(id: String): UserSummary {
        return UserSummary(
            id = id,
            username = id,
            displayName = "User $id",
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
        )
    }

    private fun _participant(
        user: UserSummary,
        micMuted: Boolean = false,
        micBlocked: Boolean = false,
        headphonesMuted: Boolean = false,
        headphonesBlocked: Boolean = false,
        headphonesListening: Boolean = true,
        voiceBlocked: Boolean = false,
        cameraOn: Boolean = false,
        screenSharing: Boolean = false,
    ): LiveParticipant {
        return LiveParticipant(
            liveSessionId = "live_${user.id}",
            user = user,
            joinedAt = DateTime.utc(2026, 6, 5),
            micMuted = micMuted,
            micBlocked = micBlocked,
            headphonesMuted = headphonesMuted,
            headphonesBlocked = headphonesBlocked,
            headphonesListening = headphonesListening,
            voiceBlocked = voiceBlocked,
            cameraOn = cameraOn,
            screenSharing = screenSharing,
            connectionState = "connected",
        )
    }

    private fun _liveJoinResult(
        participant: LiveParticipant,
        live: LiveState,
    ): LiveJoinResult {
        return LiveJoinResult(
            liveKit =
                LiveKitConnectionInfo(
                    serverUrl = "wss://live.example.test",
                    token = "token",
                    tokenExpiresAt = DateTime.utc(2026, 6, 5, 1),
                    roomName = live.roomId,
                ),
            participant = participant,
            live = live,
        )
    }

    private fun _live(roomId: String, participants: List<LiveParticipant>): LiveState {
        return LiveState(
            roomId = roomId,
            participantCount = participants.size,
            participants = participants,
            updatedAt = DateTime.utc(2026, 6, 5),
        )
    }

    private fun _roomCard(
        id: String,
        liveCount: Int = 0,
        livePreview: List<UserSummary> = emptyList(),
    ): RoomCard {
        return RoomCard(
            id = id,
            name = "Room $id",
            avatarUrl = null,
            defaultAvatarKey = "room-1",
            memberCount = 2,
            liveParticipantCount = liveCount,
            liveAvatarPreview = livePreview,
            lastMessage = null,
            unreadCount = 0,
            updatedAt = DateTime.utc(2026, 6, 5),
        )
    }
}
