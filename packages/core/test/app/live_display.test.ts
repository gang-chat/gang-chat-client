import { describe, expect, it } from 'vitest';

import {
  LiveScreenSourceListBodyState,
  LiveScreenSourcePickerState,
  authoritativeLivePresenceChanges,
  authoritativeLivePresenceUserIds,
  canConfirmLiveScreenSourceSelection,
  canLoadLiveScreenSources,
  canOpenLiveStageShareFullScreen,
  joinedLiveRoomSummary,
  liveCameraOpenFailureMessage,
  liveForciblyRemovedNotice,
  liveMicControlState,
  liveParticipantByUserId,
  liveParticipantDisplayName,
  liveParticipantTileState,
  liveScreenShareByIdentity,
  liveScreenShareFailureMessage,
  liveScreenShareStageLabel,
  liveScreenShareViewers,
  liveScreenSourceById,
  liveScreenSourceListBodyState,
  liveScreenSourceLoadFailed,
  liveScreenSourceLoadStarted,
  liveScreenSourceLoadSucceeded,
  liveScreenSourceSelected,
  liveScreenSourceSelectedChanged,
  liveStateMissingConnectedParticipants,
  liveVoiceConnectFailureMessage,
  pickLiveStageShare,
  reconcileLiveScreenSourceSelection,
  shouldExitMissingFullScreenShare,
  shouldPatchEndedLocalScreenShare,
  visibleLiveParticipantsForStage,
  visibleLiveScreenSourceThumbnail,
} from '../../src/app/live_display';
import { LiveParticipant, LiveState, RoomCard, RoomDetail, RoomMembership, UserSummary } from '../../src/protocol/models';

class Track {
  constructor(
    readonly id: string,
    opts: { isScreenShare: boolean; isLocal: boolean },
  ) {
    this.isScreenShare = opts.isScreenShare;
    this.isLocal = opts.isLocal;
  }

  readonly isScreenShare: boolean;
  readonly isLocal: boolean;
}

