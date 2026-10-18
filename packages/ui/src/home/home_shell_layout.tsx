import { useRef, type ReactNode } from 'react';

import { ManagedUserAudioDeviceService, ManagedUserAudioSettingsStore } from '@gang-chat/core/app/managed_user_audio_settings';
import { PersonalMusicPlaylistsController } from '@gang-chat/core/app/personal_music_playlists';
import { RealtimeConnectionStatus } from '@gang-chat/core/app/realtime_controller';
import * as room_display from '@gang-chat/core/app/room_display';
import { SettingsController } from '@gang-chat/core/app/settings_controller';
import { SettingsSection } from '@gang-chat/core/app/settings_shell_state';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import { unawaited } from '@gang-chat/core/lib/async';
import { supportsLocalScreenShare } from '@gang-chat/core/live/live_session';
import { GlobalSearchResults } from '@gang-chat/core/protocol/models';

import { useElementSize } from '../lib/use_element_size';
import { MusicPlaylistsPanel } from '../settings/settings_music_playlists';
import { SettingsPage } from '../settings/settings_page';
import { ChatFileDownloadActions, ChatVoicePlaybackActions } from './chat_messages';
import { showChatImagePreview } from './chat_image_preview';
import { ChatPane } from './chat_pane';
import { HomeContent } from './home_content';
import { HomeNotificationsPane } from './home_notifications';
import type { HomeShellState } from './home_shell';
import { _ConfirmedMentionBackspaceFormatter, _ContentMode, _roomTitle } from './home_shell';
import { _isMusicTrackPreviewApi, _isPersonalMusicPlaylistApi, _isRoomMusicPlaylistApi } from './home_shell_messages';
import { _RoomMessageHistoryPane } from './home_shell_room_message_history';
import type { _TitleLiveRoomDockBuilder } from './home_shell_title_bar';
import { HomeSidebar } from './home_sidebar';
import { LiveChannelPane } from './live_channel_pane';
import { RoomMembersDialog } from './room_members_dialog';
import { RoomSettingsDialog, RoomSettingsSection } from './room_settings_dialog';

/**
 * `LayoutBuilder` for the narrow sidebar footer: hands the slot's measured
 * width to [builder] (Dart `constraints.maxWidth.clamp(0, infinity)`).
 */
function _FooterLiveRoomDockSlot({ builder }: { builder: _TitleLiveRoomDockBuilder }) {
  const ref = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(ref);
  return (
    <div ref={ref} style={{ width: '100%', minWidth: 0, display: 'flex', flexDirection: 'column' }}>
      {size == null ? null : builder(Math.max(size.width, 0), { fillAvailable: true })}
    </div>
  );
}

/**
 * Port of `extension _HomeShellLayout on _HomeShellState`.
 *
 * The Dart extension getters stay getters: HomeShell copies the table's
 * property descriptors onto its prototype. A getter cannot declare a `this`
 * parameter, so each one casts `this` to [HomeShellState].
 */
