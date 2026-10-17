package com.gangchat.client.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.MusicTrackPreviewAsset
import com.gangchat.client.app.MusicTrackPreviewPlatform
import com.gangchat.client.app.MusicTrackPreviewPlatformFactory
import com.gangchat.client.app.PersonalMusicPlaylistsController
import com.gangchat.client.app.SettingsController
import com.gangchat.client.app.SettingsSection
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.home.musicTrackCardAddToPlaylistTag
import com.gangchat.client.home.musicTrackCardPreviewTag
import com.gangchat.client.home.musicTrackPlaylistTargetConfirmTag
import com.gangchat.client.live.SystemAudioDevices
import com.gangchat.client.protocol.ApiException
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.DownloadedFile
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.MusicBoxSearchResult
import com.gangchat.client.protocol.MusicTrackPreviewApi
import com.gangchat.client.protocol.PersonalMusicPlaylist
import com.gangchat.client.protocol.PersonalMusicPlaylistApi
import com.gangchat.client.protocol.PersonalMusicPlaylistBatchAddApi
import com.gangchat.client.protocol.PersonalMusicPlaylistBatchAddResult
import com.gangchat.client.protocol.PersonalMusicPlaylistItem
import com.gangchat.client.protocol.PersonalMusicPlaylistItemsPage
import com.gangchat.client.protocol.PersonalMusicPlaylistMergeApi
import com.gangchat.client.protocol.PersonalMusicPlaylistMergeResult
import com.gangchat.client.protocol.PersonalMusicPlaylistPage
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomMembership
import com.gangchat.client.protocol.RoomMusicPlaylistApi
import com.gangchat.client.protocol.RoomMusicPlaylistBatchAddApi
import com.gangchat.client.protocol.RoomMusicPlaylistCloneApi
import com.gangchat.client.protocol.RoomMusicPlaylistImportApi
import com.gangchat.client.protocol.RoomMusicPlaylistMergeApi
import com.gangchat.client.protocol.RoomPage
import com.gangchat.client.protocol.StickerPackStore
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.shell.DefaultMusicTrackPreviewPlatformFactory
import com.gangchat.client.shell.ShellTestLoop
import com.gangchat.client.shell.enterText
import com.gangchat.client.shell.field
import com.gangchat.client.shell.fieldText
import com.gangchat.client.shell.pump
import com.gangchat.client.shell.pumpAndSettle
import com.gangchat.client.shell.rect
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.didExceedMaxLines
import com.gangchat.client.ui.hasTooltip
import com.gangchat.client.ui.installTestImageLoader
import com.gangchat.client.ui.onAllTooltips
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.settingsSideNavigationTag
import com.gangchat.client.ui.textColor
import com.gangchat.client.ui.textLayout
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.IconSemanticsKey
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val size360x800 = "w360dp-h800dp-mdpi"
private const val size360x900 = "w360dp-h900dp-mdpi"
private const val size600x900 = "w600dp-h900dp-mdpi"
private const val size720x600 = "w720dp-h600dp-mdpi"
private const val size720x800 = "w720dp-h800dp-mdpi"
private const val size720x900 = "w720dp-h900dp-mdpi"
private const val size1100x800 = "w1100dp-h800dp-mdpi"

/** Dart's `_playlistSettingsCurrentUser`. */
private val playlistSettingsCurrentUser =
    CurrentUser(
        id = "user_1",
        uid = "100001",
        username = "tester",
        displayName = "测试用户",
        bio = "",
        gender = "secret",
        email = null,
        emailPublic = false,
        phoneNumber = null,
        phoneNumberPublic = false,
        avatarUrl = null,
        defaultAvatarKey = "blue-1",
        isSuperuser = false,
        createdAt = null,
    )

