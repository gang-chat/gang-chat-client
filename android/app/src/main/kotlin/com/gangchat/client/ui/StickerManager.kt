package com.gangchat.client.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.ManagedSticker
import com.gangchat.client.app.MediaCacheController
import com.gangchat.client.app.MediaCacheRequest
import com.gangchat.client.app.StickerActionKind
import com.gangchat.client.app.StickerActionPatch
import com.gangchat.client.app.StickerFilterDraft
import com.gangchat.client.app.StickerImageDimensions
import com.gangchat.client.app.StickerManagementCapabilities
import com.gangchat.client.app.StickerManagementScope
import com.gangchat.client.app.StickerPackLoadPatch
import com.gangchat.client.app.StickerPlacementData
import com.gangchat.client.app.StickerPreviewActionKind
import com.gangchat.client.app.StickerPreviewState
import com.gangchat.client.app.StickerSelectionPatch
import com.gangchat.client.app.canSelectVisibleStickers
import com.gangchat.client.app.canStartStickerPrimaryAction
import com.gangchat.client.app.canStartStickerRename
import com.gangchat.client.app.canStartStickerSelectionAction
import com.gangchat.client.app.canUseStickerManagementControl
import com.gangchat.client.app.filteredManagedStickerItems
import com.gangchat.client.app.imageExtensionForMimeType
import com.gangchat.client.app.managedStickerById
import com.gangchat.client.app.managedStickerItems
import com.gangchat.client.app.movedStickerOrder
import com.gangchat.client.app.pinnedStickerOrder
import com.gangchat.client.app.stickerActionCancelled
import com.gangchat.client.app.stickerActionErrorShown
import com.gangchat.client.app.stickerActionFailed
import com.gangchat.client.app.stickerActionNoticeShown
import com.gangchat.client.app.stickerActionStarted
import com.gangchat.client.app.stickerActionSucceeded
import com.gangchat.client.app.stickerAllVisibleSelected
import com.gangchat.client.app.stickerBulkDeleteConfirmBody
import com.gangchat.client.app.stickerDeleteDialogTitle
import com.gangchat.client.app.stickerDeletedNotice
import com.gangchat.client.app.stickerDimensionsText
import com.gangchat.client.app.stickerDownloadNotice
import com.gangchat.client.app.stickerFilterActive
import com.gangchat.client.app.stickerFilterApplied
import com.gangchat.client.app.stickerManagementBusy
import com.gangchat.client.app.stickerManagementCountText
import com.gangchat.client.app.stickerManagementModeToggled
import com.gangchat.client.app.stickerMoveNotice
import com.gangchat.client.app.stickerNameFromFilename
import com.gangchat.client.app.stickerNoOrderChangeNotice
import com.gangchat.client.app.stickerNoUploadableImagesMessage
import com.gangchat.client.app.stickerOrderWithStickerIdsPinnedToFront
import com.gangchat.client.app.stickerPackForSticker
import com.gangchat.client.app.stickerPackUpserted
import com.gangchat.client.app.stickerPacksLoadFailed
import com.gangchat.client.app.stickerPacksLoadStarted
import com.gangchat.client.app.stickerPacksLoadSucceeded
import com.gangchat.client.app.stickerPickerOpenFailureMessage
import com.gangchat.client.app.stickerPinnedNotice
import com.gangchat.client.app.stickerPlacement
import com.gangchat.client.app.stickerPreviewActionFailed
import com.gangchat.client.app.stickerPreviewActionFinished
import com.gangchat.client.app.stickerPreviewActionRequested
import com.gangchat.client.app.stickerPreviewMoveSucceeded
import com.gangchat.client.app.stickerRenameName
import com.gangchat.client.app.stickerSelectionNumbers
import com.gangchat.client.app.stickerSelectionToggled
import com.gangchat.client.app.stickerSelectionWouldChangePinnedOrder
import com.gangchat.client.app.stickerSingleDeleteConfirmBody
import com.gangchat.client.app.stickerSortOrdersBeforeExisting
import com.gangchat.client.app.stickerUploadFilename
import com.gangchat.client.app.stickerUploadItemsFromFiles
import com.gangchat.client.app.stickerUploadNotice
import com.gangchat.client.app.stickerVisibleSelectionButtonText
import com.gangchat.client.app.stickerVisibleSelectionToggled
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.DownloadedFile
import com.gangchat.client.protocol.Sticker
import com.gangchat.client.protocol.StickerPack
import com.gangchat.client.protocol.UploadedAsset
import com.gangchat.client.shell.FileSelectionService
import com.gangchat.client.shell.FileTypeGroup
import com.gangchat.client.shell.SelectedFile
import com.gangchat.client.widgets.BoxFit
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.Dialog
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.FocusNode
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.applyBoxFitDestination
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.ignorePointer
import com.gangchat.client.widgets.inscribeCentered
import com.gangchat.client.widgets.padding
import com.gangchat.client.widgets.rememberBuildContext
import com.gangchat.client.widgets.showDialog
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException

/**
 * Dart's `StickerImagePreviewOpener`
 * (`Future<void> Function(BuildContext context, {required String imageUrl,
 * required String suggestedName, bool forceSquare})`).
 */
typealias StickerImagePreviewOpener =
    suspend (context: BuildContext, imageUrl: String, suggestedName: String, forceSquare: Boolean) -> Unit

private val _stickerFileTypeGroups =
    listOf(FileTypeGroup(label = "图片和 ZIP", extensions = listOf("png", "jpg", "jpeg", "webp", "gif", "zip")))

/**
 * 表情包管理的数据来源。个人/房间各自实现,面板只负责编排与展示。
 *
 * 业务逻辑(排序、筛选、上传项解析等)统一复用 `app/Sticker*`
 * 中的纯函数,这里仅暴露最小的副作用接口。
 */
abstract class StickerManagerBackend {
    /** 用于文案区分个人/房间。 */
    abstract val scope: StickerManagementScope

    /** API 是否可用(未登录等情况下为 false)。 */
    abstract val hasApi: Boolean

    /** 当前数据源允许的操作能力。个人表情默认完整可管理。 */
    open val capabilities: StickerManagementCapabilities get() = StickerManagementCapabilities()

    /** 读取当前作用域下的全部表情包。 */
    abstract suspend fun loadPacks(): List<StickerPack>

    /** 没有可用表情包时创建一个默认包。 */
    abstract suspend fun createDefaultPack(sortOrder: Int? = null): StickerPack

    /** 上传图片资源,返回资源 id。 */
    abstract suspend fun uploadImageAsset(bytes: ByteArray, filename: String, purpose: String): String

    /** 向表情包追加一张表情。 */
    abstract suspend fun addSticker(packId: String, assetId: String, name: String, sortOrder: Int? = null)

    /** 删除一张表情。 */
    abstract suspend fun deleteSticker(packId: String, stickerId: String)

    /** 重命名表情,返回服务端确认后的名称(失败返回 null)。 */
    abstract suspend fun renameSticker(packId: String, stickerId: String, name: String): String?

    /** 重新排序表情包内的表情。 */
    abstract suspend fun reorderStickers(packId: String, stickerIds: List<String>)

    /** 下载选中的表情,返回文件数据。 */
    abstract suspend fun downloadStickers(stickerIds: List<String>): DownloadedFile

    /** “设为头像”动作。返回 null 表示该作用域不支持(房间)。 */
    open val onSetAvatar: (suspend (item: ManagedSticker) -> Unit)? get() = null
}

/** Test tag of the action grid (Dart's `ValueKey('sticker-action-grid')`). */
const val stickerActionGridTag = "sticker-action-grid"

/** Test tag of the scrolling sticker grid (Dart's `ValueKey('sticker-manager-items-scroll')`). */
const val stickerManagerItemsScrollTag = "sticker-manager-items-scroll"

/** Test tag of the sticker dialogs' panel (Dart's tests find it as the dialog's `Material`). */
const val stickerDialogFrameTag = "sticker-dialog-frame"

/** Test tag of the preview image box (Dart's `ValueKey('sticker-preview-image')`). */
const val stickerPreviewImageTag = "sticker-preview-image"

/**
 * 通用表情包管理面板。个人设置与房间设置共用同一套交互与视觉。
 *
 * [fileSelectionService] null uses a default `FileSelectionService()` (Dart's
 * `const FileSelectionService()` default).
 */
