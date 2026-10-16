import type { Message } from '../protocol/models';
import * as message_display from './message_display';

export enum RoomMessageHistoryCategory {
  all = 'all',
  links = 'links',
  voice = 'voice',
  stickers = 'stickers',
  images = 'images',
  files = 'files',
  music = 'music',
  system = 'system',
}

/**
 * Port of the Dart extension `RoomMessageHistoryCategoryValue` on
 * [RoomMessageHistoryCategory]: `category.apiValue` →
 * `RoomMessageHistoryCategoryValue.apiValue(category)`.
 */
export const RoomMessageHistoryCategoryValue = {
  apiValue(category: RoomMessageHistoryCategory): string {
    switch (category) {
      case RoomMessageHistoryCategory.all:
        return 'all';
      case RoomMessageHistoryCategory.links:
        return 'links';
      case RoomMessageHistoryCategory.voice:
        return 'voice';
      case RoomMessageHistoryCategory.stickers:
        return 'stickers';
      case RoomMessageHistoryCategory.images:
        return 'images';
      case RoomMessageHistoryCategory.files:
        return 'files';
      case RoomMessageHistoryCategory.music:
        return 'music';
      case RoomMessageHistoryCategory.system:
        return 'system';
    }
  },
};

export function roomMessageHistoryCopyText(message: Message): string {
  const content = message_display.messageCopyText(message).trim();
  if (content.length > 0) return content;
  for (const attachment of message.attachments) {
    const name = attachment.name?.trim();
    if (name != null && name.length > 0) return name;
  }
  return message.type === 'sticker' ? '[表情]' : '[消息]';
}

/** Local midnight of the day [value] falls on (Dart `DateTime(y, m, d)` of `toLocal()`). */
export function roomMessageHistoryDayStart(value: Date): Date {
  // JS `Date` getters already read the local wall clock (Dart `toLocal()`).
  const date = new Date(2000, value.getMonth(), value.getDate());
  date.setFullYear(value.getFullYear(), value.getMonth(), value.getDate());
  return date;
}

export function roomMessageHistoryDayEndExclusive(value: Date): Date {
  // Dart `DateTime.add(Duration(days: 1))` adds exactly 24 hours.
  return new Date(roomMessageHistoryDayStart(value).getTime() + 86_400_000);
}
