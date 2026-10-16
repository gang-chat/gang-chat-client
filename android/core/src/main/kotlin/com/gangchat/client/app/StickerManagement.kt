package com.gangchat.client.app

import com.gangchat.client.protocol.Sticker
import com.gangchat.client.protocol.StickerPack

enum class StickerManagementScope { personal, room }

enum class StickerActionKind { upload, delete, order, download }

enum class StickerPreviewActionKind {
    rename,
    setAvatar,
    download,
    delete,
    moveUp,
    moveDown,
    pin,
}

class StickerManagementCapabilities(
    val canUpload: Boolean = true,
    val canBatchManage: Boolean = true,
    val canFilter: Boolean = true,
    val canDownload: Boolean = true,
    val canSelectAll: Boolean = true,
    val canDelete: Boolean = true,
    val canPin: Boolean = true,
    val canRename: Boolean = true,
    val canMove: Boolean = true,
    val canSetAvatar: Boolean = true,
) {
    companion object {
        /** Dart `const StickerManagementCapabilities.readOnlyDownloads()`. */
        fun readOnlyDownloads(): StickerManagementCapabilities =
            StickerManagementCapabilities(
                canUpload = false,
                canBatchManage = true,
                canFilter = true,
                canDownload = true,
                canSelectAll = true,
                canDelete = false,
                canPin = false,
                canRename = false,
                canMove = false,
                canSetAvatar = false,
            )
    }
}

private val _stickerPreviewErrorUnchanged: Any = Any()

class ManagedSticker(val pack: StickerPack, val sticker: Sticker)

class StickerPackLoadPatch(
    val packs: List<StickerPack>,
    val selectedStickerIds: List<String>,
    val loading: Boolean,
    val error: String?,
)

class StickerSelectionPatch(
    val managing: Boolean,
    val filterKeyword: String,
    val filterMimeType: String,
    val selectedStickerIds: List<String>,
)

class StickerFilterDraft(val keyword: String, val mimeType: String)

class StickerFilterDraftPatch(val mimeType: String)

class StickerActionPatch(
    val uploading: Boolean,
    val deleting: Boolean,
    val savingOrder: Boolean,
    val downloading: Boolean,
    val selectedStickerIds: List<String>,
    val error: String?,
    val notice: String?,
)

class StickerPreviewState(
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val canPin: Boolean,
    val savingName: Boolean = false,
    val settingAvatar: Boolean = false,
    val downloading: Boolean = false,
    val deleting: Boolean = false,
    val movingUp: Boolean = false,
    val movingDown: Boolean = false,
    val pinning: Boolean = false,
    val error: String? = null,
) {
    val busy: Boolean
        get() =
            savingName ||
                settingAvatar ||
                downloading ||
                deleting ||
                movingUp ||
                movingDown ||
                pinning

    /**
     * [error] keeps Dart's sentinel default: omit it to keep the current
     * error, pass `null` to clear it.
     */
    fun copyWith(
        canMoveUp: Boolean? = null,
        canMoveDown: Boolean? = null,
        canPin: Boolean? = null,
        savingName: Boolean? = null,
        settingAvatar: Boolean? = null,
        downloading: Boolean? = null,
        deleting: Boolean? = null,
        movingUp: Boolean? = null,
        movingDown: Boolean? = null,
        pinning: Boolean? = null,
        error: Any? = _stickerPreviewErrorUnchanged,
    ): StickerPreviewState {
        return StickerPreviewState(
            canMoveUp = canMoveUp ?: this.canMoveUp,
            canMoveDown = canMoveDown ?: this.canMoveDown,
            canPin = canPin ?: this.canPin,
            savingName = savingName ?: this.savingName,
            settingAvatar = settingAvatar ?: this.settingAvatar,
            downloading = downloading ?: this.downloading,
            deleting = deleting ?: this.deleting,
            movingUp = movingUp ?: this.movingUp,
            movingDown = movingDown ?: this.movingDown,
            pinning = pinning ?: this.pinning,
            error =
                if (error === _stickerPreviewErrorUnchanged) {
                    this.error
                } else {
                    error as String?
                },
        )
    }

    companion object {
        fun initial(
            canMoveUp: Boolean,
            canMoveDown: Boolean,
            canPin: Boolean,
        ): StickerPreviewState {
            return StickerPreviewState(
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                canPin = canPin,
            )
        }
    }
}

