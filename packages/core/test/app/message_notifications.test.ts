import { expect, test } from 'vitest';

import {
  RealtimeMessageNotificationTracker,
  realtimeMessageCreatedUpdateReason,
} from '../../src/app/message_notifications';

test('only explicit realtime message updates under all policy notify', () => {
  const tracker = new RealtimeMessageNotificationTracker();

  expect(
    tracker.register({
      roomId: 'room-1',
      updateReason: '',
      notificationPolicy: 'all',
      messageId: 'message-history',
    }),
  ).toBe(false);
  expect(
    tracker.register({
      roomId: 'room-1',
      updateReason: realtimeMessageCreatedUpdateReason,
      notificationPolicy: 'all',
      messageId: 'message-live',
    }),
  ).toBe(true);
});

test('duplicates stay silent even if the notification policy changes', () => {
  const tracker = new RealtimeMessageNotificationTracker();

  expect(
    tracker.register({
      roomId: 'room-1',
      updateReason: realtimeMessageCreatedUpdateReason,
      notificationPolicy: 'silent',
      messageId: 'message-1',
    }),
  ).toBe(false);
  expect(
    tracker.register({
      roomId: 'room-1',
      updateReason: realtimeMessageCreatedUpdateReason,
      notificationPolicy: 'all',
      messageId: 'message-1',
    }),
  ).toBe(false);
});

test('room id participates in deduplication and old entries are bounded', () => {
  const tracker = new RealtimeMessageNotificationTracker({ capacity: 2 });

  const register = (roomId: string, messageId: string) =>
    tracker.register({
      roomId,
      updateReason: realtimeMessageCreatedUpdateReason,
      notificationPolicy: 'all',
      messageId,
    });

  expect(register('room-1', 'message-1')).toBe(true);
  expect(register('room-2', 'message-1')).toBe(true);
  expect(register('room-3', 'message-3')).toBe(true);
  expect(register('room-1', 'message-1')).toBe(true);
});

test('clear permits notifications in a replacement account session', () => {
  const tracker = new RealtimeMessageNotificationTracker();

  expect(
    tracker.register({
      roomId: 'room-1',
      updateReason: realtimeMessageCreatedUpdateReason,
      notificationPolicy: 'all',
      messageId: 'message-1',
    }),
  ).toBe(true);
  tracker.clear();
  expect(
    tracker.register({
      roomId: 'room-1',
      updateReason: realtimeMessageCreatedUpdateReason,
      notificationPolicy: 'all',
      messageId: 'message-1',
    }),
  ).toBe(true);
});
