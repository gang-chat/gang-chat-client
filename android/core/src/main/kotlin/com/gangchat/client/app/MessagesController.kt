package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.MessagePage
import com.gangchat.client.protocol.MessageQuote
import com.gangchat.client.protocol.MessageRecallResult
import com.gangchat.client.protocol.UploadCancelledException
import com.gangchat.client.protocol.UploadProgressCallback
import com.gangchat.client.protocol.UploadTransferController
import com.gangchat.client.protocol.UploadedAsset
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.protocol.newClientId
import com.gangchat.client.protocol.newUuid
import kotlin.time.Duration

typealias PendingMessageHandler = (pending: PendingMessage) -> Unit

typealias PendingFileMessageHandler = (pending: PendingFileMessage) -> Unit

/**
 * Dart's `typedef FileMessageProgressHandler = void Function(PendingFileMessage
 * pending, {required int sentBytes, required int totalBytes})`; invoke with
 * named arguments.
 */
fun interface FileMessageProgressHandler {
    operator fun invoke(pending: PendingFileMessage, sentBytes: Long, totalBytes: Long)
}

typealias FileMessageUploadedHandler = (pending: PendingFileMessage, attachment: MessageAttachment) -> Unit

fun canSendComposedMessage(
    body: String,
    type: String,
    attachments: List<MessageAttachment>,
): Boolean {
    if (type == "text") return body.trim().isNotEmpty()
    return attachments.isNotEmpty()
}

/**
 * One server message in the deterministic send plan produced from the
 * composer. Rich music components remain standalone protocol messages; when
 * text is present it follows immediately as a second plain-text message.
 */
class ComposerMessageSendPart(
    val body: String,
    val type: String,
    val attachments: List<MessageAttachment>,
) {
    val isComponent: Boolean get() = type == "playlist" || type == "music_track"
}

fun composerMessageSendPlan(
    body: String,
    component: Message? = null,
): List<ComposerMessageSendPart> {
    val normalizedBody = body.trimEnd()
    val result = mutableListOf<ComposerMessageSendPart>()
    if (component != null) {
        result.add(
            ComposerMessageSendPart(
                body = "",
                type = component.type,
                attachments =
                    listOf(
                        MessageAttachment(
                            type = component.type,
                            sourceMessageId = component.id,
                        ),
                    ),
            ),
        )
    }
    if (normalizedBody.trim().isNotEmpty()) {
        result.add(
            ComposerMessageSendPart(
                body = normalizedBody,
                type = "text",
                attachments = emptyList(),
            ),
        )
    }
    return result
}

/**
 * Dart's `typedef ComposerMessagePartSender = Future<bool> Function(
 * ComposerMessageSendPart part, {required bool hasFollowingPart})`.
 */
typealias ComposerMessagePartSender =
    suspend (part: ComposerMessageSendPart, hasFollowingPart: Boolean) -> Boolean

/**
 * Runs a composed multi-message plan in order and stops on the first failure.
 * Already-successful parts are never replayed by this invocation.
 */
suspend fun sendComposerMessagePartsSequentially(
    parts: List<ComposerMessageSendPart>,
    send: ComposerMessagePartSender,
): Boolean {
    for (index in parts.indices) {
        val succeeded = send(parts[index], index < parts.size - 1)
        if (!succeeded) return false
    }
    return true
}

class PendingMessage(val clientMessageId: String, val local: Message)

class PendingFileMessage(
    val clientMessageId: String,
    val transfer: FileTransferState,
    val localAttachment: MessageAttachment,
    val local: Message,
)

class FileMessageStatePatch(
    val messages: List<Message>,
    val fileTransfers: Map<String, FileTransferState>,
)

class MessageSendStatePatch(
    val messages: List<Message>,
    val sending: Boolean,
    val error: String?,
)

/**
 * Message list, send and upload sequencing. Open (like the Dart class) so
 * tests and callers can substitute single operations, e.g. [markRead].
 */
open class MessagesController(val api: GangApi? = null) {
    private val _client: GangApi
        get() {
            return api ?: throw IllegalStateException("MessagesController requires an authenticated API")
        }

