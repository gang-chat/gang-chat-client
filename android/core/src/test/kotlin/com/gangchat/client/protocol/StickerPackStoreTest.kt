package com.gangchat.client.protocol

import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.testing.GangTestRule
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Not a Dart test: checks the cache file layout of the sticker_pack_store.dart port. */
class StickerPackStoreTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `writes, reads and clears the per-account cache file`() = gang.runTest {
        val store = StickerPackStore()
        val pack =
            StickerPack.fromJson(
                mapOf(
                    "id" to "stkp_1",
                    "scope" to "personal",
                    "name" to "My Stickers",
                    "sort_order" to 10,
                    "stickers" to
                        listOf(
                            mapOf(
                                "id" to "stk_1",
                                "name" to "ok",
                                "sort_order" to 20,
                                "asset" to mapOf("id" to "asset_1", "url" to "/assets/asset_1/ok.webp"),
                            ),
                        ),
                ),
            )

        assertNull(store.readPersonalPacks(userId = "", apiBaseUrl = "http://api.test"))
        store.writePersonalPacks(userId = "user_1", apiBaseUrl = "http://api.test", packs = listOf(pack))

        // base64url("http://api.test\nuser_1") without padding.
        val file =
            File(gang.tempDir, "cache/sticker-packs/gang-sticker-packs-v1-aHR0cDovL2FwaS50ZXN0CnVzZXJfMQ.json")
        assertTrue(file.exists())
        val saved = jsonDecode(file.readText()) as Map<*, *>
        assertEquals(1L, saved["version"])
        assertEquals("stkp_1", ((saved["packs"] as List<*>).single() as Map<*, *>)["id"])

        val read = store.readPersonalPacks(userId = "user_1", apiBaseUrl = "http://api.test")
        assertEquals("ok", read!!.single().stickers.single().name)

        store.clearPersonalPacks(userId = "user_1", apiBaseUrl = "http://api.test")
        assertFalse(file.exists())
        assertNull(store.readPersonalPacks(userId = "user_1", apiBaseUrl = "http://api.test"))
    }
}
