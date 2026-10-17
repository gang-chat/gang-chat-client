package com.gangchat.client.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.gangchat.client.app.AppUpdatePlatform
import com.gangchat.client.app.AudioDeviceInfo
import com.gangchat.client.app.AvailableAppUpdate
import com.gangchat.client.shell.ReleaseDownloadCancellationToken
import com.gangchat.client.shell.ReleaseDownloadProgress
import com.gangchat.client.shell.ReleaseUpdateService
import com.gangchat.client.app.AudioDeviceStore
import com.gangchat.client.app.AutoUpdatePromptStore
import com.gangchat.client.app.CloseBehavior
import com.gangchat.client.app.CloseBehaviorStore
import com.gangchat.client.app.LanguagePreferenceStore
import com.gangchat.client.app.StoredAudioDevices
import com.gangchat.client.app.defaultCloseBehavior
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.http.HttpRequest
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.live.LiveAudioDeviceService
import com.gangchat.client.protocol.GangApiClient
import com.gangchat.client.shell.AndroidPushRegistration
import com.gangchat.client.shell.AndroidSystemService
import com.gangchat.client.shell.InstallInfoService
import com.gangchat.client.testing.MockClient
import com.gangchat.client.ui.AppNotificationHost
import com.gangchat.client.ui.MediaCacheScope
import com.gangchat.client.ui.UiTheme
import com.gangchat.client.ui.testMediaCacheController
import com.gangchat.client.app.MediaCacheController
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.RootNavigator
import com.gangchat.client.widgets.WindowMediaQuery

/** Dart's `_currentUserJson` (gang_app_shell_test.dart). */
val settingsCurrentUserJson: Map<String, Any?> =
    linkedMapOf(
        "id" to "user-1",
        "uid" to "uid-1",
        "username" to "kai",
        "display_name" to "Kai",
        "bio" to "",
        "gender" to "secret",
        "email" to "kai@example.com",
        "email_verified" to true,
        "email_public" to false,
        "phone_number" to null,
        "phone_number_public" to false,
        "avatar_url" to null,
        "default_avatar_key" to "blue-3",
        "is_superuser" to false,
        "created_at" to "2026-01-01T00:00:00Z",
        "status" to "active",
        "language" to "zh-Hans",
    )

/** Dart's `_userJson(id:, username:, displayName:)`. */
fun settingsUserJson(id: String, username: String, displayName: String): Map<String, Any?> =
    linkedMapOf(
        "id" to id,
        "username" to username,
        "display_name" to displayName,
        "avatar_url" to null,
        "default_avatar_key" to "blue-3",
    )

/** A `GangApiClient` answering through [handler] (Dart's `GangApiClient(httpClient: MockClient(...))`). */
fun settingsApi(
    baseUrl: String = "http://example.test/api/v1",
    handler: suspend (HttpRequest) -> HttpResponse,
): GangApiClient =
    GangApiClient(
        baseUrl = baseUrl,
        accessTokenProvider = { "access-token" },
        httpClient = MockClient { request -> handler(request) },
    )

/**
 * The test app root for settings (Dart's `MaterialApp(theme: uiTheme(),
 * home: ...)`): theme, window media query, a hermetic media cache, the
 * notice host and the root navigator.
 */
fun ComposeContentTestRule.setSettingsContent(
    cache: MediaCacheController = testMediaCacheController(),
    content: @Composable (BuildContext) -> Unit,
) {
    setContent {
        UiTheme {
            WindowMediaQuery {
                MediaCacheScope(cache) {
                    AppNotificationHost {
                        RootNavigator { context -> content(context) }
                    }
                }
            }
        }
    }
}

/**
 * The platform services as Robolectric runs them: the real bridge would
 * prompt for permissions and talk to the system, so tests answer here.
 * [supported] false is Dart's desktop host (no Android services).
 */
