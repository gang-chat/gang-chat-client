package com.gangchat.client.shell

import androidx.compose.runtime.Composable
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.ViewState
import kotlinx.coroutines.CancellationException

/**
 * Temporarily enables Android landscape rotation for full-screen media.
 *
 * Disposing the scope restores the device default: portrait-only on Android
 * phones and unrestricted rotation on Android tablets.
 */
@Composable
fun FullScreenMediaOrientation(
    controller: AppOrientationController? = null,
    child: @Composable () -> Unit,
) = StatefulWidget(FullScreenMediaOrientationWidget(child, controller)) { _FullScreenMediaOrientationState() }

class FullScreenMediaOrientationWidget(
    val child: @Composable () -> Unit,
    val controller: AppOrientationController?,
)

private class _FullScreenMediaOrientationState : ViewState<FullScreenMediaOrientationWidget>() {
    private lateinit var _controller: AppOrientationController

    override fun initState() {
        super.initState()
        _controller = widget.controller ?: AppOrientationController()
        _allowLandscape()
    }

    override fun didUpdateWidget(oldWidget: FullScreenMediaOrientationWidget) {
        super.didUpdateWidget(oldWidget)
        if (oldWidget.controller == widget.controller) return
        _restoreDefault(_controller)
        _controller = widget.controller ?: AppOrientationController()
        _allowLandscape()
    }

    override fun dispose() {
        _restoreDefault(_controller)
        super.dispose()
    }

    private fun _allowLandscape() {
        val controller = _controller
        unawaited { _ignoreFailure { controller.allowFullScreenMediaLandscape() } }
    }

    private fun _restoreDefault(controller: AppOrientationController) {
        unawaited { _ignoreFailure { controller.restoreDefaultOrientation() } }
    }

    private suspend fun _ignoreFailure(operation: suspend () -> Unit) {
        try {
            operation()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Orientation support is best-effort and must never break navigation.
        }
    }

    @Composable
    override fun build() = widget.child()
}
