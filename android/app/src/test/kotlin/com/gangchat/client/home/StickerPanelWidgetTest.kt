package com.gangchat.client.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.StickerPanelLoadState
import com.gangchat.client.app.StickerPanelSource
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.Sticker
import com.gangchat.client.protocol.StickerPack
import com.gangchat.client.protocol.UploadedAsset
import com.gangchat.client.ui.Button
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.dpBounds
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.IconSemanticsKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Port of test/widget/home/sticker_panel_widget_test.dart. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class StickerPanelWidgetTest {
    @get:Rule
    val compose = createComposeRule()

    private fun hasIcon(icon: ImageVector) = SemanticsMatcher.expectValue(IconSemanticsKey, icon)

    @Test
    fun `empty sticker sources expose their first-upload actions`() {
        var personalUploads = 0
        var roomUploads = 0
        var state by mutableStateOf(StickerPanelLoadState(loaded = true))
        lateinit var hostContext: BuildContext
        compose.setChatContent(width = 560.dp, height = 300.dp) { context ->
            hostContext = context
            StickerPanelForTest(
                state = state,
                onSendSticker = {},
                onRefresh = {},
                onSourceChanged = {},
                onUploadFirstPersonalSticker = { personalUploads += 1 },
                onUploadFirstRoomSticker = { roomUploads += 1 },
            )
        }

        compose.onNodeWithText("暂无个人表情").assertExists()
        val personalAction = compose.onNodeWithTag(stickerPanelUploadFirstPersonalTag)
        personalAction.assertExists()
        compose.onNodeWithText("上传第一个表情").assertExists()
        val expectedActionWidth = Button.minimumWidthForLabel(hostContext, label = "上传第一个表情", hasIcon = true)
        val width = personalAction.dpBounds(compose.density.density).let { it.right - it.left }
        assertEquals(expectedActionWidth.value, width.value, 0.5f)
        assertTrue(width < 560.dp)
        personalAction.performClick()
        assertEquals(1, personalUploads)
        assertEquals(0, roomUploads)

        state = StickerPanelLoadState(source = StickerPanelSource.room, loaded = true)
        compose.waitForIdle()

        compose.onNodeWithText("暂无房间表情").assertExists()
        val roomAction = compose.onNodeWithTag(stickerPanelUploadFirstRoomTag)
        roomAction.assertExists()
        roomAction.performClick()
        assertEquals(1, personalUploads)
        assertEquals(1, roomUploads)
    }

    @Test
    fun `room first-upload action stays hidden without permission`() {
        compose.setChatContent(width = 560.dp, height = 300.dp) { _ ->
            StickerPanelForTest(
                state = StickerPanelLoadState(source = StickerPanelSource.room, loaded = true),
                onSendSticker = {},
                onRefresh = {},
                onSourceChanged = {},
                onUploadFirstPersonalSticker = {},
            )
        }

        compose.onNodeWithText("暂无房间表情").assertExists()
        compose.onNodeWithText("上传第一个表情").assertDoesNotExist()
    }

    @Test
    fun `sticker panel flattens packs without pack headers`() {
        val sent = mutableListOf<String>()
        compose.setChatContent(width = 560.dp, height = 300.dp) { _ ->
            StickerPanelForTest(
                state =
                    StickerPanelLoadState(
                        loaded = true,
                        personalPacks = listOf(pack("saved", "Saved Stickers", listOf("saved_1")), pack("mine", "我的表情包", listOf("mine_1"))),
                    ),
                onSendSticker = { sticker -> sent.add(sticker.id) },
                onRefresh = {},
                onSourceChanged = {},
            )
        }

        compose.onNodeWithText("Saved Stickers").assertDoesNotExist()
        // "我的表情包" is the source switch's segment, not a pack header.
        assertEquals(1, compose.onAllNodesWithText("我的表情包").fetchSemanticsNodes().size)
        val fallbacks = compose.onAllNodes(hasIcon(Icons.Outlined.ImageNotSupported), useUnmergedTree = true)
        assertEquals(2, fallbacks.fetchSemanticsNodes().size)

        fallbacks[0].performClick()

        assertEquals(listOf("saved_1"), sent)
    }

    private fun pack(id: String, name: String, stickerIds: List<String>) =
        StickerPack(
            id = id,
            scope = "personal",
            roomId = null,
            name = name,
            sortOrder = 10,
            updatedAt = DateTime.utc(2026, 6, 9),
            stickers =
                stickerIds.mapIndexed { index, stickerId ->
                    Sticker(
                        id = stickerId,
                        name = stickerId,
                        sortOrder = (index + 1) * 10,
                        asset = UploadedAsset(id = "asset_$stickerId", url = "", thumbnailUrl = null, mimeType = "image/png", filename = "$stickerId.png", sizeBytes = 128),
                    )
                },
        )
}
