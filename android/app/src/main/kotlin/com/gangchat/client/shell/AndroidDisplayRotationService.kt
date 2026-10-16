package com.gangchat.client.shell

import com.gangchat.client.platform.AndroidDisplay

typealias AndroidDisplayRotationReader = suspend () -> Int?

/**
 * Reads Android's current display rotation as quarter turns from its natural
 * orientation. Callers are responsible for restricting use to Android.
 *
 * [readRotation] stands in for the Dart service's injectable channel; it
 * defaults to the display of the attached activity (`Surface.ROTATION_*`).
 */
class AndroidDisplayRotationService(
    private val readRotation: AndroidDisplayRotationReader = { AndroidDisplay.getDisplayRotation() },
) {
    suspend fun currentQuarterTurns(): Int {
        val value = readRotation()
        return normalizeDisplayRotationQuarterTurns(value)
    }
}

fun normalizeDisplayRotationQuarterTurns(value: Int?): Int {
    return when (value) {
        1 -> 1
        2 -> 2
        3 -> 3
        else -> 0
    }
}

/**
 * Counter-rotation that keeps a frozen portrait frame fixed while Android's
 * display surface rotates underneath the image-preview route.
 */
fun counterDisplayRotationQuarterTurns(displayQuarterTurns: Int): Int {
    val normalized = normalizeDisplayRotationQuarterTurns(displayQuarterTurns)
    return (4 - normalized) % 4
}
