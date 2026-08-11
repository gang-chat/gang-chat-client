import 'dart:typed_data';

import '../protocol/api_client.dart';
import '../protocol/models.dart';
import 'file_transfer_state.dart';

typedef PendingMessageHandler = void Function(PendingMessage pending);
typedef PendingFileMessageHandler = void Function(PendingFileMessage pending);
typedef FileMessageProgressHandler =
    void Function(
      PendingFileMessage pending, {
      required int sentBytes,
      required int totalBytes,
    });
typedef FileMessageUploadedHandler =
    void Function(PendingFileMessage pending, MessageAttachment attachment);

bool canSendComposedMessage({
  required String body,
  required String type,
  required List<MessageAttachment> attachments,
}) {
  if (type == 'text') return body.trim().isNotEmpty;
  return attachments.isNotEmpty;
}

/// One server message in the deterministic send plan produced from the
/// composer. Rich music components remain standalone protocol messages; when
/// text is present it follows immediately as a second plain-text message.
class ComposerMessageSendPart {
  const ComposerMessageSendPart({
    required this.body,
    required this.type,
    required this.attachments,
  });

  final String body;
  final String type;
  final List<MessageAttachment> attachments;

  bool get isComponent => type == 'playlist' || type == 'music_track';
}

List<ComposerMessageSendPart> composerMessageSendPlan({
  required String body,
  Message? component,
}) {
  final normalizedBody = body.trimRight();
  final result = <ComposerMessageSendPart>[];
  if (component != null) {
    result.add(
      ComposerMessageSendPart(
        body: '',
        type: component.type,
        attachments: [
          MessageAttachment(
            type: component.type,
            sourceMessageId: component.id,
          ),
        ],
      ),
    );
  }
  if (normalizedBody.trim().isNotEmpty) {
    result.add(
      ComposerMessageSendPart(
        body: normalizedBody,
        type: 'text',
        attachments: const [],
      ),
    );
  }
  return result;
}

typedef ComposerMessagePartSender =
    Future<bool> Function(
      ComposerMessageSendPart part, {
      required bool hasFollowingPart,
    });

/// Runs a composed multi-message plan in order and stops on the first failure.
/// Already-successful parts are never replayed by this invocation.
Future<bool> sendComposerMessagePartsSequentially({
  required List<ComposerMessageSendPart> parts,
  required ComposerMessagePartSender send,
}) async {
  for (var index = 0; index < parts.length; index++) {
    final succeeded = await send(
      parts[index],
      hasFollowingPart: index < parts.length - 1,
    );
    if (!succeeded) return false;
  }
  return true;
}

class PendingMessage {
  const PendingMessage({required this.clientMessageId, required this.local});

  final String clientMessageId;
  final Message local;
}

class PendingFileMessage {
  const PendingFileMessage({
    required this.clientMessageId,
    required this.transfer,
    required this.localAttachment,
    required this.local,
  });

  final String clientMessageId;
  final FileTransferState transfer;
  final MessageAttachment localAttachment;
  final Message local;
}

class FileMessageStatePatch {
  const FileMessageStatePatch({
    required this.messages,
    required this.fileTransfers,
  });

  final List<Message> messages;
  final Map<String, FileTransferState> fileTransfers;
}

class MessageSendStatePatch {
  const MessageSendStatePatch({
    required this.messages,
    required this.sending,
    required this.error,
  });

  final List<Message> messages;
  final bool sending;
  final String? error;
}

class MessagesController {
  const MessagesController({this.api});

  final GangApi? api;

  GangApi get _client {
    final client = api;
    if (client == null) {
      throw StateError('MessagesController requires an authenticated API');
    }
    return client;
  }

  /// Mint a client-side id with the given [prefix]. Exposed so callers can tag
  /// staged composer attachments before any message exists.
  String mintClientId(String prefix) => newClientId(prefix);