    /**
     * Mint a client-side id with the given [prefix]. Exposed so callers can tag
     * staged composer attachments before any message exists.
     */
    open fun mintClientId(prefix: String): String = newClientId(prefix)

    open fun createPendingMessage(
        roomId: String,
        sender: UserSummary,
        body: String,
        type: String = "text",
        attachments: List<MessageAttachment> = emptyList(),
        mentions: List<JsonMap> = emptyList(),
        quote: MessageQuote? = null,
        quotes: List<MessageQuote> = emptyList(),
    ): PendingMessage {
        val clientMessageId = newClientId("cmsg")
        return PendingMessage(
            clientMessageId = clientMessageId,
            local =
                Message.local(
                    roomId = roomId,
                    sender = sender,
                    clientMessageId = clientMessageId,
                    type = type,
                    body = body,
                    attachments = attachments,
                    mentions = mentions,
                    quote = quote,
                    quotes = quotes,
                ),
        )
    }

    open fun createPendingFileMessage(
        roomId: String,
        sender: UserSummary,
        filename: String,
        sizeBytes: Long,
        mimeType: String,
    ): PendingFileMessage {
        val clientMessageId = newClientId("cmsg")
        val transfer =
            FileTransferState.upload(
                controller = UploadTransferController(),
                totalBytes = sizeBytes,
            )
        val localAttachment =
            fileAttachment(
                name = filename,
                asset =
                    UploadedAsset(
                        id = "local_$clientMessageId",
                        url = "",
                        thumbnailUrl = null,
                        mimeType = mimeType,
                        filename = filename,
                        sizeBytes = sizeBytes,
                    ),
            )
        return PendingFileMessage(
            clientMessageId = clientMessageId,
            transfer = transfer,
            localAttachment = localAttachment,
            local =
                Message.local(
                    roomId = roomId,
                    sender = sender,
                    clientMessageId = clientMessageId,
                    body = filename,
                    type = "file",
                    attachments = listOf(localAttachment),
                ),
        )
    }

    open fun createPendingVoiceMessage(
        roomId: String,
        sender: UserSummary,
        filename: String,
        sizeBytes: Long,
        mimeType: String,
        duration: Duration,
        quote: MessageQuote? = null,
        quotes: List<MessageQuote> = emptyList(),
    ): PendingFileMessage {
        val clientMessageId = newClientId("cmsg")
        val transfer =
            FileTransferState.upload(
                controller = UploadTransferController(),
                totalBytes = sizeBytes,
            )
        val localAttachment =
            fileAttachment(
                type = "audio",
                name = filename,
                asset =
                    UploadedAsset(
                        id = "local_$clientMessageId",
                        url = "",
                        thumbnailUrl = null,
                        mimeType = mimeType,
                        filename = filename,
                        sizeBytes = sizeBytes,
                    ),
                duration = duration,
            )
        return PendingFileMessage(
            clientMessageId = clientMessageId,
            transfer = transfer,
            localAttachment = localAttachment,
            local =
                Message.local(
                    roomId = roomId,
                    sender = sender,
                    clientMessageId = clientMessageId,
                    body = filename,
                    type = "audio",
                    attachments = listOf(localAttachment),
                    quote = quote,
                    quotes = quotes,
                ),
        )
    }

    open fun fileAttachment(
        type: String = "file",
        name: String,
        asset: UploadedAsset,
        duration: Duration? = null,
    ): MessageAttachment {
        val durationMs =
            if (duration == null || duration <= Duration.ZERO) {
                null
            } else {
                duration.inWholeMilliseconds.toInt()
            }
        return MessageAttachment(
            type = type,
            name = name,
            asset = asset,
            durationMs = durationMs,
        )
    }

    open suspend fun uploadFileAsset(
        bytes: ByteArray,
        filename: String,
        controller: UploadTransferController? = null,
        onProgress: UploadProgressCallback? = null,
    ): UploadedAsset {
        return _client.uploadFileAsset(
            bytes = bytes,
            filename = filename,
            controller = controller,
            onProgress = onProgress,
        )
    }

    open suspend fun loadMessages(roomId: String): List<Message> {
        val page = _client.listMessages(roomId = roomId)
        return page.messages
    }

