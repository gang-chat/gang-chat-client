package com.gangchat.client.shell

import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.FormatException
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.complete
import com.gangchat.client.platform.AndroidSystemBridge
import com.gangchat.client.platform.GangChatPushRegistration
import kotlin.time.Duration
import kotlinx.coroutines.Deferred

/**
 * Android-only operating-system capabilities used by the shell adapters.
 *
 * The contract deliberately contains no Android SDK types. Calls go straight
 * to [AndroidSystemBridge] (formerly the `gang_chat/android_system`
 * MethodChannel); bridge failures surface as
 * [com.gangchat.client.platform.PlatformException]s with the channel's codes.
 * [isSupported] is false when the platform layer is not installed (plain JVM
 * unit tests), and then every call returns a harmless default, as the Dart
 * service did off Android.
 */
open class AndroidSystemService {
    open val isSupported: Boolean get() = AndroidSystemBridge.isInstalled

    private val bridge: AndroidSystemBridge get() = AndroidSystemBridge.instance

    open val selectedNotificationRoomIds: Stream<String>
        get() {
            _ensureMethodHandler()
            return _selectedRooms.stream
        }

    open val pushRegistrationChanges: Stream<AndroidPushRegistration>
        get() {
            _ensureMethodHandler()
            return _pushRegistrationChanges.stream
        }

    /**
     * Emitted when Android removes the app from its recent-task list.
     *
     * The native foreground service waits briefly for
     * [AndroidTaskRemovalRequest.complete] before it terminates the process.
     * That gives the authenticated shell a chance to leave voice and close its
     * realtime session without changing ordinary background behaviour. Native
     * code still enforces a timeout, so a wedged event loop cannot keep the
     * removed task alive.
     */
    open val taskRemovalRequests: Stream<AndroidTaskRemovalRequest>
        get() {
            _ensureMethodHandler()
            return _taskRemovalRequests.stream
        }

    open suspend fun openUri(uri: Uri) {
        _invokeVoid { bridge.openUri(uri.toString()) }
    }

    open suspend fun openMailto(uri: Uri) {
        _invokeVoid { bridge.openMailto(uri.toString()) }
    }

    open suspend fun installedAt(): String? {
        if (!isSupported) return null
        return bridge.getInstalledAt()
    }

    open suspend fun createDocument(suggestedName: String, mimeType: String? = null): AndroidDocumentLocation? {
        if (!isSupported) return null
        val result = bridge.createDocument(suggestedName, mimeType) ?: return null
        val uri = result.uri.trim()
        val path = result.path.trim()
        if (uri.isEmpty() || path.isEmpty()) {
            throw FormatException("Android 保存位置响应不完整")
        }
        return AndroidDocumentLocation(uri = uri, stagingPath = path)
    }

    open suspend fun writeDocumentBytes(uri: String, bytes: ByteArray) {
        _invokeVoid { bridge.writeDocumentBytes(uri, bytes) }
    }

    open suspend fun commitDocumentFile(uri: String, stagingPath: String) {
        _invokeVoid { bridge.commitDocumentFile(uri, stagingPath) }
    }

    open suspend fun discardDocumentFile(stagingPath: String) {
        _invokeVoid { bridge.discardDocumentFile(stagingPath) }
    }

    open suspend fun saveToDownloads(filename: String, bytes: ByteArray, mimeType: String? = null): String? {
        if (!isSupported) return null
        return bridge.saveToDownloads(filename, mimeType, bytes)
    }

    open suspend fun readClipboardImage(): AndroidClipboardImage? {
        if (!isSupported) return null
        val result = bridge.readClipboardImage() ?: return null
        if (result.bytes.isEmpty()) return null
        return AndroidClipboardImage(
            bytes = result.bytes,
            filename = result.filename,
            mimeType = result.mimeType,
        )
    }

    open suspend fun writeClipboardImage(bytes: ByteArray, mimeType: String): Boolean {
        if (!isSupported || bytes.isEmpty()) return false
        return bridge.writeClipboardImage(bytes, mimeType)
    }

    open suspend fun enterBackground() {
        _invokeVoid { bridge.enterBackground() }
    }

    open suspend fun enterForeground() {
        _invokeVoid { bridge.enterForeground() }
    }

    open suspend fun showRoomMessage(
        roomId: String,
        roomName: String,
        sender: String,
        body: String,
        unreadCount: Int,
        messageId: String? = null,
    ) {
        _invokeVoid {
            bridge.showRoomMessage(
                roomId = roomId,
                roomName = roomName,
                sender = sender,
                body = body,
                unreadCount = unreadCount,
                messageId = messageId.orEmpty(),
            )
        }
    }

    open suspend fun cancelRoomNotification(roomId: String) {
        _invokeVoid { bridge.cancelRoomNotification(roomId) }
    }

    open suspend fun syncBadge(unreadCount: Int) {
        _invokeVoid { bridge.syncBadge(unreadCount.coerceIn(0, 9999)) }
    }

    open suspend fun clearMessageNotifications() {
        _invokeVoid { bridge.clearMessageNotifications() }
    }