  PendingMessage createPendingMessage({
    required String roomId,
    required UserSummary sender,
    required String body,
    String type = 'text',
    List<MessageAttachment> attachments = const [],
    List<Map<String, Object?>> mentions = const [],
    MessageQuote? quote,
    List<MessageQuote> quotes = const [],
  }) {
    final clientMessageId = newClientId('cmsg');
    return PendingMessage(
      clientMessageId: clientMessageId,
      local: Message.local(
        roomId: roomId,
        sender: sender,
        clientMessageId: clientMessageId,
        type: type,
        body: body,
        attachments: attachments,
        mentions: mentions,
        quote: quote,
        quotes: quotes,
      ),
    );
  }

  PendingFileMessage createPendingFileMessage({
    required String roomId,
    required UserSummary sender,
    required String filename,
    required int sizeBytes,
    required String mimeType,
  }) {
    final clientMessageId = newClientId('cmsg');
    final transfer = FileTransferState.upload(
      controller: UploadTransferController(),
      totalBytes: sizeBytes,
    );
    final localAttachment = fileAttachment(
      name: filename,
      asset: UploadedAsset(
        id: 'local_$clientMessageId',
        url: '',
        thumbnailUrl: null,
        mimeType: mimeType,
        filename: filename,
        sizeBytes: sizeBytes,
      ),
    );
    return PendingFileMessage(
      clientMessageId: clientMessageId,
      transfer: transfer,
      localAttachment: localAttachment,
      local: Message.local(
        roomId: roomId,
        sender: sender,
        clientMessageId: clientMessageId,
        body: filename,
        type: 'file',
        attachments: [localAttachment],
      ),
    );
  }

  PendingFileMessage createPendingVoiceMessage({
    required String roomId,
    required UserSummary sender,
    required String filename,
    required int sizeBytes,
    required String mimeType,
    required Duration duration,
    MessageQuote? quote,
    List<MessageQuote> quotes = const [],
  }) {
    final clientMessageId = newClientId('cmsg');
    final transfer = FileTransferState.upload(
      controller: UploadTransferController(),
      totalBytes: sizeBytes,
    );
    final localAttachment = fileAttachment(
      type: 'audio',
      name: filename,
      asset: UploadedAsset(
        id: 'local_$clientMessageId',
        url: '',
        thumbnailUrl: null,
        mimeType: mimeType,
        filename: filename,
        sizeBytes: sizeBytes,
      ),
      duration: duration,
    );
    return PendingFileMessage(
      clientMessageId: clientMessageId,
      transfer: transfer,
      localAttachment: localAttachment,
      local: Message.local(
        roomId: roomId,
        sender: sender,
        clientMessageId: clientMessageId,
        body: filename,
        type: 'audio',
        attachments: [localAttachment],
        quote: quote,
        quotes: quotes,
      ),
    );
  }

  MessageAttachment fileAttachment({
    String type = 'file',
    required String name,
    required UploadedAsset asset,
    Duration? duration,
  }) {
    final durationMs = duration == null || duration <= Duration.zero
        ? null
        : duration.inMilliseconds;
    return MessageAttachment(
      type: type,
      name: name,
      asset: asset,
      durationMs: durationMs,
    );
  }

  Future<UploadedAsset> uploadFileAsset({
    required Uint8List bytes,
    required String filename,
    UploadTransferController? controller,
    UploadProgressCallback? onProgress,
  }) {
    return _client.uploadFileAsset(
      bytes: bytes,
      filename: filename,
      controller: controller,
      onProgress: onProgress,
    );
  }

  Future<List<Message>> loadMessages(String roomId) async {
    final page = await _client.listMessages(roomId: roomId);
    return page.messages;
  }

  Future<MessagePage> loadMessageHistory({
    required String roomId,
    String query = '',
    String category = 'all',
    String? senderUserId,
    DateTime? startAt,
    DateTime? endAt,
    int limit = 50,
    String? before,
  }) {
    return _client.listMessageHistory(
      roomId: roomId,
      query: query,
      category: category,
      senderUserId: senderUserId,
      startAt: startAt,
      endAt: endAt,
      limit: limit,
      before: before,
    );
  }