/**
 * `test/widget/settings/personal_music_playlists_widget_test.dart`.
 *
 * Flutter's default test platform is Android, so the Dart cases without a
 * platform override already ran the Android behaviour; the cases looped over
 * Windows/macOS/Android keep their Android variant. Skipped as desktop-only:
 * `desktop mouse opens an empty playlist after visiting another playlist`
 * (mouse drag-selection inside a `SelectionArea`) and the Windows/macOS
 * variants of the looped cases. The hover case keeps its colour and spacing
 * checks but not the desktop mouse-cursor assertions.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = size720x900, shadows = [ShadowNoOpMagnifier::class])
class PersonalMusicPlaylistsWidgetTest {
    @get:Rule
    val compose = createComposeRule()

    private val loop = ShellTestLoop()

    private var viewInsets by mutableStateOf(EdgeInsets.zero)

    @Before
    fun setUp() {
        loop.start()
        installTestImageLoader()
    }

    @After
    fun tearDown() = loop.finish()

    /** Dart's `_pumpPlaylistSettings` (plus the platform fakes Robolectric needs). */
    private fun pumpPlaylistSettings(
        api: FakePersonalPlaylistApi,
        previewPlatformFactory: MusicTrackPreviewPlatformFactory? = null,
        initialMusicPlaylistId: String? = null,
        currentUser: CurrentUser? = playlistSettingsCurrentUser,
    ) {
        compose.mainClock.autoAdvance = false
        val controller = SettingsController(api = null, apiBaseUrl = "", stickerPackStore = StickerPackStore())
        val factory = previewPlatformFactory ?: DefaultMusicTrackPreviewPlatformFactory()
        compose.setSettingsContent(viewInsets = { viewInsets }) { _ ->
            SettingsPage(
                initialSection = SettingsSection.playlists,
                initialMusicPlaylistId = initialMusicPlaylistId,
                api = api,
                controller = controller,
                onClose = {},
                currentUser = currentUser,
                musicTrackPreviewPlatformFactory = factory,
                systemAudioDevices = SystemAudioDevices(supported = false),
                audioDeviceStore = FakeSettingsAudioDeviceStore(),
                audioDeviceService = FakeSettingsAudioDeviceService(),
                androidSystemService = FakeSettingsAndroidSystemService(),
                autoUpdatePromptStore = FakeSettingsAutoUpdatePromptStore(),
                installInfoService = FakeSettingsInstallInfoService(null),
                closeBehaviorStore = MemorySettingsCloseBehaviorStore(),
                languageStore = MemorySettingsLanguageStore(),
            )
        }
        compose.pumpAndSettle()
    }

    /** Dart's `Scaffold(body: MusicPlaylistsPanel(...))` for the room panels. */
    private fun pumpRoomPanel(
        controller: PersonalMusicPlaylistsController,
        previewApi: MusicTrackPreviewApi? = null,
        previewPlatformFactory: MusicTrackPreviewPlatformFactory? = null,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setSettingsContent { _ ->
            Box(Modifier.fillMaxSize().background(UiColors.background), propagateMinConstraints = true) {
                MusicPlaylistsPanel(
                    controller = controller,
                    title = "房间歌单",
                    unavailableMessage = "房间歌单暂不可用",
                    previewApi = previewApi,
                    previewPlatformFactory = previewPlatformFactory,
                )
            }
        }
        compose.pumpAndSettle()
    }

    private fun roomController(
        roomApi: FakeRoomPlaylistApi,
        canManage: Boolean,
        personalApi: PersonalMusicPlaylistApi? = null,
    ) = PersonalMusicPlaylistsController.room(
        roomApi = roomApi,
        personalApi = personalApi,
        roomId = "room_1",
        canManage = canManage,
        searchTracks = { _, _ -> emptyList() },
    )

    private fun tapText(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.pumpAndSettle()
    }

    private fun tapTag(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.pumpAndSettle()
    }

    /** Dart's `tester.tapAt(tester.getTopLeft(finder) + Offset(dx, dy))`. */
    private fun tapTopLeft(
        tag: String,
        dx: Float = 2f,
        dy: Float = 2f,
    ) {
        compose.onNodeWithTag(tag).performTouchInput { click(Offset(dx.dp.toPx(), dy.dp.toPx())) }
        compose.pump()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun tooltipCount(
        message: String,
        insideTag: String? = null,
    ): Int {
        val matcher = if (insideTag == null) hasTooltip(message) else hasTooltip(message) and hasAnyAncestor(hasTestTag(insideTag))
        return compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size
    }

    private fun hasIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) = SemanticsMatcher.expectValue(IconSemanticsKey, icon)

    /** The text field of the open dialog (the page's own search field excluded). */
    private fun dialogField() = compose.onNode(hasSetTextAction() and !hasAnyAncestor(hasTestTag(settingsSearchInputTag)))

    private fun openTrackSearch() {
        tapText("夜晚")
        tapTag(searchAddPersonalMusicPlaylistItemTag)
    }

    private fun cardBackground(tag: String): Int {
        val card = compose.onNodeWithTag(tag)
        val height = card.fetchSemanticsNode().size.height / compose.density.density
        return compose.pixelAt(card, 6f, height / 2).toArgb()
    }

    private fun personalPlaylistSettingsRemainUsable(narrow: Boolean) {
        val api = FakePersonalPlaylistApi()
        pumpPlaylistSettings(api, currentUser = null)

        val playlistsTile =
            compose.onNode(hasText("我的歌单") and hasAnyAncestor(hasTestTag(settingsSideNavigationTag)), useUnmergedTree = true)
        if (narrow) {
            // Narrow settings pages show the numbered section tiles as a
            // horizontally scrolling strip; the open section stays reachable
            // there.
            playlistsTile.performScrollTo()
            compose.pump()
        }
        assertTrue(compose.hasTextNode("我的歌单"))
        playlistsTile.assertExists()
        // The playlists section is the selected navigation tile.
        assertEquals(UiColors.onInverse, playlistsTile.textColor())
        assertEquals(1, compose.textCount("歌单管理"))
        assertEquals(1, compose.textCount("夜晚"))
        assertEquals(1, compose.textCount("新建歌单"))
        assertEquals(1, compose.textCount("管理"))
        assertEquals(1, compose.textCount("筛选"))
        assertEquals(0, compose.textCount("删除"))
        assertTrue(exists(createPersonalMusicPlaylistTag))

        tapText("夜晚")

        assertEquals(1, compose.textCount("搜索添加"))
        assertEquals(0, compose.textCount("管理歌曲"))
        assertEquals(1, compose.textCount("管理"))
        assertEquals(1, compose.textCount("筛选"))
        assertEquals(1, compose.textCount("晴天"))
        assertTrue(compose.hasTextNode("周杰伦", substring = true))
        // Dart: `find.byType(ui.MusicPlaylistTrackSurface)`.
        assertTrue(exists(personalMusicPlaylistItemTag("mbpi_1")))
    }

    @Test
    @Config(qualifiers = size360x800)
    fun `personal playlist settings remain usable at 360 px`() = personalPlaylistSettingsRemainUsable(narrow = true)

    @Test
    @Config(qualifiers = size1100x800)
    fun `personal playlist settings remain usable at 1100 px`() = personalPlaylistSettingsRemainUsable(narrow = false)

    @Test
    fun `playlist settings can open an exact playlist directly`() {
        val api = FakePersonalPlaylistApi()
        pumpPlaylistSettings(api, initialMusicPlaylistId = "mbp_1", currentUser = null)

        assertTrue(exists(personalMusicPlaylistHeaderTag))
        assertEquals(1, compose.textCount("搜索添加"))
        assertEquals(1, compose.textCount("晴天"))
    }

    @Test
    fun `empty playlist opens on the first tap while summaries refresh`() {
        val api = FakePersonalPlaylistApi(playlists = listOf(FakePersonalPlaylistApi.playlist, FakePersonalPlaylistApi.emptyPlaylist))
        pumpPlaylistSettings(api)

        tapText("夜晚")
        assertEquals(1, compose.textCount("晴天"))
        tapTag(backToPersonalMusicPlaylistsTag)

        val pendingRefresh = CompletableDeferred<PersonalMusicPlaylistPage>()
        api.nextPlaylistListResponse = pendingRefresh
        compose.onTooltip("刷新设置").performClick()
        compose.pump()
        compose.pump()
        assertEquals(2, api.listRequestCount)
        assertEquals(1, compose.textCount("空歌单"))

        compose.onNodeWithText("空歌单").performClick()
        compose.pump()
        assertEquals(1, compose.textCount("搜索添加"))
        assertEquals(1, compose.textCount("歌单还是空的"))

        pendingRefresh.complete(api.playlistPage())
        compose.pumpAndSettle()
    }

    @Test
    fun `the whole playlist card opens on the first tap`() {
        val api = FakePersonalPlaylistApi(playlists = listOf(FakePersonalPlaylistApi.emptyPlaylist))
        pumpPlaylistSettings(api)

        tapTopLeft(personalMusicPlaylistCardTag("mbp_empty"), 3f, 3f)
        compose.pumpAndSettle()

        assertEquals(1, compose.textCount("搜索添加"))
        assertEquals(1, compose.textCount("歌单还是空的"))
    }

    @Test
    @Config(qualifiers = size360x900)
    fun `playlist detail header places back, icon, and name in one row`() {
        val api = FakePersonalPlaylistApi()
        pumpPlaylistSettings(api)
        tapText("夜晚")

        val header = hasAnyAncestor(hasTestTag(personalMusicPlaylistHeaderTag))
        val back = compose.onNode(hasTestTag(backToPersonalMusicPlaylistsTag) and header)
        val playlistIcon = compose.onNode(hasIcon(Icons.AutoMirrored.Outlined.QueueMusic) and header, useUnmergedTree = true)
        val playlistName = compose.onNode(hasText("夜晚") and header, useUnmergedTree = true)

        assertTrue(exists(personalMusicPlaylistHeaderTag))
        back.assertExists()
        // Dart: `isA<ui.ButtonIconPlain>()` — a plain icon button with a tooltip.
        assertEquals(1, tooltipCount("返回歌单列表", insideTag = personalMusicPlaylistHeaderTag))
        playlistIcon.assertExists()
        playlistName.assertExists()
        val backRect = back.rect()
        val iconRect = playlistIcon.rect()
        val nameRect = playlistName.rect()
        assertTrue((backRect.left + backRect.right) / 2 < (iconRect.left + iconRect.right) / 2)
        assertTrue((iconRect.left + iconRect.right) / 2 < (nameRect.left + nameRect.right) / 2)
        assertEquals(0, compose.textCount("返回歌单列表"))
    }

    @Test
    @Config(qualifiers = size720x600)
    fun `playlist list and detail keep controls while content scrolls`() {
        val playlists =
            List(16) { index ->
                PersonalMusicPlaylist(
                    id = if (index == 0) "mbp_1" else "sticky_playlist_$index",
                    name = "列表 $index",
                    description = "",
                    revision = 1,
                    itemCount = 16,
                    createdAt = null,
                    updatedAt = null,
                )
            }
        val playlistItems =
            List(16) { index ->
                PersonalMusicPlaylistItem(
                    id = "sticky_item_$index",
                    playlistId = "mbp_1",
                    trackId = "track_$index",
                    source = "netease",
                    title = "歌曲 $index",
                    artists = listOf("歌手"),
                    durationMs = 180000,
                    sortOrder = (index + 1) * 10,
                    createdAt = null,
                )
            }
        val api = FakePersonalPlaylistApi(playlists = playlists, playlistItems = playlistItems)
        pumpPlaylistSettings(api)

        val playlistScroll = compose.onNodeWithTag(personalMusicPlaylistsScrollTag)
        val listTitleTop = compose.onNodeWithText("歌单管理").rect().top
        val createButtonTop = compose.onNodeWithTag(createPersonalMusicPlaylistTag).rect().top
        playlistScroll.performTouchInput { swipeUp(startY = centerY, endY = centerY - 300.dp.toPx()) }
        compose.pumpAndSettle()
        assertEquals(listTitleTop, compose.onNodeWithText("歌单管理").rect().top)
        assertEquals(createButtonTop, compose.onNodeWithTag(createPersonalMusicPlaylistTag).rect().top)
        assertTrue(scrollOffset(personalMusicPlaylistsScrollTag) > 0f)

        playlistScroll.performScrollToIndex(0)
        compose.pumpAndSettle()
        tapText("列表 0")

        val detailHeaderTop = compose.onNodeWithTag(personalMusicPlaylistHeaderTag).rect().top
        val searchButtonTop = compose.onNodeWithTag(searchAddPersonalMusicPlaylistItemTag).rect().top
        compose.onNodeWithTag(personalMusicPlaylistItemsScrollTag).performTouchInput {
            swipeUp(startY = centerY, endY = centerY - 300.dp.toPx())
        }
        compose.pumpAndSettle()
        assertEquals(detailHeaderTop, compose.onNodeWithTag(personalMusicPlaylistHeaderTag).rect().top)
        assertEquals(searchButtonTop, compose.onNodeWithTag(searchAddPersonalMusicPlaylistItemTag).rect().top)
        assertTrue(scrollOffset(personalMusicPlaylistItemsScrollTag) > 0f)
    }

    private fun scrollOffset(tag: String): Float =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun `track search follows typing, highlights matches, and ignores stale results`() {
        val firstSearch = CompletableDeferred<List<MusicBoxSearchResult>>()
        val secondSearch = CompletableDeferred<List<MusicBoxSearchResult>>()
        val api =
            FakePersonalPlaylistApi(
                onSearch = { keyword, _ ->
                    when (keyword) {
                        "旧搜索" -> firstSearch.await()
                        "新搜索" -> secondSearch.await()
                        else -> emptyList()
                    }
                },
            )
        pumpPlaylistSettings(api)
        openTrackSearch()

        compose.onNodeWithText("搜索歌曲添加到歌单", useUnmergedTree = true).assertExists()
        dialogField().assertExists()
        assertEquals(0, compose.textCount("搜索"))

        dialogField().performTextReplacement("旧搜索")
        compose.pump(360)
        assertEquals(listOf("netease:旧搜索"), api.searchRequests)

        dialogField().performTextReplacement("新搜索")
        compose.pump(360)
        assertEquals(listOf("netease:旧搜索", "netease:新搜索"), api.searchRequests)

        secondSearch.complete(listOf(MusicBoxSearchResult(trackId = "new_track", name = "新搜索结果", artists = listOf("新搜索歌手"), source = "netease")))
        compose.pump()

        assertEquals(1, compose.textCount("新搜索结果"))
        val highlighted =
            compose.onNode(hasText("新搜索结果"), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text]
                .first { it.text == "新搜索结果" }
        assertTrue(
            "the result title highlights the query",
            highlighted.spanStyles.any { span ->
                span.item.color == UiColors.accent && highlighted.text.substring(span.start, span.end) == "新搜索"
            },
        )

        firstSearch.complete(listOf(MusicBoxSearchResult(trackId = "old_track", name = "旧搜索结果", artists = listOf("旧歌手"), source = "netease")))
        compose.pump()

        assertEquals(1, compose.textCount("新搜索结果"))
        assertEquals(0, compose.textCount("旧搜索结果"))
    }

    @Test
    fun `changing source reruns the current track search immediately`() {
        val api = FakePersonalPlaylistApi()
        pumpPlaylistSettings(api)
        openTrackSearch()

        dialogField().performTextReplacement("晴天")
        compose.pump(360)
        compose.pumpAndSettle()
        assertEquals("netease:晴天", api.searchRequests.last())

        compose.onAllNodesWithText("哔哩哔哩").onFirst().performClick()
        compose.pumpAndSettle()

        assertEquals("bilibili:晴天", api.searchRequests.last())
    }

    @Test
    @Config(qualifiers = size360x900)
    fun `search result uses icon-only add and opens the shared preview card`() {
        val result = MusicBoxSearchResult(trackId = "search_track", name = "搜索结果歌曲", artists = listOf("搜索结果歌手"), source = "netease")
        val api = PreviewPersonalPlaylistApi(onSearch = { _, _ -> listOf(result) })
        val previewFactory = FakePreviewPlatformFactory()
        pumpPlaylistSettings(api, previewPlatformFactory = previewFactory)
        openTrackSearch()

        dialogField().performTextReplacement("搜索")
        compose.pump(360)
        compose.pumpAndSettle()

        val resultTile = musicPlaylistSearchResultTag("netease", "search_track")
        val directAdd = musicPlaylistSearchResultAddTag("netease", "search_track")
        val inTile = hasAnyAncestor(hasTestTag(resultTile))
        assertTrue(exists(resultTile))
        assertEquals(1, compose.onAllNodes(hasText("搜索结果歌手") and inTile, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasText("网易云") and inTile, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasText("添加") and inTile, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertTrue(exists(directAdd))

        tapTag(directAdd)
        assertEquals(listOf("mbp_1:netease:search_track"), api.addRequests)
        assertEquals(0, compose.textCount("试听"))

        compose.onNode(hasText("搜索结果歌曲") and inTile, useUnmergedTree = true).performClick()
        compose.pumpAndSettle()
        assertEquals(1, compose.textCount("试听"))
        assertEquals(1, compose.textCount("添加到歌单"))

        compose.onNodeWithTag(musicTrackCardPreviewTag).performClick()
        compose.pump()
        api.completePreview()
        compose.pumpAndSettle()
        assertEquals(1, compose.textCount("取消试听"))
        assertEquals(1, previewFactory.platform.playCalls)

        tapTag(musicTrackCardAddToPlaylistTag)
        compose.onNodeWithTag("music-track-playlist-target:mbp_1").performClick()
        compose.pump()
        tapTag(musicTrackPlaylistTargetConfirmTag)
        assertEquals(listOf("mbp_1:netease:search_track", "mbp_1:netease:search_track"), api.addRequests)
        assertEquals(1, compose.textCount("取消试听"))
    }

    @Test
    @Config(qualifiers = size360x900)
    fun `playlist item actions use dialogs and management-only card controls`() {
        val api = FakePersonalPlaylistApi(playlistItems = listOf(FakePersonalPlaylistApi.playlistItem, secondItem))
        pumpPlaylistSettings(api)
        tapText("夜晚")

        val item = personalMusicPlaylistItemTag("mbpi_1")
        assertEquals(0, compose.textCount("筛选歌名或歌手"))
        assertEquals(0, tooltipCount("上移", insideTag = item))

        tapTag(filterPersonalMusicPlaylistItemsTag)
        assertEquals(1, compose.textCount("筛选歌曲"))
        assertEquals(1, compose.textCount("歌曲来源"))
        tapText("取消")

        tapTag(managePersonalMusicPlaylistItemsTag)
        assertEquals(1, compose.textCount("取消管理"))
        assertEquals(1, compose.textCount("添加到歌单"))
        assertEquals(1, compose.textCount("置顶"))
        assertEquals(1, compose.textCount("全选已加载"))
        assertEquals(1, tooltipCount("上移", insideTag = item))
        assertEquals(1, tooltipCount("下移", insideTag = item))
        assertEquals(1, tooltipCount("分享歌曲", insideTag = item))
        assertEquals(0, tooltipCount("删除", insideTag = item))
        assertFalse(compose.buttonEnabled(addSelectedPersonalMusicPlaylistItemsTag))

        tapTopLeft(item)
        compose.pumpAndSettle()
        assertFalse(compose.buttonEnabled(pinSelectedPersonalMusicPlaylistItemsTag))
        val selectionNumber = personalMusicPlaylistItemSelectionNumberTag("mbpi_1")
        assertTrue(exists(selectionNumber))
        assertEquals(
            1,
            compose.onAllNodes(hasText("1") and hasAnyAncestor(hasTestTag(selectionNumber)), useUnmergedTree = true).fetchSemanticsNodes().size,
        )

        tapTopLeft(item)
        compose.pumpAndSettle()
        assertFalse(compose.buttonEnabled(pinSelectedPersonalMusicPlaylistItemsTag))
        assertFalse(exists(selectionNumber))

        compose.onNode(hasTooltip("上移") and hasAnyAncestor(hasTestTag(item)), useUnmergedTree = true).performClick()
        compose.pumpAndSettle()
        assertFalse(compose.buttonEnabled(pinSelectedPersonalMusicPlaylistItemsTag))

        tapTopLeft(personalMusicPlaylistItemTag("mbpi_2"))
        compose.pumpAndSettle()
        assertTrue(compose.buttonEnabled(pinSelectedPersonalMusicPlaylistItemsTag))
    }

    @Test
    @Config(qualifiers = size360x800)
    fun `playlist batch mode selects cards instead of opening and filters by count`() {
        val api = FakePersonalPlaylistApi()
        pumpPlaylistSettings(api)

        tapText("管理")
        assertEquals(1, compose.textCount("取消管理"))
        assertEquals(1, compose.textCount("删除"))
        assertEquals(1, compose.textCount("合并"))
        assertEquals(1, compose.textCount("置顶"))
        assertEquals(1, compose.textCount("全选"))
        assertEquals(1, tooltipCount("重命名"))
        assertEquals(1, tooltipCount("上移"))
        assertEquals(1, tooltipCount("下移"))
        assertEquals(1, tooltipCount("分享"))
        assertEquals(0, tooltipCount("删除"))
        assertFalse(compose.buttonEnabled(mergeSelectedMusicPlaylistsTag))

        val selectedCard = personalMusicPlaylistCardTag("mbp_1")
        tapTopLeft(selectedCard)
        compose.pumpAndSettle()
        assertEquals(0, compose.textCount("搜索添加"))
        assertEquals(UiColors.selected.toArgb(), cardBackground(selectedCard))

        compose.onNode(hasTooltip("上移") and hasAnyAncestor(hasTestTag(selectedCard)), useUnmergedTree = true).performClick()
        compose.pumpAndSettle()
        assertEquals(UiColors.selected.toArgb(), cardBackground(selectedCard))

        assertTrue(compose.buttonEnabled(deleteSelectedPersonalMusicPlaylistsTag))
        tapTag(deleteSelectedPersonalMusicPlaylistsTag)
        assertEquals(1, compose.textCount("删除歌单"))
        tapText("取消")

        tapText("筛选")
        assertEquals(1, compose.textCount("歌曲数量"))
        dialogField().assertExists()
        compose.onNodeWithText("空歌单").performClick()
        compose.onNodeWithText("确认").performClick()
        compose.pumpAndSettle()

        assertEquals(1, compose.textCount("没有符合筛选条件的歌单"))
        assertEquals(1, compose.textCount("筛选 0 / 1", substring = true))
    }

    @Test
    @Config(qualifiers = size720x800)
    fun `personal playlist share searches rooms and sends a snapshot request`() {
        val api = FakePersonalPlaylistApi()
        pumpPlaylistSettings(api)
        tapText("管理")

        val shareButton = sharePersonalMusicPlaylistTag("mbp_1")
        assertTrue(exists(shareButton))
        tapTag(shareButton)

        assertEquals(1, compose.textCount("分享歌单"))
        assertEquals(1, compose.textCount("选择要将歌单“夜晚”分享到的文字频道"))
        assertEquals(1, compose.textCount("夜晚房间"))
        assertEquals(1, compose.textCount("另一个房间"))

        viewInsets = EdgeInsets(bottom = 300.dp)
        compose.pump()
        viewInsets = EdgeInsets.zero
        compose.pumpAndSettle()

        tapTag("music-playlist-share-room-profile-room_2")
        assertEquals(1, compose.textCount("2 名成员"))
        assertFalse(compose.buttonWithTextEnabled("确认分享"))
        tapTag("music-playlist-share-room-profile-room_2")
        compose.enterText("music-playlist-share-room-search", "另一个")
        assertEquals(0, compose.textCount("夜晚房间"))
        assertEquals(1, compose.textCount("另一个房间"))

        tapTopLeft("music-playlist-share-room-option-room_2", 3f, 3f)
        tapText("确认分享")

        assertEquals(listOf("room_2:playlist:mbp_1"), api.shareRequests)
        assertEquals(1, compose.textCount("已将“夜晚”分享到“另一个房间”", substring = true))
    }

    @Test
    @Config(qualifiers = size720x800)
    fun `playlist item share replaces single delete and sends a snapshot reference`() {
        val api = FakePersonalPlaylistApi()
        pumpPlaylistSettings(api)
        tapTag(personalMusicPlaylistCardTag("mbp_1"))
        tapTag(managePersonalMusicPlaylistItemsTag)

        val shareButton = shareMusicPlaylistItemTag("mbpi_1")
        assertTrue(exists(shareButton))
        assertEquals(1, compose.onAllNodes(hasIcon(Icons.Filled.DeleteOutline), useUnmergedTree = true).fetchSemanticsNodes().size)
        tapTag(shareButton)

        assertEquals(1, compose.textCount("分享歌曲"))
        tapTopLeft("music-track-share-room-option-room_2", 3f, 3f)
        tapText("确认分享")

        assertEquals(listOf("room_2:music_track:mbp_1:mbpi_1"), api.shareRequests)
        assertEquals(1, compose.textCount("已将“晴天”分享到", substring = true))
    }

    @Test
    @Config(qualifiers = size360x900)
    fun `batch adds personal songs without removing the source on android`() {
        val api =
            FakePersonalPlaylistApi(
                playlists = listOf(FakePersonalPlaylistApi.playlist, FakePersonalPlaylistApi.emptyPlaylist),
                playlistItems = listOf(FakePersonalPlaylistApi.playlistItem, secondItem),
            )
        pumpPlaylistSettings(api)
        tapText("夜晚")
        tapTag(managePersonalMusicPlaylistItemsTag)
        for (itemId in listOf("mbpi_2", "mbpi_1")) tapTopLeft(personalMusicPlaylistItemTag(itemId))
        compose.pumpAndSettle()

        assertTrue(compose.buttonEnabled(addSelectedPersonalMusicPlaylistItemsTag))
        tapTag(addSelectedPersonalMusicPlaylistItemsTag)
        assertEquals(1, compose.textCount("选择目标歌单"))
        tapTag("music-playlist-batch-add-target-mbp_empty")
        assertEquals(1, compose.textCount("确认添加歌曲"))
        assertEquals(1, compose.textCount("原歌单中的歌曲不会删除", substring = true))
        assertTrue(api.batchAddRequests.isEmpty())
        tapText("确认添加")

        assertEquals(listOf("mbp_1:mbp_empty:mbpi_2,mbpi_1"), api.batchAddRequests)
        assertEquals(2, api.playlistItems.size)
        assertEquals(1, compose.textCount("已添加到“空歌单”", substring = true))
    }

    @Test
    @Config(qualifiers = size360x800)
    fun `merges personal playlists in selection order on android`() {
        val api = FakePersonalPlaylistApi(playlists = listOf(FakePersonalPlaylistApi.playlist, FakePersonalPlaylistApi.emptyPlaylist))
        pumpPlaylistSettings(api)
        tapTag(managePersonalMusicPlaylistsTag)

        tapTopLeft(personalMusicPlaylistCardTag("mbp_empty"))
        tapTopLeft(personalMusicPlaylistCardTag("mbp_1"))
        compose.pumpAndSettle()

        assertTrue(compose.buttonEnabled(mergeSelectedMusicPlaylistsTag))
        tapTag(mergeSelectedMusicPlaylistsTag)
        assertEquals(1, compose.textCount("合并歌单"))
        assertEquals(1, compose.textCount("未合并的剩余歌曲仍保留", substring = true))
        compose.enterText(personalMusicPlaylistNameInputTag, "合并结果")
        compose.onAllNodesWithText("合并").onLast().performClick()
        compose.pumpAndSettle()
        assertEquals(1, compose.textCount("确认合并歌单"))
        assertEquals(1, compose.textCount("此操作无法撤销", substring = true))
        assertTrue(api.mergeRequests.isEmpty())
        tapText("确认合并")

        assertEquals(listOf("合并结果:mbp_empty,mbp_1"), api.mergeRequests)
        assertEquals(1, compose.textCount("合并结果"))
        assertEquals(1, compose.textCount("已合并为“合并结果”", substring = true))
    }

    @Test
    @Config(qualifiers = size360x800)
    fun `merges room playlists on android`() {
        val roomApi = FakeRoomPlaylistApi(playlists = listOf(FakeRoomPlaylistApi.playlist, FakeRoomPlaylistApi.secondPlaylist))
        pumpRoomPanel(roomController(roomApi, canManage = true))
        tapTag(managePersonalMusicPlaylistsTag)
        for (id in listOf("mbp_room_2", "mbp_room_1")) tapTopLeft(personalMusicPlaylistCardTag(id))
        compose.pumpAndSettle()
        tapTag(mergeSelectedMusicPlaylistsTag)
        compose.enterText(personalMusicPlaylistNameInputTag, "房间合并结果")
        compose.onAllNodesWithText("合并").onLast().performClick()
        compose.pumpAndSettle()
        tapText("确认合并")

        assertEquals(listOf("房间合并结果:mbp_room_2,mbp_room_1"), roomApi.mergeRequests)
        assertEquals(1, compose.textCount("房间合并结果"))
    }

    @Test
    @Config(qualifiers = size360x900)
    fun `batch adds selected room songs to another room playlist`() {
        val roomApi = FakeRoomPlaylistApi(playlists = listOf(FakeRoomPlaylistApi.playlist, FakeRoomPlaylistApi.secondPlaylist))
        pumpRoomPanel(roomController(roomApi, canManage = true))
        tapText("房间精选")
        tapTag(managePersonalMusicPlaylistItemsTag)
        tapTopLeft(personalMusicPlaylistItemTag("mbpi_room_1"))
        compose.pumpAndSettle()
        tapTag(addSelectedPersonalMusicPlaylistItemsTag)
        tapTag("music-playlist-batch-add-target-mbp_room_2")
        tapText("确认添加")

        assertEquals(listOf("room_1:mbp_room_1:mbp_room_2:mbpi_room_1"), roomApi.batchAddRequests)
        assertEquals(1, compose.textCount("已添加到“房间第二歌单”", substring = true))
    }

    @Test
    @Config(qualifiers = size360x900)
    fun `warns and reports when a target playlist only has one slot`() {
        val almostFull =
            PersonalMusicPlaylist(
                id = "mbp_almost_full",
                name = "即将满的歌单",
                description = "",
                revision = 1,
                itemCount = 499,
                createdAt = null,
                updatedAt = null,
            )
        val api =
            FakePersonalPlaylistApi(
                playlists = listOf(FakePersonalPlaylistApi.playlist, almostFull),
                playlistItems = listOf(FakePersonalPlaylistApi.playlistItem, secondItem),
            )
        pumpPlaylistSettings(api)
        tapText("夜晚")
        tapTag(managePersonalMusicPlaylistItemsTag)
        for (itemId in listOf("mbpi_1", "mbpi_2")) tapTopLeft(personalMusicPlaylistItemTag(itemId))
        compose.pumpAndSettle()
        tapTag(addSelectedPersonalMusicPlaylistItemsTag)
        tapTag("music-playlist-batch-add-target-mbp_almost_full")

        assertEquals(1, compose.textCount("歌曲不能全部添加"))
        assertEquals(1, compose.textCount("最多还能添加 1 首", substring = true))
        assertTrue(api.batchAddRequests.isEmpty())
        tapText("继续添加")

        assertEquals(listOf("mbp_1:mbp_almost_full:mbpi_1,mbpi_2"), api.batchAddRequests)
        assertEquals(1, compose.textCount("目标歌单已达 500 首上限", substring = true))
    }

    @Test
    @Config(qualifiers = size720x800)
    fun `warns before merging more than 500 playlist items`() {
        val api =
            FakePersonalPlaylistApi(
                playlists =
                    listOf(
                        PersonalMusicPlaylist(id = "mbp_many_1", name = "很多歌曲一", description = "", revision = 1, itemCount = 400, createdAt = null, updatedAt = null),
                        PersonalMusicPlaylist(id = "mbp_many_2", name = "很多歌曲二", description = "", revision = 1, itemCount = 300, createdAt = null, updatedAt = null),
                    ),
            )
        pumpPlaylistSettings(api)
        tapTag(managePersonalMusicPlaylistsTag)
        for (id in listOf("mbp_many_1", "mbp_many_2")) tapTopLeft(personalMusicPlaylistCardTag(id))
        compose.pumpAndSettle()
        tapTag(mergeSelectedMusicPlaylistsTag)
        compose.enterText(personalMusicPlaylistNameInputTag, "超长合并")
        compose.onAllNodesWithText("合并").onLast().performClick()
        compose.pumpAndSettle()

        assertEquals(1, compose.textCount("歌曲不能全部保留"))
        assertEquals(1, compose.textCount("共有 700 首歌曲", substring = true))
        assertEquals(1, compose.textCount("最多保留前 500 首", substring = true))
        assertEquals(1, compose.textCount("继续合并"))
        assertTrue(api.mergeRequests.isEmpty())
        tapText("取消")
        assertTrue(api.mergeRequests.isEmpty())
    }

    @Test
    fun `batch pin follows card selection order`() {
        val api =
            FakePersonalPlaylistApi(
                playlists =
                    listOf(
                        FakePersonalPlaylistApi.playlist,
                        PersonalMusicPlaylist(id = "mbp_2", name = "白天", description = "", revision = 1, itemCount = 2, createdAt = null, updatedAt = null),
                        PersonalMusicPlaylist(id = "mbp_3", name = "清晨", description = "", revision = 1, itemCount = 3, createdAt = null, updatedAt = null),
                    ),
            )
        pumpPlaylistSettings(api)
        tapText("管理")
        compose.onNodeWithText("清晨").performClick()
        compose.onNodeWithText("白天").performClick()
        compose.pumpAndSettle()

        tapTag(pinSelectedPersonalMusicPlaylistsTag)

        assertEquals(listOf(listOf("mbp_3", "mbp_2")), api.pinRequests)
        assertEquals(listOf("mbp_3", "mbp_2", "mbp_1"), api.playlists.map { it.id })
        assertTrue(compose.onNodeWithText("清晨").rect().top < compose.onNodeWithText("白天").rect().top)
        assertTrue(compose.onNodeWithText("白天").rect().top < compose.onNodeWithText("夜晚").rect().top)

        compose.onAllTooltips("下移").onFirst().performClick()
        compose.pumpAndSettle()
        assertEquals(listOf("mbp_3:down"), api.moveRequests)
        assertEquals(listOf("mbp_2", "mbp_3", "mbp_1"), api.playlists.map { it.id })
    }

    @Test
    fun `playlist rename dialog starts with the current name`() {
        val api = FakePersonalPlaylistApi()
        pumpPlaylistSettings(api)
        tapText("管理")
        compose.onTooltip("重命名").performClick()
        compose.pumpAndSettle()

        assertEquals(1, compose.textCount("重命名歌单"))
        assertEquals("夜晚", compose.field(personalMusicPlaylistNameInputTag).fieldText())

        compose.enterText(personalMusicPlaylistNameInputTag, "夜间精选")
        tapText("重命名")

        assertEquals(listOf("mbp_1:夜间精选"), api.renameRequests)
        assertEquals(1, compose.textCount("夜间精选"))
    }

    @Test
    @Config(qualifiers = size600x900)
    fun `playlist names move every control row before using two lines`() {
        val mediumName = "需要先让所有管理按钮换行但仍可单行显示的歌单"
        val longName = "需要先让所有管理按钮换行并且按钮换行以后名称仍然需要使用两行显示的超长歌单名称"
        val api =
            FakePersonalPlaylistApi(
                playlists =
                    listOf(
                        PersonalMusicPlaylist(id = "mbp_1", name = mediumName, description = "", revision = 1, itemCount = 1, createdAt = null, updatedAt = null),
                        PersonalMusicPlaylist(id = "mbp_2", name = longName, description = "", revision = 1, itemCount = 2, createdAt = null, updatedAt = null),
                    ),
            )
        pumpPlaylistSettings(api)
        tapText("管理")

        for (id in listOf("mbp_1", "mbp_2")) {
            val information = compose.onNodeWithTag(personalMusicPlaylistInformationTag(id), useUnmergedTree = true)
            val controls = compose.onNodeWithTag(personalMusicPlaylistControlsTag(id), useUnmergedTree = true)
            information.assertExists()
            controls.assertExists()
            assertTrue("$id controls sit below the name", controls.rect().top > information.rect().bottom)
        }
        assertEquals(1, compose.onNode(hasText(mediumName), useUnmergedTree = true).textLayout().layoutInput.maxLines)
        val long = compose.onNode(hasText(longName), useUnmergedTree = true)
        assertEquals(2, long.textLayout().layoutInput.maxLines)
        assertFalse(long.didExceedMaxLines())
    }

    @Test
    fun `playlist cards use hover feedback and consistent spacing`() {
        val api =
            FakePersonalPlaylistApi(
                playlists =
                    listOf(
                        FakePersonalPlaylistApi.playlist,
                        PersonalMusicPlaylist(id = "mbp_2", name = "白天", description = "", revision = 1, itemCount = 2, createdAt = null, updatedAt = null),
                    ),
            )
        pumpPlaylistSettings(api)

        val firstCard = personalMusicPlaylistCardTag("mbp_1")
        val secondCard = personalMusicPlaylistCardTag("mbp_2")
        assertTrue(exists(firstCard))
        assertTrue(exists(secondCard))
        assertTrue(compose.onNodeWithTag(secondCard).rect().top - compose.onNodeWithTag(firstCard).rect().bottom >= 9.dp)
        assertNotEquals(UiColors.selected.toArgb(), cardBackground(firstCard))

        // PORT: the desktop mouse-cursor (SystemMouseCursors.click) checks are
        // dropped; Android has no cursor shapes. Hover still applies to mice.
        compose.onNodeWithTag(firstCard).performMouseInput { enter(center) }
        compose.pumpAndSettle()
        assertEquals(UiColors.selected.toArgb(), cardBackground(firstCard))
        val card = compose.onNodeWithTag(firstCard)
        val height = card.fetchSemanticsNode().size.height / compose.density.density
        assertEquals(UiColors.selectedBorder.toArgb(), compose.pixelAt(card, 0f, height / 2).toArgb())
    }

    @Test
    @Config(qualifiers = size360x800)
    fun `room playlist panel is readable but not editable by members on android`() {
        val api = FakeRoomPlaylistApi()
        pumpRoomPanel(
            roomController(api, canManage = false),
            previewApi = PreviewPersonalPlaylistApi(),
            previewPlatformFactory = FakePreviewPlatformFactory(),
        )

        assertEquals(1, compose.textCount("房间歌单"))
        assertEquals(1, compose.textCount("房间精选"))
        assertFalse(compose.buttonEnabled(createPersonalMusicPlaylistTag))
        assertFalse(compose.buttonEnabled(managePersonalMusicPlaylistsTag))

        tapText("房间精选")
        assertEquals(1, compose.textCount("房间歌曲"))
        assertFalse(compose.buttonEnabled(searchAddPersonalMusicPlaylistItemTag))
        assertFalse(compose.buttonEnabled(managePersonalMusicPlaylistItemsTag))
        tapText("房间歌曲")
        assertEquals(1, compose.textCount("试听"))
        assertEquals(0, compose.textCount("添加到歌单"))
        assertTrue(exists(musicTrackCardPreviewTag))
    }

    @Test
    @Config(qualifiers = size720x800)
    fun `room playlist managers get preview and add actions`() {
        val personalApi = FakePersonalPlaylistApi()
        pumpRoomPanel(
            roomController(FakeRoomPlaylistApi(), canManage = true, personalApi = personalApi),
            previewApi = PreviewPersonalPlaylistApi(),
            previewPlatformFactory = FakePreviewPlatformFactory(),
        )
        tapText("房间精选")
        tapText("房间歌曲")

        assertEquals(1, compose.textCount("试听"))
        assertEquals(1, compose.textCount("添加到歌单"))

        tapText("添加到歌单")
        assertTrue(compose.hasTextNode("房间精选"))
        assertEquals(1, compose.textCount("夜晚"))

        compose.onNodeWithTag("music-track-playlist-target:mbp_1").performClick()
        compose.pump()
        tapTag(musicTrackPlaylistTargetConfirmTag)
        assertEquals(listOf("mbp_1:netease:track_room_1"), personalApi.addRequests)
    }

    @Test
    @Config(qualifiers = size360x800)
    fun `room playlist management clones one row on android`() {
        val roomApi = FakeRoomPlaylistApi()
        pumpRoomPanel(roomController(roomApi, canManage = true))
        tapTag(managePersonalMusicPlaylistsTag)

        assertFalse(exists("clone-selected-room-music-playlists"))
        assertTrue(exists(mergeSelectedMusicPlaylistsTag))
        val card = personalMusicPlaylistCardTag("mbp_room_1")
        val cloneButton = cloneRoomMusicPlaylistTag("mbp_room_1")
        assertTrue(exists(cloneButton))
        assertEquals(0, tooltipCount("删除", insideTag = card))

        tapTag(cloneButton)
        assertEquals(1, compose.textCount("克隆歌单"))
        assertEquals(1, compose.textCount("房间备注名 · 歌单名", substring = true))
        compose.onAllNodesWithText("克隆").onLast().performClick()
        compose.pumpAndSettle()

        assertEquals(listOf("mbp_room_1"), roomApi.cloneRequests)
        assertEquals(1, compose.textCount("已克隆到我的歌单 - 房间备注名 · 房间精选"))
        assertEquals(1, compose.textCount("取消管理"))
    }

    @Test
    @Config(qualifiers = size720x800)
    fun `room playlist clone shows the 50-playlist limit notice`() {
        val roomApi =
            FakeRoomPlaylistApi(
                cloneError = ApiException("个人歌单数量已达到上限", statusCode = 409, code = "playlist_limit_reached", requestId = null),
            )
        pumpRoomPanel(roomController(roomApi, canManage = true))
        tapTag(managePersonalMusicPlaylistsTag)
        tapTag(cloneRoomMusicPlaylistTag("mbp_room_1"))
        compose.onAllNodesWithText("克隆").onLast().performClick()
        compose.pumpAndSettle()

        assertEquals(listOf("mbp_room_1"), roomApi.cloneRequests)
        assertEquals(1, compose.textCount("克隆失败：我的歌单已达 50 个上限"))
    }

    @Test
    @Config(qualifiers = size360x800)
    fun `room playlist creation imports a personal playlist on android`() {
        val roomApi = FakeRoomPlaylistApi()
        val personalApi = FakePersonalPlaylistApi()
        pumpRoomPanel(roomController(roomApi, canManage = true, personalApi = personalApi))
        tapTag(createPersonalMusicPlaylistTag)
        assertEquals(1, compose.textCount("导入我的歌单"))

        compose.onNodeWithText("导入我的歌单").performClick()
        // The originating button stays in its loading state while the picker
        // dialog is open.
        compose.pump(300)
        compose.onNodeWithText("夜晚").performClick()
        compose.pumpAndSettle()
        assertTrue(exists(selectedPersonalMusicPlaylistImportTag))

        compose.enterText(personalMusicPlaylistNameInputTag, "导入精选")
        tapText("创建")

        assertEquals(listOf("room_1:导入精选:mbp_1"), roomApi.importRequests)
    }

    private val secondItem =
        PersonalMusicPlaylistItem(
            id = "mbpi_2",
            playlistId = "mbp_1",
            trackId = "track_2",
            source = "netease",
            title = "夜曲",
            artists = listOf("周杰伦"),
            durationMs = 226000,
            sortOrder = 20,
            createdAt = null,
        )
}

private inline fun <reified T : Any> _unimplementedFake(): T {
    val name = T::class.java.simpleName
    return java.lang.reflect.Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.getOrNull(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "Unimplemented $name"
            else -> throw NotImplementedError("$name.${method.name} is not implemented by this fake")
        }
    } as T
}

