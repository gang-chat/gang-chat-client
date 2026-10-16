package com.gangchat.client.app

import com.gangchat.client.auth.AuthClient
import com.gangchat.client.auth.AuthException
import com.gangchat.client.auth.AuthSession
import com.gangchat.client.auth.TokenStore
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.testing.GangTestRule
import kotlin.time.Duration.Companion.days
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

class AuthSessionControllerTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `AuthSessionController notifies listeners without Flutter notifier`() = gang.runTest {
        val store = _FakeTokenStore()
        val controller =
            AuthSessionController(
                tokenStore = store,
                apiBaseUrl = "https://api.example.test",
            )
        var notifications = 0
        val listener: AuthSessionListener = { notifications += 1 }

        controller.addListener(listener)

        controller.acceptSession(_session)
        assertEquals(1, notifications)
        assertSame(_session, controller.session)
        assertEquals("refresh_1", store.refreshToken)
        assertEquals("https://api.example.test", store.apiBaseUrl)

        val loggedOut = controller.beginLogout()
        assertSame(_session, loggedOut)
        assertEquals(2, notifications)
        assertNull(controller.session)

        controller.removeListener(listener)
        controller.acceptSession(_session)
        assertEquals(2, notifications)
        controller.dispose()
    }

    @Test
    fun `transient access refresh failure keeps the active session`() = gang.runTest {
        val store = _FakeTokenStore()
        val client =
            _FakeAuthClient(
                onRefresh = { _ -> throw IllegalStateException("offline") },
            )
        val controller =
            AuthSessionController(
                tokenStore = store,
                apiBaseUrl = "https://api.example.test",
                authClientFactory = { _ -> client },
            )

        controller.acceptSession(_session)

        _expectThrows<IllegalStateException> {
            controller.accessToken(forceRefresh = true)
        }
        assertSame(_session, controller.session)
        assertEquals("refresh_1", store.refreshToken)
        assertEquals(0, store.clearCount)

        controller.dispose()
    }

    @Test
    fun `unauthorized access refresh clears the active session`() = gang.runTest {
        val store = _FakeTokenStore()
        val client =
            _FakeAuthClient(
                onRefresh = { _ ->
                    throw AuthException(
                        "session expired",
                        statusCode = 401,
                        code = "unauthorized",
                    )
                },
            )
        val controller =
            AuthSessionController(
                tokenStore = store,
                apiBaseUrl = "https://api.example.test",
                authClientFactory = { _ -> client },
            )

        controller.acceptSession(_session)

        _expectThrows<AuthException> {
            controller.accessToken(forceRefresh = true)
        }
        assertNull(controller.session)
        assertNull(store.refreshToken)
        assertEquals(1, store.clearCount)

        controller.dispose()
    }

    @Test
    fun `rotated refresh token write failure keeps the in-memory session`() = gang.runTest {
        val store = _FakeTokenStore()
        val client = _FakeAuthClient(onRefresh = { _ -> _rotatedSession })
        val controller =
            AuthSessionController(
                tokenStore = store,
                apiBaseUrl = "https://api.example.test",
                authClientFactory = { _ -> client },
            )

        controller.acceptSession(_session)
        store.failWrites = true

        val accessToken = controller.accessToken(forceRefresh = true)

        assertEquals("access_2", accessToken)
        assertSame(_rotatedSession, controller.session)
        assertEquals("refresh_1", store.refreshToken)
        assertEquals(0, store.clearCount)

        controller.dispose()
    }

    @Test
    fun `transient restore failure keeps the stored refresh token`() = gang.runTest {
        val store = _FakeTokenStore(refreshToken = "stored_refresh")
        val client =
            _FakeAuthClient(
                onRefresh = { _ -> throw IllegalStateException("offline") },
            )
        val controller =
            AuthSessionController(
                tokenStore = store,
                apiBaseUrl = "https://api.example.test",
                authClientFactory = { _ -> client },
            )

        val result = controller.restoreSession()

        assertEquals(AuthRestoreStatus.refreshFailed, result.status)
        assertNull(controller.session)
        assertEquals("stored_refresh", store.refreshToken)
        assertEquals(0, store.clearCount)

        controller.dispose()
    }

    @Test
    fun `unauthorized restore failure clears the stored refresh token`() = gang.runTest {
        val store = _FakeTokenStore(refreshToken = "stored_refresh")
        val client =
            _FakeAuthClient(
                onRefresh = { _ ->
                    throw AuthException(
                        "session expired",
                        statusCode = 401,
                        code = "unauthorized",
                    )
                },
            )
        val controller =
            AuthSessionController(
                tokenStore = store,
                apiBaseUrl = "https://api.example.test",
                authClientFactory = { _ -> client },
            )

        val result = controller.restoreSession()

        assertEquals(AuthRestoreStatus.refreshFailed, result.status)
        assertNull(controller.session)
        assertNull(store.refreshToken)
        assertEquals(1, store.clearCount)

        controller.dispose()
    }

    private class _FakeTokenStore(var refreshToken: String? = null) : TokenStore() {
        var apiBaseUrl: String? = null
        var clearCount = 0
        var failWrites = false

        override suspend fun readRefreshToken(): String? {
            return refreshToken
        }

        override suspend fun writeRefreshToken(refreshToken: String) {
            if (failWrites) throw IllegalStateException("keychain unavailable")
            this.refreshToken = refreshToken
        }

        override suspend fun writeApiBaseUrl(baseUrl: String) {
            apiBaseUrl = baseUrl
        }

        override suspend fun clearRefreshToken() {
            clearCount += 1
            refreshToken = null
        }
    }

    private class _FakeAuthClient(
        val onRefresh: (suspend (refreshToken: String) -> AuthSession)? = null,
    ) : AuthClient(baseUrl = "https://unused.test") {
        override suspend fun refresh(refreshToken: String): AuthSession {
            val refresh = onRefresh ?: return super.refresh(refreshToken)
            return refresh(refreshToken)
        }
    }

    private companion object {
        val _session =
            AuthSession(
                accessToken = "access_1",
                refreshToken = "refresh_1",
                accessTokenExpiresAt = DateTime.now().add(1.days),
                user =
                    CurrentUser(
                        id = "user_1",
                        uid = "1001",
                        username = "alice",
                        displayName = "Alice",
                        bio = "",
                        gender = "secret",
                        email = "alice@example.test",
                        emailPublic = false,
                        phoneNumber = null,
                        phoneNumberPublic = false,
                        avatarUrl = null,
                        defaultAvatarKey = "blue-3",
                        isSuperuser = false,
                        createdAt = DateTime.utc(2026, 6, 1),
                    ),
            )

        val _rotatedSession =
            AuthSession(
                accessToken = "access_2",
                refreshToken = "refresh_2",
                accessTokenExpiresAt = DateTime.now().add(1.days),
                user = _session.user,
            )
    }
}

/** Dart's `await expectLater(future, throwsA(isA<T>()))`. */
private suspend inline fun <reified T : Throwable> _expectThrows(block: () -> Unit) {
    try {
        block()
    } catch (error: Throwable) {
        if (error is T) return
        throw error
    }
    fail("Expected ${T::class.java.simpleName} to be thrown")
}
