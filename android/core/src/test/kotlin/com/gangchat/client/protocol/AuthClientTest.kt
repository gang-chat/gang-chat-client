package com.gangchat.client.protocol

import com.gangchat.client.auth.AuthClient
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.http.ClientException
import com.gangchat.client.foundation.http.HttpRequest
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.MockClient
import javax.net.ssl.SSLHandshakeException
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Dart: test/unit/protocol/auth_client_test.dart (tests lib/src/auth/auth_client.dart).
class AuthClientTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `auth requests report the supplied client system user agent`() = gang.runTest {
        lateinit var capturedRequest: HttpRequest
        val client =
            AuthClient(
                baseUrl = "https://api.example.test",
                userAgent = "GangChat Client (Android)",
                httpClient =
                    MockClient { request ->
                        capturedRequest = request
                        _authResponse()
                    },
            )

        client.login(login = "alice", password = "secret")

        assertEquals("GangChat Client (Android)", capturedRequest.headers["user-agent"])
        client.close()
    }

    @Test
    fun `auth checks normalized username availability`() = gang.runTest {
        lateinit var requestedUri: Uri
        val client =
            AuthClient(
                baseUrl = "https://api.example.test",
                httpClient =
                    MockClient { request ->
                        requestedUri = request.url
                        HttpResponse(
                            jsonEncode(mapOf("available" to false)),
                            200,
                            headers = mapOf("content-type" to "application/json; charset=utf-8"),
                        )
                    },
            )

        val available = client.isUsernameAvailable(" Taken_Name ")

        assertFalse(available)
        assertEquals("/auth/username-availability", requestedUri.path)
        assertEquals("Taken_Name", requestedUri.queryParameters["username"])
        client.close()
    }

    @Test
    fun `username availability uses the auth TLS fallback chain`() = gang.runTest {
        var directCalls = 0
        var fallbackCalls = 0
        val client =
            AuthClient(
                baseUrl = "https://api.example.test",
                httpClient =
                    MockClient { request ->
                        directCalls += 1
                        throw _handshakeException(request.url)
                    },
                environmentProxyClientFactory = {
                    MockClient { _ ->
                        fallbackCalls += 1
                        HttpResponse(
                            jsonEncode(mapOf("available" to true)),
                            200,
                            headers = mapOf("content-type" to "application/json; charset=utf-8"),
                        )
                    }
                },
                handshakeRetryDelays = listOf(Duration.ZERO, Duration.ZERO),
            )

        val available = client.isUsernameAvailable("available_name")

        assertTrue(available)
        assertEquals(2, directCalls)
        assertEquals(1, fallbackCalls)
        client.close()
    }

    @Test
    fun `auth checks normalized email availability`() = gang.runTest {
        lateinit var requestedUri: Uri
        val client =
            AuthClient(
                baseUrl = "https://api.example.test",
                httpClient =
                    MockClient { request ->
                        requestedUri = request.url
                        HttpResponse(
                            jsonEncode(mapOf("available" to false)),
                            200,
                            headers = mapOf("content-type" to "application/json; charset=utf-8"),
                        )
                    },
            )

        val available = client.isEmailAvailable(" Taken@Example.Test ")

        assertFalse(available)
        assertEquals("/auth/email-availability", requestedUri.path)
        assertEquals("Taken@Example.Test", requestedUri.queryParameters["email"])
        client.close()
    }

    @Test
    fun `auth retries a TLS handshake failure on the direct client`() = gang.runTest {
        var calls = 0
        var fallbackCalls = 0
        val client =
            AuthClient(
                baseUrl = "https://api.example.test",
                httpClient =
                    MockClient { request ->
                        calls += 1
                        if (calls == 1) throw _handshakeException(request.url)
                        _authResponse()
                    },
                environmentProxyClientFactory = {
                    MockClient { _ ->
                        fallbackCalls += 1
                        _authResponse()
                    }
                },
                handshakeRetryDelays = listOf(Duration.ZERO, Duration.ZERO),
            )

        val session = client.login(login = "alice", password = "secret")

        assertEquals("alice", session.user.username)
        assertEquals(2, calls)
        assertEquals(0, fallbackCalls)
        client.close()
    }

    @Test
    fun `auth falls back to the environment proxy after TLS retries`() = gang.runTest {
        var directCalls = 0
        var fallbackCalls = 0
        val client =
            AuthClient(
                baseUrl = "https://api.example.test",
                httpClient =
                    MockClient { request ->
                        directCalls += 1
                        throw _handshakeException(request.url)
                    },
                environmentProxyClientFactory = {
                    MockClient { _ ->
                        fallbackCalls += 1
                        _authResponse()
                    }
                },
                handshakeRetryDelays = listOf(Duration.ZERO, Duration.ZERO),
            )

        val session = client.login(login = "alice", password = "secret")

        assertEquals("alice", session.user.username)
        assertEquals(2, directCalls)
        assertEquals(1, fallbackCalls)
        client.close()
    }

    @Test
    fun `auth does not retry unrelated transport failures`() = gang.runTest {
        var directCalls = 0
        var fallbackCalls = 0
        val client =
            AuthClient(
                baseUrl = "https://api.example.test",
                httpClient =
                    MockClient { request ->
                        directCalls += 1
                        throw ClientException("connection reset", request.url)
                    },
                environmentProxyClientFactory = {
                    MockClient { _ ->
                        fallbackCalls += 1
                        _authResponse()
                    }
                },
                handshakeRetryDelays = listOf(Duration.ZERO, Duration.ZERO),
            )

        val error = runCatching { client.login(login = "alice", password = "secret") }.exceptionOrNull()
        assertTrue(error.toString(), error is ClientException)

        assertEquals(1, directCalls)
        assertEquals(0, fallbackCalls)
        client.close()
    }

    @Test
    fun `registration email verification sends the complete protocol`() = gang.runTest {
        val requests = mutableListOf<HttpRequest>()
        val client =
            AuthClient(
                baseUrl = "https://api.example.test/api/v1",
                httpClient =
                    MockClient { request ->
                        requests.add(request)
                        when (request.url.path) {
                            "/api/v1/auth/email-verification/inspect" ->
                                HttpResponse(
                                    jsonEncode(mapOf("can_send" to true, "retry_after" to 0)),
                                    200,
                                    headers = mapOf("content-type" to "application/json; charset=utf-8"),
                                )
                            "/api/v1/auth/email-verification/start",
                            "/api/v1/auth/email-verification/resend",
                            ->
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "challenge_id" to "email-challenge",
                                            "retry_after" to 60,
                                        ),
                                    ),
                                    200,
                                    headers = mapOf("content-type" to "application/json; charset=utf-8"),
                                )
                            "/api/v1/auth/email-verification/verify" ->
                                HttpResponse(
                                    jsonEncode(mapOf("verification_token" to "verified-email-token")),
                                    200,
                                    headers = mapOf("content-type" to "application/json; charset=utf-8"),
                                )
                            "/api/v1/auth/register" -> _authResponse()
                            else -> HttpResponse("not found", 404)
                        }
                    },
            )

        val inspection =
            client.inspectEmailVerification(
                " kai@example.test ",
            )
        val started = client.startEmailVerification("kai@example.test")
        val resent = client.resendEmailVerificationCode(started.id)
        val token =
            client.verifyEmailVerificationCode(
                challengeId = resent.id,
                code = "123456",
            )
        client.register(
            username = "kai",
            email = "kai@example.test",
            password = "secret-password",
            emailVerificationToken = token,
        )

        assertTrue(inspection.canSend)
        assertEquals(60, started.retryAfterSeconds)
        assertEquals("verified-email-token", token)
        assertEquals(mapOf("email" to "kai@example.test"), jsonDecode(requests.first().body))
        assertEquals(
            mapOf(
                "challenge_id" to "email-challenge",
                "code" to "123456",
            ),
            jsonDecode(requests[3].body),
        )
        assertEquals(
            mapOf(
                "username" to "kai",
                "email" to "kai@example.test",
                "password" to "secret-password",
                "email_verification_token" to "verified-email-token",
            ),
            jsonDecode(requests.last().body),
        )
        client.close()
    }

    @Test
    fun `authenticated email verification uses the current-user routes`() = gang.runTest {
        val requests = mutableListOf<HttpRequest>()
        val client =
            AuthClient(
                baseUrl = "https://api.example.test/api/v1",
                httpClient =
                    MockClient { request ->
                        requests.add(request)
                        if (request.url.path.endsWith("/inspect")) {
                            return@MockClient HttpResponse(
                                jsonEncode(mapOf("can_send" to true, "retry_after" to 0)),
                                200,
                            )
                        }
                        HttpResponse(
                            jsonEncode(mapOf("challenge_id" to "challenge", "retry_after" to 60)),
                            200,
                        )
                    },
            )

        client.inspectEmailVerification(
            "bound@example.test",
            accessToken = "access-token",
        )
        client.startEmailVerification(
            "bound@example.test",
            accessToken = "access-token",
        )

        assertEquals(
            listOf(
                "/api/v1/users/me/email-verification/inspect",
                "/api/v1/users/me/email-verification/start",
            ),
            requests.map { request -> request.url.path },
        )
        assertTrue(
            requests.all { request ->
                request.headers["authorization"] == "Bearer access-token"
            },
        )
        client.close()
    }

    @Test
    fun `password reset client sends the complete protocol payloads`() = gang.runTest {
        val requests = mutableListOf<HttpRequest>()
        val client =
            AuthClient(
                baseUrl = "https://api.example.test/api/v1",
                httpClient =
                    MockClient { request ->
                        requests.add(request)
                        when (request.url.path) {
                            "/api/v1/auth/password-reset/inspect" ->
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "can_send" to true,
                                            "masked_email" to "k***@example.test",
                                            "retry_after" to 0,
                                        ),
                                    ),
                                    200,
                                    headers = mapOf("content-type" to "application/json; charset=utf-8"),
                                )
                            "/api/v1/auth/password-reset/start",
                            "/api/v1/auth/password-reset/resend",
                            ->
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "challenge_id" to "challenge-1",
                                            "masked_email" to "k***@example.test",
                                            "retry_after" to 60,
                                        ),
                                    ),
                                    200,
                                    headers = mapOf("content-type" to "application/json; charset=utf-8"),
                                )
                            "/api/v1/auth/password-reset/verify" ->
                                HttpResponse(
                                    jsonEncode(mapOf("reset_token" to "reset-token")),
                                    200,
                                    headers = mapOf("content-type" to "application/json; charset=utf-8"),
                                )
                            "/api/v1/auth/password-reset/complete",
                            "/api/v1/auth/password-reset/claim",
                            ->
                                HttpResponse(
                                    jsonEncode(mapOf("ok" to true)),
                                    200,
                                    headers = mapOf("content-type" to "application/json; charset=utf-8"),
                                )
                            else -> HttpResponse("not found", 404)
                        }
                    },
            )

        val inspection = client.inspectPasswordReset(" kai ")
        val started = client.startPasswordReset(" kai ")
        val resent = client.resendPasswordResetCode(started.id)
        val token =
            client.verifyPasswordResetCode(
                challengeId = started.id,
                code = "123456",
            )
        client.completePasswordReset(
            resetToken = token,
            newPassword = "new-password",
        )
        client.claimPasswordResetForSession(
            accessToken = "access-token",
            resetToken = token,
        )

        assertTrue(inspection.canSend)
        assertEquals("k***@example.test", started.maskedEmail)
        assertEquals(60, resent.retryAfterSeconds)
        assertEquals("reset-token", token)
        assertEquals(mapOf("login" to "kai"), jsonDecode(requests.first().body))
        assertEquals(
            mapOf(
                "challenge_id" to "challenge-1",
                "code" to "123456",
            ),
            jsonDecode(requests[3].body),
        )
        assertEquals("Bearer access-token", requests.last().headers["authorization"])
        client.close()
    }
}

private fun _authResponse(): HttpResponse =
    HttpResponse(
        jsonEncode(
            mapOf(
                "access_token" to "access-token",
                "refresh_token" to "refresh-token",
                "expires_in" to 900,
                "user" to
                    mapOf(
                        "id" to "user-1",
                        "uid" to "10000001",
                        "username" to "alice",
                        "display_name" to "Alice",
                    ),
            ),
        ),
        200,
        headers = mapOf("content-type" to "application/json; charset=utf-8"),
    )

/**
 * Dart's `HandshakeException('connection terminated')`: the platform client
 * reports a failed TLS handshake as a ClientException carrying the
 * SSLException (see OkHttpClientAdapter's `toClientException`).
 */
private fun _handshakeException(url: Uri): ClientException =
    ClientException(
        "HandshakeException: connection terminated",
        url,
        SSLHandshakeException("connection terminated"),
    )