    open suspend fun loadMessageHistory(
        roomId: String,
        query: String = "",
        category: String = "all",
        senderUserId: String? = null,
        startAt: DateTime? = null,
        endAt: DateTime? = null,
        limit: Int = 50,
        before: String? = null,
    ): MessagePage {
        return _client.listMessageHistory(
            roomId = roomId,
            query = query,
            category = category,
            senderUserId = senderUserId,
            startAt = startAt,
            endAt = endAt,
            limit = limit,
            before = before,
        )
    }

    open suspend fun hideMessageHistory(
        roomId: String,
        messageIds: Iterable<String>,
    ): Int {
        val ids = messageIds.toSet().toList()
        var deletedCount = 0
        var start = 0
        while (start < ids.size) {
            val end = (start + 100).coerceIn(0, ids.size)
            deletedCount +=
                _client.hideMessageHistory(
                    roomId = roomId,
                    messageIds = ids.subList(start, end),
                )
            start += 100
        }
        return deletedCount
    }

    open suspend fun loadMessagesUntil(
        roomId: String,
        messageId: String,
        pageLimit: Int = 100,
        maxPages: Int = 20,
    ): List<Message> {
        val messages = mutableListOf<Message>()
        var before: String? = null
        for (pageIndex in 0 until maxPages) {
            val page =
                _client.listMessages(
                    roomId = roomId,
                    limit = pageLimit,
                    before = before,
                )
            messages.addAll(0, page.messages)
            if (messages.any { message -> message.id == messageId }) {
                return messages
            }
            if (!page.hasMore || page.nextBefore == null) return messages
            before = page.nextBefore
        }
        return messages
    }

    open suspend fun markRead(
        roomId: String,
        lastReadMessageId: String,
    ): Int {
        return _client.markRead(
            roomId = roomId,
            lastReadMessageId = lastReadMessageId,
        )
    }

    open suspend fun recallMessage(
        roomId: String,
        messageId: String,
        reason: String? = null,
    ): MessageRecallResult {
        return _client.recallMessage(
            roomId = roomId,
            messageId = messageId,
            reason = reason,
        )
    }

    open suspend fun sendMessage(
        roomId: String,
        clientMessageId: String,
        body: String,
        type: String = "text",
        attachments: List<MessageAttachment> = emptyList(),
        mentions: List<JsonMap> = emptyList(),
        quoteMessageId: String? = null,
        quoteMessageIds: List<String> = emptyList(),
    ): Message {
        return _client.sendMessage(
            roomId = roomId,
            clientMessageId = clientMessageId,
            body = body,
            type = type,
            attachments = attachments,
            mentions = mentions,
            quoteMessageId = quoteMessageId,
            quoteMessageIds = quoteMessageIds,
            idempotencyKey = newUuid(),
        )
    }

    open suspend fun sendComposedMessage(
        roomId: String,
        sender: UserSummary,
        body: String,
        type: String = "text",
        attachments: List<MessageAttachment> = emptyList(),
        mentions: List<JsonMap> = emptyList(),
        quote: MessageQuote? = null,
        quotes: List<MessageQuote> = emptyList(),
        onPending: PendingMessageHandler? = null,
    ): Message {
        val pending =
            createPendingMessage(
                roomId = roomId,
                sender = sender,
                body = body,
                type = type,
                attachments = attachments,
                mentions = mentions,
                quote = quote,
                quotes = quotes,
            )
        onPending?.invoke(pending)
        return sendMessage(
            roomId = roomId,
            clientMessageId = pending.clientMessageId,
            body = body,
            type = type,
            attachments = attachments,
            mentions = mentions,
            quoteMessageId = quote?.messageId,
            quoteMessageIds = quotes.map { quote -> quote.messageId },
        )
    }

