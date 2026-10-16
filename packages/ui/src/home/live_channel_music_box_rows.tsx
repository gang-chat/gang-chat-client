import { Fragment, useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode, type RefObject } from 'react';

import {
  MusicBoxQueueItemStatus,
  MusicBoxSearchResult,
  UserSummary,
  type CurrentUser,
  type MusicBoxQueueItem,
  type MusicBoxRequester,
  type PersonalMusicPlaylistItem,
  type PublicRoom,
} from '@gang-chat/core/protocol/models';

import { EdgeInsets } from '../lib/edge_insets';
import { Center, Column, Expanded, Row, SizedBox, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { measureLineCount, measureTextWidth } from '../lib/text_measure';
import { useElementSize } from '../lib/use_element_size';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar } from '../ui/avatar';
import { Button, ButtonIcon, ButtonTone } from '../ui/button';
import { HighlightedText } from '../ui/highlighted_text';
import { Icon, Icons, type IconData } from '../ui/icon';
import { UiColors, UiCurves, UiRadii, UiTypography, kClientFontStack } from '../ui/tokens';
import { HoverCardAnchor, type HoverCardBuilder } from './hover_card_anchor';
import { _musicBoxControlHeight } from './live_channel_music_box';
import { UserHoverCard, type RoomProfileResolver, type UserProfileActionBuilder, type UserProfileResolver } from './room_profile_card';

/*
 * Shared list furniture for the music box body: one track row used by the
 * request queue, search results, and saved-playlist tracks; one playlist row;
 * section headers; and the empty/loading/error states. Keeping a single row
 * implementation means a song looks and behaves the same no matter which tab
 * it was found in.
 */

export const _musicBoxRowMinHeight = 44;
export const _musicBoxRowActionSize = 28;
export const _musicBoxRowLeadingWidth = 24;

// Flutter's AnimatedContainer used by the rows: 80ms, easeOutCubic.
const _rowTransition = `background-color 80ms ${UiCurves.easeOutCubic}, border-color 80ms ${UiCurves.easeOutCubic}`;

/**
 * Shrinks the font a little when [text] would wrap past [comfortableLines]
 * at [width], so long titles stay fully visible without an ellipsis.
 *
 * PORT: the Dart `BuildContext` (text direction / text scaler) is dropped;
 * lines are counted with the canvas measurement in `lib/text_measure`.
 */
export function _musicBoxAdaptiveListTextStyle(opts: {
  text: string;
  baseStyle: CSSProperties;
  width: number;
  comfortableLines?: number;
  maximumReduction?: number;
}): CSSProperties {
  const { text, baseStyle, width, comfortableLines = 2, maximumReduction = 2 } = opts;
  const baseSize = typeof baseStyle.fontSize === 'number' ? baseStyle.fontSize : 13;
  const lineCount = measureLineCount(text, baseStyle, width);
  if (lineCount <= comfortableLines) return baseStyle;
  const reduction = Math.min(Math.max(lineCount - comfortableLines, 1), Math.trunc(maximumReduction));
  return { ...baseStyle, fontSize: baseSize - reduction };
}

// ---------------------------------------------------------------------------
// Local stand-ins for Flutter layout behaviour the UI kit does not provide.

/**
 * A single-line label with Flutter's `TextOverflow.fade` (`softWrap: false`):
 * when the text is wider than its box, the trailing edge fades out over the
 * width of an ellipsis instead of being cut or ellipsized.
 *
 * PORT: kit gap — the kit `Text` only knows ellipsis / clip. The fade is a CSS
 * mask applied while the label overflows (Flutter's paragraph shader).
 */
