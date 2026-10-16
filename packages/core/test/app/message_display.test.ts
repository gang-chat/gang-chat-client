import { expect, test } from 'vitest';

import {
  formatChatTimestamp,
  formatDetailedChatTimestamp,
  formatMessageTime,
  insertMessageText,
  kSystemEventRoomDescriptionChanged,
  kSystemEventRoomJoinPolicyChanged,
  kSystemEventRoomNameChanged,
  kSystemEventRoomRoleChanged,
  kSystemEventRoomVisibilityChanged,
  kSystemMessageType,
  MessageContentKind,
  messageClipboardText,
  messageClipboardTextMatches,
  messageComponentClipboardText,
  messageComponentFromClipboardText,
  messageContentKind,
  messageCopyText,
  messageDeliveryStatusText,
  messageDraftForRoom,
  messageQuoteSnapshot,
  outgoingTextMessageBody,
  removeMessageDraft,
  saveMessageDraft,
  shouldShowChatTimestamp,
  shouldShowFileAttachmentBody,
  stickerAttachmentTitle,
  stickerMessageDraft,
  systemMessageEvent,
  systemMessageRoleChangeOmitsActor,
  systemMessageRoleLabel,
  systemMessageRoleVerb,
} from '../../src/app/message_display';
import {
  Message,
  MessageAttachment,
  PersonalMusicPlaylistItem,
  SharedMusicPlaylist,
  SharedMusicTrack,
  Sticker,
  UploadedAsset,
  UserSummary,
} from '../../src/protocol/models';

test('message draft helpers save restore and remove per room text', () => {
  const original = { room_1: 'hello' };

  expect(saveMessageDraft({ drafts: original, roomId: 'room_2', text: 'draft' })).toEqual({
    room_1: 'hello',
    room_2: 'draft',
  });
  expect(saveMessageDraft({ drafts: original, roomId: null, text: 'ignored' })).toBe(original);
  expect(messageDraftForRoom({ drafts: original, roomId: 'room_1' })).toBe('hello');
  expect(messageDraftForRoom({ drafts: original, roomId: 'missing' })).toBe('');

  expect(removeMessageDraft({ drafts: original, roomId: 'room_1' })).toEqual({});
  expect(removeMessageDraft({ drafts: original, roomId: 'missing' })).toBe(original);
  expect(removeMessageDraft({ drafts: original, roomId: null })).toBe(original);
});

test('formatMessageTime formats local wall clock time', () => {
  expect(formatMessageTime(new Date(2026, 5, 4, 7, 5))).toBe('07:05');
  expect(formatMessageTime(new Date(2026, 5, 4, 23, 59))).toBe('23:59');
});

test('formatChatTimestamp follows chat relative date labels', () => {
  const now = new Date(2026, 5, 12, 10);

  expect(formatChatTimestamp(new Date(2026, 5, 12, 7, 5), { now })).toBe('07:05');
  expect(formatChatTimestamp(new Date(2026, 5, 11, 7, 5), { now })).toBe('昨天 07:05');
  expect(formatChatTimestamp(new Date(2026, 5, 10, 7, 5), { now })).toBe('前天 07:05');
  expect(formatChatTimestamp(new Date(2026, 5, 9, 7, 5), { now })).toBe('星期二 07:05');
  expect(formatChatTimestamp(new Date(2026, 4, 31, 7, 5), { now })).toBe('5月31日 07:05');
  expect(formatChatTimestamp(new Date(2025, 11, 31, 7, 5), { now })).toBe('2025年12月31日 07:05');
});

test('formatDetailedChatTimestamp always includes full date and weekday', () => {
  expect(formatDetailedChatTimestamp(new Date(2026, 5, 12, 7, 5))).toBe('2026年6月12日 星期五 07:05');
  expect(formatDetailedChatTimestamp(new Date(2025, 11, 31, 23, 59))).toBe('2025年12月31日 星期三 23:59');
});

