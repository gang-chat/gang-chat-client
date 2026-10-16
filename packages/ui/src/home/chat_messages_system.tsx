import { Fragment, useState, type CSSProperties, type ReactNode } from 'react';

import * as live_display from '@gang-chat/core/app/live_display';
import * as message_display from '@gang-chat/core/app/message_display';
import * as room_display from '@gang-chat/core/app/room_display';
import type { CurrentUser, LiveState, Message, PublicRoom, UserSummary } from '@gang-chat/core/protocol/models';

import { EdgeInsets } from '../lib/edge_insets';
import { SizedBox, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { useMountedRef } from '../lib/use_mounted';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar } from '../ui/avatar';
import { UiContextMenuTriggerRegion } from '../ui/context_menu';
import { Icon, Icons, type IconData } from '../ui/icon';
import { ReadOnlySelectableText } from '../ui/read_only_text_box';
import { RoleBadge } from '../ui/role_badge';
import { UiTooltip } from '../ui/tooltip';
import { UiColors, UiCurves, UiTypography, withAlpha } from '../ui/tokens';
import { _senderName, _showChatMessageContextMenu, chatRoomUsernameColor, type ChatMessageActions } from './chat_messages_actions';
// Circular (chat_pane imports the message modules): read only at render time.
import { _messageMaxWidth } from './chat_pane';
import { _AvatarHoverCard } from './chat_profile_card';
import { HoverCardAnchor, HoverCardTapRegionScope, hoverInfoCardWidth } from './hover_card_anchor';
import type { RoomProfileResolver, UserProfileActionBuilder } from './room_profile_card';

/**
 * System messages and recalled / force-deleted messages from
 * `chat_messages.dart`, plus the message context-menu region they share.
 */

/**
 * Port of the Flutter `Alignment` constants these widgets accept (Dart
 * `AlignmentGeometry`). Only the horizontal part matters: the containers
 * shrink-wrap their height.
 */
export const Alignment = {
  topLeft: 'topLeft',
  topCenter: 'topCenter',
  topRight: 'topRight',
  centerLeft: 'centerLeft',
  center: 'center',
  centerRight: 'centerRight',
  bottomLeft: 'bottomLeft',
  bottomCenter: 'bottomCenter',
  bottomRight: 'bottomRight',
} as const;
export type AlignmentGeometry = (typeof Alignment)[keyof typeof Alignment];

/** Port of Flutter's `WrapAlignment`. */
export const WrapAlignment = {
  start: 'start',
  end: 'end',
  center: 'center',
  spaceBetween: 'spaceBetween',
  spaceAround: 'spaceAround',
  spaceEvenly: 'spaceEvenly',
} as const;
export type WrapAlignment = (typeof WrapAlignment)[keyof typeof WrapAlignment];

function _alignmentJustify(alignment: AlignmentGeometry): CSSProperties['justifyContent'] {
  if (alignment.endsWith('Left')) return 'flex-start';
  if (alignment.endsWith('Right')) return 'flex-end';
  return 'center';
}

function _wrapJustify(alignment: WrapAlignment): CSSProperties['justifyContent'] {
  switch (alignment) {
    case WrapAlignment.start:
      return 'flex-start';
    case WrapAlignment.end:
      return 'flex-end';
    case WrapAlignment.center:
      return 'center';
    case WrapAlignment.spaceBetween:
      return 'space-between';
    case WrapAlignment.spaceAround:
      return 'space-around';
    case WrapAlignment.spaceEvenly:
      return 'space-evenly';
  }
}

export interface ChatSystemMessageContentProps {
  message: Message;
  event: message_display.SystemMessageEvent;
  currentUser: CurrentUser;
  ownerUserId: string | null;
  live: LiveState | null;
  messageActions?: ChatMessageActions | null;
  enableContextMenu?: boolean;
  highlighted?: boolean;
  showSurface?: boolean;
  alignment?: AlignmentGeometry;
  wrapAlignment?: WrapAlignment;
  textStyle?: CSSProperties;
  avatarSize?: number;
  onResolveSenderProfile?: ((sender: UserSummary) => Promise<UserSummary>) | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterProfileRoom?: ((room: PublicRoom) => void) | null;
  profileActionBuilder?: UserProfileActionBuilder | null;
  /** DOM addition: Dart `Key`s became `data-testid`. */
  'data-testid'?: string;
}

