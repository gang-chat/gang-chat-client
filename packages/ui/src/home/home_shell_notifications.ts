import * as room_badges from '@gang-chat/core/app/room_badges';
import * as room_display from '@gang-chat/core/app/room_display';
import * as room_invites from '@gang-chat/core/app/room_invites';
import * as room_join from '@gang-chat/core/app/room_join';
import * as room_notifications from '@gang-chat/core/app/room_notifications';
import { userFacingErrorMessage } from '@gang-chat/core/app/error_display';
import { unawaited } from '@gang-chat/core/lib/async';
import type {
  PublicRoom,
  RoomApplication,
  RoomDetail,
  RoomEventNotification,
  RoomInvite,
} from '@gang-chat/core/protocol/models';

import type { HomeShellState } from './home_shell';
import { _ContentMode } from './home_shell';

/** Port of `extension _HomeShellNotifications on _HomeShellState`. */
export const HomeShellNotifications = {
  _canShowJoinRequestBadgeForRoom(this: HomeShellState, room: RoomDetail): boolean {
    const access = room_display.roomAccessState({
      room,
      currentUser: this._currentUser,
    });
    return access.canReviewJoinRequests && room_invites.roomInvitesEnabled(room.joinPolicy);
  },

  _applyRoomNavigationBadgePatch(this: HomeShellState, patch: room_badges.RoomNavigationBadgePatch | null): void {
    if (patch == null || !this.mounted) return;
    this._setHomeState(() => {
      this._hasPendingRoomInvites = patch.hasPendingRoomInvites;
      if (patch.pendingRoomNotificationCount != null) {
        this._pendingRoomNotificationCount = patch.pendingRoomNotificationCount;
      }
      this._selectedRoomHasPendingJoinRequests = patch.selectedRoomHasPendingJoinRequests;
    });
  },

  _setSelectedJoinRequestBadge(this: HomeShellState, hasPending: boolean): void {
    this._applyRoomNavigationBadgePatch(
      room_badges.selectedJoinRequestBadgeUpdated({
        currentHasPendingRoomInvites: this._hasPendingRoomInvites,
        currentSelectedRoomHasPendingJoinRequests: this._selectedRoomHasPendingJoinRequests,
        hasPending,
      }),
    );
  },

  _handleSelectedJoinRequestsChanged(this: HomeShellState, hasPending: boolean): void {
    const room = this._selectedRoom;
    if (room == null) return;
    this._setSelectedJoinRequestBadge(hasPending && this._canShowJoinRequestBadgeForRoom(room));
  },

  async _refreshSelectedJoinRequestBadge(this: HomeShellState, room?: RoomDetail | null): Promise<void> {
    const target = room ?? this._selectedRoom;
    if (target == null) {
      this._setSelectedJoinRequestBadge(false);
      return;
    }
    if (!this._canShowJoinRequestBadgeForRoom(target)) {
      this._setSelectedJoinRequestBadge(false);
      return;
    }
    try {
      const hasPending = await this._roomsController.hasPendingJoinRequests(target, {
        canReviewJoinRequests: true,
      });
      if (!this.mounted || this._selectedServerId !== target.id) return;
      const currentRoom = this._selectedRoom;
      this._setSelectedJoinRequestBadge(
        hasPending &&
          currentRoom != null &&
          currentRoom.id === target.id &&
          this._canShowJoinRequestBadgeForRoom(currentRoom),
      );
    } catch {
      // Badge refreshes are best-effort.
    }
  },

  _openNotifications(this: HomeShellState, opts: { openContent: boolean }): void {
    const { openContent } = opts;
    if (!this._settingsOpen && this._contentMode === _ContentMode.notifications) {
      this._closeNotifications();
      return;
    }
    this._setHomeState(() => {
      this._settingsOpen = false;
      this._contentMode = _ContentMode.notifications;
      this._auxiliaryOpenedFromNarrowSidebar = openContent;
      if (openContent) this._narrowContentOpen = true;
    });
    unawaited(
      this._loadNotifications({
        silent:
          this._notificationInvites.length > 0 ||
          this._notificationApplications.length > 0 ||
          this._notificationRoomEvents.length > 0,
      }),
    );
  },

  _closeNotifications(this: HomeShellState): void {
    this._setHomeState(() => {
      this._clearDeferredRoomNotificationVisualMarkersInState();
      this._contentMode = _ContentMode.chat;
      this._restorePaneAfterAuxiliaryInState();
    });
  },

  _openNotificationRoom(this: HomeShellState, room: PublicRoom): void {
    if (!room.joined) return;
    this._setHomeState(() => this._clearDeferredRoomNotificationVisualMarkersInState());
    this._selectServer(this._roomCardForPublicRoom(room), { openContent: this._compactLayout });
  },

  _openNotificationRoomEvent(this: HomeShellState, notification: RoomEventNotification): void {
    const room = notification.room;
    if (!room.joined) return;
    this._setHomeState(() => this._clearDeferredRoomNotificationVisualMarkersInState());
    this._selectServer(this._roomCardForPublicRoom(room), {
      openContent: this._compactLayout,
      focusMessageId: notification.messageId,
    });
  },

  async _loadNotifications(
    this: HomeShellState,
    opts: { silent?: boolean; clearVisualReadMarkers?: boolean } = {},
  ): Promise<void> {
    const { silent = false, clearVisualReadMarkers = false } = opts;
    if (!silent) {
      this._setHomeState(() => {
        this._loadingNotifications = true;
        this._notificationError = null;
      });
    }

    try {
      const [invites, applications, roomEvents] = await Promise.all([
        this._roomsController.listRoomInvites({ status: 'all' }),
        this._roomsController.listRoomApplications({ status: 'all' }),
        this._roomsController.listRoomNotifications(),
      ]);
      if (!this.mounted) return;
      this._setHomeState(() => {
        const visualReadAt = clearVisualReadMarkers ? this._deferredRoomNotificationVisualReadAt : null;
        const visualReadIds: ReadonlySet<string> = clearVisualReadMarkers
          ? this._deferredRoomNotificationVisualReadIds
          : new Set<string>();
        this._notificationInvites = invites;
        this._notificationApplications = applications;
        this._notificationRoomEvents =
          visualReadAt == null || visualReadIds.size === 0
            ? roomEvents
            : room_notifications.markUnreadRoomEventNotificationsRead({
                notifications: roomEvents,
                readAt: visualReadAt,
                notificationIds: visualReadIds,
              });
        if (clearVisualReadMarkers) {
          this._deferredRoomNotificationVisualReadAt = null;
          this._deferredRoomNotificationVisualReadIds = new Set<string>();
        }
        this._loadingNotifications = false;
        this._notificationError = null;
        this._pendingRoomNotificationCount = room_notifications.pendingRoomNotificationCount({
          invites,
          applications,
          roomEvents,
        });
        this._hasPendingRoomInvites = this._pendingRoomNotificationCount > 0;
      });
      if (this._contentMode === _ContentMode.notifications) {
        unawaited(this._markLoadedRoomNotificationsRead(roomEvents));
      }
    } catch (error) {
      if (!this.mounted) return;
      this._setHomeState(() => {
        this._loadingNotifications = false;
        this._notificationError = userFacingErrorMessage(error);
      });
    }
  },

  async _refreshPendingRoomInviteBadge(this: HomeShellState): Promise<void> {
    try {
      const [invites, applications, roomEvents] = await Promise.all([
        this._roomsController.listRoomInvites(),
        this._roomsController.listRoomApplications(),
        this._roomsController.listRoomNotifications(),
      ]);
      if (!this.mounted) return;
      this._setHomeState(() => {
        const pendingCount = room_notifications.pendingRoomNotificationCount({
          invites,
          applications,
          roomEvents,
        });
        const patch = room_badges.roomInviteBadgeUpdated({
          currentHasPendingRoomInvites: this._hasPendingRoomInvites,
          currentPendingRoomNotificationCount: this._pendingRoomNotificationCount,
          currentSelectedRoomHasPendingJoinRequests: this._selectedRoomHasPendingJoinRequests,
          hasPending: pendingCount > 0,
          pendingRoomNotificationCount: pendingCount,
        });
        if (patch == null) return;
        this._hasPendingRoomInvites = patch.hasPendingRoomInvites;
        this._pendingRoomNotificationCount = pendingCount;
        this._selectedRoomHasPendingJoinRequests = patch.selectedRoomHasPendingJoinRequests;
      });
    } catch {
      // Badge refreshes are best-effort.
    }
  },

  async _markLoadedRoomNotificationsRead(this: HomeShellState, roomEvents: readonly RoomEventNotification[]): Promise<void> {
    const unreadIds = new Set(
      roomEvents.filter((notification) => notification.isUnread).map((notification) => notification.id),
    );
    if (unreadIds.size === 0) return;
    try {
      await this._roomsController.markRoomNotificationsRead();
      if (!this.mounted) return;
      this._setHomeState(() => {
        this._deferredRoomNotificationVisualReadAt = new Date();
        this._deferredRoomNotificationVisualReadIds = new Set([...this._deferredRoomNotificationVisualReadIds, ...unreadIds]);
        const pendingCount = room_notifications.pendingRoomNotificationCount({
          invites: this._notificationInvites,
          applications: this._notificationApplications,
        });
        this._pendingRoomNotificationCount = pendingCount;
        this._hasPendingRoomInvites = pendingCount > 0;
      });
    } catch {
      // The next refresh retries the read marker.
    }
  },

  _clearDeferredRoomNotificationVisualMarkersInState(this: HomeShellState): void {
    const readAt = this._deferredRoomNotificationVisualReadAt;
    const readIds = this._deferredRoomNotificationVisualReadIds;
    if (readAt == null || readIds.size === 0) return;
    this._notificationRoomEvents = room_notifications.markUnreadRoomEventNotificationsRead({
      notifications: this._notificationRoomEvents,
      readAt,
      notificationIds: readIds,
    });
    this._deferredRoomNotificationVisualReadAt = null;
    this._deferredRoomNotificationVisualReadIds = new Set<string>();
  },

  async _reviewNotificationInvite(this: HomeShellState, invite: RoomInvite, accept: boolean): Promise<void> {
    if (
      !room_notifications.canReviewNotificationInvite({
        invite,
        busyInviteId: this._busyNotificationInviteId,
      })
    ) {
      return;
    }

    let reason: string | null = null;
    let startedBeforeDialog = false;
    if (
      accept &&
      room_notifications.roomInviteAcceptRequiresApplication(invite, {
        roomInvites: this._notificationInvites,
      })
    ) {
      this._setHomeState(() => {
        this._busyNotificationInviteId = invite.id;
        this._notificationError = null;
      });
      startedBeforeDialog = true;
      const rawReason = await this._showJoinApplicationDialog(invite.room);
      if (rawReason == null || !this.mounted) {
        if (this.mounted) {
          this._setHomeState(() => {
            this._busyNotificationInviteId = null;
          });
        }
        return;
      }
      reason = room_join.joinRequestReasonValue(rawReason);
    }

    if (!startedBeforeDialog) {
      this._setHomeState(() => {
        this._busyNotificationInviteId = invite.id;
        this._notificationError = null;
      });
    }

    try {
      const result = await this._roomsController.reviewRoomInvite({
        inviteId: invite.id,
        accept,
        reason,
      });
      if (!this.mounted) return;
      this._setHomeState(() => {
        this._busyNotificationInviteId = null;
        const room = result.room;
        if (accept && room != null) {
          this._servers = this._roomsController.patchRoomCardUpserted({
            rooms: this._servers,
            room: room.toCard(),
          }).rooms;
        }
      });
      await this._loadNotifications({ silent: true });
      if (accept && result.joined) unawaited(this._loadServersSilently());
    } catch (error) {
      if (!this.mounted) return;
      this._setHomeState(() => {
        this._busyNotificationInviteId = null;
        this._notificationError = userFacingErrorMessage(error);
      });
    }
  },

  async _withdrawNotificationApplication(this: HomeShellState, application: RoomApplication): Promise<void> {
    if (
      !room_notifications.canWithdrawNotificationApplication({
        application,
        busyApplicationId: this._busyNotificationApplicationId,
      })
    ) {
      return;
    }

    this._setHomeState(() => {
      this._busyNotificationApplicationId = application.id;
      this._notificationError = null;
    });

    try {
      await this._roomsController.withdrawRoomApplication({ requestId: application.id });
      if (!this.mounted) return;
      this._setHomeState(() => {
        this._busyNotificationApplicationId = null;
      });
      await this._loadNotifications({ silent: true });
    } catch (error) {
      if (!this.mounted) return;
      this._setHomeState(() => {
        this._busyNotificationApplicationId = null;
        this._notificationError = userFacingErrorMessage(error);
      });
    }
  },

  async _copyNotification(this: HomeShellState, item: room_notifications.RoomNotificationItem): Promise<void> {
    const text = room_notifications.roomNotificationCopyText(item);
    if (text.length === 0) {
      throw new Error('这条通知没有可复制的内容');
    }
    await this._clipboardService.writeText(text);
  },

  async _deleteNotification(this: HomeShellState, item: room_notifications.RoomNotificationItem): Promise<void> {
    await this._deleteNotifications([item]);
  },

  async _deleteNotifications(
    this: HomeShellState,
    items: readonly room_notifications.RoomNotificationItem[],
  ): Promise<void> {
    const byId = new Map<string, room_notifications.RoomNotificationItem>();
    for (const item of items) byId.set(item.id, item);
    const uniqueItems = [...byId.values()];
    if (uniqueItems.length === 0) return;
    try {
      await Promise.all(
        uniqueItems.map((item) =>
          this._roomsController.deleteRoomNotification({
            notificationType: room_notifications.roomNotificationDeletionType(item),
            notificationId: room_notifications.roomNotificationDeletionId(item),
          }),
        ),
      );
    } finally {
      if (this.mounted) await this._loadNotifications({ silent: true });
    }
  },

  _applyRoomInvitesUpdated(this: HomeShellState): void {
    unawaited(this._refreshPendingRoomInviteBadge());
    if (this._contentMode === _ContentMode.notifications) {
      unawaited(this._loadNotifications({ silent: true }));
    }
  },

  _applyRoomApplicationsUpdated(this: HomeShellState): void {
    unawaited(this._refreshPendingRoomInviteBadge());
    if (this._contentMode === _ContentMode.notifications) {
      unawaited(this._loadNotifications({ silent: true }));
    }
  },

  _applyRoomNotificationsUpdated(this: HomeShellState): void {
    unawaited(this._refreshPendingRoomInviteBadge());
    if (this._contentMode === _ContentMode.notifications) {
      unawaited(this._loadNotifications({ silent: true }));
    }
  },
};
