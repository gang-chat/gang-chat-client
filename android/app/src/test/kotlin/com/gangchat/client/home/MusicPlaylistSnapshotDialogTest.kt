package com.gangchat.client.home

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gangchat.client.app.MusicTrackPreviewAsset
import com.gangchat.client.app.MusicTrackPreviewPlatform
import com.gangchat.client.app.MusicTrackPreviewPlatformFactory
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.DownloadedFile
import com.gangchat.client.protocol.MusicTrackPreviewApi
import com.gangchat.client.protocol.PersonalMusicPlaylist
import com.gangchat.client.protocol.PersonalMusicPlaylistPage
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.showDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Dart's `music_playlist_snapshot_dialog_test.dart` (the Android variant). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-xxhdpi")
class MusicPlaylistSnapshotDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `android playlist snapshot exposes clone and interactive songs`() {
        var cloned = 0
        var added = 0
        val targetPlaylist =
            PersonalMusicPlaylist(id = "personal-1", name = "我的收藏", description = "", revision = 1, itemCount = 0, createdAt = null, updatedAt = null)

        compose.setHomeContent { context ->
            Text(
                "打开",
                modifier =
                    Modifier.clickable {
                        unawaited {
                            showDialog<Unit>(context) { _ ->
                                MusicPlaylistSnapshotDialog(
                                    title = "共享歌单",
                                    tracks =
                                        listOf(
                                            MusicTrackCardData(
                                                id = "shared-track-1",
                                                source = "netease",
                                                trackId = "track-1",
                                                title = "第一首完整歌曲",
                                                artists = listOf("歌手"),
                                                durationMs = 180000,
                                            ),
                                        ),
                                    previewApi = _SnapshotPreviewApi(),
                                    previewPlatformFactory = _SnapshotPreviewFactory(),
                                    loadPersonalPlaylists = {
                                        PersonalMusicPlaylistPage(
                                            playlists = listOf(targetPlaylist),
                                            page = 1,
                                            pageSize = 50,
                                            total = 1,
                                            hasMore = false,
                                            maxPlaylists = 50,
                                            maxPlaylistItems = 500,
                                        )
                                    },
                                    onAddToPlaylist = { _, playlist ->
                                        assertEquals(targetPlaylist.id, playlist.id)
                                        added += 1
                                    },
                                    onClone = {
                                        cloned += 1
                                        PersonalMusicPlaylist(
                                            id = "clone-1",
                                            name = "共享歌单",
                                            description = "",
                                            revision = 1,
                                            itemCount = 1,
                                            createdAt = null,
                                            updatedAt = null,
                                        )
                                    },
                                    contentKey = "shared-playlist-dialog",
                                    cloneButtonKey = "shared-playlist-clone",
                                )
                            }
                        }
                    },
            )
        }

        compose.onNodeWithText("打开").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("shared-playlist-dialog").assertExists()
        compose.onNodeWithText("第一首完整歌曲").assertExists()
        compose.onNodeWithText("克隆到我的歌单").assertExists()

        compose.onNodeWithTag("shared-playlist-clone").performClick()
        compose.waitForIdle()
        assertEquals(1, cloned)
        compose.onNodeWithText("已克隆到我的歌单").assertExists()

        compose.onNodeWithTag(musicPlaylistSnapshotTrackTag("shared-track-1")).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(musicTrackCardTag("shared-track-1")).assertExists()
        compose.onNodeWithTag(musicTrackCardPreviewTag).assertExists()
        compose.onNodeWithTag(musicTrackCardAddToPlaylistTag).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("music-track-playlist-target:personal-1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(musicTrackPlaylistTargetConfirmTag).performClick()
        compose.waitForIdle()
        assertEquals(1, added)
    }
}

private class _SnapshotPreviewApi : MusicTrackPreviewApi {
    override suspend fun downloadMusicTrackPreview(source: String, trackId: String): DownloadedFile =
        DownloadedFile(bytes = byteArrayOf(1, 2, 3), filename = "preview.ogg", mimeType = "audio/ogg")
}

private class _SnapshotPreviewFactory : MusicTrackPreviewPlatformFactory {
    override fun create(): MusicTrackPreviewPlatform = _SnapshotPreviewPlatform()
}

private class _SnapshotPreviewPlatform : MusicTrackPreviewPlatform {
    private val _completed: StreamController<Unit> = StreamController.broadcast()

    override val onCompleted: Stream<Unit> get() = _completed.stream

    override suspend fun findCached(cacheKey: String): MusicTrackPreviewAsset? = null

    override suspend fun store(cacheKey: String, bytes: ByteArray): MusicTrackPreviewAsset = MusicTrackPreviewAsset(cacheKey)

    override suspend fun play(asset: MusicTrackPreviewAsset) {}

    override suspend fun stop() {}

    override suspend fun dispose() = _completed.close()
}