export function _MusicBoxFadeText({
  text,
  style,
  textAlign,
  'data-testid': testId,
}: {
  text: string;
  style: CSSProperties;
  textAlign?: CSSProperties['textAlign'];
  'data-testid'?: string;
}) {
  const ref = useRef<HTMLSpanElement | null>(null);
  const [overflowing, setOverflowing] = useState(false);
  const styleKey = JSON.stringify(style);
  useLayoutEffect(() => {
    const node = ref.current;
    if (node == null) return;
    const measure = () => {
      const next = node.scrollWidth - node.clientWidth > 0.5;
      setOverflowing((previous) => (previous === next ? previous : next));
    };
    measure();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(measure);
    observer.observe(node);
    return () => observer.disconnect();
  }, [text, styleKey]);
  const fadeWidth = overflowing ? measureTextWidth('…', style) : 0;
  const mask = overflowing ? `linear-gradient(to right, #000 calc(100% - ${fadeWidth}px), transparent)` : undefined;
  return (
    <Text
      ref={ref}
      softWrap={false}
      overflow="clip"
      textAlign={textAlign}
      data-testid={testId}
      style={{ ...style, maskImage: mask, WebkitMaskImage: mask }}
    >
      {text}
    </Text>
  );
}

/**
 * Port of `FittedBox(fit: BoxFit.scaleDown)`: lays [children] out at their
 * natural size and scales them down (never up) to fit the available width
 * and, when [boundedHeight], height.
 *
 * PORT: kit gap — measured with ResizeObserver and applied as a CSS
 * transform. With an unbounded height the box takes the scaled child height,
 * like the Flutter render box.
 */
export function _MusicBoxScaleDown({
  children,
  alignment = 'center',
  boundedHeight = true,
}: {
  children?: ReactNode;
  alignment?: 'center' | 'bottomLeft';
  boundedHeight?: boolean;
}) {
  const outerRef = useRef<HTMLDivElement | null>(null);
  const innerRef = useRef<HTMLDivElement | null>(null);
  const [fit, setFit] = useState<{ scale: number; height: number }>({ scale: 1, height: 0 });
  useLayoutEffect(() => {
    const outer = outerRef.current;
    const inner = innerRef.current;
    if (outer == null || inner == null) return;
    const measure = () => {
      const availableWidth = outer.clientWidth;
      const availableHeight = boundedHeight ? outer.clientHeight : Number.POSITIVE_INFINITY;
      // offsetWidth / offsetHeight ignore the transform: the natural size.
      const childWidth = inner.offsetWidth;
      const childHeight = inner.offsetHeight;
      let scale = 1;
      if (childWidth > 0 && availableWidth > 0) scale = Math.min(scale, availableWidth / childWidth);
      if (childHeight > 0 && Number.isFinite(availableHeight) && availableHeight > 0) {
        scale = Math.min(scale, availableHeight / childHeight);
      }
      setFit((previous) =>
        Math.abs(previous.scale - scale) < 0.001 && Math.abs(previous.height - childHeight) < 0.5
          ? previous
          : { scale, height: childHeight },
      );
    };
    measure();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(measure);
    observer.observe(outer);
    observer.observe(inner);
    return () => observer.disconnect();
  }, [boundedHeight]);
  const bottomLeft = alignment === 'bottomLeft';
  return (
    <div
      ref={outerRef}
      style={{
        display: 'flex',
        flexDirection: 'row',
        justifyContent: bottomLeft ? 'flex-start' : 'center',
        alignItems: bottomLeft ? 'flex-end' : 'center',
        width: '100%',
        height: boundedHeight ? '100%' : fit.height * fit.scale,
        minWidth: 0,
        boxSizing: 'border-box',
      }}
    >
      <div
        ref={innerRef}
        style={{
          flexShrink: 0,
          width: 'max-content',
          transform: fit.scale < 1 ? `scale(${fit.scale})` : undefined,
          transformOrigin: bottomLeft ? 'left bottom' : 'center',
        }}
      >
        {children}
      </div>
    </div>
  );
}

/**
 * Dart `MouseRegion(onEnter:, onExit:)` on [ref]'s element.
 *
 * PORT: native listeners rather than React's onMouseEnter/Leave, which
 * follow the React tree through portals: a row would otherwise stay hovered
 * while the pointer is over its hover card, which Flutter draws in the
 * separate overlay tree.
 */
function _useMouseRegion(ref: RefObject<HTMLElement | null>, onChanged: (hovered: boolean) => void): void {
  const callback = useRef(onChanged);
  callback.current = onChanged;
  useEffect(() => {
    const node = ref.current;
    if (node == null) return;
    const enter = (event: PointerEvent) => {
      if (event.pointerType !== 'touch') callback.current(true);
    };
    const leave = (event: PointerEvent) => {
      if (event.pointerType !== 'touch') callback.current(false);
    };
    node.addEventListener('pointerenter', enter);
    node.addEventListener('pointerleave', leave);
    return () => {
      node.removeEventListener('pointerenter', enter);
      node.removeEventListener('pointerleave', leave);
    };
  }, [ref]);
}

