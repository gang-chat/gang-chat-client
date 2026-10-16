import { normalizedAudioVolume } from '@gang-chat/core/app/audio_levels';
import type { RealtimeConnectionStatus, RealtimeEvent } from '@gang-chat/core/app/realtime_controller';
import { isLiveSnapshotRealtimeEventType } from '@gang-chat/core/app/realtime_live_events';
import { unawaited } from '@gang-chat/core/lib/async';
import { asString, isJsonMap, type JsonMap } from '@gang-chat/core/lib/json';
import { LiveState, type RoomCard } from '@gang-chat/core/protocol/models';

import { _ContentMode, type HomeShellState } from './home_shell';

export const HomeShellRealtime = {
  _startRealtime(this: HomeShellState): void {
    this._realtimeEvents?.cancel();
    this._realtimeStatusEvents?.cancel();

    const realtime = this._services.realtime;
    realtime.onReconnect = () => this._onRealtimeReconnect();
    this._realtimeStatus = realtime.status;
    this._realtimeStatusEvents = realtime.statusChanges.listen((status) => this._onRealtimeStatusChanged(status));
    this._realtimeEvents = realtime.events.listen((event) => this._onRealtimeEvent(event));
    unawaited(realtime.start());
  },

  _onRealtimeStatusChanged(this: HomeShellState, status: RealtimeConnectionStatus): void {
    if (!this.mounted) return;
    this._setHomeState(() => {
      this._realtimeStatus = status;
    });
  },

  _onRealtimeReconnect(this: HomeShellState): void {
    if (!this.mounted) return;
    unawaited(this._androidPushRegistration?.synchronize().catch(() => {}));
    unawaited(this._services.roomReads.retryPending());
    unawaited(this._loadServersSilently());
    const selected = this._selectedServerId;
    if (selected != null) unawaited(this._refreshLiveSilently(selected));
    const joinedLiveRoomId = this._joinedLiveRoomId;
    if (joinedLiveRoomId != null && !this._liveSessionController.isAttachedToRoom(joinedLiveRoomId)) {
      unawaited(this._restoreLiveAfterRealtimeReconnect(joinedLiveRoomId));
    }
  },

  async _loadServersSilently(this: HomeShellState): Promise<void> {
    try {
      const servers = await this._roomsController.loadRooms();
      if (!this.mounted) return;
      this._setHomeState(() => {
        let nextServers = this._roomsController.patchRoomCardsRefreshed({ rooms: servers }).rooms;
        const selectedRoomId = this._selectedServerId;
        if (selectedRoomId != null) {
          nextServers = this._roomsController.patchRoomUnreadCleared({
            rooms: nextServers,
            roomId: selectedRoomId,
          }).rooms;
        }
        this._servers = nextServers;
      });
    } catch {
      // Silent refresh: keep the current list.
    }
  },

  async _refreshLiveSilently(this: HomeShellState, roomId: string): Promise<void> {
    try {
      const live = await this._roomsController.getLiveState(roomId);
      if (!this.mounted) return;
      const patch = this._roomsController.patchSelectedLiveRefreshed({
        live,
        selectedRoomId: this._selectedServerId,
        currentLive: this._live,
      });
      if (patch == null) return;
      this._cacheJoinedLiveParticipantUsers(patch.live);
      this._applyJoinedLiveAuthoritativeSnapshot(patch.live, { eventType: 'live_state_refreshed' });
      this._setHomeState(() => {
        this._live = patch.live;
        this._servers = this._roomsController.patchRoomLiveCount(this._servers, roomId, patch.live);
      });
      this._syncJoinedLiveAudioFromSnapshot(patch.live);
    } catch {
      // Silent refresh: keep the current snapshot.
    }
  },

  _onRealtimeEvent(this: HomeShellState, event: RealtimeEvent): void {
    if (isLiveSnapshotRealtimeEventType(event.type)) {
      this._applyLiveSnapshot(event.type, event.data);
      return;
    }
    switch (event.type) {
      case 'room_added':
        this._applyRoomAdded(event.data);
        break;
      case 'room_updated':
        this._applyRoomUpdated(event.data);
        break;
      case 'room_deleted':
        this._applyRoomDeleted(event.data);
        break;
      case 'room_invites_updated':
        this._applyRoomInvitesUpdated();
        break;
      case 'room_applications_updated':
        this._applyRoomApplicationsUpdated();
        break;
      case 'room_notifications_updated':
        this._applyRoomNotificationsUpdated();
        break;
      case 'room_role_changed':
        this._applyRoomRoleChanged(event.data);
        break;
      case 'room_member_profile_changed':
        this._applyRoomMemberProfileChanged(event.data);
        break;
      case 'room_join_requests_updated':
        this._applyRoomJoinRequestsUpdated(event.data);
        break;
      case 'account_suspended':
        unawaited(this._logoutSuspendedAccount());
        break;
      case 'music_box_changed':
        this._onMusicBoxChanged(event.data);
        break;
      case 'music_box_progress':
        this._onMusicBoxProgress(event.data);
        break;
      default:
        break;
    }
  },

  async _logoutSuspendedAccount(this: HomeShellState): Promise<void> {
    if (this._accountSuspensionLogoutInProgress) return;
    this._accountSuspensionLogoutInProgress = true;
    await this._logout();
  },

  _applyLiveSnapshot(this: HomeShellState, eventType: string, data: JsonMap): void {
    const eventRoomId = asString(data['room_id']);
    const liveJson = data['live'];
    let joinedRoomSnapshot: LiveState | null = null;
    if (eventRoomId === this._joinedLiveRoomId && isJsonMap(liveJson)) {
      try {
        joinedRoomSnapshot = LiveState.fromJson(liveJson);
        this._cacheJoinedLiveParticipantUsers(joinedRoomSnapshot);
        this._applyJoinedLiveAuthoritativeSnapshot(joinedRoomSnapshot, {
          eventType,
          eventData: data,
        });
      } catch {
        // A malformed snapshot only skips the presence bookkeeping.
      }
    }
    const patch = this._roomsController.patchLiveSnapshot({
      rooms: this._servers,
      selectedRoomId: this._selectedServerId,
      data,
      joinedLiveRoomId: this._joinedLiveRoomId,
      currentUserId: this._currentUser.id,
      previousLive: this._live,
      localParticipantConnected:
        eventRoomId != null &&
        this._liveSessionController.isAttachedToRoom(eventRoomId) &&
        this._liveSessionController.connectedParticipantIdentities.has(this._currentUser.id),
    });
    if (patch == null || !this.mounted) return;
    this._setHomeState(() => {
      this._servers = patch.rooms;
      if (patch.selectedLive != null) this._live = patch.selectedLive;
    });
    const selectedLive = patch.selectedLive;
    if (selectedLive != null) {
      this._syncJoinedLiveAudioFromSnapshot(selectedLive);
    } else if (joinedRoomSnapshot != null) {
      // Voice participation continues while the user browses another room.
      // Moderation/reconnect snapshots for that joined room must still update
      // the actual microphone/headphones state even though its roster is not
      // the currently rendered one.
      this._syncJoinedLiveAudioFromSnapshot(joinedRoomSnapshot);
    }
  },

  _applyRoomAdded(this: HomeShellState, data: JsonMap): void {
    const room = this._roomsController.roomCardFromSnapshot(data);
    if (room == null || !this.mounted) return;
    this._setHomeState(() => {
      this._servers = this._roomsController.patchRoomCardUpserted({ rooms: this._servers, room }).rooms;
    });
  },

  _applyRoomUpdated(this: HomeShellState, data: JsonMap): void {
    const previousJoinPolicy = this._selectedRoom?.joinPolicy ?? null;
    const room = this._roomsController.roomCardFromSnapshot(data);
    if (room == null || !this.mounted) return;
    const updateReason = data['update_reason'];
    const shouldNotifyMessage = this._messageNotificationTracker.register({
      roomId: room.id,
      updateReason: updateReason == null ? '' : String(updateReason),
      notificationPolicy: room.notificationPolicy,
      messageId: room.lastMessage?.id ?? null,
    });
    if (room.id === this._joinedLiveRoomId) {
      this._joinedLivePersonalAiVoiceAnnouncementsEnabled = room.aiVoiceAnnouncementsEnabled;
    }
    const shouldRefreshMessages =
      room.id === this._selectedServerId &&
      room.lastMessage != null &&
      room.lastMessage.id !== this._latestLoadedServerMessageId();
    const patch = this._roomsController.patchRoomUpdated({
      rooms: this._servers,
      incoming: room,
      selectedRoom: this._selectedRoom,
    });
    let selectedNewMessageCount: number | null = null;
    if (room.id === this._selectedServerId && room.hasUnreadCount) {
      // Account-scoped room snapshots are authoritative for unread state.
      // Apply a read receipt from another device even when the last message is
      // already loaded locally and no message refresh is necessary.
      selectedNewMessageCount = room.unreadCount;
    } else if (shouldRefreshMessages) {
      for (const candidate of patch.rooms) {
        if (candidate.id === room.id) {
          selectedNewMessageCount = candidate.unreadCount;
          break;
        }
      }
    }
    this._setHomeState(() => {
      this._servers = patch.rooms;
      this._selectedRoom = patch.selectedRoom;
      if (selectedNewMessageCount != null) {
        this._selectedRoomNewMessageCount = selectedNewMessageCount;
      }
      if (patch.shouldReloadMembers) this._membersReloadToken++;
    });
    const selectedRoom = patch.selectedRoom;
    if (selectedRoom != null && selectedRoom.id === room.id && previousJoinPolicy !== selectedRoom.joinPolicy) {
      unawaited(this._refreshSelectedJoinRequestBadge(selectedRoom));
    }
    if (shouldRefreshMessages) {
      unawaited(this._refreshSelectedMessagesSilently(room.id));
    }
    this._syncAndroidNotificationState();
    if (shouldNotifyMessage) this._notifyRealtimeRoomMessage(room);
  },

  _notifyRealtimeRoomMessage(this: HomeShellState, room: RoomCard): void {
    const androidSystemService = this.widget.androidSystemService;
    if (androidSystemService.isSupported && !this._isAppForeground) {
      const message = room.lastMessage;
      if (message != null) {
        unawaited(
          androidSystemService
            .showRoomMessage({
              roomId: room.id,
              roomName: room.displayName,
              sender: message.senderDisplayName,
              body: message.bodyPreview,
              unreadCount: this._androidUnreadCount,
              messageId: message.id,
            })
            .catch(() => {}),
        );
      }
      return;
    }
    const volume = this._headphonesMuted ? 0.0 : normalizedAudioVolume(this._liveSessionController.outputVolume);
    unawaited(this._messageNotificationSoundPlayer.play({ volume }).catch(() => {}));
    unawaited(this.widget.windowController.requestMessageAttention());
  },

  get _androidUnreadCount(): number {
    const self = this as unknown as HomeShellState;
    return self._servers.reduce((total, room) => total + room.unreadCount, 0);
  },

  _syncAndroidNotificationState(this: HomeShellState): void {
    const service = this.widget.androidSystemService;
    if (!service.isSupported) return;
    for (const room of this._servers) {
      if (room.unreadCount <= 0) {
        unawaited(service.cancelRoomNotification(room.id).catch(() => {}));
      }
    }
    unawaited(service.syncBadge(this._androidUnreadCount).catch(() => {}));
  },

  _latestLoadedServerMessageId(this: HomeShellState): string | null {
    return this._latestServerMessageId(this._messages);
  },

  async _refreshSelectedMessagesSilently(this: HomeShellState, roomId: string): Promise<void> {
    try {
      const messages = await this._messagesController.loadMessages(roomId);
      if (!this.mounted || this._selectedServerId !== roomId) return;
      const serverClientIds = new Set(messages.map((message) => message.clientMessageId));
      const pending = this._messages.filter(
        (message) => message.pending && !serverClientIds.has(message.clientMessageId),
      );
      const nextMessages = [...messages, ...pending];
      this._setHomeState(() => {
        this._messages = nextMessages;
      });
    } catch {
      // Silent refresh: keep the loaded messages.
    }
  },

  _applyRoomDeleted(this: HomeShellState, data: JsonMap): void {
    const patch = this._roomsController.patchRoomDeleted({
      rooms: this._servers,
      selectedRoomId: this._selectedServerId,
      selectedRoom: this._selectedRoom,
      selectedRoomHasPendingJoinRequests: this._selectedRoomHasPendingJoinRequests,
      messages: this._messages,
      live: this._live,
      livePanelOpen: this._contentMode === _ContentMode.live,
      settingsOpen: this._settingsOpen,
      joinedLiveRoomId: this._joinedLiveRoomId,
      data,
    });
    if (patch == null || !this.mounted) return;
    this._setHomeState(() => {
      this._discardRoomDraftInState(patch.roomId);
      this._servers = patch.rooms;
      this._selectedServerId = patch.selectedRoomId;
      this._selectedRoom = patch.selectedRoom;
      this._selectedRoomHasPendingJoinRequests = patch.selectedRoomHasPendingJoinRequests;
      this._messages = patch.messages;
      this._live = patch.live;
      this._settingsOpen = patch.settingsOpen;
      this._contentMode = patch.livePanelOpen ? _ContentMode.live : _ContentMode.chat;
      this._joinedLiveRoomId = patch.joinedLiveRoomId;
      if (patch.wasSelected) {
        for (const entry of this._stagedAttachments) {
          entry.uploadController.cancel();
        }
        this._stagedAttachments.length = 0;
        this._setComposerText('', { saveDraft: false });
        this._selectedRoomNewMessageCount = 0;
        this._fileTransfers = new Map();
        this._roomError = null;
        this._sendError = null;
        this._narrowContentOpen = false;
        this._resetMusicBox();
      }
    });
    if (this.widget.androidSystemService.isSupported) {
      unawaited(this.widget.androidSystemService.cancelRoomNotification(patch.roomId).catch(() => {}));
      this._syncAndroidNotificationState();
    }
    if (patch.shouldDisconnectLive) {
      unawaited(this._liveSessionController.disconnect());
    }
  },

  /**
   * Applies a `room_role_changed` event for the affected member (the current
   * user). The shared room snapshot omits `my_role`, so this is the only way a
   * promote/demote reaches the open room without a manual refetch. Updates the
   * selected room's membership role in place so permission-gated UI (manage,
   * review join requests) re-evaluates immediately.
   */
  _applyRoomRoleChanged(this: HomeShellState, data: JsonMap): void {
    const patch = this._roomsController.patchRoomRoleChanged({
      selectedRoom: this._selectedRoom,
      data,
    });
    if (patch == null || !this.mounted) return;
    this._setHomeState(() => {
      this._selectedRoom = patch.selectedRoom;
      // A role change can flip whether the user may review join requests, so
      // bump the members reload token to re-pull that list if the panel's open.
      this._membersReloadToken++;
    });
    unawaited(this._refreshSelectedJoinRequestBadge(patch.selectedRoom));
    // Music-box capabilities are actor-specific and derived from the current
    // room role. Refresh them immediately instead of waiting for the next
    // queue/playback mutation.
    if (patch.roomId === this._selectedServerId) {
      unawaited(this._loadMusicBox(patch.roomId, { refreshAfterCurrent: true }));
    }
  },

  /**
   * Applies a `room_join_requests_updated` event: the pending join-request set
   * for [roomId] changed (new request, or one approved/denied elsewhere). If
   * it targets the open room, nudge the members panel to reload its request
   * list via the reload token. Also refresh the notification badge, since a
   * new request may need the current user's attention.
   */
  _applyRoomJoinRequestsUpdated(this: HomeShellState, data: JsonMap): void {
    const roomId = asString(data['room_id']);
    if (!this.mounted) return;
    unawaited(this._refreshPendingRoomInviteBadge());
    if (roomId == null || roomId !== this._selectedServerId) return;
    this._setHomeState(() => {
      this._membersReloadToken++;
    });
    unawaited(this._refreshSelectedJoinRequestBadge());
  },

  _applyRoomMemberProfileChanged(this: HomeShellState, data: JsonMap): void {
    const roomId = asString(data['room_id']);
    if (!this.mounted || roomId == null || roomId !== this._selectedServerId) return;
    this._setHomeState(() => {
      this._membersReloadToken++;
    });
  },
};
