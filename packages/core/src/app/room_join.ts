import type { JoinRoomResult, PublicRoom, RoomInvite } from '../protocol/models';
import { userFacingErrorMessage } from './error_display';
import { publicRoomJoinActionable } from './room_display';
import * as room_invites from './room_invites';

export class RoomJoinSearchPatch {
  constructor(opts: { results: PublicRoom[]; searching: boolean; error: string | null }) {
    this.results = opts.results;
    this.searching = opts.searching;
    this.error = opts.error;
  }

  readonly results: PublicRoom[];
  readonly searching: boolean;
  readonly error: string | null;
}

export class RoomJoinSearchInputPatch {
  constructor(opts: {
    search: RoomJoinSearchPatch;
    query: string;
    shouldSearch: boolean;
    shouldCancelInFlightSearch: boolean;
  }) {
    this.search = opts.search;
    this.query = opts.query;
    this.shouldSearch = opts.shouldSearch;
    this.shouldCancelInFlightSearch = opts.shouldCancelInFlightSearch;
  }

  readonly search: RoomJoinSearchPatch;
  readonly query: string;
  readonly shouldSearch: boolean;
  readonly shouldCancelInFlightSearch: boolean;
}

export class RoomJoinInvitesPatch {
  constructor(opts: { invites: RoomInvite[]; loading: boolean; error: string | null }) {
    this.invites = opts.invites;
    this.loading = opts.loading;
    this.error = opts.error;
  }

  readonly invites: RoomInvite[];
  readonly loading: boolean;
  readonly error: string | null;
}

export class RoomJoinPublicActionPatch {
  constructor(opts: { busyRoomId: string | null; error: string | null; pendingRoomIds: Set<string> }) {
    this.busyRoomId = opts.busyRoomId;
    this.error = opts.error;
    this.pendingRoomIds = opts.pendingRoomIds;
  }

  readonly busyRoomId: string | null;
  readonly error: string | null;
  readonly pendingRoomIds: Set<string>;
}

export class RoomJoinInviteDecisionPatch {
  constructor(opts: {
    invites: RoomInvite[];
    pendingRoomIds: Set<string>;
    busyInviteId: string | null;
    error: string | null;
  }) {
    this.invites = opts.invites;
    this.pendingRoomIds = opts.pendingRoomIds;
    this.busyInviteId = opts.busyInviteId;
    this.error = opts.error;
  }

  readonly invites: RoomInvite[];
  readonly pendingRoomIds: Set<string>;
  readonly busyInviteId: string | null;
  readonly error: string | null;
}

export function hasRoomSearchQuery(query: string): boolean {
  return query.trim().length > 0;
}

export function shouldShowRoomInviteSection(opts: { loadingInvites: boolean; invites: Iterable<RoomInvite> }): boolean {
  if (opts.loadingInvites) return true;
  for (const _ of opts.invites) return true;
  return false;
}

export function canStartPublicRoomAction(busyRoomId: string | null): boolean {
  return busyRoomId == null;
}

export function canStartRoomInviteDecision(opts: { busyInviteId: string | null; busyRoomId: string | null }): boolean {
  return opts.busyInviteId == null && opts.busyRoomId == null;
}

export function joinRequestReasonValue(rawReason: string): string | null {
  const reason = rawReason.trim();
  if (reason.length === 0) return null;
  return reason;
}

export function publicRoomJoinSubtitle(room: PublicRoom): string {
  const rid = room.rid.trim();
  if (rid.length === 0) return `${room.memberCount} 名成员`;
  return `${rid} · ${room.memberCount} 名成员`;
}

export function pendingRoomInviteSubtitle(invite: RoomInvite): string {
  const rid = invite.room.rid.trim();
  if (rid.length === 0) return '邀请你加入';
  return `邀请你加入 · RID ${rid}`;
}