@Composable
fun StickerManagerPanel(
    backend: StickerManagerBackend,
    modifier: Modifier = Modifier,
    fileSelectionService: FileSelectionService? = null,
    imagePreviewOpener: StickerImagePreviewOpener? = null,
    title: String = "表情包管理",
    unavailableText: String = "表情包需要登录后从服务端读取",
) = StatefulWidget(StickerManagerPanelWidget(backend, fileSelectionService, imagePreviewOpener, title, unavailableText, modifier)) {
    _StickerManagerPanelState()
}

class StickerManagerPanelWidget(
    val backend: StickerManagerBackend,
    val fileSelectionService: FileSelectionService?,
    val imagePreviewOpener: StickerImagePreviewOpener?,
    val title: String,
    val unavailableText: String,
    val modifier: Modifier,
)

private class _StickerManagerPanelState : ViewState<StickerManagerPanelWidget>() {
    private var _packs: List<StickerPack> = emptyList()
    private var _selectedStickerIds: List<String> = emptyList()
    private var _loading = true
    private var _uploading = false
    private var _deleting = false
    private var _savingOrder = false
    private var _downloading = false
    private var _managing = false
    private var _filterKeyword = ""
    private var _filterMimeType = ""
    private var _error: String? = null
    private var _notice: String? = null
    private var _floatingNoticeSerial = 0
    private val _floatingNoticeEventKeys = mutableMapOf<String, Int>()
    private var _defaultFileSelectionService: FileSelectionService? = null

    private val _scope: StickerManagementScope get() = widget.backend.scope
    private val _capabilities: StickerManagementCapabilities get() = widget.backend.capabilities

    private val _fileSelectionService: FileSelectionService
        get() = widget.fileSelectionService
            ?: _defaultFileSelectionService
            ?: FileSelectionService().also { _defaultFileSelectionService = it }

    override fun initState() {
        if (widget.backend.hasApi) {
            unawaited { _load() }
        } else {
            _loading = false
        }
    }

    private val _allItems: List<ManagedSticker> get() = managedStickerItems(_packs)

    private val _filteredItems: List<ManagedSticker>
        get() = filteredManagedStickerItems(_allItems, keyword = _filterKeyword, mimeType = _filterMimeType)

    private val _filterActive: Boolean get() = stickerFilterActive(keyword = _filterKeyword, mimeType = _filterMimeType)

    private val _busy: Boolean
        get() = stickerManagementBusy(uploading = _uploading, deleting = _deleting, savingOrder = _savingOrder, downloading = _downloading)

    private fun _selectionNumbers(): Map<String, Int> = stickerSelectionNumbers(_selectedStickerIds)

    private fun _applyLoadPatch(patch: StickerPackLoadPatch) {
        _packs = patch.packs
        _selectedStickerIds = patch.selectedStickerIds
        _loading = patch.loading
        _error = patch.error
        _markFloatingNoticeEvent("error", _error)
    }

    private fun _applySelectionPatch(patch: StickerSelectionPatch) {
        _managing = patch.managing
        _filterKeyword = patch.filterKeyword
        _filterMimeType = patch.filterMimeType
        _selectedStickerIds = patch.selectedStickerIds
    }

    private fun _applyActionPatch(patch: StickerActionPatch) {
        _uploading = patch.uploading
        _deleting = patch.deleting
        _savingOrder = patch.savingOrder
        _downloading = patch.downloading
        _selectedStickerIds = patch.selectedStickerIds
        _error = patch.error
        _notice = patch.notice
        _markFloatingNoticeEvent("error", _error)
        _markFloatingNoticeEvent("notice", _notice)
    }

    private fun _markFloatingNoticeEvent(channel: String, message: String?) {
        if (message == null || message.trim().isEmpty()) return
        _floatingNoticeEventKeys[channel] = ++_floatingNoticeSerial
    }

    private fun _floatingNoticeEventKey(channel: String): Any? = _floatingNoticeEventKeys[channel]

    private fun _started(action: StickerActionKind) =
        stickerActionStarted(
            action = action,
            uploading = _uploading,
            deleting = _deleting,
            savingOrder = _savingOrder,
            downloading = _downloading,
            selectedStickerIds = _selectedStickerIds,
        )

    private fun _succeeded(action: StickerActionKind, notice: String, clearSelection: Boolean = false) =
        stickerActionSucceeded(
            action = action,
            uploading = _uploading,
            deleting = _deleting,
            savingOrder = _savingOrder,
            downloading = _downloading,
            selectedStickerIds = _selectedStickerIds,
            error = _error,
            notice = notice,
            clearSelection = clearSelection,
        )

    private fun _failed(action: StickerActionKind, failure: Any) =
        stickerActionFailed(
            action = action,
            uploading = _uploading,
            deleting = _deleting,
            savingOrder = _savingOrder,
            downloading = _downloading,
            selectedStickerIds = _selectedStickerIds,
            failure = failure,
        )

    private suspend fun _load() {
        setState { _applyLoadPatch(stickerPacksLoadStarted(packs = _packs, selectedStickerIds = _selectedStickerIds)) }
        try {
            val packs = widget.backend.loadPacks()
            if (!mounted) return
            setState { _applyLoadPatch(stickerPacksLoadSucceeded(packs = packs, selectedStickerIds = _selectedStickerIds)) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!mounted) return
            setState {
                _applyLoadPatch(stickerPacksLoadFailed(packs = _packs, selectedStickerIds = _selectedStickerIds, failure = e))
            }
        }
    }

    private suspend fun _ensurePack(): StickerPack {
        if (_packs.isNotEmpty()) return _packs.first()
        val created = widget.backend.createDefaultPack(sortOrder = 10)
        if (mounted) {
            setState {
                _applyLoadPatch(
                    stickerPackUpserted(
                        packs = _packs,
                        selectedStickerIds = _selectedStickerIds,
                        pack = created,
                        loading = _loading,
                        error = _error,
                    ),
                )
            }
        }
        return created
    }