// ---------------------------------------------------------------------------

export interface _MusicBoxTrackRowProps {
  leading: ReactNode;
  title: string;
  subtitle: string;
  query?: string;
  selected?: boolean;
  subtitleColor?: string;
  cardBuilder?: HoverCardBuilder | null;
  cardResetKey?: unknown;
  trailing?: readonly ReactNode[];
  hoverColor?: string;
  /** Dart `key` (test id). */
  'data-testid'?: string;
}

/**
 * One song in any music-box list. [leading] is the row's status glyph
 * (index number, download spinner, playing waveform); [trailing] holds the
 * list's constant actions. The title area opens the song card
 * ([cardBuilder]).
 */
export function _MusicBoxTrackRow({
  leading,
  title,
  subtitle,
  query = '',
  selected = false,
  subtitleColor = UiColors.textSecondary,
  cardBuilder,
  cardResetKey,
  trailing = [],
  hoverColor = UiColors.surfaceLow,
  'data-testid': testId,
}: _MusicBoxTrackRowProps) {
  const [hovered, setHoveredState] = useState(false);
  const setHovered = (next: boolean) => {
    if (hovered === next) return;
    setHoveredState(next);
  };
  // The LayoutBuilder constraint: the full row width.
  const layoutRef = useRef<HTMLDivElement | null>(null);
  const layoutSize = useElementSize(layoutRef);
  _useMouseRegion(layoutRef, setHovered);

  const actionCount = trailing.length;
  const titleStyle: CSSProperties = {
    ...UiTypography.body,
    color: selected ? UiColors.accent : UiColors.text,
    fontSize: 13.5,
    fontWeight: 600,
    lineHeight: 1.3,
  };
  const subtitleStyle: CSSProperties = {
    ...UiTypography.mono,
    color: subtitleColor,
    fontSize: 10.5,
  };
  const maxWidth = layoutSize?.width ?? Number.POSITIVE_INFINITY;
  const textWidth = Math.max(maxWidth - 16 - _musicBoxRowLeadingWidth - 8 - actionCount * (_musicBoxRowActionSize + 2), 24);
  const adaptiveTitle = _musicBoxAdaptiveListTextStyle({ text: title, baseStyle: titleStyle, width: textWidth });
  const adaptiveSubtitle = _musicBoxAdaptiveListTextStyle({ text: subtitle, baseStyle: subtitleStyle, width: textWidth });
  const text = (
    <Column mainAxisSize="min" crossAxisAlignment="start">
      <HighlightedText text={title} query={query} style={adaptiveTitle} />
      {subtitle.length > 0 ? (
        <>
          <SizedBox height={2} />
          <HighlightedText text={subtitle} query={query} style={adaptiveSubtitle} />
        </>
      ) : null}
    </Column>
  );

  return (
    <div
      ref={layoutRef}
      data-testid={testId}
      style={{ boxSizing: 'border-box' }}
    >
      <Row
        style={{
          minHeight: _musicBoxRowMinHeight,
          padding: '7px 8px',
          backgroundColor: selected ? UiColors.selected : hovered ? hoverColor : UiColors.transparent,
          borderRadius: UiRadii.md,
          border: `1px solid ${selected ? UiColors.accentBorder : UiColors.transparent}`,
          transition: _rowTransition,
        }}
      >
        <SizedBox width={_musicBoxRowLeadingWidth}>
          <Center>{leading}</Center>
        </SizedBox>
        <SizedBox width={8} />
        <Expanded>
          {cardBuilder == null ? (
            text
          ) : (
            <HoverCardAnchor resetKey={cardResetKey} cardWidth={310} cardBuilder={cardBuilder}>
              {text}
            </HoverCardAnchor>
          )}
        </Expanded>
        {trailing.map((action, index) => (
          <Fragment key={index}>
            <SizedBox width={2} />
            {action}
          </Fragment>
        ))}
      </Row>
    </div>
  );
}

