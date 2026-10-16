import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';

import { HostPlatform } from '@gang-chat/core/config/host_platform';
import * as live_display from '@gang-chat/core/app/live_display';
import * as room_display from '@gang-chat/core/app/room_display';
import * as room_join from '@gang-chat/core/app/room_join';
import * as search_display from '@gang-chat/core/app/search_display';
import { unawaited } from '@gang-chat/core/lib/async';
import {
  CurrentUser,
  GlobalSearchResults,
  MessageSearchResult,
  PublicRoom,
  RoomCard,
  RoomMembership,
  UserSummary,
} from '@gang-chat/core/protocol/models';
import type { DesktopWindowController } from '@gang-chat/core/shell/desktop_window_controller';

import { EdgeInsets } from '../lib/edge_insets';
import { Center, Column, Expanded, Row, SizedBox, Spacer, Stack, Positioned, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { FocusNode, TextEditingController, TextInputAction, UndoHistoryController } from '../lib/text_editing';
import { measureTextWidth } from '../lib/text_measure';
import { useElementSize } from '../lib/use_element_size';
import { useListenable } from '../lib/use_listenable';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar } from '../ui/avatar';
import { Button, ButtonTone } from '../ui/button';
import { CompactCategoryButton } from '../ui/compact_category_button';
import { FloatingNotice, FloatingNoticeEmitter, FloatingNoticeTone } from '../ui/feedback';
import { HighlightedText } from '../ui/highlighted_text';
import { Icon, Icons, type IconData } from '../ui/icon';
import { _EditableTextField } from '../ui/input';
import { OverflowMarqueeText } from '../ui/overflow_marquee_text';
import { buildTextFieldContextMenu, isTextContextMenuPanelHit } from '../ui/text_context_menu';
import { UiColors, UiCurves, UiRadii, UiTypography, boxShadow, withAlpha } from '../ui/tokens';
import { UiTooltip } from '../ui/tooltip';
import { WindowChromeInsets } from '../ui/window_chrome';
import { AppWindowControls, AppWindowDragRegion, appWindowControlGap, appWindowControlsWidth } from '../shell/window_controls';
import { HomeAdaptiveLayout } from './adaptive_layout';
import { narrowBreakpoint, sidebarWidth } from './navigation';
import { tapRegionGroupContains, useTapRegionGroupMember } from './hover_card_anchor';
import { RoomHoverCard, UserHoverCard, type RoomProfileResolver, type UserProfileResolver } from './room_profile_card';

export const _homeTitleBarHeight = 44;
const _homeTitleBarSearchWidth = 520;
const _homeTitleBarSearchHeight = 30;
const _homeTitleBarMinSearchWidth = 122;
const _homeTitleBarControlsWidth = appWindowControlsWidth;
const _homeTitleBarLiveRoomHeight = 30;
const _homeTitleBarLiveRoomHorizontalInset = 10;
const _homeTitleBarLiveRoomDefaultWidth = sidebarWidth - _homeTitleBarLiveRoomHorizontalInset * 2;
const _homeTitleBarLiveRoomSearchGap = 14;
const _homeTitleBarLiveRoomActionSize = 28;
const _homeTitleBarLiveRoomMinActionSize = 16;
const _homeTitleBarLiveRoomMinWidth = 80;
const _homeTitleBarLiveRoomTextSafetyWidth = 2;

export type _TitleLiveRoomDockBuilder = (maxWidth: number, opts?: { fillAvailable?: boolean }) => ReactNode;

function _clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

// PORT: the Dart helpers read `Theme.of(context).platform`; the DOM build
// answers from [HostPlatform], so they no longer take a BuildContext.
function _homeTitleBarShowsCustomWindowControls(): boolean {
  // A browser tab has no window of its own to minimise or close.
  if (HostPlatform.isWeb) return false;
  return !HostPlatform.isMacOS && !HostPlatform.isAndroid;
}

/// Phone-sized platforms put the live room on the right of the title bar.
/// In a browser that means a phone browser; desktop browsers keep it on the
/// left like the desktop app.
// PORT: on the web HostPlatform does not expose the browser's OS, so a
// browser always counts as a desktop browser here.
function _homeTitleBarIsMobilePlatform(): boolean {
  return HostPlatform.isAndroid || HostPlatform.isIOS;
}

function _homeTitleBarBrandWidth(maxWidth: number): number {
  const showWindowControls = _homeTitleBarShowsCustomWindowControls();
  const wide = maxWidth >= narrowBreakpoint;
  const compactBrandWidth = _clamp(
    maxWidth - (showWindowControls ? _homeTitleBarControlsWidth : 0) - _homeTitleBarMinSearchWidth,
    118,
    168,
  );
  return wide ? sidebarWidth : compactBrandWidth;
}

function _homeTitleBarCanShowSearch(maxWidth: number): boolean {
  return maxWidth >= narrowBreakpoint;
}

function _homeTitleBarPlacesLiveRoomOnRight(): boolean {
  if (HostPlatform.isWeb) return _homeTitleBarIsMobilePlatform();
  return !_homeTitleBarShowsCustomWindowControls() && !HostPlatform.isMacOS;
}

function _homeTitleBarLiveRoomLeft(): number {
  // Only the macOS app has traffic lights to clear; a browser on a Mac
  // reports macOS too but draws none.
  if (!HostPlatform.isWeb && HostPlatform.isMacOS) {
    return WindowChromeInsets.of().titleBarLeadingControlsSafeInset;
  }
  return _homeTitleBarLiveRoomHorizontalInset;
}

export class _HomeTitleBarSearchLayout {
  constructor(opts: { left: number; width: number }) {
    this.left = opts.left;
    this.width = opts.width;
  }

  readonly left: number;
  readonly width: number;

  get right(): number {
    return this.left + this.width;
  }
}