describe('live display', () => {
  it('liveParticipantByUserId returns matching participant', () => {
    const live = _live(['alice', 'bob']);

    expect(liveParticipantByUserId(live, 'bob')?.user.displayName).toBe('User bob');
    expect(liveParticipantByUserId(live, 'missing')).toBeNull();
    expect(liveParticipantByUserId(null, 'bob')).toBeNull();
  });

  it('liveParticipantDisplayName uses fallback for missing live user', () => {
    const live = _live(['alice']);

    expect(liveParticipantDisplayName(live, 'alice')).toBe('User alice');
    expect(liveParticipantDisplayName(live, 'missing')).toBe('');
    expect(liveParticipantDisplayName(null, 'missing', { fallback: 'Unknown' })).toBe('Unknown');
  });

  it('liveParticipantDisplayName prefers room display name', () => {
    const live = _liveWithParticipants([_participant('alice', { roomDisplayName: 'Room Alice' })]);

    expect(liveParticipantDisplayName(live, 'alice')).toBe('Room Alice');
  });

  it('visible live participants hide local user until join is ready', () => {
    const live = _live(['alice', 'bob']);

    expect(
      visibleLiveParticipantsForStage(live.participants, {
        currentUserId: 'alice',
        localParticipantReady: false,
      }).map((participant) => participant.user.id),
    ).toEqual(['bob']);
    expect(
      visibleLiveParticipantsForStage(live.participants, {
        currentUserId: 'alice',
        localParticipantReady: true,
      }).map((participant) => participant.user.id),
    ).toEqual(['alice', 'bob']);
    expect(
      visibleLiveParticipantsForStage(live.participants, {
        currentUserId: 'missing',
        localParticipantReady: false,
      }).map((participant) => participant.user.id),
    ).toEqual(['alice', 'bob']);
  });

  it('visible live participants hide joining users', () => {
    const live = _liveWithParticipants([
      _participant('alice', { connectionState: 'joining' }),
      _participant('bob', { connectionState: 'online' }),
    ]);

    expect(
      visibleLiveParticipantsForStage(live.participants, {
        currentUserId: 'missing',
        localParticipantReady: true,
      }).map((participant) => participant.user.id),
    ).toEqual(['bob']);
  });

  it('visible live participants show joining users already in LiveKit', () => {
    const live = _liveWithParticipants([
      _participant('alice', { connectionState: 'joining' }),
      _participant('bob', { connectionState: 'online' }),
    ]);

    expect(
      visibleLiveParticipantsForStage(live.participants, {
        currentUserId: 'missing',
        localParticipantReady: true,
        connectedParticipantIds: new Set(['alice']),
      }).map((participant) => participant.user.id),
    ).toEqual(['alice', 'bob']);
  });

  it('visible live participants keep reconnecting users in the roster', () => {
    const live = _liveWithParticipants([
      _participant('alice', { connectionState: 'reconnecting' }),
      _participant('bob', { connectionState: 'online' }),
    ]);

    expect(
      visibleLiveParticipantsForStage(live.participants, {
        currentUserId: 'missing',
        localParticipantReady: true,
      }).map((participant) => participant.user.id),
    ).toEqual(['alice', 'bob']);
  });

  it('authoritative presence keeps reconnecting and excludes joining', () => {
    const live = _liveWithParticipants([
      _participant('joining', { connectionState: 'joining' }),
      _participant('online', { connectionState: 'online' }),
      _participant('reconnecting', { connectionState: 'reconnecting' }),
      _participant('left', { connectionState: 'left' }),
    ]);

    expect(authoritativeLivePresenceUserIds(live)).toEqual(new Set(['online', 'reconnecting']));
  });

  it('authoritative presence changes ignore a reconnect transition', () => {
    const reconnecting = authoritativeLivePresenceChanges({
      before: new Set(['alice', 'bob']),
      after: new Set(['alice', 'bob']),
    });
    const reconciled = authoritativeLivePresenceChanges({
      before: new Set(['alice', 'bob']),
      after: new Set(['alice']),
    });

    expect(reconnecting.joined.size).toBe(0);
    expect(reconnecting.left.size).toBe(0);
    expect(reconciled.joined.size).toBe(0);
    expect(reconciled.left).toEqual(new Set(['bob']));
  });

  it('live state detects connected LiveKit users missing from server state', () => {
    const live = _live(['alice']);

    expect(liveStateMissingConnectedParticipants(live, { connectedParticipantIds: new Set(['alice']) })).toBe(false);
    expect(liveStateMissingConnectedParticipants(live, { connectedParticipantIds: new Set(['alice', 'bob']) })).toBe(
      true,
    );
  });

  it('pickLiveStageShare prefers remote screen share then local fallback', () => {
    const localShare = new Track('local', { isScreenShare: true, isLocal: true });
    const remoteShare = new Track('remote', { isScreenShare: true, isLocal: false });
    const camera = new Track('camera', { isScreenShare: false, isLocal: false });
    const opts = { isScreenShare: (track: Track) => track.isScreenShare, isLocal: (track: Track) => track.isLocal };

    expect(pickLiveStageShare([camera, localShare, remoteShare], opts)).toBe(remoteShare);
    expect(pickLiveStageShare([camera, localShare], opts)).toBe(localShare);
    expect(pickLiveStageShare([camera], opts)).toBeNull();
  });

  it('stage share label and fullscreen guard are UI independent', () => {
    expect(liveScreenShareStageLabel('Alice')).toBe('Alice 的屏幕');
    expect(liveScreenShareStageLabel('')).toBe('屏幕共享');
    expect(canOpenLiveStageShareFullScreen({ stageShareIdentity: 'alice', localUserId: 'bob' })).toBe(true);
    expect(canOpenLiveStageShareFullScreen({ stageShareIdentity: 'alice', localUserId: 'alice' })).toBe(false);
  });

  it('screen share viewers require an active share and exclude broadcaster', () => {
    const broadcaster = _participant('alice', {
      screenSharing: true,
      screenViewers: [_user('alice'), _user('bob')],
    });
    const live = _liveWithParticipants([broadcaster]);

    expect(liveScreenShareViewers(live, 'alice').map((user) => user.id)).toEqual(['bob']);
    expect(liveScreenShareViewers(live, 'missing')).toEqual([]);
    expect(
      liveScreenShareViewers(_liveWithParticipants([_participant('alice', { screenViewers: [_user('bob')] })]), 'alice'),
    ).toEqual([]);
  });

  it('live notices and failure messages stay outside UI', () => {
    expect(liveForciblyRemovedNotice()).toBe('你已被移出语音');
    expect(liveVoiceConnectFailureMessage('network')).toBe('无法连接语音频道');
    expect(liveCameraOpenFailureMessage('denied')).toBe('无法打开摄像头');
    expect(liveScreenShareFailureMessage('denied')).toBe('无法共享屏幕');
  });

  it('liveScreenShareByIdentity resolves active fullscreen share', () => {
    const screenShare = new Track('alice', { isScreenShare: true, isLocal: false });
    const camera = new Track('alice', { isScreenShare: false, isLocal: false });
    const opts = {
      trackIdentity: (track: Track) => track.id,
      isScreenShare: (track: Track) => track.isScreenShare,
    };

    expect(liveScreenShareByIdentity([camera, screenShare], { ...opts, identity: 'alice' })).toBe(screenShare);
    expect(liveScreenShareByIdentity([screenShare], { ...opts, identity: null })).toBeNull();
    expect(liveScreenShareByIdentity([camera], { ...opts, identity: 'alice' })).toBeNull();
  });

  it('shouldExitMissingFullScreenShare exits only missing tracked share', () => {
    const share = {};

    expect(shouldExitMissingFullScreenShare({ fullScreenShareIdentity: 'alice', fullScreenShare: null })).toBe(true);
    expect(shouldExitMissingFullScreenShare({ fullScreenShareIdentity: 'alice', fullScreenShare: share })).toBe(false);
    expect(shouldExitMissingFullScreenShare({ fullScreenShareIdentity: null, fullScreenShare: null })).toBe(false);
  });

  it('ended local screen share patch gate follows joined selected room', () => {
    const gate = (
      localScreenSharing: boolean,
      sessionScreenSharing: boolean,
      joinedLiveRoomId: string | null,
      selectedRoomId: string | null,
    ) => shouldPatchEndedLocalScreenShare({ localScreenSharing, sessionScreenSharing, joinedLiveRoomId, selectedRoomId });

    expect(gate(true, false, 'room_1', 'room_1')).toBe(true);
    expect(gate(false, false, 'room_1', 'room_1')).toBe(false);
    expect(gate(true, true, 'room_1', 'room_1')).toBe(false);
    expect(gate(true, false, 'room_2', 'room_1')).toBe(false);
    expect(gate(true, false, null, 'room_1')).toBe(false);
  });

  it('joined live room summary prefers selected detail then room list', () => {
    const selected = _roomDetail({
      id: 'room_1',
      name: 'Alpha',
      remarkName: 'Project Alpha',
      defaultAvatarKey: 'green-2',
    });
    const rooms = [
      _roomCard({ id: 'room_1', name: 'Old Alpha', defaultAvatarKey: 'blue-3' }),
      _roomCard({ id: 'room_2', name: 'Beta', remarkName: 'Room Beta', defaultAvatarKey: 'amber-2' }),
    ];

    const fromSelected = joinedLiveRoomSummary({ joinedLiveRoomId: 'room_1', selectedRoom: selected, rooms });

    expect(fromSelected?.roomId).toBe('room_1');
    expect(fromSelected?.displayName).toBe('Project Alpha');
    expect(fromSelected?.avatarLabel).toBe('Alpha');
    expect(fromSelected?.defaultAvatarKey).toBe('green-2');

    const fromList = joinedLiveRoomSummary({ joinedLiveRoomId: 'room_2', selectedRoom: selected, rooms });

    expect(fromList?.roomId).toBe('room_2');
    expect(fromList?.displayName).toBe('Room Beta');
    expect(fromList?.avatarLabel).toBe('Beta');
    expect(fromList?.defaultAvatarKey).toBe('amber-2');
  });

  it('joined live room summary hides when room cannot be resolved', () => {
    expect(joinedLiveRoomSummary({ joinedLiveRoomId: null, selectedRoom: null, rooms: [] })).toBeNull();
    expect(
      joinedLiveRoomSummary({
        joinedLiveRoomId: 'missing',
        selectedRoom: null,
        rooms: [_roomCard({ id: 'room_1', name: 'Alpha' })],
      }),
    ).toBeNull();
  });

  it('screen source selection keeps existing source or falls back to first', () => {
    const screen = new Track('screen', { isScreenShare: true, isLocal: true });
    const window = new Track('window', { isScreenShare: true, isLocal: true });
    const sourceId = (source: Track) => source.id;

    expect(reconcileLiveScreenSourceSelection([screen, window], { selectedId: 'window', sourceId })).toBe('window');
    expect(reconcileLiveScreenSourceSelection([screen, window], { selectedId: 'missing', sourceId })).toBe('screen');
    expect(reconcileLiveScreenSourceSelection<Track>([], { selectedId: 'missing', sourceId })).toBeNull();
  });

  it('screen source lookup and confirmation require a selected id', () => {
    const screen = new Track('screen', { isScreenShare: true, isLocal: true });
    const sourceId = (source: Track) => source.id;

    expect(liveScreenSourceById([screen], { selectedId: 'screen', sourceId })).toBe(screen);
    expect(liveScreenSourceById([screen], { selectedId: 'missing', sourceId })).toBeNull();
    expect(liveScreenSourceById<Track>(null, { selectedId: 'screen', sourceId })).toBeNull();
    expect(canConfirmLiveScreenSourceSelection('screen')).toBe(true);
    expect(canConfirmLiveScreenSourceSelection(null)).toBe(false);
  });

  it('screen source picker state tracks loading selection and failures', () => {
    const screen = new Track('screen', { isScreenShare: true, isLocal: true });
    const window = new Track('window', { isScreenShare: true, isLocal: true });
    const sourceId = (source: Track) => source.id;
    const initial = new LiveScreenSourcePickerState<Track>();

    expect(canLoadLiveScreenSources(initial)).toBe(true);

    const started = liveScreenSourceLoadStarted(initial);

    expect(started.loading).toBe(true);
    expect(started.error).toBeNull();
    expect(canLoadLiveScreenSources(started)).toBe(false);

    const loaded = liveScreenSourceLoadSucceeded({ state: started, sources: [screen, window], sourceId });

    expect(loaded.loading).toBe(false);
    expect(loaded.sources).toEqual([screen, window]);
    expect(loaded.selectedId).toBe('screen');
    expect(loaded.error).toBeNull();

    const selected = liveScreenSourceSelectedChanged(loaded, 'window');

    expect(selected.selectedId).toBe('window');

    const reloaded = liveScreenSourceLoadSucceeded({ state: selected, sources: [screen, window], sourceId });

    expect(reloaded.selectedId).toBe('window');

    const failed = liveScreenSourceLoadFailed({
      state: liveScreenSourceLoadStarted(reloaded),
      failure: new Error('sources failed'),
    });

    expect(failed.loading).toBe(false);
    expect(failed.sources).toEqual([screen, window]);
    expect(failed.selectedId).toBe('window');
    expect(failed.error).toBe('加载语音频道失败');

    expect(liveScreenSourceSelectedChanged(failed, null).selectedId).toBeNull();
  });

  it('screen source list helpers expose body selected and thumbnail states', () => {
    const screen = new Track('screen', { isScreenShare: true, isLocal: true });
    const sourceId = (source: Track) => source.id;

    expect(liveScreenSourceListBodyState<Track>(null)).toBe(LiveScreenSourceListBodyState.loading);
    expect(liveScreenSourceListBodyState<Track>([])).toBe(LiveScreenSourceListBodyState.empty);
    expect(liveScreenSourceListBodyState([screen])).toBe(LiveScreenSourceListBodyState.results);

    expect(liveScreenSourceSelected(screen, { selectedId: 'screen', sourceId })).toBe(true);
    expect(liveScreenSourceSelected(screen, { selectedId: 'window', sourceId })).toBe(false);

    expect(visibleLiveScreenSourceThumbnail({ thumbnail: [1, 2], imageError: null })).toEqual([1, 2]);
    expect(visibleLiveScreenSourceThumbnail({ thumbnail: new Uint8Array(0), imageError: null })).toBeNull();
    expect(visibleLiveScreenSourceThumbnail({ thumbnail: [1], imageError: {} })).toBeNull();
  });

  it('liveParticipantTileState highlights speaking or broadcasting users', () => {
    const idle = liveParticipantTileState(_participant('alice'), { speaking: false });
    expect(idle.broadcasting).toBe(false);
    expect(idle.highlighted).toBe(false);
    expect(idle.micMutedForDisplay).toBe(false);
    expect(idle.micActive).toBe(false);

    const speaking = liveParticipantTileState(_participant('alice'), { speaking: true });
    expect(speaking.highlighted).toBe(true);
    expect(speaking.micActive).toBe(true);

    const broadcasting = liveParticipantTileState(_participant('alice', { cameraOn: true }), { speaking: false });
    expect(broadcasting.broadcasting).toBe(true);
    expect(broadcasting.highlighted).toBe(true);
    expect(broadcasting.micActive).toBe(false);
  });

  it('liveParticipantTileState treats voice-blocked users as force-muted', () => {
    const state = liveParticipantTileState(_participant('alice', { voiceBlocked: true }), { speaking: true });

    expect(state.micMutedForDisplay).toBe(true);
    expect(state.micActive).toBe(false);
    expect(state.highlighted).toBe(true);
  });

  it('liveParticipantTileState keeps logical mute authoritative', () => {
    const state = liveParticipantTileState(_participant('alice', { micMuted: true }), {
      speaking: false,
      liveKitMicMuted: false,
    });

    expect(state.micMutedForDisplay).toBe(true);
    expect(state.micActive).toBe(false);

    const trackMuted = liveParticipantTileState(_participant('alice'), { speaking: true, liveKitMicMuted: true });

    expect(trackMuted.micMutedForDisplay).toBe(true);
    expect(trackMuted.micActive).toBe(false);

    const moderated = liveParticipantTileState(_participant('alice', { micMuted: true, voiceBlocked: true }), {
      speaking: true,
      liveKitMicMuted: false,
    });

    expect(moderated.micMutedForDisplay).toBe(true);
    expect(moderated.micActive).toBe(false);
  });

  it('liveParticipantTileState ignores provisional LiveKit mute while joining', () => {
    const joining = liveParticipantTileState(_participant('alice', { connectionState: 'joining' }), {
      speaking: false,
      liveKitMicMuted: true,
    });
    const online = liveParticipantTileState(_participant('alice', { connectionState: 'online' }), {
      speaking: false,
      liveKitMicMuted: true,
    });
    const logicallyMuted = liveParticipantTileState(
      _participant('alice', { connectionState: 'joining', micMuted: true }),
      { speaking: false, liveKitMicMuted: true },
    );

    expect(joining.micMutedForDisplay).toBe(false);
    expect(online.micMutedForDisplay).toBe(true);
    expect(logicallyMuted.micMutedForDisplay).toBe(true);
  });

  it('live control display helpers describe toggled states', () => {
    const mutedMic = liveMicControlState({ micMuted: true, voiceBlocked: false });
    expect(mutedMic.mutedForDisplay).toBe(true);
    expect(mutedMic.active).toBe(false);
    expect(mutedMic.enabled).toBe(true);

    const liveMic = liveMicControlState({ micMuted: false, voiceBlocked: false });
    expect(liveMic.mutedForDisplay).toBe(false);
    expect(liveMic.active).toBe(true);
    expect(liveMic.enabled).toBe(true);

    const blockedMic = liveMicControlState({ micMuted: false, voiceBlocked: true });
    expect(blockedMic.mutedForDisplay).toBe(true);
    expect(blockedMic.active).toBe(false);
    expect(blockedMic.enabled).toBe(false);
  });
});

