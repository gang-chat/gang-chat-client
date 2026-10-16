// Port of `chat_header.dart` (a part of `chat_pane.dart`).
import { useRef, type CSSProperties, type ReactNode } from 'react';

import * as room_display from '@gang-chat/core/app/room_display';
import type { UserSummary } from '@gang-chat/core/protocol/models';

import { EdgeInsets } from '../lib/edge_insets';
import { Column, Expanded, Row, SizedBox, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { measureTextWidth } from '../lib/text_measure';
import { useElementSize } from '../lib/use_element_size';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar } from '../ui/avatar';
import { BadgeDot } from '../ui/badge_dot';
import { ButtonIconPlain } from '../ui/button';
import { Icon, Icons, type IconData } from '../ui/icon';
import { PressableSurface } from '../ui/surface';
import { UiTooltip } from '../ui/tooltip';
import { UiColors, UiRadii, UiTypography } from '../ui/tokens';
import { HomeAdaptiveLayout } from './adaptive_layout';
import {
  _chatHeaderBottomInset,
  _chatHeaderHeight,
  _chatHeaderHorizontalInset,
  _chatHeaderVisualTopInset,
  _headerActionButtonSize,
  _headerActionGap,
  _liveHeaderCardHeight,
} from './chat_pane';

export interface _RoomHeaderProps {
  onNavigateBack?: (() => void) | null;
  title: string;
  avatarLabel: string;
  avatarUrl: string | null;
  defaultAvatarKey: string;
  memberCount: number | null;
  onlineMemberCount: number | null;
  liveParticipantCount: number | null;
  liveAvatarPreview: readonly UserSummary[];
  hasPendingJoinRequests: boolean;
  onLivePressed: () => void;
  onMembersPressed: () => void;
  onSettingsPressed: () => void;
}

export function _RoomHeader(props: _RoomHeaderProps) {
  const headerTopInset = _chatHeaderVisualTopInset;
  return (
    <SizedBox
      height={_chatHeaderHeight}
      style={{
        paddingLeft: _chatHeaderHorizontalInset,
        paddingTop: headerTopInset,
        paddingRight: _chatHeaderHorizontalInset,
        paddingBottom: _chatHeaderBottomInset,
      }}
    >
      <Row crossAxisAlignment="start" style={{ height: '100%' }}>
        <Expanded>
          <_LiveChannelHeaderCard
            onNavigateBack={props.onNavigateBack}
            title={props.title}
            avatarLabel={props.avatarLabel}
            avatarUrl={props.avatarUrl}
            defaultAvatarKey={props.defaultAvatarKey}
            memberCount={props.memberCount}
            onlineMemberCount={props.onlineMemberCount}
            liveParticipantCount={props.liveParticipantCount}
            liveAvatarPreview={props.liveAvatarPreview}
            onPressed={props.onLivePressed}
          />
        </Expanded>
        <SizedBox width={10} />
        <_RoomHeaderActions
          hasPendingJoinRequests={props.hasPendingJoinRequests}
          onMembersPressed={props.onMembersPressed}
          onSettingsPressed={props.onSettingsPressed}
        />
      </Row>
    </SizedBox>
  );
}

interface _LiveChannelHeaderCardProps {
  onNavigateBack?: (() => void) | null;
  title: string;
  avatarLabel: string;
  avatarUrl: string | null;
  defaultAvatarKey: string;
  memberCount: number | null;
  onlineMemberCount: number | null;
  liveParticipantCount: number | null;
  liveAvatarPreview: readonly UserSummary[];
  onPressed: () => void;
}

export function _LiveChannelHeaderCard(props: _LiveChannelHeaderCardProps) {
  const compact = HomeAdaptiveLayout.usesCompactLayout();
  const liveActive = (props.liveParticipantCount ?? 0) > 0;
  if (!compact) {
    return <_DesktopLiveChannelHeaderCard {...props} liveActive={liveActive} />;
  }
  return <_AndroidLiveChannelHeaderCard {...props} liveActive={liveActive} />;
}

const _liveHeaderLeftPadding = 7.0;
const _liveHeaderRightPadding = 13.0;
const _liveHeaderBackColumnWidth = 30.0;
const _liveHeaderBackGap = 3.0;
const _androidLiveHeaderPreviewMaxWidth = 52.0;
const _desktopLiveHeaderPreviewMaxWidth = 190.0;
const _liveHeaderSideGap = 10.0;
const _liveHeaderRoomInfoMinWidth = 44.0;
const _androidLiveHeaderPreviewMinWidth = 32.0;
const _desktopLiveHeaderPreviewMinWidth = 60.0;