    private suspend fun _upload() {
        if (!widget.backend.hasApi || _busy || !_capabilities.canUpload) return
        val files: List<SelectedFile>
        try {
            files = _fileSelectionService.openFiles(acceptedTypeGroups = _stickerFileTypeGroups)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!mounted) return
            setState {
                _applyActionPatch(
                    stickerActionErrorShown(
                        uploading = _uploading,
                        deleting = _deleting,
                        savingOrder = _savingOrder,
                        downloading = _downloading,
                        selectedStickerIds = _selectedStickerIds,
                        failure = stickerPickerOpenFailureMessage(e),
                        notice = _notice,
                    ),
                )
            }
            return
        }
        if (files.isEmpty()) return
        setState { _applyActionPatch(_started(StickerActionKind.upload)) }
        var uploadedCount = 0
        try {
            val uploadItems =
                stickerUploadItemsFromFiles(
                    stickerUploadSourcesFromSelectedFiles(files),
                    decodeImageDimensions = ::decodeStickerImageDimensions,
                )
            if (uploadItems.isEmpty()) throw IllegalStateException(stickerNoUploadableImagesMessage())
            val pack = _ensurePack()
            val uploadSortOrders = stickerSortOrdersBeforeExisting(pack, uploadItems.size)
            for ((index, item) in uploadItems.withIndex()) {
                val assetId =
                    widget.backend.uploadImageAsset(
                        bytes = item.bytes,
                        filename = stickerUploadFilename(item.filename, index),
                        purpose = "sticker",
                    )
                widget.backend.addSticker(
                    packId = pack.id,
                    assetId = assetId,
                    name = stickerNameFromFilename(item.filename),
                    sortOrder = uploadSortOrders[index],
                )
                uploadedCount += 1
            }
            _load()
            if (!mounted) return
            setState {
                _applyActionPatch(_succeeded(StickerActionKind.upload, stickerUploadNotice(scope = _scope, count = uploadedCount)))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!mounted) return
            setState { _applyActionPatch(_failed(StickerActionKind.upload, e)) }
        }
    }

    private suspend fun _deleteItem(item: ManagedSticker): Boolean {
        if (!widget.backend.hasApi || _busy || !_capabilities.canDelete) return false
        val confirmed =
            showDialog<Boolean>(context) { _ ->
                StickerConfirmDialog(
                    title = stickerDeleteDialogTitle(_scope),
                    body = stickerSingleDeleteConfirmBody(scope = _scope, stickerName = item.sticker.name),
                    confirmLabel = "删除",
                    confirmIcon = Icons.Filled.DeleteOutline,
                    danger = true,
                )
            }
        if (confirmed != true || !mounted) return false
        setState { _applyActionPatch(_started(StickerActionKind.delete)) }
        try {
            widget.backend.deleteSticker(packId = item.pack.id, stickerId = item.sticker.id)
            _load()
            if (!mounted) return false
            setState { _applyActionPatch(_succeeded(StickerActionKind.delete, stickerDeletedNotice(scope = _scope))) }
            return true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!mounted) return false
            setState { _applyActionPatch(_failed(StickerActionKind.delete, e)) }
            return false
        }
    }

    private suspend fun _renameItem(item: ManagedSticker, name: String): String? {
        val trimmed = stickerRenameName(name)
        if (!widget.backend.hasApi || trimmed == null || !_capabilities.canRename) return null
        try {
            val actual = widget.backend.renameSticker(packId = item.pack.id, stickerId = item.sticker.id, name = trimmed)
            _load()
            return actual
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!mounted) return null
            setState {
                _applyActionPatch(
                    stickerActionErrorShown(
                        uploading = _uploading,
                        deleting = _deleting,
                        savingOrder = _savingOrder,
                        downloading = _downloading,
                        selectedStickerIds = _selectedStickerIds,
                        failure = e,
                        notice = _notice,
                    ),
                )
            }
            return null
        }
    }

    private suspend fun _moveItem(item: ManagedSticker, delta: Int): StickerPlacementData? {
        val placement = _placement(item.sticker.id)
        if (_filterActive) return placement
        if (!widget.backend.hasApi || placement == null || _busy || !_capabilities.canMove) return placement
        val ids = movedStickerOrder(placement.pack, item.sticker.id, delta) ?: return placement
        setState { _applyActionPatch(_started(StickerActionKind.order)) }
        try {
            widget.backend.reorderStickers(packId = placement.pack.id, stickerIds = ids)
            _load()
            if (!mounted) return placement
            setState { _applyActionPatch(_succeeded(StickerActionKind.order, stickerMoveNotice(scope = _scope, delta = delta))) }
            return _placement(item.sticker.id)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!mounted) return placement
            setState { _applyActionPatch(_failed(StickerActionKind.order, e)) }
            return placement
        }
    }

    private suspend fun _pinItem(item: ManagedSticker): StickerPlacementData? {
        val placement = _placement(item.sticker.id)
        if (!widget.backend.hasApi || placement == null || placement.index == 0 || _busy || !_capabilities.canPin) {
            return placement
        }
        val ids = pinnedStickerOrder(placement.pack, item.sticker.id) ?: return placement
        setState { _applyActionPatch(_started(StickerActionKind.order)) }
        try {
            widget.backend.reorderStickers(packId = placement.pack.id, stickerIds = ids)
            _load()
            if (!mounted) return placement
            setState { _applyActionPatch(_succeeded(StickerActionKind.order, stickerPinnedNotice(scope = _scope))) }
            return _placement(item.sticker.id)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!mounted) return placement
            setState { _applyActionPatch(_failed(StickerActionKind.order, e)) }
            return placement
        }
    }

    private suspend fun _downloadIds(stickerIds: List<String>) {
        if (!canStartStickerSelectionAction(busy = _busy, selectedStickerIds = stickerIds, allowed = _capabilities.canDownload)) {
            return
        }
        setState { _applyActionPatch(_started(StickerActionKind.download)) }
        try {
            val file = widget.backend.downloadStickers(stickerIds = stickerIds)
            val location =
                _fileSelectionService.getSaveLocation(
                    suggestedName = file.filename,
                    acceptedTypeGroups = _stickerFileTypeGroups,
                    confirmButtonText = "保存",
                )
            if (location == null) {
                if (!mounted) return
                setState {
                    _applyActionPatch(
                        stickerActionCancelled(
                            action = StickerActionKind.download,
                            uploading = _uploading,
                            deleting = _deleting,
                            savingOrder = _savingOrder,
                            downloading = _downloading,
                            selectedStickerIds = _selectedStickerIds,
                        ),
                    )
                }
                return
            }
            _fileSelectionService.saveBytesToLocation(
                bytes = file.bytes,
                location = location,
                filename = file.filename,
                mimeType = file.mimeType,
            )
            if (!mounted) return
            setState {
                _applyActionPatch(
                    _succeeded(StickerActionKind.download, stickerDownloadNotice(scope = _scope, count = stickerIds.size)),
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!mounted) return
            setState { _applyActionPatch(_failed(StickerActionKind.download, e)) }
        }
    }

    private suspend fun _deleteSelected() {
        val selectedIds = _selectedStickerIds
        if (!canStartStickerSelectionAction(busy = _busy, selectedStickerIds = selectedIds, allowed = _capabilities.canDelete)) {
            return
        }
        val confirmed =
            showDialog<Boolean>(context) { _ ->
                StickerConfirmDialog(
                    title = stickerDeleteDialogTitle(_scope),
                    body = stickerBulkDeleteConfirmBody(scope = _scope, count = selectedIds.size),
                    confirmLabel = "删除",
                    confirmIcon = Icons.Filled.DeleteOutline,
                    danger = true,
                )
            }
        if (confirmed != true || !mounted) return
        setState { _applyActionPatch(_started(StickerActionKind.delete)) }
        var deletedCount = 0
        try {
            for (id in selectedIds) {
                val item = managedStickerById(_allItems, id) ?: continue
                widget.backend.deleteSticker(packId = item.pack.id, stickerId = item.sticker.id)
                deletedCount += 1
            }
            _load()
            if (!mounted) return
            setState {
                _applyActionPatch(
                    _succeeded(StickerActionKind.delete, stickerDeletedNotice(scope = _scope, count = deletedCount), clearSelection = true),
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!mounted) return
            setState { _applyActionPatch(_failed(StickerActionKind.delete, e)) }
        }
    }

    private suspend fun _pinSelected() {
        val selectedIds = _selectedStickerIds
        if (!canStartStickerSelectionAction(busy = _busy, selectedStickerIds = selectedIds, allowed = _capabilities.canPin) ||
            !stickerSelectionWouldChangePinnedOrder(packs = _packs, selectedStickerIds = selectedIds)
        ) {
            return
        }
        val selectedByPack = LinkedHashMap<String, MutableList<String>>()
        for (id in selectedIds) {
            val pack = stickerPackForSticker(_packs, id) ?: continue
            selectedByPack.getOrPut(pack.id) { mutableListOf() }.add(id)
        }
        if (selectedByPack.isEmpty()) {
            setState {
                _applyActionPatch(
                    stickerActionNoticeShown(
                        uploading = _uploading,
                        deleting = _deleting,
                        savingOrder = _savingOrder,
                        downloading = _downloading,
                        selectedStickerIds = _selectedStickerIds,
                        error = _error,
                        notice = stickerNoOrderChangeNotice(_scope),
                    ),
                )
            }
            return
        }
        setState { _applyActionPatch(_started(StickerActionKind.order)) }
        try {
            for (pack in _packs) {
                val selectedInPack = selectedByPack[pack.id]
                if (selectedInPack == null || selectedInPack.isEmpty()) continue
                val nextOrder = stickerOrderWithStickerIdsPinnedToFront(pack, selectedInPack) ?: continue
                widget.backend.reorderStickers(packId = pack.id, stickerIds = nextOrder)
            }
            _load()
            if (!mounted) return
            setState {
                _applyActionPatch(_succeeded(StickerActionKind.order, stickerPinnedNotice(scope = _scope, count = selectedIds.size)))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!mounted) return
            setState { _applyActionPatch(_failed(StickerActionKind.order, e)) }
        }
    }

    private suspend fun _setAvatar(item: ManagedSticker) {
        val onSetAvatar = widget.backend.onSetAvatar
        if (onSetAvatar == null || !_capabilities.canSetAvatar) return
        onSetAvatar(item)
        if (mounted) _load()
    }

    private fun _toggleManageMode() {
        if (!canUseStickerManagementControl(busy = _busy, allowed = _capabilities.canBatchManage)) return
        setState {
            _applySelectionPatch(
                stickerManagementModeToggled(managing = _managing, filterKeyword = _filterKeyword, filterMimeType = _filterMimeType),
            )
        }
    }

    private fun _toggleSelection(stickerId: String) {
        setState {
            _applySelectionPatch(
                stickerSelectionToggled(
                    managing = _managing,
                    filterKeyword = _filterKeyword,
                    filterMimeType = _filterMimeType,
                    selectedStickerIds = _selectedStickerIds,
                    stickerId = stickerId,
                ),
            )
        }
    }

    private fun _selectAllVisible(items: List<ManagedSticker>) {
        if (!canSelectVisibleStickers(busy = _busy, visibleItems = items, allowed = _capabilities.canSelectAll)) return
        setState {
            _applySelectionPatch(
                stickerVisibleSelectionToggled(
                    managing = _managing,
                    busy = _busy,
                    filterKeyword = _filterKeyword,
                    filterMimeType = _filterMimeType,
                    selectedStickerIds = _selectedStickerIds,
                    visibleItems = items,
                ),
            )
        }
    }

    private suspend fun _openFilter() {
        if (!canUseStickerManagementControl(busy = _busy, allowed = _capabilities.canFilter)) return
        val value =
            showDialog<StickerFilterDraft>(context) { _ ->
                StickerFilterDialog(keyword = _filterKeyword, mimeType = _filterMimeType)
            }
        if (value == null || !mounted) return
        setState {
            _applySelectionPatch(stickerFilterApplied(managing = _managing, keyword = value.keyword, mimeType = value.mimeType))
        }
    }

    private fun _placement(stickerId: String): StickerPlacementData? = stickerPlacement(_packs, stickerId)

    private fun _preview(item: ManagedSticker) {
        val imageUrl = AppConfigScope.of(context).resolveAssetUrl(item.sticker.asset.url) ?: return
        val placement = _placement(item.sticker.id)
        val capabilities = _capabilities
        val filterActive = _filterActive
        val onSetAvatar = widget.backend.onSetAvatar
        val opener = widget.imagePreviewOpener
        unawaited {
            showDialog<Unit>(context) { _ ->
                StickerPreviewDialog(
                    item = item,
                    imageUrl = imageUrl,
                    canMoveUp = capabilities.canMove && !filterActive && (placement?.canMoveUp ?: false),
                    canMoveDown = capabilities.canMove && !filterActive && (placement?.canMoveDown ?: false),
                    canPin = capabilities.canPin && (placement?.canPin ?: false),
                    canRename = capabilities.canRename,
                    canDownload = capabilities.canDownload,
                    canDelete = capabilities.canDelete,
                    onRename = { name -> _renameItem(item, name) },
                    onSetAvatar = if (onSetAvatar == null || !capabilities.canSetAvatar) null else ({ _setAvatar(item) }),
                    onDownload = { _downloadIds(listOf(item.sticker.id)) },
                    onDelete = { _deleteItem(item) },
                    onMoveUp = { _moveItem(item, -1) },
                    onMoveDown = { _moveItem(item, 1) },
                    onPin = { _pinItem(item) },
                    imagePreviewOpener = opener,
                )
            }
        }
    }

    @Composable
    override fun build() {
        val items = _filteredItems
        val totalCount = _allItems.size
        val selectionNumbers = _selectionNumbers()
        val busy = _busy
        val capabilities = _capabilities
        val selectedStickerIds = _selectedStickerIds
        val allVisibleSelected = stickerAllVisibleSelected(selectedStickerIds = selectedStickerIds, visibleItems = items)
        val canPinSelection =
            canStartStickerSelectionAction(busy = busy, selectedStickerIds = selectedStickerIds, allowed = capabilities.canPin) &&
                stickerSelectionWouldChangePinnedOrder(packs = _packs, selectedStickerIds = selectedStickerIds)
        val managing = _managing
        val stickerBody: @Composable () -> Unit =
            when {
                _loading && _packs.isEmpty() -> {
                    {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = UiColors.accent,
                                strokeWidth = 4.dp,
                                trackColor = Color.Transparent,
                                strokeCap = StrokeCap.Square,
                            )
                        }
                    }
                }
                totalCount == 0 -> {
                    {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            StickerEmptyState(text = "暂无表情,点击本地上传会自动创建", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                items.isEmpty() -> {
                    {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            StickerEmptyState(text = "没有匹配的表情", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                else -> {
                    {
                        StickerGrid(
                            items = items,
                            managing = managing,
                            selectionNumbers = selectionNumbers,
                            busy = busy,
                            scrollable = true,
                            modifier = Modifier.testTag(stickerManagerItemsScrollTag),
                            onTap = { item -> if (_managing) _toggleSelection(item.sticker.id) else _preview(item) },
                        )
                    }
                }
            }

        val notice = _notice
        val error = _error
        FloatingNoticeEmitter(
            notices =
                listOfNotNull(
                    notice?.let { FloatingNotice(message = it, tone = FloatingNoticeTone.success, eventKey = _floatingNoticeEventKey("notice")) },
                    error?.let {
                        FloatingNotice(message = it, tone = FloatingNoticeTone.error, duration = null, eventKey = _floatingNoticeEventKey("error"))
                    },
                ),
        ) {
            if (!widget.backend.hasApi) {
                SettingsList(modifier = widget.modifier, children = listOf({ StickerEmptyState(text = widget.unavailableText) }))
            } else {
                val visibleSelectionText = stickerVisibleSelectionButtonText(selectedStickerIds = selectedStickerIds, visibleItems = items)
                val actions =
                    buildList {
                        add(
                            StickerActionGridEntry(
                                label = if (managing) "删除" else "本地上传",
                                button =
                                    ButtonWidget(
                                        onPressed =
                                            if (canStartStickerPrimaryAction(
                                                    busy = busy,
                                                    allowed = if (managing) capabilities.canDelete else capabilities.canUpload,
                                                )
                                            ) {
                                                if (managing) ({ unawaited { _deleteSelected() } }) else ({ unawaited { _upload() } })
                                            } else {
                                                null
                                            },
                                        loading = if (managing) _deleting else _uploading,
                                        tone = if (managing) ButtonTone.danger else ButtonTone.primary,
                                        icon = { Icon(if (managing) Icons.Filled.DeleteOutline else Icons.Filled.UploadFile) },
                                        width = Dp.Infinity,
                                        child = { Text(if (managing) "删除" else "本地上传") },
                                    ),
                            ),
                        )
                        add(
                            StickerActionGridEntry(
                                label = if (managing) "取消管理" else "批量管理",
                                button =
                                    ButtonWidget(
                                        onPressed =
                                            if (canUseStickerManagementControl(busy = busy, allowed = capabilities.canBatchManage)) {
                                                ::_toggleManageMode
                                            } else {
                                                null
                                            },
                                        selected = managing,
                                        tone = if (managing) ButtonTone.primary else ButtonTone.neutral,
                                        icon = { Icon(if (managing) Icons.Filled.Close else Icons.Filled.ChecklistRtl) },
                                        width = Dp.Infinity,
                                        child = { Text(if (managing) "取消管理" else "批量管理") },
                                    ),
                            ),
                        )
                        add(
                            StickerActionGridEntry(
                                label = "筛选",
                                button =
                                    ButtonWidget(
                                        onPressed =
                                            if (canUseStickerManagementControl(busy = busy, allowed = capabilities.canFilter)) {
                                                { unawaited { _openFilter() } }
                                            } else {
                                                null
                                            },
                                        selected = _filterActive,
                                        tone = if (_filterActive) ButtonTone.primary else ButtonTone.neutral,
                                        icon = { Icon(Icons.Outlined.FilterAlt) },
                                        width = Dp.Infinity,
                                        child = { Text("筛选") },
                                    ),
                            ),
                        )
                        if (managing) {
                            add(
                                StickerActionGridEntry(
                                    label = "下载",
                                    button =
                                        ButtonWidget(
                                            onPressed =
                                                if (canStartStickerSelectionAction(
                                                        busy = busy,
                                                        selectedStickerIds = selectedStickerIds,
                                                        allowed = capabilities.canDownload,
                                                    )
                                                ) {
                                                    { unawaited { _downloadIds(_selectedStickerIds) } }
                                                } else {
                                                    null
                                                },
                                            loading = _downloading,
                                            icon = { Icon(Icons.Outlined.Download) },
                                            width = Dp.Infinity,
                                            child = { Text("下载") },
                                        ),
                                ),
                            )
                            add(
                                StickerActionGridEntry(
                                    label = "置顶",
                                    button =
                                        ButtonWidget(
                                            onPressed = if (canPinSelection) ({ unawaited { _pinSelected() } }) else null,
                                            loading = _savingOrder,
                                            icon = { Icon(Icons.Filled.VerticalAlignTop) },
                                            width = Dp.Infinity,
                                            child = { Text("置顶") },
                                        ),
                                ),
                            )
                            add(
                                StickerActionGridEntry(
                                    label = visibleSelectionText,
                                    button =
                                        ButtonWidget(
                                            onPressed =
                                                if (canSelectVisibleStickers(busy = busy, visibleItems = items, allowed = capabilities.canSelectAll)) {
                                                    { _selectAllVisible(items) }
                                                } else {
                                                    null
                                                },
                                            selected = allVisibleSelected,
                                            icon = {
                                                Icon(if (allVisibleSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank)
                                            },
                                            width = Dp.Infinity,
                                            child = { Text(visibleSelectionText) },
                                        ),
                                ),
                            )
                        }
                    }
                SettingsFixedHeaderCard(
                    modifier = widget.modifier,
                    title = widget.title,
                    spacing = 10.dp,
                    trailing = {
                        Text(
                            stickerManagementCountText(filterActive = _filterActive, visibleCount = items.size, totalCount = totalCount),
                            style = uiTextStyle(color = UiColors.textMuted, fontSize = 12.0, fontWeight = FontWeight.W600),
                        )
                    },
                    headerChildren = listOf({ StickerActionGrid(actions = actions) }),
                    body = stickerBody,
                )
            }
        }
    }
}

// 表情包专用配色:这两个颜色不在通用 token 中,保留本地常量以维持原视觉。
private val _stickerAccent = UiColors.accentStrong
private val _stickerDangerBorder = UiColors.dangerBorder

class StickerActionGridEntry(val label: String, val button: ButtonWidget)

private val _actionGridGap = 10.dp

/**
 * Keeps sticker actions at three columns while every icon and label fits,
 * then switches the complete action set to two columns.
 */
@Composable
fun StickerActionGrid(actions: List<StickerActionGridEntry>, modifier: Modifier = Modifier) {
    if (actions.isEmpty()) return
    BoxWithConstraints(modifier) {
        val context = rememberBuildContext()
        val finite = constraints.hasBoundedWidth
        val buttonWidthsWithIcons =
            actions.map { action ->
                Button.minimumWidthForLabel(
                    context,
                    label = action.label,
                    hasIcon = action.button.icon != null,
                    padding = action.button.padding,
                )
            }
        val maximumButtonWidth = buttonWidthsWithIcons.maxOf { it }
        val threeColumnWidth = maximumButtonWidth * 3 + _actionGridGap * 2
        val columns = if (actions.size > 2 && finite && maxWidth < threeColumnWidth) 2 else min(3, actions.size)
        val availableWidth = if (finite) maxWidth else threeColumnWidth
        val itemWidth = (availableWidth - _actionGridGap * (columns - 1)) / columns.toFloat()
        val hideIcons = columns == 2 && buttonWidthsWithIcons.any { minimumWidth -> minimumWidth > itemWidth }
        // Dart's Wrap: every child is exactly [itemWidth] wide, so the runs are
        // laid out as rows of [columns] (which also avoids sub-pixel wrapping).
        Column(Modifier.testTag(stickerActionGridTag), verticalArrangement = Arrangement.spacedBy(_actionGridGap)) {
            for (row in actions.chunked(columns)) {
                Row(horizontalArrangement = Arrangement.spacedBy(_actionGridGap)) {
                    for (action in row) {
                        Box(Modifier.width(itemWidth)) {
                            (if (hideIcons) action.button.withoutIcon() else action.button).build()
                        }
                    }
                }
            }
        }
    }
}

/** 空状态文案。 */
@Composable
fun StickerEmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(74.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, style = uiTextStyle(color = UiColors.textMuted, fontSize = 13.0))
    }
}

@Composable
private fun _StickerFieldLabel(text: String) {
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = uiTextStyle(color = UiColors.textMuted, fontSize = 12.0, fontWeight = FontWeight.W600),
    )
}

/** 表情网格。 */
@Composable
fun StickerGrid(
    items: List<ManagedSticker>,
    managing: Boolean,
    selectionNumbers: Map<String, Int>,
    busy: Boolean,
    onTap: (ManagedSticker) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) {
    BoxWithConstraints(modifier) {
        val width = if (constraints.hasBoundedWidth) maxWidth.value.toDouble() else 360.0
        val columns = floor(width / 92).toInt().coerceIn(3, 9)
        val tile: @Composable (ManagedSticker) -> Unit = { item ->
            Box(Modifier.aspectRatio(1f), propagateMinConstraints = true) {
                _StickerGridTile(
                    item = item,
                    managing = managing,
                    selectionNumber = selectionNumbers[item.sticker.id],
                    busy = busy,
                    onTap = { onTap(item) },
                )
            }
        }
        if (scrollable) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items.size) { index -> tile(items[index]) }
            }
        } else {
            // Dart's shrink-wrapped, non-scrolling GridView.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in items.chunked(columns)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (item in row) Box(Modifier.weight(1f)) { tile(item) }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun _StickerGridTile(
    item: ManagedSticker,
    managing: Boolean,
    selectionNumber: Int?,
    busy: Boolean,
    onTap: () -> Unit,
) {
    val selected = selectionNumber != null
    BoxWithConstraints {
        val tileHeight = if (constraints.hasBoundedHeight) maxOf(54.dp, maxHeight) else 86.dp
        UiTooltip(message = item.sticker.name) {
            PressableSurface(
                onPressed = if (busy) null else onTap,
                selected = selected,
                height = tileHeight,
                padding = EdgeInsets.all(7.dp),
                backgroundColor = UiColors.background,
                selectedBackgroundColor = UiColors.selected,
                borderColor = if (selected) UiColors.selectedBorder else UiColors.border,
                selectedBorderColor = UiColors.selectedBorder,
            ) {
                Box(Modifier.fillMaxSize().clipToBounds()) {
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        _StickerThumbnail(sticker = item.sticker, size = minOf(62.dp, tileHeight - 18.dp))
                    }
                    if (managing && selected) {
                        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.24f)), contentAlignment = Alignment.TopEnd) {
                            Box(
                                Modifier
                                    .padding(5.dp)
                                    .size(28.dp)
                                    .decoration(_stickerAccent, RectangleShape, UiColors.background, 2.dp)
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$selectionNumber",
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    style = uiTextStyle(color = UiColors.background, fontSize = 12.0, fontWeight = FontWeight.W600),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun _StickerThumbnail(sticker: Sticker, size: Dp) {
    val asset = sticker.asset
    val imageUrl = LocalAppConfig.current.resolveAssetUrl(asset.thumbnailUrl ?: asset.url)
    val fallback: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(UiColors.surfaceLow), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.ImageNotSupported, color = UiColors.textMuted, size = size * 0.38f)
        }
    }
    Box(
        Modifier
            .size(size)
            .decoration(Color.Transparent, RectangleShape, UiColors.border)
            .clipToBounds(),
        propagateMinConstraints = true,
    ) {
        if (imageUrl == null) {
            fallback()
        } else {
            CachedAssetImage(
                url = imageUrl,
                filename = asset.filename,
                mimeType = asset.mimeType,
                expectedBytes = asset.sizeBytes,
                fit = BoxFit.contain,
                modifier = Modifier.fillMaxSize(),
                errorBuilder = { _, _ -> fallback() },
            )
        }
    }
}

private class _StickerMimeFilter(val mimeType: String, val label: String)

private val _stickerMimeFilters =
    listOf(
        _StickerMimeFilter("", "全部"),
        _StickerMimeFilter("image/png", "PNG"),
        _StickerMimeFilter("image/jpeg", "JPG"),
        _StickerMimeFilter("image/webp", "WebP"),
        _StickerMimeFilter("image/gif", "GIF"),
    )

/**
 * Dart's Material `Dialog(backgroundColor: surfaceLow, shape: rounded lg +
 * border)` around a box at most [maxWidth] wide with the sticker dialogs'
 * padding.
 */
@Composable
private fun _StickerDialogFrame(maxWidth: Dp, borderColor: Color, content: @Composable () -> Unit) {
    Dialog {
        Column(
            Modifier
                .testTag(stickerDialogFrameTag)
                .widthIn(max = maxWidth)
                .decoration(UiColors.surfaceLow, RoundedCornerShape(UiRadii.lg), borderColor)
                .padding(EdgeInsets.fromLTRB(22.dp, 20.dp, 22.dp, 18.dp)),
        ) {
            content()
        }
    }
}

/** 表情筛选对话框。 */
@Composable
fun StickerFilterDialog(keyword: String, mimeType: String) =
    StatefulWidget(StickerFilterDialogWidget(keyword, mimeType)) { _StickerFilterDialogState() }

class StickerFilterDialogWidget(val keyword: String, val mimeType: String)

private class _StickerFilterDialogState : ViewState<StickerFilterDialogWidget>() {
    private lateinit var _keywordController: TextEditingController
    private val _keywordFocusNode = FocusNode()
    private lateinit var _mimeType: String

    override fun initState() {
        _keywordController = TextEditingController(text = widget.keyword)
        _mimeType = widget.mimeType
        // Dart requests focus after the first frame; the node defers the
        // request until the field attaches.
        _keywordFocusNode.requestFocus()
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun build() {
        _StickerDialogFrame(maxWidth = 420.dp, borderColor = UiColors.border) {
            Text("筛选", style = uiTextStyle(color = UiColors.text, fontSize = 18.0, fontWeight = FontWeight.W600))
            Spacer(Modifier.height(16.dp))
            _StickerFieldLabel("名称关键字")
            Spacer(Modifier.height(8.dp))
            Input(
                modifier = Modifier.fillMaxWidth(),
                controller = _keywordController,
                focusNode = _keywordFocusNode,
                hintText = "",
                showClearButton = true,
                minLines = 1,
                maxLines = 1,
            )
            Spacer(Modifier.height(16.dp))
            _StickerFieldLabel("图片类型")
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (filter in _stickerMimeFilters) {
                    val selected = _mimeType == filter.mimeType
                    PressableSurface(
                        onPressed = { setState { _mimeType = filter.mimeType } },
                        selected = selected,
                        width = 72.dp,
                        height = 36.dp,
                        padding = EdgeInsets.zero,
                        backgroundColor = UiColors.background,
                        selectedBackgroundColor = UiColors.selected,
                        pressedBackgroundColor = UiColors.surfaceLow,
                        borderColor = if (selected) UiColors.selectedBorder else UiColors.border,
                        selectedBorderColor = UiColors.selectedBorder,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                filter.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style =
                                    uiTextStyle(
                                        color = if (selected) _stickerAccent else UiColors.textSecondary,
                                        fontSize = 13.0,
                                        fontWeight = FontWeight.W600,
                                    ),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            ResponsiveDialogActionBar(
                expanded = true,
                modifier = Modifier.fillMaxWidth(),
                actions =
                    listOf(
                        ResponsiveDialogAction(
                            label = "重置",
                            icon = Icons.Filled.RestartAlt,
                            onPressed = {
                                _keywordController.clear()
                                setState { _mimeType = "" }
                            },
                        ),
                        ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(context).pop() }),
                        ResponsiveDialogAction(
                            label = "确认",
                            icon = Icons.Filled.Check,
                            tone = ButtonTone.primary,
                            onPressed = {
                                Navigator.of(context).pop(
                                    StickerFilterDraft(keyword = _keywordController.text.trim(), mimeType = _mimeType),
                                )
                            },
                        ),
                    ),
            )
        }
    }
}

/** 二次确认对话框。 */
@Composable
fun StickerConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    confirmIcon: ImageVector,
    danger: Boolean = false,
) {
    val context = rememberBuildContext()
    _StickerDialogFrame(maxWidth = 420.dp, borderColor = if (danger) _stickerDangerBorder else UiColors.border) {
        Text(title, style = uiTextStyle(color = if (danger) UiColors.danger else UiColors.text, fontSize = 18.0, fontWeight = FontWeight.W600))
        Spacer(Modifier.height(10.dp))
        Text(body, style = uiTextStyle(color = UiColors.textSecondary, fontSize = 13.0, height = 1.5))
        Spacer(Modifier.height(18.dp))
        ResponsiveDialogActionBar(
            modifier = Modifier.fillMaxWidth(),
            actions =
                listOf(
                    ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(context).pop(false) }),
                    ResponsiveDialogAction(
                        label = confirmLabel,
                        icon = confirmIcon,
                        tone = if (danger) ButtonTone.danger else ButtonTone.primary,
                        onPressed = { Navigator.of(context).pop(true) },
                    ),
                ),
        )
    }
}

@Composable
private fun _StickerPreviewImage(imageUrl: String, asset: UploadedAsset, onOpenPreview: (() -> Unit)?, modifier: Modifier) =
    StatefulWidget(_StickerPreviewImageWidget(imageUrl, asset, onOpenPreview, modifier)) { _StickerPreviewImageState() }

private class _StickerPreviewImageWidget(
    val imageUrl: String,
    val asset: UploadedAsset,
    val onOpenPreview: (() -> Unit)?,
    val modifier: Modifier,
)

private class _StickerPreviewImageState : ViewState<_StickerPreviewImageWidget>() {
    private var _cache: MediaCacheController? = null
    private var _resolvedSize: Size? = null
    private var _resolving = false
    private var _failed = false
    private var _resolveSerial = 0
    private var _hovered = false
    private var _inBuild = false

    // Dart's didChangeDependencies: runs when the MediaCacheScope changes,
    // just before a build (so state changes need no extra rebuild).
    private fun _syncCache() {
        val cache = MediaCacheScope.of(context)
        if (_cache !== cache) {
            _cache = cache
            _resolvedSize = _assetImageSize()
            _failed = false
            _inBuild = true
            try {
                _resolveIfNeeded()
            } finally {
                _inBuild = false
            }
        }
    }

    private fun _set(fn: () -> Unit) = if (_inBuild) fn() else setState(fn)

    override fun didUpdateWidget(oldWidget: _StickerPreviewImageWidget) {
        if (oldWidget.asset != widget.asset || oldWidget.imageUrl != widget.imageUrl) {
            _resolvedSize = _assetImageSize()
            _failed = false
            _hovered = false
            _resolveIfNeeded()
        }
    }

    override fun dispose() {
        _resolveSerial += 1
    }

    private fun _assetImageSize(): Size? {
        val width = widget.asset.width
        val height = widget.asset.height
        if (width == null || height == null || width <= 0 || height <= 0) return null
        return Size(width.toFloat(), height.toFloat())
    }

    private fun _resolveIfNeeded() {
        val assetSize = _assetImageSize()
        if (assetSize != null) {
            _resolveSerial += 1
            if (_resolvedSize != assetSize || _resolving || _failed) {
                _set {
                    _resolvedSize = assetSize
                    _resolving = false
                    _failed = false
                }
            }
            return
        }

        _resolveSerial += 1
        val cache = _cache
        if (cache == null || _resolving || _failed) return
        val request =
            MediaCacheRequest.tryFromUrl(
                url = widget.imageUrl,
                filename = widget.asset.filename,
                mimeType = widget.asset.mimeType,
                expectedBytes = widget.asset.sizeBytes,
            )
        if (request == null) {
            _set { _failed = true }
            return
        }

        val serial = ++_resolveSerial
        _set {
            _resolving = true
            _failed = false
        }
        unawaited {
            try {
                val file = cachedMediaImageProvider(cache, request)
                if (!mounted || serial != _resolveSerial) return@unawaited
                val size = decodeImageFileSize(file)
                if (!mounted || serial != _resolveSerial) return@unawaited
                setState {
                    _resolvedSize = size
                    _resolving = false
                    _failed = false
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!mounted || serial != _resolveSerial) return@unawaited
                setState {
                    _resolving = false
                    _failed = true
                }
            }
        }
    }

    @Composable
    override fun build() {
        _syncCache()
        val canOpen = widget.onOpenPreview != null
        BoxWithConstraints(widget.modifier.testTag(stickerPreviewImageTag).height(320.dp)) {
            val viewport = Size(maxWidth.value, maxHeight.value)
            val sourceSize = _resolvedSize
            if (sourceSize == null) {
                _buildPlaceholder()
                return@BoxWithConstraints
            }
            val imageRect = inscribeCentered(applyBoxFitDestination(BoxFit.contain, sourceSize, viewport), viewport)
            val hoverOpacity by animateFloatAsState(if (_hovered) 1f else 0f, tween(120), label = "stickerPreviewHover")
            Box(Modifier.fillMaxSize().clipToBounds()) {
                Box(
                    Modifier
                        .offset(imageRect.left.dp, imageRect.top.dp)
                        .size(imageRect.width.dp, imageRect.height.dp)
                        .pointerInput(canOpen) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (!canOpen || event.changes.none { it.type == PointerType.Mouse }) continue
                                    when (event.type) {
                                        PointerEventType.Enter -> setState { _hovered = true }
                                        PointerEventType.Exit -> setState { _hovered = false }
                                        else -> Unit
                                    }
                                }
                            }
                        }
                        .then(
                            if (canOpen) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Image,
                                    onClick = { widget.onOpenPreview?.invoke() },
                                )
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    CachedAssetImage(
                        url = widget.imageUrl,
                        filename = widget.asset.filename,
                        mimeType = widget.asset.mimeType,
                        expectedBytes = widget.asset.sizeBytes,
                        width = imageRect.width.dp,
                        height = imageRect.height.dp,
                        fit = BoxFit.contain,
                        loadingBuilder = { _ -> _buildPlaceholder() },
                        errorBuilder = { _, _ -> _buildPlaceholder(error = true) },
                    )
                    // Flutter's IgnorePointer over a purely visual overlay: a node
                    // without pointer input is already invisible to hit testing,
                    // so taps reach the tile underneath.
                    Box(
                        Modifier.matchParentSize().graphicsLayer { alpha = hoverOpacity },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Search, color = Color.White.copy(alpha = 0.92f), size = 36.dp)
                    }
                }
            }
        }
    }

    @Composable
    private fun _buildPlaceholder(error: Boolean = false) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(if (error) Icons.Outlined.BrokenImage else Icons.Outlined.Image, color = UiColors.textMuted, size = 42.dp)
        }
    }
}

