import type { LiveParticipant, LiveState, RoomCard, RoomDetail, UserSummary } from '../protocol/models';
import { userFacingErrorMessage } from './error_display';

export class JoinedLiveRoomSummary {
  constructor(opts: {
    roomId: string;
    displayName: string;
    avatarLabel: string;
    avatarUrl: string | null;
    defaultAvatarKey: string;
  }) {
    this.roomId = opts.roomId;
    this.displayName = opts.displayName;
    this.avatarLabel = opts.avatarLabel;
    this.avatarUrl = opts.avatarUrl;
    this.defaultAvatarKey = opts.defaultAvatarKey;
  }

  readonly roomId: string;
  readonly displayName: string;
  readonly avatarLabel: string;
  readonly avatarUrl: string | null;
  readonly defaultAvatarKey: string;
}

export class LiveParticipantTileState {
  constructor(opts: { broadcasting: boolean; highlighted: boolean; micMutedForDisplay: boolean; micActive: boolean }) {
    this.broadcasting = opts.broadcasting;
    this.highlighted = opts.highlighted;
    this.micMutedForDisplay = opts.micMutedForDisplay;
    this.micActive = opts.micActive;
  }

  readonly broadcasting: boolean;
  readonly highlighted: boolean;
  readonly micMutedForDisplay: boolean;
  readonly micActive: boolean;
}

export class AuthoritativeLivePresenceChanges {
  constructor(opts: { joined: Set<string>; left: Set<string> }) {
    this.joined = opts.joined;
    this.left = opts.left;
  }

  readonly joined: Set<string>;
  readonly left: Set<string>;
}

/**
 * User identities which the server still considers present in the voice
 * channel. A reconnecting participant deliberately stays present during the
 * bounded recovery window; a first-time `joining` placeholder does not become
 * audible/visible presence until the app-level join reaches `online`.
 */
export function authoritativeLivePresenceUserIds(live: LiveState): Set<string> {
  const ids = new Set<string>();
  for (const participant of live.participants) {
    if (
      liveParticipantConnectionReady(participant) ||
      participant.connectionState.trim().toLowerCase() === 'reconnecting'
    ) {
      ids.add(participant.user.id);
    }
  }
  return ids;
}

/**
 * Computes join/leave cues from two authoritative server snapshots.
 *
 * LiveKit can emit `ParticipantDisconnected` at the start of a temporary
 * reconnect while the server correctly keeps that person in the roster. Cues
 * must therefore follow this snapshot transition rather than the raw media
 * callback, otherwise users hear a leave sound while the member remains.
 */
export function authoritativeLivePresenceChanges(opts: {
  before: ReadonlySet<string>;
  after: ReadonlySet<string>;
}): AuthoritativeLivePresenceChanges {
  return new AuthoritativeLivePresenceChanges({
    joined: _difference(opts.after, opts.before),
    left: _difference(opts.before, opts.after),
  });
}

export class LiveMicControlState {
  constructor(opts: { mutedForDisplay: boolean; active: boolean; enabled: boolean }) {
    this.mutedForDisplay = opts.mutedForDisplay;
    this.active = opts.active;
    this.enabled = opts.enabled;
  }

  readonly mutedForDisplay: boolean;
  readonly active: boolean;
  readonly enabled: boolean;
}

export enum LiveScreenSourceListBodyState {
  loading = 'loading',
  empty = 'empty',
  results = 'results',
}

export class LiveScreenSourcePickerState<T> {
  constructor(
    opts: {
      sources?: T[] | null;
      selectedId?: string | null;
      loading?: boolean;
      error?: string | null;
    } = {},
  ) {
    this.sources = opts.sources ?? null;
    this.selectedId = opts.selectedId ?? null;
    this.loading = opts.loading ?? false;
    this.error = opts.error ?? null;
  }

  readonly sources: T[] | null;
  readonly selectedId: string | null;
  readonly loading: boolean;
  readonly error: string | null;

  /**
   * `selectedId` / `error`: `undefined` (or omitted) keeps the current value,
   * `null` clears it — Dart's "unchanged" sentinel.
   */
  copyWith(
    opts: {
      sources?: T[] | null;
      selectedId?: string | null;
      loading?: boolean;
      error?: string | null;
    } = {},
  ): LiveScreenSourcePickerState<T> {
    return new LiveScreenSourcePickerState<T>({
      sources: opts.sources ?? this.sources,
      selectedId: opts.selectedId === undefined ? this.selectedId : opts.selectedId,
      loading: opts.loading ?? this.loading,
      error: opts.error === undefined ? this.error : opts.error,
    });
  }
}

