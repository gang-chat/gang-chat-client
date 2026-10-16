import { describe, expect, test } from 'vitest';

import { FileTransferState } from '../../src/app/file_transfer_state';
import {
  canSendComposedMessage,
  composerMessageSendPlan,
  MessagesController,
  sendComposerMessagePartsSequentially,
} from '../../src/app/messages_controller';
import { isJsonMap, type JsonMap } from '../../src/lib/json';
import { GangApiClient, UploadTransferController } from '../../src/protocol/api_client';
import { Message, MessageAttachment, MessageQuote, UploadedAsset, UserSummary } from '../../src/protocol/models';
import { ClientException, type HttpClient, type HttpRequest, type StreamedResponse } from '../../src/platform/http';

interface MockRequest {
  method: string;
  url: URL;
  bodyBytes: Uint8Array;
}

interface MockResponse {
  statusCode: number;
  body: string;
  headers?: Record<string, string>;
}

/** Port of `package:http/testing.dart` `MockClient` for the [HttpClient] interface. */
class MockClient implements HttpClient {
  constructor(private readonly handler: (request: MockRequest) => Promise<MockResponse>) {}

  async send(request: HttpRequest): Promise<StreamedResponse> {
    const bodyBytes = await _readBody(request.body);
    const response = await this.handler({ method: request.method, url: new URL(request.url), bodyBytes });
    const bytes = new TextEncoder().encode(response.body);
    return {
      statusCode: response.statusCode,
      headers: response.headers ?? { 'content-type': 'application/json; charset=utf-8' },
      contentLength: bytes.length,
      stream: (async function* () {
        yield bytes;
      })(),
    };
  }

  close(): void {}
}

async function _readBody(body: HttpRequest['body']): Promise<Uint8Array> {
  if (body == null) return new Uint8Array();
  if (typeof body === 'string') return new TextEncoder().encode(body);
  if (body instanceof Uint8Array) return body;
  const chunks: number[] = [];
  for await (const chunk of body) chunks.push(...chunk);
  return Uint8Array.from(chunks);
}

function _jsonBody(request: MockRequest): JsonMap {
  const decoded: unknown = JSON.parse(new TextDecoder().decode(request.bodyBytes));
  if (!isJsonMap(decoded)) throw new Error('expected a JSON object body');
  return decoded;
}

function _api(handler: (request: MockRequest) => Promise<MockResponse>): GangApiClient {
  return new GangApiClient({
    baseUrl: 'http://example.test/api/v1',
    accessTokenProvider: async () => 'token',
    httpClient: new MockClient(handler),
  });
}

describe('composerMessageSendPlan', () => {
  test('keeps a normal text message as one message', () => {
    const plan = composerMessageSendPlan({ body: 'hello   ' });

    expect(plan).toHaveLength(1);
    expect(plan[0]!.type).toBe('text');
    expect(plan[0]!.body).toBe('hello');
    expect(plan[0]!.attachments).toEqual([]);
  });

  test('sends a copied component before following text', () => {
    const component = Message.local({
      roomId: 'room_1',
      sender: _sender,
      clientMessageId: 'source_message_1',
      type: 'playlist',
      body: '[歌单] 夜晚歌单',
    });

    const plan = composerMessageSendPlan({ body: '紧接着发送的说明', component });

    expect(plan).toHaveLength(2);
    expect(plan[0]!.type).toBe('playlist');
    expect(plan[0]!.body).toBe('');
    expect(plan[0]!.attachments[0]!.type).toBe('playlist');
    expect(plan[0]!.attachments[0]!.sourceMessageId).toBe('source_message_1');
    expect(plan[1]!.type).toBe('text');
    expect(plan[1]!.body).toBe('紧接着发送的说明');
  });

  test('keeps a component-only draft as one message', () => {
    const component = Message.local({
      roomId: 'room_1',
      sender: _sender,
      clientMessageId: 'source_track_1',
      type: 'music_track',
      body: '[歌曲] 一首歌',
    });

    const plan = composerMessageSendPlan({ body: '', component });

    expect(plan).toHaveLength(1);
    expect(plan[0]!.type).toBe('music_track');
    expect(plan[0]!.attachments[0]!.sourceMessageId).toBe('source_track_1');
  });

  test('sequential execution stops after the first failed part', async () => {
    const component = Message.local({
      roomId: 'room_1',
      sender: _sender,
      clientMessageId: 'source_track_2',
      type: 'music_track',
      body: '[歌曲] 一首歌',
    });
    const plan = composerMessageSendPlan({ body: '说明文字', component });
    const attempted: string[] = [];
    const followingFlags: boolean[] = [];

    const completed = await sendComposerMessagePartsSequentially({
      parts: plan,
      send: async (part, { hasFollowingPart }) => {
        attempted.push(part.type);
        followingFlags.push(hasFollowingPart);
        return part.type !== 'text';
      },
    });

    expect(completed).toBe(false);
    expect(attempted).toEqual(['music_track', 'text']);
    expect(followingFlags).toEqual([true, false]);

    const firstFailureAttempts: string[] = [];
    const stoppedImmediately = await sendComposerMessagePartsSequentially({
      parts: plan,
      send: async (part) => {
        firstFailureAttempts.push(part.type);
        return false;
      },
    });
    expect(stoppedImmediately).toBe(false);
    expect(firstFailureAttempts).toEqual(['music_track']);
  });
});

