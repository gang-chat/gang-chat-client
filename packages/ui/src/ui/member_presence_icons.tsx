import { RoomMemberPresence, roomMemberPresenceLabel } from '@gang-chat/core/app/room_members_filter';

import { Text } from '../lib/layout';
import { Icons, type IconData } from './icon';
import { UiColors, UiTypography } from './tokens';

export enum PresencePillTone {
  voice = 'voice',
  online = 'online',
  offline = 'offline',
  suspended = 'suspended',
}

export function roomMemberPresenceIcon(presence: RoomMemberPresence): IconData {
  switch (presence) {
    case RoomMemberPresence.live:
      return Icons.call;
    case RoomMemberPresence.online:
      return Icons.circle;
    case RoomMemberPresence.offline:
      return Icons.circle_outlined;
  }
}

export function roomMemberPresencePillTone(presence: RoomMemberPresence): PresencePillTone {
  switch (presence) {
    case RoomMemberPresence.live:
      return PresencePillTone.voice;
    case RoomMemberPresence.online:
      return PresencePillTone.online;
    case RoomMemberPresence.offline:
      return PresencePillTone.offline;
  }
}

export interface PresencePillProps {
  label: string;
  tone: PresencePillTone;
}

function PresencePillImpl({ label, tone }: PresencePillProps) {
  const style = _presencePillStyle(tone);
  return (
    <div
      style={{
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        width: 'fit-content',
        maxWidth: '100%',
        minWidth: 0,
        backgroundColor: style.surface,
        borderRadius: 999,
        border: `1px solid ${style.border}`,
        padding: '5px 9px',
      }}
    >
      <_PresenceDot color={style.foreground} filled={style.dotFilled} />
      <div style={{ width: 6, flexShrink: 0 }} />
      <Text maxLines={1} style={{ ...UiTypography.label, color: style.foreground, fontWeight: 600 }}>
        {label}
      </Text>
    </div>
  );
}

/**
 * Port of `PresencePill` with its Dart factory constructors
 * (`PresencePill.member(presence)`, `PresencePill.online()`, ...), which here
 * return elements.
 */
export const PresencePill = Object.assign(PresencePillImpl, {
  member(presence: RoomMemberPresence) {
    return <PresencePillImpl label={roomMemberPresenceLabel(presence)} tone={roomMemberPresencePillTone(presence)} />;
  },
  online({ label = '在线' }: { label?: string } = {}) {
    return <PresencePillImpl label={label} tone={PresencePillTone.online} />;
  },
  offline({ label = '离线' }: { label?: string } = {}) {
    return <PresencePillImpl label={label} tone={PresencePillTone.offline} />;
  },
  voice({ label = '语音' }: { label?: string } = {}) {
    return <PresencePillImpl label={label} tone={PresencePillTone.voice} />;
  },
  suspended({ label = '封禁中' }: { label?: string } = {}) {
    return <PresencePillImpl label={label} tone={PresencePillTone.suspended} />;
  },
  fromLabel(label: string) {
    return <PresencePillImpl label={label} tone={presencePillToneForLabel(label)} />;
  },
});

/** The tone `PresencePill.fromLabel` picks for [label]. */
export function presencePillToneForLabel(label: string): PresencePillTone {
  switch (label.trim()) {
    case '语音':
      return PresencePillTone.voice;
    case '离线':
      return PresencePillTone.offline;
    case '封禁中':
      return PresencePillTone.suspended;
    default:
      return PresencePillTone.online;
  }
}

function _PresenceDot({ color, filled }: { color: string; filled: boolean }) {
  return (
    <div
      style={{
        width: 10,
        height: 10,
        flexShrink: 0,
        boxSizing: 'border-box',
        borderRadius: '50%',
        backgroundColor: filled ? color : 'transparent',
        border: filled ? undefined : `1.4px solid ${color}`,
      }}
    />
  );
}

interface _PresencePillStyle {
  foreground: string;
  surface: string;
  border: string;
  dotFilled: boolean;
}

function _presencePillStyle(tone: PresencePillTone): _PresencePillStyle {
  switch (tone) {
    case PresencePillTone.voice:
      return {
        foreground: UiColors.presenceVoice,
        surface: UiColors.presenceVoiceSurface,
        border: UiColors.presenceVoiceBorder,
        dotFilled: true,
      };
    case PresencePillTone.online:
      return {
        foreground: UiColors.presenceOnline,
        surface: UiColors.presenceOnlineSurface,
        border: UiColors.presenceOnlineBorder,
        dotFilled: true,
      };
    case PresencePillTone.offline:
      return {
        foreground: UiColors.presenceOffline,
        surface: UiColors.presenceOfflineSurface,
        border: UiColors.presenceOfflineBorder,
        dotFilled: false,
      };
    case PresencePillTone.suspended:
      return {
        foreground: UiColors.danger,
        surface: UiColors.roleSuperuserSurface,
        border: UiColors.dangerBorder,
        dotFilled: true,
      };
  }
}