export function liveParticipantByUserId(live: LiveState | null | undefined, userId: string): LiveParticipant | null {
  if (live == null) return null;
  for (const participant of live.participants) {
    if (participant.user.id === userId) return participant;
  }
  return null;
}

export function liveParticipantDisplayName(
  live: LiveState | null | undefined,
  userId: string,
  opts: { fallback?: string } = {},
): string {
  const { fallback = '' } = opts;
  const participant = liveParticipantByUserId(live, userId);
  if (participant == null) return fallback;
  return liveUserDisplayName(participant.user, { fallback });
}

export function liveUserDisplayName(user: UserSummary, opts: { fallback?: string } = {}): string {
  const { fallback = '' } = opts;
  const roomName = _nonEmpty(user.roomDisplayName);
  if (roomName != null) return roomName;
  const displayName = _nonEmpty(user.displayName);
  if (displayName != null) return displayName;
  const username = _nonEmpty(user.username);
  if (username != null) return username;
  return fallback;
}

export function visibleLiveParticipantsForStage(
  participants: Iterable<LiveParticipant>,
  opts: {
    currentUserId: string;
    localParticipantReady: boolean;
    connectedParticipantIds?: ReadonlySet<string>;
  },
): LiveParticipant[] {
  const { currentUserId, localParticipantReady, connectedParticipantIds = new Set<string>() } = opts;
  const visible: LiveParticipant[] = [];
  for (const participant of participants) {
    if (
      (localParticipantReady || participant.user.id !== currentUserId) &&
      liveParticipantVisibleInRoster(participant, { connectedParticipantIds })
    ) {
      visible.push(participant);
    }
  }
  return visible;
}

export function liveParticipantConnectionReady(participant: LiveParticipant): boolean {
  const state = participant.connectionState.trim().toLowerCase();
  return state === 'online' || state === 'connected' || state === 'joined';
}

export function liveParticipantVisibleInRoster(
  participant: LiveParticipant,
  opts: { connectedParticipantIds?: ReadonlySet<string> } = {},
): boolean {
  const { connectedParticipantIds = new Set<string>() } = opts;
  const connectionState = participant.connectionState.trim().toLowerCase();
  // A participant marked reconnecting remains part of the server's live
  // count during its bounded reconnect grace period. Keep that same member in
  // the open roster even while LiveKit is rebuilding the remote-participant
  // map, otherwise the room card says X users while the channel shows X - 1.
  // A provisional first-time "joining" participant is still hidden until
  // LiveKit confirms it; mic mute is independent of roster visibility.
  return (
    liveParticipantConnectionReady(participant) ||
    connectionState === 'reconnecting' ||
    connectedParticipantIds.has(participant.user.id)
  );
}

export function liveStateMissingConnectedParticipants(
  live: LiveState | null | undefined,
  opts: { connectedParticipantIds: ReadonlySet<string> },
): boolean {
  const { connectedParticipantIds } = opts;
  if (live == null || connectedParticipantIds.size === 0) return false;
  const liveUserIds = new Set(live.participants.map((participant) => participant.user.id));
  for (const id of connectedParticipantIds) {
    if (!liveUserIds.has(id)) return true;
  }
  return false;
}

export function liveParticipantTileState(
  participant: LiveParticipant,
  opts: { speaking: boolean; liveKitMicMuted?: boolean | null },
): LiveParticipantTileState {
  const { speaking, liveKitMicMuted } = opts;
  const broadcasting = participant.cameraOn || participant.screenSharing;
  // App mute uses capture gain 0 while deliberately leaving the published
  // LiveKit microphone track enabled. The server snapshot is therefore the
  // authority for logical mute. A newly connected LiveKit participant exists
  // briefly before its microphone publication arrives, which LiveKit reports
  // as muted. Only let media state tighten the display after the participant
  // has completed the app-level join; otherwise an open-mic join visibly
  // flashes as muted. A real publish failure is reported to the server before
  // connection_state becomes ready, so it still renders muted.
  const liveKitMicMutedForDisplay = liveParticipantConnectionReady(participant) && liveKitMicMuted === true;
  const micMutedForDisplay =
    participant.micBlocked || participant.voiceBlocked || participant.micMuted || liveKitMicMutedForDisplay;
  return new LiveParticipantTileState({
    broadcasting,
    highlighted: speaking || broadcasting,
    micMutedForDisplay,
    micActive: !micMutedForDisplay && speaking,
  });
}