test('hideMessageHistory deletes large selections in server-sized batches', async () => {
  const batchSizes: number[] = [];
  const api = _api(async (request) => {
    expect(request.url.pathname).toBe('/api/v1/rooms/room_1/message-history/hide');
    const body = _jsonBody(request);
    const ids = body['message_ids'] as string[];
    expect(body['confirm']).toBe(true);
    batchSizes.push(ids.length);
    return { statusCode: 200, body: JSON.stringify({ ok: true, deleted_count: ids.length }) };
  });

  const deleted = await new MessagesController({ api }).hideMessageHistory({
    roomId: 'room_1',
    messageIds: Array.from({ length: 205 }, (_, index) => `msg_${index}`),
  });

  expect(deleted).toBe(205);
  expect(batchSizes).toEqual([100, 100, 5]);
});

test('sendComposedMessage publishes a local pending message before sending', async () => {
  let pendingClientId: string | null = null;
  const firstQuote = new MessageQuote({
    messageId: 'source_1',
    senderDisplayName: '房内 Alice',
    body: 'source body',
    createdAt: new Date(Date.UTC(2026, 6, 14, 8, 12)),
  });
  const secondQuote = new MessageQuote({
    messageId: 'source_2',
    senderDisplayName: '房内 Bob',
    body: 'second source',
    createdAt: new Date(Date.UTC(2026, 6, 14, 8, 13)),
  });
  const api = _api(async (request) => {
    expect(request.method).toBe('POST');
    expect(request.url.pathname).toBe('/api/v1/rooms/room_1/messages');
    const body = _jsonBody(request);
    expect(body['client_message_id']).toBe(pendingClientId);
    expect(body['body']).toBe('hello');
    expect(body['quote_message_id']).toBe('source_1');
    expect(body['quote_message_ids']).toEqual(['source_1', 'source_2']);
    const message = _messageJson({ clientMessageId: body['client_message_id'] as string, body: 'hello' });
    message['quote'] = {
      message_id: 'source_1',
      sender_display_name: '房内 Alice',
      body: 'source body',
      created_at: '2026-07-14T08:12:00Z',
    };
    message['quotes'] = [
      message['quote'],
      {
        message_id: 'source_2',
        sender_display_name: '房内 Bob',
        body: 'second source',
        created_at: '2026-07-14T08:13:00Z',
      },
    ];
    return { statusCode: 201, body: JSON.stringify({ message }) };
  });

  const pendingBodies: string[] = [];
  const sent = await new MessagesController({ api }).sendComposedMessage({
    roomId: 'room_1',
    sender: _sender,
    body: 'hello',
    quotes: [firstQuote, secondQuote],
    onPending: (pending) => {
      pendingClientId = pending.clientMessageId;
      pendingBodies.push(pending.local.body);
      expect(pending.local.pending).toBe(true);
      expect(pending.local.effectiveQuotes).toEqual([firstQuote, secondQuote]);
    },
  });

  expect(pendingBodies).toEqual(['hello']);
  expect(sent.clientMessageId).toBe(pendingClientId);
  expect(sent.pending).toBe(false);
  expect(sent.quote?.senderDisplayName).toBe('房内 Alice');
  expect(sent.effectiveQuotes.map((quote) => quote.messageId)).toEqual(['source_1', 'source_2']);
});

