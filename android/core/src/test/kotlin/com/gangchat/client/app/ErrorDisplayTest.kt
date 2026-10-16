package com.gangchat.client.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorDisplayTest {
    @Test
    fun `preserves an existing Chinese error`() {
        assertEquals("保存失败", userFacingErrorMessage("保存失败"))
    }

    @Test
    fun `maps common platform errors without exposing English details`() {
        assertEquals(
            "网络连接失败，请检查网络后重试",
            userFacingErrorMessage(Exception("SocketException: connection failed")),
        )
        assertEquals(
            "当前平台不支持此操作",
            userFacingErrorMessage(IllegalStateException("unsupported platform")),
        )
    }

    @Test
    fun `uses the caller fallback for unknown English errors`() {
        assertEquals(
            "读取图片失败",
            userFacingErrorMessage(
                IllegalStateException("decoder exploded"),
                fallback = "读取图片失败",
            ),
        )
    }
}
