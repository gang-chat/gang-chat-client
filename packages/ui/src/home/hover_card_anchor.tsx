import {
  createContext,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type DOMAttributes,
  type MouseEvent as ReactMouseEvent,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
  type RefObject,
  type SyntheticEvent,
} from 'react';
import { createPortal } from 'react-dom';

import { Timer, unawaited } from '@gang-chat/core/lib/async';
import { HostPlatform } from '@gang-chat/core/config/host_platform';

import { EdgeInsets, type ResolvedEdgeInsets } from '../lib/edge_insets';
import { kTouchSlop } from '../lib/mouse_cursors';
import { TextSelection } from '../lib/text_editing';
import { measureTextWidth } from '../lib/text_measure';
import { useViewState, ViewState } from '../lib/view_state';
import { AnchoredPanel } from '../ui/anchored_panel';
import { insertOverlay } from '../ui/overlay';
import { _contentRect } from '../ui/platform_gestures';
import { ReadOnlySelectableText } from '../ui/read_only_text_box';
import { isTextContextMenuPanelHit } from '../ui/text_context_menu';
import { UiTypography } from '../ui/tokens';

export const hoverCardDefaultWidth = 248;
export const hoverCardDefaultGap = 10;

/** Dart `Widget Function(BuildContext context)`; the context is dropped. */
export type HoverCardBuilder = () => ReactNode;

/**
 * Port of `hoverCardOverlaySafeInsets(context)` (the context is dropped).
 *
 * PORT: Android used `MediaQuery.viewPaddingOf(context)`. The DOM has no
 * system view padding, so every platform resolves to zero (the desktop value).
 */
export function hoverCardOverlaySafeInsets(): ResolvedEdgeInsets {
  return EdgeInsets.zero;
}

/**
 * Port of `hoverInfoCardWidth(context, message, ...)` (the context is dropped):
 * the width that fits the longest line of [message], clamped to
 * [minWidth]..[maxWidth].
 */
export function hoverInfoCardWidth(
  message: string,
  opts: { style?: CSSProperties | null; horizontalPadding?: number; minWidth?: number; maxWidth?: number } = {},
): number {
  const { horizontalPadding = 24, minWidth = 132, maxWidth = 360 } = opts;
  const editableTextLayoutSlack = 18.0;
  const textStyle: CSSProperties = opts.style ?? { ...UiTypography.body, fontSize: 12, lineHeight: 1.38 };
  const lines = message.trimEnd().split('\n');
  let longestLineWidth = 0.0;
  for (const line of lines) {
    longestLineWidth = Math.max(longestLineWidth, measureTextWidth(line.length === 0 ? ' ' : line, textStyle));
  }
  return Math.min(Math.max(longestLineWidth + horizontalPadding + editableTextLayoutSlack, minWidth), maxWidth);
}

// ---------------------------------------------------------------------------
// Tap-region groups.
//
// PORT: Flutter's `TapRegion(groupId:)` treats every region of a group as one
// hit area: a pointer down inside any of them is "inside" for all. The hover
// card anchor, its card (drawn in the overlay, far from the anchor in the DOM)
// and nested cards share one group. The kit's `useTapOutside` only knows the
// refs it is given, so the group registry lives here. Other modules that put
// their own region into a hover-card group (the title-bar search results in
// Dart's `HoverCardTapRegionScope`) register with [useTapRegionGroupMember]
// and test pointer downs with [tapRegionGroupContains].

type _TapRegionHitTest = (target: Node | null, x: number, y: number) => boolean;

const _tapRegionGroups = new Map<unknown, Set<_TapRegionHitTest>>();

function _registerTapRegion(group: unknown, hitTest: _TapRegionHitTest): () => void {
  let members = _tapRegionGroups.get(group);
  if (members == null) {
    members = new Set();
    _tapRegionGroups.set(group, members);
  }
  members.add(hitTest);
  return () => {
    const current = _tapRegionGroups.get(group);
    if (current == null) return;
    current.delete(hitTest);
    if (current.size === 0) _tapRegionGroups.delete(group);
  };
}

function _rectContains(rect: DOMRect | null, x: number, y: number): boolean {
  return rect != null && x >= rect.left && y >= rect.top && x < rect.right && y < rect.bottom;
}