  Future<int> hideMessageHistory({
    required String roomId,
    required Iterable<String> messageIds,
  }) async {
    final ids = messageIds.toSet().toList(growable: false);
    var deletedCount = 0;
    for (var start = 0; start < ids.length; start += 100) {
      final end = (start + 100).clamp(0, ids.length);
      deletedCount += await _client.hideMessageHistory(
        roomId: roomId,
        messageIds: ids.sublist(start, end),
      );
    }
    return deletedCount;
  }

  Future<List<Message>> loadMessagesUntil({
    required String roomId,
    required String messageId,
    int pageLimit = 100,
    int maxPages = 20,
  }) async {
    final messages = <Message>[];
    String? before;
    for (var pageIndex = 0; pageIndex < maxPages; pageIndex++) {
      final page = await _client.listMessages(
        roomId: roomId,
        limit: pageLimit,
        before: before,
      );
      messages.insertAll(0, page.messages);
      if (messages.any((message) => message.id == messageId)) {
        return messages;
      }
      if (!page.hasMore || page.nextBefore == null) return messages;
      before = page.nextBefore;
    }
    return messages;
  }

  Future<int> markRead({
    required String roomId,
    required String lastReadMessageId,
  }) {
    return _client.markRead(
      roomId: roomId,
      lastReadMessageId: lastReadMessageId,
    );
  }

  Future<MessageRecallResult> recallMessage({
    required String roomId,
    required String messageId,
    String? reason,
  }) {
    return _client.recallMessage(
      roomId: roomId,
      messageId: messageId,
      reason: reason,
    );
  }

  Future<Message> sendMessage({
    required String roomId,
    required String clientMessageId,
    required String body,
    String type = 'text',
    List<MessageAttachment> attachments = const [],
    List<Map<String, Object?>> mentions = const [],
    String? quoteMessageId,
    List<String> quoteMessageIds = const [],
  }) {
    return _client.sendMessage(
      roomId: roomId,
      clientMessageId: clientMessageId,
      body: body,
      type: type,
      attachments: attachments,
      mentions: mentions,
      quoteMessageId: quoteMessageId,
      quoteMessageIds: quoteMessageIds,
      idempotencyKey: newUuid(),
    );
  }

  Future<Message> sendComposedMessage({
    required String roomId,
    required UserSummary sender,
    required String body,
    String type = 'text',
    List<MessageAttachment> attachments = const [],
    List<Map<String, Object?>> mentions = const [],
    MessageQuote? quote,
    List<MessageQuote> quotes = const [],
    PendingMessageHandler? onPending,
  }) {
    final pending = createPendingMessage(
      roomId: roomId,
      sender: sender,
      body: body,
      type: type,
      attachments: attachments,
      mentions: mentions,
      quote: quote,
      quotes: quotes,
    );
    onPending?.call(pending);
    return sendMessage(
      roomId: roomId,
      clientMessageId: pending.clientMessageId,
      body: body,
      type: type,
      attachments: attachments,
      mentions: mentions,
      quoteMessageId: quote?.messageId,
      quoteMessageIds: quotes.map((quote) => quote.messageId).toList(),
    );
  }