test('shouldShowChatTimestamp shares identical timestamp labels', () => {
  const now = new Date(2026, 5, 12, 10);

  expect(shouldShowChatTimestamp({ current: new Date(2026, 5, 12, 7, 5), previous: null, now })).toBe(true);
  expect(
    shouldShowChatTimestamp({
      current: new Date(2026, 5, 12, 7, 5, 45),
      previous: new Date(2026, 5, 12, 7, 5, 5),
      now,
    }),
  ).toBe(false);
  expect(
    shouldShowChatTimestamp({
      current: new Date(2026, 5, 12, 7, 6),
      previous: new Date(2026, 5, 12, 7, 5),
      now,
    }),
  ).toBe(true);
});

test('messageContentKind recognizes playlists alongside existing message types', () => {
  expect(messageContentKind(_message())).toBe(MessageContentKind.text);
  expect(
    messageContentKind(
      _message({
        type: 'audio',
        attachments: [
          new MessageAttachment({
            type: 'audio',
            name: 'voice_1.m4a',
            durationMs: 15000,
            asset: new UploadedAsset({
              id: 'asset_voice',
              url: '/uploads/voice_1.m4a',
              thumbnailUrl: null,
              mimeType: 'audio/mp4',
            }),
          }),
        ],
      }),
    ),
  ).toBe(MessageContentKind.voice);
  expect(
    messageContentKind(
      _message({
        type: 'file',
        attachments: [new MessageAttachment({ type: 'file', name: 'a.pdf' })],
      }),
    ),
  ).toBe(MessageContentKind.files);
  expect(messageContentKind(_message({ type: 'sticker', attachments: [_stickerAttachment()] }))).toBe(
    MessageContentKind.sticker,
  );
  expect(
    messageContentKind(
      _message({
        type: 'playlist',
        attachments: [
          new MessageAttachment({
            type: 'playlist',
            playlistId: 'playlist_1',
            playlist: new SharedMusicPlaylist({
              id: 'playlist_1',
              name: '夜晚',
              description: '',
              itemCount: 0,
              createdAt: null,
              creator: new UserSummary({
                id: 'user_1',
                username: 'alice',
                displayName: 'Alice',
                avatarUrl: null,
                defaultAvatarKey: 'blue-1',
              }),
              items: [],
            }),
          }),
        ],
      }),
    ),
  ).toBe(MessageContentKind.playlist);
  expect(
    messageContentKind(
      _message({
        type: 'music_track',
        attachments: [
          new MessageAttachment({
            type: 'music_track',
            track: new SharedMusicTrack({
              id: 'item_1',
              playlistId: 'playlist_1',
              trackId: 'track_1',
              source: 'netease',
              title: '歌曲',
              artists: ['歌手'],
              durationMs: 180000,
            }),
          }),
        ],
      }),
    ),
  ).toBe(MessageContentKind.musicTrack);
});

test('systemMessageEvent parses structured role-change messages', () => {
  const actor = _user({ id: 'user_actor', username: 'owner', displayName: 'Owner' });
  const target = _user({ id: 'user_target', username: 'logan', displayName: 'Logan', defaultAvatarKey: 'green-2' });
  const event = systemMessageEvent(
    _message({
      type: 'system',
      body: '降职为管理员',
      attachments: [
        new MessageAttachment({
          type: 'system',
          event: kSystemEventRoomRoleChanged,
          actor,
          target,
          fromRole: 'owner',
          toRole: 'admin',
        }),
      ],
    }),
  );

  expect(event).not.toBeNull();
  expect(event!.event).toBe(kSystemEventRoomRoleChanged);
  expect(event!.subject.id).toBe('user_target');
  expect(event!.actor?.id).toBe('user_actor');
  expect(systemMessageRoleLabel(event!.toRole)).toBe('管理员');
  expect(systemMessageRoleVerb(event!)).toBe('降职为');
  expect(systemMessageRoleChangeOmitsActor(event!)).toBe(true);
});

