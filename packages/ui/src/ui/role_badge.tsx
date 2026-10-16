import type { CSSProperties } from 'react';

import { paddingStyle, EdgeInsets, type EdgeInsetsValue } from '../lib/edge_insets';
import { Text } from '../lib/layout';
import { HighlightedText } from './highlighted_text';
import { UiColors, UiTypography } from './tokens';

export enum RoleBadgeTone {
  member = 'member',
  admin = 'admin',
  creator = 'creator',
  superuser = 'superuser',
  neutral = 'neutral',
}

export function roleBadgeToneForLabel(label: string): RoleBadgeTone {
  switch (label.trim()) {
    case '成员':
      return RoleBadgeTone.member;
    case '管理员':
      return RoleBadgeTone.admin;
    case '创建者':
      return RoleBadgeTone.creator;
    case '超级用户':
      return RoleBadgeTone.superuser;
    default:
      return RoleBadgeTone.neutral;
  }
}

export function roleBadgeForegroundColorForLabel(label: string): string {
  return _roleBadgeStyle(roleBadgeToneForLabel(label)).foreground;
}

export interface RoleBadgeProps {
  label: string;
  query?: string;
  padding?: EdgeInsetsValue;
  borderRadius?: number;
  fontSize?: number;
  fontWeight?: CSSProperties['fontWeight'];
  style?: CSSProperties;
}

export function RoleBadge({
  label,
  query = '',
  padding = EdgeInsets.symmetric({ horizontal: 9, vertical: 5 }),
  borderRadius = 999,
  fontSize = 12,
  fontWeight = 600,
  style,
}: RoleBadgeProps) {
  const badgeStyle = _roleBadgeStyle(roleBadgeToneForLabel(label));
  const textStyle: CSSProperties = { ...UiTypography.label, color: badgeStyle.foreground, fontSize, fontWeight };
  return (
    <div
      style={{
        boxSizing: 'border-box',
        width: 'fit-content',
        maxWidth: '100%',
        minWidth: 0,
        backgroundColor: badgeStyle.background,
        borderRadius,
        border: `1px solid ${badgeStyle.border}`,
        ...paddingStyle(padding),
        ...style,
      }}
    >
      {query.trim().length === 0 ? (
        <Text maxLines={1} style={textStyle}>
          {label}
        </Text>
      ) : (
        <HighlightedText text={label} query={query} maxLines={1} overflow="ellipsis" style={textStyle} />
      )}
    </div>
  );
}

interface _RoleBadgeStyle {
  foreground: string;
  background: string;
  border: string;
}

function _roleBadgeStyle(tone: RoleBadgeTone): _RoleBadgeStyle {
  switch (tone) {
    case RoleBadgeTone.member:
      return { foreground: UiColors.roleMember, background: UiColors.roleMemberSurface, border: UiColors.roleMemberBorder };
    case RoleBadgeTone.admin:
      return { foreground: UiColors.roleAdmin, background: UiColors.roleAdminSurface, border: UiColors.roleAdminBorder };
    case RoleBadgeTone.creator:
      return { foreground: UiColors.roleCreator, background: UiColors.roleCreatorSurface, border: UiColors.roleCreatorBorder };
    case RoleBadgeTone.superuser:
      return {
        foreground: UiColors.roleSuperuser,
        background: UiColors.roleSuperuserSurface,
        border: UiColors.roleSuperuserBorder,
      };
    case RoleBadgeTone.neutral:
      return { foreground: UiColors.textSecondary, background: UiColors.surfacePressed, border: UiColors.border };
  }
}