fun managedStickerItems(
    packs: List<StickerPack>,
    orderForPack: ((pack: StickerPack) -> List<String>?)? = null,
): List<ManagedSticker> {
    val items = mutableListOf<ManagedSticker>()
    for (pack in packs) {
        for (sticker in orderedStickers(pack, order = orderForPack?.invoke(pack))) {
            items.add(ManagedSticker(pack = pack, sticker = sticker))
        }
    }
    return items
}

fun filteredManagedStickerItems(
    items: Iterable<ManagedSticker>,
    keyword: String,
    mimeType: String,
): List<ManagedSticker> {
    val normalizedKeyword = keyword.trim().lowercase()
    return items.filter { item ->
        _matchesStickerFilter(
            item.sticker,
            keyword = normalizedKeyword,
            mimeType = mimeType,
        )
    }
}

fun stickerFilterActive(keyword: String, mimeType: String): Boolean {
    return keyword.trim().isNotEmpty() || mimeType.isNotEmpty()
}

fun stickerManagementBusy(
    uploading: Boolean,
    deleting: Boolean,
    savingOrder: Boolean,
    downloading: Boolean,
): Boolean {
    return uploading || deleting || savingOrder || downloading
}

fun stickerManagementCountText(
    filterActive: Boolean,
    visibleCount: Int,
    totalCount: Int,
): String {
    return if (filterActive) "$visibleCount / $totalCount 个" else "$totalCount 个"
}

fun canStartStickerPrimaryAction(busy: Boolean, allowed: Boolean = true): Boolean {
    return allowed && !busy
}

fun canStartStickerSelectionAction(
    busy: Boolean,
    selectedStickerIds: Iterable<String>,
    allowed: Boolean = true,
): Boolean {
    return allowed && !busy && selectedStickerIds.any()
}

fun canSelectVisibleStickers(
    busy: Boolean,
    visibleItems: Iterable<ManagedSticker>,
    allowed: Boolean = true,
): Boolean {
    return allowed && !busy && visibleItems.any()
}

fun stickerAllVisibleSelected(
    selectedStickerIds: Iterable<String>,
    visibleItems: List<ManagedSticker>,
): Boolean {
    if (visibleItems.isEmpty()) return false
    val selectedSet = selectedStickerIds.toSet()
    return visibleItems.all { item -> selectedSet.contains(item.sticker.id) }
}

@Suppress("UNUSED_PARAMETER")
fun stickerVisibleSelectionButtonText(
    selectedStickerIds: Iterable<String>,
    visibleItems: List<ManagedSticker>,
): String {
    return "全选"
}

fun canUseStickerManagementControl(busy: Boolean, allowed: Boolean = true): Boolean {
    return allowed && !busy
}

fun stickerRenameName(value: String): String? {
    val trimmed = value.trim()
    return if (trimmed.isEmpty()) null else trimmed
}

fun canStartStickerRename(
    busy: Boolean,
    name: String,
    allowed: Boolean = true,
): Boolean {
    return allowed && !busy && stickerRenameName(name) != null
}

fun canStartStickerPreviewAction(
    state: StickerPreviewState,
    action: StickerPreviewActionKind,
    name: String? = null,
): Boolean {
    if (action == StickerPreviewActionKind.rename) {
        return canStartStickerRename(busy = state.busy, name = name ?: "")
    }
    return !state.busy
}

fun stickerPreviewActionRequested(
    state: StickerPreviewState,
    action: StickerPreviewActionKind,
    name: String? = null,
): StickerPreviewState? {
    if (!canStartStickerPreviewAction(state = state, action = action, name = name)) {
        return null
    }
    return stickerPreviewActionStarted(state = state, action = action)
}

