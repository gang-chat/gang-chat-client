package com.gangchat.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomMembership
import com.gangchat.client.protocol.RoomPersonalProfile
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.showTextContextMenu
import com.gangchat.client.ui.testMediaCacheController
import com.gangchat.client.ui.textLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val _currentUser =
    CurrentUser(
        id = "u_current",
        uid = "1000002",
        username = "current",
        displayName = "Current User",
        bio = "",
        gender = "secret",
        email = null,
        emailPublic = false,
        phoneNumber = null,
        phoneNumberPublic = false,
        avatarUrl = null,
        defaultAvatarKey = "green-2",
        isSuperuser = false,
        createdAt = null,
    )

private val _creator = UserSummary(id = "u_creator", username = "creator", displayName = "Room Creator", avatarUrl = null, defaultAvatarKey = "blue-3")

private val _joinedRoom =
    PublicRoom(
        id = "room_1",
        rid = "R10001",
        name = "Launch Room",
        description = "用于发布前沟通的语音频道。",
        avatarUrl = null,
        defaultAvatarKey = "room-2",
        visibility = "private",
        joinPolicy = "closed",
        memberCount = 12,
        onlineMemberCount = 3,
        liveParticipantCount = 1,
        joined = true,
        joinState = "joined",
        createdBy = _creator,
        personalProfile = RoomPersonalProfile(displayName = "Current In Room"),
        myMembership = RoomMembership(joinedAt = DateTime.utc(2026, 6, 1), role = "admin"),
    )