/** 表情预览/编辑对话框。[onSetAvatar] 为空时隐藏“设为头像”(房间场景)。 */
@Composable
fun StickerPreviewDialog(
    item: ManagedSticker,
    imageUrl: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canPin: Boolean,
    canRename: Boolean,
    canDownload: Boolean,
    canDelete: Boolean,
    onRename: suspend (name: String) -> String?,
    onSetAvatar: (suspend () -> Unit)?,
    onDownload: suspend () -> Unit,
    onDelete: suspend () -> Boolean,
    onMoveUp: suspend () -> StickerPlacementData?,
    onMoveDown: suspend () -> StickerPlacementData?,
    onPin: suspend () -> StickerPlacementData?,
    imagePreviewOpener: StickerImagePreviewOpener? = null,
) = StatefulWidget(
    StickerPreviewDialogWidget(
        item, imageUrl, canMoveUp, canMoveDown, canPin, canRename, canDownload, canDelete, onRename, onSetAvatar, onDownload,
        onDelete, onMoveUp, onMoveDown, onPin, imagePreviewOpener,
    ),
) { _StickerPreviewDialogState() }

class StickerPreviewDialogWidget(
    val item: ManagedSticker,
    val imageUrl: String,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val canPin: Boolean,
    val canRename: Boolean,
    val canDownload: Boolean,
    val canDelete: Boolean,
    val onRename: suspend (name: String) -> String?,
    val onSetAvatar: (suspend () -> Unit)?,
    val onDownload: suspend () -> Unit,
    val onDelete: suspend () -> Boolean,
    val onMoveUp: suspend () -> StickerPlacementData?,
    val onMoveDown: suspend () -> StickerPlacementData?,
    val onPin: suspend () -> StickerPlacementData?,
    val imagePreviewOpener: StickerImagePreviewOpener?,
)

