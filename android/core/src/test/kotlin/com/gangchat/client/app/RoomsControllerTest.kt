package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.protocol.GangApiClient
import com.gangchat.client.protocol.JoinRequest
import com.gangchat.client.protocol.LastMessagePreview
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomInvite
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.RoomMembership
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.MockClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

class RoomsControllerTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `loadRoomMembersSnapshot paginates and isolates request errors`() = gang.runTest {
        val seen = mutableListOf<Uri>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        seen.add(request.url)
                        if (request.url.path == "/api/v1/rooms/room_1/members") {
                            val cursor = request.url.queryParameters["cursor"]
                            if (cursor == null) {
                                return@MockClient _jsonResponse(
                                    mapOf(
                                        "members" to listOf(_memberJson("user_1")),
                                        "next_cursor" to "page_2",
                                    ),
                                )
                            }
                            assertEquals("page_2", cursor)
                            return@MockClient _jsonResponse(
                                mapOf(
                                    "members" to listOf(_memberJson("user_2")),
                                    "next_cursor" to "",
                                ),
                            )
                        }
                        if (request.url.path == "/api/v1/rooms/room_1/live") {
                            return@MockClient _jsonResponse(mapOf("live" to _liveJson(participantCount = 2)))
                        }
                        if (request.url.path == "/api/v1/rooms/room_1/join-requests") {
                            return@MockClient HttpResponse("forbidden", 403)
                        }
                        fail("Unexpected request: ${request.url}")
                        throw AssertionError()
                    },
            )

        val snapshot =
            RoomsController(api = api).loadRoomMembersSnapshot(
                roomId = "room_1",
                fallbackLive = _liveState(participantCount = 0),
                includeJoinRequests = true,
            )

        assertEquals(
            listOf("user_1", "user_2"),
            snapshot.members.map { member -> member.user.id },
        )
        assertEquals(2, snapshot.live.participantCount)
        assertTrue(snapshot.joinRequests.isEmpty())
        assertNotNull(snapshot.joinRequestsError)
        assertEquals(
            listOf(
                "/api/v1/rooms/room_1/members",
                "/api/v1/rooms/room_1/members",
                "/api/v1/rooms/room_1/live",
                "/api/v1/rooms/room_1/join-requests",
            ),
            seen.map { uri -> uri.path },
        )
        api.close()
    }

    @Test
    fun `loadRoomMembersAndLive falls back when live snapshot fails`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        if (request.url.path == "/api/v1/rooms/room_1/members") {
                            return@MockClient _jsonResponse(
                                mapOf(
                                    "members" to listOf(_memberJson("user_1")),
                                    "next_cursor" to null,
                                ),
                            )
                        }
                        if (request.url.path == "/api/v1/rooms/room_1/live") {
                            return@MockClient HttpResponse("unavailable", 503)
                        }
                        fail("Unexpected request: ${request.url}")
                        throw AssertionError()
                    },
            )

        val fallback = _liveState(participantCount = 9)
        val snapshot =
            RoomsController(
                api = api,
            ).loadRoomMembersAndLive(roomId = "room_1", fallbackLive = fallback)

        assertEquals("user_1", snapshot.members.single().user.id)
        assertSame(fallback, snapshot.live)
        api.close()
    }

    @Test
    fun `hasPendingJoinRequests uses caller permission gate`() = gang.runTest {
        var requestedJoinRequests = false
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requestedJoinRequests = true
                        assertEquals("/api/v1/rooms/room_1/join-requests", request.url.path)
                        _jsonResponse(
                            mapOf(
                                "requests" to
                                    listOf(
                                        mapOf(
                                            "id" to "request_1",
                                            "status" to "pending",
                                            "user" to _userJson("user_1"),
                                            "created_at" to "2026-06-04T00:00:00Z",
                                        ),
                                    ),
                            ),
                        )
                    },
            )
        val controller = RoomsController(api = api)

        assertFalse(
            controller.hasPendingJoinRequests(
                _roomDetail("room_1"),
                canReviewJoinRequests = false,
            ),
        )
        assertFalse(requestedJoinRequests)

        assertTrue(
            controller.hasPendingJoinRequests(
                _roomDetail("room_1"),
                canReviewJoinRequests = true,
            ),
        )
        assertTrue(requestedJoinRequests)
        api.close()
    }

    @Test
    fun `room list reducers remove cards invites and join requests by id`() {
        val api = _patchApi("Reducer test should not call the API")
        val controller = RoomsController(api = api)

        assertEquals(
            listOf("room_2"),
            controller
                .removeRoomCard(listOf(_roomCard("room_1"), _roomCard("room_2")), "room_1")
                .map { room -> room.id },
        )
        assertEquals(
            listOf("room_3"),
            controller
                .patchRoomCardsRefreshed(rooms = listOf(_roomCard("room_3")))
                .rooms
                .map { room -> room.id },
        )
        assertEquals(
            listOf("room_1", "updated room"),
            controller
                .patchRoomCardUpserted(
                    rooms = listOf(_roomCard("room_1"), _roomCard("room_2")),
                    room = _roomCard("room_2", name = "updated room"),
                )
                .rooms
                .map { room -> room.name },
        )
        assertEquals(
            7,
            controller
                .patchRoomCardUpdated(
                    rooms = listOf(_roomCard("room_1", unreadCount = 7)),
                    incoming = _roomCard("room_1", name = "updated room"),
                )
                .rooms
                .single()
                .unreadCount,
        )
        assertEquals(
            listOf("invite_1"),
            controller
                .removeRoomInvite(
                    listOf(
                        _roomInvite("invite_1"),
                        _roomInvite("invite_2"),
                    ),
                    "invite_2",
                )
                .map { invite -> invite.id },
        )
        assertEquals(
            listOf("request_2"),
            controller
                .removeJoinRequest(
                    listOf(
                        _joinRequest("request_1"),
                        _joinRequest("request_2"),
                    ),
                    "request_1",
                )
                .map { request -> request.id },
        )
        api.close()
    }

    @Test
    fun `room detail and live refresh patches guard selected room state`() {
        val api = _patchApi("Reducer test should not call the API")
        val controller = RoomsController(api = api)
        val detail = _roomDetail("room_1")

        val detailPatch =
            controller.patchRoomDetailApplied(
                rooms = listOf(_roomCard("room_2")),
                detail = detail,
            )
        assertSame(detail, detailPatch.selectedRoom)
        assertEquals(listOf("room_1", "room_2"), detailPatch.rooms.map { room -> room.id })

        assertNull(
            controller.patchSelectedRoomDetailRefreshed(
                rooms = emptyList(),
                selectedRoomId = "room_2",
                detail = detail,
            ),
        )
        val selectedDetailPatch =
            controller.patchSelectedRoomDetailRefreshed(
                rooms = emptyList(),
                selectedRoomId = "room_1",
                detail = detail,
            )
        assertSame(detail, selectedDetailPatch?.selectedRoom)
        assertEquals("room_1", selectedDetailPatch?.rooms?.single()?.id)

        val live = _liveState(participantCount = 2)
        val livePatch =
            controller.patchSelectedLiveRefreshed(
                live = live,
                selectedRoomId = "room_1",
            )
        assertSame(live, livePatch?.live)
        assertNull(
            controller.patchSelectedLiveRefreshed(
                live = live,
                selectedRoomId = "room_2",
            ),
        )
        val newerLive =
            LiveState.fromJson(
                _liveJson(participantCount = 3) + mapOf("updated_at" to "2026-06-04T00:00:02Z"),
            )
        assertNull(
            controller.patchSelectedLiveRefreshed(
                live = live,
                selectedRoomId = "room_1",
                currentLive = newerLive,
            ),
        )
        api.close()
    }

    @Test
    fun `room deleted reducer clears selected room state only when selected`() {
        val api = _patchApi("Reducer test should not call the API")
        val controller = RoomsController(api = api)
        val selectedRoom = _roomDetail("room_1")
        val messages = listOf(_message("message_1", roomId = "room_1"))
        val live = _liveState(participantCount = 2)

        val selectedPatch =
            controller.patchRoomDeleted(
                rooms = listOf(_roomCard("room_1"), _roomCard("room_2")),
                selectedRoomId = "room_1",
                selectedRoom = selectedRoom,
                selectedRoomHasPendingJoinRequests = true,
                messages = messages,
                live = live,
                livePanelOpen = true,
                settingsOpen = true,
                joinedLiveRoomId = "room_1",
                data = mapOf("room_id" to "room_1"),
            )!!

        assertEquals(listOf("room_2"), selectedPatch.rooms.map { room -> room.id })
        assertNull(selectedPatch.selectedRoomId)
        assertNull(selectedPatch.selectedRoom)
        assertFalse(selectedPatch.selectedRoomHasPendingJoinRequests)
        assertTrue(selectedPatch.messages.isEmpty())
        assertNull(selectedPatch.live)
        assertFalse(selectedPatch.livePanelOpen)
        assertFalse(selectedPatch.settingsOpen)
        assertNull(selectedPatch.joinedLiveRoomId)
        assertTrue(selectedPatch.wasSelected)
        assertTrue(selectedPatch.shouldDisconnectLive)

        val retainedRoom = _roomDetail("room_2")
        val retainedPatch =
            controller.patchRoomDeleted(
                rooms = listOf(_roomCard("room_1"), _roomCard("room_2")),
                selectedRoomId = "room_2",
                selectedRoom = retainedRoom,
                selectedRoomHasPendingJoinRequests = true,
                messages = messages,
                live = live,
                livePanelOpen = true,
                settingsOpen = true,
                joinedLiveRoomId = "room_2",
                data = mapOf("room_id" to "room_1"),
            )!!

        assertEquals(listOf("room_2"), retainedPatch.rooms.map { room -> room.id })
        assertEquals("room_2", retainedPatch.selectedRoomId)
        assertSame(retainedRoom, retainedPatch.selectedRoom)
        assertTrue(retainedPatch.selectedRoomHasPendingJoinRequests)
        assertSame(messages, retainedPatch.messages)
        assertSame(live, retainedPatch.live)
        assertTrue(retainedPatch.livePanelOpen)
        assertTrue(retainedPatch.settingsOpen)
        assertEquals("room_2", retainedPatch.joinedLiveRoomId)
        assertFalse(retainedPatch.wasSelected)
        assertFalse(retainedPatch.shouldDisconnectLive)
        assertNull(
            controller.patchRoomDeleted(
                rooms = emptyList(),
                selectedRoomId = null,
                selectedRoom = null,
                selectedRoomHasPendingJoinRequests = false,
                messages = emptyList(),
                live = null,
                livePanelOpen = false,
                settingsOpen = false,
                joinedLiveRoomId = null,
                data = emptyMap(),
            ),
        )
        api.close()
    }

    @Test
    fun `room role changed reducer patches only the selected room`() {
        val api = _patchApi("Reducer test should not call the API")
        val controller = RoomsController(api = api)
        val selectedRoom = _roomDetail("room_1")

        val patch =
            controller.patchRoomRoleChanged(
                selectedRoom = selectedRoom,
                data = mapOf("room_id" to "room_1", "role" to "admin"),
            )!!

        assertEquals("room_1", patch.roomId)
        assertEquals("room_1", patch.selectedRoom.id)
        assertEquals("admin", patch.selectedRoom.myMembership.role)
        assertEquals("member", selectedRoom.myMembership.role)

        assertNull(
            controller.patchRoomRoleChanged(
                selectedRoom = selectedRoom,
                data = mapOf("room_id" to "room_2", "role" to "admin"),
            ),
        )
        assertNull(
            controller.patchRoomRoleChanged(
                selectedRoom = selectedRoom,
                data = mapOf("room_id" to "room_1"),
            ),
        )
        assertNull(
            controller.patchRoomRoleChanged(
                selectedRoom = null,
                data = mapOf("room_id" to "room_1", "role" to "admin"),
            ),
        )
        api.close()
    }

    @Test
    fun `room open guards reject duplicate and stale async work`() {
        assertTrue(
            shouldSkipRoomOpenRequest(
                loadingRoom = true,
                selectedRoomId = "room_1",
                roomId = "room_1",
            ),
        )
        assertFalse(
            shouldSkipRoomOpenRequest(
                loadingRoom = true,
                selectedRoomId = "room_2",
                roomId = "room_1",
            ),
        )
        assertFalse(
            shouldSkipRoomOpenRequest(
                loadingRoom = false,
                selectedRoomId = "room_1",
                roomId = "room_1",
            ),
        )

        assertTrue(
            canApplyRoomOpenResult(
                requestedRoomId = "room_1",
                selectedRoomId = "room_1",
            ),
        )
        assertFalse(
            canApplyRoomOpenResult(
                requestedRoomId = "room_1",
                selectedRoomId = "room_2",
            ),
        )

        assertTrue(
            shouldShowOptimisticRoomOpenRefreshFailure(
                hasOptimisticDetail = true,
                requestedRoomId = "room_1",
                selectedRoomId = "room_1",
            ),
        )
        assertFalse(
            shouldShowOptimisticRoomOpenRefreshFailure(
                hasOptimisticDetail = false,
                requestedRoomId = "room_1",
                selectedRoomId = "room_1",
            ),
        )
        assertFalse(
            shouldShowOptimisticRoomOpenRefreshFailure(
                hasOptimisticDetail = true,
                requestedRoomId = "room_1",
                selectedRoomId = "room_2",
            ),
        )

        assertTrue(
            shouldFinishRoomOpenLoading(
                requestedRoomId = "room_1",
                selectedRoomId = "room_1",
            ),
        )
        assertFalse(
            shouldFinishRoomOpenLoading(
                requestedRoomId = "room_1",
                selectedRoomId = null,
            ),
        )

        assertTrue(
            canApplyLiveRefresh(
                live = _liveState(participantCount = 2),
                selectedRoomId = "room_1",
            ),
        )
        assertFalse(
            canApplyLiveRefresh(
                live = _liveState(participantCount = 2),
                selectedRoomId = "room_2",
            ),
        )
    }

    @Test
    fun `room list load patches preserve loading semantics`() {
        val api = _patchApi("Reducer test should not call the API")
        val controller = RoomsController(api = api)
        val existingRooms = listOf(_roomCard("room_1"))
        val loadedRooms = listOf(_roomCard("room_2"))

        val started = controller.patchRoomListLoadStarted(rooms = existingRooms)
        assertSame(existingRooms, started.rooms)
        assertTrue(started.loading)
        assertNull(started.error)

        val succeeded = controller.patchRoomListLoadSucceeded(rooms = loadedRooms)
        assertEquals(listOf("room_2"), succeeded.rooms.map { room -> room.id })
        assertFalse(succeeded.loading)
        assertNull(succeeded.error)

        val failed =
            controller.patchRoomListLoadFailed(
                rooms = existingRooms,
                failure = "load failed",
            )
        assertSame(existingRooms, failed.rooms)
        assertFalse(failed.loading)
        assertEquals("操作失败，请稍后重试", failed.error)
        api.close()
    }

    @Test
    fun `room open state patches preserve Home open-room semantics`() {
        val api = _patchApi("Reducer test should not call the API")
        val controller = RoomsController(api = api)
        val currentRoom = _roomDetail("room_1")
        val optimisticRoom = _roomDetail("room_2")
        val currentMessages = listOf(_message("message_1", roomId = "room_1"))
        val currentLive = _liveState(participantCount = 2)

        val loadingPatch =
            controller.patchRoomOpenStarted(
                roomId = "room_2",
                currentSelectedRoom = currentRoom,
                currentMessages = currentMessages,
                currentLive = currentLive,
                currentLivePanelOpen = true,
                joinLive = false,
            )
        assertFalse(loadingPatch.settingsOpen)
        assertEquals("room_2", loadingPatch.selectedRoomId)
        assertSame(currentRoom, loadingPatch.selectedRoom)
        assertTrue(loadingPatch.loadingRoom)
        assertNull(loadingPatch.error)
        assertFalse(loadingPatch.selectedRoomHasPendingJoinRequests)
        assertSame(currentMessages, loadingPatch.messages)
        assertSame(currentLive, loadingPatch.live)
        assertFalse(loadingPatch.livePanelOpen)

        val optimisticPatch =
            controller.patchRoomOpenStarted(
                roomId = "room_2",
                currentSelectedRoom = currentRoom,
                currentMessages = currentMessages,
                currentLive = currentLive,
                currentLivePanelOpen = true,
                joinLive = true,
                optimisticDetail = optimisticRoom,
            )
        assertSame(optimisticRoom, optimisticPatch.selectedRoom)
        assertTrue(optimisticPatch.messages.isEmpty())
        assertSame(optimisticRoom.live, optimisticPatch.live)
        assertTrue(optimisticPatch.livePanelOpen)

        val snapshot =
            RoomOpenSnapshot(
                detail = optimisticRoom,
                messages = listOf(_message("message_2", roomId = "room_2")),
                live = _liveState(participantCount = 4),
            )
        val noJoinEffects =
            roomOpenSucceededEffects(
                snapshot = snapshot,
                joinLive = false,
            )
        assertSame(optimisticRoom, noJoinEffects.joinRequestBadgeRoom)
        assertNull(noJoinEffects.joinLiveSource)

        val joinEffects =
            roomOpenSucceededEffects(
                snapshot = snapshot,
                joinLive = true,
            )
        assertSame(optimisticRoom, joinEffects.joinRequestBadgeRoom)
        assertEquals("room_card_speaker", joinEffects.joinLiveSource)

        val successPatch =
            controller.patchRoomOpenSucceeded(
                currentSettingsOpen = true,
                currentLoadingRoom = true,
                currentError = "kept",
                currentSelectedRoomHasPendingJoinRequests = true,
                snapshot = snapshot,
                joinLive = true,
            )
        assertTrue(successPatch.settingsOpen)
        assertEquals("room_2", successPatch.selectedRoomId)
        assertSame(optimisticRoom, successPatch.selectedRoom)
        assertTrue(successPatch.loadingRoom)
        assertEquals("kept", successPatch.error)
        assertTrue(successPatch.selectedRoomHasPendingJoinRequests)
        assertSame(snapshot.messages, successPatch.messages)
        assertSame(snapshot.live, successPatch.live)
        assertTrue(successPatch.livePanelOpen)

        val failedPatch =
            controller.patchRoomOpenFailed(
                settingsOpen = true,
                selectedRoomId = "room_2",
                selectedRoom = optimisticRoom,
                loadingRoom = true,
                selectedRoomHasPendingJoinRequests = true,
                messages = snapshot.messages,
                live = snapshot.live,
                livePanelOpen = true,
                failure = "open failed",
            )
        assertTrue(failedPatch.settingsOpen)
        assertEquals("room_2", failedPatch.selectedRoomId)
        assertSame(optimisticRoom, failedPatch.selectedRoom)
        assertTrue(failedPatch.loadingRoom)
        assertEquals("操作失败，请稍后重试", failedPatch.error)
        assertTrue(failedPatch.selectedRoomHasPendingJoinRequests)
        assertSame(snapshot.messages, failedPatch.messages)
        assertSame(snapshot.live, failedPatch.live)
        assertTrue(failedPatch.livePanelOpen)

        val finishedPatch =
            controller.patchRoomOpenFinished(
                settingsOpen = true,
                selectedRoomId = "room_2",
                selectedRoom = optimisticRoom,
                error = "stale error",
                selectedRoomHasPendingJoinRequests = true,
                messages = snapshot.messages,
                live = snapshot.live,
                livePanelOpen = true,
            )
        assertTrue(finishedPatch.settingsOpen)
        assertEquals("room_2", finishedPatch.selectedRoomId)
        assertSame(optimisticRoom, finishedPatch.selectedRoom)
        assertFalse(finishedPatch.loadingRoom)
        assertEquals("stale error", finishedPatch.error)
        assertTrue(finishedPatch.selectedRoomHasPendingJoinRequests)
        assertSame(snapshot.messages, finishedPatch.messages)
        assertSame(snapshot.live, finishedPatch.live)
        assertTrue(finishedPatch.livePanelOpen)
        api.close()
    }

    @Test
    fun `room members dialog load patches preserve dialog state semantics`() {
        val api = _patchApi("Patch test should not call the API")
        val controller = RoomsController(api = api)
        val members = listOf(_roomMember("user_1"))
        val requests = listOf(_joinRequest("request_1"))
        val live = _liveState(participantCount = 1)

        val started =
            controller.patchRoomMembersLoadStarted(
                members = members,
                requests = requests,
                live = live,
                changed = true,
                busyRequestIds = setOf("request_1"),
            )
        assertSame(members, started.members)
        assertSame(requests, started.requests)
        assertSame(live, started.live)
        assertTrue(started.loading)
        assertTrue(started.changed)
        assertNull(started.error)
        assertNull(started.requestError)
        assertEquals(setOf("request_1"), started.busyRequestIds)

        val snapshot =
            RoomMembersSnapshot(
                members = listOf(_roomMember("user_2")),
                live = _liveState(participantCount = 2),
                joinRequests = listOf(_joinRequest("request_2")),
                joinRequestsError = "forbidden",
            )
        val succeeded =
            controller.patchRoomMembersLoadSucceeded(
                snapshot = snapshot,
                changed = false,
                busyRequestIds = setOf("request_2"),
            )
        assertSame(snapshot.members, succeeded.members)
        assertSame(snapshot.joinRequests, succeeded.requests)
        assertSame(snapshot.live, succeeded.live)
        assertFalse(succeeded.loading)
        assertFalse(succeeded.changed)
        assertNull(succeeded.error)
        assertEquals("forbidden", succeeded.requestError)
        assertTrue(succeeded.hasPendingRequests)
        assertFalse(succeeded.shouldNotifyPendingRequests)

        val failed =
            controller.patchRoomMembersLoadFailed(
                members = members,
                requests = requests,
                live = live,
                changed = true,
                busyRequestIds = setOf("request_1"),
                failure = Exception("load failed"),
            )
        assertSame(members, failed.members)
        assertSame(requests, failed.requests)
        assertSame(live, failed.live)
        assertFalse(failed.loading)
        assertTrue(failed.changed)
        assertEquals("操作失败，请稍后重试", failed.error)
        assertNull(failed.requestError)
        assertEquals(setOf("request_1"), failed.busyRequestIds)
        api.close()
    }

    @Test
    fun `room members dialog reload patches isolate member and request errors`() {
        val api = _patchApi("Patch test should not call the API")
        val controller = RoomsController(api = api)
        val members = listOf(_roomMember("user_1"))
        val requests = listOf(_joinRequest("request_1"))
        val live = _liveState(participantCount = 1)
        val liveSnapshot =
            RoomMembersLiveSnapshot(
                members = listOf(_roomMember("user_2")),
                live = _liveState(participantCount = 3),
            )

        val membersReloaded =
            controller.patchRoomMembersAndLiveReloadSucceeded(
                snapshot = liveSnapshot,
                requests = requests,
                loading = true,
                changed = true,
                requestError = "request error",
                busyRequestIds = setOf("request_1"),
            )
        assertSame(liveSnapshot.members, membersReloaded.members)
        assertSame(requests, membersReloaded.requests)
        assertSame(liveSnapshot.live, membersReloaded.live)
        assertTrue(membersReloaded.loading)
        assertTrue(membersReloaded.changed)
        assertNull(membersReloaded.error)
        assertEquals("request error", membersReloaded.requestError)
        assertEquals(setOf("request_1"), membersReloaded.busyRequestIds)

        val membersFailed =
            controller.patchRoomMembersAndLiveReloadFailed(
                members = members,
                requests = requests,
                live = live,
                loading = false,
                changed = true,
                requestError = "request error",
                busyRequestIds = setOf("request_1"),
                failure = Exception("members failed"),
            )
        assertSame(members, membersFailed.members)
        assertSame(requests, membersFailed.requests)
        assertSame(live, membersFailed.live)
        assertFalse(membersFailed.loading)
        assertTrue(membersFailed.changed)
        assertEquals("操作失败，请稍后重试", membersFailed.error)
        assertEquals("request error", membersFailed.requestError)

        val requestsReloaded =
            controller.patchRoomJoinRequestsReloadSucceeded(
                members = members,
                live = live,
                requests = listOf(_joinRequest("request_2")),
                loading = false,
                changed = false,
                busyRequestIds = setOf("request_1"),
            )
        assertSame(members, requestsReloaded.members)
        assertEquals(
            listOf("request_2"),
            requestsReloaded.requests.map { request -> request.id },
        )
        assertSame(live, requestsReloaded.live)
        assertFalse(requestsReloaded.loading)
        assertFalse(requestsReloaded.changed)
        assertNull(requestsReloaded.error)
        assertNull(requestsReloaded.requestError)
        assertTrue(requestsReloaded.hasPendingRequests)
        assertTrue(requestsReloaded.shouldNotifyPendingRequests)

        val requestsFailed =
            controller.patchRoomJoinRequestsReloadFailed(
                members = members,
                requests = requests,
                live = live,
                loading = false,
                changed = false,
                busyRequestIds = setOf("request_1"),
                failure = Exception("requests failed"),
            )
        assertSame(members, requestsFailed.members)
        assertSame(requests, requestsFailed.requests)
        assertSame(live, requestsFailed.live)
        assertNull(requestsFailed.error)
        assertEquals("操作失败，请稍后重试", requestsFailed.requestError)
        assertEquals(setOf("request_1"), requestsFailed.busyRequestIds)
        api.close()
    }

    @Test
    fun `room members dialog review patches update busy ids and requests`() {
        val api = _patchApi("Patch test should not call the API")
        val controller = RoomsController(api = api)
        val members = listOf(_roomMember("user_1"))
        val requests = listOf(_joinRequest("request_1"), _joinRequest("request_2"))
        val live = _liveState(participantCount = 1)

        val started =
            controller.patchJoinRequestReviewStarted(
                members = members,
                requests = requests,
                live = live,
                loading = false,
                changed = false,
                requestId = "request_1",
                busyRequestIds = setOf("request_2"),
            )
        assertSame(members, started.members)
        assertEquals(requests, started.requests)
        assertSame(live, started.live)
        assertFalse(started.loading)
        assertFalse(started.changed)
        assertNull(started.error)
        assertNull(started.requestError)
        assertEquals(setOf("request_1", "request_2"), started.busyRequestIds)

        val succeeded =
            controller.patchJoinRequestReviewSucceeded(
                members = members,
                requests = started.requests,
                live = live,
                loading = false,
                changed = false,
                error = "kept",
                requestError = null,
                requestId = "request_1",
                busyRequestIds = started.busyRequestIds,
            )
        assertSame(members, succeeded.members)
        assertEquals(listOf("request_2"), succeeded.requests.map { request -> request.id })
        assertSame(live, succeeded.live)
        assertFalse(succeeded.loading)
        assertTrue(succeeded.changed)
        assertEquals("kept", succeeded.error)
        assertNull(succeeded.requestError)
        assertEquals(setOf("request_2"), succeeded.busyRequestIds)
        assertTrue(succeeded.hasPendingRequests)

        val failed =
            controller.patchJoinRequestReviewFailed(
                members = members,
                requests = started.requests,
                live = live,
                loading = false,
                changed = true,
                error = "kept",
                requestId = "request_1",
                busyRequestIds = started.busyRequestIds,
                failure = Exception("review failed"),
            )
        assertSame(members, failed.members)
        assertEquals(started.requests, failed.requests)
        assertSame(live, failed.live)
        assertFalse(failed.loading)
        assertTrue(failed.changed)
        assertEquals("kept", failed.error)
        assertEquals("操作失败，请稍后重试", failed.requestError)
        assertEquals(setOf("request_2"), failed.busyRequestIds)
        api.close()
    }

    @Test
    fun `patchLiveSnapshot re-inserts self when a stale snapshot drops the local participant for the joined room`() =
        gang.runTest {
            val api = _patchApi("Patch test should not call the API")
            val controller = RoomsController(api = api)

            val previousLive =
                LiveState.fromJson(
                    _liveJsonWithParticipants(listOf("me", "other")),
                )

            // Snapshot computed before we joined: it only lists "other".
            val patch =
                controller.patchLiveSnapshot(
                    rooms = listOf(_roomCard("room_1")),
                    selectedRoomId = "room_1",
                    data = _liveSnapshotData(listOf("other")),
                    joinedLiveRoomId = "room_1",
                    currentUserId = "me",
                    previousLive = previousLive,
                    localParticipantConnected = true,
                )

            assertNotNull(patch)
            val ids =
                patch!!.selectedLive!!.participants
                    .map { participant -> participant.user.id }
            assertTrue(ids.containsAll(listOf("me", "other")))
            assertEquals(2, patch.selectedLive.participantCount)
            assertEquals(2, patch.rooms.single().liveParticipantCount)
            assertTrue(
                patch.rooms.single().liveAvatarPreview.map { user -> user.id }
                    .containsAll(listOf("me", "other")),
            )
            api.close()
        }

    @Test
    fun `patchLiveSnapshot drops self after the local media session detached`() = gang.runTest {
        val api = _patchApi("Patch test should not call the API")
        val controller = RoomsController(api = api)
        val previousLive =
            LiveState.fromJson(
                _liveJsonWithParticipants(listOf("me", "other")),
            )

        val patch =
            controller.patchLiveSnapshot(
                rooms = listOf(_roomCard("room_1", liveParticipantCount = 2)),
                selectedRoomId = "room_1",
                data = _liveSnapshotData(listOf("other")),
                joinedLiveRoomId = "room_1",
                currentUserId = "me",
                previousLive = previousLive,
                localParticipantConnected = false,
            )

        assertEquals(
            listOf("other"),
            patch!!.selectedLive!!.participants.map { participant -> participant.user.id },
        )
        assertEquals(1, patch.rooms.single().liveParticipantCount)
        api.close()
    }

    @Test
    fun `patchLiveSnapshot does not re-insert self when not joined to the room`() = gang.runTest {
        val api = _patchApi("Patch test should not call the API")
        val controller = RoomsController(api = api)

        val previousLive =
            LiveState.fromJson(
                _liveJsonWithParticipants(listOf("me", "other")),
            )

        val patch =
            controller.patchLiveSnapshot(
                rooms = listOf(_roomCard("room_1")),
                selectedRoomId = "room_1",
                data = _liveSnapshotData(listOf("other")),
                joinedLiveRoomId = null,
                currentUserId = "me",
                previousLive = previousLive,
            )

        assertNotNull(patch)
        val ids =
            patch!!.selectedLive!!.participants
                .map { participant -> participant.user.id }
        assertEquals(listOf("other"), ids)
        api.close()
    }

    @Test
    fun `patchLiveSnapshot preserves participant room display names`() {
        val api = _patchApi("Patch test should not call the API")
        val controller = RoomsController(api = api)
        val previousJson = _liveJsonWithParticipants(listOf("me")).toMutableMap()
        @Suppress("UNCHECKED_CAST")
        val participants = (previousJson["participants"] as List<Any?>).toMutableList()
        @Suppress("UNCHECKED_CAST")
        val single = (participants.single() as Map<String, Any?>).toMutableMap()
        single["room_display_name"] = "Room Me"
        participants[0] = single
        previousJson["participants"] = participants
        val previousLive = LiveState.fromJson(previousJson)

        val patch =
            controller.patchLiveSnapshot(
                rooms = listOf(_roomCard("room_1")),
                selectedRoomId = "room_1",
                data = _liveSnapshotData(listOf("me")),
                previousLive = previousLive,
            )

        assertNotNull(patch)
        assertEquals(
            "Room Me",
            patch!!.selectedLive!!.participants.single().user.roomDisplayName,
        )
        api.close()
    }

    @Test
    fun `patchLiveSnapshot ignores an older selected-room live snapshot`() {
        val api = _patchApi("Patch test should not call the API")
        val controller = RoomsController(api = api)
        val previousLive =
            LiveState.fromJson(
                _liveJsonWithParticipants(listOf("me", "other")) +
                    mapOf("updated_at" to "2026-06-04T00:00:02Z"),
            )
        val staleData = _liveSnapshotData(listOf("other"))

        val patch =
            controller.patchLiveSnapshot(
                rooms = listOf(_roomCard("room_1", liveParticipantCount = 2)),
                selectedRoomId = "room_1",
                data = staleData,
                joinedLiveRoomId = "room_1",
                currentUserId = "me",
                previousLive = previousLive,
            )

        assertNotNull(patch)
        assertNull(patch!!.selectedLive)
        assertEquals(2, patch.rooms.single().liveParticipantCount)
        api.close()
    }

    @Test
    fun `patchRoomUpdated updates selected room counts and reload hint`() {
        val api = _patchApi("Patch test should not call the API")
        val controller = RoomsController(api = api)

        val patch =
            controller.patchRoomUpdated(
                rooms = listOf(_roomCard("room_1", unreadCount = 5, onlineMemberCount = 1)),
                incoming =
                    _roomCard(
                        "room_1",
                        name = "Renamed",
                        memberCount = 4,
                        onlineMemberCount = 2,
                        aiVoiceAnnouncementsEnabled = false,
                    ),
                selectedRoom = _roomDetail("room_1", onlineMemberCount = 1),
            )

        assertEquals(5, patch.rooms.single().unreadCount)
        assertEquals(2, patch.rooms.single().onlineMemberCount)
        assertEquals("Renamed", patch.selectedRoom?.name)
        assertEquals(4, patch.selectedRoom?.memberCount)
        assertEquals(2, patch.selectedRoom?.onlineMemberCount)
        assertFalse(patch.rooms.single().aiVoiceAnnouncementsEnabled)
        assertEquals(false, patch.selectedRoom?.aiVoiceAnnouncementsEnabled)
        assertTrue(patch.shouldReloadMembers)
        api.close()
    }

    @Test
    fun `patchRoomUpdated keeps selected room fresh messages unread`() {
        val api = _patchApi("Patch test should not call the API")
        val controller = RoomsController(api = api)

        val patch =
            controller.patchRoomUpdated(
                rooms = listOf(_roomCard("room_1", unreadCount = 0, lastMessageId = "msg_1")),
                incoming = _roomCard("room_1", lastMessageId = "msg_2"),
                selectedRoom = _roomDetail("room_1"),
            )

        assertEquals(1, patch.rooms.single().unreadCount)
        api.close()
    }

    @Test
    fun `patchRoomUpdated increments unread for fresh messages off-room`() {
        val api = _patchApi("Patch test should not call the API")
        val controller = RoomsController(api = api)

        val patch =
            controller.patchRoomUpdated(
                rooms = listOf(_roomCard("room_1", unreadCount = 5, lastMessageId = "msg_1")),
                incoming = _roomCard("room_1", lastMessageId = "msg_2"),
                selectedRoom = _roomDetail("room_2"),
            )

        assertEquals(6, patch.rooms.single().unreadCount)

        val sameLastMessage =
            controller.patchRoomUpdated(
                rooms = patch.rooms,
                incoming = _roomCard("room_1", lastMessageId = "msg_2"),
                selectedRoom = _roomDetail("room_2"),
            )

        assertEquals(6, sameLastMessage.rooms.single().unreadCount)
        api.close()
    }

    @Test
    fun `patchRoomUpdated prefers server unread count when present`() {
        val api = _patchApi("Patch test should not call the API")
        val controller = RoomsController(api = api)

        val patch =
            controller.patchRoomUpdated(
                rooms = listOf(_roomCard("room_1", unreadCount = 5, lastMessageId = "msg_1")),
                incoming =
                    _roomCard(
                        "room_1",
                        unreadCount = 7,
                        hasUnreadCount = true,
                        lastMessageId = "msg_2",
                    ),
                selectedRoom = _roomDetail("room_2"),
            )

        assertEquals(7, patch.rooms.single().unreadCount)
        api.close()
    }

    @Test
    fun `patchRoomUpdated reorders rooms by latest activity time`() {
        val api = _patchApi("Patch test should not call the API")
        val controller = RoomsController(api = api)

        val patch =
            controller.patchRoomUpdated(
                rooms =
                    listOf(
                        _roomCard(
                            "room_old",
                            unreadCount = 1,
                            lastMessageId = "msg_old",
                            lastMessageAt = DateTime.utc(2026, 6, 4, 10),
                        ),
                        _roomCard(
                            "room_target",
                            unreadCount = 1,
                            lastMessageId = "msg_previous",
                            lastMessageAt = DateTime.utc(2026, 6, 4, 9),
                        ),
                    ),
                incoming =
                    _roomCard(
                        "room_target",
                        lastMessageId = "msg_new",
                        lastMessageAt = DateTime.utc(2026, 6, 4, 11),
                    ),
                selectedRoom = _roomDetail("room_other"),
            )

        assertEquals(listOf("room_target", "room_old"), patch.rooms.map { room -> room.id })
        assertEquals(2, patch.rooms.first().unreadCount)
        api.close()
    }

    @Test
    fun `patchRoomUnreadCleared clears a single room count`() {
        val api = _patchApi("Patch test should not call the API")
        val controller = RoomsController(api = api)

        val patch =
            controller.patchRoomUnreadCleared(
                rooms =
                    listOf(
                        _roomCard("room_1", unreadCount = 5),
                        _roomCard("room_2", unreadCount = 2),
                    ),
                roomId = "room_1",
            )

        assertEquals(listOf("room_2", "room_1"), patch.rooms.map { room -> room.id })
        assertEquals(2, patch.rooms[0].unreadCount)
        assertEquals(0, patch.rooms[1].unreadCount)
        api.close()
    }

    @Test
    fun `room ordering puts pinned first and red unread before gray unread`() {
        val api = _patchApi("Ordering test should not call the API")
        val controller = RoomsController(api = api)

        val ordered =
            controller
                .patchRoomCardsRefreshed(
                    rooms =
                        listOf(
                            _roomCard("normal"),
                            _roomCard("gray", unreadCount = 2, notificationPolicy = "silent"),
                            _roomCard("red", unreadCount = 1),
                            _roomCard("pinned", isPinned = true),
                        ),
                )
                .rooms

        assertEquals(listOf("pinned", "red", "gray", "normal"), ordered.map { room -> room.id })
        api.close()
    }

    /** An API whose every request fails the test, like Dart's `fail(...)`. */
    private fun _patchApi(reason: String): GangApiClient {
        val httpClient: HttpClient =
            MockClient { request ->
                fail("$reason: ${request.url}")
                throw AssertionError()
            }
        return GangApiClient(
            baseUrl = "http://example.test/api/v1",
            accessTokenProvider = { "token" },
            httpClient = httpClient,
        )
    }

    private fun _roomCard(
        id: String,
        name: String? = null,
        unreadCount: Int = 0,
        memberCount: Int = 3,
        onlineMemberCount: Int = 0,
        lastMessageId: String? = null,
        notificationPolicy: String = "all",
        isPinned: Boolean = false,
        hasUnreadCount: Boolean = false,
        liveParticipantCount: Int = 0,
        lastMessageAt: DateTime? = null,
        updatedAt: DateTime? = null,
        aiVoiceAnnouncementsEnabled: Boolean = false,
    ): RoomCard {
        return RoomCard(
            id = id,
            name = name ?: id,
            avatarUrl = null,
            defaultAvatarKey = "room-1",
            notificationPolicy = notificationPolicy,
            isPinned = isPinned,
            memberCount = memberCount,
            onlineMemberCount = onlineMemberCount,
            liveParticipantCount = liveParticipantCount,
            liveAvatarPreview = emptyList(),
            lastMessage =
                if (lastMessageId == null) {
                    null
                } else {
                    LastMessagePreview(
                        id = lastMessageId,
                        type = "text",
                        senderDisplayName = "Alice",
                        bodyPreview = "hello",
                        createdAt = lastMessageAt ?: DateTime.utc(2026, 6, 4),
                    )
                },
            unreadCount = unreadCount,
            hasUnreadCount = hasUnreadCount,
            aiVoiceAnnouncementsEnabled = aiVoiceAnnouncementsEnabled,
            updatedAt = updatedAt ?: DateTime.utc(2026, 6, 4),
        )
    }

    private fun _roomInvite(id: String): RoomInvite {
        return RoomInvite(
            id = id,
            status = "pending",
            room = _publicRoom("room_$id"),
            inviter = _user("inviter_$id"),
            createdAt = DateTime.utc(2026, 6, 4),
        )
    }

    private fun _joinRequest(id: String): JoinRequest {
        return JoinRequest(
            id = id,
            status = "pending",
            user = _user("user_$id"),
            createdAt = DateTime.utc(2026, 6, 4),
        )
    }

    private fun _roomMember(userId: String): RoomMember {
        return RoomMember(
            user = _user(userId),
            role = "member",
            joinedAt = DateTime.utc(2026, 6, 4),
            isOnline = true,
        )
    }

    private fun _roomDetail(
        id: String,
        role: String = "member",
        onlineMemberCount: Int = 0,
    ): RoomDetail {
        return RoomDetail(
            id = id,
            name = id,
            avatarUrl = null,
            defaultAvatarKey = "room-1",
            memberCount = 3,
            onlineMemberCount = onlineMemberCount,
            myMembership =
                RoomMembership(
                    joinedAt = DateTime.utc(2026, 6, 4),
                    role = role,
                ),
            live = _liveState(participantCount = 0),
            createdAt = DateTime.utc(2026, 6, 4),
            updatedAt = DateTime.utc(2026, 6, 4),
        )
    }

    private fun _message(id: String, roomId: String): Message {
        return Message(
            id = id,
            roomId = roomId,
            sender = _user("sender"),
            clientMessageId = "client_$id",
            body = "hello",
            createdAt = DateTime.utc(2026, 6, 4),
        )
    }

    private fun _publicRoom(id: String): PublicRoom {
        return PublicRoom(
            id = id,
            rid = "R$id",
            name = id,
            avatarUrl = null,
            defaultAvatarKey = "room-1",
            visibility = "public",
            joinPolicy = "approval_required",
            memberCount = 3,
            liveParticipantCount = 0,
            joined = false,
            joinState = "none",
        )
    }

    private fun _user(id: String): UserSummary {
        return UserSummary(
            id = id,
            username = id,
            displayName = id,
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
        )
    }

    private fun _jsonResponse(body: JsonMap): HttpResponse {
        return HttpResponse(
            jsonEncode(body),
            200,
            headers = mapOf("content-type" to "application/json"),
        )
    }

    private fun _memberJson(userId: String): JsonMap {
        return mapOf(
            "user" to _userJson(userId),
            "role" to "member",
            "joined_at" to "2026-06-04T00:00:00Z",
            "is_online" to true,
        )
    }

    private fun _userJson(userId: String): JsonMap {
        return mapOf(
            "id" to userId,
            "username" to userId,
            "display_name" to userId,
            "avatar_url" to null,
            "default_avatar_key" to "blue-1",
        )
    }

    private fun _liveState(participantCount: Int): LiveState {
        return LiveState.fromJson(_liveJson(participantCount = participantCount))
    }

    private fun _liveJson(participantCount: Int): JsonMap {
        return mapOf(
            "room_id" to "room_1",
            "participant_count" to participantCount,
            "participants" to emptyList<Any?>(),
            "updated_at" to "2026-06-04T00:00:00Z",
        )
    }

    private fun _participantJson(userId: String): JsonMap {
        return mapOf(
            "live_session_id" to "session_$userId",
            "user" to
                mapOf(
                    "id" to userId,
                    "username" to userId,
                    "display_name" to userId,
                    "avatar_url" to null,
                    "default_avatar_key" to "blue-3",
                ),
            "joined_at" to "2026-06-04T00:00:00Z",
            "mic_muted" to false,
            "headphones_muted" to false,
            "voice_blocked" to false,
            "camera_on" to false,
            "screen_sharing" to false,
            "connection_state" to "connected",
        )
    }

    private fun _liveJsonWithParticipants(userIds: List<String>): JsonMap {
        return mapOf(
            "room_id" to "room_1",
            "participant_count" to userIds.size,
            "participants" to userIds.map(::_participantJson),
            "updated_at" to "2026-06-04T00:00:00Z",
        )
    }

    private fun _liveSnapshotData(userIds: List<String>): JsonMap {
        return mapOf(
            "room_id" to "room_1",
            "participant_count" to userIds.size,
            "live" to _liveJsonWithParticipants(userIds),
        )
    }
}
