import { useEffect, useImperativeHandle, useLayoutEffect, useRef, useState, type ReactNode, type Ref } from 'react';

import { insertOverlay, type OverlayEntryHandle } from './overlay';
import { UiCurves, UiShadows, UiTooltipTheme, boxShadow } from './tokens';

/**
 * Material [Tooltip] port (`UiTooltip`).
 *
 * Follows Material's desktop behaviour: the bubble appears after
 * [waitDuration] of hover (immediately when another tooltip is already open),
 * hides [exitDuration] after the pointer leaves, and any pointer down anywhere
 * hides every tooltip. The bubble is drawn in the app overlay below (or above)
 * the child, centred on it and kept 10px from the window edges
 * (`positionDependentBox`).
 *
 * The Dart wrapper existed to give each tooltip its own semantics node; the
 * DOM has no equivalent problem. The anchor wrapper here is `display:
 * contents`, so wrapping a child in a tooltip never changes its layout.
 * Parameter names mirror Material's [Tooltip] so call sites are interchangeable.
 */

export type TooltipTriggerMode = 'longPress' | 'tap' | 'manual';

export interface UiTooltipState {
  /** Shows the tooltip programmatically; mirrors `TooltipState`. */
  ensureTooltipVisible(): boolean;
}

export interface UiTooltipProps {
  message: string;
  children?: ReactNode;
  /** Hover delay in ms (theme default 380). */
  waitDuration?: number | null;
  /** Hide delay after the pointer leaves, in ms (Material default 100). */
  exitDuration?: number | null;
  preferBelow?: boolean | null;
  verticalOffset?: number | null;
  /**
   * Touch trigger. Mouse hover shows the tooltip whatever this is, as in
   * Flutter; the desktop app has no touch long-press trigger.
   */
  triggerMode?: TooltipTriggerMode | null;
  ref?: Ref<UiTooltipState>;
}

const kDefaultVerticalOffset = 24;
const kDefaultHoverExitDuration = 100;
const kFadeInDuration = 150;
const kFadeOutDuration = 75;
const kScreenMargin = 10;
// Material's desktop tooltip height.
const kTooltipMinHeight = 24;
const kMaxTimeout = 2_147_483_647;

interface AnchorRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

class TooltipController {
  static readonly opened = new Set<TooltipController>();
  static readonly pending = new Set<TooltipController>();
  private static globalListenerInstalled = false;

  props: UiTooltipProps;
  anchor: HTMLElement | null = null;
  hovering = false;
  rect: AnchorRect | null = null;
  /** Whether the bubble is fading in / shown (false while fading out). */
  visible = false;
  private showTimer: ReturnType<typeof setTimeout> | null = null;
  private hideTimer: ReturnType<typeof setTimeout> | null = null;
  private removeTimer: ReturnType<typeof setTimeout> | null = null;
  private entry: OverlayEntryHandle | null = null;
  private disposed = false;

  constructor(props: UiTooltipProps) {
    this.props = props;
    TooltipController.installGlobalListener();
  }

  static dismissAll(): void {
    for (const tooltip of [...TooltipController.opened, ...TooltipController.pending]) {
      tooltip.scheduleDismiss(0);
    }
  }

  private static installGlobalListener(): void {
    if (TooltipController.globalListenerInstalled || typeof document === 'undefined') return;
    TooltipController.globalListenerInstalled = true;
    // Material dismisses every tooltip on any pointer down.
    document.addEventListener('pointerdown', () => TooltipController.dismissAll(), true);
  }

  get isOpen(): boolean {
    return this.entry != null && this.visible;
  }

  handleEnter(): void {
    this.hovering = true;
    // Dismiss other open tooltips unless their own pointer still hovers them;
    // when one was open, show this one without the hover delay.
    const toDismiss = [...TooltipController.opened].filter((t) => t !== this && !t.hovering);
    for (const tooltip of toDismiss) tooltip.scheduleDismiss(0);
    this.scheduleShow(toDismiss.length > 0 ? 0 : (this.props.waitDuration ?? UiTooltipTheme.waitDuration));
  }

  handleExit(): void {
    this.hovering = false;
    this.scheduleDismiss(this.props.exitDuration ?? kDefaultHoverExitDuration);
  }

