package com.gangchat.client.app

import com.gangchat.client.auth.AuthSession
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.AccessTokenProvider
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.protocol.GangApiClient
import com.gangchat.client.protocol.defaultUserLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** No Dart test existed for authenticated_app_context.dart; these cover its two methods. */
class AuthenticatedAppContextTest {
    @Test
    fun `createApiClient returns the injected api or a client on the base url`() {
        val api = _unimplemented<GangApi>()
        val injected = _context(api = api)
        assertSame(api, injected.createApiClient())

        val built = _context().createApiClient()
        assertTrue(built is GangApiClient)
        assertEquals("https://api.example.test", (built as GangApiClient).baseUrl)
        assertEquals("logan", _context().currentUser.username)
    }

    @Test
    fun `the api client reports server time and latency to the server clock`() {
        val clock = ServerClock()
        val client = _context(serverClock = clock).createApiClient() as GangApiClient

        client.onServerTime!!.invoke("Thu, 01 Jan 2026 00:00:00 GMT")
        client.onRequestLatency!!.invoke(kotlin.time.Duration.parse("120ms"))

        assertEquals(kotlin.time.Duration.parse("120ms"), clock.requestRoundTrip)
    }

    @Test
    fun `hasSameApiSource compares base url, injected api and server clock`() {
        val clock = ServerClock()
        val api = _unimplemented<GangApi>()
        val base = _context(serverClock = clock, api = api)

        assertTrue(base.hasSameApiSource(_context(serverClock = clock, api = api)))
        assertFalse(base.hasSameApiSource(_context(serverClock = ServerClock(), api = api)))
        assertFalse(base.hasSameApiSource(_context(serverClock = clock, api = null)))
        assertFalse(
            base.hasSameApiSource(
                _context(serverClock = clock, api = api, apiBaseUrl = "https://other.example.test"),
            ),
        )
    }

    private fun _context(
        serverClock: ServerClock = ServerClock(),
        api: GangApi? = null,
        apiBaseUrl: String = "https://api.example.test",
    ): AuthenticatedAppContext =
        AuthenticatedAppContext(
            session =
                AuthSession(
                    accessToken = "access",
                    refreshToken = "refresh",
                    accessTokenExpiresAt = DateTime.utc(2026, 6, 4),
                    user = _user(),
                ),
            apiBaseUrl = apiBaseUrl,
            accessTokenProvider = AccessTokenProvider { "access" },
            logout = {},
            exitSessionForAppExit = {},
            serverClock = serverClock,
            api = api,
        )

    private fun _user(): CurrentUser =
        CurrentUser(
            id = "user_1",
            uid = "1001",
            username = "logan",
            displayName = "Logan",
            bio = "",
            gender = "secret",
            email = null,
            emailPublic = false,
            phoneNumber = null,
            phoneNumberPublic = false,
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
            isSuperuser = false,
            language = defaultUserLanguage,
            createdAt = DateTime.utc(2026, 6, 4),
        )
}

private inline fun <reified T : Any> _unimplemented(): T {
    val name = T::class.java.simpleName
    return java.lang.reflect.Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.getOrNull(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "Unimplemented $name"
            else -> throw NotImplementedError("$name.${method.name} is not implemented by this fake")
        }
    } as T
}
