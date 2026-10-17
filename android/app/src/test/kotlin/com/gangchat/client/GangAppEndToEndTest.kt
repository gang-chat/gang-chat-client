// End-to-end run of the whole app against the repository's mock server
// (tool/js/mock_server, see its README): MainActivity's real startup,
// GangApp, OkHttp and the realtime SSE stream. Skipped unless
// GANG_E2E_API_BASE_URL is set, so CI is unaffected. To run it:
//
//   /opt/node22/bin/node tool/js/mock_server/server.mjs --port 21116 &
//   cd android && GANG_E2E_API_BASE_URL=http://127.0.0.1:21116/api/v1 \
//     ./gradlew :app:recordRoborazziDebug --tests 'com.gangchat.client.GangAppEndToEndTest'
//
// GANG_E2E_ASSET_BASE_URL defaults to the API URL without `/api/v1`.
// Screenshots: app/build/outputs/roborazzi/e2e_{phone,tablet}_NN_*.png
// (compare with docs/js-port-screenshots/, the Electron client on the same
// server). The test posts messages into the mock server's in-memory state;
// restart the server for a clean seed. On localhost the read receipt's SSE
// echo can land before the frame that shows an opened room's "未读消息"
// divider (Dart orders these the same way); start the server with
// `--latency 40` for device-like timing.
package com.gangchat.client

import android.os.Looper
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import com.gangchat.client.config.AppConfig
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.home.chatComposerTag
import com.gangchat.client.home.chatMessageListTag
import com.gangchat.client.home.homeNotificationsRefreshButtonTag
import com.gangchat.client.plugins.FlutterSecureStorage
import com.gangchat.client.shell.ShellTestLoop
import com.gangchat.client.shell.authIdentityInputTag
import com.gangchat.client.shell.authPasswordInputTag
import com.gangchat.client.shell.authSubmitButtonTag
import com.gangchat.client.shell.authSurfaceTag
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.hasTooltip
import com.gangchat.client.ui.installTestImageLoader
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.time.Duration

private const val out = "build/outputs/roborazzi"

