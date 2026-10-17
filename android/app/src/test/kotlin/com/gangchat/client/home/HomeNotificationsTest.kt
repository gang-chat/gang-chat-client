package com.gangchat.client.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.RoomNotificationItem
import com.gangchat.client.app.kRoomEventNotificationMentioned
import com.gangchat.client.app.kRoomEventNotificationRolePromoted
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomApplication
import com.gangchat.client.protocol.RoomEventNotification
import com.gangchat.client.protocol.RoomInvite
import com.gangchat.client.protocol.RoomMembership
import com.gangchat.client.protocol.RoomPersonalProfile
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.AppNotificationHost
import com.gangchat.client.widgets.IconSemanticsKey
import com.gangchat.client.ui.MediaCacheScope
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiTheme
import com.gangchat.client.ui.hasTooltip
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.testMediaCacheController
import com.gangchat.client.ui.textLayout
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.LocalMediaQuery
import com.gangchat.client.widgets.RootNavigator
import com.gangchat.client.widgets.WindowMediaQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Fixtures of test/widget/home/room_hover_card_test.dart.

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

private val _superuser =
    UserSummary(
        id = "u_superuser",
        username = "GANG",
        displayName = "GANG",
        avatarUrl = null,
        defaultAvatarKey = "blue-3",
        roomRole = "superuser",
        isSuperuser = true,
    )

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

private fun _room(id: String, rid: String, name: String, joined: Boolean = true): PublicRoom =
    PublicRoom(
        id = id,
        rid = rid,
        name = name,
        avatarUrl = null,
        defaultAvatarKey = "room-2",
        visibility = "private",
        joinPolicy = "closed",
        memberCount = 2,
        onlineMemberCount = 0,
        liveParticipantCount = 0,
        joined = joined,
        joinState = if (joined) "joined" else "none",
    )