test('systemMessageEvent parses room profile change values', () => {
  const actor = _user({ id: 'user_actor', username: 'owner', displayName: 'Owner' });
  const event = systemMessageEvent(
    _message({
      type: 'system',
      body: '房间名称被Owner修改为New Room',
      attachments: [
        new MessageAttachment({
          type: 'system',
          event: kSystemEventRoomNameChanged,
          user: actor,
          actor,
          oldValue: 'Old Room',
          newValue: 'New Room',
        }),
      ],
    }),
  );

  expect(event).not.toBeNull();
  expect(event!.event).toBe(kSystemEventRoomNameChanged);
  expect(event!.subject.id).toBe('user_actor');
  expect(event!.actor?.id).toBe('user_actor');
  expect(event!.oldValue).toBe('Old Room');
  expect(event!.newValue).toBe('New Room');
});

test('messageCopyText renders structured system message text', () => {
  const actor = _user({ id: 'user_actor', username: 'owner', displayName: 'Owner', roomDisplayName: '房内Owner' });
  const systemMessage = (body: string, event: string, oldValue: string, newValue: string) =>
    _message({
      type: 'system',
      body,
      attachments: [new MessageAttachment({ type: 'system', event, actor, oldValue, newValue })],
    });

  expect(messageCopyText(systemMessage('房间名称修改为New Room', kSystemEventRoomNameChanged, 'Old Room', 'New Room'))).toBe(
    '房间名称 被 房内Owner 修改为 New Room',
  );
  expect(messageCopyText(systemMessage('房间简介修改为Hello', kSystemEventRoomDescriptionChanged, 'Old', 'Hello'))).toBe(
    '房间简介 被 房内Owner 修改为\nHello',
  );
  expect(
    messageCopyText(systemMessage('房间可见性修改为私密', kSystemEventRoomVisibilityChanged, 'public', 'private')),
  ).toBe('房间可见性 被 房内Owner 修改为 私密');
  expect(
    messageCopyText(
      systemMessage('房间加入方式修改为关闭', kSystemEventRoomJoinPolicyChanged, 'approval_required', 'closed'),
    ),
  ).toBe('房间加入方式 被 房内Owner 修改为 关闭');
});

test('messageCopyText renders removed message placeholders', () => {
  const sender = _user({
    id: 'user_sender',
    username: 'logan',
    displayName: 'Logan',
    defaultAvatarKey: 'green-2',
    roomDisplayName: '房内Logan',
  });
  const admin = _user({ id: 'user_admin', username: 'admin', displayName: 'Admin' });

  expect(messageCopyText(_message({ sender, isRecalled: true, recalledBy: admin }))).toBe(
    'Admin 撤回了一条来自 房内Logan 的消息',
  );
  expect(messageCopyText(_message({ sender, isForceDeleted: true, forceDeletedBy: admin }))).toBe('Admin 删除了一条消息');
});

