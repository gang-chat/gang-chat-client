package com.gangchat.client.protocol

import com.gangchat.client.live.resolveLiveKitServerUrl
import org.junit.Assert.assertEquals
import org.junit.Test

// Dart: test/unit/protocol/livekit_url_test.dart (tests lib/src/live/livekit_url.dart).
class LivekitUrlTest {
    @Test
    fun `rewrites loopback LiveKit host when API host is remote`() {
        assertEquals(
            "ws://198.44.178.206:7880",
            resolveLiveKitServerUrl(
                serverUrl = "http://127.0.0.1:7880",
                apiBaseUrl = "http://198.44.178.206:21116/api/v1",
            ),
        )
    }

    @Test
    fun `keeps loopback LiveKit host for a local API`() {
        assertEquals(
            "ws://127.0.0.1:7880",
            resolveLiveKitServerUrl(
                serverUrl = "http://127.0.0.1:7880",
                apiBaseUrl = "http://localhost:21116/api/v1",
            ),
        )
    }

    @Test
    fun `keeps already-public LiveKit host`() {
        assertEquals(
            "wss://voice.example.com",
            resolveLiveKitServerUrl(
                serverUrl = "wss://voice.example.com",
                apiBaseUrl = "http://198.44.178.206:21116/api/v1",
            ),
        )
    }

    @Test
    fun `normalizes public HTTP LiveKit URL to WebSocket scheme`() {
        assertEquals(
            "ws://198.44.178.206:7880",
            resolveLiveKitServerUrl(
                serverUrl = "http://198.44.178.206:7880",
                apiBaseUrl = "http://198.44.178.206:21116/api/v1",
            ),
        )
    }

    @Test
    fun `normalizes HTTPS LiveKit URL to secure WebSocket scheme`() {
        assertEquals(
            "wss://voice.example.com",
            resolveLiveKitServerUrl(
                serverUrl = "https://voice.example.com",
                apiBaseUrl = "https://api.example.com/api/v1",
            ),
        )
    }
}