  Future<Message> sendFileMessage({
    required String roomId,
    required UserSummary sender,
    required String filename,
    required int sizeBytes,
    required String mimeType,
    required Future<Uint8List> Function() readBytes,
    PendingFileMessageHandler? onPending,
    FileMessageProgressHandler? onProgress,
    FileMessageUploadedHandler? onUploaded,
  }) async {
    final pending = createPendingFileMessage(
      roomId: roomId,
      sender: sender,
      filename: filename,
      sizeBytes: sizeBytes,
      mimeType: mimeType,
    );
    onPending?.call(pending);

    final transfer = pending.transfer;
    final bytes = await readBytes();
    if (bytes.isEmpty) throw StateError('文件为空');
    if (transfer.cancelled) throw const UploadCancelledException();

    final asset = await uploadFileAsset(
      bytes: bytes,
      filename: filename,
      controller: transfer.controller,
      onProgress: onProgress == null
          ? null
          : ({required sentBytes, required totalBytes}) {
              onProgress(pending, sentBytes: sentBytes, totalBytes: totalBytes);
            },
    );
    if (transfer.cancelled) throw const UploadCancelledException();

    final attachment = fileAttachment(name: filename, asset: asset);
    onUploaded?.call(pending, attachment);

    return sendMessage(
      roomId: roomId,
      clientMessageId: pending.clientMessageId,
      body: filename,
      type: 'file',
      attachments: [attachment],
    );
  }

  Future<Message> sendVoiceMessage({
    required String roomId,
    required UserSummary sender,
    required String filename,
    required int sizeBytes,
    required String mimeType,
    required Duration duration,
    required Future<Uint8List> Function() readBytes,
    MessageQuote? quote,
    List<MessageQuote> quotes = const [],
    PendingFileMessageHandler? onPending,
    FileMessageProgressHandler? onProgress,
    FileMessageUploadedHandler? onUploaded,
  }) async {
    final pending = createPendingVoiceMessage(
      roomId: roomId,
      sender: sender,
      filename: filename,
      sizeBytes: sizeBytes,
      mimeType: mimeType,
      duration: duration,
      quote: quote,
      quotes: quotes,
    );
    onPending?.call(pending);

    final transfer = pending.transfer;
    final bytes = await readBytes();
    if (bytes.isEmpty) throw StateError('鏂囦欢涓虹┖');
    if (transfer.cancelled) throw const UploadCancelledException();

    final asset = await uploadFileAsset(
      bytes: bytes,
      filename: filename,
      controller: transfer.controller,
      onProgress: onProgress == null
          ? null
          : ({required sentBytes, required totalBytes}) {
              onProgress(pending, sentBytes: sentBytes, totalBytes: totalBytes);
            },
    );
    if (transfer.cancelled) throw const UploadCancelledException();

    final attachment = fileAttachment(
      type: 'audio',
      name: filename,
      asset: asset,
      duration: duration,
    );
    onUploaded?.call(pending, attachment);

    return sendMessage(
      roomId: roomId,
      clientMessageId: pending.clientMessageId,
      body: filename,
      type: 'audio',
      attachments: [attachment],
      quoteMessageId: quote?.messageId,
      quoteMessageIds: quotes.map((quote) => quote.messageId).toList(),
    );
  }

  List<Message> patchPendingMessage({
    required List<Message> messages,
    required PendingMessage pending,
  }) {
    return appendLocalMessage(messages, pending.local);
  }

  List<Message> patchSentMessage({
    required List<Message> messages,
    required Message sent,
  }) {
    return replaceByClientId(messages, sent);
  }

  List<Message> patchFailedMessage({
    required List<Message> messages,
    required String clientMessageId,
  }) {
    return markFailedByClientId(messages, clientMessageId);
  }

  MessageSendStatePatch patchMessageSendStarted({
    required List<Message> messages,
  }) {
    return MessageSendStatePatch(
      messages: messages,
      sending: true,
      error: null,
    );
  }

  MessageSendStatePatch patchMessageSendPending({
    required List<Message> messages,
    required PendingMessage pending,
    required String? error,
  }) {
    return MessageSendStatePatch(
      messages: patchPendingMessage(messages: messages, pending: pending),
      sending: true,
      error: error,
    );
  }

  MessageSendStatePatch patchMessageSendSucceeded({
    required List<Message> messages,
    required Message sent,
    required String? error,
  }) {
    return MessageSendStatePatch(
      messages: patchSentMessage(messages: messages, sent: sent),
      sending: true,
      error: error,
    );
  }