/** Dart's `_FakePersonalPlaylistApi`. */
private open class FakePersonalPlaylistApi(
    val onSearch: (suspend (keyword: String, source: String) -> List<MusicBoxSearchResult>)? = null,
    playlists: List<PersonalMusicPlaylist>? = null,
    playlistItems: List<PersonalMusicPlaylistItem>? = null,
) : GangApi by _unimplementedFake<GangApi>(),
    PersonalMusicPlaylistApi,
    PersonalMusicPlaylistMergeApi,
    PersonalMusicPlaylistBatchAddApi {
    val playlists: MutableList<PersonalMusicPlaylist> = (playlists ?: listOf(playlist)).toMutableList()
    val playlistItems: MutableList<PersonalMusicPlaylistItem> = (playlistItems ?: listOf(playlistItem)).toMutableList()
    val searchRequests = mutableListOf<String>()
    val pinRequests = mutableListOf<List<String>>()
    val moveRequests = mutableListOf<String>()
    val renameRequests = mutableListOf<String>()
    val addRequests = mutableListOf<String>()
    val mergeRequests = mutableListOf<String>()
    val batchAddRequests = mutableListOf<String>()
    val shareRequests = mutableListOf<String>()
    var listRequestCount = 0
    var nextPlaylistListResponse: CompletableDeferred<PersonalMusicPlaylistPage>? = null

    fun playlistPage(): PersonalMusicPlaylistPage =
        PersonalMusicPlaylistPage(
            playlists = playlists.toList(),
            page = 1,
            pageSize = 50,
            total = playlists.size,
            hasMore = false,
            maxPlaylists = 50,
            maxPlaylistItems = 500,
        )

    override suspend fun listPersonalMusicPlaylists(
        page: Int,
        pageSize: Int,
    ): PersonalMusicPlaylistPage {
        listRequestCount += 1
        val pending = nextPlaylistListResponse
        if (pending != null) {
            nextPlaylistListResponse = null
            return pending.await()
        }
        return playlistPage()
    }

    override suspend fun getPersonalMusicPlaylist(
        playlistId: String,
        page: Int,
        pageSize: Int,
        keyword: String?,
        source: String?,
    ): PersonalMusicPlaylistItemsPage {
        if (playlistId == emptyPlaylist.id) {
            return PersonalMusicPlaylistItemsPage(playlist = emptyPlaylist, items = emptyList(), page = 1, pageSize = 50, total = 0, hasMore = false)
        }
        val currentPlaylist =
            PersonalMusicPlaylist(
                id = playlist.id,
                name = playlist.name,
                description = playlist.description,
                revision = playlist.revision,
                itemCount = playlistItems.size,
                createdAt = playlist.createdAt,
                updatedAt = playlist.updatedAt,
            )
        return PersonalMusicPlaylistItemsPage(
            playlist = currentPlaylist,
            items = playlistItems.toList(),
            page = 1,
            pageSize = 50,
            total = playlistItems.size,
            hasMore = false,
        )
    }

    override suspend fun createPersonalMusicPlaylist(name: String): PersonalMusicPlaylist = playlist

    override suspend fun mergePersonalMusicPlaylists(
        name: String,
        playlistIds: List<String>,
    ): PersonalMusicPlaylistMergeResult {
        mergeRequests.add("$name:${playlistIds.joinToString(",")}")
        val sourceItemCount = playlists.filter { playlistIds.contains(it.id) }.sumOf { it.itemCount }
        playlists.removeAll { playlistIds.contains(it.id) }
        val merged =
            PersonalMusicPlaylist(
                id = "mbp_merged_${mergeRequests.size}",
                name = name,
                description = "",
                revision = 1,
                itemCount = sourceItemCount,
                createdAt = null,
                updatedAt = null,
            )
        playlists.add(merged)
        return mergeResult(merged, sourceItemCount, playlistIds.size)
    }

    override suspend fun batchAddPersonalMusicPlaylistItems(
        sourcePlaylistId: String,
        targetPlaylistId: String,
        itemIds: List<String>,
    ): PersonalMusicPlaylistBatchAddResult {
        batchAddRequests.add("$sourcePlaylistId:$targetPlaylistId:${itemIds.joinToString(",")}")
        val targetIndex = playlists.indexOfFirst { it.id == targetPlaylistId }
        val (updated, result) = batchAddResult(playlists[targetIndex], itemIds)
        playlists[targetIndex] = updated
        return result
    }

    override suspend fun renamePersonalMusicPlaylist(
        playlistId: String,
        name: String,
    ): PersonalMusicPlaylist {
        renameRequests.add("$playlistId:$name")
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index < 0) return playlist
        val current = playlists[index]
        val renamed =
            PersonalMusicPlaylist(
                id = current.id,
                name = name,
                description = current.description,
                revision = current.revision + 1,
                itemCount = current.itemCount,
                createdAt = current.createdAt,
                updatedAt = current.updatedAt,
            )
        playlists[index] = renamed
        return renamed
    }

    override suspend fun deletePersonalMusicPlaylist(playlistId: String) {
        playlists.removeAll { it.id == playlistId }
    }

    override suspend fun pinPersonalMusicPlaylists(playlistIds: List<String>) {
        pinRequests.add(playlistIds.toList())
        val selected = playlistIds.mapNotNull { id -> playlists.firstOrNull { it.id == id } }
        val selectedIds = playlistIds.toSet()
        playlists.removeAll { selectedIds.contains(it.id) }
        playlists.addAll(0, selected)
    }

    override suspend fun movePersonalMusicPlaylist(
        playlistId: String,
        direction: String,
    ) {
        moveRequests.add("$playlistId:$direction")
        val from = playlists.indexOfFirst { it.id == playlistId }
        val to = from + if (direction == "up") -1 else 1
        if (from < 0 || to < 0 || to >= playlists.size) return
        val moving = playlists.removeAt(from)
        playlists.add(to, moving)
    }

    override suspend fun searchPersonalMusicPlaylistTracks(
        keyword: String,
        source: String?,
        count: Int,
        page: Int,
    ): List<MusicBoxSearchResult> {
        val normalizedSource = source ?: ""
        searchRequests.add("$normalizedSource:$keyword")
        val handler = onSearch ?: return emptyList()
        return handler(keyword, normalizedSource)
    }

    override suspend fun addPersonalMusicPlaylistItem(
        playlistId: String,
        track: MusicBoxSearchResult,
        durationMs: Int?,
    ): PersonalMusicPlaylistItem {
        addRequests.add("$playlistId:${track.source}:${track.trackId}")
        val item =
            PersonalMusicPlaylistItem(
                id = "added_${addRequests.size}",
                playlistId = playlistId,
                trackId = track.trackId,
                source = track.source,
                title = track.name,
                artists = track.artists,
                durationMs = durationMs ?: 0,
                sortOrder = (playlistItems.size + 1) * 10,
                createdAt = null,
            )
        playlistItems.add(item)
        return item
    }

    override suspend fun deletePersonalMusicPlaylistItem(
        playlistId: String,
        itemId: String,
    ) {}

    override suspend fun deletePersonalMusicPlaylistItems(
        playlistId: String,
        itemIds: List<String>,
    ) {}

    override suspend fun movePersonalMusicPlaylistItem(
        playlistId: String,
        itemId: String,
        direction: String,
    ) {}

    override suspend fun reorderPersonalMusicPlaylistItems(
        playlistId: String,
        itemIds: List<String>,
    ) {}

    private val rooms =
        listOf(
            RoomCard(
                id = "room_1",
                name = "夜晚房间",
                avatarUrl = null,
                defaultAvatarKey = "blue-1",
                memberCount = 3,
                liveParticipantCount = 0,
                liveAvatarPreview = emptyList(),
                lastMessage = null,
                unreadCount = 0,
                updatedAt = DateTime.utc(2026, 8, 7),
                rid = "10001",
            ),
            RoomCard(
                id = "room_2",
                name = "另一个房间",
                avatarUrl = null,
                defaultAvatarKey = "blue-2",
                memberCount = 2,
                liveParticipantCount = 0,
                liveAvatarPreview = emptyList(),
                lastMessage = null,
                unreadCount = 0,
                updatedAt = DateTime.utc(2026, 8, 7),
                rid = "10002",
            ),
        )

    override suspend fun listRooms(
        limit: Int,
        cursor: String?,
    ): RoomPage = RoomPage(rooms = rooms, nextCursor = null)

    override suspend fun getRoom(roomId: String): RoomDetail {
        val room = rooms.first { it.id == roomId }
        return RoomDetail(
            id = room.id,
            name = room.name,
            rid = room.rid,
            avatarUrl = room.avatarUrl,
            defaultAvatarKey = room.defaultAvatarKey,
            memberCount = room.memberCount,
            onlineMemberCount = room.onlineMemberCount,
            myMembership = RoomMembership(joinedAt = DateTime.utc(2026, 8, 1), role = "member"),
            live = LiveState(roomId = room.id, participantCount = 0, participants = emptyList(), updatedAt = room.updatedAt),
            createdAt = DateTime.utc(2026, 8, 1),
            updatedAt = room.updatedAt,
        )
    }

    override suspend fun sendMessage(
        roomId: String,
        clientMessageId: String,
        body: String,
        type: String,
        attachments: List<MessageAttachment>,
        mentions: List<Map<String, Any?>>,
        quoteMessageId: String?,
        quoteMessageIds: List<String>,
        idempotencyKey: String?,
    ): Message {
        val attachment = attachments.single()
        val identifier = if (type == "music_track") "${attachment.playlistId}:${attachment.itemId}" else attachment.playlistId
        shareRequests.add("$roomId:$type:$identifier")
        return Message(
            id = "message_${shareRequests.size}",
            roomId = roomId,
            sender = UserSummary(id = "user_1", username = "tester", displayName = "测试用户", avatarUrl = null, defaultAvatarKey = "blue-1"),
            clientMessageId = clientMessageId,
            type = type,
            body = "[歌单] 夜晚",
            attachments = attachments,
            createdAt = DateTime.utc(2026, 8, 7),
        )
    }

    companion object {
        val playlist =
            PersonalMusicPlaylist(id = "mbp_1", name = "夜晚", description = "", revision = 2, itemCount = 1, createdAt = null, updatedAt = null)

        val emptyPlaylist =
            PersonalMusicPlaylist(id = "mbp_empty", name = "空歌单", description = "", revision = 1, itemCount = 0, createdAt = null, updatedAt = null)

        val playlistItem =
            PersonalMusicPlaylistItem(
                id = "mbpi_1",
                playlistId = "mbp_1",
                trackId = "track_1",
                source = "netease",
                title = "晴天",
                artists = listOf("周杰伦"),
                durationMs = 269000,
                sortOrder = 10,
                createdAt = null,
            )
    }
}

