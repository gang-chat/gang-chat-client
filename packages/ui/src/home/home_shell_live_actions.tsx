import { rememberedAudioVolume, restoredAudioVolume } from '@gang-chat/core/app/audio_device_state';
import { normalizedAudioVolume } from '@gang-chat/core/app/audio_levels';
import { userFacingErrorMessage } from '@gang-chat/core/app/error_display';
import {
  LiveModerationAction,
  canApplyPickedScreenShareSource,
  canPatchSelectedLiveState,
  isBenignGoneLiveStatePatch,
  joinedLiveRoomToDisconnectBeforeJoin,
  liveOutputMuteToggled,
  shouldSyncLiveKitMicAfterServerPatch,
  type LiveJoinPreviousRoomDisconnectedPatch,
  type LiveJoinResultPatch,
  type LiveJoinStatePatch,
  type LiveLocalDeparturePatch,
  type LivePublishPermissionPatch,
} from '@gang-chat/core/app/live_controller';
import * as live_display from '@gang-chat/core/app/live_display';
import {
  LivePresenceAnnouncementAction,
  shouldSpeakLivePresenceAnnouncement,
  type LivePresenceAnnouncement,
} from '@gang-chat/core/app/live_presence_announcement';
import * as room_display from '@gang-chat/core/app/room_display';
import * as member_filter from '@gang-chat/core/app/room_members_filter';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import { TimeoutException, delay, unawaited, withTimeout } from '@gang-chat/core/lib/async';
import type { JsonMap } from '@gang-chat/core/lib/json';
import { LivePresenceSound } from '@gang-chat/core/live/live_presence_sound_service';
import {
  supportsDesktopScreenShare,
  supportsLocalScreenShare,
  type LiveParticipantDepartureKind,
  type LiveVideoTrack,
  type ScreenSource,
} from '@gang-chat/core/live/live_session';
import { ApiException } from '@gang-chat/core/protocol/api_client';
import {
  LiveJoinResult,
  LiveState,
  RoomMember,
  type LiveParticipant,
  type RoomDetail,
  type UserSummary,
} from '@gang-chat/core/protocol/models';
import { platform } from '@gang-chat/core/platform/platform';
import type { DesktopWindowController } from '@gang-chat/core/shell/desktop_window_controller';

import { Text } from '../lib/layout';
import { ButtonTone } from '../ui/button';
import { DialogFrame, ResponsiveDialogAction, showFloatingErrorNotice } from '../ui/feedback';
import { Icons } from '../ui/icon';
import { showDialog } from '../ui/overlay';
import { UiTypography } from '../ui/tokens';
import { _ContentMode, _supportsWindowManagement, type HomeShellState } from './home_shell';
import { LiveStageSelection, LiveStageSelectionMode } from './live_channel_pane';
import { LiveScreenSharePicker } from './live_screen_share_picker';

/** `WidgetsBinding.instance.endOfFrame`: resolves after the next paint. */
function _endOfFrame(): Promise<void> {
  return new Promise((resolve) => {
    if (typeof requestAnimationFrame === 'function') {
      requestAnimationFrame(() => resolve());
    } else {
      setTimeout(resolve, 0);
    }
  });
}

