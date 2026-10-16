package com.gangchat.client.shell

import androidx.compose.ui.unit.DpSize
import com.gangchat.client.platform.AndroidDisplay
import com.gangchat.client.platform.DeviceOrientation
import com.gangchat.client.platform.SystemChrome
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

typealias PreferredOrientationsSetter = suspend (orientations: List<DeviceOrientation>) -> Unit
typealias LogicalViewSizeReader = () -> DpSize

/** Dart's `isAndroidTabletLogicalSize(Size)` for a Compose logical size. */
fun isAndroidTabletLogicalSize(logicalSize: DpSize): Boolean =
    isAndroidTabletLogicalSize(logicalSize.width.value.toDouble(), logicalSize.height.value.toDouble())

/**
 * Owns the platform boundary for Gang Chat's Android orientation policy.
 *
 * Android phones stay portrait-only unless full-screen media is active, while
 * Android tablets use the platform's unrestricted orientation behavior.
 */
class AppOrientationController(
    setPreferredOrientations: PreferredOrientationsSetter? = null,
    logicalViewSize: LogicalViewSizeReader? = null,
) {
    private val _setPreferredOrientations: PreferredOrientationsSetter =
        setPreferredOrientations ?: { orientations -> SystemChrome.setPreferredOrientations(orientations) }
    private val _logicalViewSize: LogicalViewSizeReader = logicalViewSize ?: ::_primaryLogicalViewSize

    // Requests run one after another in call order, like the Dart future chain.
    private val _pending = Mutex()

    val restoresPortraitByDefault: Boolean
        get() = !isAndroidTabletLogicalSize(_logicalViewSize())

    suspend fun lockPortrait() = _enqueue(portraitOnly)

    suspend fun restoreDefaultOrientation() =
        _enqueue(if (restoresPortraitByDefault) portraitOnly else unrestricted)

    suspend fun allowFullScreenMediaLandscape() =
        _enqueue(if (restoresPortraitByDefault) fullScreenMedia else unrestricted)

    private suspend fun _enqueue(orientations: List<DeviceOrientation>) {
        // A failure is reported to this caller only; later restoration
        // requests still run.
        _pending.withLock { _setPreferredOrientations(orientations) }
    }

    companion object {
        val portraitOnly: List<DeviceOrientation> = listOf(DeviceOrientation.portraitUp)

        val fullScreenMedia: List<DeviceOrientation> =
            listOf(
                DeviceOrientation.portraitUp,
                DeviceOrientation.landscapeLeft,
                DeviceOrientation.landscapeRight,
            )
        val unrestricted: List<DeviceOrientation> = emptyList()

        private fun _primaryLogicalViewSize(): DpSize = AndroidDisplay.logicalWindowSize()
    }
}
