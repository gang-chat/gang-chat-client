package com.gangchat.client.app

import com.gangchat.client.foundation.StreamSubscription
import com.gangchat.client.foundation.completedFuture
import com.gangchat.client.foundation.future
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.shell.AndroidPushRegistration
import com.gangchat.client.shell.AndroidSystemService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

/**
 * Keeps the Android push token associated with the authenticated API session.
 *
 * Registering a device is intentionally independent from the realtime
 * connection: it must not make an offline account appear online. Disposing
 * this controller also keeps the registration intact so process death or
 * removing the app from Recents behaves like a mobile app going offline, not
 * like an explicit logout.
 */
class AndroidPushRegistrationController(
    val api: GangApi,
    val androidSystemService: AndroidSystemService,
) {
    private var _changes: StreamSubscription<AndroidPushRegistration>? = null
    private var _lastRegistration: AndroidPushRegistration? = null
    private var _operationTail: Deferred<Unit> = completedFuture(Unit)
    private var _disposed = false

    suspend fun start() {
        if (_disposed || !androidSystemService.isSupported || _changes != null) {
            return
        }
        _changes =
            androidSystemService.pushRegistrationChanges.listen({ registration ->
                // Dart: `unawaited(_enqueue(...).catchError((_) {}))`. A failed
                // Deferred that nobody awaits is never reported, which is the
                // same as swallowing its error.
                _enqueue { _upsert(registration) }
            })
        synchronize()
    }

    suspend fun synchronize() {
        _enqueue {
            val registration = androidSystemService.pushRegistration() ?: return@_enqueue
            _upsert(registration)
        }.await()
    }

    suspend fun unregister() {
        _enqueue {
            val registration =
                _lastRegistration ?: androidSystemService.pushRegistration() ?: return@_enqueue
            api.deletePushDevice(
                provider = registration.provider,
                installationId = registration.installationId,
            )
            _lastRegistration = null
        }.await()
    }

    private suspend fun _upsert(registration: AndroidPushRegistration) {
        if (_disposed) return
        api.upsertPushDevice(
            provider = registration.provider,
            installationId = registration.installationId,
            token = registration.token,
            enabled = registration.enabled,
        )
        _lastRegistration = registration
    }

    private fun _enqueue(operation: suspend () -> Unit): Deferred<Unit> {
        if (_disposed) return completedFuture(Unit)
        val previous = _operationTail
        val next =
            future {
                previous.await()
                operation()
            }
        // The tail swallows errors so one failed operation never blocks the
        // ones queued after it (Dart's `next.then((_) {}, onError: ...)`).
        val tail = CompletableDeferred<Unit>()
        next.invokeOnCompletion { tail.complete(Unit) }
        _operationTail = tail
        return next
    }

    suspend fun dispose() {
        if (_disposed) return
        _disposed = true
        val changes = _changes
        _changes = null
        changes?.cancel()
    }
}