export const HomeShellLiveActions = {
  _setLiveStageSelection(this: HomeShellState, selection: LiveStageSelection | null): void {
    const roomId = this._selectedServerId;
    if (roomId == null) return;
    this._setHomeState(() => {
      this._liveStageSelections.set(roomId, selection);
    });
    this._syncWatchedLiveStageSelection(selection);
  },

  _enterLiveFullScreen(this: HomeShellState, track: LiveVideoTrack): void {
    const generation = ++this._liveFullScreenTransitionGeneration;
    this._queueLiveFullScreenTransition(() => this._performEnterLiveFullScreen(track, generation));
  },

  async _performEnterLiveFullScreen(this: HomeShellState, track: LiveVideoTrack, generation: number): Promise<void> {
    if (!this.mounted || generation !== this._liveFullScreenTransitionGeneration) return;
    if (track.isLocal && track.isScreenShare && this.widget.androidSystemService.isSupported) {
      showFloatingErrorNotice('Android 无法安全排除本机窗口，屏幕共享期间请使用小窗预览');
      return;
    }
    const requiresCaptureExclusion =
      track.isLocal && track.isScreenShare && this.widget.windowController.supportsWindowsCaptureExclusion;
    if (requiresCaptureExclusion && !this._windowCaptureExcludedForLiveFullScreen) {
      const excluded = await this.widget.windowController.setWindowCaptureExcluded(true);
      if (!this.mounted || generation !== this._liveFullScreenTransitionGeneration) {
        if (excluded) {
          await this.widget.windowController.setWindowCaptureExcluded(false);
        }
        return;
      }
      if (!excluded) {
        showFloatingErrorNotice('无法将 Gang Chat 排除在屏幕捕获之外，已取消本机全屏预览');
        return;
      }
      this._windowCaptureExcludedForLiveFullScreen = true;
    }
    if (!requiresCaptureExclusion && this._windowCaptureExcludedForLiveFullScreen) {
      await this._restoreLiveFullScreenCapture();
      if (!this.mounted || generation !== this._liveFullScreenTransitionGeneration) {
        return;
      }
    }

    this._setHomeState(() => {
      this._fullScreenLiveTrack = track;
    });
    if (!track.isLocal) {
      if (track.isScreenShare) {
        unawaited(this._liveSessionController.setWatchedScreenShareIdentity(track.identity));
      } else {
        unawaited(this._liveSessionController.setWatchedCameraIdentity(track.identity));
      }
    }
    await this._setSystemFullScreen(true);
  },

  _exitLiveFullScreen(this: HomeShellState): void {
    ++this._liveFullScreenTransitionGeneration;
    this._setHomeState(() => {
      this._fullScreenLiveTrack = null;
    });
    const selectedServerId = this._selectedServerId;
    this._syncWatchedLiveStageSelection(
      selectedServerId == null ? null : (this._liveStageSelections.get(selectedServerId) ?? null),
    );
    this._queueLiveFullScreenTransition(() => this._performExitLiveFullScreen());
  },

  async _performExitLiveFullScreen(this: HomeShellState): Promise<void> {
    await this._setSystemFullScreen(false);
    await this._restoreLiveFullScreenCapture();
  },

  async _restoreLiveFullScreenCapture(
    this: HomeShellState,
    opts: { windowController?: DesktopWindowController | null } = {},
  ): Promise<void> {
    if (!this._windowCaptureExcludedForLiveFullScreen) return;
    this._windowCaptureExcludedForLiveFullScreen = false;
    await (opts.windowController ?? this.widget.windowController).setWindowCaptureExcluded(false);
  },

  _resetLiveFullScreenForLifecycle(
    this: HomeShellState,
    opts: { windowController?: DesktopWindowController | null } = {},
  ): void {
    const windowController = opts.windowController ?? null;
    ++this._liveFullScreenTransitionGeneration;
    this._fullScreenLiveTrack = null;
    this._queueLiveFullScreenTransition(async () => {
      await this._setSystemFullScreen(false, { windowController });
      await this._restoreLiveFullScreenCapture({ windowController });
    });
  },

  _queueLiveFullScreenTransition(this: HomeShellState, transition: () => Promise<void>): void {
    const previous = this._liveFullScreenTransitionTail;
    this._liveFullScreenTransitionTail = (async () => {
      try {
        await previous;
      } catch {
        // A failed transition must not block the next one.
      }
      try {
        await transition();
      } catch {
        // Best effort.
      }
    })();
  },

  _syncWatchedLiveStageSelection(this: HomeShellState, selection: LiveStageSelection | null): void {
    let screenShareIdentity: string | null = null;
    let cameraIdentity: string | null = null;
    if (selection?.mode === LiveStageSelectionMode.track && selection.identity !== this._currentUser.id) {
      if (selection.isScreenShare === true) {
        screenShareIdentity = selection.identity ?? null;
      } else {
        cameraIdentity = selection.identity ?? null;
      }
    }
    unawaited(this._liveSessionController.setWatchedScreenShareIdentity(screenShareIdentity));
    unawaited(this._liveSessionController.setWatchedCameraIdentity(cameraIdentity));

    const roomId = this._joinedLiveRoomId;
    if (roomId == null) return;
    const previousSync = this._liveScreenViewSyncTail;
    this._liveScreenViewSyncTail = (async () => {
      try {
        await previousSync;
      } catch {
        // Keep the chain going.
      }
      if (!this.mounted || this._joinedLiveRoomId !== roomId) return;
      const isStillCurrentTarget = (): boolean => {
        const currentSelection = this._liveStageSelections.get(roomId) ?? null;
        const currentIdentity =
          currentSelection?.mode === LiveStageSelectionMode.track &&
          currentSelection.isScreenShare === true &&
          currentSelection.identity !== this._currentUser.id
            ? (currentSelection.identity ?? null)
            : null;
        return currentIdentity === screenShareIdentity;
      };

      for (let attempt = 0; attempt < 3; attempt += 1) {
        if (!this.mounted || this._joinedLiveRoomId !== roomId || !isStillCurrentTarget()) {
          return;
        }
        try {
          await this._liveController.updateMyLiveScreenView({
            roomId,
            broadcasterUserId: screenShareIdentity,
          });
          return;
        } catch (error) {
          const shouldRetry =
            screenShareIdentity != null &&
            error instanceof ApiException &&
            error.code === 'screen_share_not_active' &&
            attempt < 2;
          if (!shouldRetry) return;
          await delay(120 * (attempt + 1));
        }
      }
    })();
  },

  async _setSystemFullScreen(
    this: HomeShellState,
    fullScreen: boolean,
    opts: { windowController?: DesktopWindowController | null } = {},
  ): Promise<void> {
    await (opts.windowController ?? this.widget.windowController).setFullScreenMediaPlaybackActive(fullScreen);
    if (!_supportsWindowManagement()) return;
    try {
      // windowManager.isFullScreen / setFullScreen
      const window = platform().window;
      if (window == null) return;
      if ((await window.isFullScreen()) !== fullScreen) {
        await window.setFullScreen(fullScreen);
      }
    } catch {
      // Best effort.
    }
  },

  _resolveFullScreenLiveTrack(this: HomeShellState): LiveVideoTrack | null {
    const selected = this._fullScreenLiveTrack;
    if (selected == null) return null;
    for (const track of this._liveSessionController.videoTracks) {
      if (track.identity === selected.identity && track.isScreenShare === selected.isScreenShare) {
        return track;
      }
    }
    return null;
  },

  _onLiveSessionChanged(this: HomeShellState): void {
    if (!this.mounted) return;
    const joinedLiveRoomId = this._joinedLiveRoomId;
    const connectedParticipantIds = this._liveSessionController.connectedParticipantIdentities;
    if (
      joinedLiveRoomId != null &&
      joinedLiveRoomId === this._selectedServerId &&
      live_display.liveStateMissingConnectedParticipants(this._live ?? this._selectedRoom?.live, {
        connectedParticipantIds,
      })
    ) {
      unawaited(this._syncLiveConnectedParticipants(joinedLiveRoomId));
    }
    if (
      live_display.shouldPatchEndedLocalScreenShare({
        localScreenSharing: this._screenSharing,
        sessionScreenSharing: this._liveSessionController.isScreenSharing,
        joinedLiveRoomId: this._joinedLiveRoomId,
        selectedRoomId: this._selectedServerId,
      })
    ) {
      unawaited(this._patchLiveState({ screenSharing: false }));
    }
    if (this._fullScreenLiveTrack != null && this._resolveFullScreenLiveTrack() == null) {
      this._exitLiveFullScreen();
    }
    this._setHomeState(() => {});
  },

  _onLiveParticipantJoined(this: HomeShellState, participantIdentity: string): void {
    if (
      participantIdentity === this._currentUser.id ||
      this._joinedLivePresenceCueParticipantIds.has(participantIdentity)
    ) {
      return;
    }
    this._joinedLivePresenceCueParticipantIds.add(participantIdentity);
    // A media join is safe to announce immediately. The cue identity set
    // deduplicates the authoritative snapshot that normally follows it.
    this._playLivePresenceSound(LivePresenceSound.joined, {
      participantIdentity,
      announcementAction: LivePresenceAnnouncementAction.joined,
    });
  },

  _onLiveParticipantLeft(
    this: HomeShellState,
    _participantIdentity: string,
    _departureKind: LiveParticipantDepartureKind,
  ): void {
    // A transient transport loss emits this callback immediately while the
    // server intentionally keeps the participant as `reconnecting` for its
    // recovery grace period. Removing the card or playing a leave cue here
    // causes the exact mismatch reported in production. Track/media cleanup
    // remains inside LiveSession; roster and cue changes follow the next
    // authoritative server snapshot instead.
  },

  _seedJoinedLiveAuthoritativePresence(this: HomeShellState, live: LiveState): void {
    this._joinedLiveAuthoritativeParticipantIds = live_display.authoritativeLivePresenceUserIds(live);
    this._joinedLiveAuthoritativeSnapshotAt = live.updatedAt;
    this._joinedLivePresenceCueParticipantIds.clear();
  },

  _clearJoinedLiveAuthoritativePresence(this: HomeShellState): void {
    this._joinedLiveAuthoritativeParticipantIds = new Set<string>();
    this._joinedLivePresenceCueParticipantIds.clear();
    this._joinedLiveAuthoritativeSnapshotAt = null;
  },

  _applyJoinedLiveAuthoritativeSnapshot(
    this: HomeShellState,
    live: LiveState,
    opts: { eventType: string; eventData?: JsonMap },
  ): void {
    const { eventType, eventData = {} } = opts;
    if (live.roomId !== this._joinedLiveRoomId) return;
    const previousUpdatedAt = this._joinedLiveAuthoritativeSnapshotAt;
    if (previousUpdatedAt != null && live.updatedAt.getTime() < previousUpdatedAt.getTime()) {
      return;
    }
    const next = live_display.authoritativeLivePresenceUserIds(live);
    const changes = live_display.authoritativeLivePresenceChanges({
      before: this._joinedLiveAuthoritativeParticipantIds,
      after: next,
    });
    this._joinedLiveAuthoritativeParticipantIds = next;
    this._joinedLiveAuthoritativeSnapshotAt = live.updatedAt;

    for (const identity of changes.joined) {
      if (identity === this._currentUser.id) continue;
      if (this._joinedLivePresenceCueParticipantIds.has(identity)) continue;
      this._joinedLivePresenceCueParticipantIds.add(identity);
      this._playLivePresenceSound(LivePresenceSound.joined, {
        participantIdentity: identity,
        announcementAction: LivePresenceAnnouncementAction.joined,
      });
    }
    const removedUserIdValue = eventData['user_id'];
    const removedUserId = removedUserIdValue == null ? null : String(removedUserIdValue);
    const removedByModerator = eventType === 'live_participant_moderated' && eventData['action'] === 'kick';
    for (const identity of changes.left) {
      if (identity === this._currentUser.id) continue;
      this._joinedLivePresenceCueParticipantIds.delete(identity);
      this._playLivePresenceSound(LivePresenceSound.left, {
        participantIdentity: identity,
        announcementAction:
          removedByModerator && removedUserId === identity
            ? LivePresenceAnnouncementAction.removed
            : LivePresenceAnnouncementAction.left,
      });
    }

    // Keep a remote stage selection through a transient media reconnect so it
    // can recover in place. Clear it only when the server has confirmed that
    // participant is no longer present.
    const roomId = this._joinedLiveRoomId;
    const selection = roomId == null ? null : (this._liveStageSelections.get(roomId) ?? null);
    const selectedParticipantLeft =
      selection?.mode === LiveStageSelectionMode.track &&
      selection.identity != null &&
      changes.left.has(selection.identity);
    if (roomId != null && selectedParticipantLeft) {
      this._liveStageSelections.set(roomId, LiveStageSelection.none());
      this._syncWatchedLiveStageSelection(LiveStageSelection.none());
    }
    const fullScreenTrack = this._fullScreenLiveTrack;
    if (fullScreenTrack != null && !fullScreenTrack.isLocal && changes.left.has(fullScreenTrack.identity)) {
      this._exitLiveFullScreen();
    }
  },

  _playLivePresenceSound(
    this: HomeShellState,
    sound: LivePresenceSound,
    opts: {
      participantIdentity?: string | null;
      announcementAction?: LivePresenceAnnouncementAction | null;
      continueAfterRoomExit?: boolean;
    } = {},
  ): void {
    const { continueAfterRoomExit = false } = opts;
    const roomId = this._joinedLiveRoomId;
    if (roomId == null) return;
    const playback = this._livePresenceEventTail.then(() =>
      this._queueLivePresenceAudio(sound, {
        roomId,
        participantIdentity: opts.participantIdentity ?? null,
        announcementAction: opts.announcementAction ?? null,
        continueAfterRoomExit,
      }),
    );
    this._livePresenceEventTail = playback.catch(() => {});
  },

  async _queueLivePresenceAudio(
    this: HomeShellState,
    sound: LivePresenceSound,
    opts: {
      roomId: string;
      participantIdentity: string | null;
      announcementAction: LivePresenceAnnouncementAction | null;
      continueAfterRoomExit: boolean;
    },
  ): Promise<void> {
    const { roomId, participantIdentity, announcementAction, continueAfterRoomExit } = opts;
    if (!continueAfterRoomExit && this._joinedLiveRoomId !== roomId) return;
    const volume = this._headphonesMuted ? 0.0 : normalizedAudioVolume(this._liveSessionController.outputVolume);
    let announcement: LivePresenceAnnouncement | null = null;
    // This personal preference gates only synthesized speech. The coordinator
    // below always receives the join/leave cue, even when announcements are off.
    const shouldAnnounce = shouldSpeakLivePresenceAnnouncement({
      enabled: this._joinedLivePersonalAiVoiceAnnouncementsEnabled,
      participantIdentity,
      currentUserIdentity: this._currentUser.id,
    });
    if (shouldAnnounce && announcementAction != null) {
      const user = await this._resolveJoinedLiveParticipantUser(roomId, participantIdentity!);
      if (user != null && this._joinedLiveRoomId === roomId) {
        announcement = room_display.livePresenceAnnouncementForUser({
          user,
          action: announcementAction,
        });
      }
    }
    await this._livePresenceAudioCoordinator.play(sound, {
      volume,
      announcement,
    });
  },

  async _resolveJoinedLiveParticipantUser(
    this: HomeShellState,
    roomId: string,
    participantIdentity: string,
  ): Promise<UserSummary | null> {
    const cached = this._joinedLiveParticipantUsers.get(participantIdentity);
    if (cached != null) return cached;
    try {
      const live = await this._roomsController.getLiveState(roomId);
      if (this._joinedLiveRoomId !== roomId) return null;
      this._cacheJoinedLiveParticipantUsers(live);
      return this._joinedLiveParticipantUsers.get(participantIdentity) ?? null;
    } catch {
      return null;
    }
  },

  _cacheJoinedLiveParticipantUsers(this: HomeShellState, live: LiveState): void {
    if (live.roomId !== this._joinedLiveRoomId) return;
    for (const participant of live.participants) {
      this._joinedLiveParticipantUsers.set(participant.user.id, participant.user);
    }
  },

  async _syncLiveConnectedParticipants(this: HomeShellState, roomId: string): Promise<void> {
    if (this._syncingLiveConnectedParticipants) return;
    this._syncingLiveConnectedParticipants = true;
    try {
      await this._refreshLiveSilently(roomId);
    } finally {
      if (this.mounted) this._syncingLiveConnectedParticipants = false;
    }
  },

  _onForciblyRemovedFromLive(this: HomeShellState): void {
    if (!this.mounted) return;
    this._playLivePresenceSound(LivePresenceSound.left, { continueAfterRoomExit: true });
    const patch = this._liveController.patchLocalDeparture({
      live: this._live,
      rooms: this._servers,
      joinedLiveRoomId: this._joinedLiveRoomId,
      userId: this._currentUser.id,
      joiningLive: false,
    });
    this._setHomeState(() => {
      this._leavingLive = false;
      this._applyLiveLocalDeparturePatch(patch);
    });
    this._syncWatchedLiveStageSelection(null);
    unawaited(this._liveSessionController.disconnect());
  },

  _onUnexpectedlyDisconnectedFromLive(this: HomeShellState): void {
    if (!this.mounted) return;
    const roomId = this._joinedLiveRoomId;
    if (roomId == null) return;
    unawaited(this._restoreLiveAfterRealtimeReconnect(roomId));
  },

  _onPublishPermissionChanged(this: HomeShellState, canPublish: boolean): void {
    if (!this.mounted) return;
    const patch = this._liveController.patchPublishPermission({
      canPublish,
      micMuted: this._micMuted,
    });
    this._setHomeState(() => this._applyLivePublishPermissionPatch(patch));
  },

  _applyLiveJoinStatePatch(this: HomeShellState, patch: LiveJoinStatePatch): void {
    this._joinedLiveRoomId = patch.joinedLiveRoomId;
    this._joiningLive = patch.joiningLive;
    this._contentMode = patch.livePanelOpen ? _ContentMode.live : this._contentMode;
    this._roomError = patch.error;
    if (patch.joinedLiveRoomId == null) {
      this._joinedLivePersonalAiVoiceAnnouncementsEnabled = false;
      this._joinedLiveParticipantUsers.clear();
      this._clearJoinedLiveAuthoritativePresence();
    }
  },

  _applyLiveJoinPreviousRoomDisconnectedPatch(this: HomeShellState, patch: LiveJoinPreviousRoomDisconnectedPatch): void {
    const departedRoomId = this._joinedLiveRoomId;
    this._live = patch.live;
    this._servers = this._roomsController.patchRoomCardsRefreshed({ rooms: patch.rooms }).rooms;
    this._joinedLiveRoomId = patch.joinedLiveRoomId;
    this._joiningLive = patch.joiningLive;
    this._contentMode = patch.livePanelOpen ? _ContentMode.live : this._contentMode;
    this._roomError = patch.error;
    if (departedRoomId != null && departedRoomId !== patch.joinedLiveRoomId) {
      this._liveStageSelections.delete(departedRoomId);
      this._clearJoinedLiveAuthoritativePresence();
    }
  },

  _applyLiveLocalDeparturePatch(this: HomeShellState, patch: LiveLocalDeparturePatch): void {
    const departedRoomId = this._joinedLiveRoomId;
    this._live = patch.live;
    this._servers = this._roomsController.patchRoomCardsRefreshed({ rooms: patch.rooms }).rooms;
    this._joinedLiveRoomId = patch.joinedLiveRoomId;
    this._joiningLive = patch.joiningLive;
    this._cameraOn = patch.cameraOn;
    this._screenSharing = patch.screenSharing;
    this._voiceBlocked = patch.voiceBlocked;
    if (patch.joinedLiveRoomId == null) {
      // A selected remote camera/share belongs to this voice participation,
      // not to the room UI. Forget it when that participation ends so a later
      // manual join never resumes watching stale media. Realtime reconnects do
      // not pass through this departure patch and therefore keep watching.
      if (departedRoomId != null) {
        this._liveStageSelections.delete(departedRoomId);
      }
      this._joinedLivePersonalAiVoiceAnnouncementsEnabled = false;
      this._joinedLiveParticipantUsers.clear();
      this._clearJoinedLiveAuthoritativePresence();
    }
  },

  _applyLivePublishPermissionPatch(this: HomeShellState, patch: LivePublishPermissionPatch): void {
    this._voiceBlocked = patch.voiceBlocked;
    this._micMuted = patch.micMuted;
  },

  _applyLiveJoinResultPatch(this: HomeShellState, patch: LiveJoinResultPatch): void {
    this._micMuted = patch.micMuted;
    this._headphonesMuted = patch.headphonesMuted;
    this._cameraOn = patch.cameraOn;
    this._screenSharing = patch.screenSharing;
    this._voiceBlocked = patch.voiceBlocked;
    this._live = patch.live;
    this._servers = this._roomsController.patchRoomCardsRefreshed({ rooms: patch.rooms }).rooms;
  },

  _withCurrentRoomLiveDisplayNameInJoinResult(this: HomeShellState, result: LiveJoinResult, roomId: string): LiveJoinResult {
    const participant = this._withCurrentRoomLiveDisplayName(result.participant, roomId);
    const live = this._withCurrentRoomLiveDisplayNameInLive(result.live, roomId);
    if (participant === result.participant && live === result.live) {
      return result;
    }
    return new LiveJoinResult({
      liveKit: result.liveKit,
      participant,
      live,
    });
  },

  _withCurrentRoomLiveDisplayNameInLive(this: HomeShellState, live: LiveState, roomId: string): LiveState {
    if (live.roomId !== roomId) return live;
    let changed = false;
    const participants = live.participants.map((participant) =>
      this._withCurrentRoomLiveDisplayName(participant, roomId, {
        changed: () => {
          changed = true;
        },
      }),
    );
    if (!changed) return live;
    return new LiveState({
      roomId: live.roomId,
      participantCount: live.participantCount,
      participants,
      updatedAt: live.updatedAt,
    });
  },

  _withCurrentRoomLiveDisplayName(
    this: HomeShellState,
    participant: LiveParticipant,
    roomId: string,
    opts: { changed?: (() => void) | null } = {},
  ): LiveParticipant {
    if (participant.user.id !== this._currentUser.id) return participant;
    const displayName =
      this._selectedRoom?.id === roomId ? (this._selectedRoom?.personalProfile.displayName?.trim() ?? null) : null;
    if (displayName == null || displayName.length === 0) return participant;
    if (participant.user.roomDisplayName?.trim() === displayName) {
      return participant;
    }
    opts.changed?.();
    return participant.copyWith({
      user: participant.user.copyWith({ roomDisplayName: displayName }),
    });
  },

  _setMicMutedLocally(this: HomeShellState, muted: boolean, opts: { syncVolume?: boolean } = {}): void {
    const { syncVolume = true } = opts;
    if (syncVolume) {
      const volume = muted ? 0.0 : this._restoredInputVolume();
      if (muted) {
        this._rememberInputVolume(this._liveSessionController.inputVolume);
      }
      unawaited(this._liveSessionController.setInputVolume(volume, { persist: false }));
    }
    unawaited(this._liveSessionController.setMicMuted(muted));
    if (this._micMuted !== muted) {
      this._setHomeState(() => {
        this._micMuted = muted;
      });
    }
  },

  _syncLocalAudioFromParticipant(this: HomeShellState, participant: LiveParticipant): void {
    const authoritativeMicMuted = participant.micMuted || participant.micBlocked || participant.voiceBlocked;
    const authoritativeHeadphonesMuted = participant.headphonesMuted || participant.headphonesBlocked;
    if (this._micMuted !== authoritativeMicMuted) {
      this._setMicMutedLocally(authoritativeMicMuted);
    }
    if (this._headphonesMuted !== authoritativeHeadphonesMuted) {
      this._setHeadphonesMuted(authoritativeHeadphonesMuted, {
        patchState: false,
        coupleMic: false,
      });
    }
  },

  _syncJoinedLiveAudioFromSnapshot(this: HomeShellState, live: LiveState): void {
    if (!this._liveStateRequests.isIdle || this._joinedLiveRoomId !== live.roomId) return;
    for (const participant of live.participants) {
      if (participant.user.id !== this._currentUser.id) continue;
      this._syncLocalAudioFromParticipant(participant);
      return;
    }
  },

  _toggleMicMute(this: HomeShellState): void {
    const muted = !this._micMuted;
    const unmuteHeadphones = !muted && this._headphonesMuted;
    this._setMicMutedLocally(muted);
    if (unmuteHeadphones) {
      this._setHeadphonesMuted(false, { patchState: false });
    }
    unawaited(
      this._patchLiveState({
        micMuted: muted,
        headphonesMuted: unmuteHeadphones ? false : null,
        syncLiveKitMic: false,
      }),
    );
  },

  _toggleHeadphonesMute(this: HomeShellState): void {
    const patch = liveOutputMuteToggled({ headphonesMuted: this._headphonesMuted });
    this._setHeadphonesMuted(patch.headphonesMuted);
  },

  _setHeadphonesMuted(
    this: HomeShellState,
    muted: boolean,
    opts: { syncVolume?: boolean; patchState?: boolean; coupleMic?: boolean } = {},
  ): void {
    const { syncVolume = true, patchState = true, coupleMic = true } = opts;
    const headphonesChanged = this._headphonesMuted !== muted;
    const muteMic = coupleMic && muted && !this._micMuted;
    if (!headphonesChanged && !muteMic) return;

    this._setHomeState(() => {
      if (muteMic) {
        this._micMuted = true;
      }
      if (headphonesChanged) {
        this._headphonesMuted = muted;
      }
    });

    if (muteMic) {
      this._rememberInputVolume(this._liveSessionController.inputVolume);
      unawaited(this._liveSessionController.setInputVolume(0, { persist: false }));
      unawaited(this._liveSessionController.setMicMuted(true));
    }
    if (headphonesChanged && syncVolume) {
      const volume = muted ? 0.0 : this._restoredOutputVolume();
      if (muted) {
        this._rememberOutputVolume(this._liveSessionController.outputVolume);
      }
      unawaited(this._liveSessionController.setOutputVolume(volume, { persist: false }));
    }
    if (headphonesChanged) {
      unawaited(this._liveSessionController.setOutputMuted(muted));
    }
    // Report the headphone state to the server so other participants see it on
    // the live snapshot. Best-effort: a failure here doesn't undo the local
    // mute, which already took effect on the LiveKit session above.
    if (patchState) {
      const micMutedPatch = muteMic || (!muted && this._micMuted) ? true : null;
      unawaited(
        this._patchLiveState({
          micMuted: micMutedPatch,
          headphonesMuted: headphonesChanged ? muted : null,
          syncLiveKitMic: micMutedPatch == null,
        }),
      );
    }
  },

  _changeInputVolume(this: HomeShellState, volume: number): void {
    const normalized = normalizedAudioVolume(volume);
    if (normalized === 0) {
      this._rememberInputVolume(this._liveSessionController.inputVolume);
    } else {
      this._rememberInputVolume(normalized);
    }
    unawaited(this._liveSessionController.setInputVolume(normalized));
    const muted = normalized === 0;
    if (!muted && this._voiceBlocked) return;
    const micChanged = muted !== this._micMuted;
    if (micChanged) {
      this._setMicMutedLocally(muted, { syncVolume: false });
    }
    const unmuteHeadphones = !muted && this._headphonesMuted;
    if (unmuteHeadphones) {
      this._setHeadphonesMuted(false, { patchState: false });
    }
    if (!micChanged && !unmuteHeadphones) return;
    unawaited(
      this._patchLiveState({
        micMuted: micChanged ? muted : null,
        headphonesMuted: unmuteHeadphones ? false : null,
        syncLiveKitMic: false,
      }),
    );
  },

  _changeOutputVolume(this: HomeShellState, volume: number): void {
    const normalized = normalizedAudioVolume(volume);
    if (normalized === 0) {
      this._rememberOutputVolume(this._liveSessionController.outputVolume);
    } else {
      this._rememberOutputVolume(normalized);
    }
    unawaited(this._liveSessionController.setOutputVolume(normalized));
    this._setHeadphonesMuted(normalized === 0, { syncVolume: false });
  },

  _changeScreenShareVolume(this: HomeShellState, volume: number): void {
    const normalized = normalizedAudioVolume(volume);
    if (normalized > 0) {
      this._lastScreenShareVolumeBeforeMute = rememberedAudioVolume(normalized);
    }
    this._runLiveAudioStateChange(this._liveSessionController.setScreenShareVolume(normalized));
  },

  _toggleScreenShareAudioMute(this: HomeShellState): void {
    const current = normalizedAudioVolume(this._liveSessionController.screenShareVolume);
    if (current <= 0) {
      this._changeScreenShareVolume(restoredAudioVolume(this._lastScreenShareVolumeBeforeMute));
      return;
    }
    this._lastScreenShareVolumeBeforeMute = rememberedAudioVolume(current);
    this._changeScreenShareVolume(0);
  },

  _participantVoiceVolume(this: HomeShellState, userId: string): number {
    unawaited(this._restoreParticipantVoiceVolume(userId));
    return this._liveSessionController.participantVoiceVolume(userId);
  },

  _changeParticipantVoiceVolume(this: HomeShellState, userId: string, volume: number): void {
    this._runLiveAudioStateChange(this._liveSessionController.setParticipantVoiceVolume(userId, volume));
  },

  _toggleParticipantVoiceMute(this: HomeShellState, userId: string): void {
    this._runLiveAudioStateChange(this._liveSessionController.toggleParticipantVoiceMuted(userId));
  },

  async _restoreParticipantVoiceVolume(this: HomeShellState, userId: string): Promise<void> {
    const changed = await this._liveSessionController.restoreParticipantVoiceVolume(userId);
    if (!changed || !this.mounted) return;
    this._setHomeState(() => {});
  },

  _runLiveAudioStateChange(this: HomeShellState, change: Promise<void>): void {
    // LiveSession updates its normalized volume before applying it to tracks.
    // Rebuild now so taps and slider changes are reflected immediately, then
    // rebuild once more after the asynchronous device/persistence work ends.
    if (this.mounted) this._setHomeState(() => {});
    unawaited(this._finishLiveAudioStateChange(change));
  },

  async _finishLiveAudioStateChange(this: HomeShellState, change: Promise<void>): Promise<void> {
    await change;
    if (!this.mounted) return;
    this._setHomeState(() => {});
  },

  _canModerateLiveParticipant(this: HomeShellState, participant: LiveParticipant): boolean {
    const room = this._selectedRoom;
    if (
      room == null ||
      this._busyLiveMemberRemovalIds.has(participant.user.id) ||
      this._busyLiveMemberModerationIds.has(participant.user.id)
    ) {
      return false;
    }
    return this._liveParticipantPermission({ participant, room }).canRemoveMember;
  },

  _canRemoveLiveParticipant(this: HomeShellState, participant: LiveParticipant): boolean {
    const room = this._selectedRoom;
    if (room == null || this._busyLiveMemberRemovalIds.has(participant.user.id)) {
      return false;
    }
    return this._liveParticipantPermission({ participant, room }).canRemoveMember;
  },

  _liveParticipantPermission(
    this: HomeShellState,
    opts: { participant: LiveParticipant; room: RoomDetail },
  ): member_filter.RoomMemberPermissionState {
    const { participant, room } = opts;
    const managementPermission = room_display.roomManagementPermissionState({
      room,
      currentUser: this._currentUser,
    });
    return member_filter.roomMemberPermissionState({
      member: this._roomMemberFromLiveParticipant(participant, room),
      currentUser: this._currentUser,
      canEditCreatorOnly: managementPermission.canEditCreatorOnly,
      canManageMembers: room.isAdmin || this._currentUser.isSuperuser,
      ownerUserId: room.createdBy?.id ?? null,
    });
  },

  _toggleLiveParticipantMicModeration(this: HomeShellState, participant: LiveParticipant): Promise<void> {
    const blocked = participant.micBlocked || participant.voiceBlocked;
    const action = blocked ? LiveModerationAction.restoreVoice : LiveModerationAction.muteMic;
    return this._moderateLiveParticipant(participant, action);
  },

  _toggleLiveParticipantHeadphonesModeration(this: HomeShellState, participant: LiveParticipant): Promise<void> {
    const action = participant.headphonesBlocked
      ? LiveModerationAction.restoreHeadphones
      : LiveModerationAction.blockVoice;
    return this._moderateLiveParticipant(participant, action);
  },

  async _moderateLiveParticipant(
    this: HomeShellState,
    participant: LiveParticipant,
    action: LiveModerationAction,
  ): Promise<void> {
    const room = this._selectedRoom;
    if (room == null || !this._canModerateLiveParticipant(participant)) return;
    const member = this._roomMemberFromLiveParticipant(participant, room);
    const confirmed = await showDialog<boolean>((close) => (
      <_LiveMemberModerationConfirmDialog member={member} action={action} onClose={close} />
    ));
    if (confirmed !== true || !this.mounted) return;
    const userId = participant.user.id;
    if (this._busyLiveMemberModerationIds.has(userId)) return;
    this._setHomeState(() => {
      this._busyLiveMemberModerationIds.add(userId);
      this._roomError = null;
    });
    try {
      await this._liveController.moderateParticipant({
        roomId: room.id,
        userId,
        action,
      });
      if (!this.mounted) return;
      const live = this._liveController.patchModeratedParticipant({
        live: this._live,
        participant,
        action,
      });
      this._setHomeState(() => {
        this._busyLiveMemberModerationIds.delete(userId);
        this._live = live;
        this._roomError = null;
      });
    } catch (error) {
      if (!this.mounted) return;
      this._setHomeState(() => {
        this._busyLiveMemberModerationIds.delete(userId);
        this._roomError = userFacingErrorMessage(error);
      });
    }
  },

  async _removeLiveParticipant(this: HomeShellState, participant: LiveParticipant): Promise<void> {
    const room = this._selectedRoom;
    if (room == null || !this._canRemoveLiveParticipant(participant)) return;
    const member = this._roomMemberFromLiveParticipant(participant, room);
    const confirmed = await showDialog<boolean>((close) => (
      <_LiveMemberRemoveConfirmDialog member={member} onClose={close} />
    ));
    if (confirmed !== true || !this.mounted) return;
    const userId = participant.user.id;
    if (this._busyLiveMemberRemovalIds.has(userId)) return;
    this._setHomeState(() => {
      this._busyLiveMemberRemovalIds.add(userId);
      this._roomError = null;
    });
    try {
      await this._liveController.kickParticipant({ roomId: room.id, userId });
      if (!this.mounted) return;
      const patch = this._liveController.removeUserFromLive({
        live: this._live,
        rooms: this._servers,
        roomId: room.id,
        userId,
      });
      const wasAuthoritativelyPresent = this._joinedLiveAuthoritativeParticipantIds.delete(userId);
      this._joinedLivePresenceCueParticipantIds.delete(userId);
      if (wasAuthoritativelyPresent) {
        this._playLivePresenceSound(LivePresenceSound.left, {
          participantIdentity: userId,
          announcementAction: LivePresenceAnnouncementAction.removed,
        });
      }
      this._setHomeState(() => {
        this._busyLiveMemberRemovalIds.delete(userId);
        this._live = patch.live;
        this._servers = patch.rooms;
        this._roomError = null;
      });
    } catch (error) {
      if (!this.mounted) return;
      this._setHomeState(() => {
        this._busyLiveMemberRemovalIds.delete(userId);
        this._roomError = userFacingErrorMessage(error);
      });
    }
  },

  _roomMemberFromLiveParticipant(this: HomeShellState, participant: LiveParticipant, room: RoomDetail): RoomMember {
    const role = participant.user.roomRole ?? (participant.user.id === room.createdBy?.id ? 'owner' : 'member');
    return new RoomMember({
      user: participant.user,
      role,
      joinedAt: participant.joinedAt,
      roomDisplayName: participant.user.roomDisplayName,
    });
  },

  _syncSettingsAudioVolumeToLive(this: HomeShellState, kind: string, volume: number): void {
    if (kind === 'audioinput') {
      this._changeInputVolume(volume);
      return;
    }
    if (kind === 'audiooutput') {
      this._changeOutputVolume(volume);
    }
  },

  _rememberInputVolume(this: HomeShellState, volume: number): void {
    this._lastInputVolumeBeforeMute = rememberedAudioVolume(volume);
  },

  _rememberOutputVolume(this: HomeShellState, volume: number): void {
    this._lastOutputVolumeBeforeMute = rememberedAudioVolume(volume);
  },

  _restoredInputVolume(this: HomeShellState): number {
    return restoredAudioVolume(this._lastInputVolumeBeforeMute);
  },

  _restoredOutputVolume(this: HomeShellState): number {
    return restoredAudioVolume(this._lastOutputVolumeBeforeMute);
  },

  async _joinLive(this: HomeShellState, source: string): Promise<void> {
    const room = this._selectedRoom;
    if (room == null || this._joiningLive || this._leavingLive) return;
    // Opening a room normally starts this fetch already. Retrying at the live
    // lifecycle boundary covers startup/token-refresh races without delaying
    // the voice join itself.
    this._ensureMusicBoxLoaded(room.id);
    const desiredMicMuted = this._micMuted;
    const desiredHeadphonesMuted = this._headphonesMuted;

    if (this.widget.androidSystemService.isSupported) {
      try {
        await this.widget.androidSystemService.requestBluetoothConnectPermission();
      } catch {
        // Speaker/earpiece routing remains usable when nearby-device access is
        // denied or unavailable.
      }
      if (!this.mounted) return;
    }

    this._setHomeState(() =>
      this._applyLiveJoinStatePatch(this._liveController.patchJoinStarted({ joinedLiveRoomId: this._joinedLiveRoomId })),
    );

    const previousLiveRoomId = joinedLiveRoomToDisconnectBeforeJoin({
      joinedLiveRoomId: this._joinedLiveRoomId,
      targetRoomId: room.id,
    });
    if (previousLiveRoomId != null) {
      this._playLivePresenceSound(LivePresenceSound.left, { continueAfterRoomExit: true });
      await this._notifyLiveLeft(previousLiveRoomId);
      await this._liveSessionController.disconnect();
      if (this.mounted) {
        this._setHomeState(() => {
          this._applyLiveJoinPreviousRoomDisconnectedPatch(
            this._liveController.patchJoinPreviousRoomDisconnected({
              live: this._live,
              rooms: this._servers,
              previousRoomId: previousLiveRoomId,
              userId: this._currentUser.id,
              livePanelOpen: true,
              error: this._roomError,
            }),
          );
        });
        this._syncWatchedLiveStageSelection(null);
      }
    }

    try {
      const result = await this._liveController.joinLive({
        roomId: room.id,
        source,
        micMuted: this._voiceBlocked ? true : desiredMicMuted,
        headphonesMuted: desiredHeadphonesMuted,
      });
      if (!this.mounted) return;
      const displayResult = this._withCurrentRoomLiveDisplayNameInJoinResult(result, room.id);
      const joinPatch = this._liveController.patchJoinResult({
        rooms: this._servers,
        result: displayResult,
      });
      this._setHomeState(() => {
        this._applyLiveJoinResultPatch(joinPatch);
        this._micMuted = joinPatch.voiceBlocked ? true : desiredMicMuted;
        this._headphonesMuted = desiredHeadphonesMuted;
        this._joinedLivePersonalAiVoiceAnnouncementsEnabled = room.aiVoiceAnnouncementsEnabled;
        this._joinedLiveParticipantUsers.clear();
        for (const participant of displayResult.live.participants) {
          this._joinedLiveParticipantUsers.set(participant.user.id, participant.user);
        }
        this._seedJoinedLiveAuthoritativePresence(displayResult.live);
      });
      try {
        await this._liveSessionController.connectWithRetry(displayResult, {
          isCancelled: () => !this.mounted,
          initialMicMuted: this._voiceBlocked ? true : desiredMicMuted,
        });
      } catch (error) {
        if (this.mounted) {
          this._setHomeState(() => {
            this._roomError = userFacingErrorMessage(error);
          });
        }
        return;
      }
      if (!this.mounted) return;
      this._setHomeState(() =>
        this._applyLiveJoinStatePatch(
          this._liveController.patchJoinConnected({
            roomId: room.id,
            livePanelOpen: true,
            error: this._roomError,
          }),
        ),
      );
      this._playLivePresenceSound(LivePresenceSound.joined);
      // Publish the user's pre-join audio choices and ready state atomically.
      // This also restores the last mic/headphones choice after leaving and
      // prevents the server's initial joining placeholder from leaking into
      // the visible roster.
      await this._restoreLiveParticipantState({
        roomId: room.id,
        micMuted: this._voiceBlocked ? true : desiredMicMuted,
        headphonesMuted: desiredHeadphonesMuted,
        cameraOn: false,
        cameraMirrored: false,
        screenSharing: false,
      });
      // Only publish the viewer relation after this participant is online.
      // Otherwise the screen-view snapshot can race the ready-state snapshot
      // and leave every client displaying the viewer as still "joining".
      this._syncWatchedLiveStageSelection(this._liveStageSelections.get(room.id) ?? null);
    } catch (error) {
      if (this.mounted) {
        this._setHomeState(() => {
          this._roomError = userFacingErrorMessage(error);
        });
      }
    } finally {
      if (this.mounted) {
        this._setHomeState(() =>
          this._applyLiveJoinStatePatch(
            this._liveController.patchJoinFinished({
              joinedLiveRoomId: this._joinedLiveRoomId,
              livePanelOpen: true,
              error: this._roomError,
            }),
          ),
        );
      }
    }
  },

  async _restoreLiveAfterRealtimeReconnect(this: HomeShellState, roomId: string): Promise<void> {
    if (this._joiningLive || this._joinedLiveRoomId !== roomId) return;
    this._ensureMusicBoxLoaded(roomId);
    if (this._liveSessionController.isAttachedToRoom(roomId)) {
      await this._refreshLiveSilently(roomId);
      return;
    }

    const previousMicMuted = this._micMuted;
    const previousHeadphonesMuted = this._headphonesMuted;
    const previousCameraOn = this._cameraOn;
    const previousCameraMirrored =
      live_display.liveParticipantByUserId(this._live ?? this._selectedRoom?.live, this._currentUser.id)
        ?.cameraMirrored ?? false;
    const previousScreenSharing = this._screenSharing;
    this._setHomeState(() => {
      this._joiningLive = true;
      this._roomError = null;
    });

    try {
      const result = await this._liveController.joinLive({
        roomId,
        source: 'reconnect',
        micMuted: previousMicMuted,
        headphonesMuted: previousHeadphonesMuted,
      });
      if (!this.mounted || this._joinedLiveRoomId !== roomId) return;

      const displayResult = this._withCurrentRoomLiveDisplayNameInJoinResult(result, roomId);
      const joinPatch = this._liveController.patchJoinResult({
        rooms: this._servers,
        result: displayResult,
      });
      this._setHomeState(() => {
        this._micMuted = joinPatch.micMuted;
        this._headphonesMuted = joinPatch.headphonesMuted;
        this._cameraOn = joinPatch.cameraOn;
        this._screenSharing = joinPatch.screenSharing;
        this._voiceBlocked = joinPatch.voiceBlocked;
        this._servers = joinPatch.rooms;
        if (this._selectedServerId === result.live.roomId) {
          this._live = joinPatch.live;
        }
      });

      try {
        await this._liveSessionController.connectWithRetry(result, {
          isCancelled: () => !this.mounted || this._joinedLiveRoomId !== roomId,
          initialMicMuted: joinPatch.voiceBlocked ? true : previousMicMuted,
        });
      } catch (error) {
        if (this.mounted) {
          this._setHomeState(() => {
            this._roomError = userFacingErrorMessage(error);
          });
        }
        return;
      }
      if (!this.mounted || this._joinedLiveRoomId !== roomId) return;

      const canPublish = !joinPatch.voiceBlocked;
      await this._restoreLiveParticipantState({
        roomId,
        micMuted: canPublish ? previousMicMuted : true,
        headphonesMuted: previousHeadphonesMuted,
        cameraOn: canPublish && previousCameraOn,
        cameraMirrored: previousCameraMirrored,
        screenSharing: canPublish && previousScreenSharing && this._liveSessionController.isScreenSharing,
      });
      this._syncWatchedLiveStageSelection(this._liveStageSelections.get(roomId) ?? null);
    } catch (error) {
      if (this.mounted) {
        this._setHomeState(() => {
          this._roomError = userFacingErrorMessage(error);
        });
      }
    } finally {
      if (this.mounted) {
        this._setHomeState(() => {
          this._joiningLive = false;
        });
      }
    }
  },

  async _restoreLiveParticipantState(
    this: HomeShellState,
    opts: {
      roomId: string;
      micMuted: boolean;
      headphonesMuted: boolean;
      cameraOn: boolean;
      cameraMirrored: boolean;
      screenSharing: boolean;
    },
  ): Promise<void> {
    const { roomId, micMuted, headphonesMuted, cameraOn, cameraMirrored, screenSharing } = opts;
    let restoredCameraOn = cameraOn;
    if (headphonesMuted) {
      try {
        await this._liveSessionController.setOutputMuted(true);
      } catch {
        // Best effort.
      }
    }
    if (restoredCameraOn) {
      try {
        await this._liveSessionController.setCameraEnabled(true);
      } catch (error) {
        restoredCameraOn = false;
        if (this.mounted) {
          this._setHomeState(() => {
            this._roomError = userFacingErrorMessage(error);
          });
        }
      }
    }
    if (!screenSharing && this._liveSessionController.isScreenSharing) {
      try {
        await this._stopLocalScreenShareSafely();
      } catch {
        // Best effort.
      }
    }

    try {
      await this._liveSessionController.setMicMuted(micMuted);
    } catch {
      // Best effort.
    }
    const effectiveMicMuted = micMuted === false ? this._liveSessionController.localMicMuted : micMuted;
    try {
      const participant = await this._liveStateRequests.run(() =>
        this._liveController.updateMyState({
          roomId,
          micMuted: effectiveMicMuted,
          headphonesMuted,
          cameraOn: restoredCameraOn,
          cameraMirrored,
          screenSharing,
          connectionState: 'online',
        }),
      );
      const displayParticipant = this._withCurrentRoomLiveDisplayName(participant, roomId);
      if (
        shouldSyncLiveKitMicAfterServerPatch({
          requestedMicMuted: effectiveMicMuted,
          serverMicMuted: displayParticipant.micMuted,
        })
      ) {
        try {
          await this._liveSessionController.setMicMuted(displayParticipant.micMuted);
        } catch {
          // Best effort.
        }
      }
      if (!this.mounted || this._joinedLiveRoomId !== roomId || !this._liveStateRequests.isIdle) {
        return;
      }
      const updateSelectedLive = this._selectedServerId === roomId;
      const patch = this._liveController.patchStateUpdate({
        live: updateSelectedLive ? this._live : null,
        participant: displayParticipant,
      });
      this._syncLocalAudioFromParticipant(displayParticipant);
      this._setHomeState(() => {
        this._micMuted = patch.micMuted;
        this._headphonesMuted = patch.headphonesMuted;
        this._cameraOn = patch.cameraOn;
        this._screenSharing = patch.screenSharing;
        this._voiceBlocked = patch.voiceBlocked;
        if (updateSelectedLive) this._live = patch.live;
      });
    } catch (error) {
      if (!this.mounted || isBenignGoneLiveStatePatch(error)) return;
      this._setHomeState(() => {
        this._roomError = userFacingErrorMessage(error);
      });
    }
  },

  async _leaveLive(this: HomeShellState): Promise<void> {
    const roomId = this._joinedLiveRoomId;
    if (roomId == null || this._leavingLive) return;
    this._playLivePresenceSound(LivePresenceSound.left, { continueAfterRoomExit: true });
    const shouldKeepLivePanelOpen = this._contentMode === _ContentMode.live;
    const patch = this._liveController.patchLocalDeparture({
      live: this._live,
      rooms: this._servers,
      joinedLiveRoomId: roomId,
      userId: this._currentUser.id,
      joiningLive: false,
    });
    this._setHomeState(() => {
      this._leavingLive = true;
      this._applyLiveLocalDeparturePatch(patch);
    });
    this._syncWatchedLiveStageSelection(null);
    try {
      await this._notifyLiveLeft(roomId);
      await this._liveSessionController.disconnect();
    } catch (error) {
      if (this.mounted) {
        this._setHomeState(() => {
          this._roomError = userFacingErrorMessage(error);
        });
      }
    } finally {
      if (this.mounted) {
        this._setHomeState(() => {
          this._leavingLive = false;
          this._applyLiveJoinStatePatch(
            this._liveController.patchJoinFinished({
              joinedLiveRoomId: this._joinedLiveRoomId,
              livePanelOpen: shouldKeepLivePanelOpen,
              error: this._roomError,
            }),
          );
        });
      }
    }
  },

  async _notifyLiveLeft(this: HomeShellState, roomId: string): Promise<void> {
    try {
      await this._liveStateRequests.run(() => this._liveController.leaveLive({ roomId }));
    } catch (error) {
      if (!this.mounted || isBenignGoneLiveStatePatch(error)) return;
      this._setHomeState(() => {
        this._roomError = userFacingErrorMessage(error);
      });
    }
  },

  async _patchLiveState(
    this: HomeShellState,
    opts: {
      micMuted?: boolean | null;
      headphonesMuted?: boolean | null;
      cameraOn?: boolean | null;
      cameraMirrored?: boolean | null;
      screenSharing?: boolean | null;
      connectionState?: string | null;
      syncLiveKitMic?: boolean;
    } = {},
  ): Promise<void> {
    const micMuted = opts.micMuted ?? null;
    const { syncLiveKitMic = true } = opts;
    const roomId = this._joinedLiveRoomId;
    if (roomId == null) return;
    try {
      let effectiveMicMuted: boolean | null = micMuted;
      const shouldApplyLocalMic = micMuted != null && (syncLiveKitMic || micMuted === false);
      if (shouldApplyLocalMic) {
        try {
          await this._liveSessionController.setMicMuted(micMuted);
        } catch {
          // Best effort.
        }
        if (micMuted === false) {
          effectiveMicMuted = this._liveSessionController.localMicMuted;
        }
      }
      const participant = await this._liveStateRequests.run(() =>
        this._liveController.updateMyState({
          roomId,
          micMuted: effectiveMicMuted,
          headphonesMuted: opts.headphonesMuted ?? null,
          cameraOn: opts.cameraOn ?? null,
          cameraMirrored: opts.cameraMirrored ?? null,
          screenSharing: opts.screenSharing ?? null,
          connectionState: opts.connectionState ?? null,
        }),
      );
      const displayParticipant = this._withCurrentRoomLiveDisplayName(participant, roomId);
      if (
        shouldSyncLiveKitMicAfterServerPatch({
          requestedMicMuted: effectiveMicMuted,
          serverMicMuted: displayParticipant.micMuted,
        })
      ) {
        try {
          await this._liveSessionController.setMicMuted(displayParticipant.micMuted);
        } catch {
          // Best effort.
        }
      }
      if (!this.mounted || this._joinedLiveRoomId !== roomId || !this._liveStateRequests.isIdle) {
        return;
      }
      const updateSelectedLive = canPatchSelectedLiveState({
        joinedLiveRoomId: roomId,
        selectedRoomId: this._selectedServerId,
      });
      const patch = this._liveController.patchStateUpdate({
        live: updateSelectedLive ? this._live : null,
        participant: displayParticipant,
      });
      this._syncLocalAudioFromParticipant(displayParticipant);
      this._setHomeState(() => {
        this._micMuted = patch.micMuted;
        this._headphonesMuted = patch.headphonesMuted;
        this._cameraOn = patch.cameraOn;
        this._screenSharing = patch.screenSharing;
        this._voiceBlocked = patch.voiceBlocked;
        if (updateSelectedLive) this._live = patch.live;
      });
    } catch (error) {
      if (!this.mounted || isBenignGoneLiveStatePatch(error)) return;
      this._setHomeState(() => {
        this._roomError = userFacingErrorMessage(error);
      });
    }
  },

  async _toggleCamera(this: HomeShellState): Promise<void> {
    const useTransitionGuard = !HostPlatform.isWeb && HostPlatform.isAndroid;
    if (!useTransitionGuard) {
      await this._toggleCameraImpl();
      return;
    }
    if (this._switchingAndroidLocalVideoSource) return;
    this._switchingAndroidLocalVideoSource = true;
    try {
      await this._toggleCameraImpl();
    } finally {
      this._switchingAndroidLocalVideoSource = false;
    }
  },

  async _flipLocalCamera(this: HomeShellState): Promise<boolean> {
    try {
      const flipped = await this._liveSessionController.flipCamera();
      if (!flipped && this.mounted) {
        this._setHomeState(() => {
          this._roomError = '当前设备无法翻转摄像头';
        });
      }
      return flipped;
    } catch (error) {
      if (this.mounted) {
        this._setHomeState(() => {
          this._roomError = userFacingErrorMessage(error);
        });
      }
      return false;
    }
  },

  async _setLocalCameraMirrored(this: HomeShellState, mirrored: boolean): Promise<boolean> {
    const roomId = this._joinedLiveRoomId;
    if (roomId == null) return false;
    const updateSelectedLive = canPatchSelectedLiveState({
      joinedLiveRoomId: roomId,
      selectedRoomId: this._selectedServerId,
    });
    try {
      const participant = await this._liveStateRequests.run(() =>
        this._liveController.updateMyState({
          roomId,
          cameraMirrored: mirrored,
        }),
      );
      const displayParticipant = this._withCurrentRoomLiveDisplayName(participant, roomId);
      if (!this.mounted || this._joinedLiveRoomId !== roomId || !this._liveStateRequests.isIdle) {
        return true;
      }
      const patch = this._liveController.patchStateUpdate({
        live: updateSelectedLive ? this._live : null,
        participant: displayParticipant,
      });
      this._setHomeState(() => {
        if (updateSelectedLive) this._live = patch.live;
      });
      return displayParticipant.cameraMirrored === mirrored;
    } catch (error) {
      if (this.mounted && !isBenignGoneLiveStatePatch(error)) {
        this._setHomeState(() => {
          this._roomError = userFacingErrorMessage(error);
        });
      }
      return false;
    }
  },

  async _toggleCameraImpl(this: HomeShellState): Promise<void> {
    const roomId = this._joinedLiveRoomId;
    if (
      roomId == null ||
      !canPatchSelectedLiveState({
        joinedLiveRoomId: roomId,
        selectedRoomId: this._selectedServerId,
      })
    ) {
      return;
    }
    const enable = !this._cameraOn;
    if (enable && this._screenSharing) {
      try {
        await this._stopLocalScreenShareSafely();
        if (this.mounted && this._screenSharing) {
          this._setHomeState(() => {
            this._screenSharing = false;
          });
        }
        await this._patchLiveState({ screenSharing: false });
      } catch (error) {
        if (this.mounted) {
          this._setHomeState(() => {
            this._roomError = userFacingErrorMessage(error);
          });
        }
        return;
      }
    }
    try {
      await this._liveSessionController.setCameraEnabled(enable);
    } catch (error) {
      if (this.mounted) {
        this._setHomeState(() => {
          this._roomError = userFacingErrorMessage(error);
        });
      }
      return;
    }
    await this._patchLiveState({
      cameraOn: enable,
      screenSharing: enable ? false : null,
    });
  },

  async _toggleScreenShare(this: HomeShellState): Promise<void> {
    const useTransitionGuard = !HostPlatform.isWeb && HostPlatform.isAndroid;
    if (!useTransitionGuard) {
      await this._toggleScreenShareImpl();
      return;
    }
    if (this._switchingAndroidLocalVideoSource) return;
    this._switchingAndroidLocalVideoSource = true;
    try {
      await this._toggleScreenShareImpl();
    } finally {
      this._switchingAndroidLocalVideoSource = false;
    }
  },

  async _toggleScreenShareImpl(this: HomeShellState): Promise<void> {
    if (!supportsLocalScreenShare()) return;
    const roomId = this._joinedLiveRoomId;
    if (
      roomId == null ||
      !canPatchSelectedLiveState({
        joinedLiveRoomId: roomId,
        selectedRoomId: this._selectedServerId,
      })
    ) {
      return;
    }

    if (this._screenSharing) {
      try {
        await this._stopLocalScreenShareSafely();
      } catch {
        // Best effort.
      }
      await this._patchLiveState({ screenSharing: false });
      return;
    }

    let source: ScreenSource | null = null;
    if (supportsDesktopScreenShare()) {
      source =
        (await showDialog<ScreenSource>(() => (
          <LiveScreenSharePicker
            loadSources={() => this._liveSessionController.listScreenSources()}
            refreshThumbnails={() => this._liveSessionController.refreshScreenSourceThumbnails()}
          />
        ))) ?? null;
      if (source == null || !this.mounted) return;
    }
    if (
      !canApplyPickedScreenShareSource({
        pickedForRoomId: roomId,
        joinedLiveRoomId: this._joinedLiveRoomId,
        selectedRoomId: this._selectedServerId,
      })
    ) {
      return;
    }

    const restoreCameraOnFailure = this._cameraOn;
    if (this._cameraOn) {
      try {
        await this._liveSessionController.setCameraEnabled(false);
        if (this.mounted && this._cameraOn) {
          this._setHomeState(() => {
            this._cameraOn = false;
          });
        }
      } catch (error) {
        if (this.mounted) {
          this._setHomeState(() => {
            this._roomError = userFacingErrorMessage(error);
          });
        }
        return;
      }
    }

    try {
      await this._liveSessionController.setScreenShareEnabled(true, { sourceId: source?.id ?? null });
    } catch (error) {
      if (restoreCameraOnFailure) {
        try {
          await this._liveSessionController.setCameraEnabled(true);
          if (this.mounted) {
            this._setHomeState(() => {
              this._cameraOn = true;
            });
          }
        } catch {
          // Best effort.
        }
      }
      if (this.mounted) {
        this._setHomeState(() => {
          this._roomError = userFacingErrorMessage(error);
        });
      }
      return;
    }
    await this._patchLiveState({ screenSharing: true, cameraOn: false });
  },

  async _stopLocalScreenShareSafely(this: HomeShellState): Promise<void> {
    const fullScreenTrack = this._fullScreenLiveTrack;
    const shouldDisposeAndroidFullScreenFirst =
      this.mounted &&
      this.widget.androidSystemService.isSupported &&
      fullScreenTrack != null &&
      fullScreenTrack.isLocal &&
      fullScreenTrack.isScreenShare;
    if (shouldDisposeAndroidFullScreenFirst) {
      // A full-screen renderer can still own the MediaProjection texture for
      // the current frame. Remove that renderer before asking WebRTC to
      // release the virtual display; otherwise Android may synchronously
      // wait for a texture/handler teardown that is itself waiting for the UI
      // thread.
      this._exitLiveFullScreen();
      await _endOfFrame();
      try {
        await withTimeout(this._liveFullScreenTransitionTail, 800);
      } catch (error) {
        // The renderer has already been disposed by the completed frame. A
        // slow system-UI transition must not permanently block capture stop.
        if (!(error instanceof TimeoutException)) throw error;
      }
    }
    await this._liveSessionController.setScreenShareEnabled(false);
  },
};

