/**
 * Port of Flutter's `Listenable` / `ChangeNotifier` / `ValueNotifier`.
 *
 * Controllers in `@gang-chat/core` keep the exact shape of their Dart
 * originals: they extend [ChangeNotifier] and call [notifyListeners] after a
 * state change. React components subscribe through `useListenable` in
 * `@gang-chat/ui`.
 */
export type VoidCallback = () => void;

export interface Listenable {
  addListener(listener: VoidCallback): void;
  removeListener(listener: VoidCallback): void;
}

export class ChangeNotifier implements Listenable {
  private _listeners: VoidCallback[] = [];
  private _disposed = false;

  /**
   * Monotonic counter bumped on every [notifyListeners]. React's
   * `useSyncExternalStore` uses it as the snapshot so a notification always
   * re-renders, even when the notifier mutates itself in place.
   */
  version = 0;

  get hasListeners(): boolean {
    return this._listeners.length > 0;
  }

  get isDisposed(): boolean {
    return this._disposed;
  }

  addListener(listener: VoidCallback): void {
    if (this._disposed) return;
    this._listeners.push(listener);
  }

  removeListener(listener: VoidCallback): void {
    const index = this._listeners.indexOf(listener);
    if (index >= 0) this._listeners.splice(index, 1);
  }

  notifyListeners(): void {
    if (this._disposed) return;
    this.version += 1;
    // Copy so listeners may remove themselves while being notified.
    for (const listener of [...this._listeners]) {
      try {
        listener();
      } catch (error) {
        console.error('ChangeNotifier listener threw', error);
      }
    }
  }

  dispose(): void {
    this._disposed = true;
    this._listeners = [];
  }
}

export class ValueNotifier<T> extends ChangeNotifier {
  constructor(private _value: T) {
    super();
  }

  get value(): T {
    return this._value;
  }

  set value(next: T) {
    if (Object.is(this._value, next)) return;
    this._value = next;
    this.notifyListeners();
  }
}

/** Port of `Listenable.merge`. */
export function mergeListenables(listenables: ReadonlyArray<Listenable | null | undefined>): Listenable {
  const present = listenables.filter((l): l is Listenable => l != null);
  return {
    addListener(listener) {
      for (const l of present) l.addListener(listener);
    },
    removeListener(listener) {
      for (const l of present) l.removeListener(listener);
    },
  };
}
