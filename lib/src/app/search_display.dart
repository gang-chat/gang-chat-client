import '../protocol/models.dart';
import 'file_display.dart' as file_display;
import 'room_display.dart' as room_display;
import 'room_notifications.dart' as room_notifications;

enum GlobalSearchCategory { myRooms, publicRooms, userSettings, messages, files }

const globalSearchCategories = [
  GlobalSearchCategory.myRooms,
  GlobalSearchCategory.publicRooms,
  GlobalSearchCategory.messages,
  GlobalSearchCategory.files,
];

const superuserGlobalSearchCategories = [
  GlobalSearchCategory.myRooms,
  GlobalSearchCategory.userSettings,
  GlobalSearchCategory.messages,
  GlobalSearchCategory.files,
];

String globalSearchCategoryKey(GlobalSearchCategory category) {
  return switch (category) {
    GlobalSearchCategory.myRooms => 'my_rooms',
    GlobalSearchCategory.publicRooms => 'public_rooms',
    GlobalSearchCategory.userSettings => 'user_settings',
    GlobalSearchCategory.messages => 'messages',
    GlobalSearchCategory.files => 'files',
  };
}

String? globalSearchCursorForCategory(
  GlobalSearchCursors cursors,
  GlobalSearchCategory category,
) {
  return switch (category) {
    GlobalSearchCategory.myRooms => cursors.myRooms,
    GlobalSearchCategory.publicRooms => cursors.publicRooms,
    GlobalSearchCategory.userSettings => cursors.userSettings,
    GlobalSearchCategory.messages => cursors.messages,
    GlobalSearchCategory.files => cursors.files,
  };
}

GlobalSearchCursors globalSearchCursorsForCategories(
  GlobalSearchCursors cursors,
  Iterable<GlobalSearchCategory> categories,
) {
  String? myRooms;
  String? publicRooms;
  String? userSettings;
  String? messages;
  String? files;
  for (final category in categories) {
    switch (category) {
      case GlobalSearchCategory.myRooms:
        myRooms = cursors.myRooms;
      case GlobalSearchCategory.publicRooms:
        publicRooms = cursors.publicRooms;
      case GlobalSearchCategory.userSettings:
        userSettings = cursors.userSettings;
      case GlobalSearchCategory.messages:
        messages = cursors.messages;
      case GlobalSearchCategory.files:
        files = cursors.files;
    }
  }
  return GlobalSearchCursors(
    myRooms: myRooms,
    publicRooms: publicRooms,
    userSettings: userSettings,
    messages: messages,
    files: files,
  );
}

GlobalSearchResults globalSearchResultsByAppendingPage({
  required GlobalSearchResults current,
  required GlobalSearchResults page,
  required Iterable<GlobalSearchCategory> categories,
}) {
  var appendMyRooms = false;
  var appendPublicRooms = false;
  var appendUserSettings = false;
  var appendMessages = false;
  var appendFiles = false;
  for (final category in categories) {
    switch (category) {
      case GlobalSearchCategory.myRooms:
        appendMyRooms = true;
      case GlobalSearchCategory.publicRooms:
        appendPublicRooms = true;
      case GlobalSearchCategory.userSettings:
        appendUserSettings = true;
      case GlobalSearchCategory.messages:
        appendMessages = true;
      case GlobalSearchCategory.files:
        appendFiles = true;
    }
  }

  return GlobalSearchResults(
    myRooms: appendMyRooms
        ? [...current.myRooms, ...page.myRooms]
        : current.myRooms,
    publicRooms: appendPublicRooms
        ? [...current.publicRooms, ...page.publicRooms]
        : current.publicRooms,
    userSettings: appendUserSettings
        ? [...current.userSettings, ...page.userSettings]
        : current.userSettings,
    messages: appendMessages
        ? [...current.messages, ...page.messages]
        : current.messages,
    files: appendFiles ? [...current.files, ...page.files] : current.files,
    nextCursors: GlobalSearchCursors(
      myRooms: appendMyRooms
          ? page.nextCursors.myRooms
          : current.nextCursors.myRooms,
      publicRooms: appendPublicRooms
          ? page.nextCursors.publicRooms
          : current.nextCursors.publicRooms,
      userSettings: appendUserSettings
          ? page.nextCursors.userSettings
          : current.nextCursors.userSettings,
      messages: appendMessages
          ? page.nextCursors.messages
          : current.nextCursors.messages,
      files: appendFiles ? page.nextCursors.files : current.nextCursors.files,
    ),
    totalCounts: GlobalSearchCounts(
      myRooms: appendMyRooms
          ? page.totalCounts.myRooms ?? current.totalCounts.myRooms
          : current.totalCounts.myRooms,
      publicRooms: appendPublicRooms
          ? page.totalCounts.publicRooms ?? current.totalCounts.publicRooms
          : current.totalCounts.publicRooms,
      userSettings: appendUserSettings
          ? page.totalCounts.userSettings ?? current.totalCounts.userSettings
          : current.totalCounts.userSettings,
      messages: appendMessages
          ? page.totalCounts.messages ?? current.totalCounts.messages
          : current.totalCounts.messages,
      files: appendFiles
          ? page.totalCounts.files ?? current.totalCounts.files
          : current.totalCounts.files,
    ),
  );
}

bool hasGlobalSearchQuery(String query) {
  return query.trim().isNotEmpty;
}