/** Whether a pointer event at ([event.clientX], [event.clientY]) lands in any region of [group]. */
export function tapRegionGroupContains(
  group: unknown,
  event: { readonly target: EventTarget | null; readonly clientX: number; readonly clientY: number },
): boolean {
  const members = _tapRegionGroups.get(group);
  if (members == null) return false;
  const target = event.target instanceof Node ? event.target : null;
  for (const hitTest of members) {
    if (hitTest(target, event.clientX, event.clientY)) return true;
  }
  return false;
}

/**
 * Registers the element behind [ref] as a region of [group] (Dart
 * `TapRegion(groupId: group)`). The element may be a `display: contents`
 * wrapper: its rendered children count.
 */
export function useTapRegionGroupMember(group: unknown, ref: RefObject<HTMLElement | null>): void {
  useEffect(() => {
    if (group == null) return;
    return _registerTapRegion(group, (target, x, y) => {
      const node = ref.current;
      if (node == null || !node.isConnected) return false;
      if (target != null && node.contains(target)) return true;
      const rect = getComputedStyle(node).display === 'contents' ? _contentRect(node) : node.getBoundingClientRect();
      return _rectContains(rect, x, y);
    });
  }, [group, ref]);
}

// ---------------------------------------------------------------------------
// OverlayPortal.
//
// PORT: Flutter's `OverlayPortal` draws its child in the app overlay while the
// child keeps the portal's inherited widgets (theme, config scopes, the hover
// card chain). Here an overlay entry provides an empty full-window layer and
// the content is rendered into it with `createPortal`, so React context flows
// from the anchor like inherited widgets do. React also bubbles synthetic
// events from a portal to the anchor's React ancestors, which Flutter's hit
// testing never did, so [_OverlayPortalBoundary] stops them at the portal root.
// (React's simulated mouse/pointer enter-leave still treats the portal as part
// of its React ancestors; handlers here use DOM containment instead.)

const _overlayPortalHostStyle: CSSProperties = {
  position: 'absolute',
  inset: 0,
  pointerEvents: 'none',
};

/**
 * Inserts an overlay layer while [visible] and returns its element (null until
 * the overlay host has rendered it). Render the portal content into it with
 * `createPortal(<_OverlayPortalBoundary>…</_OverlayPortalBoundary>, host)`.
 */
export function _useOverlayPortalHost(visible: boolean): HTMLDivElement | null {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  useLayoutEffect(() => {
    if (!visible) return;
    let current: HTMLDivElement | null = null;
    // Stable per entry, so overlay rebuilds do not re-run the ref.
    const attach = (node: HTMLDivElement | null) => {
      if (node == null || node === current) return;
      current = node;
      setHost(node);
    };
    const entry = insertOverlay(() => <div ref={attach} style={_overlayPortalHostStyle} />);
    return () => {
      entry.remove();
      setHost(null);
    };
  }, [visible]);
  return visible ? host : null;
}

const _stopPropagation = (event: SyntheticEvent) => event.stopPropagation();

const _boundaryHandlers: DOMAttributes<HTMLDivElement> = {
  onClick: _stopPropagation,
  onDoubleClick: _stopPropagation,
  onContextMenu: _stopPropagation,
  onMouseDown: _stopPropagation,
  onMouseUp: _stopPropagation,
  onMouseMove: _stopPropagation,
  onMouseOver: _stopPropagation,
  onMouseOut: _stopPropagation,
  onPointerDown: _stopPropagation,
  onPointerUp: _stopPropagation,
  onPointerMove: _stopPropagation,
  onPointerOver: _stopPropagation,
  onPointerOut: _stopPropagation,
  onPointerCancel: _stopPropagation,
  onWheel: _stopPropagation,
  onKeyDown: _stopPropagation,
  onKeyUp: _stopPropagation,
  onFocus: _stopPropagation,
  onBlur: _stopPropagation,
  onDragStart: _stopPropagation,
  onDragEnter: _stopPropagation,
  onDragOver: _stopPropagation,
  onDragLeave: _stopPropagation,
  onDrop: _stopPropagation,
  onCopy: _stopPropagation,
  onCut: _stopPropagation,
  onPaste: _stopPropagation,
  onTouchStart: _stopPropagation,
  onTouchMove: _stopPropagation,
  onTouchEnd: _stopPropagation,
};

/** Root of portal content: a full-window, click-through box that keeps its events to itself. */
export function _OverlayPortalBoundary({ children }: { children?: ReactNode }) {
  return (
    <div style={_overlayPortalHostStyle} {..._boundaryHandlers}>
      {children}
    </div>
  );
}

