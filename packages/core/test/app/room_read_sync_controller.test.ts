import { afterEach, expect, test } from 'vitest';

import { MessagesController } from '../../src/app/messages_controller';
import { RoomReadSyncController } from '../../src/app/room_read_sync_controller';
import { Completer } from '../../src/lib/async';

const tearDowns: Array<() => void> = [];
afterEach(() => {
  for (const tearDown of tearDowns.splice(0)) tearDown();
});

function _controller(onMarkRead: (roomId: string, messageId: string) => Promise<number>): RoomReadSyncController {
  const controller = new RoomReadSyncController({ messages: new _FakeMessagesController(onMarkRead) });
  tearDowns.push(() => controller.close());
  return controller;
}

test('serializes and coalesces read receipts for one room', async () => {
  const firstStarted = new Completer<void>();
  const releaseFirst = new Completer<void>();
  const requestedMessageIds: string[] = [];
  const controller = _controller(async (_, messageId) => {
    requestedMessageIds.push(messageId);
    if (requestedMessageIds.length === 1) {
      firstStarted.complete();
      await releaseFirst.promise;
    }
    return 0;
  });

  const first = controller.markRead({
    roomId: 'room_1',
    lastReadMessageId: 'msg_1',
    messageCreatedAt: new Date(Date.UTC(2026, 6, 14, 10)),
  });
  await firstStarted.promise;
  const second = controller.markRead({
    roomId: 'room_1',
    lastReadMessageId: 'msg_2',
    messageCreatedAt: new Date(Date.UTC(2026, 6, 14, 10, 1)),
  });
  releaseFirst.complete();
  await Promise.all([first, second]);

  expect(requestedMessageIds).toEqual(['msg_1', 'msg_2']);
  expect(controller.isSynced({ roomId: 'room_1', messageId: 'msg_2' })).toBe(true);
});

test('retains a failed receipt and retries it after reconnect', async () => {
  let requests = 0;
  const controller = _controller(async () => {
    requests++;
    if (requests === 1) throw new Error('offline');
    return 0;
  });

  await controller.markRead({
    roomId: 'room_1',
    lastReadMessageId: 'msg_2',
    messageCreatedAt: new Date(Date.UTC(2026, 6, 14, 10)),
  });
  expect(controller.isSynced({ roomId: 'room_1', messageId: 'msg_2' })).toBe(false);

  await controller.retryPending();

  expect(requests).toBe(2);
  expect(controller.isSynced({ roomId: 'room_1', messageId: 'msg_2' })).toBe(true);
});

test('does not replace a failed newer receipt with an older intent', async () => {
  const requestedMessageIds: string[] = [];
  const controller = _controller(async (_, messageId) => {
    requestedMessageIds.push(messageId);
    if (requestedMessageIds.length === 1) throw new Error('offline');
    return 0;
  });

  await controller.markRead({
    roomId: 'room_1',
    lastReadMessageId: 'msg_2',
    messageCreatedAt: new Date(Date.UTC(2026, 6, 14, 10, 1)),
  });
  await controller.markRead({
    roomId: 'room_1',
    lastReadMessageId: 'msg_1',
    messageCreatedAt: new Date(Date.UTC(2026, 6, 14, 10)),
  });

  expect(requestedMessageIds).toEqual(['msg_2', 'msg_2']);
  expect(controller.isSynced({ roomId: 'room_1', messageId: 'msg_2' })).toBe(true);
});

test('does not resend an already synced receipt', async () => {
  let requests = 0;
  const controller = _controller(async () => {
    requests++;
    return 0;
  });

  await controller.markRead({
    roomId: 'room_1',
    lastReadMessageId: 'msg_2',
    messageCreatedAt: new Date(Date.UTC(2026, 6, 14, 10)),
  });
  await controller.markRead({
    roomId: 'room_1',
    lastReadMessageId: 'msg_2',
    messageCreatedAt: new Date(Date.UTC(2026, 6, 14, 10)),
  });

  expect(requests).toBe(1);
});

test('drains different rooms independently', async () => {
  const roomOneStarted = new Completer<void>();
  const releaseRoomOne = new Completer<void>();
  const requestedRooms: string[] = [];
  const controller = _controller(async (roomId) => {
    requestedRooms.push(roomId);
    if (roomId === 'room_1') {
      roomOneStarted.complete();
      await releaseRoomOne.promise;
    }
    return 0;
  });

  const roomOne = controller.markRead({
    roomId: 'room_1',
    lastReadMessageId: 'msg_1',
    messageCreatedAt: new Date(Date.UTC(2026, 6, 14, 10)),
  });
  await roomOneStarted.promise;
  await controller.markRead({
    roomId: 'room_2',
    lastReadMessageId: 'msg_2',
    messageCreatedAt: new Date(Date.UTC(2026, 6, 14, 10)),
  });

  expect(requestedRooms).toEqual(expect.arrayContaining(['room_1', 'room_2']));
  expect(controller.isSynced({ roomId: 'room_2', messageId: 'msg_2' })).toBe(true);
  releaseRoomOne.complete();
  await roomOne;
});

test('close ignores a successful response that arrives late', async () => {
  const started = new Completer<void>();
  const release = new Completer<void>();
  let requests = 0;
  const controller = new RoomReadSyncController({
    messages: new _FakeMessagesController(async () => {
      requests++;
      started.complete();
      await release.promise;
      return 0;
    }),
  });

  const pending = controller.markRead({
    roomId: 'room_1',
    lastReadMessageId: 'msg_1',
    messageCreatedAt: new Date(Date.UTC(2026, 6, 14, 10)),
  });
  await started.promise;
  controller.close();
  release.complete();
  await pending;
  await controller.retryPending();

  expect(requests).toBe(1);
  expect(controller.isSynced({ roomId: 'room_1', messageId: 'msg_1' })).toBe(false);
});

test('uses the message id as a stable tie-breaker for equal timestamps', async () => {
  const requestedMessageIds: string[] = [];
  const controller = _controller(async (_, messageId) => {
    requestedMessageIds.push(messageId);
    return 0;
  });
  const createdAt = new Date(Date.UTC(2026, 6, 14, 10));

  await controller.markRead({ roomId: 'room_1', lastReadMessageId: 'msg_b', messageCreatedAt: createdAt });
  await controller.markRead({ roomId: 'room_1', lastReadMessageId: 'msg_a', messageCreatedAt: createdAt });

  expect(requestedMessageIds).toEqual(['msg_b']);
  expect(controller.isSynced({ roomId: 'room_1', messageId: 'msg_b' })).toBe(true);
});

test('ignores blank room and message identifiers', async () => {
  let requests = 0;
  const controller = _controller(async () => {
    requests++;
    return 0;
  });
  const createdAt = new Date(Date.UTC(2026, 6, 14, 10));

  await controller.markRead({ roomId: '', lastReadMessageId: 'msg_1', messageCreatedAt: createdAt });
  await controller.markRead({ roomId: 'room_1', lastReadMessageId: '', messageCreatedAt: createdAt });

  expect(requests).toBe(0);
});

class _FakeMessagesController extends MessagesController {
  constructor(readonly onMarkRead: (roomId: string, messageId: string) => Promise<number>) {
    super();
  }

  override markRead(opts: { roomId: string; lastReadMessageId: string }): Promise<number> {
    return this.onMarkRead(opts.roomId, opts.lastReadMessageId);
  }
}