  scheduleShow(delay: number): void {
    if (this.disposed || this.props.message.length === 0) return;
    this.clearHideTimer();
    if (this.isOpen) return;
    this.clearShowTimer();
    if (delay <= 0) {
      this.show();
      return;
    }
    TooltipController.pending.add(this);
    this.showTimer = setTimeout(() => {
      this.showTimer = null;
      TooltipController.pending.delete(this);
      this.show();
    }, delay);
  }

  scheduleDismiss(delay: number): void {
    this.clearShowTimer();
    if (!this.isOpen) return;
    this.clearHideTimer();
    if (delay <= 0) {
      this.hide();
      return;
    }
    // A pinned tooltip passes a year-long exit duration: never arm that timer.
    if (delay > kMaxTimeout) return;
    this.hideTimer = setTimeout(() => {
      this.hideTimer = null;
      this.hide();
    }, delay);
  }

  show(): boolean {
    if (this.disposed || this.anchor == null || this.props.message.length === 0) return false;
    const rect = anchorRect(this.anchor);
    if (rect == null) return false;
    this.rect = rect;
    this.clearHideTimer();
    this.clearRemoveTimer();
    TooltipController.opened.add(this);
    this.visible = true;
    if (this.entry != null) {
      this.entry.markNeedsBuild();
      return true;
    }
    this.entry = insertOverlay(() => (
      <TooltipBubble
        message={this.props.message}
        anchor={this.rect!}
        preferBelow={this.props.preferBelow ?? true}
        verticalOffset={this.props.verticalOffset ?? kDefaultVerticalOffset}
        visible={this.visible}
      />
    ));
    return true;
  }

  hide(): void {
    const entry = this.entry;
    if (entry == null || !this.visible) return;
    TooltipController.opened.delete(this);
    this.visible = false;
    entry.markNeedsBuild();
    this.clearRemoveTimer();
    this.removeTimer = setTimeout(() => {
      this.removeTimer = null;
      if (this.visible) return;
      this.entry?.remove();
      this.entry = null;
    }, kFadeOutDuration);
  }

  refresh(): void {
    if (this.entry == null) return;
    if (this.props.message.length === 0) {
      this.removeImmediately();
      return;
    }
    this.entry.markNeedsBuild();
  }

  dispose(): void {
    this.disposed = true;
    this.clearShowTimer();
    this.clearHideTimer();
    this.removeImmediately();
  }

  private removeImmediately(): void {
    TooltipController.opened.delete(this);
    this.clearRemoveTimer();
    this.visible = false;
    this.entry?.remove();
    this.entry = null;
  }

  private clearShowTimer(): void {
    TooltipController.pending.delete(this);
    if (this.showTimer != null) clearTimeout(this.showTimer);
    this.showTimer = null;
  }

  private clearHideTimer(): void {
    if (this.hideTimer != null) clearTimeout(this.hideTimer);
    this.hideTimer = null;
  }

  private clearRemoveTimer(): void {
    if (this.removeTimer != null) clearTimeout(this.removeTimer);
    this.removeTimer = null;
  }
}

/** Union of the anchor's rendered boxes (the wrapper itself is `display: contents`). */
function anchorRect(anchor: HTMLElement): AnchorRect | null {
  let left = Infinity;
  let top = Infinity;
  let right = -Infinity;
  let bottom = -Infinity;
  const children = anchor.children.length > 0 ? Array.from(anchor.children) : [anchor];
  for (const child of children) {
    const r = child.getBoundingClientRect();
    if (r.width === 0 && r.height === 0) continue;
    left = Math.min(left, r.left);
    top = Math.min(top, r.top);
    right = Math.max(right, r.right);
    bottom = Math.max(bottom, r.bottom);
  }
  if (!Number.isFinite(left)) return null;
  return { x: left, y: top, width: right - left, height: bottom - top };
}

/** Port of Flutter's `positionDependentBox`. */
export function positionDependentBox(opts: {
  size: { width: number; height: number };
  childSize: { width: number; height: number };
  target: { x: number; y: number };
  preferBelow: boolean;
  verticalOffset?: number;
  margin?: number;
}): { x: number; y: number } {
  const { size, childSize, target, preferBelow, verticalOffset = 0, margin = kScreenMargin } = opts;
  // VERTICAL DIRECTION
  const fitsBelow = target.y + verticalOffset + childSize.height <= size.height - margin;
  const fitsAbove = target.y - verticalOffset - childSize.height >= margin;
  const tooltipBelow = fitsAbove === fitsBelow ? preferBelow : fitsBelow;
  const y = tooltipBelow
    ? Math.min(target.y + verticalOffset, size.height - margin)
    : Math.max(target.y - verticalOffset - childSize.height, margin);
  // HORIZONTAL DIRECTION
  const flexibleSpace = size.width - childSize.width;
  const x =
    flexibleSpace <= 2 * margin
      ? // If there's not enough horizontal space for margin + child, center the child.
        flexibleSpace / 2
      : Math.min(Math.max(target.x - childSize.width / 2, margin), flexibleSpace - margin);
  return { x, y };
}