    open suspend fun sendFileMessage(
        roomId: String,
        sender: UserSummary,
        filename: String,
        sizeBytes: Long,
        mimeType: String,
        readBytes: suspend () -> ByteArray,
        onPending: PendingFileMessageHandler? = null,
        onProgress: FileMessageProgressHandler? = null,
        onUploaded: FileMessageUploadedHandler? = null,
    ): Message {
        val pending =
            createPendingFileMessage(
                roomId = roomId,
                sender = sender,
                filename = filename,
                sizeBytes = sizeBytes,
                mimeType = mimeType,
            )
        onPending?.invoke(pending)

        val transfer = pending.transfer
        val bytes = readBytes()
        if (bytes.isEmpty()) throw IllegalStateException("文件为空")
        if (transfer.cancelled) throw UploadCancelledException()

        val asset =
            uploadFileAsset(
                bytes = bytes,
                filename = filename,
                controller = transfer.controller,
                onProgress =
                    if (onProgress == null) {
                        null
                    } else {
                        UploadProgressCallback { sentBytes, totalBytes ->
                            onProgress(pending, sentBytes = sentBytes, totalBytes = totalBytes)
                        }
                    },
            )
        if (transfer.cancelled) throw UploadCancelledException()

        val attachment = fileAttachment(name = filename, asset = asset)
        onUploaded?.invoke(pending, attachment)

        return sendMessage(
            roomId = roomId,
            clientMessageId = pending.clientMessageId,
            body = filename,
            type = "file",
            attachments = listOf(attachment),
        )
    }

    open suspend fun sendVoiceMessage(
        roomId: String,
        sender: UserSummary,
        filename: String,
        sizeBytes: Long,
        mimeType: String,
        duration: Duration,
        readBytes: suspend () -> ByteArray,
        quote: MessageQuote? = null,
        quotes: List<MessageQuote> = emptyList(),
        onPending: PendingFileMessageHandler? = null,
        onProgress: FileMessageProgressHandler? = null,
        onUploaded: FileMessageUploadedHandler? = null,
    ): Message {
        val pending =
            createPendingVoiceMessage(
                roomId = roomId,
                sender = sender,
                filename = filename,
                sizeBytes = sizeBytes,
                mimeType = mimeType,
                duration = duration,
                quote = quote,
                quotes = quotes,
            )
        onPending?.invoke(pending)

        val transfer = pending.transfer
        val bytes = readBytes()
        // PORT: the Dart source has this message as mojibake (the GBK reading
        // of the UTF-8 bytes of "文件为空"); kept verbatim, like Dart.
        if (bytes.isEmpty()) throw IllegalStateException("鏂囦欢涓虹┖")
        if (transfer.cancelled) throw UploadCancelledException()

        val asset =
            uploadFileAsset(
                bytes = bytes,
                filename = filename,
                controller = transfer.controller,
                onProgress =
                    if (onProgress == null) {
                        null
                    } else {
                        UploadProgressCallback { sentBytes, totalBytes ->
                            onProgress(pending, sentBytes = sentBytes, totalBytes = totalBytes)
                        }
                    },
            )
        if (transfer.cancelled) throw UploadCancelledException()

        val attachment =
            fileAttachment(
                type = "audio",
                name = filename,
                asset = asset,
                duration = duration,
            )
        onUploaded?.invoke(pending, attachment)

        return sendMessage(
            roomId = roomId,
            clientMessageId = pending.clientMessageId,
            body = filename,
            type = "audio",
            attachments = listOf(attachment),
            quoteMessageId = quote?.messageId,
            quoteMessageIds = quotes.map { quote -> quote.messageId },
        )
    }

    open fun patchPendingMessage(
        messages: List<Message>,
        pending: PendingMessage,
    ): List<Message> {
        return appendLocalMessage(messages, pending.local)
    }

    open fun patchSentMessage(
        messages: List<Message>,
        sent: Message,
    ): List<Message> {
        return replaceByClientId(messages, sent)
    }

    open fun patchFailedMessage(
        messages: List<Message>,
        clientMessageId: String,
    ): List<Message> {
        return markFailedByClientId(messages, clientMessageId)
    }

    open fun patchMessageSendStarted(messages: List<Message>): MessageSendStatePatch {
        return MessageSendStatePatch(
            messages = messages,
            sending = true,
            error = null,
        )
    }

    open fun patchMessageSendPending(
        messages: List<Message>,
        pending: PendingMessage,
        error: String?,
    ): MessageSendStatePatch {
        return MessageSendStatePatch(
            messages = patchPendingMessage(messages = messages, pending = pending),
            sending = true,
            error = error,
        )
    }

