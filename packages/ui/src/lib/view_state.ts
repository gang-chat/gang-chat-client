import { useEffect, useLayoutEffect, useReducer, useRef } from 'react';

/**
 * Port of Flutter's `State<T extends StatefulWidget>`.
 *
 * Large stateful widgets (HomeShell, ChatPane, SettingsPage, ...) keep their
 * Dart structure: a class that owns fields, lifecycle methods and handlers,
 * plus a React component that renders from it. Use it with [useViewState]:
 *
 * ```tsx
 * class _CounterState extends ViewState<CounterProps> {
 *   count = 0;
 *   override initState() { ... }
 *   increment = () => this.setState(() => { this.count += 1; });
 * }
 *
 * export function Counter(props: CounterProps) {
 *   const state = useViewState(() => new _CounterState(), props);
 *   return <Text onPress={state.increment}>{state.count}</Text>;
 * }
 * ```
 *
 * - `this.props` is `widget` (always the latest props).
 * - `initState` runs once after the first render commit (like Flutter, before
 *   the first frame the user interacts with). `didUpdateProps(old)` is
 *   `didUpdateWidget`. `dispose` runs on unmount.
 * - `setState(fn)` runs [fn] synchronously then schedules a re-render; it is a
 *   no-op after dispose, so async callbacks can check [mounted] like Dart.
 * - Arrow-function class fields keep `this` bound for handlers.
 */
export abstract class ViewState<P> {
  props!: P;
  private _mounted = false;
  private _disposed = false;
  /** @internal set by useViewState */
  _rerender: () => void = () => {};

  /** Dart's `widget`. */
  get widget(): P {
    return this.props;
  }

  get mounted(): boolean {
    return this._mounted && !this._disposed;
  }

  initState(): void {}

  didUpdateProps(_oldProps: P): void {}

  dispose(): void {}

  setState(fn?: () => void): void {
    if (this._disposed) return;
    fn?.();
    this._rerender();
  }

  /** @internal */
  _mount(): void {
    this._mounted = true;
    this.initState();
  }

  /** @internal */
  _unmount(): void {
    if (this._disposed) return;
    this._disposed = true;
    this._mounted = false;
    this.dispose();
  }
}

export function useViewState<P, S extends ViewState<P>>(create: () => S, props: P): S {
  const [, force] = useReducer((x: number) => x + 1, 0);
  const ref = useRef<S | null>(null);
  if (ref.current == null) {
    const state = create();
    state.props = props;
    state._rerender = force;
    ref.current = state;
  }
  const state = ref.current;
  const previousProps = useRef<P>(props);

  if (state.props !== props) {
    state.props = props;
  }

  useLayoutEffect(() => {
    state._rerender = force;
    state._mount();
    return () => state._unmount();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const old = previousProps.current;
    if (old !== props) {
      previousProps.current = props;
      state.didUpdateProps(old);
    }
  });

  return state;
}