export function pickLiveStageShare<T>(
  tracks: Iterable<T>,
  opts: { isScreenShare: (track: T) => boolean; isLocal: (track: T) => boolean },
): T | null {
  let localShare: T | null = null;
  for (const track of tracks) {
    if (!opts.isScreenShare(track)) continue;
    if (!opts.isLocal(track)) return track;
    localShare ??= track;
  }
  return localShare;
}

export function liveScreenShareByIdentity<T>(
  tracks: Iterable<T>,
  opts: {
    identity: string | null | undefined;
    trackIdentity: (track: T) => string;
    isScreenShare: (track: T) => boolean;
  },
): T | null {
  const { identity } = opts;
  if (identity == null) return null;
  for (const track of tracks) {
    if (opts.isScreenShare(track) && opts.trackIdentity(track) === identity) return track;
  }
  return null;
}

export function shouldExitMissingFullScreenShare(opts: {
  fullScreenShareIdentity: string | null | undefined;
  fullScreenShare: unknown;
}): boolean {
  return opts.fullScreenShareIdentity != null && opts.fullScreenShare == null;
}

export function shouldPatchEndedLocalScreenShare(opts: {
  localScreenSharing: boolean;
  sessionScreenSharing: boolean;
  joinedLiveRoomId: string | null | undefined;
  selectedRoomId: string | null | undefined;
}): boolean {
  const { localScreenSharing, sessionScreenSharing, joinedLiveRoomId, selectedRoomId } = opts;
  return (
    localScreenSharing && !sessionScreenSharing && joinedLiveRoomId != null && joinedLiveRoomId === selectedRoomId
  );
}

export function joinedLiveRoomSummary(opts: {
  joinedLiveRoomId: string | null | undefined;
  selectedRoom: RoomDetail | null | undefined;
  rooms: Iterable<RoomCard>;
}): JoinedLiveRoomSummary | null {
  const roomId = _nonEmpty(opts.joinedLiveRoomId);
  if (roomId == null) return null;

  const selected = opts.selectedRoom;
  if (selected != null && selected.id === roomId) {
    return new JoinedLiveRoomSummary({
      roomId: selected.id,
      displayName: _roomDetailDisplayName(selected),
      avatarLabel: _roomDetailAvatarLabel(selected),
      avatarUrl: selected.avatarUrl,
      defaultAvatarKey: selected.defaultAvatarKey,
    });
  }

  for (const room of opts.rooms) {
    if (room.id !== roomId) continue;
    return new JoinedLiveRoomSummary({
      roomId: room.id,
      displayName: room.displayName,
      avatarLabel: _roomCardAvatarLabel(room),
      avatarUrl: room.avatarUrl,
      defaultAvatarKey: room.defaultAvatarKey,
    });
  }

  return null;
}

export function reconcileLiveScreenSourceSelection<T>(
  sources: Iterable<T>,
  opts: { selectedId: string | null | undefined; sourceId: (source: T) => string },
): string | null {
  const { selectedId, sourceId } = opts;
  let hasFirst = false;
  let firstSource: T | undefined;
  for (const source of sources) {
    if (!hasFirst) {
      hasFirst = true;
      firstSource = source;
    }
    if (sourceId(source) === selectedId) return selectedId;
  }
  return hasFirst ? sourceId(firstSource as T) : null;
}

export function liveScreenSourceById<T>(
  sources: Iterable<T> | null | undefined,
  opts: { selectedId: string | null | undefined; sourceId: (source: T) => string },
): T | null {
  const { selectedId, sourceId } = opts;
  if (selectedId == null || sources == null) return null;
  for (const source of sources) {
    if (sourceId(source) === selectedId) return source;
  }
  return null;
}

export function liveScreenSourceListBodyState<T>(sources: Iterable<T> | null | undefined): LiveScreenSourceListBodyState {
  if (sources == null) return LiveScreenSourceListBodyState.loading;
  for (const _ of sources) return LiveScreenSourceListBodyState.results;
  return LiveScreenSourceListBodyState.empty;
}

