/**
 * Ports of the small `dart:async` pieces the Flutter client leans on:
 * `Completer`, `Timer`, `Future.delayed`, `Future.timeout` and `unawaited`.
 *
 * Durations are plain milliseconds everywhere in the TypeScript port.
 */
export type Milliseconds = number;

export class Completer<T = void> {
  readonly promise: Promise<T>;
  private _resolve!: (value: T | PromiseLike<T>) => void;
  private _reject!: (reason?: unknown) => void;
  private _completed = false;

  constructor() {
    this.promise = new Promise<T>((resolve, reject) => {
      this._resolve = resolve;
      this._reject = reject;
    });
    // A Completer whose future nobody awaits must not raise an unhandled
    // rejection; callers that await `promise` still observe the error.
    this.promise.catch(() => {});
  }

  get isCompleted(): boolean {
    return this._completed;
  }

  complete(value: T | PromiseLike<T>): void {
    if (this._completed) return;
    this._completed = true;
    this._resolve(value);
  }

  completeError(error: unknown): void {
    if (this._completed) return;
    this._completed = true;
    this._reject(error);
  }
}

/** Port of `dart:async` `Timer`. */
export class Timer {
  private _handle: ReturnType<typeof setTimeout> | null;
  private _active = true;

  constructor(duration: Milliseconds, callback: () => void) {
    this._handle = setTimeout(() => {
      this._active = false;
      this._handle = null;
      callback();
    }, Math.max(0, duration));
  }

  static periodic(duration: Milliseconds, callback: (timer: PeriodicTimer) => void): PeriodicTimer {
    return new PeriodicTimer(duration, callback);
  }

  get isActive(): boolean {
    return this._active;
  }

  cancel(): void {
    if (this._handle != null) clearTimeout(this._handle);
    this._handle = null;
    this._active = false;
  }
}

export class PeriodicTimer {
  private _handle: ReturnType<typeof setInterval> | null;
  tick = 0;

  constructor(duration: Milliseconds, callback: (timer: PeriodicTimer) => void) {
    this._handle = setInterval(() => {
      this.tick += 1;
      callback(this);
    }, Math.max(1, duration));
  }

  get isActive(): boolean {
    return this._handle != null;
  }

  cancel(): void {
    if (this._handle != null) clearInterval(this._handle);
    this._handle = null;
  }
}

export function delay(duration: Milliseconds): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, Math.max(0, duration)));
}

export class TimeoutException extends Error {
  constructor(message = 'TimeoutException', readonly duration?: Milliseconds) {
    super(message);
    this.name = 'TimeoutException';
  }
}

/** Port of `Future.timeout`: rejects with [TimeoutException] (or the result of [onTimeout]). */
export function withTimeout<T>(
  promise: Promise<T>,
  duration: Milliseconds,
  onTimeout?: () => T | Promise<T>,
): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    let settled = false;
    const handle = setTimeout(() => {
      if (settled) return;
      settled = true;
      if (onTimeout) {
        Promise.resolve()
          .then(onTimeout)
          .then(resolve, reject);
      } else {
        reject(new TimeoutException(`Future not completed after ${duration}ms`, duration));
      }
    }, duration);
    promise.then(
      (value) => {
        if (settled) return;
        settled = true;
        clearTimeout(handle);
        resolve(value);
      },
      (error) => {
        if (settled) return;
        settled = true;
        clearTimeout(handle);
        reject(error);
      },
    );
  });
}

/** Port of `unawaited`: fire-and-forget, logging instead of raising unhandled rejections. */
export function unawaited(promise: Promise<unknown> | void | undefined | null): void {
  if (promise == null) return;
  promise.catch((error) => {
    console.warn('unawaited future failed', error);
  });
}

/** Serializes async operations, like the `_tail = _tail.then(...)` chains in the Dart code. */
export class SerialQueue {
  private _tail: Promise<unknown> = Promise.resolve();

  run<T>(operation: () => Promise<T>): Promise<T> {
    const next = this._tail.then(operation, operation);
    this._tail = next.catch(() => {});
    return next;
  }
}

/** Microtask-equivalent of `scheduleMicrotask`. */
export function scheduleMicrotask(callback: () => void): void {
  queueMicrotask(callback);
}
