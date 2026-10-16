import { describe, expect, it } from 'vitest';

import type { JsonMap } from '../../src/lib/json';
import {
  Message,
  MessageAttachment,
  MusicBoxPlaybackState,
  RoomApplication,
  RoomCard,
  RoomDetail,
  RoomInvite,
  RoomMember,
  RoomMemberPage,
  MusicBoxState,
} from '../../src/protocol/models';

// Model-parsing cases from test/unit/protocol/api_client_test.dart. The
// synchronous ones are ported verbatim; the last few feed the JSON fixtures of
// API-level tests straight into fromJson, keeping only the parsing assertions
// (the request/transport side belongs to the api_client tests).

describe('models', () => {
  it('copied music component serializes its source message reference', () => {
    const playlist = new MessageAttachment({
      type: 'playlist',
      sourceMessageId: 'message_playlist_1',
    });
    const track = new MessageAttachment({
      type: 'music_track',
      sourceMessageId: 'message_track_1',
    });

    expect(playlist.toJson()).toEqual({
      type: 'playlist',
      source_message_id: 'message_playlist_1',
    });
    expect(track.toJson()).toEqual({
      type: 'music_track',
      source_message_id: 'message_track_1',
    });
  });

  it('Message parses rich sender fields for profile popups', () => {
    const message = Message.fromJson({
      id: 'msg_1',
      room_id: 'room_1',
      sender: {
        id: 'user_1',
        uid: '1000001',
        username: 'alice',
        display_name: 'Alice',
        bio: 'Building quietly.',
        gender: 'female',
        email: 'alice@example.test',
        email_public: true,
        phone_number: '+8613800000000',
        phone_number_public: true,
        avatar_url: '/assets/asset_1/avatar.png',
        default_avatar_key: 'rose-2',
        is_superuser: true,
        common_rooms: [
          {
            room_id: 'room_2',
            rid: 'R10002',
            room_name: 'Side Room',
            visibility: 'private',
            room_username: 'Alice Side',
            membership_role: 'member',
          },
        ],
      },
      sender_room_username: 'A. Chen',
      sender_room_role: 'admin',
      sender_common_rooms: [
        {
          room_id: 'room_3',
          rid: 'R10003',
          room_name: 'Admin Room',
          visibility: 'public',
          room_username: 'Alice Admin',
          membership_role: 'admin',
        },
      ],
      client_message_id: 'cmsg_1',
      body: 'hello',
      created_at: '2026-05-31T14:00:00Z',
    });

    const sender = message.sender;
    expect(sender.uid).toBe('1000001');
    expect(sender.bio).toBe('Building quietly.');
    expect(sender.gender).toBe('female');
    expect(sender.email).toBe('alice@example.test');
    expect(sender.emailPublic).toBe(true);
    expect(sender.phoneNumber).toBe('+8613800000000');
    expect(sender.phoneNumberPublic).toBe(true);
    expect(sender.roomDisplayName).toBe('A. Chen');
    expect(sender.roomRole).toBe('admin');
    expect(sender.isSuperuser).toBe(true);
    expect(sender.commonRooms).toHaveLength(1);
    const room = sender.commonRooms[0]!;
    expect(room.id).toBe('room_3');
    expect(room.rid).toBe('R10003');
    expect(room.name).toBe('Admin Room');
    expect(room.visibility).toBe('public');
    expect(room.roomDisplayName).toBe('Alice Admin');
    expect(room.roomRole).toBe('admin');
  });

  it('RoomMember parses room names, remarks, presence, and role fields', () => {
    const page = RoomMemberPage.fromJson({
      next_cursor: 'cursor_2',
      members: [
        {
          user: {
            id: 'user_1',
            uid: '1000001',
            username: 'alice',
            display_name: 'Alice',
            avatar_url: '/assets/asset_1/avatar.png',
            default_avatar_key: 'rose-2',
            is_superuser: true,
          },
          role: 'admin',
          room_display_name: 'Alice In Room',
          remark_name: 'Ops lead',
          text_muted_until: 'permanent',
          presence: { status: 'connected' },
          joined_at: '2026-05-31T14:00:00Z',
        },
      ],
    });

    expect(page.members).toHaveLength(1);
    const member = page.members[0]!;
    expect(page.nextCursor).toBe('cursor_2');
    expect(member.user.uid).toBe('1000001');
    expect(member.user.roomDisplayName).toBe('Alice In Room');
    expect(member.user.roomRole).toBe('admin');
    expect(member.user.isSuperuser).toBe(true);
    expect(member.roomDisplayName).toBe('Alice In Room');
    expect(member.remarkName).toBe('Ops lead');
    expect(member.textMutedUntil).toBe('permanent');
    expect(member.isOnline).toBe(true);
    expect(member.joinedAt.toISOString()).toBe('2026-05-31T14:00:00.000Z');
  });

  it('RoomMember parses online state from nested user payload', () => {
    const member = RoomMember.fromJson({
      user: {
        id: 'user_1',
        uid: '1000001',
        username: 'alice',
        display_name: 'Alice',
        is_online: true,
      },
      role: 'member',
      joined_at: '2026-05-31T14:00:00Z',
    });

    expect(member.isOnline).toBe(true);
    expect(member.user.isOnline).toBe(true);
  });

  it('RoomCard defaults the personal AI voice preference to disabled', () => {
    const room = RoomCard.fromJson({
      id: 'room_1',
      name: 'Alpha',
      avatar_url: null,
      default_avatar_key: 'blue-1',
      member_count: 2,
      live_participant_count: 1,
      live_avatar_preview: [],
      last_message: null,
      unread_count: 0,
      updated_at: '2026-07-11T10:00:00Z',
    });

    expect(room.aiVoiceAnnouncementsEnabled).toBe(false);
  });

  it('RoomDetail parses room settings and nullable creator', () => {
    const room = RoomDetail.fromJson({
      ..._roomDetailJson(),
      created_by: null,
      description: 'A room for launch planning',
      visibility: 'public',
      join_policy: 'open',
      remark_name: 'Launch',
      notification_policy: 'mentions',
      is_pinned: true,
      personal_profile: {
        display_name: 'Alice Ops',
        avatar_url: '/assets/asset_2/room-avatar.png',
        default_avatar_key: 'mint-2',
      },
      ai_voice_announcements_enabled: false,
      can_delete_room: true,
      my_membership: {
        joined_at: '2026-05-31T14:00:00Z',
        role: 'superuser',
      },
    });

    expect(room.createdBy).toBeNull();
    expect(room.description).toBe('A room for launch planning');
    expect(room.visibility).toBe('public');
    expect(room.joinPolicy).toBe('open');
    expect(room.remarkName).toBe('Launch');
    expect(room.notificationPolicy).toBe('mentions');
    expect(room.isPinned).toBe(true);
    expect(room.personalProfile.displayName).toBe('Alice Ops');
    expect(room.onlineMemberCount).toBe(1);
    expect(room.toCard().onlineMemberCount).toBe(1);
    expect(room.aiVoiceAnnouncementsEnabled).toBe(false);
    expect(room.myMembership.aiVoiceAnnouncementsEnabled).toBe(false);
    expect(room.isAdmin).toBe(true);
    expect(room.isSuperuser).toBe(true);
    expect(room.canDelete).toBe(true);
    expect(room.toCard().displayName).toBe('Launch');
    expect(room.toCard().isPinned).toBe(true);
  });

  it('room invite notification parses room profile card fields', () => {
    const invite = RoomInvite.fromJson(_roomInviteJson({ roomJoined: true, includeViewerRoomInfo: true }));
    const room = invite.room;

    expect(room.description).toBe('Invite room bio');
    expect(room.createdBy?.id).toBe('user_1');
    expect(room.personalProfile.displayName).toBe('Alice in Room');
    expect(room.myMembership?.role).toBe('member');
  });

  it('room application notification parses missing reviewer state', () => {
    const application = RoomApplication.fromJson(_roomApplicationJson({ status: 'approved', reviewerExists: false }));

    expect(application.reviewerExists).toBe(false);
    expect(application.reviewer?.isDeleted).toBe(true);
    expect(application.reviewer?.displayName).toBe('Alice');
  });

  it('playlist messages parse the server snapshot', () => {
    const snapshot: JsonMap = {
      id: 'playlist_1',
      name: '夜晚',
      description: '',
      item_count: 1,
      created_at: '2026-08-07T09:30:00Z',
      creator: {
        id: 'user_1',
        username: 'alice',
        display_name: 'Alice',
        room_display_name: '房间里的 Alice',
        avatar_url: null,
        default_avatar_key: 'blue-1',
      },
      items: [
        {
          id: 'item_1',
          playlist_id: 'playlist_1',
          track_id: 'track_1',
          source: 'netease',
          title: '第一首歌',
          artists: ['歌手'],
          duration_ms: 180000,
          sort_order: 10,
          created_at: '2026-08-07T09:31:00Z',
        },
      ],
    };
    const requestedAttachment = new MessageAttachment({ type: 'playlist', playlistId: 'playlist_1' });
    expect(requestedAttachment.toJson()).toEqual({ type: 'playlist', playlist_id: 'playlist_1' });

    const message = Message.fromJson({
      id: 'msg_playlist_1',
      room_id: 'room_1',
      sender: { id: 'user_1', username: 'alice', display_name: 'Alice' },
      client_message_id: 'cmsg_playlist_1',
      type: 'playlist',
      body: '[歌单] 夜晚',
      attachments: [{ type: 'playlist', playlist: snapshot }],
      created_at: '2026-08-07T09:32:00Z',
    });

    expect(message.type).toBe('playlist');
    expect(message.playlistAttachment?.playlist?.name).toBe('夜晚');
    expect(message.playlistAttachment?.playlist?.creator.roomDisplayName).toBe('房间里的 Alice');
    expect(message.playlistAttachment?.playlist?.items[0]?.trackId).toBe('track_1');
  });

  it('MusicBoxState parses the room snapshot', () => {
    const state = MusicBoxState.fromJson({
      enabled: true,
      playback: {
        state: 'playing',
        current_item_id: 'item_1',
        position_ms: 4200,
        volume: 70,
        updated_at: '2026-06-01T10:00:00Z',
      },
      queue: [
        {
          id: 'item_1',
          source: 'netease',
          track_id: 't1',
          title: 'Song A',
          artist: 'Artist A',
          duration_ms: 200000,
          status: 'ready',
          added_by_user_id: 'user_2',
        },
      ],
      usage: { used_bytes: 1024, limit_bytes: 10240 },
    });

    expect(state.enabled).toBe(true);
    expect(state.playback.state).toBe(MusicBoxPlaybackState.playing);
    expect(state.currentItem?.title).toBe('Song A');
    expect(state.usage.limitBytes).toBe(10240);
  });
});