/** PORT: the Dart `BuildContext` argument is dropped (see the platform helpers above). */
export function _homeTitleBarSearchLayout(
  maxWidth: number,
  opts: { hasLiveRoom: boolean },
): _HomeTitleBarSearchLayout | null {
  const { hasLiveRoom } = opts;
  if (!_homeTitleBarCanShowSearch(maxWidth)) return null;

  const showWindowControls = _homeTitleBarShowsCustomWindowControls();
  const placeLiveRoomOnRight = _homeTitleBarPlacesLiveRoomOnRight();
  const liveRoomLeft = _homeTitleBarLiveRoomLeft();
  let minimumLeft = 0;
  let maximumRight = maxWidth;
  if (showWindowControls) {
    maximumRight -= _homeTitleBarControlsWidth + appWindowControlGap;
  }
  if (hasLiveRoom) {
    const reservedLiveRoomWidth =
      _homeTitleBarLiveRoomHorizontalInset + _homeTitleBarLiveRoomMinWidth + _homeTitleBarLiveRoomSearchGap;
    if (placeLiveRoomOnRight) {
      maximumRight -= reservedLiveRoomWidth;
    } else {
      minimumLeft = liveRoomLeft + _homeTitleBarLiveRoomMinWidth + _homeTitleBarLiveRoomSearchGap;
    }
  }

  const availableWidth = maximumRight - minimumLeft;
  const width = _clamp(availableWidth, _homeTitleBarMinSearchWidth, _homeTitleBarSearchWidth);
  const centeredLeft = (maxWidth - width) / 2;
  if (hasLiveRoom && !placeLiveRoomOnRight) {
    const preferredLeft = liveRoomLeft + _homeTitleBarLiveRoomDefaultWidth + _homeTitleBarLiveRoomSearchGap;
    if (maximumRight - width >= preferredLeft) {
      minimumLeft = preferredLeft;
    }
  }
  if (hasLiveRoom && placeLiveRoomOnRight) {
    const preferredRight =
      maxWidth - _homeTitleBarLiveRoomHorizontalInset - _homeTitleBarLiveRoomDefaultWidth - _homeTitleBarLiveRoomSearchGap;
    if (preferredRight - width >= minimumLeft) {
      maximumRight = preferredRight;
    }
  }
  // Dart `num.clamp` throws when the bounds cross; here the lower bound wins,
  // which is what the Flutter layout reached in every reachable case.
  const left = Math.max(minimumLeft, Math.min(centeredLeft, maximumRight - width));
  return new _HomeTitleBarSearchLayout({ left, width });
}

// ---------------------------------------------------------------------------
// Tap regions.
//
// PORT: Flutter's `TapRegion(groupId:)`. The group registry lives in
// `hover_card_anchor.tsx` (hover cards opened from the search results join the
// same group through `HoverCardTapRegionScope`, as in Dart). The search
// field's text context menu was in the group via
// `buildTextFieldContextMenu(tapRegionGroupId:)`; the kit's menu has no group,
// so a pointer down on it also counts as inside.

export interface _SearchTapRegionProps {
  groupId: unknown;
  onTapOutside?: ((event: PointerEvent) => void) | null;
  children?: ReactNode;
  style?: CSSProperties;
  'data-testid'?: string;
}