// ---------------------------------------------------------------------------
// HoverCardTapRegionScope.

export interface HoverCardTapRegionScopeData {
  readonly tapRegionGroup: unknown;
  readonly onOverlayActivityChanged: ((active: boolean) => void) | null;
}

const _HoverCardTapRegionContext = createContext<HoverCardTapRegionScopeData | null>(null);

export interface HoverCardTapRegionScopeProps {
  tapRegionGroup: unknown;
  onOverlayActivityChanged?: ((active: boolean) => void) | null;
  children?: ReactNode;
}

function HoverCardTapRegionScopeImpl({ tapRegionGroup, onOverlayActivityChanged = null, children }: HoverCardTapRegionScopeProps) {
  const value = useMemo<HoverCardTapRegionScopeData>(
    () => ({ tapRegionGroup, onOverlayActivityChanged }),
    [tapRegionGroup, onOverlayActivityChanged],
  );
  return <_HoverCardTapRegionContext.Provider value={value}>{children}</_HoverCardTapRegionContext.Provider>;
}

/** Port of `HoverCardTapRegionScope.maybeOf(context)` (a hook). */
function useHoverCardTapRegionScope(): HoverCardTapRegionScopeData | null {
  return useContext(_HoverCardTapRegionContext);
}

export const HoverCardTapRegionScope = Object.assign(HoverCardTapRegionScopeImpl, {
  maybeOf: useHoverCardTapRegionScope,
});

// ---------------------------------------------------------------------------
// HoverCardSelectableText.

export interface HoverCardSelectableTextProps {
  value: string;
  style: CSSProperties;
  copyStartOffset?: number;
  maxLines?: number;
  textAlign?: CSSProperties['textAlign'];
}

/**
 * Selectable identity/detail text that keeps its owning hover card open while
 * the shared text context menu or Android selection handles are active.
 *
 * User, room, and song cards use the same wrapper so right-click selection,
 * copy shortcuts, and touch selection cannot drift between card types.
 */
export function HoverCardSelectableText({
  value,
  style,
  copyStartOffset = 0,
  maxLines = 1,
  textAlign = 'start',
}: HoverCardSelectableTextProps) {
  const hoverScope = HoverCardTapRegionScope.maybeOf();
  const start = Math.min(Math.max(copyStartOffset, 0), value.length);
  return (
    <ReadOnlySelectableText
      value={value}
      style={style}
      maxLines={maxLines}
      textAlign={textAlign}
      secondaryClickSelection={new TextSelection({ baseOffset: start, extentOffset: value.length })}
      showSelectAllInContextMenu={false}
      contextMenuTapRegionGroupId={hoverScope?.tapRegionGroup}
      onContextMenuOpenChanged={hoverScope?.onOverlayActivityChanged ?? null}
    />
  );
}

// ---------------------------------------------------------------------------
// HoverCardAnchor.

export interface HoverCardAnchorProps {
  children?: ReactNode;
  cardBuilder: HoverCardBuilder;
  onBeforeOpen?: (() => Promise<void>) | null;
  onVisibilityChanged?: ((visible: boolean) => void) | null;
  /**
   * Changing it dismisses the card. Dart callers passed `Object.hash(...)`;
   * an array of the same values works here (compared element by element).
   */
  resetKey?: unknown;
  cardWidth?: number;
  gap?: number;
  /** Milliseconds. */
  closeDelay?: number;
}

interface _HoverCardChainScopeData {
  readonly anchor: _HoverCardAnchorState;
  readonly coordinator: _HoverCardCoordinator;
  readonly tapRegionGroup: unknown;
}

const _HoverCardChainContext = createContext<_HoverCardChainScopeData | null>(null);

function _resetKeysEqual(a: unknown, b: unknown): boolean {
  if (Object.is(a, b)) return true;
  if (!Array.isArray(a) || !Array.isArray(b) || a.length !== b.length) return false;
  for (let index = 0; index < a.length; index += 1) {
    if (!Object.is(a[index], b[index])) return false;
  }
  return true;
}

class _HoverCardAnchorState extends ViewState<HoverCardAnchorProps> {
  readonly _anchorKey: { current: HTMLDivElement | null } = { current: null };
  readonly _cardKey: { current: HTMLDivElement | null } = { current: null };
  readonly _rootTapRegionGroup = {};
  readonly _overlayActivityToken = {};