String globalSearchCategoryLabel(GlobalSearchCategory category) {
  return switch (category) {
    GlobalSearchCategory.myRooms => '我的房间',
    GlobalSearchCategory.publicRooms => '公开房间',
    GlobalSearchCategory.userSettings => '用户设置',
    GlobalSearchCategory.messages => '聊天记录',
    GlobalSearchCategory.files => '聊天文件',
  };
}

int globalSearchCategoryCount(
  GlobalSearchResults? results,
  GlobalSearchCategory category,
) {
  if (results == null) return 0;
  return switch (category) {
    GlobalSearchCategory.myRooms =>
      results.totalCounts.myRooms ?? results.myRooms.length,
    GlobalSearchCategory.publicRooms =>
      results.totalCounts.publicRooms ?? results.publicRooms.length,
    GlobalSearchCategory.userSettings =>
      results.totalCounts.userSettings ?? results.userSettings.length,
    GlobalSearchCategory.messages =>
      results.totalCounts.messages ?? results.messages.length,
    GlobalSearchCategory.files =>
      results.totalCounts.files ?? results.files.length,
  };
}

bool globalSearchHasResults(GlobalSearchResults? results) {
  if (results == null) return false;
  return results.myRooms.isNotEmpty ||
      results.publicRooms.isNotEmpty ||
      results.userSettings.isNotEmpty ||
      results.messages.isNotEmpty ||
      results.files.isNotEmpty;
}

GlobalSearchResults globalSearchResultsForView(
  GlobalSearchResults results, {
  required String query,
}) {
  return GlobalSearchResults(
    myRooms: visibleMyRoomSearchResults(rooms: results.myRooms, query: query),
    publicRooms: results.publicRooms,
    userSettings: results.userSettings,
    messages: results.messages,
    files: results.files,
    nextCursors: results.nextCursors,
    totalCounts: results.totalCounts,
  );
}

List<RoomCard> visibleMyRoomSearchResults({
  required Iterable<RoomCard> rooms,
  required String query,
}) {
  final normalizedQuery = query.trim().toLowerCase();
  if (normalizedQuery.isEmpty) return List.unmodifiable(rooms);
  return [
    for (final room in rooms)
      if (_visibleRoomCardMatches(room, normalizedQuery)) room,
  ];
}

List<RoomCard> sidebarRoomsForSearch({
  required List<RoomCard> rooms,
  required String query,
  required GlobalSearchCategory? activeCategory,
  required GlobalSearchResults? results,
}) {
  if (!hasGlobalSearchQuery(query)) return rooms;
  if (activeCategory != GlobalSearchCategory.myRooms) return rooms;
  return results?.myRooms ?? const [];
}

RoomCard roomCardFromSearchContext(
  SearchRoomContext room, {
  required DateTime updatedAt,
}) {
  return RoomCard(
    id: room.id,
    name: room.name,
    rid: room.rid,
    remarkName: room.remarkName,
    avatarUrl: room.avatarUrl,
    defaultAvatarKey: room.defaultAvatarKey,
    memberCount: 0,
    liveParticipantCount: 0,
    liveAvatarPreview: const [],
    lastMessage: null,
    unreadCount: 0,
    updatedAt: updatedAt,
  );
}

String globalSearchMessageTitle(MessageSearchResult result) {
  final roomName = result.room.displayName.trim();
  return roomName.isNotEmpty ? roomName : '房间';
}

String globalSearchMessageSubtitle(MessageSearchResult result) {
  final senderName = globalSearchMessageSenderName(result);
  final body = result.message.body.trim();
  if (body.isEmpty) return senderName;
  return '$senderName · $body';
}

String globalSearchMessageSenderName(MessageSearchResult result) {
  final roomDisplayName = result.message.sender.roomDisplayName?.trim();
  if (roomDisplayName != null && roomDisplayName.isNotEmpty) {
    return roomDisplayName;
  }
  final displayName = result.message.sender.displayName.trim();
  if (displayName.isNotEmpty) return displayName;
  final username = result.message.sender.username.trim();
  return username.isNotEmpty ? username : '用户';
}

String globalSearchFileTitle(MessageSearchResult result) {
  final attachment = _firstFileAttachment(result.message);
  if (attachment != null) return file_display.fileAttachmentTitle(attachment);
  final body = result.message.body.trim();
  return body.isNotEmpty ? body : 'file';
}

String globalSearchFileSubtitle(MessageSearchResult result) {
  final meta = file_display.fileAttachmentMeta(
    _firstFileAttachment(result.message)?.asset,
  );
  return meta.isNotEmpty ? meta : '文件';
}

String globalSearchResultTimeLabel(MessageSearchResult result) {
  return room_notifications.roomInviteTimestampLabel(result.message.createdAt);
}

MessageAttachment? _firstFileAttachment(Message message) {
  for (final attachment in message.fileAttachments) {
    return attachment;
  }
  return null;
}

bool _visibleRoomCardMatches(RoomCard room, String query) {
  if (_contains(room.name, query)) return true;
  if (_contains(room.remarkName, query)) return true;
  if (_contains(room.displayName, query)) return true;
  if (room.rid.trim().toLowerCase() == query) return true;
  return _contains(room_display.roomSidebarSubtitle(room), query);
}

bool _contains(String? value, String query) {
  final trimmed = value?.trim().toLowerCase();
  return trimmed != null && trimmed.contains(query);
}
