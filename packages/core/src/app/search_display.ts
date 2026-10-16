import {
  GlobalSearchCounts,
  GlobalSearchCursors,
  GlobalSearchResults,
  RoomCard,
  type Message,
  type MessageAttachment,
  type MessageSearchResult,
  type SearchRoomContext,
} from '../protocol/models';
import * as file_display from './file_display';
import * as room_display from './room_display';
import * as room_notifications from './room_notifications';

export enum GlobalSearchCategory {
  myRooms = 'myRooms',
  publicRooms = 'publicRooms',
  userSettings = 'userSettings',
  messages = 'messages',
  files = 'files',
}

export const globalSearchCategories: readonly GlobalSearchCategory[] = [
  GlobalSearchCategory.myRooms,
  GlobalSearchCategory.publicRooms,
  GlobalSearchCategory.messages,
  GlobalSearchCategory.files,
];

export const superuserGlobalSearchCategories: readonly GlobalSearchCategory[] = [
  GlobalSearchCategory.myRooms,
  GlobalSearchCategory.userSettings,
  GlobalSearchCategory.messages,
  GlobalSearchCategory.files,
];

export function globalSearchCategoryKey(category: GlobalSearchCategory): string {
  switch (category) {
    case GlobalSearchCategory.myRooms:
      return 'my_rooms';
    case GlobalSearchCategory.publicRooms:
      return 'public_rooms';
    case GlobalSearchCategory.userSettings:
      return 'user_settings';
    case GlobalSearchCategory.messages:
      return 'messages';
    case GlobalSearchCategory.files:
      return 'files';
  }
}

export function globalSearchCursorForCategory(
  cursors: GlobalSearchCursors,
  category: GlobalSearchCategory,
): string | null {
  switch (category) {
    case GlobalSearchCategory.myRooms:
      return cursors.myRooms;
    case GlobalSearchCategory.publicRooms:
      return cursors.publicRooms;
    case GlobalSearchCategory.userSettings:
      return cursors.userSettings;
    case GlobalSearchCategory.messages:
      return cursors.messages;
    case GlobalSearchCategory.files:
      return cursors.files;
  }
}

export function globalSearchCursorsForCategories(
  cursors: GlobalSearchCursors,
  categories: Iterable<GlobalSearchCategory>,
): GlobalSearchCursors {
  let myRooms: string | null = null;
  let publicRooms: string | null = null;
  let userSettings: string | null = null;
  let messages: string | null = null;
  let files: string | null = null;
  for (const category of categories) {
    switch (category) {
      case GlobalSearchCategory.myRooms:
        myRooms = cursors.myRooms;
        break;
      case GlobalSearchCategory.publicRooms:
        publicRooms = cursors.publicRooms;
        break;
      case GlobalSearchCategory.userSettings:
        userSettings = cursors.userSettings;
        break;
      case GlobalSearchCategory.messages:
        messages = cursors.messages;
        break;
      case GlobalSearchCategory.files:
        files = cursors.files;
        break;
    }
  }
  return new GlobalSearchCursors({
    myRooms,
    publicRooms,
    userSettings,
    messages,
    files,
  });
}

