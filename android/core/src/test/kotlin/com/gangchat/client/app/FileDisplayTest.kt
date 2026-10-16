package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.UploadTransferController
import com.gangchat.client.protocol.UploadedAsset
import com.gangchat.client.protocol.UserSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FileDisplayTest {
    @Test
    fun `basename removes folders query and fragment`() {
        assertEquals("report.pdf", basename("C:\\Users\\kai\\report.pdf?download=1#top"))
        assertEquals("file", basename("/tmp/"))
        assertEquals("image.png", basename("  image.png  "))
    }

    @Test
    fun `normalizedFilePaths trims empty and duplicate file paths`() {
        assertEquals(
            listOf("/tmp/a.png", "/tmp/b.png"),
            normalizedFilePaths(
                listOf(
                    " /tmp/a.png ",
                    "",
                    " /tmp/b.png",
                    "/tmp/a.png",
                    "   ",
                ),
            ),
        )
    }

    @Test
    fun `file interaction notices and download url parsing stay outside UI`() {
        assertEquals("无法读取剪贴板中的文件", clipboardFilesReadFailureMessage("denied"))
        assertEquals("无法打开文件选择器", filePickerOpenFailureMessage("blocked"))
        assertEquals("无法读取文件", fileReadFailureMessage("missing"))
        assertEquals("文件为空", fileEmptyMessage())
        assertEquals(
            "example.test",
            fileDownloadUri("https://example.test/file.txt")?.host,
        )
        assertNull(fileDownloadUri("://bad"))
        assertEquals("无法下载文件", fileDownloadUnavailableMessage())
        assertEquals("文件已下载", fileDownloadedNotice())
    }

    @Test
    fun `clipboard image upload filenames include timestamp and sequence`() {
        val timestamp = DateTime(2026, 6, 11, 17, 55, 1, 23)

        assertEquals(
            "clipboard-image-20260611-175501-023-001.png",
            clipboardImageUploadFilename(timestamp = timestamp, sequence = 1),
        )
        assertEquals(
            "clipboard-image-20260611-175501-023-002.jpg",
            clipboardImageUploadFilename(
                timestamp = timestamp,
                sequence = 2,
                mimeType = "image/jpeg; charset=binary",
            ),
        )
    }

    @Test
    fun `extension and mime type are inferred from normalized basename`() {
        assertEquals("jpeg", extensionOf("/tmp/photo.JPEG?token=1"))
        assertEquals("image/jpeg", mimeTypeFromFilename("/tmp/photo.JPEG?token=1"))
        assertEquals("application/octet-stream", mimeTypeFromFilename("archive.unknown"))
    }

    @Test
    fun `fileAttachmentTitle falls back from explicit name to asset fields`() {
        assertEquals(
            "explicit.txt",
            fileAttachmentTitle(
                MessageAttachment(
                    type = "file",
                    name = " explicit.txt ",
                    asset = _asset(filename = "asset.txt", url = "/ignored/url.txt"),
                ),
            ),
        )
        assertEquals(
            "asset-name.txt",
            fileAttachmentTitle(
                MessageAttachment(
                    type = "file",
                    asset = _asset(filename = " asset-name.txt ", url = "/ignored/url.txt"),
                ),
            ),
        )
        assertEquals(
            "final name.txt",
            fileAttachmentTitle(
                MessageAttachment(
                    type = "file",
                    asset =
                        _asset(
                            filename = null,
                            url = "/uploads/final%20name.txt?download=1",
                        ),
                ),
            ),
        )
    }

    @Test
    fun `fileAttachmentMeta combines mime type and formatted size`() {
        assertEquals(
            "application/pdf - 1.5 KB",
            fileAttachmentMeta(_asset(mimeType = "application/pdf", sizeBytes = 1536)),
        )
        assertEquals(
            "1.0 MB",
            fileAttachmentMeta(_asset(mimeType = "", sizeBytes = 1048576)),
        )
        assertEquals("", fileAttachmentMeta(null))
    }

    @Test
    fun `fileAttachmentPreviewPath uses image thumbnail or asset URL only`() {
        assertEquals(
            "/uploads/thumb.png",
            fileAttachmentPreviewPath(
                _asset(
                    url = "/uploads/image.png",
                    mimeType = "image/png",
                    thumbnailUrl = "/uploads/thumb.png",
                ),
            ),
        )
        assertEquals(
            "/uploads/image.png",
            fileAttachmentPreviewPath(
                _asset(url = "/uploads/image.png", mimeType = "image/png"),
            ),
        )
        assertNull(
            fileAttachmentPreviewPath(
                _asset(url = "/uploads/file.pdf", mimeType = "application/pdf"),
            ),
        )
    }

    @Test
    fun `isImageMimeType normalizes mime casing`() {
        assertTrue(isImageMimeType("IMAGE/PNG"))
        assertFalse(isImageMimeType("text/plain"))
        assertFalse(isImageMimeType(null))
    }

    @Test
    fun `fileAttachmentPreviewSize preserves image ratio within bounds`() {
        var size =
            fileAttachmentPreviewSize(
                _asset(mimeType = "image/png", width = 1600, height = 900),
            )
        assertEquals(320.0, size.width, 0.0)
        assertEquals(180.0, size.height, 0.0)

        size =
            fileAttachmentPreviewSize(
                _asset(mimeType = "image/png", width = 900, height = 1600),
            )
        assertEquals(135.0, size.width, 0.0)
        assertEquals(240.0, size.height, 0.0)

        size =
            fileAttachmentPreviewSize(
                _asset(mimeType = "image/png", width = 128, height = 96),
            )
        assertEquals(128.0, size.width, 0.0)
        assertEquals(96.0, size.height, 0.0)

        size = fileAttachmentPreviewSize(_asset(mimeType = "image/png"))
        assertEquals(220.0, size.width, 0.0)
        assertEquals(150.0, size.height, 0.0)
    }

    @Test
    fun `fileTransferLabel reflects state progress and speed`() {
        val transfer =
            FileTransferState.upload(
                controller = UploadTransferController(),
                totalBytes = 4096,
            ).apply {
                sentBytes = 2048
                bytesPerSecond = 2048.0
            }

        assertEquals("上传中 50% · 2.0 KB/s", fileTransferLabel(transfer))

        transfer.pauseTransfer()
        assertEquals("已暂停 50%", fileTransferLabel(transfer))

        transfer.resumeTransfer()
        transfer.markFailed("network failed")
        assertEquals("传输失败", fileTransferLabel(transfer))
    }

    @Test
    fun `fileTransferProgressState exposes progress label and error state`() {
        val transfer =
            FileTransferState.upload(
                controller = UploadTransferController(),
                totalBytes = 100,
            ).apply { sentBytes = 25 }

        var state = fileTransferProgressState(transfer)
        assertEquals(0.25, state.value)
        assertEquals("上传中 25%", state.label)
        assertFalse(state.failed)

        transfer.markSendingMessage()
        state = fileTransferProgressState(transfer)
        assertEquals(1.0, state.value)
        assertEquals("发送中", state.label)

        val failedTransfer =
            FileTransferState.upload(
                controller = UploadTransferController(),
                totalBytes = 100,
            ).apply { sentBytes = 25 }
        failedTransfer.markFailed("network failed")
        state = fileTransferProgressState(failedTransfer)
        assertEquals(0.25, state.value)
        assertEquals("网络连接失败，请检查网络后重试", state.label)
        assertTrue(state.failed)
    }

    @Test
    fun `fileDownloadKey is stable from client message and asset identity`() {
        val attachment =
            MessageAttachment(
                type = "file",
                name = "fallback.txt",
                asset = _asset(id = "asset_1"),
            )

        assertEquals("client_1:2:asset_1", fileDownloadKey(_message(), attachment, 2))
    }

    @Test
    fun `fileAttachmentTransferSlot binds upload only to first attachment`() {
        val message = _message()
        val first = _attachment(id = "first")
        val second = _attachment(id = "second")
        val upload =
            FileTransferState.upload(
                controller = UploadTransferController(),
                totalBytes = 100,
            )
        val download =
            FileTransferState.download(
                controller = UploadTransferController(),
                totalBytes = 200,
                destinationPath = "/tmp/second.txt",
            )
        val secondKey = fileDownloadKey(message, second, 1)

        val firstSlot =
            fileAttachmentTransferSlot(
                message = message,
                attachment = first,
                index = 0,
                uploadTransfer = upload,
                downloads = mapOf(secondKey to download),
            )
        assertSame(upload, firstSlot.transfer)
        assertTrue(firstSlot.usesUploadTransfer)

        val secondSlot =
            fileAttachmentTransferSlot(
                message = message,
                attachment = second,
                index = 1,
                uploadTransfer = upload,
                downloads = mapOf(secondKey to download),
            )
        assertSame(download, secondSlot.transfer)
        assertFalse(secondSlot.usesUploadTransfer)
        assertEquals(secondKey, secondSlot.downloadKey)
    }

    @Test
    fun `fileAttachmentInteractionState allows download only without transfer`() {
        assertTrue(
            fileAttachmentInteractionState(
                title = "report.pdf",
                url = "/uploads/report.pdf",
                transfer = null,
            ).canDownload,
        )
        assertEquals(
            "report.pdf",
            fileAttachmentInteractionState(
                title = "report.pdf",
                url = null,
                transfer = null,
            ).tooltip,
        )
        assertFalse(
            fileAttachmentInteractionState(
                title = "report.pdf",
                url = "/uploads/report.pdf",
                transfer =
                    FileTransferState.upload(
                        controller = UploadTransferController(),
                        totalBytes = 100,
                    ),
            ).canDownload,
        )
    }

    @Test
    fun `fileAttachmentTrailingState describes transfer actions`() {
        val upload =
            FileTransferState.upload(
                controller = UploadTransferController(),
                totalBytes = 100,
            )
        var state =
            fileAttachmentTrailingState(
                transfer = upload,
                canDownload = false,
            )
        assertEquals(FileAttachmentTrailingKind.activeTransfer, state.kind)
        assertEquals("暂停上传", state.pauseResumeTooltip)
        assertFalse(state.pauseResumeIsResume)
        assertEquals("取消上传", state.cancelTooltip)

        upload.pauseTransfer()
        state = fileAttachmentTrailingState(transfer = upload, canDownload = false)
        assertEquals("继续上传", state.pauseResumeTooltip)
        assertTrue(state.pauseResumeIsResume)

        upload.resumeTransfer()
        upload.markSendingMessage()
        assertEquals(
            FileAttachmentTrailingKind.sending,
            fileAttachmentTrailingState(transfer = upload, canDownload = false).kind,
        )
    }

    @Test
    fun `fileAttachmentTrailingState separates failed and idle states`() {
        val upload =
            FileTransferState.upload(
                controller = UploadTransferController(),
                totalBytes = 100,
            ).apply { markFailed("upload failed") }
        val download =
            FileTransferState.download(
                controller = UploadTransferController(),
                totalBytes = 100,
                destinationPath = "/tmp/file.txt",
            ).apply { markFailed("download failed") }

        var state =
            fileAttachmentTrailingState(
                transfer = upload,
                canDownload = false,
            )
        assertEquals(FileAttachmentTrailingKind.failed, state.kind)
        assertFalse(state.showDismiss)

        state = fileAttachmentTrailingState(transfer = download, canDownload = false)
        assertEquals(FileAttachmentTrailingKind.failed, state.kind)
        assertTrue(state.showDismiss)

        assertEquals(
            FileAttachmentTrailingKind.placeholder,
            fileAttachmentTrailingState(transfer = null, canDownload = false).kind,
        )
        assertEquals(
            FileAttachmentTrailingKind.download,
            fileAttachmentTrailingState(transfer = null, canDownload = true).kind,
        )
    }

    @Test
    fun `file transfer action guards follow active paused and failed state`() {
        val transfer =
            FileTransferState.upload(
                controller = UploadTransferController(),
                totalBytes = 100,
            )

        assertTrue(canPauseFileTransfer(transfer))
        assertFalse(canResumeFileTransfer(transfer))
        assertTrue(canCancelActiveFileTransfer(transfer))

        transfer.pauseTransfer()
        assertFalse(canPauseFileTransfer(transfer))
        assertTrue(canResumeFileTransfer(transfer))
        assertTrue(canCancelActiveFileTransfer(transfer))

        transfer.resumeTransfer()
        transfer.markFailed("network failed")
        assertFalse(canPauseFileTransfer(transfer))
        assertFalse(canResumeFileTransfer(transfer))
        assertFalse(canCancelActiveFileTransfer(transfer))

        val sending =
            FileTransferState.upload(
                controller = UploadTransferController(),
                totalBytes = 100,
            ).apply { markSendingMessage() }
        assertFalse(canCancelActiveFileTransfer(sending))

        assertFalse(canPauseFileTransfer(null))
        assertFalse(canResumeFileTransfer(null))
        assertFalse(canCancelActiveFileTransfer(null))
    }

    private fun _asset(
        id: String = "asset",
        url: String = "/uploads/file.txt",
        thumbnailUrl: String? = null,
        filename: String? = "file.txt",
        mimeType: String = "text/plain",
        sizeBytes: Long? = null,
        width: Int? = null,
        height: Int? = null,
    ): UploadedAsset {
        return UploadedAsset(
            id = id,
            url = url,
            thumbnailUrl = thumbnailUrl,
            mimeType = mimeType,
            filename = filename,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
        )
    }

    private fun _message(): Message {
        return Message(
            id = "message_1",
            roomId = "room_1",
            sender =
                UserSummary(
                    id = "user_1",
                    username = "logan",
                    displayName = "Logan",
                    avatarUrl = null,
                    defaultAvatarKey = "blue-3",
                ),
            clientMessageId = "client_1",
            body = "",
            createdAt = DateTime.utc(2026, 6, 4),
        )
    }

    private fun _attachment(id: String): MessageAttachment {
        return MessageAttachment(
            type = "file",
            name = "$id.txt",
            asset = _asset(id = id),
        )
    }
}
