package com.gangchat.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.UserCommonRoom
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.genderMark
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.showTextContextMenu
import com.gangchat.client.ui.testMediaCacheController
import com.gangchat.client.ui.textColor
import com.gangchat.client.ui.textLayout
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.EdgeInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val _user =
    UserSummary(
        id = "u1",
        username = "logan",
        displayName = "加一",
        avatarUrl = null,
        defaultAvatarKey = "blue-3",
        uid = "10001",
        bio = "随便写点什么",
        gender = "male",
        roomRole = "admin",
        isOnline = true,
        commonRooms = listOf(UserCommonRoom(id = "r1", rid = "R1", name = "摸鱼大队"), UserCommonRoom(id = "r2", rid = "R2", name = "技术交流")),
    )

private val _currentUser =
    CurrentUser(
        id = "u1",
        uid = "10001",
        username = "logan",
        displayName = "加一",
        bio = "",
        gender = "secret",
        email = null,
        emailPublic = false,
        phoneNumber = null,
        phoneNumberPublic = false,
        avatarUrl = null,
        defaultAvatarKey = "blue-3",
        isSuperuser = false,
        createdAt = null,
    )

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Flutter's default widget-test surface (800 x 600), where the card opens
// beside the centred avatar instead of over it.
@Config(sdk = [35], qualifiers = "w800dp-h600dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
@OptIn(ExperimentalTestApi::class)
class AvatarHoverCardTest {
    @get:Rule
    val compose = createComposeRule()

    private fun host(imagePreviewActions: ChatImagePreviewActions? = null, content: @Composable () -> Unit) {
        compose.setHomeContent { _ ->
            val centered: @Composable () -> Unit = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() } }
            if (imagePreviewActions == null) centered() else ChatImagePreviewActionsScope(actions = imagePreviewActions) { centered() }
        }
    }

    private fun avatar() = compose.onNodeWithTag(messageAvatarTag)

    private fun tapAvatar() {
        avatar().performClick()
        compose.waitForIdle()
    }

    private fun tapOutside() {
        compose.onNodeWithTag("page").performTouchInput { click(Offset(5f, 5f)) }
        compose.waitForIdle()
    }

    private fun profileCardAvatar() = compose.onNodeWithTag(userProfileCardAvatarPreviewTag)

    private fun hoverAvatar() {
        compose.onNodeWithTag("page").performMouseInput { moveTo(Offset.Zero) }
        avatar().performMouseInput { moveTo(center) }
        compose.waitForIdle()
    }

    private fun imagePreviewActions() =
        ChatImagePreviewActions(onDownload = { _, _ -> }, onSaveAs = { _, _ -> }, onCopyToClipboard = { _ -> }, mediaCache = testMediaCacheController())

    @Test
    fun `hover over a message avatar reveals the profile card`() {
        host { AvatarHoverCardForTest(user = _user) }

        // Card stays hidden until the pointer enters the avatar.
        compose.onNodeWithText("@logan").assertDoesNotExist()

        hoverAvatar()

        compose.onNodeWithText("@logan").assertExists()
        assertTrue(compose.onAllNodesWithText("加一").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("♂").assertExists()
        compose.onNodeWithText("在线").assertExists()
        compose.onNodeWithText("管理员").assertExists()
        compose.onNodeWithText("男").assertDoesNotExist()
        assertEquals(genderMark("male")?.color, compose.onNodeWithText("♂").textColor())
        assertEquals(FontWeight.W900, compose.onNodeWithText("♂").textLayout().layoutInput.style.fontWeight)
        compose.onNodeWithText("随便写点什么").assertExists()
        compose.onNodeWithText("2 个共同房间").assertExists()
        compose.onNodeWithText("摸鱼大队").assertExists()
        compose.onNodeWithText("技术交流").assertExists()
        compose.onNodeWithText("UID: 10001").assertExists()

        // Card disappears when the pointer leaves the avatar.
        compose.onNodeWithTag("page").performMouseInput { moveTo(Offset(5f, 5f)) }
        compose.pump(200)
        compose.onNodeWithText("@logan").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w190dp-h740dp-mdpi")
    fun `narrow user cards wrap identity and common-room names without ellipses`() {
        val longDisplayName = "这是一个需要完整换行显示的很长用户名"
        val longCommonRoomName = "这是一个同样需要完整换行显示的共同房间名称"
        val user =
            UserSummary(
                id = "long-user",
                username = "long_user_name_that_must_wrap",
                displayName = longDisplayName,
                avatarUrl = null,
                defaultAvatarKey = "blue-3",
                commonRooms = listOf(UserCommonRoom(id = "long-room", rid = "LONG", name = longCommonRoomName), UserCommonRoom(id = "short-room", rid = "SHORT", name = "LOL")),
            )
        host { AvatarHoverCardForTest(user = user) }
        tapAvatar()

        val density = compose.density.density
        val name = compose.onNodeWithText(longDisplayName).fetchSemanticsNode().boundsInRoot
        val avatarBounds = profileCardAvatar().fetchSemanticsNode().boundsInRoot
        val commonRoomName = compose.onNodeWithText(longCommonRoomName).fetchSemanticsNode().boundsInRoot
        assertTrue(name.height / density > 20f)
        assertTrue(commonRoomName.height / density < 20f)
        assertTrue(name.left > avatarBounds.right)
        // The long name shrinks its row to the compact 16 dp avatar; the short
        // one keeps 20 dp, and both avatars start at the same x.
        // The card's own avatar shows the same initials; the room row is the second.
        val longInitials = compose.onAllNodes(hasText("这是") and hasAnyAncestor(hasTestTag(hoverCardPanelTag)), useUnmergedTree = true)[1]
        val shortInitials = compose.onNode(hasText("LO"), useUnmergedTree = true)
        val longSize = longInitials.textLayout().layoutInput.style.fontSize.value / 0.36f
        val shortSize = shortInitials.textLayout().layoutInput.style.fontSize.value / 0.36f
        assertEquals(16f, longSize, 0.1f)
        assertEquals(20f, shortSize, 0.1f)
        val longLeft = longInitials.fetchSemanticsNode().boundsInRoot.center.x / density - longSize / 2
        val shortLeft = shortInitials.fetchSemanticsNode().boundsInRoot.center.x / density - shortSize / 2
        assertEquals(longLeft, shortLeft, 1f)
    }

    @Test
    fun `hover card shows voice instead of online presence tag`() {
        host { AvatarHoverCardForTest(user = _user, inLive = true) }
        tapAvatar()

        compose.onNodeWithText("在线").assertDoesNotExist()
        compose.onNodeWithText("离线").assertDoesNotExist()
        compose.onNodeWithText("语音").assertExists()
        // Active, voice-coloured border painted over the avatar.
        assertEquals(UiColors.presenceVoice, compose.pixelIn(profileCardAvatar(), 24f, 1f))
    }

    @Test
    fun `hover card treats current user as online without summary flag`() {
        val lightweightSelf = UserSummary(id = "u1", username = "logan", displayName = "加一", avatarUrl = null, defaultAvatarKey = "blue-3")
        host { AvatarHoverCardForTest(user = lightweightSelf, currentUser = _currentUser) }
        tapAvatar()

        compose.onNodeWithText("在线").assertExists()
        assertEquals(UiColors.accent, compose.pixelIn(profileCardAvatar(), 24f, 1f))
    }

    @Test
    fun `hover card hides room role outside room context`() {
        host { AvatarHoverCardForTest(user = _user, showRoomRole = false) }
        tapAvatar()

        compose.onNodeWithText("@logan").assertExists()
        compose.onNodeWithText("管理员").assertDoesNotExist()
    }

    @Test
    fun `Android user card stays open while dragging a selection handle`() {
        host { AvatarHoverCardForTest(user = _user) }
        tapAvatar()
        compose.onNodeWithText("@logan").assertExists()

        // An active text selection (the context menu or handles) makes an
        // outside drag belong to it: the card stays open.
        compose.showTextContextMenu(field = hasText("@logan") and SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
        compose.onNodeWithTag("page").performTouchInput {
            down(Offset(5f, 5f))
            moveBy(Offset(30.dp.toPx(), 0f))
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithText("@logan").assertExists()

        tapOutside()
        tapOutside()
        compose.onNodeWithText("@logan").assertDoesNotExist()
    }

    @Test
    fun `preset profile card avatar does not open image preview`() {
        host { AvatarHoverCardForTest(user = _user) }
        tapAvatar()
        profileCardAvatar().performClick()
        compose.pump(200)

        compose.onNodeWithTag(chatImagePreviewImageTag).assertDoesNotExist()
    }

    @Test
    fun `uploaded profile card avatar opens the shared image preview`() {
        host(imagePreviewActions = imagePreviewActions()) {
            AvatarHoverCardForTest(user = _user.copyWith(avatarUrl = "https://example.com/avatar.png"))
        }
        tapAvatar()
        profileCardAvatar().performClick()
        compose.pump(200)

        compose.onNodeWithTag(chatImagePreviewImageTag).assertExists()
        compose.onTooltip("下载").assertExists()
        compose.onTooltip("另存为").assertExists()
        compose.onTooltip("复制到剪贴板").assertExists()
    }

    @Test
    fun `common room avatar opens a room profile card`() {
        host { AvatarHoverCardForTest(user = _user) }
        tapAvatar()
        compose.onNodeWithText("@logan").assertExists()

        compose.onNodeWithText("摸鱼", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("RID: R1").assertExists()
        // The parent user card stays open under the nested room card.
        compose.onNodeWithText("@logan").assertExists()
    }

    @Test
    fun `tapping the avatar again closes the profile card`() {
        host { AvatarHoverCardForTest(user = _user) }

        tapAvatar()
        compose.onNodeWithText("@logan").assertExists()

        tapAvatar()
        compose.onNodeWithText("@logan").assertDoesNotExist()
    }

    @Test
    fun `nested room enter button invokes the common room callback`() {
        var openedRoom: PublicRoom? = null
        host { AvatarHoverCardForTest(user = _user, onEnterCommonRoom = { openedRoom = it }) }
        tapAvatar()
        compose.onNodeWithText("摸鱼", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("进入房间").performClick()
        compose.waitForIdle()

        assertEquals("r1", openedRoom?.id)
    }

    @Test
    fun `opening another common room closes the previous room card`() {
        val siblingUser =
            UserSummary(
                id = "u1",
                username = "logan",
                displayName = "Logan",
                avatarUrl = null,
                defaultAvatarKey = "blue-3",
                commonRooms = listOf(UserCommonRoom(id = "r1", rid = "R1", name = "Room One"), UserCommonRoom(id = "r2", rid = "R2", name = "Room Two")),
            )
        host { AvatarHoverCardForTest(user = siblingUser) }
        tapAvatar()
        compose.onNodeWithText("@logan").assertExists()

        val roomAvatars = compose.onAllNodes(hasText("RO") and hasAnyAncestor(hasTestTag(hoverCardPanelTag)), useUnmergedTree = true)
        roomAvatars.assertCountEqualsCompat(2)

        roomAvatars[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("RID: R1").assertExists()

        compose.onAllNodes(hasText("RO") and hasAnyAncestor(hasTestTag(hoverCardPanelTag)), useUnmergedTree = true)[1].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("@logan").assertExists()
        compose.onNodeWithText("RID: R1").assertDoesNotExist()
        compose.onNodeWithText("RID: R2").assertExists()
    }

    @Test
    fun `common rooms overflow scrolls instead of showing summary row`() {
        val manyRoomsUser =
            UserSummary(
                id = "u1",
                username = "logan",
                displayName = "Logan",
                avatarUrl = null,
                defaultAvatarKey = "blue-3",
                commonRooms =
                    listOf("One", "Two", "Three", "Four", "Five", "Six").mapIndexed { index, name ->
                        UserCommonRoom(id = "r${index + 1}", rid = "R${index + 1}", name = "Room $name")
                    },
            )
        host { AvatarHoverCardForTest(user = manyRoomsUser) }
        tapAvatar()

        compose.onNodeWithText("6 个共同房间").assertExists()
        compose.onNodeWithText("等 6 个房间").assertDoesNotExist()
        val scroller = compose.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag(hoverCardPanelTag)))
        scroller.assertExists()

        scroller.performScrollToNode(hasText("Room Six"))
        compose.waitForIdle()

        compose.onNodeWithText("Room Six").assertIsDisplayed()
    }

    @Test
    fun `clicking an earlier card rolls back later nested cards`() {
        val creator = UserSummary(id = "creator", username = "creator", displayName = "Creator", avatarUrl = null, defaultAvatarKey = "green-2")
        val rollbackUser =
            UserSummary(
                id = "u1",
                username = "logan",
                displayName = "Logan",
                avatarUrl = null,
                defaultAvatarKey = "blue-3",
                commonRooms = listOf(UserCommonRoom(id = "r1", rid = "R1", name = "Room One")),
            )
        host {
            AvatarHoverCardForTest(
                user = rollbackUser,
                onResolveRoomProfile = { room ->
                    PublicRoom(
                        id = room.id,
                        rid = room.rid,
                        name = room.name,
                        avatarUrl = null,
                        defaultAvatarKey = "room-1",
                        visibility = "private",
                        joinPolicy = "closed",
                        memberCount = 2,
                        onlineMemberCount = 1,
                        liveParticipantCount = 0,
                        joined = true,
                        joinState = "joined",
                        createdBy = creator,
                    )
                },
                onResolveProfile = { user -> if (user.id == creator.id) user.copyWith(bio = "Creator bio", isOnline = true) else user },
            )
        }
        tapAvatar()
        compose.onNodeWithText("@logan").assertExists()

        compose.onNode(hasText("RO") and hasAnyAncestor(hasTestTag(hoverCardPanelTag)), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("RID: R1").assertExists()

        compose.onNode(hasText("CR") and hasAnyAncestor(hasTestTag(hoverCardPanelTag)), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("@creator").assertExists()
        compose.onNodeWithText("Creator bio").assertExists()

        compose.onNodeWithTag(roomProfileCardIconPreviewTag).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("@creator").assertDoesNotExist()
        compose.onNodeWithText("Creator bio").assertDoesNotExist()
        compose.onNodeWithText("RID: R1").assertExists()
        compose.onNodeWithText("@logan").assertExists()

        compose.onNodeWithTag(userProfileCardAvatarPreviewTag).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("RID: R1").assertDoesNotExist()
        compose.onNodeWithText("@logan").assertExists()

        tapOutside()
        compose.onNodeWithText("@logan").assertDoesNotExist()
    }

    @Test
    fun `common room avatar waits for the latest room profile`() {
        var resolveCalls = 0
        host {
            AvatarHoverCardForTest(
                user = _user,
                onResolveRoomProfile = { room ->
                    resolveCalls += 1
                    PublicRoom(
                        id = room.id,
                        rid = room.rid,
                        name = "Fresh Room",
                        avatarUrl = null,
                        defaultAvatarKey = "room-1",
                        visibility = "private",
                        joinPolicy = "closed",
                        description = "Latest room summary",
                        memberCount = 9,
                        onlineMemberCount = 3,
                        liveParticipantCount = 0,
                        joined = true,
                        joinState = "joined",
                    )
                },
            )
        }
        tapAvatar()
        compose.onNodeWithText("摸鱼", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        assertEquals(1, resolveCalls)
        compose.onNodeWithText("Fresh Room").assertExists()
        compose.onNodeWithText("Latest room summary").assertExists()
        compose.onNodeWithText("9 名成员").assertExists()
    }

    @Test
    fun `tap opens the profile card until an outside tap`() {
        host { AvatarHoverCardForTest(user = _user) }

        tapAvatar()
        compose.onNodeWithText("@logan").assertExists()

        // A mouse passing over and off the pinned avatar does not close it.
        hoverAvatar()
        compose.onNodeWithTag("page").performMouseInput { moveTo(Offset(5f, 5f)) }
        compose.pump(200)
        compose.onNodeWithText("@logan").assertExists()

        tapOutside()
        compose.onNodeWithText("@logan").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-mdpi")
    fun `Android hover cards stay inside the system navigation safe area`() {
        compose.setHomeContent(viewPadding = EdgeInsets.fromLTRB(12.dp, 24.dp, 16.dp, 48.dp)) { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { AvatarHoverCardForTest(user = _user) }
        }
        tapAvatar()

        val density = compose.density.density
        val card = compose.onNodeWithTag(hoverCardPanelTag).fetchSemanticsNode().boundsInRoot
        assertTrue(card.left / density >= 12f - 0.5f)
        assertTrue(card.top / density >= 24f - 0.5f)
        assertTrue(card.right / density <= 344f + 0.5f)
        assertTrue(card.bottom / density <= 592f + 0.5f)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-mdpi")
    fun `Android positioned user cards stay inside every system safe edge`() {
        var launchContext: BuildContext? = null
        compose.setHomeContent(viewPadding = EdgeInsets.fromLTRB(12.dp, 24.dp, 16.dp, 48.dp)) { context ->
            launchContext = context
            Box(Modifier.fillMaxSize())
        }

        unawaited {
            showUserProfileCardAtPosition(launchContext!!, position = DpOffset(350.dp, 620.dp), user = _user, resolveOnOpen = false)
        }
        compose.waitForIdle()

        val density = compose.density.density
        val card = compose.onNodeWithTag(hoverCardPanelTag).fetchSemanticsNode().boundsInRoot
        assertTrue(card.left / density >= 20f - 0.5f)
        assertTrue(card.top / density >= 32f - 0.5f)
        assertTrue(card.right / density <= 336f + 0.5f)
        assertTrue(card.bottom / density <= 584f + 0.5f)
    }

    @Test
    fun `resolves gender and common rooms before opening`() {
        // The message summary lacks gender/common rooms; the resolver supplies them.
        val lightweight = UserSummary(id = "u1", username = "logan", displayName = "加一", avatarUrl = null, defaultAvatarKey = "blue-3")
        var calls = 0
        host {
            AvatarHoverCardForTest(
                user = lightweight,
                onResolveProfile = {
                    calls++
                    _user
                },
            )
        }
        tapAvatar()

        assertEquals(1, calls)
        compose.onNodeWithText("♂").assertExists()
        compose.onNodeWithText("男").assertDoesNotExist()
        compose.onNodeWithText("2 个共同房间").assertExists()
        compose.onNodeWithText("摸鱼大队").assertExists()
    }

    @Test
    fun `refreshes the resolved profile each time the card opens`() {
        val lightweight = UserSummary(id = "u1", username = "logan", displayName = "Initial User", avatarUrl = null, defaultAvatarKey = "blue-3")
        var calls = 0
        host {
            AvatarHoverCardForTest(
                user = lightweight,
                onResolveProfile = { sender ->
                    calls += 1
                    sender.copyWith(displayName = "Fresh User $calls", bio = "Fresh bio $calls", isOnline = calls % 2 == 0)
                },
            )
        }

        tapAvatar()
        assertEquals(1, calls)
        compose.onNodeWithText("Fresh User 1").assertExists()
        compose.onNodeWithText("Fresh bio 1").assertExists()

        tapAvatar()
        compose.onNodeWithText("Fresh User 1").assertDoesNotExist()

        tapAvatar()
        assertEquals(2, calls)
        compose.onNodeWithText("Fresh User 1").assertDoesNotExist()
        compose.onNodeWithText("Fresh User 2").assertExists()
        compose.onNodeWithText("Fresh bio 2").assertExists()
    }

    @Test
    fun `waits for resolved profile before showing the card`() {
        val lightweight =
            UserSummary(id = "u1", username = "logan", displayName = "加一", avatarUrl = null, defaultAvatarKey = "blue-3", isOnline = false)
        val completer = Completer<UserSummary>()
        var calls = 0
        host {
            AvatarHoverCardForTest(
                user = lightweight,
                onResolveProfile = {
                    calls++
                    completer.future.await()
                },
            )
        }
        tapAvatar()

        assertEquals(1, calls)
        compose.onNodeWithText("@logan").assertDoesNotExist()
        compose.onNodeWithText("离线").assertDoesNotExist()

        compose.runOnIdle { completer.complete(_user) }
        compose.waitForIdle()

        compose.onNodeWithText("@logan").assertExists()
        compose.onNodeWithText("在线").assertExists()
        compose.onNodeWithText("离线").assertDoesNotExist()
    }

    @Test
    fun `deleted user card is a non-interactive tombstone`() {
        val deleted =
            UserSummary(
                id = "user_deleted",
                uid = "1000999",
                username = "historical_name",
                displayName = "Historical User",
                bio = "This must not be shown.",
                gender = "male",
                avatarUrl = "/historical-avatar.png",
                defaultAvatarKey = "blue-3",
                roomDisplayName = "Historical Room Name",
                roomRole = "admin",
                isOnline = true,
                isDeleted = true,
                commonRooms = listOf(UserCommonRoom(id = "room_old", rid = "R100", name = "Historical Room", avatarUrl = null, defaultAvatarKey = "room-1")),
            )
        var resolveCalls = 0
        var actionBuilderCalls = 0
        host {
            AvatarHoverCardForTest(
                user = deleted,
                onResolveProfile = { user ->
                    resolveCalls += 1
                    user
                },
                profileActionBuilder = {
                    actionBuilderCalls += 1
                    UserProfileAction(label = "管理成员", icon = Icons.Outlined.AdminPanelSettings, onPressed = {})
                },
            )
        }
        tapAvatar()

        assertEquals(0, resolveCalls)
        assertEquals(0, actionBuilderCalls)
        compose.onNodeWithText("用户已注销").assertExists()
        compose.onNodeWithText("@已注销").assertDoesNotExist()
        compose.onNodeWithTag(deletedUserProfileCardTag).assertExists()
        compose
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(com.gangchat.client.widgets.IconSemanticsKey) and hasAnyAncestor(hasTestTag(deletedUserProfileCardTag)),
                useUnmergedTree = true,
            )
            .assertCountEqualsCompat(0)
        compose.onNodeWithText("This must not be shown.").assertDoesNotExist()
        compose.onNodeWithText("Historical Room").assertDoesNotExist()
        compose.onNodeWithText("UID: 1000999").assertDoesNotExist()
        compose.onNodeWithText("管理成员").assertDoesNotExist()
    }

    @Test
    fun `suspended user keeps their profile with a red status pill`() {
        val suspended =
            UserSummary(
                id = "user_suspended",
                uid = "1000888",
                username = "suspended_user",
                displayName = "被封禁用户",
                bio = "资料仍然保留",
                avatarUrl = null,
                defaultAvatarKey = "green-2",
                isOnline = true,
                isSuspended = true,
            )
        host { AvatarHoverCardForTest(user = suspended, inLive = true) }
        tapAvatar()

        compose.onNodeWithText("被封禁用户").assertExists()
        compose.onNodeWithText("@suspended_user").assertExists()
        compose.onNodeWithText("资料仍然保留").assertExists()
        compose.onNodeWithText("封禁中").assertExists()
        compose.onNodeWithText("在线").assertDoesNotExist()
        compose.onNodeWithText("语音").assertDoesNotExist()
        assertEquals(UiColors.danger, compose.onNodeWithText("封禁中").textColor())
    }
}

/** Matches a node carrying [message] as its tooltip (Flutter's `find.byTooltip`). */
fun hasTooltipText(message: String) = com.gangchat.client.ui.hasTooltip(message)

fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEqualsCompat(count: Int) {
    assertEquals(count, fetchSemanticsNodes().size)
}
