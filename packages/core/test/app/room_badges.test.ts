import { expect, test } from 'vitest';

import {
  roomInviteBadgeUpdated,
  roomNavigationBadgesReset,
  selectedJoinRequestBadgeUpdated,
} from '../../src/app/room_badges';

test('room navigation badge reset is a no-op when already clear', () => {
  expect(
    roomNavigationBadgesReset({
      currentHasPendingRoomInvites: false,
      currentSelectedRoomHasPendingJoinRequests: false,
    }),
  ).toBeNull();

  const patch = roomNavigationBadgesReset({
    currentHasPendingRoomInvites: true,
    currentSelectedRoomHasPendingJoinRequests: true,
  });

  expect(patch?.hasPendingRoomInvites).toBe(false);
  expect(patch?.selectedRoomHasPendingJoinRequests).toBe(false);
});

test('room invite badge update preserves selected join request badge', () => {
  expect(
    roomInviteBadgeUpdated({
      currentHasPendingRoomInvites: true,
      currentSelectedRoomHasPendingJoinRequests: false,
      hasPending: true,
    }),
  ).toBeNull();

  const patch = roomInviteBadgeUpdated({
    currentHasPendingRoomInvites: false,
    currentSelectedRoomHasPendingJoinRequests: true,
    hasPending: true,
  });

  expect(patch?.hasPendingRoomInvites).toBe(true);
  expect(patch?.selectedRoomHasPendingJoinRequests).toBe(true);
});

test('selected join request badge update preserves room invite badge', () => {
  expect(
    selectedJoinRequestBadgeUpdated({
      currentHasPendingRoomInvites: true,
      currentSelectedRoomHasPendingJoinRequests: false,
      hasPending: false,
    }),
  ).toBeNull();

  const patch = selectedJoinRequestBadgeUpdated({
    currentHasPendingRoomInvites: true,
    currentSelectedRoomHasPendingJoinRequests: false,
    hasPending: true,
  });

  expect(patch?.hasPendingRoomInvites).toBe(true);
  expect(patch?.selectedRoomHasPendingJoinRequests).toBe(true);
});