    open fun patchMessageSendSucceeded(
        messages: List<Message>,
        sent: Message,
        error: String?,
    ): MessageSendStatePatch {
        return MessageSendStatePatch(
            messages = patchSentMessage(messages = messages, sent = sent),
            sending = true,
            error = error,
        )
    }

    open fun patchMessageSendFailed(
        messages: List<Message>,
        clientMessageId: String?,
        error: String?,
    ): MessageSendStatePatch {
        return MessageSendStatePatch(
            messages =
                if (clientMessageId == null) {
                    messages
                } else {
                    patchFailedMessage(
                        messages = messages,
                        clientMessageId = clientMessageId,
                    )
                },
            sending = true,
            error = error,
        )
    }

    open fun patchMessageSendFinished(
        messages: List<Message>,
        error: String?,
    ): MessageSendStatePatch {
        return MessageSendStatePatch(
            messages = messages,
            sending = false,
            error = error,
        )
    }

    open fun patchPendingFileMessage(
        messages: List<Message>,
        fileTransfers: Map<String, FileTransferState>,
        pending: PendingFileMessage,
    ): FileMessageStatePatch {
        return FileMessageStatePatch(
            messages = appendLocalMessage(messages, pending.local),
            fileTransfers =
                LinkedHashMap(fileTransfers).apply {
                    put(pending.clientMessageId, pending.transfer)
                },
        )
    }

    open fun updateFileTransferProgress(
        fileTransfers: Map<String, FileTransferState>,
        pending: PendingFileMessage,
        sentBytes: Long,
        totalBytes: Long,
    ): Boolean {
        val current = fileTransfers[pending.clientMessageId]
        if (current == null || current.cancelled) return false
        current.updateProgress(sentBytes = sentBytes, totalBytes = totalBytes)
        return true
    }

    open fun patchFileTransferProgress(
        messages: List<Message>,
        fileTransfers: Map<String, FileTransferState>,
        pending: PendingFileMessage,
        sentBytes: Long,
        totalBytes: Long,
    ): FileMessageStatePatch? {
        val changed =
            updateFileTransferProgress(
                fileTransfers = fileTransfers,
                pending = pending,
                sentBytes = sentBytes,
                totalBytes = totalBytes,
            )
        if (!changed) return null
        return FileMessageStatePatch(
            messages = messages,
            fileTransfers = fileTransfers,
        )
    }

    open fun pauseFileUpload(
        fileTransfers: Map<String, FileTransferState>,
        clientMessageId: String,
    ): Boolean {
        val transfer = fileTransfers[clientMessageId]
        if (transfer == null || transfer.isDownload) return false
        return transfer.pauseTransfer()
    }

    open fun patchPausedFileUpload(
        messages: List<Message>,
        fileTransfers: Map<String, FileTransferState>,
        clientMessageId: String,
    ): FileMessageStatePatch? {
        val changed =
            pauseFileUpload(
                fileTransfers = fileTransfers,
                clientMessageId = clientMessageId,
            )
        if (!changed) return null
        return FileMessageStatePatch(
            messages = messages,
            fileTransfers = fileTransfers,
        )
    }

    open fun resumeFileUpload(
        fileTransfers: Map<String, FileTransferState>,
        clientMessageId: String,
    ): Boolean {
        val transfer = fileTransfers[clientMessageId]
        if (transfer == null || transfer.isDownload) return false
        return transfer.resumeTransfer()
    }

    open fun patchResumedFileUpload(
        messages: List<Message>,
        fileTransfers: Map<String, FileTransferState>,
        clientMessageId: String,
    ): FileMessageStatePatch? {
        val changed =
            resumeFileUpload(
                fileTransfers = fileTransfers,
                clientMessageId = clientMessageId,
            )
        if (!changed) return null
        return FileMessageStatePatch(
            messages = messages,
            fileTransfers = fileTransfers,
        )
    }

    open fun cancelFileUpload(
        fileTransfers: Map<String, FileTransferState>,
        clientMessageId: String,
    ): Boolean {
        val transfer = fileTransfers[clientMessageId]
        if (transfer == null || transfer.isDownload) return false
        return transfer.cancelTransfer()
    }