private fun mergeResult(
    merged: PersonalMusicPlaylist,
    sourceItemCount: Int,
    deletedPlaylistCount: Int,
) = PersonalMusicPlaylistMergeResult(
    playlist = merged,
    sourceItemCount = sourceItemCount,
    uniqueItemCount = sourceItemCount,
    duplicateCount = 0,
    itemCount = sourceItemCount,
    omittedCount = 0,
    deletedPlaylistCount = deletedPlaylistCount,
    retainedPlaylistCount = 0,
    consumedSourceItemCount = sourceItemCount,
    truncated = false,
)

private fun batchAddResult(
    current: PersonalMusicPlaylist,
    itemIds: List<String>,
): Pair<PersonalMusicPlaylist, PersonalMusicPlaylistBatchAddResult> {
    val available = 500 - current.itemCount
    val addedCount = if (available <= 0) 0 else minOf(itemIds.size, available)
    val omittedCount = itemIds.size - addedCount
    val updated = current.copyWith(itemCount = current.itemCount + addedCount, revision = current.revision + 1)
    return updated to
        PersonalMusicPlaylistBatchAddResult(
            playlist = updated,
            selectedItemCount = itemIds.size,
            uniqueItemCount = itemIds.size,
            duplicateCount = 0,
            alreadyPresentCount = 0,
            addedItemCount = addedCount,
            omittedCount = omittedCount,
            truncated = omittedCount > 0,
        )
}

