import type { JoinRoomResult, RoomInvite, RoomMember, UserSummary } from '../protocol/models';
import { userFacingErrorMessage } from './error_display';
import { roomMemberSearchRank } from './room_members_filter';

export enum RoomInviteSearchBodyState {
  loading = 'loading',
  prompt = 'prompt',
  empty = 'empty',
  results = 'results',
}

export class RoomMemberInviteDialogPatch {
  constructor(opts: {
    searchResults: UserSummary[];
    searching: boolean;
    error: string | null;
    pendingInviteUserIds: Set<string>;
    busyUserIds: Set<string>;
  }) {
    this.searchResults = opts.searchResults;
    this.searching = opts.searching;
    this.error = opts.error;
    this.pendingInviteUserIds = opts.pendingInviteUserIds;
    this.busyUserIds = opts.busyUserIds;
  }

  readonly searchResults: UserSummary[];
  readonly searching: boolean;
  readonly error: string | null;
  readonly pendingInviteUserIds: Set<string>;
  readonly busyUserIds: Set<string>;
}

export class RoomMemberInviteSearchInputPatch {
  constructor(opts: {
    dialog: RoomMemberInviteDialogPatch;
    query: string;
    shouldSearch: boolean;
    shouldCancelInFlightSearch: boolean;
  }) {
    this.dialog = opts.dialog;
    this.query = opts.query;
    this.shouldSearch = opts.shouldSearch;
    this.shouldCancelInFlightSearch = opts.shouldCancelInFlightSearch;
  }

  readonly dialog: RoomMemberInviteDialogPatch;
  readonly query: string;
  readonly shouldSearch: boolean;
  readonly shouldCancelInFlightSearch: boolean;
}

export class RoomInviteCandidate {
  constructor(opts: { user: UserSummary; existing: boolean; pending: boolean; busy: boolean }) {
    this.user = opts.user;
    this.existing = opts.existing;
    this.pending = opts.pending;
    this.busy = opts.busy;
  }

  readonly user: UserSummary;
  readonly existing: boolean;
  readonly pending: boolean;
  readonly busy: boolean;

  get inviteActionEnabled(): boolean {
    return !this.existing && !this.pending;
  }

  get canStartInvite(): boolean {
    return this.inviteActionEnabled && !this.busy;
  }
}

export function roomInvitesEnabled(joinPolicy: string): boolean {
  return joinPolicy.trim().toLowerCase() !== 'closed';
}

export function roomInviteSearchBodyState(opts: {
  searching: boolean;
  query: string;
  candidates: Iterable<RoomInviteCandidate>;
}): RoomInviteSearchBodyState {
  if (opts.searching) return RoomInviteSearchBodyState.loading;
  if (opts.query.trim().length === 0) return RoomInviteSearchBodyState.prompt;
  for (const _ of opts.candidates) return RoomInviteSearchBodyState.results;
  return RoomInviteSearchBodyState.empty;
}

export class RoomInviteReviewState {
  constructor(opts: { invites: RoomInvite[]; pendingRoomIds: Set<string> }) {
    this.invites = opts.invites;
    this.pendingRoomIds = opts.pendingRoomIds;
  }

  readonly invites: RoomInvite[];
  readonly pendingRoomIds: Set<string>;

  get hasPendingInvites(): boolean {
    return this.invites.length > 0;
  }
}

export class RoomMemberInviteActionState {
  constructor(opts: { pendingInviteUserIds: Set<string>; busyUserIds: Set<string> }) {
    this.pendingInviteUserIds = opts.pendingInviteUserIds;
    this.busyUserIds = opts.busyUserIds;
  }

  readonly pendingInviteUserIds: Set<string>;
  readonly busyUserIds: Set<string>;
}

export function roomInviteCandidates(opts: {
  searchResults: Iterable<UserSummary>;
  members: Iterable<RoomMember>;
  query: string;
  pendingInviteUserIds?: Iterable<string>;
  busyUserIds?: Iterable<string>;
  invitesEnabled?: boolean;
}): RoomInviteCandidate[] {
  const { query, invitesEnabled = true } = opts;
  const members = [...opts.members];
  const normalizedQuery = query.trim().toLowerCase();
  const pendingSet = new Set(opts.pendingInviteUserIds ?? []);
  const busySet = new Set(opts.busyUserIds ?? []);
  const membersByUserId = new Map<string, RoomMember>();
  for (const member of members) membersByUserId.set(member.user.id, member);
  const users: UserSummary[] = [];
  const seenUserIds = new Set<string>();

  const add = (user: UserSummary): void => {
    if (user.isSuperuser) return;
    if (seenUserIds.has(user.id)) return;
    seenUserIds.add(user.id);
    users.push(user);
  };

  for (const user of opts.searchResults) {
    if (_userIdentityMatchesQuery(user, normalizedQuery)) {
      add(user);
    }
  }
  if (normalizedQuery.length > 0) {
    for (const member of members) {
      if (roomMemberSearchRank(member, normalizedQuery) < 99) {
        add(member.user);
      }
    }
  }

  return users.map(
    (user) =>
      new RoomInviteCandidate({
        user,
        existing: !invitesEnabled || membersByUserId.has(user.id),
        pending: pendingSet.has(user.id),
        busy: busySet.has(user.id),
      }),
  );
}

