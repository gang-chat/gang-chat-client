import { Completer } from '../lib/async';
import { ApiException, newClientId } from '../protocol/api_client';
import type { GangApi } from '../protocol/api_client';
import { LiveState, RoomCard } from '../protocol/models';
import type { LiveJoinResult, LiveParticipant } from '../protocol/models';
import * as room_live_state from './room_live_state';

export class LiveDeparturePatch {
  constructor(opts: { live: LiveState | null; rooms: RoomCard[] }) {
    this.live = opts.live;
    this.rooms = opts.rooms;
  }

  readonly live: LiveState | null;
  readonly rooms: RoomCard[];
}

export class LiveLocalDeparturePatch {
  constructor(opts: {
    live: LiveState | null;
    rooms: RoomCard[];
    joinedLiveRoomId: string | null;
    joiningLive: boolean;
    cameraOn: boolean;
    screenSharing: boolean;
    voiceBlocked: boolean;
  }) {
    this.live = opts.live;
    this.rooms = opts.rooms;
    this.joinedLiveRoomId = opts.joinedLiveRoomId;
    this.joiningLive = opts.joiningLive;
    this.cameraOn = opts.cameraOn;
    this.screenSharing = opts.screenSharing;
    this.voiceBlocked = opts.voiceBlocked;
  }

  readonly live: LiveState | null;
  readonly rooms: RoomCard[];
  readonly joinedLiveRoomId: string | null;
  readonly joiningLive: boolean;
  readonly cameraOn: boolean;
  readonly screenSharing: boolean;
  readonly voiceBlocked: boolean;
}

export class LivePublishPermissionPatch {
  constructor(opts: { micMuted: boolean; voiceBlocked: boolean }) {
    this.micMuted = opts.micMuted;
    this.voiceBlocked = opts.voiceBlocked;
  }

  readonly micMuted: boolean;
  readonly voiceBlocked: boolean;
}

export class LiveOutputMutePatch {
  constructor(opts: { headphonesMuted: boolean }) {
    this.headphonesMuted = opts.headphonesMuted;
  }

  readonly headphonesMuted: boolean;
}

export class LiveModerationAction {
  static readonly kick = new LiveModerationAction('kick', 'kick');
  static readonly muteMic = new LiveModerationAction('muteMic', 'mute_mic');
  static readonly blockVoice = new LiveModerationAction('blockVoice', 'block_voice');
  static readonly restoreVoice = new LiveModerationAction('restoreVoice', 'restore_voice');
  static readonly restoreHeadphones = new LiveModerationAction('restoreHeadphones', 'restore_headphones');

  static readonly values: readonly LiveModerationAction[] = [
    LiveModerationAction.kick,
    LiveModerationAction.muteMic,
    LiveModerationAction.blockVoice,
    LiveModerationAction.restoreVoice,
    LiveModerationAction.restoreHeadphones,
  ];

  private constructor(
    readonly name: string,
    readonly apiValue: string,
  ) {}

  toString(): string {
    return `LiveModerationAction.${this.name}`;
  }
}

export class LiveJoinResultPatch {
  constructor(opts: {
    micMuted: boolean;
    headphonesMuted: boolean;
    cameraOn: boolean;
    screenSharing: boolean;
    voiceBlocked: boolean;
    live: LiveState;
    rooms: RoomCard[];
  }) {
    this.micMuted = opts.micMuted;
    this.headphonesMuted = opts.headphonesMuted;
    this.cameraOn = opts.cameraOn;
    this.screenSharing = opts.screenSharing;
    this.voiceBlocked = opts.voiceBlocked;
    this.live = opts.live;
    this.rooms = opts.rooms;
  }

  readonly micMuted: boolean;
  readonly headphonesMuted: boolean;
  readonly cameraOn: boolean;
  readonly screenSharing: boolean;
  readonly voiceBlocked: boolean;
  readonly live: LiveState;
  readonly rooms: RoomCard[];
}

export class LiveStateUpdatePatch {
  constructor(opts: {
    micMuted: boolean;
    headphonesMuted: boolean;
    cameraOn: boolean;
    screenSharing: boolean;
    voiceBlocked: boolean;
    live: LiveState | null;
  }) {
    this.micMuted = opts.micMuted;
    this.headphonesMuted = opts.headphonesMuted;
    this.cameraOn = opts.cameraOn;
    this.screenSharing = opts.screenSharing;
    this.voiceBlocked = opts.voiceBlocked;
    this.live = opts.live;
  }