/** `_buildDesktopCard`. */
function _DesktopLiveChannelHeaderCard(props: _LiveChannelHeaderCardProps & { liveActive: boolean }) {
  const { liveActive } = props;
  return (
    <PressableSurface
      data-testid="chat-header-live-button"
      width={Infinity}
      height={_liveHeaderCardHeight}
      borderRadius={UiRadii.md}
      backgroundColor={UiColors.surface}
      selectedBackgroundColor={UiColors.selected}
      borderColor={UiColors.border}
      selectedBorderColor={UiColors.selectedBorder}
      selected={liveActive}
      padding={EdgeInsets.symmetric({ horizontal: 10 })}
      tooltip="进入语音频道"
      onPressed={props.onPressed}
    >
      <_LiveHeaderLayout
        liveActive={liveActive}
        previewMaxWidthLimit={_desktopLiveHeaderPreviewMaxWidth}
        previewMinWidth={_desktopLiveHeaderPreviewMinWidth}
        roomInfo={
          <_LiveHeaderRoomInfo
            title={props.title}
            avatarLabel={props.avatarLabel}
            avatarUrl={props.avatarUrl}
            defaultAvatarKey={props.defaultAvatarKey}
            memberCount={props.memberCount}
            onlineMemberCount={props.onlineMemberCount}
            liveActive={liveActive}
          />
        }
        preview={(previewMaxWidth) => (
          <_LiveHeaderAvatarPreview
            users={props.liveAvatarPreview}
            participantCount={props.liveParticipantCount ?? 0}
            maxWidth={previewMaxWidth}
          />
        )}
      />
    </PressableSurface>
  );
}

/** `_buildAndroidCard` (also the narrow-window desktop layout). */
function _AndroidLiveChannelHeaderCard(props: _LiveChannelHeaderCardProps & { liveActive: boolean }) {
  const { liveActive, onNavigateBack } = props;
  return (
    <PressableSurface
      data-testid="chat-header-live-button"
      width={Infinity}
      height={_liveHeaderCardHeight}
      borderRadius={UiRadii.md}
      backgroundColor={UiColors.surface}
      selectedBackgroundColor={UiColors.selected}
      borderColor={UiColors.border}
      selectedBorderColor={UiColors.selectedBorder}
      selected={liveActive}
      interactive
      padding={EdgeInsets.only({ left: _liveHeaderLeftPadding, right: _liveHeaderRightPadding })}
    >
      <Row crossAxisAlignment="stretch" style={{ flex: '1 1 0', minHeight: 0 }}>
        {onNavigateBack != null ? (
          <>
            <_LiveHeaderBackColumn onPressed={onNavigateBack} />
            <SizedBox width={_liveHeaderBackGap} />
          </>
        ) : null}
        <Expanded>
          <UiTooltip message="进入语音频道">
            <div
              role="button"
              aria-label="进入语音频道"
              onClick={(event) => {
                if (event.button !== 0) return;
                props.onPressed();
              }}
              style={{ flex: '1 1 0', minHeight: 0, display: 'flex', flexDirection: 'column', cursor: SystemMouseCursors.click }}
            >
              <_LiveHeaderLayout
                liveActive={liveActive}
                previewMaxWidthLimit={_androidLiveHeaderPreviewMaxWidth}
                previewMinWidth={_androidLiveHeaderPreviewMinWidth}
                roomInfo={
                  <_LiveHeaderRoomInfo
                    title={props.title}
                    avatarLabel={props.avatarLabel}
                    avatarUrl={props.avatarUrl}
                    defaultAvatarKey={props.defaultAvatarKey}
                    memberCount={props.memberCount}
                    onlineMemberCount={props.onlineMemberCount}
                    liveActive={liveActive}
                  />
                }
                preview={() => <_LiveHeaderParticipantCount participantCount={props.liveParticipantCount ?? 0} />}
              />
            </div>
          </UiTooltip>
        </Expanded>
      </Row>
    </PressableSurface>
  );
}

/**
 * The shared `LayoutBuilder` + `Stack` of both cards: room info pinned left
 * (when it has at least [_liveHeaderRoomInfoMinWidth]) and the live preview
 * pinned right (while live, when it has at least [previewMinWidth]).
 */