/** Dart's `_PreviewPersonalPlaylistApi`. */
private class PreviewPersonalPlaylistApi(
    onSearch: (suspend (keyword: String, source: String) -> List<MusicBoxSearchResult>)? = null,
) : FakePersonalPlaylistApi(onSearch = onSearch),
    MusicTrackPreviewApi {
    private val _preview = CompletableDeferred<DownloadedFile>()

    fun completePreview() {
        if (_preview.isCompleted) return
        _preview.complete(
            DownloadedFile(bytes = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112), filename = "preview.m4a", mimeType = "audio/mp4"),
        )
    }

    override suspend fun downloadMusicTrackPreview(
        source: String,
        trackId: String,
    ): DownloadedFile = _preview.await()
}

/** Dart's `_FakePreviewPlatformFactory`. */
private class FakePreviewPlatformFactory : MusicTrackPreviewPlatformFactory {
    val platform = FakePreviewPlatform()

    override fun create(): MusicTrackPreviewPlatform = platform
}

/** Dart's `_FakePreviewPlatform`. */
private class FakePreviewPlatform : MusicTrackPreviewPlatform {
    private val _completed: StreamController<Unit> = StreamController.broadcast()
    var playCalls = 0

    override val onCompleted: Stream<Unit> get() = _completed.stream