function _roomInviteJson(
  opts: {
    status?: string;
    roomExists?: boolean;
    invalidReason?: string | null;
    inviterRoomRole?: string;
    inviterExists?: boolean;
    roomJoined?: boolean;
    includeViewerRoomInfo?: boolean;
  } = {},
): JsonMap {
  const {
    status = 'pending',
    roomExists = true,
    invalidReason = null,
    inviterRoomRole = 'owner',
    inviterExists = true,
    roomJoined = false,
    includeViewerRoomInfo = false,
  } = opts;
  return {
    id: 'rinv_1',
    status,
    created_at: '2026-05-31T13:00:00Z',
    updated_at: '2026-05-31T13:00:00Z',
    room_exists: roomExists,
    inviter_exists: inviterExists,
    invalid_reason: invalidReason,
    room: {
      id: 'room_1',
      rid: '900001',
      name: 'Invite Room',
      description: 'Invite room bio',
      avatar_url: null,
      default_avatar_key: 'room-1',
      visibility: 'private',
      join_policy: 'closed',
      member_count: 1,
      online_member_count: 1,
      live_participant_count: 0,
      joined: roomJoined,
      join_state: roomJoined ? 'joined' : 'none',
      created_by: {
        id: 'user_1',
        uid: '1000001',
        username: 'alice',
        display_name: 'Alice',
        avatar_url: null,
        default_avatar_key: 'blue-3',
      },
      ...(includeViewerRoomInfo
        ? {
            personal_profile: {
              display_name: 'Alice in Room',
              avatar_url: '/room-alice.png',
              default_avatar_key: 'green-2',
            },
            my_membership: {
              joined_at: '2026-05-31T14:00:00Z',
              role: 'member',
            },
          }
        : {}),
    },
    inviter: {
      id: 'user_1',
      uid: '1000001',
      username: 'alice',
      display_name: 'Alice',
      avatar_url: null,
      default_avatar_key: 'blue-3',
      room_display_name: 'Alice in Invite Room',
      room_role: inviterRoomRole,
    },
  };
}

