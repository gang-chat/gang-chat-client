import { describe, expect, it } from 'vitest';

import {
  LivePresenceAnnouncement,
  LivePresenceAnnouncementAction,
  shouldSpeakLivePresenceAnnouncement,
} from '../../src/app/live_presence_announcement';
import { livePresenceAnnouncementForUser } from '../../src/app/room_display';
import { UserSummary } from '../../src/protocol/models';

describe('live presence announcement', () => {
  it('presence speech excludes self and obeys the room switch', () => {
    expect(
      shouldSpeakLivePresenceAnnouncement({
        enabled: true,
        participantIdentity: 'user-1',
        currentUserIdentity: 'user-1',
      }),
    ).toBe(false);
    expect(
      shouldSpeakLivePresenceAnnouncement({
        enabled: false,
        participantIdentity: 'user-2',
        currentUserIdentity: 'user-1',
      }),
    ).toBe(false);
    expect(
      shouldSpeakLivePresenceAnnouncement({
        enabled: true,
        participantIdentity: 'user-2',
        currentUserIdentity: 'user-1',
      }),
    ).toBe(true);
    expect(
      shouldSpeakLivePresenceAnnouncement({
        enabled: true,
        participantIdentity: '  ',
        currentUserIdentity: 'user-1',
      }),
    ).toBe(false);
    expect(
      shouldSpeakLivePresenceAnnouncement({
        enabled: true,
        participantIdentity: null,
        currentUserIdentity: 'user-1',
      }),
    ).toBe(false);
  });

  it('presence announcement uses role and room display name as segments', () => {
    const announcement = livePresenceAnnouncementForUser({
      user: new UserSummary({
        id: 'user-2',
        username: 'morgan',
        displayName: 'Morgan Account',
        roomDisplayName: '房间里的 Morgan',
        roomRole: 'admin',
        avatarUrl: null,
        defaultAvatarKey: 'blue-1',
      }),
      action: LivePresenceAnnouncementAction.joined,
    });

    expect(announcement.segments).toEqual(['管理员', '房间里的 Morgan', '进入了语音频道']);
  });

  it('presence announcement distinguishes leave from removal', () => {
    const left = new LivePresenceAnnouncement({
      roleLabel: '成员',
      roomDisplayName: '小林',
      action: LivePresenceAnnouncementAction.left,
    });
    const removed = new LivePresenceAnnouncement({
      roleLabel: '成员',
      roomDisplayName: '小林',
      action: LivePresenceAnnouncementAction.removed,
    });

    expect(left.actionLabel).toBe('离开了语音频道');
    expect(removed.actionLabel).toBe('被踢出了语音频道');
  });

  it('presence announcement falls back to account display name', () => {
    const announcement = livePresenceAnnouncementForUser({
      user: new UserSummary({
        id: 'user-2',
        username: 'morgan',
        displayName: 'Morgan',
        avatarUrl: null,
        defaultAvatarKey: 'blue-1',
      }),
      action: LivePresenceAnnouncementAction.left,
    });

    expect(announcement.segments).toEqual(['成员', 'Morgan', '离开了语音频道']);
  });
});
