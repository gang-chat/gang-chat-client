package com.gangchat.client.foundation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {
    @Test
    fun decodesDartShapes() {
        val decoded = jsonDecode("""{"a":1,"b":1.5,"c":[true,null,"x"],"d":{"e":12345678901234}}""").asJsonMap()
        assertEquals(1L, decoded["a"])
        assertEquals(1.5, decoded["b"])
        assertEquals(listOf(true, null, "x"), decoded["c"])
        assertEquals(12345678901234L, decoded.optMap("d")!!["e"])
        assertEquals(listOf("a", "b", "c", "d"), decoded.keys.toList())
        assertEquals(1, decoded.optInt("a"))
    }

    @Test
    fun encodesLikeDart() {
        assertEquals("""{"a":1,"b":1.0,"c":[null,"中"]}""", jsonEncode(mapOf("a" to 1, "b" to 1.0, "c" to listOf(null, "中"))))
    }

    @Test(expected = FormatException::class)
    fun invalidJsonThrowsFormatException() {
        jsonDecode("{nope")
    }

    @Test
    fun formatExceptionToStringMatchesDart() {
        assertTrue(FormatException("bad").toString() == "FormatException: bad")
    }
}
