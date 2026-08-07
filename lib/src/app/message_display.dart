import '../protocol/models.dart';
import 'file_display.dart';
import 'voice_message_display.dart' as voice_display;

enum MessageContentKind { sticker, voice, files, playlist, musicTrack, text }

const String kSystemMessageType = 'system';
const String kSystemEventRoomMemberJoined = 'room_member_joined';
const String kSystemEventRoomMemberLeft = 'room_member_left';
const String kSystemEventRoomMemberRemoved = 'room_member_removed';
const String kSystemEventLiveJoined = 'live_joined';
const String kSystemEventLiveLeft = 'live_left';
const String kSystemEventRoomRoleChanged = 'room_role_changed';
const String kSystemEventRoomNameChanged = 'room_name_changed';
const String kSystemEventRoomDescriptionChanged = 'room_description_changed';
const String kSystemEventRoomVisibilityChanged = 'room_visibility_changed';
const String kSystemEventRoomJoinPolicyChanged = 'room_join_policy_changed';

class SystemMessageEvent {
  const SystemMessageEvent({
    required this.event,
    required this.message,
    this.user,
    this.actor,
    this.target,
    this.fromRole,
    this.toRole,
    this.oldValue,
    this.newValue,
  });

  final String event;
  final Message message;
  final UserSummary? user;
  final UserSummary? actor;
  final UserSummary? target;
  final String? fromRole;
  final String? toRole;
  final String? oldValue;
  final String? newValue;

  UserSummary get subject => target ?? user ?? message.sender;
  bool get isRoleChange => event == kSystemEventRoomRoleChanged;
}

class MessageTextEdit {
  const MessageTextEdit({required this.text, required this.cursorOffset});

  final String text;
  final int cursorOffset;
}

class StickerMessageDraft {
  const StickerMessageDraft({
    required this.body,
    required this.type,
    required this.attachments,
  });

  final String body;
  final String type;
  final List<MessageAttachment> attachments;
}

Map<String, String> saveMessageDraft({
  required Map<String, String> drafts,
  required String? roomId,
  required String text,
}) {
  if (roomId == null) return drafts;
  return {...drafts, roomId: text};
}

Map<String, String> removeMessageDraft({
  required Map<String, String> drafts,
  required String? roomId,
}) {
  if (roomId == null || !drafts.containsKey(roomId)) return drafts;
  return {
    for (final entry in drafts.entries)
      if (entry.key != roomId) entry.key: entry.value,
  };
}

String messageDraftForRoom({
  required Map<String, String> drafts,
  required String roomId,
}) {
  return drafts[roomId] ?? '';
}

String formatMessageTime(DateTime value) {
  final local = value.toLocal();
  final hour = local.hour.toString().padLeft(2, '0');
  final minute = local.minute.toString().padLeft(2, '0');
  return '$hour:$minute';
}

String formatChatTimestamp(DateTime value, {DateTime? now}) {
  final local = value.toLocal();
  final localNow = (now ?? DateTime.now()).toLocal();
  final today = DateTime(localNow.year, localNow.month, localNow.day);
  final date = DateTime(local.year, local.month, local.day);
  final dayDelta = today.difference(date).inDays;
  final time = formatMessageTime(local);

  if (dayDelta == 0) return time;
  if (dayDelta == 1) return '昨天 $time';
  if (dayDelta == 2) return '前天 $time';
  if (dayDelta >= 3 && dayDelta < 7) {
    return '${_weekdayLabel(local.weekday)} $time';
  }
  if (local.year == localNow.year) {
    return '${local.month}月${local.day}日 $time';
  }
  return '${local.year}年${local.month}月${local.day}日 $time';
}

String formatDetailedChatTimestamp(DateTime value) {
  final local = value.toLocal();
  final time = formatMessageTime(local);
  return '${local.year}年${local.month}月${local.day}日 '
      '${_weekdayLabel(local.weekday)} $time';
}

bool shouldShowChatTimestamp({
  required DateTime current,
  DateTime? previous,
  DateTime? now,
}) {
  if (previous == null) return true;
  final referenceNow = now ?? DateTime.now();
  return formatChatTimestamp(current, now: referenceNow) !=
      formatChatTimestamp(previous, now: referenceNow);
}

String _weekdayLabel(int weekday) {
  return switch (weekday) {
    DateTime.monday => '星期一',
    DateTime.tuesday => '星期二',
    DateTime.wednesday => '星期三',
    DateTime.thursday => '星期四',
    DateTime.friday => '星期五',
    DateTime.saturday => '星期六',
    DateTime.sunday => '星期日',
    _ => '星期日',
  };
}