test('sendFileMessage owns pending upload and send sequencing', async () => {
  let pendingClientId: string | null = null;
  const requests: string[] = [];
  const api = _api(async (request) => {
    requests.push(request.url.pathname);
    if (request.url.pathname === '/api/v1/uploads/files') {
      const multipartBody = new TextDecoder().decode(request.bodyBytes);
      expect(multipartBody).toContain('report.pdf');
      return {
        statusCode: 201,
        body: JSON.stringify({ asset: _assetJson({ id: 'asset_1', filename: 'report.pdf', sizeBytes: 3 }) }),
      };
    }

    expect(request.url.pathname).toBe('/api/v1/rooms/room_1/messages');
    const body = _jsonBody(request);
    expect(body['client_message_id']).toBe(pendingClientId);
    expect(body['body']).toBe('report.pdf');
    expect(body['type']).toBe('file');
    const attachments = body['attachments'] as JsonMap[];
    expect(attachments).toHaveLength(1);
    const attachment = attachments[0]!;
    expect(attachment['type']).toBe('file');
    expect(attachment['name']).toBe('report.pdf');
    expect((attachment['asset'] as JsonMap)['id']).toBe('asset_1');
    return {
      statusCode: 201,
      body: JSON.stringify({
        message: _messageJson({
          clientMessageId: body['client_message_id'] as string,
          body: 'report.pdf',
          type: 'file',
          attachments,
        }),
      }),
    };
  });

  const events: string[] = [];
  const progress: number[] = [];
  const sent = await new MessagesController({ api }).sendFileMessage({
    roomId: 'room_1',
    sender: _sender,
    filename: 'report.pdf',
    sizeBytes: 3,
    mimeType: 'application/pdf',
    readBytes: async () => Uint8Array.from([1, 2, 3]),
    onPending: (pending) => {
      pendingClientId = pending.clientMessageId;
      events.push('pending');
      expect(pending.local.type).toBe('file');
      expect(pending.local.pending).toBe(true);
    },
    onProgress: (_, { sentBytes, totalBytes }) => {
      expect(totalBytes).toBe(3);
      progress.push(sentBytes);
    },
    onUploaded: (_, attachment) => {
      events.push('uploaded');
      expect(attachment.asset?.id).toBe('asset_1');
    },
  });

  expect(requests).toEqual(['/api/v1/uploads/files', '/api/v1/rooms/room_1/messages']);
  expect(events).toEqual(['pending', 'uploaded']);
  expect(progress[0]).toBe(0);
  expect(progress[progress.length - 1]).toBe(3);
  expect(sent.type).toBe('file');
  expect(sent.attachments[0]!.asset?.id).toBe('asset_1');
});

test('sendVoiceMessage uploads audio and sends duration metadata', async () => {
  let pendingClientId: string | null = null;
  const requests: string[] = [];
  const api = _api(async (request) => {
    requests.push(request.url.pathname);
    if (request.url.pathname === '/api/v1/uploads/files') {
      const multipartBody = new TextDecoder().decode(request.bodyBytes);
      expect(multipartBody).toContain('voice_1.m4a');
      return {
        statusCode: 201,
        body: JSON.stringify({
          asset: _assetJson({ id: 'asset_voice', filename: 'voice_1.m4a', sizeBytes: 4, mimeType: 'audio/mp4' }),
        }),
      };
    }

    expect(request.url.pathname).toBe('/api/v1/rooms/room_1/messages');
    const body = _jsonBody(request);
    expect(body['client_message_id']).toBe(pendingClientId);
    expect(body['body']).toBe('voice_1.m4a');
    expect(body['type']).toBe('audio');
    const attachments = body['attachments'] as JsonMap[];
    expect(attachments).toHaveLength(1);
    const attachment = attachments[0]!;
    expect(attachment['type']).toBe('audio');
    expect(attachment['name']).toBe('voice_1.m4a');
    expect(attachment['duration_ms']).toBe(15000);
    expect((attachment['asset'] as JsonMap)['id']).toBe('asset_voice');
    return {
      statusCode: 201,
      body: JSON.stringify({
        message: _messageJson({
          clientMessageId: body['client_message_id'] as string,
          body: 'voice_1.m4a',
          type: 'audio',
          attachments,
        }),
      }),
    };
  });

  const events: string[] = [];
  const sent = await new MessagesController({ api }).sendVoiceMessage({
    roomId: 'room_1',
    sender: _sender,
    filename: 'voice_1.m4a',
    sizeBytes: 4,
    mimeType: 'audio/mp4',
    duration: 15_000,
    readBytes: async () => Uint8Array.from([1, 2, 3, 4]),
    onPending: (pending) => {
      pendingClientId = pending.clientMessageId;
      events.push('pending');
      expect(pending.local.type).toBe('audio');
      expect(pending.local.attachments[0]!.durationMs).toBe(15000);
    },
    onUploaded: (_, attachment) => {
      events.push('uploaded');
      expect(attachment.type).toBe('audio');
      expect(attachment.durationMs).toBe(15000);
    },
  });

  expect(requests).toEqual(['/api/v1/uploads/files', '/api/v1/rooms/room_1/messages']);
  expect(events).toEqual(['pending', 'uploaded']);
  expect(sent.type).toBe('audio');
  expect(sent.attachments[0]!.durationMs).toBe(15000);
  expect(sent.attachments[0]!.asset?.mimeType).toBe('audio/mp4');
});

