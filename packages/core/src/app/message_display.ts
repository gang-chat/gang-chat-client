import { isJsonMap, type JsonMap } from '../lib/json';
import {
  Message,
  MessageAttachment,
  MessageQuote,
  type SharedMusicPlaylist,
  type SharedMusicTrack,
  type Sticker,
  type UserSummary,
} from '../protocol/models';
import { fileAttachmentTitle, imageExtensionForMimeType, isImageMimeType } from './file_display';
import * as voice_display from './voice_message_display';

export enum MessageContentKind {
  sticker = 'sticker',
  voice = 'voice',
  files = 'files',
  playlist = 'playlist',
  musicTrack = 'musicTrack',
  text = 'text',
}

export const kSystemMessageType = 'system';
export const kSystemEventRoomMemberJoined = 'room_member_joined';
export const kSystemEventRoomMemberLeft = 'room_member_left';
export const kSystemEventRoomMemberRemoved = 'room_member_removed';
export const kSystemEventLiveJoined = 'live_joined';
export const kSystemEventLiveLeft = 'live_left';
export const kSystemEventRoomRoleChanged = 'room_role_changed';
export const kSystemEventRoomNameChanged = 'room_name_changed';
export const kSystemEventRoomDescriptionChanged = 'room_description_changed';
export const kSystemEventRoomVisibilityChanged = 'room_visibility_changed';
export const kSystemEventRoomJoinPolicyChanged = 'room_join_policy_changed';

export class SystemMessageEvent {
  constructor(opts: {
    event: string;
    message: Message;
    user?: UserSummary | null;
    actor?: UserSummary | null;
    target?: UserSummary | null;
    fromRole?: string | null;
    toRole?: string | null;
    oldValue?: string | null;
    newValue?: string | null;
  }) {
    this.event = opts.event;
    this.message = opts.message;
    this.user = opts.user ?? null;
    this.actor = opts.actor ?? null;
    this.target = opts.target ?? null;
    this.fromRole = opts.fromRole ?? null;
    this.toRole = opts.toRole ?? null;
    this.oldValue = opts.oldValue ?? null;
    this.newValue = opts.newValue ?? null;
  }

  readonly event: string;
  readonly message: Message;
  readonly user: UserSummary | null;
  readonly actor: UserSummary | null;
  readonly target: UserSummary | null;
  readonly fromRole: string | null;
  readonly toRole: string | null;
  readonly oldValue: string | null;
  readonly newValue: string | null;

  get subject(): UserSummary {
    return this.target ?? this.user ?? this.message.sender;
  }

  get isRoleChange(): boolean {
    return this.event === kSystemEventRoomRoleChanged;
  }
}

export class MessageTextEdit {
  constructor(opts: { text: string; cursorOffset: number }) {
    this.text = opts.text;
    this.cursorOffset = opts.cursorOffset;
  }

  readonly text: string;
  readonly cursorOffset: number;
}

export class StickerMessageDraft {
  constructor(opts: { body: string; type: string; attachments: MessageAttachment[] }) {
    this.body = opts.body;
    this.type = opts.type;
    this.attachments = opts.attachments;
  }

  readonly body: string;
  readonly type: string;
  readonly attachments: MessageAttachment[];
}

export function saveMessageDraft(opts: {
  drafts: Record<string, string>;
  roomId: string | null;
  text: string;
}): Record<string, string> {
  const { drafts, roomId, text } = opts;
  if (roomId == null) return drafts;
  return { ...drafts, [roomId]: text };
}

export function removeMessageDraft(opts: {
  drafts: Record<string, string>;
  roomId: string | null;
}): Record<string, string> {
  const { drafts, roomId } = opts;
  if (roomId == null || !Object.prototype.hasOwnProperty.call(drafts, roomId)) return drafts;
  const next: Record<string, string> = {};
  for (const [key, value] of Object.entries(drafts)) {
    if (key !== roomId) next[key] = value;
  }
  return next;
}

