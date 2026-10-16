import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from 'react';

import { SystemMouseCursors } from '../lib/mouse_cursors';
import { measureTextWidth } from '../lib/text_measure';
import { useElementSize } from '../lib/use_element_size';
import { BadgeDot } from './badge_dot';
import { UiContextMenuItem, UiContextMenuSection, showUiContextMenu } from './context_menu';
import { Icon, Icons, type IconData } from './icon';
import { rectInWindow } from './overlay';
import { PressableSurface } from './surface';
import { UiColors, UiMotion, UiRadii, UiShadows, argb, boxShadow, kClientFontStack } from './tokens';

export class Segment<T> {
  constructor(opts: { value: T; label: string; icon?: IconData | null; showBadge?: boolean; badgeKey?: string | null }) {
    this.value = opts.value;
    this.label = opts.label;
    this.icon = opts.icon ?? null;
    this.showBadge = opts.showBadge ?? false;
    this.badgeKey = opts.badgeKey ?? null;
  }

  readonly value: T;
  readonly label: string;
  readonly icon: IconData | null;
  readonly showBadge: boolean;
  /** Test id of the badge dot (Dart `badgeKey`). */
  readonly badgeKey: string | null;
}

export interface SegmentedControlProps<T> {
  segments: readonly Segment<T>[];
  value: T;
  onChanged: (value: T) => void;
  expanded?: boolean;
  /** Height of each segment. Defaults to 36; shrink for denser layouts. */
  height?: number;
  /** Outer box style (DOM addition). */
  style?: CSSProperties;
}

const _segmentPadding = 10;
const _minimumSegmentPadding = 6;
const _iconSize = 15;
const _iconGap = 6;
const _overflowControlGap = 6;
// Canvas and DOM text layout can differ by a few subpixels across platform
// font fallbacks. Keep a small buffer so a fully measured label is never
// forced back into clipping at a fractional-width boundary.
const _contentWidthSafety = 6;
const _thumbInset = 3;
const _duration = 180;
const _segmentTextStyle: CSSProperties = {
  fontFamily: kClientFontStack,
  fontSize: 13,
  fontWeight: 600,
};
// Flutter's drag slop for precise (mouse) pointers.
const _mouseDragSlop = 1;

function _sum(values: Iterable<number>): number {
  let total = 0;
  for (const value of values) total += value;
  return total;
}

function _segmentWidthsDiffer(left: readonly number[], right: readonly number[]): boolean {
  if (left.length !== right.length) return true;
  for (let index = 0; index < left.length; index += 1) {
    if (Math.abs(left[index]! - right[index]!) > 0.5) return true;
  }
  return false;
}

export function measureSegmentContentWidth<T>(segment: Segment<T>): number {
  const textWidth = measureTextWidth(segment.label, _segmentTextStyle);
  const iconWidth = segment.icon == null ? 0 : _iconSize + _iconGap;
  const badgeWidth = segment.showBadge ? 8 : 0;
  return textWidth + iconWidth + badgeWidth + _contentWidthSafety;
}

/**
 * The narrowest width that can show every segment without entering the
 * overflow/scroll layout.
 */
function minimumWidthFor<T>(segments: readonly Segment<T>[]): number {
  return _sum(segments.map((segment) => measureSegmentContentWidth(segment) + _minimumSegmentPadding * 2));
}

interface _SegmentWidthFit {
  widths: number[];
  horizontalPadding: number;
}

export function _fitSegmentWidths(opts: {
  segmentCount: number;
  contentWidths: readonly number[];
  preferredWidths: readonly number[];
  minimumWidth: number;
  preferredWidth: number;
  targetWidth: number;
}): _SegmentWidthFit {
  const { segmentCount, contentWidths, preferredWidths, minimumWidth, preferredWidth, targetWidth } = opts;
  if (targetWidth >= preferredWidth) {
    const scale = preferredWidth <= 0 ? 1 : targetWidth / preferredWidth;
    return { widths: preferredWidths.map((width) => width * scale), horizontalPadding: _segmentPadding };
  }

  const contentWidth = _sum(contentWidths);
  const padding =
    segmentCount === 0
      ? _minimumSegmentPadding
      : Math.min(Math.max((targetWidth - contentWidth) / (segmentCount * 2), _minimumSegmentPadding), _segmentPadding);
  const widths = contentWidths.map((width) => width + padding * 2);
  const roundingDelta = targetWidth - _sum(widths);
  if (widths.length > 0 && targetWidth >= minimumWidth) {
    widths[widths.length - 1]! += roundingDelta;
  }
  return { widths, horizontalPadding: padding };
}

