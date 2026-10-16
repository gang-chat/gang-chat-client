import { useCallback, useRef, useSyncExternalStore } from 'react';
import type { Listenable } from '@gang-chat/core/lib/change_notifier';

/**
 * Port of `ListenableBuilder` / `AnimatedBuilder`: re-renders the calling
 * component whenever any of [listenables] notifies. `null` entries are
 * ignored so optional controllers can be passed straight through.
 */
export function useListenable(...listenables: ReadonlyArray<Listenable | null | undefined>): void {
  const versionRef = useRef(0);
  const key = listenables;
  const subscribe = useCallback(
    (onChange: () => void) => {
      const listener = () => {
        versionRef.current += 1;
        onChange();
      };
      for (const l of key) l?.addListener(listener);
      return () => {
        for (const l of key) l?.removeListener(listener);
      };
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    key,
  );
  useSyncExternalStore(subscribe, () => versionRef.current, () => versionRef.current);
}

/**
 * Port of `ValueListenableBuilder`: subscribes to [listenable] and returns
 * `select(listenable)`, re-rendering only when the selected value changes.
 */
export function useListenableSelector<L extends Listenable, T>(
  listenable: L,
  select: (listenable: L) => T,
  isEqual: (a: T, b: T) => boolean = Object.is,
): T {
  const selectRef = useRef(select);
  selectRef.current = select;
  const cache = useRef<{ value: T } | null>(null);
  const subscribe = useCallback(
    (onChange: () => void) => {
      const listener = () => {
        const next = selectRef.current(listenable);
        if (cache.current && isEqual(cache.current.value, next)) return;
        cache.current = { value: next };
        onChange();
      };
      listenable.addListener(listener);
      return () => listenable.removeListener(listener);
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [listenable],
  );
  const getSnapshot = () => {
    if (cache.current == null) cache.current = { value: selectRef.current(listenable) };
    return cache.current.value;
  };
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot);
}
