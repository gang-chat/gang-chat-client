import { useState } from 'react';

import { CloseBehavior, defaultCloseBehavior } from '@gang-chat/core/app/close_behavior';
import { userFacingErrorMessage } from '@gang-chat/core/app/error_display';
import * as room_display from '@gang-chat/core/app/room_display';
import * as member_filter from '@gang-chat/core/app/room_members_filter';
import type { RoomOpenSnapshot } from '@gang-chat/core/app/rooms_controller';
import { SettingsSection } from '@gang-chat/core/app/settings_shell_state';
import * as sticker_display from '@gang-chat/core/app/sticker_display';
import { unawaited, withTimeout } from '@gang-chat/core/lib/async';
import type { CurrentUser, RoomCard, RoomDetail, UserSummary } from '@gang-chat/core/protocol/models';
import type { AndroidTaskRemovalRequest } from '@gang-chat/core/shell/android_system_service';
import type { ReleaseDownloadCancellationToken } from '@gang-chat/core/shell/release_update_service';

import { Column, Expanded, Row, SizedBox, Text } from '../lib/layout';
import { useElementSize } from '../lib/use_element_size';
import { Button, ButtonTone } from '../ui/button';
import { DialogFrame, ResponsiveDialogAction, ResponsiveDialogActionBar } from '../ui/feedback';
import { Icon, Icons } from '../ui/icon';
import { showDialog } from '../ui/overlay';
import { UiSwitch } from '../ui/switch';
import { UiColors, UiTypography } from '../ui/tokens';
import { _ContentMode, type HomeShellState } from './home_shell';
import { _userWithLocalCommonRoomNames } from './home_shell_search';
import { RoomManagementResultKind, type RoomManagementResult } from './room_management';
import { UserProfileAction } from './room_profile_card';
import { RoomSettingsSection } from './room_settings_dialog';

import { useRef } from 'react';

