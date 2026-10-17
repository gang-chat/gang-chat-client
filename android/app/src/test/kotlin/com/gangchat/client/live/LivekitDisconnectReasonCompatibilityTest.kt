package com.gangchat.client.live

import livekit.LivekitModels
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Port of test/unit/live/livekit_disconnect_reason_compatibility_test.dart.
 *
 * The Dart test exercised the livekit_client fork's protocol-to-SDK mapping;
 * on Android the same mapping lives in [liveKitProtocolDisconnectReason]
 * (the reason a remote participant left with, read from the signal update).
 */
class LivekitDisconnectReasonCompatibilityTest {
    @Test
    fun `every protocol disconnect reason has a safe SDK mapping`() {
        for (reason in LivekitModels.DisconnectReason.entries) {
            try {
                liveKitProtocolDisconnectReason(reason)
            } catch (error: Exception) {
                throw AssertionError("Missing compatibility mapping for ${reason.name}", error)
            }
        }
        liveKitProtocolDisconnectReason(null)
    }

    @Test
    fun `mobile process timeout remains a normal participant departure`() {
        assertEquals(
            LkDisconnectReason.disconnected,
            liveKitProtocolDisconnectReason(LivekitModels.DisconnectReason.CONNECTION_TIMEOUT),
        )
    }

    @Test
    fun `new room and signaling reasons retain their existing semantics`() {
        assertEquals(
            LkDisconnectReason.roomDeleted,
            liveKitProtocolDisconnectReason(LivekitModels.DisconnectReason.ROOM_CLOSED),
        )
        assertEquals(
            LkDisconnectReason.signalingConnectionFailure,
            liveKitProtocolDisconnectReason(LivekitModels.DisconnectReason.SIGNAL_CLOSE),
        )
    }
}