export interface _MusicBoxRowActionProps {
  icon: IconData;
  label: string;
  onPressed: (() => void) | null;
  tone?: ButtonTone;
  selected?: boolean;
  loading?: boolean;
  size?: number;
  /** Dart `key` (test id). */
  'data-testid'?: string;
}

/**
 * An icon action inside the scrolling list. Deliberately no tooltip: the
 * Dart version avoided Material tooltips inside a viewport because hovering
 * between tooltip overlays corrupted the Windows accessibility tree
 * (flutter/flutter#182444). The label is exposed as the accessible name
 * instead, so screen readers still announce it.
 */
export function _MusicBoxRowAction({
  icon,
  label,
  onPressed,
  tone = ButtonTone.neutral,
  selected = false,
  loading = false,
  size = _musicBoxRowActionSize,
  'data-testid': testId,
}: _MusicBoxRowActionProps) {
  return (
    <ButtonIcon
      icon={icon}
      tone={tone}
      selected={selected}
      loading={loading}
      onPressed={onPressed}
      size={size}
      ghost={tone !== ButtonTone.primary}
      aria-label={label}
      data-testid={testId}
    />
  );
}

export interface _MusicBoxRequesterAvatarProps {
  requester: MusicBoxRequester;
  currentUser?: CurrentUser | null;
  onResolveUserProfile?: UserProfileResolver | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterCommonRoom?: ((room: PublicRoom) => void) | null;
  userProfileActionBuilder?: UserProfileActionBuilder | null;
  size?: number;
  /** Dart `key` (test id). */
  'data-testid'?: string;
}

/**
 * The person who requested a song: their avatar with the app's user card on
 * hover/tap. No tooltip here — tooltips inside a scrolling list tripped the
 * Windows accessibility bridge in Flutter (flutter/flutter#182444), and the
 * card already names the user.
 */
export function _MusicBoxRequesterAvatar({
  requester,
  currentUser,
  onResolveUserProfile,
  onResolveRoomProfile,
  onEnterCommonRoom,
  userProfileActionBuilder,
  size = 24,
  'data-testid': testId,
}: _MusicBoxRequesterAvatarProps) {
  const config = useAppConfig();
  return (
    <UserHoverCard
      user={
        new UserSummary({
          id: requester.userId,
          username: requester.username,
          displayName: requester.displayName,
          avatarUrl: requester.avatarUrl,
          defaultAvatarKey: requester.defaultAvatarKey,
        })
      }
      currentUser={currentUser ?? undefined}
      onResolveProfile={onResolveUserProfile ?? undefined}
      onResolveRoomProfile={onResolveRoomProfile ?? undefined}
      onEnterCommonRoom={onEnterCommonRoom ?? undefined}
      profileActionBuilder={userProfileActionBuilder ?? undefined}
      showRoomRole
    >
      <div
        role="img"
        aria-label={`点歌人：${requester.displayName}`}
        data-testid={testId}
        style={{ display: 'flex', flexShrink: 0 }}
      >
        <Avatar
          label={requester.avatarLabel}
          imageUrl={config.resolveAssetUrl(requester.avatarUrl)}
          defaultAvatarKey={requester.defaultAvatarKey}
          size={size}
          showBorder={false}
        />
      </div>
    </UserHoverCard>
  );
}

/**
 * Leading glyph for a queue row: the 1-based position normally, a spinner
 * while the server is still fetching the file, a waveform for the current
 * track.
 */
export function _MusicBoxQueueRowLeading({ item, index, isCurrent }: { item: MusicBoxQueueItem; index: number; isCurrent: boolean }) {
  // A download that is already streaming renders like a ready track.
  const loading =
    !item.playable && (item.status === MusicBoxQueueItemStatus.pending || item.status === MusicBoxQueueItemStatus.downloading);
  if (loading) {
    return (
      <SizedBox data-testid={`music-box-queue-leading-loading:${item.id}`} width={14} height={14}>
        <CircularProgressIndicator size={14} strokeWidth={1.8} />
      </SizedBox>
    );
  }
  if (isCurrent) {
    return (
      <span data-testid={`music-box-queue-leading-playing:${item.id}`} style={{ display: 'flex' }}>
        <Icon icon={Icons.graphic_eq} size={18} color={UiColors.accent} semanticLabel="正在播放" />
      </span>
    );
  }
  if (item.status === MusicBoxQueueItemStatus.failed) {
    return (
      <span data-testid={`music-box-queue-leading-failed:${item.id}`} style={{ display: 'flex' }}>
        <Icon icon={Icons.error_outline} size={16} color={UiColors.danger} />
      </span>
    );
  }
  return (
    <Text
      data-testid={`music-box-queue-leading-index:${item.id}`}
      softWrap={false}
      style={{ ...UiTypography.mono, color: UiColors.textMuted, fontSize: 11, fontWeight: 700 }}
    >
      {`${index + 1}`.padStart(2, '0')}
    </Text>
  );
}