// Dart's `\s` is JavaScript whitespace (Unicode spaces included).
private val _unsafeFilenameCharacters =
    Regex("[\\\\/:*?\"<>|\\s\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]+")

private class _StickerPreviewDialogState : ViewState<StickerPreviewDialogWidget>() {
    private lateinit var _nameController: TextEditingController
    private lateinit var _previewState: StickerPreviewState
    private var _errorEventKey = 0

    private val _sticker: Sticker get() = widget.item.sticker
    private val _busy: Boolean get() = _previewState.busy
    private val _canMoveUp: Boolean get() = _previewState.canMoveUp
    private val _canMoveDown: Boolean get() = _previewState.canMoveDown
    private val _canPin: Boolean get() = _previewState.canPin
    private val _savingName: Boolean get() = _previewState.savingName
    private val _settingAvatar: Boolean get() = _previewState.settingAvatar
    private val _downloading: Boolean get() = _previewState.downloading
    private val _deleting: Boolean get() = _previewState.deleting
    private val _movingUp: Boolean get() = _previewState.movingUp
    private val _movingDown: Boolean get() = _previewState.movingDown
    private val _pinning: Boolean get() = _previewState.pinning
    private val _error: String? get() = _previewState.error

    override fun initState() {
        _nameController = TextEditingController(text = _sticker.name)
        _previewState =
            StickerPreviewState.initial(canMoveUp = widget.canMoveUp, canMoveDown = widget.canMoveDown, canPin = widget.canPin)
    }

