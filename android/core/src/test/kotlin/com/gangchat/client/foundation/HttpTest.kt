package com.gangchat.client.foundation

import com.gangchat.client.foundation.http.ClientException
import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.foundation.http.HttpRequest
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.http.MultipartFile
import com.gangchat.client.foundation.http.MultipartRequest
import com.gangchat.client.foundation.http.get
import com.gangchat.client.foundation.http.post
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.MockClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HttpTest {
    @get:Rule
    val gang = GangTestRule()

    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun url(path: String) = Uri.parse(server.url(path).toString())

    @Test
    fun postsJsonAndDecodesUtf8Body() = gang.runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(201)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("X-Multi", "a")
                .addHeader("X-Multi", "b")
                .body("""{"name":"房间"}""")
                .build(),
        )
        val client = HttpClient()
        val response =
            client.post(
                url("/rooms"),
                headers = mapOf("content-type" to "application/json; charset=utf-8", "authorization" to "Bearer t"),
                body = """{"q":"你"}""",
            )
        assertEquals(201, response.statusCode)
        assertEquals("""{"name":"房间"}""", response.body)
        assertEquals("a,b", response.headers["x-multi"])
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("Bearer t", recorded.headers["Authorization"])
        assertEquals("""{"q":"你"}""", recorded.body?.utf8())
    }

    @Test
    fun streamsMultipartUploadsWithProgress() = gang.runTest {
        server.enqueue(MockResponse.Builder().code(200).body("ok").build())
        val chunks = listOf("hello ".toByteArray(), "world".toByteArray())
        val progress = mutableListOf<Int>()
        val request = MultipartRequest("POST", url("/upload"))
        request.fields["purpose"] = "avatar"
        var sent = 0
        request.files.add(
            MultipartFile(
                "file",
                com.gangchat.client.foundation.http.ByteStream(
                    flow {
                        for (chunk in chunks) {
                            emit(chunk)
                            sent += chunk.size
                            progress.add(sent)
                        }
                    },
                ),
                11,
                filename = "头像.png",
            ),
        )
        val response = HttpResponse.fromStream(HttpClient().send(request))
        assertEquals(200, response.statusCode)
        assertEquals(listOf(6, 11), progress)
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body, body.contains("name=\"purpose\""))
        assertTrue(body, body.contains("filename=\"头像.png\""))
        assertTrue(body, body.contains("hello world"))
    }

    @Test
    fun streamedResponseCanBeListenedIncrementally() = gang.runTest {
        server.enqueue(MockResponse.Builder().code(200).body("event: a\ndata: {}\n\n").build())
        val streamed = HttpClient().send(HttpRequest("GET", url("/stream")))
        val lines = mutableListOf<String>()
        val done = CompletableDeferred<Unit>()
        streamed.stream.utf8Lines().listen({ lines.add(it) }, onDone = { done.complete(Unit) })
        done.await()
        assertEquals(listOf("event: a", "data: {}", ""), lines)
    }

    @Test
    fun connectionFailuresBecomeClientExceptions() = gang.runTest {
        val dead = url("/x")
        server.close()
        val error = runCatching { HttpClient().get(dead) }.exceptionOrNull()
        assertTrue(error.toString(), error is ClientException)
        assertTrue(error.toString(), error.toString().lowercase().contains("connection"))
    }

    @Test
    fun mockClientSeesBufferedRequest() = gang.runTest {
        val client = MockClient { request -> HttpResponse("""{"ok":true}""", 200, headers = mapOf("content-type" to "application/json")) }
        val response = client.post(Uri.parse("https://api.test/a"), body = "x")
        assertEquals("""{"ok":true}""", response.body)
        assertEquals("x", client.requests.single().body)
    }
}
