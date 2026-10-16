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

class StickerOrderingTest {
    @Test
    fun `orderedStickers honors draft order and keeps missing stickers`() {
        val pack = _pack("pack_1", listOf("a", "b", "c"))

        val ordered = orderedStickers(pack, order = listOf("c", "missing", "a"))

        assertEquals(listOf("c", "a", "b"), ordered.map { sticker -> sticker.id })
    }

    @Test
    fun `batch upload sort orders place the whole batch before existing`() {
        val pack = _pack("pack_1", listOf("a", "b", "c"))

        assertEquals(listOf(-20, -10, 0), stickerSortOrdersBeforeExisting(pack, 3))
        assertEquals(
            listOf(10, 20, 30),
            stickerSortOrdersBeforeExisting(_pack("empty", emptyList()), 3),
        )
        assertTrue(stickerSortOrdersBeforeExisting(pack, 0).isEmpty())
    }

    @Test
    fun `move and pin return null when order does not change`() {
        val pack = _pack("pack_1", listOf("a", "b", "c"))

        assertEquals(listOf("a", "c", "b"), movedStickerOrder(pack, "b", 1))
        assertNull(movedStickerOrder(pack, "a", -1))
        assertEquals(listOf("c", "a", "b"), pinnedStickerOrder(pack, "c"))
        assertNull(pinnedStickerOrder(pack, "a"))
    }

    @Test
    fun `stickerPlacement exposes reusable move and pin state`() {
        val packs =
            listOf(
                _pack("pack_1", listOf("a", "b", "c")),
            )

        val first = stickerPlacement(packs, "a")
        assertEquals(0, first?.index)
        assertEquals(3, first?.total)
        assertEquals(false, first?.canMoveUp)
        assertEquals(true, first?.canMoveDown)
        assertEquals(false, first?.canPin)

        val middle = stickerPlacement(packs, "b")
        assertEquals("pack_1", middle?.pack?.id)
        assertEquals("b", middle?.sticker?.id)
        assertEquals(true, middle?.canMoveUp)
        assertEquals(true, middle?.canMoveDown)
        assertEquals(true, middle?.canPin)

        assertNull(stickerPlacement(packs, "missing"))
    }

    @Test
    fun `selectedStickerIdsByPack groups visible selection by owning pack`() {
        val packs =
            listOf(
                _pack("pack_1", listOf("a", "b")),
                _pack("pack_2", listOf("c")),
            )

        val grouped = selectedStickerIdsByPack(packs, listOf("c", "missing", "a"))

        assertEquals(
            mapOf(
                "pack_2" to listOf("c"),
                "pack_1" to listOf("a"),
            ),
            grouped,
        )
    }

    @Test
    fun `batch pin is available only when selected order would change`() {
        val packs =
            listOf(
                _pack("pack_1", listOf("a", "b", "c")),
                _pack("pack_2", listOf("d", "e")),
            )

        assertFalse(
            stickerSelectionWouldChangePinnedOrder(
                packs = packs,
                selectedStickerIds = listOf("a", "b"),
            ),
        )
        assertTrue(
            stickerSelectionWouldChangePinnedOrder(
                packs = packs,
                selectedStickerIds = listOf("b"),
            ),
        )
        assertTrue(
            stickerSelectionWouldChangePinnedOrder(
                packs = packs,
                selectedStickerIds = listOf("b", "a"),
            ),
        )
        assertTrue(
            stickerSelectionWouldChangePinnedOrder(
                packs = packs,
                selectedStickerIds = listOf("d"),
                orderByPack =
                    mapOf(
                        "pack_2" to listOf("e", "d"),
                    ),
            ),
        )
        assertFalse(
            stickerSelectionWouldChangePinnedOrder(
                packs = packs,
                selectedStickerIds = listOf("missing"),
            ),
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
                            mimeType = "image/png",
                        ),
                )
            },
    )
}