    private suspend fun _saveName() {
        val name = stickerRenameName(_nameController.text)
        if (name == null || !widget.canRename) return
        _runPreviewAction(
            actionKind = StickerPreviewActionKind.rename,
            name = _nameController.text,
            action = { widget.onRename(name) },
            onSuccess = { actualName ->
                if (actualName == null) {
                    setState {
                        _previewState =
                            stickerPreviewActionFailed(state = _previewState, action = StickerPreviewActionKind.rename, failure = "名称保存失败")
                        _errorEventKey++
                    }
                } else {
                    _nameController.text = actualName
                    Navigator.of(context).pop()
                }
            },
        )
    }

    private suspend fun _setAvatar() {
        val onSetAvatar = widget.onSetAvatar ?: return
        _runPreviewAction(actionKind = StickerPreviewActionKind.setAvatar, action = {
            onSetAvatar()
            true
        })
    }

    private suspend fun _download() {
        _runPreviewAction(actionKind = StickerPreviewActionKind.download, action = {
            widget.onDownload()
            true
        })
    }

    private suspend fun _openImagePreview() {
        val opener = widget.imagePreviewOpener ?: return
        opener(context, widget.imageUrl, _previewSuggestedName, false)
    }

    private val _previewSuggestedName: String
        get() {
            val asset = _sticker.asset
            val filename = asset.filename?.trim()
            if (filename != null && filename.isNotEmpty()) return filename
            val rawName = _sticker.name.trim()
            val safeName = rawName.replace(_unsafeFilenameCharacters, "_").replace(Regex("_+"), "_")
            val stem = if (safeName.isEmpty() || safeName == "_") "sticker" else safeName
            return "$stem.${imageExtensionForMimeType(asset.mimeType)}"
        }