test('message list reducers append replace remove and mark failed', () => {
  const controller = new MessagesController();
  const local = Message.local({
    roomId: 'room_1',
    sender: _sender,
    clientMessageId: 'client_1',
    body: 'pending',
  });
  const sent = new Message({
    id: 'message_1',
    roomId: 'room_1',
    sender: _sender,
    clientMessageId: 'client_1',
    body: 'sent',
    createdAt: new Date(Date.UTC(2026, 5, 4)),
  });

  const appended = controller.appendLocalMessage([], local);
  expect(appended).toHaveLength(1);
  expect(appended[0]!.pending).toBe(true);

  const failed = controller.markFailedByClientId(appended, 'client_1');
  expect(failed[0]!.failed).toBe(true);

  const replaced = controller.replaceByClientId(failed, sent);
  expect(replaced).toHaveLength(1);
  expect(replaced[0]!.body).toBe('sent');
  expect(replaced[0]!.pending).toBe(false);

  expect(controller.removeByClientId(replaced, 'client_1')).toEqual([]);
});

test('composed message state patches cover pending sent and failed', () => {
  const controller = new MessagesController();
  const existing = [_message('existing', { clientMessageId: 'existing_client' })];
  const pending = controller.createPendingMessage({
    roomId: 'room_1',
    sender: _sender,
    body: 'hello',
  });
  const sent = new Message({
    id: 'message_1',
    roomId: 'room_1',
    sender: _sender,
    clientMessageId: pending.clientMessageId,
    body: 'hello',
    createdAt: new Date(Date.UTC(2026, 5, 4)),
  });

  const started = controller.patchMessageSendStarted({ messages: existing });
  expect(started.messages).toBe(existing);
  expect(started.sending).toBe(true);
  expect(started.error).toBeNull();

  const pendingPatch = controller.patchMessageSendPending({
    messages: existing,
    pending,
    error: 'kept',
  });
  expect(pendingPatch.messages).toHaveLength(2);
  expect(pendingPatch.messages[1]!.pending).toBe(true);
  expect(pendingPatch.sending).toBe(true);
  expect(pendingPatch.error).toBe('kept');

  const failedPatch = controller.patchMessageSendFailed({
    messages: pendingPatch.messages,
    clientMessageId: pending.clientMessageId,
    error: null,
  });
  expect(failedPatch.messages[1]!.failed).toBe(true);
  expect(failedPatch.sending).toBe(true);
  expect(failedPatch.error).toBeNull();

  const failedWithoutClientId = controller.patchMessageSendFailed({
    messages: pendingPatch.messages,
    clientMessageId: null,
    error: 'kept',
  });
  expect(failedWithoutClientId.messages).toBe(pendingPatch.messages);
  expect(failedWithoutClientId.sending).toBe(true);
  expect(failedWithoutClientId.error).toBe('kept');

  const sentPatch = controller.patchMessageSendSucceeded({
    messages: failedPatch.messages,
    sent,
    error: null,
  });
  expect(sentPatch.messages[sentPatch.messages.length - 1]).toBe(sent);
  expect(sentPatch.sending).toBe(true);
  expect(sentPatch.error).toBeNull();

  const finished = controller.patchMessageSendFinished({
    messages: sentPatch.messages,
    error: 'kept',
  });
  expect(finished.messages).toBe(sentPatch.messages);
  expect(finished.sending).toBe(false);
  expect(finished.error).toBe('kept');
});

