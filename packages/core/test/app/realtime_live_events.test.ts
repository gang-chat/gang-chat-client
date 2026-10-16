import { expect, test } from 'vitest';

import { isLiveSnapshotRealtimeEventType } from '../../src/app/realtime_live_events';

test('all server live reconnect snapshots use the live state route', () => {
  expect(
    [
      'live_participant_reconnecting',
      'live_participant_reconnected',
      'live_participants_reconciled',
      'live_room_reconnecting',
    ].every(isLiveSnapshotRealtimeEventType),
  ).toBe(true);
});

test('unrelated realtime events stay on their dedicated routes', () => {
  expect(isLiveSnapshotRealtimeEventType('room_updated')).toBe(false);
  expect(isLiveSnapshotRealtimeEventType('message_created')).toBe(false);
});