/**
 * Renders a system message exactly as it appears in a text channel.
 *
 * Message-history and other secondary surfaces reuse this widget so inline
 * avatars, profile cards, role colors, and information actions stay
 * interactive instead of falling back to plain text.
 */
export function ChatSystemMessageContent({
  message,
  event,
  currentUser,
  ownerUserId,
  live,
  messageActions = null,
  enableContextMenu = true,
  highlighted = false,
  showSurface = true,
  alignment = Alignment.center,
  wrapAlignment = WrapAlignment.center,
  textStyle = UiTypography.label,
  avatarSize = 16,
  onResolveSenderProfile,
  onResolveRoomProfile,
  onEnterProfileRoom,
  profileActionBuilder,
  'data-testid': testId,
}: ChatSystemMessageContentProps) {
  return (
    <_SystemMessageContainer
      testId={testId}
      message={enableContextMenu ? message : null}
      actions={enableContextMenu ? messageActions : null}
      highlighted={highlighted}
      showSurface={showSurface}
      alignment={alignment}
      wrapAlignment={wrapAlignment}
    >
      {new _SystemMessageParts({
        event,
        currentUser,
        ownerUserId,
        live,
        textStyle,
        avatarSize,
        onResolveSenderProfile,
        onResolveRoomProfile,
        onEnterProfileRoom,
        profileActionBuilder,
      }).build()}
    </_SystemMessageContainer>
  );
}

export interface _SystemMessageContainerProps {
  children: readonly ReactNode[];
  message?: Message | null;
  actions?: ChatMessageActions | null;
  highlighted?: boolean;
  showSurface?: boolean;
  alignment?: AlignmentGeometry;
  wrapAlignment?: WrapAlignment;
  testId?: string;
}

export function _SystemMessageContainer({
  children,
  message = null,
  actions = null,
  highlighted = false,
  showSurface = true,
  alignment = Alignment.center,
  wrapAlignment = WrapAlignment.center,
  testId,
}: _SystemMessageContainerProps) {
  const [contextMenuActive, setContextMenuActiveState] = useState(false);
  const mounted = useMountedRef();
  const setContextMenuActive = (active: boolean) => {
    if (!mounted.current) return;
    setContextMenuActiveState(active);
  };

  const active = contextMenuActive;
  const contentColor = withAlpha(UiColors.surfacePressed, 0.82);
  const highlightColor = active ? UiColors.selected : highlighted ? withAlpha(UiColors.selected, 0.86) : contentColor;
  const parts = (
    // Wrap(spacing: 5, runSpacing: 5, crossAxisAlignment: center).
    <div
      style={{
        display: 'flex',
        flexDirection: 'row',
        flexWrap: 'wrap',
        justifyContent: _wrapJustify(wrapAlignment),
        alignItems: 'center',
        columnGap: 5,
        rowGap: 5,
        minWidth: 0,
      }}
    >
      {children.map((child, index) => (
        <Fragment key={index}>{child}</Fragment>
      ))}
    </div>
  );
  const duration = highlighted ? 0 : 90;
  const content = showSurface ? (
    <div
      style={{
        boxSizing: 'border-box',
        backgroundColor: highlightColor,
        borderRadius: 999,
        border: `1px solid ${active || highlighted ? UiColors.selectedBorder : UiColors.border}`,
        padding: '6px 10px',
        transition:
          duration === 0
            ? 'none'
            : `background-color ${duration}ms ${UiCurves.easeOutCubic}, border-color ${duration}ms ${UiCurves.easeOutCubic}`,
      }}
    >
      {/* The inner fill doubles the translucent resting colour, like the Dart DecoratedBox. */}
      <div style={{ backgroundColor: highlightColor, borderRadius: 999 }}>{parts}</div>
    </div>
  ) : (
    parts
  );
  const wrappedContent =
    message == null || actions == null ? (
      content
    ) : (
      <_MessageContextMenuRegion message={message} actions={actions} onContextMenuActiveChanged={setContextMenuActive}>
        {content}
      </_MessageContextMenuRegion>
    );

  return (
    <div data-testid={testId} style={{ display: 'flex', flexDirection: 'row', justifyContent: _alignmentJustify(alignment), minWidth: 0 }}>
      <div style={{ flex: '0 1 auto', minWidth: 0, maxWidth: _messageMaxWidth + 96 }}>{wrappedContent}</div>
    </div>
  );
}

