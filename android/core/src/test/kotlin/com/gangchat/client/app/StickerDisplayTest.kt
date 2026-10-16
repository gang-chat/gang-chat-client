package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.Sticker
import com.gangchat.client.protocol.StickerPack
import com.gangchat.client.protocol.UploadedAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerDisplayTest {
    @Test
    fun `stickerPanelStickers flattens packs from the selected source`() {
        val personal =
            listOf(
                _pack("personal_pack", listOf("p1", "p2")),
            )
        val room =
            listOf(
                _pack("room_pack", listOf("r1")),
            )

        assertEquals(
            listOf("p1", "p2"),
            stickerPanelStickers(
                source = StickerPanelSource.personal,
                personalPacks = personal,
                roomPacks = room,
            ).map { sticker -> sticker.id },
        )
        assertEquals(
            listOf("r1"),
            stickerPanelStickers(
                source = StickerPanelSource.room,
                personalPacks = personal,
                roomPacks = room,
            ).map { sticker -> sticker.id },
        )
    }

    @Test
    fun `stickerPanelBodyState prioritizes result loading error and empty`() {
        val stickers = _pack("pack", listOf("s1")).stickers

        assertEquals(
            StickerPanelBodyState.results,
            stickerPanelBodyState(
                loading = true,
                error = "network",
                stickers = stickers,
            ),
        )
        assertEquals(
            StickerPanelBodyState.loading,
            stickerPanelBodyState(
                loading = true,
                error = "network",
                stickers = emptyList(),
            ),
        )
        assertEquals(
            StickerPanelBodyState.error,
            stickerPanelBodyState(
                loading = false,
                error = "network",
                stickers = emptyList(),
            ),
        )
        assertEquals(
            StickerPanelBodyState.empty,
            stickerPanelBodyState(loading = false, error = null, stickers = emptyList()),
        )
    }

    @Test
    fun `stickerPanelEmptyText describes the selected source`() {
        assertEquals("暂无个人表情", stickerPanelEmptyText(StickerPanelSource.personal))
        assertEquals("暂无房间表情", stickerPanelEmptyText(StickerPanelSource.room))
    }

    @Test
    fun `sticker panel load state covers reset source and load gates`() {
        val initial = StickerPanelLoadState()

        assertEquals(StickerPanelSource.personal, initial.source)
        assertTrue(initial.personalPacks.isEmpty())
        assertTrue(initial.roomPacks.isEmpty())
        assertFalse(initial.loading)
        assertFalse(initial.loaded)
        assertNull(initial.error)
        assertTrue(shouldLoadStickerPanel(state = initial, forceReload = false))

        val roomSource =
            stickerPanelSourceChanged(
                initial,
                StickerPanelSource.room,
            )
        val started = stickerPanelLoadStarted(roomSource)

        assertEquals(StickerPanelSource.room, started.source)
        assertTrue(started.loading)
        assertNull(started.error)
        assertFalse(shouldLoadStickerPanel(state = started, forceReload = true))

        val reset = stickerPanelReset(source = started.source)

        assertEquals(StickerPanelSource.room, reset.source)
        assertFalse(reset.loaded)
        assertFalse(reset.loading)
    }

    @Test
    fun `sticker panel load patches keep cached and loaded packs reusable`() {
        val initial = StickerPanelLoadState(error = "previous")
        val cached =
            listOf(
                _pack("cached_pack", listOf("cached")),
            )
        val room =
            listOf(
                _pack("room_pack", listOf("room")),
            )

        val started = stickerPanelLoadStarted(initial)
        val cachedApplied =
            stickerPanelCachedPersonalApplied(
                state = started,
                packs = cached,
            )

        assertEquals(listOf("cached_pack"), cachedApplied.personalPacks.map { pack -> pack.id })
        assertTrue(cachedApplied.loading)

        val succeeded =
            stickerPanelLoadSucceeded(
                state = cachedApplied,
                personalPacks = cached,
                roomPacks = room,
            )

        assertEquals(listOf("cached_pack"), succeeded.personalPacks.map { pack -> pack.id })
        assertEquals(listOf("room_pack"), succeeded.roomPacks.map { pack -> pack.id })
        assertFalse(succeeded.loading)
        assertTrue(succeeded.loaded)
        assertNull(succeeded.error)
        assertFalse(
            shouldLoadStickerPanel(state = succeeded, forceReload = false),
        )
        assertTrue(shouldLoadStickerPanel(state = succeeded, forceReload = true))

        val failed =
            stickerPanelLoadFailed(
                state = stickerPanelLoadStarted(succeeded),
                failure = IllegalStateException("network failed"),
            )

        assertEquals(listOf("cached_pack"), failed.personalPacks.map { pack -> pack.id })
        assertEquals(listOf("room_pack"), failed.roomPacks.map { pack -> pack.id })
        assertFalse(failed.loading)
        assertTrue(failed.loaded)
        assertEquals("网络连接失败，请检查网络后重试", failed.error)

        val finished = stickerPanelLoadFinished(failed)

        assertFalse(finished.loading)
        assertEquals(failed.error, finished.error)
    }

    @Test
    fun `stickerDimensionsText prefers asset dimensions`() {
        assertEquals(
            "80x64",
            stickerDimensionsText(
                _asset(width = 80, height = 64),
                resolved = StickerImageDimensions(width = 10, height = 10),
                resolving = false,
                failed = false,
            ),
        )
    }

    @Test
    fun `stickerDimensionsText uses resolved dimensions and states`() {
        assertEquals(
            "20x30",
            stickerDimensionsText(
                _asset(),
                resolved = StickerImageDimensions(width = 20, height = 30),
                resolving = false,
                failed = false,
            ),
        )
        assertEquals(
            "正在读取尺寸",
            stickerDimensionsText(_asset(), resolving = true, failed = false),
        )
        assertEquals(
            "尺寸读取失败",
            stickerDimensionsText(_asset(), resolving = false, failed = true),
        )
        assertEquals(
            "未知尺寸",
            stickerDimensionsText(_asset(), resolving = false, failed = false),
        )
    }
}

private fun _pack(id: String, stickerIds: List<String>): StickerPack {
    return StickerPack(
        id = id,
        scope = "personal",
        roomId = null,
        name = id,
        sortOrder = 10,
        updatedAt = DateTime.utc(2026, 6, 5),
        stickers =
            stickerIds.mapIndexed { index, stickerId ->
                Sticker(
                    id = stickerId,
                    name = stickerId,
                    sortOrder = (index + 1) * 10,
                    asset = _asset(),
                )
            },
    )
}

private fun _asset(width: Int? = null, height: Int? = null): UploadedAsset {
    return UploadedAsset(
        id = "asset_1",
        url = "/stickers/asset.png",
        thumbnailUrl = null,
        mimeType = "image/png",
        filename = "asset.png",
        sizeBytes = 128,
        width = width,
        height = height,
    )
}