    open fun patchUploadedFileMessage(
        messages: List<Message>,
        fileTransfers: Map<String, FileTransferState>,
        pending: PendingFileMessage,
        attachment: MessageAttachment,
    ): FileMessageStatePatch {
        if (pending.transfer.cancelled) {
            return FileMessageStatePatch(
                messages = messages,
                fileTransfers = fileTransfers,
            )
        }
        pending.transfer.markSendingMessage()
        return FileMessageStatePatch(
            messages =
                updateByClientId(
                    messages,
                    pending.clientMessageId,
                ) { message -> copyMessage(message, attachments = listOf(attachment)) },
            fileTransfers = fileTransfers,
        )
    }

    open fun patchSentFileMessage(
        messages: List<Message>,
        fileTransfers: Map<String, FileTransferState>,
        clientMessageId: String,
        sent: Message,
    ): FileMessageStatePatch {
        val nextTransfers = LinkedHashMap(fileTransfers).apply { remove(clientMessageId) }
        return FileMessageStatePatch(
            messages = replaceByClientId(messages, sent),
            fileTransfers = nextTransfers,
        )
    }

    open fun patchFailedFileMessage(
        messages: List<Message>,
        fileTransfers: Map<String, FileTransferState>,
        clientMessageId: String,
        failure: Any,
    ): FileMessageStatePatch {
        val transfer = fileTransfers[clientMessageId]
        transfer?.markFailed(failure)
        return FileMessageStatePatch(
            messages = markFailedByClientId(messages, clientMessageId),
            fileTransfers = fileTransfers,
        )
    }

    open fun patchRemovedFileMessage(
        messages: List<Message>,
        fileTransfers: Map<String, FileTransferState>,
        clientMessageId: String,
    ): FileMessageStatePatch {
        val nextTransfers = LinkedHashMap(fileTransfers).apply { remove(clientMessageId) }
        return FileMessageStatePatch(
            messages = removeByClientId(messages, clientMessageId),
            fileTransfers = nextTransfers,
        )
    }

    open fun replaceByClientId(messages: List<Message>, sent: Message): List<Message> {
        var replaced = false
        val next =
            messages.map { message ->
                if (message.clientMessageId != sent.clientMessageId) return@map message
                replaced = true
                sent
            }
        if (replaced) return next
        return next + sent
    }

    open fun replaceByMessageId(messages: List<Message>, updated: Message): List<Message> {
        var replaced = false
        val next =
            messages.map { message ->
                if (message.id != updated.id) return@map message
                replaced = true
                updated
            }
        if (replaced) return next
        return replaceByClientId(messages, updated)
    }

    open fun appendLocalMessage(messages: List<Message>, message: Message): List<Message> {
        return messages + message
    }

    open fun removeByClientId(
        messages: List<Message>,
        clientMessageId: String,
    ): List<Message> {
        return messages.filter { message -> message.clientMessageId != clientMessageId }
    }

    open fun markFailedByClientId(
        messages: List<Message>,
        clientMessageId: String,
    ): List<Message> {
        return updateByClientId(
            messages,
            clientMessageId,
        ) { message -> message.markFailed() }
    }

    open fun updateByClientId(
        messages: List<Message>,
        clientMessageId: String,
        update: (message: Message) -> Message,
    ): List<Message> {
        return messages.map { message ->
            if (message.clientMessageId != clientMessageId) return@map message
            update(message)
        }
    }

    open fun copyMessage(
        message: Message,
        attachments: List<MessageAttachment>? = null,
        pending: Boolean? = null,
        failed: Boolean? = null,
    ): Message {
        return Message(
            id = message.id,
            roomId = message.roomId,
            sender = message.sender,
            clientMessageId = message.clientMessageId,
            type = message.type,
            body = message.body,
            createdAt = message.createdAt,
            attachments = attachments ?: message.attachments,
            mentions = message.mentions,
            quote = message.quote,
            quotes = message.quotes,
            isRecalled = message.isRecalled,
            recalledAt = message.recalledAt,
            recalledBy = message.recalledBy,
            isForceDeleted = message.isForceDeleted,
            forceDeletedAt = message.forceDeletedAt,
            forceDeletedBy = message.forceDeletedBy,
            pending = pending ?: message.pending,
            failed = failed ?: message.failed,
        )
    }
}