export function globalSearchResultsByAppendingPage(opts: {
  current: GlobalSearchResults;
  page: GlobalSearchResults;
  categories: Iterable<GlobalSearchCategory>;
}): GlobalSearchResults {
  const { current, page } = opts;
  let appendMyRooms = false;
  let appendPublicRooms = false;
  let appendUserSettings = false;
  let appendMessages = false;
  let appendFiles = false;
  for (const category of opts.categories) {
    switch (category) {
      case GlobalSearchCategory.myRooms:
        appendMyRooms = true;
        break;
      case GlobalSearchCategory.publicRooms:
        appendPublicRooms = true;
        break;
      case GlobalSearchCategory.userSettings:
        appendUserSettings = true;
        break;
      case GlobalSearchCategory.messages:
        appendMessages = true;
        break;
      case GlobalSearchCategory.files:
        appendFiles = true;
        break;
    }
  }

  return new GlobalSearchResults({
    myRooms: appendMyRooms ? [...current.myRooms, ...page.myRooms] : current.myRooms,
    publicRooms: appendPublicRooms ? [...current.publicRooms, ...page.publicRooms] : current.publicRooms,
    userSettings: appendUserSettings ? [...current.userSettings, ...page.userSettings] : current.userSettings,
    messages: appendMessages ? [...current.messages, ...page.messages] : current.messages,
    files: appendFiles ? [...current.files, ...page.files] : current.files,
    nextCursors: new GlobalSearchCursors({
      myRooms: appendMyRooms ? page.nextCursors.myRooms : current.nextCursors.myRooms,
      publicRooms: appendPublicRooms ? page.nextCursors.publicRooms : current.nextCursors.publicRooms,
      userSettings: appendUserSettings ? page.nextCursors.userSettings : current.nextCursors.userSettings,
      messages: appendMessages ? page.nextCursors.messages : current.nextCursors.messages,
      files: appendFiles ? page.nextCursors.files : current.nextCursors.files,
    }),
    totalCounts: new GlobalSearchCounts({
      myRooms: appendMyRooms ? (page.totalCounts.myRooms ?? current.totalCounts.myRooms) : current.totalCounts.myRooms,
      publicRooms: appendPublicRooms
        ? (page.totalCounts.publicRooms ?? current.totalCounts.publicRooms)
        : current.totalCounts.publicRooms,
      userSettings: appendUserSettings
        ? (page.totalCounts.userSettings ?? current.totalCounts.userSettings)
        : current.totalCounts.userSettings,
      messages: appendMessages
        ? (page.totalCounts.messages ?? current.totalCounts.messages)
        : current.totalCounts.messages,
      files: appendFiles ? (page.totalCounts.files ?? current.totalCounts.files) : current.totalCounts.files,
    }),
  });
}

export function hasGlobalSearchQuery(query: string): boolean {
  return query.trim().length > 0;
}

export function globalSearchCategoryLabel(category: GlobalSearchCategory): string {
  switch (category) {
    case GlobalSearchCategory.myRooms:
      return '我的房间';
    case GlobalSearchCategory.publicRooms:
      return '公开房间';
    case GlobalSearchCategory.userSettings:
      return '用户设置';
    case GlobalSearchCategory.messages:
      return '聊天记录';
    case GlobalSearchCategory.files:
      return '聊天文件';
  }
}

export function globalSearchCategoryCount(results: GlobalSearchResults | null, category: GlobalSearchCategory): number {
  if (results == null) return 0;
  switch (category) {
    case GlobalSearchCategory.myRooms:
      return results.totalCounts.myRooms ?? results.myRooms.length;
    case GlobalSearchCategory.publicRooms:
      return results.totalCounts.publicRooms ?? results.publicRooms.length;
    case GlobalSearchCategory.userSettings:
      return results.totalCounts.userSettings ?? results.userSettings.length;
    case GlobalSearchCategory.messages:
      return results.totalCounts.messages ?? results.messages.length;
    case GlobalSearchCategory.files:
      return results.totalCounts.files ?? results.files.length;
  }
}

export function globalSearchHasResults(results: GlobalSearchResults | null): boolean {
  if (results == null) return false;
  return (
    results.myRooms.length > 0 ||
    results.publicRooms.length > 0 ||
    results.userSettings.length > 0 ||
    results.messages.length > 0 ||
    results.files.length > 0
  );
}

export function globalSearchResultsForView(results: GlobalSearchResults, opts: { query: string }): GlobalSearchResults {
  return new GlobalSearchResults({
    myRooms: visibleMyRoomSearchResults({ rooms: results.myRooms, query: opts.query }),
    publicRooms: results.publicRooms,
    userSettings: results.userSettings,
    messages: results.messages,
    files: results.files,
    nextCursors: results.nextCursors,
    totalCounts: results.totalCounts,
  });
}