fun defaultStickerPackName(scope: StickerManagementScope): String {
    return if (scope == StickerManagementScope.room) "房间表情包" else "我的表情包"
}

fun stickerUploadNotice(
    scope: StickerManagementScope,
    count: Int,
): String {
    return if (scope == StickerManagementScope.room) {
        "已添加 $count 个房间表情"
    } else {
        "已添加 $count 个表情"
    }
}

fun stickerDeleteDialogTitle(scope: StickerManagementScope): String {
    return if (scope == StickerManagementScope.room) "删除房间表情" else "删除表情"
}

fun stickerSingleDeleteConfirmBody(
    scope: StickerManagementScope,
    stickerName: String,
): String {
    return if (scope == StickerManagementScope.room) {
        "将从这个房间的表情包中删除「$stickerName」。"
    } else {
        "将从服务端删除「$stickerName」，删除后不会再出现在你的表情包里。"
    }
}

fun stickerBulkDeleteConfirmBody(
    scope: StickerManagementScope,
    count: Int,
): String {
    return if (scope == StickerManagementScope.room) {
        "将从这个房间的表情包中删除选中的 $count 个表情。"
    } else {
        "将从服务端删除选中的 $count 个表情，删除后不会再出现在你的表情包里。"
    }
}

fun stickerDeletedNotice(
    scope: StickerManagementScope,
    count: Int? = null,
): String {
    if (scope == StickerManagementScope.room) {
        return if (count == null) "房间表情已删除" else "已删除 $count 个房间表情"
    }
    return if (count == null) "表情已删除" else "已删除 $count 个表情"
}

fun stickerDownloadNotice(
    scope: StickerManagementScope,
    count: Int,
): String {
    if (scope == StickerManagementScope.room) {
        return if (count == 1) "房间表情已下载" else "房间表情压缩包已下载"
    }
    return if (count == 1) "表情已下载" else "表情压缩包已下载"
}

fun stickerNoOrderChangeNotice(scope: StickerManagementScope): String {
    return if (scope == StickerManagementScope.room) "房间表情排序没有变化" else "表情排序没有变化"
}

fun stickerPinnedNotice(
    scope: StickerManagementScope,
    count: Int? = null,
): String {
    if (scope == StickerManagementScope.room) {
        return if (count == null) "房间表情已置顶" else "已置顶 $count 个房间表情"
    }
    return if (count == null) "表情已置顶" else "已置顶 $count 个表情"
}

fun stickerMoveNotice(
    scope: StickerManagementScope,
    delta: Int,
): String {
    val direction = if (delta < 0) "上" else "下"
    return if (scope == StickerManagementScope.room) {
        "房间表情已${direction}移一位"
    } else {
        "表情已${direction}移一位"
    }
}

fun stickerActionStarted(
    action: StickerActionKind,
    uploading: Boolean,
    deleting: Boolean,
    savingOrder: Boolean,
    downloading: Boolean,
    selectedStickerIds: Iterable<String>,
): StickerActionPatch {
    return _stickerActionPatch(
        action = action,
        actionBusy = true,
        uploading = uploading,
        deleting = deleting,
        savingOrder = savingOrder,
        downloading = downloading,
        selectedStickerIds = selectedStickerIds.toList(),
        error = null,
        notice = null,
    )
}

fun stickerActionSucceeded(
    action: StickerActionKind,
    uploading: Boolean,
    deleting: Boolean,
    savingOrder: Boolean,
    downloading: Boolean,
    selectedStickerIds: Iterable<String>,
    error: String?,
    notice: String,
    clearSelection: Boolean = false,
): StickerActionPatch {
    return _stickerActionPatch(
        action = action,
        actionBusy = false,
        uploading = uploading,
        deleting = deleting,
        savingOrder = savingOrder,
        downloading = downloading,
        selectedStickerIds = if (clearSelection) emptyList() else selectedStickerIds.toList(),
        error = error,
        notice = notice,
    )
}

