package com.gangchat.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.gangchat.client.app.roomCreatedAtLabel
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.ui.Input
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.floatingNoticeVisibleDuration
import com.gangchat.client.ui.hasTooltip
import com.gangchat.client.ui.onAllTooltips
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.textLayout
import com.gangchat.client.widgets.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The cases of test/widget/shell/gang_app_shell_test_parts/room_management_widget_tests.dart
 * that can be hosted on the room settings and members panels directly (the
 * Dart tests reach them through the home shell). The panels are embedded
 * like the shell embeds them; on wide windows a 248 dp spacer stands in for
 * the sidebar, so the panel gets the shell's content width.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Flutter's default widget-test surface (800 x 600).
@Config(sdk = [35], qualifiers = "w800dp-h600dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class RoomManagementWidgetTest {
    @get:Rule
    val compose = createComposeRule()

    private enum class Panel { members, settings }

    private fun host(
        api: RoomManagementTestApi,
        currentUser: CurrentUser = roomManagementTestCurrentUser(),
        panel: MutableState<Panel> = mutableStateOf(Panel.members),
        room: MutableState<RoomDetail>? = null,
        initialSearchQuery: String = "",
    ) {
        val initialRoom = api.room()
        val roomState = room ?: mutableStateOf(initialRoom)
        compose.setHomeContent { context ->
            val content: @Composable () -> Unit = {
                when (panel.value) {
                    Panel.members ->
                        RoomMembersDialog(
                            controller = api.controller,
                            room = roomState.value,
                            currentUser = currentUser,
                            initialLive = roomState.value.live,
                            initialSearchQuery = initialSearchQuery,
                            embedded = true,
                        )
                    Panel.settings ->
                        RoomSettingsDialog(
                            controller = api.controller,
                            room = roomState.value,
                            currentUser = currentUser,
                            isInLive = false,
                            onLeaveLive = {},
                            onRoomUpdated = {},
                            embedded = true,
                            // The shell's message history and playlists panes.
                            messageHistoryBuilder = { _ -> Text("消息记录面板") },
                            musicPlaylistsBuilder = { _ -> Text("房间歌单面板") },
                        )
                }
            }
            if (HomeAdaptiveLayout.usesCompactLayout(context)) {
                Box(Modifier.fillMaxSize()) { content() }
            } else {
                Row(Modifier.fillMaxSize()) {
                    Spacer(Modifier.width(sidebarWidth))
                    Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                }
            }
        }
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        compose.pump(16)
    }

    private fun count(matcher: SemanticsMatcher) = compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun tag(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun clickText(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun clickButton(label: String) {
        compose.onNode(hasText(label) and hasClickAction()).performClick()
        compose.waitForIdle()
    }

    private fun clickTooltip(message: String) {
        compose.onTooltip(message).performClick()
        compose.waitForIdle()
    }

    private fun bounds(node: SemanticsNodeInteraction) = node.fetchSemanticsNode().boundsInRoot

    /** Unclipped bounds (a scroll viewport clips [bounds] of partly hidden nodes). */
    private fun fullBounds(node: SemanticsNodeInteraction) =
        node.fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun dp(px: Float) = px / compose.density.density

    private fun isSelected(node: SemanticsNodeInteraction): Boolean =
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true

    /** The pressable surface of the button carrying [tooltip]. */
    private fun buttonWithTooltip(tooltip: String) = compose.onNode(hasParent(hasTooltip(tooltip)), useUnmergedTree = true)

    private fun searchField() = compose.onNode(hasSetTextAction())

    private fun fieldIn(fieldTag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(fieldTag)))

    /** The (possibly read-only) text field carrying or inside [fieldTag]. */
    private fun editableIn(fieldTag: String) =
        compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText) and
                (hasTestTag(fieldTag) or hasAnyAncestor(hasTestTag(fieldTag))),
            useUnmergedTree = true,
        )

    private fun editableText(node: SemanticsNodeInteraction): String =
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text ?: ""

    @Test
    fun `authenticated home shell opens room management with real APIs`() {
        val api = RoomManagementTestApi()
        val panel = mutableStateOf(Panel.members)
        host(api, panel = panel)

        assertTrue(count(hasText("成员")) >= 1)
        compose.onNodeWithText("房间成员").assertExists()
        compose.onNodeWithText("新成员").assertExists()
        compose.onNodeWithText("黑名单").assertExists()
        tag(newMembersTabBadgeTag).assertExists()
        assertEquals(0, count(hasText("邀请成员")))
        compose.onNodeWithText("语音 1").assertExists()
        compose.onNodeWithText("在线 2").assertExists()
        compose.onNodeWithText("管理员 1").assertExists()
        compose.onNodeWithText("创建者 1").assertExists()
        assertTrue(bounds(tag(memberPresenceFilterTag)).bottom < bounds(tag(memberRoleFilterTag)).top)
        assertTrue(dp(bounds(tag(roomMembersListTag)).height) > 220f)
        assertEquals(0, count(hasText("@riley")))
        assertEquals(0, count(hasText("10000001")))
        assertTrue(count(hasText("Kai")) >= 1)
        assertTrue(count(hasText("Morgan")) >= 1)
        assertEquals(1, count(hasText("Morgan") and hasAnyAncestor(hasTestTag(memberInformationTag("user-2")))))
        assertEquals(0, count(hasText("uid-1 · @kai")))
        assertEquals(0, count(hasText("user-2 · @morgan")))
        assertTrue(count(hasText("创建者")) >= 1)
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/members"))
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/join-requests"))

        clickText("新成员")

        compose.onNodeWithText("邀请成员").assertExists()
        compose.onNodeWithText("加入申请").assertExists()
        compose.onTooltip("详情").assertExists()
        clickTooltip("详情")
        assertTrue(isSelected(buttonWithTooltip("详情")))
        compose.onNodeWithText("申请详情").assertExists()
        compose.onNodeWithText("来源").assertExists()
        compose.onNodeWithText("公开房间搜索").assertExists()
        compose.onNodeWithText("申请理由").assertExists()
        compose.onNodeWithText("Please approve my request").assertExists()
        clickButton("关闭")
        assertFalse(isSelected(buttonWithTooltip("详情")))

        searchField().performTextInput("mo")
        compose.pump(320)

        assertTrue(count(hasText("Morgan", substring = true)) >= 1)
        compose.onNodeWithText("@morgan").assertExists()
        // The already-member action is a disabled Button (no click action).
        compose.onNode(hasText("在房间内") and isNotEnabled()).assertExists()

        searchField().performTextClearance()
        compose.pump(320)

        clickText("房间成员")

        clickTooltip("设为管理员")
        assertTrue(count(hasText("设为管理员")) >= 1)
        assertTrue(count(hasText("Morgan", substring = true)) >= 1)
        clickButton("设为管理员")

        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/members/user-2"))

        clickTooltip("踢出此用户")
        compose.onNodeWithText("踢出此用户").assertExists()
        clickButton("踢出")

        assertEquals(2, api.requestedPaths.count { it == "/api/v1/rooms/server-alpha/members/user-2" })
        assertEquals(0, count(hasText("Morgan")))

        clickText("新成员")

        searchField().performTextInput("ri")
        compose.pump(320)

        assertTrue(api.requestedPaths.contains("/api/v1/users/search"))
        assertTrue(count(hasText("Riley", substring = true)) >= 1)
        for (username in listOf("@riley", "@river", "@rina", "@riko", "@rita")) compose.onNodeWithText(username).assertExists()

        compose.onAllNodes(hasText("邀请") and hasClickAction())[0].performClick()
        compose.waitForIdle()

        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/invites"))
        compose.onNode(hasText("已邀请") and isNotEnabled()).assertExists()

        // The shell's back button and room header open the settings page.
        panel.value = Panel.settings
        settle()

        compose.onNodeWithText("房间设置").assertExists()
        assertTrue(count(hasText("房间信息")) >= 1)
        compose.onNodeWithText("个人偏好").assertExists()
        compose.onNodeWithText("消息记录").assertExists()
        compose.onNodeWithText("表情包").assertExists()
        compose.onNodeWithText("歌单").assertExists()
        assertTrue(bounds(compose.onNodeWithText("表情包")).center.x < bounds(compose.onNodeWithText("歌单")).center.x)
        assertEquals(0, count(hasText("设置")))
        assertEquals(0, count(isToggleable()))
        val description = fieldIn(roomSettingsDescriptionInputTag)
        description.assertExists()
        compose.onNodeWithText("房间 RID").assertExists()
        assertEquals("server-alpha", editableText(editableIn(roomSettingsRidTag)))
        compose.onNodeWithText("创建时间").assertExists()
        assertEquals(roomCreatedAtLabel(DateTime.parse("2026-06-01T00:00:00Z")), editableText(editableIn(roomSettingsCreatedAtTag)))
        assertTrue(fullBounds(tag(roomSettingsRidTag)).top > fullBounds(tag(roomSettingsDescriptionInputTag)).bottom)
        assertTrue(fullBounds(tag(roomSettingsCreatedAtTag)).top > fullBounds(tag(roomSettingsRidTag)).bottom)

        fieldIn(roomSettingsNameInputTag).performTextReplacement("Alpha Renamed")
        compose.onNode(hasText("保存房间设置") and hasClickAction()).performScrollTo().performClick()
        settle()

        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha"))
        compose.onNodeWithText("房间信息已保存").assertExists()
        assertTrue(count(hasText("Alpha Renamed", substring = true)) >= 1)

        // The stacked floating notices (role change, removal, invite, save)
        // cover the top of the window, including the 个人偏好 segment; let
        // them time out so the tap reaches the segment.
        compose.pump(floatingNoticeVisibleDuration.inWholeMilliseconds + 1_000)
        compose.onAllNodesWithText("个人偏好")[0].performClick()
        compose.waitForIdle()

        compose.onNodeWithText("房间消息").assertExists()
        compose.onNodeWithText("全部").assertExists()
        compose.onNodeWithText("接收但不提醒").assertExists()
        compose.onNodeWithText("屏蔽").assertExists()
        compose.onNodeWithText("AI 语音播报").assertExists()
        assertEquals(2, count(isToggleable()))
        assertTrue(bounds(compose.onNodeWithText("AI 语音播报")).top > bounds(compose.onNodeWithText("置顶房间")).bottom)
        compose.onAllNodes(isToggleable())[1].performClick()
        compose.waitForIdle()
        clickText("接收但不提醒")
        compose.onNode(hasText("保存个人偏好") and hasClickAction()).performScrollTo().performClick()
        settle()

        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/me"))
        assertEquals(1, api.myRoomSettingsUpdates.size)
        assertEquals("silent", api.myRoomSettingsUpdates.single()["notification_policy"])
        assertEquals(false, api.myRoomSettingsUpdates.single()["is_pinned"])
        assertEquals(true, api.myRoomSettingsUpdates.single()["ai_voice_announcements_enabled"])
        compose.onNodeWithText("个人偏好已保存").assertExists()
        assertTrue(fullBounds(compose.onNodeWithText("个人偏好已保存")).top < fullBounds(tag(roomSettingsRemarkNameInputTag)).top)

        // The message history and playlist panes belong to the home shell.
        clickText("消息记录")
        compose.onNodeWithText("消息记录面板").assertExists()
        clickText("歌单")
        compose.onNodeWithText("房间歌单面板").assertExists()
    }

    @Test
    @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun `android room description input centers its first line vertically`() {
        val api = RoomManagementTestApi()
        host(api, panel = mutableStateOf(Panel.settings))

        // Dart: an unbounded (maxLines == null) field whose text is vertically
        // centred with symmetric padding, i.e. the collapsed one-line field
        // keeps the standard input height and its line sits in the middle.
        val frame = bounds(tag(roomSettingsDescriptionInputTag))
        val field = bounds(fieldIn(roomSettingsDescriptionInputTag))
        val layout = fieldIn(roomSettingsDescriptionInputTag).textLayout()
        assertEquals(1, layout.lineCount)
        assertEquals(frame.center.y, field.center.y, compose.density.density)
        assertEquals(Input.defaultHeight.value, dp(field.height), 0.5f)
        assertEquals(dp(bounds(tag(roomSettingsNameInputTag)).height), dp(frame.height), 0.5f)
        // The field grows with its text instead of scrolling a single line.
        fieldIn(roomSettingsDescriptionInputTag).performTextReplacement("第一行\n第二行\n第三行")
        compose.waitForIdle()
        assertEquals(3, fieldIn(roomSettingsDescriptionInputTag).textLayout().lineCount)
        assertTrue(dp(bounds(tag(roomSettingsDescriptionInputTag)).height) > Input.defaultHeight.value + 20f)
    }

    @Test
    fun `room settings confirms auto-reviewing pending applications before join policy change`() {
        val api = RoomManagementTestApi()
        host(api, panel = mutableStateOf(Panel.settings))

        compose.onAllNodesWithText("开放").onLast().performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNode(hasText("保存房间设置") and hasClickAction()).performScrollTo().performClick()
        settle()

        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/join-requests"))
        compose.onNodeWithText("确认修改加入方式？").assertExists()
        assertEquals(1, count(hasText("自动批准所有未处理申请", substring = true)))
        assertTrue(api.roomSettingsUpdates.isEmpty())

        clickButton("取消")
        assertTrue(api.roomSettingsUpdates.isEmpty())

        compose.onNode(hasText("保存房间设置") and hasClickAction()).performClick()
        settle()
        assertEquals(1, api.roomSettingsUpdates.size)
        assertEquals("approval_required", api.roomSettingsUpdates.single()["join_policy"])
        assertEquals(0, count(hasText("确认修改加入方式？")))
    }

    @Test
    fun `room settings info fields are read-only for regular members`() {
        val api = RoomManagementTestApi(currentRoomRole = "member")
        host(api, panel = mutableStateOf(Panel.settings))

        assertEquals(0, count(hasSetTextAction() and hasAnyAncestor(hasTestTag(roomSettingsNameInputTag))))
        assertEquals("Alpha Room", editableText(compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText) and hasAnyAncestor(hasTestTag(roomSettingsNameInputTag)), useUnmergedTree = true)))
        assertEquals("", editableText(compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText) and hasAnyAncestor(hasTestTag(roomSettingsDescriptionInputTag)), useUnmergedTree = true)))
        assertEquals(0, count(hasSetTextAction() and hasAnyAncestor(hasTestTag(roomSettingsDescriptionInputTag))))
        val save = compose.onNode(hasText("保存房间设置") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
        save.assertExists()
    }

    @Test
    fun `authenticated home shell hides member removal for regular users`() {
        val api = RoomManagementTestApi(currentRoomRole = "member", currentRoomJoinPolicy = "closed")
        host(api)

        assertTrue(count(hasText("成员")) >= 1)
        assertEquals(0, count(hasText("房间成员")))
        assertEquals(0, count(hasText("新成员")))
        assertEquals(0, count(hasText("黑名单")))
        assertTrue(count(hasText("Morgan")) >= 1)
        assertEquals(0, count(hasTooltip("踢出此用户")))
        assertEquals(0, count(hasTooltip("设为管理员")))
        assertEquals(0, count(hasTooltip("转让创建者")))
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/members"))
        assertFalse(api.requestedPaths.contains("/api/v1/rooms/server-alpha/join-requests"))
        assertFalse(api.requestedPaths.contains("/api/v1/rooms/server-alpha/blacklist"))
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-mdpi")
    fun `narrow member management stacks filters and wraps member actions`() {
        val api = RoomManagementTestApi()
        host(api)

        val presenceRect = bounds(tag(memberPresenceFilterTag))
        val roleRect = bounds(tag(memberRoleFilterTag))
        assertTrue(presenceRect.bottom < roleRect.top)
        assertEquals(presenceRect.left, roleRect.left, 0.01f)
        assertEquals(presenceRect.right, roleRect.right, 0.01f)

        tag("member-actions-user-1").assertDoesNotExist()
        assertEquals(0, count(hasTooltip("成员设置")))
        val memberActions = tag(memberActionWrapTag("user-2"))
        memberActions.assertExists()
        compose.onTooltip("修改房间内用户名").assertExists()
        compose.onTooltip("设为管理员").assertExists()
        compose.onTooltip("踢出此用户").assertExists()
        compose.onTooltip("转让创建者").assertExists()

        assertTrue(bounds(memberActions).top > bounds(compose.onAllNodesWithText("Morgan")[0]).bottom)

        clickTooltip("修改房间内用户名")
        compose.onNodeWithText("修改Morgan Account的房间内用户名").assertExists()
    }

    @Test
    @Config(qualifiers = "w900dp-h800dp-mdpi")
    fun `one long member name stacks every actionable member on wide layouts`() {
        val longRoomDisplayName = "Morgan 的较长且独占信息行时能完整显示的房间昵称"
        val api = RoomManagementTestApi(secondaryMemberRoomDisplayName = longRoomDisplayName, includeActionComparisonMember = true)
        host(api)

        val longName = compose.onNodeWithText(longRoomDisplayName, useUnmergedTree = true)
        val morganActions = tag(memberActionWrapTag("user-2"))
        val taylorActions = tag(memberActionWrapTag("user-5"))
        val morganInformation = tag(memberInformationTag("user-2"))
        val morganPresence = tag(memberPresenceTag("user-2"))
        val morganRole = tag(memberRoleTag("user-2"))
        longName.assertExists()
        morganActions.assertExists()
        taylorActions.assertExists()
        morganInformation.assertExists()
        morganPresence.assertExists()
        morganRole.assertExists()
        assertFalse(longName.textLayout().didOverflowHeight || longName.textLayout().isLineEllipsized(longName.textLayout().lineCount - 1))
        assertTrue(bounds(morganActions).top > bounds(morganInformation).bottom)
        assertTrue(bounds(taylorActions).top > bounds(compose.onNodeWithText("Taylor", useUnmergedTree = true)).bottom)
        val informationRect = bounds(morganInformation)
        assertEquals(informationRect.center.y, bounds(morganPresence).center.y, 1f)
        assertEquals(informationRect.center.y, bounds(morganRole).center.y, 1f)
    }

    @Test
    @Config(qualifiers = "w720dp-h800dp-mdpi", fontScale = 1.2f)
    fun `member filters stack before either segmented control starts scrolling`() {
        val api = RoomManagementTestApi()
        host(api)

        val presenceFilter = tag(memberPresenceFilterTag)
        val roleFilter = tag(memberRoleFilterTag)
        assertTrue(bounds(presenceFilter).bottom < bounds(roleFilter).top)
        for (filter in listOf(memberPresenceFilterTag, memberRoleFilterTag)) {
            assertEquals(
                "each filter should receive a full row before using overflow controls",
                0,
                count(hasTestTag(com.gangchat.client.ui.segmentedControlScrollViewTag) and hasAnyAncestor(hasTestTag(filter))),
            )
            val labels = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and hasAnyAncestor(hasTestTag(filter)), useUnmergedTree = true)
            for (index in 0 until labels.fetchSemanticsNodes().size) {
                assertFalse(labels[index].textLayout().didOverflowWidth)
            }
        }
    }

    @Test
    @Config(qualifiers = "w900dp-h800dp-mdpi")
    fun `member names wrap in full only after every action row stacks`() {
        val longRoomDisplayName = "Morgan 的一个比较长但信息独占一行时仍然不能完整显示的房间成员昵称"
        val api = RoomManagementTestApi(secondaryMemberRoomDisplayName = longRoomDisplayName, includeActionComparisonMember = true)
        host(api)

        val longName = compose.onNodeWithText(longRoomDisplayName, useUnmergedTree = true)
        val morganInformation = tag(memberInformationTag("user-2"))
        val morganActions = tag(memberActionWrapTag("user-2"))
        val taylorInformation = tag(memberInformationTag("user-5"))
        val taylorActions = tag(memberActionWrapTag("user-5"))
        val layout = longName.textLayout()
        // No line limit and no ellipsis (Dart: maxLines and overflow null).
        assertEquals(Int.MAX_VALUE, layout.layoutInput.maxLines)
        assertFalse(layout.isLineEllipsized(layout.lineCount - 1))
        assertFalse(layout.didOverflowHeight)
        assertTrue(dp(bounds(morganInformation).height) > 48f)
        assertTrue(bounds(morganActions).top > bounds(morganInformation).bottom)
        assertTrue(bounds(taylorActions).top > bounds(taylorInformation).bottom)
    }

    @Test
    fun `authenticated home shell hides new members for closed rooms`() {
        val api = RoomManagementTestApi(currentRoomJoinPolicy = "closed")
        host(api)

        compose.onNodeWithText("房间成员").assertExists()
        assertEquals(0, count(hasText("新成员")))
        compose.onNodeWithText("黑名单").assertExists()
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/members"))
        assertFalse(api.requestedPaths.contains("/api/v1/rooms/server-alpha/join-requests"))
    }

    @Test
    fun `authenticated home shell lets superusers remove creators`() {
        val api = RoomManagementTestApi(currentRoomRole = "member", secondaryMemberRole = "owner")
        host(api, currentUser = roomManagementTestCurrentUser(isSuperuser = true))

        assertTrue(count(hasText("Morgan")) >= 1)
        assertTrue(count(hasText("创建者")) >= 1)
        compose.onTooltip("踢出此用户").assertExists()
        assertEquals(0, count(hasTooltip("设为管理员")))
        assertEquals(0, count(hasTooltip("转让创建者")))
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/members"))
    }

    @Test
    fun `creator removal action aligns with member action group`() {
        val api = RoomManagementTestApi(currentRoomRole = "member", secondaryMemberRole = "owner", includeActionComparisonMember = true)
        host(api, currentUser = roomManagementTestCurrentUser(isSuperuser = true))

        assertTrue(count(hasText("Morgan")) >= 1)
        assertTrue(count(hasText("Taylor")) >= 1)
        assertEquals(2, compose.onAllTooltips("踢出此用户").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllTooltips("转让创建者").fetchSemanticsNodes().size)

        val creatorRemove = compose.onNode(hasTooltip("踢出此用户") and hasAnyAncestor(hasTestTag(memberActionWrapTag("user-2"))), useUnmergedTree = true)
        val memberTransfer = compose.onNode(hasTooltip("转让创建者") and hasAnyAncestor(hasTestTag(memberActionWrapTag("user-5"))), useUnmergedTree = true)
        assertEquals(bounds(memberTransfer).right, bounds(creatorRemove).right, 0.01f)
    }

    @Test
    fun `member management keeps row order after role updates`() {
        val api = RoomManagementTestApi(includeActionComparisonMember = true)
        val room = mutableStateOf(api.room())
        host(api, room = room)

        assertTrue(count(hasText("Morgan")) >= 1)
        compose.onNodeWithText("Taylor").assertExists()
        val morganTopBefore = bounds(compose.onAllNodesWithText("Morgan")[0]).top
        val taylorTopBefore = bounds(compose.onNodeWithText("Taylor")).top
        assertTrue(morganTopBefore < taylorTopBefore)

        assertEquals(2, compose.onAllTooltips("设为管理员").fetchSemanticsNodes().size)
        compose.onAllTooltips("设为管理员")[1].performClick()
        compose.waitForIdle()

        clickButton("设为管理员")
        settle()

        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/members/user-5"))
        assertTrue(bounds(compose.onAllNodesWithText("Morgan")[0]).top < bounds(compose.onNodeWithText("Taylor")).top)

        // PORT: the shell applies Dart's realtime `room_updated` event by
        // passing the refreshed room, which reloads the members.
        room.value = api.room()
        settle()

        assertTrue(bounds(compose.onAllNodesWithText("Morgan")[0]).top < bounds(compose.onNodeWithText("Taylor")).top)
    }

    @Test
    fun `message profile can jump to member management by UID`() {
        // PORT: the message avatar's '管理成员' action belongs to the home
        // shell; this hosts the members panel it opens with the UID query.
        val api = RoomManagementTestApi()
        host(api, initialSearchQuery = "uid-2")

        assertTrue(count(hasText("成员")) >= 1)
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/members"))
        assertEquals("uid-2", editableText(searchField()))
        assertTrue(count(hasText("Morgan", substring = true)) >= 1)
    }
}
