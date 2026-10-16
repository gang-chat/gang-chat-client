package com.gangchat.client.protocol

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.delayed
import com.gangchat.client.foundation.http.BaseRequest
import com.gangchat.client.foundation.http.ClientException
import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.foundation.http.HttpRequest
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.http.MultipartRequest
import com.gangchat.client.foundation.http.StreamedResponse
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.foundation.utf8Decode
import com.gangchat.client.foundation.utf8Encode
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.MockClient
import java.io.ByteArrayOutputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ApiClientTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `getAppVersion fetches current server version metadata`() = gang.runTest {
        val serverTimes = mutableListOf<String?>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                onServerTime = { value -> serverTimes.add(value) },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/app/version", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "latest_version" to "1.2.3",
                                    "minimum_supported_version" to "1.0.0",
                                    "release_notes" to "ok",
                                    "download_url" to "https://example.test/download",
                                ),
                            ),
                            200,
                            headers = mapOf(gangServerTimeHeader to "2026-07-08T09:00:00Z"),
                        )
                    },
            )

        val info = api.getAppVersion()

        assertEquals("1.2.3", info.latestVersion)
        assertEquals("1.0.0", info.minimumSupportedVersion)
        assertEquals("ok", info.releaseNotes)
        assertEquals("https://example.test/download", info.downloadUrl)
        assertEquals(listOf("2026-07-08T09:00:00Z"), serverTimes)
        api.close()
    }

    @Test
    fun `reports request round-trip latency after a successful response`() = gang.runTest {
        val latencies = mutableListOf<Duration>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                onRequestLatency = { value -> latencies.add(value) },
                httpClient =
                    MockClient { _ ->
                        delayed(2.milliseconds)
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "latest_version" to "1.2.3",
                                    "minimum_supported_version" to "1.0.0",
                                    "release_notes" to "ok",
                                    "download_url" to "https://example.test/download",
                                ),
                            ),
                            200,
                        )
                    },
            )

        api.getAppVersion()

        assertEquals(1, latencies.size)
        assertTrue(latencies.single().inWholeMicroseconds > 0)
        api.close()
    }

    @Test
    fun `listMessages retries once after a transient closed connection`() = gang.runTest {
        var requests = 0
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requests += 1
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/rooms/room_1/messages", request.url.path)
                        assertEquals("50", request.url.queryParameters["limit"])
                        assertEquals("Bearer token", request.headers["authorization"])

                        if (requests == 1) {
                            throw ClientException(
                                "Connection closed before full header was received",
                                request.url,
                            )
                        }

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "messages" to
                                        listOf(
                                            mapOf(
                                                "id" to "msg_1",
                                                "room_id" to "room_1",
                                                "sender" to
                                                    mapOf(
                                                        "id" to "user_1",
                                                        "username" to "alice",
                                                        "display_name" to "Alice",
                                                    ),
                                                "client_message_id" to "cmsg_1",
                                                "body" to "hello",
                                                "created_at" to "2026-05-31T14:00:00Z",
                                            ),
                                        ),
                                    "has_more" to false,
                                    "next_before" to null,
                                ),
                            ),
                            200,
                        )
                    },
            )

        val page = api.listMessages(roomId = "room_1")

        assertEquals(2, requests)
        assertEquals("hello", page.messages.single().body)
        api.close()
    }

    @Test
    fun `sendMessage retries a dropped connection with one idempotency key`() = gang.runTest {
        var requests = 0
        val idempotencyKeys = mutableListOf<String>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requests += 1
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/rooms/room_1/messages", request.url.path)
                        _assertJson(
                            mapOf("client_message_id" to "cmsg_1", "body" to "hello"),
                            jsonDecode(utf8Decode(request.bodyBytes)),
                        )
                        idempotencyKeys.add(request.headers["idempotency-key"]!!)

                        if (requests == 1) {
                            throw ClientException("Connection reset by peer", request.url)
                        }

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "message" to
                                        mapOf(
                                            "id" to "msg_1",
                                            "room_id" to "room_1",
                                            "sender" to
                                                mapOf(
                                                    "id" to "user_1",
                                                    "username" to "alice",
                                                    "display_name" to "Alice",
                                                ),
                                            "client_message_id" to "cmsg_1",
                                            "body" to "hello",
                                            "created_at" to "2026-05-31T14:00:00Z",
                                        ),
                                ),
                            ),
                            201,
                        )
                    },
            )

        val message =
            api.sendMessage(
                roomId = "room_1",
                clientMessageId = "cmsg_1",
                body = "hello",
            )

        assertEquals(2, requests)
        assertEquals(2, idempotencyKeys.size)
        assertTrue(idempotencyKeys.first().isNotEmpty())
        assertEquals(idempotencyKeys.first(), idempotencyKeys.last())
        assertEquals("hello", message.body)
        api.close()
    }

    @Test
    fun `listMessageHistory sends all filters and parses the page`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/rooms/room_1/message-history", request.url.path)
                        assertEquals(
                            mapOf(
                                "limit" to "25",
                                "category" to "links",
                                "query" to "needle",
                                "sender_user_id" to "user_2",
                                "start_at" to "2026-07-01T00:00:00.000Z",
                                "end_at" to "2026-07-14T00:00:00.000Z",
                                "before" to "msg_older",
                            ),
                            request.url.queryParameters,
                        )
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "messages" to
                                        listOf(
                                            mapOf(
                                                "id" to "msg_1",
                                                "room_id" to "room_1",
                                                "sender" to
                                                    mapOf(
                                                        "id" to "user_2",
                                                        "username" to "alice",
                                                        "display_name" to "Alice",
                                                    ),
                                                "client_message_id" to "cmsg_1",
                                                "body" to "needle",
                                                "created_at" to "2026-07-10T12:00:00Z",
                                            ),
                                        ),
                                    "has_more" to true,
                                    "next_before" to "msg_1",
                                ),
                            ),
                            200,
                        )
                    },
            )

        val page =
            api.listMessageHistory(
                roomId = "room_1",
                query = "  needle  ",
                category = "links",
                senderUserId = "user_2",
                startAt = DateTime.utc(2026, 7, 1),
                endAt = DateTime.utc(2026, 7, 14),
                limit = 25,
                before = "msg_older",
            )

        assertEquals("msg_1", page.messages.single().id)
        assertTrue(page.hasMore)
        assertEquals("msg_1", page.nextBefore)
        api.close()
    }

    @Test
    fun `hideMessageHistory confirms current-account record deletion`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/rooms/room_1/message-history/hide", request.url.path)
                        _assertJson(
                            mapOf(
                                "message_ids" to listOf("msg_1", "msg_2"),
                                "confirm" to true,
                            ),
                            jsonDecode(utf8Decode(request.bodyBytes)),
                        )
                        HttpResponse(jsonEncode(mapOf("ok" to true, "deleted_count" to 2)), 200)
                    },
            )

        val deleted =
            api.hideMessageHistory(
                roomId = "room_1",
                messageIds = listOf("msg_1", "msg_2"),
            )

        assertEquals(2, deleted)
        api.close()
    }

    @Test
    fun `sendMessage keeps Chinese JSON UTF-8 without response charset`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/rooms/room_1/messages", request.url.path)
                        assertEquals("application/json", request.headers["accept"])
                        assertEquals(
                            "application/json; charset=utf-8",
                            request.headers["content-type"],
                        )
                        _assertJson(
                            mapOf("client_message_id" to "cmsg_1", "body" to "你好，世界"),
                            jsonDecode(utf8Decode(request.bodyBytes)),
                        )

                        HttpResponse.bytes(
                            utf8Encode(
                                jsonEncode(
                                    mapOf(
                                        "message" to
                                            mapOf(
                                                "id" to "msg_1",
                                                "room_id" to "room_1",
                                                "sender" to
                                                    mapOf(
                                                        "id" to "user_1",
                                                        "username" to "alice",
                                                        "display_name" to "Alice",
                                                    ),
                                                "client_message_id" to "cmsg_1",
                                                "body" to "服务端中文",
                                                "created_at" to "2026-05-31T14:00:00Z",
                                            ),
                                    ),
                                ),
                            ),
                            201,
                            headers = mapOf("content-type" to "application/json"),
                        )
                    },
            )

        val message =
            api.sendMessage(
                roomId = "room_1",
                clientMessageId = "cmsg_1",
                body = "你好，世界",
            )

        assertEquals("服务端中文", message.body)
        api.close()
    }

    @Test
    fun `sendMessage can send and parse a sticker attachment`() = gang.runTest {
        val stickerAsset =
            UploadedAsset(
                id = "asset_1",
                url = "/assets/asset_1/ok.webp",
                thumbnailUrl = null,
                mimeType = "image/webp",
            )
        val stickerAttachment =
            MessageAttachment(
                type = "sticker",
                stickerId = "sticker_1",
                name = "ok",
                asset = stickerAsset,
            )
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/rooms/room_1/messages", request.url.path)
                        _assertJson(
                            mapOf(
                                "client_message_id" to "cmsg_1",
                                "body" to "[ok]",
                                "type" to "sticker",
                                "attachments" to listOf(stickerAttachment.toJson()),
                            ),
                            jsonDecode(utf8Decode(request.bodyBytes)),
                        )

                        HttpResponse.bytes(
                            utf8Encode(
                                jsonEncode(
                                    mapOf(
                                        "message" to
                                            mapOf(
                                                "id" to "msg_1",
                                                "room_id" to "room_1",
                                                "sender" to
                                                    mapOf(
                                                        "id" to "user_1",
                                                        "username" to "alice",
                                                        "display_name" to "Alice",
                                                    ),
                                                "client_message_id" to "cmsg_1",
                                                "type" to "sticker",
                                                "body" to "[ok]",
                                                "attachments" to listOf(stickerAttachment.toJson()),
                                                "created_at" to "2026-05-31T14:00:00Z",
                                            ),
                                    ),
                                ),
                            ),
                            201,
                            headers = mapOf("content-type" to "application/json"),
                        )
                    },
            )

        val message =
            api.sendMessage(
                roomId = "room_1",
                clientMessageId = "cmsg_1",
                body = "[ok]",
                type = "sticker",
                attachments = listOf(stickerAttachment),
            )

        assertEquals("sticker", message.type)
        assertEquals("/assets/asset_1/ok.webp", message.stickerAttachment?.asset?.url)
        api.close()
    }

    @Test
    fun `playlist messages send an id and parse the server snapshot`() = gang.runTest {
        val requestedAttachment =
            MessageAttachment(
                type = "playlist",
                playlistId = "playlist_1",
            )
        val snapshot =
            mapOf(
                "id" to "playlist_1",
                "name" to "夜晚",
                "description" to "",
                "item_count" to 1,
                "created_at" to "2026-08-07T09:30:00Z",
                "creator" to
                    mapOf(
                        "id" to "user_1",
                        "username" to "alice",
                        "display_name" to "Alice",
                        "room_display_name" to "房间里的 Alice",
                        "avatar_url" to null,
                        "default_avatar_key" to "blue-1",
                    ),
                "items" to
                    listOf(
                        mapOf(
                            "id" to "item_1",
                            "playlist_id" to "playlist_1",
                            "track_id" to "track_1",
                            "source" to "netease",
                            "title" to "第一首歌",
                            "artists" to listOf("歌手"),
                            "duration_ms" to 180000,
                            "sort_order" to 10,
                            "created_at" to "2026-08-07T09:31:00Z",
                        ),
                    ),
            )
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/rooms/room_1/messages", request.url.path)
                        _assertJson(
                            mapOf(
                                "client_message_id" to "cmsg_playlist_1",
                                "body" to "",
                                "type" to "playlist",
                                "attachments" to listOf(requestedAttachment.toJson()),
                            ),
                            jsonDecode(utf8Decode(request.bodyBytes)),
                        )
                        HttpResponse.bytes(
                            utf8Encode(
                                jsonEncode(
                                    mapOf(
                                        "message" to
                                            mapOf(
                                                "id" to "msg_playlist_1",
                                                "room_id" to "room_1",
                                                "sender" to
                                                    mapOf(
                                                        "id" to "user_1",
                                                        "username" to "alice",
                                                        "display_name" to "Alice",
                                                    ),
                                                "client_message_id" to "cmsg_playlist_1",
                                                "type" to "playlist",
                                                "body" to "[歌单] 夜晚",
                                                "attachments" to
                                                    listOf(
                                                        mapOf("type" to "playlist", "playlist" to snapshot),
                                                    ),
                                                "created_at" to "2026-08-07T09:32:00Z",
                                            ),
                                    ),
                                ),
                            ),
                            201,
                            headers = mapOf("content-type" to "application/json"),
                        )
                    },
            )

        val message =
            api.sendMessage(
                roomId = "room_1",
                clientMessageId = "cmsg_playlist_1",
                body = "",
                type = "playlist",
                attachments = listOf(requestedAttachment),
            )

        assertEquals("playlist", message.type)
        assertEquals("夜晚", message.playlistAttachment?.playlist?.name)
        assertEquals(
            "房间里的 Alice",
            message.playlistAttachment?.playlist?.creator?.roomDisplayName,
        )
        assertEquals(
            "track_1",
            message.playlistAttachment?.playlist?.items?.single()?.trackId,
        )
        api.close()
    }

    @Test
    fun `music track share serializes source and parses snapshot`() = gang.runTest {
        val requestedAttachment =
            MessageAttachment(
                type = "music_track",
                playlistId = "playlist_1",
                playlistScope = "room",
                sourceRoomId = "source_room",
                itemId = "item_1",
            )
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/rooms/room_1/messages", request.url.path)
                        _assertJson(
                            mapOf(
                                "client_message_id" to "cmsg_track_1",
                                "body" to "",
                                "type" to "music_track",
                                "attachments" to listOf(requestedAttachment.toJson()),
                            ),
                            jsonDecode(utf8Decode(request.bodyBytes)),
                        )
                        HttpResponse.bytes(
                            utf8Encode(
                                jsonEncode(
                                    mapOf(
                                        "message" to
                                            mapOf(
                                                "id" to "msg_track_1",
                                                "room_id" to "room_1",
                                                "sender" to
                                                    mapOf(
                                                        "id" to "user_1",
                                                        "username" to "alice",
                                                        "display_name" to "Alice",
                                                    ),
                                                "client_message_id" to "cmsg_track_1",
                                                "type" to "music_track",
                                                "body" to "[歌曲] 晴天",
                                                "attachments" to
                                                    listOf(
                                                        mapOf(
                                                            "type" to "music_track",
                                                            "track" to
                                                                mapOf(
                                                                    "id" to "item_1",
                                                                    "playlist_id" to "playlist_1",
                                                                    "track_id" to "track_1",
                                                                    "source" to "netease",
                                                                    "title" to "晴天",
                                                                    "artists" to listOf("周杰伦"),
                                                                    "duration_ms" to 269000,
                                                                ),
                                                        ),
                                                    ),
                                                "created_at" to "2026-08-07T09:32:00Z",
                                            ),
                                    ),
                                ),
                            ),
                            201,
                            headers = mapOf("content-type" to "application/json"),
                        )
                    },
            )

        val message =
            api.sendMessage(
                roomId = "room_1",
                clientMessageId = "cmsg_track_1",
                body = "",
                type = "music_track",
                attachments = listOf(requestedAttachment),
            )

        assertEquals("晴天", message.musicTrackAttachment?.track?.title)
        assertEquals("track_1", message.musicTrackAttachment?.track?.trackId)
        api.close()
    }

    @Test
    fun `copied music component serializes its source message reference`() {
        val playlist =
            MessageAttachment(
                type = "playlist",
                sourceMessageId = "message_playlist_1",
            )
        val track =
            MessageAttachment(
                type = "music_track",
                sourceMessageId = "message_track_1",
            )

        assertEquals(
            mapOf(
                "type" to "playlist",
                "source_message_id" to "message_playlist_1",
            ),
            playlist.toJson(),
        )
        assertEquals(
            mapOf(
                "type" to "music_track",
                "source_message_id" to "message_track_1",
            ),
            track.toJson(),
        )
    }

    @Test
    fun `shared playlist clone uses the message-scoped endpoint`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals(
                            "/api/v1/rooms/room_1/messages/msg_1/playlist/clone-to-me",
                            request.url.path,
                        )
                        HttpResponse.bytes(
                            utf8Encode(
                                jsonEncode(
                                    mapOf(
                                        "playlist" to
                                            mapOf(
                                                "id" to "clone_1",
                                                "name" to "朋友的歌单 · 夜晚",
                                                "description" to "",
                                                "revision" to 1,
                                                "item_count" to 1,
                                                "created_at" to "2026-08-07T09:35:00Z",
                                                "updated_at" to "2026-08-07T09:35:00Z",
                                            ),
                                    ),
                                ),
                            ),
                            201,
                            headers = mapOf("content-type" to "application/json"),
                        )
                    },
            )

        val playlist =
            api.cloneSharedMusicPlaylistToPersonal(
                roomId = "room_1",
                messageId = "msg_1",
            )

        assertEquals("clone_1", playlist.id)
        assertEquals("朋友的歌单 · 夜晚", playlist.name)
        api.close()
    }

    @Test
    fun `Message parses rich sender fields for profile popups`() {
        val message =
            Message.fromJson(
                mapOf(
                    "id" to "msg_1",
                    "room_id" to "room_1",
                    "sender" to
                        mapOf(
                            "id" to "user_1",
                            "uid" to "1000001",
                            "username" to "alice",
                            "display_name" to "Alice",
                            "bio" to "Building quietly.",
                            "gender" to "female",
                            "email" to "alice@example.test",
                            "email_public" to true,
                            "phone_number" to "+8613800000000",
                            "phone_number_public" to true,
                            "avatar_url" to "/assets/asset_1/avatar.png",
                            "default_avatar_key" to "rose-2",
                            "is_superuser" to true,
                            "common_rooms" to
                                listOf(
                                    mapOf(
                                        "room_id" to "room_2",
                                        "rid" to "R10002",
                                        "room_name" to "Side Room",
                                        "visibility" to "private",
                                        "room_username" to "Alice Side",
                                        "membership_role" to "member",
                                    ),
                                ),
                        ),
                    "sender_room_username" to "A. Chen",
                    "sender_room_role" to "admin",
                    "sender_common_rooms" to
                        listOf(
                            mapOf(
                                "room_id" to "room_3",
                                "rid" to "R10003",
                                "room_name" to "Admin Room",
                                "visibility" to "public",
                                "room_username" to "Alice Admin",
                                "membership_role" to "admin",
                            ),
                        ),
                    "client_message_id" to "cmsg_1",
                    "body" to "hello",
                    "created_at" to "2026-05-31T14:00:00Z",
                ),
            )

        val sender = message.sender
        assertEquals("1000001", sender.uid)
        assertEquals("Building quietly.", sender.bio)
        assertEquals("female", sender.gender)
        assertEquals("alice@example.test", sender.email)
        assertEquals(true, sender.emailPublic)
        assertEquals("+8613800000000", sender.phoneNumber)
        assertEquals(true, sender.phoneNumberPublic)
        assertEquals("A. Chen", sender.roomDisplayName)
        assertEquals("admin", sender.roomRole)
        assertTrue(sender.isSuperuser)
        assertEquals(1, sender.commonRooms.size)
        assertEquals("room_3", sender.commonRooms.single().id)
        assertEquals("R10003", sender.commonRooms.single().rid)
        assertEquals("Admin Room", sender.commonRooms.single().name)
        assertEquals("public", sender.commonRooms.single().visibility)
        assertEquals("Alice Admin", sender.commonRooms.single().roomDisplayName)
        assertEquals("admin", sender.commonRooms.single().roomRole)
    }

    @Test
    fun `RoomMember parses room names, remarks, presence, and role fields`() {
        val page =
            RoomMemberPage.fromJson(
                mapOf(
                    "next_cursor" to "cursor_2",
                    "members" to
                        listOf(
                            mapOf(
                                "user" to
                                    mapOf(
                                        "id" to "user_1",
                                        "uid" to "1000001",
                                        "username" to "alice",
                                        "display_name" to "Alice",
                                        "avatar_url" to "/assets/asset_1/avatar.png",
                                        "default_avatar_key" to "rose-2",
                                        "is_superuser" to true,
                                    ),
                                "role" to "admin",
                                "room_display_name" to "Alice In Room",
                                "remark_name" to "Ops lead",
                                "text_muted_until" to "permanent",
                                "presence" to mapOf("status" to "connected"),
                                "joined_at" to "2026-05-31T14:00:00Z",
                            ),
                        ),
                ),
            )

        val member = page.members.single()
        assertEquals("cursor_2", page.nextCursor)
        assertEquals("1000001", member.user.uid)
        assertEquals("Alice In Room", member.user.roomDisplayName)
        assertEquals("admin", member.user.roomRole)
        assertTrue(member.user.isSuperuser)
        assertEquals("Alice In Room", member.roomDisplayName)
        assertEquals("Ops lead", member.remarkName)
        assertEquals("permanent", member.textMutedUntil)
        assertEquals(true, member.isOnline)
        assertEquals(
            "2026-05-31T14:00:00.000Z",
            member.joinedAt.toUtc().toIso8601String(),
        )
    }

    @Test
    fun `RoomMember parses online state from nested user payload`() {
        val member =
            RoomMember.fromJson(
                mapOf(
                    "user" to
                        mapOf(
                            "id" to "user_1",
                            "uid" to "1000001",
                            "username" to "alice",
                            "display_name" to "Alice",
                            "is_online" to true,
                        ),
                    "role" to "member",
                    "joined_at" to "2026-05-31T14:00:00Z",
                ),
            )

        assertEquals(true, member.isOnline)
        assertEquals(true, member.user.isOnline)
    }

    @Test
    fun `listRoomMembers requests room members with pagination`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/rooms/room_1/members", request.url.path)
                        assertEquals(
                            mapOf(
                                "limit" to "100",
                                "cursor" to "cursor_1",
                            ),
                            request.url.queryParameters,
                        )
                        assertEquals("Bearer token", request.headers["authorization"])

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "members" to
                                        listOf(
                                            mapOf(
                                                "user" to
                                                    mapOf(
                                                        "id" to "user_1",
                                                        "uid" to "1000001",
                                                        "username" to "alice",
                                                        "display_name" to "Alice",
                                                    ),
                                                "role" to "owner",
                                                "joined_at" to "2026-05-31T14:00:00Z",
                                            ),
                                        ),
                                    "next_cursor" to null,
                                ),
                            ),
                            200,
                        )
                    },
            )

        val page =
            api.listRoomMembers(
                "room_1",
                limit = 100,
                cursor = "cursor_1",
            )

        assertEquals("owner", page.members.single().role)
        assertEquals("alice", page.members.single().user.username)
        api.close()
    }

    @Test
    fun `getRoomMemberProfile parses signature and profile rooms`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/rooms/room_1/members/user_2/profile", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "profile" to
                                        mapOf(
                                            "user" to
                                                mapOf(
                                                    "id" to "user_2",
                                                    "uid" to "1000002",
                                                    "username" to "bob",
                                                    "display_name" to "Bob",
                                                    "avatar_url" to "/global.png",
                                                    "default_avatar_key" to "blue-2",
                                                    "bio" to "Ship quietly",
                                                    "gender" to "female",
                                                    "is_online" to true,
                                                    "common_rooms" to
                                                        listOf(
                                                            mapOf(
                                                                "id" to "room_2",
                                                                "rid" to "900002",
                                                                "name" to "Ops",
                                                                "remark_name" to "Night Ops",
                                                                "avatar_url" to "/room.png",
                                                                "default_avatar_key" to "room-2",
                                                                "visibility" to "private",
                                                                "room_role" to "admin",
                                                            ),
                                                        ),
                                                ),
                                            "room_display_name" to "Room Bob",
                                            "room_avatar_url" to "/room-bob.png",
                                            "room_default_avatar_key" to "green-2",
                                            "role" to "admin",
                                            "joined_at" to "2026-05-31T14:00:00Z",
                                        ),
                                ),
                            ),
                            200,
                        )
                    },
            )

        val profile =
            api.getRoomMemberProfile(
                roomId = "room_1",
                userId = "user_2",
            )

        assertEquals("Ship quietly", profile.user.bio)
        assertEquals("female", profile.user.gender)
        assertEquals("Room Bob", profile.user.roomDisplayName)
        assertEquals("admin", profile.user.roomRole)
        assertEquals(true, profile.user.isOnline)
        assertEquals("/global.png", profile.user.avatarUrl)
        assertEquals("Night Ops", profile.user.commonRooms.single().remarkName)
        assertEquals("/room.png", profile.user.commonRooms.single().avatarUrl)
        assertEquals("room-2", profile.user.commonRooms.single().defaultAvatarKey)
        api.close()
    }

    @Test
    fun `getUserProfile calls the global profile endpoint`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/users/user_2/profile", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "profile" to
                                        mapOf(
                                            "user" to
                                                mapOf(
                                                    "id" to "user_2",
                                                    "uid" to "1000002",
                                                    "username" to "bob",
                                                    "display_name" to "Bob",
                                                    "avatar_url" to "/global.png",
                                                    "default_avatar_key" to "blue-2",
                                                    "bio" to "Global profile",
                                                    "gender" to "male",
                                                    "is_online" to true,
                                                    "common_rooms" to
                                                        listOf(
                                                            mapOf(
                                                                "id" to "room_2",
                                                                "rid" to "900002",
                                                                "name" to "Ops",
                                                                "default_avatar_key" to "room-2",
                                                                "visibility" to "private",
                                                                "room_role" to "admin",
                                                            ),
                                                        ),
                                                ),
                                        ),
                                ),
                            ),
                            200,
                        )
                    },
            )

        val profile = api.getUserProfile("user_2")

        assertEquals("bob", profile.username)
        assertEquals("Global profile", profile.bio)
        assertEquals("male", profile.gender)
        assertEquals(true, profile.isOnline)
        assertEquals("Ops", profile.commonRooms.single().name)
        assertEquals("admin", profile.commonRooms.single().roomRole)
        api.close()
    }

    @Test
    fun `searchUsers calls the user search endpoint and parses summaries`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/users/search", request.url.path)
                        assertEquals(mapOf("q" to "1000001", "limit" to "20"), request.url.queryParameters)
                        assertEquals("Bearer token", request.headers["authorization"])

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "users" to
                                        listOf(
                                            mapOf(
                                                "id" to "user_1",
                                                "uid" to "1000001",
                                                "username" to "alice",
                                                "display_name" to "Alice",
                                                "avatar_url" to "/assets/asset_1/avatar.png",
                                                "default_avatar_key" to "rose-2",
                                                "is_superuser" to true,
                                            ),
                                        ),
                                    "next_cursor" to null,
                                ),
                            ),
                            200,
                        )
                    },
            )

        val users = api.searchUsers(query = "1000001")

        assertEquals("user_1", users.single().id)
        assertEquals("1000001", users.single().uid)
        assertEquals("/assets/asset_1/avatar.png", users.single().avatarUrl)
        assertTrue(users.single().isSuperuser)
        api.close()
    }

    @Test
    fun `searchUsersPage forwards cursor and parses pagination metadata`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/users/search", request.url.path)
                        assertEquals(
                            mapOf(
                                "q" to "alice",
                                "limit" to "8",
                                "cursor" to "8",
                                "include_suspended" to "true",
                            ),
                            request.url.queryParameters,
                        )
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "users" to
                                        listOf(
                                            mapOf(
                                                "id" to "user_1",
                                                "uid" to "1000001",
                                                "username" to "alice",
                                                "display_name" to "Alice",
                                                "default_avatar_key" to "blue-3",
                                                "is_suspended" to true,
                                            ),
                                        ),
                                    "next_cursor" to "16",
                                    "total_count" to 21,
                                ),
                            ),
                            200,
                        )
                    },
            )

        val page =
            api.searchUsersPage(
                query = "alice",
                limit = 8,
                cursor = "8",
                includeSuspended = true,
            )

        assertEquals("alice", page.users.single().username)
        assertTrue(page.users.single().isSuspended)
        assertEquals("16", page.nextCursor)
        assertEquals(21, page.totalCount)
        api.close()
    }

    @Test
    fun `superuser user settings APIs use target-only endpoints`() = gang.runTest {
        val requests = mutableListOf<HttpRequest>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requests.add(request)
                        val userJson =
                            mapOf(
                                "id" to "user_2",
                                "uid" to "1000002",
                                "username" to "bob",
                                "display_name" to "Bob",
                                "bio" to "",
                                "gender" to "secret",
                                "email" to "bob@example.com",
                                "email_verified" to true,
                                "email_public" to false,
                                "phone_number_public" to false,
                                "default_avatar_key" to "blue-3",
                                "language" to "zh-Hans",
                                "is_superuser" to false,
                                "status" to "active",
                            )
                        if (request.url.path.endsWith("/audio-settings")) {
                            return@MockClient HttpResponse(
                                jsonEncode(
                                    mapOf(
                                        "audio_settings" to
                                            mapOf(
                                                "default_audio_input_volume" to 90,
                                                "default_audio_output_volume" to 80,
                                                "live_mic_input_volume" to 70,
                                                "live_voice_output_volume" to 60,
                                                "live_screen_share_output_volume" to 50,
                                                "live_music_output_volume" to 40,
                                            ),
                                    ),
                                ),
                                200,
                            )
                        }
                        if (request.url.path.endsWith("/password")) {
                            return@MockClient HttpResponse(jsonEncode(mapOf("ok" to true)), 200)
                        }
                        HttpResponse(jsonEncode(mapOf("user" to userJson)), 200)
                    },
            )

        val user = api.getForcedUserSettings("user_2")
        val updated =
            api.updateForcedUserSettings(
                userId = "user_2",
                displayName = "Bob",
                emailVerified = true,
            )
        val audio = api.getForcedUserAudioSettings("user_2")
        api.updateForcedUserAudioSettings(userId = "user_2", settings = audio)
        api.forceResetUserPassword(
            userId = "user_2",
            newPassword = "new password",
        )

        assertEquals("user_2", user.id)
        assertTrue(updated.emailVerified)
        assertEquals(40, audio.liveMusicOutputVolume)
        assertEquals(
            listOf(
                "GET",
                "PATCH",
                "GET",
                "PATCH",
                "POST",
            ),
            requests.map { request -> request.method },
        )
        val passwordBody = jsonDecode(requests.last().body) as Map<*, *>
        assertEquals(mapOf("new_password" to "new password"), passwordBody)
        assertFalse(passwordBody.containsKey("current_password"))
        api.close()
    }

    @Test
    fun `search calls global search endpoint and parses categories`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/search", request.url.path)
                        assertEquals(mapOf("q" to "alpha", "limit" to "8"), request.url.queryParameters)
                        assertEquals("Bearer token", request.headers["authorization"])

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "my_rooms" to listOf(_roomCardSearchJson()),
                                    "public_rooms" to listOf(_publicRoomSearchJson()),
                                    "messages" to
                                        listOf(
                                            mapOf(
                                                "room" to _searchRoomContextJson(),
                                                "message" to _messageSearchJson(body = "alpha roadmap"),
                                            ),
                                        ),
                                    "files" to
                                        listOf(
                                            mapOf(
                                                "room" to _searchRoomContextJson(),
                                                "message" to
                                                    _messageSearchJson(
                                                        type = "file",
                                                        body = "alpha.pdf",
                                                        attachments =
                                                            listOf(
                                                                mapOf(
                                                                    "type" to "file",
                                                                    "name" to "alpha.pdf",
                                                                    "asset" to
                                                                        mapOf(
                                                                            "id" to "asset_1",
                                                                            "url" to "/assets/alpha.pdf",
                                                                            "thumbnail_url" to null,
                                                                            "mime_type" to "application/pdf",
                                                                            "filename" to "alpha.pdf",
                                                                            "size_bytes" to 2048,
                                                                        ),
                                                                ),
                                                            ),
                                                    ),
                                            ),
                                        ),
                                ),
                            ),
                            200,
                        )
                    },
            )

        val results = api.search(query = "alpha")

        assertEquals("Alpha Room", results.myRooms.single().name)
        assertFalse(results.publicRooms.single().joined)
        assertEquals("Alpha Room", results.messages.single().room.name)
        assertEquals("alpha roadmap", results.messages.single().message.body)
        assertEquals(
            "alpha.pdf",
            results.files.single().message.fileAttachments.single().asset?.filename,
        )
        api.close()
    }

    @Test
    fun `inviteMember posts the target user id and parses the invite`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/rooms/room_1/invites", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])
                        assertTrue(request.headers["idempotency-key"]!!.isNotEmpty())
                        _assertJson(
                            mapOf("user_id" to "user_2"),
                            jsonDecode(utf8Decode(request.bodyBytes)),
                        )

                        HttpResponse(jsonEncode(mapOf("invite" to _roomInviteJson())), 201)
                    },
            )

        val invite = api.inviteMember(roomId = "room_1", userId = "user_2")

        assertEquals("rinv_1", invite.id)
        assertEquals("pending", invite.status)
        assertEquals("Invite Room", invite.room.name)
        assertEquals("alice", invite.inviter.username)
        assertEquals("owner", invite.inviter.roomRole)
        api.close()
    }

    @Test
    fun `listRoomBlacklist requests and parses blocked users`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/rooms/room_1/blacklist", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "blacklist" to listOf(_roomBlacklistEntryJson()),
                                    "next_cursor" to null,
                                ),
                            ),
                            200,
                        )
                    },
            )

        val entries = api.listRoomBlacklist("room_1")

        assertEquals("blocked_user", entries.single().user.username)
        assertEquals("alice", entries.single().blockedBy?.username)
        api.close()
    }

    @Test
    fun `blockRoomUser posts target id and parses entry`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/rooms/room_1/blacklist", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])
                        assertTrue(request.headers["idempotency-key"]!!.isNotEmpty())
                        _assertJson(
                            mapOf("user_id" to "user_2"),
                            jsonDecode(utf8Decode(request.bodyBytes)),
                        )

                        HttpResponse(
                            jsonEncode(mapOf("entry" to _roomBlacklistEntryJson())),
                            201,
                        )
                    },
            )

        val entry = api.blockRoomUser(roomId = "room_1", userId = "user_2")

        assertEquals("user_2", entry.user.id)
        api.close()
    }

    @Test
    fun `unblockRoomUser deletes the blocked user entry`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("DELETE", request.method)
                        assertEquals("/api/v1/rooms/room_1/blacklist/user_2", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])

                        HttpResponse(jsonEncode(mapOf("ok" to true)), 200)
                    },
            )

        api.unblockRoomUser(roomId = "room_1", userId = "user_2")

        api.close()
    }

    @Test
    fun `joinRoom posts an optional application reason`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/rooms/room_1/join", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])
                        assertTrue(request.headers["idempotency-key"]!!.isNotEmpty())
                        _assertJson(
                            mapOf("reason" to "Let me in"),
                            jsonDecode(utf8Decode(request.bodyBytes)),
                        )

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "join_request" to
                                        mapOf(
                                            "id" to "jrq_1",
                                            "room_id" to "room_1",
                                            "status" to "pending",
                                            "reason" to "Let me in",
                                            "created_at" to "2026-01-01T00:00:00Z",
                                        ),
                                ),
                            ),
                            202,
                        )
                    },
            )

        val result = api.joinRoom("room_1", reason = "  Let me in  ")

        assertTrue(result.pending)
        api.close()
    }

    @Test
    fun `listRoomInvites requests pending invites for the current user`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/room-invites", request.url.path)
                        assertEquals(mapOf("status" to "pending"), request.url.queryParameters)
                        assertEquals("Bearer token", request.headers["authorization"])

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "invites" to listOf(_roomInviteJson()),
                                    "next_cursor" to null,
                                ),
                            ),
                            200,
                        )
                    },
            )

        val invites = api.listRoomInvites()

        assertEquals("rinv_1", invites.single().id)
        assertEquals("900001", invites.single().room.rid)
        api.close()
    }

    @Test
    fun `listRoomInvites can request all invites for notifications`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/room-invites", request.url.path)
                        assertEquals(mapOf("status" to "all"), request.url.queryParameters)
                        assertEquals("Bearer token", request.headers["authorization"])

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "invites" to
                                        listOf(
                                            _roomInviteJson(
                                                status = "pending",
                                                roomExists = false,
                                                inviterExists = false,
                                                inviterRoomRole = "left",
                                                invalidReason = "room_missing",
                                            ),
                                        ),
                                    "next_cursor" to null,
                                ),
                            ),
                            200,
                        )
                    },
            )

        val invites = api.listRoomInvites(status = "all")

        assertEquals("pending", invites.single().status)
        assertFalse(invites.single().roomExists)
        assertFalse(invites.single().inviterExists)
        assertTrue(invites.single().room.isDeleted)
        assertTrue(invites.single().inviter.isDeleted)
        assertEquals("room_missing", invites.single().invalidReason)
        assertEquals("left", invites.single().inviter.roomRole)
        api.close()
    }

    @Test
    fun `room invite notification parses room profile card fields`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/room-invites", request.url.path)

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "invites" to
                                        listOf(
                                            _roomInviteJson(roomJoined = true, includeViewerRoomInfo = true),
                                        ),
                                    "next_cursor" to null,
                                ),
                            ),
                            200,
                        )
                    },
            )

        val invites = api.listRoomInvites(status = "all")
        val room = invites.single().room

        assertEquals("Invite room bio", room.description)
        assertEquals("user_1", room.createdBy?.id)
        assertEquals("Alice in Room", room.personalProfile.displayName)
        assertEquals("member", room.myMembership?.role)
        api.close()
    }

    @Test
    fun `reviewRoomInvite accepts an invite and parses the joined room`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("PATCH", request.method)
                        assertEquals("/api/v1/room-invites/rinv_1", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])
                        _assertJson(
                            mapOf("decision" to "accept"),
                            jsonDecode(utf8Decode(request.bodyBytes)),
                        )

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "ok" to true,
                                    "invite" to _roomInviteJson(status = "accepted"),
                                    "room" to _roomDetailJson(),
                                ),
                            ),
                            200,
                        )
                    },
            )

        val result =
            api.reviewRoomInvite(
                inviteId = "rinv_1",
                accept = true,
            )

        assertTrue(result.joined)
        assertEquals("Invite Room", result.room?.name)
        api.close()
    }

    @Test
    fun `reviewRoomInvite parses a pending join request after accepting`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("PATCH", request.method)
                        assertEquals("/api/v1/room-invites/rinv_1", request.url.path)
                        _assertJson(
                            mapOf("decision" to "accept", "reason" to "Let me in"),
                            jsonDecode(utf8Decode(request.bodyBytes)),
                        )

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "ok" to true,
                                    "invite" to _roomInviteJson(status = "accepted"),
                                    "join_request" to
                                        mapOf(
                                            "id" to "jrq_1",
                                            "room_id" to "room_1",
                                            "status" to "pending",
                                            "created_at" to "2026-01-01T00:00:00Z",
                                        ),
                                ),
                            ),
                            202,
                        )
                    },
            )

        val result =
            api.reviewRoomInvite(
                inviteId = "rinv_1",
                accept = true,
                reason = "  Let me in  ",
            )

        assertFalse(result.joined)
        assertTrue(result.pending)
        api.close()
    }

    @Test
    fun `listRoomApplications requests all applications for notifications`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/room-applications", request.url.path)
                        assertEquals(mapOf("status" to "all"), request.url.queryParameters)
                        assertEquals("Bearer token", request.headers["authorization"])

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "applications" to
                                        listOf(
                                            _roomApplicationJson(status = "approved", reason = "Hi team"),
                                        ),
                                    "next_cursor" to null,
                                ),
                            ),
                            200,
                        )
                    },
            )

        val applications = api.listRoomApplications(status = "all")

        assertEquals("jrq_1", applications.single().id)
        assertEquals("approved", applications.single().status)
        assertEquals("Invite Room", applications.single().room.name)
        assertEquals("owner", applications.single().reviewer?.roomRole)
        assertNotNull(applications.single().reviewedAt)
        assertEquals("Hi team", applications.single().reason)
        assertTrue(applications.single().reviewerExists)
        api.close()
    }

    @Test
    fun `room application notification parses missing reviewer state`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/room-applications", request.url.path)

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "applications" to
                                        listOf(
                                            _roomApplicationJson(status = "approved", reviewerExists = false),
                                        ),
                                    "next_cursor" to null,
                                ),
                            ),
                            200,
                        )
                    },
            )

        val applications = api.listRoomApplications(status = "all")

        assertFalse(applications.single().reviewerExists)
        assertEquals(true, applications.single().reviewer?.isDeleted)
        assertEquals("Alice", applications.single().reviewer?.displayName)
        api.close()
    }

    @Test
    fun `listRoomNotifications requests and parses room event notifications`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/room-notifications", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "notifications" to listOf(_roomEventNotificationJson()),
                                    "next_cursor" to null,
                                ),
                            ),
                            200,
                        )
                    },
            )

        val notifications = api.listRoomNotifications()
        val notification = notifications.single()

        assertEquals("rnot_1", notification.id)
        assertEquals("role_promoted", notification.type)
        assertEquals("admin", notification.toRole)
        assertEquals("msg_mention_1", notification.messageId)
        assertEquals("@Alice hello", notification.messagePreview)
        assertEquals("Invite Room", notification.room.name)
        assertEquals("Invite room bio", notification.room.description)
        assertEquals("Alice in Invite Room", notification.actor?.roomDisplayName)
        assertEquals("owner", notification.actor?.roomRole)
        assertTrue(notification.isUnread)
        api.close()
    }

    @Test
    fun `markRoomNotificationsRead posts the read receipt endpoint`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/room-notifications/read", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])

                        HttpResponse(jsonEncode(mapOf("ok" to true)), 200)
                    },
            )

        api.markRoomNotificationsRead()
        api.close()
    }

    @Test
    fun `withdrawRoomApplication sends withdraw decision and parses result`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("PATCH", request.method)
                        assertEquals("/api/v1/room-applications/jrq_1", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])
                        _assertJson(
                            mapOf("decision" to "withdraw"),
                            jsonDecode(utf8Decode(request.bodyBytes)),
                        )

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "ok" to true,
                                    "application" to _roomApplicationJson(status = "withdrawn"),
                                ),
                            ),
                            200,
                        )
                    },
            )

        val application = api.withdrawRoomApplication(requestId = "jrq_1")

        assertEquals("withdrawn", application.status)
        assertNull(application.reviewer)
        api.close()
    }

    @Test
    fun `sendMessage can send and parse a file attachment`() = gang.runTest {
        val fileAsset =
            UploadedAsset(
                id = "asset_1",
                url = "/assets/asset_1/report.pdf",
                thumbnailUrl = null,
                mimeType = "application/pdf",
                filename = "report.pdf",
                sizeBytes = 4096,
            )
        val fileAttachment =
            MessageAttachment(
                type = "file",
                name = "report.pdf",
                asset = fileAsset,
            )
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/rooms/room_1/messages", request.url.path)
                        _assertJson(
                            mapOf(
                                "client_message_id" to "cmsg_1",
                                "body" to "report.pdf",
                                "type" to "file",
                                "attachments" to listOf(fileAttachment.toJson()),
                            ),
                            jsonDecode(utf8Decode(request.bodyBytes)),
                        )

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "message" to
                                        mapOf(
                                            "id" to "msg_1",
                                            "room_id" to "room_1",
                                            "sender" to
                                                mapOf(
                                                    "id" to "user_1",
                                                    "username" to "alice",
                                                    "display_name" to "Alice",
                                                ),
                                            "client_message_id" to "cmsg_1",
                                            "type" to "file",
                                            "body" to "report.pdf",
                                            "attachments" to listOf(fileAttachment.toJson()),
                                            "created_at" to "2026-05-31T14:00:00Z",
                                        ),
                                ),
                            ),
                            201,
                        )
                    },
            )

        val message =
            api.sendMessage(
                roomId = "room_1",
                clientMessageId = "cmsg_1",
                body = "report.pdf",
                type = "file",
                attachments = listOf(fileAttachment),
            )

        assertEquals("file", message.type)
        assertEquals("report.pdf", message.fileAttachments.single().asset?.filename)
        assertEquals(4096L, message.fileAttachments.single().asset?.sizeBytes)
        api.close()
    }

    @Test
    fun `joinLive retries a transient socket write abort with one idempotency key`() = gang.runTest {
        var requests = 0
        val idempotencyKeys = mutableListOf<String>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requests += 1
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/rooms/room_1/live/join", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])
                        assertEquals(true, (jsonDecode(request.body) as Map<*, *>)["mic_muted"])
                        assertEquals(true, (jsonDecode(request.body) as Map<*, *>)["headphones_muted"])
                        idempotencyKeys.add(request.headers["idempotency-key"]!!)

                        if (requests == 1) {
                            throw ClientException(
                                "SocketException: Write failed (OS Error: " +
                                    "你的主机中的软件中止了一个已建立的连接。, errno = 10053)",
                                request.url,
                            )
                        }

                        HttpResponse(jsonEncode(_liveJoinJson()), 200)
                    },
            )

        val result =
            api.joinLive(
                roomId = "room_1",
                clientLiveSessionId = "clive_1",
                source = "room_card_speaker",
                micMuted = true,
                headphonesMuted = true,
            )

        assertEquals(2, requests)
        assertEquals(2, idempotencyKeys.size)
        assertTrue(idempotencyKeys.first().isNotEmpty())
        assertEquals(idempotencyKeys.first(), idempotencyKeys.last())
        assertEquals("wss://voice.example.com", result.liveKit.serverUrl)
        assertEquals(1, result.live.participantCount)
        api.close()
    }

    @Test
    fun `listSessions parses recent account activity array`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/auth/sessions", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])
                        HttpResponse(
                            jsonEncode(
                                listOf(
                                    mapOf(
                                        "id" to "session_1",
                                        "user_agent" to "Flutter test",
                                        "ip_address" to "127.0.0.1",
                                        "location" to "Local",
                                        "created_at" to 1780300800,
                                        "last_used_at" to 1780300860,
                                        "expires_at" to 1782892800,
                                        "is_current" to true,
                                    ),
                                ),
                            ),
                            200,
                        )
                    },
            )

        val sessions = api.listSessions()

        assertEquals(1, sessions.size)
        assertEquals("Local", sessions.single().location)
        assertTrue(sessions.single().isCurrent)
        api.close()
    }

    @Test
    fun `listForcedUserSessions reads the managed account activity`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/users/user_2/sessions", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])
                        HttpResponse(
                            jsonEncode(
                                listOf(
                                    mapOf(
                                        "id" to "target_session",
                                        "user_agent" to "Windows target",
                                        "ip_address" to "203.0.113.8",
                                        "location" to "Shanghai",
                                        "created_at" to 1780300800,
                                        "last_used_at" to 1780300860,
                                        "expires_at" to 1782892800,
                                        "is_current" to false,
                                    ),
                                ),
                            ),
                            200,
                        )
                    },
            )

        val sessions = api.listForcedUserSessions("user_2")

        assertEquals("target_session", sessions.single().id)
        assertEquals("203.0.113.8", sessions.single().ipAddress)
        assertFalse(sessions.single().isCurrent)
        api.close()
    }

    @Test
    fun `updateAccount sends language preference and parses it`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("PATCH", request.method)
                        assertEquals("/api/v1/users/me/account", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])
                        _assertJson(
                            mapOf(
                                "language" to "en",
                            ),
                            jsonDecode(request.body),
                        )
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "user" to
                                        mapOf(
                                            "id" to "user_1",
                                            "uid" to "1000001",
                                            "username" to "alice",
                                            "display_name" to "Alice",
                                            "bio" to "",
                                            "gender" to "secret",
                                            "email" to "alice@example.test",
                                            "email_public" to false,
                                            "phone_number_public" to false,
                                            "avatar_url" to null,
                                            "default_avatar_key" to "blue-3",
                                            "language" to "en",
                                        ),
                                ),
                            ),
                            200,
                        )
                    },
            )

        val user = api.updateAccount(language = "en")

        assertEquals("en", user.language)
        api.close()
    }

    @Test
    fun `updateAccount sends the email verification token`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        _assertJson(
                            mapOf(
                                "email" to "new@example.test",
                                "email_verification_token" to "verification-token",
                            ),
                            jsonDecode(request.body),
                        )
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "user" to
                                        mapOf(
                                            "id" to "user_1",
                                            "uid" to "1000001",
                                            "username" to "alice",
                                            "display_name" to "Alice",
                                            "bio" to "",
                                            "gender" to "secret",
                                            "email" to "new@example.test",
                                            "email_verified" to true,
                                            "email_public" to false,
                                            "phone_number_public" to false,
                                            "avatar_url" to null,
                                            "default_avatar_key" to "blue-3",
                                        ),
                                ),
                            ),
                            200,
                        )
                    },
            )

        val user =
            api.updateAccount(
                email = "new@example.test",
                emailVerificationToken = "verification-token",
            )

        assertEquals("new@example.test", user.email)
        assertTrue(user.emailVerified)
        api.close()
    }

    @Test
    fun `uploadImageAsset posts multipart image data`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    _DartMultipartMockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/uploads/images", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])
                        assertTrue(request.headers["content-type"]!!.startsWith("multipart/form-data"))
                        assertTrue(request.bodyBytes.isNotEmpty())
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "asset" to
                                        mapOf(
                                            "id" to "asset_1",
                                            "url" to "/assets/asset_1/avatar.png",
                                            "thumbnail_url" to "/assets/asset_1/avatar.png",
                                            "mime_type" to "image/png",
                                        ),
                                ),
                            ),
                            201,
                        )
                    },
            )

        val asset =
            api.uploadImageAsset(
                bytes = byteArrayOf(1, 2, 3),
                filename = "avatar.png",
                purpose = "avatar",
            )

        assertEquals("asset_1", asset.id)
        assertEquals("image/png", asset.mimeType)
        api.close()
    }

    @Test
    fun `uploadFileAsset posts multipart file data`() = gang.runTest {
        val progress = mutableListOf<Long>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    _DartMultipartMockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/uploads/files", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])
                        assertTrue(request.headers["content-type"]!!.startsWith("multipart/form-data"))
                        val body = utf8Decode(request.bodyBytes, allowMalformed = true)
                        assertTrue(body, body.contains("name=\"purpose\""))
                        assertTrue(body, body.contains("message_file"))
                        assertTrue(body, body.contains("report.pdf"))
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "asset" to
                                        mapOf(
                                            "id" to "asset_1",
                                            "filename" to "report.pdf",
                                            "size_bytes" to 4096,
                                            "url" to "/assets/asset_1/report.pdf",
                                            "thumbnail_url" to null,
                                            "mime_type" to "application/pdf",
                                        ),
                                ),
                            ),
                            201,
                        )
                    },
            )

        val asset =
            api.uploadFileAsset(
                bytes = byteArrayOf(37, 80, 68, 70),
                filename = "report.pdf",
                onProgress = { sentBytes, totalBytes ->
                    assertEquals(4L, totalBytes)
                    progress.add(sentBytes)
                },
            )

        assertEquals("asset_1", asset.id)
        assertEquals("report.pdf", asset.filename)
        assertEquals(4096L, asset.sizeBytes)
        assertEquals("application/pdf", asset.mimeType)
        assertEquals(0L, progress.first())
        assertEquals(4L, progress.last())
        api.close()
    }

    @Test
    fun `uploadImageAsset retries a transient socket write abort`() = gang.runTest {
        var requests = 0
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    _DartMultipartMockClient { request ->
                        requests += 1
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/uploads/images", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])
                        assertTrue(request.headers["content-type"]!!.startsWith("multipart/form-data"))
                        assertTrue(request.bodyBytes.isNotEmpty())

                        if (requests == 1) {
                            throw ClientException(
                                "SocketException: Write failed (OS Error: " +
                                    "你的主机中的软件中止了一个已建立的连接。, errno = 10053)",
                                request.url,
                            )
                        }

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "asset" to
                                        mapOf(
                                            "id" to "asset_1",
                                            "url" to "/assets/asset_1/sticker.png",
                                            "thumbnail_url" to "/assets/asset_1/sticker.png",
                                            "mime_type" to "image/png",
                                            "width" to 128,
                                            "height" to 128,
                                        ),
                                ),
                            ),
                            201,
                        )
                    },
            )

        val asset =
            api.uploadImageAsset(
                bytes = byteArrayOf(1, 2, 3),
                filename = "sticker.png",
                purpose = "sticker",
            )

        assertEquals(2, requests)
        assertEquals("asset_1", asset.id)
        assertEquals(128, asset.width)
        api.close()
    }

    @Test
    fun `uploadImageAsset retries a transient 503 response`() = gang.runTest {
        var requests = 0
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    _DartMultipartMockClient { request ->
                        requests += 1
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/uploads/images", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])

                        if (requests == 1) {
                            return@_DartMultipartMockClient HttpResponse("service unavailable", 503)
                        }

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "asset" to
                                        mapOf(
                                            "id" to "asset_1",
                                            "url" to "/assets/asset_1/sticker.png",
                                            "thumbnail_url" to "/assets/asset_1/sticker.png",
                                            "mime_type" to "image/png",
                                        ),
                                ),
                            ),
                            201,
                        )
                    },
            )

        val asset =
            api.uploadImageAsset(
                bytes = byteArrayOf(1, 2, 3),
                filename = "sticker.png",
                purpose = "sticker",
            )

        assertEquals(2, requests)
        assertEquals("asset_1", asset.id)
        api.close()
    }

    @Test
    fun `listStickerPacks parses personal sticker assets`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/sticker-packs", request.url.path)
                        assertEquals("personal", request.url.queryParameters["scope"])
                        assertEquals("Bearer token", request.headers["authorization"])
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "packs" to
                                        listOf(
                                            mapOf(
                                                "id" to "stkp_1",
                                                "scope" to "personal",
                                                "room_id" to null,
                                                "name" to "My Stickers",
                                                "sort_order" to 10,
                                                "updated_at" to "2026-06-02T08:00:00Z",
                                                "stickers" to
                                                    listOf(
                                                        mapOf(
                                                            "id" to "stk_1",
                                                            "name" to "ok",
                                                            "sort_order" to 20,
                                                            "asset" to
                                                                mapOf(
                                                                    "id" to "asset_1",
                                                                    "url" to "/assets/asset_1/ok.webp",
                                                                    "thumbnail_url" to "/assets/asset_1/ok.webp",
                                                                    "mime_type" to "image/webp",
                                                                    "width" to 128,
                                                                    "height" to 128,
                                                                    "created_at" to "2026-06-02T07:59:00Z",
                                                                ),
                                                        ),
                                                    ),
                                            ),
                                        ),
                                ),
                            ),
                            200,
                        )
                    },
            )

        val packs = api.listStickerPacks()

        assertEquals("My Stickers", packs.single().name)
        assertEquals("image/webp", packs.single().stickers.single().asset.mimeType)
        assertEquals(128, packs.single().stickers.single().asset.width)
        api.close()
    }

    @Test
    fun `saveSticker posts target scope and parses returned pack`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/rooms/room_1/stickers/save", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])
                        _assertJson(
                            mapOf(
                                "sticker_id" to "stk_1",
                                "source_message_id" to "msg_1",
                                "target_scope" to "room",
                                "name" to "ok",
                            ),
                            jsonDecode(request.body),
                        )
                        HttpResponse(
                            jsonEncode(
                                _stickerPackJson(stickers = listOf(_stickerJson(id = "stk_saved"))),
                            ),
                            201,
                        )
                    },
            )

        val pack =
            api.saveSticker(
                roomId = "room_1",
                stickerId = "stk_1",
                sourceMessageId = "msg_1",
                targetScope = "room",
                name = "ok",
            )

        assertEquals("stk_saved", pack.stickers.single().id)
        api.close()
    }

    @Test
    fun `personal sticker APIs carry the managed owner user id`() = gang.runTest {
        var requestIndex = 0
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requestIndex += 1
                        when (requestIndex) {
                            1 -> {
                                assertEquals("GET", request.method)
                                assertEquals("/api/v1/sticker-packs", request.url.path)
                                assertEquals(
                                    mapOf(
                                        "scope" to "personal",
                                        "owner_user_id" to "user_2",
                                    ),
                                    request.url.queryParameters,
                                )
                                HttpResponse(jsonEncode(mapOf("packs" to emptyList<Any?>())), 200)
                            }
                            2 -> {
                                assertEquals("POST", request.method)
                                _assertJson(
                                    mapOf(
                                        "scope" to "personal",
                                        "name" to "Target Pack",
                                        "owner_user_id" to "user_2",
                                    ),
                                    jsonDecode(request.body),
                                )
                                HttpResponse(jsonEncode(_stickerPackJson()), 201)
                            }
                            else -> _fail("unexpected request ${request.method} ${request.url}")
                        }
                    },
            )

        api.listStickerPacks(scope = "personal", ownerUserId = "user_2")
        api.createStickerPack(name = "Target Pack", ownerUserId = "user_2")

        assertEquals(2, requestIndex)
        api.close()
    }

    @Test
    fun `sticker pack management uses server sticker routes`() = gang.runTest {
        var requestIndex = 0
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requestIndex += 1
                        assertEquals("Bearer token", request.headers["authorization"])

                        when (requestIndex) {
                            1 -> {
                                assertEquals("POST", request.method)
                                assertEquals("/api/v1/sticker-packs", request.url.path)
                                _assertJson(
                                    mapOf(
                                        "scope" to "personal",
                                        "name" to "My Stickers",
                                        "sort_order" to 10,
                                    ),
                                    jsonDecode(request.body),
                                )
                                HttpResponse(jsonEncode(_stickerPackJson()), 201)
                            }
                            2 -> {
                                assertEquals("POST", request.method)
                                assertEquals("/api/v1/sticker-packs/stkp_1/stickers", request.url.path)
                                assertTrue(request.headers["idempotency-key"]!!.isNotEmpty())
                                _assertJson(
                                    mapOf(
                                        "asset_id" to "asset_2",
                                        "name" to "hi",
                                        "sort_order" to 20,
                                    ),
                                    jsonDecode(request.body),
                                )
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "sticker" to
                                                mapOf(
                                                    "id" to "stk_2",
                                                    "asset_id" to "asset_2",
                                                    "name" to "hi",
                                                    "sort_order" to 20,
                                                ),
                                        ),
                                    ),
                                    201,
                                )
                            }
                            3 -> {
                                assertEquals("PATCH", request.method)
                                assertEquals("/api/v1/sticker-packs/stkp_1", request.url.path)
                                _assertJson(
                                    mapOf(
                                        "name" to "Saved",
                                        "sort_order" to 30,
                                    ),
                                    jsonDecode(request.body),
                                )
                                HttpResponse(
                                    jsonEncode(_stickerPackJson(name = "Saved")),
                                    200,
                                )
                            }
                            4 -> {
                                assertEquals("DELETE", request.method)
                                assertEquals(
                                    "/api/v1/sticker-packs/stkp_1/stickers/stk_2",
                                    request.url.path,
                                )
                                HttpResponse(jsonEncode(mapOf("ok" to true)), 200)
                            }
                            5 -> {
                                assertEquals("DELETE", request.method)
                                assertEquals("/api/v1/sticker-packs/stkp_1", request.url.path)
                                HttpResponse(jsonEncode(mapOf("ok" to true)), 200)
                            }
                            else ->
                                _fail(
                                    "unexpected request $requestIndex ${request.method} ${request.url}",
                                )
                        }
                    },
            )

        val pack =
            api.createStickerPack(
                name = "My Stickers",
                sortOrder = 10,
            )
        api.addSticker(
            packId = pack.id,
            assetId = "asset_2",
            name = "hi",
            sortOrder = 20,
        )
        val updated =
            api.updateStickerPack(
                packId = pack.id,
                name = "Saved",
                sortOrder = 30,
            )
        api.deleteSticker(packId = pack.id, stickerId = "stk_2")
        api.deleteStickerPack(pack.id)

        assertEquals("Saved", updated.name)
        assertEquals(5, requestIndex)
        api.close()
    }

    @Test
    fun `sticker item management uses rename reorder and download routes`() = gang.runTest {
        var requestIndex = 0
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requestIndex += 1
                        assertEquals("Bearer token", request.headers["authorization"])

                        when (requestIndex) {
                            1 -> {
                                assertEquals("PATCH", request.method)
                                assertEquals(
                                    "/api/v1/sticker-packs/stkp_1/stickers/stk_1",
                                    request.url.path,
                                )
                                _assertJson(
                                    mapOf(
                                        "name" to "ok",
                                    ),
                                    jsonDecode(request.body),
                                )
                                HttpResponse(
                                    jsonEncode(mapOf("sticker" to _stickerJson(name = "ok (2)"))),
                                    200,
                                )
                            }
                            2 -> {
                                assertEquals("POST", request.method)
                                assertEquals(
                                    "/api/v1/sticker-packs/stkp_1/stickers/reorder",
                                    request.url.path,
                                )
                                _assertJson(
                                    mapOf(
                                        "sticker_ids" to listOf("stk_2", "stk_1"),
                                    ),
                                    jsonDecode(request.body),
                                )
                                HttpResponse(
                                    jsonEncode(
                                        _stickerPackJson(
                                            stickers =
                                                listOf(
                                                    _stickerJson(id = "stk_2", sortOrder = 10),
                                                    _stickerJson(id = "stk_1", sortOrder = 20),
                                                ),
                                        ),
                                    ),
                                    200,
                                )
                            }
                            3 -> {
                                assertEquals("GET", request.method)
                                assertEquals("/api/v1/stickers/download", request.url.path)
                                assertEquals("stk_1,stk_2", request.url.queryParameters["ids"])
                                HttpResponse.bytes(
                                    byteArrayOf(80, 75, 3, 4),
                                    200,
                                    headers =
                                        mapOf(
                                            "content-type" to "application/zip",
                                            "content-disposition" to "attachment; filename=\"stickers.zip\"",
                                        ),
                                )
                            }
                            else ->
                                _fail(
                                    "unexpected request $requestIndex ${request.method} ${request.url}",
                                )
                        }
                    },
            )

        val renamed =
            api.updateSticker(
                packId = "stkp_1",
                stickerId = "stk_1",
                name = "ok",
            )
        val reordered =
            api.reorderStickers(
                packId = "stkp_1",
                stickerIds = listOf("stk_2", "stk_1"),
            )
        val downloaded =
            api.downloadStickers(
                stickerIds = listOf("stk_1", "stk_2"),
            )

        assertEquals("ok (2)", renamed.name)
        assertEquals("stk_2", reordered.stickers.first().id)
        assertEquals("stickers.zip", downloaded.filename)
        assertEquals("application/zip", downloaded.mimeType)
        assertArrayEquals(byteArrayOf(80, 75, 3, 4), downloaded.bytes)
        assertEquals(3, requestIndex)
        api.close()
    }

    @Test
    fun `downloadStickers prefers UTF-8 filename star header`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/stickers/download", request.url.path)
                        HttpResponse.bytes(
                            byteArrayOf(80, 75, 3, 4),
                            200,
                            headers =
                                mapOf(
                                    "content-type" to "application/zip",
                                    "content-disposition" to
                                        "attachment; filename=\"stickers.zip\"; " +
                                        "filename*=UTF-8''%E8%A1%A8%E6%83%85.zip",
                                ),
                        )
                    },
            )

        val downloaded = api.downloadStickers(stickerIds = listOf("stk_1"))

        assertEquals("表情.zip", downloaded.filename)
        api.close()
    }

    @Test
    fun `downloadMusicTrackPreview posts identity and returns M4A bytes`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/me/music-box/preview", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])
                        _assertJson(
                            mapOf(
                                "source" to "netease",
                                "track_id" to "track-1",
                            ),
                            jsonDecode(request.body),
                        )
                        HttpResponse.bytes(
                            byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112),
                            200,
                            headers =
                                mapOf(
                                    "content-type" to "audio/mp4",
                                    "content-disposition" to "inline; filename=\"music-preview.m4a\"",
                                ),
                        )
                    },
            )

        val downloaded =
            api.downloadMusicTrackPreview(
                source = "netease",
                trackId = "track-1",
            )

        assertArrayEquals(byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112), downloaded.bytes)
        assertEquals("audio/mp4", downloaded.mimeType)
        assertEquals("music-preview.m4a", downloaded.filename)
        api.close()
    }

    @Test
    fun `deleteSticker treats transient close as success when sticker is gone`() = gang.runTest {
        var requestIndex = 0
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requestIndex += 1
                        assertEquals("Bearer token", request.headers["authorization"])

                        when (requestIndex) {
                            1 -> {
                                assertEquals("DELETE", request.method)
                                assertEquals(
                                    "/api/v1/sticker-packs/stkp_1/stickers/stk_2",
                                    request.url.path,
                                )
                                throw ClientException(
                                    "Connection closed before full header was received",
                                    request.url,
                                )
                            }
                            2 -> {
                                assertEquals("GET", request.method)
                                assertEquals("/api/v1/sticker-packs", request.url.path)
                                assertEquals("personal", request.url.queryParameters["scope"])
                                HttpResponse(
                                    jsonEncode(_personalStickerPacksJson()),
                                    200,
                                )
                            }
                            else ->
                                _fail(
                                    "unexpected request $requestIndex ${request.method} ${request.url}",
                                )
                        }
                    },
            )

        api.deleteSticker(packId = "stkp_1", stickerId = "stk_2")

        assertEquals(2, requestIndex)
        api.close()
    }

    @Test
    fun `deleteSticker retries a transient close when sticker remains`() = gang.runTest {
        var requestIndex = 0
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requestIndex += 1
                        assertEquals("Bearer token", request.headers["authorization"])

                        when (requestIndex) {
                            1 -> {
                                assertEquals("DELETE", request.method)
                                assertEquals(
                                    "/api/v1/sticker-packs/stkp_1/stickers/stk_2",
                                    request.url.path,
                                )
                                throw ClientException(
                                    "Connection closed before full header was received",
                                    request.url,
                                )
                            }
                            2 -> {
                                assertEquals("GET", request.method)
                                assertEquals("/api/v1/sticker-packs", request.url.path)
                                assertEquals("personal", request.url.queryParameters["scope"])
                                HttpResponse(
                                    jsonEncode(_personalStickerPacksJson(linkedAssetId = "asset_2")),
                                    200,
                                )
                            }
                            3 -> {
                                assertEquals("DELETE", request.method)
                                assertEquals(
                                    "/api/v1/sticker-packs/stkp_1/stickers/stk_2",
                                    request.url.path,
                                )
                                HttpResponse(jsonEncode(mapOf("ok" to true)), 200)
                            }
                            else ->
                                _fail(
                                    "unexpected request $requestIndex ${request.method} ${request.url}",
                                )
                        }
                    },
            )

        api.deleteSticker(packId = "stkp_1", stickerId = "stk_2")

        assertEquals(3, requestIndex)
        api.close()
    }

    @Test
    fun `deleteSticker treats transient 503 as success when sticker is gone`() = gang.runTest {
        var requestIndex = 0
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requestIndex += 1
                        assertEquals("Bearer token", request.headers["authorization"])

                        when (requestIndex) {
                            1 -> {
                                assertEquals("DELETE", request.method)
                                assertEquals(
                                    "/api/v1/sticker-packs/stkp_1/stickers/stk_2",
                                    request.url.path,
                                )
                                HttpResponse("service unavailable", 503)
                            }
                            2 -> {
                                assertEquals("GET", request.method)
                                assertEquals("/api/v1/sticker-packs", request.url.path)
                                assertEquals("personal", request.url.queryParameters["scope"])
                                HttpResponse(
                                    jsonEncode(_personalStickerPacksJson()),
                                    200,
                                )
                            }
                            else ->
                                _fail(
                                    "unexpected request $requestIndex ${request.method} ${request.url}",
                                )
                        }
                    },
            )

        api.deleteSticker(packId = "stkp_1", stickerId = "stk_2")

        assertEquals(2, requestIndex)
        api.close()
    }

    @Test
    fun `addSticker treats transient write failure as success when asset exists`() = gang.runTest {
        var requestIndex = 0
        val idempotencyKeys = mutableListOf<String>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requestIndex += 1
                        assertEquals("Bearer token", request.headers["authorization"])

                        when (requestIndex) {
                            1 -> {
                                assertEquals("POST", request.method)
                                assertEquals("/api/v1/sticker-packs/stkp_1/stickers", request.url.path)
                                idempotencyKeys.add(request.headers["idempotency-key"]!!)
                                throw ClientException(
                                    "SocketException: Write failed (OS Error: connection aborted, errno = 10053)",
                                    request.url,
                                )
                            }
                            2 -> {
                                assertEquals("GET", request.method)
                                assertEquals("/api/v1/sticker-packs", request.url.path)
                                assertEquals("personal", request.url.queryParameters["scope"])
                                HttpResponse(
                                    jsonEncode(_personalStickerPacksJson(linkedAssetId = "asset_2")),
                                    200,
                                )
                            }
                            else ->
                                _fail(
                                    "unexpected request $requestIndex ${request.method} ${request.url}",
                                )
                        }
                    },
            )

        api.addSticker(packId = "stkp_1", assetId = "asset_2", name = "hi")

        assertEquals(2, requestIndex)
        assertTrue(idempotencyKeys.single().isNotEmpty())
        api.close()
    }

    @Test
    fun `addSticker retries once after transient write failure when not linked`() = gang.runTest {
        var requestIndex = 0
        val idempotencyKeys = mutableListOf<String>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requestIndex += 1
                        assertEquals("Bearer token", request.headers["authorization"])

                        when (requestIndex) {
                            1 -> {
                                assertEquals("POST", request.method)
                                assertEquals("/api/v1/sticker-packs/stkp_1/stickers", request.url.path)
                                idempotencyKeys.add(request.headers["idempotency-key"]!!)
                                throw ClientException(
                                    "SocketException: Write failed (OS Error: connection aborted, errno = 10053)",
                                    request.url,
                                )
                            }
                            2 -> {
                                assertEquals("GET", request.method)
                                assertEquals("/api/v1/sticker-packs", request.url.path)
                                assertEquals("personal", request.url.queryParameters["scope"])
                                HttpResponse(
                                    jsonEncode(_personalStickerPacksJson()),
                                    200,
                                )
                            }
                            3 -> {
                                assertEquals("POST", request.method)
                                assertEquals("/api/v1/sticker-packs/stkp_1/stickers", request.url.path)
                                idempotencyKeys.add(request.headers["idempotency-key"]!!)
                                _assertJson(
                                    mapOf(
                                        "asset_id" to "asset_2",
                                        "name" to "hi",
                                    ),
                                    jsonDecode(request.body),
                                )
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "sticker" to
                                                mapOf(
                                                    "id" to "stk_2",
                                                    "asset_id" to "asset_2",
                                                    "name" to "hi",
                                                    "sort_order" to 10,
                                                ),
                                        ),
                                    ),
                                    201,
                                )
                            }
                            else ->
                                _fail(
                                    "unexpected request $requestIndex ${request.method} ${request.url}",
                                )
                        }
                    },
            )

        api.addSticker(packId = "stkp_1", assetId = "asset_2", name = "hi")

        assertEquals(3, requestIndex)
        assertEquals(2, idempotencyKeys.size)
        assertTrue(idempotencyKeys.first().isNotEmpty())
        assertEquals(idempotencyKeys.first(), idempotencyKeys.last())
        api.close()
    }

    @Test
    fun `addSticker recovers after repeated transient write failures once linked`() = gang.runTest {
        var requestIndex = 0
        val idempotencyKeys = mutableListOf<String>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requestIndex += 1
                        assertEquals("Bearer token", request.headers["authorization"])

                        when (requestIndex) {
                            1 -> {
                                assertEquals("POST", request.method)
                                assertEquals("/api/v1/sticker-packs/stkp_1/stickers", request.url.path)
                                idempotencyKeys.add(request.headers["idempotency-key"]!!)
                                throw ClientException(
                                    "SocketException: Write failed (OS Error: connection aborted, errno = 10053)",
                                    request.url,
                                )
                            }
                            2 -> {
                                assertEquals("GET", request.method)
                                assertEquals("/api/v1/sticker-packs", request.url.path)
                                HttpResponse(
                                    jsonEncode(_personalStickerPacksJson()),
                                    200,
                                )
                            }
                            3 -> {
                                assertEquals("POST", request.method)
                                assertEquals("/api/v1/sticker-packs/stkp_1/stickers", request.url.path)
                                idempotencyKeys.add(request.headers["idempotency-key"]!!)
                                throw ClientException(
                                    "SocketException: Write failed (OS Error: connection aborted, errno = 10053)",
                                    request.url,
                                )
                            }
                            4 -> {
                                assertEquals("GET", request.method)
                                assertEquals("/api/v1/sticker-packs", request.url.path)
                                HttpResponse(
                                    jsonEncode(_personalStickerPacksJson(linkedAssetId = "asset_2")),
                                    200,
                                )
                            }
                            else ->
                                _fail(
                                    "unexpected request $requestIndex ${request.method} ${request.url}",
                                )
                        }
                    },
            )

        api.addSticker(packId = "stkp_1", assetId = "asset_2", name = "hi")

        assertEquals(4, requestIndex)
        assertEquals(2, idempotencyKeys.size)
        assertEquals(idempotencyKeys.first(), idempotencyKeys.last())
        api.close()
    }

    @Test
    fun `RoomCard defaults the personal AI voice preference to disabled`() {
        val room =
            RoomCard.fromJson(
                mapOf(
                    "id" to "room_1",
                    "name" to "Alpha",
                    "avatar_url" to null,
                    "default_avatar_key" to "blue-1",
                    "member_count" to 2,
                    "live_participant_count" to 1,
                    "live_avatar_preview" to emptyList<Any?>(),
                    "last_message" to null,
                    "unread_count" to 0,
                    "updated_at" to "2026-07-11T10:00:00Z",
                ),
            )

        assertFalse(room.aiVoiceAnnouncementsEnabled)
    }

    @Test
    fun `RoomDetail parses room settings and nullable creator`() {
        val room =
            RoomDetail.fromJson(
                _roomDetailJson() +
                    mapOf(
                        "created_by" to null,
                        "description" to "A room for launch planning",
                        "visibility" to "public",
                        "join_policy" to "open",
                        "remark_name" to "Launch",
                        "notification_policy" to "mentions",
                        "is_pinned" to true,
                        "personal_profile" to
                            mapOf(
                                "display_name" to "Alice Ops",
                                "avatar_url" to "/assets/asset_2/room-avatar.png",
                                "default_avatar_key" to "mint-2",
                            ),
                        "ai_voice_announcements_enabled" to false,
                        "can_delete_room" to true,
                        "my_membership" to
                            mapOf(
                                "joined_at" to "2026-05-31T14:00:00Z",
                                "role" to "superuser",
                            ),
                    ),
            )

        assertNull(room.createdBy)
        assertEquals("A room for launch planning", room.description)
        assertEquals("public", room.visibility)
        assertEquals("open", room.joinPolicy)
        assertEquals("Launch", room.remarkName)
        assertEquals("mentions", room.notificationPolicy)
        assertTrue(room.isPinned)
        assertEquals("Alice Ops", room.personalProfile.displayName)
        assertEquals(1, room.onlineMemberCount)
        assertEquals(1, room.toCard().onlineMemberCount)
        assertFalse(room.aiVoiceAnnouncementsEnabled)
        assertFalse(room.myMembership.aiVoiceAnnouncementsEnabled)
        assertTrue(room.isAdmin)
        assertTrue(room.isSuperuser)
        assertTrue(room.canDelete)
        assertEquals("Launch", room.toCard().displayName)
        assertTrue(room.toCard().isPinned)
    }

    @Test
    fun `room management endpoints use server room routes`() = gang.runTest {
        var requests = 0
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requests += 1
                        assertEquals("Bearer token", request.headers["authorization"])
                        when (requests) {
                            1 -> {
                                assertEquals("PATCH", request.method)
                                assertEquals("/api/v1/rooms/room_1", request.url.path)
                                _assertJson(
                                    mapOf(
                                        "name" to "New Room",
                                        "description" to "Updated",
                                        "visibility" to "private",
                                        "join_policy" to "closed",
                                        "ai_voice_announcements_enabled" to false,
                                        "avatar_asset_id" to "asset_1",
                                        "default_avatar_key" to "teal-2",
                                    ),
                                    jsonDecode(utf8Decode(request.bodyBytes)),
                                )
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "room" to
                                                _roomDetailJson() +
                                                mapOf(
                                                    "name" to "New Room",
                                                    "description" to "Updated",
                                                    "visibility" to "private",
                                                    "join_policy" to "closed",
                                                    "ai_voice_announcements_enabled" to false,
                                                    "avatar_url" to "/assets/asset_1/room.png",
                                                    "default_avatar_key" to "teal-2",
                                                ),
                                        ),
                                    ),
                                    200,
                                )
                            }
                            2 -> {
                                assertEquals("PATCH", request.method)
                                assertEquals("/api/v1/rooms/room_1/me", request.url.path)
                                _assertJson(
                                    mapOf(
                                        "remark_name" to "Ops",
                                        "notification_policy" to "silent",
                                        "room_display_name" to "Alice Ops",
                                        "is_pinned" to true,
                                        "ai_voice_announcements_enabled" to false,
                                    ),
                                    jsonDecode(utf8Decode(request.bodyBytes)),
                                )
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "room" to
                                                _roomDetailJson() +
                                                mapOf(
                                                    "remark_name" to "Ops",
                                                    "notification_policy" to "silent",
                                                    "is_pinned" to true,
                                                    "personal_profile" to
                                                        mapOf(
                                                            "display_name" to "Alice Ops",
                                                            "avatar_url" to "/assets/asset_2/avatar.png",
                                                            "default_avatar_key" to "mint-2",
                                                        ),
                                                ),
                                        ),
                                    ),
                                    200,
                                )
                            }
                            3 -> {
                                assertEquals("DELETE", request.method)
                                assertEquals("/api/v1/rooms/room_1/members/me", request.url.path)
                                _assertJson(
                                    mapOf(
                                        "confirm_delete_if_empty" to true,
                                    ),
                                    jsonDecode(utf8Decode(request.bodyBytes)),
                                )
                                HttpResponse("{}", 200)
                            }
                            4 -> {
                                assertEquals("DELETE", request.method)
                                assertEquals("/api/v1/rooms/room_1", request.url.path)
                                _assertJson(
                                    mapOf(
                                        "confirm_name" to "New Room",
                                    ),
                                    jsonDecode(utf8Decode(request.bodyBytes)),
                                )
                                HttpResponse("{}", 200)
                            }
                            5 -> {
                                assertEquals("PATCH", request.method)
                                assertEquals("/api/v1/rooms/room_1/members/user_2", request.url.path)
                                _assertJson(
                                    mapOf(
                                        "role" to "admin",
                                    ),
                                    jsonDecode(utf8Decode(request.bodyBytes)),
                                )
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "member" to
                                                mapOf(
                                                    "user" to
                                                        mapOf(
                                                            "id" to "user_2",
                                                            "username" to "bob",
                                                            "display_name" to "Bob",
                                                        ),
                                                    "role" to "admin",
                                                    "joined_at" to "2026-05-31T14:00:00Z",
                                                ),
                                        ),
                                    ),
                                    200,
                                )
                            }
                            6 -> {
                                assertEquals("PATCH", request.method)
                                assertEquals("/api/v1/rooms/room_1/members/user_2", request.url.path)
                                _assertJson(
                                    mapOf(
                                        "room_display_name" to "Room Bob",
                                    ),
                                    jsonDecode(utf8Decode(request.bodyBytes)),
                                )
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "member" to
                                                mapOf(
                                                    "user" to
                                                        mapOf(
                                                            "id" to "user_2",
                                                            "username" to "bob",
                                                            "display_name" to "Bob",
                                                            "room_display_name" to "Room Bob",
                                                        ),
                                                    "room_display_name" to "Room Bob",
                                                    "role" to "admin",
                                                    "joined_at" to "2026-05-31T14:00:00Z",
                                                ),
                                        ),
                                    ),
                                    200,
                                )
                            }
                            7 -> {
                                assertEquals("DELETE", request.method)
                                assertEquals("/api/v1/rooms/room_1/members/user_2", request.url.path)
                                HttpResponse("{}", 200)
                            }
                            8 -> {
                                assertEquals("PATCH", request.method)
                                assertEquals("/api/v1/rooms/room_1/creator", request.url.path)
                                _assertJson(
                                    mapOf(
                                        "user_id" to "user_2",
                                    ),
                                    jsonDecode(utf8Decode(request.bodyBytes)),
                                )
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "room" to
                                                _roomDetailJson() +
                                                mapOf(
                                                    "created_by" to
                                                        mapOf(
                                                            "id" to "user_2",
                                                            "username" to "bob",
                                                            "display_name" to "Bob",
                                                        ),
                                                ),
                                        ),
                                    ),
                                    200,
                                )
                            }
                            else -> _fail("unexpected request ${request.method} ${request.url}")
                        }
                    },
            )

        val updatedRoom =
            api.updateRoom(
                roomId = "room_1",
                name = "New Room",
                description = "Updated",
                visibility = "private",
                joinPolicy = "closed",
                aiVoiceAnnouncementsEnabled = false,
                avatarAssetId = "asset_1",
                defaultAvatarKey = "teal-2",
            )
        val myRoom =
            api.updateMyRoomSettings(
                roomId = "room_1",
                remarkName = "Ops",
                notificationPolicy = "silent",
                roomDisplayName = "Alice Ops",
                isPinned = true,
                aiVoiceAnnouncementsEnabled = false,
            )
        api.leaveRoom(roomId = "room_1", confirmDeleteIfEmpty = true)
        api.deleteRoom(roomId = "room_1", confirmName = "New Room")
        val member =
            api.updateRoomMemberRole(
                roomId = "room_1",
                userId = "user_2",
                role = "admin",
            )
        val renamedMember =
            api.updateRoomMemberRoomDisplayName(
                roomId = "room_1",
                userId = "user_2",
                roomDisplayName = "Room Bob",
            )
        api.removeRoomMember(roomId = "room_1", userId = "user_2")
        val transferred =
            api.transferRoomCreator(
                roomId = "room_1",
                userId = "user_2",
            )

        assertEquals("New Room", updatedRoom.name)
        assertEquals("Ops", myRoom.remarkName)
        assertTrue(myRoom.isPinned)
        assertEquals("admin", member.role)
        assertEquals("Room Bob", renamedMember.roomDisplayName)
        assertEquals("user_2", transferred.createdBy?.id)
        assertEquals(8, requests)
        api.close()
    }

    @Test
    fun `updateProfile retries a transient socket write abort`() = gang.runTest {
        var requests = 0
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requests += 1
                        assertEquals("PATCH", request.method)
                        assertEquals("/api/v1/users/me/profile", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])
                        _assertJson(
                            mapOf(
                                "avatar_asset_id" to "asset_1",
                            ),
                            jsonDecode(request.body),
                        )

                        if (requests == 1) {
                            throw ClientException(
                                "SocketException: Write failed (OS Error: " +
                                    "你的主机中的软件中止了一个已建立的连接, errno = 10053)",
                                request.url,
                            )
                        }

                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "user" to
                                        mapOf(
                                            "id" to "user_1",
                                            "uid" to "1000001",
                                            "username" to "alice",
                                            "display_name" to "Alice",
                                            "avatar_url" to "/assets/asset_1/avatar.png",
                                            "default_avatar_key" to "blue-3",
                                        ),
                                ),
                            ),
                            200,
                        )
                    },
            )

        val user = api.updateProfile(avatarAssetId = "asset_1")

        assertEquals(2, requests)
        assertEquals("/assets/asset_1/avatar.png", user.avatarUrl)
        api.close()
    }

    @Test
    fun `getMusicBoxState parses the room snapshot`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/rooms/room_1/music-box/state", request.url.path)
                        assertEquals("Bearer token", request.headers["authorization"])
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "enabled" to true,
                                    "playback" to
                                        mapOf(
                                            "state" to "playing",
                                            "current_item_id" to "item_1",
                                            "position_ms" to 4200,
                                            "volume" to 70,
                                            "updated_at" to "2026-06-01T10:00:00Z",
                                        ),
                                    "queue" to
                                        listOf(
                                            mapOf(
                                                "id" to "item_1",
                                                "source" to "netease",
                                                "track_id" to "t1",
                                                "title" to "Song A",
                                                "artist" to "Artist A",
                                                "duration_ms" to 200000,
                                                "status" to "ready",
                                                "added_by_user_id" to "user_2",
                                            ),
                                        ),
                                    "usage" to mapOf("used_bytes" to 1024, "limit_bytes" to 10240),
                                ),
                            ),
                            200,
                        )
                    },
            )

        val state = api.getMusicBoxState("room_1")

        assertTrue(state.enabled)
        assertEquals(MusicBoxPlaybackState.playing, state.playback.state)
        assertEquals("Song A", state.currentItem?.title)
        assertEquals(10240L, state.usage.limitBytes)
        api.close()
    }

    @Test
    fun `searchMusicBox sends keyword and parses results`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("GET", request.method)
                        assertEquals("/api/v1/rooms/room_1/music-box/search", request.url.path)
                        assertEquals("晴天", request.url.queryParameters["keyword"])
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "results" to
                                        listOf(
                                            mapOf(
                                                "source" to "netease",
                                                "track_id" to "t9",
                                                "name" to "晴天",
                                                "artists" to listOf("周杰伦"),
                                            ),
                                        ),
                                ),
                            ),
                            200,
                            headers = mapOf("content-type" to "application/json; charset=utf-8"),
                        )
                    },
            )

        val results = api.searchMusicBox(roomId = "room_1", keyword = "晴天")

        assertEquals(1, results.size)
        assertEquals("晴天", results.single().name)
        assertEquals(listOf("周杰伦"), results.single().artists)
        api.close()
    }

    @Test
    fun `queueMusicBoxTrack posts the track body and returns the snapshot`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/rooms/room_1/music-box/queue", request.url.path)
                        _assertJson(
                            mapOf(
                                "source" to "netease",
                                "track_id" to "t9",
                                "title" to "晴天",
                                "artist" to "周杰伦",
                            ),
                            jsonDecode(request.body),
                        )
                        HttpResponse(
                            jsonEncode(mapOf("enabled" to true, "queue" to emptyList<Any?>(), "usage" to emptyMap<String, Any?>())),
                            200,
                        )
                    },
            )

        val state =
            api.queueMusicBoxTrack(
                roomId = "room_1",
                trackId = "t9",
                title = "晴天",
                source = "netease",
                artist = "周杰伦",
            )

        assertTrue(state.enabled)
        api.close()
    }

    @Test
    fun `controlMusicBox posts the action and optional queue item`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/rooms/room_1/music-box/control", request.url.path)
                        _assertJson(
                            mapOf(
                                "action" to "play_now",
                                "item_id" to "queue_1",
                            ),
                            jsonDecode(request.body),
                        )
                        HttpResponse(
                            jsonEncode(mapOf("enabled" to true, "queue" to emptyList<Any?>(), "usage" to emptyMap<String, Any?>())),
                            200,
                        )
                    },
            )

        val state =
            api.controlMusicBox(
                roomId = "room_1",
                action = "play_now",
                itemId = "queue_1",
            )

        assertTrue(state.enabled)
        api.close()
    }

    @Test
    fun `activateMusicBoxPlaylist posts saved source and playback intent`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/rooms/room_1/music-box/activate", request.url.path)
                        _assertJson(
                            mapOf(
                                "source_type" to "room_playlist",
                                "start_play" to true,
                                "playlist_id" to "playlist_1",
                                "start_item_id" to "playlist_item_2",
                            ),
                            jsonDecode(request.body),
                        )
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "enabled" to true,
                                    "revision" to 2,
                                    "active_source" to
                                        mapOf(
                                            "type" to "room_playlist",
                                            "playlist_id" to "playlist_1",
                                            "snapshot_id" to "snapshot_1",
                                            "name" to "Room list",
                                            "created_at" to "2026-08-06T03:04:05Z",
                                        ),
                                    "queue" to emptyList<Any?>(),
                                    "temporary_queue" to emptyList<Any?>(),
                                    "usage" to emptyMap<String, Any?>(),
                                ),
                            ),
                            200,
                        )
                    },
            )

        val state =
            api.activateMusicBoxPlaylist(
                roomId = "room_1",
                sourceType = MusicBoxActiveSourceType.roomPlaylist,
                playlistId = "playlist_1",
                startItemId = "playlist_item_2",
            )

        assertEquals(MusicBoxActiveSourceType.roomPlaylist, state.activeSource.type)
        assertEquals("snapshot_1", state.activeSource.snapshotId)
        assertEquals(DateTime.utc(2026, 8, 6, 3, 4, 5), state.activeSource.createdAt)
        assertEquals(2, state.revision)
        api.close()
    }

    @Test
    fun `cloneActiveMusicBoxPlaylist pins the immutable snapshot`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals(
                            "/api/v1/rooms/room_1/music-box/active-playlist/clone",
                            request.url.path,
                        )
                        _assertJson(
                            mapOf(
                                "playlist_id" to "playlist_1",
                                "snapshot_id" to "snapshot_1",
                            ),
                            jsonDecode(request.body),
                        )
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "playlist" to
                                        mapOf(
                                            "id" to "playlist_clone",
                                            "name" to "朋友的歌单 · 夜晚",
                                            "description" to "",
                                            "revision" to 1,
                                            "item_count" to 2,
                                        ),
                                ),
                            ),
                            201,
                            headers = mapOf("content-type" to "application/json; charset=utf-8"),
                        )
                    },
            )

        val playlist =
            api.cloneActiveMusicBoxPlaylist(
                roomId = "room_1",
                playlistId = "playlist_1",
                snapshotId = "snapshot_1",
            )

        assertEquals("playlist_clone", playlist.id)
        assertEquals("朋友的歌单 · 夜晚", playlist.name)
        assertEquals(2, playlist.itemCount)
        api.close()
    }

    @Test
    fun `removeMusicBoxItem deletes the queue item`() = gang.runTest {
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("DELETE", request.method)
                        assertEquals("/api/v1/rooms/room_1/music-box/queue/item_1", request.url.path)
                        HttpResponse(
                            jsonEncode(mapOf("enabled" to true, "queue" to emptyList<Any?>(), "usage" to emptyMap<String, Any?>())),
                            200,
                        )
                    },
            )

        val state =
            api.removeMusicBoxItem(
                roomId = "room_1",
                itemId = "item_1",
            )

        assertTrue(state.enabled)
        api.close()
    }

    @Test
    fun `personal playlist endpoints parse paginated metadata and items`() = gang.runTest {
        var requests = 0
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requests += 1
                        assertEquals("GET", request.method)
                        assertEquals("Bearer token", request.headers["authorization"])
                        if (requests == 1) {
                            assertEquals("/api/v1/me/music-box/playlists", request.url.path)
                            assertEquals("1", request.url.queryParameters["page"])
                            assertEquals("50", request.url.queryParameters["page_size"])
                            return@MockClient HttpResponse(
                                jsonEncode(
                                    mapOf(
                                        "playlists" to
                                            listOf(
                                                mapOf(
                                                    "id" to "mbp_1",
                                                    "name" to "夜晚",
                                                    "description" to "",
                                                    "revision" to 3,
                                                    "item_count" to 1,
                                                    "created_at" to "2026-07-31T01:00:00Z",
                                                    "updated_at" to "2026-07-31T01:05:00Z",
                                                ),
                                            ),
                                        "pagination" to
                                            mapOf(
                                                "page" to 1,
                                                "page_size" to 50,
                                                "total" to 1,
                                                "has_more" to false,
                                            ),
                                        "limits" to mapOf("max_playlists" to 50, "max_playlist_items" to 500),
                                    ),
                                ),
                                200,
                                headers = mapOf("content-type" to "application/json; charset=utf-8"),
                            )
                        }
                        assertEquals("/api/v1/me/music-box/playlists/mbp_1", request.url.path)
                        assertEquals("晴", request.url.queryParameters["keyword"])
                        assertEquals("netease", request.url.queryParameters["source"])
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "playlist" to
                                        mapOf(
                                            "id" to "mbp_1",
                                            "name" to "夜晚",
                                            "description" to "",
                                            "revision" to 3,
                                            "item_count" to 1,
                                        ),
                                    "items" to
                                        listOf(
                                            mapOf(
                                                "id" to "mbpi_1",
                                                "playlist_id" to "mbp_1",
                                                "track_id" to "track_1",
                                                "source" to "netease",
                                                "title" to "晴天",
                                                "artists" to listOf("周杰伦"),
                                                "duration_ms" to 269000,
                                                "sort_order" to 10,
                                            ),
                                        ),
                                    "pagination" to
                                        mapOf(
                                            "page" to 1,
                                            "page_size" to 50,
                                            "total" to 1,
                                            "has_more" to false,
                                        ),
                                ),
                            ),
                            200,
                            headers = mapOf("content-type" to "application/json; charset=utf-8"),
                        )
                    },
            )

        val playlists = api.listPersonalMusicPlaylists()
        val items =
            api.getPersonalMusicPlaylist(
                playlistId = "mbp_1",
                keyword = "晴",
                source = "netease",
            )

        assertEquals("夜晚", playlists.playlists.single().name)
        assertEquals(500, playlists.maxPlaylistItems)
        assertEquals("晴天", items.items.single().title)
        assertEquals(listOf("周杰伦"), items.items.single().artists)
        api.close()
    }

    @Test
    fun `personal playlist mutations use scoped routes and request bodies`() = gang.runTest {
        val seen = mutableListOf<String>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        seen.add("${request.method} ${request.url.path}")
                        when (request.method to request.url.path) {
                            "POST" to "/api/v1/me/music-box/playlists" -> {
                                _assertJson(mapOf("name" to "夜晚"), jsonDecode(request.body))
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "playlist" to
                                                mapOf(
                                                    "id" to "mbp_1",
                                                    "name" to "夜晚",
                                                    "description" to "",
                                                    "revision" to 1,
                                                    "item_count" to 0,
                                                ),
                                        ),
                                    ),
                                    201,
                                    headers = mapOf("content-type" to "application/json; charset=utf-8"),
                                )
                            }
                            "POST" to "/api/v1/me/music-box/playlists/merge" -> {
                                _assertJson(
                                    mapOf(
                                        "name" to "合并结果",
                                        "playlist_ids" to listOf("mbp_2", "mbp_1"),
                                    ),
                                    jsonDecode(request.body),
                                )
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "playlist" to
                                                mapOf(
                                                    "id" to "mbp_merged",
                                                    "name" to "合并结果",
                                                    "description" to "",
                                                    "revision" to 1,
                                                    "item_count" to 2,
                                                ),
                                            "merge" to
                                                mapOf(
                                                    "source_item_count" to 3,
                                                    "unique_item_count" to 2,
                                                    "duplicate_count" to 1,
                                                    "item_count" to 2,
                                                    "omitted_count" to 0,
                                                    "deleted_playlist_count" to 2,
                                                    "retained_playlist_count" to 0,
                                                    "consumed_source_item_count" to 3,
                                                    "truncated" to false,
                                                ),
                                        ),
                                    ),
                                    201,
                                    headers = mapOf("content-type" to "application/json; charset=utf-8"),
                                )
                            }
                            "POST" to "/api/v1/me/music-box/playlists/mbp_1/items" -> {
                                _assertJson(
                                    mapOf(
                                        "track_id" to "track_1",
                                        "source" to "netease",
                                        "title" to "晴天",
                                        "artists" to listOf("周杰伦"),
                                    ),
                                    jsonDecode(request.body),
                                )
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "item" to
                                                mapOf(
                                                    "id" to "mbpi_1",
                                                    "playlist_id" to "mbp_1",
                                                    "track_id" to "track_1",
                                                    "source" to "netease",
                                                    "title" to "晴天",
                                                    "artists" to listOf("周杰伦"),
                                                    "sort_order" to 10,
                                                ),
                                        ),
                                    ),
                                    201,
                                    headers = mapOf("content-type" to "application/json; charset=utf-8"),
                                )
                            }
                            "PATCH" to "/api/v1/me/music-box/playlists/mbp_1/items/order" -> {
                                _assertJson(
                                    mapOf(
                                        "item_id" to "mbpi_1",
                                        "direction" to "down",
                                    ),
                                    jsonDecode(request.body),
                                )
                                HttpResponse(jsonEncode(mapOf("ok" to true)), 200)
                            }
                            "PATCH" to "/api/v1/me/music-box/playlists/order" -> {
                                val body = jsonDecode(request.body) as Map<*, *>
                                if (body.containsKey("playlist_ids")) {
                                    _assertJson(
                                        mapOf(
                                            "playlist_ids" to listOf("mbp_1", "mbp_2"),
                                        ),
                                        body,
                                    )
                                } else {
                                    _assertJson(mapOf("playlist_id" to "mbp_1", "direction" to "up"), body)
                                }
                                HttpResponse(jsonEncode(mapOf("ok" to true)), 200)
                            }
                            "PATCH" to "/api/v1/me/music-box/playlists/mbp_1" -> {
                                _assertJson(mapOf("name" to "夜间精选"), jsonDecode(request.body))
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "playlist" to
                                                mapOf(
                                                    "id" to "mbp_1",
                                                    "name" to "夜间精选",
                                                    "description" to "",
                                                    "revision" to 2,
                                                    "item_count" to 1,
                                                ),
                                        ),
                                    ),
                                    200,
                                    headers = mapOf("content-type" to "application/json; charset=utf-8"),
                                )
                            }
                            "DELETE" to "/api/v1/me/music-box/playlists/mbp_1/items" -> {
                                _assertJson(
                                    mapOf(
                                        "item_ids" to listOf("mbpi_1"),
                                    ),
                                    jsonDecode(request.body),
                                )
                                HttpResponse(jsonEncode(mapOf("ok" to true, "deleted" to 1)), 200)
                            }
                            "DELETE" to "/api/v1/me/music-box/playlists/mbp_1" ->
                                HttpResponse(jsonEncode(mapOf("ok" to true)), 200)
                            else -> _fail("unexpected request: ${request.method} ${request.url}")
                        }
                    },
            )

        val playlist = api.createPersonalMusicPlaylist(name = "夜晚")
        val merged =
            api.mergePersonalMusicPlaylists(
                name = "合并结果",
                playlistIds = listOf("mbp_2", "mbp_1"),
            )
        val item =
            api.addPersonalMusicPlaylistItem(
                playlistId = playlist.id,
                track =
                    MusicBoxSearchResult(
                        trackId = "track_1",
                        name = "晴天",
                        artists = listOf("周杰伦"),
                        source = "netease",
                    ),
            )
        api.movePersonalMusicPlaylistItem(
            playlistId = playlist.id,
            itemId = item.id,
            direction = "down",
        )
        api.pinPersonalMusicPlaylists(playlistIds = listOf(playlist.id, "mbp_2"))
        api.movePersonalMusicPlaylist(
            playlistId = playlist.id,
            direction = "up",
        )
        val renamed =
            api.renamePersonalMusicPlaylist(
                playlistId = playlist.id,
                name = "夜间精选",
            )
        api.deletePersonalMusicPlaylistItems(
            playlistId = playlist.id,
            itemIds = listOf(item.id),
        )
        api.deletePersonalMusicPlaylist(playlist.id)

        assertEquals("夜间精选", renamed.name)
        assertEquals("合并结果", merged.playlist.name)
        assertEquals(1, merged.duplicateCount)
        assertFalse(merged.truncated)
        assertEquals(9, seen.size)
        api.close()
    }

    @Test
    fun `room playlist APIs use room-scoped routes and bodies`() = gang.runTest {
        val seen = mutableListOf<String>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        seen.add("${request.method} ${request.url.path}")
                        assertEquals("Bearer token", request.headers["authorization"])
                        when (request.method to request.url.path) {
                            "GET" to "/api/v1/rooms/room_1/music-box/playlists" -> {
                                assertEquals(
                                    mapOf(
                                        "page" to "1",
                                        "page_size" to "50",
                                    ),
                                    request.url.queryParameters,
                                )
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "playlists" to
                                                listOf(
                                                    mapOf(
                                                        "id" to "mbp_room_1",
                                                        "name" to "房间精选",
                                                        "description" to "",
                                                        "revision" to 1,
                                                        "item_count" to 0,
                                                    ),
                                                ),
                                            "pagination" to
                                                mapOf(
                                                    "page" to 1,
                                                    "page_size" to 50,
                                                    "total" to 1,
                                                    "has_more" to false,
                                                ),
                                            "limits" to mapOf("max_playlists" to 50, "max_playlist_items" to 500),
                                        ),
                                    ),
                                    200,
                                    headers = mapOf("content-type" to "application/json; charset=utf-8"),
                                )
                            }
                            "POST" to "/api/v1/rooms/room_1/music-box/playlists" -> {
                                val body = jsonDecode(request.body) as Map<*, *>
                                val imported = body.containsKey("import_playlist_id")
                                _assertJson(
                                    if (imported) {
                                        mapOf("name" to "导入精选", "import_playlist_id" to "mbp_personal_1")
                                    } else {
                                        mapOf("name" to "新歌单")
                                    },
                                    body,
                                )
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "playlist" to
                                                mapOf(
                                                    "id" to if (imported) "mbp_room_import" else "mbp_room_2",
                                                    "name" to if (imported) "导入精选" else "新歌单",
                                                    "description" to "",
                                                    "revision" to 1,
                                                    "item_count" to if (imported) 1 else 0,
                                                ),
                                        ),
                                    ),
                                    201,
                                    headers = mapOf("content-type" to "application/json; charset=utf-8"),
                                )
                            }
                            "POST" to "/api/v1/rooms/room_1/music-box/playlists/merge" -> {
                                _assertJson(
                                    mapOf(
                                        "name" to "房间合并",
                                        "playlist_ids" to listOf("mbp_room_2", "mbp_room_1"),
                                    ),
                                    jsonDecode(request.body),
                                )
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "playlist" to
                                                mapOf(
                                                    "id" to "mbp_room_merged",
                                                    "name" to "房间合并",
                                                    "description" to "",
                                                    "revision" to 1,
                                                    "item_count" to 1,
                                                ),
                                            "merge" to
                                                mapOf(
                                                    "source_item_count" to 1,
                                                    "unique_item_count" to 1,
                                                    "duplicate_count" to 0,
                                                    "item_count" to 1,
                                                    "omitted_count" to 0,
                                                    "deleted_playlist_count" to 2,
                                                    "retained_playlist_count" to 0,
                                                    "consumed_source_item_count" to 1,
                                                    "truncated" to false,
                                                ),
                                        ),
                                    ),
                                    201,
                                    headers = mapOf("content-type" to "application/json; charset=utf-8"),
                                )
                            }
                            "POST" to "/api/v1/rooms/room_1/music-box/playlists/mbp_room_2/clone-to-me" -> {
                                assertTrue(request.body.isEmpty())
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "playlist" to
                                                mapOf(
                                                    "id" to "mbp_personal_clone_1",
                                                    "name" to "房间备注名 · 新歌单",
                                                    "description" to "",
                                                    "revision" to 1,
                                                    "item_count" to 0,
                                                ),
                                        ),
                                    ),
                                    201,
                                    headers = mapOf("content-type" to "application/json; charset=utf-8"),
                                )
                            }
                            "POST" to "/api/v1/rooms/room_1/music-box/playlists/mbp_room_2/items" -> {
                                _assertJson(
                                    mapOf(
                                        "track_id" to "track_1",
                                        "source" to "netease",
                                        "title" to "晴天",
                                        "artists" to listOf("周杰伦"),
                                    ),
                                    jsonDecode(request.body),
                                )
                                HttpResponse(
                                    jsonEncode(
                                        mapOf(
                                            "item" to
                                                mapOf(
                                                    "id" to "mbpi_room_1",
                                                    "playlist_id" to "mbp_room_2",
                                                    "track_id" to "track_1",
                                                    "source" to "netease",
                                                    "title" to "晴天",
                                                    "artists" to listOf("周杰伦"),
                                                    "sort_order" to 10,
                                                ),
                                        ),
                                    ),
                                    201,
                                    headers = mapOf("content-type" to "application/json; charset=utf-8"),
                                )
                            }
                            "PATCH" to "/api/v1/rooms/room_1/music-box/playlists/order" -> {
                                _assertJson(
                                    mapOf(
                                        "playlist_ids" to listOf("mbp_room_2", "mbp_room_1"),
                                    ),
                                    jsonDecode(request.body),
                                )
                                HttpResponse(jsonEncode(mapOf("ok" to true)), 200)
                            }
                            "DELETE" to "/api/v1/rooms/room_1/music-box/playlists/mbp_room_2/items" -> {
                                _assertJson(
                                    mapOf(
                                        "item_ids" to listOf("mbpi_room_1"),
                                    ),
                                    jsonDecode(request.body),
                                )
                                HttpResponse(jsonEncode(mapOf("ok" to true, "deleted" to 1)), 200)
                            }
                            "DELETE" to "/api/v1/rooms/room_1/music-box/playlists/mbp_room_2" ->
                                HttpResponse(jsonEncode(mapOf("ok" to true)), 200)
                            else -> _fail("unexpected request: ${request.method} ${request.url}")
                        }
                    },
            )

        val listed = api.listRoomMusicPlaylists(roomId = "room_1")
        val created =
            api.createRoomMusicPlaylist(
                roomId = "room_1",
                name = "新歌单",
            )
        val imported =
            api.createRoomMusicPlaylistFromPersonal(
                roomId = "room_1",
                name = "导入精选",
                importPlaylistId = "mbp_personal_1",
            )
        val merged =
            api.mergeRoomMusicPlaylists(
                roomId = "room_1",
                name = "房间合并",
                playlistIds = listOf("mbp_room_2", "mbp_room_1"),
            )
        val item =
            api.addRoomMusicPlaylistItem(
                roomId = "room_1",
                playlistId = created.id,
                track =
                    MusicBoxSearchResult(
                        trackId = "track_1",
                        name = "晴天",
                        artists = listOf("周杰伦"),
                        source = "netease",
                    ),
            )
        val cloned =
            api.cloneRoomMusicPlaylistToPersonal(
                roomId = "room_1",
                playlistId = created.id,
            )
        api.pinRoomMusicPlaylists(
            roomId = "room_1",
            playlistIds = listOf(created.id, listed.playlists.single().id),
        )
        api.deleteRoomMusicPlaylistItems(
            roomId = "room_1",
            playlistId = created.id,
            itemIds = listOf(item.id),
        )
        api.deleteRoomMusicPlaylist(roomId = "room_1", playlistId = created.id)

        assertEquals(1, imported.itemCount)
        assertEquals("房间备注名 · 新歌单", cloned.name)
        assertEquals("房间合并", merged.playlist.name)
        assertEquals(9, seen.size)
        api.close()
    }

    @Test
    fun `playlist batch add APIs preserve selected item order`() = gang.runTest {
        val seen = mutableListOf<String>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        seen.add("${request.method} ${request.url.path}")
                        assertEquals("Bearer token", request.headers["authorization"])
                        _assertJson(
                            mapOf(
                                "source_playlist_id" to
                                    if (request.url.path.contains("/rooms/")) "room_source" else "personal_source",
                                "item_ids" to listOf("second", "first"),
                            ),
                            jsonDecode(request.body),
                        )
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "playlist" to
                                        mapOf(
                                            "id" to
                                                if (request.url.path.contains("/rooms/")) "room_target" else "personal_target",
                                            "name" to "目标歌单",
                                            "description" to "",
                                            "revision" to 3,
                                            "item_count" to 500,
                                        ),
                                    "batch_add" to
                                        mapOf(
                                            "selected_item_count" to 2,
                                            "unique_item_count" to 2,
                                            "duplicate_count" to 0,
                                            "already_present_count" to 0,
                                            "added_item_count" to 1,
                                            "omitted_count" to 1,
                                            "truncated" to true,
                                        ),
                                ),
                            ),
                            200,
                            headers = mapOf("content-type" to "application/json; charset=utf-8"),
                        )
                    },
            )

        val personal =
            api.batchAddPersonalMusicPlaylistItems(
                sourcePlaylistId = "personal_source",
                targetPlaylistId = "personal_target",
                itemIds = listOf("second", "first"),
            )
        val room =
            api.batchAddRoomMusicPlaylistItems(
                roomId = "room_1",
                sourcePlaylistId = "room_source",
                targetPlaylistId = "room_target",
                itemIds = listOf("second", "first"),
            )

        assertEquals(
            listOf(
                "POST /api/v1/me/music-box/playlists/personal_target/items/batch-add",
                "POST /api/v1/rooms/room_1/music-box/playlists/room_target/items/batch-add",
            ),
            seen,
        )
        assertEquals(1, personal.addedItemCount)
        assertEquals(1, personal.omittedCount)
        assertTrue(personal.truncated)
        assertEquals("room_target", room.playlist.id)
        api.close()
    }
}

