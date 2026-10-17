package com.gangchat.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.LastMessagePreview
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.ui.latencySignalBadgeTag
import com.gangchat.client.ui.latencySignalBarTag
import com.gangchat.client.ui.setUiContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@OptIn(ExperimentalTestApi::class)
class HomeSidebarDraftTest {
    @get:Rule
    val compose = createComposeRule()

    private val currentUser =
        CurrentUser(
            id = "user_1",
            uid = "10000001",
            username = "logan",
            displayName = "Logan",
            bio = "",
            gender = "secret",
            email = null,
            emailPublic = false,
            phoneNumber = null,
            phoneNumberPublic = false,
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
            isSuperuser = false,
            createdAt = DateTime.utc(2026, 6, 4),
        )

    private fun sidebar(
        servers: List<RoomCard>,
        timestampNow: DateTime,
        width: Int,
        height: Int,
        roomDrafts: Map<String, String> = emptyMap(),
        requestRoundTrip: kotlin.time.Duration? = null,
        onServerSelected: (RoomCard) -> Unit = {},
    ) {
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize().testTag("page")) {
                Box(Modifier.size(width.dp, height.dp)) {
                    HomeSidebar(
                        width = width.dp,
                        currentUser = currentUser,
                        servers = servers,
                        timestampNow = timestampNow,
                        roomDrafts = roomDrafts,
                        selectedServerId = null,
                        joinedLiveRoomId = null,
                        realtimeReconnecting = false,
                        requestRoundTrip = requestRoundTrip,
                        searchQuery = "",
                        loading = false,
                        error = null,
                        settingsActive = false,
                        createRoomActive = false,
                        notificationsActive = false,
                        logoutActive = false,
                        hasPendingNotifications = false,
                        pendingNotificationCount = 0,
                        includeWindowChromeOffset = false,
                        onServerSelected = onServerSelected,
                        onCreateRoom = {},
                        onOpenNotifications = {},
                        onOpenSettings = {},
                        onLogout = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `room draft replaces subtitle without replacing last message time`() {
        val lastMessageAt = DateTime.now()
        val expectedTime = "${lastMessageAt.hour.toString().padStart(2, '0')}:${lastMessageAt.minute.toString().padStart(2, '0')}"
        val room =
            RoomCard(
                id = "room_1",
                name = "Draft room",
                avatarUrl = null,
                defaultAvatarKey = "room-1",
                memberCount = 2,
                liveParticipantCount = 0,
                liveAvatarPreview = emptyList(),
                lastMessage =
                    LastMessagePreview(id = "message_1", senderDisplayName = "Logan", bodyPreview = "latest message", createdAt = lastMessageAt),
                unreadCount = 0,
                updatedAt = lastMessageAt,
            )
        sidebar(listOf(room), lastMessageAt, width = 320, height = 180, roomDrafts = mapOf("room_1" to "draft\ncontent"))

        compose.onNodeWithTag(homeSidebarRoomDraftTag("room_1"), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("[草稿] draft content", substring = true), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("latest message", substring = true), useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasText(expectedTime), useUnmergedTree = true).assertExists()
    }

    /** The fill painted at the centre of latency bar [index]. */
    private fun latencyBarColor(index: Int): Color {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val bounds = compose.onNodeWithTag(latencySignalBarTag(index), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        return pixels[bounds.center.x.toInt(), bounds.center.y.toInt()]
    }

    @Test
    fun `user summary overlays the latest request latency signal`() {
        sidebar(emptyList(), DateTime.utc(2026, 7, 8, 9), width = 320, height = 120, requestRoundTrip = 96.milliseconds)

        compose.onNodeWithTag(latencySignalBadgeTag, useUnmergedTree = true).assertExists()
        assertEquals(Color(0xFF26B36F), latencyBarColor(1))

        compose.onNodeWithTag(latencySignalBadgeTag, useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("96 ms").assertExists()

        compose.onNodeWithTag("page").performTouchInput { click(Offset(5f, 5f)) }
        compose.waitForIdle()

        compose.onNodeWithText("96 ms").assertDoesNotExist()
    }

    @Test
    fun `latency card hover can be pinned until an outside tap`() {
        sidebar(emptyList(), DateTime.utc(2026, 7, 8, 9), width = 320, height = 120, requestRoundTrip = 96.milliseconds)
        compose.mainClock.autoAdvance = false
        fun pump(millis: Long) {
            compose.mainClock.advanceTimeBy(millis)
            repeat(3) { compose.mainClock.advanceTimeByFrame() }
        }

        val badge = compose.onNodeWithTag(latencySignalBadgeTag, useUnmergedTree = true)
        compose.onNodeWithTag("page").performMouseInput { moveTo(Offset.Zero) }
        badge.performMouseInput { moveTo(center) }
        pump(350)

        compose.onNodeWithText("96 ms").assertExists()

        badge.performMouseInput { click(center) }
        pump(500)
        compose.onNodeWithTag("page").performMouseInput { moveTo(Offset(5f, 5f)) }
        pump(150)

        compose.onNodeWithText("96 ms").assertExists()

        compose.onNodeWithTag("page").performMouseInput { click(Offset(5f, 5f)) }
        pump(500)

        compose.onNodeWithText("96 ms").assertDoesNotExist()
    }

    @Test
    fun `Android room scroll drag does not select a room on release`() {
        val rooms =
            List(8) { index ->
                RoomCard(
                    id = "room_$index",
                    name = "Room $index",
                    avatarUrl = null,
                    defaultAvatarKey = "room-${index % 6 + 1}",
                    memberCount = 2,
                    liveParticipantCount = 0,
                    liveAvatarPreview = emptyList(),
                    lastMessage = null,
                    unreadCount = 0,
                    updatedAt = DateTime.utc(2026, 7, 30),
                )
            }
        val selectedRoomIds = mutableListOf<String>()
        sidebar(rooms, DateTime.utc(2026, 7, 30), width = 320, height = 430, onServerSelected = { selectedRoomIds.add(it.id) })

        val firstRoom = compose.onNodeWithTag(homeSidebarRoomTag("room_0"))
        firstRoom.performTouchInput {
            down(center)
            moveBy(Offset(0f, -30.dp.toPx()))
            up()
        }
        compose.waitForIdle()

        assertTrue(selectedRoomIds.isEmpty())

        compose.onNodeWithTag(homeSidebarRoomTag("room_0")).performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals(listOf("room_0"), selectedRoomIds)
    }
}
