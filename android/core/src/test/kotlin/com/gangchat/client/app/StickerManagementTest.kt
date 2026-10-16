package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.Sticker
import com.gangchat.client.protocol.StickerPack
import com.gangchat.client.protocol.UploadedAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerManagementTest {
    @Test
    fun `managedStickerItems expands packs and honors draft order`() {
        val packs =
            listOf(
                _pack("pack_1", listOf("a", "b", "c")),
            )

        val items = managedStickerItems(packs, orderForPack = { _ -> listOf("c", "a") })

        assertEquals(listOf("c", "a", "b"), items.map { item -> item.sticker.id })
        assertEquals(setOf("pack_1"), items.map { item -> item.pack.id }.toSet())
    }

    @Test
    fun `filteredManagedStickerItems matches keyword and mime type`() {
        val items =
            managedStickerItems(
                listOf(
                    _pack(
                        "pack_1",
                        listOf("happy", "sad", "wave"),
                        mimeTypes = mapOf("happy" to "image/gif", "wave" to "image/webp"),
                    ),
                ),
            )

        assertEquals(
            listOf("happy"),
            filteredManagedStickerItems(
                items,
                keyword = " HAP ",
                mimeType = "",
            ).map { item -> item.sticker.id },
        )
        assertEquals(
            listOf("wave"),
            filteredManagedStickerItems(
                items,
                keyword = "",
                mimeType = "image/webp",
            ).map { item -> item.sticker.id },
        )
        assertTrue(
            filteredManagedStickerItems(items, keyword = "sad", mimeType = "image/gif").isEmpty(),
        )
    }

    @Test
    fun `selection helpers keep selection behavior reusable`() {
        val items =
            managedStickerItems(
                listOf(
                    _pack("pack_1", listOf("a", "b", "c")),
                ),
            )

        assertFalse(stickerFilterActive(keyword = " ", mimeType = ""))
        assertTrue(stickerFilterActive(keyword = "", mimeType = "image/png"))
        assertEquals(mapOf("b" to 1, "a" to 2), stickerSelectionNumbers(listOf("b", "a")))
        assertEquals(listOf("a", "b"), toggledStickerSelection(listOf("a"), "b"))
        assertEquals(listOf("b"), toggledStickerSelection(listOf("a", "b"), "a"))
        assertEquals(
            listOf("b", "a"),
            stickerSelectionForVisibleItems(emptyList(), items.take(2)),
        )
        assertTrue(
            stickerAllVisibleSelected(
                selectedStickerIds = listOf("b", "a"),
                visibleItems = items.take(2),
            ),
        )
        assertEquals(
            "全选",
            stickerVisibleSelectionButtonText(
                selectedStickerIds = listOf("b", "a"),
                visibleItems = items.take(2),
            ),
        )
        assertEquals(
            emptyList<String>(),
            stickerSelectionForVisibleItems(listOf("a", "b"), items.take(2)),
        )
        assertFalse(
            stickerAllVisibleSelected(
                selectedStickerIds = listOf("a"),
                visibleItems = items.take(2),
            ),
        )
        assertEquals(
            "全选",
            stickerVisibleSelectionButtonText(
                selectedStickerIds = listOf("a"),
                visibleItems = items.take(2),
            ),
        )
    }

    @Test
    fun `management state helpers describe counts and action gates`() {
        val items =
            managedStickerItems(
                listOf(
                    _pack("pack_1", listOf("a", "b")),
                ),
            )

        assertFalse(
            stickerManagementBusy(
                uploading = false,
                deleting = false,
                savingOrder = false,
                downloading = false,
            ),
        )
        assertTrue(
            stickerManagementBusy(
                uploading = false,
                deleting = true,
                savingOrder = false,
                downloading = false,
            ),
        )
        assertEquals(
            "2 个",
            stickerManagementCountText(
                filterActive = false,
                visibleCount = 1,
                totalCount = 2,
            ),
        )
        assertEquals(
            "1 / 2 个",
            stickerManagementCountText(
                filterActive = true,
                visibleCount = 1,
                totalCount = 2,
            ),
        )
        assertTrue(canStartStickerPrimaryAction(busy = false))
        assertFalse(canStartStickerPrimaryAction(busy = true))
        assertFalse(canStartStickerPrimaryAction(busy = false, allowed = false))
        assertTrue(
            canStartStickerSelectionAction(busy = false, selectedStickerIds = listOf("a")),
        )
        assertFalse(
            canStartStickerSelectionAction(
                busy = false,
                selectedStickerIds = listOf("a"),
                allowed = false,
            ),
        )
        assertFalse(
            canStartStickerSelectionAction(busy = false, selectedStickerIds = emptyList()),
        )
        assertFalse(
            canStartStickerSelectionAction(busy = true, selectedStickerIds = listOf("a")),
        )
        assertTrue(canSelectVisibleStickers(busy = false, visibleItems = items))
        assertFalse(
            canSelectVisibleStickers(busy = false, visibleItems = emptyList()),
        )
        assertTrue(canUseStickerManagementControl(busy = false))
        assertFalse(canUseStickerManagementControl(busy = true))
        assertFalse(
            canUseStickerManagementControl(busy = false, allowed = false),
        )
        val roomReadOnly = StickerManagementCapabilities.readOnlyDownloads()
        assertFalse(roomReadOnly.canUpload)
        assertTrue(roomReadOnly.canBatchManage)
        assertTrue(roomReadOnly.canFilter)
        assertTrue(roomReadOnly.canDownload)
        assertTrue(roomReadOnly.canSelectAll)
        assertFalse(roomReadOnly.canDelete)
        assertFalse(roomReadOnly.canPin)
        assertFalse(roomReadOnly.canRename)
        assertFalse(roomReadOnly.canMove)
    }

    @Test
    fun `sticker rename helpers normalize and gate empty names`() {
        assertEquals("wave", stickerRenameName("  wave  "))
        assertNull(stickerRenameName("   "))
        assertTrue(canStartStickerRename(busy = false, name = " wave "))
        assertFalse(canStartStickerRename(busy = false, name = "   "))
        assertFalse(canStartStickerRename(busy = true, name = "wave"))
        assertFalse(
            canStartStickerRename(busy = false, name = "wave", allowed = false),
        )
    }

    @Test
    fun `sticker preview state gates actions and tracks busy flags`() {
        val initial =
            StickerPreviewState.initial(
                canMoveUp = true,
                canMoveDown = false,
                canPin = true,
            )

        assertFalse(initial.busy)
        assertTrue(
            canStartStickerPreviewAction(
                state = initial,
                action = StickerPreviewActionKind.rename,
                name = " wave ",
            ),
        )
        assertFalse(
            canStartStickerPreviewAction(
                state = initial,
                action = StickerPreviewActionKind.rename,
                name = "   ",
            ),
        )

        val requested =
            stickerPreviewActionRequested(
                state = initial,
                action = StickerPreviewActionKind.rename,
                name = " wave ",
            )
        assertNotNull(requested)
        assertTrue(requested!!.savingName)
        assertNull(requested.error)
        assertNull(
            stickerPreviewActionRequested(
                state = initial,
                action = StickerPreviewActionKind.rename,
                name = "   ",
            ),
        )

        val downloading =
            stickerPreviewActionStarted(
                state = initial,
                action = StickerPreviewActionKind.download,
            )

        assertTrue(downloading.busy)
        assertTrue(downloading.downloading)
        assertNull(downloading.error)
        assertFalse(
            canStartStickerPreviewAction(
                state = downloading,
                action = StickerPreviewActionKind.delete,
            ),
        )
        assertNull(
            stickerPreviewActionRequested(
                state = downloading,
                action = StickerPreviewActionKind.delete,
            ),
        )

        val failed =
            stickerPreviewActionFailed(
                state = downloading,
                action = StickerPreviewActionKind.download,
                failure = IllegalStateException("download failed"),
            )

        assertFalse(failed.busy)
        assertFalse(failed.downloading)
        assertEquals("操作失败，请稍后重试", failed.error)

        val renaming =
            stickerPreviewActionStarted(
                state = failed,
                action = StickerPreviewActionKind.rename,
            )

        assertTrue(renaming.savingName)
        assertNull(renaming.error)

        val finished =
            stickerPreviewActionFinished(
                state = renaming,
                action = StickerPreviewActionKind.rename,
            )

        assertFalse(finished.savingName)
        assertNull(finished.error)
    }

    @Test
    fun `sticker preview move success updates placement controls`() {
        val initial =
            StickerPreviewState.initial(
                canMoveUp = true,
                canMoveDown = true,
                canPin = true,
            )
        val moving =
            stickerPreviewActionStarted(
                state = initial,
                action = StickerPreviewActionKind.pin,
            )

        val succeeded =
            stickerPreviewMoveSucceeded(
                state = moving,
                action = StickerPreviewActionKind.pin,
                canMoveUp = false,
                canMoveDown = true,
                canPin = false,
            )

        assertFalse(succeeded.pinning)
        assertFalse(succeeded.busy)
        assertFalse(succeeded.canMoveUp)
        assertTrue(succeeded.canMoveDown)
        assertFalse(succeeded.canPin)
        assertNull(succeeded.error)
    }

    @Test
    fun `personal sticker management copy stays reusable outside UI`() {
        assertEquals("我的表情包", defaultStickerPackName(StickerManagementScope.personal))
        assertEquals(
            "已添加 3 个表情",
            stickerUploadNotice(scope = StickerManagementScope.personal, count = 3),
        )
        assertEquals("删除表情", stickerDeleteDialogTitle(StickerManagementScope.personal))
        assertEquals(
            "将从服务端删除「wave」，删除后不会再出现在你的表情包里。",
            stickerSingleDeleteConfirmBody(
                scope = StickerManagementScope.personal,
                stickerName = "wave",
            ),
        )
        assertEquals(
            "将从服务端删除选中的 2 个表情，删除后不会再出现在你的表情包里。",
            stickerBulkDeleteConfirmBody(
                scope = StickerManagementScope.personal,
                count = 2,
            ),
        )
        assertEquals(
            "表情已删除",
            stickerDeletedNotice(scope = StickerManagementScope.personal),
        )
        assertEquals(
            "已删除 2 个表情",
            stickerDeletedNotice(scope = StickerManagementScope.personal, count = 2),
        )
        assertEquals(
            "表情已下载",
            stickerDownloadNotice(scope = StickerManagementScope.personal, count = 1),
        )
        assertEquals(
            "表情压缩包已下载",
            stickerDownloadNotice(scope = StickerManagementScope.personal, count = 2),
        )
        assertEquals(
            "表情排序没有变化",
            stickerNoOrderChangeNotice(StickerManagementScope.personal),
        )
        assertEquals(
            "表情已置顶",
            stickerPinnedNotice(scope = StickerManagementScope.personal),
        )
        assertEquals(
            "已置顶 2 个表情",
            stickerPinnedNotice(scope = StickerManagementScope.personal, count = 2),
        )
        assertEquals(
            "表情已上移一位",
            stickerMoveNotice(scope = StickerManagementScope.personal, delta = -1),
        )
        assertEquals(
            "表情已下移一位",
            stickerMoveNotice(scope = StickerManagementScope.personal, delta = 1),
        )
    }

    @Test
    fun `room sticker management copy stays reusable outside UI`() {
        assertEquals("房间表情包", defaultStickerPackName(StickerManagementScope.room))
        assertEquals(
            "已添加 3 个房间表情",
            stickerUploadNotice(scope = StickerManagementScope.room, count = 3),
        )
        assertEquals("删除房间表情", stickerDeleteDialogTitle(StickerManagementScope.room))
        assertEquals(
            "将从这个房间的表情包中删除「wave」。",
            stickerSingleDeleteConfirmBody(
                scope = StickerManagementScope.room,
                stickerName = "wave",
            ),
        )
        assertEquals(
            "将从这个房间的表情包中删除选中的 2 个表情。",
            stickerBulkDeleteConfirmBody(
                scope = StickerManagementScope.room,
                count = 2,
            ),
        )
        assertEquals("房间表情已删除", stickerDeletedNotice(scope = StickerManagementScope.room))
        assertEquals(
            "已删除 2 个房间表情",
            stickerDeletedNotice(scope = StickerManagementScope.room, count = 2),
        )
        assertEquals(
            "房间表情已下载",
            stickerDownloadNotice(scope = StickerManagementScope.room, count = 1),
        )
        assertEquals(
            "房间表情压缩包已下载",
            stickerDownloadNotice(scope = StickerManagementScope.room, count = 2),
        )
        assertEquals(
            "房间表情排序没有变化",
            stickerNoOrderChangeNotice(StickerManagementScope.room),
        )
        assertEquals("房间表情已置顶", stickerPinnedNotice(scope = StickerManagementScope.room))
        assertEquals(
            "已置顶 2 个房间表情",
            stickerPinnedNotice(scope = StickerManagementScope.room, count = 2),
        )
        assertEquals(
            "房间表情已上移一位",
            stickerMoveNotice(scope = StickerManagementScope.room, delta = -1),
        )
        assertEquals(
            "房间表情已下移一位",
            stickerMoveNotice(scope = StickerManagementScope.room, delta = 1),
        )
    }

    @Test
    fun `sticker pack load patches retain current selection safely`() {
        val currentPacks =
            listOf(
                _pack("pack_1", listOf("a", "b")),
            )
        val nextPacks =
            listOf(
                _pack("pack_2", listOf("b", "c")),
            )

        val started =
            stickerPacksLoadStarted(
                packs = currentPacks,
                selectedStickerIds = listOf("b"),
            )

        assertEquals(listOf("pack_1"), started.packs.map { pack -> pack.id })
        assertEquals(listOf("b"), started.selectedStickerIds)
        assertTrue(started.loading)
        assertNull(started.error)

        val succeeded =
            stickerPacksLoadSucceeded(
                packs = nextPacks,
                selectedStickerIds = listOf("missing", "b", "a"),
            )

        assertEquals(listOf("pack_2"), succeeded.packs.map { pack -> pack.id })
        assertEquals(listOf("b"), succeeded.selectedStickerIds)
        assertFalse(succeeded.loading)
        assertNull(succeeded.error)

        val failed =
            stickerPacksLoadFailed(
                packs = currentPacks,
                selectedStickerIds = listOf("a"),
                failure = IllegalStateException("network failed"),
            )

        assertEquals(listOf("pack_1"), failed.packs.map { pack -> pack.id })
        assertEquals(listOf("a"), failed.selectedStickerIds)
        assertFalse(failed.loading)
        assertEquals("网络连接失败，请检查网络后重试", failed.error)
    }

    @Test
    fun `sticker pack upsert patch preserves load state and valid selection`() {
        val currentPacks =
            listOf(
                _pack("pack_1", listOf("a", "b")),
                _pack("pack_2", listOf("c")),
            )

        val inserted =
            stickerPackUpserted(
                packs = currentPacks,
                selectedStickerIds = listOf("missing", "a", "d"),
                pack = _pack("pack_3", listOf("d")),
                loading = true,
                error = "previous",
            )

        assertEquals(
            listOf(
                "pack_1",
                "pack_2",
                "pack_3",
            ),
            inserted.packs.map { pack -> pack.id },
        )
        assertEquals(listOf("a", "d"), inserted.selectedStickerIds)
        assertTrue(inserted.loading)
        assertEquals("previous", inserted.error)

        val replaced =
            stickerPackUpserted(
                packs = currentPacks,
                selectedStickerIds = listOf("b", "c", "x"),
                pack = _pack("pack_1", listOf("x")),
                loading = false,
                error = null,
            )

        assertEquals(listOf("pack_1", "pack_2"), replaced.packs.map { pack -> pack.id })
        assertEquals(listOf("x"), replaced.packs.first().stickers.map { sticker -> sticker.id })
        assertEquals(listOf("c", "x"), replaced.selectedStickerIds)
        assertFalse(replaced.loading)
        assertNull(replaced.error)
    }

    @Test
    fun `sticker selection patches keep selection state reusable`() {
        val items =
            managedStickerItems(
                listOf(
                    _pack("pack_1", listOf("a", "b", "c")),
                ),
            )

        val manageMode =
            stickerManagementModeToggled(
                managing = false,
                filterKeyword = "gif",
                filterMimeType = "image/gif",
            )

        assertTrue(manageMode.managing)
        assertEquals("gif", manageMode.filterKeyword)
        assertEquals("image/gif", manageMode.filterMimeType)
        assertTrue(manageMode.selectedStickerIds.isEmpty())

        val ignoredSelection =
            stickerSelectionToggled(
                managing = false,
                filterKeyword = "",
                filterMimeType = "",
                selectedStickerIds = listOf("a"),
                stickerId = "b",
            )

        assertEquals(listOf("a"), ignoredSelection.selectedStickerIds)

        val toggledSelection =
            stickerSelectionToggled(
                managing = true,
                filterKeyword = "",
                filterMimeType = "",
                selectedStickerIds = listOf("a"),
                stickerId = "b",
            )

        assertEquals(listOf("a", "b"), toggledSelection.selectedStickerIds)

        val ignoredVisibleSelection =
            stickerVisibleSelectionToggled(
                managing = true,
                busy = true,
                filterKeyword = "",
                filterMimeType = "",
                selectedStickerIds = listOf("a"),
                visibleItems = items,
            )

        assertEquals(listOf("a"), ignoredVisibleSelection.selectedStickerIds)

        val visibleSelection =
            stickerVisibleSelectionToggled(
                managing = true,
                busy = false,
                filterKeyword = "",
                filterMimeType = "",
                selectedStickerIds = emptyList(),
                visibleItems = items.take(2),
            )

        assertEquals(listOf("b", "a"), visibleSelection.selectedStickerIds)

        val filter =
            stickerFilterApplied(
                managing = true,
                keyword = "webp",
                mimeType = "image/webp",
            )

        assertTrue(filter.managing)
        assertEquals("webp", filter.filterKeyword)
        assertEquals("image/webp", filter.filterMimeType)
        assertTrue(filter.selectedStickerIds.isEmpty())
    }

    @Test
    fun `sticker filter draft patches are shared by filter dialogs`() {
        val draft = StickerFilterDraft(keyword = "happy", mimeType = "image/gif")

        assertEquals("happy", draft.keyword)
        assertEquals("image/gif", draft.mimeType)
        assertEquals(
            "image/webp",
            stickerFilterMimeTypeChanged(mimeType = "image/webp").mimeType,
        )
        assertEquals("", stickerFilterDraftReset().mimeType)
    }

    @Test
    fun `sticker action patches update one busy flag and transient copy`() {
        val started =
            stickerActionStarted(
                action = StickerActionKind.upload,
                uploading = false,
                deleting = false,
                savingOrder = true,
                downloading = false,
                selectedStickerIds = listOf("a"),
            )

        assertTrue(started.uploading)
        assertFalse(started.deleting)
        assertTrue(started.savingOrder)
        assertFalse(started.downloading)
        assertEquals(listOf("a"), started.selectedStickerIds)
        assertNull(started.error)
        assertNull(started.notice)

        val succeeded =
            stickerActionSucceeded(
                action = StickerActionKind.delete,
                uploading = false,
                deleting = true,
                savingOrder = false,
                downloading = false,
                selectedStickerIds = listOf("a", "b"),
                error = null,
                notice = "deleted",
                clearSelection = true,
            )

        assertFalse(succeeded.uploading)
        assertFalse(succeeded.deleting)
        assertTrue(succeeded.selectedStickerIds.isEmpty())
        assertNull(succeeded.error)
        assertEquals("deleted", succeeded.notice)

        val failed =
            stickerActionFailed(
                action = StickerActionKind.order,
                uploading = false,
                deleting = false,
                savingOrder = true,
                downloading = false,
                selectedStickerIds = listOf("b"),
                failure = IllegalStateException("sort failed"),
            )

        assertFalse(failed.savingOrder)
        assertEquals(listOf("b"), failed.selectedStickerIds)
        assertEquals("操作失败，请稍后重试", failed.error)
        assertNull(failed.notice)
    }

    @Test
    fun `sticker action notice∕error patches preserve independent state`() {
        val cancelled =
            stickerActionCancelled(
                action = StickerActionKind.download,
                uploading = false,
                deleting = false,
                savingOrder = false,
                downloading = true,
                selectedStickerIds = listOf("a"),
            )

        assertFalse(cancelled.downloading)
        assertEquals(listOf("a"), cancelled.selectedStickerIds)
        assertNull(cancelled.error)
        assertNull(cancelled.notice)

        val notice =
            stickerActionNoticeShown(
                uploading = false,
                deleting = false,
                savingOrder = false,
                downloading = false,
                selectedStickerIds = listOf("a"),
                error = "previous",
                notice = "nothing changed",
            )

        assertEquals("previous", notice.error)
        assertEquals("nothing changed", notice.notice)
        assertEquals(listOf("a"), notice.selectedStickerIds)

        val error =
            stickerActionErrorShown(
                uploading = false,
                deleting = false,
                savingOrder = false,
                downloading = false,
                selectedStickerIds = listOf("a"),
                failure = "picker failed",
                notice = "previous notice",
            )

        assertEquals("操作失败，请稍后重试", error.error)
        assertEquals("previous notice", error.notice)
        assertEquals(listOf("a"), error.selectedStickerIds)
    }

    @Test
    fun `retainedStickerSelection and managedStickerById use current packs`() {
        val packs =
            listOf(
                _pack("pack_1", listOf("a")),
                _pack("pack_2", listOf("b")),
            )
        val items = managedStickerItems(packs)

        assertEquals(listOf("b", "a"), retainedStickerSelection(listOf("missing", "b", "a"), packs))
        assertEquals("pack_2", managedStickerById(items, "b")?.pack?.id)
        assertNull(managedStickerById(items, "missing"))
    }
}

private fun _pack(
    id: String,
    stickerIds: List<String>,
    mimeTypes: Map<String, String> = emptyMap(),
    sortOrder: Int = 10,
): StickerPack {
    return StickerPack(
        id = id,
        scope = "personal",
        roomId = null,
        name = id,
        sortOrder = sortOrder,
        updatedAt = DateTime.utc(2026, 6, 4),
        stickers =
            stickerIds.mapIndexed { index, stickerId ->
                Sticker(
                    id = stickerId,
                    name = stickerId,
                    sortOrder = (index + 1) * 10,
                    asset =
                        UploadedAsset(
                            id = "asset_$stickerId",
                            url = "/assets/$stickerId.png",
                            thumbnailUrl = null,
                            mimeType = mimeTypes[stickerId] ?: "image/png",
                        ),
                )
            },
    )
}
