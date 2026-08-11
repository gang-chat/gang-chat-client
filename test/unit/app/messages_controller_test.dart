import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:client/src/app/file_transfer_state.dart';
import 'package:client/src/app/messages_controller.dart';
import 'package:client/src/protocol/api_client.dart';
import 'package:client/src/protocol/models.dart';

void main() {
  group('composerMessageSendPlan', () {
    test('keeps a normal text message as one message', () {
      final plan = composerMessageSendPlan(body: 'hello   ');

      expect(plan, hasLength(1));
      expect(plan.single.type, 'text');
      expect(plan.single.body, 'hello');
      expect(plan.single.attachments, isEmpty);
    });

    test('sends a copied component before following text', () {
      final component = Message.local(
        roomId: 'room_1',
        sender: _sender,
        clientMessageId: 'source_message_1',
        type: 'playlist',
        body: '[歌单] 夜晚歌单',
      );

      final plan = composerMessageSendPlan(
        body: '紧接着发送的说明',
        component: component,
      );

      expect(plan, hasLength(2));
      expect(plan.first.type, 'playlist');
      expect(plan.first.body, isEmpty);
      expect(plan.first.attachments.single.type, 'playlist');
      expect(plan.first.attachments.single.sourceMessageId, 'source_message_1');
      expect(plan.last.type, 'text');
      expect(plan.last.body, '紧接着发送的说明');
    });

    test('keeps a component-only draft as one message', () {
      final component = Message.local(
        roomId: 'room_1',
        sender: _sender,
        clientMessageId: 'source_track_1',
        type: 'music_track',
        body: '[歌曲] 一首歌',
      );

      final plan = composerMessageSendPlan(body: '', component: component);

      expect(plan, hasLength(1));
      expect(plan.single.type, 'music_track');
      expect(plan.single.attachments.single.sourceMessageId, 'source_track_1');
    });

    test('sequential execution stops after the first failed part', () async {
      final component = Message.local(
        roomId: 'room_1',
        sender: _sender,
        clientMessageId: 'source_track_2',
        type: 'music_track',
        body: '[歌曲] 一首歌',
      );
      final plan = composerMessageSendPlan(body: '说明文字', component: component);
      final attempted = <String>[];
      final followingFlags = <bool>[];

      final completed = await sendComposerMessagePartsSequentially(
        parts: plan,
        send: (part, {required hasFollowingPart}) async {
          attempted.add(part.type);
          followingFlags.add(hasFollowingPart);
          return part.type != 'text';
        },
      );

      expect(completed, isFalse);
      expect(attempted, ['music_track', 'text']);
      expect(followingFlags, [true, false]);

      final firstFailureAttempts = <String>[];
      final stoppedImmediately = await sendComposerMessagePartsSequentially(
        parts: plan,
        send: (part, {required hasFollowingPart}) async {
          firstFailureAttempts.add(part.type);
          return false;
        },
      );
      expect(stoppedImmediately, isFalse);
      expect(firstFailureAttempts, ['music_track']);
    });
  });

  test(
    'hideMessageHistory deletes large selections in server-sized batches',
    () async {
      final batchSizes = <int>[];
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          expect(request.url.path, '/api/v1/rooms/room_1/message-history/hide');
          final body = jsonDecode(utf8.decode(request.bodyBytes));
          final ids = (body['message_ids'] as List<Object?>).cast<String>();
          expect(body['confirm'], isTrue);
          batchSizes.add(ids.length);
          return http.Response(
            jsonEncode({'ok': true, 'deleted_count': ids.length}),
            200,
          );
        }),
      );
      addTearDown(api.close);

      final deleted = await MessagesController(api: api).hideMessageHistory(
        roomId: 'room_1',
        messageIds: List.generate(205, (index) => 'msg_$index'),
      );

      expect(deleted, 205);
      expect(batchSizes, [100, 100, 5]);
    },
  );

  test(
    'sendComposedMessage publishes a local pending message before sending',
    () async {
      String? pendingClientId;
      final firstQuote = MessageQuote(
        messageId: 'source_1',
        senderDisplayName: '房内 Alice',
        body: 'source body',
        createdAt: DateTime.utc(2026, 7, 14, 8, 12),
      );
      final secondQuote = MessageQuote(
        messageId: 'source_2',
        senderDisplayName: '房内 Bob',
        body: 'second source',
        createdAt: DateTime.utc(2026, 7, 14, 8, 13),
      );
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/api/v1/rooms/room_1/messages');
          final body = jsonDecode(utf8.decode(request.bodyBytes));
          expect(body['client_message_id'], pendingClientId);
          expect(body['body'], 'hello');
          expect(body['quote_message_id'], 'source_1');
          expect(body['quote_message_ids'], ['source_1', 'source_2']);
          final message = _messageJson(
            clientMessageId: body['client_message_id']! as String,
            body: 'hello',
          );
          message['quote'] = {
            'message_id': 'source_1',
            'sender_display_name': '房内 Alice',
            'body': 'source body',
            'created_at': '2026-07-14T08:12:00Z',
          };
          message['quotes'] = [
            message['quote'],
            {
              'message_id': 'source_2',
              'sender_display_name': '房内 Bob',
              'body': 'second source',
              'created_at': '2026-07-14T08:13:00Z',
            },
          ];
          return http.Response.bytes(
            utf8.encode(jsonEncode({'message': message})),
            201,
            headers: const {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );
      addTearDown(api.close);

      final pendingBodies = <String>[];
      final sent = await MessagesController(api: api).sendComposedMessage(
        roomId: 'room_1',
        sender: _sender,
        body: 'hello',
        quotes: [firstQuote, secondQuote],
        onPending: (pending) {
          pendingClientId = pending.clientMessageId;
          pendingBodies.add(pending.local.body);
          expect(pending.local.pending, isTrue);
          expect(pending.local.effectiveQuotes, [firstQuote, secondQuote]);
        },
      );

      expect(pendingBodies, ['hello']);
      expect(sent.clientMessageId, pendingClientId);
      expect(sent.pending, isFalse);
      expect(sent.quote?.senderDisplayName, '房内 Alice');
      expect(sent.effectiveQuotes.map((quote) => quote.messageId), [
        'source_1',
        'source_2',
      ]);
    },
  );

  test('sendFileMessage owns pending upload and send sequencing', () async {
    String? pendingClientId;
    final requests = <String>[];
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        requests.add(request.url.path);
        if (request.url.path == '/api/v1/uploads/files') {
          final multipartBody = utf8.decode(
            request.bodyBytes,
            allowMalformed: true,
          );
          expect(multipartBody, contains('report.pdf'));
          return http.Response(
            jsonEncode({
              'asset': _assetJson(
                id: 'asset_1',
                filename: 'report.pdf',
                sizeBytes: 3,
              ),
            }),
            201,
          );
        }

        expect(request.url.path, '/api/v1/rooms/room_1/messages');
        final body = jsonDecode(utf8.decode(request.bodyBytes));
        expect(body['client_message_id'], pendingClientId);
        expect(body['body'], 'report.pdf');
        expect(body['type'], 'file');
        final attachments = body['attachments']! as List<Object?>;
        final attachment = attachments.single! as Map<String, Object?>;
        expect(attachment['type'], 'file');
        expect(attachment['name'], 'report.pdf');
        expect((attachment['asset']! as Map<String, Object?>)['id'], 'asset_1');
        return http.Response(
          jsonEncode({
            'message': _messageJson(
              clientMessageId: body['client_message_id']! as String,
              body: 'report.pdf',
              type: 'file',
              attachments: attachments,
            ),
          }),
          201,
        );
      }),
    );
    addTearDown(api.close);

    final events = <String>[];
    final progress = <int>[];
    final sent = await MessagesController(api: api).sendFileMessage(
      roomId: 'room_1',
      sender: _sender,
      filename: 'report.pdf',
      sizeBytes: 3,
      mimeType: 'application/pdf',
      readBytes: () async => Uint8List.fromList([1, 2, 3]),
      onPending: (pending) {
        pendingClientId = pending.clientMessageId;
        events.add('pending');
        expect(pending.local.type, 'file');
        expect(pending.local.pending, isTrue);
      },
      onProgress: (_, {required sentBytes, required totalBytes}) {
        expect(totalBytes, 3);
        progress.add(sentBytes);
      },
      onUploaded: (_, attachment) {
        events.add('uploaded');
        expect(attachment.asset?.id, 'asset_1');
      },
    );

    expect(requests, [
      '/api/v1/uploads/files',
      '/api/v1/rooms/room_1/messages',
    ]);
    expect(events, ['pending', 'uploaded']);
    expect(progress.first, 0);
    expect(progress.last, 3);
    expect(sent.type, 'file');
    expect(sent.attachments.single.asset?.id, 'asset_1');
  });

  test('sendVoiceMessage uploads audio and sends duration metadata', () async {
    String? pendingClientId;
    final requests = <String>[];
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        requests.add(request.url.path);
        if (request.url.path == '/api/v1/uploads/files') {
          final multipartBody = utf8.decode(
            request.bodyBytes,
            allowMalformed: true,
          );
          expect(multipartBody, contains('voice_1.m4a'));
          return http.Response(
            jsonEncode({
              'asset': _assetJson(
                id: 'asset_voice',
                filename: 'voice_1.m4a',
                sizeBytes: 4,
                mimeType: 'audio/mp4',
              ),
            }),
            201,
          );
        }

        expect(request.url.path, '/api/v1/rooms/room_1/messages');
        final body = jsonDecode(utf8.decode(request.bodyBytes));
        expect(body['client_message_id'], pendingClientId);
        expect(body['body'], 'voice_1.m4a');
        expect(body['type'], 'audio');
        final attachments = body['attachments']! as List<Object?>;
        final attachment = attachments.single! as Map<String, Object?>;
        expect(attachment['type'], 'audio');
        expect(attachment['name'], 'voice_1.m4a');
        expect(attachment['duration_ms'], 15000);
        expect(
          (attachment['asset']! as Map<String, Object?>)['id'],
          'asset_voice',
        );
        return http.Response(
          jsonEncode({
            'message': _messageJson(
              clientMessageId: body['client_message_id']! as String,
              body: 'voice_1.m4a',
              type: 'audio',
              attachments: attachments,
            ),
          }),
          201,
        );
      }),
    );
    addTearDown(api.close);

    final events = <String>[];
    final sent = await MessagesController(api: api).sendVoiceMessage(
      roomId: 'room_1',
      sender: _sender,
      filename: 'voice_1.m4a',
      sizeBytes: 4,
      mimeType: 'audio/mp4',
      duration: const Duration(seconds: 15),
      readBytes: () async => Uint8List.fromList([1, 2, 3, 4]),
      onPending: (pending) {
        pendingClientId = pending.clientMessageId;
        events.add('pending');
        expect(pending.local.type, 'audio');
        expect(pending.local.attachments.single.durationMs, 15000);
      },
      onUploaded: (_, attachment) {
        events.add('uploaded');
        expect(attachment.type, 'audio');
        expect(attachment.durationMs, 15000);
      },
    );

    expect(requests, [
      '/api/v1/uploads/files',
      '/api/v1/rooms/room_1/messages',
    ]);
    expect(events, ['pending', 'uploaded']);
    expect(sent.type, 'audio');
    expect(sent.attachments.single.durationMs, 15000);
    expect(sent.attachments.single.asset?.mimeType, 'audio/mp4');
  });

  test('message list reducers append replace remove and mark failed', () {
    const controller = MessagesController();
    final local = Message.local(
      roomId: 'room_1',
      sender: _sender,
      clientMessageId: 'client_1',
      body: 'pending',
    );
    final sent = Message(
      id: 'message_1',
      roomId: 'room_1',
      sender: _sender,
      clientMessageId: 'client_1',
      body: 'sent',
      createdAt: DateTime.utc(2026, 6, 4),
    );

    final appended = controller.appendLocalMessage(const [], local);
    expect(appended.single.pending, isTrue);

    final failed = controller.markFailedByClientId(appended, 'client_1');
    expect(failed.single.failed, isTrue);

    final replaced = controller.replaceByClientId(failed, sent);
    expect(replaced.single.body, 'sent');
    expect(replaced.single.pending, isFalse);

    expect(controller.removeByClientId(replaced, 'client_1'), isEmpty);
  });

  test('composed message state patches cover pending sent and failed', () {
    const controller = MessagesController();
    final existing = [_message('existing', clientMessageId: 'existing_client')];
    final pending = controller.createPendingMessage(
      roomId: 'room_1',
      sender: _sender,
      body: 'hello',
    );
    final sent = Message(
      id: 'message_1',
      roomId: 'room_1',
      sender: _sender,
      clientMessageId: pending.clientMessageId,
      body: 'hello',
      createdAt: DateTime.utc(2026, 6, 4),
    );

    final started = controller.patchMessageSendStarted(messages: existing);
    expect(started.messages, same(existing));
    expect(started.sending, isTrue);
    expect(started.error, isNull);

    final pendingPatch = controller.patchMessageSendPending(
      messages: existing,
      pending: pending,
      error: 'kept',
    );
    expect(pendingPatch.messages, hasLength(2));
    expect(pendingPatch.messages.last.pending, isTrue);
    expect(pendingPatch.sending, isTrue);
    expect(pendingPatch.error, 'kept');

    final failedPatch = controller.patchMessageSendFailed(
      messages: pendingPatch.messages,
      clientMessageId: pending.clientMessageId,
      error: null,
    );
    expect(failedPatch.messages.last.failed, isTrue);
    expect(failedPatch.sending, isTrue);
    expect(failedPatch.error, isNull);

    final failedWithoutClientId = controller.patchMessageSendFailed(
      messages: pendingPatch.messages,
      clientMessageId: null,
      error: 'kept',
    );
    expect(failedWithoutClientId.messages, same(pendingPatch.messages));
    expect(failedWithoutClientId.sending, isTrue);
    expect(failedWithoutClientId.error, 'kept');

    final sentPatch = controller.patchMessageSendSucceeded(
      messages: failedPatch.messages,
      sent: sent,
      error: null,
    );
    expect(sentPatch.messages.last, sent);
    expect(sentPatch.sending, isTrue);
    expect(sentPatch.error, isNull);

    final finished = controller.patchMessageSendFinished(
      messages: sentPatch.messages,
      error: 'kept',
    );
    expect(finished.messages, same(sentPatch.messages));
    expect(finished.sending, isFalse);
    expect(finished.error, 'kept');
  });

  test('file message state patches cover upload lifecycle', () {
    const controller = MessagesController();
    final pending = controller.createPendingFileMessage(
      roomId: 'room_1',
      sender: _sender,
      filename: 'report.pdf',
      sizeBytes: 100,
      mimeType: 'application/pdf',
    );

    var patch = controller.patchPendingFileMessage(
      messages: const [],
      fileTransfers: const {},
      pending: pending,
    );
    expect(patch.messages.single.pending, isTrue);
    expect(
      patch.fileTransfers[pending.clientMessageId],
      same(pending.transfer),
    );

    final progressPatch = controller.patchFileTransferProgress(
      messages: patch.messages,
      fileTransfers: patch.fileTransfers,
      pending: pending,
      sentBytes: 25,
      totalBytes: 100,
    );
    expect(progressPatch, isNotNull);
    expect(progressPatch!.messages, same(patch.messages));
    expect(progressPatch.fileTransfers, same(patch.fileTransfers));
    expect(pending.transfer.sentBytes, 25);
    patch = progressPatch;

    final attachment = MessageAttachment(
      type: 'file',
      name: 'report.pdf',
      asset: UploadedAsset(
        id: 'asset_1',
        url: '/assets/asset_1/report.pdf',
        thumbnailUrl: null,
        mimeType: 'application/pdf',
        filename: 'report.pdf',
        sizeBytes: 100,
      ),
    );
    patch = controller.patchUploadedFileMessage(
      messages: patch.messages,
      fileTransfers: patch.fileTransfers,
      pending: pending,
      attachment: attachment,
    );
    expect(pending.transfer.sendingMessage, isTrue);
    expect(patch.messages.single.attachments.single.asset?.id, 'asset_1');

    patch = controller.patchFailedFileMessage(
      messages: patch.messages,
      fileTransfers: patch.fileTransfers,
      clientMessageId: pending.clientMessageId,
      failure: 'network failed',
    );
    expect(pending.transfer.failed, isTrue);
    expect(pending.transfer.error, '网络连接失败，请检查网络后重试');
    expect(patch.messages.single.failed, isTrue);

    final sent = Message(
      id: 'message_1',
      roomId: 'room_1',
      sender: _sender,
      clientMessageId: pending.clientMessageId,
      type: 'file',
      body: 'report.pdf',
      attachments: [attachment],
      createdAt: DateTime.utc(2026, 6, 4),
    );
    patch = controller.patchSentFileMessage(
      messages: patch.messages,
      fileTransfers: patch.fileTransfers,
      clientMessageId: pending.clientMessageId,
      sent: sent,
    );
    expect(patch.messages.single, sent);
    expect(patch.fileTransfers, isEmpty);
  });

  test('file message remove patch drops local message and transfer', () {
    const controller = MessagesController();
    final pending = controller.createPendingFileMessage(
      roomId: 'room_1',
      sender: _sender,
      filename: 'report.pdf',
      sizeBytes: 100,
      mimeType: 'application/pdf',
    );
    final pendingPatch = controller.patchPendingFileMessage(
      messages: const [],
      fileTransfers: const {},
      pending: pending,
    );

    final removed = controller.patchRemovedFileMessage(
      messages: pendingPatch.messages,
      fileTransfers: pendingPatch.fileTransfers,
      clientMessageId: pending.clientMessageId,
    );

    expect(removed.messages, isEmpty);
    expect(removed.fileTransfers, isEmpty);
  });

  test(
    'file upload controls pause resume and cancel only upload transfers',
    () {
      const controller = MessagesController();
      final pending = controller.createPendingFileMessage(
        roomId: 'room_1',
        sender: _sender,
        filename: 'report.pdf',
        sizeBytes: 100,
        mimeType: 'application/pdf',
      );
      final transfers = {
        pending.clientMessageId: pending.transfer,
        'download': FileTransferState.download(
          controller: UploadTransferController(),
          totalBytes: 100,
          destinationPath: '/tmp/report.pdf',
        ),
      };

      expect(
        controller.patchPausedFileUpload(
          messages: const [],
          fileTransfers: transfers,
          clientMessageId: pending.clientMessageId,
        ),
        isNotNull,
      );
      expect(pending.transfer.paused, isTrue);
      expect(
        controller.patchPausedFileUpload(
          messages: const [],
          fileTransfers: transfers,
          clientMessageId: pending.clientMessageId,
        ),
        isNull,
      );

      expect(
        controller.patchResumedFileUpload(
          messages: const [],
          fileTransfers: transfers,
          clientMessageId: pending.clientMessageId,
        ),
        isNotNull,
      );
      expect(pending.transfer.paused, isFalse);
      expect(
        controller.patchResumedFileUpload(
          messages: const [],
          fileTransfers: transfers,
          clientMessageId: pending.clientMessageId,
        ),
        isNull,
      );

      expect(
        controller.pauseFileUpload(
          fileTransfers: transfers,
          clientMessageId: 'download',
        ),
        isFalse,
      );
      expect(
        controller.cancelFileUpload(
          fileTransfers: transfers,
          clientMessageId: 'download',
        ),
        isFalse,
      );
      expect(
        controller.cancelFileUpload(
          fileTransfers: transfers,
          clientMessageId: 'missing',
        ),
        isFalse,
      );

      expect(
        controller.cancelFileUpload(
          fileTransfers: transfers,
          clientMessageId: pending.clientMessageId,
        ),
        isTrue,
      );
      expect(pending.transfer.cancelled, isTrue);
      expect(
        controller.cancelFileUpload(
          fileTransfers: transfers,
          clientMessageId: pending.clientMessageId,
        ),
        isFalse,
      );
    },
  );

  test('canSendComposedMessage validates text and attachment messages', () {
    expect(
      canSendComposedMessage(
        body: ' hello ',
        type: 'text',
        attachments: const [],
      ),
      isTrue,
    );
    expect(
      canSendComposedMessage(body: ' ', type: 'text', attachments: const []),
      isFalse,
    );
    expect(
      canSendComposedMessage(
        body: '',
        type: 'sticker',
        attachments: [_stickerAttachment()],
      ),
      isTrue,
    );
    expect(
      canSendComposedMessage(
        body: 'ignored',
        type: 'sticker',
        attachments: const [],
      ),
      isFalse,
    );
  });

  test('markRead forwards the latest message id to the API', () async {
    var requests = 0;
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        requests++;
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/rooms/room_1/read');
        expect(
          jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>,
          {'last_read_message_id': 'msg_2'},
        );
        if (requests == 1) {
          throw http.ClientException('Connection reset by peer', request.url);
        }
        return http.Response(jsonEncode({'ok': true, 'unread_count': 0}), 200);
      }),
    );
    addTearDown(api.close);

    final unread = await MessagesController(
      api: api,
    ).markRead(roomId: 'room_1', lastReadMessageId: 'msg_2');

    expect(unread, 0);
    expect(requests, 2);
  });
}

