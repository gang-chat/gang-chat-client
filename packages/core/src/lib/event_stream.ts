/**
 * Minimal port of Dart's broadcast `Stream` / `StreamController`, enough for
 * the event buses the Flutter client uses (`StreamController.broadcast()`,
 * `stream.listen`, `subscription.cancel()`).
 */
export interface StreamSubscription {
  cancel(): void;
}

export interface EventStream<T> {
  listen(
    onData: (event: T) => void,
    options?: { onError?: (error: unknown) => void; onDone?: () => void },
  ): StreamSubscription;
}

interface Listener<T> {
  onData: (event: T) => void;
  onError?: (error: unknown) => void;
  onDone?: () => void;
}

export class StreamController<T> {
  private _listeners = new Set<Listener<T>>();
  private _closed = false;

  readonly stream: EventStream<T> = {
    listen: (onData, options) => {
      if (this._closed) {
        options?.onDone?.();
        return { cancel: () => {} };
      }
      const listener: Listener<T> = { onData, onError: options?.onError, onDone: options?.onDone };
      this._listeners.add(listener);
      return {
        cancel: () => {
          this._listeners.delete(listener);
        },
      };
    },
  };

  get isClosed(): boolean {
    return this._closed;
  }

  get hasListener(): boolean {
    return this._listeners.size > 0;
  }

  add(event: T): void {
    if (this._closed) return;
    for (const listener of [...this._listeners]) {
      try {
        listener.onData(event);
      } catch (error) {
        console.error('StreamController listener threw', error);
      }
    }
  }

  addError(error: unknown): void {
    if (this._closed) return;
    for (const listener of [...this._listeners]) listener.onError?.(error);
  }

  close(): void {
    if (this._closed) return;
    this._closed = true;
    const listeners = [...this._listeners];
    this._listeners.clear();
    for (const listener of listeners) listener.onDone?.();
  }
}

/** Resolves with the next event of [stream] that satisfies [test]. */
export function firstWhere<T>(stream: EventStream<T>, test: (event: T) => boolean): Promise<T> {
  return new Promise<T>((resolve) => {
    const sub = stream.listen((event) => {
      if (!test(event)) return;
      sub.cancel();
      resolve(event);
    });
  });
}