function _LiveHeaderLayout({
  liveActive,
  previewMaxWidthLimit,
  previewMinWidth,
  roomInfo,
  preview,
}: {
  liveActive: boolean;
  previewMaxWidthLimit: number;
  previewMinWidth: number;
  roomInfo: ReactNode;
  preview: (previewMaxWidth: number) => ReactNode;
}) {
  const ref = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(ref);
  const maxWidth = size?.width ?? 0;
  const previewMaxWidth = liveActive ? Math.min(previewMaxWidthLimit, maxWidth) : 0.0;
  const roomInfoWidth = Math.max(0.0, maxWidth - (previewMaxWidth > 0 ? previewMaxWidth + _liveHeaderSideGap : 0));
  const slot: CSSProperties = { position: 'absolute', top: 0, bottom: 0, display: 'flex', alignItems: 'center' };
  return (
    <div ref={ref} style={{ position: 'relative', flex: '1 1 0', minHeight: 0, minWidth: 0 }}>
      {size != null && roomInfoWidth >= _liveHeaderRoomInfoMinWidth ? (
        <div style={{ ...slot, left: 0, width: roomInfoWidth }}>{roomInfo}</div>
      ) : null}
      {size != null && liveActive && previewMaxWidth >= previewMinWidth ? (
        <div style={{ ...slot, right: 0, maxWidth: previewMaxWidth, justifyContent: 'flex-end' }}>{preview(previewMaxWidth)}</div>
      ) : null}
    </div>
  );
}

function _LiveHeaderBackColumn({ onPressed }: { onPressed: () => void }) {
  return (
    <div data-testid="chat-header-back-button" style={{ display: 'flex', alignSelf: 'stretch' }}>
      <ButtonIconPlain
        tooltip="返回房间列表"
        onPressed={onPressed}
        width={_liveHeaderBackColumnWidth}
        // Dart `height: double.infinity`: the full card height.
        style={{ height: 'auto', alignSelf: 'stretch' }}
        icon={<Icon icon={Icons.arrow_back_rounded} />}
      />
    </div>
  );
}

interface _LiveHeaderRoomInfoProps {
  title: string;
  avatarLabel: string;
  avatarUrl: string | null;
  defaultAvatarKey: string;
  memberCount: number | null;
  onlineMemberCount: number | null;
  liveActive: boolean;
}

function _LiveHeaderRoomInfo(props: _LiveHeaderRoomInfoProps) {
  const config = useAppConfig();
  return (
    <Row style={{ width: '100%' }}>
      <div data-testid="chat-header-room-avatar" style={{ display: 'flex', flexShrink: 0 }}>
        <Avatar
          label={props.avatarLabel}
          imageUrl={config.resolveAssetUrl(props.avatarUrl)}
          defaultAvatarKey={props.defaultAvatarKey}
          size={34}
          active={props.liveActive}
          activeBorderWidth={1.1}
        />
      </div>
      <SizedBox width={10} />
      <Expanded>
        <Column mainAxisAlignment="center" crossAxisAlignment="start">
          <Text
            data-testid="chat-header-room-title"
            maxLines={1}
            style={{ ...UiTypography.label, color: UiColors.text, fontWeight: 600, maxWidth: '100%' }}
          >
            {props.title}
          </Text>
          <SizedBox height={4} />
          <Text
            data-testid="chat-header-room-meta"
            maxLines={1}
            style={{ ...UiTypography.label, color: UiColors.textMuted, maxWidth: '100%' }}
          >
            {_roomMeta({ memberCount: props.memberCount, onlineMemberCount: props.onlineMemberCount })}
          </Text>
        </Column>
      </Expanded>
    </Row>
  );
}

function _LiveHeaderParticipantCount({ participantCount }: { participantCount: number }) {
  if (participantCount <= 0) return null;
  return (
    <Row data-testid="chat-header-live-preview" mainAxisSize="min" style={{ maxWidth: '100%' }}>
      <span data-testid="chat-header-live-preview-icon" style={{ display: 'flex', flexShrink: 0 }}>
        <Icon icon={Icons.volume_up} color={UiColors.accent} size={18} />
      </span>
      <SizedBox width={6} />
      <Text
        data-testid="chat-header-live-preview-count"
        maxLines={1}
        style={{ ...UiTypography.label, color: UiColors.white, fontWeight: 700, fontSize: 13, minWidth: 0 }}
      >
        {`${participantCount}`}
      </Text>
    </Row>
  );
}