MessageContentKind messageContentKind(Message message) {
  if (message.stickerAttachment != null) return MessageContentKind.sticker;
  if (voice_display.voiceMessageAttachment(message) != null) {
    return MessageContentKind.voice;
  }
  if (message.fileAttachments.isNotEmpty) return MessageContentKind.files;
  if (message.playlistAttachment != null) return MessageContentKind.playlist;
  if (message.musicTrackAttachment != null) {
    return MessageContentKind.musicTrack;
  }
  return MessageContentKind.text;
}

SystemMessageEvent? systemMessageEvent(Message message) {
  if (message.type != kSystemMessageType) return null;
  MessageAttachment? attachment;
  for (final candidate in message.attachments) {
    if (candidate.type == kSystemMessageType) {
      attachment = candidate;
      break;
    }
  }
  final event = attachment?.event?.trim();
  if (event == null || event.isEmpty) return null;
  final user = attachment?.user;
  final target = attachment?.target ?? user;
  return SystemMessageEvent(
    event: event,
    message: message,
    user: user,
    actor: attachment?.actor,
    target: target,
    fromRole: attachment?.fromRole,
    toRole: attachment?.toRole,
    oldValue: attachment?.oldValue,
    newValue: attachment?.newValue,
  );
}

String systemMessageRoleLabel(String? role) {
  return switch (role) {
    'owner' || 'creator' => '创建者',
    'admin' => '管理员',
    'member' => '成员',
    _ => '成员',
  };
}

String systemMessageRoleVerb(SystemMessageEvent event) {
  final fromRank = _roleRank(event.fromRole);
  final toRank = _roleRank(event.toRole);
  if (toRank > fromRank) return '晋升为';
  return '降职为';
}

bool systemMessageRoleChangeOmitsActor(SystemMessageEvent event) {
  return event.isRoleChange &&
      event.fromRole == 'owner' &&
      event.toRole == 'admin';
}

String messageCopyText(Message message) {
  if (message.isRemoved) return removedMessageCopyText(message);
  final event = systemMessageEvent(message);
  if (event != null) return systemMessageCopyText(event);
  return message.body.trimRight();
}

/// Text written to the system clipboard for a complete chat component.
///
/// The normal [messageCopyText] deliberately stays compact because it is also
/// used by history/search previews. Structured music messages use a dedicated
/// clipboard representation so right-click copy never loses fields that are
/// only visible inside their interactive card.
String messageClipboardText(Message message) {
  if (message.isRemoved) return removedMessageCopyText(message);
  final playlist = message.playlistAttachment?.playlist;
  if (playlist != null) return _musicPlaylistClipboardText(playlist);
  final track = message.musicTrackAttachment?.track;
  if (track != null) return _musicTrackClipboardText(track);
  return messageCopyText(message);
}

String _musicPlaylistClipboardText(SharedMusicPlaylist playlist) {
  final creator = _systemUserLabel(playlist.creator);
  final createdAt = playlist.createdAt?.toLocal().toIso8601String() ?? '未知';
  final lines = <String>[
    '[歌单] ${playlist.name}',
    '歌曲数量：${playlist.itemCount}',
    '创建人：$creator',
    '创建日期：$createdAt',
  ];
  final description = playlist.description.trim();
  if (description.isNotEmpty) lines.add('简介：$description');
  if (playlist.items.isNotEmpty) {
    lines.add('歌曲：');
    for (var index = 0; index < playlist.items.length; index++) {
      final item = playlist.items[index];
      final artists = item.artists
          .map((value) => value.trim())
          .where((value) => value.isNotEmpty)
          .join(' / ');
      lines.add(
        '${index + 1}. ${item.title}'
        '${artists.isEmpty ? '' : ' - $artists'}'
        ' [${item.source}:${item.trackId}]',
      );
    }
  }
  return lines.join('\n');
}

String _musicTrackClipboardText(SharedMusicTrack track) {
  final artists = track.artists
      .map((value) => value.trim())
      .where((value) => value.isNotEmpty)
      .join(' / ');
  final seconds = track.durationMs <= 0 ? 0 : track.durationMs ~/ 1000;
  final duration = seconds <= 0
      ? '未知'
      : '${seconds ~/ 60}:${(seconds % 60).toString().padLeft(2, '0')}';
  return <String>[
    '[歌曲] ${track.title}',
    '歌手：${artists.isEmpty ? '未知' : artists}',
    '时长：$duration',
    '来源：${track.source}',
    '链接标识：${track.trackId}',
  ].join('\n');
}

