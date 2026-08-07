import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/message_display.dart';
import 'package:client/src/protocol/models.dart';

void main() {
  test('message draft helpers save restore and remove per room text', () {
    const original = {'room_1': 'hello'};

    expect(
      saveMessageDraft(drafts: original, roomId: 'room_2', text: 'draft'),
      {'room_1': 'hello', 'room_2': 'draft'},
    );
    expect(
      saveMessageDraft(drafts: original, roomId: null, text: 'ignored'),
      same(original),
    );
    expect(messageDraftForRoom(drafts: original, roomId: 'room_1'), 'hello');
    expect(messageDraftForRoom(drafts: original, roomId: 'missing'), '');

    expect(removeMessageDraft(drafts: original, roomId: 'room_1'), isEmpty);
    expect(
      removeMessageDraft(drafts: original, roomId: 'missing'),
      same(original),
    );
    expect(removeMessageDraft(drafts: original, roomId: null), same(original));
  });

  test('formatMessageTime formats local wall clock time', () {
    expect(formatMessageTime(DateTime(2026, 6, 4, 7, 5)), '07:05');
    expect(formatMessageTime(DateTime(2026, 6, 4, 23, 59)), '23:59');
  });

  test('formatChatTimestamp follows chat relative date labels', () {
    final now = DateTime(2026, 6, 12, 10);

    expect(formatChatTimestamp(DateTime(2026, 6, 12, 7, 5), now: now), '07:05');
    expect(
      formatChatTimestamp(DateTime(2026, 6, 11, 7, 5), now: now),
      '昨天 07:05',
    );
    expect(
      formatChatTimestamp(DateTime(2026, 6, 10, 7, 5), now: now),
      '前天 07:05',
    );
    expect(
      formatChatTimestamp(DateTime(2026, 6, 9, 7, 5), now: now),
      '星期二 07:05',
    );
    expect(
      formatChatTimestamp(DateTime(2026, 5, 31, 7, 5), now: now),
      '5月31日 07:05',
    );
    expect(
      formatChatTimestamp(DateTime(2025, 12, 31, 7, 5), now: now),
      '2025年12月31日 07:05',
    );
  });

  test('formatDetailedChatTimestamp always includes full date and weekday', () {
    expect(
      formatDetailedChatTimestamp(DateTime(2026, 6, 12, 7, 5)),
      '2026年6月12日 星期五 07:05',
    );
    expect(
      formatDetailedChatTimestamp(DateTime(2025, 12, 31, 23, 59)),
      '2025年12月31日 星期三 23:59',
    );
  });

  test('shouldShowChatTimestamp shares identical timestamp labels', () {
    final now = DateTime(2026, 6, 12, 10);

    expect(
      shouldShowChatTimestamp(
        current: DateTime(2026, 6, 12, 7, 5),
        previous: null,
        now: now,
      ),
      isTrue,
    );
    expect(
      shouldShowChatTimestamp(
        current: DateTime(2026, 6, 12, 7, 5, 45),
        previous: DateTime(2026, 6, 12, 7, 5, 5),
        now: now,
      ),
      isFalse,
    );
    expect(
      shouldShowChatTimestamp(
        current: DateTime(2026, 6, 12, 7, 6),
        previous: DateTime(2026, 6, 12, 7, 5),
        now: now,
      ),
      isTrue,
    );
  });

  test(
    'messageContentKind recognizes playlists alongside existing message types',
    () {
      expect(messageContentKind(_message()), MessageContentKind.text);
      expect(
        messageContentKind(
          _message(
            type: 'audio',
            attachments: const [
              MessageAttachment(
                type: 'audio',
                name: 'voice_1.m4a',
                durationMs: 15000,
                asset: UploadedAsset(
                  id: 'asset_voice',
                  url: '/uploads/voice_1.m4a',
                  thumbnailUrl: null,
                  mimeType: 'audio/mp4',
                ),
              ),
            ],
          ),
        ),
        MessageContentKind.voice,
      );
      expect(
        messageContentKind(
          _message(
            type: 'file',
            attachments: const [MessageAttachment(type: 'file', name: 'a.pdf')],
          ),
        ),
        MessageContentKind.files,
      );
      expect(
        messageContentKind(
          _message(type: 'sticker', attachments: [_stickerAttachment()]),
        ),
        MessageContentKind.sticker,
      );
      expect(
        messageContentKind(
          _message(
            type: 'playlist',
            attachments: const [
              MessageAttachment(
                type: 'playlist',
                playlistId: 'playlist_1',
                playlist: SharedMusicPlaylist(
                  id: 'playlist_1',
                  name: '夜晚',
                  description: '',
                  itemCount: 0,
                  createdAt: null,
                  creator: UserSummary(
                    id: 'user_1',
                    username: 'alice',
                    displayName: 'Alice',
                    avatarUrl: null,
                    defaultAvatarKey: 'blue-1',
                  ),
                  items: [],
                ),
              ),
            ],
          ),
        ),
        MessageContentKind.playlist,
      );
    },
  );

  test('systemMessageEvent parses structured role-change messages', () {
    const actor = UserSummary(
      id: 'user_actor',
      username: 'owner',
      displayName: 'Owner',
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
    );
    const target = UserSummary(
      id: 'user_target',
      username: 'logan',
      displayName: 'Logan',
      avatarUrl: null,
      defaultAvatarKey: 'green-2',
    );
    final event = systemMessageEvent(
      _message(
        type: 'system',
        body: '降职为管理员',
        attachments: const [
          MessageAttachment(
            type: 'system',
            event: kSystemEventRoomRoleChanged,
            actor: actor,
            target: target,
            fromRole: 'owner',
            toRole: 'admin',
          ),
        ],
      ),
    );

    expect(event, isNotNull);
    expect(event!.event, kSystemEventRoomRoleChanged);
    expect(event.subject.id, 'user_target');
    expect(event.actor?.id, 'user_actor');
    expect(systemMessageRoleLabel(event.toRole), '管理员');
    expect(systemMessageRoleVerb(event), '降职为');
    expect(systemMessageRoleChangeOmitsActor(event), isTrue);
  });

  test('systemMessageEvent parses room profile change values', () {
    const actor = UserSummary(
      id: 'user_actor',
      username: 'owner',
      displayName: 'Owner',
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
    );
    final event = systemMessageEvent(
      _message(
        type: 'system',
        body: '房间名称被Owner修改为New Room',
        attachments: const [
          MessageAttachment(
            type: 'system',
            event: kSystemEventRoomNameChanged,
            user: actor,
            actor: actor,
            oldValue: 'Old Room',
            newValue: 'New Room',
          ),
        ],
      ),
    );

    expect(event, isNotNull);
    expect(event!.event, kSystemEventRoomNameChanged);
    expect(event.subject.id, 'user_actor');
    expect(event.actor?.id, 'user_actor');
    expect(event.oldValue, 'Old Room');
    expect(event.newValue, 'New Room');
  });

  test('messageCopyText renders structured system message text', () {
    const actor = UserSummary(
      id: 'user_actor',
      username: 'owner',
      displayName: 'Owner',
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
      roomDisplayName: '房内Owner',
    );
    final nameChanged = _message(
      type: 'system',
      body: '房间名称修改为New Room',
      attachments: const [
        MessageAttachment(
          type: 'system',
          event: kSystemEventRoomNameChanged,
          actor: actor,
          oldValue: 'Old Room',
          newValue: 'New Room',
        ),
      ],
    );
    final descriptionChanged = _message(
      type: 'system',
      body: '房间简介修改为Hello',
      attachments: const [
        MessageAttachment(
          type: 'system',
          event: kSystemEventRoomDescriptionChanged,
          actor: actor,
          oldValue: 'Old',
          newValue: 'Hello',
        ),
      ],
    );
    final visibilityChanged = _message(
      type: 'system',
      body: '房间可见性修改为私密',
      attachments: const [
        MessageAttachment(
          type: 'system',
          event: kSystemEventRoomVisibilityChanged,
          actor: actor,
          oldValue: 'public',
          newValue: 'private',
        ),
      ],
    );
    final joinPolicyChanged = _message(
      type: 'system',
      body: '房间加入方式修改为关闭',
      attachments: const [
        MessageAttachment(
          type: 'system',
          event: kSystemEventRoomJoinPolicyChanged,
          actor: actor,
          oldValue: 'approval_required',
          newValue: 'closed',
        ),
      ],
    );

    expect(messageCopyText(nameChanged), '房间名称 被 房内Owner 修改为 New Room');
    expect(messageCopyText(descriptionChanged), '房间简介 被 房内Owner 修改为\nHello');
    expect(messageCopyText(visibilityChanged), '房间可见性 被 房内Owner 修改为 私密');
    expect(messageCopyText(joinPolicyChanged), '房间加入方式 被 房内Owner 修改为 关闭');
  });

  test('messageCopyText renders removed message placeholders', () {
    const sender = UserSummary(
      id: 'user_sender',
      username: 'logan',
      displayName: 'Logan',
      avatarUrl: null,
      defaultAvatarKey: 'green-2',
      roomDisplayName: '房内Logan',
    );
    const admin = UserSummary(
      id: 'user_admin',
      username: 'admin',
      displayName: 'Admin',
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
    );

    expect(
      messageCopyText(
        _message(sender: sender, isRecalled: true, recalledBy: admin),
      ),
      'Admin 撤回了一条来自 房内Logan 的消息',
    );
    expect(
      messageCopyText(
        _message(sender: sender, isForceDeleted: true, forceDeletedBy: admin),
      ),
      'Admin 删除了一条消息',
    );
  });

  test('messageQuoteSnapshot omits the sender for system messages', () {
    final systemQuote = messageQuoteSnapshot(
      _message(type: kSystemMessageType, body: 'joined the room'),
    );
    final userQuote = messageQuoteSnapshot(_message());

    expect(systemQuote.senderDisplayName, isEmpty);
    expect(userQuote.senderDisplayName, 'Logan');
  });

  test('shouldShowFileAttachmentBody hides duplicate single-file body', () {
    const attachment = MessageAttachment(type: 'file', name: 'report.pdf');

    expect(
      shouldShowFileAttachmentBody(
        body: ' report.pdf ',
        attachments: [attachment],
      ),
      isFalse,
    );
    expect(
      shouldShowFileAttachmentBody(
        body: 'see attached',
        attachments: [attachment],
      ),
      isTrue,
    );
    expect(
      shouldShowFileAttachmentBody(
        body: 'report.pdf',
        attachments: [
          attachment,
          const MessageAttachment(type: 'file', name: 'other.pdf'),
        ],
      ),
      isTrue,
    );
    expect(
      shouldShowFileAttachmentBody(body: ' ', attachments: [attachment]),
      isFalse,
    );
  });

  test(
    'stickerAttachmentTitle trims names and falls back to sticker label',
    () {
      expect(
        stickerAttachmentTitle(
          const MessageAttachment(type: 'sticker', name: ' wave '),
        ),
        'wave',
      );
      expect(
        stickerAttachmentTitle(
          const MessageAttachment(type: 'sticker', name: '   '),
        ),
        '表情',
      );
      expect(
        stickerAttachmentTitle(const MessageAttachment(type: 'sticker')),
        '表情',
      );
    },
  );

  test('messageDeliveryStatusText reflects pending and failed states', () {
    expect(messageDeliveryStatusText(_message()), isNull);
    expect(messageDeliveryStatusText(_message(pending: true)), '发送中');
    expect(
      messageDeliveryStatusText(_message(pending: true, failed: true)),
      '发送失败',
    );
  });

  test(
    'outgoingTextMessageBody trims trailing whitespace and skips blanks',
    () {
      expect(outgoingTextMessageBody(' hello  \n '), ' hello');
      expect(outgoingTextMessageBody('   \n'), isNull);
    },
  );

  test('stickerMessageDraft builds reusable sticker payload data', () {
    final sticker = _sticker();
    final draft = stickerMessageDraft(sticker);

    expect(draft.body, '[表情] wave');
    expect(draft.type, 'sticker');
    expect(draft.attachments, hasLength(1));
    expect(draft.attachments.single.type, 'sticker');
    expect(draft.attachments.single.stickerId, 'sticker_1');
    expect(draft.attachments.single.name, 'wave');
    expect(draft.attachments.single.asset, same(sticker.asset));
  });

  test('insertMessageText replaces selection and returns cursor offset', () {
    var edit = insertMessageText(
      currentText: 'hello',
      insertedText: '!',
      selectionStart: null,
      selectionEnd: null,
    );
    expect(edit.text, 'hello!');
    expect(edit.cursorOffset, 6);

    edit = insertMessageText(
      currentText: 'hello world',
      insertedText: 'there',
      selectionStart: 6,
      selectionEnd: 11,
    );
    expect(edit.text, 'hello there');
    expect(edit.cursorOffset, 11);

    edit = insertMessageText(
      currentText: 'hello world',
      insertedText: 'there',
      selectionStart: 11,
      selectionEnd: 6,
    );
    expect(edit.text, 'hello there');
    expect(edit.cursorOffset, 11);

    edit = insertMessageText(
      currentText: 'hello',
      insertedText: 'x',
      selectionStart: -20,
      selectionEnd: 20,
    );
    expect(edit.text, 'x');
    expect(edit.cursorOffset, 1);
  });
}