function _live(userIds: string[]): LiveState {
  return _liveWithParticipants(userIds.map((id) => _participant(id, { micMuted: true })));
}

function _liveWithParticipants(participants: LiveParticipant[]): LiveState {
  return new LiveState({
    roomId: 'room_1',
    participantCount: participants.length,
    participants,
    updatedAt: new Date(Date.UTC(2026, 5, 5)),
  });
}

function _participant(
  id: string,
  opts: {
    micMuted?: boolean;
    voiceBlocked?: boolean;
    cameraOn?: boolean;
    screenSharing?: boolean;
    screenViewers?: UserSummary[];
    connectionState?: string;
    roomDisplayName?: string | null;
  } = {},
): LiveParticipant {
  return new LiveParticipant({
    liveSessionId: `live_${id}`,
    user: new UserSummary({
      id,
      username: `user_${id}`,
      displayName: `User ${id}`,
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
      roomDisplayName: opts.roomDisplayName ?? null,
    }),
    joinedAt: new Date(Date.UTC(2026, 5, 5)),
    micMuted: opts.micMuted ?? false,
    headphonesMuted: false,
    voiceBlocked: opts.voiceBlocked ?? false,
    cameraOn: opts.cameraOn ?? false,
    screenSharing: opts.screenSharing ?? false,
    screenViewers: opts.screenViewers ?? [],
    connectionState: opts.connectionState ?? 'connected',
  });
}