export function messageDraftForRoom(opts: { drafts: Record<string, string>; roomId: string }): string {
  const { drafts, roomId } = opts;
  return Object.prototype.hasOwnProperty.call(drafts, roomId) ? drafts[roomId]! : '';
}

export function formatMessageTime(value: Date): string {
  // JS `Date` getters already read the local wall clock (Dart `toLocal()`).
  const hour = String(value.getHours()).padStart(2, '0');
  const minute = String(value.getMinutes()).padStart(2, '0');
  return `${hour}:${minute}`;
}

export function formatChatTimestamp(value: Date, opts: { now?: Date | null } = {}): string {
  const local = value;
  const localNow = opts.now ?? new Date();
  const today = _localDate(localNow.getFullYear(), localNow.getMonth(), localNow.getDate());
  const date = _localDate(local.getFullYear(), local.getMonth(), local.getDate());
  const dayDelta = _inDays(today, date);
  const time = formatMessageTime(local);

  if (dayDelta === 0) return time;
  if (dayDelta === 1) return `昨天 ${time}`;
  if (dayDelta === 2) return `前天 ${time}`;
  if (dayDelta >= 3 && dayDelta < 7) {
    return `${_weekdayLabel(_dartWeekday(local))} ${time}`;
  }
  if (local.getFullYear() === localNow.getFullYear()) {
    return `${local.getMonth() + 1}月${local.getDate()}日 ${time}`;
  }
  return `${local.getFullYear()}年${local.getMonth() + 1}月${local.getDate()}日 ${time}`;
}

export function formatDetailedChatTimestamp(value: Date): string {
  const local = value;
  const time = formatMessageTime(local);
  return (
    `${local.getFullYear()}年${local.getMonth() + 1}月${local.getDate()}日 ` +
    `${_weekdayLabel(_dartWeekday(local))} ${time}`
  );
}

export function shouldShowChatTimestamp(opts: {
  current: Date;
  previous?: Date | null;
  now?: Date | null;
}): boolean {
  const { current } = opts;
  const previous = opts.previous ?? null;
  if (previous == null) return true;
  const referenceNow = opts.now ?? new Date();
  return (
    formatChatTimestamp(current, { now: referenceNow }) !== formatChatTimestamp(previous, { now: referenceNow })
  );
}

function _weekdayLabel(weekday: number): string {
  switch (weekday) {
    case 1:
      return '星期一';
    case 2:
      return '星期二';
    case 3:
      return '星期三';
    case 4:
      return '星期四';
    case 5:
      return '星期五';
    case 6:
      return '星期六';
    case 7:
      return '星期日';
    default:
      return '星期日';
  }
}

export function messageContentKind(message: Message): MessageContentKind {
  if (message.stickerAttachment != null) return MessageContentKind.sticker;
  if (voice_display.voiceMessageAttachment(message) != null) {
    return MessageContentKind.voice;
  }
  if (_firstOf(message.fileAttachments) != null) return MessageContentKind.files;
  if (message.playlistAttachment != null) return MessageContentKind.playlist;
  if (message.musicTrackAttachment != null) {
    return MessageContentKind.musicTrack;
  }
  return MessageContentKind.text;
}

export function systemMessageEvent(message: Message): SystemMessageEvent | null {
  if (message.type !== kSystemMessageType) return null;
  let attachment: MessageAttachment | null = null;
  for (const candidate of message.attachments) {
    if (candidate.type === kSystemMessageType) {
      attachment = candidate;
      break;
    }
  }
  const event = attachment?.event?.trim();
  if (event == null || event.length === 0) return null;
  const user = attachment?.user ?? null;
  const target = attachment?.target ?? user;
  return new SystemMessageEvent({
    event,
    message,
    user,
    actor: attachment?.actor,
    target,
    fromRole: attachment?.fromRole,
    toRole: attachment?.toRole,
    oldValue: attachment?.oldValue,
    newValue: attachment?.newValue,
  });
}