  readonly micMuted: boolean;
  readonly headphonesMuted: boolean;
  readonly cameraOn: boolean;
  readonly screenSharing: boolean;
  readonly voiceBlocked: boolean;
  readonly live: LiveState | null;
}

/**
 * Keeps mutations of the current user's live state in request order.
 *
 * The live endpoint updates a single participant row. Sending optimistic UI
 * changes concurrently allows an older request to arrive last and overwrite a
 * newer microphone, headphones, camera, or screen-share choice. Failed
 * requests are delivered to their caller without breaking later requests.
 */
export class LiveStateRequestQueue {
  private _tail: Promise<void> = Promise.resolve();
  private _pendingCount = 0;

  get isIdle(): boolean {
    return this._pendingCount === 0;
  }

  run<T>(request: () => Promise<T>): Promise<T> {
    const completer = new Completer<T>();
    this._pendingCount += 1;
    this._tail = this._tail.then(async () => {
      try {
        const result = await request();
        this._pendingCount -= 1;
        completer.complete(result);
      } catch (error) {
        this._pendingCount -= 1;
        completer.completeError(error);
      }
    });
    return completer.promise;
  }
}

export class LiveJoinStatePatch {
  constructor(opts: {
    joinedLiveRoomId: string | null;
    joiningLive: boolean;
    livePanelOpen: boolean;
    error: string | null;
  }) {
    this.joinedLiveRoomId = opts.joinedLiveRoomId;
    this.joiningLive = opts.joiningLive;
    this.livePanelOpen = opts.livePanelOpen;
    this.error = opts.error;
  }

  readonly joinedLiveRoomId: string | null;
  readonly joiningLive: boolean;
  readonly livePanelOpen: boolean;
  readonly error: string | null;
}

export class LiveJoinPreviousRoomDisconnectedPatch {
  constructor(opts: {
    live: LiveState | null;
    rooms: RoomCard[];
    joinedLiveRoomId: string | null;
    joiningLive: boolean;
    livePanelOpen: boolean;
    error: string | null;
  }) {
    this.live = opts.live;
    this.rooms = opts.rooms;
    this.joinedLiveRoomId = opts.joinedLiveRoomId;
    this.joiningLive = opts.joiningLive;
    this.livePanelOpen = opts.livePanelOpen;
    this.error = opts.error;
  }

  readonly live: LiveState | null;
  readonly rooms: RoomCard[];
  readonly joinedLiveRoomId: string | null;
  readonly joiningLive: boolean;
  readonly livePanelOpen: boolean;
  readonly error: string | null;
}

export function joinedLiveRoomToDisconnectBeforeJoin(opts: {
  joinedLiveRoomId: string | null;
  targetRoomId: string;
}): string | null {
  const { joinedLiveRoomId, targetRoomId } = opts;
  if (joinedLiveRoomId == null || joinedLiveRoomId === targetRoomId) {
    return null;
  }
  return joinedLiveRoomId;
}

export function canPatchSelectedLiveState(opts: {
  joinedLiveRoomId: string | null;
  selectedRoomId: string | null;
}): boolean {
  return opts.joinedLiveRoomId != null && opts.joinedLiveRoomId === opts.selectedRoomId;
}

export function canApplyPickedScreenShareSource(opts: {
  pickedForRoomId: string;
  joinedLiveRoomId: string | null;
  selectedRoomId: string | null;
}): boolean {
  return opts.joinedLiveRoomId === opts.pickedForRoomId && opts.selectedRoomId === opts.pickedForRoomId;
}

export function isBenignGoneLiveStatePatch(error: unknown): boolean {
  return error instanceof ApiException && error.statusCode === 409;
}

export function shouldSyncLiveKitMicAfterServerPatch(opts: {
  requestedMicMuted: boolean | null;
  serverMicMuted: boolean;
}): boolean {
  return opts.requestedMicMuted != null && opts.serverMicMuted !== opts.requestedMicMuted;
}

export function liveParticipantWithExclusiveMedia(participant: LiveParticipant): LiveParticipant {
  if (!participant.cameraOn || !participant.screenSharing) return participant;
  return participant.copyWith({ cameraOn: false });
}

