import { expect, test } from 'vitest';

import {
  RoomMessageHistoryCategory,
  RoomMessageHistoryCategoryValue,
  roomMessageHistoryCopyText,
  roomMessageHistoryDayEndExclusive,
  roomMessageHistoryDayStart,
} from '../../src/app/room_message_history';
import { Message, UserSummary } from '../../src/protocol/models';

test('history categories expose stable server values', () => {
  expect(Object.values(RoomMessageHistoryCategory).map(RoomMessageHistoryCategoryValue.apiValue)).toEqual([
    'all',
    'links',
    'voice',
    'stickers',
    'images',
    'files',
    'music',
    'system',
  ]);
});

test('history copy contains only message content', () => {
  const message = new Message({
    id: 'msg_1',
    roomId: 'room_1',
    sender: new UserSummary({
      id: 'user_1',
      username: 'alice',
      displayName: 'Alice',
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
    }),
    clientMessageId: 'client_1',
    body: '  hello world  ',
    createdAt: new Date(Date.UTC(2026, 6, 13, 8, 30)),
  });

  expect(roomMessageHistoryCopyText(message)).toBe('hello world');
});

test('date filtering uses a local inclusive end day', () => {
  const day = new Date(2026, 6, 13, 20, 30);

  expect(roomMessageHistoryDayStart(day)).toEqual(new Date(2026, 6, 13));
  expect(roomMessageHistoryDayEndExclusive(day)).toEqual(new Date(2026, 6, 14));
});
