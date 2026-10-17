package com.gangchat.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.MusicBoxController
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.MusicBoxActiveSource
import com.gangchat.client.protocol.MusicBoxActiveSourceType
import com.gangchat.client.protocol.MusicBoxCapabilities
import com.gangchat.client.protocol.MusicBoxPlaybackState
import com.gangchat.client.protocol.MusicBoxQueueItem
import com.gangchat.client.protocol.MusicBoxQueueItemStatus
import com.gangchat.client.protocol.MusicBoxRequester
import com.gangchat.client.protocol.MusicBoxSearchResult
import com.gangchat.client.protocol.MusicBoxState
import com.gangchat.client.protocol.PersonalMusicPlaylist
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.protocol.musicBoxActiveSourceTypeValue
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.TooltipSemanticsKey
import com.gangchat.client.ui.dpBounds
import com.gangchat.client.ui.hasTooltip
import com.gangchat.client.ui.installTestImageLoader
import com.gangchat.client.ui.segmentedControlThumbTag
import com.gangchat.client.ui.textContextMenuPanelTag
import com.gangchat.client.ui.textLayout
import com.gangchat.client.ui.uiSliderHandleTag
import com.gangchat.client.ui.uiSliderHoverLabelTag
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.LocalMediaQuery
import com.gangchat.client.widgets.TextEditingController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Port of `test/widget/home/music_box_progress_widget_test.dart`: the music
 * box panel's server-authoritative progress, the now-playing strip and the
 * flat body list (request queue with its inline search, room / personal
 * playlist groups unfolding in place). Hosted like Dart's `_host`: the panel
 * in a 360 dp wide box of the 800x600 test window.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-mdpi", shadows = [ShadowNoOpMagnifier::class])
class MusicBoxProgressWidgetTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installTestImageLoader()

    /** The arguments a Dart test re-pumps (`pumpWidget(_host(...))`). */
    private class PanelHost(state: MusicBoxState, height: Double?, volume: Double) {
        var state by mutableStateOf(state)
        var height by mutableStateOf(height)
        var volume by mutableStateOf(volume)
        var viewInsets by mutableStateOf(EdgeInsets.zero)

        /** Bumping this disposes the panel and builds a fresh one (Dart's `pumpWidget(SizedBox.shrink())`). */
        var generation by mutableIntStateOf(0)
    }

    private fun setPanel(
        state: MusicBoxState,
        searchController: TextEditingController = TextEditingController(),
        height: Double? = null,
        searchResults: List<MusicBoxSearchResult> = emptyList(),
        source: String = "netease",
        volume: Double = 1.0,
        onVolumeChanged: (Double) -> Unit = {},
        controller: MusicBoxController? = null,
        roomId: String? = null,
        onStateChanged: ((MusicBoxState) -> Unit)? = null,
        onQueueResult: (MusicBoxSearchResult) -> Unit = {},
        onRemoveItem: (MusicBoxQueueItem) -> Unit = {},
        currentUser: CurrentUser? = null,
        room: PublicRoom? = null,
        onResolveUserProfile: UserProfileResolver? = null,
        userProfileActionBuilder: UserProfileActionBuilder? = null,
        onCreateFirstRoomPlaylist: (() -> Unit)? = null,
        onCreateFirstPersonalPlaylist: (() -> Unit)? = null,
        onEditRoomPlaylist: MusicPlaylistEditCallback? = null,
        onEditPersonalPlaylist: MusicPlaylistEditCallback? = null,
    ): PanelHost {
        val host = PanelHost(state, height, volume)
        compose.setHomeContent { _ ->
            val media = LocalMediaQuery.current
            CompositionLocalProvider(LocalMediaQuery provides media.copy(viewInsets = host.viewInsets)) {
                key(host.generation) {
                    val boxHeight = host.height
                    Box(Modifier.width(360.dp).then(if (boxHeight != null) Modifier.height(boxHeight.dp) else Modifier.fillMaxHeight())) {
                        LiveMusicBoxPanel(
                            state = host.state,
                            searchController = searchController,
                            searchResults = searchResults,
                            searching = false,
                            searchError = null,
                            source = source,
                            controller = controller,
                            roomId = roomId,
                            room = room,
                            onStateChanged = onStateChanged,
                            currentUser = currentUser,
                            onResolveUserProfile = onResolveUserProfile,
                            userProfileActionBuilder = userProfileActionBuilder,
                            onCreateFirstRoomPlaylist = onCreateFirstRoomPlaylist,
                            onCreateFirstPersonalPlaylist = onCreateFirstPersonalPlaylist,
                            onEditRoomPlaylist = onEditRoomPlaylist,
                            onEditPersonalPlaylist = onEditPersonalPlaylist,
                            onTogglePlayback = {},
                            onSkip = {},
                            onQueueResult = onQueueResult,
                            onRemoveItem = onRemoveItem,
                            onSourceChanged = {},
                            onClose = {},
                            volume = host.volume,
                            onVolumeChanged = onVolumeChanged,
                        )
                    }
                }
            }
        }
        return host
    }

    // --- Finders and gestures ----------------------------------------------

    private fun exists(tag: String) = compose.tagCount(tag) == 1

    private fun rect(tag: String): DpRect = compose.rectOf(tag)

    private fun textRect(text: String): DpRect = compose.onNodeWithText(text, useUnmergedTree = true).dpBounds(compose.density.density)

    private fun textCount(text: String) = compose.countOf(hasText(text))

    private fun withinCount(tag: String, matcher: SemanticsMatcher) = compose.within(tag, matcher).fetchSemanticsNodes().size

    private fun groupHeader(type: MusicBoxActiveSourceType) = "music-box-group-header:${musicBoxActiveSourceTypeValue(type)}"

    private fun playlistRow(type: MusicBoxActiveSourceType, id: String) = "music-box-playlist-row:${musicBoxActiveSourceTypeValue(type)}:$id"

    /** Dart's `_groupOpen`: the header's expanded semantics (its chevron's quarter turn is not exposed). */
    private fun groupOpen(type: MusicBoxActiveSourceType): Boolean = compose.tag(groupHeader(type)).fetchSemanticsNode().config[LiveExpandedKey]

    /** Dart's `_actionLabel`: in-list actions carry their label as semantics, not a tooltip. */
    private fun actionLabel(tag: String): String = compose.tag(tag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()

    /** Whether neither [tag] nor anything inside it reports itself disabled. */
    private fun actionEnabled(tag: String): Boolean =
        compose.countOf(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))),
        ) == 0

    /** The tooltip of [tag] or of a node inside it. */
    private fun tooltip(tag: String): String? =
        compose.onAllNodes(hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstNotNullOfOrNull { it.config.getOrNull(TooltipSemanticsKey) }

    private fun click(tag: String) {
        compose.tag(tag).performClick()
        compose.waitForIdle()
    }

    private fun clickText(text: String) {
        compose.onNodeWithText(text, useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    private fun clickTextWithin(tag: String, text: String) {
        compose.within(tag, hasText(text)).fetchSemanticsNodes().single()
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    /** Dart's `_reveal`: scrolls the body list until [tag] is on screen. */
    private fun reveal(tag: String) {
        compose.tag("music-box-list").performScrollToNode(hasTestTag(tag))
        compose.waitForIdle()
    }

    private fun openGroup(type: MusicBoxActiveSourceType) {
        reveal(groupHeader(type))
        click(groupHeader(type))
    }

    private fun unfoldPlaylist(type: MusicBoxActiveSourceType, id: String) {
        reveal(playlistRow(type, id))
        click(playlistRow(type, id))
    }

    private fun openSearch() {
        reveal("music-box-search-toggle")
        click("music-box-search-toggle")
    }

    private fun showActiveSource() {
        click("music-box-active-source-chip")
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
    }

    private fun searchField(): SemanticsNodeInteraction =
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("music-box-search-input")), useUnmergedTree = true)

    private fun hover(tag: String) {
        compose.onNodeWithTag("page").performMouseInput { moveTo(Offset(1f, 1f)) }
        compose.tag(tag).performMouseInput { moveTo(center) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }

    private fun within(inner: DpRect, outer: DpRect) = inner.top >= outer.top - 0.5.dp && inner.bottom <= outer.bottom + 0.5.dp

    private fun centerY(rect: DpRect): Float = (rect.top.value + rect.bottom.value) / 2

    /** The LazyColumn's scroll range (`value` 0 at the top, `value == maxValue` at the end). */
    private fun listScroll() = compose.tag("music-box-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]

    // --- Tests ---------------------------------------------------------------

    @Test
    fun `music box icon controls expose stable hover tooltips`() {
        val item = musicTrack("tooltip-track", title = "提示测试歌曲")
        setPanel(
            musicState(playbackState = MusicBoxPlaybackState.playing, positionMs = 1000, currentItemId = item.id, queue = listOf(item), temporaryQueue = listOf(item)),
            height = 600.0,
        )
        assertEquals("上一首", tooltip("music-box-transport-previous"))
        assertEquals("暂停", tooltip("music-box-primary-playback"))
        assertEquals("下一首", tooltip("music-box-transport-next"))
        assertEquals("播放模式：顺序播放", tooltip("music-box-transport-mode"))
        assertEquals("关闭音乐盒", tooltip("music-box-close"))
    }

    @Test
    fun `now playing title moves only when it overflows`() {
        val longTitle = "一首需要在音乐盒顶部左右往返显示完整内容的特别长歌曲名称"
        val item = musicTrack("marquee-current", title = longTitle, artist = "测试歌手")
        compose.mainClock.autoAdvance = false
        val host = setPanel(musicState(playbackState = MusicBoxPlaybackState.playing, positionMs = 30000, currentItemId = item.id, queue = listOf(item)))
        compose.mainClock.advanceTimeByFrame()

        val track = "music-box-now-playing:title-marquee-track"
        assertTrue(exists(track))
        // The track scrolls inside a clipped viewport; Dart reads the
        // Transform's translation, here the unclipped bounds move instead.
        val start = compose.tag(track).getUnclippedBoundsInRoot().left
        compose.mainClock.advanceTimeBy(900)
        compose.mainClock.advanceTimeBy(400)
        val firstOffset = compose.tag(track).getUnclippedBoundsInRoot().left - start
        compose.mainClock.advanceTimeBy(400)
        val secondOffset = compose.tag(track).getUnclippedBoundsInRoot().left - start
        assertTrue(firstOffset < 0.dp)
        assertTrue(secondOffset < firstOffset)
        assertTrue(textCount(longTitle) >= 1)

        val shortItem = musicTrack("short-current", title = "短歌名", artist = "测试歌手")
        host.state = musicState(playbackState = MusicBoxPlaybackState.playing, positionMs = 31000, currentItemId = shortItem.id, queue = listOf(shortItem))
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertFalse(exists(track))
        assertTrue(textCount("短歌名") >= 1)
    }

    @Test
    fun `opening the music panel centers the currently playing queue item once`() {
        val queue =
            List(20) { index ->
                musicTrack(
                    "queue-$index",
                    title = if (index % 2 == 0) "第 $index 首歌" else "第 $index 首需要多行显示以验证自适应高度定位的很长歌曲名称",
                    artist = "歌手 $index",
                )
            }
        val host = setPanel(musicState(playbackState = MusicBoxPlaybackState.playing, positionMs = 30000, currentItemId = "queue-10", queue = queue), height = 500.0)

        val currentTile = "music-box-queue-tile:queue-10"
        assertTrue(exists(currentTile))
        assertEquals(centerY(rect("music-box-list")), centerY(rect(currentTile)), 1f)

        compose.tag("music-box-list").performTouchInput { swipe(center, center - Offset(0f, 60.dp.toPx()), durationMillis = 200) }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        val manuallyShiftedCenter = centerY(rect(currentTile))
        host.state =
            musicState(
                playbackState = MusicBoxPlaybackState.playing,
                positionMs = 31000,
                currentItemId = "queue-10",
                queue = queue,
                revision = 1,
                hasRevision = true,
            )
        compose.waitForIdle()
        assertEquals(manuallyShiftedCenter, centerY(rect(currentTile)), 1f)
    }

    private fun boundaryCase(currentIndex: Int) {
        val queue = List(12) { index -> musicTrack("boundary-$index", title = "第 $index 首歌") }
        // The deck and the group headers under the queue leave a 500 dp
        // panel's list short enough that the last queue row can still be
        // centered; 600 dp makes centering either end hit the boundary.
        setPanel(
            musicState(playbackState = MusicBoxPlaybackState.playing, positionMs = 30000, currentItemId = "boundary-$currentIndex", queue = queue),
            height = 600.0,
        )
        val listRect = rect("music-box-list")
        val tileRect = rect("music-box-queue-tile:boundary-$currentIndex")
        val scroll = listScroll()
        assertTrue(within(tileRect, listRect))
        if (currentIndex == 0) {
            assertEquals(0f, scroll.value(), 0f)
            // Centering would scroll above the start, so the reveal clamps.
            assertTrue(centerY(tileRect) < centerY(listRect) - 1)
        } else {
            // Lazy lists only expose whether they can still scroll forward.
            assertEquals(scroll.maxValue(), scroll.value(), 0f)
            // Centering would scroll past the end, so the reveal clamps.
            assertTrue(centerY(tileRect) > centerY(listRect) + 1)
        }
    }

    @Test
    fun `opening the music panel keeps the top current item at its boundary`() = boundaryCase(0)

    @Test
    fun `opening the music panel keeps the bottom current item at its boundary`() = boundaryCase(11)

    @Test
    fun `source picker hides QQ Music and normalizes a legacy selection`() {
        setPanel(musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0), source = "tencent")
        openSearch()
        assertEquals(0, textCount("QQ音乐"))
        assertEquals(1, textCount("网易云"))
        assertEquals(1, textCount("哔哩哔哩"))
        // The selected thumb sits under 网易云.
        val thumb = compose.onNode(hasTestTag(segmentedControlThumbTag) and hasAnyAncestor(hasTestTag("music-box-search-source")), useUnmergedTree = true)
            .dpBounds(compose.density.density)
        val netease = textRect("网易云")
        val neteaseCenter = (netease.left.value + netease.right.value) / 2
        assertTrue(neteaseCenter > thumb.left.value && neteaseCenter < thumb.right.value)
    }

    @Test
    fun `members without control see a locked transport line`() {
        setPanel(
            musicState(playbackState = MusicBoxPlaybackState.playing, positionMs = 1000, capabilities = MusicBoxCapabilities(canControl = false)),
            height = 500.0,
        )
        assertTrue(exists("music-box-transport-locked"))
        assertEquals(1, textCount("播放由房间管理员控制"))
        assertFalse(exists("music-box-primary-playback"))
        assertFalse(exists("music-box-transport-previous"))
        assertFalse(exists("music-box-transport-next"))
        assertFalse(exists("music-box-transport-mode"))
    }

    @Test
    fun `now playing names the requester only for the request queue`() {
        val item =
            musicTrack(
                "req",
                requestedBy = MusicBoxRequester(userId = "requester", displayName = "点歌用户", avatarUrl = null, defaultAvatarKey = "blue-3"),
            )
        val host = setPanel(musicState(playbackState = MusicBoxPlaybackState.playing, positionMs = 0, currentItemId = item.id, queue = listOf(item)), height = 500.0)
        val chip = "music-box-now-playing:requester"
        assertTrue(exists(chip))
        assertEquals(1, withinCount(chip, hasText("点歌用户")))

        host.state =
            musicState(playbackState = MusicBoxPlaybackState.playing, positionMs = 0, currentItemId = item.id, queue = listOf(item), activeSource = roomPlaylistSource)
        compose.waitForIdle()
        assertFalse(exists(chip))
    }

    @Test
    fun `group header of the active scope shows the playing state`() {
        val wave = hasIcon(Icons.Filled.GraphicEq)
        val queueHeader = "music-box-queue-header"
        val roomHeader = groupHeader(MusicBoxActiveSourceType.roomPlaylist)
        val myHeader = groupHeader(MusicBoxActiveSourceType.userPlaylist)
        val host = setPanel(musicState(playbackState = MusicBoxPlaybackState.playing, positionMs = 0), height = 500.0)
        assertEquals(1, withinCount(queueHeader, hasText("点歌队列 · 1 首 · 正在播放")))
        assertEquals(1, withinCount(queueHeader, wave))
        assertEquals(0, withinCount(roomHeader, wave))
        assertEquals(0, withinCount(myHeader, wave))

        host.state = musicState(playbackState = MusicBoxPlaybackState.paused, positionMs = 0, activeSource = roomPlaylistSource)
        compose.waitForIdle()
        assertEquals(1, withinCount(roomHeader, wave))
        assertEquals(1, withinCount(queueHeader, hasText("点歌队列 · 0 首 · 未播放")))
        assertEquals(0, withinCount(queueHeader, wave))
        assertEquals(0, withinCount(myHeader, wave))

        host.state = musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0, activeSource = roomPlaylistSource)
        compose.waitForIdle()
        assertEquals(0, compose.countOf(wave))
    }

    @Test
    fun `playlist groups stay collapsed and unloaded until opened`() {
        val state = musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0)
        val api = RoomPlaylistApiFake(state, playlist = roomPlaylist, items = listOf(roomPlaylistItem))
        setPanel(state, height = 500.0, controller = MusicBoxController(api), roomId = "room-1", currentUser = playlistCurrentUser, room = playlistRoom)

        assertTrue(exists("music-box-queue-tile:a"))
        assertTrue(exists(groupHeader(MusicBoxActiveSourceType.roomPlaylist)))
        assertTrue(exists(groupHeader(MusicBoxActiveSourceType.userPlaylist)))
        assertFalse(groupOpen(MusicBoxActiveSourceType.roomPlaylist))
        assertFalse(groupOpen(MusicBoxActiveSourceType.userPlaylist))
        assertFalse(exists(playlistRow(MusicBoxActiveSourceType.roomPlaylist, "room-list")))
        assertEquals(0, api.roomListCalls)
        assertEquals(0, api.personalListCalls)

        openGroup(MusicBoxActiveSourceType.roomPlaylist)
        assertEquals(1, api.roomListCalls)
        assertEquals(0, api.personalListCalls)
        assertTrue(groupOpen(MusicBoxActiveSourceType.roomPlaylist))
        assertFalse(groupOpen(MusicBoxActiveSourceType.userPlaylist))
        assertTrue(exists(playlistRow(MusicBoxActiveSourceType.roomPlaylist, "room-list")))
    }

    @Test
    fun `room playlist group lists playlists and unfolds one in place`() {
        val state = musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0)
        val api =
            RoomPlaylistApiFake(
                state,
                playlist = roomPlaylist,
                items = listOf(roomPlaylistItem),
                roomPlaylists =
                    listOf(
                        roomPlaylist,
                        PersonalMusicPlaylist(id = "room-list-2", name = "房间第二单", description = "", revision = 1, itemCount = 3, createdAt = null, updatedAt = null),
                    ),
            )
        setPanel(state, height = 500.0, controller = MusicBoxController(api), roomId = "room-1", currentUser = playlistCurrentUser, room = playlistRoom)

        openGroup(MusicBoxActiveSourceType.roomPlaylist)
        assertEquals(1, withinCount(groupHeader(MusicBoxActiveSourceType.roomPlaylist), hasText("2")))
        val row = playlistRow(MusicBoxActiveSourceType.roomPlaylist, "room-list")
        assertEquals(1, withinCount(row, hasText("房间收藏")))
        assertTrue(exists(playlistRow(MusicBoxActiveSourceType.roomPlaylist, "room-list-2")))
        assertTrue(exists("music-box-queue-tile:a"))
        assertEquals(0, api.roomPlaylistLoads)

        unfoldPlaylist(MusicBoxActiveSourceType.roomPlaylist, "room-list")
        assertEquals(1, api.roomPlaylistLoads)
        val tracks = "music-box-playlist-tracks:room-list"
        val track = "music-box-playlist-track:netease:track-1"
        assertEquals(1, withinCount(tracks, hasTestTag(track)))
        assertTrue(within(rect(row), rect("music-box-list")))
        assertTrue(rect(track).top > rect(row).top)
        reveal(playlistRow(MusicBoxActiveSourceType.roomPlaylist, "room-list-2"))
        assertTrue(rect(playlistRow(MusicBoxActiveSourceType.roomPlaylist, "room-list-2")).top > rect(track).top)
        assertNull(api.activatedSourceType)

        reveal(row)
        click(row)
        assertFalse(exists(tracks))
        assertFalse(exists(track))
        assertTrue(exists(row))
    }

    @Test
    fun `a song inside a playlist activates the playlist from itself`() {
        val state = musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0)
        val api = RoomPlaylistApiFake(state, playlist = roomPlaylist, items = listOf(roomPlaylistItem))
        setPanel(state, height = 500.0, controller = MusicBoxController(api), roomId = "room-1", currentUser = playlistCurrentUser, room = playlistRoom)
        openGroup(MusicBoxActiveSourceType.roomPlaylist)

        // The playlist row itself carries no play action.
        assertFalse(exists("music-box-playlist-play-all:room_playlist:room-list"))
        assertNull(api.activatedSourceType)

        unfoldPlaylist(MusicBoxActiveSourceType.roomPlaylist, "room-list")
        val trackPlay = "music-box-playlist-track-play:netease:track-1"
        assertEquals("从这首歌开始播放歌单", actionLabel(trackPlay))
        click(trackPlay)
        assertEquals(MusicBoxActiveSourceType.roomPlaylist, api.activatedSourceType)
        assertEquals("room-list", api.activatedPlaylistId)
        assertEquals("room-list-item", api.activatedStartItemId)
    }

    @Test
    fun `active source chip unfolds the playing playlist in its group`() {
        val snapshot = listOf(musicTrack("snap-1", title = "快照第一首"), musicTrack("snap-2", title = "快照第二首"))
        val state =
            musicState(
                playbackState = MusicBoxPlaybackState.playing,
                positionMs = 1000,
                currentItemId = "snap-1",
                queue = snapshot,
                temporaryQueue = listOf(musicTrack("req-1", title = "待点歌曲")),
                activeSource = roomPlaylistSource,
            )
        val api = RoomPlaylistApiFake(state, playlist = roomPlaylist, items = listOf(roomPlaylistItem))
        setPanel(state, height = 500.0, controller = MusicBoxController(api), roomId = "room-1", currentUser = playlistCurrentUser, room = playlistRoom)
        assertTrue(exists("music-box-queue-tile:req-1"))
        assertFalse(exists("music-box-queue-tile:snap-1"))

        showActiveSource()

        assertFalse(exists("music-box-search-panel"))
        assertTrue(groupOpen(MusicBoxActiveSourceType.roomPlaylist))
        val row = playlistRow(MusicBoxActiveSourceType.roomPlaylist, "room-list")
        assertTrue(exists(row))
        val tracks = "music-box-playlist-tracks:room-list"
        assertEquals(1, withinCount(tracks, hasTestTag("music-box-queue-tile:snap-1")))
        assertEquals(1, withinCount(tracks, hasTestTag("music-box-queue-tile:snap-2")))
        assertTrue(rect("music-box-queue-tile:snap-1").top > rect(row).top)
        assertTrue(exists("music-box-queue-leading-playing:snap-1"))
        assertFalse(exists("music-box-queue-remove:snap-1"))
        assertFalse(exists("music-box-playlist-track:netease:track-1"))
        assertEquals(0, api.roomPlaylistLoads)
        // Nothing plays a playlist as a whole; songs inside carry playback.
        assertFalse(exists("music-box-playlist-play-all:room_playlist:room-list"))
        assertNull(api.activatedSourceType)
        // The request queue keeps its own row above the viewport. The lazy
        // list disposes offscreen rows (Dart finds it with skipOffstage:
        // false), so scroll back to it.
        reveal("music-box-queue-tile:req-1")
        assertTrue(exists("music-box-queue-tile:req-1"))
    }

    @Test
    fun `active source chip scrolls back to the request queue`() {
        val queue = List(15) { index -> musicTrack("req-$index", title = "点歌 $index") }
        val state = musicState(playbackState = MusicBoxPlaybackState.playing, positionMs = 0, currentItemId = "req-0", queue = queue)
        val api = RoomPlaylistApiFake(state, playlist = roomPlaylist, items = listOf(roomPlaylistItem))
        setPanel(state, height = 500.0, controller = MusicBoxController(api), roomId = "room-1", currentUser = playlistCurrentUser, room = playlistRoom)
        openGroup(MusicBoxActiveSourceType.roomPlaylist)
        unfoldPlaylist(MusicBoxActiveSourceType.roomPlaylist, "room-list")
        val listRect = rect("music-box-list")
        val queueHeader = "点歌队列 · 15 首 · 正在播放"
        // The header is above the viewport: either still composed above it or
        // already disposed by the lazy list (it is the list's first item).
        if (textCount(queueHeader) > 0) assertTrue(textRect(queueHeader).top < listRect.top) else assertTrue(listScroll().value() > 0f)
        assertTrue(exists("music-box-playlist-track:netease:track-1"))

        showActiveSource()

        assertTrue(within(textRect(queueHeader), listRect))
        assertTrue(exists("music-box-queue-tile:req-0"))
        // Revealing the queue does not fold what the user opened below it.
        reveal("music-box-playlist-tracks:room-list")
        assertTrue(exists("music-box-playlist-tracks:room-list"))
    }

    @Test
    fun `a queue song switches the room back to the request queue`() {
        val state =
            musicState(
                playbackState = MusicBoxPlaybackState.playing,
                positionMs = 0,
                currentItemId = "snap-1",
                queue = listOf(musicTrack("snap-1", title = "快照歌曲")),
                temporaryQueue = listOf(musicTrack("req-1", title = "第一首点歌", canPlayNow = false), musicTrack("req-2", title = "第二首点歌", canPlayNow = false)),
                activeSource = roomPlaylistSource,
            )
        val api = MusicBoxApiFake(state)
        setPanel(state, height = 500.0, controller = MusicBoxController(api), roomId = "room-1")

        assertEquals(1, textCount("点歌队列 · 2 首 · 未播放"))
        assertFalse(exists("music-box-queue-tile:snap-1"))
        assertFalse(exists("music-box-queue-play-all"))
        val play = "music-box-queue-play-now:req-2"
        assertEquals("从这首歌开始播放点歌队列", actionLabel(play))
        click(play)
        assertEquals(MusicBoxActiveSourceType.temporary, api.activatedSourceType)
        assertNull(api.activatedPlaylistId)
        assertEquals("req-2", api.activatedStartItemId)
        assertNull(api.action)
    }

    @Test
    fun `queue row play-now jumps to that request`() {
        val state =
            musicState(
                playbackState = MusicBoxPlaybackState.playing,
                positionMs = 0,
                currentItemId = "cur",
                queue = listOf(musicTrack("cur", title = "正在播放的歌", canPlayNow = true), musicTrack("next", title = "插队歌曲", canPlayNow = true)),
            )
        val api = MusicBoxApiFake(state)
        setPanel(state, height = 500.0, controller = MusicBoxController(api), roomId = "room-1")

        assertEquals(1, textCount("点歌队列 · 2 首 · 正在播放"))
        assertFalse(exists("music-box-queue-play-all"))
        assertFalse(exists("music-box-queue-play-now:cur"))
        click("music-box-queue-play-now:next")

        assertEquals("play_now", api.action)
        assertEquals("next", api.itemId)
        assertEquals(1, textCount("已优先播放"))
    }

    @Test
    fun `priority play from the song card uses the floating notice`() {
        val state =
            musicState(
                playbackState = MusicBoxPlaybackState.playing,
                positionMs = 0,
                currentItemId = "another-track",
                queue = listOf(musicTrack("priority-track", title = "优先歌曲", canPlayNow = true)),
            )
        setPanel(state, height = 500.0, controller = MusicBoxController(MusicBoxApiFake(state)), roomId = "room-1")

        clickText("优先歌曲")
        compose.onNode(hasText("优先播放") and hasAnyAncestor(hasTestTag("music-box-song-card:priority-track"))).performClick()
        compose.waitForIdle()

        assertEquals(1, textCount("已优先播放"))
    }

    @Test
    fun `queue row remove button appears only on removable requests`() {
        val removed = mutableListOf<MusicBoxQueueItem>()
        setPanel(
            musicState(
                playbackState = MusicBoxPlaybackState.stopped,
                positionMs = 0,
                currentItemId = "",
                queue = listOf(musicTrack("removable", title = "可删除"), musicTrack("locked", title = "不可删除", canRemove = false)),
            ),
            height = 500.0,
            onRemoveItem = { removed.add(it) },
        )

        val remove = "music-box-queue-remove:removable"
        assertEquals("从点歌队列删除", actionLabel(remove))
        click(remove)
        assertEquals(listOf("removable"), removed.map { it.id })

        assertFalse(exists("music-box-queue-remove:locked"))
        assertTrue(exists("music-box-queue-tile:locked"))
    }

    @Test
    fun `clearing the request queue asks first then clears`() {
        val state = musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0, currentItemId = "", queue = listOf(musicTrack("req", title = "点歌队列歌曲")))
        val api = MusicBoxApiFake(state)
        setPanel(state, height = 500.0, controller = MusicBoxController(api), roomId = "room-1")

        click("music-box-queue-clear")
        assertEquals(1, textCount("确认清空当前点歌队列？此操作不会删除已保存的歌单。"))
        assertNull(api.action)
        click("music-box-confirm-clear-temporary-queue")
        assertEquals("clear_temporary_playlist", api.action)
    }

    @Test
    fun `search toggle unfolds a panel whose query replaces the list`() {
        val searchController = TextEditingController()
        val queued = mutableListOf<MusicBoxSearchResult>()
        val fresh = MusicBoxSearchResult(trackId = "qingtian", name = "晴天", artists = listOf("周杰伦"), source = "netease")
        val alreadyQueued = MusicBoxSearchResult(trackId = "already-queued-track", name = "已点歌曲", artists = listOf("歌手"), source = "netease")
        val queuedItem = musicTrack("queued-item", trackId = "already-queued-track", title = "已点歌曲")
        setPanel(
            musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0, currentItemId = "", queue = listOf(queuedItem), temporaryQueue = listOf(queuedItem)),
            searchController = searchController,
            height = 500.0,
            searchResults = listOf(fresh, alreadyQueued),
            onQueueResult = { queued.add(it) },
        )
        val roomHeader = groupHeader(MusicBoxActiveSourceType.roomPlaylist)
        val myHeader = groupHeader(MusicBoxActiveSourceType.userPlaylist)
        val toggle = "music-box-search-toggle"
        assertFalse(exists("music-box-search-panel"))
        assertFalse(exists("music-box-search-input"))
        assertEquals("搜索歌曲点歌", actionLabel(toggle))

        openSearch()
        assertTrue(exists("music-box-search-panel"))
        assertEquals("收起搜索", actionLabel(toggle))
        searchField().assertIsFocused()
        assertTrue(exists("music-box-queue-tile:queued-item"))
        assertFalse(exists("music-box-search-results-list"))
        assertTrue(exists(roomHeader))

        searchField().performTextReplacement("晴天")
        compose.waitForIdle()

        assertTrue(compose.tagCount("music-box-search-results-list") >= 1)
        assertEquals(1, textCount("搜索结果 · 网易云 · 2 首"))
        assertFalse(exists("music-box-queue-tile:queued-item"))
        assertFalse(exists(roomHeader))
        assertFalse(exists(myHeader))

        val add = "music-box-search-add:netease:qingtian"
        assertEquals("加入点歌队列", actionLabel(add))
        click(add)
        assertEquals(listOf(fresh), queued)

        val queuedAdd = "music-box-search-add:netease:already-queued-track"
        assertEquals("已在点歌队列", actionLabel(queuedAdd))
        assertFalse(actionEnabled(queuedAdd))
        click(queuedAdd)
        assertEquals(listOf(fresh), queued)

        compose.onNode(hasTooltip("清空搜索"), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertTrue(searchController.text.isEmpty())
        assertTrue(exists("music-box-search-panel"))
        assertEquals(0, compose.tagCount("music-box-search-results-list"))
        assertTrue(exists("music-box-queue-tile:queued-item"))
        assertTrue(exists(roomHeader))

        searchField().performTextReplacement("晴天")
        compose.waitForIdle()
        assertTrue(compose.tagCount("music-box-search-results-list") >= 1)

        click(toggle)
        assertTrue(searchController.text.isEmpty())
        assertFalse(exists("music-box-search-panel"))
        assertEquals(0, compose.tagCount("music-box-search-results-list"))
        assertTrue(exists("music-box-queue-tile:queued-item"))
        assertTrue(exists(roomHeader))
        assertTrue(exists(myHeader))
    }

    @Test
    fun `empty playlist groups offer the matching first-playlist action`() {
        var personalCreateCount = 0
        var roomCreateCount = 0
        val state = musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0)
        val api = RoomPlaylistApiFake(state, playlist = roomPlaylist, items = emptyList(), roomPlaylists = emptyList())
        setPanel(
            state,
            height = 500.0,
            controller = MusicBoxController(api),
            roomId = "room-1",
            currentUser = playlistCurrentUser,
            room = playlistRoom,
            onCreateFirstPersonalPlaylist = { personalCreateCount += 1 },
            onCreateFirstRoomPlaylist = { roomCreateCount += 1 },
        )

        openGroup(MusicBoxActiveSourceType.userPlaylist)
        assertEquals(1, textCount("还没有个人歌单"))
        val createPersonal = "music-box-create-first-personal-playlist"
        reveal(createPersonal)
        click(createPersonal)
        assertEquals(1, personalCreateCount)
        assertEquals(0, roomCreateCount)

        openGroup(MusicBoxActiveSourceType.roomPlaylist)
        assertEquals(1, textCount("还没有房间歌单"))
        val createRoom = "music-box-create-first-room-playlist"
        reveal(createRoom)
        click(createRoom)
        assertEquals(1, personalCreateCount)
        assertEquals(1, roomCreateCount)
        // Dart finds the personal group's empty state offstage; the lazy list
        // disposes it, so scroll to it instead.
        compose.tag("music-box-list").performScrollToNode(hasText("还没有个人歌单"))
        assertEquals(1, textCount("还没有个人歌单"))
    }

    @Test
    fun `my playlists pins an active playlist the viewer cannot list`() {
        val mine = PersonalMusicPlaylist(id = "mine", name = "我的收藏", description = "", revision = 1, itemCount = 5, createdAt = null, updatedAt = null)
        val state =
            musicState(
                playbackState = MusicBoxPlaybackState.stopped,
                positionMs = 0,
                currentItemId = "",
                queue = listOf(musicTrack("snap-1"), musicTrack("snap-2")),
                activeSource =
                    MusicBoxActiveSource(type = MusicBoxActiveSourceType.userPlaylist, id = "other-playlist", name = "夜晚精选", ownerUserId = "other-user"),
            )
        val api = RoomPlaylistApiFake(state, playlist = roomPlaylist, items = emptyList(), personalPlaylists = listOf(mine))
        setPanel(state, height = 620.0, controller = MusicBoxController(api), roomId = "room-1", currentUser = playlistCurrentUser, room = playlistRoom)
        openGroup(MusicBoxActiveSourceType.userPlaylist)

        assertEquals(1, withinCount(groupHeader(MusicBoxActiveSourceType.userPlaylist), hasText("2")))
        val pinned = playlistRow(MusicBoxActiveSourceType.userPlaylist, "other-playlist")
        val own = playlistRow(MusicBoxActiveSourceType.userPlaylist, "mine")
        assertEquals(1, withinCount(pinned, hasText("夜晚精选")))
        assertEquals(1, withinCount(pinned, hasText("2 首")))
        reveal(own)
        assertEquals(1, withinCount(pinned, hasText("夜晚精选")))
        assertEquals(1, withinCount(own, hasText("5 首")))
        assertTrue(rect(pinned).top < rect(own).top)
    }

    @Test
    fun `playlist row card views a read-only playlist in place`() {
        val state = musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0)
        val api = RoomPlaylistApiFake(state, playlist = roomPlaylist, items = listOf(roomPlaylistItem))
        setPanel(state, height = 500.0, controller = MusicBoxController(api), roomId = "room-1", currentUser = playlistCurrentUser, room = playlistRoom)
        openGroup(MusicBoxActiveSourceType.roomPlaylist)

        click("music-box-playlist-card-anchor:room-list")
        assertTrue(exists(musicPlaylistCardTag("room-list")))
        assertEquals(1, textCount("查看歌单"))
        assertEquals(0, textCount("编辑歌单"))
        assertFalse(exists("music-box-playlist-tracks:room-list"))

        click(musicPlaylistCardViewTag)
        val row = playlistRow(MusicBoxActiveSourceType.roomPlaylist, "room-list")
        assertTrue(exists(row))
        assertEquals(1, withinCount("music-box-playlist-tracks:room-list", hasTestTag("music-box-playlist-track:netease:track-1")))
        assertNull(api.activatedSourceType)
    }

    @Test
    fun `preset track song card offers no queue action`() {
        val state = musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0)
        val api = RoomPlaylistApiFake(state, playlist = roomPlaylist, items = listOf(roomPlaylistItem))
        setPanel(state, height = 500.0, controller = MusicBoxController(api), roomId = "room-1", currentUser = playlistCurrentUser, room = playlistRoom)
        openGroup(MusicBoxActiveSourceType.roomPlaylist)
        unfoldPlaylist(MusicBoxActiveSourceType.roomPlaylist, "room-list")

        clickTextWithin("music-box-playlist-track:netease:track-1", "房间歌曲")
        val card = "music-box-song-card:search:netease:track-1"
        assertTrue(exists(card))
        assertEquals(0, withinCount(card, hasText("点歌队列")))
        assertEquals(0, withinCount(card, hasText("已在队列中")))
        assertEquals(1, withinCount(card, hasText("添加到歌单")))
    }

    @Test
    fun `downloading queue item shows a spinner as its leading glyph`() {
        val item = musicTrack("downloading-track", title = "正在下载的歌曲", status = MusicBoxQueueItemStatus.downloading)
        setPanel(musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0, queue = listOf(item), temporaryQueue = listOf(item)), height = 500.0)

        val loading = "music-box-queue-leading-loading:downloading-track"
        assertTrue(exists(loading))
        assertFalse(exists("music-box-queue-leading-index:downloading-track"))
        val loadingRect = rect(loading)
        assertTrue((loadingRect.left.value + loadingRect.right.value) / 2 < textRect("正在下载的歌曲").left.value)
    }

    @Test
    fun `streaming download is offered for play-now like a ready song`() {
        val state =
            musicState(
                playbackState = MusicBoxPlaybackState.playing,
                positionMs = 0,
                currentItemId = "cur",
                queue =
                    listOf(
                        musicTrack("cur", title = "正在播放的歌", canPlayNow = true),
                        musicTrack("streaming", title = "边下边播", status = MusicBoxQueueItemStatus.downloading, canPlayNow = true, playable = true),
                        // An older server sends can_play_now only for ready songs, but a
                        // downloading row without `playable` must stay unplayable regardless.
                        musicTrack("cold", title = "刚开始下载", status = MusicBoxQueueItemStatus.downloading, canPlayNow = true),
                    ),
            )
        val api = MusicBoxApiFake(state)
        setPanel(state, height = 500.0, controller = MusicBoxController(api), roomId = "room-1")

        assertFalse(exists("music-box-queue-leading-loading:streaming"))
        assertTrue(exists("music-box-queue-leading-loading:cold"))
        assertFalse(exists("music-box-queue-play-now:cold"))
        click("music-box-queue-play-now:streaming")

        assertEquals("play_now", api.action)
        assertEquals("streaming", api.itemId)
    }

    @Test
    fun `search result opens a song card with an explicit queue action`() {
        val searchController = TextEditingController("绝不认输")
        val queued = mutableListOf<MusicBoxSearchResult>()
        val result = MusicBoxSearchResult(trackId = "BV1xx411c7mD", name = "《Hi-Res无损音质》｜《绝不认输》完整歌曲标题", artists = listOf("VV音乐局"), source = "bilibili")
        setPanel(
            musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0),
            searchController = searchController,
            height = 500.0,
            searchResults = listOf(result),
            onQueueResult = { queued.add(it) },
        )
        openSearch()

        val tile = "music-box-search-tile:bilibili:BV1xx411c7mD"
        assertTrue(exists(tile))
        assertEquals(1, withinCount(tile, hasIcon(Icons.Filled.MusicNote)))
        assertEquals(0, withinCount(tile, hasText("哔哩哔哩")))

        click(tile)
        val songCard = "music-box-song-card:search:bilibili:BV1xx411c7mD"
        assertTrue(exists(songCard))
        assertEquals(0, textCount("点歌人"))
        assertEquals(0, withinCount(songCard, hasText("歌单")))
        assertEquals(1, textCount("作者"))
        assertEquals(1, textCount("详情"))
        assertEquals(1, textCount("BV1xx411c7mD"))
        clickTextWithin(songCard, "点歌队列")
        assertEquals(listOf(result), queued)
    }

    @Test
    fun `queued catalog track disables the song card queue action`() {
        val searchController = TextEditingController("已点歌曲")
        val queued = mutableListOf<MusicBoxSearchResult>()
        val result = MusicBoxSearchResult(trackId = "already-queued-track", name = "已点歌曲", artists = listOf("歌手"), source = "netease")
        setPanel(
            musicState(
                playbackState = MusicBoxPlaybackState.stopped,
                positionMs = 0,
                temporaryQueue = listOf(musicTrack("queued-item", trackId = "already-queued-track", title = "已点歌曲")),
            ),
            searchController = searchController,
            height = 500.0,
            searchResults = listOf(result),
            onQueueResult = { queued.add(it) },
        )
        openSearch()

        click("music-box-search-tile:netease:already-queued-track")
        val card = "music-box-song-card:search:netease:already-queued-track"
        assertTrue(exists(card))
        compose.onNode(hasText("已在队列中") and hasAnyAncestor(hasTestTag(card))).assertIsNotEnabled()
        assertTrue(queued.isEmpty())
    }

    @Test
    fun `Bilibili queue card shows its BV details after attribution`() {
        val item =
            musicTrack(
                "bilibili-request",
                source = "bilibili",
                trackId = "BV17x411w7KC",
                title = "点歌的哔哩哔哩歌曲",
                requestedBy =
                    MusicBoxRequester(userId = "requester", displayName = "点歌用户", avatarLabel = "点歌用户", avatarUrl = null, defaultAvatarKey = "blue-3"),
            )
        setPanel(musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0, queue = listOf(item), temporaryQueue = listOf(item)), height = 500.0)
        clickText("点歌的哔哩哔哩歌曲")

        assertEquals(1, textCount("点歌人"))
        assertEquals(1, textCount("详情"))
        assertEquals(1, textCount("BV17x411w7KC"))
        assertTrue(textRect("详情").top > textRect("点歌人").top)
    }

    @Test
    fun `android song card metadata supports text selection`() {
        setPanel(musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0), height = 500.0)
        clickTextWithin("music-box-queue-list", "Song")

        val titleField =
            compose.onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Song")) and
                    hasAnyAncestor(hasTestTag("music-box-song-card:a")),
                useUnmergedTree = true,
            )
        titleField.assertExists()
        // Dart selects through the field state and shows the toolbar; here a
        // double tap selects the word and opens the shared selection menu.
        titleField.performTouchInput { doubleClick(Offset(left + 10.dp.toPx(), centerY)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()

        assertTrue(exists(textContextMenuPanelTag))
        assertTrue(exists("music-box-song-card:a"))
    }

    @Test
    fun `android playlist picker shows complete adaptive names and scopes`() {
        val roomName = "一个需要自适应换行完整显示的非常长的房间歌单名称"
        val personalName = "个人歌单"
        val roomPlaylistLong =
            PersonalMusicPlaylist(id = "room-playlist-long", name = roomName, description = "", revision = 1, itemCount = 0, createdAt = DateTime(2026, 7, 30, 20, 34), updatedAt = null)
        val personalPlaylist =
            PersonalMusicPlaylist(id = "personal-playlist-long", name = personalName, description = "", revision = 1, itemCount = 0, createdAt = DateTime(2026, 7, 29, 16, 0), updatedAt = null)
        val state = musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0)
        val api = RoomPlaylistApiFake(state, playlist = roomPlaylistLong, items = emptyList(), personalPlaylists = listOf(personalPlaylist))
        setPanel(state, height = 500.0, controller = MusicBoxController(api), roomId = "room-1", currentUser = playlistCurrentUser, room = playlistRoom)
        clickTextWithin("music-box-queue-list", "Song")
        clickText("添加到歌单")

        val roomTarget = "music-track-playlist-target:room:room-1:room-playlist-long"
        val personalTarget = "music-track-playlist-target:personal-playlist-long"
        assertTrue(exists(roomTarget))
        assertTrue(exists(personalTarget))
        assertEquals(1, textCount(roomName))
        assertEquals(1, textCount(personalName))
        assertEquals(2, textCount("0 首歌曲"))
        assertEquals(0, compose.countOf(hasText("房间 ·", substring = true)))
        assertEquals(0, compose.countOf(hasText("我的 ·", substring = true)))
        assertTrue(rect(roomTarget).heightDp > 64.dp)
        assertTrue(rect(personalTarget).heightDp >= 64.dp)
        assertEquals(0, compose.countOf(hasIcon(Icons.Outlined.Person)))
        assertEquals(0, compose.countOf(hasIcon(Icons.Outlined.MeetingRoom)))
        assertEquals(1, withinCount(roomTarget, hasIcon(Icons.Filled.MeetingRoom)))
        assertEquals(1, withinCount(personalTarget, hasIcon(Icons.Filled.Person)))
        // Dart: no maxLines and no overflow, so the long name is never cut.
        val roomNameLayout = compose.onNodeWithText(roomName, useUnmergedTree = true).textLayout()
        assertFalse(roomNameLayout.hasVisualOverflow)
        assertFalse(actionEnabled(musicTrackPlaylistTargetConfirmTag))
        val target = rect(roomTarget)
        compose.onNodeWithTag("page").performTouchInput { click(Offset((target.left + 3.dp).toPx(), (target.top + 3.dp).toPx())) }
        compose.waitForIdle()
        assertTrue(actionEnabled(musicTrackPlaylistTargetConfirmTag))
        assertEquals(1, withinCount(roomTarget, hasIcon(Icons.Filled.RadioButtonChecked)))
    }

    @Test
    fun `long queue rows grow and keep every title and artist line`() {
        val longTitle = "[Hi-Res lossless] | A very long song title that must remain complete across as many lines as the narrow queue needs"
        val item = musicTrack("long-queue-item", title = longTitle, artist = "An equally long artist name that must wrap without truncation")
        setPanel(musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0, queue = listOf(item), temporaryQueue = listOf(item)), height = 500.0)

        val tile = "music-box-queue-tile:long-queue-item"
        assertTrue(rect(tile).heightDp > 82.dp)
        val title = compose.onNode(hasText(longTitle) and hasAnyAncestor(hasTestTag(tile)), useUnmergedTree = true).textLayout()
        assertTrue(title.lineCount >= 2)
        assertFalse(title.hasVisualOverflow)
        assertTrue(title.layoutInput.style.fontSize.value < 13f)
    }

    @Test
    fun `song requester avatar stays next to the right-aligned name`() {
        val item =
            musicTrack(
                "requester-position",
                artist = "Artist",
                requestedBy =
                    MusicBoxRequester(userId = "requester", displayName = "testxxxx", avatarLabel = "testxxxx", avatarUrl = null, defaultAvatarKey = "blue-3"),
            )
        setPanel(
            musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0, queue = listOf(item), temporaryQueue = listOf(item)),
            height = 500.0,
            onResolveUserProfile = {
                UserSummary(
                    id = "requester",
                    username = "requester_handle",
                    displayName = "testxxxx",
                    avatarUrl = null,
                    defaultAvatarKey = "blue-3",
                    uid = "10001",
                    roomRole = "admin",
                )
            },
            userProfileActionBuilder = { UserProfileAction(label = "管理成员", icon = Icons.Outlined.ManageAccounts, onPressed = {}) },
        )
        clickTextWithin("music-box-queue-tile:requester-position", "Song")

        val group = "music-box-song-attribution-value-group"
        val avatar = "music-box-song-attribution-avatar"
        val nameTag = "music-box-song-attribution-name"
        val avatarRect = rect(avatar)
        val nameRect = compose.onNode(hasTestTag(nameTag) and hasAnyAncestor(hasTestTag(group)), useUnmergedTree = true).dpBounds(compose.density.density)
        val groupRect = rect(group)
        assertTrue(nameRect.left >= avatarRect.right)
        assertTrue(nameRect.left - avatarRect.right <= 8.dp)
        assertEquals(groupRect.right.value, nameRect.right.value, 1f)
        assertTrue(nameRect.heightDp < 20.dp)
        assertEquals(0, withinCount(nameTag, SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)))

        hover(avatar)

        assertEquals(1, textCount("@requester_handle"))
        assertEquals(1, textCount("管理员"))
        assertEquals(1, textCount("管理成员"))
        assertTrue(exists("music-box-song-card:requester-position"))
        assertTrue(exists(group))
    }

    @Test
    fun `playlist attribution shows a playlist icon and opens its playlist card`() {
        val state =
            musicState(
                playbackState = MusicBoxPlaybackState.stopped,
                positionMs = 0,
                queue = listOf(musicTrack("playlist-owner-width", artist = "Artist")),
                activeSource =
                    MusicBoxActiveSource(
                        type = MusicBoxActiveSourceType.userPlaylist,
                        id = "playlist-123",
                        name = "123",
                        ownerUserId = "playlist-owner",
                        owner = MusicBoxRequester(userId = "playlist-owner", displayName = "testxxx", avatarLabel = "TE", avatarUrl = null, defaultAvatarKey = "blue-3"),
                    ),
            )
        val api = RoomPlaylistApiFake(state, playlist = roomPlaylist, items = emptyList())
        setPanel(state, height = 500.0, controller = MusicBoxController(api), roomId = "room-1")
        showActiveSource()
        clickTextWithin("music-box-playlist-tracks:playlist-123", "Song")

        val attribution = "music-box-song-playlist-attribution"
        assertTrue(exists(attribution))
        val icon = "music-box-song-playlist-icon"
        assertEquals(1, withinCount(attribution, hasTestTag(icon)))
        assertEquals(1, withinCount(attribution, hasText("123")))
        assertEquals(centerY(rect(attribution)), centerY(rect(icon)), 0.6f)
        assertEquals(0, textCount("testxxx的歌单"))
        assertEquals(0, textCount(" · 123"))

        hover(attribution)
        val playlistCard = musicPlaylistCardTag("playlist-123")
        assertTrue(exists(playlistCard))
        assertEquals(1, withinCount(playlistCard, hasText("testxxx")))
    }

    @Test
    fun `owned playlist card edits the exact playlist`() {
        var editedPlaylistId: String? = null
        val state =
            musicState(
                playbackState = MusicBoxPlaybackState.stopped,
                positionMs = 0,
                queue = listOf(musicTrack("owned-playlist-track", title = "Owned song")),
                activeSource =
                    MusicBoxActiveSource(type = MusicBoxActiveSourceType.userPlaylist, id = "owned-playlist", name = "Owned playlist", ownerUserId = "playlist-current-user"),
            )
        val api = RoomPlaylistApiFake(state, playlist = roomPlaylist, items = emptyList())
        setPanel(
            state,
            height = 620.0,
            controller = MusicBoxController(api),
            roomId = "room-1",
            currentUser = playlistCurrentUser,
            room = playlistRoom,
            onEditPersonalPlaylist = { playlistId -> editedPlaylistId = playlistId },
        )
        showActiveSource()
        clickText("Owned song")
        hover("music-box-song-playlist-attribution")

        assertEquals(1, textCount("编辑歌单"))
        assertEquals(0, textCount("查看歌单"))
        click(musicPlaylistCardViewTag)
        assertEquals("owned-playlist", editedPlaylistId)
    }

    @Test
    fun `manageable room playlist card uses the room editor`() {
        var editedPlaylistId: String? = null
        val state =
            musicState(
                playbackState = MusicBoxPlaybackState.stopped,
                positionMs = 0,
                queue = listOf(musicTrack("room-playlist-track", title = "Room song")),
                activeSource = roomPlaylistSource,
            )
        val api = RoomPlaylistApiFake(state, playlist = roomPlaylist, items = emptyList())
        setPanel(
            state,
            height = 620.0,
            controller = MusicBoxController(api),
            roomId = "room-1",
            currentUser = playlistCurrentUser,
            room = playlistRoom,
            onEditRoomPlaylist = { playlistId -> editedPlaylistId = playlistId },
        )
        showActiveSource()
        clickText("Room song")
        hover("music-box-song-playlist-attribution")

        assertEquals(1, textCount("编辑歌单"))
        click(musicPlaylistCardViewTag)
        assertEquals("room-list", editedPlaylistId)
    }

    @Test
    fun `long music titles wrap fully and shrink without ellipses`() {
        // Dart runs this on the Windows theme; the song card layout is shared.
        val searchController = TextEditingController("标题")
        val longTitle = "这是一个用于验证歌曲名片完整显示能力的非常非常长的歌曲标题它需要自动缩小字体并显示为三行四行甚至更多行且不能出现省略号"
        val result = MusicBoxSearchResult(trackId = "long-title", name = longTitle, artists = listOf("一位名字同样很长但必须完整显示的歌手"), source = "netease")
        setPanel(
            musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0),
            searchController = searchController,
            height = 500.0,
            searchResults = listOf(result),
        )
        openSearch()
        val tile = "music-box-search-tile:netease:long-title"
        assertTrue(rect(tile).heightDp > 50.dp)

        click(tile)
        val adaptiveTitle = "music-box-song-card-title"
        val selectableTitle =
            compose.onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(longTitle)) and hasAnyAncestor(hasTestTag(adaptiveTitle)),
                useUnmergedTree = true,
            )
        val layout = selectableTitle.textLayout()
        assertTrue(layout.lineCount > 2)
        assertFalse(layout.hasVisualOverflow)
        assertTrue(layout.layoutInput.style.fontSize.value < 16f)
        assertTrue(rect(adaptiveTitle).heightDp > 38.dp)
        assertEquals(0, compose.countOf(hasText("...", substring = true)))
    }

    @Test
    fun `personal playlist song card shows its playlist identity`() {
        val state =
            musicState(
                playbackState = MusicBoxPlaybackState.stopped,
                positionMs = 0,
                activeSource =
                    MusicBoxActiveSource(
                        type = MusicBoxActiveSourceType.userPlaylist,
                        id = "personal-list",
                        name = "通勤歌单",
                        ownerUserId = "owner",
                        owner = MusicBoxRequester(userId = "owner", displayName = "用户名称", avatarLabel = "全局名称", avatarUrl = "/avatar.png", defaultAvatarKey = "green-2"),
                    ),
            )
        val api = RoomPlaylistApiFake(state, playlist = roomPlaylist, items = emptyList())
        setPanel(state, height = 500.0, controller = MusicBoxController(api), roomId = "room-1")

        showActiveSource()
        clickTextWithin("music-box-playlist-tracks:personal-list", "Song")

        val songCard = "music-box-song-card:a"
        assertTrue(exists(songCard))
        assertEquals(1, withinCount(songCard, hasText("歌单")))
        assertEquals(0, textCount("用户名称的歌单"))
        assertEquals(0, textCount(" · 通勤歌单"))
        assertEquals(0, textCount("点歌人"))
        val attribution = "music-box-song-playlist-attribution"
        assertEquals(1, withinCount(attribution, hasText("通勤歌单")))
        assertEquals(1, withinCount(attribution, hasTestTag("music-box-song-playlist-icon")))

        val attributionRect = rect(attribution)
        compose.onNodeWithTag("page").performTouchInput {
            click(Offset(((attributionRect.left + attributionRect.right) / 2).toPx(), ((attributionRect.top + attributionRect.bottom) / 2).toPx()))
        }
        compose.waitForIdle()
        val playlistCard = musicPlaylistCardTag("personal-list")
        assertTrue(exists(playlistCard))
        assertEquals(1, withinCount(playlistCard, hasText("用户名称")))
    }

    @Test
    fun `music box volume uses a flat surface`() {
        val volumeChanges = mutableListOf<Double>()
        setPanel(musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0), onVolumeChanged = { volumeChanges.add(it) })

        val volume = "music-box-volume-control"
        assertTrue(exists(volume))
        assertEquals(1, withinCount(volume, hasTestTag(uiSliderHandleTag)))
        hover(uiSliderHandleTag)
        assertEquals(1, withinCount(uiSliderHoverLabelTag, hasText("100%")))

        compose.onNode(hasIcon(Icons.AutoMirrored.Filled.VolumeUp) and hasAnyAncestor(hasTestTag(volume)), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf(0.0), volumeChanges)
    }

    @Test
    fun `music box volume follows externally restored mute state`() {
        val host = setPanel(musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0), volume = 1.0)
        val volume = "music-box-volume-control"
        assertEquals(1, withinCount(volume, hasText("100")))

        host.volume = 0.0
        compose.waitForIdle()

        assertEquals(1, withinCount(volume, hasText("00")))
        assertEquals(1, compose.countOf(hasIcon(Icons.AutoMirrored.Filled.VolumeOff)))
        hover(uiSliderHandleTag)
        assertEquals(1, withinCount(uiSliderHoverLabelTag, hasText("0%")))
    }

    @Test
    fun `height pressure shrinks only the body list`() {
        val state = musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0)
        val host = setPanel(state, height = 400.0)
        openSearch()
        val sizes = listOf("music-box-search-input", "music-box-search-source", "music-box-volume-control", "music-box-primary-playback")
        val comfortable = sizes.map { rect(it).let { r -> r.widthDp to r.heightDp } }
        val comfortableListHeight = rect("music-box-list").heightDp

        host.generation += 1
        host.height = 370.0
        compose.waitForIdle()
        openSearch()

        assertEquals(comfortable, sizes.map { rect(it).let { r -> r.widthDp to r.heightDp } })
        assertEquals((comfortableListHeight - 30.dp).value, rect("music-box-list").heightDp.value, 1f)
    }

    @Test
    fun `empty request queue points at the search toggle`() {
        setPanel(musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0, queue = emptyList()), height = 400.0)
        assertEquals(1, textCount("点歌队列为空"))
        assertEquals(1, textCount("点击右上角的搜索按钮点歌"))
        assertTrue(exists("music-box-search-toggle"))
        assertEquals(0, compose.tagCount("music-box-queue-list"))
    }

    @Test
    @Config(qualifiers = "w360dp-h740dp-mdpi")
    fun `Android keyboard overlays the room while the search field stays put`() {
        // Dart: `resizeToAvoidBottomInset: false`; the panel fills the window.
        val host = setPanel(musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0))
        openSearch()
        val beforeKeyboard = rect("music-box-search-input")

        searchField().performClick()
        host.viewInsets = EdgeInsets(bottom = 300.dp)
        compose.waitForIdle()

        val withKeyboard = rect("music-box-search-input")
        assertEquals(beforeKeyboard, withKeyboard)
        assertTrue(withKeyboard.bottom < (740 - 300).dp)
        searchField().assertIsFocused()
    }

    @Test
    fun `android search keeps focus while the panel reaches its minimum height`() {
        val host = setPanel(musicState(playbackState = MusicBoxPlaybackState.stopped, positionMs = 0), height = 460.0)
        openSearch()
        searchField().performClick()
        compose.waitForIdle()
        searchField().assertIsFocused()

        host.height = 400.0
        compose.waitForIdle()

        // Dart also checks `testTextInput.isVisible`; focus is what keeps the IME up.
        searchField().assertIsFocused()
    }

    @Test
    fun `renders the server-reported position`() {
        setPanel(musicState(playbackState = MusicBoxPlaybackState.playing, positionMs = 5000))
        assertEquals(1, textCount("0:05"))
    }

    @Test
    fun `does not advance the position without a fresh snapshot`() {
        setPanel(musicState(playbackState = MusicBoxPlaybackState.playing, positionMs = 5000))
        assertEquals(1, textCount("0:05"))

        // No local ticker: time passing must not move the position. The server
        // is the only thing that advances it, via a new snapshot.
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        assertEquals(1, textCount("0:05"))
    }

    @Test
    fun `updates when a fresh snapshot reports a new position`() {
        val host = setPanel(musicState(playbackState = MusicBoxPlaybackState.playing, positionMs = 5000))
        assertEquals(1, textCount("0:05"))

        host.state = musicState(playbackState = MusicBoxPlaybackState.playing, positionMs = 6000)
        compose.waitForIdle()
        assertEquals(1, textCount("0:06"))
        assertEquals(0, textCount("0:05"))
    }
}