function _userIdentityMatchesQuery(user: UserSummary, normalizedQuery: string): boolean {
  if (normalizedQuery.length === 0) return true;

  const contains = (value: string | null | undefined): boolean => {
    const text = value?.trim().toLowerCase();
    return text != null && text.length > 0 && text.includes(normalizedQuery);
  };

  return contains(user.uid) || contains(user.id) || contains(user.username) || contains(user.displayName);
}

export function canStartRoomInvite(opts: {
  userId: string;
  members: Iterable<RoomMember>;
  pendingInviteUserIds?: Iterable<string>;
  busyUserIds?: Iterable<string>;
  isSuperuser?: boolean;
  invitesEnabled?: boolean;
}): boolean {
  const { userId, isSuperuser = false, invitesEnabled = true } = opts;
  if (!invitesEnabled) return false;
  if (isSuperuser) return false;
  for (const member of opts.members) {
    if (member.user.id === userId) return false;
  }
  return !_contains(opts.pendingInviteUserIds ?? [], userId) && !_contains(opts.busyUserIds ?? [], userId);
}

export function roomMemberInviteSearchCleared(opts: {
  pendingInviteUserIds: Iterable<string>;
  busyUserIds: Iterable<string>;
}): RoomMemberInviteDialogPatch {
  return new RoomMemberInviteDialogPatch({
    searchResults: [],
    searching: false,
    error: null,
    pendingInviteUserIds: new Set(opts.pendingInviteUserIds),
    busyUserIds: new Set(opts.busyUserIds),
  });
}

export function roomMemberInviteSearchQueryChanged(opts: {
  rawQuery: string;
  searchResults: Iterable<UserSummary>;
  pendingInviteUserIds: Iterable<string>;
  busyUserIds: Iterable<string>;
}): RoomMemberInviteSearchInputPatch {
  const query = opts.rawQuery.trim();
  if (query.length === 0) {
    return new RoomMemberInviteSearchInputPatch({
      dialog: roomMemberInviteSearchCleared({
        pendingInviteUserIds: opts.pendingInviteUserIds,
        busyUserIds: opts.busyUserIds,
      }),
      query: '',
      shouldSearch: false,
      shouldCancelInFlightSearch: true,
    });
  }
  return new RoomMemberInviteSearchInputPatch({
    dialog: roomMemberInviteSearchStarted({
      searchResults: opts.searchResults,
      pendingInviteUserIds: opts.pendingInviteUserIds,
      busyUserIds: opts.busyUserIds,
    }),
    query,
    shouldSearch: true,
    shouldCancelInFlightSearch: true,
  });
}

export function roomMemberInviteSearchStarted(opts: {
  searchResults: Iterable<UserSummary>;
  pendingInviteUserIds: Iterable<string>;
  busyUserIds: Iterable<string>;
}): RoomMemberInviteDialogPatch {
  return new RoomMemberInviteDialogPatch({
    searchResults: [...opts.searchResults],
    searching: true,
    error: null,
    pendingInviteUserIds: new Set(opts.pendingInviteUserIds),
    busyUserIds: new Set(opts.busyUserIds),
  });
}

export function roomMemberInviteSearchSucceeded(opts: {
  searchResults: Iterable<UserSummary>;
  pendingInviteUserIds: Iterable<string>;
  busyUserIds: Iterable<string>;
}): RoomMemberInviteDialogPatch {
  return new RoomMemberInviteDialogPatch({
    searchResults: [...opts.searchResults],
    searching: false,
    error: null,
    pendingInviteUserIds: new Set(opts.pendingInviteUserIds),
    busyUserIds: new Set(opts.busyUserIds),
  });
}

export function roomMemberInviteSearchFailed(opts: {
  searchResults: Iterable<UserSummary>;
  pendingInviteUserIds: Iterable<string>;
  busyUserIds: Iterable<string>;
  failure: unknown;
}): RoomMemberInviteDialogPatch {
  return new RoomMemberInviteDialogPatch({
    searchResults: [...opts.searchResults],
    searching: false,
    error: userFacingErrorMessage(opts.failure),
    pendingInviteUserIds: new Set(opts.pendingInviteUserIds),
    busyUserIds: new Set(opts.busyUserIds),
  });
}

