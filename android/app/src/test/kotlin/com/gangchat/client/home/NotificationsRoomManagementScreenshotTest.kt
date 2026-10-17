package com.gangchat.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.gangchat.client.app.kRoomEventNotificationMemberRemoved
import com.gangchat.client.app.kRoomEventNotificationMentioned
import com.gangchat.client.app.kRoomEventNotificationRolePromoted
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomApplication
import com.gangchat.client.protocol.RoomEventNotification
import com.gangchat.client.protocol.RoomInvite
import com.gangchat.client.protocol.RoomMembership
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.installTestImageLoader
import com.gangchat.client.ui.hasTooltip
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.Text
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val out = "build/outputs/roborazzi"
private const val tablet = "w1280dp-h800dp-mdpi"

private fun _user(id: String, username: String, displayName: String, avatarKey: String, role: String? = null) =
    UserSummary(id = id, username = username, displayName = displayName, avatarUrl = null, defaultAvatarKey = avatarKey, roomRole = role)

private fun _publicRoom(id: String, rid: String, name: String, avatarKey: String) =
    PublicRoom(
        id = id,
        rid = rid,
        name = name,
        avatarUrl = null,
        defaultAvatarKey = avatarKey,
        visibility = "public",
        joinPolicy = "approval_required",
        memberCount = 8,
        onlineMemberCount = 3,
        liveParticipantCount = 0,
        joined = true,
        joinState = "joined",
        myMembership = RoomMembership(joinedAt = DateTime.utc(2026, 6, 1), role = "member"),
    )