/** Eased programmatic scroll (`ScrollController.animateTo`). */
function animateScrollTo(node: HTMLElement, target: number, duration: number): Promise<void> {
  return new Promise((resolve) => {
    const start = node.scrollLeft;
    const delta = target - start;
    if (Math.abs(delta) < 0.5 || duration <= 0) {
      node.scrollLeft = target;
      resolve();
      return;
    }
    const startedAt = performance.now();
    // Curves.easeOutCubic.
    const ease = (t: number) => 1 - Math.pow(1 - t, 3);
    const step = (now: number) => {
      const t = Math.min(1, (now - startedAt) / duration);
      node.scrollLeft = start + delta * ease(t);
      if (t < 1) requestAnimationFrame(step);
      else resolve();
    };
    requestAnimationFrame(step);
  });
}

function SegmentedControlImpl<T>({ segments, value, onChanged, expanded = false, height = 36, style }: SegmentedControlProps<T>) {
  const outerRef = useRef<HTMLDivElement | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const menuAnchorRef = useRef<HTMLDivElement | null>(null);
  const outerSize = useElementSize(outerRef);
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const [pressedIndex, setPressedIndex] = useState<number | null>(null);
  const [showJumpToStart, setShowJumpToStart] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  const layoutState = useRef({
    overflowLayoutLastBuild: false,
    selectedIndexLastBuild: null as number | null,
    overflowAvailableWidthLastBuild: null as number | null,
    centerSelectedScheduled: false,
    overflowSegmentWidths: [] as number[],
    showJumpToStart: false,
  });
  layoutState.current.showJumpToStart = showJumpToStart;

  const selectedIndexRaw = segments.findIndex((segment) => segment.value === value);
  const selectedIndexOf = () => {
    const index = segments.findIndex((segment) => segment.value === value);
    return index < 0 ? 0 : index;
  };

  const contentWidths = segments.map((segment) => measureSegmentContentWidth(segment));
  const preferredWidths = contentWidths.map((width) => width + _segmentPadding * 2);
  const minimumWidths = contentWidths.map((width) => width + _minimumSegmentPadding * 2);
  const preferredWidth = _sum(preferredWidths);
  const minimumWidth = _sum(minimumWidths);
  // LayoutBuilder: until the box is measured, lay out at the preferred width.
  const constrained = outerSize != null;
  const availableWidth = constrained ? Math.max(outerSize.width, 0) : preferredWidth;
  const selectedIndex = Math.min(Math.max(selectedIndexRaw < 0 ? 0 : selectedIndexRaw, 0), Math.max(segments.length - 1, 0));
  const usesOverflowLayout = segments.length > 0 && constrained && minimumWidth > availableWidth;

  const centerSegment = (index: number) => {
    const node = scrollRef.current;
    const widths = layoutState.current.overflowSegmentWidths;
    if (node == null || index < 0 || index >= widths.length) return;
    const start = _sum(widths.slice(0, index));
    const end = start + widths[index]!;
    const futureJumpButtonSpace = layoutState.current.showJumpToStart || start <= 0 ? 0 : height + _overflowControlGap;
    const viewportWidth = Math.max(node.clientWidth - futureJumpButtonSpace, 0);
    const maxScroll = Math.max(node.scrollWidth - node.clientWidth, 0);
    const target = Math.min(Math.max((start + end) / 2 - viewportWidth / 2, 0), maxScroll);
    if (Math.abs(target - node.scrollLeft) < 0.5) return;
    void animateScrollTo(node, target, _duration).then(() => {
      if (mounted.current && layoutState.current.overflowLayoutLastBuild) {
        // Showing the left jump button reduces the viewport after the first
        // scroll frame. A second pass uses the final viewport and reaches the
        // exact center, or the nearest scroll edge when centering is impossible.
        scheduleCenterSelectedSegment(index);
      }
    });
  };

  const scheduleCenterSelectedSegment = (index: number) => {
    if (layoutState.current.centerSelectedScheduled) return;
    layoutState.current.centerSelectedScheduled = true;
    requestAnimationFrame(() => {
      layoutState.current.centerSelectedScheduled = false;
      if (!mounted.current || !layoutState.current.overflowLayoutLastBuild) return;
      centerSegment(index);
    });
  };

  // Bookkeeping the Dart build did inline, run after commit.
  useLayoutEffect(() => {
    const s = layoutState.current;
    if (usesOverflowLayout) {
      const overflowLayoutChanged =
        !s.overflowLayoutLastBuild ||
        s.overflowAvailableWidthLastBuild == null ||
        Math.abs(s.overflowAvailableWidthLastBuild - availableWidth) > 0.5 ||
        _segmentWidthsDiffer(s.overflowSegmentWidths, preferredWidths);
      s.overflowSegmentWidths = preferredWidths;
      if (overflowLayoutChanged || s.selectedIndexLastBuild !== selectedIndex) {
        s.overflowLayoutLastBuild = true;
        scheduleCenterSelectedSegment(selectedIndex);
      }
      s.overflowLayoutLastBuild = true;
      s.overflowAvailableWidthLastBuild = availableWidth;
      s.selectedIndexLastBuild = selectedIndex;
    } else {
      s.overflowLayoutLastBuild = false;
      s.overflowAvailableWidthLastBuild = null;
      s.selectedIndexLastBuild = selectedIndex;
    }
  });

  const selectSegment = (index: number) => {
    onChanged(segments[index]!.value);
    if (layoutState.current.overflowLayoutLastBuild) {
      scheduleCenterSelectedSegment(index);
    }
  };

  const scrollToStart = () => {
    const node = scrollRef.current;
    if (node == null) return;
    void animateScrollTo(node, 0, _duration);
  };

  const showSelectionMenu = async () => {
    if (menuOpen || segments.length === 0) return;
    const anchor = rectInWindow(menuAnchorRef);
    if (anchor == null) return;
    setMenuOpen(true);
    try {
      await showUiContextMenu({
        position: { x: anchor.x, y: anchor.y + anchor.height },
        sections: [
          new UiContextMenuSection(
            segments.map(
              (segment, index) =>
                new UiContextMenuItem({
                  label: segment.label,
                  icon: segment.icon,
                  selected: index === selectedIndexOf(),
                  onPressed: () => selectSegment(index),
                }),
            ),
          ),
        ],
      });
    } finally {
      if (mounted.current) setMenuOpen(false);
    }
  };

  // Wheel → horizontal scroll and mouse drag scrolling for the overflow strip.
  useEffect(() => {
    const node = scrollRef.current;
    if (node == null || !usesOverflowLayout) return;
    const onWheel = (event: WheelEvent) => {
      const horizontalDelta = event.deltaX;
      const delta = Math.abs(horizontalDelta) > 0 ? horizontalDelta : event.deltaY;
      if (delta === 0) return;
      const maxScroll = Math.max(node.scrollWidth - node.clientWidth, 0);
      const target = Math.min(Math.max(node.scrollLeft + delta, 0), maxScroll);
      event.preventDefault();
      node.scrollLeft = target;
    };
    let drag: { pointerId: number; startX: number; startScroll: number; dragging: boolean } | null = null;
    let suppressClick = false;
    const onPointerDown = (event: PointerEvent) => {
      if (event.pointerType !== 'mouse' || event.button !== 0) return;
      drag = { pointerId: event.pointerId, startX: event.clientX, startScroll: node.scrollLeft, dragging: false };
    };
    const onPointerMove = (event: PointerEvent) => {
      if (drag == null || drag.pointerId !== event.pointerId) return;
      const dx = event.clientX - drag.startX;
      if (!drag.dragging && Math.abs(dx) > _mouseDragSlop) {
        drag.dragging = true;
        try {
          node.setPointerCapture(event.pointerId);
        } catch {
          // Best effort.
        }
      }
      if (drag.dragging) node.scrollLeft = drag.startScroll - dx;
    };
    const onPointerUp = (event: PointerEvent) => {
      if (drag == null || drag.pointerId !== event.pointerId) return;
      // A drag wins over the segment tap, like Flutter's gesture arena.
      if (drag.dragging) suppressClick = true;
      drag = null;
    };
    const onClickCapture = (event: MouseEvent) => {
      if (!suppressClick) return;
      suppressClick = false;
      event.stopPropagation();
      event.preventDefault();
    };
    node.addEventListener('wheel', onWheel, { passive: false });
    node.addEventListener('pointerdown', onPointerDown);
    node.addEventListener('pointermove', onPointerMove);
    node.addEventListener('pointerup', onPointerUp);
    node.addEventListener('pointercancel', onPointerUp);
    node.addEventListener('click', onClickCapture, true);
    return () => {
      node.removeEventListener('wheel', onWheel);
      node.removeEventListener('pointerdown', onPointerDown);
      node.removeEventListener('pointermove', onPointerMove);
      node.removeEventListener('pointerup', onPointerUp);
      node.removeEventListener('pointercancel', onPointerUp);
      node.removeEventListener('click', onClickCapture, true);
    };
  }, [usesOverflowLayout]);

  const renderTrack = (segmentWidths: readonly number[], horizontalPadding: number) => {
    const selectedLeft = _sum(segmentWidths.slice(0, selectedIndex));
    const selectedPressed = pressedIndex === selectedIndex;
    return (
      <div style={{ position: 'relative', width: '100%', height: '100%' }}>
        <div
          style={{
            position: 'absolute',
            inset: 0,
            boxSizing: 'border-box',
            backgroundColor: UiColors.fieldFill,
            borderRadius: UiRadii.pill,
            border: `1px solid ${UiColors.border}`,
          }}
        />
        <_SegmentThumb
          pressed={selectedPressed}
          style={{
            position: 'absolute',
            left: selectedLeft + _thumbInset,
            top: _thumbInset,
            width: Math.max((segmentWidths[selectedIndex] ?? 0) - _thumbInset * 2, 0),
            height: Math.max(height - _thumbInset * 2, 0),
          }}
        />
        <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'row' }}>
          {segments.map((segment, index) => (
            <div key={index} style={{ width: segmentWidths[index], height, flexShrink: 0 }}>
              <_SegmentHitTarget
                segment={segment}
                selected={index === selectedIndex}
                hovered={index === hoveredIndex}
                height={height}
                horizontalPadding={horizontalPadding}
                onHoverChanged={(hovered) => {
                  setHoveredIndex(hovered ? index : null);
                  if (!hovered) setPressedIndex((pressed) => (pressed === index ? null : pressed));
                }}
                onPressedChanged={(pressed) => setPressedIndex(pressed ? index : null)}
                onTap={() => selectSegment(index)}
              />
            </div>
          ))}
        </div>
      </div>
    );
  };

  if (segments.length === 0) return null;

  let body;
  let boxWidth: CSSProperties['width'];
  if (usesOverflowLayout) {
    boxWidth = '100%';
    body = (
      <div style={{ display: 'flex', flexDirection: 'row', alignItems: 'flex-start', width: '100%', height }}>
        {showJumpToStart ? (
          <>
            <_SegmentControlIconButton tooltip="回到开头" icon={Icons.keyboard_double_arrow_left_rounded} size={height} onPressed={scrollToStart} />
            <div style={{ width: _overflowControlGap, flexShrink: 0 }} />
          </>
        ) : null}
        <div
          ref={scrollRef}
          data-testid="segmented-control-scroll-view"
          onScroll={(event) => {
            const show = event.currentTarget.scrollLeft > 1;
            if (show !== layoutState.current.showJumpToStart) setShowJumpToStart(show);
          }}
          style={{ flex: '1 1 0', minWidth: 0, overflowX: 'auto', overflowY: 'hidden', scrollbarWidth: 'none' }}
        >
          <div style={{ width: _sum(preferredWidths), height }}>{renderTrack(preferredWidths, _segmentPadding)}</div>
        </div>
        <div style={{ width: _overflowControlGap, flexShrink: 0 }} />
        <div ref={menuAnchorRef} style={{ flexShrink: 0 }}>
          <_SegmentControlIconButton
            tooltip="选择选项"
            icon={Icons.menu_rounded}
            size={height}
            selected={menuOpen}
            onPressed={() => void showSelectionMenu()}
          />
        </div>
      </div>
    );
  } else {
    const targetWidth = expanded && constrained ? availableWidth : constrained && preferredWidth > availableWidth ? availableWidth : preferredWidth;
    const fit = _fitSegmentWidths({
      segmentCount: segments.length,
      contentWidths,
      preferredWidths,
      minimumWidth,
      preferredWidth,
      targetWidth,
    });
    boxWidth = expanded && constrained ? '100%' : targetWidth;
    body = <div style={{ width: boxWidth, height }}>{renderTrack(fit.widths, fit.horizontalPadding)}</div>;
  }

  // The measuring box is the LayoutBuilder: its natural width is the preferred
  // width, capped by the parent (`maxWidth: 100%`) and allowed to shrink in a
  // row, so its measured width is `constraints.maxWidth` clamped to what the
  // control wants.
  return (
    <div
      ref={outerRef}
      style={{
        width: expanded ? '100%' : preferredWidth,
        maxWidth: '100%',
        minWidth: 0,
        flexShrink: 1,
        height,
        position: 'relative',
        ...style,
      }}
    >
      <div style={{ position: 'absolute', left: 0, top: 0, width: boxWidth, maxWidth: '100%', height }}>{body}</div>
    </div>
  );
}