  readonly _coordinator = new _HoverCardCoordinator(() => this._handleDescendantActivityChanged());
  _parentCoordinator: _HoverCardCoordinator | null = null;
  _parentAnchor: _HoverCardAnchorState | null = null;
  _inheritedTapRegionGroup: unknown = null;
  _overAnchor = false;
  _overCard = false;
  _pinned = false;
  _portalVisible = false;
  _advertisedActiveToParent = false;
  _deferredOutsidePointer: number | null = null;
  _deferredOutsideStart: { x: number; y: number } | null = null;
  _deferredOutsideMoved = false;
  _detachDeferredOutsidePointer: (() => void) | null = null;
  _closeTimer: Timer | null = null;
  _openFuture: Promise<void> | null = null;

  /** `OverlayPortalController.isShowing`. */
  _portalShowing = false;

  get _tapRegionGroup(): unknown {
    return this._inheritedTapRegionGroup ?? this._rootTapRegionGroup;
  }

  get _cardWidth(): number {
    return this.widget.cardWidth ?? hoverCardDefaultWidth;
  }

  get _gap(): number {
    return this.widget.gap ?? hoverCardDefaultGap;
  }

  get _closeDelay(): number {
    return this.widget.closeDelay ?? 120;
  }

  get _wantsOpen(): boolean {
    return this._pinned || this._overAnchor || this._overCard || this._coordinator.hasActiveDescendants;
  }

  get _keepsParentOpen(): boolean {
    return this._wantsOpen || this._portalVisible || this._openFuture != null;
  }

  /** Port of `didChangeDependencies`: picks up the enclosing card chain and tap-region scope. */
  _didChangeDependencies(chainScope: _HoverCardChainScopeData | null, tapRegionScope: HoverCardTapRegionScopeData | null): void {
    const nextParent = chainScope?.coordinator ?? null;
    if (nextParent !== this._parentCoordinator) {
      if (this._advertisedActiveToParent) {
        this._parentCoordinator?.setDescendantActive(this, false);
        this._advertisedActiveToParent = false;
      }
      this._parentCoordinator?.releaseOpenChild(this);
      this._parentCoordinator = nextParent;
    }
    this._parentAnchor = chainScope?.anchor ?? null;
    this._inheritedTapRegionGroup = chainScope?.tapRegionGroup ?? tapRegionScope?.tapRegionGroup ?? null;
    this._syncParentActivity();
  }

  override didUpdateProps(oldWidget: HoverCardAnchorProps): void {
    if (_resetKeysEqual(oldWidget.resetKey, this.widget.resetKey)) return;
    this._dismiss();
  }

  override dispose(): void {
    if (this._advertisedActiveToParent) {
      this._parentCoordinator?.setDescendantActive(this, false);
      this._advertisedActiveToParent = false;
    }
    this._parentCoordinator?.releaseOpenChild(this);
    this._clearDeferredOutsidePointerRoute();
    this._closeTimer?.cancel();
    if (this._portalVisible) this.widget.onVisibilityChanged?.(false);
  }

  _enterAnchor(): void {
    this._overAnchor = true;
    this._open();
    this._syncParentActivity();
  }

  _exitAnchor(): void {
    this._overAnchor = false;
    this._syncParentActivity();
    this._scheduleClose();
  }

  _enterCard(): void {
    this._overCard = true;
    this._closeTimer?.cancel();
    this._syncParentActivity();
  }

  _exitCard(): void {
    this._overCard = false;
    this._syncParentActivity();
    this._scheduleClose();
  }

  _open(): void {
    this._closeTimer?.cancel();
    const beforeOpen = this.widget.onBeforeOpen ?? null;
    if (beforeOpen == null) {
      this._showPortal();
      return;
    }
    const existing = this._openFuture;
    if (existing != null) return;

    // Future.sync: a synchronous throw becomes a failed future.
    let future: Promise<void>;
    try {
      future = Promise.resolve(beforeOpen());
    } catch (error) {
      future = Promise.reject(error);
    }
    this._openFuture = future;
    this._syncParentActivity();
    unawaited(
      future
        .catch(() => {})
        .finally(() => {
          if (!this.mounted || this._openFuture !== future) return;
          this._openFuture = null;
          if (this._wantsOpen) this._showPortal();
          this._syncParentActivity();
        }),
    );
  }

