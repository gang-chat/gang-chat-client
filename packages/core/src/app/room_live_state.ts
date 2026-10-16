import { RoomCard, type LiveState } from '../protocol/models';

export function patchRoomLiveCount(opts: { rooms: RoomCard[]; roomId: string; live: LiveState }): RoomCard[] {
  const { rooms, roomId, live } = opts;
  return rooms.map((room) => (room.id === roomId ? roomCardWithLive(room, live) : room));
}

export function roomCardWithLive(room: RoomCard, live: LiveState): RoomCard {
  const preview = live.participants.slice(0, 5).map((item) => item.user);
  return new RoomCard({
    id: room.id,
    name: room.name,
    rid: room.rid,
    visibility: room.visibility,
    remarkName: room.remarkName,
    description: room.description,
    notificationPolicy: room.notificationPolicy,
    isPinned: room.isPinned,
    avatarUrl: room.avatarUrl,
    defaultAvatarKey: room.defaultAvatarKey,
    memberCount: room.memberCount,
    onlineMemberCount: room.onlineMemberCount,
    liveParticipantCount: live.participantCount,
    liveAvatarPreview: preview,
    lastMessage: room.lastMessage,
    unreadCount: room.unreadCount,
    hasUnreadCount: room.hasUnreadCount,
    unreadMentionCount: room.unreadMentionCount,
    hasUnreadMentionCount: room.hasUnreadMentionCount,
    hasPendingJoinRequests: room.hasPendingJoinRequests,
    aiVoiceAnnouncementsEnabled: room.aiVoiceAnnouncementsEnabled,
    updatedAt: room.updatedAt,
  });
}
