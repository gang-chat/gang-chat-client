package com.gangchat.client.auth

import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.http.ClientException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Not a Dart test: checks the dart:io behaviour kept by the auth_transport port. */
class AuthTransportTest {
    @Test
    fun `finds the proxy from the environment like dart io`() {
        val env =
            mapOf(
                "https_proxy" to "http://proxy.local:3128/",
                "HTTP_PROXY" to "plain.local",
                "no_proxy" to "internal.test, [::1]",
            )
        assertEquals("PROXY proxy.local:3128", findProxyFromEnvironment("https", "api.example.test", env))
        assertEquals("PROXY plain.local:1080", findProxyFromEnvironment("http", "api.example.test", env))
        assertEquals("DIRECT", findProxyFromEnvironment("https", "api.internal.test", env))
        assertEquals("DIRECT", findProxyFromEnvironment("https", "::1", env))
        assertEquals("DIRECT", findProxyFromEnvironment("https", "api.example.test", emptyMap()))

        val withCredentials = _parseProxyConfiguration("PROXY user:secret@proxy.local:3128")!!
        assertEquals("proxy.local", withCredentials.host)
        assertEquals(3128, withCredentials.port)
        assertEquals("user", withCredentials.username)
        assertEquals("secret", withCredentials.password)
        assertNull(_parseProxyConfiguration("DIRECT"))
    }

    @Test
    fun `recognizes TLS handshake failures`() {
        val url = Uri.parse("https://api.example.test/auth/login")
        assertTrue(isTlsHandshakeFailure(SSLHandshakeException("connection terminated")))
        assertTrue(
            isTlsHandshakeFailure(
                ClientException("HandshakeException: terminated", url, SSLHandshakeException("terminated")),
            ),
        )
        assertTrue(isTlsHandshakeFailure(Exception("TLS handshake timed out")))
        assertFalse(isTlsHandshakeFailure(ClientException("Connection reset by peer", url)))
    }
}