test('file message state patches cover upload lifecycle', () => {
  const controller = new MessagesController();
  const pending = controller.createPendingFileMessage({
    roomId: 'room_1',
    sender: _sender,
    filename: 'report.pdf',
    sizeBytes: 100,
    mimeType: 'application/pdf',
  });

  let patch = controller.patchPendingFileMessage({
    messages: [],
    fileTransfers: new Map(),
    pending,
  });
  expect(patch.messages).toHaveLength(1);
  expect(patch.messages[0]!.pending).toBe(true);
  expect(patch.fileTransfers.get(pending.clientMessageId)).toBe(pending.transfer);

  const progressPatch = controller.patchFileTransferProgress({
    messages: patch.messages,
    fileTransfers: patch.fileTransfers,
    pending,
    sentBytes: 25,
    totalBytes: 100,
  });
  expect(progressPatch).not.toBeNull();
  expect(progressPatch!.messages).toBe(patch.messages);
  expect(progressPatch!.fileTransfers).toBe(patch.fileTransfers);
  expect(pending.transfer.sentBytes).toBe(25);
  patch = progressPatch!;

  const attachment = new MessageAttachment({
    type: 'file',
    name: 'report.pdf',
    asset: new UploadedAsset({
      id: 'asset_1',
      url: '/assets/asset_1/report.pdf',
      thumbnailUrl: null,
      mimeType: 'application/pdf',
      filename: 'report.pdf',
      sizeBytes: 100,
    }),
  });
  patch = controller.patchUploadedFileMessage({
    messages: patch.messages,
    fileTransfers: patch.fileTransfers,
    pending,
    attachment,
  });
  expect(pending.transfer.sendingMessage).toBe(true);
  expect(patch.messages[0]!.attachments[0]!.asset?.id).toBe('asset_1');

  patch = controller.patchFailedFileMessage({
    messages: patch.messages,
    fileTransfers: patch.fileTransfers,
    clientMessageId: pending.clientMessageId,
    failure: 'network failed',
  });
  expect(pending.transfer.failed).toBe(true);
  expect(pending.transfer.error).toBe('网络连接失败，请检查网络后重试');
  expect(patch.messages[0]!.failed).toBe(true);

  const sent = new Message({
    id: 'message_1',
    roomId: 'room_1',
    sender: _sender,
    clientMessageId: pending.clientMessageId,
    type: 'file',
    body: 'report.pdf',
    attachments: [attachment],
    createdAt: new Date(Date.UTC(2026, 5, 4)),
  });
  patch = controller.patchSentFileMessage({
    messages: patch.messages,
    fileTransfers: patch.fileTransfers,
    clientMessageId: pending.clientMessageId,
    sent,
  });
  expect(patch.messages).toEqual([sent]);
  expect(patch.fileTransfers.size).toBe(0);
});

test('file message remove patch drops local message and transfer', () => {
  const controller = new MessagesController();
  const pending = controller.createPendingFileMessage({
    roomId: 'room_1',
    sender: _sender,
    filename: 'report.pdf',
    sizeBytes: 100,
    mimeType: 'application/pdf',
  });
  const pendingPatch = controller.patchPendingFileMessage({
    messages: [],
    fileTransfers: new Map(),
    pending,
  });

  const removed = controller.patchRemovedFileMessage({
    messages: pendingPatch.messages,
    fileTransfers: pendingPatch.fileTransfers,
    clientMessageId: pending.clientMessageId,
  });

  expect(removed.messages).toEqual([]);
  expect(removed.fileTransfers.size).toBe(0);
});