/** `expect(decodedBody, {...})`: compares JSON values, Int/Long-agnostic. */
private fun _assertJson(expected: Any?, actual: Any?) {
    assertEquals(jsonDecode(jsonEncode(expected)), actual)
}

/** `fail(...)` inside a MockClient handler, typed `Nothing` like Dart's. */
private fun _fail(message: String): Nothing = throw AssertionError(message)

/**
 * Dart's MockClient receives a `MultipartRequest` already finalized: its
 * `content-type` carries the boundary and the body holds the encoded parts.
 * The shared MockClient flattens multipart requests to a simplified format,
 * so the upload cases (which assert on the content type and on the
 * `name="purpose"` part header) send through Dart's encoding instead.
 */
private class _DartMultipartMockClient(handler: suspend (HttpRequest) -> HttpResponse) : HttpClient {
    private val inner = MockClient(handler)

    override suspend fun send(request: BaseRequest): StreamedResponse {
        if (request !is MultipartRequest) return inner.send(request)
        val boundary = "dart-http-boundary-test"
        val finalized = HttpRequest(request.method, request.url)
        finalized.headers.putAll(request.headers)
        finalized.headers["content-type"] = "multipart/form-data; boundary=$boundary"
        val out = ByteArrayOutputStream()
        for ((name, value) in request.fields) {
            out.write(utf8Encode("--$boundary\r\ncontent-disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n"))
        }
        for (file in request.files) {
            out.write(
                utf8Encode(
                    "--$boundary\r\n" +
                        "content-type: ${file.contentType ?: "application/octet-stream"}\r\n" +
                        "content-disposition: form-data; name=\"${file.field}\"; filename=\"${file.filename}\"\r\n\r\n",
                ),
            )
            out.write(file.stream.toBytes())
            out.write(utf8Encode("\r\n"))
        }
        out.write(utf8Encode("--$boundary--\r\n"))
        finalized.bodyBytes = out.toByteArray()
        return inner.send(finalized)
    }
}