  MessageSendStatePatch patchMessageSendFailed({
    required List<Message> messages,
    required String? clientMessageId,
    required String? error,
  }) {
    return MessageSendStatePatch(
      messages: clientMessageId == null
          ? messages
          : patchFailedMessage(
              messages: messages,
              clientMessageId: clientMessageId,
            ),
      sending: true,
      error: error,
    );
  }

  MessageSendStatePatch patchMessageSendFinished({
    required List<Message> messages,
    required String? error,
  }) {
    return MessageSendStatePatch(
      messages: messages,
      sending: false,
      error: error,
    );
  }

  FileMessageStatePatch patchPendingFileMessage({
    required List<Message> messages,
    required Map<String, FileTransferState> fileTransfers,
    required PendingFileMessage pending,
  }) {
    return FileMessageStatePatch(
      messages: appendLocalMessage(messages, pending.local),
      fileTransfers: {
        ...fileTransfers,
        pending.clientMessageId: pending.transfer,
      },
    );
  }

  bool updateFileTransferProgress({
    required Map<String, FileTransferState> fileTransfers,
    required PendingFileMessage pending,
    required int sentBytes,
    required int totalBytes,
  }) {
    final current = fileTransfers[pending.clientMessageId];
    if (current == null || current.cancelled) return false;
    current.updateProgress(sentBytes: sentBytes, totalBytes: totalBytes);
    return true;
  }

  FileMessageStatePatch? patchFileTransferProgress({
    required List<Message> messages,
    required Map<String, FileTransferState> fileTransfers,
    required PendingFileMessage pending,
    required int sentBytes,
    required int totalBytes,
  }) {
    final changed = updateFileTransferProgress(
      fileTransfers: fileTransfers,
      pending: pending,
      sentBytes: sentBytes,
      totalBytes: totalBytes,
    );
    if (!changed) return null;
    return FileMessageStatePatch(
      messages: messages,
      fileTransfers: fileTransfers,
    );
  }

  bool pauseFileUpload({
    required Map<String, FileTransferState> fileTransfers,
    required String clientMessageId,
  }) {
    final transfer = fileTransfers[clientMessageId];
    if (transfer == null || transfer.isDownload) return false;
    return transfer.pauseTransfer();
  }

  FileMessageStatePatch? patchPausedFileUpload({
    required List<Message> messages,
    required Map<String, FileTransferState> fileTransfers,
    required String clientMessageId,
  }) {
    final changed = pauseFileUpload(
      fileTransfers: fileTransfers,
      clientMessageId: clientMessageId,
    );
    if (!changed) return null;
    return FileMessageStatePatch(
      messages: messages,
      fileTransfers: fileTransfers,
    );
  }

  bool resumeFileUpload({
    required Map<String, FileTransferState> fileTransfers,
    required String clientMessageId,
  }) {
    final transfer = fileTransfers[clientMessageId];
    if (transfer == null || transfer.isDownload) return false;
    return transfer.resumeTransfer();
  }

  FileMessageStatePatch? patchResumedFileUpload({
    required List<Message> messages,
    required Map<String, FileTransferState> fileTransfers,
    required String clientMessageId,
  }) {
    final changed = resumeFileUpload(
      fileTransfers: fileTransfers,
      clientMessageId: clientMessageId,
    );
    if (!changed) return null;
    return FileMessageStatePatch(
      messages: messages,
      fileTransfers: fileTransfers,
    );
  }

  bool cancelFileUpload({
    required Map<String, FileTransferState> fileTransfers,
    required String clientMessageId,
  }) {
    final transfer = fileTransfers[clientMessageId];
    if (transfer == null || transfer.isDownload) return false;
    return transfer.cancelTransfer();
  }