export interface _MusicBoxPlaylistRowProps {
  name: string;
  itemCount: number;
  playing: boolean;
  open?: boolean;
  icon: IconData;
  onOpen: () => void;
  hoverColor?: string;
  /** Wraps the leading icon; used to attach a `MusicPlaylistHoverCard`. */
  leadingCard?: ((icon: ReactNode) => ReactNode) | null;
  /** Dart `key` (test id). */
  'data-testid'?: string;
}

/**
 * A saved playlist block in the flat list. Tapping the row unfolds its
 * tracks in place; playback starts from a specific song inside, never from
 * the playlist as a whole. The leading icon carries the playlist card
 * (creator, count, 查看/编辑歌单).
 */
export function _MusicBoxPlaylistRow({
  name,
  itemCount,
  playing,
  open = false,
  icon: iconData,
  onOpen,
  hoverColor = UiColors.surfaceLow,
  leadingCard,
  'data-testid': testId,
}: _MusicBoxPlaylistRowProps) {
  const [hovered, setHoveredState] = useState(false);
  const setHovered = (next: boolean) => {
    if (hovered === next) return;
    setHoveredState(next);
  };
  const regionRef = useRef<HTMLDivElement | null>(null);
  _useMouseRegion(regionRef, setHovered);
  const icon = (
    <Icon
      icon={playing ? Icons.graphic_eq : iconData}
      size={18}
      color={playing ? UiColors.accent : UiColors.textSecondary}
    />
  );
  const leading =
    leadingCard == null ? (
      icon
    ) : (
      // In Flutter the card anchor's own tap recognizer wins the gesture arena
      // over the row's GestureDetector; stopping the click here keeps a tap on
      // the leading card from also folding/unfolding the row.
      <div onClick={(event) => event.stopPropagation()} style={{ display: 'contents' }}>
        {leadingCard(icon)}
      </div>
    );
  return (
    <div
      ref={regionRef}
      role="button"
      aria-label={name}
      data-testid={testId}
      onClick={(event) => {
        if (event.button !== 0) return;
        onOpen();
      }}
      style={{ cursor: SystemMouseCursors.click, boxSizing: 'border-box' }}
    >
      <Row
        style={{
          minHeight: _musicBoxRowMinHeight,
          padding: '7px 8px',
          backgroundColor: playing ? UiColors.selected : hovered ? hoverColor : UiColors.transparent,
          borderRadius: UiRadii.md,
          border: `1px solid ${playing ? UiColors.accentBorder : UiColors.transparent}`,
          transition: _rowTransition,
        }}
      >
        <SizedBox width={_musicBoxRowLeadingWidth}>
          <Center>{leading}</Center>
        </SizedBox>
        <SizedBox width={8} />
        <Expanded>
          <Column mainAxisSize="min" crossAxisAlignment="start">
            <Text
              style={{
                ...UiTypography.body,
                color: playing ? UiColors.accent : UiColors.text,
                fontSize: 13.5,
                fontWeight: 600,
              }}
            >
              {name}
            </Text>
            <SizedBox height={2} />
            <Text style={{ ...UiTypography.mono, color: UiColors.textMuted, fontSize: 10.5 }}>
              {playing ? `正在播放 · ${itemCount} 首` : `${itemCount} 首`}
            </Text>
          </Column>
        </Expanded>
        <SizedBox width={2} />
        <Icon icon={open ? Icons.expand_more : Icons.chevron_right} size={16} color={UiColors.textMuted} />
      </Row>
    </div>
  );
}

export interface _MusicBoxSectionHeaderProps {
  title: string;
  leading?: ReactNode;
  trailing?: readonly ReactNode[];
  channel?: string | null;
  channelLit?: boolean;
}