private fun _roomInviteJson(
    status: String = "pending",
    roomExists: Boolean = true,
    invalidReason: String? = null,
    inviterRoomRole: String = "owner",
    inviterExists: Boolean = true,
    roomJoined: Boolean = false,
    includeViewerRoomInfo: Boolean = false,
): JsonMap {
    val room =
        linkedMapOf<String, Any?>(
            "id" to "room_1",
            "rid" to "900001",
            "name" to "Invite Room",
            "description" to "Invite room bio",
            "avatar_url" to null,
            "default_avatar_key" to "room-1",
            "visibility" to "private",
            "join_policy" to "closed",
            "member_count" to 1,
            "online_member_count" to 1,
            "live_participant_count" to 0,
            "joined" to roomJoined,
            "join_state" to if (roomJoined) "joined" else "none",
            "created_by" to
                mapOf(
                    "id" to "user_1",
                    "uid" to "1000001",
                    "username" to "alice",
                    "display_name" to "Alice",
                    "avatar_url" to null,
                    "default_avatar_key" to "blue-3",
                ),
        )
    if (includeViewerRoomInfo) {
        room["personal_profile"] =
            mapOf(
                "display_name" to "Alice in Room",
                "avatar_url" to "/room-alice.png",
                "default_avatar_key" to "green-2",
            )
        room["my_membership"] =
            mapOf(
                "joined_at" to "2026-05-31T14:00:00Z",
                "role" to "member",
            )
    }
    return mapOf(
        "id" to "rinv_1",
        "status" to status,
        "created_at" to "2026-05-31T13:00:00Z",
        "updated_at" to "2026-05-31T13:00:00Z",
        "room_exists" to roomExists,
        "inviter_exists" to inviterExists,
        "invalid_reason" to invalidReason,
        "room" to room,
        "inviter" to
            mapOf(
                "id" to "user_1",
                "uid" to "1000001",
                "username" to "alice",
                "display_name" to "Alice",
                "avatar_url" to null,
                "default_avatar_key" to "blue-3",
                "room_display_name" to "Alice in Invite Room",
                "room_role" to inviterRoomRole,
            ),
    )
}

