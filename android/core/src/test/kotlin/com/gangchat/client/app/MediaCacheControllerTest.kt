package com.gangchat.client.app

import com.gangchat.client.foundation.http.BaseRequest
import com.gangchat.client.foundation.http.ByteStream
import com.gangchat.client.foundation.http.ClientException
import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.foundation.http.StreamedResponse
import com.gangchat.client.protocol.UploadTransferController
import com.gangchat.client.testing.GangTestRule
import java.io.File
import kotlinx.coroutines.flow.asFlow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Dart: test/unit/app/media_cache_controller_test.dart (media_cache_web_test
// is web only and not ported).
class MediaCacheControllerTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `getOrDownload stores bytes and reuses cached file`() = gang.runTest {
        val temp = _createTemp("gang_media_cache_test_")
        try {
            var requests = 0
            val controller =
                MediaCacheController(
                    cacheDirectoryProvider = { temp },
                    httpClientFactory = {
                        _BytesClient {
                            requests += 1
                            byteArrayOf(1, 2, 3, 4)
                        }
                    },
                )
            val request =
                MediaCacheRequest.tryFromUrl(
                    url = "https://assets.example.test/files/report.pdf",
                    filename = "report.pdf",
                    mimeType = "application/pdf",
                    expectedBytes = 4,
                )!!

            val first = controller.getOrDownload(request = request)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), first.readBytes())
            val second = controller.getOrDownload(request = request)

            assertEquals(first.path, second.path)
            assertEquals(1, requests)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `copyFileToPath writes cached bytes atomically`() = gang.runTest {
        val temp = _createTemp("gang_media_cache_test_")
        try {
            val source = File("${temp.path}/source.bin")
            source.writeBytes(byteArrayOf(5, 6, 7))
            val destination = "${temp.path}/nested/destination.bin"
            val controller =
                MediaCacheController(
                    cacheDirectoryProvider = { temp },
                )
            val transfer =
                FileTransferState.download(
                    controller = UploadTransferController(),
                    totalBytes = 0,
                    destinationPath = destination,
                )
            val progress = mutableListOf<Long>()

            controller.copyFileToPath(
                source = source,
                destinationPath = destination,
                transfer = transfer,
                onProgress = { sentBytes, totalBytes ->
                    assertEquals(3L, totalBytes)
                    progress.add(sentBytes)
                },
            )

            assertArrayEquals(byteArrayOf(5, 6, 7), File(destination).readBytes())
            assertTrue(transfer.wroteDestination)
            assertEquals(listOf(0L, 3L), progress)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `getOrDownload deletes partial cache file after cancellation`() = gang.runTest {
        val temp = _createTemp("gang_media_cache_test_")
        try {
            val controller =
                MediaCacheController(
                    cacheDirectoryProvider = { temp },
                    httpClientFactory = { _BytesClient { byteArrayOf(8, 9, 10) } },
                )
            val request =
                MediaCacheRequest.tryFromUrl(
                    url = "https://assets.example.test/files/cancel.bin",
                    filename = "cancel.bin",
                    expectedBytes = 3,
                )!!
            val transfer =
                FileTransferState.download(
                    controller = UploadTransferController(),
                    totalBytes = 0,
                    destinationPath = "${temp.path}/cancel.bin",
                )

            val error =
                runCatching {
                    controller.getOrDownload(
                        request = request,
                        transfer = transfer,
                        onProgress = { sentBytes, _ ->
                            if (sentBytes > 0) transfer.cancelTransfer()
                        },
                    )
                }.exceptionOrNull()
            assertTrue(error is MediaCacheCancelledException)

            val cached = controller.cachedFile(request)
            assertNull(cached)
        } finally {
            temp.deleteRecursively()
        }
    }

    /** Dart's `Directory.systemTemp.createTemp(prefix)`. */
    private fun _createTemp(prefix: String): File =
        kotlin.io.path.createTempDirectory(gang.tempDir.toPath(), prefix).toFile()

    // Nested so test helpers of other files in this package don't clash on the JVM.
    private class _BytesClient(val bytes: () -> ByteArray) : HttpClient {
        private var _closed = false

        override suspend fun send(request: BaseRequest): StreamedResponse {
            if (_closed) throw ClientException("closed", request.url)
            val body = bytes()
            return StreamedResponse(
                ByteStream(body.map { byte -> byteArrayOf(byte) }.asFlow()),
                200,
                contentLength = body.size.toLong(),
            )
        }

        override fun close() {
            _closed = true
        }
    }
}