/** A `TapRegion` box: registers itself in [groupId] and reports pointer downs outside the group. */
export function _SearchTapRegion({ groupId, onTapOutside, children, style, 'data-testid': testId }: _SearchTapRegionProps) {
  const ref = useRef<HTMLDivElement | null>(null);
  const callback = useRef(onTapOutside);
  callback.current = onTapOutside;
  const listening = onTapOutside != null;
  useTapRegionGroupMember(groupId, ref);

  useEffect(() => {
    if (!listening || typeof document === 'undefined') return;
    const listener = (event: PointerEvent) => {
      if (tapRegionGroupContains(groupId, event)) return;
      if (isTextContextMenuPanelHit({ x: event.clientX, y: event.clientY })) return;
      callback.current?.(event);
    };
    document.addEventListener('pointerdown', listener, true);
    return () => document.removeEventListener('pointerdown', listener, true);
  }, [groupId, listening]);

  return (
    <div ref={ref} data-testid={testId} style={style}>
      {children}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Title bar.

export interface _HomeTitleBarProps {
  windowController: DesktopWindowController;
  searchController: TextEditingController;
  searchTapRegionGroup: unknown;
  liveRoomDockBuilder: _TitleLiveRoomDockBuilder;
  interactionLocked: boolean;
  onActivateSearch: () => void;
  onSearchTapOutside: () => void;
  onSearchContextMenuOpenChanged: (open: boolean) => void;
  onClearSearchQuery: () => void;
}

export function _HomeTitleBar(props: _HomeTitleBarProps) {
  const { windowController } = props;
  const [maximized, setMaximized] = useState(false);
  const mountedRef = useRef(false);
  const boxRef = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(boxRef);

  const syncMaximized = async () => {
    const value = await windowController.isMaximizedWindow();
    if (!mountedRef.current) return;
    setMaximized(value);
  };

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  // initState + didUpdateWidget(windowController changed).
  useEffect(() => {
    unawaited(syncMaximized());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [windowController]);

  const minimize = () => {
    unawaited(windowController.minimizeWindow());
  };

  const toggleMaximize = () => {
    unawaited(
      (async () => {
        await windowController.toggleMaximizeWindow();
        await syncMaximized();
      })(),
    );
  };

  const close = () => {
    unawaited(windowController.closeWindow());
  };

  let content: ReactNode = null;
  if (size != null) {
    const maxWidth = size.width;
    // Windows/Linux 使用自定义窗口按钮；macOS 使用系统原生红绿灯，
    // Android/iOS 等非桌面平台不构建窗口控制区。
    const showWindowControls = _homeTitleBarShowsCustomWindowControls();
    const brandWidth = _homeTitleBarBrandWidth(maxWidth);
    const searchLayout = _homeTitleBarSearchLayout(maxWidth, { hasLiveRoom: true });
    const narrow = maxWidth < narrowBreakpoint;
    const showLiveRoom = !(narrow && HostPlatform.isAndroid);
    const placeLiveRoomOnRight = _homeTitleBarPlacesLiveRoomOnRight();
    const liveRoomLeft = _homeTitleBarLiveRoomLeft();
    const liveRoomBoundary = showWindowControls
      ? maxWidth - _homeTitleBarControlsWidth - appWindowControlGap
      : maxWidth;
    const liveRoomAvailableWidth =
      searchLayout == null
        ? liveRoomBoundary - liveRoomLeft - _homeTitleBarLiveRoomHorizontalInset
        : placeLiveRoomOnRight
          ? maxWidth - _homeTitleBarLiveRoomHorizontalInset - searchLayout.right - _homeTitleBarLiveRoomSearchGap
          : searchLayout.left - liveRoomLeft - _homeTitleBarLiveRoomSearchGap;
    const liveRoomMaxWidth = Math.max(liveRoomAvailableWidth, 0);

    content = (
      <Stack style={{ width: '100%', height: '100%' }}>
        <Row crossAxisAlignment="stretch" style={{ width: '100%', height: '100%' }}>
          <AppWindowDragRegion
            windowController={windowController}
            onDoubleTap={toggleMaximize}
            style={{ width: brandWidth, flexShrink: 0 }}
          >
            <div style={_fillStyle} />
          </AppWindowDragRegion>
          <AppWindowDragRegion
            windowController={windowController}
            onDoubleTap={toggleMaximize}
            style={{ flex: '1 1 0', minWidth: 0 }}
          >
            <div style={_fillStyle} />
          </AppWindowDragRegion>
          {showWindowControls ? (
            <div className="gang-no-drag" style={{ display: 'flex', alignItems: 'center', flexShrink: 0 }}>
              <AppWindowControls maximized={maximized} onMinimize={minimize} onToggleMaximize={toggleMaximize} onClose={close} />
            </div>
          ) : null}
        </Row>
        {showLiveRoom ? (
          <Positioned
            className="gang-no-drag"
            top={(_homeTitleBarHeight - _homeTitleBarLiveRoomHeight) / 2}
            left={placeLiveRoomOnRight ? undefined : liveRoomLeft}
            right={placeLiveRoomOnRight ? _homeTitleBarLiveRoomHorizontalInset : undefined}
          >
            {props.liveRoomDockBuilder(liveRoomMaxWidth)}
          </Positioned>
        ) : null}
        {searchLayout != null ? (
          <Positioned
            className="gang-no-drag"
            left={searchLayout.left}
            width={searchLayout.width}
            top={(_homeTitleBarHeight - _homeTitleBarSearchHeight) / 2}
          >
            <_SearchTapRegion groupId={props.searchTapRegionGroup} onTapOutside={() => props.onSearchTapOutside()}>
              <SizedBox data-testid="home-title-search" height={_homeTitleBarSearchHeight}>
                <_TitleSearchField
                  controller={props.searchController}
                  tapRegionGroup={props.searchTapRegionGroup}
                  enabled={!props.interactionLocked}
                  onContextMenuOpenChanged={props.onSearchContextMenuOpenChanged}
                  onActivated={props.onActivateSearch}
                  onClearQuery={props.onClearSearchQuery}
                />
              </SizedBox>
            </_SearchTapRegion>
          </Positioned>
        ) : null}
      </Stack>
    );
  }

  return (
    <div
      ref={boxRef}
      style={{
        height: _homeTitleBarHeight,
        flexShrink: 0,
        width: '100%',
        boxSizing: 'border-box',
        backgroundColor: UiColors.surfaceLow,
        // Flutter's DecoratedBox paints the bottom border over the child
        // instead of insetting it; an inset shadow keeps the full 44px box.
        boxShadow: `inset 0 -1px 0 ${UiColors.border}`,
        position: 'relative',
      }}
    >
      {content}
    </div>
  );
}

const _fillStyle: CSSProperties = { width: '100%', height: '100%', minWidth: 0, minHeight: 0 };

// ---------------------------------------------------------------------------
// Live room dock.

function _firstCharacter(text: string): string {
  // Dart `text.characters.first`: the first grapheme cluster.
  if (typeof Intl !== 'undefined' && 'Segmenter' in Intl) {
    const segmenter = new Intl.Segmenter(undefined, { granularity: 'grapheme' });
    for (const segment of segmenter.segment(text)) return segment.segment;
  }
  return Array.from(text)[0] ?? '';
}

function _titleLiveTextCanShowCharacter(opts: { text: string; style: CSSProperties; maxWidth: number }): boolean {
  const { text, style, maxWidth } = opts;
  if (text.length === 0 || maxWidth <= 0) return false;
  return _titleLiveRoomTextWidth({ text: _firstCharacter(text), style }) <= maxWidth;
}

// PORT: `TextPainter` + `TextScaler` → canvas measurement of the same font;
// the text scaler is always 1 on the desktop build.
function _titleLiveRoomTextWidth(opts: { text: string; style: CSSProperties }): number {
  return Math.ceil(measureTextWidth(opts.text, opts.style)) + _homeTitleBarLiveRoomTextSafetyWidth;
}

export interface _TitleLiveRoomDockProps {
  maxWidth: number;
  fillAvailable?: boolean;
  room: live_display.JoinedLiveRoomSummary | null;
  micMuted: boolean;
  headphonesMuted: boolean;
  voiceBlocked: boolean;
  onOpen: (() => void) | null;
  onToggleMic: (() => void) | null;
  onToggleHeadphones: () => void;
  onLeave: (() => void) | null;
  interactionLocked: boolean;
}

const _roomNameStyle: CSSProperties = {
  ...UiTypography.label,
  color: UiColors.text,
  fontWeight: 600,
};

export function _TitleLiveRoomDock({
  maxWidth,
  fillAvailable = false,
  room,
  micMuted,
  headphonesMuted,
  voiceBlocked,
  onOpen,
  onToggleMic,
  onToggleHeadphones,
  onLeave,
  interactionLocked,
}: _TitleLiveRoomDockProps) {
  const config = useAppConfig();
  const namedLeadingInset = 7;
  const compactLeadingInset = 6;
  const trailingInset = 4;
  const volumeIconSize = 16;
  const identityGap = 7;
  const avatarSize = 20;
  const nameActionGap = 8;
  const joined = room != null;
  const label = room?.displayName ?? '未加入语音频道';
  const actionCount = joined ? 3 : 2;
  const micControl = live_display.liveMicControlState({ micMuted, voiceBlocked });
  const resolvedAvatar = joined ? config.resolveAssetUrl(room.avatarUrl) : null;
  const roomNameStyle = _roomNameStyle;
  const fullRoomNameWidth = _titleLiveRoomTextWidth({ text: label, style: roomNameStyle });
  const preferredIdentityWidth = joined ? volumeIconSize + identityGap + avatarSize + identityGap : 0;
  const preferredWidth = Math.max(
    namedLeadingInset +
      preferredIdentityWidth +
      fullRoomNameWidth +
      nameActionGap +
      _homeTitleBarLiveRoomActionSize * actionCount +
      trailingInset,
    _homeTitleBarLiveRoomDefaultWidth,
  );
  const width = fillAvailable ? maxWidth : _clamp(preferredWidth, 0, maxWidth);
  const actionSize = _clamp(
    (width - (joined ? 32 : 20)) / actionCount,
    _homeTitleBarLiveRoomMinActionSize,
    _homeTitleBarLiveRoomActionSize,
  );
  const actionButtonsWidth = actionSize * actionCount;
  const namedIdentityFixedWidth =
    namedLeadingInset + (joined ? preferredIdentityWidth : 0) + nameActionGap + actionButtonsWidth + trailingInset;
  const showRoomName = _titleLiveTextCanShowCharacter({
    text: label,
    style: roomNameStyle,
    maxWidth: width - namedIdentityFixedWidth,
  });
  const volumeIdentityWidth =
    compactLeadingInset + volumeIconSize + identityGap + avatarSize + actionButtonsWidth + trailingInset;
  const showVolumeIcon = joined && (showRoomName || width >= volumeIdentityWidth);

  const dock = (
    <UiTooltip message={joined ? '打开语音频道' : '可在加入前设置麦克风和耳机'} preferBelow verticalOffset={22}>
      <div
        data-testid="home-title-live-room"
        onClick={(event) => {
          if (event.button !== 0) return;
          onOpen?.();
        }}
        style={{
          width,
          height: _homeTitleBarLiveRoomHeight,
          boxSizing: 'border-box',
          cursor: joined ? SystemMouseCursors.click : SystemMouseCursors.basic,
          backgroundColor: UiColors.surface,
          borderRadius: UiRadii.md,
          border: `1px solid ${UiColors.border}`,
          paddingLeft: showRoomName ? namedLeadingInset : compactLeadingInset,
          paddingRight: trailingInset,
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center',
          minWidth: 0,
        }}
      >
        {showVolumeIcon ? (
          <>
            <Icon icon={Icons.volume_up} size={volumeIconSize} color={UiColors.accent} />
            <SizedBox width={identityGap} />
          </>
        ) : null}
        {joined ? (
          <Avatar
            label={room.avatarLabel}
            imageUrl={resolvedAvatar}
            defaultAvatarKey={room.defaultAvatarKey}
            size={avatarSize}
            showBorder={false}
          />
        ) : null}
        {showRoomName ? (
          <>
            {joined ? <SizedBox width={identityGap} /> : null}
            <Expanded data-testid="home-title-live-room:name">
              <OverflowMarqueeText trackKey="home-title-live-room:marquee-track" text={label} style={roomNameStyle} />
            </Expanded>
            <SizedBox width={nameActionGap} />
          </>
        ) : (
          <Spacer />
        )}
        <_TitleLiveActionButton
          testId="home-title-live-room:mic"
          size={actionSize}
          tooltip={micControl.mutedForDisplay ? '打开麦克风' : '关闭麦克风'}
          icon={micControl.mutedForDisplay ? Icons.mic_off : Icons.mic}
          color={!micControl.enabled ? UiColors.danger : micControl.active ? UiColors.accent : UiColors.textMuted}
          onPressed={micControl.enabled ? onToggleMic : null}
        />
        <_TitleLiveActionButton
          testId="home-title-live-room:headphones"
          size={actionSize}
          tooltip={headphonesMuted ? '打开耳机' : '关闭耳机'}
          icon={headphonesMuted ? Icons.headset_off : Icons.headphones}
          color={headphonesMuted ? UiColors.textMuted : UiColors.accent}
          onPressed={onToggleHeadphones}
        />
        {joined ? (
          <_TitleLiveActionButton
            testId="home-title-live-room:leave"
            size={actionSize}
            tooltip="离开语音频道"
            icon={Icons.call_end}
            color={UiColors.danger}
            onPressed={onLeave}
          />
        ) : null}
      </div>
    </UiTooltip>
  );
  return (
    <div style={{ opacity: interactionLocked ? 0.54 : 1, pointerEvents: interactionLocked ? 'none' : undefined, width: 'fit-content' }}>
      {dock}
    </div>
  );
}

export interface _TitleLiveActionButtonProps {
  tooltip: string;
  icon: IconData;
  color: string;
  onPressed: (() => void) | null;
  size: number;
  testId?: string;
}

export function _TitleLiveActionButton({ tooltip, icon, color, onPressed, size, testId }: _TitleLiveActionButtonProps) {
  const enabled = onPressed != null;
  return (
    <UiTooltip message={tooltip} preferBelow verticalOffset={22}>
      <div
        data-testid={testId}
        role="button"
        aria-label={tooltip}
        aria-disabled={!enabled}
        onClick={(event) => {
          // The action owns the tap (the inner GestureDetector won the arena
          // in Flutter), so it never also opens the dock behind it.
          event.stopPropagation();
          if (event.button !== 0) return;
          if (enabled) onPressed();
        }}
        style={{
          width: size,
          height: size,
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
        }}
      >
        <Icon icon={icon} size={_clamp(size - 8, 10, 16)} color={enabled ? color : withAlpha(color, 0.56)} />
      </div>
    </UiTooltip>
  );
}

// ---------------------------------------------------------------------------
// Search field.

/// 标题栏中央的搜索框。不复用通用 [Input] 封装,而是一个普通的圆角矩形,
/// 获得焦点时会像按钮一样显示绿色边框与底色。
export interface _TitleSearchFieldProps {
  controller: TextEditingController;
  tapRegionGroup: unknown;
  enabled: boolean;
  onContextMenuOpenChanged: (open: boolean) => void;
  onActivated: () => void;
  onClearQuery: () => void;
}

const _searchTextStyle: CSSProperties = { ...UiTypography.body, fontSize: 13, lineHeight: 1.2 };
const _searchHintStyle: CSSProperties = { ...UiTypography.body, fontSize: 13, lineHeight: 1.2, color: UiColors.textMuted };

export function _TitleSearchField(props: _TitleSearchFieldProps) {
  const { controller, enabled, onClearQuery } = props;
  const focusNodeRef = useRef<FocusNode | null>(null);
  focusNodeRef.current ??= new FocusNode();
  const focusNode = focusNodeRef.current;
  const undoControllerRef = useRef<UndoHistoryController | null>(null);
  undoControllerRef.current ??= new UndoHistoryController();
  const undoController = undoControllerRef.current;
  const propsRef = useRef(props);
  propsRef.current = props;

  // `_hasText` / `_focused` followed the controller and focus node.
  useListenable(controller, focusNode);
  const hasText = controller.text.trim().length > 0;
  const focused = focusNode.hasFocus;

  useEffect(() => {
    const handleFocusChange = () => {
      if (focusNode.hasFocus && propsRef.current.enabled) propsRef.current.onActivated();
    };
    focusNode.addListener(handleFocusChange);
    return () => focusNode.removeListener(handleFocusChange);
  }, [focusNode]);

  // didUpdateWidget: losing `enabled` drops focus.
  const previousEnabled = useRef(enabled);
  useEffect(() => {
    if (previousEnabled.current && !enabled) focusNode.unfocus();
    previousEnabled.current = enabled;
  }, [enabled, focusNode]);

  useEffect(
    () => () => {
      focusNode.dispose();
      undoController.dispose();
    },
    [focusNode, undoController],
  );

  const activeFocus = focused && enabled;
  const accent = activeFocus ? UiColors.accent : UiColors.textMuted;
  const transition = `background-color 90ms ${UiCurves.easeOutCubic}, border-color 90ms ${UiCurves.easeOutCubic}`;
  return (
    <div
      style={{
        height: _homeTitleBarSearchHeight,
        boxSizing: 'border-box',
        padding: '0 10px',
        backgroundColor: activeFocus ? UiColors.selected : UiColors.surface,
        borderRadius: UiRadii.md,
        border: `1px solid ${activeFocus ? UiColors.selectedBorder : UiColors.border}`,
        transition,
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        minWidth: 0,
      }}
    >
      <Icon icon={Icons.search} size={16} color={accent} />
      <SizedBox width={8} />
      <div
        style={{ flex: '1 1 0', minWidth: 0 }}
        onClick={() => {
          // TextField.onTap.
          if (propsRef.current.enabled) propsRef.current.onActivated();
        }}
      >
        {/* PORT: Flutter's `cursorWidth: 1.5` has no CSS equivalent; the caret keeps the accent colour. */}
        <_EditableTextField
          controller={controller}
          focusNode={focusNode}
          undoController={undoController}
          enabled={enabled}
          maxLines={1}
          textInputAction={TextInputAction.search}
          style={_searchTextStyle}
          hintText="搜索"
          hintStyle={_searchHintStyle}
          aria-label="搜索"
          contextMenuBuilder={(editableTextState) =>
            buildTextFieldContextMenu(editableTextState, {
              undoController,
              tapRegionGroupId: propsRef.current.tapRegionGroup,
              onOpenChanged: propsRef.current.onContextMenuOpenChanged,
            })
          }
        />
      </div>
      {hasText && enabled ? (
        <>
          <SizedBox width={6} />
          <UiTooltip message="清空搜索">
            <div
              role="button"
              aria-label="清空搜索"
              onClick={(event) => {
                if (event.button !== 0) return;
                onClearQuery();
              }}
              style={{ display: 'flex', alignItems: 'center', flexShrink: 0 }}
            >
              <Icon icon={Icons.close} size={15} color={UiColors.textMuted} />
            </div>
          </UiTooltip>
        </>
      ) : null}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Search results panel.

export interface _TitleSearchResultsPanelProps {
  query: string;
  results: GlobalSearchResults | null;
  loading: boolean;
  loadingMore: boolean;
  error: string | null;
  timestampNow: Date;
  currentUser: CurrentUser;
  activeCategory: search_display.GlobalSearchCategory | null;
  visibleCategories: readonly search_display.GlobalSearchCategory[];
  busyPublicRoomId: string | null;
  pendingPublicRoomIds: ReadonlySet<string>;
  onCategorySelected: (category: search_display.GlobalSearchCategory) => void;
  onLoadMore: () => void;
  onMyRoomSelected: (room: RoomCard) => void;
  onProfileRoomSelected: (room: PublicRoom) => void;
  onResolveRoomProfile: RoomProfileResolver;
  onResolveRoomUserProfile: (roomId: string, user: UserSummary) => Promise<UserSummary>;
  onResolveUserProfile: UserProfileResolver;
  onPublicRoomAction: (room: PublicRoom) => void;
  onUserSettingsSelected: (user: UserSummary) => void;
  onMessageSelected: (result: MessageSearchResult) => void;
  onFileSelected: (result: MessageSearchResult) => void;
}

export function _TitleSearchResultsPanel(props: _TitleSearchResultsPanelProps) {
  const { error, results, activeCategory, currentUser, onCategorySelected } = props;
  const message = error?.trim();
  const panel = (
    <div
      style={{
        pointerEvents: 'auto',
        boxSizing: 'border-box',
        backgroundColor: UiColors.surfaceLow,
        borderRadius: UiRadii.lg,
        border: `1px solid ${UiColors.borderStrong}`,
        boxShadow: boxShadow([{ color: withAlpha(UiColors.black, 0.34), blurRadius: 18, offsetY: 8 }]),
        overflow: 'hidden',
      }}
    >
      <Column crossAxisAlignment="stretch" style={{ maxHeight: 440 }}>
        <_SearchCategoryTabs
          results={results}
          activeCategory={activeCategory}
          categories={currentUser.isSuperuser ? search_display.superuserGlobalSearchCategories : search_display.globalSearchCategories}
          onCategorySelected={onCategorySelected}
        />
        <div style={{ height: 1, flexShrink: 0, backgroundColor: UiColors.border }} />
        <div style={{ flex: '0 1 auto', minHeight: 0, display: 'flex', flexDirection: 'column' }}>
          <_TitleSearchResultsBody {...props} />
        </div>
      </Column>
    </div>
  );
  if (message == null || message.length === 0) return panel;
  return (
    <FloatingNoticeEmitter notices={[new FloatingNotice({ message, tone: FloatingNoticeTone.error, duration: null })]}>
      {panel}
    </FloatingNoticeEmitter>
  );
}

function _TitleSearchResultsBody(props: _TitleSearchResultsPanelProps) {
  const { query, results, loading, loadingMore, error, visibleCategories, activeCategory, onLoadMore } = props;
  const compact = HomeAdaptiveLayout.usesCompactLayout();
  const config = useAppConfig();

  const failure = error;
  if (failure != null) {
    return <_SearchPanelState icon={Icons.error_outline} title="搜索失败" detail="请稍后重试" />;
  }

  const snapshot = results;
  if (snapshot == null && loading) {
    return (
      <SizedBox height={104}>
        <Center style={{ height: '100%' }}>
          <SizedBox width={22} height={22}>
            <CircularProgressIndicator size={22} strokeWidth={2.2} color={UiColors.accent} />
          </SizedBox>
        </Center>
      </SizedBox>
    );
  }

  if (!search_display.globalSearchHasResults(snapshot)) {
    return <_SearchPanelState icon={Icons.search} title="没有找到结果" detail={query.trim()} />;
  }

  const sections: ReactNode[] = [];
  for (const category of visibleCategories) {
    const section = _sectionFor(props, snapshot!, category, { compact, resolveAssetUrl: (path) => config.resolveAssetUrl(path) });
    if (section != null) {
      if (sections.length > 0) sections.push(<SizedBox key={`gap-${category}`} height={10} />);
      sections.push(section);
    }
  }

  if (sections.length === 0) {
    return (
      <_SearchPanelState
        icon={Icons.filter_alt_outlined}
        title="该分类没有结果"
        detail={activeCategory == null ? query.trim() : search_display.globalSearchCategoryLabel(activeCategory)}
      />
    );
  }

  return (
    <div
      onScroll={(event) => {
        // NotificationListener<ScrollNotification>.
        if (loadingMore) return;
        const node = event.currentTarget;
        const maxScrollExtent = node.scrollHeight - node.clientHeight;
        if (maxScrollExtent <= 0) return;
        const extentAfter = maxScrollExtent - node.scrollTop;
        if (extentAfter <= 96) onLoadMore();
      }}
      style={{
        overflowY: 'auto',
        minHeight: 0,
        flex: '0 1 auto',
        boxSizing: 'border-box',
        padding: '10px 10px 12px 10px',
      }}
    >
      {sections}
      {loadingMore ? <_SearchLoadingMoreIndicator /> : null}
    </div>
  );
}

function _sectionFor(
  props: _TitleSearchResultsPanelProps,
  snapshot: GlobalSearchResults,
  category: search_display.GlobalSearchCategory,
  env: { compact: boolean; resolveAssetUrl: (path: string | null | undefined) => string | null },
): ReactNode | null {
  const {
    query,
    timestampNow,
    currentUser,
    busyPublicRoomId,
    pendingPublicRoomIds,
    onMyRoomSelected,
    onProfileRoomSelected,
    onResolveRoomProfile,
    onResolveRoomUserProfile,
    onResolveUserProfile,
    onPublicRoomAction,
    onUserSettingsSelected,
    onMessageSelected,
    onFileSelected,
  } = props;
  const { compact, resolveAssetUrl } = env;
  const count = search_display.globalSearchCategoryCount(snapshot, category);
  if (count === 0) return null;

  let children: ReactNode[];
  switch (category) {
    case search_display.GlobalSearchCategory.myRooms:
      children = snapshot.myRooms.map((room) => {
        const time = room_display.roomSidebarLastMessageTime(room, { now: timestampNow });
        return (
          <_RoomSearchResultTile
            key={`my-room-${room.id}`}
            title={room.displayName}
            subtitle={room_display.roomSidebarSubtitle(room)}
            query={query}
            leading={
              <RoomHoverCard
                room={_publicRoomFromRoomCard(room)}
                currentUser={currentUser}
                onResolveRoom={onResolveRoomProfile}
                onResolveUserProfile={(user: UserSummary) => onResolveRoomUserProfile(room.id, user)}
                onEnterRoom={onProfileRoomSelected}
              >
                <Avatar
                  label={room_display.roomCardAvatarLabel(room)}
                  imageUrl={resolveAssetUrl(room.avatarUrl)}
                  defaultAvatarKey={room.defaultAvatarKey}
                  size={30}
                />
              </RoomHoverCard>
            }
            titleTrailing={
              time.length === 0 ? null : (
                <Text maxLines={1} style={{ ...UiTypography.label, color: UiColors.textMuted, fontSize: 10.5, flexShrink: 0 }}>
                  {time}
                </Text>
              )
            }
            trailing={<_MyRoomEnterActivity room={room} onPressed={onMyRoomSelected} compact={compact} />}
          />
        );
      });
      break;
    case search_display.GlobalSearchCategory.publicRooms:
      children = room_join
        .publicRoomJoinCandidates({
          rooms: snapshot.publicRooms,
          pendingRoomIds: pendingPublicRoomIds,
          busyRoomId: busyPublicRoomId,
        })
        .map((candidate) => (
          <_RoomSearchResultTile
            key={`public-room-${candidate.room.id}`}
            title={candidate.room.name}
            subtitle={_publicRoomSearchMeta(candidate.room)}
            query={query}
            leading={
              <RoomHoverCard
                room={candidate.room}
                currentUser={currentUser}
                onResolveRoom={onResolveRoomProfile}
                onResolveUserProfile={(user: UserSummary) => onResolveRoomUserProfile(candidate.room.id, user)}
                onEnterRoom={candidate.room.joined ? onProfileRoomSelected : null}
              >
                <Avatar
                  label={candidate.room.name}
                  imageUrl={resolveAssetUrl(candidate.room.avatarUrl)}
                  defaultAvatarKey={candidate.room.defaultAvatarKey}
                  size={30}
                />
              </RoomHoverCard>
            }
            trailing={<_PublicRoomJoinActivity candidate={candidate} onPressed={onPublicRoomAction} compact={compact} />}
          />
        ));
      break;
    case search_display.GlobalSearchCategory.userSettings:
      children = snapshot.userSettings.map((user) => (
        <_RoomSearchResultTile
          key={`user-${user.id}`}
          title={user.displayName.trim().length === 0 ? user.username : user.displayName}
          subtitle={`@${user.username}`}
          query={query}
          leading={
            <UserHoverCard user={user} currentUser={currentUser} onResolveProfile={onResolveUserProfile}>
              <Avatar
                label={user.displayName}
                imageUrl={resolveAssetUrl(user.avatarUrl)}
                defaultAvatarKey={user.defaultAvatarKey}
                size={30}
              />
            </UserHoverCard>
          }
          trailing={
            <Button
              data-testid={`open-user-settings-${user.id}`}
              width={_searchResultActionWidth(compact, { hasIcon: true })}
              height={30}
              padding={_searchResultActionPadding(compact, { wideHorizontal: 16 })}
              onPressed={() => onUserSettingsSelected(user)}
              icon={<Icon icon={Icons.manage_accounts_outlined} size={15} />}
            >
              进入设置
            </Button>
          }
        />
      ));
      break;
    case search_display.GlobalSearchCategory.messages:
      children = snapshot.messages.map((result) => (
        <_MessageSearchResultTile
          key={`message-${result.message.id}`}
          result={result}
          title={search_display.globalSearchMessageTitle(result)}
          subtitle={search_display.globalSearchMessageSubtitle(result)}
          query={query}
          onPressed={() => onMessageSelected(result)}
        />
      ));
      break;
    case search_display.GlobalSearchCategory.files:
      children = snapshot.files.map((result) => (
        <_MessageSearchResultTile
          key={`file-${result.message.id}`}
          result={result}
          title={search_display.globalSearchFileTitle(result)}
          subtitle={search_display.globalSearchFileSubtitle(result)}
          query={query}
          onPressed={() => onFileSelected(result)}
        />
      ));
      break;
  }

  return (
    <_SearchResultSection key={`section-${category}`} title={search_display.globalSearchCategoryLabel(category)} count={count}>
      {children}
    </_SearchResultSection>
  );
}

export interface _SearchCategoryTabsProps {
  results: GlobalSearchResults | null;
  activeCategory: search_display.GlobalSearchCategory | null;
  categories: readonly search_display.GlobalSearchCategory[];
  onCategorySelected: (category: search_display.GlobalSearchCategory) => void;
}

export function _SearchCategoryTabs({ results, activeCategory, categories, onCategorySelected }: _SearchCategoryTabsProps) {
  const ref = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(ref);
  const compact = size != null && size.width < 380;
  const columns = compact ? 2 : 4;
  // ((maxWidth - (columns - 1) * 6) / columns), expressed against the measured row.
  const itemWidth = `max(0px, calc((100% - ${(columns - 1) * 6}px) / ${columns}))`;
  return (
    <div style={{ padding: 8, boxSizing: 'border-box', flexShrink: 0 }}>
      <div ref={ref} style={{ display: 'flex', flexDirection: 'row', flexWrap: 'wrap', gap: 6, width: '100%' }}>
        {categories.map((category) => (
          <div key={category} data-testid={`search-category-${category}`} style={{ width: itemWidth, flexShrink: 0, minWidth: 0 }}>
            <CompactCategoryButton
              label={search_display.globalSearchCategoryLabel(category)}
              count={search_display.globalSearchCategoryCount(results, category)}
              selected={activeCategory === category}
              onPressed={() => onCategorySelected(category)}
            />
          </div>
        ))}
      </div>
    </div>
  );
}

export function _SearchResultSection({ title, count, children }: { title: string; count: number; children?: ReactNode }) {
  return (
    <Column crossAxisAlignment="stretch">
      <div style={{ padding: '0 2px 7px 2px', boxSizing: 'border-box' }}>
        <Text style={{ ...UiTypography.label, color: UiColors.textMuted }}>{`${title} ${count}`}</Text>
      </div>
      {children}
    </Column>
  );
}

export function _SearchLoadingMoreIndicator() {
  return (
    <SizedBox height={34}>
      <Center style={{ height: '100%' }}>
        <SizedBox width={16} height={16}>
          <CircularProgressIndicator size={16} strokeWidth={2} color={UiColors.accent} />
        </SizedBox>
      </Center>
    </SizedBox>
  );
}

export interface _RoomSearchResultTileProps {
  title: string;
  subtitle: string;
  query: string;
  leading: ReactNode;
  titleTrailing?: ReactNode;
  trailing?: ReactNode;
}

export function _RoomSearchResultTile({ title, subtitle, query, leading, titleTrailing, trailing }: _RoomSearchResultTileProps) {
  return (
    <_SearchResultTile
      leading={leading}
      title={title}
      titleTrailing={titleTrailing}
      subtitle={subtitle}
      query={query}
      trailing={trailing}
    />
  );
}

export function _MyRoomEnterActivity({
  room,
  onPressed,
  compact,
}: {
  room: RoomCard;
  onPressed: (room: RoomCard) => void;
  compact: boolean;
}) {
  return (
    <Button
      data-testid={`my-room-action-${room.id}`}
      width={_searchResultActionWidth(compact, { hasIcon: false })}
      height={30}
      padding={_searchResultActionPadding(compact, { wideHorizontal: 10 })}
      tone={ButtonTone.primary}
      onPressed={() => onPressed(room)}
    >
      进入房间
    </Button>
  );
}

export function _PublicRoomJoinActivity({
  candidate,
  onPressed,
  compact,
}: {
  candidate: room_join.PublicRoomJoinCandidate;
  onPressed: (room: PublicRoom) => void;
  compact: boolean;
}) {
  const room = candidate.room;
  return (
    <Button
      data-testid={`public-room-action-${room.id}`}
      width={_searchResultActionWidth(compact, { hasIcon: false })}
      height={30}
      padding={_searchResultActionPadding(compact, { wideHorizontal: 10 })}
      tone={candidate.actionable ? ButtonTone.primary : ButtonTone.neutral}
      loading={candidate.busy}
      onPressed={candidate.actionEnabled ? () => onPressed(room) : null}
    >
      {_publicRoomSearchActionLabel(candidate)}
    </Button>
  );
}

// PORT: these read `HomeAdaptiveLayout.usesCompactLayout(context)` in Dart;
// the caller resolves the compact flag (a hook) and passes it in.
function _searchResultActionPadding(compact: boolean, opts: { wideHorizontal: number }) {
  return EdgeInsets.symmetric({ horizontal: compact ? 4 : opts.wideHorizontal });
}

function _searchResultActionWidth(compact: boolean, opts: { hasIcon: boolean }): number | null {
  if (!compact) return null;
  return opts.hasIcon ? 90 : 72;
}

export interface _MessageSearchResultTileProps {
  result: MessageSearchResult;
  title: string;
  subtitle: string;
  query: string;
  onPressed: () => void;
}

export function _MessageSearchResultTile({ result, title, subtitle, query, onPressed }: _MessageSearchResultTileProps) {
  const config = useAppConfig();
  return (
    <_SearchResultTile
      testId={`search-message-result-${result.message.id}`}
      onPressed={onPressed}
      leading={
        <Avatar
          label={result.room.name}
          imageUrl={config.resolveAssetUrl(result.room.avatarUrl)}
          defaultAvatarKey={result.room.defaultAvatarKey}
          size={30}
        />
      }
      title={title}
      subtitle={subtitle}
      query={query}
      trailing={
        <Text maxLines={1} style={{ ...UiTypography.label, color: UiColors.textMuted }}>
          {search_display.globalSearchResultTimeLabel(result)}
        </Text>
      }
    />
  );
}

export interface _SearchResultTileProps {
  leading: ReactNode;
  title: string;
  subtitle: string;
  query: string;
  titleTrailing?: ReactNode;
  trailing?: ReactNode;
  onPressed?: (() => void) | null;
  testId?: string;
}

export function _SearchResultTile({ leading, title, subtitle, query, titleTrailing, trailing, onPressed, testId }: _SearchResultTileProps) {
  const [hovered, setHovered] = useState(false);
  const interactive = onPressed != null;
  const compact = HomeAdaptiveLayout.usesCompactLayout();
  return (
    <div
      data-testid={testId}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={(event) => {
        if (event.button !== 0) return;
        onPressed?.();
      }}
      style={{
        cursor: interactive ? SystemMouseCursors.click : SystemMouseCursors.basic,
        marginBottom: 6,
        padding: '8px 8px',
        boxSizing: 'border-box',
        backgroundColor: hovered && interactive ? UiColors.surface : UiColors.transparent,
        borderRadius: UiRadii.sm,
        transition: 'background-color 90ms linear',
      }}
    >
      <Row>
        <SizedBox width={30} height={30}>
          <Center style={{ width: '100%', height: '100%' }}>{leading}</Center>
        </SizedBox>
        <SizedBox width={9} />
        <Expanded>
          <Column crossAxisAlignment="start" mainAxisSize="min" style={{ alignSelf: 'stretch' }}>
            <Row style={{ alignSelf: 'stretch' }}>
              <Expanded>
                <HighlightedText
                  text={title}
                  query={query}
                  maxLines={1}
                  overflow="ellipsis"
                  style={{ ...UiTypography.body, fontSize: 13, fontWeight: 600 }}
                />
              </Expanded>
              {titleTrailing != null ? (
                <>
                  <SizedBox width={8} />
                  {titleTrailing}
                </>
              ) : null}
            </Row>
            {subtitle.trim().length > 0 ? (
              <>
                <SizedBox height={3} />
                <Row style={{ alignSelf: 'stretch' }}>
                  <Expanded>
                    <HighlightedText
                      text={subtitle}
                      query={query}
                      maxLines={1}
                      overflow="ellipsis"
                      style={{ ...UiTypography.label, color: UiColors.textMuted, lineHeight: compact ? 1.35 : 1.2 }}
                    />
                  </Expanded>
                </Row>
              </>
            ) : null}
          </Column>
        </Expanded>
        {trailing != null ? (
          <>
            <SizedBox width={8} />
            <div style={{ maxWidth: 126, minWidth: 0, flexShrink: 0, display: 'flex' }}>{trailing}</div>
          </>
        ) : null}
      </Row>
    </div>
  );
}

export function _SearchPanelState({ icon, title, detail }: { icon: IconData; title: string; detail: string }) {
  return (
    <SizedBox height={118}>
      <Center style={{ height: '100%' }}>
        <div style={{ padding: '0 18px', boxSizing: 'border-box', minWidth: 0, maxWidth: '100%' }}>
          <Column mainAxisSize="min" style={{ maxWidth: '100%' }}>
            <Icon icon={icon} color={UiColors.textMuted} size={22} />
            <SizedBox height={8} />
            <Text style={UiTypography.body}>{title}</Text>
            <SizedBox height={4} />
            <Text maxLines={1} style={{ ...UiTypography.label, color: UiColors.textMuted, maxWidth: '100%' }}>
              {detail}
            </Text>
          </Column>
        </div>
      </Center>
    </SizedBox>
  );
}

function _publicRoomSearchMeta(room: PublicRoom): string {
  return `${room.memberCount} 名成员`;
}

function _publicRoomSearchActionLabel(candidate: room_join.PublicRoomJoinCandidate): string {
  const room = candidate.room;
  if (room.joined) return '进入房间';
  if (candidate.pending) return '待审批';
  if (room.joinPolicy === 'closed') return '不可加入';
  return '加入房间';
}

export function _publicRoomFromRoomCard(room: RoomCard): PublicRoom {
  return new PublicRoom({
    id: room.id,
    rid: room.rid,
    name: room.displayName,
    avatarLabel: room_display.roomCardAvatarLabel(room),
    avatarUrl: room.avatarUrl,
    defaultAvatarKey: room.defaultAvatarKey,
    visibility: room.visibility,
    joinPolicy: room.visibility === 'public' ? 'open' : 'closed',
    description: room.description,
    memberCount: room.memberCount,
    onlineMemberCount: room.onlineMemberCount,
    liveParticipantCount: room.liveParticipantCount,
    joined: true,
    joinState: 'joined',
    myMembership: new RoomMembership({
      joinedAt: new Date(0),
      role: 'member',
    }),
  });
}
