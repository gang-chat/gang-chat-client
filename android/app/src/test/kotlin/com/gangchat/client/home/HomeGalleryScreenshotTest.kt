package com.gangchat.client.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.MusicTrackPreviewAsset
import com.gangchat.client.app.MusicTrackPreviewController
import com.gangchat.client.app.MusicTrackPreviewPlatform
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.DownloadedFile
import com.gangchat.client.protocol.LastMessagePreview
import com.gangchat.client.protocol.MusicTrackPreviewApi
import com.gangchat.client.protocol.PersonalMusicPlaylist
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.RoomMembership
import com.gangchat.client.protocol.RoomPersonalProfile
import com.gangchat.client.protocol.UserCommonRoom
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.installTestImageLoader
import com.gangchat.client.ui.setUiContent
import com.gangchat.client.ui.solidPng
import com.gangchat.client.ui.testMediaCacheController
import com.gangchat.client.widgets.Text
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val out = "build/outputs/roborazzi"

/** Part 2 galleries: sidebar (wide + compact), profile cards, music cards, image preview. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class HomeGalleryScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun installLoader() = installTestImageLoader()

    private val now = DateTime.utc(2026, 6, 10, 15, 20)

    private val currentUser =
        CurrentUser(
            id = "user_demo",
            uid = "10000001",
            username = "demo",
            displayName = "Demo",
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

    private fun room(
        id: String,
        name: String,
        key: String,
        sender: String,
        body: String,
        hour: Int,
        minute: Int,
        unread: Int = 0,
        mentions: Int = 0,
        live: Int = 0,
        pinned: Boolean = false,
        pendingJoin: Boolean = false,
    ): RoomCard {
        val at = DateTime.utc(2026, 6, 10, hour, minute)
        return RoomCard(
            id = id,
            name = name,
            avatarUrl = null,
            defaultAvatarKey = key,
            memberCount = 10,
            liveParticipantCount = live,
            liveAvatarPreview = emptyList(),
            lastMessage = LastMessagePreview(id = "m_$id", senderDisplayName = sender, bodyPreview = body, createdAt = at),
            unreadCount = unread,
            hasUnreadCount = unread > 0,
            unreadMentionCount = mentions,
            hasUnreadMentionCount = mentions > 0,
            updatedAt = at,
            isPinned = pinned,
            hasPendingJoinRequests = pendingJoin,
        )
    }

    private val rooms =
        listOf(
            room("r1", "Gang 总部", "blue-3", "大卫", "我把新的设计稿发到群文件了", 15, 17, live = 3, pinned = true, pendingJoin = true),
            room("r2", "游戏开黑", "olive-2", "Ivan", "谁开个房间", 14, 26, unread = 2, live = 1),
            room("r3", "开发频道", "green-2", "Alice", "+1", 14, 9, unread = 7, mentions = 1),
            room("r4", "闲聊水群", "orange-2", "大卫", "推荐一部电影吧", 14, 3, unread = 3),
            room("r5", "音乐电台", "red-2", "Grace", "点了一首《平凡之路》", 14, 32),
            room("r6", "设计组", "purple-2", "Carol", "[语音]", 14, 3),
        )

    @Composable
    private fun Sidebar(width: androidx.compose.ui.unit.Dp) {
        HomeSidebar(
            width = width,
            currentUser = currentUser,
            servers = rooms,
            timestampNow = now,
            selectedServerId = "r1",
            joinedLiveRoomId = null,
            realtimeReconnecting = false,
            searchQuery = "",
            loading = false,
            error = null,
            settingsActive = false,
            createRoomActive = false,
            notificationsActive = false,
            logoutActive = false,
            hasPendingNotifications = true,
            pendingNotificationCount = 3,
            onServerSelected = {},
            onCreateRoom = {},
            onOpenNotifications = {},
            onOpenSettings = {},
            onLogout = {},
            roomDrafts = mapOf("r6" to "晚上评审\n记得带稿子"),
            requestRoundTrip = 42.milliseconds,
            includeWindowChromeOffset = false,
            modifier = Modifier.fillMaxHeight(),
        )
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun sidebarWide() {
        compose.setUiContent { _ ->
            Row(Modifier.fillMaxSize()) {
                Sidebar(sidebarWidth)
                Box(Modifier.fillMaxSize().background(UiColors.background))
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/home_sidebar_wide.png")
    }

    @Test
    fun sidebarCompact() {
        compose.setUiContent { _ -> Sidebar(411.dp) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/home_sidebar_compact.png")
    }

    private val profileUser =
        UserSummary(
            id = "u1",
            username = "logan",
            displayName = "加一",
            avatarUrl = null,
            defaultAvatarKey = "purple-2",
            uid = "10001",
            bio = "随便写点什么，这是一段比较长的个人简介，用来检查换行。",
            gender = "male",
            roomRole = "admin",
            isOnline = true,
            commonRooms =
                listOf(
                    UserCommonRoom(id = "r1", rid = "R1", name = "摸鱼大队"),
                    UserCommonRoom(id = "r2", rid = "R2", name = "技术交流"),
                    UserCommonRoom(id = "r3", rid = "R3", name = "这是一个需要完整显示的共同房间名称"),
                ),
        )

    private val profileRoom =
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
            createdBy = UserSummary(id = "u_creator", username = "creator", displayName = "Room Creator", avatarUrl = null, defaultAvatarKey = "blue-3"),
            personalProfile = RoomPersonalProfile(displayName = "Current In Room"),
            myMembership = RoomMembership(joinedAt = DateTime.utc(2026, 6, 1), role = "admin"),
        )

    @Test
    fun userProfileCard() {
        compose.setHomeContent { _ ->
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopStart) {
                AvatarHoverCardForTest(user = profileUser, currentUser = avatarHoverCardTestCurrentUser)
            }
        }
        compose.onNodeWithTag(messageAvatarTag).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/home_user_profile_card.png")

        // Nested room card from the first common room.
        compose.onNode(hasText("摸鱼") and hasAnyAncestor(hasTestTag(hoverCardPanelTag)), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/home_user_profile_card_nested_room.png")
    }

    @Test
    fun roomProfileCard() {
        compose.setHomeContent { _ ->
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopStart) {
                RoomHoverCardForTest(room = profileRoom, currentUser = avatarHoverCardTestCurrentUser, modifier = Modifier.testTag("room-avatar"), onEnterRoom = {})
            }
        }
        compose.onNodeWithTag("room-avatar").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/home_room_profile_card.png")
    }

    @Test
    fun deletedProfileCards() {
        compose.setHomeContent { _ ->
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(260.dp)) {
                AvatarHoverCardForTest(user = profileUser.copyWith(isDeleted = true))
                RoomHoverCardForTest(room = profileRoom.copyWith(isDeleted = true), currentUser = avatarHoverCardTestCurrentUser, modifier = Modifier.testTag("room-avatar"))
            }
        }
        compose.onNodeWithTag(messageAvatarTag).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/home_deleted_user_card.png")
        // The tap on the room avatar is outside the user card, which closes.
        compose.onNodeWithTag("room-avatar").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/home_deleted_room_card.png")
    }

    private fun previewController() = MusicTrackPreviewController(api = _GalleryPreviewApi(), platform = _GalleryPreviewPlatform())

    @Test
    fun musicTrackCard() {
        val controller = previewController()
        compose.setHomeContent { _ ->
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopStart) {
                MusicTrackHoverCard(
                    data =
                        MusicTrackCardData(
                            id = "item-1",
                            source = "netease",
                            trackId = "track-1",
                            title = "平凡之路（电影《后会无期》主题曲）",
                            artists = listOf("朴树"),
                            durationMs = 301000,
                        ),
                    previewController = controller,
                    playlists = listOf(PersonalMusicPlaylist(id = "p1", name = "我的收藏", description = "", revision = 1, itemCount = 12, createdAt = null, updatedAt = null)),
                    onAddToPlaylist = {},
                ) {
                    Box(Modifier.size(240.dp, 56.dp).testTag("track-row")) { Text("平凡之路") }
                }
            }
        }
        compose.onNodeWithTag("track-row").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/home_music_track_card.png")

        compose.onNodeWithTag(musicTrackCardAddToPlaylistTag).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/home_music_track_playlist_picker.png")
    }

    @Test
    fun musicPlaylistCard() {
        compose.setHomeContent { _ ->
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopStart) {
                MusicPlaylistHoverCard(
                    data =
                        MusicPlaylistCardData(
                            id = "pl-1",
                            name = "周五晚上的歌单",
                            songCount = 24,
                            createdAt = DateTime.utc(2026, 6, 1, 20, 30),
                            creator = UserSummary(id = "u2", username = "grace", displayName = "Grace", avatarUrl = null, defaultAvatarKey = "red-2"),
                            room = profileRoom,
                            showPlayingStatus = true,
                        ),
                    onPlayAll = {},
                    onViewPlaylist = {},
                    currentUser = avatarHoverCardTestCurrentUser,
                ) {
                    Box(Modifier.size(240.dp, 56.dp).testTag("playlist-row")) { Text("周五晚上的歌单") }
                }
            }
        }
        compose.onNodeWithTag("playlist-row").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/home_music_playlist_card.png")
    }

    @Test
    fun musicPlaylistSnapshotDialog() {
        compose.setHomeContent { context ->
            Text(
                "打开",
                modifier =
                    Modifier.clickable {
                        unawaited {
                            com.gangchat.client.widgets.showDialog<Unit>(context) { _ ->
                                MusicPlaylistSnapshotDialog(
                                    title = "共享歌单",
                                    tracks =
                                        listOf(
                                            MusicTrackCardData("t1", "netease", "1", "第一首完整歌曲", listOf("歌手"), 180000),
                                            MusicTrackCardData("t2", "bilibili", "BV1xx", "一首很长很长很长很长很长很长的哔哩哔哩视频标题", listOf("UP主"), 0),
                                            MusicTrackCardData("t3", "qq", "3", "Third Song", listOf("Artist A", "Artist B"), 245000),
                                        ),
                                    previewApi = _GalleryPreviewApi(),
                                    previewPlatformFactory =
                                        object : com.gangchat.client.app.MusicTrackPreviewPlatformFactory {
                                            override fun create(): MusicTrackPreviewPlatform = _GalleryPreviewPlatform()
                                        },
                                    loadPersonalPlaylists = {
                                        com.gangchat.client.protocol.PersonalMusicPlaylistPage(
                                            playlists = emptyList(),
                                            page = 1,
                                            pageSize = 50,
                                            total = 0,
                                            hasMore = false,
                                            maxPlaylists = 50,
                                            maxPlaylistItems = 500,
                                        )
                                    },
                                    onAddToPlaylist = { _, _ -> },
                                    onClone = {
                                        PersonalMusicPlaylist(id = "c", name = "共享歌单", description = "", revision = 1, itemCount = 3, createdAt = null, updatedAt = null)
                                    },
                                )
                            }
                        }
                    },
            )
        }
        compose.onNodeWithText("打开").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/home_music_playlist_snapshot_dialog.png")
    }

    @Test
    fun imagePreview() {
        val url = "https://example.invalid/wide.png"
        val cache = testMediaCacheController(mapOf(url to solidPng(240, 120, 0xFF26B36F.toInt())))
        compose.setHomeContent(cache = cache) { context ->
            val actions =
                ChatImagePreviewActions(onDownload = { _, _ -> }, onSaveAs = { _, _ -> }, onCopyToClipboard = { _ -> }, mediaCache = cache)
            ChatImagePreviewActionsScope(actions = actions) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(label = "Alice", defaultAvatarKey = "pink-2", size = 32.dp)
                        Text("Alice: 看看这张图")
                    }
                    Text(
                        "open",
                        modifier =
                            Modifier.clickable {
                                unawaited {
                                    showChatImagePreview(
                                        context,
                                        imageUrl = url,
                                        suggestedName = "wide.png",
                                        actions = actions,
                                        orientationController = com.gangchat.client.shell.AppOrientationController(setPreferredOrientations = { _ -> }),
                                        displayRotationReader = { 0 },
                                    )
                                }
                            },
                    )
                }
            }
        }
        // Robolectric draws only on capture; the preview freezes the drawn page.
        compose.onRoot().captureToImage()
        compose.onNodeWithText("open").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(chatImagePreviewTransformTag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        repeat(20) {
            Thread.sleep(30)
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage("$out/home_image_preview.png")
    }
}

private class _GalleryPreviewApi : MusicTrackPreviewApi {
    override suspend fun downloadMusicTrackPreview(source: String, trackId: String): DownloadedFile =
        DownloadedFile(bytes = byteArrayOf(1, 2, 3), filename = "preview.m4a", mimeType = "audio/mp4")
}

private class _GalleryPreviewPlatform : MusicTrackPreviewPlatform {
    private val _completed: StreamController<Unit> = StreamController.broadcast()

    override val onCompleted: Stream<Unit> get() = _completed.stream

    override suspend fun findCached(cacheKey: String): MusicTrackPreviewAsset? = null

    override suspend fun store(cacheKey: String, bytes: ByteArray): MusicTrackPreviewAsset = MusicTrackPreviewAsset(cacheKey)

    override suspend fun play(asset: MusicTrackPreviewAsset) {}

    override suspend fun stop() {}

    override suspend fun dispose() = _completed.close()
}