test('messageClipboardText preserves complete music components', () => {
  const creator = _user({ id: 'user_creator', username: 'alice', displayName: 'Alice', roomDisplayName: '房内 Alice' });
  const playlistMessage = _message({
    type: 'playlist',
    body: '[歌单] 夜晚',
    attachments: [
      new MessageAttachment({
        type: 'playlist',
        playlist: new SharedMusicPlaylist({
          id: 'playlist_1',
          name: '夜晚',
          description: '适合夜里',
          itemCount: 1,
          createdAt: new Date(Date.UTC(2026, 7, 7, 9, 30)),
          creator,
          items: [
            new PersonalMusicPlaylistItem({
              id: 'item_1',
              playlistId: 'playlist_1',
              trackId: 'track_1',
              source: 'netease',
              title: '晴天',
              artists: ['周杰伦'],
              durationMs: 269000,
              sortOrder: 10,
              createdAt: null,
            }),
          ],
        }),
      }),
    ],
  });
  const playlistText = messageClipboardText(playlistMessage);
  expect(playlistText).toContain('[歌单] 夜晚');
  expect(playlistText).toContain('歌曲数量：1');
  expect(playlistText).toContain('创建人：房内 Alice');
  expect(playlistText).toContain('1. 晴天 - 周杰伦 [netease:track_1]');
  expect(messageClipboardTextMatches(playlistMessage, playlistText)).toBe(true);
  expect(messageClipboardTextMatches(playlistMessage, `${playlistText.replaceAll('\n', '\r\n')}\r\n`)).toBe(true);
  expect(messageClipboardTextMatches(playlistMessage, `${playlistText} 已修改`)).toBe(false);

  const trackText = messageClipboardText(
    _message({
      type: 'music_track',
      body: '[歌曲] 晴天',
      attachments: [
        new MessageAttachment({
          type: 'music_track',
          track: new SharedMusicTrack({
            id: 'item_1',
            playlistId: 'playlist_1',
            trackId: 'track_1',
            source: 'netease',
            title: '晴天',
            artists: ['周杰伦'],
            durationMs: 269000,
          }),
        }),
      ],
    }),
  );
  expect(trackText).toContain('[歌曲] 晴天');
  expect(trackText).toContain('歌手：周杰伦');
  expect(trackText).toContain('时长：4:29');
  expect(trackText).toContain('链接标识：track_1');

  const encodedText = messageComponentClipboardText(playlistMessage);
  expect(encodedText.startsWith(playlistText)).toBe(true);
  expect(messageClipboardTextMatches(playlistMessage, encodedText)).toBe(true);
  const restored = messageComponentFromClipboardText(encodedText);
  expect(restored).not.toBeNull();
  expect(restored!.id).toBe(playlistMessage.id);
  expect(restored!.roomId).toBe(playlistMessage.roomId);
  expect(restored!.playlistAttachment?.playlist?.name).toBe('夜晚');
  expect(restored!.playlistAttachment?.playlist?.items).toHaveLength(1);
  expect(messageComponentFromClipboardText(`${playlistText} 已修改`)).toBeNull();
  expect(messageComponentFromClipboardText(`篡改的可见内容${encodedText}`)).toBeNull();
});

test('messageQuoteSnapshot omits the sender for system messages', () => {
  const systemQuote = messageQuoteSnapshot(_message({ type: kSystemMessageType, body: 'joined the room' }));
  const userQuote = messageQuoteSnapshot(_message());

  expect(systemQuote.senderDisplayName).toBe('');
  expect(userQuote.senderDisplayName).toBe('Logan');
});

test('shouldShowFileAttachmentBody hides duplicate single-file body', () => {
  const attachment = new MessageAttachment({ type: 'file', name: 'report.pdf' });

  expect(shouldShowFileAttachmentBody({ body: ' report.pdf ', attachments: [attachment] })).toBe(false);
  expect(shouldShowFileAttachmentBody({ body: 'see attached', attachments: [attachment] })).toBe(true);
  expect(
    shouldShowFileAttachmentBody({
      body: 'report.pdf',
      attachments: [attachment, new MessageAttachment({ type: 'file', name: 'other.pdf' })],
    }),
  ).toBe(true);
  expect(shouldShowFileAttachmentBody({ body: ' ', attachments: [attachment] })).toBe(false);
});

test('stickerAttachmentTitle trims names and falls back to sticker label', () => {
  expect(stickerAttachmentTitle(new MessageAttachment({ type: 'sticker', name: ' wave ' }))).toBe('wave');
  expect(stickerAttachmentTitle(new MessageAttachment({ type: 'sticker', name: '   ' }))).toBe('表情');
  expect(stickerAttachmentTitle(new MessageAttachment({ type: 'sticker' }))).toBe('表情');
});

test('messageDeliveryStatusText reflects pending and failed states', () => {
  expect(messageDeliveryStatusText(_message())).toBeNull();
  expect(messageDeliveryStatusText(_message({ pending: true }))).toBe('发送中');
  expect(messageDeliveryStatusText(_message({ pending: true, failed: true }))).toBe('发送失败');
});

