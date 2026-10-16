package com.gangchat.client.lifecycle

import kotlin.coroutines.cancellation.CancellationException

/**
 * Application-wide registry of async cleanup callbacks that must run before
 * the process exits. Used so that `window_manager`'s prevent-close hook can
 * flush in-flight work owned by deeply nested widgets (e.g. leaving a live
 * voice session) before destroying the window.
 *
 * Hooks run in registration order. Each hook is best-effort: an exception
 * from one hook does not stop the others. A global timeout is applied at
 * the call site to avoid hanging the close button on a wedged backend.
 */
object ShutdownHooks {
    private val _hooks = mutableListOf<suspend () -> Unit>()

    /**
     * Register a cleanup callback. Returns a token that can be passed to
     * [unregister] to remove this exact callback.
     */
    fun register(hook: suspend () -> Unit): Any {
        _hooks.add(hook)
        return hook
    }

    fun unregister(token: Any) {
        val index = _hooks.indexOfFirst { it == token }
        if (index >= 0) _hooks.removeAt(index)
    }

    /**
     * Run every registered hook. Caller is expected to wrap this with their
     * own timeout. Hooks fire in registration order, but exceptions from one
     * hook do not block the others.
     */
    suspend fun runAll() {
        val snapshot = _hooks.toList()
        for (hook in snapshot) {
            try {
                hook()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // swallow — shutdown is best-effort
            }
        }
    }
}