export class PublicRoomJoinCandidate {
  constructor(opts: { room: PublicRoom; pending: boolean; busy: boolean }) {
    this.room = opts.room;
    this.pending = opts.pending;
    this.busy = opts.busy;
  }

  readonly room: PublicRoom;
  readonly pending: boolean;
  readonly busy: boolean;

  get opensJoinedRoom(): boolean {
    return this.room.joined;
  }

  get actionable(): boolean {
    return publicRoomJoinActionable(this.room, { pending: this.pending });
  }

  get actionEnabled(): boolean {
    return this.actionable && !this.busy;
  }
}

export function publicRoomJoinCandidates(opts: {
  rooms: Iterable<PublicRoom>;
  pendingRoomIds?: Iterable<string>;
  busyRoomId?: string | null;
}): PublicRoomJoinCandidate[] {
  const pendingSet = new Set(opts.pendingRoomIds ?? []);
  const busyRoomId = opts.busyRoomId ?? null;
  return [...opts.rooms].map(
    (room) =>
      new PublicRoomJoinCandidate({
        room,
        pending: room.joinState === 'pending' || pendingSet.has(room.id),
        busy: busyRoomId === room.id,
      }),
  );
}

export function pendingRoomIdsAfterJoinResult(opts: {
  pendingRoomIds: Iterable<string>;
  room: PublicRoom;
  result: JoinRoomResult;
}): Set<string> {
  const next = new Set(opts.pendingRoomIds);
  if (opts.result.pending) next.add(opts.room.id);
  return next;
}

export function roomJoinSearchCleared(): RoomJoinSearchPatch {
  return new RoomJoinSearchPatch({ results: [], searching: false, error: null });
}

export function roomJoinSearchQueryChanged(opts: {
  rawQuery: string;
  results: Iterable<PublicRoom>;
  error: string | null;
}): RoomJoinSearchInputPatch {
  const query = opts.rawQuery.trim();
  if (!hasRoomSearchQuery(query)) {
    return new RoomJoinSearchInputPatch({
      search: roomJoinSearchCleared(),
      query: '',
      shouldSearch: false,
      shouldCancelInFlightSearch: true,
    });
  }
  return new RoomJoinSearchInputPatch({
    search: roomJoinSearchStarted({ results: opts.results, error: opts.error }),
    query,
    shouldSearch: true,
    shouldCancelInFlightSearch: true,
  });
}

export function roomJoinSearchStarted(opts: { results: Iterable<PublicRoom>; error: string | null }): RoomJoinSearchPatch {
  return new RoomJoinSearchPatch({
    results: [...opts.results],
    searching: true,
    error: opts.error,
  });
}

export function roomJoinSearchSucceeded(opts: { results: Iterable<PublicRoom> }): RoomJoinSearchPatch {
  return new RoomJoinSearchPatch({
    results: [...opts.results],
    searching: false,
    error: null,
  });
}

export function roomJoinSearchFailed(opts: { results: Iterable<PublicRoom>; failure: unknown }): RoomJoinSearchPatch {
  return new RoomJoinSearchPatch({
    results: [...opts.results],
    searching: false,
    error: userFacingErrorMessage(opts.failure),
  });
}

export function roomJoinInvitesLoadStarted(opts: { invites: Iterable<RoomInvite> }): RoomJoinInvitesPatch {
  return new RoomJoinInvitesPatch({
    invites: [...opts.invites],
    loading: true,
    error: null,
  });
}

export function roomJoinInvitesLoadSucceeded(opts: { invites: Iterable<RoomInvite> }): RoomJoinInvitesPatch {
  return new RoomJoinInvitesPatch({
    invites: [...opts.invites],
    loading: false,
    error: null,
  });
}

export function roomJoinInvitesLoadFailed(opts: { invites: Iterable<RoomInvite>; failure: unknown }): RoomJoinInvitesPatch {
  return new RoomJoinInvitesPatch({
    invites: [...opts.invites],
    loading: false,
    error: userFacingErrorMessage(opts.failure),
  });
}

