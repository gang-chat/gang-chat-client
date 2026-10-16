package com.gangchat.client.app

import com.gangchat.client.foundation.http.HttpRequest
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.protocol.GangApiClient
import com.gangchat.client.protocol.StickerPackStore
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.MockClient
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsControllerManagedUserTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `managed settings controller routes account operations to target APIs`() = gang.runTest {
        val requests = mutableListOf<HttpRequest>()
        val target =
            linkedMapOf<String, Any?>(
                "id" to "target_user",
                "uid" to "1000042",
                "username" to "target",
                "display_name" to "Target",
                "bio" to "",
                "gender" to "secret",
                "email" to "target@example.test",
                "email_verified" to true,
                "email_public" to false,
                "phone_number_public" to false,
                "avatar_url" to null,
                "default_avatar_key" to "blue-3",
                "is_superuser" to false,
                "language" to "zh-Hans",
            )
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requests.add(request)
                        if (request.url.path.endsWith("/sessions")) {
                            return@MockClient HttpResponse(jsonEncode(emptyList<Any?>()), 200)
                        }
                        if (request.url.path.endsWith("/password")) {
                            return@MockClient HttpResponse(jsonEncode(mapOf("ok" to true)), 200)
                        }
                        HttpResponse(jsonEncode(mapOf("user" to target)), 200)
                    },
            )
        val controller =
            SettingsController(
                api = api,
                apiBaseUrl = "http://example.test/api/v1",
                stickerPackStore = StickerPackStore(),
                managedUserId = "target_user",
            )

        controller.loadAccount()
        controller.loadSessions()
        controller.updateAccount(
            email = "changed-target@example.test",
            language = "en",
            emailVerificationToken = "verified-token",
        )
        controller.changePassword(
            currentPassword = "",
            newPassword = "new password",
        )
        controller.setManagedAccountSuspended(true)
        controller.setManagedAccountSuspended(false)
        controller.deleteMyAccount()

        assertEquals(
            listOf(
                "/api/v1/users/target_user/settings",
                "/api/v1/users/target_user/sessions",
                "/api/v1/users/target_user/settings",
                "/api/v1/users/target_user/password",
                "/api/v1/users/target_user/settings",
                "/api/v1/users/target_user/settings",
                "/api/v1/users/target_user/account",
            ),
            requests.map { request -> request.url.path },
        )
        assertEquals(
            mapOf(
                "email" to "changed-target@example.test",
                "email_verified" to true,
                "language" to "en",
            ),
            jsonDecode(requests[2].body),
        )
        assertEquals(mapOf("new_password" to "new password"), jsonDecode(requests[3].body))
        assertEquals(mapOf("status" to "suspended"), jsonDecode(requests[4].body))
        assertEquals(mapOf("status" to "active"), jsonDecode(requests[5].body))
        assertEquals(mapOf("confirm" to true), jsonDecode(requests[6].body))
        api.close()
    }
}