/**
 * Self-check screenshots (phone and tablet) of the notification centre and
 * the room settings / members panels; compare with the Dart layout code and
 * docs/js-port-screenshots/{04_notifications,06_members,07_room_settings}.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class NotificationsRoomManagementScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun installLoader() = installTestImageLoader()

    private val currentUser: CurrentUser = roomManagementTestCurrentUser()

    private val alice = _user("u_alice", "alice", "Alice", "pink-2", role = "member")
    private val carol = _user("u_carol", "carol", "Carol", "violet-2", role = "member")
    private val ah = _user("u_ah", "afang", "阿方", "orange-2", role = "member")
    private val ivan = _user("u_ivan", "ivan", "Ivan", "blue-3", role = "member")
    private val reading = _publicRoom("room_read", "R1001", "读书会", "brown-2")
    private val photo = _publicRoom("room_photo", "R1002", "摄影爱好者", "rose-2")
    private val dev = _publicRoom("room_dev", "R1003", "开发频道", "green-2")
    private val old = _publicRoom("room_old", "R1004", "旧项目组", "olive-2")
    private val flutter = _publicRoom("room_flutter", "R1005", "Flutter 交流", "cyan-2")

    private val invites =
        listOf(RoomInvite(id = "inv_1", status = "pending", room = reading, inviter = alice, createdAt = DateTime.utc(2026, 10, 16, 5, 16)))

    private val applications =
        listOf(
            RoomApplication(
                id = "app_1",
                status = "pending",
                room = photo,
                createdAt = DateTime.utc(2026, 10, 16, 2, 16),
                updatedAt = DateTime.utc(2026, 10, 16, 2, 16),
            ),
            RoomApplication(
                id = "app_2",
                status = "rejected",
                room = old,
                createdAt = DateTime.utc(2026, 10, 12, 7, 16),
                updatedAt = DateTime.utc(2026, 10, 14, 7, 16),
                reviewedAt = DateTime.utc(2026, 10, 14, 7, 16),
                reviewer = ah,
            ),
        )

    private val roomNotifications =
        listOf(
            RoomEventNotification(
                id = "ev_mention",
                type = kRoomEventNotificationMentioned,
                room = dev,
                actor = carol,
                createdAt = DateTime.utc(2026, 10, 16, 4, 16),
                messageId = "m1",
                messagePreview = "@Demo 今晚八点开会，记得带上设计稿",
            ),
            RoomEventNotification(
                id = "ev_promoted",
                type = kRoomEventNotificationRolePromoted,
                room = dev,
                actor = alice,
                createdAt = DateTime.utc(2026, 10, 11, 7, 16),
                fromRole = "member",
                toRole = "admin",
                readAt = DateTime.utc(2026, 10, 11, 8, 0),
            ),
            RoomEventNotification(
                id = "ev_removed",
                type = kRoomEventNotificationMemberRemoved,
                room = flutter,
                actor = ivan,
                createdAt = DateTime.utc(2026, 9, 26, 7, 16),
                readAt = DateTime.utc(2026, 9, 27, 8, 0),
            ),
        )

    /** Hosts [content] like the home shell: full screen on phones, right of the sidebar on wide windows. */
    private fun host(content: @Composable (BuildContext) -> Unit) {
        compose.setHomeContent { context ->
            if (HomeAdaptiveLayout.usesCompactLayout(context)) {
                Box(Modifier.fillMaxSize()) { content(context) }
            } else {
                Row(Modifier.fillMaxSize()) {
                    Spacer(Modifier.width(sidebarWidth))
                    Box(Modifier.weight(1f).fillMaxHeight()) { content(context) }
                }
            }
        }
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        compose.pump(400)
    }

    private fun capture(name: String) {
        settle()
        compose.onRoot().captureRoboImage("$out/$name.png")
    }

    private fun hostNotifications() =
        host { _ ->
            HomeNotificationsPane(
                invites = invites,
                applications = applications,
                roomNotifications = roomNotifications,
                loading = false,
                error = null,
                busyInviteId = null,
                busyApplicationId = null,
                currentUser = currentUser,
                onClose = {},
                onRefresh = {},
                onReviewInvite = { _, _ -> },
                onWithdrawApplication = {},
                onOpenRoom = {},
                onOpenRoomEvent = {},
                onCopyNotification = {},
                onDeleteNotification = {},
                onDeleteNotifications = {},
            )
        }

    private fun notifications(suffix: String) {
        hostNotifications()
        capture("notifications_list_$suffix")

        compose.onNodeWithTag(homeNotificationsDateFilterButtonTag, useUnmergedTree = true).performClick()
        capture("notifications_date_range_$suffix")

        compose.onNode(hasAnyAncestor(hasTestTag(notificationStartDateFieldTag)) and hasClickAction()).performClick()
        capture("notifications_calendar_$suffix")
    }

    private fun notificationsSelection(suffix: String) {
        hostNotifications()
        compose.onNodeWithTag(homeNotificationsSelectButtonTag, useUnmergedTree = true).performClick()
        compose.onNodeWithTag(notificationSelectBoxTag("invite:inv_1"), useUnmergedTree = true).performClick()
        capture("notifications_selection_$suffix")
    }

    private fun hostSettings(api: RoomManagementTestApi) =
        host { _ ->
            RoomSettingsDialog(
                controller = api.controller,
                room = api.room(),
                currentUser = currentUser,
                isInLive = false,
                onLeaveLive = {},
                onRoomUpdated = {},
                embedded = true,
                messageHistoryBuilder = { _ -> Text("消息记录面板") },
                musicPlaylistsBuilder = { _ -> Text("房间歌单面板") },
            )
        }

    private fun roomSettings(suffix: String) {
        val api = RoomManagementTestApi()
        hostSettings(api)
        capture("room_settings_info_$suffix")

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("删除房间") and hasClickAction())
        capture("room_settings_info_bottom_$suffix")

        compose.onAllNodesWithText("个人偏好")[0].performClick()
        capture("room_settings_preferences_$suffix")
    }

    private fun hostMembers(api: RoomManagementTestApi) =
        host { _ ->
            RoomMembersDialog(
                controller = api.controller,
                room = api.room(),
                currentUser = currentUser,
                initialLive = api.room().live,
                embedded = true,
            )
        }

    private fun members(suffix: String) {
        val api =
            RoomManagementTestApi(
                includeActionComparisonMember = true,
                blacklistUsers =
                    listOf(
                        roomManagementUserJson("user-8", "spammer", "Spam Bot", uid = "uid-8"),
                        roomManagementUserJson("user-9", "troll", "Troll", uid = "uid-9"),
                    ),
            )
        hostMembers(api)
        capture("room_members_list_$suffix")

        compose.onAllNodes(hasParent(hasTooltip("踢出此用户")), useUnmergedTree = true)[0].performClick()
        capture("room_members_kick_confirm_$suffix")
        compose.onNode(hasText("取消") and hasClickAction()).performClick()
        settle()

        compose.onAllNodes(hasParent(hasTooltip("修改房间内用户名")), useUnmergedTree = true)[0].performClick()
        capture("room_members_rename_$suffix")
        compose.onNode(hasText("取消") and hasClickAction()).performClick()
        settle()

        compose.onNodeWithText("新成员").performClick()
        settle()
        compose.onNode(hasSetTextAction()).performTextInput("ri")
        compose.pump(320)
        capture("room_members_invite_$suffix")

        compose.onNodeWithText("黑名单").performClick()
        capture("room_members_blacklist_$suffix")
    }

    @Test
    fun `phone notifications`() = notifications("phone")

    @Test
    @Config(qualifiers = tablet)
    fun `tablet notifications`() = notifications("tablet")

    @Test
    fun `phone notification selection`() = notificationsSelection("phone")

    @Test
    @Config(qualifiers = tablet)
    fun `tablet notification selection`() = notificationsSelection("tablet")

    @Test
    fun `phone room settings`() = roomSettings("phone")

    @Test
    @Config(qualifiers = tablet)
    fun `tablet room settings`() = roomSettings("tablet")

    @Test
    fun `phone room members`() = members("phone")

    @Test
    @Config(qualifiers = tablet)
    fun `tablet room members`() = members("tablet")
}