test('outgoingTextMessageBody trims trailing whitespace and skips blanks', () => {
  expect(outgoingTextMessageBody(' hello  \n ')).toBe(' hello');
  expect(outgoingTextMessageBody('   \n')).toBeNull();
});

test('stickerMessageDraft builds reusable sticker payload data', () => {
  const sticker = _sticker();
  const draft = stickerMessageDraft(sticker);

  expect(draft.body).toBe('[表情] wave');
  expect(draft.type).toBe('sticker');
  expect(draft.attachments).toHaveLength(1);
  expect(draft.attachments[0]!.type).toBe('sticker');
  expect(draft.attachments[0]!.stickerId).toBe('sticker_1');
  expect(draft.attachments[0]!.name).toBe('wave');
  expect(draft.attachments[0]!.asset).toBe(sticker.asset);
});

test('insertMessageText replaces selection and returns cursor offset', () => {
  let edit = insertMessageText({ currentText: 'hello', insertedText: '!', selectionStart: null, selectionEnd: null });
  expect(edit.text).toBe('hello!');
  expect(edit.cursorOffset).toBe(6);

  edit = insertMessageText({ currentText: 'hello world', insertedText: 'there', selectionStart: 6, selectionEnd: 11 });
  expect(edit.text).toBe('hello there');
  expect(edit.cursorOffset).toBe(11);

  edit = insertMessageText({ currentText: 'hello world', insertedText: 'there', selectionStart: 11, selectionEnd: 6 });
  expect(edit.text).toBe('hello there');
  expect(edit.cursorOffset).toBe(11);

  edit = insertMessageText({ currentText: 'hello', insertedText: 'x', selectionStart: -20, selectionEnd: 20 });
  expect(edit.text).toBe('x');
  expect(edit.cursorOffset).toBe(1);
});

function _user(opts: {
  id: string;
  username: string;
  displayName: string;
  defaultAvatarKey?: string;
  roomDisplayName?: string | null;
}): UserSummary {
  return new UserSummary({
    id: opts.id,
    username: opts.username,
    displayName: opts.displayName,
    avatarUrl: null,
    defaultAvatarKey: opts.defaultAvatarKey ?? 'blue-3',
    roomDisplayName: opts.roomDisplayName ?? null,
  });
}

function _message(
  opts: {
    type?: string;
    body?: string;
    attachments?: MessageAttachment[];
    sender?: UserSummary | null;
    isRecalled?: boolean;
    recalledBy?: UserSummary | null;
    isForceDeleted?: boolean;
    forceDeletedBy?: UserSummary | null;
    pending?: boolean;
    failed?: boolean;
  } = {},
): Message {
  return new Message({
    id: 'message_1',
    roomId: 'room_1',
    sender: opts.sender ?? _user({ id: 'user_1', username: 'logan', displayName: 'Logan' }),
    clientMessageId: 'client_1',
    type: opts.type ?? 'text',
    body: opts.body ?? 'hello',
    createdAt: new Date(Date.UTC(2026, 5, 4)),
    attachments: opts.attachments ?? [],
    isRecalled: opts.isRecalled ?? false,
    recalledBy: opts.recalledBy ?? null,
    isForceDeleted: opts.isForceDeleted ?? false,
    forceDeletedBy: opts.forceDeletedBy ?? null,
    pending: opts.pending ?? false,
    failed: opts.failed ?? false,
  });
}

function _stickerAttachment(): MessageAttachment {
  return new MessageAttachment({
    type: 'sticker',
    asset: new UploadedAsset({
      id: 'asset_1',
      url: '/sticker.webp',
      thumbnailUrl: null,
      mimeType: 'image/webp',
    }),
  });
}

function _sticker(): Sticker {
  return new Sticker({
    id: 'sticker_1',
    name: 'wave',
    sortOrder: 10,
    asset: new UploadedAsset({
      id: 'asset_1',
      url: '/sticker.webp',
      thumbnailUrl: null,
      mimeType: 'image/webp',
    }),
  });
}
