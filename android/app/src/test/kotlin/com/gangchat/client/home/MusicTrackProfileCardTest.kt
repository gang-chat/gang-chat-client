package com.gangchat.client.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.MusicTrackPreviewAsset
import com.gangchat.client.app.MusicTrackPreviewController
import com.gangchat.client.app.MusicTrackPreviewPlatform
import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.DownloadedFile
import com.gangchat.client.protocol.MusicTrackPreviewApi
import com.gangchat.client.protocol.PersonalMusicPlaylist
import com.gangchat.client.ui.AppNotificationHost
import com.gangchat.client.ui.MediaCacheScope
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiTheme
import com.gangchat.client.ui.testMediaCacheController
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.IconSemanticsKey
import com.gangchat.client.widgets.LocalMediaQuery
import com.gangchat.client.widgets.RootNavigator
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.WindowMediaQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dart's `music_track_profile_card_test.dart` (the Android variant of the
 * per-platform song-card case, plus the shared picker case).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-xxhdpi")
class MusicTrackProfileCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val controllers = mutableListOf<MusicTrackPreviewController>()

    @After
    fun tearDown() {
        compose.runOnIdle { for (controller in controllers) unawaited { controller.dispose() } }
    }

    private fun previewController(api: MusicTrackPreviewApi, platform: MusicTrackPreviewPlatform) =
        MusicTrackPreviewController(api = api, platform = platform).also { controllers += it }

    private var viewInsets by mutableStateOf(EdgeInsets.zero)

    private fun host(content: @Composable () -> Unit) {
        compose.setContent {
            UiTheme {
                WindowMediaQuery {
                    val media = LocalMediaQuery.current.copy(viewInsets = viewInsets)
                    CompositionLocalProvider(LocalMediaQuery provides media) {
                        MediaCacheScope(testMediaCacheController()) {
                            AppNotificationHost {
                                RootNavigator { _ ->
                                    Box(Modifier.fillMaxSize().background(UiColors.background).testTag("page"), contentAlignment = Alignment.TopStart) {
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

    private fun hasIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) = SemanticsMatcher.expectValue(IconSemanticsKey, icon)

    private fun count(matcher: SemanticsMatcher): Int = compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun searchField() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(musicTrackPlaylistTargetSearchTag)))

    @Test
    fun `song card previews and stops when closed on android`() {
        val api = _PreviewApi()
        val player = _PreviewPlatform()
        val controller = previewController(api, player)
        var addedToPlaylist: PersonalMusicPlaylist? = null
        host {
            MusicTrackHoverCard(
                data = MusicTrackCardData(id = "item-1", source = "netease", trackId = "track-1", title = "完整歌曲名称", artists = listOf("歌手"), durationMs = 180000),
                previewController = controller,
                playlists =
                    listOf(
                        PersonalMusicPlaylist(id = "playlist-1", name = "完整歌单名称", description = "", revision = 1, itemCount = 2, createdAt = null, updatedAt = null),
                    ),
                onAddToPlaylist = { playlist -> addedToPlaylist = playlist },
            ) {
                Box(Modifier.size(240.dp, 56.dp).testTag("track-row")) { Text("完整歌曲名称") }
            }
        }

        compose.onNodeWithTag("track-row").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("试听").assertExists()
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("添加到歌单")).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("3:00").assertExists()

        compose.onNodeWithTag(musicTrackCardPreviewTag).performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, count(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)))

        compose.runOnIdle { api.complete() }
        compose.waitForIdle()
        compose.onNodeWithText("取消试听").assertExists()
        assertEquals(1, player.playCalls)

        compose.onNodeWithTag(musicTrackCardAddToPlaylistTag).performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("添加到歌单")).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("全部").assertExists()
        compose.onNodeWithText("房间歌单").assertExists()
        compose.onNodeWithText("完整歌单名称").assertExists()
        compose.onNodeWithTag("music-track-playlist-target:playlist-1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(musicTrackPlaylistTargetConfirmTag).performClick()
        compose.waitForIdle()
        assertEquals("playlist-1", addedToPlaylist?.id)
        compose.onNodeWithText("取消试听").assertExists()

        compose.onNodeWithTag("page").performTouchInput { click(Offset(700.dp.toPx(), 500.dp.toPx())) }
        compose.pump(200)
        compose.onNodeWithText("取消试听").assertDoesNotExist()
        assertTrue(player.stopCalls >= 2)
    }

    @Test
    @Config(qualifiers = "w360dp-h720dp-xxhdpi")
    fun `shared song picker keeps personal and room playlist scopes`() {
        viewInsets = EdgeInsets(bottom = 300.dp)
        val controller = previewController(_PreviewApi(), _PreviewPlatform())
        var addedTarget: MusicTrackPlaylistTarget? = null
        val personal =
            PersonalMusicPlaylist(
                id = "personal-1",
                name = "这是一个需要完整换行显示而不能省略的超长个人歌单名称",
                description = "",
                revision = 1,
                itemCount = 1,
                createdAt = null,
                updatedAt = null,
            )
        val room = PersonalMusicPlaylist(id = "room-1", name = "当前房间歌单", description = "", revision = 1, itemCount = 2, createdAt = null, updatedAt = null)
        host {
            MusicTrackHoverCard(
                data = MusicTrackCardData(id = "shared-track", source = "bilibili", trackId = "BV1test", title = "分享歌曲", artists = listOf("歌手"), durationMs = 0),
                previewController = controller,
                loadPlaylistTargets = {
                    listOf(MusicTrackPlaylistTarget.personal(personal), MusicTrackPlaylistTarget.room(playlist = room, roomId = "room-alpha"))
                },
                onAddToPlaylistTarget = { target -> addedTarget = target },
            ) {
                Box(Modifier.size(240.dp, 56.dp).testTag("shared-track-row")) { Text("分享歌曲") }
            }
        }

        compose.onNodeWithTag("shared-track-row").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("作者").assertExists()
        compose.onNodeWithTag(musicTrackCardAddToPlaylistTag).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("我的歌单").assertExists()
        compose.onNodeWithText("当前房间歌单").assertExists()
        compose.onNodeWithText("这是一个需要完整换行显示而不能省略的超长个人歌单名称").assertExists()
        compose.onNodeWithText("1 首歌曲").assertExists()
        compose.onNodeWithText("2 首歌曲").assertExists()
        assertEquals(0, count(hasIcon(Icons.Outlined.Person)))
        assertEquals(0, count(hasIcon(Icons.Outlined.MeetingRoom)))
        assertEquals(1, count(hasIcon(Icons.Filled.Person)))
        assertEquals(1, count(hasIcon(Icons.Filled.MeetingRoom)))

        viewInsets = EdgeInsets.zero
        compose.waitForIdle()
        compose.onNodeWithText("房间歌单").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("music-track-playlist-target:personal-1").assertDoesNotExist()
        compose.onNodeWithTag("music-track-playlist-target:room:room-alpha:room-1").assertExists()

        compose.onNodeWithText("全部").performClick()
        searchField().performTextReplacement("超长")
        compose.waitForIdle()
        val personalTile = compose.onNodeWithTag("music-track-playlist-target:personal-1")
        personalTile.assertExists()
        assertTrue(personalTile.fetchSemanticsNode().size.height / compose.density.density > 64f)
        compose.onNodeWithTag("music-track-playlist-target:room:room-alpha:room-1").assertDoesNotExist()
        searchField().performTextReplacement("")
        compose.waitForIdle()

        compose.onNodeWithTag("music-track-playlist-target:room:room-alpha:room-1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(musicTrackPlaylistTargetConfirmTag).performClick()
        compose.waitForIdle()

        assertEquals(MusicTrackPlaylistTargetScope.room, addedTarget?.scope)
        assertEquals("room-alpha", addedTarget?.roomId)
        assertEquals("room-1", addedTarget?.playlist?.id)
    }
}

private class _PreviewApi : MusicTrackPreviewApi {
    private val _download = Completer<DownloadedFile>()

    fun complete() {
        _download.complete(DownloadedFile(bytes = byteArrayOf(1, 2, 3), filename = "preview.m4a", mimeType = "audio/mp4"))
    }

    override suspend fun downloadMusicTrackPreview(source: String, trackId: String): DownloadedFile = _download.future.await()
}

private class _PreviewPlatform : MusicTrackPreviewPlatform {
    private val _completed: StreamController<Unit> = StreamController.broadcast(sync = true)
    var playCalls = 0
    var stopCalls = 0

    override val onCompleted: Stream<Unit> get() = _completed.stream

    override suspend fun findCached(cacheKey: String): MusicTrackPreviewAsset? = null

    override suspend fun store(cacheKey: String, bytes: ByteArray): MusicTrackPreviewAsset = MusicTrackPreviewAsset(cacheKey)

    override suspend fun play(asset: MusicTrackPreviewAsset) {
        playCalls += 1
    }

    override suspend fun stop() {
        stopCalls += 1
    }

    override suspend fun dispose() = _completed.close()
}
