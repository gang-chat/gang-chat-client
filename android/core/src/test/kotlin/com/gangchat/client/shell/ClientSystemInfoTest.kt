package com.gangchat.client.shell

import com.gangchat.client.foundation.TargetPlatform
import org.junit.Assert.assertEquals
import org.junit.Test

class ClientSystemInfoTest {
    @Test
    fun `client system names cover supported Flutter platforms`() {
        assertEquals("Android", clientSystemName(TargetPlatform.android))
        assertEquals("iOS", clientSystemName(TargetPlatform.iOS))
        assertEquals("Linux", clientSystemName(TargetPlatform.linux))
        assertEquals("macOS", clientSystemName(TargetPlatform.macOS))
        assertEquals("Windows", clientSystemName(TargetPlatform.windows))
        assertEquals("Fuchsia", clientSystemName(TargetPlatform.fuchsia))
    }

    @Test
    fun `client user agent includes the normalized system name`() {
        assertEquals(
            "GangChat Client (Android)",
            gangChatClientUserAgent(TargetPlatform.android),
        )
        assertEquals(
            "GangChat Client (Windows)",
            gangChatClientUserAgent(TargetPlatform.windows),
        )
    }
}