fun stickerActionFailed(
    action: StickerActionKind,
    uploading: Boolean,
    deleting: Boolean,
    savingOrder: Boolean,
    downloading: Boolean,
    selectedStickerIds: Iterable<String>,
    failure: Any,
): StickerActionPatch {
    return _stickerActionPatch(
        action = action,
        actionBusy = false,
        uploading = uploading,
        deleting = deleting,
        savingOrder = savingOrder,
        downloading = downloading,
        selectedStickerIds = selectedStickerIds.toList(),
        error = userFacingErrorMessage(failure),
        notice = null,
    )
}

fun stickerActionCancelled(
    action: StickerActionKind,
    uploading: Boolean,
    deleting: Boolean,
    savingOrder: Boolean,
    downloading: Boolean,
    selectedStickerIds: Iterable<String>,
): StickerActionPatch {
    return _stickerActionPatch(
        action = action,
        actionBusy = false,
        uploading = uploading,
        deleting = deleting,
        savingOrder = savingOrder,
        downloading = downloading,
        selectedStickerIds = selectedStickerIds.toList(),
        error = null,
        notice = null,
    )
}

fun stickerActionNoticeShown(
    uploading: Boolean,
    deleting: Boolean,
    savingOrder: Boolean,
    downloading: Boolean,
    selectedStickerIds: Iterable<String>,
    error: String?,
    notice: String,
): StickerActionPatch {
    return StickerActionPatch(
        uploading = uploading,
        deleting = deleting,
        savingOrder = savingOrder,
        downloading = downloading,
        selectedStickerIds = selectedStickerIds.toList(),
        error = error,
        notice = notice,
    )
}

fun stickerActionErrorShown(
    uploading: Boolean,
    deleting: Boolean,
    savingOrder: Boolean,
    downloading: Boolean,
    selectedStickerIds: Iterable<String>,
    failure: Any,
    notice: String? = null,
): StickerActionPatch {
    return StickerActionPatch(
        uploading = uploading,
        deleting = deleting,
        savingOrder = savingOrder,
        downloading = downloading,
        selectedStickerIds = selectedStickerIds.toList(),
        error = userFacingErrorMessage(failure),
        notice = notice,
    )
}

fun stickerPreviewActionStarted(
    state: StickerPreviewState,
    action: StickerPreviewActionKind,
): StickerPreviewState {
    return _stickerPreviewActionState(
        state,
        action = action,
        active = true,
        error = null,
    )
}

fun stickerPreviewActionFinished(
    state: StickerPreviewState,
    action: StickerPreviewActionKind,
): StickerPreviewState {
    return _stickerPreviewActionState(state, action = action, active = false)
}

fun stickerPreviewActionFailed(
    state: StickerPreviewState,
    action: StickerPreviewActionKind,
    failure: Any,
): StickerPreviewState {
    return _stickerPreviewActionState(
        state,
        action = action,
        active = false,
        error = userFacingErrorMessage(failure),
    )
}

fun stickerPreviewMoveSucceeded(
    state: StickerPreviewState,
    action: StickerPreviewActionKind,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canPin: Boolean,
): StickerPreviewState {
    return _stickerPreviewActionState(
        state,
        action = action,
        active = false,
    ).copyWith(canMoveUp = canMoveUp, canMoveDown = canMoveDown, canPin = canPin)
}

fun stickerPacksLoadStarted(
    packs: Iterable<StickerPack>,
    selectedStickerIds: Iterable<String>,
): StickerPackLoadPatch {
    return StickerPackLoadPatch(
        packs = packs.toList(),
        selectedStickerIds = selectedStickerIds.toList(),
        loading = true,
        error = null,
    )
}

