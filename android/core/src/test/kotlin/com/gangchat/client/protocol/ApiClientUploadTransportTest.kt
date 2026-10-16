package com.gangchat.client.protocol

import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.testing.GangTestRule
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Not a Dart test: checks that the streamed upload body behaves like Dart's
 * `IOClient` on the real platform client, where an error thrown by the
 * `async*` body generator (a cancelled upload) reaches the caller unchanged.
 */
class ApiClientUploadTransportTest {
    @get:Rule
    val gang = GangTestRule()

    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    @Test
    fun `cancelling a streamed upload throws UploadCancelledException`() = gang.runTest {
        server.enqueue(MockResponse.Builder().code(201).body("{}").build())
        val controller = UploadTransferController()
        val progress = mutableListOf<Long>()
        val api =
            GangApiClient(
                baseUrl = server.url("/api/v1").toString(),
                accessTokenProvider = { "token" },
                httpClient = HttpClient(),
            )

        val error =
            runCatching {
                api.uploadFileAsset(
                    bytes = ByteArray(200 * 1024) { it.toByte() },
                    filename = "big.bin",
                    controller = controller,
                    onProgress = { sentBytes, _ ->
                        progress.add(sentBytes)
                        if (sentBytes > 0) controller.cancel()
                    },
                )
            }.exceptionOrNull()

        assertTrue(error.toString(), error is UploadCancelledException)
        assertEquals("上传已取消", error.toString())
        assertEquals(listOf(0L, 64L * 1024), progress)
        api.close()
    }

    @Test
    fun `a streamed upload reports progress through the platform client`() = gang.runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(201)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .body("""{"asset":{"id":"asset_1","url":"/assets/asset_1/big.bin","mime_type":"application/octet-stream","size_bytes":204800}}""")
                .build(),
        )
        val progress = mutableListOf<Long>()
        val api =
            GangApiClient(
                baseUrl = server.url("/api/v1").toString(),
                accessTokenProvider = { "token" },
                httpClient = HttpClient(),
            )

        val asset =
            api.uploadFileAsset(
                bytes = ByteArray(200 * 1024) { it.toByte() },
                filename = "big.bin",
                onProgress = { sentBytes, totalBytes ->
                    assertEquals(200L * 1024, totalBytes)
                    progress.add(sentBytes)
                },
            )

        assertEquals("asset_1", asset.id)
        assertEquals(204800L, asset.sizeBytes)
        assertEquals(listOf(0L, 65536L, 131072L, 196608L, 204800L), progress)
        val recorded = server.takeRequest()
        assertEquals("Bearer token", recorded.headers["Authorization"])
        assertTrue(recorded.headers["Content-Type"]!!.startsWith("multipart/form-data"))
        api.close()
    }
}