export interface _LiveMemberRemoveConfirmDialogProps {
  member: RoomMember;
  onClose: (result?: boolean) => void;
}

export function _LiveMemberRemoveConfirmDialog({ member, onClose }: _LiveMemberRemoveConfirmDialogProps) {
  return (
    <DialogFrame
      title="踢出语音频道"
      icon={Icons.warning_amber_outlined}
      adaptiveActions={[
        new ResponsiveDialogAction({ label: '取消', onPressed: () => onClose(false) }),
        new ResponsiveDialogAction({ label: '踢出', tone: ButtonTone.danger, onPressed: () => onClose(true) }),
      ]}
    >
      <Text style={UiTypography.body}>{`确定要将 ${member_filter.roomMemberDisplayName(member)} 踢出语音频道吗？`}</Text>
    </DialogFrame>
  );
}

export interface _LiveMemberModerationConfirmDialogProps {
  member: RoomMember;
  action: LiveModerationAction;
  onClose: (result?: boolean) => void;
}

export function _LiveMemberModerationConfirmDialog({ member, action, onClose }: _LiveMemberModerationConfirmDialogProps) {
  const name = member_filter.roomMemberDisplayName(member);
  return (
    <DialogFrame
      title={_moderationTitle(action)}
      icon={Icons.warning_amber_outlined}
      adaptiveActions={[
        new ResponsiveDialogAction({ label: '取消', onPressed: () => onClose(false) }),
        new ResponsiveDialogAction({
          label: _moderationConfirmLabel(action),
          tone: ButtonTone.danger,
          onPressed: () => onClose(true),
        }),
      ]}
    >
      <Text style={UiTypography.body}>{_moderationBody(action, name)}</Text>
    </DialogFrame>
  );
}