  _showPortal(): void {
    this._parentCoordinator?.showOnlyChild(this);
    if (this._portalVisible && this._portalShowing) return;
    this._setPortalVisible(true);
    this._showPortalController();
    this._syncParentActivity();
  }

  _pinOpen(): void {
    if (this._pinned) {
      this._pinned = false;
      this._closeTimer?.cancel();
      if (this._overAnchor || this._overCard) {
        this._syncParentActivity();
      } else {
        this._hidePortal();
      }
      return;
    }
    this._pinned = true;
    this._open();
    this._syncParentActivity();
  }

  _hidePortal(): void {
    this._coordinator.dismissOpenChild();
    this._setPortalVisible(false);
    this._parentCoordinator?.releaseOpenChild(this);
    this._hidePortalController();
    this._syncParentActivity();
  }

  // PORT: Flutter deferred portal show/hide to after the frame while the
  // build phase was running; DOM state changes are always allowed here.
  _showPortalController(): void {
    if (!this.mounted || !this._portalVisible || this._portalShowing) return;
    this.setState(() => {
      this._portalShowing = true;
    });
  }

  _hidePortalController(): void {
    if (!this.mounted || this._portalVisible || !this._portalShowing) return;
    this.setState(() => {
      this._portalShowing = false;
    });
  }

  _dismiss(): void {
    this._pinned = false;
    this._overAnchor = false;
    this._overCard = false;
    this._closeTimer?.cancel();
    this._hidePortal();
  }

  _dismissFromPeer(): void {
    this._pinned = false;
    this._overAnchor = false;
    this._overCard = false;
    this._closeTimer?.cancel();
    this._hidePortal();
  }

  _scheduleClose(): void {
    if (this._pinned) return;
    this._closeTimer?.cancel();
    this._closeTimer = new Timer(this._closeDelay, () => {
      if (!this.mounted || this._wantsOpen) return;
      this._setPortalVisible(false);
      this._hidePortalController();
      this._syncParentActivity();
    });
  }

  _setPortalVisible(visible: boolean): void {
    if (this._portalVisible === visible) return;
    this._portalVisible = visible;
    this.widget.onVisibilityChanged?.(visible);
  }

  _handleDescendantActivityChanged(): void {
    if (!this.mounted) return;
    if (this._coordinator.hasActiveDescendants) {
      this._closeTimer?.cancel();
    } else {
      this._scheduleClose();
    }
    this._syncParentActivity();
  }

  readonly _handleOverlayActivityChanged = (active: boolean): void => {
    this._coordinator.setDescendantActive(this._overlayActivityToken, active);
    if (active) {
      this._closeTimer?.cancel();
    } else {
      this._scheduleClose();
    }
    this._syncParentActivity();
  };

  _syncParentActivity(): void {
    const parent = this._parentCoordinator;
    if (parent == null) return;
    const active = this._keepsParentOpen;
    if (active === this._advertisedActiveToParent) return;
    parent.setDescendantActive(this, active);
    this._advertisedActiveToParent = active;
  }

  _handleTapInside(event: PointerEvent): void {
    if (this._parentAnchor != null) return;
    const position = { x: event.clientX, y: event.clientY };
    if (this._openAnchorChainContains(position)) return;

    const target = this._deepestOpenCardAt(position);
    if (target == null) {
      if (this._coordinator.hasActiveDescendants) return;
      this._dismiss();
      return;
    }
    this._dismissChainAfter(target);
  }

  _handleTapOutside(event: PointerEvent): void {
    const isAndroid = HostPlatform.isAndroid;
    if (!isAndroid || !this._coordinator.hasActiveDescendants) {
      this._dismiss();
      return;
    }

    // Android text selection handles live in a separate Overlay. Their pointer
    // events therefore look like outside taps to this card even though they
    // belong to an active text-selection interaction. Wait for this pointer to
    // finish: a drag belongs to the handle and keeps the card mounted, while a
    // regular outside tap still dismisses the card with one interaction.
    this._trackDeferredOutsidePointer(event);
  }

