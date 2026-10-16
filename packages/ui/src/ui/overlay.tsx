import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useSyncExternalStore,
  type CSSProperties,
  type ReactNode,
  type RefObject,
} from 'react';

/**
 * Port of Flutter's `Overlay` + `Navigator` popup routes.
 *
 * The app root renders one [OverlayHost]. Everything Flutter pushed as a
 * route or overlay entry — dialogs (`showDialog`), context menus
 * (`showMenu`), anchored panels, hover cards, toasts — is inserted here and
 * drawn above the page in insertion order, in a fixed full-window layer.
 *
 *  - [insertOverlay] adds a non-modal layer (`OverlayEntry`). The layer is a
 *    fixed full-window box with `pointer-events: none`, so clicks pass through
 *    to the page; the entry's content positions itself (absolute) and MUST
 *    set `pointerEvents: 'auto'` on its own root box to receive input.
 *    Route content (pushRoute) follows the same rule; showDialog wraps its
 *    content for you.
 *  - [pushRoute] / [showDialog] add a modal layer with a barrier and resolve
 *    with the value passed to `close(result)` (`Navigator.pop(result)`).
 *  - Inside a route, [useRoute] gives `close` / `isCurrent` (the
 *    `Navigator.of(context)` / `ModalRoute.of(context)` equivalents).
 *  - Escape pops the top dismissible route.
 */

export interface OverlayEntryHandle {
  readonly id: number;
  /** Removes the entry (`OverlayEntry.remove`). */
  remove(): void;
  /** Re-renders the entry's builder (`OverlayEntry.markNeedsBuild`). */
  markNeedsBuild(): void;
  readonly mounted: boolean;
}

export interface RouteOptions {
  /** Clicking the barrier pops the route with `undefined`. Default true. */
  barrierDismissible?: boolean;
  /** Barrier colour; Flutter's dialog default is black 54%. */
  barrierColor?: string;
  /** Whether Escape pops the route. Default = barrierDismissible. */
  dismissOnBack?: boolean;
  /** Called before a barrier/Escape dismissal; return false to veto (`PopScope.canPop`). */
  onWillPop?: () => boolean | Promise<boolean>;
}

export interface RouteHandle<T> extends OverlayEntryHandle {
  readonly result: Promise<T | undefined>;
  close(result?: T): void;
}

interface Entry {
  id: number;
  version: number;
  builder: () => ReactNode;
  modal: boolean;
  options: RouteOptions;
  close?: (result?: unknown) => void;
}

export const kDialogBarrierColor = '#0000008A';

let nextId = 1;
let entries: Entry[] = [];
const listeners = new Set<() => void>();

function emit() {
  entries = [...entries];
  for (const listener of [...listeners]) listener();
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function getSnapshot() {
  return entries;
}

function handleFor(entry: Entry): OverlayEntryHandle {
  return {
    id: entry.id,
    remove() {
      const index = entries.indexOf(entry);
      if (index < 0) return;
      entries.splice(index, 1);
      emit();
    },
    markNeedsBuild() {
      if (!entries.includes(entry)) return;
      entry.version += 1;
      emit();
    },
    get mounted() {
      return entries.includes(entry);
    },
  };
}

export function insertOverlay(builder: () => ReactNode): OverlayEntryHandle {
  const entry: Entry = { id: nextId++, version: 0, builder, modal: false, options: {} };
  entries.push(entry);
  emit();
  return handleFor(entry);
}

export function pushRoute<T>(builder: () => ReactNode, options: RouteOptions = {}): RouteHandle<T> {
  let resolve!: (value: T | undefined) => void;
  const result = new Promise<T | undefined>((r) => {
    resolve = r;
  });
  const entry: Entry = { id: nextId++, version: 0, builder, modal: true, options };
  const base = handleFor(entry);
  let closed = false;
  const close = (value?: T) => {
    if (closed) return;
    closed = true;
    base.remove();
    resolve(value);
  };
  entry.close = close as (result?: unknown) => void;
  entries.push(entry);
  emit();
  return {
    id: base.id,
    result,
    close,
    remove: () => close(undefined),
    markNeedsBuild: () => base.markNeedsBuild(),
    get mounted() {
      return base.mounted;
    },
  };
}

/**
 * Port of `showDialog`: pushes a modal route whose content is centred over a
 * barrier, resolving with the value passed to `close`.
 */
export function showDialog<T = void>(
  builder: (close: (result?: T) => void) => ReactNode,
  options: RouteOptions = {},
): Promise<T | undefined> {
  let closeRef: (result?: T) => void = () => {};
  const route = pushRoute<T>(
    () => (
      <div style={dialogCenterStyle} data-overlay-dialog="true">
        <div style={dialogContentStyle}>{builder((result) => closeRef(result))}</div>
      </div>
    ),
    { barrierColor: kDialogBarrierColor, ...options },
  );
  closeRef = route.close;
  return route.result;
}

/** Pops the top-most modal route (`Navigator.maybePop`). Returns false when none could pop. */
export async function maybePopTopRoute(): Promise<boolean> {
  for (let i = entries.length - 1; i >= 0; i -= 1) {
    const entry = entries[i]!;
    if (!entry.modal) continue;
    const dismissible = entry.options.dismissOnBack ?? entry.options.barrierDismissible ?? true;
    if (!dismissible) return true; // a non-dismissible route swallows back
    if (entry.options.onWillPop && !(await entry.options.onWillPop())) return true;
    entry.close?.(undefined);
    return true;
  }
  return false;
}

/** True while any modal route is open (used to suspend page shortcuts). */
export function hasOpenModalRoute(): boolean {
  return entries.some((entry) => entry.modal);
}

/** Closes every overlay and route (logout / window teardown). */
export function clearOverlays(): void {
  for (const entry of [...entries]) entry.close?.(undefined);
  entries = [];
  emit();
}

interface RouteContextValue {
  close: (result?: unknown) => void;
  isCurrent: () => boolean;
  entryId: number;
}

const RouteContext = createContext<RouteContextValue | null>(null);

/**
 * Inside a route: `Navigator.of(context).pop(result)` → `useRoute().close(result)`.
 * Outside any route, `close` is a no-op and `isCurrent` is true.
 */
export function useRoute<T = unknown>(): { close: (result?: T) => void; isCurrent: () => boolean; inRoute: boolean } {
  const value = useContext(RouteContext);
  return {
    close: (value?.close ?? (() => {})) as (result?: T) => void,
    isCurrent: value?.isCurrent ?? (() => true),
    inRoute: value != null,
  };
}

export function OverlayHost({ children }: { children: ReactNode }) {
  const current = useSyncExternalStore(subscribe, getSnapshot, getSnapshot);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || event.defaultPrevented) return;
      if (!hasOpenModalRoute()) return;
      event.preventDefault();
      event.stopPropagation();
      void maybePopTopRoute();
    };
    document.addEventListener('keydown', onKeyDown, true);
    return () => document.removeEventListener('keydown', onKeyDown, true);
  }, []);

  return (
    <div style={rootStyle}>
      {children}
      {current.map((entry) => (
        <OverlayLayer key={entry.id} entry={entry} version={entry.version} />
      ))}
    </div>
  );
}