MessageQuote messageQuoteSnapshot(Message message) {
  final roomName = message.sender.roomDisplayName?.trim();
  final displayName = message.sender.displayName.trim();
  final username = message.sender.username.trim();
  return MessageQuote(
    messageId: message.id,
    senderDisplayName: message.type == kSystemMessageType
        ? ''
        : roomName?.isNotEmpty == true
        ? roomName!
        : displayName.isNotEmpty
        ? displayName
        : username.isNotEmpty
        ? username
        : '用户',
    body: messageQuoteBodySnapshot(message),
    createdAt: message.createdAt,
    previewAttachment: messageQuotePreviewAttachment(message),
  );
}

MessageAttachment? messageQuotePreviewAttachment(Message message) {
  final sticker = message.stickerAttachment;
  if (sticker != null) return sticker;
  for (final attachment in message.fileAttachments) {
    if (isImageMimeType(attachment.asset?.mimeType)) return attachment;
  }
  return null;
}

String messageQuoteBodySnapshot(Message message) {
  final text = messageCopyText(message).trim();
  if (messageContentKind(message) == MessageContentKind.text &&
      text.isNotEmpty) {
    return text;
  }
  final voice = voice_display.voiceMessageAttachment(message);
  if (voice != null) {
    final duration = voice_display.formatVoiceBubbleDuration(
      voice_display.voiceAttachmentDuration(voice),
    );
    return duration.isEmpty ? '[语音]' : '[语音] $duration';
  }
  final sticker = message.stickerAttachment;
  if (sticker != null) return '[表情] ${stickerAttachmentTitle(sticker)}';
  final files = message.fileAttachments.toList(growable: false);
  if (files.isNotEmpty) {
    final nonImage = files.where(
      (attachment) => !isImageMimeType(attachment.asset?.mimeType),
    );
    final preview = nonImage.isNotEmpty ? nonImage.first : files.first;
    final label = nonImage.isEmpty ? '[图片]' : '[文件]';
    return '$label ${fileAttachmentTitle(preview)}';
  }
  return text.isEmpty ? '[消息]' : text;
}

String removedMessageCopyText(Message message) {
  if (message.isForceDeleted) {
    final actor = message.forceDeletedBy;
    if (actor == null) return '消息已被删除';
    return '${_systemUserLabel(actor)} 删除了一条消息';
  }

  final actor = message.recalledBy ?? message.sender;
  if (actor.id == message.sender.id) {
    return '${_systemUserLabel(actor)} 撤回了一条消息';
  }
  return '${_systemUserLabel(actor)} 撤回了一条来自 '
      '${_systemUserLabel(message.sender)} 的消息';
}

String systemMessageCopyText(SystemMessageEvent event) {
  final subject = event.subject;
  switch (event.event) {
    case kSystemEventRoomMemberJoined:
      return '${_systemUserLabel(subject)} 加入了房间';
    case kSystemEventRoomMemberLeft:
      return '${_systemUserLabel(subject)} 离开了房间';
    case kSystemEventRoomMemberRemoved:
      final actor = event.actor;
      if (actor == null) return '${_systemUserLabel(subject)} 被踢出了房间';
      return '${_systemUserLabel(subject)} 被 ${_systemUserLabel(actor)} '
          '踢出了房间';
    case kSystemEventLiveJoined:
      return '${_systemUserLabel(subject)} 进入了语音频道';
    case kSystemEventLiveLeft:
      return '${_systemUserLabel(subject)} 退出了语音频道';
    case kSystemEventRoomRoleChanged:
      final roleLabel = systemMessageRoleLabel(event.toRole);
      final verb = systemMessageRoleVerb(event);
      final omitActor = systemMessageRoleChangeOmitsActor(event);
      final actor = event.actor;
      final subjectPart = _systemUserLabel(subject);
      if (!omitActor && actor != null) {
        return '$subjectPart 被 ${_systemUserLabel(actor)} $verb $roleLabel';
      }
      return '$subjectPart $verb $roleLabel';
    case kSystemEventRoomNameChanged:
      final actor = event.actor ?? event.user;
      final value = _systemChangedValueLabel(event.newValue);
      if (actor == null) return '房间名称 修改为 $value';
      return '房间名称 被 ${_systemUserLabel(actor)} 修改为 $value';
    case kSystemEventRoomDescriptionChanged:
      final actor = event.actor ?? event.user;
      final value = _systemChangedValueLabel(event.newValue);
      if (actor == null) return '房间简介 修改为\n$value';
      return '房间简介 被 ${_systemUserLabel(actor)} 修改为\n$value';
    case kSystemEventRoomVisibilityChanged:
      final actor = event.actor ?? event.user;
      final value = systemMessageVisibilityLabel(event.newValue);
      if (actor == null) return '房间可见性 修改为 $value';
      return '房间可见性 被 ${_systemUserLabel(actor)} 修改为 $value';
    case kSystemEventRoomJoinPolicyChanged:
      final actor = event.actor ?? event.user;
      final value = systemMessageJoinPolicyLabel(event.newValue);
      if (actor == null) return '房间加入方式 修改为 $value';
      return '房间加入方式 被 ${_systemUserLabel(actor)} 修改为 $value';
    default:
      final fallback = event.message.body.trimRight();
      final subjectPart = _systemUserLabel(subject);
      if (fallback.isEmpty) return subjectPart;
      return '$subjectPart $fallback';
  }
}

