package com.gangchat.client.app

import com.gangchat.client.foundation.Uri
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FileDownloadsControllerTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `downloadToFile streams bytes and reports progress`() = gang.runTest {
        val temp = _createTemp("gang_download_test_")
        try {
            val destination = "${temp.path}/report.txt"
            val controller =
                FileDownloadsController(
                    httpClientFactory = {
                        _StreamClient(
                            listOf(
                                byteArrayOf(1, 2),
                                byteArrayOf(3, 4),
                            ),
                            contentLength = 4,
                        )
                    },
                )
            val transfer =
                controller.createDownload(
                    totalBytes = 0,
                    destinationPath = destination,
                )
            val progress = mutableListOf<Long>()

            controller.downloadToFile(
                uri = Uri.parse("http://example.test/report.txt"),
                transfer = transfer,
                onProgress = { sentBytes, totalBytes ->
                    assertEquals(4L, totalBytes)
                    progress.add(sentBytes)
                },
            )

            assertArrayEquals(byteArrayOf(1, 2, 3, 4), File(destination).readBytes())
            assertEquals(listOf(0L, 2L, 4L), progress)
            assertNull(transfer.downloadClient)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `downloadToFile deletes partial file after cancellation`() = gang.runTest {
        val temp = _createTemp("gang_download_test_")
        try {
            val destination = "${temp.path}/partial.bin"
            val controller =
                FileDownloadsController(
                    httpClientFactory = {
                        _StreamClient(
                            listOf(byteArrayOf(1, 2, 3)),
                            contentLength = 3,
                        )
                    },
                )
            val transfer =
                controller.createDownload(
                    totalBytes = 0,
                    destinationPath = destination,
                )

            val error =
                runCatching {
                    controller.downloadToFile(
                        uri = Uri.parse("http://example.test/partial.bin"),
                        transfer = transfer,
                        onProgress = { sentBytes, _ ->
                            if (sentBytes > 0) controller.cancel(transfer)
                        },
                    )
                }.exceptionOrNull()
            assertTrue(error is DownloadCancelledException)

            assertFalse(File(destination).exists())
            assertNull(transfer.downloadClient)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `download state patches cover started completed failed and removed`() {
        val controller = FileDownloadsController()
        val transfer =
            controller.createDownload(
                totalBytes = 100,
                destinationPath = "/tmp/report.pdf",
            )

        assertTrue(
            controller.canStartDownload(
                downloads = emptyMap(),
                downloadKey = "message_1:file",
            ),
        )

        var patch =
            controller.patchStartedDownload(
                downloads = emptyMap(),
                downloadKey = "message_1:file",
                transfer = transfer,
            )
        assertSame(transfer, patch.downloads["message_1:file"])
        assertFalse(
            controller.canStartDownload(
                downloads = patch.downloads,
                downloadKey = "message_1:file",
            ),
        )
        assertTrue(
            controller.shouldApplyDownloadProgress(
                downloads = patch.downloads,
                downloadKey = "message_1:file",
                transfer = transfer,
            ),
        )
        val progressPatch =
            controller.patchDownloadProgress(
                downloads = patch.downloads,
                downloadKey = "message_1:file",
                transfer = transfer,
            )
        assertNotNull(progressPatch)
        assertSame(patch.downloads, progressPatch!!.downloads)

        patch =
            controller.patchFailedDownload(
                downloads = patch.downloads,
                transfer = transfer,
                failure = "network failed",
            )
        assertTrue(transfer.failed)
        assertEquals("网络连接失败，请检查网络后重试", transfer.error)

        patch =
            controller.patchRemovedDownload(
                downloads = patch.downloads,
                downloadKey = "message_1:file",
            )
        assertTrue(patch.downloads.isEmpty())
    }

    @Test
    fun `download completion and cleanup guards reject stale transfers`() {
        val controller = FileDownloadsController()
        val active =
            controller.createDownload(
                totalBytes = 100,
                destinationPath = "/tmp/active.pdf",
            )
        val stale =
            controller.createDownload(
                totalBytes = 100,
                destinationPath = "/tmp/stale.pdf",
            )
        val downloads = mapOf("download" to active)

        assertTrue(
            controller.canCompleteDownload(
                downloads = downloads,
                downloadKey = "download",
                transfer = active,
            ),
        )
        assertFalse(
            controller.canCompleteDownload(
                downloads = downloads,
                downloadKey = "download",
                transfer = stale,
            ),
        )

        active.wroteDestination = true
        assertEquals("/tmp/active.pdf", controller.partialDownloadPathToDelete(active))
        assertNull(controller.partialDownloadPathToDelete(stale))

        active.cancelTransfer()
        assertFalse(
            controller.canCompleteDownload(
                downloads = downloads,
                downloadKey = "download",
                transfer = active,
            ),
        )
    }

    @Test
    fun `download controls pause resume and cancel only download transfers`() {
        val controller = FileDownloadsController()
        val download =
            controller.createDownload(
                totalBytes = 100,
                destinationPath = "/tmp/report.pdf",
            )
        val upload =
            FileTransferState.upload(
                controller = UploadTransferController(),
                totalBytes = 100,
            )
        val downloads = mapOf("download" to download, "upload" to upload)

        assertNotNull(
            controller.patchPausedDownload(
                downloads = downloads,
                downloadKey = "download",
            ),
        )
        assertTrue(download.paused)
        assertNull(
            controller.patchPausedDownload(
                downloads = downloads,
                downloadKey = "download",
            ),
        )

        assertNotNull(
            controller.patchResumedDownload(
                downloads = downloads,
                downloadKey = "download",
            ),
        )
        assertFalse(download.paused)
        assertNull(
            controller.patchResumedDownload(
                downloads = downloads,
                downloadKey = "download",
            ),
        )

        assertFalse(
            controller.pauseDownload(downloads = downloads, downloadKey = "upload"),
        )
        assertFalse(
            controller.cancelDownload(downloads = downloads, downloadKey = "upload"),
        )
        assertFalse(
            controller.cancelDownload(downloads = downloads, downloadKey = "missing"),
        )

        assertTrue(
            controller.cancelDownload(downloads = downloads, downloadKey = "download"),
        )
        assertTrue(download.cancelled)
        assertFalse(
            controller.cancelDownload(downloads = downloads, downloadKey = "download"),
        )
    }

    /** Dart's `Directory.systemTemp.createTemp(prefix)`. */
    private fun _createTemp(prefix: String): File =
        kotlin.io.path.createTempDirectory(gang.tempDir.toPath(), prefix).toFile()

    // Nested so test helpers of other files in this package don't clash on the JVM.
    private class _StreamClient(
        val chunks: List<ByteArray>,
        val contentLength: Long,
    ) : HttpClient {
        private var _closed = false

        override suspend fun send(request: BaseRequest): StreamedResponse {
            if (_closed) throw ClientException("closed", request.url)
            return StreamedResponse(ByteStream(chunks.asFlow()), 200, contentLength = contentLength)
        }

        override fun close() {
            _closed = true
        }
    }
}