    override suspend fun findCached(cacheKey: String): MusicTrackPreviewAsset? = null

    override suspend fun store(
        cacheKey: String,
        bytes: ByteArray,
    ): MusicTrackPreviewAsset = MusicTrackPreviewAsset(cacheKey)

    override suspend fun play(asset: MusicTrackPreviewAsset) {
        playCalls += 1
    }

    override suspend fun stop() {}

    override suspend fun dispose() = _completed.close()
}

/** Dart's `_FakeRoomPlaylistApi`. */
private class FakeRoomPlaylistApi(
    val cloneError: Exception? = null,
    playlists: List<PersonalMusicPlaylist>? = null,
) : RoomMusicPlaylistApi by _unimplementedFake<RoomMusicPlaylistApi>(),
    RoomMusicPlaylistMergeApi,
    RoomMusicPlaylistBatchAddApi,
    RoomMusicPlaylistImportApi,
    RoomMusicPlaylistCloneApi {
    val importRequests = mutableListOf<String>()
    val cloneRequests = mutableListOf<String>()
    val mergeRequests = mutableListOf<String>()
    val batchAddRequests = mutableListOf<String>()
    val playlists: MutableList<PersonalMusicPlaylist> = (playlists ?: listOf(playlist)).toMutableList()

    override suspend fun listRoomMusicPlaylists(
        roomId: String,
        page: Int,
        pageSize: Int,
    ): PersonalMusicPlaylistPage {
        assertEquals("room_1", roomId)
        return PersonalMusicPlaylistPage(
            playlists = playlists.toList(),
            page = 1,
            pageSize = 50,
            total = playlists.size,
            hasMore = false,
            maxPlaylists = 50,
            maxPlaylistItems = 500,
        )
    }

    override suspend fun getRoomMusicPlaylist(
        roomId: String,
        playlistId: String,
        page: Int,
        pageSize: Int,
        keyword: String?,
        source: String?,
    ): PersonalMusicPlaylistItemsPage {
        assertEquals("room_1", roomId)
        assertEquals(playlist.id, playlistId)
        return PersonalMusicPlaylistItemsPage(playlist = playlist, items = listOf(item), page = 1, pageSize = 50, total = 1, hasMore = false)
    }

    override suspend fun createRoomMusicPlaylistFromPersonal(
        roomId: String,
        name: String,
        importPlaylistId: String,
    ): PersonalMusicPlaylist {
        importRequests.add("$roomId:$name:$importPlaylistId")
        return PersonalMusicPlaylist(id = "mbp_room_imported", name = name, description = "", revision = 1, itemCount = 1, createdAt = null, updatedAt = null)
    }

    override suspend fun cloneRoomMusicPlaylistToPersonal(
        roomId: String,
        playlistId: String,
    ): PersonalMusicPlaylist {
        assertEquals("room_1", roomId)
        cloneRequests.add(playlistId)
        cloneError?.let { throw it }
        return cloneResult
    }

    override suspend fun mergeRoomMusicPlaylists(
        roomId: String,
        name: String,
        playlistIds: List<String>,
    ): PersonalMusicPlaylistMergeResult {
        assertEquals("room_1", roomId)
        mergeRequests.add("$name:${playlistIds.joinToString(",")}")
        val sourceItemCount = playlists.filter { playlistIds.contains(it.id) }.sumOf { it.itemCount }
        playlists.removeAll { playlistIds.contains(it.id) }
        val merged =
            PersonalMusicPlaylist(
                id = "mbp_room_merged_${mergeRequests.size}",
                name = name,
                description = "",
                revision = 1,
                itemCount = sourceItemCount,
                createdAt = null,
                updatedAt = null,
            )
        playlists.add(merged)
        return mergeResult(merged, sourceItemCount, playlistIds.size)
    }

    override suspend fun batchAddRoomMusicPlaylistItems(
        roomId: String,
        sourcePlaylistId: String,
        targetPlaylistId: String,
        itemIds: List<String>,
    ): PersonalMusicPlaylistBatchAddResult {
        batchAddRequests.add("$roomId:$sourcePlaylistId:$targetPlaylistId:${itemIds.joinToString(",")}")
        val targetIndex = playlists.indexOfFirst { it.id == targetPlaylistId }
        val (updated, result) = batchAddResult(playlists[targetIndex], itemIds)
        playlists[targetIndex] = updated
        return result
    }

    companion object {
        val playlist =
            PersonalMusicPlaylist(id = "mbp_room_1", name = "房间精选", description = "", revision = 1, itemCount = 1, createdAt = null, updatedAt = null)

        val cloneResult =
            PersonalMusicPlaylist(
                id = "mbp_personal_clone_1",
                name = "房间备注名 · 房间精选",
                description = "",
                revision = 1,
                itemCount = 1,
                createdAt = null,
                updatedAt = null,
            )

        val secondPlaylist =
            PersonalMusicPlaylist(id = "mbp_room_2", name = "房间第二歌单", description = "", revision = 1, itemCount = 2, createdAt = null, updatedAt = null)

        val item =
            PersonalMusicPlaylistItem(
                id = "mbpi_room_1",
                playlistId = "mbp_room_1",
                trackId = "track_room_1",
                source = "netease",
                title = "房间歌曲",
                artists = listOf("歌手"),
                durationMs = 180000,
                sortOrder = 10,
                createdAt = null,
            )
    }
}
