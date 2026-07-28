package com.gangchat.client

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class AndroidSystemBridge(private val activity: Activity) {
    private data class PendingDocument(
        val result: MethodChannel.Result,
        val suggestedName: String,
    )

    private var pendingDocument: PendingDocument? = null
    private var pendingNotificationPermission: MethodChannel.Result? = null
    private var pendingBluetoothPermission: MethodChannel.Result? = null
    private var channel: MethodChannel? = null
    private var pendingNotificationRoomId: String? = null
    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady = false
    private val pendingSpeech = mutableMapOf<String, MethodChannel.Result>()

    fun attach(channel: MethodChannel) {
        this.channel = channel
        activeChannel = channel
        channel.setMethodCallHandler(::handleMethodCall)
        pendingNotificationRoomId =
            activity.intent?.getStringExtra(MainActivity.notificationRoomIdExtra)
    }

    fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "openUri" -> openUri(call.argument<String>("uri"), result)
            "openMailto" -> openMailto(call.argument<String>("uri"), result)
            "getInstalledAt" -> getInstalledAt(result)
            "createDocument" ->
                createDocument(
                    suggestedName = call.argument<String>("suggestedName").orEmpty(),
                    mimeType = call.argument<String>("mimeType"),
                    result = result,
                )
            "commitDocumentFile" ->
                commitDocumentFile(
                    uriValue = call.argument<String>("uri"),
                    path = call.argument<String>("path"),
                    result = result,
                )
            "discardDocumentFile" ->
                discardDocumentFile(
                    path = call.argument<String>("path"),
                    result = result,
                )
            "writeDocumentBytes" ->
                writeDocumentBytes(
                    uriValue = call.argument<String>("uri"),
                    bytes = call.argument<ByteArray>("bytes"),
                    result = result,
                )
            "saveToDownloads" ->
                saveToDownloads(
                    filename = call.argument<String>("filename").orEmpty(),
                    mimeType = call.argument<String>("mimeType"),
                    bytes = call.argument<ByteArray>("bytes"),
                    result = result,
                )
            "readClipboardImage" -> readClipboardImage(result)
            "writeClipboardImage" ->
                writeClipboardImage(
                    bytes = call.argument<ByteArray>("bytes"),
                    mimeType = call.argument<String>("mimeType"),
                    result = result,
                )
            "enterBackground" -> enterBackground(result)
            "enterForeground" -> exitBackground(result)
            "showRoomMessage" -> showRoomMessage(call, result)
            "cancelRoomNotification" -> {
                GangChatNotifications.cancelRoom(
                    activity,
                    call.argument<String>("roomId").orEmpty(),
                )
                result.success(null)
            }
            "syncBadge" -> {
                GangChatNotifications.syncBadge(
                    activity,
                    call.argument<Int>("unreadCount") ?: 0,
                )
                result.success(null)
            }
            "clearMessageNotifications" -> {
                GangChatNotifications.cancelMessages(activity)
                result.success(null)
            }
            "notificationsEnabled" ->
                result.success(GangChatNotifications.notificationsAllowed(activity))
            "notificationPreferenceEnabled" ->
                result.success(preferences().getBoolean(notificationsEnabledKey, true))
            "setNotificationPreferenceEnabled" -> {
                val enabled = call.argument<Boolean>("enabled") ?: true
                preferences().edit().putBoolean(notificationsEnabledKey, enabled).apply()
                if (!enabled) GangChatNotifications.cancelMessages(activity)
                result.success(GangChatNotifications.notificationsAllowed(activity))
            }
            "getPushRegistration" ->
                GangChatPushRegistrationProvider.current(activity) { registration ->
                    activity.runOnUiThread {
                        registration.fold(
                            onSuccess = { result.success(it?.asMap()) },
                            onFailure = {
                                result.error(
                                    "push_registration_failed",
                                    it.message,
                                    null,
                                )
                            },
                        )
                    }
                }
            "requestNotificationPermission" -> requestNotificationPermission(result)
            "requestBluetoothConnectPermission" -> requestBluetoothConnectPermission(result)
            "openNotificationSettings" -> openNotificationSettings(result)
            "getInitialNotificationRoomId" -> {
                val roomId = pendingNotificationRoomId
                pendingNotificationRoomId = null
                result.success(roomId)
            }
            "speak" ->
                speak(
                    call.argument<List<String>>("segments").orEmpty(),
                    (call.argument<Double>("volume") ?: 1.0).toFloat(),
                    call.argument<Int>("pauseMs") ?: 280,
                    result,
                )
            "stopSpeech" -> {
                textToSpeech?.stop()
                completePendingSpeech()
                result.success(null)
            }
            "disposeSpeech" -> {
                textToSpeech?.stop()
                textToSpeech?.shutdown()
                textToSpeech = null
                textToSpeechReady = false
                completePendingSpeech()
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != createDocumentRequestCode) return false
        val pending = pendingDocument ?: return true
        pendingDocument = null
        if (resultCode != Activity.RESULT_OK) {
            pending.result.success(null)
            return true
        }
        val uri = data?.data
        if (uri == null) {
            pending.result.error("document_missing", "The selected document is unavailable.", null)
            return true
        }
        try {
            activity.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // ACTION_CREATE_DOCUMENT still grants this activity a one-shot write.
        }
        val stagingRoot = File(activity.cacheDir, documentStagingDirectory).apply { mkdirs() }
        val safeName = File(pending.suggestedName).name.ifBlank { "download" }
        val stagingFile = File(stagingRoot, "${UUID.randomUUID()}-$safeName")
        pending.result.success(
            mapOf(
                "uri" to uri.toString(),
                "path" to stagingFile.absolutePath,
            ),
        )
        return true
    }

    fun onRequestPermissionsResult(requestCode: Int): Boolean {
        when (requestCode) {
            notificationPermissionRequestCode -> {
                val pending = pendingNotificationPermission ?: return true
                pendingNotificationPermission = null
                pending.success(GangChatNotifications.notificationsAllowed(activity))
                return true
            }
            bluetoothPermissionRequestCode -> {
                val pending = pendingBluetoothPermission ?: return true
                pendingBluetoothPermission = null
                pending.success(bluetoothConnectPermissionGranted())
                return true
            }
            else -> return false
        }
    }

    fun handleIntent(intent: Intent?) {
        val roomId =
            intent?.getStringExtra(MainActivity.notificationRoomIdExtra)
                ?.trim()
                .orEmpty()
        if (roomId.isEmpty()) return
        if (channel == null) {
            pendingNotificationRoomId = roomId
        } else {
            channel?.invokeMethod("notificationSelected", mapOf("roomId" to roomId))
        }
        intent?.removeExtra(MainActivity.notificationRoomIdExtra)
    }

    private fun openUri(value: String?, result: MethodChannel.Result) {
        val uri = parseAllowedUri(value, setOf("http", "https"), result) ?: return
        launch(Intent(Intent.ACTION_VIEW, uri), result)
    }

    private fun openMailto(value: String?, result: MethodChannel.Result) {
        val uri = parseAllowedUri(value, setOf("mailto"), result) ?: return
        launch(Intent(Intent.ACTION_SENDTO, uri), result)
    }

    private fun parseAllowedUri(
        value: String?,
        schemes: Set<String>,
        result: MethodChannel.Result,
    ): Uri? {
        val uri = value?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val scheme = uri?.scheme?.lowercase(Locale.ROOT)
        if (uri == null || scheme !in schemes) {
            result.error("invalid_uri", "The URI scheme is not allowed.", null)
            return null
        }
        return uri
    }

    private fun launch(intent: Intent, result: MethodChannel.Result) {
        try {
            activity.startActivity(intent)
            result.success(null)
        } catch (_: ActivityNotFoundException) {
            result.error("handler_unavailable", "No compatible Android app is installed.", null)
        } catch (error: Exception) {
            result.error("launch_failed", error.message, null)
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstalledAt(result: MethodChannel.Result) {
        try {
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activity.packageManager.getPackageInfo(
                        activity.packageName,
                        PackageManager.PackageInfoFlags.of(0),
                    )
                } else {
                    activity.packageManager.getPackageInfo(activity.packageName, 0)
                }
            val formatter =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            result.success(formatter.format(Date(info.lastUpdateTime)))
        } catch (error: Exception) {
            result.error("package_info_failed", error.message, null)
        }
    }

    private fun createDocument(
        suggestedName: String,
        mimeType: String?,
        result: MethodChannel.Result,
    ) {
        if (pendingDocument != null) {
            result.error("document_picker_busy", "Another save dialog is already open.", null)
            return
        }
        val safeName = File(suggestedName).name.ifBlank { "download" }
        pendingDocument = PendingDocument(result, safeName)
        val intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, safeName)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                )
            }
        try {
            activity.startActivityForResult(intent, createDocumentRequestCode)
        } catch (_: ActivityNotFoundException) {
            pendingDocument = null
            result.error("document_picker_unavailable", "The Android document picker is unavailable.", null)
        }
    }

    private fun commitDocumentFile(
        uriValue: String?,
        path: String?,
        result: MethodChannel.Result,
    ) {
        val source = path?.let(::File)
        if (uriValue.isNullOrBlank() || source == null || !source.isFile) {
            result.error("invalid_document", "The staged file is unavailable.", null)
            return
        }
        try {
            activity.contentResolver.openOutputStream(Uri.parse(uriValue), "w").use { output ->
                requireNotNull(output) { "The selected document cannot be opened." }
                source.inputStream().use { input -> input.copyTo(output) }
            }
            source.delete()
            result.success(null)
        } catch (error: Exception) {
            result.error("document_write_failed", error.message, null)
        }
    }

    private fun discardDocumentFile(path: String?, result: MethodChannel.Result) {
        val source = path?.let(::File)
        val stagingRoot = File(activity.cacheDir, documentStagingDirectory)
        val isStagedFile =
            source != null &&
                runCatching {
                    val rootPath = stagingRoot.canonicalPath + File.separator
                    source.canonicalPath.startsWith(rootPath)
                }.getOrDefault(false)
        if (!isStagedFile) {
            result.error("invalid_staging_file", "The staged file path is invalid.", null)
            return
        }
        if (source.exists() && !source.delete()) {
            result.error("staging_delete_failed", "The staged file could not be deleted.", null)
            return
        }
        result.success(null)
    }

    private fun writeDocumentBytes(
        uriValue: String?,
        bytes: ByteArray?,
        result: MethodChannel.Result,
    ) {
        if (uriValue.isNullOrBlank() || bytes == null) {
            result.error("invalid_document", "The selected document or data is unavailable.", null)
            return
        }
        try {
            activity.contentResolver.openOutputStream(Uri.parse(uriValue), "w").use { output ->
                requireNotNull(output) { "The selected document cannot be opened." }
                output.write(bytes)
            }
            result.success(null)
        } catch (error: Exception) {
            result.error("document_write_failed", error.message, null)
        }
    }

    private fun saveToDownloads(
        filename: String,
        mimeType: String?,
        bytes: ByteArray?,
        result: MethodChannel.Result,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            result.error(
                "downloads_unavailable",
                "Public Downloads requires the document picker on this Android version.",
                null,
            )
            return
        }
        if (filename.isBlank() || bytes == null) {
            result.error("invalid_download", "The download name or data is unavailable.", null)
            return
        }
        val resolver = activity.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, File(filename).name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType ?: "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Gang Chat")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            result.error("download_create_failed", "Android could not create the download.", null)
            return
        }
        try {
            resolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "The download cannot be opened." }
                output.write(bytes)
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            result.success(uri.toString())
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            result.error("download_write_failed", error.message, null)
        }
    }

    private fun readClipboardImage(result: MethodChannel.Result) {
        val clipboard =
            activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            result.success(null)
            return
        }
        val uri = clip.getItemAt(0).uri
        if (uri == null) {
            result.success(null)
            return
        }
        try {
            val mimeType =
                activity.contentResolver.getType(uri)
                    ?.takeIf { it.startsWith("image/") }
            if (mimeType == null) {
                result.success(null)
                return
            }
            val bytes = activity.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "The clipboard image cannot be opened." }
                input.readBytes()
            }
            result.success(
                mapOf(
                    "bytes" to bytes,
                    "filename" to "clipboard-image.${extensionForMimeType(mimeType)}",
                    "mimeType" to mimeType,
                ),
            )
        } catch (error: Exception) {
            result.error("clipboard_read_failed", error.message, null)
        }
    }

    private fun writeClipboardImage(
        bytes: ByteArray?,
        mimeType: String?,
        result: MethodChannel.Result,
    ) {
        if (bytes == null || bytes.isEmpty()) {
            result.success(false)
            return
        }
        val normalizedMime =
            mimeType?.takeIf { it.startsWith("image/") } ?: "image/png"
        try {
            val root = File(activity.cacheDir, clipboardImageDirectory).apply { mkdirs() }
            root.listFiles()
                ?.filter { System.currentTimeMillis() - it.lastModified() > clipboardMaxAgeMs }
                ?.forEach(File::delete)
            val file =
                File(
                    root,
                    "clipboard-${UUID.randomUUID()}.${extensionForMimeType(normalizedMime)}",
                )
            file.writeBytes(bytes)
            val uri =
                FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    file,
                )
            val clipboard =
                activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newUri(activity.contentResolver, "Gang Chat 图片", uri),
            )
            result.success(true)
        } catch (error: Exception) {
            result.error("clipboard_write_failed", error.message, null)
        }
    }

    private fun extensionForMimeType(mimeType: String): String =
        when (mimeType.lowercase(Locale.ROOT)) {
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "png"
        }

    private fun enterBackground(result: MethodChannel.Result) {
        try {
            GangChatNotifications.ensureChannels(activity)
            ContextCompat.startForegroundService(
                activity,
                Intent(activity, GangChatBackgroundService::class.java),
            )
            result.success(null)
        } catch (error: Exception) {
            result.error("background_service_failed", error.message, null)
        }
    }

    private fun exitBackground(result: MethodChannel.Result) {
        activity.stopService(Intent(activity, GangChatBackgroundService::class.java))
        result.success(null)
    }

    private fun showRoomMessage(call: MethodCall, result: MethodChannel.Result) {
        GangChatNotifications.showRoomMessage(
            context = activity,
            roomId = call.argument<String>("roomId").orEmpty(),
            roomName = call.argument<String>("roomName").orEmpty(),
            sender = call.argument<String>("sender").orEmpty(),
            body = call.argument<String>("body").orEmpty(),
            unreadCount = call.argument<Int>("unreadCount") ?: 1,
            messageId = call.argument<String>("messageId").orEmpty(),
        )
        result.success(null)
    }

    private fun requestNotificationPermission(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            result.success(GangChatNotifications.notificationsAllowed(activity))
            return
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            result.success(GangChatNotifications.notificationsAllowed(activity))
            return
        }
        if (pendingNotificationPermission != null) {
            result.error("permission_request_busy", "A notification permission request is active.", null)
            return
        }
        pendingNotificationPermission = result
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            notificationPermissionRequestCode,
        )
    }

    private fun requestBluetoothConnectPermission(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            bluetoothConnectPermissionGranted()
        ) {
            result.success(true)
            return
        }
        if (pendingBluetoothPermission != null) {
            result.error("permission_request_busy", "A Bluetooth permission request is active.", null)
            return
        }
        pendingBluetoothPermission = result
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
            bluetoothPermissionRequestCode,
        )
    }

    private fun bluetoothConnectPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED

    private fun openNotificationSettings(result: MethodChannel.Result) {
        launch(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            },
            result,
        )
    }

    private fun speak(
        segments: List<String>,
        volume: Float,
        pauseMs: Int,
        result: MethodChannel.Result,
    ) {
        val cleanSegments = segments.map(String::trim).filter(String::isNotEmpty)
        if (cleanSegments.isEmpty()) {
            result.success(null)
            return
        }
        ensureTextToSpeech { ready ->
            if (!ready) {
                result.error("tts_unavailable", "Android text-to-speech is unavailable.", null)
                return@ensureTextToSpeech
            }
            val tts = textToSpeech
            if (tts == null) {
                result.error("tts_unavailable", "Android text-to-speech is unavailable.", null)
                return@ensureTextToSpeech
            }
            val finalId = "gang-chat-${UUID.randomUUID()}"
            pendingSpeech[finalId] = result
            cleanSegments.forEachIndexed { index, segment ->
                val id = if (index == cleanSegments.lastIndex) finalId else "$finalId-$index"
                val params =
                    android.os.Bundle().apply {
                        putFloat(
                            TextToSpeech.Engine.KEY_PARAM_VOLUME,
                            volume.coerceIn(0f, 1f),
                        )
                    }
                tts.speak(segment, TextToSpeech.QUEUE_ADD, params, id)
                if (index < cleanSegments.lastIndex) {
                    tts.playSilentUtterance(
                        pauseMs.coerceAtLeast(0).toLong(),
                        TextToSpeech.QUEUE_ADD,
                        "$finalId-pause-$index",
                    )
                }
            }
        }
    }

    private fun ensureTextToSpeech(callback: (Boolean) -> Unit) {
        if (textToSpeechReady && textToSpeech != null) {
            callback(true)
            return
        }
        // A previous initialization is still in progress. Presence
        // announcements are best-effort; do not queue behind an unknown engine.
        if (textToSpeech != null) {
            callback(false)
            return
        }
        textToSpeech =
            TextToSpeech(activity.applicationContext) { status ->
                val tts = textToSpeech
                textToSpeechReady = status == TextToSpeech.SUCCESS && tts != null
                if (textToSpeechReady) {
                    val languageResult = tts!!.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                        languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        tts.language = Locale.CHINESE
                    }
                    tts.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit

                            override fun onDone(utteranceId: String?) {
                                val pending =
                                    utteranceId?.let(pendingSpeech::remove) ?: return
                                activity.runOnUiThread { pending.success(null) }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                onError(utteranceId, TextToSpeech.ERROR)
                            }

                            override fun onError(utteranceId: String?, errorCode: Int) {
                                val pending =
                                    utteranceId?.let(pendingSpeech::remove) ?: return
                                activity.runOnUiThread {
                                    pending.error(
                                        "tts_failed",
                                        "Android text-to-speech failed ($errorCode).",
                                        null,
                                    )
                                }
                            }
                        },
                    )
                }
                callback(textToSpeechReady)
            }
    }

    private fun completePendingSpeech() {
        val results = pendingSpeech.values.toList()
        pendingSpeech.clear()
        activity.runOnUiThread { results.forEach { it.success(null) } }
    }

    private fun preferences() =
        activity.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    companion object {
        const val channelName = "gang_chat/android_system"
        const val preferencesName = "gang_chat_android_preferences"
        const val notificationsEnabledKey = "notifications_enabled"
        @Volatile
        private var activeChannel: MethodChannel? = null

        fun notifyPushTokenChanged(registration: Map<String, Any>) {
            Handler(Looper.getMainLooper()).post {
                activeChannel?.invokeMethod("pushTokenChanged", registration)
            }
        }

        fun notifyTaskRemoved(onFinished: () -> Unit): Boolean {
            val currentChannel = activeChannel ?: return false
            Handler(Looper.getMainLooper()).post {
                currentChannel.invokeMethod(
                    "taskRemoved",
                    null,
                    object : MethodChannel.Result {
                        override fun success(result: Any?) = onFinished()

                        override fun error(
                            errorCode: String,
                            errorMessage: String?,
                            errorDetails: Any?,
                        ) = onFinished()

                        override fun notImplemented() = onFinished()
                    },
                )
            }
            return true
        }

        private const val createDocumentRequestCode = 4201
        private const val notificationPermissionRequestCode = 4202
        private const val bluetoothPermissionRequestCode = 4203
        private const val documentStagingDirectory = "document-staging"
        private const val clipboardImageDirectory = "clipboard-images"
        private const val clipboardMaxAgeMs = 24L * 60L * 60L * 1000L
    }
}
