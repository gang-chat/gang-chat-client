import { describe, expect, test } from 'vitest';

import {
  activeMessageMentionQuery,
  MessageMentionKind,
  messageMentionDescriptors,
  messageMentionOptions,
  messageMentionRanges,
  messageMentionsUser,
} from '../../src/app/message_mentions';
import { RoomMember, UserSummary } from '../../src/protocol/models';

describe('activeMessageMentionQuery', () => {
  test('finds the query before the cursor', () => {
    const query = activeMessageMentionQuery({
      text: 'hello @pan',
      cursorOffset: 'hello @pan'.length,
    });

    expect(query).not.toBeNull();
    expect(query!.start).toBe(6);
    expect(query!.end).toBe(10);
    expect(query!.query).toBe('pan');
  });

  test('ignores email-like text', () => {
    const query = activeMessageMentionQuery({
      text: 'mail a@b.com',
      cursorOffset: 'mail a@b.com'.length,
    });

    expect(query).toBeNull();
  });

  test('finds mention directly after existing text', () => {
    const query = activeMessageMentionQuery({
      text: 'hello@pan',
      cursorOffset: 'hello@pan'.length,
    });

    expect(query).not.toBeNull();
    expect(query!.start).toBe(5);
    expect(query!.end).toBe(9);
    expect(query!.query).toBe('pan');
  });

  test('finds mention directly after chinese text', () => {
    const query = activeMessageMentionQuery({
      text: '你好@潘',
      cursorOffset: '你好@潘'.length,
    });

    expect(query).not.toBeNull();
    expect(query!.start).toBe(2);
    expect(query!.query).toBe('潘');
  });
});

test('messageMentionRanges highlights mention tokens', () => {
  const ranges = messageMentionRanges('hi @alpha and @beta plus text@gamma');

  expect(ranges.map((range) => [range.start, range.end])).toEqual([
    [3, 9],
    [14, 19],
    [29, 35],
  ]);
});

test('messageMentionRanges ignores email addresses', () => {
  const ranges = messageMentionRanges('mail a@b.com and hi@alpha');

  expect(ranges.map((range) => [range.start, range.end])).toEqual([[19, 25]]);
});

test('messageMentionRanges can use known labels containing spaces', () => {
  const ranges = messageMentionRanges('hi @Panel Member and @other', {
    labels: ['Panel Member'],
  });

  expect(ranges.map((range) => [range.start, range.end])).toEqual([
    [3, 16],
    [21, 27],
  ]);
});

test('messageMentionOptions filters by room display name and role', () => {
  const owner = _member('1', 'owner', 'Panel Owner', { role: 'owner' });
  const admin = _member('2', 'admin', 'Panel Admin', { role: 'admin' });
  const member = _member('3', 'member', 'Panel Member');

  const options = messageMentionOptions({
    members: [member, admin, owner],
    query: 'pan',
    ownerUserId: owner.user.id,
  });

  expect(options.map((option) => option.label)).toEqual(['Panel Owner', 'Panel Admin', 'Panel Member']);
});

test('messageMentionOptions excludes the current user', () => {
  const currentUser = _member('1', 'me', 'Panel Me');
  const otherUser = _member('2', 'other', 'Panel Other');

  const options = messageMentionOptions({
    members: [currentUser, otherUser],
    query: 'pan',
    ownerUserId: null,
    excludedUserId: currentUser.user.id,
  });

  expect(options.map((option) => option.label)).toEqual(['Panel Other']);
});

test('messageMentionOptions filters by UID', () => {
  const member = _member('1', 'member', 'Panel Member', { uid: '10000088' });

  const options = messageMentionOptions({
    members: [member],
    query: '0088',
    ownerUserId: null,
  });

  expect(options.map((option) => option.label)).toEqual(['Panel Member']);
});

test('messageMentionOptions places special mentions first', () => {
  const member = _member('1', 'member', 'Panel Member');

  const options = messageMentionOptions({
    members: [member],
    query: '',
    ownerUserId: null,
  });

  expect(options.map((option) => option.label).slice(0, 3)).toEqual(['所有人', '管理员', 'Panel Member']);
  expect(options[0]!.kind).toBe(MessageMentionKind.everyone);
  expect(options[1]!.kind).toBe(MessageMentionKind.admins);
});

test('messageMentionDescriptors includes special and user mentions', () => {
  const member = _member('1', 'member', 'Panel Member');

  const mentions = messageMentionDescriptors({
    text: '@所有人 @管理员 @Panel Member @Panel Member',
    members: [member],
    confirmedLabels: ['所有人', '管理员', 'Panel Member', 'Panel Member'],
  });

  expect(mentions).toEqual([
    { type: 'all', label: '所有人' },
    { type: 'admins', label: '管理员' },
    { type: 'user', user_id: '1', label: 'Panel Member' },
  ]);
});

test('messageMentionsUser matches all admins and direct users', () => {
  const admin = _member('1', 'admin', 'Panel Admin', { role: 'admin' }).user;
  const member = _member('2', 'member', 'Panel Member').user;

  expect(messageMentionsUser({ text: '@管理员', user: admin, ownerUserId: null })).toBe(true);
  expect(messageMentionsUser({ text: '@管理员', user: member, ownerUserId: null })).toBe(false);
  expect(
    messageMentionsUser({
      text: '',
      mentions: [{ type: 'user', user_id: '2' }],
      user: member,
      ownerUserId: null,
    }),
  ).toBe(true);
  expect(messageMentionsUser({ text: '@所有人', user: member, ownerUserId: null })).toBe(true);
});

function _member(
  id: string,
  username: string,
  roomDisplayName: string,
  opts: { role?: string; uid?: string | null } = {},
): RoomMember {
  const { role = 'member', uid = null } = opts;
  const user = new UserSummary({
    id,
    username,
    displayName: username,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
    uid,
    roomDisplayName,
    roomRole: role,
  });
  return new RoomMember({
    user,
    role,
    joinedAt: new Date(0),
    roomDisplayName,
  });
}
