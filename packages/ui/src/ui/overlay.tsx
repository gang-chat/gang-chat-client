import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useSyncExternalStore,
  type ReactNode,
} from 'react';
import { BackHandler, Platform, Pressable, StyleSheet, View, type View as RNView } from 'react-native';

/**
 * Port of Flutter's `Overlay` + `Navigator` popup routes.
 *
 * The app root renders one [OverlayHost]. Everything Flutter pushed as a
 * route or overlay entry — dialogs (`showDialog`), context menus
 * (`showMenu`), anchored panels, hover cards, toasts — is inserted here and
 * drawn above the page in insertion order.
 *
 *  - [insertOverlay] adds a non-modal layer (`OverlayEntry`).
 *  - [pushRoute] / [showDialog] add a modal layer with a barrier and resolve
 *    with the value passed to `close(result)` (`Navigator.pop(result)`).
 *  - Inside a route, [useRoute] gives `close` / `isCurrent` (the
 *    `Navigator.of(context)` / `ModalRoute.of(context)` equivalents).
 *  - Escape (web) / the Android back button pops the top dismissible route.
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
  /** Tapping the barrier pops the route with `undefined`. Default true. */
  barrierDismissible?: boolean;
  /** Barrier colour; Flutter's dialog default is black 54%. */
  barrierColor?: string;
  /** Whether Escape / Android back pops the route. Default = barrierDismissible. */
  dismissOnBack?: boolean;
  /** Called before a barrier/back dismissal; return false to veto (`PopScope.canPop`). */
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
  return () => listeners.delete(listener);
}

function getSnapshot() {
  return entries;
}

export function insertOverlay(builder: () => ReactNode): OverlayEntryHandle {
  const entry: Entry = { id: nextId++, version: 0, builder, modal: false, options: {} };
  entries.push(entry);
  emit();
  return handleFor(entry);
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
  return { ...base, id: base.id, result, close, remove: () => close(undefined), get mounted() {
    return base.mounted;
  } };
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
      <View style={styles.dialogCenter} pointerEvents="box-none">
        {builder((result) => closeRef(result))}
      </View>
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
  const snapshot = [...entries];
  for (const entry of snapshot) {
    if (entry.close) entry.close(undefined);
  }
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
export function useRoute<T = unknown>(): { close: (result?: T) => void; isCurrent: () => boolean } {
  const value = useContext(RouteContext);
  return {
    close: (value?.close ?? (() => {})) as (result?: T) => void,
    isCurrent: value?.isCurrent ?? (() => true),
  };
}

export function OverlayHost({ children }: { children: ReactNode }) {
  const current = useSyncExternalStore(subscribe, getSnapshot, getSnapshot);

  useEffect(() => {
    if (Platform.OS === 'web') {
      const onKeyDown = (event: KeyboardEvent) => {
        if (event.key !== 'Escape' || event.defaultPrevented) return;
        if (!hasOpenModalRoute()) return;
        event.preventDefault();
        event.stopPropagation();
        void maybePopTopRoute();
      };
      document.addEventListener('keydown', onKeyDown, true);
      return () => document.removeEventListener('keydown', onKeyDown, true);
    }
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (!hasOpenModalRoute()) return false;
      void maybePopTopRoute();
      return true;
    });
    return () => sub.remove();
  }, []);

  return (
    <View style={styles.root}>
      {children}
      {current.map((entry) => (
        <OverlayLayer key={entry.id} entry={entry} version={entry.version} />
      ))}
    </View>
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
    return (
      <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
        {content}
      </View>
    );
  }
  const barrierDismissible = entry.options.barrierDismissible ?? true;
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="关闭"
        style={[StyleSheet.absoluteFill, { backgroundColor: entry.options.barrierColor ?? 'transparent' }, webCursorDefault]}
        onPress={async () => {
          if (!barrierDismissible) return;
          if (entry.options.onWillPop && !(await entry.options.onWillPop())) return;
          entry.close?.(undefined);
        }}
      />
      <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
        {content}
      </View>
    </View>
  );
}

const webCursorDefault = Platform.OS === 'web' ? ({ cursor: 'default' } as object) : null;

export interface WindowRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** Measures a host component in window coordinates (`RenderBox.localToGlobal`). */
export function measureInWindow(ref: { current: RNView | null } | RNView | null): Promise<WindowRect | null> {
  const node = ref && 'current' in (ref as object) ? (ref as { current: RNView | null }).current : (ref as RNView | null);
  if (node == null) return Promise.resolve(null);
  return new Promise((resolve) => {
    try {
      node.measureInWindow((x, y, width, height) => resolve({ x, y, width, height }));
    } catch {
      resolve(null);
    }
  });
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  dialogCenter: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
});
