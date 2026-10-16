package com.gangchat.client.app

import com.gangchat.client.auth.AuthClient
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.MockClient
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EmailVerificationControllerTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `current-user verification opens active challenge when inspect omits id`() = gang.runTest {
        val requestedPaths = mutableListOf<String>()
        val authorizationHeaders = mutableListOf<String?>()
        val controller =
            EmailVerificationController(
                apiBaseUrl = "https://api.example.test/api/v1",
                accessTokenProvider = { "access-token" },
                authClientFactory = { baseUrl ->
                    AuthClient(
                        baseUrl = baseUrl,
                        httpClient =
                            MockClient { request ->
                                requestedPaths.add(request.url.path)
                                authorizationHeaders.add(request.headers["authorization"])
                                if (request.url.path ==
                                    "/api/v1/users/me/email-verification/inspect"
                                ) {
                                    return@MockClient _jsonResponse(mapOf("can_send" to false, "retry_after" to 42))
                                }
                                if (request.url.path ==
                                    "/api/v1/users/me/email-verification/start"
                                ) {
                                    return@MockClient _jsonResponse(
                                        mapOf(
                                            "challenge_id" to "existing-challenge",
                                            "retry_after" to 42,
                                        ),
                                    )
                                }
                                HttpResponse("unexpected request", 404)
                            },
                    )
                },
            )

        val challenge =
            controller.inspectOrStartForCurrentUser(
                "new@example.test",
            )

        assertEquals("existing-challenge", challenge.id)
        assertEquals(42, challenge.retryAfterSeconds)
        assertEquals(
            listOf(
                "/api/v1/users/me/email-verification/inspect",
                "/api/v1/users/me/email-verification/start",
            ),
            requestedPaths,
        )
        assertEquals(
            listOf(
                "Bearer access-token",
                "Bearer access-token",
            ),
            authorizationHeaders,
        )
    }
}

private fun _jsonResponse(body: Map<String, Any?>): HttpResponse {
    return HttpResponse(
        jsonEncode(body),
        200,
        headers = mapOf("content-type" to "application/json; charset=utf-8"),
    )
}