    private suspend fun _delete() {
        _runPreviewAction(
            actionKind = StickerPreviewActionKind.delete,
            action = widget.onDelete,
            onSuccess = { deleted -> if (deleted && mounted) Navigator.of(context).pop() },
        )
    }

    private suspend fun _move(action: suspend () -> StickerPlacementData?, actionKind: StickerPreviewActionKind) {
        _runPreviewAction(
            actionKind = actionKind,
            action = action,
            onSuccess = { placement ->
                if (placement == null) return@_runPreviewAction
                setState {
                    _previewState =
                        stickerPreviewMoveSucceeded(
                            state = _previewState,
                            action = actionKind,
                            canMoveUp = placement.canMoveUp,
                            canMoveDown = placement.canMoveDown,
                            canPin = placement.canPin,
                        )
                }
            },
        )
    }

    private suspend fun <T> _runPreviewAction(
        actionKind: StickerPreviewActionKind,
        name: String? = null,
        action: suspend () -> T,
        onSuccess: ((T) -> Unit)? = null,
    ): T? {
        val started = stickerPreviewActionRequested(state = _previewState, action = actionKind, name = name) ?: return null
        setState { _previewState = started }
        try {
            val result = action()
            if (!mounted) return result
            onSuccess?.invoke(result)
            return result
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (mounted) {
                setState {
                    _previewState = stickerPreviewActionFailed(state = _previewState, action = actionKind, failure = e)
                    _errorEventKey++
                }
            }
            return null
        } finally {
            if (mounted) {
                setState { _previewState = stickerPreviewActionFinished(state = _previewState, action = actionKind) }
            }
        }
    }

