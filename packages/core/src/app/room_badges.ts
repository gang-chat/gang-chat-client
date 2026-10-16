export class RoomNavigationBadgePatch {
  constructor(opts: {
    hasPendingRoomInvites: boolean;
    selectedRoomHasPendingJoinRequests: boolean;
    pendingRoomNotificationCount?: number | null;
  }) {
    this.hasPendingRoomInvites = opts.hasPendingRoomInvites;
    this.selectedRoomHasPendingJoinRequests = opts.selectedRoomHasPendingJoinRequests;
    this.pendingRoomNotificationCount = opts.pendingRoomNotificationCount ?? null;
  }

  readonly hasPendingRoomInvites: boolean;
  readonly selectedRoomHasPendingJoinRequests: boolean;
  readonly pendingRoomNotificationCount: number | null;
}

export function roomNavigationBadgesReset(opts: {
  currentHasPendingRoomInvites: boolean;
  currentSelectedRoomHasPendingJoinRequests: boolean;
}): RoomNavigationBadgePatch | null {
  if (!opts.currentHasPendingRoomInvites && !opts.currentSelectedRoomHasPendingJoinRequests) {
    return null;
  }
  return new RoomNavigationBadgePatch({
    hasPendingRoomInvites: false,
    selectedRoomHasPendingJoinRequests: false,
    pendingRoomNotificationCount: 0,
  });
}

export function roomInviteBadgeUpdated(opts: {
  currentHasPendingRoomInvites: boolean;
  currentSelectedRoomHasPendingJoinRequests: boolean;
  hasPending: boolean;
  currentPendingRoomNotificationCount?: number | null;
  pendingRoomNotificationCount?: number | null;
}): RoomNavigationBadgePatch | null {
  const currentPendingRoomNotificationCount = opts.currentPendingRoomNotificationCount ?? null;
  const pendingRoomNotificationCount = opts.pendingRoomNotificationCount ?? null;
  if (
    opts.currentHasPendingRoomInvites === opts.hasPending &&
    (pendingRoomNotificationCount == null || currentPendingRoomNotificationCount === pendingRoomNotificationCount)
  ) {
    return null;
  }
  return new RoomNavigationBadgePatch({
    hasPendingRoomInvites: opts.hasPending,
    selectedRoomHasPendingJoinRequests: opts.currentSelectedRoomHasPendingJoinRequests,
    pendingRoomNotificationCount,
  });
}

export function selectedJoinRequestBadgeUpdated(opts: {
  currentHasPendingRoomInvites: boolean;
  currentSelectedRoomHasPendingJoinRequests: boolean;
  hasPending: boolean;
}): RoomNavigationBadgePatch | null {
  if (opts.currentSelectedRoomHasPendingJoinRequests === opts.hasPending) return null;
  return new RoomNavigationBadgePatch({
    hasPendingRoomInvites: opts.currentHasPendingRoomInvites,
    selectedRoomHasPendingJoinRequests: opts.hasPending,
  });
}
