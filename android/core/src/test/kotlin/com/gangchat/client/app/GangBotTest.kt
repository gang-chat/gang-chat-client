package com.gangchat.client.app

import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.foundation.utf8Decode
import com.gangchat.client.protocol.GangApiClient
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.MockClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Dart: test/unit/app/gang_bot_test.dart. Dart `group` names prefix each case.
class GangBotTest {
    @get:Rule
    val gang = GangTestRule()

    // group('gang-bot mentions')

    @Test
    fun `gang-bot mentions offers gang-bot first only when the room has it on`() {
        val withBot =
            messageMentionOptions(
                members = emptyList(),
                query = "",
                ownerUserId = null,
                includeGangBot = true,
            )
        assertEquals(kMentionGangBotLabel, withBot.first().label)
        assertEquals(MessageMentionKind.bot, withBot.first().kind)
        assertEquals("AI", withBot.first().roleLabel)

        val withoutBot =
            messageMentionOptions(
                members = emptyList(),
                query = "",
                ownerUserId = null,
            )
        assertFalse(withoutBot.map { option -> option.label }.contains(kMentionGangBotLabel))
    }

    @Test
    fun `gang-bot mentions matches the bot by name and common aliases`() {
        for (query in listOf("gang", "bot", "ai", "机器")) {
            val options =
                messageMentionOptions(
                    members = emptyList(),
                    query = query,
                    ownerUserId = null,
                    includeGangBot = true,
                )
            assertTrue(
                query,
                options.map { option -> option.kind }.contains(MessageMentionKind.bot),
            )
        }
        val unrelated =
            messageMentionOptions(
                members = emptyList(),
                query = "zzz",
                ownerUserId = null,
                includeGangBot = true,
            )
        assertTrue(unrelated.isEmpty())
    }

    @Test
    fun `gang-bot mentions sends a bot mention, never a user one`() {
        val descriptors =
            messageMentionDescriptors(
                text = "@gang-bot 你怎么看",
                members = emptyList(),
                confirmedLabels = listOf("gang-bot"),
            )
        assertEquals(
            listOf(
                mapOf("type" to "bot", "label" to "gang-bot"),
            ),
            descriptors,
        )
        assertEquals(MessageMentionKind.bot, messageMentionKindForLabel("Gang-Bot"))
        val someone =
            UserSummary(
                id = "u1",
                username = "gang-bot-fan",
                displayName = "gang-bot",
                avatarUrl = null,
                defaultAvatarKey = "blue-3",
            )
        assertFalse(
            messageMentionLabelTargetsUser(
                label = "gang-bot",
                user = someone,
                ownerUserId = null,
            ),
        )
    }

    // group('gang-bot models')

    @Test
    fun `gang-bot models marks the bot sender`() {
        val sender =
            UserSummary.fromJson(
                mapOf(
                    "id" to "gang-bot",
                    "username" to "gang-bot",
                    "display_name" to "gang-bot",
                    "is_bot" to true,
                ),
            )
        assertTrue(sender.isBot)
        assertTrue(sender.copyWith(displayName = "x").isBot)
        assertFalse(
            UserSummary.fromJson(mapOf("id" to "u1", "username" to "alice")).isBot,
        )
    }

    @Test
    fun `gang-bot models reads the room switch, defaulting to unavailable`() {
        val card =
            RoomCard.fromJson(
                mapOf(
                    "id" to "r1",
                    "name" to "Room",
                    "member_count" to 2,
                    "updated_at" to "2026-10-15T10:00:00Z",
                ),
            )
        assertFalse(card.gangBotAvailable)
        assertFalse(card.gangBotActive)

        val detail =
            RoomDetail.fromJson(
                _roomDetailJson(
                    extra = mapOf("gang_bot_available" to true, "gang_bot_enabled" to false),
                ),
            )
        assertTrue(detail.gangBotAvailable)
        assertFalse(detail.gangBotEnabled)
        assertFalse(detail.gangBotActive)
        assertTrue(detail.toCard().gangBotAvailable)
        assertFalse(detail.copyWithRole("admin").gangBotEnabled)
    }

    @Test
    fun `updateRoom sends the gang-bot switch`() = gang.runTest {
        var sentBody: Any? = null
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("PATCH", request.method)
                        assertEquals("/api/v1/rooms/room_1", request.url.path)
                        sentBody = jsonDecode(utf8Decode(request.bodyBytes))
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "room" to
                                        _roomDetailJson(
                                            extra =
                                                mapOf(
                                                    "gang_bot_available" to true,
                                                    "gang_bot_enabled" to false,
                                                ),
                                        ),
                                ),
                            ),
                            200,
                        )
                    },
            )
        val room = api.updateRoom(roomId = "room_1", gangBotEnabled = false)
        assertEquals(mapOf("gang_bot_enabled" to false), sentBody)
        assertFalse(room.gangBotEnabled)
    }

    private fun _roomDetailJson(extra: JsonMap = emptyMap()): JsonMap {
        return linkedMapOf<String, Any?>(
            "id" to "room_1",
            "name" to "Room",
            "avatar_url" to null,
            "default_avatar_key" to "blue-3",
            "member_count" to 2,
            "my_membership" to mapOf("joined_at" to "2026-10-15T10:00:00Z", "role" to "owner"),
            "live" to
                mapOf(
                    "room_id" to "room_1",
                    "participant_count" to 0,
                    "participants" to emptyList<Any?>(),
                    "updated_at" to "2026-10-15T10:00:00Z",
                ),
            "created_at" to "2026-10-15T09:00:00Z",
            "updated_at" to "2026-10-15T10:00:00Z",
        ).apply { putAll(extra) }
    }
}