export function liveOutputMuteToggled(opts: { headphonesMuted: boolean }): LiveOutputMutePatch {
  return new LiveOutputMutePatch({ headphonesMuted: !opts.headphonesMuted });
}

export class LiveController {
  constructor(opts: { api: GangApi }) {
    this.api = opts.api;
  }

  readonly api: GangApi;

  joinLive(opts: {
    roomId: string;
    source: string;
    micMuted: boolean;
    headphonesMuted: boolean;
  }): Promise<LiveJoinResult> {
    return this.api.joinLive({
      roomId: opts.roomId,
      clientLiveSessionId: newClientId('clive'),
      source: opts.source,
      micMuted: opts.micMuted,
      headphonesMuted: opts.headphonesMuted,
    });
  }

  updateMyState(opts: {
    roomId: string;
    micMuted?: boolean | null;
    headphonesMuted?: boolean | null;
    cameraOn?: boolean | null;
    cameraMirrored?: boolean | null;
    screenSharing?: boolean | null;
    connectionState?: string | null;
  }): Promise<LiveParticipant> {
    return this.api.updateMyLiveState({
      roomId: opts.roomId,
      micMuted: opts.micMuted ?? undefined,
      headphonesMuted: opts.headphonesMuted ?? undefined,
      cameraOn: opts.cameraOn ?? undefined,
      cameraMirrored: opts.cameraMirrored ?? undefined,
      screenSharing: opts.screenSharing ?? undefined,
      connectionState: opts.connectionState ?? undefined,
    });
  }

  leaveLive(opts: { roomId: string }): Promise<LiveParticipant> {
    return this.api.updateMyLiveState({ roomId: opts.roomId, connectionState: 'left' });
  }

  updateMyLiveScreenView(opts: { roomId: string; broadcasterUserId?: string | null }): Promise<void> {
    return this.api.updateMyLiveScreenView({
      roomId: opts.roomId,
      broadcasterUserId: opts.broadcasterUserId ?? undefined,
    });
  }

  kickParticipant(opts: { roomId: string; userId: string }): Promise<void> {
    return this.moderateParticipant({
      roomId: opts.roomId,
      userId: opts.userId,
      action: LiveModerationAction.kick,
    });
  }

  moderateParticipant(opts: { roomId: string; userId: string; action: LiveModerationAction }): Promise<void> {
    return this.api.moderateLiveParticipant({
      roomId: opts.roomId,
      userId: opts.userId,
      action: opts.action.apiValue,
    });
  }

  patchJoinStarted(opts: { joinedLiveRoomId: string | null }): LiveJoinStatePatch {
    return new LiveJoinStatePatch({
      joinedLiveRoomId: opts.joinedLiveRoomId,
      joiningLive: true,
      livePanelOpen: true,
      error: null,
    });
  }

  patchJoinPreviousRoomDisconnected(opts: {
    live: LiveState | null;
    rooms: RoomCard[];
    previousRoomId: string;
    userId: string;
    livePanelOpen: boolean;
    error: string | null;
  }): LiveJoinPreviousRoomDisconnectedPatch {
    const departure = this.removeUserFromLive({
      live: opts.live,
      rooms: opts.rooms,
      roomId: opts.previousRoomId,
      userId: opts.userId,
    });
    return new LiveJoinPreviousRoomDisconnectedPatch({
      live: departure.live,
      rooms: departure.rooms,
      joinedLiveRoomId: null,
      joiningLive: true,
      livePanelOpen: opts.livePanelOpen,
      error: opts.error,
    });
  }

  patchJoinConnected(opts: { roomId: string; livePanelOpen: boolean; error: string | null }): LiveJoinStatePatch {
    return new LiveJoinStatePatch({
      joinedLiveRoomId: opts.roomId,
      joiningLive: true,
      livePanelOpen: opts.livePanelOpen,
      error: opts.error,
    });
  }

  patchJoinFinished(opts: {
    joinedLiveRoomId: string | null;
    livePanelOpen: boolean;
    error: string | null;
  }): LiveJoinStatePatch {
    return new LiveJoinStatePatch({
      joinedLiveRoomId: opts.joinedLiveRoomId,
      joiningLive: false,
      livePanelOpen: opts.livePanelOpen,
      error: opts.error,
    });
  }

