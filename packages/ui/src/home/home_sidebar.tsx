import { useEffect, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent, type ReactNode, type RefObject } from 'react';

import * as network_latency from '@gang-chat/core/app/network_latency';
import * as room_display from '@gang-chat/core/app/room_display';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import type { CurrentUser, RoomCard } from '@gang-chat/core/protocol/models';

import { EdgeInsets } from '../lib/edge_insets';
import { Center, Column, Expanded, Flexible, Row, SizedBox, Spacer, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { useElementSize } from '../lib/use_element_size';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar } from '../ui/avatar';
import { ButtonIcon } from '../ui/button';
import { FloatingNotice, FloatingNoticeEmitter, FloatingNoticeTone } from '../ui/feedback';
import { HighlightedText } from '../ui/highlighted_text';
import { Icon, Icons, type IconData } from '../ui/icon';
import { LatencySignalBadge } from '../ui/latency_signal';
import { PressableSurface } from '../ui/surface';
import { UiTooltip } from '../ui/tooltip';
import { UiColors, UiRadii, UiTypography, argb, withAlpha } from '../ui/tokens';
import { WindowChromeInsets } from '../ui/window_chrome';
import { sidebarHorizontalPadding } from './navigation';

const _sidebarTopPadding = 16.0;
const _sidebarBottomPadding = 16.0;
const _homeSidebarHeaderHeight = 30.0;
const _homeSidebarHeaderGap = 14.0;
const _serverCardHeight = 68.0;
const _serverCardGap = 10.0;
const _serverCardHorizontalPadding = 10.0;
const _serverListScrollbarGutter = 8.0;
const _footerButtonSize = 38.0;
const _footerButtonGap = 8.0;
const _footerButtonOuterHeight = _footerButtonSize + 3.0 + 5.0;
const _compactFooterBreakpoint = 130.0;
const _compactSummaryBreakpoint = 88.0;
const _latencySignalGoodColor = argb(0xff26b36f);
const _latencySignalFairColor = argb(0xffe0a12a);
const _latencySignalPoorColor = argb(0xffe25a5a);

/** `Shadow(color: 0xAA0F1115, blurRadius: 3, offset: (0, 1))` on the card glyphs. */
const _glyphShadow = `0px 1px 3px ${argb(0xaa0f1115)}`;

/**
 * A `DecoratedBox` border: Flutter paints it over the child without insetting
 * it, so it is drawn as an inset shadow here instead of a CSS border (which
 * would shift the content by its width).
 */
function _decoratedBorder(color: string, width = 1): string {
  return `inset 0 0 0 ${width}px ${color}`;
}

export interface HomeSidebarProps {
  width: number;
  currentUser: CurrentUser;
  servers: readonly RoomCard[];
  timestampNow: Date;
  /** Draft text per room id (Dart `Map<String, String>`). */
  roomDrafts?: ReadonlyMap<string, string> | Readonly<Record<string, string>>;
  selectedServerId: string | null;
  joinedLiveRoomId: string | null;
  realtimeReconnecting: boolean;
  /** Milliseconds (Dart `Duration?`). */
  requestRoundTrip?: number | null;
  searchQuery: string;
  loading: boolean;
  error: string | null;
  settingsActive: boolean;
  createRoomActive: boolean;
  notificationsActive: boolean;
  logoutActive: boolean;
  hasPendingNotifications: boolean;
  pendingNotificationCount: number;
  onServerSelected: (server: RoomCard) => void;
  onCreateRoom: () => void;
  onOpenNotifications: () => void;
  onOpenSettings: () => void;
  onLogout: () => void;
  includeWindowChromeOffset?: boolean;
  header?: ReactNode;
  bodyOverride?: ReactNode;
  footerMiddle?: ReactNode;
}

function _present(node: ReactNode): boolean {
  return node != null && node !== false;
}

function _draftFor(drafts: HomeSidebarProps['roomDrafts'], roomId: string): string | null {
  if (drafts == null) return null;
  if (drafts instanceof Map) return drafts.get(roomId) ?? null;
  return (drafts as Readonly<Record<string, string>>)[roomId] ?? null;
}