  _trackDeferredOutsidePointer(event: PointerEvent): void {
    this._clearDeferredOutsidePointerRoute();
    this._deferredOutsidePointer = event.pointerId;
    this._deferredOutsideStart = { x: event.clientX, y: event.clientY };
    this._deferredOutsideMoved = false;
    // PORT: `GestureBinding.pointerRouter.addRoute` → window listeners.
    const handler = (e: PointerEvent) => this._handleDeferredOutsidePointer(e);
    window.addEventListener('pointermove', handler, true);
    window.addEventListener('pointerup', handler, true);
    window.addEventListener('pointercancel', handler, true);
    this._detachDeferredOutsidePointer = () => {
      window.removeEventListener('pointermove', handler, true);
      window.removeEventListener('pointerup', handler, true);
      window.removeEventListener('pointercancel', handler, true);
    };
  }

  _handleDeferredOutsidePointer(event: PointerEvent): void {
    const pointer = this._deferredOutsidePointer;
    if (pointer == null || event.pointerId !== pointer) return;
    if (event.type === 'pointermove') {
      const start = this._deferredOutsideStart;
      if (start != null && Math.hypot(event.clientX - start.x, event.clientY - start.y) >= kTouchSlop) {
        this._deferredOutsideMoved = true;
      }
      return;
    }
    if (event.type !== 'pointerup' && event.type !== 'pointercancel') return;

    const keepOpen = this._deferredOutsideMoved;
    this._clearDeferredOutsidePointerRoute();
    if (!keepOpen && this.mounted && this._portalVisible) this._dismiss();
  }

  _clearDeferredOutsidePointerRoute(): void {
    this._detachDeferredOutsidePointer?.();
    this._detachDeferredOutsidePointer = null;
    this._deferredOutsidePointer = null;
    this._deferredOutsideStart = null;
    this._deferredOutsideMoved = false;
  }

  _dismissChainAfter(target: _HoverCardAnchorState): void {
    if (this === target) {
      this._coordinator.dismissOpenChild();
      return;
    }
    const child = this._coordinator.openChild;
    if (child == null) return;
    child._dismissChainAfter(target);
  }

  _deepestOpenCardAt(globalPosition: { x: number; y: number }): _HoverCardAnchorState | null {
    const child = this._coordinator.openChild?._deepestOpenCardAt(globalPosition) ?? null;
    if (child != null) return child;
    if (this._portalVisible && _rectContains(this._cardRectInGlobal(), globalPosition.x, globalPosition.y)) {
      return this;
    }
    return null;
  }

  _openAnchorChainContains(globalPosition: { x: number; y: number }): boolean {
    if (_rectContains(this._anchorRectInGlobal(), globalPosition.x, globalPosition.y)) return true;
    return this._coordinator.openChild?._openAnchorChainContains(globalPosition) ?? false;
  }

  /** The overlay covers the whole window, so overlay and global coordinates match. */
  _anchorRectInOverlay(): DOMRect | null {
    return this._anchorRectInGlobal();
  }

  _anchorRectInGlobal(): DOMRect | null {
    const anchor = this._anchorKey.current;
    if (anchor == null || !anchor.isConnected) return null;
    return _contentRect(anchor);
  }

  _cardRectInGlobal(): DOMRect | null {
    const card = this._cardKey.current;
    if (card == null || !card.isConnected) return null;
    return card.getBoundingClientRect();
  }
}