/** The notification test cases of test/widget/home/room_hover_card_test.dart (they host `HomeNotificationsPane`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Flutter's default widget-test surface (800 x 600): the wide layout.
@Config(sdk = [35], qualifiers = "w800dp-h600dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class HomeNotificationsTest {
    @get:Rule
    val compose = createComposeRule()

    /** The on-screen keyboard (Dart's `tester.view.viewInsets`). */
    private val viewInsets: MutableState<EdgeInsets> = mutableStateOf(EdgeInsets.zero)

    /**
     * Dart's `_host`: `MaterialApp(home: Scaffold(body: Center(child: ...)))`,
     * with a controllable keyboard inset.
     */
    private fun host(content: @Composable () -> Unit) {
        val cache = testMediaCacheController()
        compose.setContent {
            UiTheme {
                WindowMediaQuery {
                    val media = LocalMediaQuery.current
                    CompositionLocalProvider(LocalMediaQuery provides media.copy(viewInsets = viewInsets.value)) {
                        MediaCacheScope(cache) {
                            AppNotificationHost {
                                RootNavigator { _ ->
                                    Box(
                                        Modifier.fillMaxSize().background(UiColors.background).testTag("page"),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        content()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Pane(
        invites: List<RoomInvite> = emptyList(),
        applications: List<RoomApplication> = emptyList(),
        roomNotifications: List<RoomEventNotification> = emptyList(),
        onOpenRoomEvent: (RoomEventNotification) -> Unit = {},
        onCopyNotification: RoomNotificationCopyCallback? = null,
        onDeleteNotification: RoomNotificationDeleteCallback? = null,
        onDeleteNotifications: RoomNotificationsDeleteCallback? = null,
    ) {
        HomeNotificationsPane(
            invites = invites,
            applications = applications,
            roomNotifications = roomNotifications,
            loading = false,
            error = null,
            busyInviteId = null,
            busyApplicationId = null,
            currentUser = _currentUser,
            onClose = {},
            onRefresh = {},
            onReviewInvite = { _, _ -> },
            onWithdrawApplication = {},
            onOpenRoom = {},
            onOpenRoomEvent = onOpenRoomEvent,
            onCopyNotification = onCopyNotification,
            onDeleteNotification = onDeleteNotification,
            onDeleteNotifications = onDeleteNotifications,
        )
    }

    private fun tag(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun click(tag: String) {
        tag(tag).performClick()
        compose.waitForIdle()
    }

    private fun hover(node: SemanticsNodeInteraction) {
        compose.onNodeWithTag("page").performMouseInput { moveTo(Offset.Zero) }
        node.performMouseInput { moveTo(center) }
        compose.waitForIdle()
    }

    private fun hasIcon(icon: ImageVector) = SemanticsMatcher.expectValue(IconSemanticsKey, icon)

    private fun checkboxState(tag: String): ToggleableState? =
        tag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)

    private fun isSelected(node: SemanticsNodeInteraction): Boolean =
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true

    /** The pressable surface under a `ButtonIcon`'s tag (its tooltip box carries the tag). */
    private fun buttonSurface(tag: String) = compose.onNode(hasParent(hasTestTag(tag)), useUnmergedTree = true)

    private fun count(matcher: SemanticsMatcher) = compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun sizeDp(node: SemanticsNodeInteraction): Pair<Float, Float> {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val density = compose.density.density
        return Pair(bounds.width / density, bounds.height / density)
    }

    private fun inlineDateField() =
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(notificationCalendarInlineDateInputTag)))

    private fun dateFieldSurface(tag: String) = compose.onNode(hasAnyAncestor(hasTestTag(tag)) and hasClickAction())

    @Test
    fun `notification user avatar opens a user profile card`() {
        val invite =
            RoomInvite(
                id = "invite_user_card",
                status = "accepted",
                room = _room(id = "room_1", rid = "R100", name = "Invite Room"),
                inviter = _creator,
                createdAt = DateTime.utc(2026, 6, 1),
            )
        host { Pane(invites = listOf(invite)) }

        click(homeNotificationsSelectButtonTag)
        val selection = notificationSelectBoxTag("invite:invite_user_card")
        tag(selection).assertExists()

        val avatar = tag("notification-inviter-avatar-invite_user_card")
        hover(avatar)
        compose.onNodeWithText("@creator").assertExists()

        avatar.performClick()
        compose.waitForIdle()
        assertEquals(ToggleableState.Off, checkboxState(selection))
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `notification date filter sits to the right of search`() {
        val invite =
            RoomInvite(id = "invite_date_filter", status = "accepted", room = _joinedRoom, inviter = _creator, createdAt = DateTime(2026, 6, 1))
        host { Pane(invites = listOf(invite)) }
        compose.onNode(hasSetTextAction()).performTextInput("Launch")
        compose.waitForIdle()

        val searchRect = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val dateRect = compose.onTooltip("筛选通知日期").fetchSemanticsNode().boundsInRoot
        assertTrue(searchRect.right < dateRect.left)
        // The time cell never highlights the query (Dart's `query: ''`).
        assertEquals("2026/06/01 00:00", tag("notification-time-invite_date_filter").textLayout().layoutInput.text.text)
        assertTrue(tag("notification-time-invite_date_filter").textLayout().layoutInput.text.spanStyles.isEmpty())

        compose.onTooltip("筛选通知日期").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("筛选通知日期").assertExists()
        tag(notificationStartDateFieldTag).assertExists()
        tag(notificationEndDateFieldTag).assertExists()
        tag(notificationDateResetButtonTag).assertExists()
        assertEquals(0, count(hasText("当前区间：", substring = true)))

        val dialogCenter = tag(notificationStartDateFieldTag).fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(tag(notificationDateResetButtonTag).fetchSemanticsNode().boundsInRoot.center.x < dialogCenter)
        assertTrue(tag(notificationDateConfirmButtonTag).fetchSemanticsNode().boundsInRoot.center.x > dialogCenter)

        dateFieldSurface(notificationStartDateFieldTag).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("选择开始日期").assertExists()
        compose.onNodeWithText("1970 年 1 月").assertExists()
        compose.onNodeWithText("一").assertExists()
        compose.onNodeWithText("日").assertExists()
        assertEquals(0, count(hasText("SELECT DATE")))
        assertEquals(0, count(hasText("Cancel")))
        assertEquals(0, count(hasText("OK")))
        tag(notificationCalendarInlineDateInputTag).assertDoesNotExist()
        compose.onTooltip("切换到手动输入").assertExists()
        compose.onTooltip("展开年份选择").assertExists()
        for (week in 0 until 5) tag(notificationCalendarWeekTag(week)).assertExists()
        tag(notificationCalendarWeekTag(5)).assertDoesNotExist()

        val (dayWidth, dayHeight) = sizeDp(tag(notificationDateDayTag(1970, 1, 2)))
        assertEquals(dayWidth, dayHeight, 0.01f)

        click(notificationCalendarYearPickerToggleTag)
        tag(notificationCalendarYearPickerTag).assertExists()
        tag(notificationCalendarYearTag(1970)).assertExists()

        click(notificationCalendarInputModeToggleTag)
        tag(notificationCalendarInlineDateInputTag).assertExists()
        tag(notificationCalendarYearPickerTag).assertDoesNotExist()
        // PORT: Android hides the month grid while typing (Dart's Windows run
        // of this test keeps it).
        tag(notificationCalendarMonthGridTag).assertDoesNotExist()
        assertTrue(isSelected(buttonSurface(notificationCalendarInputModeToggleTag)))

        inlineDateField().assertIsFocused()
        assertEquals(TextAlign.Center, inlineDateField().textLayout().layoutInput.style.textAlign)

        inlineDateField().performTextReplacement("1971-01-01")
        inlineDateField().performImeAction()
        compose.waitForIdle()
        tag(notificationCalendarInlineDateInputTag).assertDoesNotExist()
        compose.onNodeWithText("1971 年 1 月").assertExists()

        click(notificationCalendarInputModeToggleTag)
        tag(notificationCalendarInlineDateInputTag).assertExists()

        inlineDateField().performTextReplacement("1971-02-30")
        inlineDateField().performImeAction()
        compose.waitForIdle()
        compose.onNodeWithText("请输入如 2026-07-10 的有效日期").assertExists()
        tag(notificationCalendarInlineDateInputTag).assertExists()

        inlineDateField().performTextReplacement("1971-02-02")
        // An outside tap commits (Dart taps the weekday header, hidden here
        // while typing).
        compose.onNodeWithText("选择开始日期").performClick()
        compose.waitForIdle()
        tag(notificationCalendarInlineDateInputTag).assertDoesNotExist()
        compose.onNodeWithText("1971 年 2 月").assertExists()
        tag(notificationDateDayTag(1971, 2, 2)).assertExists()

        click(notificationCalendarInputModeToggleTag)
        inlineDateField().performTextReplacement("1971-03-03")
        click(notificationCalendarInputModeToggleTag)
        compose.onNodeWithText("1971 年 3 月").assertExists()

        click(notificationCalendarConfirmButtonTag)
        click(notificationDateConfirmButtonTag)

        assertTrue(isSelected(buttonSurface(homeNotificationsDateFilterButtonTag)))

        compose.onTooltip("筛选通知日期").performClick()
        compose.waitForIdle()
        click(notificationDateResetButtonTag)
        tag(notificationStartDateFieldTag).assertDoesNotExist()
        assertFalse(isSelected(buttonSurface(homeNotificationsDateFilterButtonTag)))
    }

    @Test
    @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun `android notification date filters fit narrow screens`() {
        val invite =
            RoomInvite(
                id = "invite_android_date_filter",
                status = "accepted",
                room = _joinedRoom,
                inviter = _creator,
                createdAt = DateTime(2026, 6, 1),
            )
        host { Pane(invites = listOf(invite)) }

        compose.onTooltip("筛选通知日期").performClick()
        compose.waitForIdle()

        dateFieldSurface(notificationStartDateFieldTag).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("选择开始日期").assertExists()
        compose.onNodeWithText("1970/01").assertExists()
        tag(notificationCalendarPreviousYearTag).assertDoesNotExist()
        tag(notificationCalendarNextYearTag).assertDoesNotExist()

        click(notificationCalendarInputModeToggleTag)

        val field = inlineDateField()
        assertEquals("1970-01-01", field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text)
        field.assertIsFocused()
        assertEquals(TextRange(0, 10), field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.TextSelectionRange))
        viewInsets.value = EdgeInsets(bottom = 300.dp)
        compose.waitForIdle()
        tag(notificationCalendarMonthGridTag).assertDoesNotExist()

        click(notificationCalendarInputModeToggleTag)
        tag(notificationCalendarInlineDateInputTag).assertDoesNotExist()
        tag(notificationCalendarMonthGridTag).assertDoesNotExist()
        viewInsets.value = EdgeInsets.zero
        compose.waitForIdle()
        tag(notificationCalendarMonthGridTag).assertExists()

        click(notificationCalendarConfirmButtonTag)
        dateFieldSurface(notificationEndDateFieldTag).performClick()
        compose.waitForIdle()
        click(notificationCalendarInputModeToggleTag)

        val endField = inlineDateField()
        endField.assertIsFocused()
        val endText = endField.fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text ?: ""
        assertEquals(TextRange(0, endText.length), endField.fetchSemanticsNode().config.getOrNull(SemanticsProperties.TextSelectionRange))
        viewInsets.value = EdgeInsets(bottom = 300.dp)
        compose.waitForIdle()
        tag(notificationCalendarMonthGridTag).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w360dp-h740dp-xxhdpi", fontScale = 1.25f)
    fun `android notification user targets keep full badges and correct avatars`() {
        val invite = RoomInvite(id = "invite_android_superuser", status = "accepted", room = _joinedRoom, inviter = _superuser, createdAt = DateTime.utc(2026, 7, 20))
        val application =
            RoomApplication(
                id = "application_android_superuser",
                status = "approved",
                room = _joinedRoom,
                createdAt = DateTime.utc(2026, 7, 20),
                updatedAt = DateTime.utc(2026, 7, 21),
                reviewedAt = DateTime.utc(2026, 7, 21),
                reviewer = _superuser,
            )
        val notification =
            RoomEventNotification(
                id = "event_android_superuser",
                type = kRoomEventNotificationRolePromoted,
                room = _joinedRoom,
                actor = _superuser,
                createdAt = DateTime.utc(2026, 7, 22),
                fromRole = "member",
                toRole = "superuser",
            )
        host { Pane(invites = listOf(invite), applications = listOf(application), roomNotifications = listOf(notification)) }

        for (key in
            listOf(
                "notification-inviter-role-invite_android_superuser",
                "notification-application-reviewer-role-application_android_superuser",
                "notification-room-event-actor-role-event_android_superuser",
                "notification-room-event-target-role-event_android_superuser",
            )) {
            val roleText = compose.onNode(hasText("超级用户") and hasAnyAncestor(hasTestTag(key)), useUnmergedTree = true)
            val layout = roleText.textLayout()
            assertFalse("$key should not ellipsize 超级用户", layout.isLineEllipsized(0))
            assertFalse("$key should not ellipsize 超级用户", layout.hasVisualOverflow)
        }

        for (key in
            listOf(
                "notification-inviter-avatar-invite_android_superuser",
                "notification-application-reviewer-avatar-application_android_superuser",
                "notification-room-event-actor-avatar-event_android_superuser",
            )) {
            val (width, height) = sizeDp(tag(key))
            assertEquals(18f, width, 0.01f)
            assertEquals(18f, height, 0.01f)
            assertEquals(1, count(hasTestTag(notificationBrandAvatarImageTag) and hasAnyAncestor(hasTestTag(key))))
        }

        val roomAvatars = compose.onAllNodes(SemanticsMatcher("room avatar") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("notification-room-avatar-") == true
        }, useUnmergedTree = true)
        val avatars = roomAvatars.fetchSemanticsNodes()
        assertTrue(avatars.isNotEmpty())
        for (index in avatars.indices) {
            assertEquals(18f, avatars[index].boundsInRoot.width / compose.density.density, 0.01f)
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `windows superuser notifications use desktop brand avatars`() {
        // PORT: hosted on the Android tablet's wide layout, which shares the
        // desktop sizes and the bundled brand avatar.
        val invite = RoomInvite(id = "invite_windows_superuser", status = "accepted", room = _joinedRoom, inviter = _superuser, createdAt = DateTime.utc(2026, 7, 20))
        val application =
            RoomApplication(
                id = "application_windows_superuser",
                status = "approved",
                room = _joinedRoom,
                createdAt = DateTime.utc(2026, 7, 20),
                updatedAt = DateTime.utc(2026, 7, 21),
                reviewedAt = DateTime.utc(2026, 7, 21),
                reviewer = _superuser,
            )
        val notification =
            RoomEventNotification(
                id = "event_windows_superuser",
                type = kRoomEventNotificationRolePromoted,
                room = _joinedRoom,
                actor = _superuser,
                createdAt = DateTime.utc(2026, 7, 22),
                fromRole = "member",
                toRole = "superuser",
            )
        host { Pane(invites = listOf(invite), applications = listOf(application), roomNotifications = listOf(notification)) }

        for (key in
            listOf(
                "notification-inviter-avatar-invite_windows_superuser",
                "notification-application-reviewer-avatar-application_windows_superuser",
                "notification-room-event-actor-avatar-event_windows_superuser",
            )) {
            val (width, height) = sizeDp(tag(key))
            assertEquals(34f, width, 0.01f)
            assertEquals(34f, height, 0.01f)
            assertEquals(1, count(hasTestTag(notificationBrandAvatarImageTag) and hasAnyAncestor(hasTestTag(key))))
        }
        assertEquals(1, count(hasText("超级用户") and hasAnyAncestor(hasTestTag("notification-inviter-role-invite_windows_superuser"))))
        val avatars =
            compose.onAllNodes(SemanticsMatcher("room avatar") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("notification-room-avatar-") == true
            }, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(avatars.isNotEmpty())
        for (avatar in avatars) assertEquals(34f, avatar.boundsInRoot.width / compose.density.density, 0.01f)
    }

    @Test
    fun `Android and Windows superusers prefer a custom avatar`() {
        val customAvatar =
            "data:image/png;base64," +
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        val superuserWithAvatar =
            UserSummary(
                id = _superuser.id,
                username = _superuser.username,
                displayName = _superuser.displayName,
                avatarUrl = customAvatar,
                defaultAvatarKey = _superuser.defaultAvatarKey,
                roomRole = _superuser.roomRole,
                isSuperuser = true,
            )
        val invite =
            RoomInvite(
                id = "invite_superuser_custom_avatar",
                status = "accepted",
                room = _joinedRoom,
                inviter = superuserWithAvatar,
                createdAt = DateTime.utc(2026, 7, 20),
            )
        // PORT: Android phones (compact) and the wide layout instead of the
        // Android and Windows platforms.
        val compact = mutableStateOf(true)
        host { HomeAdaptiveLayout(compact = compact.value) { Pane(invites = listOf(invite)) } }

        for ((isCompact, size) in listOf(true to 18f, false to 34f)) {
            compact.value = isCompact
            compose.waitForIdle()
            val avatar = tag("notification-inviter-avatar-invite_superuser_custom_avatar")
            assertEquals(size, sizeDp(avatar).first, 0.01f)
            assertEquals(0, count(hasTestTag(notificationBrandAvatarImageTag)))
        }
    }

    @Test
    fun `room event notification avatars open profile cards`() {
        val openedEvents = mutableListOf<String>()
        val notification =
            RoomEventNotification(
                id = "event_user_card",
                type = kRoomEventNotificationRolePromoted,
                room = _room(id = "room_event", rid = "R200", name = "Event Room"),
                actor = _creator,
                createdAt = DateTime.utc(2026, 6, 1),
                fromRole = "member",
                toRole = "admin",
            )
        host { Pane(roomNotifications = listOf(notification), onOpenRoomEvent = { event -> openedEvents.add(event.id) }) }

        tag("notification-room-event-new-event_user_card").assertExists()
        click(homeNotificationsSelectButtonTag)
        val selection = notificationSelectBoxTag("room-event:event_user_card")
        tag(selection).assertExists()

        hover(tag("notification-room-event-actor-avatar-event_user_card"))
        compose.onNodeWithText("@creator").assertExists()

        hover(tag("notification-room-avatar-room-event-event_user_card"))
        compose.onNodeWithText("RID: R200").assertExists()

        compose.onNode(hasIcon(Icons.Rounded.LocationOn), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("event_user_card"), openedEvents)
        assertEquals(ToggleableState.Off, checkboxState(selection))
    }

    @Test
    fun `mention room event notification shows content from info button`() {
        val preview = "@Current User 请看一下这里"
        val notification =
            RoomEventNotification(
                id = "event_mention_info",
                type = kRoomEventNotificationMentioned,
                room = _room(id = "room_event_mention", rid = "R201", name = "Mention Room"),
                actor = _creator,
                createdAt = DateTime.utc(2026, 6, 1),
                messageId = "msg_mention_info",
                messagePreview = preview,
            )
        host { Pane(roomNotifications = listOf(notification)) }

        compose.onNodeWithText("提及").assertExists()
        assertEquals(0, count(hasText(preview)))
        click(homeNotificationsSelectButtonTag)
        val selection = notificationSelectBoxTag("room-event:event_mention_info")
        tag(selection).assertExists()

        val info = compose.onNode(hasIcon(Icons.Outlined.Info), useUnmergedTree = true)
        hover(info)
        assertTrue(count(hasText(preview)) >= 1)
        info.performClick()
        compose.waitForIdle()
        assertEquals(ToggleableState.Off, checkboxState(selection))
    }

    @Test
    fun `deleted notification rooms open only a tombstone card`() {
        val invite =
            RoomInvite(
                id = "invite_deleted",
                status = "pending",
                room =
                    PublicRoom(
                        id = "room_deleted",
                        rid = "R404",
                        name = "Deleted Room",
                        description = "This should not be visible.",
                        avatarUrl = "/deleted.png",
                        defaultAvatarKey = "room-2",
                        visibility = "private",
                        joinPolicy = "closed",
                        memberCount = 0,
                        onlineMemberCount = 0,
                        liveParticipantCount = 0,
                        joined = false,
                        joinState = "none",
                        createdBy = _creator,
                    ),
                inviter = _creator,
                createdAt = DateTime.utc(2026, 6, 1),
                roomExists = false,
                invalidReason = "room_missing",
            )
        host { Pane(invites = listOf(invite)) }

        assertEquals(1, count(hasText("房间已删除")))
        assertEquals(0, count(hasText("空")))
        assertEquals(0, count(hasText("Deleted Room")))

        val avatar = tag("notification-room-avatar-invite_deleted")
        // Label '' and no fallback text: the avatar shows no initials.
        assertEquals(0, count(hasAnyAncestor(hasTestTag("notification-room-avatar-invite_deleted")) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)))
        hover(avatar)
        assertEquals(2, count(hasText("房间已删除")))
        assertEquals(0, count(hasText("Deleted Room")))
        assertEquals(0, count(hasText("RID: R404")))

        avatar.performClick()
        compose.waitForIdle()
        assertEquals(0, count(hasText("Deleted Room")))
        assertEquals(0, count(hasText("RID: R404")))
    }

    @Test
    fun `deleted notification users display missing user placeholders`() {
        val invite =
            RoomInvite(
                id = "invite_deleted_user",
                status = "pending",
                room = _room(id = "room_1", rid = "R100", name = "Invite Room", joined = false),
                inviter =
                    UserSummary(
                        id = "user_deleted",
                        username = "deleted",
                        displayName = "Deleted User",
                        avatarUrl = "/deleted-user.png",
                        defaultAvatarKey = "blue-3",
                        roomRole = "left",
                    ),
                createdAt = DateTime.utc(2026, 6, 1),
                inviterExists = false,
                invalidReason = "inviter_deleted",
            )
        host { Pane(invites = listOf(invite)) }

        assertEquals(1, count(hasText("用户已注销")))
        assertEquals(0, count(hasText("空")))
        assertEquals(0, count(hasText("Deleted User")))
        val avatarTag = "notification-inviter-avatar-invite_deleted_user"
        assertEquals(0, count(hasAnyAncestor(hasTestTag(avatarTag)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)))
        hover(tag(avatarTag))
        assertEquals(2, count(hasText("用户已注销")))
        assertEquals(0, count(hasText("@已注销")))
        assertEquals(0, count(hasText("Deleted User")))
    }

    @Test
    fun `notification context menu copies text and confirms deletion`() {
        val invite = RoomInvite(id = "invite_context_menu", status = "accepted", room = _joinedRoom, inviter = _creator, createdAt = DateTime.utc(2026, 6, 1))
        val copiedItems = mutableListOf<String>()
        val deletedItems = mutableListOf<String>()
        host {
            Pane(
                invites = listOf(invite),
                onCopyNotification = { item -> copiedItems.add(item.id) },
                onDeleteNotification = { item -> deletedItems.add(item.id) },
            )
        }

        val row = tag(notificationContextRowTag("invite:invite_context_menu"))
        row.performMouseInput { rightClick() }
        compose.waitForIdle()
        val surface = tag(notificationRowSurfaceTag("invite:invite_context_menu"))
        val height = sizeDp(surface).second
        assertEquals(UiColors.selected, compose.pixelIn(surface, 6f, height / 2))
        assertEquals(UiColors.selectedBorder, compose.pixelIn(surface, 0.4f, height / 2))
        compose.onNodeWithText("复制").assertExists()
        compose.onNodeWithText("删除").assertExists()

        compose.onNodeWithText("复制").performClick()
        compose.waitForIdle()
        assertEquals(listOf("invite:invite_context_menu"), copiedItems)

        row.performMouseInput { rightClick() }
        compose.waitForIdle()
        compose.onNodeWithText("删除").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("删除通知").assertExists()
        compose.onNodeWithText("删除后将无法恢复").assertExists()

        compose.onNode(hasText("删除") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(listOf("invite:invite_context_menu"), deletedItems)
    }

    @Test
    @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun `Android notification hold opens the desktop context menu`() {
        val invite =
            RoomInvite(id = "invite_android_context_menu", status = "accepted", room = _joinedRoom, inviter = _creator, createdAt = DateTime.utc(2026, 6, 1))
        host { Pane(invites = listOf(invite), onCopyNotification = {}, onDeleteNotification = {}) }

        tag(notificationContextRowTag("invite:invite_android_context_menu")).performTouchInput { longClick() }
        compose.waitForIdle()

        compose.onNodeWithText("复制").assertExists()
        compose.onNodeWithText("删除").assertExists()
    }

    @Test
    fun `batch management selects notifications and confirms bulk delete`() {
        val first = RoomInvite(id = "invite_batch_first", status = "accepted", room = _joinedRoom, inviter = _creator, createdAt = DateTime.utc(2026, 6, 1))
        val second = RoomInvite(id = "invite_batch_second", status = "accepted", room = _joinedRoom, inviter = _creator, createdAt = DateTime.utc(2026, 6, 2))
        val deletedBatches = mutableListOf<List<String>>()
        host {
            Pane(
                invites = listOf(first, second),
                onCopyNotification = {},
                onDeleteNotifications = { items: List<RoomNotificationItem> -> deletedBatches.add(items.map { it.id }) },
            )
        }

        click(homeNotificationsSelectButtonTag)
        tag(homeNotificationsSelectAllTag).assertExists()
        compose.onTooltip("全选当前通知").assertExists()
        tag(notificationSelectBoxTag("invite:invite_batch_first")).assertExists()

        click(notificationSelectBoxTag("invite:invite_batch_first"))
        click(notificationContextRowTag("invite:invite_batch_second"))

        tag(notificationContextRowTag("invite:invite_batch_second")).performMouseInput { rightClick() }
        compose.waitForIdle()
        assertEquals(0, count(hasText("复制")))
        compose.onNodeWithText("删除").assertExists()

        compose.onNodeWithText("删除").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("删除选中的通知").assertExists()
        compose.onNodeWithText("确定删除所有选中的2条通知？").assertExists()

        compose.onNode(hasText("删除") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(listOf(listOf("invite:invite_batch_second", "invite:invite_batch_first")), deletedBatches)
        compose.onNodeWithText("已删除 2 条通知").assertExists()
        assertTrue(count(hasTooltip("全选当前通知")) + count(hasTooltip("取消全选当前通知")) >= 1)
    }
}