  patchLocalDeparture(opts: {
    live: LiveState | null;
    rooms: RoomCard[];
    joinedLiveRoomId: string | null;
    userId: string;
    joiningLive: boolean;
  }): LiveLocalDeparturePatch {
    const { live, rooms, userId, joiningLive } = opts;
    const roomId = opts.joinedLiveRoomId;
    const departure =
      roomId == null
        ? new LiveDeparturePatch({ live, rooms })
        : this.removeUserFromLive({ live, rooms, roomId, userId });
    return new LiveLocalDeparturePatch({
      live: departure.live,
      rooms: departure.rooms,
      joinedLiveRoomId: null,
      joiningLive,
      cameraOn: false,
      screenSharing: false,
      voiceBlocked: false,
    });
  }

  patchPublishPermission(opts: { canPublish: boolean; micMuted: boolean }): LivePublishPermissionPatch {
    const { canPublish, micMuted } = opts;
    return new LivePublishPermissionPatch({
      micMuted: canPublish ? micMuted : true,
      voiceBlocked: !canPublish,
    });
  }

  patchJoinResult(opts: {
    rooms: RoomCard[];
    result: LiveJoinResult;
    showMicUnmutedWhenAllowed?: boolean;
  }): LiveJoinResultPatch {
    const { rooms, result, showMicUnmutedWhenAllowed = false } = opts;
    const participant = liveParticipantWithExclusiveMedia(
      this._joinResultParticipantForDisplay(result.participant, { showMicUnmutedWhenAllowed }),
    );
    const live = this._replaceParticipantInLive(result.live, participant);
    return new LiveJoinResultPatch({
      micMuted: participant.micMuted,
      headphonesMuted: participant.headphonesMuted,
      cameraOn: participant.cameraOn,
      screenSharing: participant.screenSharing,
      voiceBlocked: participant.voiceBlocked,
      live,
      rooms: room_live_state.patchRoomLiveCount({
        rooms,
        roomId: live.roomId,
        live,
      }),
    });
  }

  private _joinResultParticipantForDisplay(
    participant: LiveParticipant,
    opts: { showMicUnmutedWhenAllowed: boolean },
  ): LiveParticipant {
    if (
      !opts.showMicUnmutedWhenAllowed ||
      participant.voiceBlocked ||
      participant.micBlocked ||
      !participant.micMuted
    ) {
      return participant;
    }
    return participant.copyWith({ micMuted: false });
  }

  private _replaceParticipantInLive(live: LiveState, participant: LiveParticipant): LiveState {
    let replaced = false;
    let changed = false;
    const participants = live.participants.map((item) => {
      if (item.liveSessionId !== participant.liveSessionId) return item;
      replaced = true;
      const merged = this._mergeLiveParticipant(participant, { fallback: item });
      if (item === merged) return item;
      changed = true;
      return merged;
    });
    if (!replaced || !changed) return live;
    return new LiveState({
      roomId: live.roomId,
      participantCount: live.participantCount,
      participants,
      updatedAt: live.updatedAt,
    });
  }

  patchStateUpdate(opts: { live: LiveState | null; participant: LiveParticipant }): LiveStateUpdatePatch {
    const normalized = liveParticipantWithExclusiveMedia(opts.participant);
    return new LiveStateUpdatePatch({
      micMuted: normalized.micMuted,
      headphonesMuted: normalized.headphonesMuted,
      cameraOn: normalized.cameraOn,
      screenSharing: normalized.screenSharing,
      voiceBlocked: normalized.voiceBlocked,
      live: this.mergeParticipant(opts.live, normalized),
    });
  }

  removeUserFromLive(opts: {
    live: LiveState | null;
    rooms: RoomCard[];
    roomId: string;
    userId: string;
  }): LiveDeparturePatch {
    const { live, rooms, roomId, userId } = opts;
    return new LiveDeparturePatch({
      live: this._removeUserFromLiveState({ live, roomId, userId }),
      rooms: this._removeUserFromRoomLivePreview({ rooms, roomId, userId }),
    });
  }