open class FakeSettingsAndroidSystemService(
    private val supported: Boolean = false,
    var preferenceEnabled: Boolean = true,
    var systemAllowsNotifications: Boolean = true,
) : AndroidSystemService() {
    val mailtoUris = mutableListOf<Uri>()
    val events = mutableListOf<String>()

    override val isSupported: Boolean get() = supported

    override suspend fun notificationPreferenceEnabled(): Boolean = preferenceEnabled

    override suspend fun notificationsEnabled(): Boolean = systemAllowsNotifications

    override suspend fun setNotificationPreferenceEnabled(enabled: Boolean): Boolean {
        events.add("preference:$enabled")
        preferenceEnabled = enabled
        return true
    }

    override suspend fun requestNotificationPermission(): Boolean = systemAllowsNotifications

    override suspend fun requestUpdateNotificationPermission(): Boolean = true

    override suspend fun setUpdateDownloadActive(active: Boolean) {
        events.add("update-download-active:$active")
    }

    override suspend fun pushRegistration(): AndroidPushRegistration? = null

    override suspend fun requestBluetoothConnectPermission(): Boolean = true

    override suspend fun openMailto(uri: Uri) {
        mailtoUris.add(uri)
    }

    override suspend fun openNotificationSettings() {
        events.add("open-notification-settings")
    }

    override suspend fun installedAt(): String? = null
}

/** Dart's `_FakeAudioDeviceStore`. */
class FakeSettingsAudioDeviceStore(
    val inputVolume: Double = 0.35,
    val outputVolume: Double = 0.75,
    val screenShareMaxHeight: Int? = null,
) : AudioDeviceStore() {
    val writes = mutableListOf<String>()

    override suspend fun read(): StoredAudioDevices =
        if (screenShareMaxHeight == null) {
            StoredAudioDevices(inputVolume = inputVolume, outputVolume = outputVolume)
        } else {
            StoredAudioDevices(inputVolume = inputVolume, outputVolume = outputVolume, screenShareMaxHeight = screenShareMaxHeight)
        }

    override suspend fun writeInputDeviceId(deviceId: String) {
        writes.add("input-device:$deviceId")
    }

    override suspend fun writeOutputDeviceId(deviceId: String) {
        writes.add("output-device:$deviceId")
    }

    override suspend fun writeInputVolume(volume: Double) {
        writes.add("input-volume:$volume")
    }

    override suspend fun writeOutputVolume(volume: Double) {
        writes.add("output-volume:$volume")
    }

    override suspend fun writeScreenShareMaxHeight(height: Int) {
        writes.add("screen-share-height:$height")
    }

    override suspend fun writeScreenShareFrameRate(frameRate: Int) {
        writes.add("screen-share-frame-rate:$frameRate")
    }
}

/** Dart's `_FakeSettingsAudioDeviceService`. */
class FakeSettingsAudioDeviceService(
    val devices: List<AudioDeviceInfo> = listOf(fakeSettingsInput, fakeSettingsOutput),
    private val selectedInput: AudioDeviceInfo? = devices.firstOrNull { it.kind == "audioinput" },
    private val selectedOutput: AudioDeviceInfo? = devices.firstOrNull { it.kind == "audiooutput" },
) : LiveAudioDeviceService() {
    val selections = mutableListOf<String>()

    override val devicesChanged: Stream<List<AudioDeviceInfo>> get() = Stream.empty()

    override val selectedAudioInput: AudioDeviceInfo? get() = selectedInput

    override val selectedAudioOutput: AudioDeviceInfo? get() = selectedOutput

    override suspend fun enumerateDevices(): List<AudioDeviceInfo> = devices

    override suspend fun selectAudioInput(device: AudioDeviceInfo) {
        selections.add("input:${device.deviceId}")
    }

    override suspend fun selectAudioOutput(device: AudioDeviceInfo) {
        selections.add("output:${device.deviceId}")
    }

    companion object {
        val fakeSettingsInput = AudioDeviceInfo(deviceId = "input-1", label = "Input 1", kind = "audioinput")
        val fakeSettingsOutput = AudioDeviceInfo(deviceId = "output-1", label = "Output 1", kind = "audiooutput")
    }
}