private fun _roomBlacklistEntryJson(): JsonMap =
    mapOf(
        "user" to
            mapOf(
                "id" to "user_2",
                "uid" to "1000002",
                "username" to "blocked_user",
                "display_name" to "Blocked User",
                "avatar_url" to null,
                "default_avatar_key" to "red-2",
            ),
        "blocked_by" to
            mapOf(
                "id" to "user_1",
                "uid" to "1000001",
                "username" to "alice",
                "display_name" to "Alice",
                "avatar_url" to null,
                "default_avatar_key" to "blue-3",
            ),
        "created_at" to "2026-05-31T13:00:00Z",
    )

private fun _roomApplicationJson(
    status: String = "pending",
    reason: String = "",
    reviewerExists: Boolean = true,
): JsonMap {
    val reviewed = status == "approved" || status == "rejected"
    return mapOf(
        "id" to "jrq_1",
        "status" to status,
        "reason" to reason,
        "created_at" to "2026-05-30T13:00:00Z",
        "updated_at" to "2026-05-31T13:00:00Z",
        "reviewed_at" to if (reviewed) "2026-05-31T13:00:00Z" else null,
        "room" to _roomInviteJson()["room"],
        "reviewer_exists" to reviewerExists,
        "reviewer" to
            if (reviewed) {
                mapOf(
                    "id" to "user_1",
                    "uid" to "1000001",
                    "username" to "alice",
                    "display_name" to "Alice",
                    "avatar_url" to null,
                    "default_avatar_key" to "blue-3",
                    "room_display_name" to "Alice in Invite Room",
                    "room_role" to "owner",
                )
            } else {
                null
            },
    )
}