/** Shared hover/tap shell for small profile cards anchored to an avatar. */
export function HoverCardAnchor(props: HoverCardAnchorProps) {
  const state = useViewState(() => new _HoverCardAnchorState(), props);
  const chainScope = useContext(_HoverCardChainContext);
  const tapRegionScope = useContext(_HoverCardTapRegionContext);

  useLayoutEffect(() => {
    state._didChangeDependencies(chainScope, tapRegionScope);
  }, [state, chainScope, tapRegionScope]);

  const tapRegionGroup = chainScope?.tapRegionGroup ?? tapRegionScope?.tapRegionGroup ?? state._rootTapRegionGroup;
  useTapRegionGroupMember(tapRegionGroup, state._anchorKey);

  const host = _useOverlayPortalHost(state._portalShowing);
  const tapStart = useRef<{ x: number; y: number } | null>(null);

  // Dart MouseRegion enter/exit: DOM containment, so the overlay card (a
  // React child of this component) never counts as part of the anchor.
  const onMouseOver = (event: ReactMouseEvent<HTMLDivElement>) => {
    const from = event.relatedTarget as Node | null;
    if (from != null && event.currentTarget.contains(from)) return;
    state._enterAnchor();
  };
  const onMouseOut = (event: ReactMouseEvent<HTMLDivElement>) => {
    const to = event.relatedTarget as Node | null;
    if (to != null && event.currentTarget.contains(to)) return;
    state._exitAnchor();
  };

  return (
    <>
      <div
        ref={(node) => {
          state._anchorKey.current = node;
        }}
        style={{ display: 'contents' }}
        onMouseOver={onMouseOver}
        onMouseOut={onMouseOut}
        onPointerDown={(event: ReactPointerEvent<HTMLDivElement>) => {
          tapStart.current = { x: event.clientX, y: event.clientY };
        }}
        onClick={(event) => {
          if (event.button !== 0) return;
          // A drag (text selection, scrolling) is not a tap.
          const start = tapStart.current;
          tapStart.current = null;
          if (start != null && Math.hypot(event.clientX - start.x, event.clientY - start.y) > kTouchSlop) return;
          // PORT: in Flutter a nested tap recognizer (a text field, a link)
          // won the gesture arena over the anchor's tap-to-pin; the DOM fires
          // both, so clicks on native interactive elements are skipped.
          if (_isNestedInteractiveTarget(event.target, event.currentTarget)) return;
          state._pinOpen();
        }}
        // On Android a hold is reserved for the desktop-equivalent context
        // menu (Dart registered an empty onLongPress there). The desktop has
        // no long-press, so nothing is wired here.
      >
        {props.children}
      </div>
      {host != null && state._portalShowing
        ? createPortal(
            <_OverlayPortalBoundary>
              <_HoverCardLayer state={state} tapRegionGroup={tapRegionGroup} />
            </_OverlayPortalBoundary>,
            host,
          )
        : null}
    </>
  );
}

function _isNestedInteractiveTarget(target: EventTarget | null, boundary: Element): boolean {
  let node = target instanceof Element ? target : null;
  while (node != null && node !== boundary) {
    if (node.matches('input, textarea, select, button, a[href], [contenteditable="true"]')) return true;
    node = node.parentElement;
  }
  return false;
}

interface _LayerPosition {
  left: number;
  top: number;
}

