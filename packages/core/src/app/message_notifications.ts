import { normalizeRoomNotificationPolicy } from './room_display';

export const realtimeMessageCreatedUpdateReason = 'message_created';

/**
 * Tracks realtime message notifications without conflating them with room
 * snapshots loaded during sign-in or reconnect recovery.
 */
export class RealtimeMessageNotificationTracker {
  constructor(opts: { capacity?: number } = {}) {
    const { capacity = 256 } = opts;
    if (!(capacity > 0)) throw new Error('capacity must be positive');
    this.capacity = capacity;
  }

  readonly capacity: number;
  // A JS Set keeps insertion order, like Dart's LinkedHashSet.
  private readonly _seenMessageKeys = new Set<string>();

  register(opts: {
    roomId: string;
    updateReason: string;
    notificationPolicy: string;
    messageId: string | null;
  }): boolean {
    const { roomId, updateReason, notificationPolicy, messageId } = opts;
    if (updateReason.trim().toLowerCase() !== realtimeMessageCreatedUpdateReason) {
      return false;
    }
    const normalizedRoomId = roomId.trim();
    const normalizedMessageId = messageId?.trim() ?? '';
    if (normalizedRoomId.length === 0 || normalizedMessageId.length === 0) return false;

    const key = `${normalizedRoomId}:${normalizedMessageId}`;
    if (this._seenMessageKeys.has(key)) return false;
    this._seenMessageKeys.add(key);
    while (this._seenMessageKeys.size > this.capacity) {
      const first = this._seenMessageKeys.values().next();
      if (first.done) break;
      this._seenMessageKeys.delete(first.value);
    }

    return normalizeRoomNotificationPolicy(notificationPolicy) === 'all';
  }

  clear(): void {
    this._seenMessageKeys.clear();
  }
}