fun stickerPacksLoadSucceeded(
    packs: Iterable<StickerPack>,
    selectedStickerIds: Iterable<String>,
): StickerPackLoadPatch {
    val nextPacks = packs.toList()
    return StickerPackLoadPatch(
        packs = nextPacks,
        selectedStickerIds =
            retainedStickerSelection(
                selectedStickerIds.toList(),
                nextPacks,
            ),
        loading = false,
        error = null,
    )
}

fun stickerPacksLoadFailed(
    packs: Iterable<StickerPack>,
    selectedStickerIds: Iterable<String>,
    failure: Any,
): StickerPackLoadPatch {
    return StickerPackLoadPatch(
        packs = packs.toList(),
        selectedStickerIds = selectedStickerIds.toList(),
        loading = false,
        error = userFacingErrorMessage(failure),
    )
}

fun stickerPackUpserted(
    packs: Iterable<StickerPack>,
    selectedStickerIds: Iterable<String>,
    pack: StickerPack,
    loading: Boolean,
    error: String?,
): StickerPackLoadPatch {
    val nextPacks = _upsertStickerPack(packs.toList(), pack)
    return StickerPackLoadPatch(
        packs = nextPacks,
        selectedStickerIds =
            retainedStickerSelection(
                selectedStickerIds.toList(),
                nextPacks,
            ),
        loading = loading,
        error = error,
    )
}

fun stickerManagementModeToggled(
    managing: Boolean,
    filterKeyword: String,
    filterMimeType: String,
): StickerSelectionPatch {
    return StickerSelectionPatch(
        managing = !managing,
        filterKeyword = filterKeyword,
        filterMimeType = filterMimeType,
        selectedStickerIds = emptyList(),
    )
}

fun stickerSelectionToggled(
    managing: Boolean,
    filterKeyword: String,
    filterMimeType: String,
    selectedStickerIds: Iterable<String>,
    stickerId: String,
): StickerSelectionPatch {
    return StickerSelectionPatch(
        managing = managing,
        filterKeyword = filterKeyword,
        filterMimeType = filterMimeType,
        selectedStickerIds =
            if (managing) {
                toggledStickerSelection(selectedStickerIds.toList(), stickerId)
            } else {
                selectedStickerIds.toList()
            },
    )
}

fun stickerVisibleSelectionToggled(
    managing: Boolean,
    busy: Boolean,
    filterKeyword: String,
    filterMimeType: String,
    selectedStickerIds: Iterable<String>,
    visibleItems: List<ManagedSticker>,
): StickerSelectionPatch {
    return StickerSelectionPatch(
        managing = managing,
        filterKeyword = filterKeyword,
        filterMimeType = filterMimeType,
        selectedStickerIds =
            if (managing &&
                canSelectVisibleStickers(busy = busy, visibleItems = visibleItems)
            ) {
                stickerSelectionForVisibleItems(
                    selectedStickerIds.toList(),
                    visibleItems,
                )
            } else {
                selectedStickerIds.toList()
            },
    )
}

fun stickerFilterApplied(
    managing: Boolean,
    keyword: String,
    mimeType: String,
): StickerSelectionPatch {
    return StickerSelectionPatch(
        managing = managing,
        filterKeyword = keyword,
        filterMimeType = mimeType,
        selectedStickerIds = emptyList(),
    )
}

fun stickerFilterMimeTypeChanged(mimeType: String): StickerFilterDraftPatch {
    return StickerFilterDraftPatch(mimeType = mimeType)
}

fun stickerFilterDraftReset(): StickerFilterDraftPatch {
    return StickerFilterDraftPatch(mimeType = "")
}

fun stickerSelectionNumbers(selectedStickerIds: List<String>): Map<String, Int> {
    val numbers = LinkedHashMap<String, Int>()
    for ((index, stickerId) in selectedStickerIds.withIndex()) {
        numbers[stickerId] = index + 1
    }
    return numbers
}

fun toggledStickerSelection(
    selectedStickerIds: List<String>,
    stickerId: String,
): List<String> {
    val next = selectedStickerIds.toMutableList()
    if (next.contains(stickerId)) {
        next.remove(stickerId)
    } else {
        next.add(stickerId)
    }
    return next
}