private fun _roomEventNotificationJson(readAt: String? = null): JsonMap =
    mapOf(
        "id" to "rnot_1",
        "type" to "role_promoted",
        "created_at" to "2026-06-01T13:00:00Z",
        "read_at" to readAt,
        "room_exists" to true,
        "actor_exists" to true,
        "from_role" to "member",
        "to_role" to "admin",
        "message_id" to "msg_mention_1",
        "message_preview" to "@Alice hello",
        "room" to _roomInviteJson(roomJoined = true)["room"],
        "actor" to
            mapOf(
                "id" to "user_1",
                "uid" to "1000001",
                "username" to "alice",
                "display_name" to "Alice",
                "avatar_url" to null,
                "default_avatar_key" to "blue-3",
                "room_display_name" to "Alice in Invite Room",
                "room_role" to "owner",
            ),
    )

private fun _roomDetailJson(): JsonMap =
    mapOf(
        "id" to "room_1",
        "rid" to "900001",
        "name" to "Invite Room",
        "avatar_url" to null,
        "default_avatar_key" to "room-1",
        "member_count" to 2,
        "online_member_count" to 1,
        "created_by" to
            mapOf(
                "id" to "user_1",
                "uid" to "1000001",
                "username" to "alice",
                "display_name" to "Alice",
            ),
        "my_membership" to mapOf("joined_at" to "2026-05-31T14:00:00Z", "role" to "member"),
        "live" to
            mapOf(
                "room_id" to "room_1",
                "participant_count" to 0,
                "participants" to emptyList<Any?>(),
                "updated_at" to "2026-05-31T14:00:00Z",
            ),
        "created_at" to "2026-05-31T13:00:00Z",
        "updated_at" to "2026-05-31T14:00:00Z",
    )

