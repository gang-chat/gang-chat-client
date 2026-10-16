package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.future
import com.gangchat.client.foundation.waitAll
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Deferred

/**
 * Coalesces room read receipts and retains failed receipts for reconnect.
 *
 * The server remains the source of truth. This controller only serializes
 * writes per room so a burst of UI updates cannot issue overlapping receipts,
 * and so a transient failure can be retried when realtime connectivity heals.
 */
class RoomReadSyncController(messages: MessagesController) {
    private val _messages: MessagesController = messages
    private val _pending: MutableMap<String, _RoomReadCursor> = LinkedHashMap()
    private val _lastSynced: MutableMap<String, _RoomReadCursor> = LinkedHashMap()
    private val _inFlight: MutableMap<String, Deferred<Unit>> = LinkedHashMap()

    private var _closed = false
    private var _generation = 0

    fun isSynced(roomId: String, messageId: String): Boolean {
        return !_pending.containsKey(roomId) &&
            _lastSynced[roomId]?.messageId == messageId
    }

    /**
     * Dart returns the room's drain future without being `async`; callers that
     * do not await it use `unawaited { markRead(...) }`, which starts the drain
     * synchronously exactly like the Dart call.
     */
    suspend fun markRead(
        roomId: String,
        lastReadMessageId: String,
        messageCreatedAt: DateTime,
    ) {
        if (_closed || roomId.isEmpty() || lastReadMessageId.isEmpty()) {
            return
        }
        if (isSynced(roomId = roomId, messageId = lastReadMessageId)) {
            return
        }

        val candidate =
            _RoomReadCursor(
                messageId = lastReadMessageId,
                createdAt = messageCreatedAt,
            )
        val pending = _pending[roomId]
        val synced = _lastSynced[roomId]
        if (synced != null && !candidate.isAfter(synced) && pending == null) {
            return
        }
        if (pending == null || candidate.isAfter(pending)) {
            // Keep only the newest UI intent. If a request is already running, its
            // drain loop will observe this replacement and immediately send it next.
            _pending[roomId] = candidate
        }
        _ensureDrain(roomId).await()
    }

    suspend fun retryPending() {
        if (_closed || _pending.isEmpty()) return
        waitAll(_pending.keys.toList().map { roomId -> _ensureDrain(roomId) })
    }

    private fun _ensureDrain(roomId: String): Deferred<Unit> {
        val existing = _inFlight[roomId]
        if (existing != null) return existing

        val generation = _generation
        val future = future { _drain(roomId, generation) }
        _inFlight[roomId] = future
        // Dart's `whenComplete` runs after the entry is stored even when the
        // drain finished synchronously; `invokeOnCompletion` runs immediately
        // for a completed future, so register it only after storing.
        future.invokeOnCompletion {
            if (_inFlight[roomId] === future) {
                _inFlight.remove(roomId)
            }
        }
        return future
    }

    private suspend fun _drain(roomId: String, generation: Int) {
        while (!_closed && generation == _generation) {
            val cursor = _pending[roomId] ?: return
            val synced = _lastSynced[roomId]
            if (synced != null && !cursor.isAfter(synced)) {
                if (_pending[roomId]?.messageId == cursor.messageId) {
                    _pending.remove(roomId)
                }
                continue
            }

            try {
                _messages.markRead(
                    roomId = roomId,
                    lastReadMessageId = cursor.messageId,
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Keep the desired cursor queued. retryPending() will resume it after
                // realtime reconnects, while a later markRead call can retry sooner.
                return
            }
            if (_closed || generation != _generation) return

            val previous = _lastSynced[roomId]
            if (previous == null || cursor.isAfter(previous)) {
                _lastSynced[roomId] = cursor
            }
            if (_pending[roomId]?.messageId == cursor.messageId) {
                _pending.remove(roomId)
            }
        }
    }

    fun close() {
        if (_closed) return
        _closed = true
        _generation++
        _pending.clear()
        _lastSynced.clear()
    }
}

private class _RoomReadCursor(val messageId: String, val createdAt: DateTime) {
    fun isAfter(other: _RoomReadCursor): Boolean {
        val timeComparison = createdAt.compareTo(other.createdAt)
        if (timeComparison != 0) return timeComparison > 0
        return messageId.compareTo(other.messageId) > 0
    }
}