/** Dart's `_FakeAutoUpdatePromptStore`. */
class FakeSettingsAutoUpdatePromptStore(
    val writes: MutableList<Boolean> = mutableListOf(),
    val initialValue: Boolean = true,
) : AutoUpdatePromptStore() {
    var value: Boolean? = null
    var ignoredVersion: String? = null

    override suspend fun read(): Boolean = value ?: initialValue

    override suspend fun write(enabled: Boolean) {
        value = enabled
        writes.add(enabled)
    }

    override suspend fun readIgnoredVersion(): String? = ignoredVersion

    override suspend fun writeIgnoredVersion(version: String?) {
        ignoredVersion = version
    }
}

/** Dart's `_FakeInstallInfoService`. */
class FakeSettingsInstallInfoService(private val installedAt: String?) : InstallInfoService() {
    override suspend fun readInstalledAt(): String? = installedAt
}

/** An in-memory close behaviour store (Dart's tests ran on `SharedPreferences.setMockInitialValues`). */
class MemorySettingsCloseBehaviorStore(var behavior: CloseBehavior = defaultCloseBehavior) : CloseBehaviorStore() {
    override suspend fun read(): CloseBehavior = behavior

    override suspend fun write(behavior: CloseBehavior) {
        this.behavior = behavior
    }
}

/** An in-memory language preference store. */
class MemorySettingsLanguageStore(var language: String = "zh-Hans") : LanguagePreferenceStore() {
    override suspend fun read(): String = language

    override suspend fun write(language: String) {
        this.language = language
    }
}

/** The node itself or its single merged surface below a test tag (Buttons put the tag on a wrapper). */
private fun ComposeContentTestRule._buttonNode(tag: String) =
    onAllNodes(
        (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))) and
            (SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled) or SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)),
    ).fetchSemanticsNodes().first()

/** Dart's `tester.widget<Button>(find.byKey(key)).onPressed != null`. */
fun ComposeContentTestRule.buttonEnabled(tag: String): Boolean {
    val node = _buttonNode(tag)
    return node.config.getOrNull(SemanticsProperties.Disabled) == null && node.config.getOrNull(SemanticsActions.OnClick) != null
}

/** The value a slider reports (Dart's `tester.widget<Slider>(...).value`). */
fun SemanticsNodeInteraction.sliderValue(): Float =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current ?: Float.NaN

/**
 * A release service whose download reports [receivedBytes] of [totalBytes]
 * and then waits for [finish] (or never finishes); [installs] records
 * installer launches.
 */
class HangingDownloadReleaseUpdateService(
    private val receivedBytes: Long,
    private val totalBytes: Long?,
    private val finish: kotlinx.coroutines.CompletableDeferred<java.io.File>? = null,
) : ReleaseUpdateService() {
    val downloads = mutableListOf<String>()
    val installs = mutableListOf<String>()
    var cancellationToken: ReleaseDownloadCancellationToken? = null

    override suspend fun downloadUpdate(
        update: AvailableAppUpdate,
        onProgress: ReleaseDownloadProgress?,
        cancellationToken: ReleaseDownloadCancellationToken?,
    ): java.io.File {
        downloads.add(update.latestVersion)
        this.cancellationToken = cancellationToken
        onProgress?.invoke(receivedBytes = 0, totalBytes = totalBytes)
        onProgress?.invoke(receivedBytes = receivedBytes, totalBytes = totalBytes)
        val pending = finish ?: kotlinx.coroutines.CompletableDeferred()
        return pending.await()
    }

    override suspend fun startInstaller(file: java.io.File, platform: AppUpdatePlatform?) {
        installs.add("${file.name}:$platform")
    }
}