export const HomeShellRoomActions = {
  /**
   * Fetches the full profile for a message sender so the hover card can show
   * fields the lightweight message summary omits (gender, common rooms). Falls
   * back to the summary we already have when the room context or request is
   * unavailable.
   */
  async _resolveSenderProfile(this: HomeShellState, sender: UserSummary): Promise<UserSummary> {
    const room = this._selectedRoom;
    if (room == null) return sender;
    let resolved = sender;
    try {
      const profile = await this._roomsController.getRoomMemberProfile({
        roomId: room.id,
        userId: sender.id,
      });
      resolved = room_display.resolvedRoomMemberProfileUser({ profile, fallback: sender });
    } catch {
      resolved = await this._resolveUserProfile(sender);
    }
    const withRole = room_display.roomUserInfoProfile({
      user: resolved,
      room,
      currentUser: this._currentUser,
    });
    return _userWithLocalCommonRoomNames(
      withRole.copyWith({
        commonRooms: room_display.roomUserInfoCommonRooms({
          user: withRole,
          selectedRoom: room,
          currentUser: this._currentUser,
          includeSelectedRoom: false,
        }),
      }),
      this._servers,
    );
  },

  async _loadServers(this: HomeShellState): Promise<void> {
    this._setHomeState(() => {
      this._loadingServers = true;
      this._serverLoadError = null;
    });

    try {
      const servers = await this._roomsController.loadRooms();
      if (!this.mounted) return;
      this._setHomeState(() => {
        let nextServers = servers;
        const selectedRoomId = this._selectedServerId;
        if (selectedRoomId != null) {
          nextServers = this._roomsController.patchRoomUnreadCleared({
            rooms: nextServers,
            roomId: selectedRoomId,
          }).rooms;
        }
        this._servers = nextServers;
        this._loadingServers = false;
        if (this._selectedServerId != null && !this._servers.some((server) => server.id === this._selectedServerId)) {
          const removedRoomId = this._selectedServerId;
          const shouldDisconnectLive = this._joinedLiveRoomId === removedRoomId;
          for (const entry of this._stagedAttachments) {
            entry.uploadController.cancel();
          }
          this._stagedAttachments.length = 0;
          this._discardRoomDraftInState(removedRoomId);
          this._setComposerText('', { saveDraft: false });
          this._selectedServerId = null;
          this._selectedRoom = null;
          this._live = null;
          this._messages = [];
          this._selectedRoomNewMessageCount = 0;
          this._fileTransfers = new Map();
          this._fileDownloads = new Map();
          this._selectedRoomHasPendingJoinRequests = false;
          this._resetMusicBox();
          this._contentMode = _ContentMode.chat;
          if (shouldDisconnectLive) {
            this._joinedLiveRoomId = null;
            unawaited(this._liveSessionController.disconnect());
          }
        }
      });
      this._syncAndroidNotificationState();
      this._openPendingAndroidNotificationRoom();
    } catch (error) {
      if (!this.mounted) return;
      this._setHomeState(() => {
        this._loadingServers = false;
        this._serverLoadError = userFacingErrorMessage(error, { fallback: '加载服务器失败' });
      });
    }
  },

  _selectServer(
    this: HomeShellState,
    server: RoomCard,
    opts: { openContent: boolean; focusMessageId?: string | null },
  ): void {
    unawaited(
      this._openRoom(server, {
        openContent: opts.openContent,
        focusMessageId: opts.focusMessageId,
      }),
    );
  },

  _handleFocusMessageHandled(this: HomeShellState, messageId: string): void {
    if (this._focusedMessageId !== messageId) return;
    this._setHomeState(() => {
      this._focusedMessageId = null;
    });
  },

  async _openRoom(
    this: HomeShellState,
    server: RoomCard,
    opts: { openContent: boolean; focusMessageId?: string | null },
  ): Promise<void> {
    const { openContent } = opts;
    const focusMessageId = opts.focusMessageId ?? null;
    if (this._loadingRoom && this._selectedServerId === server.id) return;
    this._storeSelectedComposerDraft();

    this._setHomeState(() => {
      this._clearDeferredRoomNotificationVisualMarkersInState();
      this._selectedServerId = server.id;
      this._restoreComposerDraftForRoom(server.id);
      this._selectedRoomNewMessageCount = server.unreadCount;
      this._clearRoomUnreadCount(server.id);
      this._settingsOpen = false;
      this._contentMode = _ContentMode.chat;
      if (openContent) this._narrowContentOpen = true;
      this._selectedRoom = null;
      this._live = null;
      this._messages = [];
      this._focusedMessageId = focusMessageId;
      this._fileTransfers = new Map();
      this._fileDownloads = new Map();
      this._membersInitialSearchQuery = '';
      this._selectedRoomHasPendingJoinRequests = false;
      this._roomError = null;
      this._sendError = null;
      this._resetMusicBox();
      this._stickerPanelState = sticker_display.stickerPanelReset({
        source: this._stickerPanelState.source,
      });
      this._loadingRoom = true;
    });
    if (this.widget.androidSystemService.isSupported) {
      unawaited(this.widget.androidSystemService.cancelRoomNotification(server.id));
      this._syncAndroidNotificationState();
    }

    try {
      const snapshot = await this._roomsController.openRoom(server.id);
      if (!this.mounted || this._selectedServerId !== server.id) return;
      let messages = snapshot.messages;
      if (focusMessageId != null && !messages.some((message) => message.id === focusMessageId)) {
        messages = await this._messagesController.loadMessagesUntil({
          roomId: server.id,
          messageId: focusMessageId,
        });
        if (!this.mounted || this._selectedServerId !== server.id) return;
      }
      this._setHomeState(() => {
        this._selectedRoom = snapshot.detail;
        this._live = snapshot.live;
        this._messages = messages;
        this._focusedMessageId = focusMessageId;
        this._loadingRoom = false;
        this._roomError = null;
      });
      unawaited(this._markRoomReadFromMessages(server.id, messages));
      unawaited(this._ensureComposerMentionMembers(server.id));
      unawaited(this._refreshSelectedJoinRequestBadge(snapshot.detail));
      unawaited(this._loadMusicBox(server.id));
    } catch (error) {
      if (!this.mounted || this._selectedServerId !== server.id) return;
      this._setHomeState(() => {
        this._loadingRoom = false;
        this._roomError = userFacingErrorMessage(error);
      });
    }
  },

  _toggleSettings(this: HomeShellState, opts: { openContent: boolean }): void {
    const { openContent } = opts;
    this._setHomeState(() => {
      this._clearDeferredRoomNotificationVisualMarkersInState();
      const opening = !this._settingsOpen;
      this._settingsOpen = opening;
      if (opening) {
        this._settingsInitialSection = SettingsSection.profile;
        this._settingsInitialMusicPlaylistId = null;
        this._contentMode = _ContentMode.chat;
        this._settingsAppUpdate = null;
      }
      this._auxiliaryOpenedFromNarrowSidebar = opening && openContent;
      if (openContent) {
        this._narrowContentOpen = opening;
      }
    });
  },

  _openSettingsSection(
    this: HomeShellState,
    section: SettingsSection,
    opts: { openContent: boolean; initialMusicPlaylistId?: string | null },
  ): void {
    const { openContent } = opts;
    this._setHomeState(() => {
      this._clearDeferredRoomNotificationVisualMarkersInState();
      this._settingsOpen = true;
      this._settingsInitialSection = section;
      this._settingsInitialMusicPlaylistId = opts.initialMusicPlaylistId ?? null;
      this._settingsAppUpdate = null;
      this._contentMode = _ContentMode.chat;
      this._auxiliaryOpenedFromNarrowSidebar = openContent;
      if (openContent) this._narrowContentOpen = true;
    });
  },

  _openCreateRoom(this: HomeShellState, opts: { openContent: boolean }): void {
    const { openContent } = opts;
    if (!this._settingsOpen && this._contentMode === _ContentMode.createRoom) {
      this._closeCreateRoom();
      return;
    }
    this._setHomeState(() => {
      this._clearDeferredRoomNotificationVisualMarkersInState();
      this._settingsOpen = false;
      this._contentMode = _ContentMode.createRoom;
      this._auxiliaryOpenedFromNarrowSidebar = openContent;
      if (openContent) this._narrowContentOpen = true;
    });
  },

  _closeCreateRoom(this: HomeShellState): void {
    this._setHomeState(() => {
      this._contentMode = _ContentMode.chat;
      this._restorePaneAfterAuxiliaryInState();
    });
  },

  _closeSettings(this: HomeShellState): void {
    this._setHomeState(() => {
      this._settingsOpen = false;
      this._settingsAppUpdate = null;
      this._restorePaneAfterAuxiliaryInState();
    });
  },

  async _confirmLogout(this: HomeShellState): Promise<void> {
    if (this._logoutConfirming) return;
    this._setHomeState(() => {
      this._logoutConfirming = true;
    });
    try {
      const confirmed = await showDialog<boolean>((close) => (
        <DialogFrame
          title="退出登录"
          icon={Icons.logout}
          actionBar={
            <ResponsiveDialogActionBar
              actions={[
                new ResponsiveDialogAction({ label: '取消', onPressed: () => close(false) }),
                new ResponsiveDialogAction({
                  label: '退出登录',
                  onPressed: () => close(true),
                  tone: ButtonTone.danger,
                }),
              ]}
            />
          }
        >
          <Text style={{ ...UiTypography.body, color: UiColors.textSecondary }}>确认退出当前账号？</Text>
        </DialogFrame>
      ));
      if (!this.mounted || confirmed !== true) return;
      await this._logout();
    } finally {
      if (this.mounted) {
        this._setHomeState(() => {
          this._logoutConfirming = false;
        });
      }
    }
  },

  async _logout(this: HomeShellState): Promise<void> {
    if (this.widget.androidSystemService.isSupported) {
      try {
        await this._androidPushRegistration?.unregister();
      } catch {
        // The server also ignores devices attached to the revoked session, so
        // logout remains safe when this best-effort cleanup is offline.
      }
      try {
        await this.widget.androidSystemService.clearMessageNotifications();
        await this.widget.androidSystemService.enterForeground();
      } catch {
        // Best effort.
      }
    }
    await this._leaveLiveForSessionEnd({ disconnectTimeout: 1000 });
    await this._resetLiveAudioStateAfterSessionEnd();
    await this.widget.app.logout();
  },

  async _resetLiveAudioStateAfterSessionEnd(this: HomeShellState): Promise<void> {
    if (this.mounted) {
      this._setHomeState(() => {
        this._micMuted = false;
        this._headphonesMuted = false;
        this._voiceBlocked = false;
      });
    }
    try {
      await this._liveSessionController.setInputVolume(this._restoredInputVolume());
    } catch {
      // Best effort.
    }
    try {
      await this._liveSessionController.setOutputVolume(this._restoredOutputVolume());
    } catch {
      // Best effort.
    }
    try {
      await this._liveSessionController.setMicMuted(false);
    } catch {
      // Best effort.
    }
    try {
      await this._liveSessionController.setOutputMuted(false);
    } catch {
      // Best effort.
    }
  },

  async _leaveLiveForSessionEnd(this: HomeShellState, opts: { disconnectTimeout?: number | null } = {}): Promise<void> {
    const joinedLiveRoomId = this._joinedLiveRoomId;
    this._joinedLiveRoomId = null;
    if (joinedLiveRoomId != null) {
      await this._notifyLiveLeft(joinedLiveRoomId);
    }
    await this._liveSessionController.disconnect({ timeout: opts.disconnectTimeout ?? null });
  },

  async _stopRealtimeForExit(this: HomeShellState): Promise<void> {
    try {
      await this._services.realtime.stop();
    } catch {
      // Best effort.
    }
  },

  async _handleAndroidTaskRemoved(this: HomeShellState, request: AndroidTaskRemovalRequest): Promise<void> {
    if (this._exitingApplication) {
      // Another explicit-exit path is already sending the same cleanup. Do not
      // acknowledge early and let native Android cut that request short; the
      // native grace timeout will still guarantee that the process terminates.
      return;
    }
    this._exitingApplication = true;
    try {
      await Promise.all([this._leaveLiveForAndroidTaskRemoval(), this._stopRealtimeForExit()]);
      await this.widget.app.exitSessionForAppExit();
    } catch {
      // Native Android enforces a short process-exit timeout and LiveKit's
      // participant_left webhook is the final cleanup path.
    } finally {
      request.complete();
    }
  },

  async _leaveLiveForAndroidTaskRemoval(this: HomeShellState): Promise<void> {
    const joinedLiveRoomId = this._joinedLiveRoomId;
    this._joinedLiveRoomId = null;

    // Task removal is terminal and has a strict native deadline. Do not put
    // this leave behind an in-flight mic/camera/screen-share PATCH. Start the
    // authenticated server leave and LiveKit disconnect together so either
    // path can remove the remote track and participant immediately.
    const cleanup: Promise<void>[] = [this._liveSessionController.disconnect({ timeout: 900 })];
    if (joinedLiveRoomId != null) {
      cleanup.push(
        (async () => {
          try {
            await this._liveController.leaveLive({ roomId: joinedLiveRoomId });
          } catch {
            // LiveKit's participant_left webhook remains the fallback.
          }
        })(),
      );
    }
    await Promise.all(cleanup);
  },

  _handleAppUpdateDownloadCancellationChanged(
    this: HomeShellState,
    token: ReleaseDownloadCancellationToken | null,
  ): void {
    if (this._appUpdateDownloadCancellationToken === token) return;
    this._setHomeState(() => {
      this._appUpdateDownloadCancellationToken = token;
      if (token != null) {
        this._searchExpanded = false;
        this._titleSearchContextMenuOpen = false;
        this._pendingTitleSearchContextMenuUpdate = null;
      }
    });
    if (token != null) {
      // FocusManager.instance.primaryFocus?.unfocus()
      if (typeof document !== 'undefined') {
        const active = document.activeElement;
        if (active instanceof HTMLElement) active.blur();
      }
      unawaited(this._leaveLiveForAppUpdateDownload());
    }
  },

  async _leaveLiveForAppUpdateDownload(this: HomeShellState): Promise<void> {
    if (this._joinedLiveRoomId == null) return;
    await this._leaveLive();
  },

  async _handleWindowCloseRequest(this: HomeShellState): Promise<boolean> {
    if (this._exitingApplication) return true;
    if (this._appUpdateDownloadInProgress) {
      return this._handleAppUpdateDownloadCloseRequest();
    }
    const behavior = await this._readCloseBehavior();
    if (!this.mounted) return true;
    if (behavior === CloseBehavior.askEveryTime) {
      const result = await this._showCloseBehaviorDialog();
      if (result == null) return true;
      if (result.remember) {
        try {
          await this.widget.closeBehaviorStore.write(result.behavior);
        } catch {
          // Best effort.
        }
      }
      await this._performCloseBehavior(result.behavior);
      return true;
    }
    await this._performCloseBehavior(behavior);
    return true;
  },

  async _handleAppUpdateDownloadCloseRequest(this: HomeShellState): Promise<boolean> {
    if (this._closeConfirming) return true;
    this._setHomeState(() => {
      this._closeConfirming = true;
    });
    try {
      const confirmed = await showDialog<boolean>((close) => (
        <DialogFrame
          title="正在下载新版本"
          icon={Icons.system_update_alt_outlined}
          adaptiveActions={[
            new ResponsiveDialogAction({ label: '继续下载', onPressed: () => close(false) }),
            new ResponsiveDialogAction({
              label: '中断下载并关闭',
              onPressed: () => close(true),
              tone: ButtonTone.danger,
              icon: Icons.close,
            }),
          ]}
        >
          <Text style={{ ...UiTypography.body, color: UiColors.textSecondary, lineHeight: 1.55 }}>
            正在下载新版本，关闭将中断下载。
          </Text>
        </DialogFrame>
      ));
      if (!this.mounted || confirmed !== true) return true;
      const token = this._appUpdateDownloadCancellationToken;
      await token?.cancelAndDeletePartialFile();
      await this._exitApplication();
      return true;
    } finally {
      if (this.mounted) {
        this._setHomeState(() => {
          this._closeConfirming = false;
        });
      }
    }
  },

  async _readCloseBehavior(this: HomeShellState): Promise<CloseBehavior> {
    try {
      return await this.widget.closeBehaviorStore.read();
    } catch {
      return defaultCloseBehavior;
    }
  },

  async _showCloseBehaviorDialog(this: HomeShellState): Promise<_ClosePromptResult | null> {
    if (this._closeConfirming) return null;
    this._setHomeState(() => {
      this._closeConfirming = true;
    });
    try {
      const result = await showDialog<_ClosePromptResult>((close) => <_CloseBehaviorDialog onClose={close} />);
      return result ?? null;
    } finally {
      if (this.mounted) {
        this._setHomeState(() => {
          this._closeConfirming = false;
        });
      }
    }
  },

  async _performCloseBehavior(this: HomeShellState, behavior: CloseBehavior): Promise<void> {
    switch (behavior) {
      case CloseBehavior.askEveryTime:
        return;
      case CloseBehavior.minimizeToTray:
        await this.widget.windowController.minimizeToTray();
        return;
      case CloseBehavior.exitProgram:
        await this._exitApplication();
        return;
    }
  },

  async _exitApplication(this: HomeShellState): Promise<void> {
    if (this._exitingApplication) return;
    this._exitingApplication = true;
    try {
      await this.widget.windowController.hideAppWindowForExit();
      await this._leaveLiveForSessionEnd({ disconnectTimeout: 1000 });
      await this._resetLiveAudioStateAfterSessionEnd();
      await this._stopRealtimeForExit();
      await this.widget.app.exitSessionForAppExit();
    } finally {
      await this.widget.windowController.terminateApplication();
    }
  },

  _handleUserUpdated(this: HomeShellState, user: CurrentUser): void {
    this._setHomeState(() => {
      this._currentUser = user;
    });
    this.widget.app.onCurrentUserUpdated?.(user);
  },

  async _retryOpenSelectedRoom(this: HomeShellState): Promise<void> {
    const server = this._selectedServer;
    if (server == null) return;
    await this._openRoom(server, { openContent: false });
  },

  _openLiveChannel(this: HomeShellState): void {
    const roomId = this._selectedServerId;
    if (roomId == null) return;
    this._setHomeState(() => {
      this._settingsOpen = false;
      this._contentMode = _ContentMode.live;
      this._narrowContentOpen = true;
    });
    this._ensureMusicBoxLoaded(roomId);
  },

  async _openJoinedLiveChannel(this: HomeShellState): Promise<void> {
    const roomId = this._joinedLiveRoomId;
    if (roomId == null) return;
    this._collapseSearch();

    if (this._selectedServerId === roomId && this._selectedRoom?.id === roomId) {
      this._openLiveChannel();
      return;
    }

    const server = this._serverById(roomId);
    if (server == null) return;

    try {
      const snapshot = await this._roomsController.openRoom(roomId);
      if (!this.mounted || this._joinedLiveRoomId !== roomId) return;
      this._applyJoinedLiveRoomSnapshot(server, snapshot);
    } catch (error) {
      if (!this.mounted || this._joinedLiveRoomId !== roomId) return;
      if (this._selectedServerId === roomId) {
        this._setHomeState(() => {
          this._roomError = userFacingErrorMessage(error);
        });
      }
    }
  },

  _applyJoinedLiveRoomSnapshot(this: HomeShellState, server: RoomCard, snapshot: RoomOpenSnapshot): void {
    this._storeSelectedComposerDraft();

    this._setHomeState(() => {
      this._selectedServerId = server.id;
      this._restoreComposerDraftForRoom(server.id);
      this._selectedRoomNewMessageCount = server.unreadCount;
      this._clearRoomUnreadCount(server.id);
      this._settingsOpen = false;
      this._contentMode = _ContentMode.live;
      this._narrowContentOpen = true;
      this._selectedRoom = snapshot.detail;
      this._live = snapshot.live;
      this._messages = snapshot.messages;
      this._fileTransfers = new Map();
      this._fileDownloads = new Map();
      this._membersInitialSearchQuery = '';
      this._selectedRoomHasPendingJoinRequests = false;
      this._roomError = null;
      this._sendError = null;
      this._loadingRoom = false;
      this._resetMusicBox();
      this._stickerPanelState = sticker_display.stickerPanelReset({
        source: this._stickerPanelState.source,
      });
    });
    unawaited(this._markRoomReadFromMessages(server.id, snapshot.messages));
    unawaited(this._refreshSelectedJoinRequestBadge(snapshot.detail));
    unawaited(this._loadMusicBox(server.id));
  },

  _serverById(this: HomeShellState, roomId: string): RoomCard | null {
    for (const server of this._servers) {
      if (server.id === roomId) return server;
    }
    return null;
  },

  _openChat(this: HomeShellState): void {
    const roomId = this._selectedServerId;
    this._setHomeState(() => {
      this._clearDeferredRoomNotificationVisualMarkersInState();
      this._contentMode = _ContentMode.chat;
    });
    if (roomId != null) {
      unawaited(this._refreshSelectedMessagesSilently(roomId));
    }
  },

  async _openRoomMembers(this: HomeShellState, opts: { initialSearchQuery?: string } = {}): Promise<void> {
    const { initialSearchQuery = '' } = opts;
    const room = this._selectedRoom;
    if (room == null) return;
    this._setHomeState(() => {
      this._settingsOpen = false;
      this._contentMode = _ContentMode.members;
      this._narrowContentOpen = true;
      this._membersInitialSearchQuery = initialSearchQuery.trim();
    });
  },

  _messageProfileAction(this: HomeShellState, user: UserSummary): UserProfileAction | null {
    const room = this._selectedRoom;
    if (room == null) return null;
    const uid = user.uid?.trim();
    if (uid == null || uid.length === 0) return null;
    if (
      !member_filter.canOpenRoomMemberManagementFromProfile({
        room,
        currentUser: this._currentUser,
        target: user,
      })
    ) {
      return null;
    }
    return new UserProfileAction({
      label: '管理成员',
      icon: Icons.manage_accounts_outlined,
      onPressed: () => unawaited(this._openRoomMembers({ initialSearchQuery: uid })),
    });
  },

  async _openRoomSettings(
    this: HomeShellState,
    opts: { initialSection?: RoomSettingsSection; initialMusicPlaylistId?: string | null } = {},
  ): Promise<void> {
    const { initialSection = RoomSettingsSection.info } = opts;
    const room = this._selectedRoom;
    if (room == null) return;
    this._setHomeState(() => {
      this._settingsOpen = false;
      this._roomSettingsInitialSection = initialSection;
      this._roomSettingsInitialMusicPlaylistId = opts.initialMusicPlaylistId ?? null;
      this._contentMode = _ContentMode.roomSettings;
      this._narrowContentOpen = true;
    });
  },

  _handleRoomSettingsResult(this: HomeShellState, roomId: string, result: RoomManagementResult): void {
    switch (result.kind) {
      case RoomManagementResultKind.created:
        break;
      case RoomManagementResultKind.updated: {
        const updated = result.room;
        if (updated != null) this._applyManagedRoomUpdated(updated);
        unawaited(this._loadServers());
        break;
      }
      case RoomManagementResultKind.left:
      case RoomManagementResultKind.deleted:
        this._applyManagedRoomRemoved(roomId);
        unawaited(this._loadServers());
        break;
    }
  },

  _handleCreateRoomResult(this: HomeShellState, result: RoomManagementResult): void {
    if (result.kind !== RoomManagementResultKind.created) return;
    const room = result.room;
    if (room == null) return;
    this._applyCreatedRoom(room);
    unawaited(this._loadServers());
  },

  _applyCreatedRoom(this: HomeShellState, room: RoomDetail): void {
    this._storeSelectedComposerDraft();
    const patch = this._roomsController.patchRoomDetailApplied({
      rooms: this._servers,
      detail: room,
    });
    this._setHomeState(() => {
      this._selectedServerId = room.id;
      this._discardRoomDraftInState(room.id);
      this._stagedAttachments.length = 0;
      this._setComposerText('', { saveDraft: false });
      this._selectedRoom = patch.selectedRoom;
      this._servers = patch.rooms;
      this._live = room.live;
      this._messages = [];
      this._selectedRoomNewMessageCount = 0;
      this._fileTransfers = new Map();
      this._fileDownloads = new Map();
      this._settingsOpen = false;
      this._contentMode = _ContentMode.chat;
      this._membersInitialSearchQuery = '';
      this._selectedRoomHasPendingJoinRequests = false;
      this._roomError = null;
      this._sendError = null;
      this._loadingRoom = false;
      this._narrowContentOpen = true;
    });
  },

  _applyManagedRoomUpdated(this: HomeShellState, room: RoomDetail): void {
    if (this._selectedServerId !== room.id) return;
    if (this._joinedLiveRoomId === room.id) {
      this._joinedLivePersonalAiVoiceAnnouncementsEnabled = room.aiVoiceAnnouncementsEnabled;
    }
    const patch = this._roomsController.patchRoomDetailApplied({
      rooms: this._servers,
      detail: room,
    });
    this._setHomeState(() => {
      this._selectedRoom = patch.selectedRoom;
      this._servers = patch.rooms;
      this._live = room.live;
      this._roomError = null;
    });
    unawaited(this._refreshSelectedMessagesSilently(room.id));
    unawaited(this._refreshSelectedJoinRequestBadge(room));
  },

  _applyManagedRoomRemoved(this: HomeShellState, roomId: string): void {
    this._setHomeState(() => {
      this._discardRoomDraftInState(roomId);
      this._servers = this._roomsController.removeRoomCard(this._servers, roomId);
      if (this._selectedServerId === roomId) {
        for (const entry of this._stagedAttachments) {
          entry.uploadController.cancel();
        }
        this._stagedAttachments.length = 0;
        this._setComposerText('', { saveDraft: false });
        this._selectedServerId = null;
        this._selectedRoom = null;
        this._live = null;
        this._messages = [];
        this._selectedRoomNewMessageCount = 0;
        this._fileTransfers = new Map();
        this._fileDownloads = new Map();
        this._contentMode = _ContentMode.chat;
        this._membersInitialSearchQuery = '';
        this._settingsOpen = false;
        this._selectedRoomHasPendingJoinRequests = false;
        this._roomError = null;
        this._sendError = null;
        this._narrowContentOpen = false;
      }
      if (this._joinedLiveRoomId === roomId) {
        this._joinedLiveRoomId = null;
        this._joiningLive = false;
        this._leavingLive = false;
      }
    });
  },
};