private fun _roomCardSearchJson(): JsonMap =
    mapOf(
        "id" to "room_1",
        "rid" to "900001",
        "name" to "Alpha Room",
        "avatar_url" to null,
        "default_avatar_key" to "room-1",
        "visibility" to "public",
        "join_policy" to "open",
        "description" to "Searchable room",
        "notification_policy" to "all",
        "member_count" to 3,
        "online_member_count" to 1,
        "live_participant_count" to 0,
        "live_avatar_preview" to emptyList<Any?>(),
        "last_message" to null,
        "unread_count" to 0,
        "updated_at" to "2026-05-31T14:00:00Z",
    )

private fun _publicRoomSearchJson(): JsonMap =
    mapOf(
        "id" to "room_2",
        "rid" to "900002",
        "name" to "Alpha Public",
        "avatar_url" to null,
        "default_avatar_key" to "room-2",
        "visibility" to "public",
        "join_policy" to "open",
        "member_count" to 2,
        "online_member_count" to 0,
        "live_participant_count" to 0,
        "joined" to false,
        "join_state" to "none",
    )

private fun _searchRoomContextJson(): JsonMap =
    mapOf(
        "id" to "room_1",
        "rid" to "900001",
        "name" to "Alpha Room",
        "avatar_url" to null,
        "default_avatar_key" to "room-1",
    )