export function systemMessageRoleLabel(role: string | null): string {
  switch (role) {
    case 'owner':
    case 'creator':
      return '创建者';
    case 'admin':
      return '管理员';
    case 'member':
      return '成员';
    default:
      return '成员';
  }
}

export function systemMessageRoleVerb(event: SystemMessageEvent): string {
  const fromRank = _roleRank(event.fromRole);
  const toRank = _roleRank(event.toRole);
  if (toRank > fromRank) return '晋升为';
  return '降职为';
}

export function systemMessageRoleChangeOmitsActor(event: SystemMessageEvent): boolean {
  return event.isRoleChange && event.fromRole === 'owner' && event.toRole === 'admin';
}

export function messageCopyText(message: Message): string {
  if (message.isRemoved) return removedMessageCopyText(message);
  const event = systemMessageEvent(message);
  if (event != null) return systemMessageCopyText(event);
  return message.body.trimEnd();
}

/**
 * Text written to the system clipboard for a complete chat component.
 *
 * The normal [messageCopyText] deliberately stays compact because it is also
 * used by history/search previews. Structured music messages use a dedicated
 * clipboard representation so right-click copy never loses fields that are
 * only visible inside their interactive card.
 */
export function messageClipboardText(message: Message): string {
  if (message.isRemoved) return removedMessageCopyText(message);
  const playlist = message.playlistAttachment?.playlist ?? null;
  if (playlist != null) return _musicPlaylistClipboardText(playlist);
  const track = message.musicTrackAttachment?.track ?? null;
  if (track != null) return _musicTrackClipboardText(track);
  return messageCopyText(message);
}

/**
 * Clipboard text for a music component that remains readable in other apps
 * while carrying an application-only immutable snapshot for Gang Chat.
 *
 * Unicode tag characters are intentionally non-rendering. Unlike an
 * in-memory "last copied message" cache, this envelope survives focus
 * changes and cross-platform system-clipboard round trips. The server still
 * validates [Message.id] when the component is sent, so the snapshot cannot
 * grant access to a message the user may not reference.
 */
export function messageComponentClipboardText(message: Message): string {
  const visible = messageClipboardText(message);
  if (!_isClipboardMusicComponent(message)) return visible;
  const payload = JSON.stringify({
    version: 1,
    message: {
      id: message.id,
      room_id: message.roomId,
      client_message_id: message.clientMessageId,
      type: message.type,
      body: message.body,
      created_at: message.createdAt.toISOString(),
      sender: _clipboardUserSummaryJson(message.sender),
      attachments: message.attachments.map((attachment) => attachment.toJson()),
    },
  });
  const encoded = _base64UrlEncode(_utf8Encoder.encode(payload)).replace(/=/g, '');
  const tagRunes: number[] = [_clipboardEnvelopeStart];
  for (let index = 0; index < encoded.length; index++) {
    tagRunes.push(_clipboardTagBase + encoded.charCodeAt(index));
  }
  tagRunes.push(_clipboardEnvelopeEnd);
  return `${visible}${_stringFromCodePoints(tagRunes)}`;
}

/** Restores a copied music component directly from clipboard text. */
export function messageComponentFromClipboardText(clipboardText: string | null): Message | null {
  if (clipboardText == null || clipboardText.length === 0) return null;
  const encoded = _clipboardEnvelopePayload(clipboardText);
  if (encoded == null || encoded.length === 0 || encoded.length > 512 * 1024) {
    return null;
  }
  try {
    const padded = encoded.padEnd(Math.trunc((encoded.length + 3) / 4) * 4, '=');
    const decoded: unknown = JSON.parse(_utf8StrictDecoder.decode(_base64UrlDecode(padded)));
    if (!isJsonMap(decoded) || decoded['version'] !== 1) return null;
    const messageJson = decoded['message'];
    if (!isJsonMap(messageJson)) return null;
    const message = Message.fromJson(messageJson);
    if (message.id.trim().length === 0 || !_isClipboardMusicComponent(message)) {
      return null;
    }
    if (
      _normalizedClipboardText(_withoutClipboardEnvelope(clipboardText)) !==
      _normalizedClipboardText(messageClipboardText(message))
    ) {
      return null;
    }
    return message;
  } catch {
    return null;
  }
}