export class _ClosePromptResult {
  constructor(opts: { behavior: CloseBehavior; remember: boolean }) {
    this.behavior = opts.behavior;
    this.remember = opts.remember;
  }

  readonly behavior: CloseBehavior;
  readonly remember: boolean;
}

/** The `StatefulBuilder` dialog body of [_showCloseBehaviorDialog]. */
export function _CloseBehaviorDialog({ onClose }: { onClose: (result?: _ClosePromptResult) => void }) {
  const [selectedBehavior, setSelectedBehavior] = useState<CloseBehavior>(CloseBehavior.minimizeToTray);
  const [remember, setRemember] = useState(false);
  const buttonsRef = useRef<HTMLDivElement | null>(null);
  const maxWidth = useElementSize(buttonsRef)?.width ?? null;

  const minimizeWidth = Button.minimumWidthForLabel({ label: '最小化到托盘', hasIcon: true });
  const exitWidth = Button.minimumWidthForLabel({ label: '退出程序', hasIcon: true });
  const availableButtonWidth = maxWidth == null ? Infinity : (maxWidth - 12) / 2;
  const stack =
    maxWidth != null &&
    Number.isFinite(maxWidth) &&
    (availableButtonWidth < minimizeWidth || availableButtonWidth < exitWidth);
  const minimizeButton = (
    <Button
      data-testid="close-behavior-minimize-button"
      selected={selectedBehavior === CloseBehavior.minimizeToTray}
      onPressed={() => setSelectedBehavior(CloseBehavior.minimizeToTray)}
      icon={<Icon icon={Icons.keyboard_arrow_down} />}
      width={Infinity}
    >
      最小化到托盘
    </Button>
  );
  const exitButton = (
    <Button
      data-testid="close-behavior-exit-button"
      selected={selectedBehavior === CloseBehavior.exitProgram}
      onPressed={() => setSelectedBehavior(CloseBehavior.exitProgram)}
      icon={<Icon icon={Icons.logout} />}
      width={Infinity}
      tone={selectedBehavior === CloseBehavior.exitProgram ? ButtonTone.danger : ButtonTone.neutral}
    >
      退出程序
    </Button>
  );

  return (
    <DialogFrame
      title="关闭 Gang Chat"
      icon={Icons.close}
      actionBar={
        <ResponsiveDialogActionBar
          actions={[
            new ResponsiveDialogAction({ label: '取消', onPressed: () => onClose() }),
            new ResponsiveDialogAction({
              label: '确认',
              icon: Icons.check,
              tone: selectedBehavior === CloseBehavior.exitProgram ? ButtonTone.danger : ButtonTone.primary,
              onPressed: () => onClose(new _ClosePromptResult({ behavior: selectedBehavior, remember })),
            }),
          ]}
        />
      }
    >
      <Column mainAxisSize="min" crossAxisAlignment="stretch">
        <div ref={buttonsRef} style={{ width: '100%' }}>
          {stack ? (
            <Column crossAxisAlignment="stretch">
              {minimizeButton}
              <SizedBox height={12} />
              {exitButton}
            </Column>
          ) : (
            <Row>
              <Expanded>{minimizeButton}</Expanded>
              <SizedBox width={12} />
              <Expanded>{exitButton}</Expanded>
            </Row>
          )}
        </div>
        <SizedBox height={14} />
        <Row>
          <Expanded>
            <Text style={{ ...UiTypography.body, color: UiColors.textSecondary }}>记住我的选择</Text>
          </Expanded>
          <UiSwitch value={remember} onChanged={(value) => setRemember(value)} />
        </Row>
      </Column>
    </DialogFrame>
  );
}

// `withTimeout` is re-exported for parity with the Dart `Future.timeout` users
// in the sibling tables; kept referenced so the import list mirrors Dart's.
void withTimeout;