private fun _messageSearchJson(
    type: String = "text",
    body: String = "hello",
    attachments: List<JsonMap> = emptyList(),
): JsonMap =
    mapOf(
        "id" to "msg_1",
        "room_id" to "room_1",
        "sender" to
            mapOf(
                "id" to "user_1",
                "username" to "alice",
                "display_name" to "Alice",
                "default_avatar_key" to "blue-3",
            ),
        "client_message_id" to "cmsg_1",
        "type" to type,
        "body" to body,
        "attachments" to attachments,
        "created_at" to "2026-05-31T14:00:00Z",
    )

private fun _liveJoinJson(): JsonMap {
    val participant =
        mapOf(
            "live_session_id" to "live_1",
            "user" to mapOf("id" to "user_1", "username" to "alice", "display_name" to "Alice"),
            "joined_at" to "2026-05-31T14:00:00Z",
            "mic_muted" to true,
            "headphones_muted" to false,
            "voice_blocked" to false,
            "camera_on" to false,
            "screen_sharing" to false,
            "connection_state" to "joining",
        )

    return mapOf(
        "livekit" to
            mapOf(
                "server_url" to "wss://voice.example.com",
                "token" to "livekit-token",
                "token_expires_at" to "2026-05-31T14:10:00Z",
                "room_name" to "room_1",
            ),
        "participant" to participant,
        "live" to
            mapOf(
                "room_id" to "room_1",
                "participant_count" to 1,
                "participants" to listOf(participant),
                "updated_at" to "2026-05-31T14:00:00Z",
            ),
    )
}