export function HomeSidebar(props: HomeSidebarProps) {
  const { width, includeWindowChromeOffset = true } = props;
  const header = _present(props.header) ? props.header : null;
  const bodyOverride = _present(props.bodyOverride) ? props.bodyOverride : null;
  const chrome = WindowChromeInsets.of();
  const topChromeOffset = includeWindowChromeOffset ? chrome.sidebarTopOffset : 0.0;
  const layoutRef = useRef<HTMLDivElement | null>(null);
  const layoutSize = useElementSize(layoutRef);

  // LayoutBuilder: the height left for the body below the optional header.
  const headerHeight = header == null ? 0.0 : _homeSidebarHeaderHeight + _homeSidebarHeaderGap;
  const availableBodyHeight = (layoutSize?.height ?? Infinity) - headerHeight;
  const showSummary = availableBodyHeight >= _compactSummaryBreakpoint;
  const showFooter = availableBodyHeight >= _compactFooterBreakpoint;
  const gutter: CSSProperties = { paddingRight: _serverListScrollbarGutter, boxSizing: 'border-box' };

  return (
    <div
      style={{
        width,
        height: '100%',
        flexShrink: 0,
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: UiColors.surfaceLow,
        boxShadow: `inset -1px 0 0 ${UiColors.border}`,
        paddingLeft: sidebarHorizontalPadding,
        paddingTop: _sidebarTopPadding + topChromeOffset,
        paddingRight: sidebarHorizontalPadding - _serverListScrollbarGutter,
        paddingBottom: _sidebarBottomPadding,
      }}
    >
      <div ref={layoutRef} style={{ flex: '1 1 0', minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        <Column crossAxisAlignment="stretch" style={{ flex: '1 1 0', minHeight: 0 }}>
          {showSummary ? (
            <>
              <div style={gutter}>
                <_UserSummaryBar
                  user={props.currentUser}
                  inLive={props.joinedLiveRoomId != null}
                  reconnecting={props.realtimeReconnecting}
                  requestRoundTrip={props.requestRoundTrip ?? null}
                  logoutActive={props.logoutActive}
                  onLogout={props.onLogout}
                />
              </div>
              <SizedBox height={header != null || showFooter ? _homeSidebarHeaderGap : 10} />
            </>
          ) : null}
          {header != null ? (
            <>
              <div style={gutter}>
                <SizedBox height={_homeSidebarHeaderHeight} width="100%">
                  {header}
                </SizedBox>
              </div>
              <SizedBox height={_homeSidebarHeaderGap} />
            </>
          ) : null}
          {bodyOverride != null ? (
            <Expanded style={gutter}>{bodyOverride}</Expanded>
          ) : (
            <>
              <Expanded>{_buildServerList(props)}</Expanded>
              {showFooter ? (
                <>
                  <SizedBox height={12} />
                  <div style={gutter}>
                    <_SidebarFooter
                      settingsActive={props.settingsActive}
                      createRoomActive={props.createRoomActive}
                      notificationsActive={props.notificationsActive}
                      hasPendingNotifications={props.hasPendingNotifications}
                      pendingNotificationCount={props.pendingNotificationCount}
                      middle={_present(props.footerMiddle) ? props.footerMiddle : null}
                      onCreateRoom={props.onCreateRoom}
                      onOpenNotifications={props.onOpenNotifications}
                      onOpenSettings={props.onOpenSettings}
                    />
                  </div>
                </>
              ) : null}
            </>
          )}
        </Column>
      </div>
    </div>
  );
}

function _buildServerList(props: HomeSidebarProps): ReactNode {
  const { servers, loading, error } = props;
  if (servers.length === 0 && loading) {
    return (
      <Center style={{ flex: '1 1 0' }}>
        <CircularProgressIndicator size={22} strokeWidth={2.2} color={UiColors.accent} />
      </Center>
    );
  }

  if (servers.length === 0 && error != null) {
    return (
      <FloatingNoticeEmitter notices={[new FloatingNotice({ message: error, tone: FloatingNoticeTone.error, duration: null })]}>
        <Center style={{ flex: '1 1 0' }}>
          <Icon icon={Icons.error_outline} color={UiColors.danger} size={22} />
        </Center>
      </FloatingNoticeEmitter>
    );
  }

  return (
    <_ServerList
      servers={servers}
      timestampNow={props.timestampNow}
      roomDrafts={props.roomDrafts}
      selectedServerId={props.selectedServerId}
      joinedLiveRoomId={props.joinedLiveRoomId}
      searchQuery={props.searchQuery}
      onServerSelected={props.onServerSelected}
    />
  );
}

function _ServerList({
  servers,
  timestampNow,
  roomDrafts,
  selectedServerId,
  joinedLiveRoomId,
  searchQuery,
  onServerSelected,
}: {
  servers: readonly RoomCard[];
  timestampNow: Date;
  roomDrafts: HomeSidebarProps['roomDrafts'];
  selectedServerId: string | null;
  joinedLiveRoomId: string | null;
  searchQuery: string;
  onServerSelected: (server: RoomCard) => void;
}) {
  const controller = useRef<HTMLDivElement | null>(null);
  const cancelTouchPressOnDrag = HostPlatform.isAndroid;
  return (
    <_RawScrollbar
      testId="home-sidebar-room-scrollbar"
      controller={controller}
      thickness={7}
      thumbColor={withAlpha(UiColors.textMuted, 0.82)}
    >
      {/* ScrollConfiguration(scrollbars: false): no native scrollbar. */}
      <div
        ref={controller}
        data-testid="home-sidebar-room-list"
        style={{
          height: '100%',
          overflowX: 'hidden',
          overflowY: 'auto',
          scrollbarWidth: 'none',
          paddingRight: _serverListScrollbarGutter,
          boxSizing: 'border-box',
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: _serverCardGap }}>
          {servers.map((server) => (
            <_ServerCard
              key={server.id}
              server={server}
              timestampNow={timestampNow}
              draft={_draftFor(roomDrafts, server.id)}
              selected={server.id === selectedServerId}
              voiceJoined={server.id === joinedLiveRoomId}
              searchQuery={searchQuery}
              cancelTouchPressOnDrag={cancelTouchPressOnDrag}
              onPressed={() => onServerSelected(server)}
            />
          ))}
        </div>
      </div>
    </_RawScrollbar>
  );
}

const _scrollbarFadeDuration = 300;
const _scrollbarTimeToFade = 600;
const _scrollbarMinThumbLength = 18;

/**
 * Port of `RawScrollbar(interactive: true, radius: 999, thickness, thumbColor)`
 * over the scroll view behind [controller]: the thumb fades in while the list
 * scrolls and out [_scrollbarTimeToFade] ms after it stops, can be dragged,
 * and a press on the visible track pages by 80% of the viewport. It is drawn
 * over the list's trailing gutter like Flutter's scrollbar painter.
 */
function _RawScrollbar({
  controller,
  thickness,
  thumbColor,
  testId,
  children,
}: {
  controller: RefObject<HTMLDivElement | null>;
  thickness: number;
  thumbColor: string;
  testId?: string;
  children: ReactNode;
}) {
  const [metrics, setMetrics] = useState<{ offset: number; extent: number; track: number } | null>(null);
  const [visible, setVisible] = useState(false);
  const fadeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const drag = useRef<{ pointerId: number; startY: number; startScroll: number } | null>(null);

  const measure = () => {
    const node = controller.current;
    if (node == null) return null;
    const viewport = node.clientHeight;
    const content = node.scrollHeight;
    const maxScroll = Math.max(0, content - viewport);
    if (maxScroll <= 0 || viewport <= 0) return null;
    const fractionVisible = Math.min(Math.max(viewport / content, 0), 1);
    const extent = Math.max(viewport * fractionVisible, _scrollbarMinThumbLength);
    const offset = (node.scrollTop / maxScroll) * (viewport - extent);
    return { offset, extent, track: viewport };
  };

  const scheduleFade = () => {
    if (fadeTimer.current != null) clearTimeout(fadeTimer.current);
    fadeTimer.current = setTimeout(() => {
      fadeTimer.current = null;
      if (drag.current == null) setVisible(false);
    }, _scrollbarTimeToFade);
  };

  useEffect(() => {
    const node = controller.current;
    if (node == null) return;
    const update = () => setMetrics(measure());
    const onScroll = () => {
      update();
      setVisible(true);
      scheduleFade();
    };
    update();
    node.addEventListener('scroll', onScroll, { passive: true });
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(update);
    observer?.observe(node);
    if (node.firstElementChild != null) observer?.observe(node.firstElementChild);
    return () => {
      node.removeEventListener('scroll', onScroll);
      observer?.disconnect();
      if (fadeTimer.current != null) clearTimeout(fadeTimer.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [controller]);

  const onThumbPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    const node = controller.current;
    if (node == null || event.button !== 0) return;
    event.preventDefault();
    event.stopPropagation();
    event.currentTarget.setPointerCapture(event.pointerId);
    drag.current = { pointerId: event.pointerId, startY: event.clientY, startScroll: node.scrollTop };
    if (fadeTimer.current != null) clearTimeout(fadeTimer.current);
    setVisible(true);
  };
  const onThumbPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const node = controller.current;
    const current = drag.current;
    const m = metrics;
    if (node == null || current == null || current.pointerId !== event.pointerId || m == null) return;
    const maxScroll = Math.max(0, node.scrollHeight - node.clientHeight);
    const travel = Math.max(1, m.track - m.extent);
    node.scrollTop = current.startScroll + ((event.clientY - current.startY) * maxScroll) / travel;
  };
  const onThumbPointerUp = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (drag.current?.pointerId !== event.pointerId) return;
    drag.current = null;
    scheduleFade();
  };
  const onTrackPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    const node = controller.current;
    const m = metrics;
    if (node == null || m == null || event.button !== 0) return;
    const rect = event.currentTarget.getBoundingClientRect();
    const y = event.clientY - rect.top;
    if (y >= m.offset && y <= m.offset + m.extent) return;
    event.preventDefault();
    const page = node.clientHeight * 0.8;
    node.scrollBy({ top: y < m.offset ? -page : page, behavior: 'smooth' });
  };

  const shown = visible && metrics != null;
  return (
    <div data-testid={testId} style={{ position: 'relative', flex: '1 1 0', minHeight: 0, display: 'flex', flexDirection: 'column' }}>
      {children}
      {metrics != null ? (
        <div
          onPointerDown={onTrackPointerDown}
          style={{
            position: 'absolute',
            top: 0,
            right: 0,
            bottom: 0,
            width: thickness,
            pointerEvents: shown ? 'auto' : 'none',
          }}
        >
          <div
            onPointerDown={onThumbPointerDown}
            onPointerMove={onThumbPointerMove}
            onPointerUp={onThumbPointerUp}
            onPointerCancel={onThumbPointerUp}
            style={{
              position: 'absolute',
              top: metrics.offset,
              right: 0,
              width: thickness,
              height: metrics.extent,
              borderRadius: 999,
              backgroundColor: thumbColor,
              opacity: shown ? 1 : 0,
              transition: `opacity ${_scrollbarFadeDuration}ms linear`,
            }}
          />
        </div>
      ) : null}
    </div>
  );
}

function _SidebarFooter({
  settingsActive,
  createRoomActive,
  notificationsActive,
  hasPendingNotifications,
  pendingNotificationCount,
  middle,
  onCreateRoom,
  onOpenNotifications,
  onOpenSettings,
}: {
  settingsActive: boolean;
  createRoomActive: boolean;
  notificationsActive: boolean;
  hasPendingNotifications: boolean;
  pendingNotificationCount: number;
  middle: ReactNode;
  onCreateRoom: () => void;
  onOpenNotifications: () => void;
  onOpenSettings: () => void;
}) {
  return (
    <SizedBox height={_footerButtonOuterHeight}>
      <Row style={{ height: '100%' }}>
        <ButtonIcon
          data-testid="home-sidebar-create-room-button"
          tooltip="创建房间"
          icon={<Icon icon={Icons.add_circle_outline} />}
          selected={createRoomActive}
          onPressed={onCreateRoom}
          size={_footerButtonSize}
        />
        <SizedBox width={_footerButtonGap} />
        <_NotificationFooterButton
          selected={notificationsActive}
          hasPending={hasPendingNotifications}
          count={pendingNotificationCount}
          onPressed={onOpenNotifications}
        />
        {middle == null ? (
          <Spacer />
        ) : (
          <>
            <SizedBox width={_footerButtonGap} />
            <Expanded style={{ alignItems: 'center', justifyContent: 'center' }}>{middle}</Expanded>
            <SizedBox width={_footerButtonGap} />
          </>
        )}
        <ButtonIcon
          data-testid="home-sidebar-settings-button"
          tooltip="设置"
          icon={<Icon icon={Icons.settings_outlined} />}
          selected={settingsActive}
          onPressed={onOpenSettings}
          size={_footerButtonSize}
        />
      </Row>
    </SizedBox>
  );
}

function _NotificationFooterButton({
  selected,
  hasPending,
  count,
  onPressed,
}: {
  selected: boolean;
  hasPending: boolean;
  count: number;
  onPressed: () => void;
}) {
  // The flat button is shorter than the footer row, so centre it in the row
  // like its create-room and settings siblings, and keep the badge pinned
  // to the button's own corner.
  return (
    <SizedBox width={_footerButtonSize} height={_footerButtonOuterHeight}>
      <Center style={{ width: '100%', height: '100%' }}>
        <SizedBox width={_footerButtonSize} height={_footerButtonSize} style={{ position: 'relative' }}>
          <ButtonIcon
            data-testid="home-sidebar-notifications-button"
            tooltip="通知"
            icon={<Icon icon={Icons.notifications_none} />}
            selected={selected}
            onPressed={onPressed}
            size={_footerButtonSize}
          />
          {hasPending ? (
            <div style={{ position: 'absolute', right: -4, top: -4 }}>
              <_UnreadBadge count={count <= 0 ? 1 : count} />
            </div>
          ) : null}
        </SizedBox>
      </Center>
    </SizedBox>
  );
}

function _UserSummaryBar({
  user,
  inLive,
  reconnecting,
  requestRoundTrip,
  logoutActive,
  onLogout,
}: {
  user: CurrentUser;
  inLive: boolean;
  reconnecting: boolean;
  requestRoundTrip: number | null;
  logoutActive: boolean;
  onLogout: () => void;
}) {
  const config = useAppConfig();
  const statusLabel = room_display.currentUserPresenceLabel(user, { inLive, reconnecting });
  const latencyQuality = network_latency.networkLatencyQuality(requestRoundTrip);
  const latencyColor = _latencySignalColor(latencyQuality);
  const latencyLabel = network_latency.networkLatencyTooltip(requestRoundTrip);
  return (
    <div
      data-testid="home-sidebar-user-summary"
      style={{
        height: 58,
        boxSizing: 'border-box',
        padding: '0 10px',
        backgroundColor: UiColors.surface,
        borderRadius: UiRadii.md,
        boxShadow: _decoratedBorder(UiColors.border),
        display: 'flex',
      }}
    >
      <Row style={{ flex: '1 1 0', minWidth: 0 }}>
        <SizedBox width={38} height={38} style={{ position: 'relative' }}>
          <Avatar
            label={room_display.userAvatarLabel(user.toSummary())}
            imageUrl={config.resolveAssetUrl(user.avatarUrl)}
            defaultAvatarKey={user.defaultAvatarKey}
            size={38}
            showBorder={false}
          />
          <div style={{ position: 'absolute', right: -3, bottom: -2 }}>
            <LatencySignalBadge
              activeBars={network_latency.networkLatencySignalBars(requestRoundTrip)}
              activeColor={latencyColor}
              tooltip={latencyLabel}
            />
          </div>
        </SizedBox>
        <SizedBox width={11} />
        <Expanded>
          <Column mainAxisAlignment="center" crossAxisAlignment="start">
            <Text maxLines={1} style={{ color: UiColors.text, fontSize: 13, fontWeight: 600, letterSpacing: 0, maxWidth: '100%' }}>
              {user.displayName}
            </Text>
            <SizedBox height={4} />
            <_SidebarPresenceLabel label={statusLabel} />
          </Column>
        </Expanded>
        <SizedBox width={8} />
        <_FlatIconButton
          tooltip="退出登录"
          icon={Icons.logout}
          color={logoutActive ? UiColors.accent : UiColors.textMuted}
          size={30}
          onPressed={onLogout}
        />
      </Row>
    </div>
  );
}

function _latencySignalColor(quality: network_latency.NetworkLatencyQuality): string {
  switch (quality) {
    case network_latency.NetworkLatencyQuality.good:
      return _latencySignalGoodColor;
    case network_latency.NetworkLatencyQuality.fair:
      return _latencySignalFairColor;
    case network_latency.NetworkLatencyQuality.poor:
      return _latencySignalPoorColor;
    case network_latency.NetworkLatencyQuality.unavailable:
      return UiColors.textMuted;
  }
}

/** Compact status label with a colored presence dot. */
function _SidebarPresenceLabel({ label }: { label: string }) {
  let color: string;
  switch (label) {
    case '语音':
      color = UiColors.presenceVoice;
      break;
    case '重连中':
      color = UiColors.presenceReconnecting;
      break;
    case '离线':
      color = UiColors.presenceOffline;
      break;
    default:
      color = UiColors.presenceOnline;
  }
  return (
    <Row mainAxisSize="min" style={{ maxWidth: '100%' }}>
      <div
        data-testid="home-sidebar-presence-dot"
        style={{ width: 6, height: 6, flexShrink: 0, borderRadius: '50%', backgroundColor: color }}
      />
      <SizedBox width={6} />
      <Flexible>
        <Text maxLines={1} style={{ ...UiTypography.label, color, fontWeight: 600 }}>
          {label}
        </Text>
      </Flexible>
    </Row>
  );
}

function _FlatIconButton({
  tooltip,
  icon,
  color,
  size,
  onPressed,
}: {
  tooltip: string;
  icon: IconData;
  color: string;
  size: number;
  onPressed: () => void;
}) {
  return (
    <UiTooltip message={tooltip}>
      <div
        role="button"
        aria-label={tooltip}
        onClick={(event) => {
          if (event.button !== 0) return;
          onPressed();
        }}
        style={{
          width: size,
          height: size,
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: SystemMouseCursors.click,
        }}
      >
        <Icon icon={icon} color={color} size={size * 0.54} />
      </div>
    </UiTooltip>
  );
}

function _ServerCard({
  server,
  timestampNow,
  draft,
  selected,
  voiceJoined,
  searchQuery,
  cancelTouchPressOnDrag,
  onPressed,
}: {
  server: RoomCard;
  timestampNow: Date;
  draft: string | null;
  selected: boolean;
  voiceJoined: boolean;
  searchQuery: string;
  cancelTouchPressOnDrag: boolean;
  onPressed: () => void;
}) {
  const lastMessageTime = room_display.roomSidebarLastMessageTime(server, { now: timestampNow });
  const draftPreview = room_display.roomDraftPreview(draft);
  const subtitle = room_display.roomSidebarSubtitle(server);
  const hasUnreadMention = server.unreadMentionCount > 0;
  const mutedLabelStyle: CSSProperties = {
    ...UiTypography.label,
    color: selected ? UiColors.textSecondary : UiColors.textMuted,
  };
  const accentLabelStyle: CSSProperties = { ...UiTypography.label, color: UiColors.accent, fontWeight: 700 };

  let subtitleLine: ReactNode;
  if (draftPreview == null) {
    subtitleLine = hasUnreadMention ? (
      <Text data-testid={`home-sidebar-room-mention-${server.id}`} maxLines={1} style={{ ...UiTypography.label, maxWidth: '100%' }}>
        <span style={accentLabelStyle}>{'[@我] '}</span>
        <span style={mutedLabelStyle}>{subtitle}</span>
      </Text>
    ) : (
      <HighlightedText text={subtitle} query={searchQuery} maxLines={1} overflow="ellipsis" style={{ ...mutedLabelStyle, maxWidth: '100%' }} />
    );
  } else {
    subtitleLine = (
      <Text data-testid={`home-sidebar-room-draft-${server.id}`} maxLines={1} style={{ ...UiTypography.label, maxWidth: '100%' }}>
        <span style={accentLabelStyle}>{'[草稿] '}</span>
        <span style={mutedLabelStyle}>{draftPreview}</span>
      </Text>
    );
  }

  return (
    <PressableSurface
      data-testid={`home-sidebar-room-${server.id}`}
      width={Infinity}
      height={_serverCardHeight}
      selected={selected}
      backgroundColor={UiColors.surfaceLow}
      selectedBackgroundColor={UiColors.selected}
      pressedBackgroundColor={UiColors.surfacePressed}
      borderColor={UiColors.border}
      selectedBorderColor={UiColors.selectedBorder}
      borderRadius={UiRadii.md}
      padding={EdgeInsets.symmetric({ horizontal: _serverCardHorizontalPadding })}
      cancelTouchPressOnDrag={cancelTouchPressOnDrag}
      onPressed={onPressed}
    >
      <div style={{ position: 'relative', width: '100%' }}>
        <Row>
          <_ServerAvatar server={server} selected={selected} voiceJoined={voiceJoined} />
          <SizedBox width={9} />
          <Expanded>
            <Column mainAxisAlignment="center" crossAxisAlignment="start">
              <Row style={{ width: '100%' }}>
                <Expanded>
                  <HighlightedText
                    text={server.displayName}
                    query={searchQuery}
                    maxLines={1}
                    overflow="ellipsis"
                    style={{ color: UiColors.text, fontSize: 13, fontWeight: 600, letterSpacing: 0 }}
                  />
                </Expanded>
                {lastMessageTime.length > 0 ? (
                  <>
                    <SizedBox width={8} />
                    <Text
                      maxLines={1}
                      style={{
                        ...UiTypography.label,
                        color: selected ? UiColors.textSecondary : UiColors.textMuted,
                        fontSize: 10.5,
                        flexShrink: 0,
                      }}
                    >
                      {lastMessageTime}
                    </Text>
                  </>
                ) : null}
              </Row>
              <SizedBox height={4} />
              {subtitleLine}
            </Column>
          </Expanded>
        </Row>
        {server.isPinned ? (
          <div
            data-testid={`home-sidebar-room-pinned-${server.id}`}
            style={{ position: 'absolute', top: 2, left: 2 - _serverCardHorizontalPadding, display: 'flex', pointerEvents: 'none' }}
          >
            <Icon icon={Icons.push_pin} color={UiColors.text} size={14} style={{ textShadow: _glyphShadow }} />
          </div>
        ) : null}
      </div>
    </PressableSurface>
  );
}

function _ServerAvatar({ server, selected, voiceJoined }: { server: RoomCard; selected: boolean; voiceJoined: boolean }) {
  const config = useAppConfig();
  const hasLiveParticipants = server.liveParticipantCount > 0;
  const notificationPolicy = room_display.normalizeRoomNotificationPolicy(server.notificationPolicy);
  const mutedBadge = notificationPolicy === 'silent';
  const showPendingJoinRequestBadge = server.unreadCount <= 0 && server.hasPendingJoinRequests && notificationPolicy !== 'blocked';
  return (
    <SizedBox width={44} height={44} style={{ position: 'relative' }}>
      <Center style={{ width: '100%', height: '100%' }}>
        <Avatar
          label={room_display.roomCardAvatarLabel(server)}
          imageUrl={config.resolveAssetUrl(server.avatarUrl)}
          defaultAvatarKey={server.defaultAvatarKey}
          size={40}
          active={selected}
          activeBorderWidth={1.2}
        />
      </Center>
      {server.unreadCount > 0 ? (
        <div style={{ position: 'absolute', top: 0, right: 0 }}>
          <_UnreadBadge count={server.unreadCount} muted={mutedBadge} />
        </div>
      ) : null}
      {showPendingJoinRequestBadge ? (
        <div style={{ position: 'absolute', top: 3, right: 3 }}>
          <_PendingJoinRequestBadge testId={`home-sidebar-room-pending-join-requests-${server.id}`} muted={mutedBadge} />
        </div>
      ) : null}
      {hasLiveParticipants ? (
        <div
          data-testid={`home-sidebar-room-live-${server.id}`}
          style={{ position: 'absolute', right: 0, bottom: 1, display: 'flex', pointerEvents: 'none' }}
        >
          <Icon
            icon={Icons.volume_up}
            color={voiceJoined ? UiColors.accent : UiColors.white}
            size={15}
            style={{ textShadow: _glyphShadow }}
          />
        </div>
      ) : null}
    </SizedBox>
  );
}

function _PendingJoinRequestBadge({ muted, testId }: { muted: boolean; testId?: string }) {
  return (
    <div
      data-testid={testId}
      style={{
        width: 10,
        height: 10,
        boxSizing: 'border-box',
        borderRadius: '50%',
        backgroundColor: muted ? argb(0xff737985) : argb(0xffe14747),
        boxShadow: _decoratedBorder(muted ? argb(0xff282d35) : argb(0xff35191d), 1.1),
      }}
    />
  );
}

function _UnreadBadge({ count, muted = false }: { count: number; muted?: boolean }) {
  const label = count > 99 ? '99+' : `${count}`;
  return (
    <div
      style={{
        minWidth: 18,
        minHeight: 18,
        boxSizing: 'border-box',
        padding: '0 5px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: muted ? argb(0xff737985) : argb(0xffe14747),
        borderRadius: 999,
        boxShadow: _decoratedBorder(muted ? argb(0xff282d35) : argb(0xff35191d), 1.2),
      }}
    >
      <Text softWrap={false} style={{ color: UiColors.white, fontSize: 10, fontWeight: 600, lineHeight: 1, letterSpacing: 0 }}>
        {label}
      </Text>
    </div>
  );
}
