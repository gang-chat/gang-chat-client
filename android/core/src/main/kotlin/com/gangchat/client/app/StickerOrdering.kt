package com.gangchat.client.app

import com.gangchat.client.protocol.Sticker
import com.gangchat.client.protocol.StickerPack

class StickerPlacementData(
    val pack: StickerPack,
    val sticker: Sticker,
    val index: Int,
    val total: Int,
) {
    val canMoveUp: Boolean get() = index > 0
    val canMoveDown: Boolean get() = index < total - 1
    val canPin: Boolean get() = index > 0
}

fun orderedStickers(pack: StickerPack, order: List<String>? = null): List<Sticker> {
    val remaining = LinkedHashMap<String, Sticker>()
    for (sticker in pack.stickers) remaining[sticker.id] = sticker
    val targetOrder = order ?: pack.stickers.map { sticker -> sticker.id }
    val ordered = mutableListOf<Sticker>()
    for (stickerId in targetOrder) {
        val sticker = remaining.remove(stickerId)
        if (sticker != null) ordered.add(sticker)
    }
    ordered.addAll(remaining.values)
    return ordered
}

fun orderedStickerIds(pack: StickerPack, order: List<String>? = null): List<String> {
    return orderedStickers(
        pack,
        order = order,
    ).map { sticker -> sticker.id }
}

fun stickerSortOrdersBeforeExisting(pack: StickerPack, count: Int): List<Int> {
    if (count <= 0) return emptyList()
    if (pack.stickers.isEmpty()) {
        return List(count) { index -> (index + 1) * 10 }
    }
    var minSortOrder = pack.stickers.first().sortOrder
    for (sticker in pack.stickers.drop(1)) {
        if (sticker.sortOrder < minSortOrder) {
            minSortOrder = sticker.sortOrder
        }
    }
    val firstSortOrder = minSortOrder - count * 10
    return List(count) { index -> firstSortOrder + index * 10 }
}

fun stickerPackById(packs: List<StickerPack>, packId: String): StickerPack? {
    for (pack in packs) {
        if (pack.id == packId) return pack
    }
    return null
}

fun stickerPackForSticker(packs: List<StickerPack>, stickerId: String): StickerPack? {
    for (pack in packs) {
        if (pack.stickers.any { sticker -> sticker.id == stickerId }) return pack
    }
    return null
}

fun stickerPlacement(
    packs: List<StickerPack>,
    stickerId: String,
    orderForPack: ((pack: StickerPack) -> List<String>?)? = null,
): StickerPlacementData? {
    for (pack in packs) {
        val ordered = orderedStickers(pack, order = orderForPack?.invoke(pack))
        val index = ordered.indexOfFirst { sticker -> sticker.id == stickerId }
        if (index < 0) continue
        return StickerPlacementData(
            pack = pack,
            sticker = ordered[index],
            index = index,
            total = ordered.size,
        )
    }
    return null
}

fun movedStickerOrder(
    pack: StickerPack,
    stickerId: String,
    delta: Int,
    order: List<String>? = null,
): List<String>? {
    val ids = orderedStickerIds(pack, order = order).toMutableList()
    val from = ids.indexOf(stickerId)
    if (from < 0) return null
    val to = (from + delta).coerceIn(0, ids.size - 1)
    if (from == to) return null
    val moving = ids.removeAt(from)
    ids.add(to, moving)
    return ids
}

fun pinnedStickerOrder(
    pack: StickerPack,
    stickerId: String,
    order: List<String>? = null,
): List<String>? {
    val ids = orderedStickerIds(pack, order = order).toMutableList()
    val index = ids.indexOf(stickerId)
    if (index <= 0) return null
    val moving = ids.removeAt(index)
    ids.add(0, moving)
    return ids
}

fun stickerOrderWithStickerIdsPinnedToFront(
    pack: StickerPack,
    stickerIds: List<String>,
    order: List<String>? = null,
): List<String>? {
    if (stickerIds.isEmpty()) return null
    val pinnedSet = stickerIds.toSet()
    val currentOrder = orderedStickerIds(pack, order = order)
    val nextOrder =
        stickerIds.filter { stickerId -> currentOrder.contains(stickerId) } +
            currentOrder.filter { stickerId -> !pinnedSet.contains(stickerId) }
    if (_sameStringList(currentOrder, nextOrder)) return null
    return nextOrder
}

fun stickerSelectionWouldChangePinnedOrder(
    packs: List<StickerPack>,
    selectedStickerIds: List<String>,
    orderByPack: Map<String, List<String>> = emptyMap(),
): Boolean {
    val selectedByPack = selectedStickerIdsByPack(packs, selectedStickerIds)
    for (pack in packs) {
        val selectedInPack = selectedByPack[pack.id]
        if (selectedInPack == null || selectedInPack.isEmpty()) continue
        if (stickerOrderWithStickerIdsPinnedToFront(
                pack,
                selectedInPack,
                order = orderByPack[pack.id],
            ) != null
        ) {
            return true
        }
    }
    return false
}

fun selectedStickerIdsByPack(
    packs: List<StickerPack>,
    selectedIds: List<String>,
): Map<String, List<String>> {
    val selectedByPack = LinkedHashMap<String, MutableList<String>>()
    for (stickerId in selectedIds) {
        val pack = stickerPackForSticker(packs, stickerId) ?: continue
        selectedByPack.getOrPut(pack.id) { mutableListOf() }.add(stickerId)
    }
    return selectedByPack
}

private fun _sameStringList(a: List<String>, b: List<String>): Boolean {
    if (a.size != b.size) return false
    for (i in a.indices) {
        if (a[i] != b[i]) return false
    }
    return true
}
