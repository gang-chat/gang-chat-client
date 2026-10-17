package com.gangchat.client.shell

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import com.gangchat.client.app.EmailVerificationController
import com.gangchat.client.app.LoginAccountHistoryStore
import com.gangchat.client.app.LoginAccountRecord
import com.gangchat.client.auth.AuthClient
import com.gangchat.client.auth.EmailVerificationChallenge
import com.gangchat.client.auth.EmailVerificationInspection
import com.gangchat.client.foundation.AppCoroutines
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.testing.MockClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertTrue

/**
 * The event loop for one test: a fresh [AppCoroutines] scope on the main
 * looper whose uncaught errors fail the test (Dart's `takeException()`).
 */
class ShellTestLoop {
    private val uncaught = mutableListOf<Throwable>()
    private var previousHandler: ((Throwable) -> Unit)? = null
    private var scope: CoroutineScope? = null

    fun start() {
        previousHandler = AppCoroutines.uncaughtErrorHandler
        AppCoroutines.uncaughtErrorHandler = { error -> uncaught.add(error) }
        val newScope =
            CoroutineScope(
                SupervisorJob() +
                    Dispatchers.Main.immediate +
                    CoroutineExceptionHandler { _, error -> AppCoroutines.uncaughtErrorHandler(error) },
            )
        scope = newScope
        AppCoroutines.install(newScope)
    }

    fun finish() {
        scope?.cancel()
        AppCoroutines.reset()
        previousHandler?.let { AppCoroutines.uncaughtErrorHandler = it }
        assertTrue("uncaught async errors: $uncaught", uncaught.isEmpty())
    }
}

/**
 * Dart's `tester.pump(duration)` with the composition clock under manual
 * control: runs pending work, advances [millis], then two frames.
 */
fun ComposeContentTestRule.pump(millis: Long = 0) {
    waitForIdle()
    if (millis > 0) mainClock.advanceTimeBy(millis)
    repeat(2) { mainClock.advanceTimeByFrame() }
    waitForIdle()
}

/** Dart's `pumpAndSettle()` for these pages: their transitions end within 500 ms. */
fun ComposeContentTestRule.pumpAndSettle() {
    repeat(5) { pump(100) }
}

/** The editable text inside the [Input] tagged [tag] (Dart's `_textFieldWithHint`). */
fun ComposeContentTestRule.field(tag: String): SemanticsNodeInteraction =
    onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

/**
 * Dart's `tester.enterText(finder, text)`, plus a frame so the semantics
 * (which tests read the text from) catch up with the controller.
 */
fun ComposeContentTestRule.enterText(tag: String, text: String) {
    field(tag).performTextReplacement(text)
    pump()
}

/** The field's text (its controller's text; password fields included). */
fun SemanticsNodeInteraction.fieldText(): String {
    val config = fetchSemanticsNode().config
    val input = config.getOrNull(SemanticsProperties.InputText) ?: config.getOrNull(SemanticsProperties.EditableText)
    return input?.text ?: ""
}

/** A node's toggle state (`UiCheckbox.value`). */
fun SemanticsNodeInteraction.isToggledOn(): Boolean =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On

val hasToggleableState: SemanticsMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)

fun SemanticsNodeInteraction.rect(): DpRect = getBoundsInRoot()

val DpRect.centerX: Dp get() = (left + right) / 2
val DpRect.centerY: Dp get() = (top + bottom) / 2

fun assertDpEquals(expected: Dp, actual: Dp, tolerance: Float = 0.01f) {
    assertTrue("expected $expected, was $actual", kotlin.math.abs(expected.value - actual.value) <= tolerance)
}

/** Dart's `_expectRectCloseTo`. */
fun assertRectCloseTo(actual: DpRect, expected: DpRect) {
    assertDpEquals(expected.left, actual.left)
    assertDpEquals(expected.top, actual.top)
    assertDpEquals(expected.right, actual.right)
    assertDpEquals(expected.bottom, actual.bottom)
}

/** Dart's `_MemoryLoginAccountHistoryStore`. */
class MemoryLoginAccountHistoryStore(records: List<LoginAccountRecord> = emptyList()) : LoginAccountHistoryStore() {
    var records: List<LoginAccountRecord> = records.toList()

    override suspend fun read(): List<LoginAccountRecord> = records

    override suspend fun write(records: List<LoginAccountRecord>) {
        this.records = records.toList()
    }
}

/**
 * Dart's `_FakeEmailVerificationController`: answers through a fake
 * [AuthClient] (the Kotlin controller is final; its client is open) and
 * records the same calls.
 */
class FakeEmailVerification(
    private val onInspect: (suspend (String) -> EmailVerificationInspection)? = null,
    private val onStart: (suspend (String) -> EmailVerificationChallenge)? = null,
) {
    val calls = mutableListOf<String>()

    val controller =
        EmailVerificationController(
            apiBaseUrl = "https://unused.test",
            authClientFactory = { baseUrl -> _Client(baseUrl) },
        )

    private inner class _Client(baseUrl: String) :
        AuthClient(baseUrl = baseUrl, httpClient = MockClient { HttpResponse("unexpected request", 404) }) {
        override suspend fun isEmailAvailable(email: String): Boolean {
            calls.add("available:$email")
            return true
        }

        override suspend fun inspectEmailVerification(email: String, accessToken: String?): EmailVerificationInspection {
            calls.add("inspect:$email")
            val inspect = onInspect
            if (inspect != null) return inspect(email)
            return EmailVerificationInspection(canSend = true, retryAfterSeconds = 0)
        }

        override suspend fun startEmailVerification(email: String, accessToken: String?): EmailVerificationChallenge {
            calls.add("start:$email")
            val start = onStart
            if (start != null) return start(email)
            return EmailVerificationChallenge(id = "email-challenge", retryAfterSeconds = 60)
        }

        override suspend fun resendEmailVerificationCode(challengeId: String): EmailVerificationChallenge {
            calls.add("resend:$challengeId")
            return EmailVerificationChallenge(id = challengeId, retryAfterSeconds = 60)
        }

        override suspend fun verifyEmailVerificationCode(challengeId: String, code: String): String {
            calls.add("verify:$challengeId:$code")
            return "email-verification-token"
        }
    }
}

/** Dart's `_jsonResponse`. */
fun jsonResponse(body: Any?, statusCode: Int = 200): HttpResponse =
    HttpResponse(jsonEncode(body), statusCode, headers = mapOf("content-type" to "application/json; charset=utf-8"))