function TooltipBubble({
  message,
  anchor,
  preferBelow,
  verticalOffset,
  visible,
}: {
  message: string;
  anchor: AnchorRect;
  preferBelow: boolean;
  verticalOffset: number;
  visible: boolean;
}) {
  const ref = useRef<HTMLDivElement | null>(null);
  const [position, setPosition] = useState<{ x: number; y: number } | null>(null);
  const [shown, setShown] = useState(false);

  useLayoutEffect(() => {
    const node = ref.current;
    if (node == null) return;
    const childSize = { width: node.offsetWidth, height: node.offsetHeight };
    const next = positionDependentBox({
      size: { width: window.innerWidth, height: window.innerHeight },
      childSize,
      target: { x: anchor.x + anchor.width / 2, y: anchor.y + anchor.height / 2 },
      preferBelow,
      verticalOffset,
    });
    if (position == null || Math.abs(position.x - next.x) > 0.5 || Math.abs(position.y - next.y) > 0.5) {
      setPosition(next);
    }
  });

  useEffect(() => {
    if (position == null) return;
    // Next frame so the opacity transition starts from 0.
    const frame = requestAnimationFrame(() => setShown(visible));
    return () => cancelAnimationFrame(frame);
  }, [position, visible]);

  const opaque = position != null && shown && visible;
  return (
    <div
      ref={ref}
      style={{
        position: 'fixed',
        left: position?.x ?? 0,
        top: position?.y ?? 0,
        maxWidth: `calc(100vw - ${kScreenMargin * 2}px)`,
        minHeight: kTooltipMinHeight,
        boxSizing: 'border-box',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: UiTooltipTheme.background,
        borderRadius: UiTooltipTheme.radius,
        border: `1px solid ${UiTooltipTheme.border}`,
        boxShadow: boxShadow(UiShadows.raised),
        padding: `${UiTooltipTheme.paddingVertical}px ${UiTooltipTheme.paddingHorizontal}px`,
        pointerEvents: 'none',
        opacity: opaque ? 1 : 0,
        transition: `opacity ${visible ? kFadeInDuration : kFadeOutDuration}ms ${UiCurves.fastOutSlowIn}`,
        zIndex: 1,
      }}
    >
      <span style={{ ...UiTooltipTheme.textStyle, whiteSpace: 'pre-wrap', overflowWrap: 'break-word' }}>{message}</span>
    </div>
  );
}

function UiTooltipImpl({ ref, ...props }: UiTooltipProps) {
  const controllerRef = useRef<TooltipController | null>(null);
  if (controllerRef.current == null) controllerRef.current = new TooltipController(props);
  const controller = controllerRef.current;
  controller.props = props;

  useImperativeHandle(ref, () => ({ ensureTooltipVisible: () => controller.show() }), [controller]);

  useLayoutEffect(() => {
    controller.refresh();
  });

  useEffect(() => () => controller.dispose(), [controller]);

  // The wrapper is always rendered (it has no box) so the hover handlers stay
  // in place when the message toggles between empty and non-empty.
  return (
    <div
      ref={(node) => {
        controller.anchor = node;
      }}
      style={{ display: 'contents' }}
      onMouseOver={(event) => {
        const from = event.relatedTarget as Node | null;
        if (from != null && event.currentTarget.contains(from)) return;
        controller.handleEnter();
      }}
      onMouseOut={(event) => {
        const to = event.relatedTarget as Node | null;
        if (to != null && event.currentTarget.contains(to)) return;
        controller.handleExit();
      }}
    >
      {props.children}
    </div>
  );
}

export const UiTooltip = Object.assign(UiTooltipImpl, {
  /** Dismisses every open tooltip on screen. */
  dismissAllToolTips(): void {
    TooltipController.dismissAll();
  },
});