export interface ChatRemovedMessageContentProps {
  message: Message;
  currentUser: CurrentUser;
  ownerUserId: string | null;
  live: LiveState | null;
  messageActions: ChatMessageActions;
  enableContextMenu?: boolean;
  highlighted?: boolean;
  showSurface?: boolean;
  alignment?: AlignmentGeometry;
  wrapAlignment?: WrapAlignment;
  onResolveSenderProfile?: ((sender: UserSummary) => Promise<UserSummary>) | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterProfileRoom?: ((room: PublicRoom) => void) | null;
  profileActionBuilder?: UserProfileActionBuilder | null;
  /** DOM addition: Dart `Key`s became `data-testid`. */
  'data-testid'?: string;
}

/** Renders a recalled or force-deleted message with the text-channel layout. */
export function ChatRemovedMessageContent(props: ChatRemovedMessageContentProps) {
  const {
    message,
    messageActions,
    enableContextMenu = true,
    highlighted = false,
    showSurface = true,
    alignment = Alignment.center,
    wrapAlignment = WrapAlignment.center,
  } = props;
  return (
    <_SystemMessageContainer
      testId={props['data-testid']}
      message={enableContextMenu ? message : null}
      actions={enableContextMenu ? messageActions : null}
      highlighted={highlighted}
      showSurface={showSurface}
      alignment={alignment}
      wrapAlignment={wrapAlignment}
    >
      {_buildRemovedMessageParts(props)}
    </_SystemMessageContainer>
  );
}

function _buildRemovedMessageParts(props: ChatRemovedMessageContentProps): ReactNode[] {
  const { message } = props;
  const userChip = (user: UserSummary) => (
    <_SystemUserChip
      user={user}
      currentUser={props.currentUser}
      ownerUserId={props.ownerUserId}
      onResolveProfile={props.onResolveSenderProfile}
      onResolveRoomProfile={props.onResolveRoomProfile}
      onEnterProfileRoom={props.onEnterProfileRoom}
      profileActionBuilder={props.profileActionBuilder}
      inLive={live_display.liveParticipantByUserId(props.live, user.id) != null}
    />
  );

  if (message.isForceDeleted) {
    const actor = message.forceDeletedBy;
    return [...(actor != null ? [userChip(actor)] : []), _systemText(actor == null ? '消息已被删除' : '删除了一条消息')];
  }

  const actor = message.recalledBy ?? message.sender;
  const recalledOwnMessage = actor.id === message.sender.id;
  const action = _removedTextAction(props);
  return [
    userChip(actor),
    ...(recalledOwnMessage
      ? [_systemTextWithTrailingAction('撤回了一条消息', action)]
      : [_systemText('撤回了一条来自'), userChip(message.sender), _systemTextWithTrailingAction('的消息', action)]),
  ];
}

function _removedTextAction({ message, messageActions }: ChatRemovedMessageContentProps): ReactNode | null {
  if (!message.isRecalled || message.type !== 'text') return null;
  if (message.body.length === 0) return null;
  if (messageActions.canReeditRecalledText(message)) {
    return (
      <_RemovedMessageInlineButton
        testId={`message-reedit-${message.id}`}
        icon={Icons.edit_outlined}
        tooltip="重新编辑"
        onPressed={() => messageActions.onReeditRecalledText(message)}
      />
    );
  }
  if (messageActions.canInspectRecalledText(message)) {
    return <_SystemInfoButton testId={`message-info-${message.id}`} message={message.body} />;
  }
  return null;
}

export function _RemovedMessageInlineButton({
  icon,
  tooltip,
  onPressed = null,
  testId,
}: {
  icon: IconData;
  tooltip: string;
  onPressed?: (() => void) | null;
  testId?: string;
}) {
  const enabled = onPressed != null;
  return (
    <UiTooltip message={tooltip} waitDuration={350}>
      <div
        data-testid={testId}
        onClick={(event) => {
          if (event.button !== 0) return;
          onPressed?.();
        }}
        style={{
          width: 16,
          height: 16,
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
        }}
      >
        <Icon icon={icon} size={14} color={UiColors.accent} />
      </div>
    </UiTooltip>
  );
}

