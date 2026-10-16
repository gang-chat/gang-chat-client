package com.gangchat.client.app

import com.gangchat.client.protocol.GangApi
import com.gangchat.client.protocol.StickerPack
import com.gangchat.client.protocol.StickerPackStore

class StickerPacksController(
    val api: GangApi,
    val apiBaseUrl: String,
    val stickerPackStore: StickerPackStore,
) {
    suspend fun readCachedPersonalPacks(userId: String): List<StickerPack>? {
        return stickerPackStore.readPersonalPacks(
            userId = userId,
            apiBaseUrl = apiBaseUrl,
        )
    }

    suspend fun loadPersonalPacks(
        userId: String,
        forceReload: Boolean = false,
    ): List<StickerPack> {
        if (!forceReload) {
            val cached = readCachedPersonalPacks(userId = userId)
            if (cached != null) return cached
        }

        val packs = api.listStickerPacks(scope = "personal")
        stickerPackStore.writePersonalPacks(
            userId = userId,
            apiBaseUrl = apiBaseUrl,
            packs = packs,
        )
        return packs
    }

    suspend fun loadRoomPacks(roomId: String): List<StickerPack> {
        return api.listStickerPacks(scope = "room", roomId = roomId)
    }

    suspend fun saveSticker(
        roomId: String,
        stickerId: String,
        targetScope: String,
        userId: String,
        sourceMessageId: String? = null,
        name: String? = null,
    ): StickerPack {
        val pack =
            api.saveSticker(
                roomId = roomId,
                stickerId = stickerId,
                sourceMessageId = sourceMessageId,
                targetScope = targetScope,
                name = name,
            )
        if (targetScope == "personal") {
            val packs = api.listStickerPacks(scope = "personal")
            stickerPackStore.writePersonalPacks(
                userId = userId,
                apiBaseUrl = apiBaseUrl,
                packs = packs,
            )
        }
        return pack
    }
}