export const SegmentedControl = Object.assign(SegmentedControlImpl, { minimumWidthFor });

function _SegmentControlIconButton({
  tooltip,
  icon,
  size,
  onPressed,
  selected = false,
}: {
  tooltip: string;
  icon: IconData;
  size: number;
  onPressed: () => void;
  selected?: boolean;
}) {
  return (
    <PressableSurface
      tooltip={tooltip}
      onPressed={onPressed}
      selected={selected}
      width={size}
      height={size}
      borderRadius={UiRadii.pill}
      backgroundColor={UiColors.fieldFill}
      selectedBackgroundColor={UiColors.selected}
      borderColor={UiColors.border}
      selectedBorderColor={UiColors.accentBorder}
      contentStyle={{ alignItems: 'center' }}
    >
      <Icon icon={icon} size={_iconSize + 2} color={selected ? UiColors.accent : UiColors.textSecondary} />
    </PressableSurface>
  );
}

function _SegmentThumb({ pressed, style }: { pressed: boolean; style: CSSProperties }) {
  const color = pressed ? argb(0xffd9d9d6) : UiColors.inverse;
  const curve = UiMotion.curve;
  return (
    <div
      style={{
        ...style,
        backgroundColor: color,
        borderRadius: UiRadii.pill,
        boxShadow: boxShadow(UiShadows.raised),
        // AnimatedPositioned (180ms easeOutCubic) + AnimatedContainer colour.
        transition: `left ${_duration}ms ${curve}, width ${_duration}ms ${curve}, top ${_duration}ms ${curve}, height ${_duration}ms ${curve}, background-color ${UiMotion.fast}ms ${curve}`,
      }}
    />
  );
}