function _user(id: string): UserSummary {
  return new UserSummary({
    id,
    username: `user_${id}`,
    displayName: `User ${id}`,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
  });
}

function _roomCard(opts: { id: string; name: string; remarkName?: string | null; defaultAvatarKey?: string }): RoomCard {
  return new RoomCard({
    id: opts.id,
    name: opts.name,
    remarkName: opts.remarkName ?? null,
    avatarUrl: null,
    defaultAvatarKey: opts.defaultAvatarKey ?? 'blue-3',
    memberCount: 2,
    liveParticipantCount: 0,
    liveAvatarPreview: [],
    lastMessage: null,
    unreadCount: 0,
    updatedAt: new Date(Date.UTC(2026, 5, 5)),
  });
}

function _roomDetail(opts: { id: string; name: string; remarkName?: string | null; defaultAvatarKey?: string }): RoomDetail {
  return new RoomDetail({
    id: opts.id,
    name: opts.name,
    remarkName: opts.remarkName ?? null,
    avatarUrl: null,
    defaultAvatarKey: opts.defaultAvatarKey ?? 'blue-3',
    memberCount: 2,
    myMembership: new RoomMembership({ joinedAt: new Date(Date.UTC(2026, 5, 5)), role: 'member' }),
    live: _live([]),
    createdAt: new Date(Date.UTC(2026, 5, 5)),
    updatedAt: new Date(Date.UTC(2026, 5, 5)),
  });
}
