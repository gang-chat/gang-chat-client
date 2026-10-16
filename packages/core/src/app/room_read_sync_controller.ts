import type { MessagesController } from './messages_controller';

/**
 * Coalesces room read receipts and retains failed receipts for reconnect.
 *
 * The server remains the source of truth. This controller only serializes
 * writes per room so a burst of UI updates cannot issue overlapping receipts,
 * and so a transient failure can be retried when realtime connectivity heals.
 */
export class RoomReadSyncController {
  constructor(opts: { messages: MessagesController }) {
    this._messages = opts.messages;
  }

  private readonly _messages: MessagesController;
  private readonly _pending = new Map<string, _RoomReadCursor>();
  private readonly _lastSynced = new Map<string, _RoomReadCursor>();
  private readonly _inFlight = new Map<string, Promise<void>>();

  private _closed = false;
  private _generation = 0;

  isSynced(opts: { roomId: string; messageId: string }): boolean {
    const { roomId, messageId } = opts;
    return !this._pending.has(roomId) && this._lastSynced.get(roomId)?.messageId === messageId;
  }

  markRead(opts: { roomId: string; lastReadMessageId: string; messageCreatedAt: Date }): Promise<void> {
    const { roomId, lastReadMessageId, messageCreatedAt } = opts;
    if (this._closed || roomId.length === 0 || lastReadMessageId.length === 0) {
      return Promise.resolve();
    }
    if (this.isSynced({ roomId, messageId: lastReadMessageId })) {
      return Promise.resolve();
    }

    const candidate = new _RoomReadCursor({
      messageId: lastReadMessageId,
      createdAt: messageCreatedAt,
    });
    const pending = this._pending.get(roomId);
    const synced = this._lastSynced.get(roomId);
    if (synced != null && !candidate.isAfter(synced) && pending == null) {
      return Promise.resolve();
    }
    if (pending == null || candidate.isAfter(pending)) {
      // Keep only the newest UI intent. If a request is already running, its
      // drain loop will observe this replacement and immediately send it next.
      this._pending.set(roomId, candidate);
    }
    return this._ensureDrain(roomId);
  }

  async retryPending(): Promise<void> {
    if (this._closed || this._pending.size === 0) return;
    await Promise.all([...this._pending.keys()].map((roomId) => this._ensureDrain(roomId)));
  }

  private _ensureDrain(roomId: string): Promise<void> {
    const existing = this._inFlight.get(roomId);
    if (existing != null) return existing;

    const generation = this._generation;
    const future: Promise<void> = this._drain(roomId, generation).finally(() => {
      if (this._inFlight.get(roomId) === future) {
        this._inFlight.delete(roomId);
      }
    });
    this._inFlight.set(roomId, future);
    return future;
  }

  private async _drain(roomId: string, generation: number): Promise<void> {
    while (!this._closed && generation === this._generation) {
      const cursor = this._pending.get(roomId);
      if (cursor == null) return;
      const synced = this._lastSynced.get(roomId);
      if (synced != null && !cursor.isAfter(synced)) {
        if (this._pending.get(roomId)?.messageId === cursor.messageId) {
          this._pending.delete(roomId);
        }
        continue;
      }

      try {
        await this._messages.markRead({
          roomId,
          lastReadMessageId: cursor.messageId,
        });
      } catch {
        // Keep the desired cursor queued. retryPending() will resume it after
        // realtime reconnects, while a later markRead call can retry sooner.
        return;
      }
      if (this._closed || generation !== this._generation) return;

      const previous = this._lastSynced.get(roomId);
      if (previous == null || cursor.isAfter(previous)) {
        this._lastSynced.set(roomId, cursor);
      }
      if (this._pending.get(roomId)?.messageId === cursor.messageId) {
        this._pending.delete(roomId);
      }
    }
  }

  close(): void {
    if (this._closed) return;
    this._closed = true;
    this._generation++;
    this._pending.clear();
    this._lastSynced.clear();
  }
}

class _RoomReadCursor {
  constructor(opts: { messageId: string; createdAt: Date }) {
    this.messageId = opts.messageId;
    this.createdAt = opts.createdAt;
  }

  readonly messageId: string;
  readonly createdAt: Date;

  isAfter(other: _RoomReadCursor): boolean {
    const timeComparison = this.createdAt.getTime() - other.createdAt.getTime();
    if (timeComparison !== 0) return timeComparison > 0;
    return this.messageId > other.messageId;
  }
}
