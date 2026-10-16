package com.gangchat.client.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FnvHashTest {
    // Expected values come from the earlier native `int` implementations, so a
    // change here would orphan every cached file on disk.
    @Test
    fun `fnv1a64 matches the 64-bit FNV-1a reference values`() {
        assertEquals("cbf29ce484222325", fnv1a64("").toString(16))
        assertEquals("85944171f73967e8", fnv1a64("foobar").toString(16))
    }

    @Test
    fun `signed hashes render like the native int implementation`() {
        val expected =
            linkedMapOf(
                "" to "-340d631b7bdddcdb",
                "a" to "-509c23b379fe1374",
                "foobar" to "-7a6bbe8e08c69818",
                "https://ky-z.com/gang-chat/uploads/avatars/u1.png" to "1760ab0d0fbe3141",
                "https://os.ky-z.com:9000/gang-chat/files/表情.gif?v=2" to
                    "-435febba40bcd2c2",
            )
        for ((key, value) in expected) {
            assertEquals(
                key,
                value,
                fnv1a64Signed(key).toString(16).padStart(16, '0'),
            )
        }
    }

    @Test
    fun `music preview cache keys are unchanged`() {
        assertEquals(
            "5335af98ca736eff",
            musicTrackPreviewCacheKey("netease", "1901371647"),
        )
        assertEquals(
            "70491d7ea7d1c24f",
            musicTrackPreviewCacheKey("bilibili", "BV1xx411c7mD"),
        )
        assertEquals("2f63bd4c8601b7df", musicTrackPreviewCacheKey("", ""))
        assertEquals("604c8e206fcf16c9", musicTrackPreviewCacheKey("tencent", "歌曲 id"))
    }
}
