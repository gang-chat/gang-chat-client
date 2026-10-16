import { expect, test } from 'vitest';

import {
  GlobalSearchCategory,
  globalSearchCategoryCount,
  globalSearchCategoryKey,
  globalSearchCategoryLabel,
  globalSearchFileSubtitle,
  globalSearchFileTitle,
  globalSearchHasResults,
  globalSearchMessageSenderName,
  globalSearchMessageSubtitle,
  globalSearchMessageTitle,
  globalSearchResultsByAppendingPage,
  globalSearchResultsForView,
  globalSearchResultTimeLabel,
  sidebarRoomsForSearch,
  superuserGlobalSearchCategories,
} from '../../src/app/search_display';
import {
  GlobalSearchCounts,
  GlobalSearchCursors,
  GlobalSearchResults,
  LastMessagePreview,
  Message,
  MessageAttachment,
  MessageSearchResult,
  PublicRoom,
  RoomCard,
  SearchRoomContext,
  UploadedAsset,
  UserSummary,
} from '../../src/protocol/models';

const _june1 = new Date(Date.UTC(2026, 5, 1));

test('global search labels and counts map result categories', () => {
  const results = new GlobalSearchResults({
    myRooms: [_room('room_1')],
    publicRooms: [_publicRoom('room_2')],
    userSettings: [_user('user_1')],
    messages: [_messageResult('room_1')],
    files: [_messageResult('room_1', { type: 'file' })],
    totalCounts: new GlobalSearchCounts({ myRooms: 10, publicRooms: 11, userSettings: 14, messages: 12, files: 13 }),
  });

  expect(globalSearchCategoryLabel(GlobalSearchCategory.myRooms)).toBe('我的房间');
  expect(globalSearchCategoryCount(results, GlobalSearchCategory.myRooms)).toBe(10);
  expect(globalSearchCategoryCount(results, GlobalSearchCategory.publicRooms)).toBe(11);
  expect(globalSearchCategoryCount(results, GlobalSearchCategory.userSettings)).toBe(14);
  expect(globalSearchCategoryCount(results, GlobalSearchCategory.messages)).toBe(12);
  expect(globalSearchCategoryCount(results, GlobalSearchCategory.files)).toBe(13);
  expect(globalSearchCategoryKey(GlobalSearchCategory.myRooms)).toBe('my_rooms');
  expect(globalSearchCategoryKey(GlobalSearchCategory.publicRooms)).toBe('public_rooms');
  expect(globalSearchCategoryKey(GlobalSearchCategory.userSettings)).toBe('user_settings');
  expect(globalSearchCategoryLabel(GlobalSearchCategory.userSettings)).toBe('用户设置');
  expect(superuserGlobalSearchCategories).not.toContain(GlobalSearchCategory.publicRooms);
  expect(superuserGlobalSearchCategories).toContain(GlobalSearchCategory.userSettings);
  expect(globalSearchCategoryKey(GlobalSearchCategory.messages)).toBe('messages');
  expect(globalSearchCategoryKey(GlobalSearchCategory.files)).toBe('files');
  expect(globalSearchHasResults(results)).toBe(true);
});

test('global search cursors parse and merge selected pages', () => {
  const parsed = GlobalSearchResults.fromJson({
    my_rooms: [],
    public_rooms: [],
    messages: [],
    files: [],
    next_cursors: { my_rooms: 'my-next', public_rooms: null, messages: 'message-next', files: 'file-next' },
    total_counts: { my_rooms: 1, public_rooms: 2, messages: 3, files: 4 },
  });

  expect(parsed.nextCursors.myRooms).toBe('my-next');
  expect(parsed.nextCursors.publicRooms).toBeNull();
  expect(parsed.nextCursors.messages).toBe('message-next');
  expect(parsed.nextCursors.files).toBe('file-next');
  expect(parsed.totalCounts.myRooms).toBe(1);
  expect(parsed.totalCounts.publicRooms).toBe(2);
  expect(parsed.totalCounts.messages).toBe(3);
  expect(parsed.totalCounts.files).toBe(4);

  const current = new GlobalSearchResults({
    myRooms: [_room('room_1')],
    publicRooms: [_publicRoom('public_1')],
    messages: [_messageResult('room_1')],
    files: [_messageResult('room_1', { type: 'file' })],
    nextCursors: new GlobalSearchCursors({
      myRooms: 'my-next',
      publicRooms: 'public-next',
      messages: 'message-next',
      files: 'file-next',
    }),
    totalCounts: new GlobalSearchCounts({ myRooms: 1, publicRooms: 1, messages: 8, files: 1 }),
  });
  const page = new GlobalSearchResults({
    myRooms: [],
    publicRooms: [],
    messages: [_messageResult('room_2')],
    files: [],
    nextCursors: new GlobalSearchCursors({ messages: 'message-next-2' }),
    totalCounts: new GlobalSearchCounts({ messages: 9 }),
  });

  const merged = globalSearchResultsByAppendingPage({ current, page, categories: [GlobalSearchCategory.messages] });

  expect(merged.myRooms).toEqual(current.myRooms);
  expect(merged.publicRooms).toEqual(current.publicRooms);
  expect(merged.messages).toHaveLength(2);
  expect(merged.files).toEqual(current.files);
  expect(merged.nextCursors.myRooms).toBe('my-next');
  expect(merged.nextCursors.publicRooms).toBe('public-next');
  expect(merged.nextCursors.messages).toBe('message-next-2');
  expect(merged.nextCursors.files).toBe('file-next');
  expect(merged.totalCounts.myRooms).toBe(1);
  expect(merged.totalCounts.publicRooms).toBe(1);
  expect(merged.totalCounts.messages).toBe(9);
  expect(merged.totalCounts.files).toBe(1);
});