    @Composable
    override fun build() {
        val asset = _sticker.asset
        val showSetAvatar = widget.onSetAvatar != null
        val error = _error
        // Like Dart, typing in the name field does not rebuild the dialog (only
        // the field itself): read the text without subscribing to it.
        val nameText = Snapshot.withoutReadObservation { _nameController.text }
        _StickerDialogFrame(maxWidth = 640.dp, borderColor = UiColors.border) {
            FloatingNoticeEmitter(
                notices =
                    listOfNotNull(
                        error?.let { FloatingNotice(message = it, tone = FloatingNoticeTone.error, duration = null, eventKey = _errorEventKey) },
                    ),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "表情预览",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = uiTextStyle(color = UiColors.text, fontSize = 18.0, fontWeight = FontWeight.W600),
                        )
                        ButtonIcon(
                            onPressed = { Navigator.of(context).pop() },
                            tooltip = "关闭预览",
                            icon = { Icon(Icons.Filled.Close) },
                            size = 32.dp,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    _StickerPreviewImage(
                        imageUrl = widget.imageUrl,
                        asset = asset,
                        onOpenPreview = if (widget.imagePreviewOpener == null) null else ({ unawaited { _openImagePreview() } }),
                        modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    Input(
                        modifier = Modifier.fillMaxWidth(),
                        controller = _nameController,
                        hintText = "名称",
                        enabled = widget.canRename,
                        minLines = 1,
                        maxLines = 1,
                        onSubmitted = if (widget.canRename) ({ _ -> unawaited { _saveName() } }) else null,
                    )
                    Spacer(Modifier.height(8.dp))
                    _StickerDimensionsLine(asset = asset, imageUrl = widget.imageUrl)
                    Spacer(Modifier.height(16.dp))
                    StickerActionGrid(
                        modifier = Modifier.fillMaxWidth(),
                        actions =
                            buildList {
                                add(
                                    StickerActionGridEntry(
                                        label = "下载",
                                        button =
                                            ButtonWidget(
                                                onPressed = if (_busy || !widget.canDownload) null else ({ unawaited { _download() } }),
                                                loading = _downloading,
                                                icon = { Icon(Icons.Outlined.Download) },
                                                width = Dp.Infinity,
                                                child = { Text("下载") },
                                            ),
                                    ),
                                )
                                if (showSetAvatar) {
                                    add(
                                        StickerActionGridEntry(
                                            label = "设为头像",
                                            button =
                                                ButtonWidget(
                                                    onPressed = if (_busy) null else ({ unawaited { _setAvatar() } }),
                                                    loading = _settingAvatar,
                                                    icon = { Icon(Icons.Outlined.AccountCircle) },
                                                    width = Dp.Infinity,
                                                    child = { Text("设为头像") },
                                                ),
                                        ),
                                    )
                                }
                                add(
                                    StickerActionGridEntry(
                                        label = "保存名称",
                                        button =
                                            ButtonWidget(
                                                onPressed =
                                                    if (canStartStickerRename(busy = _busy, name = nameText, allowed = widget.canRename)) {
                                                        { unawaited { _saveName() } }
                                                    } else {
                                                        null
                                                    },
                                                loading = _savingName,
                                                tone = ButtonTone.primary,
                                                icon = { Icon(Icons.Outlined.Save) },
                                                width = Dp.Infinity,
                                                child = { Text("保存名称") },
                                            ),
                                    ),
                                )
                                add(
                                    StickerActionGridEntry(
                                        label = "置顶",
                                        button =
                                            ButtonWidget(
                                                onPressed =
                                                    if (_busy || !_canPin) {
                                                        null
                                                    } else {
                                                        { unawaited { _move(action = widget.onPin, actionKind = StickerPreviewActionKind.pin) } }
                                                    },
                                                loading = _pinning,
                                                icon = { Icon(Icons.Filled.VerticalAlignTop) },
                                                width = Dp.Infinity,
                                                child = { Text("置顶") },
                                            ),
                                    ),
                                )
                                add(
                                    StickerActionGridEntry(
                                        label = "上移一位",
                                        button =
                                            ButtonWidget(
                                                onPressed =
                                                    if (_busy || !_canMoveUp) {
                                                        null
                                                    } else {
                                                        { unawaited { _move(action = widget.onMoveUp, actionKind = StickerPreviewActionKind.moveUp) } }
                                                    },
                                                loading = _movingUp,
                                                icon = { Icon(Icons.Filled.ArrowUpward) },
                                                width = Dp.Infinity,
                                                child = { Text("上移一位") },
                                            ),
                                    ),
                                )
                                add(
                                    StickerActionGridEntry(
                                        label = "下移一位",
                                        button =
                                            ButtonWidget(
                                                onPressed =
                                                    if (_busy || !_canMoveDown) {
                                                        null
                                                    } else {
                                                        {
                                                            unawaited {
                                                                _move(action = widget.onMoveDown, actionKind = StickerPreviewActionKind.moveDown)
                                                            }
                                                        }
                                                    },
                                                loading = _movingDown,
                                                icon = { Icon(Icons.Filled.ArrowDownward) },
                                                width = Dp.Infinity,
                                                child = { Text("下移一位") },
                                            ),
                                    ),
                                )
                            },
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onPressed = if (_busy || !widget.canDelete) null else ({ unawaited { _delete() } }),
                        loading = _deleting,
                        tone = ButtonTone.danger,
                        icon = { Icon(Icons.Filled.DeleteOutline) },
                        width = Dp.Infinity,
                    ) {
                        Text("删除")
                    }
                }
            }
        }
    }
}

@Composable
private fun _StickerDimensionsLine(asset: UploadedAsset, imageUrl: String) =
    StatefulWidget(_StickerDimensionsLineWidget(asset, imageUrl)) { _StickerDimensionsLineState() }

private class _StickerDimensionsLineWidget(val asset: UploadedAsset, val imageUrl: String)

private class _StickerDimensionsLineState : ViewState<_StickerDimensionsLineWidget>() {
    private var _cache: MediaCacheController? = null
    private var _resolvedDimensions: StickerImageDimensions? = null
    private var _resolving = false
    private var _failed = false
    private var _resolveSerial = 0
    private var _inBuild = false

    // Dart's didChangeDependencies (see _StickerPreviewImageState).
    private fun _syncCache() {
        val cache = MediaCacheScope.of(context)
        if (_cache !== cache) {
            _cache = cache
            _resolvedDimensions = null
            _failed = false
            _inBuild = true
            try {
                _resolveIfNeeded()
            } finally {
                _inBuild = false
            }
        }
    }

    private fun _set(fn: () -> Unit) = if (_inBuild) fn() else setState(fn)

    override fun didUpdateWidget(oldWidget: _StickerDimensionsLineWidget) {
        if (oldWidget.asset != widget.asset || oldWidget.imageUrl != widget.imageUrl) {
            _resolvedDimensions = null
            _failed = false
            _resolveIfNeeded()
        }
    }

    override fun dispose() {
        _resolveSerial += 1
    }

    private fun _resolveIfNeeded() {
        val cache = _cache
        if (widget.asset.width != null && widget.asset.height != null) {
            _resolveSerial += 1
            if (_resolving || _resolvedDimensions != null || _failed) {
                _set {
                    _resolving = false
                    _resolvedDimensions = null
                    _failed = false
                }
            }
            return
        }
        _resolveSerial += 1
        if (cache == null) return
        val request =
            MediaCacheRequest.tryFromUrl(
                url = widget.imageUrl,
                filename = widget.asset.filename,
                mimeType = widget.asset.mimeType,
                expectedBytes = widget.asset.sizeBytes,
            )
        if (request == null) {
            _set {
                _resolving = false
                _failed = true
            }
            return
        }
        val serial = ++_resolveSerial
        _set {
            _resolving = true
            _failed = false
        }
        unawaited {
            try {
                val file = cachedMediaImageProvider(cache, request)
                if (!mounted || serial != _resolveSerial) return@unawaited
                val size = decodeImageFileSize(file)
                if (!mounted || serial != _resolveSerial) return@unawaited
                setState {
                    _resolvedDimensions = StickerImageDimensions(width = size.width.toInt(), height = size.height.toInt())
                    _resolving = false
                    _failed = false
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!mounted || serial != _resolveSerial) return@unawaited
                setState {
                    _resolving = false
                    _failed = true
                }
            }
        }
    }

    @Composable
    override fun build() {
        _syncCache()
        val dimensions = stickerDimensionsText(widget.asset, resolved = _resolvedDimensions, resolving = _resolving, failed = _failed)
        Text(
            "${widget.asset.mimeType} · $dimensions",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = uiTextStyle(color = UiColors.textMuted, fontSize = 12.0, fontWeight = FontWeight.W500),
        )
    }
}