/**
 * Whether the current plain-text clipboard still represents [message].
 *
 * Windows clipboard providers may expose line endings as CRLF even when the
 * app originally wrote LF. A terminal line break may also be appended by a
 * clipboard bridge. Normalizing only those transport differences preserves
 * stale-copy protection without degrading a structured component to text.
 */
export function messageClipboardTextMatches(message: Message, clipboardText: string | null): boolean {
  if (clipboardText == null) return false;
  return (
    _normalizedClipboardText(_withoutClipboardEnvelope(clipboardText)) ===
    _normalizedClipboardText(messageClipboardText(message))
  );
}

const _clipboardEnvelopeStart = 0xe0001;
const _clipboardEnvelopeEnd = 0xe007f;
const _clipboardTagBase = 0xe0000;

function _isClipboardMusicComponent(message: Message): boolean {
  return (
    !message.isRemoved &&
    !message.pending &&
    (message.playlistAttachment != null || message.musicTrackAttachment != null)
  );
}

function _clipboardUserSummaryJson(user: UserSummary): JsonMap {
  return {
    id: user.id,
    uid: user.uid,
    username: user.username,
    display_name: user.displayName,
    avatar_url: user.avatarUrl,
    default_avatar_key: user.defaultAvatarKey,
    room_display_name: user.roomDisplayName,
    room_role: user.roomRole,
    is_superuser: user.isSuperuser,
    is_online: user.isOnline,
    is_deleted: user.isDeleted,
    is_suspended: user.isSuspended,
  };
}

/** Dart `String.runes`: code points, with lone surrogates kept as-is. */
function _runes(value: string): number[] {
  return Array.from(value, (char) => char.codePointAt(0)!);
}

function _clipboardEnvelopePayload(value: string): string | null {
  const runes = _runes(value);
  const start = runes.lastIndexOf(_clipboardEnvelopeStart);
  if (start < 0) return null;
  const end = runes.indexOf(_clipboardEnvelopeEnd, start + 1);
  if (end <= start + 1) return null;
  const codeUnits: number[] = [];
  for (const rune of runes.slice(start + 1, end)) {
    const codeUnit = rune - _clipboardTagBase;
    if (codeUnit < 0x20 || codeUnit > 0x7e) return null;
    codeUnits.push(codeUnit);
  }
  return _stringFromCodePoints(codeUnits);
}

function _withoutClipboardEnvelope(value: string): string {
  const runes = _runes(value);
  const start = runes.lastIndexOf(_clipboardEnvelopeStart);
  if (start < 0) return value;
  const end = runes.indexOf(_clipboardEnvelopeEnd, start + 1);
  if (end < 0) return value;
  return _stringFromCodePoints([...runes.slice(0, start), ...runes.slice(end + 1)]);
}

function _normalizedClipboardText(value: string): string {
  return value.replaceAll('\r\n', '\n').replaceAll('\r', '\n').trimEnd();
}