fun stickerSelectionForVisibleItems(
    selectedStickerIds: List<String>,
    visibleItems: List<ManagedSticker>,
): List<String> {
    if (visibleItems.isEmpty()) return selectedStickerIds
    val visibleIds = visibleItems.map { item -> item.sticker.id }
    val visibleSet = visibleIds.toSet()
    val allVisibleSelected =
        stickerAllVisibleSelected(
            selectedStickerIds = selectedStickerIds,
            visibleItems = visibleItems,
        )
    return if (allVisibleSelected) {
        selectedStickerIds.filter { id -> !visibleSet.contains(id) }
    } else {
        visibleIds.reversed()
    }
}

fun retainedStickerSelection(
    selectedStickerIds: List<String>,
    packs: List<StickerPack>,
): List<String> {
    val stickerIds = LinkedHashSet<String>()
    for (pack in packs) {
        for (sticker in pack.stickers) stickerIds.add(sticker.id)
    }
    return selectedStickerIds.filter(stickerIds::contains)
}

fun managedStickerById(
    items: Iterable<ManagedSticker>,
    stickerId: String,
): ManagedSticker? {
    for (item in items) {
        if (item.sticker.id == stickerId) return item
    }
    return null
}

private fun _upsertStickerPack(
    packs: List<StickerPack>,
    pack: StickerPack,
): List<StickerPack> {
    val next = mutableListOf<StickerPack>()
    var replaced = false
    for (current in packs) {
        if (current.id == pack.id) {
            next.add(pack)
            replaced = true
        } else {
            next.add(current)
        }
    }
    if (!replaced) {
        val insertionIndex =
            next.indexOfFirst { current -> current.sortOrder > pack.sortOrder }
        if (insertionIndex < 0) {
            next.add(pack)
        } else {
            next.add(insertionIndex, pack)
        }
    }
    return next
}

private fun _matchesStickerFilter(
    sticker: Sticker,
    keyword: String,
    mimeType: String,
): Boolean {
    if (keyword.isNotEmpty() && !sticker.name.lowercase().contains(keyword)) {
        return false
    }
    if (mimeType.isNotEmpty() && sticker.asset.mimeType != mimeType) {
        return false
    }
    return true
}

private fun _stickerActionPatch(
    action: StickerActionKind,
    actionBusy: Boolean,
    uploading: Boolean,
    deleting: Boolean,
    savingOrder: Boolean,
    downloading: Boolean,
    selectedStickerIds: List<String>,
    error: String?,
    notice: String?,
): StickerActionPatch {
    return StickerActionPatch(
        uploading = if (action == StickerActionKind.upload) actionBusy else uploading,
        deleting = if (action == StickerActionKind.delete) actionBusy else deleting,
        savingOrder = if (action == StickerActionKind.order) actionBusy else savingOrder,
        downloading = if (action == StickerActionKind.download) actionBusy else downloading,
        selectedStickerIds = selectedStickerIds,
        error = error,
        notice = notice,
    )
}

private fun _stickerPreviewActionState(
    state: StickerPreviewState,
    action: StickerPreviewActionKind,
    active: Boolean,
    error: Any? = _stickerPreviewErrorUnchanged,
): StickerPreviewState {
    return when (action) {
        StickerPreviewActionKind.rename -> state.copyWith(savingName = active, error = error)
        StickerPreviewActionKind.setAvatar -> state.copyWith(settingAvatar = active, error = error)
        StickerPreviewActionKind.download -> state.copyWith(downloading = active, error = error)
        StickerPreviewActionKind.delete -> state.copyWith(deleting = active, error = error)
        StickerPreviewActionKind.moveUp -> state.copyWith(movingUp = active, error = error)
        StickerPreviewActionKind.moveDown -> state.copyWith(movingDown = active, error = error)
        StickerPreviewActionKind.pin -> state.copyWith(pinning = active, error = error)
    }
}