function _SegmentHitTarget<T>({
  segment,
  selected,
  hovered,
  height,
  horizontalPadding,
  onHoverChanged,
  onPressedChanged,
  onTap,
}: {
  segment: Segment<T>;
  selected: boolean;
  hovered: boolean;
  height: number;
  horizontalPadding: number;
  onHoverChanged: (hovered: boolean) => void;
  onPressedChanged: (pressed: boolean) => void;
  onTap: () => void;
}) {
  const foreground = selected ? UiColors.onInverse : hovered ? UiColors.text : UiColors.textSecondary;
  return (
    <div
      role="tab"
      aria-selected={selected}
      onMouseEnter={() => onHoverChanged(true)}
      onMouseLeave={() => onHoverChanged(false)}
      onPointerDown={(event) => {
        if (event.button === 0) onPressedChanged(true);
      }}
      onPointerUp={() => onPressedChanged(false)}
      onPointerCancel={() => onPressedChanged(false)}
      onClick={(event) => {
        if (event.button !== 0) return;
        onTap();
      }}
      style={{
        height,
        boxSizing: 'border-box',
        padding: `0 ${horizontalPadding}px`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        cursor: SystemMouseCursors.click,
      }}
    >
      <div style={{ position: 'relative', display: 'flex', flexDirection: 'row', alignItems: 'center', whiteSpace: 'nowrap' }}>
        {segment.icon != null ? (
          <>
            <Icon icon={segment.icon} size={_iconSize} color={foreground} />
            <div style={{ width: _iconGap, flexShrink: 0 }} />
          </>
        ) : null}
        <span style={{ ..._segmentTextStyle, color: foreground, fontWeight: selected ? 600 : 500, whiteSpace: 'nowrap' }}>
          {segment.label}
        </span>
        {segment.showBadge ? (
          <div data-testid={segment.badgeKey ?? undefined} style={{ position: 'absolute', top: -3, right: -6 }}>
            <BadgeDot size={7} />
          </div>
        ) : null}
      </div>
    </div>
  );
}