private fun _stickerPackJson(
    name: String = "My Stickers",
    stickers: List<JsonMap> = emptyList(),
): JsonMap =
    mapOf(
        "pack" to
            mapOf(
                "id" to "stkp_1",
                "scope" to "personal",
                "room_id" to null,
                "name" to name,
                "sort_order" to 10,
                "updated_at" to "2026-06-02T08:00:00Z",
                "stickers" to stickers,
            ),
    )

private fun _stickerJson(
    id: String = "stk_1",
    name: String = "ok",
    sortOrder: Int = 10,
): JsonMap {
    val assetId = "asset_$id"
    return mapOf(
        "id" to id,
        "name" to name,
        "sort_order" to sortOrder,
        "asset" to
            mapOf(
                "id" to assetId,
                "url" to "/assets/$assetId/ok.webp",
                "thumbnail_url" to "/assets/$assetId/ok.webp",
                "mime_type" to "image/webp",
                "width" to 128,
                "height" to 128,
                "created_at" to "2026-06-02T07:59:00Z",
            ),
    )
}

private fun _personalStickerPacksJson(linkedAssetId: String? = null): JsonMap =
    mapOf<String, Any?>(
        "packs" to
            listOf(
                mapOf<String, Any?>(
                    "id" to "stkp_1",
                    "scope" to "personal",
                    "room_id" to null,
                    "name" to "My Stickers",
                    "sort_order" to 10,
                    "updated_at" to "2026-06-02T08:00:00Z",
                    "stickers" to
                        if (linkedAssetId != null) {
                            listOf(
                                mapOf(
                                    "id" to "stk_2",
                                    "name" to "hi",
                                    "sort_order" to 10,
                                    "asset" to
                                        mapOf(
                                            "id" to linkedAssetId,
                                            "url" to "/assets/$linkedAssetId/hi.webp",
                                            "thumbnail_url" to "/assets/$linkedAssetId/hi.webp",
                                            "mime_type" to "image/webp",
                                        ),
                                ),
                            )
                        } else {
                            emptyList<Any?>()
                        },
                ),
            ),
    )