export function roomJoinPublicActionStarted(opts: {
  roomId: string;
  pendingRoomIds: Iterable<string>;
}): RoomJoinPublicActionPatch {
  return new RoomJoinPublicActionPatch({
    busyRoomId: opts.roomId,
    error: null,
    pendingRoomIds: new Set(opts.pendingRoomIds),
  });
}

export function roomJoinPublicActionPending(opts: {
  busyRoomId: string | null;
  error: string | null;
  pendingRoomIds: Iterable<string>;
  room: PublicRoom;
  result: JoinRoomResult;
}): RoomJoinPublicActionPatch {
  return new RoomJoinPublicActionPatch({
    busyRoomId: opts.busyRoomId,
    error: opts.error,
    pendingRoomIds: pendingRoomIdsAfterJoinResult({
      pendingRoomIds: opts.pendingRoomIds,
      room: opts.room,
      result: opts.result,
    }),
  });
}

export function roomJoinPublicActionFailed(opts: {
  busyRoomId: string | null;
  pendingRoomIds: Iterable<string>;
  failure: unknown;
}): RoomJoinPublicActionPatch {
  return new RoomJoinPublicActionPatch({
    busyRoomId: opts.busyRoomId,
    error: userFacingErrorMessage(opts.failure),
    pendingRoomIds: new Set(opts.pendingRoomIds),
  });
}

export function roomJoinPublicActionFinished(opts: {
  error: string | null;
  pendingRoomIds: Iterable<string>;
}): RoomJoinPublicActionPatch {
  return new RoomJoinPublicActionPatch({
    busyRoomId: null,
    error: opts.error,
    pendingRoomIds: new Set(opts.pendingRoomIds),
  });
}

export function roomJoinInviteDecisionStarted(opts: {
  invites: Iterable<RoomInvite>;
  pendingRoomIds: Iterable<string>;
  inviteId: string;
}): RoomJoinInviteDecisionPatch {
  return new RoomJoinInviteDecisionPatch({
    invites: [...opts.invites],
    pendingRoomIds: new Set(opts.pendingRoomIds),
    busyInviteId: opts.inviteId,
    error: null,
  });
}

export function roomJoinInviteDecisionSucceeded(opts: {
  invites: Iterable<RoomInvite>;
  pendingRoomIds: Iterable<string>;
  invite: RoomInvite;
  accept: boolean;
  result: JoinRoomResult;
}): RoomJoinInviteDecisionPatch {
  const next = room_invites.reviewedRoomInvites({
    invites: opts.invites,
    pendingRoomIds: opts.pendingRoomIds,
    invite: opts.invite,
    accept: opts.accept,
    result: opts.result,
  });
  return new RoomJoinInviteDecisionPatch({
    invites: next.invites,
    pendingRoomIds: next.pendingRoomIds,
    busyInviteId: null,
    error: null,
  });
}

export function roomJoinInviteDecisionFailed(opts: {
  invites: Iterable<RoomInvite>;
  pendingRoomIds: Iterable<string>;
  busyInviteId: string | null;
  failure: unknown;
}): RoomJoinInviteDecisionPatch {
  return new RoomJoinInviteDecisionPatch({
    invites: [...opts.invites],
    pendingRoomIds: new Set(opts.pendingRoomIds),
    busyInviteId: opts.busyInviteId,
    error: userFacingErrorMessage(opts.failure),
  });
}

export function roomJoinInviteDecisionFinished(opts: {
  invites: Iterable<RoomInvite>;
  pendingRoomIds: Iterable<string>;
  error: string | null;
}): RoomJoinInviteDecisionPatch {
  return new RoomJoinInviteDecisionPatch({
    invites: [...opts.invites],
    pendingRoomIds: new Set(opts.pendingRoomIds),
    busyInviteId: null,
    error: opts.error,
  });
}