export const HomeShellLayout = {
  get _chatFileDownloadActions(): ChatFileDownloadActions {
    const self = this as unknown as HomeShellState;
    return new ChatFileDownloadActions({
      onDownload: (message, attachment, index, resolvedUrl) =>
        unawaited(
          self._downloadAttachment({
            message,
            attachment,
            index,
            resolvedUrl,
          }),
        ),
      onPause: (key) => self._pauseDownload(key),
      onResume: (key) => self._resumeDownload(key),
      onCancel: (key) => self._cancelDownload(key),
      onDismiss: (key) => self._dismissDownload(key),
    });
  },

  get _chatVoicePlaybackActions(): ChatVoicePlaybackActions {
    const self = this as unknown as HomeShellState;
    return new ChatVoicePlaybackActions({
      activeMessageId: self._voicePlayback.playing ? self._voicePlayback.activeMessageId : null,
      activePosition: self._voicePlayback.position,
      activeDuration: self._voicePlayback.duration,
      onToggle: (messageId, resolvedUrl) => unawaited(self._toggleVoicePlayback({ messageId, resolvedUrl })),
    });
  },

  /** Dart `StickerImagePreviewOpener`; the `BuildContext` argument is dropped. */
  _openStickerManagerImagePreview(
    this: HomeShellState,
    opts: { imageUrl: string; suggestedName: string; forceSquare?: boolean },
  ): Promise<void> {
    const { imageUrl, suggestedName, forceSquare = false } = opts;
    return showChatImagePreview({
      imageUrl,
      suggestedName,
      actions: this._imagePreviewActions,
      forceSquare,
    });
  },

  _buildNarrowLayout(
    this: HomeShellState,
    width: number,
    opts: { footerLiveRoomDockBuilder?: _TitleLiveRoomDockBuilder | null } = {},
  ): ReactNode {
    const footerLiveRoomDockBuilder = opts.footerLiveRoomDockBuilder ?? null;
    if (!this._narrowContentOpen) {
      return (
        <_HomeNarrowPane key="home-narrow-room-list">
          {this._buildSidebar({
            width,
            openContentOnSelect: true,
            header: this._buildCompactSearchField(),
            bodyOverride: this._hasSearchQuery && this._searchExpanded ? this._buildSearchResultsTapRegion() : null,
            footerMiddle:
              footerLiveRoomDockBuilder == null ? null : <_FooterLiveRoomDockSlot builder={footerLiveRoomDockBuilder} />,
          })}
        </_HomeNarrowPane>
      );
    }

    return (
      <_HomeNarrowPane key="home-narrow-content">
        {this._buildContentPane({ onNavigateBack: () => this._showNarrowRoomList() })}
      </_HomeNarrowPane>
    );
  },

  _showNarrowRoomList(this: HomeShellState): void {
    this._setHomeState(() => {
      this._narrowContentOpen = false;
    });
  },

  _canHandleShellBack(this: HomeShellState, opts: { narrowLayout: boolean }): boolean {
    const { narrowLayout } = opts;
    return (
      this._appUpdateDownloadInProgress ||
      this._fullScreenLiveTrack != null ||
      (this._hasSearchQuery && this._searchExpanded) ||
      this._settingsOpen ||
      this._contentMode !== _ContentMode.chat ||
      (narrowLayout && this._narrowContentOpen)
    );
  },

  _handleShellBack(this: HomeShellState, opts: { narrowLayout: boolean }): void {
    const { narrowLayout } = opts;
    if (this._appUpdateDownloadInProgress) return;
    if (this._fullScreenLiveTrack != null) {
      this._exitLiveFullScreen();
      return;
    }
    if (this._hasSearchQuery && this._searchExpanded) {
      this._collapseSearch();
      return;
    }
    if (this._settingsOpen) {
      this._closeSettings();
      return;
    }

    switch (this._contentMode) {
      case _ContentMode.superuserUserSettings:
        this._closeSuperuserUserSettings();
        return;
      case _ContentMode.createRoom:
        this._closeCreateRoom();
        return;
      case _ContentMode.notifications:
        this._closeNotifications();
        return;
      case _ContentMode.live:
      case _ContentMode.members:
      case _ContentMode.roomSettings:
        this._openChat();
        return;
      case _ContentMode.chat:
        if (narrowLayout && this._narrowContentOpen) {
          this._showNarrowRoomList();
        }
        return;
    }
  },

  _buildContentPane(this: HomeShellState, opts: { onNavigateBack?: (() => void) | null } = {}): ReactNode {
    const onNavigateBack = opts.onNavigateBack ?? null;
    const widget = this.widget;
    if (this._settingsOpen) {
      return (
        <SettingsPage
          isSubWindow
          api={this._services.api}
          apiBaseUrl={widget.app.apiBaseUrl}
          emailVerificationController={widget.app.emailVerificationController}
          passwordResetController={widget.app.passwordResetController}
          audioDeviceStore={widget.audioDeviceStore}
          closeBehaviorStore={widget.closeBehaviorStore}
          languageStore={widget.languageStore}
          androidSystemService={widget.androidSystemService}
          windowController={widget.windowController}
          initialSection={this._settingsAppUpdate == null ? this._settingsInitialSection : SettingsSection.about}
          initialMusicPlaylistId={this._settingsInitialMusicPlaylistId}
          initialAppUpdate={this._settingsAppUpdate}
          stickerPackStore={widget.app.stickerPackStore}
          stickerImagePreviewOpener={(previewOpts) => this._openStickerManagerImagePreview(previewOpts)}
          onAppUpdateDownloadCancellationChanged={(token) => this._handleAppUpdateDownloadCancellationChanged(token)}
          currentUser={this._currentUser}
          onUserUpdated={(user) => this._handleUserUpdated(user)}
          onMusicPlaylistsChanged={() => this._markMusicPlaylistsChanged()}
          onAccountDeleted={() => this._logout()}
          onScreenShareMaxHeightChanged={(height) => unawaited(this._liveSessionController.setScreenShareMaxHeight(height))}
          onScreenShareFrameRateChanged={(frameRate) =>
            unawaited(this._liveSessionController.setScreenShareFrameRate(frameRate))
          }
          onVolumeChanged={(kind, volume) => this._syncSettingsAudioVolumeToLive(kind, volume)}
          onVolumeBoostChanged={(kind, boost) => this._syncSettingsAudioVolumeBoostToLive(kind, boost)}
          // The Settings picker already routes the native ADM (selectAudioInput/
          // Output). For inputs, also keep LiveSession's tracked capture device in
          // sync so a later mute/unmute republish stays on the chosen mic. Outputs
          // are global on the ADM and need no session-side action.
          onDeviceSelected={(kind, deviceId) => {
            if (kind === 'audioinput') {
              unawaited(this._liveSessionController.setInputDeviceId(deviceId));
            }
          }}
          onClose={() => this._closeSettings()}
          musicTrackPreviewPlatformFactory={widget.musicTrackPreviewPlatformFactory}
        />
      );
    }

    const superuserTarget = this._superuserSettingsTarget;
    if (this._contentMode === _ContentMode.superuserUserSettings && superuserTarget != null) {
      const target = superuserTarget;
      return (
        <SettingsPage
          key={`superuser-user-settings-${target.id}`}
          isSubWindow
          api={this._services.api}
          apiBaseUrl={widget.app.apiBaseUrl}
          emailVerificationController={widget.app.emailVerificationController}
          controller={
            new SettingsController({
              api: this._services.api,
              apiBaseUrl: widget.app.apiBaseUrl,
              stickerPackStore: widget.app.stickerPackStore,
              managedUserId: target.id,
            })
          }
          audioDeviceStore={new ManagedUserAudioSettingsStore({ api: this._services.api, userId: target.id })}
          audioDeviceService={new ManagedUserAudioDeviceService()}
          closeBehaviorStore={widget.closeBehaviorStore}
          languageStore={widget.languageStore}
          windowController={widget.windowController}
          stickerPackStore={widget.app.stickerPackStore}
          stickerImagePreviewOpener={(previewOpts) => this._openStickerManagerImagePreview(previewOpts)}
          fileSelectionService={this._fileSelectionService}
          onClose={() => this._closeSuperuserUserSettings()}
          onAccountDeleted={async () => {
            this._setHomeState(() => {
              const results = this._searchResults;
              if (results != null) {
                this._searchResults = new GlobalSearchResults({
                  myRooms: results.myRooms,
                  publicRooms: results.publicRooms,
                  userSettings: results.userSettings.filter((user) => user.id !== target.id),
                  messages: results.messages,
                  files: results.files,
                  nextCursors: results.nextCursors,
                  totalCounts: results.totalCounts,
                });
              }
              this._superuserSettingsTarget = null;
              this._contentMode = _ContentMode.chat;
            });
          }}
          onUserUpdated={(updated) => {
            this._setHomeState(() => {
              this._superuserSettingsTarget = updated.toSummary();
              const results = this._searchResults;
              if (results != null) {
                this._searchResults = new GlobalSearchResults({
                  myRooms: results.myRooms,
                  publicRooms: results.publicRooms,
                  userSettings: results.userSettings.map((user) => (user.id === updated.id ? updated.toSummary() : user)),
                  messages: results.messages,
                  files: results.files,
                  nextCursors: results.nextCursors,
                  totalCounts: results.totalCounts,
                });
              }
            });
          }}
        />
      );
    }

    if (this._contentMode === _ContentMode.createRoom) {
      return (
        <RoomSettingsDialog.create
          key="home-create-room-settings-dialog"
          controller={this._roomsController}
          currentUser={this._currentUser}
          embedded
          onClose={() => this._closeCreateRoom()}
          onResult={(result) => this._handleCreateRoomResult(result)}
          stickerImagePreviewOpener={(previewOpts) => this._openStickerManagerImagePreview(previewOpts)}
        />
      );
    }

    if (this._contentMode === _ContentMode.notifications) {
      return (
        <HomeNotificationsPane
          invites={this._notificationInvites}
          applications={this._notificationApplications}
          roomNotifications={this._notificationRoomEvents}
          currentUser={this._currentUser}
          loading={this._loadingNotifications}
          error={this._notificationError}
          busyInviteId={this._busyNotificationInviteId}
          busyApplicationId={this._busyNotificationApplicationId}
          onClose={() => this._closeNotifications()}
          onRefresh={() => unawaited(this._loadNotifications({ clearVisualReadMarkers: true }))}
          onReviewInvite={(invite, accept) => this._reviewNotificationInvite(invite, accept)}
          onWithdrawApplication={(application) => this._withdrawNotificationApplication(application)}
          onCopyNotification={(item) => this._copyNotification(item)}
          onDeleteNotification={(item) => this._deleteNotification(item)}
          onDeleteNotifications={(items) => this._deleteNotifications(items)}
          onResolveRoomProfile={(room) => this._resolveRoomProfile(room)}
          onResolveRoomUserProfile={(roomId, user) => this._resolveRoomUserProfile(roomId, user)}
          onOpenRoom={(room) => this._openNotificationRoom(room)}
          onOpenRoomEvent={(notification) => this._openNotificationRoomEvent(notification)}
        />
      );
    }

    const selectedServerId = this._selectedServerId;
    if (selectedServerId == null) return <HomeContent />;
    const selectedRoom = this._selectedRoom;
    if (this._contentMode === _ContentMode.members && selectedRoom != null) {
      return (
        <RoomMembersDialog
          controller={this._roomsController}
          room={selectedRoom}
          currentUser={this._currentUser}
          initialLive={this._live ?? selectedRoom.live}
          initialSearchQuery={this._membersInitialSearchQuery}
          hasPendingJoinRequests={this._selectedRoomHasPendingJoinRequests}
          reloadToken={this._membersReloadToken}
          embedded
          onClose={() => this._openChat()}
          onChanged={() => unawaited(this._loadServers())}
          onPendingJoinRequestsChanged={(hasPending) => this._handleSelectedJoinRequestsChanged(hasPending)}
          onOpenRoom={(room) => this._openNotificationRoom(room)}
        />
      );
    }
    if (this._contentMode === _ContentMode.roomSettings && selectedRoom != null) {
      const room = selectedRoom;
      const api = this._services.api;
      return (
        <RoomSettingsDialog
          key={`home-room-settings-${room.id}`}
          controller={this._roomsController}
          room={room}
          currentUser={this._currentUser}
          initialSection={this._roomSettingsInitialSection}
          isInLive={this._joinedLiveRoomId === room.id}
          onRoomUpdated={(updated) => this._applyManagedRoomUpdated(updated)}
          onLeaveLive={async () => {
            if (this._joinedLiveRoomId === room.id) await this._leaveLive();
          }}
          embedded
          onClose={() => this._openChat()}
          onResult={(result) => this._handleRoomSettingsResult(room.id, result)}
          stickerImagePreviewOpener={(previewOpts) => this._openStickerManagerImagePreview(previewOpts)}
          messageHistoryBuilder={() => (
            <_RoomMessageHistoryPane
              room={room}
              currentUser={this._currentUser}
              roomsController={this._roomsController}
              messagesController={this._messagesController}
              clipboardService={this._clipboardService}
              fileDownloads={this._fileDownloads}
              downloadActions={this._chatFileDownloadActions}
              voicePlaybackActions={this._chatVoicePlaybackActions}
              imagePreviewActions={this._imagePreviewActions}
              messageActions={this._chatMessageActions}
              onResolveRoomProfile={(publicRoom) => this._resolveRoomProfile(publicRoom)}
              onResolveRoomUserProfile={(roomId, user) => this._resolveRoomUserProfile(roomId, user)}
              onOpenRoom={(publicRoom) => this._openNotificationRoom(publicRoom)}
              profileActionBuilder={(user) => this._messageProfileAction(user)}
              onJumpToMessage={(messageId) => {
                const server = this._selectedServer;
                if (server == null) return;
                unawaited(this._openRoom(server, { openContent: true, focusMessageId: messageId }));
              }}
            />
          )}
          musicPlaylistsBuilder={() => (
            <MusicPlaylistsPanel
              key={`room-music-playlists-${room.id}`}
              controller={PersonalMusicPlaylistsController.room({
                roomApi: _isRoomMusicPlaylistApi(api) ? api : null,
                personalApi: _isPersonalMusicPlaylistApi(api) ? api : null,
                roomId: room.id,
                canManage: room_display.roomAccessState({ room, currentUser: this._currentUser }).canManageRoom,
                searchTracks: ({ keyword, source }) =>
                  api.searchMusicBox({
                    roomId: room.id,
                    keyword,
                    source,
                    count: 20,
                    page: 1,
                  }),
                onChanged: () => this._markMusicPlaylistsChanged(),
              })}
              initialPlaylistId={this._roomSettingsInitialMusicPlaylistId}
              title="房间歌单"
              unavailableMessage="房间歌单需要登录后从服务端读取"
              previewApi={_isMusicTrackPreviewApi(api) ? api : null}
              previewPlatformFactory={widget.musicTrackPreviewPlatformFactory}
              shareApi={api}
              currentUser={this._currentUser}
            />
          )}
        />
      );
    }
    if (this._contentMode === _ContentMode.live) {
      const canManageSelectedRoom =
        selectedRoom != null &&
        room_display.roomAccessState({ room: selectedRoom, currentUser: this._currentUser }).canManageRoom;
      return (
        <LiveChannelPane
          title={_roomTitle(this._selectedRoom, this._selectedServer)}
          avatarUrl={this._selectedRoom?.avatarUrl ?? this._selectedServer?.avatarUrl ?? null}
          live={this._live ?? this._selectedRoom?.live ?? null}
          currentUser={this._currentUser}
          loading={this._loadingRoom}
          joined={this._joinedLiveRoomId === this._selectedServerId}
          joining={this._joiningLive}
          leaving={this._leavingLive}
          micMuted={this._micMuted}
          headphonesMuted={this._headphonesMuted}
          voiceBlocked={this._voiceBlocked}
          cameraOn={this._cameraOn}
          screenSharing={this._screenSharing}
          speakingUserIds={this._liveSessionController.speakingIdentities}
          connectedParticipantIds={this._liveSessionController.connectedParticipantIdentities}
          liveKitMicMutedByParticipantId={this._liveSessionController.micMutedByIdentity}
          videoTracks={this._liveSessionController.videoTracks}
          stageSelection={this._liveStageSelections.get(selectedServerId) ?? null}
          suspendStageVideo={!HostPlatform.isWeb && HostPlatform.isWindows && this._fullScreenLiveTrack != null}
          onStageSelectionChanged={(selection) => this._setLiveStageSelection(selection)}
          onEnterFullScreen={(track) => this._enterLiveFullScreen(track)}
          onBackToChat={() => this._openChat()}
          onJoin={() => unawaited(this._joinLive('live_panel'))}
          onLeave={() => unawaited(this._leaveLive())}
          onToggleMic={this._voiceBlocked ? null : () => this._toggleMicMute()}
          onToggleHeadphones={() => this._toggleHeadphonesMute()}
          onToggleCamera={() => unawaited(this._toggleCamera())}
          onFlipCamera={
            !HostPlatform.isWeb && HostPlatform.isAndroid && this._liveSessionController.canFlipCamera
              ? () => this._flipLocalCamera()
              : null
          }
          onSetLocalCameraMirrored={(mirrored) => this._setLocalCameraMirrored(mirrored)}
          onToggleShare={supportsLocalScreenShare() ? () => unawaited(this._toggleScreenShare()) : null}
          musicBox={this._musicBox}
          musicBoxOpen={this._musicBoxOpen}
          musicBoxSearchController={this._musicBoxSearchController}
          musicBoxSearchResults={this._musicBoxSearchResults}
          musicBoxSearching={this._musicBoxSearching}
          musicBoxSearchError={this._musicBoxSearchError}
          musicBoxSource={this._musicBoxSource}
          onToggleMusicBox={() => this._toggleMusicBoxPanel()}
          onMusicBoxTogglePlayback={() => this._toggleMusicBoxPlayback()}
          onMusicBoxSkip={() => unawaited(this._controlMusicBox('skip'))}
          onMusicBoxPrevious={() => unawaited(this._controlMusicBox('previous'))}
          onMusicBoxModeChanged={(mode) => unawaited(this._changeMusicBoxPlaybackMode(mode))}
          musicBoxController={this._musicBoxController}
          musicBoxRoomId={this._selectedServerId}
          musicPlaylistsRevision={this._musicPlaylistsRevision}
          musicBoxRoom={selectedRoom == null ? null : room_display.publicRoomFromRoomDetail(selectedRoom)}
          onMusicBoxStateChanged={(state) => this._applyMusicBoxSnapshot(state)}
          onCreateFirstRoomMusicPlaylist={
            canManageSelectedRoom
              ? () => unawaited(this._openRoomSettings({ initialSection: RoomSettingsSection.playlists }))
              : null
          }
          onCreateFirstPersonalMusicPlaylist={() =>
            this._openSettingsSection(SettingsSection.playlists, { openContent: true })
          }
          onEditRoomMusicPlaylist={
            canManageSelectedRoom
              ? (playlistId) =>
                  this._openRoomSettings({
                    initialSection: RoomSettingsSection.playlists,
                    initialMusicPlaylistId: playlistId,
                  })
              : null
          }
          onEditPersonalMusicPlaylist={async (playlistId) => {
            this._openSettingsSection(SettingsSection.playlists, {
              openContent: true,
              initialMusicPlaylistId: playlistId,
            });
          }}
          onMusicBoxQueueResult={(result) => unawaited(this._queueMusicBoxTrack(result))}
          onMusicBoxRemoveItem={(item) => unawaited(this._removeMusicBoxItem(item))}
          onMusicBoxSourceChanged={(source) => this._changeMusicBoxSource(source)}
          inputVolume={this._liveSessionController.inputVolume}
          outputVolume={this._liveSessionController.outputVolume}
          musicBoxVolume={this._liveSessionController.musicBoxVolume}
          screenShareVolume={this._liveSessionController.screenShareVolume}
          onInputVolumeChanged={(volume) => this._changeInputVolume(volume)}
          onOutputVolumeChanged={(volume) => this._changeOutputVolume(volume)}
          onMusicBoxVolumeChanged={(volume) => unawaited(this._liveSessionController.setMusicBoxVolume(volume))}
          onScreenShareVolumeChanged={(volume) => this._changeScreenShareVolume(volume)}
          onScreenShareMuteToggled={() => this._toggleScreenShareAudioMute()}
          participantVoiceVolume={(userId) => this._participantVoiceVolume(userId)}
          onParticipantVoiceVolumeChanged={(userId, volume) => this._changeParticipantVoiceVolume(userId, volume)}
          onParticipantVoiceMuteToggled={(userId) => this._toggleParticipantVoiceMute(userId)}
          canModerateParticipant={(participant) => this._canModerateLiveParticipant(participant)}
          onToggleParticipantMicModeration={(participant) =>
            unawaited(this._toggleLiveParticipantMicModeration(participant))
          }
          onToggleParticipantHeadphonesModeration={(participant) =>
            unawaited(this._toggleLiveParticipantHeadphonesModeration(participant))
          }
          canRemoveParticipant={(participant) => this._canRemoveLiveParticipant(participant)}
          onRemoveParticipant={(participant) => unawaited(this._removeLiveParticipant(participant))}
          onResolveParticipantProfile={(user) => this._resolveSenderProfile(user)}
          onResolveParticipantRoomProfile={(room) => this._resolveRoomProfile(room)}
          onEnterParticipantProfileRoom={(room) => this._openNotificationRoom(room)}
          participantProfileActionBuilder={(user) => this._messageProfileAction(user)}
        />
      );
    }

    const visibleMessages = this._visibleMessagesForMe(this._messages);
    return (
      <ChatPane
        currentUser={this._currentUser}
        timestampNow={this._serverNow}
        roomCard={this._selectedServer}
        room={this._selectedRoom}
        live={this._live}
        messages={visibleMessages}
        newMessageCount={this._visibleNewMessageCount(visibleMessages)}
        focusMessageId={this._focusedMessageId}
        onFocusMessageHandled={(messageId) => this._handleFocusMessageHandled(messageId)}
        fileTransfers={this._fileTransfers}
        fileDownloads={this._fileDownloads}
        downloadActions={this._chatFileDownloadActions}
        imagePreviewActions={this._imagePreviewActions}
        messageActions={this._chatMessageActions}
        voicePlaybackActions={this._chatVoicePlaybackActions}
        loading={this._loadingRoom}
        error={this._roomError}
        sending={this._sending}
        sendError={this._sendError}
        composerController={this._composerController}
        composerPanelController={this._composerPanelController}
        stickerPanel={this._stickerPanelState}
        voiceState={this._voiceState}
        composerAttachments={this._stagedAttachmentViews}
        composerQuotes={this._selectedComposerQuotes}
        onRemoveComposerQuote={(messageId) => this._removeSelectedComposerQuote(messageId)}
        composerComponent={this._selectedComposerComponent}
        onRemoveComposerComponent={() => this._removeSelectedComposerComponent()}
        fileActionHighlighted={this._pickingAttachments || this._stagedAttachments.length > 0}
        mentionOptions={this._composerMentionOptions}
        mentionMembers={this._composerMentionMembersRoomId === this._selectedServerId ? this._composerMentionMembers : []}
        mentionMembersReady={this._composerMentionMembersRoomId === this._selectedServerId}
        mentionLoading={this._composerMentionQuery != null && this._loadingComposerMentionMembers}
        mentionSelectedIndex={this._composerMentionSelectedIndex}
        onSelectMention={(option) => this._selectComposerMention(option)}
        composerInputFormatters={[new _ConfirmedMentionBackspaceFormatter(this._composerController)]}
        onNavigateMentionSelection={(navigation) => this._navigateComposerMentionSelection(navigation)}
        onConfirmMentionSelection={(action) => this._confirmComposerMentionSelection(action)}
        onHighlightMentionSelection={(index) => this._highlightComposerMentionSelection(index)}
        hasPendingJoinRequests={this._selectedRoomHasPendingJoinRequests}
        onSubmit={(value) => unawaited(this._sendText(value))}
        onSendSticker={(sticker) => unawaited(this._sendSticker(sticker))}
        onLoadStickers={() => unawaited(this._loadStickerPacks({ forceReload: true }))}
        onRefreshStickers={() => unawaited(this._loadStickerPacks({ forceReload: true }))}
        onStickerSourceChanged={(source) => this._changeStickerSource(source)}
        onUploadFirstPersonalSticker={() => this._openSettingsSection(SettingsSection.stickers, { openContent: true })}
        onUploadFirstRoomSticker={
          selectedRoom != null &&
          room_display.roomAccessState({ room: selectedRoom, currentUser: this._currentUser }).canManageRoom
            ? () => unawaited(this._openRoomSettings({ initialSection: RoomSettingsSection.stickers }))
            : null
        }
        onStartVoice={() => unawaited(this._startVoiceRecording())}
        onSendVoice={() => unawaited(this._finishAndSendVoice())}
        onCancelVoice={() => unawaited(this._cancelVoiceRecording())}
        onPickFile={() => unawaited(this._pickAttachments())}
        onPasteFiles={() => this._pasteAttachments()}
        onCanPasteFiles={() => this._canPasteAttachments()}
        onRemoveAttachment={(id) => this._removeAttachment(id)}
        onRetryAttachment={(id) => this._retryAttachment(id)}
        onRetry={() => unawaited(this._retryOpenSelectedRoom())}
        onNavigateBack={onNavigateBack}
        onOpenLiveChannel={() => this._openLiveChannel()}
        onOpenRoomMembers={() => unawaited(this._openRoomMembers())}
        onOpenRoomSettings={() => unawaited(this._openRoomSettings())}
        onViewedNewMessages={() => this._clearSelectedRoomNewMessagePrompt()}
        onResolveSenderProfile={(user) => this._resolveSenderProfile(user)}
        onResolveRoomProfile={(room) => this._resolveRoomProfile(room)}
        onEnterProfileRoom={(room) => this._openNotificationRoom(room)}
        senderProfileActionBuilder={(user) => this._messageProfileAction(user)}
        onMentionUser={(user) => this._mentionUserFromAvatar(user)}
        composerDropKey={this._composerDropKey}
      />
    );
  },

  _buildSidebar(
    this: HomeShellState,
    opts: {
      width: number;
      openContentOnSelect: boolean;
      header?: ReactNode;
      bodyOverride?: ReactNode;
      footerMiddle?: ReactNode;
    },
  ): ReactNode {
    const { width, openContentOnSelect, header = null, bodyOverride = null, footerMiddle = null } = opts;
    return (
      <HomeSidebar
        width={width}
        currentUser={this._currentUser}
        servers={this._sidebarServers}
        timestampNow={this._serverNow}
        roomDrafts={this._sidebarRoomDrafts}
        selectedServerId={this._selectedServerId}
        joinedLiveRoomId={this._joinedLiveRoomId}
        realtimeReconnecting={this._realtimeStatus === RealtimeConnectionStatus.reconnecting}
        requestRoundTrip={this.widget.app.serverClock.requestRoundTrip}
        searchQuery={this._filteringSidebarBySearch ? this._searchQuery : ''}
        loading={this._loadingServers || this._loadingSidebarSearch}
        error={this._serverLoadError}
        settingsActive={this._settingsOpen}
        createRoomActive={!this._settingsOpen && this._contentMode === _ContentMode.createRoom}
        notificationsActive={!this._settingsOpen && this._contentMode === _ContentMode.notifications}
        logoutActive={this._logoutConfirming}
        hasPendingNotifications={this._hasPendingRoomInvites}
        pendingNotificationCount={this._pendingRoomNotificationCount}
        includeWindowChromeOffset={false}
        header={header}
        bodyOverride={bodyOverride}
        footerMiddle={footerMiddle}
        onServerSelected={(server) => this._selectServer(server, { openContent: openContentOnSelect })}
        onCreateRoom={() => this._openCreateRoom({ openContent: openContentOnSelect })}
        onOpenNotifications={() => this._openNotifications({ openContent: openContentOnSelect })}
        onOpenSettings={() => this._toggleSettings({ openContent: openContentOnSelect })}
        onLogout={() => unawaited(this._confirmLogout())}
      />
    );
  },
};

/**
 * `KeyedSubtree`: the narrow layout swaps the room list and the content pane
 * under distinct keys; this box only carries the key and passes the parent's
 * size through.
 */
function _HomeNarrowPane({ children }: { children?: ReactNode }) {
  return <div style={{ flex: '1 1 0', minWidth: 0, minHeight: 0, display: 'flex', flexDirection: 'column' }}>{children}</div>;
}