/** The tag on [RoomHoverCardForTest]'s anchor (Dart finds it as `find.byType(Avatar).first`). */
private const val _roomAvatarTag = "room-avatar"

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Flutter's default widget-test surface (800 x 600).
@Config(sdk = [35], qualifiers = "w800dp-h600dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
@OptIn(ExperimentalTestApi::class)
class RoomHoverCardTest {
    @get:Rule
    val compose = createComposeRule()

    private fun host(imagePreviewActions: ChatImagePreviewActions? = null, content: @Composable () -> Unit) {
        compose.setHomeContent { _ ->
            val centered: @Composable () -> Unit = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() } }
            if (imagePreviewActions == null) centered() else ChatImagePreviewActionsScope(actions = imagePreviewActions) { centered() }
        }
    }

    @Composable
    private fun RoomCard(
        room: PublicRoom = _joinedRoom,
        onResolveRoom: RoomProfileResolver? = null,
        onResolveUserProfile: UserProfileResolver? = null,
        onEnterRoom: ((PublicRoom) -> Unit)? = null,
    ) {
        RoomHoverCardForTest(
            room = room,
            currentUser = _currentUser,
            modifier = Modifier.testTag(_roomAvatarTag),
            onResolveRoom = onResolveRoom,
            onResolveUserProfile = onResolveUserProfile,
            onEnterRoom = onEnterRoom,
        )
    }

    private fun avatar() = compose.onNodeWithTag(_roomAvatarTag)

    private fun tapAvatar() {
        avatar().performClick()
        compose.waitForIdle()
    }

    private fun hoverAvatar() {
        compose.onNodeWithTag("page").performMouseInput { moveTo(Offset.Zero) }
        avatar().performMouseInput { moveTo(center) }
        compose.waitForIdle()
    }

    private fun inCard(matcher: SemanticsMatcher) = matcher and hasAnyAncestor(hasTestTag(hoverCardPanelTag))

    private fun roomProfileIcon() = compose.onNodeWithTag(roomProfileCardIconPreviewTag)

    /** The size of the avatar whose initials node is [initials] (the initials scale with the avatar). */
    private fun avatarSizeOf(initials: SemanticsNodeInteraction): Float = initials.textLayout().layoutInput.style.fontSize.value / 0.36f

    private fun ensureRoomProfileCardOpen() {
        if (compose.onAllNodes(hasText("RID: ${_joinedRoom.rid}")).fetchSemanticsNodes().isEmpty()) tapAvatar()
        compose.onNodeWithText("RID: ${_joinedRoom.rid}").assertExists()
    }

    @Test
    @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun `Android room card opens on tap but not on hold`() {
        host { RoomCard() }

        avatar().performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("RID: R10001").assertDoesNotExist()

        tapAvatar()
        compose.onNodeWithText("RID: R10001").assertExists()
    }

    @Test
    fun `hover over a room avatar reveals the room profile card`() {
        var openedRoom: PublicRoom? = null
        host { RoomCard(onEnterRoom = { room -> openedRoom = room }) }

        compose.onNodeWithText("RID: R10001").assertDoesNotExist()

        hoverAvatar()

        assertTrue(compose.onAllNodes(hasText("Launch Room")).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("12 名成员").assertExists()
        compose.onNodeWithText("用于发布前沟通的语音频道。").assertExists()
        compose.onNodeWithText("创建者").assertExists()
        compose.onNodeWithText("Room Creator").assertExists()
        compose.onNodeWithText("我的房间内信息").assertExists()
        compose.onNodeWithText("Current In Room").assertExists()
        compose.onNodeWithText("管理员").assertExists()
        compose.onNodeWithText("关闭").assertExists()
        compose.onNodeWithText("RID: R10001").assertExists()
        val enterTop = compose.onNodeWithText("进入房间").fetchSemanticsNode().boundsInRoot.top
        val ridTop = compose.onNodeWithText("RID: R10001").fetchSemanticsNode().boundsInRoot.top
        assertTrue(enterTop > ridTop)

        compose.onNodeWithText("进入房间").performClick()
        compose.waitForIdle()
        assertEquals("room_1", openedRoom?.id)
    }

    @Test
    @Config(qualifiers = "w190dp-h740dp-mdpi")
    fun `narrow room cards keep icons with complete multiline room and user names`() {
        val longRoomName = "这是一个需要完整换行显示的很长房间名称"
        val longCreatorName = "这是一个需要完整换行显示的创建者用户名"
        val creator = UserSummary(id = "long-creator", username = "long_creator", displayName = longCreatorName, avatarUrl = null, defaultAvatarKey = "blue-3")
        val room =
            PublicRoom(
                id = "long-room",
                rid = "LONG-ROOM",
                name = longRoomName,
                avatarUrl = null,
                defaultAvatarKey = "room-2",
                visibility = "private",
                joinPolicy = "closed",
                memberCount = 12,
                liveParticipantCount = 0,
                joined = false,
                joinState = "none",
                createdBy = creator,
            )
        host { RoomCard(room = room) }
        tapAvatar()

        val density = compose.density.density
        val name = compose.onNode(hasText(longRoomName) and SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
        val creatorName = compose.onNodeWithText(longCreatorName)
        name.assertExists()
        creatorName.assertExists()
        assertTrue(name.fetchSemanticsNode().boundsInRoot.height / density > 20f)
        assertTrue(creatorName.fetchSemanticsNode().boundsInRoot.height / density < 20f)
        assertTrue(name.fetchSemanticsNode().boundsInRoot.left > roomProfileIcon().fetchSemanticsNode().boundsInRoot.right)
        assertNotEquals(TextOverflow.Ellipsis, creatorName.textLayout().layoutInput.overflow)
        // Room icon, anchor and creator avatars all show "这是"; only the creator row is compact.
        val initials = compose.onAllNodes(hasText("这是"), useUnmergedTree = true)
        val compactCount = (0 until initials.fetchSemanticsNodes().size).count { index -> avatarSizeOf(initials[index]) in 15.9f..16.1f }
        assertEquals(1, compactCount)
    }

    @Test
    fun `room-card identity rows keep every final glyph on one line`() {
        val creatorName = "潘gang"
        val personalName = "testxxx"
        val creator = UserSummary(id = "mixed-creator", username = "mixed_creator", displayName = creatorName, avatarUrl = null, defaultAvatarKey = "blue-3")
        val room =
            PublicRoom(
                id = "mixed-room",
                rid = "MIXED",
                name = "666",
                avatarUrl = null,
                defaultAvatarKey = "room-2",
                visibility = "public",
                joinPolicy = "open",
                memberCount = 2,
                liveParticipantCount = 0,
                joined = true,
                joinState = "joined",
                createdBy = creator,
                personalProfile = RoomPersonalProfile(displayName = personalName),
                myMembership = RoomMembership(joinedAt = DateTime.utc(2026, 8, 5), role = "admin"),
            )
        host { RoomCard(room = room) }
        tapAvatar()

        val density = compose.density.density
        assertTrue(compose.onNodeWithText(creatorName).fetchSemanticsNode().boundsInRoot.height / density < 20f)
        assertTrue(compose.onNodeWithText(personalName).fetchSemanticsNode().boundsInRoot.height / density < 20f)
        // Neither identity row shrinks to the compact 16 dp avatar.
        for (label in listOf("潘G", "CU")) {
            val initials = compose.onAllNodes(inCard(hasText(label)), useUnmergedTree = true)
            for (index in 0 until initials.fetchSemanticsNodes().size) {
                assertEquals(20f, avatarSizeOf(initials[index]), 0.1f)
            }
        }
    }

    @Test
    fun `tap pins a room profile card until an outside tap`() {
        host { RoomCard() }

        tapAvatar()
        compose.onNodeWithText("RID: R10001").assertExists()

        compose.onNodeWithTag("page").performTouchInput { click(Offset(5f, 5f)) }
        compose.waitForIdle()
        compose.onNodeWithText("RID: R10001").assertDoesNotExist()
    }

    @Test
    fun `Android room card stays open while dragging a selection handle`() {
        host { RoomCard() }
        ensureRoomProfileCardOpen()

        // An active text selection makes an outside drag belong to it: the
        // card stays open through the drag.
        compose.showTextContextMenu(field = hasText(_joinedRoom.name) and SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
        compose.onNodeWithTag("page").performTouchInput {
            down(Offset(5f, 5f))
            compose.onNodeWithText("RID: R10001").assertExists()
            moveBy(Offset(18.dp.toPx(), 0f))
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithText("RID: R10001").assertExists()
    }

    @Test
    fun `preset room profile icon does not open image preview`() {
        host { RoomCard() }

        ensureRoomProfileCardOpen()
        roomProfileIcon().performClick()
        compose.pump(200)

        compose.onNodeWithTag(chatImagePreviewImageTag).assertDoesNotExist()
    }

    @Test
    fun `uploaded room profile icon opens the shared image preview`() {
        val roomWithIcon =
            PublicRoom(
                id = _joinedRoom.id,
                rid = _joinedRoom.rid,
                name = _joinedRoom.name,
                description = _joinedRoom.description,
                avatarUrl = "https://example.com/room.png",
                defaultAvatarKey = _joinedRoom.defaultAvatarKey,
                visibility = _joinedRoom.visibility,
                joinPolicy = _joinedRoom.joinPolicy,
                memberCount = _joinedRoom.memberCount,
                onlineMemberCount = _joinedRoom.onlineMemberCount,
                liveParticipantCount = _joinedRoom.liveParticipantCount,
                joined = _joinedRoom.joined,
                joinState = _joinedRoom.joinState,
                createdBy = _joinedRoom.createdBy,
                personalProfile = _joinedRoom.personalProfile,
                myMembership = _joinedRoom.myMembership,
            )
        val actions =
            ChatImagePreviewActions(onDownload = { _, _ -> }, onSaveAs = { _, _ -> }, onCopyToClipboard = { _ -> }, mediaCache = testMediaCacheController())
        host(imagePreviewActions = actions) { RoomCard(room = roomWithIcon) }

        ensureRoomProfileCardOpen()
        roomProfileIcon().performClick()
        compose.pump(200)

        compose.onNodeWithTag(chatImagePreviewImageTag).assertExists()
        compose.onTooltip("下载").assertExists()
        compose.onTooltip("另存为").assertExists()
        compose.onTooltip("复制到剪贴板").assertExists()
    }

    @Test
    fun `room profile creator avatar opens a user profile card`() {
        host { RoomCard() }

        hoverAvatar()
        compose.onNodeWithText("RID: R10001").assertExists()

        val creatorAvatar = compose.onNode(inCard(hasText("RO")), useUnmergedTree = true)
        creatorAvatar.assertExists()
        creatorAvatar.performMouseInput { moveTo(center) }
        compose.waitForIdle()

        compose.onNodeWithText("@creator").assertExists()
        compose.onNodeWithText("成员").assertDoesNotExist()
    }

    @Test
    fun `room profile creator card waits for the latest user profile`() {
        var resolveCalls = 0
        host {
            RoomCard(
                onResolveUserProfile = { user ->
                    resolveCalls += 1
                    user.copyWith(displayName = "Fresh Creator", bio = "Latest status", isOnline = true)
                },
            )
        }

        hoverAvatar()
        compose.onNode(inCard(hasText("RO")), useUnmergedTree = true).performMouseInput { moveTo(center) }
        compose.waitForIdle()

        assertEquals(1, resolveCalls)
        compose.onNodeWithText("Fresh Creator").assertExists()
        compose.onNodeWithText("Latest status").assertExists()
        compose.onNodeWithText("在线").assertExists()
        compose.onNodeWithText("成员").assertDoesNotExist()
    }

    @Test
    fun `refreshes the resolved room profile each time the card opens`() {
        var resolveCalls = 0
        host {
            RoomCard(
                onResolveRoom = { room ->
                    resolveCalls += 1
                    PublicRoom(
                        id = room.id,
                        rid = room.rid,
                        name = "Fresh Room $resolveCalls",
                        description = "Fresh description $resolveCalls",
                        avatarUrl = null,
                        defaultAvatarKey = room.defaultAvatarKey,
                        visibility = room.visibility,
                        joinPolicy = room.joinPolicy,
                        memberCount = 12 + resolveCalls,
                        onlineMemberCount = 3,
                        liveParticipantCount = 1,
                        joined = true,
                        joinState = "joined",
                        createdBy = room.createdBy,
                        personalProfile = room.personalProfile,
                        myMembership = room.myMembership,
                    )
                },
            )
        }

        hoverAvatar()
        assertEquals(1, resolveCalls)
        compose.onNodeWithText("Fresh Room 1").assertExists()
        compose.onNodeWithText("Fresh description 1").assertExists()
        compose.onNodeWithText("13 名成员").assertExists()

        compose.onNodeWithTag("page").performMouseInput { moveTo(Offset(5f, 5f)) }
        compose.pump(200)
        compose.onNodeWithText("Fresh Room 1").assertDoesNotExist()

        avatar().performMouseInput { moveTo(center) }
        compose.waitForIdle()

        assertEquals(2, resolveCalls)
        compose.onNodeWithText("Fresh Room 1").assertDoesNotExist()
        compose.onNodeWithText("Fresh Room 2").assertExists()
        compose.onNodeWithText("Fresh description 2").assertExists()
        compose.onNodeWithText("14 名成员").assertExists()
    }

    @Test
    fun `deleted room card skips resolving and hides all room actions`() {
        var resolveCalls = 0
        var enterCalls = 0
        val deletedRoom = _joinedRoom.copyWith(isDeleted = true)
        host {
            RoomCard(
                room = deletedRoom,
                onResolveRoom = { room ->
                    resolveCalls += 1
                    room
                },
                onEnterRoom = { enterCalls += 1 },
            )
        }

        hoverAvatar()

        assertEquals(0, resolveCalls)
        assertEquals(0, enterCalls)
        compose.onNodeWithText("房间已删除").assertExists()
        compose.onNodeWithTag(deletedRoomProfileCardTag).assertExists()
        val inTombstone = hasAnyAncestor(hasTestTag(deletedRoomProfileCardTag))
        // No status badges ("已加入" / "私有" / "关闭") and no icons.
        for (badge in listOf("已加入", "私有", "关闭")) {
            compose.onAllNodes(hasText(badge) and inTombstone, useUnmergedTree = true).assertCountEqualsCompat(0)
        }
        compose
            .onAllNodes(SemanticsMatcher.keyIsDefined(com.gangchat.client.widgets.IconSemanticsKey) and inTombstone, useUnmergedTree = true)
            .assertCountEqualsCompat(0)
        compose.onNodeWithText("Launch Room").assertDoesNotExist()
        compose.onNodeWithText("12 名成员").assertDoesNotExist()
        compose.onNodeWithText("创建者").assertDoesNotExist()
        compose.onNodeWithText("我的房间内信息").assertDoesNotExist()
        compose.onNodeWithText("进入房间").assertDoesNotExist()
        compose.onNodeWithText("RID: R10001").assertDoesNotExist()
    }
}
