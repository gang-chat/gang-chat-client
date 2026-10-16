package com.gangchat.client.shell

import com.gangchat.client.platform.SystemChrome
import com.gangchat.client.platform.SystemUiMode
import com.gangchat.client.platform.SystemUiOverlay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

typealias SystemUiModeSetter = suspend (mode: SystemUiMode, overlays: List<SystemUiOverlay>?) -> Unit

/**
 * Owns the Android system-UI boundary for full-screen media controls.
 *
 * The status and navigation bars follow the same visibility state as Gang
 * Chat's media controls and are always restored when the full-screen scope
 * is disposed.
 */
class FullScreenSystemUiController(
    setEnabledSystemUIMode: SystemUiModeSetter? = null,
) {
    private val _setEnabledSystemUIMode: SystemUiModeSetter =
        setEnabledSystemUIMode ?: { mode, overlays -> SystemChrome.setEnabledSystemUIMode(mode, overlays) }

    // Requests run one after another in call order, like the Dart future chain.
    private val _pending = Mutex()

    suspend fun setControlsVisible(visible: Boolean) {
        _enqueue {
            if (visible) {
                _setEnabledSystemUIMode(SystemUiMode.manual, SystemUiOverlay.entries)
            } else {
                _setEnabledSystemUIMode(SystemUiMode.immersiveSticky, null)
            }
        }
    }

    suspend fun restore() = setControlsVisible(true)

    private suspend fun _enqueue(operation: suspend () -> Unit) {
        // A failed platform call must not prevent a later touch or disposal
        // from restoring the system bars.
        _pending.withLock { operation() }
    }
}
