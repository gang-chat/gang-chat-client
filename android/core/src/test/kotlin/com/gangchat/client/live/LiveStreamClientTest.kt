package com.gangchat.client.live

import com.gangchat.client.foundation.http.ByteStream
import com.gangchat.client.foundation.http.StreamedResponse
import com.gangchat.client.foundation.utf8Encode
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.MockClient
import com.gangchat.client.testing.pumpEventQueue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Not a Dart test (there is none for live_stream_client.dart): a smoke test of the SSE port. */
class LiveStreamClientTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `parses server-sent events and merges the envelope room id`() = gang.runTest {
        val requests = mutableListOf<String>()
        val client =
            LiveStreamClient(
                apiBaseUrl = "http://example.test/api/v1/",
                accessTokenProvider = { forceRefresh -> if (forceRefresh) "fresh" else "token" },
                httpClient =
                    MockClient.streaming { request ->
                        requests.add("${request.method} ${request.url} ${request.headers["Authorization"]}")
                        StreamedResponse(
                            ByteStream(
                                flow {
                                    emit(utf8Encode("event: ready\ndata: {}\n\n: heartbeat\n"))
                                    emit(utf8Encode("event: message_created\ndata: {\"type\":\"message_created\",\"room_id\":\"r1\","))
                                    emit(utf8Encode("\"data\":{\"id\":\"m1\",\"body\":\"你好\"}}\n\n"))
                                    emit(utf8Encode("event: presence\ndata: {\"type\":\"presence\",\"user_id\":\"u1\"}\n\n"))
                                    emit(utf8Encode("event: list\ndata: {\"room_id\":\"r2\",\"data\":[1,2]}\n\n"))
                                    awaitCancellation()
                                },
                            ),
                            200,
                        )
                    },
            )
        val events = mutableListOf<LiveEvent>()
        val statuses = mutableListOf<StreamStatus>()
        var reconnects = 0
        client.events.listen({ events.add(it) })
        client.addListener { statuses.add(client.status) }
        client.onReconnect = { reconnects += 1 }

        client.start()
        pumpEventQueue()

        assertEquals(listOf("GET http://example.test/api/v1/me/stream Bearer token"), requests)
        assertEquals(1, reconnects)
        assertEquals(listOf("message_created", "presence", "list"), events.map { it.type })
        assertEquals(mapOf("room_id" to "r1", "id" to "m1", "body" to "你好"), events[0].data)
        assertEquals(mapOf("user_id" to "u1"), events[1].data)
        assertEquals(mapOf("room_id" to "r2", "data" to listOf(1L, 2L)), events[2].data)

        client.stop()
        assertEquals(
            listOf(StreamStatus.connecting, StreamStatus.connected, StreamStatus.offline),
            statuses,
        )
        client.dispose()
    }
}
