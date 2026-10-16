import { expect, test } from 'vitest';

import {
  RoomBlacklistCandidate,
  RoomBlacklistSearchBodyState,
  removeRoomBlacklistEntry,
  roomBlacklistActionLabel,
  roomBlacklistCandidates,
  roomBlacklistSearchBodyState,
  upsertRoomBlacklistEntry,
} from '../../src/app/room_blacklist';
import { RoomBlacklistEntry, RoomMember, UserCommonRoom, UserSummary } from '../../src/protocol/models';

test('roomBlacklistCandidates combines search results and blocked users', () => {
  const blocked = _blacklistEntry(_user('blocked', { displayName: 'Alpha Gone' }));
  const candidates = roomBlacklistCandidates({
    searchResults: [_user('external', { displayName: 'Alpha External' }), _user('member', { displayName: 'Alpha Member' })],
    blacklist: [blocked],
    members: [_member('member')],
    query: 'alpha',
  });

  expect(candidates.map((item) => item.user.id)).toEqual(['external', 'member', 'blocked']);
  expect(candidates[0]!.member).toBe(false);
  expect(candidates[0]!.blocked).toBe(false);
  expect(candidates[1]!.member).toBe(true);
  expect(candidates[1]!.blockActionEnabled).toBe(false);
  expect(candidates[2]!.blocked).toBe(true);
  expect(candidates[2]!.canUnblock).toBe(true);
});

test('roomBlacklistCandidates ignores room names in user search context', () => {
  const candidates = roomBlacklistCandidates({
    searchResults: [
      _user('room_context', {
        roomDisplayName: 'Alpha Room Alias',
        commonRooms: [
          new UserCommonRoom({
            id: 'room_1',
            rid: 'R10001',
            name: 'Alpha Common Room',
            roomDisplayName: 'Alpha Shared Name',
          }),
        ],
      }),
      _user('display_match', { displayName: 'Alpha Person' }),
    ],
    blacklist: [],
    members: [_member('room_member', { roomDisplayName: 'Alpha Current Room' })],
    query: 'alpha',
  });

  expect(candidates.map((item) => item.user.id)).toEqual(['display_match']);
});

test('roomBlacklistCandidates marks protected and busy states', () => {
  const candidates = roomBlacklistCandidates({
    searchResults: [_user('super', { isSuperuser: true }), _user('busy')],
    blacklist: [],
    members: [],
    query: 'user',
    busyUserIds: ['busy'],
  });

  expect(candidates[0]!.superuser).toBe(true);
  expect(candidates[0]!.blockActionEnabled).toBe(false);
  expect(candidates[1]!.busy).toBe(true);
  expect(candidates[1]!.canBlock).toBe(false);
});

test('roomBlacklistSearchBodyState describes loading prompt empty and results', () => {
  const candidate = new RoomBlacklistCandidate({
    user: _user('user_1'),
    member: false,
    blocked: false,
    superuser: false,
    busy: false,
  });

  expect(roomBlacklistSearchBodyState({ searching: true, query: '', candidates: [] })).toBe(
    RoomBlacklistSearchBodyState.loading,
  );
  expect(roomBlacklistSearchBodyState({ searching: false, query: '   ', candidates: [candidate] })).toBe(
    RoomBlacklistSearchBodyState.prompt,
  );
  expect(roomBlacklistSearchBodyState({ searching: false, query: 'logan', candidates: [] })).toBe(
    RoomBlacklistSearchBodyState.empty,
  );
  expect(roomBlacklistSearchBodyState({ searching: false, query: 'logan', candidates: [candidate] })).toBe(
    RoomBlacklistSearchBodyState.results,
  );
});

test('roomBlacklistActionLabel describes candidate actions', () => {
  expect(roomBlacklistActionLabel({ member: true, superuser: false, blocked: false })).toBe('在房间内');
  expect(roomBlacklistActionLabel({ member: false, superuser: true, blocked: false })).toBe('超级用户');
  expect(roomBlacklistActionLabel({ member: false, superuser: false, blocked: true })).toBe('取消拉黑');
  expect(roomBlacklistActionLabel({ member: false, superuser: false, blocked: false })).toBe('拉黑');
});

test('upsert and remove room blacklist entries keep one row per user', () => {
  const oldEntry = _blacklistEntry(_user('blocked', { displayName: 'Old' }));
  const newEntry = _blacklistEntry(_user('blocked', { displayName: 'New' }));
  const other = _blacklistEntry(_user('other'));

  const updated = upsertRoomBlacklistEntry([oldEntry, other], newEntry);

  expect(updated.map((item) => item.user.displayName)).toEqual(['New', 'User other']);
  expect(removeRoomBlacklistEntry(updated, 'blocked')).toEqual([other]);
});

function _blacklistEntry(user: UserSummary): RoomBlacklistEntry {
  return new RoomBlacklistEntry({ user, createdAt: new Date(Date.UTC(2026, 5, 23)) });
}

function _member(id: string, opts: { roomDisplayName?: string | null } = {}): RoomMember {
  return new RoomMember({
    user: _user(id),
    role: 'member',
    joinedAt: new Date(Date.UTC(2026, 5, 5)),
    roomDisplayName: opts.roomDisplayName ?? null,
  });
}

function _user(
  id: string,
  opts: {
    displayName?: string | null;
    roomDisplayName?: string | null;
    commonRooms?: UserCommonRoom[];
    isSuperuser?: boolean;
  } = {},
): UserSummary {
  return new UserSummary({
    id,
    username: `user_${id}`,
    displayName: opts.displayName ?? `User ${id}`,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
    roomDisplayName: opts.roomDisplayName ?? null,
    commonRooms: opts.commonRooms ?? [],
    isSuperuser: opts.isSuperuser ?? false,
  });
}
