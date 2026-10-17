package com.gangchat.client.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.ManagedSticker
import com.gangchat.client.app.StickerManagementCapabilities
import com.gangchat.client.app.StickerManagementScope
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.DownloadedFile
import com.gangchat.client.protocol.Sticker
import com.gangchat.client.protocol.StickerPack
import com.gangchat.client.protocol.UploadedAsset
import com.gangchat.client.shell.FileSelectionService
import com.gangchat.client.shell.FileTypeGroup
import com.gangchat.client.shell.SaveFileLocation
import com.gangchat.client.ui.Button
import com.gangchat.client.ui.ButtonWidget
import com.gangchat.client.ui.MediaCacheScope
import com.gangchat.client.ui.StickerActionGrid
import com.gangchat.client.ui.StickerActionGridEntry
import com.gangchat.client.ui.StickerFilterDialog
import com.gangchat.client.ui.StickerManagerBackend
import com.gangchat.client.ui.StickerManagerPanel
import com.gangchat.client.ui.didExceedMaxLines
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.setUiContent
import com.gangchat.client.ui.stickerActionGridTag
import com.gangchat.client.ui.stickerDialogFrameTag
import com.gangchat.client.ui.stickerManagerItemsScrollTag
import com.gangchat.client.ui.stickerPreviewImageTag
import com.gangchat.client.ui.testMediaCacheController
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.IconSemanticsKey
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
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class StickerManagerPanelWidgetTest {
    @get:Rule
    val compose = createComposeRule()

    private val cache = testMediaCacheController()

    /** Dart's `_host`: a scaffold body padded by 18 that the child fills. */
    private fun host(content: @Composable () -> Unit) {
        compose.setUiContent { _ ->
            MediaCacheScope(cache) {
                Box(Modifier.fillMaxSize().padding(18.dp), propagateMinConstraints = true) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun inGrid(dialog: Boolean = false): SemanticsMatcher {
        val grid = hasTestTag(stickerActionGridTag)
        return if (dialog) hasAnyAncestor(grid) and hasAnyAncestor(hasTestTag(stickerDialogFrameTag)) else hasAnyAncestor(grid)
    }

    private fun gridButtonRects(labels: List<String>, dialog: Boolean = false) =
        labels.map { label -> compose.onNode(hasText(label) and inGrid(dialog)).fetchSemanticsNode().boundsInRoot }

    private fun assertLabelsFit(labels: List<String>, dialog: Boolean = false) {
        for (label in labels) {
            val text = compose.onNode(hasText(label) and inGrid(dialog), useUnmergedTree = true)
            assertFalse("$label exceeded its line", text.didExceedMaxLines())
        }
    }

    private fun close(a: Float, b: Float) = assertTrue("$a != $b", abs(a - b) <= 0.5f)

    private fun buttonEnabled(label: String): Boolean {
        val node = compose.onNode(hasText(label)).fetchSemanticsNode()
        return !node.config.contains(SemanticsProperties.Disabled)
    }

    private fun buttonSelected(label: String): Boolean =
        compose.onNode(hasText(label)).fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.Selected) { null } == true

    @Test
    @Config(qualifiers = "w380dp-h760dp-xxhdpi")
    fun `narrow sticker actions switch from three columns to two`() {
        val backend =
            _FakeStickerBackend(
                capabilities = StickerManagementCapabilities.readOnlyDownloads(),
                packs = listOf(_pack("room_pack", listOf("alpha", "beta"))),
            )
        host { StickerManagerPanel(backend = backend, fileSelectionService = _FakeFileSelectionService(), title = "表情包管理") }

        compose.onNodeWithText("批量管理").performClick()
        compose.waitForIdle()

        val labels = listOf("删除", "取消管理", "筛选", "下载", "置顶", "全选")
        val rects = gridButtonRects(labels)
        assertEquals(6, rects.size)
        close(rects[0].center.y, rects[1].center.y)
        close(rects[2].center.y, rects[3].center.y)
        close(rects[4].center.y, rects[5].center.y)
        close(rects[0].center.x, rects[2].center.x)
        close(rects[1].center.x, rects[3].center.x)
        close(rects[2].center.x, rects[4].center.x)
        close(rects[3].center.x, rects[5].center.x)
        assertLabelsFit(labels)
    }

    @Test
    @Config(qualifiers = "w432dp-h760dp-xxhdpi")
    fun `narrow sticker preview keeps all labels across three action rows`() {
        val backend =
            _FakeStickerBackend(
                capabilities = StickerManagementCapabilities(),
                packs = listOf(_pack("personal_pack", listOf("preview"))),
                supportsAvatar = true,
            )
        host { StickerManagerPanel(backend = backend, fileSelectionService = _FakeFileSelectionService(), title = "表情包管理") }

        compose.onTooltip("preview").performClick()
        compose.waitForIdle()

        val labels = listOf("下载", "设为头像", "保存名称", "置顶", "上移一位", "下移一位")
        val rects = gridButtonRects(labels, dialog = true)
        assertEquals(6, rects.size)
        close(rects[0].center.y, rects[1].center.y)
        close(rects[2].center.y, rects[3].center.y)
        close(rects[4].center.y, rects[5].center.y)
        assertTrue(rects[0].center.y < rects[2].center.y)
        assertTrue(rects[2].center.y < rects[4].center.y)
        assertLabelsFit(labels, dialog = true)
        // The preview stays within its height cap and the whole dialog, with
        // all three action rows, fits the narrow window.
        val density = compose.density.density
        val preview = compose.onNodeWithTag(stickerPreviewImageTag).fetchSemanticsNode().boundsInRoot
        assertTrue(preview.height / density <= 320.5f)
        val dialogRect = compose.onNodeWithTag(stickerDialogFrameTag).fetchSemanticsNode().boundsInRoot
        assertTrue(dialogRect.width / density < 432f)
        assertTrue(dialogRect.top >= 0f)
        assertTrue(dialogRect.bottom / density <= 760f)
        for (rect in rects) {
            assertTrue(rect.left >= dialogRect.left)
            assertTrue(rect.right <= dialogRect.right)
            assertTrue(rect.bottom <= dialogRect.bottom)
        }
        compose.onAllNodes(hasText("删除") and hasAnyAncestor(hasTestTag(stickerDialogFrameTag))).assertCountEquals(1)
    }

    @Test
    @Config(qualifiers = "w720dp-h500dp-xxhdpi")
    fun `action grid hides every icon when two columns still cannot fit labels`() {
        val labels = listOf("下载", "设为头像", "保存名称", "置顶", "上移一位", "下移一位")
        host {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(220.dp)) {
                    StickerActionGrid(
                        actions =
                            labels.map { label ->
                                StickerActionGridEntry(
                                    label = label,
                                    button =
                                        ButtonWidget(
                                            onPressed = {},
                                            icon = { Icon(Icons.Outlined.Circle) },
                                            width = androidx.compose.ui.unit.Dp.Infinity,
                                            child = { Text(label) },
                                        ),
                                )
                            },
                    )
                }
            }
        }

        val rects = gridButtonRects(labels)
        assertEquals(6, rects.size)
        close(rects[0].center.y, rects[1].center.y)
        close(rects[2].center.y, rects[3].center.y)
        close(rects[4].center.y, rects[5].center.y)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(IconSemanticsKey) and inGrid(), useUnmergedTree = true).assertCountEquals(0)
        assertLabelsFit(labels)
    }

    @Test
    @Config(qualifiers = "w330dp-h620dp-xxhdpi")
    fun `narrow sticker filter keeps action labels and hides icons`() {
        compose.setUiContent { _ -> StickerFilterDialog(keyword = "", mimeType = "") }
        compose.waitForIdle()

        for (label in listOf("重置", "取消", "确认")) {
            compose.onAllNodesWithText(label).assertCountEquals(1)
            compose
                .onAllNodes(SemanticsMatcher.keyIsDefined(IconSemanticsKey) and hasAnyAncestor(hasText(label)), useUnmergedTree = true)
                .assertCountEquals(0)
            assertFalse(compose.onNodeWithText(label, useUnmergedTree = true).didExceedMaxLines())
        }
    }

    @Test
    @Config(qualifiers = "w720dp-h760dp-xxhdpi")
    fun `sticker pin disables an unchanged top selection`() {
        val backend =
            _FakeStickerBackend(capabilities = StickerManagementCapabilities(), packs = listOf(_pack("personal_pack", listOf("alpha", "beta"))))
        host { StickerManagerPanel(backend = backend, fileSelectionService = _FakeFileSelectionService(), title = "表情包管理") }

        compose.onNodeWithText("批量管理").performClick()
        compose.waitForIdle()
        assertFalse(buttonEnabled("置顶"))

        compose.onTooltip("alpha").performClick()
        compose.waitForIdle()
        assertFalse(buttonEnabled("置顶"))

        compose.onTooltip("alpha").performClick()
        compose.onTooltip("beta").performClick()
        compose.waitForIdle()
        assertTrue(buttonEnabled("置顶"))
    }

    @Test
    @Config(qualifiers = "w720dp-h600dp-xxhdpi")
    fun `sticker management keeps controls while stickers scroll`() {
        val backend =
            _FakeStickerBackend(
                capabilities = StickerManagementCapabilities(),
                packs = listOf(_pack("personal_pack", List(32) { index -> "sticker_$index" })),
            )
        host { StickerManagerPanel(backend = backend, fileSelectionService = _FakeFileSelectionService(), title = "表情包管理") }

        val titleTop = compose.onNodeWithText("表情包管理").fetchSemanticsNode().boundsInRoot.top
        val actionsTop = compose.onNodeWithTag(stickerActionGridTag).fetchSemanticsNode().boundsInRoot.top
        val firstTop = compose.onTooltip("sticker_0").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag(stickerManagerItemsScrollTag).performTouchInput {
            swipeUp(startY = centerY, endY = centerY - 260.dp.toPx(), durationMillis = 300)
        }
        compose.waitForIdle()

        assertEquals(titleTop, compose.onNodeWithText("表情包管理").fetchSemanticsNode().boundsInRoot.top)
        assertEquals(actionsTop, compose.onNodeWithTag(stickerActionGridTag).fetchSemanticsNode().boundsInRoot.top)
        val scrollRange =
            compose
                .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyAncestor(hasTestTag(stickerManagerItemsScrollTag)))
                .fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(scrollRange.value() > 0f)
        val movedFirst = compose.onAllNodes(hasText("sticker_0")).fetchSemanticsNodes().isEmpty() ||
            compose.onTooltip("sticker_0").fetchSemanticsNode().boundsInRoot.top < firstTop
        assertTrue(movedFirst)
    }

    @Test
    @Config(qualifiers = "w900dp-h900dp-xxhdpi")
    fun `room sticker panel keeps read-only actions disabled`() {
        val backend =
            _FakeStickerBackend(
                capabilities = StickerManagementCapabilities.readOnlyDownloads(),
                packs = listOf(_pack("room_pack", listOf("alpha", "beta"))),
            )
        val files = _FakeFileSelectionService()
        host { StickerManagerPanel(backend = backend, fileSelectionService = files, title = "房间表情包") }

        assertFalse(buttonEnabled("本地上传"))
        assertTrue(buttonEnabled("批量管理"))
        assertTrue(buttonEnabled("筛选"))

        compose.onNodeWithText("批量管理").performClick()
        compose.waitForIdle()

        assertFalse(buttonEnabled("删除"))
        assertFalse(buttonEnabled("下载"))
        assertFalse(buttonEnabled("置顶"))
        assertTrue(buttonEnabled("全选"))
        assertFalse(buttonSelected("全选"))

        compose.onNodeWithText("全选").performClick()
        compose.waitForIdle()

        assertTrue(buttonEnabled("全选"))
        assertTrue(buttonSelected("全选"))
        assertFalse(buttonEnabled("删除"))
        assertTrue(buttonEnabled("下载"))
        assertFalse(buttonEnabled("置顶"))

        compose.onTooltip("alpha").performClick()
        compose.waitForIdle()

        assertTrue(buttonEnabled("全选"))
        assertFalse(buttonSelected("全选"))

        compose.onNodeWithText("全选").performClick()
        compose.waitForIdle()
        assertTrue(buttonSelected("全选"))

        compose.onNodeWithText("全选").performClick()
        compose.waitForIdle()

        assertTrue(buttonEnabled("全选"))
        assertFalse(buttonSelected("全选"))

        compose.onNodeWithText("取消管理").performClick()
        compose.waitForIdle()
        compose.onTooltip("alpha").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("表情预览").assertExists()
        val preview = compose.onNodeWithTag(stickerPreviewImageTag).fetchSemanticsNode().boundsInRoot
        assertEquals(320f, preview.height / compose.density.density, 0.5f)
        assertTrue(buttonEnabled("下载"))
        assertFalse(buttonEnabled("保存名称"))
        assertFalse(buttonEnabled("置顶"))
        assertFalse(buttonEnabled("上移一位"))
        assertFalse(buttonEnabled("下移一位"))
        assertFalse(buttonEnabled("删除"))

        compose.onNodeWithText("下载").performClick()
        compose.waitForIdle()

        assertEquals(1, backend.downloads)
        assertEquals(1, files.saved)
        assertEquals(0, backend.mutations)
    }
}