int _roleRank(String? role) {
  return switch (role) {
    'owner' || 'creator' => 3,
    'admin' => 2,
    'member' => 1,
    _ => 0,
  };
}

String _systemUserLabel(UserSummary user) {
  final roomDisplayName = user.roomDisplayName?.trim();
  if (roomDisplayName != null && roomDisplayName.isNotEmpty) {
    return roomDisplayName;
  }
  final displayName = user.displayName.trim();
  if (displayName.isNotEmpty) return displayName;
  return user.username;
}

String _systemChangedValueLabel(String? value) {
  final normalized = value ?? '';
  if (normalized.isEmpty) return '（空）';
  return normalized;
}

String systemMessageVisibilityLabel(String? visibility) {
  return switch ((visibility ?? '').trim().toLowerCase()) {
    'private' => '私密',
    _ => '公开',
  };
}

String systemMessageJoinPolicyLabel(String? joinPolicy) {
  return switch ((joinPolicy ?? '').trim().toLowerCase()) {
    'open' => '开放',
    'closed' => '关闭',
    _ => '需审批',
  };
}

bool shouldShowFileAttachmentBody({
  required String body,
  required List<MessageAttachment> attachments,
}) {
  final trimmedBody = body.trim();
  if (trimmedBody.isEmpty) return false;
  if (attachments.length != 1) return true;
  return trimmedBody != fileAttachmentTitle(attachments[0]);
}

String stickerAttachmentTitle(MessageAttachment attachment) {
  final explicitName = attachment.name?.trim();
  if (explicitName != null && explicitName.isNotEmpty) return explicitName;
  return '表情';
}

/// A safe filename to seed the sticker image preview's download/save-as. Uses
/// the asset's own filename when present, otherwise derives one from the
/// sticker name and the asset's mime type.
String stickerPreviewFilename(MessageAttachment attachment) {
  final assetFilename = attachment.asset?.filename?.trim();
  if (assetFilename != null && assetFilename.isNotEmpty) return assetFilename;
  final mimeType = attachment.asset?.mimeType ?? 'image/png';
  final ext = imageExtensionForMimeType(mimeType);
  final rawName = stickerAttachmentTitle(attachment);
  final safeName = rawName
      .replaceAll(RegExp(r'[\\/:*?"<>|\s]+'), '_')
      .replaceAll(RegExp(r'_+'), '_');
  final stem = safeName.isEmpty || safeName == '_' ? 'sticker' : safeName;
  return '$stem.$ext';
}

String? messageDeliveryStatusText(Message message) {
  if (message.failed) return '发送失败';
  if (message.pending) return '发送中';
  return null;
}

String? outgoingTextMessageBody(String value) {
  final body = value.trimRight();
  return body.trim().isEmpty ? null : body;
}

StickerMessageDraft stickerMessageDraft(Sticker sticker) {
  return StickerMessageDraft(
    body: '[表情] ${sticker.name}',
    type: 'sticker',
    attachments: [
      MessageAttachment(
        type: 'sticker',
        stickerId: sticker.id,
        name: sticker.name,
        asset: sticker.asset,
      ),
    ],
  );
}

MessageTextEdit insertMessageText({
  required String currentText,
  required String insertedText,
  required int? selectionStart,
  required int? selectionEnd,
}) {
  final length = currentText.length;
  final start = selectionStart == null
      ? length
      : selectionStart.clamp(0, length).toInt();
  final end = selectionEnd == null
      ? length
      : selectionEnd.clamp(0, length).toInt();
  final replaceStart = start < end ? start : end;
  final replaceEnd = start < end ? end : start;
  final nextText = currentText.replaceRange(
    replaceStart,
    replaceEnd,
    insertedText,
  );
  return MessageTextEdit(
    text: nextText,
    cursorOffset: replaceStart + insertedText.length,
  );
}