function OverlayLayer({ entry }: { entry: Entry; version: number }) {
  const isCurrent = useCallback(() => {
    for (let i = entries.length - 1; i >= 0; i -= 1) {
      if (entries[i]!.modal) return entries[i] === entry;
    }
    return true;
  }, [entry]);
  const contextValue = useRef<RouteContextValue>({
    close: (result) => entry.close?.(result),
    isCurrent,
    entryId: entry.id,
  }).current;

  const content = <RouteContext.Provider value={contextValue}>{entry.builder()}</RouteContext.Provider>;
  if (!entry.modal) {
    return <div style={passThroughLayerStyle}>{content}</div>;
  }
  const barrierDismissible = entry.options.barrierDismissible ?? true;
  return (
    <div style={modalLayerStyle}>
      <div
        aria-hidden="true"
        style={{ position: 'absolute', inset: 0, background: entry.options.barrierColor ?? 'transparent' }}
        onMouseDown={async (event) => {
          if (event.button !== 0) return;
          if (!barrierDismissible) return;
          if (entry.options.onWillPop && !(await entry.options.onWillPop())) return;
          entry.close?.(undefined);
        }}
        onContextMenu={(event) => {
          // A right-click outside an open menu closes it, like Flutter.
          event.preventDefault();
          if (barrierDismissible) entry.close?.(undefined);
        }}
      />
      <div style={passThroughLayerStyle}>{content}</div>
    </div>
  );
}

export interface WindowRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** Measures an element in window coordinates (`RenderBox.localToGlobal` + size). */
export function measureInWindow(ref: RefObject<HTMLElement | null> | HTMLElement | null): Promise<WindowRect | null> {
  const node = ref == null ? null : ref instanceof HTMLElement ? ref : ref.current;
  if (node == null || !node.isConnected) return Promise.resolve(null);
  const rect = node.getBoundingClientRect();
  return Promise.resolve({ x: rect.left, y: rect.top, width: rect.width, height: rect.height });
}

/** Synchronous variant of [measureInWindow]. */
export function rectInWindow(ref: RefObject<HTMLElement | null> | HTMLElement | null): WindowRect | null {
  const node = ref == null ? null : ref instanceof HTMLElement ? ref : ref.current;
  if (node == null || !node.isConnected) return null;
  const rect = node.getBoundingClientRect();
  return { x: rect.left, y: rect.top, width: rect.width, height: rect.height };
}

const rootStyle: CSSProperties = {
  position: 'relative',
  display: 'flex',
  flexDirection: 'column',
  flex: 1,
  minWidth: 0,
  minHeight: 0,
  width: '100%',
  height: '100%',
};

// Non-modal layers let pointer events through everywhere except their own content.
const passThroughLayerStyle: CSSProperties = {
  position: 'fixed',
  inset: 0,
  pointerEvents: 'none',
  zIndex: 1000,
};

const modalLayerStyle: CSSProperties = {
  position: 'fixed',
  inset: 0,
  zIndex: 1000,
};

const dialogContentStyle: CSSProperties = {
  pointerEvents: 'auto',
  display: 'flex',
  flexDirection: 'column',
  maxWidth: '100%',
  maxHeight: '100%',
  minHeight: 0,
};

const dialogCenterStyle: CSSProperties = {
  position: 'absolute',
  inset: 0,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: 24,
  boxSizing: 'border-box',
  pointerEvents: 'none',
};