export function roomMemberInviteActionStarted(opts: {
  userId: string;
  pendingInviteUserIds?: Iterable<string>;
  busyUserIds?: Iterable<string>;
}): RoomMemberInviteActionState {
  return new RoomMemberInviteActionState({
    pendingInviteUserIds: new Set(opts.pendingInviteUserIds ?? []),
    busyUserIds: new Set([...(opts.busyUserIds ?? []), opts.userId]),
  });
}

export function roomMemberInviteActionSucceeded(opts: {
  userId: string;
  pendingInviteUserIds?: Iterable<string>;
  busyUserIds?: Iterable<string>;
}): RoomMemberInviteActionState {
  const { userId } = opts;
  return new RoomMemberInviteActionState({
    pendingInviteUserIds: new Set([...(opts.pendingInviteUserIds ?? []), userId]),
    busyUserIds: new Set([...(opts.busyUserIds ?? [])].filter((item) => item !== userId)),
  });
}

export function roomMemberInviteActionFailed(opts: {
  userId: string;
  pendingInviteUserIds?: Iterable<string>;
  busyUserIds?: Iterable<string>;
}): RoomMemberInviteActionState {
  const { userId } = opts;
  return new RoomMemberInviteActionState({
    pendingInviteUserIds: new Set(opts.pendingInviteUserIds ?? []),
    busyUserIds: new Set([...(opts.busyUserIds ?? [])].filter((item) => item !== userId)),
  });
}

export function roomMemberInviteStarted(opts: {
  searchResults: Iterable<UserSummary>;
  searching: boolean;
  userId: string;
  pendingInviteUserIds: Iterable<string>;
  busyUserIds: Iterable<string>;
}): RoomMemberInviteDialogPatch {
  const state = roomMemberInviteActionStarted({
    userId: opts.userId,
    pendingInviteUserIds: opts.pendingInviteUserIds,
    busyUserIds: opts.busyUserIds,
  });
  return new RoomMemberInviteDialogPatch({
    searchResults: [...opts.searchResults],
    searching: opts.searching,
    error: null,
    pendingInviteUserIds: state.pendingInviteUserIds,
    busyUserIds: state.busyUserIds,
  });
}

export function roomMemberInviteSucceeded(opts: {
  searchResults: Iterable<UserSummary>;
  searching: boolean;
  error: string | null;
  userId: string;
  pendingInviteUserIds: Iterable<string>;
  busyUserIds: Iterable<string>;
}): RoomMemberInviteDialogPatch {
  const state = roomMemberInviteActionSucceeded({
    userId: opts.userId,
    pendingInviteUserIds: opts.pendingInviteUserIds,
    busyUserIds: opts.busyUserIds,
  });
  return new RoomMemberInviteDialogPatch({
    searchResults: [...opts.searchResults],
    searching: opts.searching,
    error: opts.error,
    pendingInviteUserIds: state.pendingInviteUserIds,
    busyUserIds: state.busyUserIds,
  });
}

export function roomMemberInviteFailed(opts: {
  searchResults: Iterable<UserSummary>;
  searching: boolean;
  userId: string;
  pendingInviteUserIds: Iterable<string>;
  busyUserIds: Iterable<string>;
  failure: unknown;
}): RoomMemberInviteDialogPatch {
  const state = roomMemberInviteActionFailed({
    userId: opts.userId,
    pendingInviteUserIds: opts.pendingInviteUserIds,
    busyUserIds: opts.busyUserIds,
  });
  return new RoomMemberInviteDialogPatch({
    searchResults: [...opts.searchResults],
    searching: opts.searching,
    error: userFacingErrorMessage(opts.failure),
    pendingInviteUserIds: state.pendingInviteUserIds,
    busyUserIds: state.busyUserIds,
  });
}

export function reviewedRoomInvites(opts: {
  invites: Iterable<RoomInvite>;
  pendingRoomIds: Iterable<string>;
  invite: RoomInvite;
  accept: boolean;
  result: JoinRoomResult;
}): RoomInviteReviewState {
  const { invite, accept, result } = opts;
  const nextPendingRoomIds = new Set(opts.pendingRoomIds);
  if (accept && result.pending) {
    nextPendingRoomIds.add(invite.room.id);
  }
  const acceptedRoomId = accept ? invite.room.id : null;
  return new RoomInviteReviewState({
    invites: [...opts.invites].filter((item) => (accept ? item.room.id !== acceptedRoomId : item.id !== invite.id)),
    pendingRoomIds: nextPendingRoomIds,
  });
}

function _contains(values: Iterable<string>, value: string): boolean {
  for (const item of values) {
    if (item === value) return true;
  }
  return false;
}