function _LiveHeaderAvatarPreview({
  users,
  participantCount,
  maxWidth,
}: {
  users: readonly UserSummary[];
  participantCount: number;
  /** The LayoutBuilder's `constraints.maxWidth` (the slot's max width). */
  maxWidth: number;
}) {
  const config = useAppConfig();
  if (participantCount <= 0 || users.length === 0) return null;
  const exactCountLabel = `共 ${participantCount} 人`;
  const overflowCountLabel = `等共 ${participantCount} 人`;
  const labelStyle: CSSProperties = {
    ...UiTypography.label,
    color: UiColors.white,
    fontWeight: 600,
    fontSize: 11,
  };
  const avatarSize = 24.0;
  const iconSize = 18.0;
  const overlap = 8.0;
  const gap = 7.0;
  const measureLabelWidth = (label: string) => measureTextWidth(label, labelStyle);

  const maxVisible = Math.min(5, users.length);
  let visibleCount = 0;
  let countLabel = '';
  for (let count = maxVisible; count >= 1; count -= 1) {
    const avatarWidth = avatarSize + (count - 1) * (avatarSize - overlap);
    const candidateLabel = participantCount > count ? overflowCountLabel : exactCountLabel;
    const neededWidth = iconSize + gap + avatarWidth + gap + measureLabelWidth(candidateLabel);
    if (neededWidth <= maxWidth) {
      visibleCount = count;
      countLabel = candidateLabel;
      break;
    }
  }
  if (visibleCount === 0) return null;
  const visibleUsers = users.slice(0, visibleCount);
  const avatarWidth = avatarSize + (visibleUsers.length - 1) * (avatarSize - overlap);
  return (
    <Row data-testid="chat-header-live-preview" mainAxisSize="min" style={{ maxWidth: '100%' }}>
      <span data-testid="chat-header-live-preview-icon" style={{ display: 'flex', flexShrink: 0 }}>
        <Icon icon={Icons.volume_up} color={UiColors.accent} size={iconSize} />
      </span>
      <SizedBox width={gap} />
      <SizedBox width={avatarWidth} height={avatarSize} style={{ position: 'relative' }}>
        {visibleUsers.map((user, index) => (
          <div key={`${user.id}-${index}`} style={{ position: 'absolute', left: index * (avatarSize - overlap), top: 0 }}>
            <Avatar
              label={room_display.userAvatarLabel(user)}
              imageUrl={config.resolveAssetUrl(user.avatarUrl)}
              defaultAvatarKey={user.defaultAvatarKey}
              size={avatarSize}
              active
              activeBorderWidth={1}
            />
          </div>
        ))}
      </SizedBox>
      <SizedBox width={gap} />
      <Text data-testid="chat-header-live-preview-count" maxLines={1} style={{ ...labelStyle, minWidth: 0 }}>
        {countLabel}
      </Text>
    </Row>
  );
}

function _RoomHeaderActions({
  hasPendingJoinRequests,
  onMembersPressed,
  onSettingsPressed,
}: {
  hasPendingJoinRequests: boolean;
  onMembersPressed: () => void;
  onSettingsPressed: () => void;
}) {
  return (
    <SizedBox width={_headerActionButtonSize}>
      <Column mainAxisSize="min">
        <_HeaderIconButton
          tooltip="房间成员"
          icon={Icons.groups_outlined}
          showBadge={hasPendingJoinRequests}
          badgeKey="room-members-entry-badge"
          onPressed={onMembersPressed}
        />
        <SizedBox height={_headerActionGap} />
        <_HeaderIconButton tooltip="房间设置" icon={Icons.more_horiz} onPressed={onSettingsPressed} />
      </Column>
    </SizedBox>
  );
}

function _HeaderIconButton({
  tooltip,
  icon,
  onPressed,
  showBadge = false,
  badgeKey,
}: {
  tooltip: string;
  icon: IconData;
  onPressed: () => void;
  showBadge?: boolean;
  badgeKey?: string;
}) {
  return (
    <PressableSurface
      width={_headerActionButtonSize}
      height={_headerActionButtonSize}
      borderRadius={UiRadii.sm}
      tooltip={tooltip}
      padding={0}
      onPressed={onPressed}
      backgroundColor={UiColors.surface}
      borderColor={UiColors.border}
      aria-label={tooltip}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', flex: '1 1 0' }}>
        <div style={{ position: 'relative', display: 'flex' }}>
          <Icon icon={icon} size={16} color={UiColors.textSecondary} />
          {showBadge ? (
            <div data-testid={badgeKey} style={{ position: 'absolute', top: -5, right: -5 }}>
              <BadgeDot size={8} />
            </div>
          ) : null}
        </div>
      </div>
    </PressableSurface>
  );
}

export function _roomMeta(opts: { memberCount: number | null; onlineMemberCount: number | null }): string {
  const parts: string[] = [];
  const members = opts.memberCount ?? 0;
  if (members > 0) parts.push(`${members} 名成员`);
  const online = opts.onlineMemberCount ?? 0;
  if (online > 0) parts.push(`${online} 人在线`);
  return parts.length === 0 ? '就绪' : parts.join(' · ');
}