function _moderationTitle(action: LiveModerationAction): string {
  switch (action) {
    case LiveModerationAction.muteMic:
      return '麦克风静音此用户';
    case LiveModerationAction.blockVoice:
      return '耳机静音此用户';
    case LiveModerationAction.restoreVoice:
      return '取消麦克风静音';
    case LiveModerationAction.restoreHeadphones:
      return '取消耳机静音';
    case LiveModerationAction.kick:
    default:
      return '踢出语音频道';
  }
}

function _moderationConfirmLabel(action: LiveModerationAction): string {
  switch (action) {
    case LiveModerationAction.muteMic:
      return '麦克风静音';
    case LiveModerationAction.blockVoice:
      return '耳机静音';
    case LiveModerationAction.restoreVoice:
      return '取消麦克风静音';
    case LiveModerationAction.restoreHeadphones:
      return '取消耳机静音';
    case LiveModerationAction.kick:
    default:
      return '踢出';
  }
}

function _moderationBody(action: LiveModerationAction, name: string): string {
  switch (action) {
    case LiveModerationAction.muteMic:
      return `确定要将 ${name} 麦克风静音吗？对方的麦克风不会上传到语音频道。`;
    case LiveModerationAction.blockVoice:
      return `确定要将 ${name} 耳机静音吗？对方将无法听到语音频道。`;
    case LiveModerationAction.restoreVoice:
      return `确定要取消 ${name} 的麦克风静音吗？麦克风会恢复到正常状态。`;
    case LiveModerationAction.restoreHeadphones:
      return `确定要取消 ${name} 的耳机静音吗？耳机会恢复到正常状态。`;
    case LiveModerationAction.kick:
    default:
      return `确定要将 ${name} 踢出语音频道吗？`;
  }
}
