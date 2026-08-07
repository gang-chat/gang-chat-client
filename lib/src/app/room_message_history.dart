import '../protocol/models.dart';
import 'message_display.dart' as message_display;

enum RoomMessageHistoryCategory {
  all,
  links,
  voice,
  stickers,
  images,
  files,
  music,
  system,
}

extension RoomMessageHistoryCategoryValue on RoomMessageHistoryCategory {
  String get apiValue => switch (this) {
    RoomMessageHistoryCategory.all => 'all',
    RoomMessageHistoryCategory.links => 'links',
    RoomMessageHistoryCategory.voice => 'voice',
    RoomMessageHistoryCategory.stickers => 'stickers',
    RoomMessageHistoryCategory.images => 'images',
    RoomMessageHistoryCategory.files => 'files',
    RoomMessageHistoryCategory.music => 'music',
    RoomMessageHistoryCategory.system => 'system',
  };
}

String roomMessageHistoryCopyText(Message message) {
  final content = message_display.messageCopyText(message).trim();
  if (content.isNotEmpty) return content;
  for (final attachment in message.attachments) {
    final name = attachment.name?.trim();
    if (name != null && name.isNotEmpty) return name;
  }
  return message.type == 'sticker' ? '[表情]' : '[消息]';
}

DateTime roomMessageHistoryDayStart(DateTime value) {
  final local = value.toLocal();
  return DateTime(local.year, local.month, local.day);
}

DateTime roomMessageHistoryDayEndExclusive(DateTime value) {
  return roomMessageHistoryDayStart(value).add(const Duration(days: 1));
}
