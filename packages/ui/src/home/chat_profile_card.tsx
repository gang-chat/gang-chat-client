import type { ReactNode } from 'react';

import * as room_display from '@gang-chat/core/app/room_display';
import { CurrentUser, type PublicRoom, type UserSummary } from '@gang-chat/core/protocol/models';

import { Text } from '../lib/layout';
import { Avatar } from '../ui/avatar';
import { Icon, Icons } from '../ui/icon';
import { UiTooltip } from '../ui/tooltip';
import { UiColors, UiTypography } from '../ui/tokens';
import { UserHoverCard, type RoomProfileResolver, type UserProfileActionBuilder } from './room_profile_card';

/**
 * Port of `chat_profile_card.dart` (a part of `chat_pane.dart`): the avatar
 * hover card used by message rows and system-message user chips, plus the
 * gang-bot avatar and badge.
 */

export interface _AvatarHoverCardProps {
  user: UserSummary;
  currentUser: CurrentUser;
  children?: ReactNode;

  /**
   * Lazily fetches a richer profile (gender, common rooms) the first time the
   * card opens. When null the card shows only the message summary.
   */
  onResolveProfile?: ((sender: UserSummary) => Promise<UserSummary>) | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterCommonRoom?: ((room: PublicRoom) => void) | null;
  profileActionBuilder?: UserProfileActionBuilder | null;
  inLive?: boolean;
  showRoomRole?: boolean;
}

/** Wraps a message avatar so hovering it reveals a floating profile card. */
export function _AvatarHoverCard({
  user,
  currentUser,
  children,
  onResolveProfile,
  onResolveRoomProfile,
  onEnterCommonRoom,
  profileActionBuilder,
  inLive = false,
  showRoomRole = true,
}: _AvatarHoverCardProps) {
  // gang-bot is not an account: there is no profile to show.
  if (user.isBot) {
    return <UiTooltip message={kGangBotDescription}>{children}</UiTooltip>;
  }
  return (
    <UserHoverCard
      user={user}
      currentUser={currentUser}
      onResolveProfile={onResolveProfile ?? null}
      onResolveRoomProfile={onResolveRoomProfile ?? null}
      onEnterCommonRoom={onEnterCommonRoom ?? null}
      profileActionBuilder={profileActionBuilder ?? null}
      inLive={inLive}
      showRoomRole={showRoomRole}
    >
      {children}
    </UserHoverCard>
  );
}

export interface AvatarHoverCardForTestProps {
  user: UserSummary;
  currentUser?: CurrentUser;
  onResolveProfile?: ((sender: UserSummary) => Promise<UserSummary>) | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterCommonRoom?: ((room: PublicRoom) => void) | null;
  profileActionBuilder?: UserProfileActionBuilder | null;
  inLive?: boolean;
  showRoomRole?: boolean;
}

/**
 * Test-only entry point for the otherwise-private avatar hover card, so widget
 * tests can pump it directly without standing up the whole message list.
 */
export function AvatarHoverCardForTest({
  user,
  currentUser = _avatarHoverCardTestCurrentUser,
  onResolveProfile,
  onResolveRoomProfile,
  onEnterCommonRoom,
  profileActionBuilder,
  inLive = false,
  showRoomRole = true,
}: AvatarHoverCardForTestProps) {
  return (
    <_AvatarHoverCard
      user={user}
      currentUser={currentUser}
      onResolveProfile={onResolveProfile}
      onResolveRoomProfile={onResolveRoomProfile}
      onEnterCommonRoom={onEnterCommonRoom}
      profileActionBuilder={profileActionBuilder}
      inLive={inLive}
      showRoomRole={showRoomRole}
    >
      <Avatar label={room_display.userAvatarLabel(user)} size={32} showBorder={false} />
    </_AvatarHoverCard>
  );
}

export const _avatarHoverCardTestCurrentUser = new CurrentUser({
  id: 'avatar-hover-card-test-user',
  uid: '',
  username: 'viewer',
  displayName: 'Viewer',
  bio: '',
  gender: 'secret',
  email: null,
  emailPublic: false,
  phoneNumber: null,
  phoneNumberPublic: false,
  avatarUrl: null,
  defaultAvatarKey: 'green-2',
  isSuperuser: false,
  createdAt: null,
});

export const kGangBotDescription =
  'gang-bot 是群里的 AI，不是真人账号。@gang-bot 就能和它聊，房间管理员可以在房间设置里关掉它。';

/** gang-bot's avatar: a robot mark instead of initials. */
export function _GangBotAvatar({ size }: { size: number }) {
  return (
    <div
      style={{
        width: size,
        height: size,
        flexShrink: 0,
        boxSizing: 'border-box',
        borderRadius: '50%',
        backgroundColor: UiColors.selected,
        // A DecoratedBox border paints inside the box without taking layout space.
        boxShadow: `inset 0 0 0 1px ${UiColors.selectedBorder}`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Icon icon={Icons.smart_toy_outlined} color={UiColors.accent} size={size * 0.56} />
    </div>
  );
}

/** The "AI" tag next to gang-bot's name, so it never passes for a person. */
export function _GangBotBadge() {
  return (
    <div
      style={{
        flexShrink: 0,
        boxSizing: 'border-box',
        backgroundColor: UiColors.selected,
        borderRadius: 4,
        boxShadow: `inset 0 0 0 1px ${UiColors.selectedBorder}`,
        padding: '1px 4px',
      }}
    >
      <Text
        softWrap={false}
        style={{ ...UiTypography.label, color: UiColors.accent, fontSize: 9, fontWeight: 700, lineHeight: 1.1 }}
      >
        AI
      </Text>
    </div>
  );
}
