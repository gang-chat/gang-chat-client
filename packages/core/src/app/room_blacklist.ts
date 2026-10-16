import type { RoomBlacklistEntry, RoomMember, UserSummary } from '../protocol/models';
import { roomMemberSearchRank } from './room_members_filter';

export enum RoomBlacklistSearchBodyState {
  loading = 'loading',
  prompt = 'prompt',
  empty = 'empty',
  results = 'results',
}

export class RoomBlacklistCandidate {
  constructor(opts: { user: UserSummary; member: boolean; blocked: boolean; superuser: boolean; busy: boolean }) {
    this.user = opts.user;
    this.member = opts.member;
    this.blocked = opts.blocked;
    this.superuser = opts.superuser;
    this.busy = opts.busy;
  }

  readonly user: UserSummary;
  readonly member: boolean;
  readonly blocked: boolean;
  readonly superuser: boolean;
  readonly busy: boolean;

  get blockActionEnabled(): boolean {
    return !this.member && !this.superuser;
  }

  get canBlock(): boolean {
    return this.blockActionEnabled && !this.blocked && !this.busy;
  }

  get canUnblock(): boolean {
    return this.blocked && !this.busy;
  }
}

export function roomBlacklistSearchBodyState(opts: {
  searching: boolean;
  query: string;
  candidates: Iterable<RoomBlacklistCandidate>;
}): RoomBlacklistSearchBodyState {
  if (opts.searching) return RoomBlacklistSearchBodyState.loading;
  if (opts.query.trim().length === 0) return RoomBlacklistSearchBodyState.prompt;
  if (_isEmpty(opts.candidates)) return RoomBlacklistSearchBodyState.empty;
  return RoomBlacklistSearchBodyState.results;
}

export function roomBlacklistActionLabel(opts: { member: boolean; superuser: boolean; blocked: boolean }): string {
  if (opts.member) return '在房间内';
  if (opts.superuser) return '超级用户';
  if (opts.blocked) return '取消拉黑';
  return '拉黑';
}

export function roomBlacklistCandidates(opts: {
  searchResults: Iterable<UserSummary>;
  blacklist: Iterable<RoomBlacklistEntry>;
  members: Iterable<RoomMember>;
  query: string;
  busyUserIds?: Iterable<string>;
}): RoomBlacklistCandidate[] {
  const blacklist = [...opts.blacklist];
  const members = [...opts.members];
  const normalizedQuery = opts.query.trim().toLowerCase();
  const membersByUserId = new Map<string, RoomMember>();
  for (const member of members) membersByUserId.set(member.user.id, member);
  const blockedByUserId = new Map<string, RoomBlacklistEntry>();
  for (const entry of blacklist) blockedByUserId.set(entry.user.id, entry);
  const busySet = new Set(opts.busyUserIds ?? []);
  const users: UserSummary[] = [];
  const seenUserIds = new Set<string>();

  const add = (user: UserSummary): void => {
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
    for (const entry of blacklist) {
      if (_userIdentityMatchesQuery(entry.user, normalizedQuery)) {
        add(entry.user);
      }
    }
    for (const member of members) {
      if (roomMemberSearchRank(member, normalizedQuery) < 99) {
        add(member.user);
      }
    }
  }

  return users.map(
    (user) =>
      new RoomBlacklistCandidate({
        user,
        member: membersByUserId.has(user.id),
        blocked: blockedByUserId.has(user.id),
        superuser: user.isSuperuser,
        busy: busySet.has(user.id),
      }),
  );
}

export function upsertRoomBlacklistEntry(
  entries: Iterable<RoomBlacklistEntry>,
  entry: RoomBlacklistEntry,
): RoomBlacklistEntry[] {
  return [entry, ...[...entries].filter((item) => item.user.id !== entry.user.id)];
}

export function removeRoomBlacklistEntry(entries: Iterable<RoomBlacklistEntry>, userId: string): RoomBlacklistEntry[] {
  return [...entries].filter((item) => item.user.id !== userId);
}

function _userIdentityMatchesQuery(user: UserSummary, normalizedQuery: string): boolean {
  if (normalizedQuery.length === 0) return true;

  const contains = (value: string | null | undefined): boolean => {
    const text = value?.trim().toLowerCase();
    return text != null && text.length > 0 && text.includes(normalizedQuery);
  };

  return contains(user.uid) || contains(user.id) || contains(user.username) || contains(user.displayName);
}

function _isEmpty(values: Iterable<unknown>): boolean {
  for (const _ of values) return false;
  return true;
}