Message _message({
  String type = 'text',
  String body = 'hello',
  List<MessageAttachment> attachments = const [],
  UserSummary? sender,
  bool isRecalled = false,
  UserSummary? recalledBy,
  bool isForceDeleted = false,
  UserSummary? forceDeletedBy,
  bool pending = false,
  bool failed = false,
}) {
  return Message(
    id: 'message_1',
    roomId: 'room_1',
    sender:
        sender ??
        const UserSummary(
          id: 'user_1',
          username: 'logan',
          displayName: 'Logan',
          avatarUrl: null,
          defaultAvatarKey: 'blue-3',
        ),
    clientMessageId: 'client_1',
    type: type,
    body: body,
    createdAt: DateTime.utc(2026, 6, 4),
    attachments: attachments,
    isRecalled: isRecalled,
    recalledBy: recalledBy,
    isForceDeleted: isForceDeleted,
    forceDeletedBy: forceDeletedBy,
    pending: pending,
    failed: failed,
  );
}

MessageAttachment _stickerAttachment() {
  return MessageAttachment(
    type: 'sticker',
    asset: UploadedAsset(
      id: 'asset_1',
      url: '/sticker.webp',
      thumbnailUrl: null,
      mimeType: 'image/webp',
    ),
  );
}

Sticker _sticker() {
  return Sticker(
    id: 'sticker_1',
    name: 'wave',
    sortOrder: 10,
    asset: UploadedAsset(
      id: 'asset_1',
      url: '/sticker.webp',
      thumbnailUrl: null,
      mimeType: 'image/webp',
    ),
  );
}