function _HoverCardLayer({ state, tapRegionGroup }: { state: _HoverCardAnchorState; tapRegionGroup: unknown }) {
  const props = state.widget;
  const [position, setPosition] = useState<_LayerPosition | null>(null);
  const [, setTick] = useState(0);

  useEffect(() => {
    const onResize = () => setTick((tick) => tick + 1);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  // The card's TapRegion: every pointer down is either inside this card's
  // group (anchor, cards, the text context menu opened from a card) or
  // outside it.
  useEffect(() => {
    const onPointerDown = (event: PointerEvent) => {
      const inside =
        tapRegionGroupContains(tapRegionGroup, event) || isTextContextMenuPanelHit({ x: event.clientX, y: event.clientY });
      if (inside) {
        state._handleTapInside(event);
      } else {
        state._handleTapOutside(event);
      }
    };
    document.addEventListener('pointerdown', onPointerDown, true);
    return () => document.removeEventListener('pointerdown', onPointerDown, true);
  }, [state, tapRegionGroup]);

  useTapRegionGroupMember(tapRegionGroup, state._cardKey);

  const overlaySize = { width: window.innerWidth, height: window.innerHeight };
  const safeInsets = hoverCardOverlaySafeInsets();
  const anchorRect = state._anchorRectInOverlay();
  const constraints = _hoverCardConstraints({ overlaySize, cardWidth: state._cardWidth, safeInsets });

  useLayoutEffect(() => {
    const card = state._cardKey.current;
    const anchor = state._anchorRectInOverlay();
    if (card == null || anchor == null) return;
    const next = _hoverCardPosition({
      size: { width: window.innerWidth, height: window.innerHeight },
      childSize: { width: card.offsetWidth, height: card.offsetHeight },
      anchorRect: anchor,
      gap: state._gap,
      safeInsets: hoverCardOverlaySafeInsets(),
    });
    if (position == null || Math.abs(position.left - next.left) > 0.5 || Math.abs(position.top - next.top) > 0.5) {
      setPosition(next);
    }
  });

  const chainValue = useMemo<_HoverCardChainScopeData>(
    () => ({ anchor: state, coordinator: state._coordinator, tapRegionGroup }),
    [state, tapRegionGroup],
  );

  if (anchorRect == null) return null;

  return (
    <div
      ref={(node) => {
        state._cardKey.current = node;
      }}
      onMouseOver={(event) => {
        const from = event.relatedTarget as Node | null;
        if (from != null && event.currentTarget.contains(from)) return;
        state._enterCard();
      }}
      onMouseOut={(event) => {
        const to = event.relatedTarget as Node | null;
        if (to != null && event.currentTarget.contains(to)) return;
        state._exitCard();
      }}
      style={{
        position: 'absolute',
        left: position?.left ?? 0,
        top: position?.top ?? 0,
        width: constraints.width,
        maxHeight: constraints.maxHeight,
        display: 'flex',
        flexDirection: 'column',
        visibility: position == null ? 'hidden' : 'visible',
        pointerEvents: 'auto',
        cursor: 'default',
      }}
    >
      <AnchoredPanel width={constraints.width} style={{ minHeight: 0 }}>
        <_HoverCardChainContext.Provider value={chainValue}>
          <HoverCardTapRegionScope tapRegionGroup={tapRegionGroup} onOverlayActivityChanged={state._handleOverlayActivityChanged}>
            {props.cardBuilder()}
          </HoverCardTapRegionScope>
        </_HoverCardChainContext.Provider>
      </AnchoredPanel>
    </div>
  );
}

class _HoverCardCoordinator {
  constructor(readonly onChanged: () => void) {}

  private readonly _activeDescendants = new Set<unknown>();
  private _openChild: _HoverCardAnchorState | null = null;

  get hasActiveDescendants(): boolean {
    return this._activeDescendants.size > 0;
  }

  get openChild(): _HoverCardAnchorState | null {
    return this._openChild;
  }

  setDescendantActive(token: unknown, active: boolean): void {
    let changed: boolean;
    if (active) {
      changed = !this._activeDescendants.has(token);
      this._activeDescendants.add(token);
    } else {
      changed = this._activeDescendants.delete(token);
    }
    if (changed) this.onChanged();
  }

  showOnlyChild(child: _HoverCardAnchorState): void {
    const previous = this._openChild;
    if (previous != null && previous !== child) {
      previous._dismissFromPeer();
    }
    this._openChild = child;
  }

  releaseOpenChild(child: _HoverCardAnchorState): void {
    if (this._openChild === child) this._openChild = null;
  }

  dismissOpenChild(): void {
    const child = this._openChild;
    if (child == null) return;
    this._openChild = null;
    child._dismissFromPeer();
  }
}

/** Port of `_HoverCardLayoutDelegate.getConstraintsForChild`. */
function _hoverCardConstraints(opts: {
  overlaySize: { width: number; height: number };
  cardWidth: number;
  safeInsets: ResolvedEdgeInsets;
}): { width: number; maxHeight: number } {
  const { overlaySize, cardWidth, safeInsets } = opts;
  const availableWidth = Math.max(0.0, overlaySize.width - (safeInsets.left + safeInsets.right));
  const availableHeight = Math.max(0.0, overlaySize.height - (safeInsets.top + safeInsets.bottom));
  const effectiveCardWidth = Math.min(cardWidth, availableWidth);
  return { width: effectiveCardWidth, maxHeight: availableHeight };
}

function _clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

/** Port of `_HoverCardLayoutDelegate.getPositionForChild`. */
export function _hoverCardPosition(opts: {
  size: { width: number; height: number };
  childSize: { width: number; height: number };
  anchorRect: { left: number; top: number; right: number; bottom: number };
  gap: number;
  safeInsets: ResolvedEdgeInsets;
}): _LayerPosition {
  const { size, childSize, anchorRect, gap, safeInsets } = opts;
  const minLeft = _clamp(safeInsets.left, 0.0, size.width);
  const safeRight = Math.max(minLeft, size.width - safeInsets.right);
  const spaceRight = safeRight - anchorRect.right - gap;
  const placeRight = spaceRight >= childSize.width;
  const rawLeft = placeRight ? anchorRect.right + gap : anchorRect.left - gap - childSize.width;
  const maxLeft = Math.max(minLeft, safeRight - childSize.width);
  const left = _clamp(rawLeft, minLeft, maxLeft);

  const minTop = _clamp(safeInsets.top, 0.0, size.height);
  const safeBottom = Math.max(minTop, size.height - safeInsets.bottom);
  const maxTop = Math.max(minTop, safeBottom - childSize.height);
  const top = _clamp(anchorRect.top, minTop, maxTop);
  return { left, top };
}
