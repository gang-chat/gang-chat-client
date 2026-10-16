import { useEffect, useRef, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react';

import { kLongPressTimeout, kTouchSlop } from '../lib/mouse_cursors';

/** A point in window coordinates (Flutter global `Offset`). */
export interface WindowPoint {
  x: number;
  y: number;
}

/** The fields of a DOM `PointerEvent` the trackers read. */
export interface UiPointerEventLike {
  readonly pointerId: number;
  readonly pointerType: string;
  readonly clientX: number;
  readonly clientY: number;
  readonly button?: number;
}

function isTouchLike(pointerType: string): boolean {
  return pointerType === 'touch' || pointerType === 'pen';
}

/** Union of the rendered boxes inside a `display: contents` wrapper. */
export function _contentRect(wrapper: HTMLElement | null): DOMRect | null {
  if (wrapper == null) return null;
  const children = wrapper.children.length > 0 ? Array.from(wrapper.children) : [wrapper];
  let left = Infinity;
  let top = Infinity;
  let right = -Infinity;
  let bottom = -Infinity;
  for (const child of children) {
    const r = child.getBoundingClientRect();
    if (r.width === 0 && r.height === 0) continue;
    left = Math.min(left, r.left);
    top = Math.min(top, r.top);
    right = Math.max(right, r.right);
    bottom = Math.max(bottom, r.bottom);
  }
  if (!Number.isFinite(left)) return null;
  return new DOMRect(left, top, right - left, bottom - top);
}

export interface UiPointerTapRegionProps {
  children?: ReactNode;
  onTap: (() => void) | null;
  disableSelection?: boolean;
}

/**
 * A primary-button tap target that observes pointer events without
 * competing with other handlers.
 *
 * This is useful for navigation cards embedded in scrollable/selectable
 * surfaces where an ancestor may consume the first ordinary click. Touch
 * drags, long presses, secondary clicks, and multi-touch are rejected so
 * scrolling and context-menu gestures keep their normal behavior. The wrapper
 * is `display: contents`, so it does not affect layout.
 */
export function UiPointerTapRegion({ children, onTap, disableSelection = false }: UiPointerTapRegionProps) {
  const wrapperRef = useRef<HTMLDivElement | null>(null);
  const state = useRef<{
    pointer: number | null;
    originX: number;
    originY: number;
    touchLike: boolean;
    cancelled: boolean;
    timer: ReturnType<typeof setTimeout> | null;
  }>({ pointer: null, originX: 0, originY: 0, touchLike: false, cancelled: false, timer: null });
  const onTapRef = useRef(onTap);
  onTapRef.current = onTap;

  const clearTracking = () => {
    const s = state.current;
    if (s.timer != null) clearTimeout(s.timer);
    s.timer = null;
    s.pointer = null;
    s.touchLike = false;
    s.cancelled = false;
  };

  // didUpdateWidget: losing the callback drops any tracked press.
  useEffect(() => {
    if (onTap == null) clearTracking();
  }, [onTap]);
  useEffect(() => () => clearTracking(), []);

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (onTapRef.current == null || event.button !== 0) return;
    const s = state.current;
    if (s.pointer != null) {
      clearTracking();
      return;
    }
    s.pointer = event.pointerId;
    s.originX = event.clientX;
    s.originY = event.clientY;
    s.touchLike = isTouchLike(event.pointerType);
    s.cancelled = false;
    if (s.touchLike) {
      const pointer = event.pointerId;
      s.timer = setTimeout(() => {
        if (state.current.pointer === pointer) state.current.cancelled = true;
      }, kLongPressTimeout);
    }
  };

  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const s = state.current;
    if (event.pointerId !== s.pointer || !s.touchLike) return;
    const dx = event.clientX - s.originX;
    const dy = event.clientY - s.originY;
    if (dx * dx + dy * dy > kTouchSlop * kTouchSlop) s.cancelled = true;
  };

  const onPointerUp = (event: ReactPointerEvent<HTMLDivElement>) => {
    const s = state.current;
    if (event.pointerId !== s.pointer) return;
    const rect = _contentRect(wrapperRef.current);
    const inside =
      rect != null && event.clientX >= rect.left && event.clientX <= rect.right && event.clientY >= rect.top && event.clientY <= rect.bottom;
    const shouldTap = !s.cancelled && inside;
    clearTracking();
    if (shouldTap) onTapRef.current?.();
  };

  const onPointerCancel = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.pointerId === state.current.pointer) clearTracking();
  };

  return (
    <div
      ref={wrapperRef}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerCancel}
      style={{ display: 'contents', userSelect: disableSelection ? 'none' : undefined }}
    >
      {children}
    </div>
  );
}

/**
 * Observes an Android touch/stylus hold without consuming the pointer.
 *
 * This is intentionally a pointer-event helper instead of a recognizer. It
 * lets an outer application context-menu target observe a hold even when an
 * inner editable text control owns the platform's native long-press.
 */
export class UiAndroidLongPressTracker {
  constructor(opts: { onLongPress: (position: WindowPoint) => void }) {
    this.onLongPress = opts.onLongPress;
  }

  readonly onLongPress: (position: WindowPoint) => void;

  private _timer: ReturnType<typeof setTimeout> | null = null;
  private _pointer: number | null = null;
  private _origin: WindowPoint | null = null;

  handlePointerDown(event: UiPointerEventLike, opts: { enabled: boolean }): void {
    if (this._pointer != null) {
      // A second contact means a multi-touch gesture, never a context-menu
      // hold. Do not transfer tracking to the newer pointer.
      this.cancel();
      return;
    }
    this.cancel();
    if (!opts.enabled || !isTouchLike(event.pointerType)) return;
    this._pointer = event.pointerId;
    this._origin = { x: event.clientX, y: event.clientY };
    this._timer = setTimeout(() => {
      const position = this._origin;
      if (this._pointer == null || position == null) return;
      this._timer = null;
      this.onLongPress(position);
    }, kLongPressTimeout);
  }

  handlePointerMove(event: UiPointerEventLike): void {
    if (event.pointerId !== this._pointer) return;
    const origin = this._origin;
    if (origin == null) return;
    if (Math.hypot(event.clientX - origin.x, event.clientY - origin.y) <= kTouchSlop) return;
    this.cancel();
  }

  handlePointerUp(event: UiPointerEventLike): void {
    if (event.pointerId === this._pointer) this.cancel();
  }

  handlePointerCancel(event: UiPointerEventLike): void {
    if (event.pointerId === this._pointer) this.cancel();
  }

  cancel(): void {
    if (this._timer != null) clearTimeout(this._timer);
    this._timer = null;
    this._pointer = null;
    this._origin = null;
  }
}
