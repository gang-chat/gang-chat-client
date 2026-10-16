package com.gangchat.client.foundation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UriTest {
    @Test
    fun parsesAndNormalizes() {
        val uri = Uri.parse("HTTPS://Example.COM:443/a b/c?x=1&y=two+words#frag")
        assertEquals("https", uri.scheme)
        assertEquals("example.com", uri.host)
        assertEquals(443, uri.port)
        assertFalse(uri.hasPort)
        assertEquals("/a%20b/c", uri.path)
        assertEquals(listOf("a b", "c"), uri.pathSegments)
        assertEquals(mapOf("x" to "1", "y" to "two words"), uri.queryParameters)
        assertEquals("frag", uri.fragment)
        assertEquals("https://example.com/a%20b/c?x=1&y=two+words#frag", uri.toString())
    }

    @Test
    fun replaceQueryParametersEncodesLikeDart() {
        val base = Uri.parse("https://ky-z.com/gang-chat/api/v1/rooms")
        val withQuery = base.replace(queryParameters = mapOf("q" to "你好 world", "limit" to "20"))
        assertEquals(
            "https://ky-z.com/gang-chat/api/v1/rooms?q=%E4%BD%A0%E5%A5%BD+world&limit=20",
            withQuery.toString(),
        )
        assertEquals("你好 world", withQuery.queryParameters["q"])
    }

    @Test
    fun encodeComponentMatchesDart() {
        assertEquals("a%20b%2Fc%3F%E4%B8%AD!~*'()", Uri.encodeComponent("a b/c?中!~*'()"))
        assertEquals("a+b%2Fc", Uri.encodeQueryComponent("a b/c"))
        assertEquals("a b/c?中", Uri.decodeComponent("a%20b%2Fc%3F%E4%B8%AD"))
        assertEquals("a b", Uri.decodeQueryComponent("a+b"))
    }

    @Test
    fun constructorBuildsMailto() {
        val uri = Uri(scheme = "mailto", path = "team@example.com", queryParameters = mapOf("subject" to "Hi there"))
        assertEquals("mailto:team@example.com?subject=Hi+there", uri.toString())
    }

    @Test
    fun resolvesReferences() {
        val base = Uri.parse("https://a.com/x/y/z?q=1")
        assertEquals("https://a.com/x/y/w", base.resolve("w").toString())
        assertEquals("https://a.com/x/w", base.resolve("../w").toString())
        assertEquals("https://a.com/root", base.resolve("/root").toString())
        assertEquals("https://b.com/p", base.resolve("//b.com/p").toString())
        assertEquals("https://a.com/x/y/z?r=2", base.resolve("?r=2").toString())
    }

    @Test
    fun tryParseRejectsBadPorts() {
        assertNull(Uri.tryParse("http://host:abc/"))
        assertTrue(Uri.parse("http://host:8080/").hasPort)
        assertEquals("http://host:8080", Uri.parse("http://host:8080/").origin)
    }
}