function _musicPlaylistClipboardText(playlist: SharedMusicPlaylist): string {
  const creator = _systemUserLabel(playlist.creator);
  const createdAt = playlist.createdAt == null ? '未知' : _localIso8601String(playlist.createdAt);
  const lines: string[] = [
    `[歌单] ${playlist.name}`,
    `歌曲数量：${playlist.itemCount}`,
    `创建人：${creator}`,
    `创建日期：${createdAt}`,
  ];
  const description = playlist.description.trim();
  if (description.length > 0) lines.push(`简介：${description}`);
  if (playlist.items.length > 0) {
    lines.push('歌曲：');
    for (let index = 0; index < playlist.items.length; index++) {
      const item = playlist.items[index]!;
      const artists = item.artists
        .map((value) => value.trim())
        .filter((value) => value.length > 0)
        .join(' / ');
      lines.push(
        `${index + 1}. ${item.title}` + `${artists.length === 0 ? '' : ` - ${artists}`}` + ` [${item.source}:${item.trackId}]`,
      );
    }
  }
  return lines.join('\n');
}

function _musicTrackClipboardText(track: SharedMusicTrack): string {
  const artists = track.artists
    .map((value) => value.trim())
    .filter((value) => value.length > 0)
    .join(' / ');
  const seconds = track.durationMs <= 0 ? 0 : Math.trunc(track.durationMs / 1000);
  const duration =
    seconds <= 0 ? '未知' : `${Math.trunc(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
  return [
    `[歌曲] ${track.title}`,
    `歌手：${artists.length === 0 ? '未知' : artists}`,
    `时长：${duration}`,
    `来源：${track.source}`,
    `链接标识：${track.trackId}`,
  ].join('\n');
}

export function messageQuoteSnapshot(message: Message): MessageQuote {
  const roomName = message.sender.roomDisplayName?.trim();
  const displayName = message.sender.displayName.trim();
  const username = message.sender.username.trim();
  return new MessageQuote({
    messageId: message.id,
    senderDisplayName:
      message.type === kSystemMessageType
        ? ''
        : roomName != null && roomName.length > 0
          ? roomName
          : displayName.length > 0
            ? displayName
            : username.length > 0
              ? username
              : '用户',
    body: messageQuoteBodySnapshot(message),
    createdAt: message.createdAt,
    previewAttachment: messageQuotePreviewAttachment(message),
  });
}

export function messageQuotePreviewAttachment(message: Message): MessageAttachment | null {
  const sticker = message.stickerAttachment;
  if (sticker != null) return sticker;
  for (const attachment of message.fileAttachments) {
    if (isImageMimeType(attachment.asset?.mimeType ?? null)) return attachment;
  }
  return null;
}

export function messageQuoteBodySnapshot(message: Message): string {
  const text = messageCopyText(message).trim();
  if (messageContentKind(message) === MessageContentKind.text && text.length > 0) {
    return text;
  }
  const voice = voice_display.voiceMessageAttachment(message);
  if (voice != null) {
    const duration = voice_display.formatVoiceBubbleDuration(voice_display.voiceAttachmentDuration(voice));
    return duration.length === 0 ? '[语音]' : `[语音] ${duration}`;
  }
  const sticker = message.stickerAttachment;
  if (sticker != null) return `[表情] ${stickerAttachmentTitle(sticker)}`;
  const files = [...message.fileAttachments];
  if (files.length > 0) {
    const nonImage = files.filter((attachment) => !isImageMimeType(attachment.asset?.mimeType ?? null));
    const preview = nonImage.length > 0 ? nonImage[0]! : files[0]!;
    const label = nonImage.length === 0 ? '[图片]' : '[文件]';
    return `${label} ${fileAttachmentTitle(preview)}`;
  }
  return text.length === 0 ? '[消息]' : text;
}

export function removedMessageCopyText(message: Message): string {
  if (message.isForceDeleted) {
    const actor = message.forceDeletedBy;
    if (actor == null) return '消息已被删除';
    return `${_systemUserLabel(actor)} 删除了一条消息`;
  }

  const actor = message.recalledBy ?? message.sender;
  if (actor.id === message.sender.id) {
    return `${_systemUserLabel(actor)} 撤回了一条消息`;
  }
  return `${_systemUserLabel(actor)} 撤回了一条来自 ` + `${_systemUserLabel(message.sender)} 的消息`;
}

export function systemMessageCopyText(event: SystemMessageEvent): string {
  const subject = event.subject;
  switch (event.event) {
    case kSystemEventRoomMemberJoined:
      return `${_systemUserLabel(subject)} 加入了房间`;
    case kSystemEventRoomMemberLeft:
      return `${_systemUserLabel(subject)} 离开了房间`;
    case kSystemEventRoomMemberRemoved: {
      const actor = event.actor;
      if (actor == null) return `${_systemUserLabel(subject)} 被踢出了房间`;
      return `${_systemUserLabel(subject)} 被 ${_systemUserLabel(actor)} ` + '踢出了房间';
    }
    case kSystemEventLiveJoined:
      return `${_systemUserLabel(subject)} 进入了语音频道`;
    case kSystemEventLiveLeft:
      return `${_systemUserLabel(subject)} 退出了语音频道`;
    case kSystemEventRoomRoleChanged: {
      const roleLabel = systemMessageRoleLabel(event.toRole);
      const verb = systemMessageRoleVerb(event);
      const omitActor = systemMessageRoleChangeOmitsActor(event);
      const actor = event.actor;
      const subjectPart = _systemUserLabel(subject);
      if (!omitActor && actor != null) {
        return `${subjectPart} 被 ${_systemUserLabel(actor)} ${verb} ${roleLabel}`;
      }
      return `${subjectPart} ${verb} ${roleLabel}`;
    }
    case kSystemEventRoomNameChanged: {
      const actor = event.actor ?? event.user;
      const value = _systemChangedValueLabel(event.newValue);
      if (actor == null) return `房间名称 修改为 ${value}`;
      return `房间名称 被 ${_systemUserLabel(actor)} 修改为 ${value}`;
    }
    case kSystemEventRoomDescriptionChanged: {
      const actor = event.actor ?? event.user;
      const value = _systemChangedValueLabel(event.newValue);
      if (actor == null) return `房间简介 修改为\n${value}`;
      return `房间简介 被 ${_systemUserLabel(actor)} 修改为\n${value}`;
    }
    case kSystemEventRoomVisibilityChanged: {
      const actor = event.actor ?? event.user;
      const value = systemMessageVisibilityLabel(event.newValue);
      if (actor == null) return `房间可见性 修改为 ${value}`;
      return `房间可见性 被 ${_systemUserLabel(actor)} 修改为 ${value}`;
    }
    case kSystemEventRoomJoinPolicyChanged: {
      const actor = event.actor ?? event.user;
      const value = systemMessageJoinPolicyLabel(event.newValue);
      if (actor == null) return `房间加入方式 修改为 ${value}`;
      return `房间加入方式 被 ${_systemUserLabel(actor)} 修改为 ${value}`;
    }
    default: {
      const fallback = event.message.body.trimEnd();
      const subjectPart = _systemUserLabel(subject);
      if (fallback.length === 0) return subjectPart;
      return `${subjectPart} ${fallback}`;
    }
  }
}

function _roleRank(role: string | null): number {
  switch (role) {
    case 'owner':
    case 'creator':
      return 3;
    case 'admin':
      return 2;
    case 'member':
      return 1;
    default:
      return 0;
  }
}

function _systemUserLabel(user: UserSummary): string {
  const roomDisplayName = user.roomDisplayName?.trim();
  if (roomDisplayName != null && roomDisplayName.length > 0) {
    return roomDisplayName;
  }
  const displayName = user.displayName.trim();
  if (displayName.length > 0) return displayName;
  return user.username;
}

function _systemChangedValueLabel(value: string | null): string {
  const normalized = value ?? '';
  if (normalized.length === 0) return '（空）';
  return normalized;
}

export function systemMessageVisibilityLabel(visibility: string | null): string {
  switch ((visibility ?? '').trim().toLowerCase()) {
    case 'private':
      return '私密';
    default:
      return '公开';
  }
}

export function systemMessageJoinPolicyLabel(joinPolicy: string | null): string {
  switch ((joinPolicy ?? '').trim().toLowerCase()) {
    case 'open':
      return '开放';
    case 'closed':
      return '关闭';
    default:
      return '需审批';
  }
}

export function shouldShowFileAttachmentBody(opts: { body: string; attachments: MessageAttachment[] }): boolean {
  const { body, attachments } = opts;
  const trimmedBody = body.trim();
  if (trimmedBody.length === 0) return false;
  if (attachments.length !== 1) return true;
  return trimmedBody !== fileAttachmentTitle(attachments[0]!);
}

export function stickerAttachmentTitle(attachment: MessageAttachment): string {
  const explicitName = attachment.name?.trim();
  if (explicitName != null && explicitName.length > 0) return explicitName;
  return '表情';
}

/**
 * A safe filename to seed the sticker image preview's download/save-as. Uses
 * the asset's own filename when present, otherwise derives one from the
 * sticker name and the asset's mime type.
 */
export function stickerPreviewFilename(attachment: MessageAttachment): string {
  const assetFilename = attachment.asset?.filename?.trim();
  if (assetFilename != null && assetFilename.length > 0) return assetFilename;
  const mimeType = attachment.asset?.mimeType ?? 'image/png';
  const ext = imageExtensionForMimeType(mimeType);
  const rawName = stickerAttachmentTitle(attachment);
  const safeName = rawName.replace(/[\\/:*?"<>|\s]+/g, '_').replace(/_+/g, '_');
  const stem = safeName.length === 0 || safeName === '_' ? 'sticker' : safeName;
  return `${stem}.${ext}`;
}

export function messageDeliveryStatusText(message: Message): string | null {
  if (message.failed) return '发送失败';
  if (message.pending) return '发送中';
  return null;
}

export function outgoingTextMessageBody(value: string): string | null {
  const body = value.trimEnd();
  return body.trim().length === 0 ? null : body;
}

export function stickerMessageDraft(sticker: Sticker): StickerMessageDraft {
  return new StickerMessageDraft({
    body: `[表情] ${sticker.name}`,
    type: 'sticker',
    attachments: [
      new MessageAttachment({
        type: 'sticker',
        stickerId: sticker.id,
        name: sticker.name,
        asset: sticker.asset,
      }),
    ],
  });
}

export function insertMessageText(opts: {
  currentText: string;
  insertedText: string;
  selectionStart: number | null;
  selectionEnd: number | null;
}): MessageTextEdit {
  const { currentText, insertedText, selectionStart, selectionEnd } = opts;
  const length = currentText.length;
  const start = selectionStart == null ? length : _clamp(selectionStart, 0, length);
  const end = selectionEnd == null ? length : _clamp(selectionEnd, 0, length);
  const replaceStart = start < end ? start : end;
  const replaceEnd = start < end ? end : start;
  const nextText = currentText.substring(0, replaceStart) + insertedText + currentText.substring(replaceEnd);
  return new MessageTextEdit({
    text: nextText,
    cursorOffset: replaceStart + insertedText.length,
  });
}

function _clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(Math.trunc(value), min), max);
}

function _firstOf<T>(values: Iterable<T>): T | null {
  for (const value of values) return value;
  return null;
}

/** Local-time `DateTime(year, month, day)`; [monthIndex] is 0-based like JS. */
function _localDate(year: number, monthIndex: number, day: number): Date {
  const date = new Date(2000, monthIndex, day);
  date.setFullYear(year, monthIndex, day);
  return date;
}

/** Dart `Duration.inDays` of `later.difference(earlier)` (truncates toward zero). */
function _inDays(later: Date, earlier: Date): number {
  return Math.trunc((later.getTime() - earlier.getTime()) / 86_400_000);
}

/** Dart `DateTime.weekday`: Monday = 1 … Sunday = 7. */
function _dartWeekday(value: Date): number {
  const day = value.getDay();
  return day === 0 ? 7 : day;
}

/** Dart `value.toLocal().toIso8601String()`: `yyyy-MM-ddTHH:mm:ss.mmm`, no zone suffix. */
function _localIso8601String(value: Date): string {
  const year = value.getFullYear();
  const yearText =
    year >= 0 && year <= 9999
      ? String(year).padStart(4, '0')
      : `${year < 0 ? '-' : '+'}${String(Math.abs(year)).padStart(6, '0')}`;
  const two = (n: number) => String(n).padStart(2, '0');
  return (
    `${yearText}-${two(value.getMonth() + 1)}-${two(value.getDate())}` +
    `T${two(value.getHours())}:${two(value.getMinutes())}:${two(value.getSeconds())}` +
    `.${String(value.getMilliseconds()).padStart(3, '0')}`
  );
}

function _stringFromCodePoints(points: readonly number[]): string {
  // Chunked so very large clipboard payloads stay under engine argument limits.
  let out = '';
  for (let index = 0; index < points.length; index += 8192) {
    out += String.fromCodePoint(...points.slice(index, index + 8192));
  }
  return out;
}

const _utf8Encoder = new TextEncoder();
const _utf8StrictDecoder = new TextDecoder('utf-8', { fatal: true });
const _base64UrlAlphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';

/** Dart `base64Url.encode` (padded). */
function _base64UrlEncode(bytes: Uint8Array): string {
  let out = '';
  let index = 0;
  for (; index + 2 < bytes.length; index += 3) {
    const n = (bytes[index]! << 16) | (bytes[index + 1]! << 8) | bytes[index + 2]!;
    out +=
      _base64UrlAlphabet[(n >> 18) & 63]! +
      _base64UrlAlphabet[(n >> 12) & 63]! +
      _base64UrlAlphabet[(n >> 6) & 63]! +
      _base64UrlAlphabet[n & 63]!;
  }
  const remaining = bytes.length - index;
  if (remaining === 1) {
    const n = bytes[index]! << 16;
    out += _base64UrlAlphabet[(n >> 18) & 63]! + _base64UrlAlphabet[(n >> 12) & 63]! + '==';
  } else if (remaining === 2) {
    const n = (bytes[index]! << 16) | (bytes[index + 1]! << 8);
    out +=
      _base64UrlAlphabet[(n >> 18) & 63]! +
      _base64UrlAlphabet[(n >> 12) & 63]! +
      _base64UrlAlphabet[(n >> 6) & 63]! +
      '=';
  }
  return out;
}

/** Dart `base64Url.decode` of a padded string; throws on malformed input. */
function _base64UrlDecode(value: string): Uint8Array {
  if (value.length % 4 !== 0) throw new Error('Invalid base64 length');
  let padding = 0;
  if (value.endsWith('==')) padding = 2;
  else if (value.endsWith('=')) padding = 1;
  const out = new Uint8Array((value.length / 4) * 3 - padding);
  let outIndex = 0;
  for (let index = 0; index < value.length; index += 4) {
    let n = 0;
    for (let offset = 0; offset < 4; offset++) {
      const char = value[index + offset]!;
      let digit: number;
      if (char === '=') {
        if (index + 4 !== value.length || offset < 4 - padding) throw new Error('Invalid base64 padding');
        digit = 0;
      } else {
        // Dart's base64 decoder accepts both the URL-safe and standard alphabets.
        digit = char === '+' ? 62 : char === '/' ? 63 : _base64UrlAlphabet.indexOf(char);
        if (digit < 0) throw new Error('Invalid base64 character');
      }
      n = (n << 6) | digit;
    }
    if (outIndex < out.length) out[outIndex++] = (n >> 16) & 255;
    if (outIndex < out.length) out[outIndex++] = (n >> 8) & 255;
    if (outIndex < out.length) out[outIndex++] = n & 255;
  }
  return out;
}