export function liveScreenSourceSelected<T>(
  source: T,
  opts: { selectedId: string | null | undefined; sourceId: (source: T) => string },
): boolean {
  return opts.selectedId != null && opts.sourceId(source) === opts.selectedId;
}

export function canConfirmLiveScreenSourceSelection(selectedId: string | null | undefined): boolean {
  return selectedId != null;
}

export function canLoadLiveScreenSources<T>(state: LiveScreenSourcePickerState<T>): boolean {
  return !state.loading;
}

export function liveScreenSourceLoadStarted<T>(state: LiveScreenSourcePickerState<T>): LiveScreenSourcePickerState<T> {
  return state.copyWith({ loading: true, error: null });
}

export function liveScreenSourceLoadSucceeded<T>(opts: {
  state: LiveScreenSourcePickerState<T>;
  sources: Iterable<T>;
  sourceId: (source: T) => string;
}): LiveScreenSourcePickerState<T> {
  const { state, sourceId } = opts;
  const nextSources = [...opts.sources];
  return state.copyWith({
    sources: nextSources,
    selectedId: reconcileLiveScreenSourceSelection(nextSources, {
      selectedId: state.selectedId,
      sourceId,
    }),
    loading: false,
    error: null,
  });
}

export function liveScreenSourceLoadFailed<T>(opts: {
  state: LiveScreenSourcePickerState<T>;
  failure: unknown;
}): LiveScreenSourcePickerState<T> {
  return opts.state.copyWith({
    loading: false,
    error: userFacingErrorMessage(opts.failure, { fallback: '加载语音频道失败' }),
  });
}

export function liveScreenSourceSelectedChanged<T>(
  state: LiveScreenSourcePickerState<T>,
  selectedId: string | null,
): LiveScreenSourcePickerState<T> {
  return state.copyWith({ selectedId });
}

export function visibleLiveScreenSourceThumbnail<T extends { readonly length: number }>(opts: {
  thumbnail: T | null | undefined;
  imageError: unknown;
}): T | null {
  const { thumbnail, imageError } = opts;
  if (thumbnail == null || thumbnail.length === 0 || imageError != null) {
    return null;
  }
  return thumbnail;
}

export function canOpenLiveStageShareFullScreen(opts: { stageShareIdentity: string; localUserId: string }): boolean {
  return opts.stageShareIdentity !== opts.localUserId;
}

export function liveScreenShareStageLabel(displayName: string): string {
  return displayName.length === 0 ? '屏幕共享' : `${displayName} 的屏幕`;
}

export function liveScreenShareViewers(live: LiveState | null | undefined, broadcasterUserId: string): UserSummary[] {
  const participant = liveParticipantByUserId(live, broadcasterUserId);
  if (participant == null || !participant.screenSharing) {
    return [];
  }
  return participant.screenViewers.filter((viewer) => viewer.id !== broadcasterUserId);
}

export function liveMicControlState(opts: { micMuted: boolean; voiceBlocked: boolean }): LiveMicControlState {
  const { micMuted, voiceBlocked } = opts;
  if (voiceBlocked) {
    return new LiveMicControlState({
      mutedForDisplay: true,
      active: false,
      enabled: false,
    });
  }
  return new LiveMicControlState({
    mutedForDisplay: micMuted,
    active: !micMuted,
    enabled: true,
  });
}

export function liveForciblyRemovedNotice(): string {
  return '你已被移出语音';
}

export function liveVoiceConnectFailureMessage(error: unknown): string {
  return '无法连接语音频道';
}

export function liveCameraOpenFailureMessage(error: unknown): string {
  return '无法打开摄像头';
}

export function liveScreenShareFailureMessage(error: unknown): string {
  return '无法共享屏幕';
}

function _roomDetailDisplayName(room: RoomDetail): string {
  return _nonEmpty(room.remarkName) ?? room.name;
}

function _roomDetailAvatarLabel(room: RoomDetail): string {
  return _nonEmpty(room.name) ?? room.rid;
}

function _roomCardAvatarLabel(room: RoomCard): string {
  return _nonEmpty(room.name) ?? room.rid;
}

function _nonEmpty(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  if (trimmed == null || trimmed.length === 0) return null;
  return trimmed;
}

function _difference(a: ReadonlySet<string>, b: ReadonlySet<string>): Set<string> {
  const result = new Set<string>();
  for (const value of a) {
    if (!b.has(value)) result.add(value);
  }
  return result;
}