export function _SystemInfoButton({ message, testId }: { message: string; testId?: string }) {
  return (
    <HoverCardAnchor
      resetKey={message}
      cardWidth={hoverInfoCardWidth(message)}
      gap={8}
      cardBuilder={() => <_SystemInfoCard message={message} />}
    >
      <div
        data-testid={testId}
        style={{
          width: 16,
          height: 16,
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: SystemMouseCursors.click,
        }}
      >
        <Icon icon={Icons.info_outline} size={14} color={UiColors.accent} />
      </div>
    </HoverCardAnchor>
  );
}

export function _SystemInfoCard({ message }: { message: string }) {
  const hoverScope = HoverCardTapRegionScope.maybeOf();
  return (
    <div style={{ padding: '10px 12px' }}>
      <ReadOnlySelectableText
        value={message}
        maxLines={8}
        style={{ ...UiTypography.body, color: UiColors.text, fontSize: 12, lineHeight: 1.38 }}
        contextMenuTapRegionGroupId={hoverScope?.tapRegionGroup}
        onContextMenuOpenChanged={hoverScope?.onOverlayActivityChanged ?? null}
      />
    </div>
  );
}

export function _MessageContextMenuRegion({
  message,
  actions,
  children,
  onContextMenuActiveChanged = null,
}: {
  message: Message;
  actions: ChatMessageActions;
  children?: ReactNode;
  onContextMenuActiveChanged?: ((active: boolean) => void) | null;
}) {
  return (
    <UiContextMenuTriggerRegion
      onTriggered={(position) => {
        onContextMenuActiveChanged?.(true);
        void _showChatMessageContextMenu({ position, message, actions }).finally(() => onContextMenuActiveChanged?.(false));
      }}
    >
      {children}
    </UiContextMenuTriggerRegion>
  );
}

/** Wrap children ellipsize at the run width instead of overflowing it. */
const _wrapTextStyle: CSSProperties = { maxWidth: '100%', flex: '0 1 auto' };

export function _systemText(value: string): ReactNode {
  return (
    <Text maxLines={1} style={{ ...UiTypography.label, color: UiColors.textSecondary, fontSize: 12, ..._wrapTextStyle }}>
      {value}
    </Text>
  );
}

export function _systemTextWithTrailingAction(value: string, action: ReactNode | null): ReactNode {
  if (action == null) return _systemText(value);
  return (
    <div style={_inlineRowStyle}>
      {_systemText(value)}
      {action}
    </div>
  );
}

const _inlineRowStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'row',
  alignItems: 'center',
  width: 'fit-content',
  maxWidth: '100%',
  minWidth: 0,
};

export class _SystemMessageParts {
  constructor(opts: {
    event: message_display.SystemMessageEvent;
    currentUser: CurrentUser;
    ownerUserId: string | null;
    live: LiveState | null;
    textStyle?: CSSProperties;
    avatarSize?: number;
    onResolveSenderProfile?: ((sender: UserSummary) => Promise<UserSummary>) | null;
    onResolveRoomProfile?: RoomProfileResolver | null;
    onEnterProfileRoom?: ((room: PublicRoom) => void) | null;
    profileActionBuilder?: UserProfileActionBuilder | null;
  }) {
    this.event = opts.event;
    this.currentUser = opts.currentUser;
    this.ownerUserId = opts.ownerUserId;
    this.live = opts.live;
    this.textStyle = opts.textStyle ?? UiTypography.label;
    this.avatarSize = opts.avatarSize ?? 16;
    this.onResolveSenderProfile = opts.onResolveSenderProfile ?? null;
    this.onResolveRoomProfile = opts.onResolveRoomProfile ?? null;
    this.onEnterProfileRoom = opts.onEnterProfileRoom ?? null;
    this.profileActionBuilder = opts.profileActionBuilder ?? null;
  }

