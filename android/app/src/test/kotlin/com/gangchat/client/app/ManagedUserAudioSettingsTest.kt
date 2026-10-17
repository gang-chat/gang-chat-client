package com.gangchat.client.app

import com.gangchat.client.foundation.http.HttpRequest
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.protocol.GangApiClient
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.MockClient
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Port of test/unit/app/managed_user_audio_settings_test.dart. */
class ManagedUserAudioSettingsTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `managed audio store reads and writes the target cloud settings`() = gang.runTest {
        val requests = mutableListOf<HttpRequest>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requests.add(request)
                        val settings =
                            if (request.method == "PATCH") {
                                jsonDecode(request.body)
                            } else {
                                linkedMapOf<String, Any?>(
                                    "default_audio_input_volume" to 25,
                                    "default_audio_output_volume" to 75,
                                    "live_mic_input_volume" to 60,
                                    "live_voice_output_volume" to 70,
                                    "live_screen_share_output_volume" to 80,
                                    "live_music_output_volume" to 90,
                                )
                            }
                        HttpResponse(jsonEncode(mapOf("audio_settings" to settings)), 200)
                    },
            )
        val store = ManagedUserAudioSettingsStore(api = api, userId = "target_user")

        val stored = store.read()
        store.writeInputVolume(0.42)

        assertEquals(0.25, stored.inputVolume, 0.0)
        assertEquals(0.75, stored.outputVolume, 0.0)
        assertEquals(
            listOf(
                "/api/v1/users/target_user/audio-settings",
                "/api/v1/users/target_user/audio-settings",
            ),
            requests.map { request -> request.url.path },
        )
        assertEquals(
            mapOf(
                "default_audio_input_volume" to 42L,
                "default_audio_output_volume" to 75L,
                "live_mic_input_volume" to 60L,
                "live_voice_output_volume" to 70L,
                "live_screen_share_output_volume" to 80L,
                "live_music_output_volume" to 90L,
            ),
            jsonDecode(requests.last().body),
        )
        api.close()
    }
}