  FileMessageStatePatch patchUploadedFileMessage({
    required List<Message> messages,
    required Map<String, FileTransferState> fileTransfers,
    required PendingFileMessage pending,
    required MessageAttachment attachment,
  }) {
    if (pending.transfer.cancelled) {
      return FileMessageStatePatch(
        messages: messages,
        fileTransfers: fileTransfers,
      );
    }
    pending.transfer.markSendingMessage();
    return FileMessageStatePatch(
      messages: updateByClientId(
        messages,
        pending.clientMessageId,
        (message) => copyMessage(message, attachments: [attachment]),
      ),
      fileTransfers: fileTransfers,
    );
  }

  FileMessageStatePatch patchSentFileMessage({
    required List<Message> messages,
    required Map<String, FileTransferState> fileTransfers,
    required String clientMessageId,
    required Message sent,
  }) {
    final nextTransfers = Map<String, FileTransferState>.of(fileTransfers)
      ..remove(clientMessageId);
    return FileMessageStatePatch(
      messages: replaceByClientId(messages, sent),
      fileTransfers: nextTransfers,
    );
  }

  FileMessageStatePatch patchFailedFileMessage({
    required List<Message> messages,
    required Map<String, FileTransferState> fileTransfers,
    required String clientMessageId,
    required Object failure,
  }) {
    final transfer = fileTransfers[clientMessageId];
    transfer?.markFailed(failure);
    return FileMessageStatePatch(
      messages: markFailedByClientId(messages, clientMessageId),
      fileTransfers: fileTransfers,
    );
  }

  FileMessageStatePatch patchRemovedFileMessage({
    required List<Message> messages,
    required Map<String, FileTransferState> fileTransfers,
    required String clientMessageId,
  }) {
    final nextTransfers = Map<String, FileTransferState>.of(fileTransfers)
      ..remove(clientMessageId);
    return FileMessageStatePatch(
      messages: removeByClientId(messages, clientMessageId),
      fileTransfers: nextTransfers,
    );
  }

  List<Message> replaceByClientId(List<Message> messages, Message sent) {
    var replaced = false;
    final next = messages.map((message) {
      if (message.clientMessageId != sent.clientMessageId) return message;
      replaced = true;
      return sent;
    }).toList();
    if (replaced) return next;
    return [...next, sent];
  }

  List<Message> replaceByMessageId(List<Message> messages, Message updated) {
    var replaced = false;
    final next = messages.map((message) {
      if (message.id != updated.id) return message;
      replaced = true;
      return updated;
    }).toList();
    if (replaced) return next;
    return replaceByClientId(messages, updated);
  }

  List<Message> appendLocalMessage(List<Message> messages, Message message) {
    return [...messages, message];
  }

  List<Message> removeByClientId(
    List<Message> messages,
    String clientMessageId,
  ) {
    return messages
        .where((message) => message.clientMessageId != clientMessageId)
        .toList();
  }

  List<Message> markFailedByClientId(
    List<Message> messages,
    String clientMessageId,
  ) {
    return updateByClientId(
      messages,
      clientMessageId,
      (message) => message.markFailed(),
    );
  }

  List<Message> updateByClientId(
    List<Message> messages,
    String clientMessageId,
    Message Function(Message message) update,
  ) {
    return messages.map((message) {
      if (message.clientMessageId != clientMessageId) return message;
      return update(message);
    }).toList();
  }

  Message copyMessage(
    Message message, {
    List<MessageAttachment>? attachments,
    bool? pending,
    bool? failed,
  }) {
    return Message(
      id: message.id,
      roomId: message.roomId,
      sender: message.sender,
      clientMessageId: message.clientMessageId,
      type: message.type,
      body: message.body,
      createdAt: message.createdAt,
      attachments: attachments ?? message.attachments,
      mentions: message.mentions,
      quote: message.quote,
      quotes: message.quotes,
      isRecalled: message.isRecalled,
      recalledAt: message.recalledAt,
      recalledBy: message.recalledBy,
      isForceDeleted: message.isForceDeleted,
      forceDeletedAt: message.forceDeletedAt,
      forceDeletedBy: message.forceDeletedBy,
      pending: pending ?? message.pending,
      failed: failed ?? message.failed,
    );
  }
}