export function visibleMyRoomSearchResults(opts: { rooms: Iterable<RoomCard>; query: string }): RoomCard[] {
  const normalizedQuery = opts.query.trim().toLowerCase();
  // Dart returned `List.unmodifiable(rooms)` here.
  if (normalizedQuery.length === 0) return Object.freeze([...opts.rooms]) as RoomCard[];
  return [...opts.rooms].filter((room) => _visibleRoomCardMatches(room, normalizedQuery));
}

export function sidebarRoomsForSearch(opts: {
  rooms: RoomCard[];
  query: string;
  activeCategory: GlobalSearchCategory | null;
  results: GlobalSearchResults | null;
}): RoomCard[] {
  const { rooms } = opts;
  if (!hasGlobalSearchQuery(opts.query)) return rooms;
  if (opts.activeCategory !== GlobalSearchCategory.myRooms) return rooms;
  return opts.results?.myRooms ?? [];
}

export function roomCardFromSearchContext(room: SearchRoomContext, opts: { updatedAt: Date }): RoomCard {
  return new RoomCard({
    id: room.id,
    name: room.name,
    rid: room.rid,
    remarkName: room.remarkName,
    avatarUrl: room.avatarUrl,
    defaultAvatarKey: room.defaultAvatarKey,
    memberCount: 0,
    liveParticipantCount: 0,
    liveAvatarPreview: [],
    lastMessage: null,
    unreadCount: 0,
    updatedAt: opts.updatedAt,
  });
}

export function globalSearchMessageTitle(result: MessageSearchResult): string {
  const roomName = result.room.displayName.trim();
  return roomName.length > 0 ? roomName : '房间';
}

export function globalSearchMessageSubtitle(result: MessageSearchResult): string {
  const senderName = globalSearchMessageSenderName(result);
  const body = result.message.body.trim();
  if (body.length === 0) return senderName;
  return `${senderName} · ${body}`;
}

export function globalSearchMessageSenderName(result: MessageSearchResult): string {
  const roomDisplayName = result.message.sender.roomDisplayName?.trim();
  if (roomDisplayName != null && roomDisplayName.length > 0) {
    return roomDisplayName;
  }
  const displayName = result.message.sender.displayName.trim();
  if (displayName.length > 0) return displayName;
  const username = result.message.sender.username.trim();
  return username.length > 0 ? username : '用户';
}

export function globalSearchFileTitle(result: MessageSearchResult): string {
  const attachment = _firstFileAttachment(result.message);
  if (attachment != null) return file_display.fileAttachmentTitle(attachment);
  const body = result.message.body.trim();
  return body.length > 0 ? body : 'file';
}

export function globalSearchFileSubtitle(result: MessageSearchResult): string {
  const meta = file_display.fileAttachmentMeta(_firstFileAttachment(result.message)?.asset ?? null);
  return meta.length > 0 ? meta : '文件';
}

export function globalSearchResultTimeLabel(result: MessageSearchResult): string {
  return room_notifications.roomInviteTimestampLabel(result.message.createdAt);
}

function _firstFileAttachment(message: Message): MessageAttachment | null {
  for (const attachment of message.fileAttachments) {
    return attachment;
  }
  return null;
}

function _visibleRoomCardMatches(room: RoomCard, query: string): boolean {
  if (_contains(room.name, query)) return true;
  if (_contains(room.remarkName, query)) return true;
  if (_contains(room.displayName, query)) return true;
  if (room.rid.trim().toLowerCase() === query) return true;
  return _contains(room_display.roomSidebarSubtitle(room), query);
}

function _contains(value: string | null | undefined, query: string): boolean {
  const trimmed = value?.trim().toLowerCase();
  return trimmed != null && trimmed.includes(query);
}