private fun _pack(id: String, stickerIds: List<String>): StickerPack =
    StickerPack(
        id = id,
        scope = "room",
        roomId = "room_1",
        name = id,
        sortOrder = 10,
        updatedAt = DateTime.utc(2026, 6, 9),
        stickers =
            stickerIds.mapIndexed { index, stickerId ->
                Sticker(
                    id = stickerId,
                    name = stickerId,
                    sortOrder = (index + 1) * 10,
                    asset = UploadedAsset(id = "asset_$stickerId", url = "/assets/$stickerId.png", thumbnailUrl = "", mimeType = "image/png"),
                )
            },
    )

private class _FakeStickerBackend(
    override val capabilities: StickerManagementCapabilities,
    val packs: List<StickerPack>,
    val supportsAvatar: Boolean = false,
) : StickerManagerBackend() {
    var downloads = 0
    var mutations = 0

    override val onSetAvatar: (suspend (ManagedSticker) -> Unit)?
        get() = if (supportsAvatar) ({ _ -> }) else null

    override val scope: StickerManagementScope get() = StickerManagementScope.room

    override val hasApi: Boolean get() = true

    override suspend fun loadPacks(): List<StickerPack> = packs

    override suspend fun createDefaultPack(sortOrder: Int?): StickerPack {
        mutations += 1
        throw IllegalStateException("disabled")
    }

    override suspend fun uploadImageAsset(bytes: ByteArray, filename: String, purpose: String): String {
        mutations += 1
        throw IllegalStateException("disabled")
    }

    override suspend fun addSticker(packId: String, assetId: String, name: String, sortOrder: Int?) {
        mutations += 1
    }

    override suspend fun deleteSticker(packId: String, stickerId: String) {
        mutations += 1
    }

    override suspend fun renameSticker(packId: String, stickerId: String, name: String): String? {
        mutations += 1
        return name
    }

    override suspend fun reorderStickers(packId: String, stickerIds: List<String>) {
        mutations += 1
    }

    override suspend fun downloadStickers(stickerIds: List<String>): DownloadedFile {
        downloads += 1
        return DownloadedFile(bytes = byteArrayOf(1, 2, 3), filename = "stickers.zip", mimeType = "application/zip")
    }
}

private class _FakeFileSelectionService : FileSelectionService() {
    var saved = 0

    override suspend fun getSaveLocation(
        suggestedName: String,
        acceptedTypeGroups: List<FileTypeGroup>,
        confirmButtonText: String?,
    ): SaveFileLocation? = SaveFileLocation(path = suggestedName)

    override suspend fun saveBytesToPath(bytes: ByteArray, path: String, filename: String, mimeType: String?) {
        saved += 1
    }
}