    open suspend fun notificationsEnabled(): Boolean {
        if (!isSupported) return false
        return bridge.notificationsEnabled()
    }

    open suspend fun notificationPreferenceEnabled(): Boolean {
        if (!isSupported) return false
        return bridge.notificationPreferenceEnabled()
    }

    open suspend fun setNotificationPreferenceEnabled(enabled: Boolean): Boolean {
        if (!isSupported) return false
        return bridge.setNotificationPreferenceEnabled(enabled)
    }

    open suspend fun requestNotificationPermission(): Boolean {
        if (!isSupported) return false
        return bridge.requestNotificationPermission()
    }

    open suspend fun requestUpdateNotificationPermission(): Boolean {
        if (!isSupported) return false
        return bridge.requestUpdateNotificationPermission()
    }

    open suspend fun setUpdateDownloadActive(active: Boolean) {
        _invokeVoid { bridge.setUpdateDownloadActive(active) }
    }

    open suspend fun pushRegistration(): AndroidPushRegistration? {
        if (!isSupported) return null
        _ensureMethodHandler()
        val result = bridge.getPushRegistration() ?: return null
        return AndroidPushRegistration.fromMap(result.asMap())
    }

    open suspend fun requestBluetoothConnectPermission(): Boolean {
        if (!isSupported) return false
        return bridge.requestBluetoothConnectPermission()
    }

    open suspend fun openNotificationSettings() {
        _invokeVoid { bridge.openNotificationSettings() }
    }

    open suspend fun takeInitialNotificationRoomId(): String? {
        if (!isSupported) return null
        _ensureMethodHandler()
        val value = bridge.getInitialNotificationRoomId()
        val roomId = value?.trim().orEmpty()
        return roomId.ifEmpty { null }
    }

    open suspend fun speak(segments: List<String>, volume: Double, pause: Duration) {
        _invokeVoid {
            bridge.speak(
                segments = segments,
                volume = volume.coerceIn(0.0, 1.0).toFloat(),
                pauseMs = pause.inWholeMilliseconds.toInt(),
            )
        }
    }

    open suspend fun disposeSpeech() {
        _invokeVoid { bridge.disposeSpeech() }
    }

    private suspend fun _invokeVoid(call: suspend () -> Unit) {
        if (!isSupported) return
        call()
    }

    companion object {
        private val _selectedRooms = StreamController.broadcast<String>()
        private val _pushRegistrationChanges = StreamController.broadcast<AndroidPushRegistration>()
        private val _taskRemovalRequests = StreamController.broadcast<AndroidTaskRemovalRequest>(sync = true)
        private var _methodHandlerInstalledOn: AndroidSystemBridge? = null

        private fun _ensureMethodHandler() {
            if (!AndroidSystemBridge.isInstalled) return
            val bridge = AndroidSystemBridge.instance
            if (_methodHandlerInstalledOn === bridge) return
            _methodHandlerInstalledOn = bridge
            bridge.eventHandler =
                object : AndroidSystemBridge.EventHandler {
                    override fun notificationSelected(roomId: String) {
                        val trimmed = roomId.trim()
                        if (trimmed.isNotEmpty()) _selectedRooms.add(trimmed)
                    }

                    override fun pushTokenChanged(registration: GangChatPushRegistration) {
                        val parsed = AndroidPushRegistration.tryFromMap(registration.asMap())
                        if (parsed != null) _pushRegistrationChanges.add(parsed)
                    }

                    override fun taskRemoved(onFinished: () -> Unit) {
                        if (!_taskRemovalRequests.hasListener) {
                            onFinished()
                            return
                        }
                        val request = AndroidTaskRemovalRequest()
                        _taskRemovalRequests.add(request)
                        request.completed.invokeOnCompletion { onFinished() }
                    }
                }
        }
    }
}

class AndroidTaskRemovalRequest {
    private val _completion = Completer<Unit>()

    val completed: Deferred<Unit> get() = _completion.future

    fun complete() {
        if (!_completion.isCompleted) _completion.complete()
    }
}

class AndroidPushRegistration(
    val provider: String,
    val installationId: String,
    val token: String,
    val enabled: Boolean,
) {
    companion object {
        fun fromMap(value: Map<*, *>): AndroidPushRegistration =
            tryFromMap(value) ?: throw FormatException("Android 推送注册信息不完整")

        fun tryFromMap(value: Map<*, *>): AndroidPushRegistration? {
            val provider = value["provider"]?.toString()?.trim().orEmpty()
            val installationId = value["installationId"]?.toString()?.trim().orEmpty()
            val token = value["token"]?.toString()?.trim().orEmpty()
            if (provider.isEmpty() || installationId.isEmpty() || token.isEmpty()) {
                return null
            }
            return AndroidPushRegistration(
                provider = provider,
                installationId = installationId,
                token = token,
                enabled = value["enabled"] as? Boolean ?: true,
            )
        }
    }
}

class AndroidDocumentLocation(val uri: String, val stagingPath: String)

class AndroidClipboardImage(
    val bytes: ByteArray,
    val filename: String,
    val mimeType: String,
)