  readonly event: message_display.SystemMessageEvent;
  readonly currentUser: CurrentUser;
  readonly ownerUserId: string | null;
  readonly live: LiveState | null;
  readonly textStyle: CSSProperties;
  readonly avatarSize: number;
  readonly onResolveSenderProfile: ((sender: UserSummary) => Promise<UserSummary>) | null;
  readonly onResolveRoomProfile: RoomProfileResolver | null;
  readonly onEnterProfileRoom: ((room: PublicRoom) => void) | null;
  readonly profileActionBuilder: UserProfileActionBuilder | null;

  build(): ReactNode[] {
    const event = this.event;
    const subject = event.subject;
    switch (event.event) {
      case message_display.kSystemEventRoomMemberJoined:
        return [this._userChip(subject), this._text('加入了房间')];
      case message_display.kSystemEventRoomMemberLeft:
        return [this._userChip(subject), this._text('离开了房间')];
      case message_display.kSystemEventRoomMemberRemoved: {
        const actor = event.actor;
        return [
          this._userChip(subject),
          ...(actor == null ? [this._text('被踢出了房间')] : [this._text('被'), this._userChip(actor), this._text('踢出了房间')]),
        ];
      }
      case message_display.kSystemEventLiveJoined:
        return [this._userChip(subject), this._text('进入了语音频道')];
      case message_display.kSystemEventLiveLeft:
        return [this._userChip(subject), this._text('退出了语音频道')];
      case message_display.kSystemEventRoomRoleChanged: {
        const actor = event.actor;
        const roleLabel = message_display.systemMessageRoleLabel(event.toRole);
        const verb = message_display.systemMessageRoleVerb(event);
        const omitActor = message_display.systemMessageRoleChangeOmitsActor(event);
        return [
          this._userChip(subject, { roleOverride: event.toRole }),
          ...(!omitActor && actor != null ? [this._text('被'), this._userChip(actor)] : []),
          this._text(verb),
          <RoleBadge
            label={roleLabel}
            padding={EdgeInsets.symmetric({ horizontal: 7, vertical: 2 })}
            fontSize={11}
            fontWeight={700}
          />,
        ];
      }
      case message_display.kSystemEventRoomNameChanged: {
        const actor = event.actor ?? event.user;
        return [
          this._textWithInfoButton('房间名称', `原房间名称：${event.oldValue ?? ''}`),
          ...this._changedBy(actor),
          this._highlightText(this._systemChangedValueLabel(event.newValue)),
        ];
      }
      case message_display.kSystemEventRoomDescriptionChanged: {
        const actor = event.actor ?? event.user;
        return [
          this._textWithInfoButton('房间简介', `原房间简介：${event.oldValue ?? ''}`),
          ...this._changedBy(actor),
          this._lineBreak(),
          this._highlightText(this._systemChangedValueLabel(event.newValue), { maxLines: 4 }),
        ];
      }
      case message_display.kSystemEventRoomVisibilityChanged: {
        const actor = event.actor ?? event.user;
        return [
          this._textWithInfoButton('房间可见性', `原可见性：${message_display.systemMessageVisibilityLabel(event.oldValue)}`),
          ...this._changedBy(actor),
          this._highlightText(message_display.systemMessageVisibilityLabel(event.newValue)),
        ];
      }
      case message_display.kSystemEventRoomJoinPolicyChanged: {
        const actor = event.actor ?? event.user;
        return [
          this._textWithInfoButton('房间加入方式', `原加入方式：${message_display.systemMessageJoinPolicyLabel(event.oldValue)}`),
          ...this._changedBy(actor),
          this._highlightText(message_display.systemMessageJoinPolicyLabel(event.newValue)),
        ];
      }
      default: {
        const fallback = event.message.body.trim();
        return [this._userChip(subject), ...(fallback.length > 0 ? [this._text(fallback)] : [])];
      }
    }
  }

  /** `if (actor == null) 修改为 else 被 <actor> 修改为`. */
  private _changedBy(actor: UserSummary | null): ReactNode[] {
    if (actor == null) return [this._text('修改为')];
    return [this._text('被'), this._userChip(actor), this._text('修改为')];
  }