function _roomApplicationJson(opts: { status?: string; reason?: string; reviewerExists?: boolean } = {}): JsonMap {
  const { status = 'pending', reason = '', reviewerExists = true } = opts;
  const reviewed = status === 'approved' || status === 'rejected';
  return {
    id: 'jrq_1',
    status,
    reason,
    created_at: '2026-05-30T13:00:00Z',
    updated_at: '2026-05-31T13:00:00Z',
    reviewed_at: reviewed ? '2026-05-31T13:00:00Z' : null,
    room: _roomInviteJson()['room'],
    reviewer_exists: reviewerExists,
    reviewer: reviewed
      ? {
          id: 'user_1',
          uid: '1000001',
          username: 'alice',
          display_name: 'Alice',
          avatar_url: null,
          default_avatar_key: 'blue-3',
          room_display_name: 'Alice in Invite Room',
          room_role: 'owner',
        }
      : null,
  };
}

function _roomDetailJson(): JsonMap {
  return {
    id: 'room_1',
    rid: '900001',
    name: 'Invite Room',
    avatar_url: null,
    default_avatar_key: 'room-1',
    member_count: 2,
    online_member_count: 1,
    created_by: {
      id: 'user_1',
      uid: '1000001',
      username: 'alice',
      display_name: 'Alice',
    },
    my_membership: { joined_at: '2026-05-31T14:00:00Z', role: 'member' },
    live: {
      room_id: 'room_1',
      participant_count: 0,
      participants: [],
      updated_at: '2026-05-31T14:00:00Z',
    },
    created_at: '2026-05-31T13:00:00Z',
    updated_at: '2026-05-31T14:00:00Z',
  };
}