  mergeParticipant(live: LiveState | null, participant: LiveParticipant): LiveState | null {
    if (live == null) return null;
    let replaced = false;
    const participants = live.participants.map((item) => {
      if (item.liveSessionId !== participant.liveSessionId) return item;
      replaced = true;
      return this._mergeLiveParticipant(participant, { fallback: item });
    });
    return new LiveState({
      roomId: live.roomId,
      participantCount: replaced ? live.participantCount : live.participantCount + 1,
      participants: replaced ? participants : [...participants, liveParticipantWithExclusiveMedia(participant)],
      // A participant PATCH response does not carry a room-snapshot revision.
      // Keep the last server revision until the authoritative realtime/refresh
      // snapshot arrives. Stamping this with the client clock can make every
      // later server snapshot look stale after reconnect (especially with
      // clock skew), leaving remote mic/headphones state frozen.
      updatedAt: live.updatedAt,
    });
  }

  private _mergeLiveParticipant(participant: LiveParticipant, opts: { fallback: LiveParticipant }): LiveParticipant {
    return liveParticipantWithExclusiveMedia(
      participant.copyWith({ user: participant.user.mergeMissing(opts.fallback.user) }),
    );
  }

  patchModeratedParticipant(opts: {
    live: LiveState | null;
    participant: LiveParticipant;
    action: LiveModerationAction;
  }): LiveState | null {
    const { live, participant, action } = opts;
    let moderated: LiveParticipant;
    if (action === LiveModerationAction.muteMic) {
      moderated = participant.copyWith({ micMuted: true, micBlocked: true, voiceBlocked: true });
    } else if (action === LiveModerationAction.blockVoice) {
      moderated = participant.copyWith({ headphonesMuted: true, headphonesBlocked: true, headphonesListening: false });
    } else if (action === LiveModerationAction.restoreVoice) {
      moderated = participant.copyWith({ micMuted: false, micBlocked: false, voiceBlocked: false });
    } else if (action === LiveModerationAction.restoreHeadphones) {
      moderated = participant.copyWith({ headphonesMuted: false, headphonesBlocked: false, headphonesListening: true });
    } else {
      // LiveModerationAction.kick
      moderated = participant;
    }
    if (action === LiveModerationAction.kick) return live;
    return this.mergeParticipant(live, moderated);
  }

  private _removeUserFromLiveState(opts: { live: LiveState | null; roomId: string; userId: string }): LiveState | null {
    const { live, roomId, userId } = opts;
    if (live == null || live.roomId !== roomId) return live;
    const remaining = live.participants.filter((participant) => participant.user.id !== userId);
    if (remaining.length === live.participants.length) return live;
    return new LiveState({
      roomId: live.roomId,
      participantCount: remaining.length,
      participants: remaining,
      updatedAt: live.updatedAt,
    });
  }

  private _removeUserFromRoomLivePreview(opts: { rooms: RoomCard[]; roomId: string; userId: string }): RoomCard[] {
    const { rooms, roomId, userId } = opts;
    const idx = rooms.findIndex((room) => room.id === roomId);
    if (idx < 0) return rooms;

    const existing = rooms[idx]!;
    const remainingPreview = existing.liveAvatarPreview.filter((user) => user.id !== userId);
    const nextCount = existing.liveParticipantCount > 0 ? existing.liveParticipantCount - 1 : 0;
    if (
      remainingPreview.length === existing.liveAvatarPreview.length &&
      nextCount === existing.liveParticipantCount
    ) {
      return rooms;
    }
    const next = [...rooms];
    next[idx] = new RoomCard({
      id: existing.id,
      name: existing.name,
      rid: existing.rid,
      visibility: existing.visibility,
      remarkName: existing.remarkName ?? undefined,
      description: existing.description,
      notificationPolicy: existing.notificationPolicy,
      isPinned: existing.isPinned,
      avatarUrl: existing.avatarUrl,
      defaultAvatarKey: existing.defaultAvatarKey,
      memberCount: existing.memberCount,
      onlineMemberCount: existing.onlineMemberCount,
      liveParticipantCount: nextCount,
      liveAvatarPreview: remainingPreview,
      lastMessage: existing.lastMessage,
      unreadCount: existing.unreadCount,
      hasUnreadCount: existing.hasUnreadCount,
      unreadMentionCount: existing.unreadMentionCount,
      hasUnreadMentionCount: existing.hasUnreadMentionCount,
      hasPendingJoinRequests: existing.hasPendingJoinRequests,
      aiVoiceAnnouncementsEnabled: existing.aiVoiceAnnouncementsEnabled,
      updatedAt: existing.updatedAt,
    });
    return next;
  }
}