test('file upload controls pause resume and cancel only upload transfers', () => {
  const controller = new MessagesController();
  const pending = controller.createPendingFileMessage({
    roomId: 'room_1',
    sender: _sender,
    filename: 'report.pdf',
    sizeBytes: 100,
    mimeType: 'application/pdf',
  });
  const transfers = new Map<string, FileTransferState>([
    [pending.clientMessageId, pending.transfer],
    [
      'download',
      FileTransferState.download({
        controller: new UploadTransferController(),
        totalBytes: 100,
        destinationPath: '/tmp/report.pdf',
      }),
    ],
  ]);
  const clientMessageId = pending.clientMessageId;

  expect(controller.patchPausedFileUpload({ messages: [], fileTransfers: transfers, clientMessageId })).not.toBeNull();
  expect(pending.transfer.paused).toBe(true);
  expect(controller.patchPausedFileUpload({ messages: [], fileTransfers: transfers, clientMessageId })).toBeNull();

  expect(controller.patchResumedFileUpload({ messages: [], fileTransfers: transfers, clientMessageId })).not.toBeNull();
  expect(pending.transfer.paused).toBe(false);
  expect(controller.patchResumedFileUpload({ messages: [], fileTransfers: transfers, clientMessageId })).toBeNull();

  expect(controller.pauseFileUpload({ fileTransfers: transfers, clientMessageId: 'download' })).toBe(false);
  expect(controller.cancelFileUpload({ fileTransfers: transfers, clientMessageId: 'download' })).toBe(false);
  expect(controller.cancelFileUpload({ fileTransfers: transfers, clientMessageId: 'missing' })).toBe(false);

  expect(controller.cancelFileUpload({ fileTransfers: transfers, clientMessageId })).toBe(true);
  expect(pending.transfer.cancelled).toBe(true);
  expect(controller.cancelFileUpload({ fileTransfers: transfers, clientMessageId })).toBe(false);
});

test('canSendComposedMessage validates text and attachment messages', () => {
  expect(canSendComposedMessage({ body: ' hello ', type: 'text', attachments: [] })).toBe(true);
  expect(canSendComposedMessage({ body: ' ', type: 'text', attachments: [] })).toBe(false);
  expect(canSendComposedMessage({ body: '', type: 'sticker', attachments: [_stickerAttachment()] })).toBe(true);
  expect(canSendComposedMessage({ body: 'ignored', type: 'sticker', attachments: [] })).toBe(false);
});

test('markRead forwards the latest message id to the API', async () => {
  let requests = 0;
  const api = _api(async (request) => {
    requests++;
    expect(request.method).toBe('POST');
    expect(request.url.pathname).toBe('/api/v1/rooms/room_1/read');
    expect(_jsonBody(request)).toEqual({ last_read_message_id: 'msg_2' });
    if (requests === 1) {
      throw new ClientException('Connection reset by peer', request.url.toString());
    }
    return { statusCode: 200, body: JSON.stringify({ ok: true, unread_count: 0 }) };
  });

  const unread = await new MessagesController({ api }).markRead({ roomId: 'room_1', lastReadMessageId: 'msg_2' });

  expect(unread).toBe(0);
  expect(requests).toBe(2);
});

const _sender = new UserSummary({
  id: 'user_1',
  username: 'alice',
  displayName: 'Alice',
  avatarUrl: null,
  defaultAvatarKey: 'blue-3',
});

function _message(id: string, opts: { clientMessageId: string }): Message {
  return new Message({
    id,
    roomId: 'room_1',
    sender: _sender,
    clientMessageId: opts.clientMessageId,
    body: id,
    createdAt: new Date(Date.UTC(2026, 5, 4)),
  });
}

function _messageJson(opts: { clientMessageId: string; body: string; type?: string; attachments?: unknown[] }): JsonMap {
  return {
    id: 'msg_1',
    room_id: 'room_1',
    sender: {
      id: _sender.id,
      username: _sender.username,
      display_name: _sender.displayName,
    },
    client_message_id: opts.clientMessageId,
    type: opts.type ?? 'text',
    body: opts.body,
    attachments: opts.attachments ?? [],
    created_at: '2026-05-31T14:00:00Z',
  };
}

function _assetJson(opts: { id: string; filename: string; sizeBytes: number; mimeType?: string }): JsonMap {
  return {
    id: opts.id,
    filename: opts.filename,
    size_bytes: opts.sizeBytes,
    url: `/assets/${opts.id}/${opts.filename}`,
    thumbnail_url: null,
    mime_type: opts.mimeType ?? 'application/pdf',
  };
}

function _stickerAttachment(): MessageAttachment {
  return new MessageAttachment({
    type: 'sticker',
    asset: new UploadedAsset({
      id: 'sticker_asset',
      url: '/assets/sticker.webp',
      thumbnailUrl: null,
      mimeType: 'image/webp',
    }),
  });
}
