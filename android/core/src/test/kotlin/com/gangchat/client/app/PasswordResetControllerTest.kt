package com.gangchat.client.app

import com.gangchat.client.auth.AuthClient
import com.gangchat.client.testing.GangTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PasswordResetControllerTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `password reset grant follows the active session and email`() = gang.runTest {
        val client = _ClaimingAuthClient()
        val controller =
            PasswordResetController(
                apiBaseUrl = "https://api.example.test",
                accessTokenProvider = { "access-token" },
                authClientFactory = { _ -> client },
            )

        assertFalse(
            controller.isCurrentSessionAuthorizedFor("kai@example.test"),
        )

        controller.claimForCurrentSession(
            "reset-token",
            email = " Kai@Example.Test ",
        )

        assertEquals(listOf("access-token:reset-token"), client.claims)
        assertTrue(
            controller.isCurrentSessionAuthorizedFor("kai@example.test"),
        )
        assertFalse(
            controller.isCurrentSessionAuthorizedFor("other@example.test"),
        )

        controller.invalidateAuthorizationIfEmailChanged("other@example.test")
        assertFalse(
            controller.isCurrentSessionAuthorizedFor("kai@example.test"),
        )
        controller.invalidateAuthorizationIfEmailChanged("kai@example.test")
        assertFalse(
            controller.isCurrentSessionAuthorizedFor("kai@example.test"),
        )

        controller.clearCurrentSessionAuthorization()
        assertFalse(
            controller.isCurrentSessionAuthorizedFor("kai@example.test"),
        )
    }

    private class _ClaimingAuthClient : AuthClient(baseUrl = "https://unused.test") {
        val claims: MutableList<String> = mutableListOf()

        override suspend fun claimPasswordResetForSession(
            accessToken: String,
            resetToken: String,
        ) {
            claims.add("$accessToken:$resetToken")
        }

        override fun close() {}
    }
}