/**
 * The compact status line above a list: what it is, how many songs, and a
 * slot for the one contextual action that belongs to the whole list.
 */
export function _MusicBoxSectionHeader({ title, leading, trailing = [], channel, channelLit = false }: _MusicBoxSectionHeaderProps) {
  // The row sizes to its tallest action rather than clipping it.
  return (
    <Row crossAxisAlignment="center" style={{ minHeight: _musicBoxControlHeight }}>
      {channel != null ? (
        <>
          <SizedBox width={6} />
          <Text softWrap={false} style={{ ...UiTypography.dot, fontSize: 13, color: channelLit ? UiColors.accent : UiColors.textMuted }}>
            {channel}
          </Text>
          <SizedBox width={10} />
        </>
      ) : null}
      {leading != null ? (
        <>
          {leading}
          <SizedBox width={6} />
        </>
      ) : null}
      <Expanded>
        <_MusicBoxFadeText text={title} style={{ ...UiTypography.label, color: UiColors.text, fontWeight: 700 }} />
      </Expanded>
      {trailing.map((action, index) => (
        <Fragment key={index}>
          <SizedBox width={4} />
          {action}
        </Fragment>
      ))}
    </Row>
  );
}

export interface _MusicBoxEmptyProps {
  icon: IconData;
  message: string;
  hint?: string | null;
  /** Dart `actionKey` (test id of the action button). */
  actionKey?: string | null;
  actionLabel?: string | null;
  onAction?: (() => void) | null;
}

export function _MusicBoxEmpty({ icon, message, hint, actionKey, actionLabel, onAction }: _MusicBoxEmptyProps) {
  return (
    <_MusicBoxScaleDown>
      <Column mainAxisSize="min">
        <Icon icon={icon} size={30} color={UiColors.textMuted} />
        <SizedBox height={10} />
        <Text
          textAlign="center"
          style={{ fontFamily: kClientFontStack, color: UiColors.textMuted, fontSize: 12, fontWeight: 600 }}
        >
          {message}
        </Text>
        {hint != null ? (
          <>
            <SizedBox height={4} />
            <Text textAlign="center" style={{ fontFamily: kClientFontStack, color: UiColors.textMuted, fontSize: 11 }}>
              {hint}
            </Text>
          </>
        ) : null}
        {actionLabel != null && onAction != null ? (
          <>
            <SizedBox height={14} />
            <Button
              data-testid={actionKey ?? undefined}
              icon={<Icon icon={Icons.add} />}
              tone={ButtonTone.primary}
              height={34}
              padding={EdgeInsets.symmetric({ horizontal: 13 })}
              onPressed={onAction}
              // Under the FittedBox the button gets unbounded constraints and
              // shrink-wraps, centred by the Column (not stretched).
              style={{ alignSelf: 'center' }}
            >
              {actionLabel}
            </Button>
          </>
        ) : null}
      </Column>
    </_MusicBoxScaleDown>
  );
}

export function _MusicBoxLoading() {
  return (
    <Center style={{ width: '100%', height: '100%' }}>
      <SizedBox width={22} height={22}>
        <CircularProgressIndicator size={22} strokeWidth={2} />
      </SizedBox>
    </Center>
  );
}

export function _MusicBoxRetryState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <Center style={{ width: '100%', height: '100%' }}>
      <Column mainAxisSize="min">
        <Icon icon={Icons.error_outline} size={28} color={UiColors.textMuted} />
        <SizedBox height={8} />
        <Text
          textAlign="center"
          style={{ fontFamily: kClientFontStack, color: UiColors.textMuted, fontSize: 11, fontWeight: 600 }}
        >
          {message}
        </Text>
        <SizedBox height={8} />
        <ButtonIcon data-testid="music-box-retry" icon={Icons.refresh} tooltip="重试" onPressed={onRetry} size={30} />
      </Column>
    </Center>
  );
}

export function _musicBoxPlaylistItemAsResult(item: PersonalMusicPlaylistItem): MusicBoxSearchResult {
  return new MusicBoxSearchResult({
    trackId: item.trackId,
    name: item.title,
    artists: item.artists,
    source: item.source,
  });
}
