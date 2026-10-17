package com.gangchat.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.ManagedSticker
import com.gangchat.client.app.RoomMemberPresence
import com.gangchat.client.app.StickerManagementCapabilities
import com.gangchat.client.app.StickerManagementScope
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.DownloadedFile
import com.gangchat.client.protocol.Sticker
import com.gangchat.client.protocol.StickerPack
import com.gangchat.client.protocol.UploadedAsset
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.showDialog
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner

private const val out = "build/outputs/roborazzi"

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class AvatarStickerGalleryScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val imageUrl = "http://127.0.0.1:21116/assets/photo.png"
    private val cache =
        testMediaCacheController(
            mapOf(
                imageUrl to solidPng(64, 64, 0xFFE0A12A.toInt()),
                "http://127.0.0.1:21116/assets/wide.png" to solidPng(120, 60, 0xFF26B36F.toInt()),
                "http://127.0.0.1:21116/assets/tall.png" to solidPng(60, 120, 0xFF5CB8FF.toInt()),
            ),
        )

    @org.junit.Before
    fun installLoader() = installTestImageLoader()

    /** Lets the cache's I/O and Coil's decoding finish. */
    private fun settleImages() {
        repeat(30) {
            Thread.sleep(30)
            compose.waitForIdle()
        }
    }

    @Composable
    private fun Section(title: String, content: @Composable () -> Unit) {
        Column {
            Text(title, style = UiTypography.overline)
            Spacer(Modifier.height(6.dp))
            content()
            Spacer(Modifier.height(14.dp))
        }
    }

    @Test
    fun avatars() {
        compose.setUiContent { _ ->
            MediaCacheScope(cache) {
                Column(Modifier.padding(16.dp)) {
                    Section("SIZES") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(label = "Logan", defaultAvatarKey = "blue-3", size = 18.dp)
                            Avatar(label = "加一", defaultAvatarKey = "purple-2", size = 32.dp)
                            Avatar(label = "Kai", defaultAvatarKey = "red-2")
                            Avatar(label = "暗影", defaultAvatarKey = "green-2", size = 48.dp)
                            Avatar(label = "Room", defaultAvatarKey = "room-1", size = 48.dp, rounded = true)
                            Avatar(label = "Test", defaultAvatarKey = "gold-2", size = 88.dp, showBorder = false)
                        }
                    }
                    Section("STATES") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(label = "Kai")
                            Avatar(label = "Kai", active = true)
                            Avatar(label = "Kai", defaultAvatarKey = "blue-3", active = true, activeBorderWidth = 1.2.dp)
                            Avatar(label = "Kai", active = true, activeBorderColor = UiColors.presenceVoice, paintBorderOnForeground = true)
                            Avatar(label = "", defaultAvatarKey = "graphite-2", size = 42.dp, showFallbackText = false)
                            Avatar(label = "Img", imageUrl = imageUrl, size = 48.dp)
                            Avatar(label = "Img", imageUrl = imageUrl, size = 48.dp, rounded = true, active = true)
                            Avatar(label = "Bad", imageUrl = "http://127.0.0.1:21116/assets/missing.png", size = 48.dp)
                        }
                    }
                    Section("BADGES") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusBadge(label = "已加入", icon = Icons.Filled.CheckCircle, active = true)
                            StatusBadge(label = "未加入", icon = Icons.Outlined.Circle)
                            StatusBadge(label = "私密")
                            StatusBadge(label = "已封禁", danger = true)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PresencePill.member(RoomMemberPresence.live)
                            PresencePill.online()
                            PresencePill.offline()
                            PresencePill.suspended()
                        }
                    }
                    Section("PICKER") {
                        AvatarPicker(
                            label = "头像",
                            displayName = "Logan",
                            imageUrl = null,
                            defaultAvatarKey = "violet-2",
                            usingPreset = true,
                            uploading = false,
                            enabled = true,
                            onUpload = {},
                            onPresetSelected = {},
                        )
                        Spacer(Modifier.height(12.dp))
                        AvatarPicker(
                            label = "房间图标",
                            displayName = "Room",
                            imageUrl = imageUrl,
                            defaultAvatarKey = "blue-3",
                            usingPreset = false,
                            uploading = false,
                            enabled = true,
                            rounded = true,
                            presetKeys = kAvatarPresetKeys.take(12),
                            onUpload = {},
                            onPresetSelected = {},
                        )
                    }
                }
            }
        }
        settleImages()
        compose.onRoot().captureRoboImage("$out/app_avatars.png")
    }

    private fun stickerPacks(): List<StickerPack> {
        val urls = listOf("/assets/photo.png", "/assets/wide.png", "/assets/tall.png", "/assets/missing.png")
        return listOf(
            StickerPack(
                id = "pack",
                scope = "personal",
                roomId = null,
                name = "我的表情",
                sortOrder = 10,
                updatedAt = DateTime.utc(2026, 6, 9),
                stickers =
                    List(11) { index ->
                        Sticker(
                            id = "s$index",
                            name = "表情 $index",
                            sortOrder = (index + 1) * 10,
                            asset =
                                UploadedAsset(
                                    id = "a$index",
                                    url = urls[index % urls.size],
                                    thumbnailUrl = null,
                                    mimeType = "image/png",
                                    width = if (index == 0) 64 else null,
                                    height = if (index == 0) 64 else null,
                                ),
                        )
                    },
            ),
        )
    }

    private inner class GalleryBackend : StickerManagerBackend() {
        override val scope = StickerManagementScope.personal
        override val hasApi = true
        override val onSetAvatar: (suspend (ManagedSticker) -> Unit)? = { _ -> }

        override suspend fun loadPacks() = stickerPacks()

        override suspend fun createDefaultPack(sortOrder: Int?) = stickerPacks().first()

        override suspend fun uploadImageAsset(bytes: ByteArray, filename: String, purpose: String) = "asset"

        override suspend fun addSticker(packId: String, assetId: String, name: String, sortOrder: Int?) {}

        override suspend fun deleteSticker(packId: String, stickerId: String) {}

        override suspend fun renameSticker(packId: String, stickerId: String, name: String): String? = name

        override suspend fun reorderStickers(packId: String, stickerIds: List<String>) {}

        override suspend fun downloadStickers(stickerIds: List<String>) = DownloadedFile(byteArrayOf(), "s.zip", "application/zip")
    }

    private fun stickerPanel() {
        compose.setUiContentWithMediaCache(cache) { _ ->
            Box(Modifier.fillMaxSize().padding(top = 16.dp), propagateMinConstraints = true) {
                StickerManagerPanel(backend = GalleryBackend())
            }
        }
        settleImages()
    }

    @Test
    fun stickerManager() {
        stickerPanel()
        compose.onRoot().captureRoboImage("$out/app_sticker_manager.png")
    }

    @Test
    fun stickerManagerManaging() {
        stickerPanel()
        compose.onNodeWithText("批量管理").performClick()
        compose.waitForIdle()
        compose.onTooltip("表情 1").performClick()
        compose.onTooltip("表情 4").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/app_sticker_manager_managing.png")
    }

    @Test
    fun stickerPreviewDialog() {
        stickerPanel()
        compose.onTooltip("表情 2").performClick()
        settleImages()
        compose.onRoot().captureRoboImage("$out/app_sticker_preview_dialog.png")
    }

    @Test
    fun stickerFilterDialog() {
        stickerPanel()
        compose.onNodeWithText("筛选").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/app_sticker_filter_dialog.png")
    }

    @Test
    fun stickerConfirmDialog() {
        compose.setUiContent { context ->
            Box(Modifier.fillMaxSize())
            androidx.compose.runtime.LaunchedEffect(Unit) {
                unawaited {
                    showDialog<Boolean>(context) { _ ->
                        StickerConfirmDialog(
                            title = "删除表情",
                            body = "确定删除「表情 1」吗？删除后无法恢复。",
                            confirmLabel = "删除",
                            confirmIcon = Icons.Filled.DeleteOutline,
                            danger = true,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/app_sticker_confirm_dialog.png")
    }

    @Test
    fun stickerReadOnly() {
        compose.setUiContent { _ ->
            MediaCacheScope(cache) {
                Box(Modifier.fillMaxSize().padding(top = 16.dp), propagateMinConstraints = true) {
                    StickerManagerPanel(
                        backend =
                            object : StickerManagerBackend() {
                                override val scope = StickerManagementScope.room
                                override val hasApi = true
                                override val capabilities = StickerManagementCapabilities.readOnlyDownloads()

                                override suspend fun loadPacks() = emptyList<StickerPack>()

                                override suspend fun createDefaultPack(sortOrder: Int?) = throw IllegalStateException()

                                override suspend fun uploadImageAsset(bytes: ByteArray, filename: String, purpose: String) = ""

                                override suspend fun addSticker(packId: String, assetId: String, name: String, sortOrder: Int?) {}

                                override suspend fun deleteSticker(packId: String, stickerId: String) {}

                                override suspend fun renameSticker(packId: String, stickerId: String, name: String): String? = null

                                override suspend fun reorderStickers(packId: String, stickerIds: List<String>) {}

                                override suspend fun downloadStickers(stickerIds: List<String>) = DownloadedFile(byteArrayOf(), "", "")
                            },
                        title = "房间表情包",
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/app_sticker_manager_empty.png")
    }
}