test('sidebar search filter only applies to active my rooms category', () => {
  const rooms = [_room('room_1'), _room('room_2')];
  const results = new GlobalSearchResults({ myRooms: [_room('room_2')], publicRooms: [], messages: [], files: [] });

  expect(sidebarRoomsForSearch({ rooms, query: 'alpha', activeCategory: null, results })).toEqual(rooms);
  expect(
    sidebarRoomsForSearch({ rooms, query: 'alpha', activeCategory: GlobalSearchCategory.messages, results }),
  ).toEqual(rooms);
  expect(
    sidebarRoomsForSearch({ rooms, query: 'alpha', activeCategory: GlobalSearchCategory.myRooms, results }).map(
      (room) => room.id,
    ),
  ).toEqual(['room_2']);
  expect(sidebarRoomsForSearch({ rooms, query: ' ', activeCategory: GlobalSearchCategory.myRooms, results })).toEqual(
    rooms,
  );
});

test('global search view hides my rooms that only match hidden details', () => {
  const results = new GlobalSearchResults({
    myRooms: [
      _room('hidden', { name: 'Hidden', description: '12345' }),
      _room('title', { name: 'Room 1' }),
      _room('message', { name: 'Notes', lastMessageBody: 'visible 1' }),
      _room('rid', { name: 'RID Room', rid: '20000001' }),
    ],
    publicRooms: [],
    messages: [],
    files: [],
  });

  const visibleForOne = globalSearchResultsForView(results, { query: '1' });
  expect(visibleForOne.myRooms.map((room) => room.id)).toEqual(['title', 'message']);

  const visibleForRid = globalSearchResultsForView(results, { query: '20000001' });
  expect(visibleForRid.myRooms.map((room) => room.id)).toEqual(['rid']);
});

test('message search title uses room and subtitle keeps sender with message', () => {
  const result = _messageResult('room_1', { createdAt: new Date(2026, 5, 1, 9, 30) });

  expect(globalSearchMessageTitle(result)).toBe('Alpha');
  expect(globalSearchMessageSenderName(result)).toBe('Alice');
  expect(globalSearchMessageSubtitle(result)).toBe('Alice · alpha roadmap');
  expect(globalSearchResultTimeLabel(result)).toBe('2026/06/01 09:30');
});

test('file search display uses filename and asset metadata', () => {
  const result = _messageResult('room_1', { type: 'file' });

  expect(globalSearchFileTitle(result)).toBe('alpha.pdf');
  expect(globalSearchFileSubtitle(result)).toBe('application/pdf - 4.0 KB');
});

function _user(id: string): UserSummary {
  return new UserSummary({ id, username: 'alice', displayName: 'Alice', avatarUrl: null, defaultAvatarKey: 'blue-3' });
}

function _room(
  id: string,
  opts: { name?: string; rid?: string; description?: string; lastMessageBody?: string | null } = {},
): RoomCard {
  const lastMessageBody = opts.lastMessageBody ?? null;
  return new RoomCard({
    id,
    name: opts.name ?? (id === 'room_1' ? 'Alpha' : 'Beta'),
    rid: opts.rid ?? '',
    description: opts.description ?? '',
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    memberCount: 2,
    liveParticipantCount: 0,
    liveAvatarPreview: [],
    lastMessage:
      lastMessageBody == null
        ? null
        : new LastMessagePreview({
            id: `last_${id}`,
            type: 'text',
            senderDisplayName: 'Alice',
            bodyPreview: lastMessageBody,
            createdAt: _june1,
          }),
    unreadCount: 0,
    updatedAt: _june1,
  });
}

function _publicRoom(id: string): PublicRoom {
  return new PublicRoom({
    id,
    rid: '900002',
    name: 'Public',
    avatarUrl: null,
    defaultAvatarKey: 'room-2',
    visibility: 'public',
    joinPolicy: 'open',
    memberCount: 2,
    liveParticipantCount: 0,
    joined: false,
    joinState: 'none',
  });
}

function _messageResult(roomId: string, opts: { type?: string; createdAt?: Date } = {}): MessageSearchResult {
  const type = opts.type ?? 'text';
  return new MessageSearchResult({
    room: new SearchRoomContext({ id: roomId, rid: '900001', name: 'Alpha', avatarUrl: null, defaultAvatarKey: 'room-1' }),
    message: new Message({
      id: `msg_${type}`,
      roomId,
      sender: _user('user_1'),
      clientMessageId: `cmsg_${type}`,
      type,
      body: type === 'file' ? 'alpha.pdf' : 'alpha roadmap',
      createdAt: opts.createdAt ?? _june1,
      attachments:
        type === 'file'
          ? [
              new MessageAttachment({
                type: 'file',
                name: 'alpha.pdf',
                asset: new UploadedAsset({
                  id: 'asset_1',
                  url: '/alpha.pdf',
                  thumbnailUrl: null,
                  mimeType: 'application/pdf',
                  filename: 'alpha.pdf',
                  sizeBytes: 4096,
                }),
              }),
            ]
          : [],
    }),
  });
}