/** The seeded lines of Gang 总部 and the simulated chatter (tool/js/mock_server/lib/seed.mjs). */
private val roomHqLines =
    listOf(
        "大家早！今天的站会改到 10:30", "新版安装包已经上传到内部分发了", "今晚 9 点总部语音有分享会，欢迎来听",
        "Electron 版的登录流程我测过了，没问题", "记得更新一下个人资料里的头像", "下周开始轮值，排班表我晚点发",
        "谁把会议室的投屏线拿走了 😂", "这周的周报大家周五前交一下", "新同学欢迎多多关照", "服务器今晚 2 点维护，大概半小时",
        "有人在吗？", "刚刚的构建过了 ✅", "今晚语音几点开始？", "我把新的设计稿发到群文件了",
        "这个 bug 复现了，我去提个 issue", "晚饭吃什么", "谁有空帮我 review 一下 PR", "测试环境刚刚重启了",
        "新版本体验不错 👍", "下午三点开会别忘了", "我先下线了，明天见", "音乐电台那首歌叫什么？",
    )

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], shadows = [ShadowNoOpMagnifier::class])
class GangAppEndToEndTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val apiBaseUrl = System.getenv("GANG_E2E_API_BASE_URL").orEmpty().trimEnd('/')
    private val assetBaseUrl =
        System.getenv("GANG_E2E_ASSET_BASE_URL").orEmpty().ifEmpty { apiBaseUrl.removeSuffix("/api/v1") }.trimEnd('/')
    private val loop = ShellTestLoop()
    private val originalEnvironment = AppConfig.environment
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var activity: MainActivity
    private lateinit var device: String
    private var shot = 0

    @Before
    fun setUp() {
        assumeTrue("GANG_E2E_API_BASE_URL is not set", apiBaseUrl.isNotEmpty())
        loop.start()
        // The real secure storage needs the Android keystore, which
        // Robolectric lacks; preferences, paths and assets stay real.
        FlutterSecureStorage.setMockInitialValues(emptyMap())
        AppConfig.environment =
            AppConfig.environment +
            mapOf(
                "GANG_API_BASE_URL" to apiBaseUrl,
                "GANG_ASSET_BASE_URL" to assetBaseUrl,
                // No release bucket on the mock server: the update check 404s.
                "GANG_RELEASE_BUCKET_URL" to "$assetBaseUrl/releases",
            )
        MainActivity.holdsLaunchScreen = false
        // Robolectric has no ImageDecoder: decode the downloaded images with
        // BitmapFactory instead.
        installTestImageLoader()
        compose.mainClock.autoAdvance = false
    }

    @After
    fun tearDown() {
        if (apiBaseUrl.isEmpty()) return
        scenario?.close()
        MainActivity.holdsLaunchScreen = true
        AppConfig.environment = originalEnvironment
        loop.finish()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phone() = runFlow("phone", phone = true)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun tablet() = runFlow("tablet", phone = false)

    private fun runFlow(device: String, phone: Boolean) {
        this.device = device
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity = it }

        // Startup decides on the login page (no stored session).
        waitFor("the login page") { exists(hasTestTag(authSurfaceTag)) }
        assertTrue(activity.uiReady)
        capture("login")

        enter(authIdentityInputTag, "demo")
        enter(authPasswordInputTag, "demo1234")
        compose.onNode(hasTestTag(authSubmitButtonTag)).performClick()
        waitFor("the room list") { exists(hasText("Gang 总部")) }
        assertTrue(runBlocking { FlutterSecureStorage().read("gang.refreshToken") }?.isNotEmpty() == true)
        // The realtime stream is open.
        waitFor("the realtime stream") { (mockState()["sse_users"] as List<*>).contains("usr_demo") }
        settle()
        capture("rooms")

        compose.onAllNodes(hasText("Gang 总部") and hasClickAction()).onFirst().performClick()
        waitFor("the composer") { exists(hasTestTag(chatComposerTag)) }
        waitFor("messages") { roomHqLines.any { line -> exists(inMessageList(line)) } }
        settle()
        capture("chat")

        val sent = "安卓端到端测试 ${System.currentTimeMillis() % 1_000_000}"
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(chatComposerTag))).performTextReplacement(sent)
        pump()
        compose.onNode(hasTooltip("发送"), useUnmergedTree = true).performClick()
        waitFor("the sent message") { exists(inMessageList(sent)) }
        settle()
        capture("sent")

        // Someone else posts: the message arrives over the SSE stream.
        val incoming = "来自 Alice 的实时消息 ${System.currentTimeMillis() % 1_000_000}"
        postAsAlice(incoming)
        waitFor("the realtime message") { exists(inMessageList(incoming)) }
        // Like Dart, someone else's new message lands below the viewport
        // (unread until it is scrolled into view): scroll down to it.
        settle()
        compose.onNode(hasTestTag(chatMessageListTag)).performTouchInput { swipeUp() }
        settle()
        capture("realtime")

        if (phone) back()
        tapTooltip("通知")
        waitFor("the notifications") { exists(hasTestTag(homeNotificationsRefreshButtonTag)) }
        settle()
        capture("notifications")

        if (phone) back()
        tapTooltip("设置")
        waitFor("the settings") { exists(hasText("用户资料")) }
        settle()
        capture("settings")

        if (phone) back()
        val sessionsBefore = (mockState()["active_sessions"] as Number).toInt()
        tapTooltip("退出登录")
        compose.onAllNodes(hasText("退出登录") and hasClickAction()).onLast().performClick()
        waitFor("the login page after logout") { exists(hasTestTag(authSurfaceTag)) }
        waitFor("the server-side logout") { (mockState()["active_sessions"] as Number).toInt() == sessionsBefore - 1 }
        assertNull(runBlocking { FlutterSecureStorage().read("gang.refreshToken") })
        waitFor("the realtime stream to close") { !(mockState()["sse_users"] as List<*>).contains("usr_demo") }
        settle()
        capture("logged_out")
        assertEquals(8, shot)
    }

    // --- Driving the app -------------------------------------------------------

    /** A message body (a read-only text box) showing [text] in the chat's message list. */
    private fun inMessageList(text: String): SemanticsMatcher = hasText(text, substring = true) and hasAnyAncestor(hasTestTag(chatMessageListTag))

    private fun exists(matcher: SemanticsMatcher): Boolean =
        compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    /** One step of the composition clock and the main looper (where network results land). */
    private fun pump(millis: Long = 16) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(millis)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
        compose.waitForIdle()
    }

    /** Pumps until [condition] holds, giving the network real time between frames. */
    private fun waitFor(what: String, timeoutMillis: Long = 30_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            if (System.currentTimeMillis() >= deadline) {
                compose.onAllNodes(isRoot()).onFirst().captureRoboImage("$out/e2e_${device}_timeout.png")
                throw AssertionError("Timed out waiting for $what")
            }
            Thread.sleep(4)
            pump()
        }
    }

    /** Lets images, transitions and debounces finish (a second of frames). */
    private fun settle() {
        repeat(60) {
            Thread.sleep(5)
            pump()
        }
    }

    private fun enter(tag: String, text: String) {
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag))).performTextReplacement(text)
        pump()
    }

    private fun tapTooltip(message: String) {
        compose.onNode(hasTooltip(message), useUnmergedTree = true).performClick()
        settle()
    }

    /** The system back gesture. */
    private fun back() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun capture(name: String) {
        shot += 1
        compose.onAllNodes(isRoot()).onFirst().captureRoboImage("$out/e2e_${device}_${"%02d".format(shot)}_$name.png")
    }

    // --- The mock server --------------------------------------------------------

    private fun request(method: String, url: String, body: Any? = null, token: String? = null): Any? {
        val connection = URL(url).openConnection(Proxy.NO_PROXY) as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("accept", "application/json")
        if (token != null) connection.setRequestProperty("authorization", "Bearer $token")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("content-type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(jsonEncode(body).toByteArray()) }
        }
        val status = connection.responseCode
        val text = (if (status < 400) connection.inputStream else connection.errorStream)?.use { it.readBytes().decodeToString() }
        check(status < 400) { "$method $url -> $status $text" }
        return if (text.isNullOrEmpty()) null else jsonDecode(text)
    }

    @Suppress("UNCHECKED_CAST")
    private fun mockState(): JsonMap = request("GET", "$assetBaseUrl/__mock/state") as JsonMap

    /** Alice (another seeded account) posts [body] into Gang 总部 through the API. */
    @Suppress("UNCHECKED_CAST")
    private fun postAsAlice(body: String) {
        val session = request("POST", "$apiBaseUrl/auth/login", mapOf("login" to "alice", "password" to "demo1234")) as JsonMap
        val token = session["access_token"] as String
        request(
            "POST",
            "$apiBaseUrl/rooms/room_hq/messages",
            mapOf("client_message_id" to "e2e-${System.nanoTime()}", "type" to "text", "body" to body),
            token = token,
        )
        request("POST", "$apiBaseUrl/auth/logout", mapOf("refresh_token" to session["refresh_token"]), token = token)
    }
}