  _userChip(user: UserSummary, opts: { roleOverride?: string | null } = {}): ReactNode {
    return (
      <_SystemUserChip
        user={user}
        roleOverride={opts.roleOverride ?? null}
        currentUser={this.currentUser}
        ownerUserId={this.ownerUserId}
        onResolveProfile={this.onResolveSenderProfile}
        onResolveRoomProfile={this.onResolveRoomProfile}
        onEnterProfileRoom={this.onEnterProfileRoom}
        profileActionBuilder={this.profileActionBuilder}
        inLive={live_display.liveParticipantByUserId(this.live, user.id) != null}
        textStyle={this.textStyle}
        avatarSize={this.avatarSize}
      />
    );
  }

  _text(value: string): ReactNode {
    return (
      <Text maxLines={1} style={{ ...this.textStyle, color: UiColors.textSecondary, ..._wrapTextStyle }}>
        {value}
      </Text>
    );
  }

  _textWithInfoButton(value: string, info: string): ReactNode {
    return (
      <div style={_inlineRowStyle}>
        {this._text(value)}
        {this._infoButton(info)}
      </div>
    );
  }

  _infoButton(value: string): ReactNode {
    return <_SystemInfoButton testId={`system-info-${this.event.message.clientMessageId}-${this.event.event}`} message={value} />;
  }

  _highlightText(value: string, opts: { maxLines?: number } = {}): ReactNode {
    const { maxLines = 1 } = opts;
    return (
      <Text
        maxLines={maxLines}
        style={{
          ...this.textStyle,
          color: UiColors.accent,
          fontWeight: 700,
          maxWidth: `min(${_messageMaxWidth}px, 100%)`,
          flex: '0 1 auto',
        }}
      >
        {value}
      </Text>
    );
  }

  _lineBreak(): ReactNode {
    // SizedBox(width: _messageMaxWidth + 96, height: 0): wider than any run,
    // so it takes a run of its own (and widens the shrink-wrapped pill to its
    // max width, as the Flutter Wrap measured it).
    return <div style={{ width: _messageMaxWidth + 96, maxWidth: '100%', height: 0, flexShrink: 0 }} />;
  }

  _systemChangedValueLabel(value: string | null): string {
    const normalized = value ?? '';
    if (normalized.length === 0) return '（空）';
    return normalized;
  }
}

export interface _SystemUserChipProps {
  user: UserSummary;
  roleOverride?: string | null;
  currentUser: CurrentUser;
  ownerUserId: string | null;
  inLive: boolean;
  textStyle?: CSSProperties;
  avatarSize?: number;
  onResolveProfile?: ((sender: UserSummary) => Promise<UserSummary>) | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterProfileRoom?: ((room: PublicRoom) => void) | null;
  profileActionBuilder?: UserProfileActionBuilder | null;
}

export function _SystemUserChip({
  user,
  roleOverride = null,
  currentUser,
  ownerUserId,
  inLive,
  textStyle = UiTypography.label,
  avatarSize = 16,
  onResolveProfile,
  onResolveRoomProfile,
  onEnterProfileRoom,
  profileActionBuilder,
}: _SystemUserChipProps) {
  const config = useAppConfig();
  const name = _senderName(user);
  const colorUser = roleOverride == null ? user : user.copyWith({ roomRole: roleOverride });
  const avatar = (
    <Avatar
      label={room_display.userAvatarLabel(user)}
      imageUrl={config.resolveAssetUrl(user.avatarUrl)}
      defaultAvatarKey={user.defaultAvatarKey}
      size={avatarSize}
      activeBorderWidth={1}
    />
  );
  return (
    <div style={_inlineRowStyle}>
      <_AvatarHoverCard
        user={user}
        currentUser={currentUser}
        onResolveProfile={onResolveProfile}
        onResolveRoomProfile={onResolveRoomProfile}
        onEnterCommonRoom={onEnterProfileRoom}
        profileActionBuilder={profileActionBuilder}
        inLive={inLive}
      >
        {avatar}
      </_AvatarHoverCard>
      <SizedBox width={4} />
      <Text
        maxLines={1}
        style={{
          ...textStyle,
          color: chatRoomUsernameColor({ user: colorUser, currentUser, ownerUserId }),
          fontWeight: 600,
          maxWidth: 128,
        }}
      >
        {name}
      </Text>
    </div>
  );
}