const _sender = UserSummary(
  id: 'user_1',
  username: 'alice',
  displayName: 'Alice',
  avatarUrl: null,
  defaultAvatarKey: 'blue-3',
);

Message _message(String id, {required String clientMessageId}) {
  return Message(
    id: id,
    roomId: 'room_1',
    sender: _sender,
    clientMessageId: clientMessageId,
    body: id,
    createdAt: DateTime.utc(2026, 6, 4),
  );
}

Map<String, Object?> _messageJson({
  required String clientMessageId,
  required String body,
  String type = 'text',
  List<Object?> attachments = const [],
}) {
  return {
    'id': 'msg_1',
    'room_id': 'room_1',
    'sender': {
      'id': _sender.id,
      'username': _sender.username,
      'display_name': _sender.displayName,
    },
    'client_message_id': clientMessageId,
    'type': type,
    'body': body,
    'attachments': attachments,
    'created_at': '2026-05-31T14:00:00Z',
  };
}

Map<String, Object?> _assetJson({
  required String id,
  required String filename,
  required int sizeBytes,
  String mimeType = 'application/pdf',
}) {
  return {
    'id': id,
    'filename': filename,
    'size_bytes': sizeBytes,
    'url': '/assets/$id/$filename',
    'thumbnail_url': null,
    'mime_type': mimeType,
  };
}

MessageAttachment _stickerAttachment() {
  return MessageAttachment(
    type: 'sticker',
    asset: UploadedAsset(
      id: 'sticker_asset',
      url: '/assets/sticker.webp',
      thumbnailUrl: null,
      mimeType: 'image/webp',
    ),
  );
}
