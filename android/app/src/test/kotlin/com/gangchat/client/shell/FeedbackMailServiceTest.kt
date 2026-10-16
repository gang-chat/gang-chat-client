package com.gangchat.client.shell

import org.junit.Assert.assertEquals
import org.junit.Test

/** Port of test/unit/shell/feedback_mail_service_test.dart. */
class FeedbackMailServiceTest {
    @Test
    fun `feedback mail draft builds a mailto URI with sender metadata`() {
        val uri =
            FeedbackMailDraft(
                from = "user@example.test",
                to = "gang-chat@outlook.com",
                subject = "Subject",
                body = "Body",
            ).toMailtoUri()

        assertEquals("mailto", uri.scheme)
        assertEquals("gang-chat@outlook.com", uri.path)
        assertEquals("user@example.test", uri.queryParameters["from"])
        assertEquals("Subject", uri.queryParameters["subject"])
        assertEquals("Body", uri.queryParameters["body"])
    }
}
