package com.gangchat.client.platform

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.gangchat.client.MainActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android operating-system capabilities behind `AndroidSystemService`
 * (formerly the `gang_chat/android_system` MethodChannel of the Flutter host).
 *
 * Each method is what the channel's handler did, called directly: results
 * are return values, channel errors are [PlatformException]s with the same
 * codes, and the events the host pushed to Dart (`notificationSelected`,
 * `pushTokenChanged`, `taskRemoved`) go to [eventHandler]. Blocking disk and
 * content-resolver work runs on [Dispatchers.IO]; everything else runs on the
 * main thread like the channel handlers did.
 *
 * One instance lives for the whole process ([instance]); it acts on the
 * activity attached to [GangChatPlatform] where one is needed.
 */
class AndroidSystemBridge(context: Context) {
    /** Events the Flutter host used to send to Dart over the channel. */
    interface EventHandler {
        fun notificationSelected(roomId: String)

        fun pushTokenChanged(registration: GangChatPushRegistration)

        /**
         * The app task was removed from recents. Call [onFinished] once the
         * graceful shutdown work is done (the service still enforces a timeout).
         */
        fun taskRemoved(onFinished: () -> Unit)
    }

    /** Location chosen in the system save dialog plus its private staging file. */
    data class DocumentLocation(val uri: String, val path: String)

    data class ClipboardImage(val bytes: ByteArray, val filename: String, val mimeType: String)

    private val context: Context = context.applicationContext ?: context

    private class PendingDocument(
        val result: CompletableDeferred<DocumentLocation?>,
        val suggestedName: String,
    )

    private var pendingDocument: PendingDocument? = null
    private var pendingNotificationPermission: CompletableDeferred<Boolean>? = null
    private var pendingUpdateNotificationPermission = false
    private var pendingBluetoothPermission: CompletableDeferred<Boolean>? = null
    private var pendingNotificationRoomId: String? = null
    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady = false
    private val pendingSpeech = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Receiver of host events; installed by `AndroidSystemService`. */
    @Volatile
    var eventHandler: EventHandler? = null

    private val activity: Activity? get() = GangChatPlatform.activity

    // -- Activity lifecycle -------------------------------------------------

    /**
     * Called when the main activity is created. A notification tap that
     * launched the activity becomes the initial room id (read once with
     * [getInitialNotificationRoomId]); the extra is consumed so a recreated
     * activity does not open the room again.
     */
    fun onActivityCreated(activity: Activity) {
        pendingNotificationRoomId = activity.intent?.getStringExtra(MainActivity.notificationRoomIdExtra)
        activity.intent?.removeExtra(MainActivity.notificationRoomIdExtra)
    }

    /** A notification tap while the activity exists: emits `notificationSelected`. */
    fun handleIntent(intent: Intent?) {
        val roomId =
            intent?.getStringExtra(MainActivity.notificationRoomIdExtra)
                ?.trim()
                .orEmpty()
        if (roomId.isEmpty()) return
        eventHandler?.notificationSelected(roomId)
        intent?.removeExtra(MainActivity.notificationRoomIdExtra)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != createDocumentRequestCode) return false
        val pending = pendingDocument ?: return true
        pendingDocument = null
        if (resultCode != Activity.RESULT_OK) {
            pending.result.complete(null)
            return true
        }
        val uri = data?.data
        if (uri == null) {
            pending.result.completeExceptionally(
                PlatformException("document_missing", "The selected document is unavailable."),
            )
            return true
        }
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // ACTION_CREATE_DOCUMENT still grants this activity a one-shot write.
        }
        val stagingRoot = File(context.cacheDir, documentStagingDirectory).apply { mkdirs() }
        val safeName = File(pending.suggestedName).name.ifBlank { "download" }
        val stagingFile = File(stagingRoot, "${UUID.randomUUID()}-$safeName")
        pending.result.complete(DocumentLocation(uri = uri.toString(), path = stagingFile.absolutePath))
        return true
    }

    fun onRequestPermissionsResult(requestCode: Int): Boolean {
        when (requestCode) {
            notificationPermissionRequestCode -> {
                val pending = pendingNotificationPermission ?: return true
                pendingNotificationPermission = null
                val forUpdate = pendingUpdateNotificationPermission
                pendingUpdateNotificationPermission = false
                pending.complete(
                    if (forUpdate) {
                        GangChatNotifications.updateNotificationsAllowed(context)
                    } else {
                        GangChatNotifications.notificationsAllowed(context)
                    },
                )
                return true
            }
            bluetoothPermissionRequestCode -> {
                val pending = pendingBluetoothPermission ?: return true
                pendingBluetoothPermission = null
                pending.complete(bluetoothConnectPermissionGranted())
                return true
            }
            else -> return false
        }
    }

    // -- Links ----------------------------------------------------------------

    fun openUri(value: String?) {
        val uri = parseAllowedUri(value, setOf("http", "https"))
        launch(Intent(Intent.ACTION_VIEW, uri))
    }

    fun openMailto(value: String?) {
        val uri = parseAllowedUri(value, setOf("mailto"))
        launch(Intent(Intent.ACTION_SENDTO, uri))
    }

    private fun parseAllowedUri(value: String?, schemes: Set<String>): Uri {
        val uri = value?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val scheme = uri?.scheme?.lowercase(Locale.ROOT)
        if (uri == null || scheme !in schemes) {
            throw PlatformException("invalid_uri", "The URI scheme is not allowed.")
        }
        return uri
    }

    private fun launch(intent: Intent) {
        try {
            val host = activity
            if (host != null) {
                host.startActivity(intent)
            } else {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (_: ActivityNotFoundException) {
            throw PlatformException("handler_unavailable", "No compatible Android app is installed.")
        } catch (error: Exception) {
            throw PlatformException("launch_failed", error.message)
        }
    }

    // -- Package info ---------------------------------------------------------

    /** The install/update time as `yyyy-MM-ddTHH:mm:ss.SSSZ` (UTC). */
    @Suppress("DEPRECATION")
    fun getInstalledAt(): String {
        try {
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0),
                    )
                } else {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
            val formatter =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            return formatter.format(Date(info.lastUpdateTime))
        } catch (error: Exception) {
            throw PlatformException("package_info_failed", error.message)
        }
    }

    // -- Documents and downloads ----------------------------------------------

    /**
     * Opens the system "save as" dialog. Returns `null` when cancelled, or
     * the chosen document URI plus a private staging path to write first.
     */
    suspend fun createDocument(suggestedName: String, mimeType: String?): DocumentLocation? {
        if (pendingDocument != null) {
            throw PlatformException("document_picker_busy", "Another save dialog is already open.")
        }
        val host = GangChatPlatform.requireActivity()
        val safeName = File(suggestedName).name.ifBlank { "download" }
        val result = CompletableDeferred<DocumentLocation?>()
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
            @Suppress("DEPRECATION")
            host.startActivityForResult(intent, createDocumentRequestCode)
        } catch (_: ActivityNotFoundException) {
            pendingDocument = null
            throw PlatformException("document_picker_unavailable", "The Android document picker is unavailable.")
        }
        return result.await()
    }

    suspend fun commitDocumentFile(uriValue: String?, path: String?) {
        val source = path?.let(::File)
        if (uriValue.isNullOrBlank() || source == null || !withContext(Dispatchers.IO) { source.isFile }) {
            throw PlatformException("invalid_document", "The staged file is unavailable.")
        }
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(Uri.parse(uriValue), "w").use { output ->
                    requireNotNull(output) { "The selected document cannot be opened." }
                    source.inputStream().use { input -> input.copyTo(output) }
                }
                source.delete()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                throw PlatformException("document_write_failed", error.message)
            }
        }
    }

    suspend fun discardDocumentFile(path: String?) {
        val source = path?.let(::File)
        val stagingRoot = File(context.cacheDir, documentStagingDirectory)
        withContext(Dispatchers.IO) {
            val isStagedFile =
                source != null &&
                    runCatching {
                        val rootPath = stagingRoot.canonicalPath + File.separator
                        source.canonicalPath.startsWith(rootPath)
                    }.getOrDefault(false)
            if (!isStagedFile) {
                throw PlatformException("invalid_staging_file", "The staged file path is invalid.")
            }
            if (source.exists() && !source.delete()) {
                throw PlatformException("staging_delete_failed", "The staged file could not be deleted.")
            }
        }
    }

    suspend fun writeDocumentBytes(uriValue: String?, bytes: ByteArray?) {
        if (uriValue.isNullOrBlank() || bytes == null) {
            throw PlatformException("invalid_document", "The selected document or data is unavailable.")
        }
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(Uri.parse(uriValue), "w").use { output ->
                    requireNotNull(output) { "The selected document cannot be opened." }
                    output.write(bytes)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                throw PlatformException("document_write_failed", error.message)
            }
        }
    }

    /** Saves into the public `Download/Gang Chat` folder; returns the MediaStore URI. */
    suspend fun saveToDownloads(filename: String, mimeType: String?, bytes: ByteArray?): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw PlatformException(
                "downloads_unavailable",
                "Public Downloads requires the document picker on this Android version.",
            )
        }
        if (filename.isBlank() || bytes == null) {
            throw PlatformException("invalid_download", "The download name or data is unavailable.")
        }
        return withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, File(filename).name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType ?: "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Gang Chat")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            val uri =
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw PlatformException("download_create_failed", "Android could not create the download.")
            try {
                resolver.openOutputStream(uri, "w").use { output ->
                    requireNotNull(output) { "The download cannot be opened." }
                    output.write(bytes)
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri.toString()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                resolver.delete(uri, null, null)
                throw PlatformException("download_write_failed", error.message)
            }
        }
    }

    // -- Clipboard images -----------------------------------------------------

    suspend fun readClipboardImage(): ClipboardImage? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) return null
        val uri = clip.getItemAt(0).uri ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val mimeType =
                    context.contentResolver.getType(uri)
                        ?.takeIf { it.startsWith("image/") }
                        ?: return@withContext null
                val bytes =
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "The clipboard image cannot be opened." }
                        input.readBytes()
                    }
                ClipboardImage(
                    bytes = bytes,
                    filename = "clipboard-image.${extensionForMimeType(mimeType)}",
                    mimeType = mimeType,
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                throw PlatformException("clipboard_read_failed", error.message)
            }
        }
    }

    suspend fun writeClipboardImage(bytes: ByteArray?, mimeType: String?): Boolean {
        if (bytes == null || bytes.isEmpty()) return false
        val normalizedMime = mimeType?.takeIf { it.startsWith("image/") } ?: "image/png"
        try {
            val uri =
                withContext(Dispatchers.IO) {
                    val root = File(context.cacheDir, clipboardImageDirectory).apply { mkdirs() }
                    root.listFiles()
                        ?.filter { System.currentTimeMillis() - it.lastModified() > clipboardMaxAgeMs }
                        ?.forEach(File::delete)
                    val file =
                        File(
                            root,
                            "clipboard-${UUID.randomUUID()}.${extensionForMimeType(normalizedMime)}",
                        )
                    file.writeBytes(bytes)
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "Gang Chat 图片", uri))
            return true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw PlatformException("clipboard_write_failed", error.message)
        }
    }

    private fun extensionForMimeType(mimeType: String): String =
        when (mimeType.lowercase(Locale.ROOT)) {
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "png"
        }

    // -- Background service ---------------------------------------------------

    fun enterBackground() {
        try {
            GangChatNotifications.ensureChannels(context)
            ContextCompat.startForegroundService(
                context,
                Intent(context, GangChatBackgroundService::class.java),
            )
        } catch (error: Exception) {
            throw PlatformException("background_service_failed", error.message)
        }
    }

    fun enterForeground() {
        context.stopService(Intent(context, GangChatBackgroundService::class.java))
    }

    // -- Notifications --------------------------------------------------------

    fun showRoomMessage(
        roomId: String,
        roomName: String,
        sender: String,
        body: String,
        unreadCount: Int,
        messageId: String,
    ) {
        GangChatNotifications.showRoomMessage(
            context = context,
            roomId = roomId,
            roomName = roomName,
            sender = sender,
            body = body,
            unreadCount = unreadCount,
            messageId = messageId,
        )
    }

    fun cancelRoomNotification(roomId: String) {
        GangChatNotifications.cancelRoom(context, roomId)
    }

    fun syncBadge(unreadCount: Int) {
        GangChatNotifications.syncBadge(context, unreadCount)
    }

    fun clearMessageNotifications() {
        GangChatNotifications.cancelMessages(context)
    }

    fun notificationsEnabled(): Boolean = GangChatNotifications.notificationsAllowed(context)

    fun notificationPreferenceEnabled(): Boolean = preferences().getBoolean(notificationsEnabledKey, true)

    /** Stores the in-app notification switch; returns whether notifications are now allowed. */
    fun setNotificationPreferenceEnabled(enabled: Boolean): Boolean {
        preferences().edit().putBoolean(notificationsEnabledKey, enabled).apply()
        if (!enabled) GangChatNotifications.cancelMessages(context)
        return GangChatNotifications.notificationsAllowed(context)
    }

    suspend fun getPushRegistration(): GangChatPushRegistration? =
        try {
            GangChatPushRegistrationProvider.current(context)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw PlatformException("push_registration_failed", error.message)
        }

    suspend fun requestNotificationPermission(forUpdate: Boolean = false): Boolean {
        GangChatNotifications.ensureChannels(context)
        fun allowed() =
            if (forUpdate) {
                GangChatNotifications.updateNotificationsAllowed(context)
            } else {
                GangChatNotifications.notificationsAllowed(context)
            }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return allowed()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return allowed()
        }
        if (pendingNotificationPermission != null) {
            throw PlatformException("permission_request_busy", "A notification permission request is active.")
        }
        val host = GangChatPlatform.requireActivity()
        val pending = CompletableDeferred<Boolean>()
        pendingNotificationPermission = pending
        pendingUpdateNotificationPermission = forUpdate
        ActivityCompat.requestPermissions(
            host,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            notificationPermissionRequestCode,
        )
        return pending.await()
    }

    suspend fun requestUpdateNotificationPermission(): Boolean = requestNotificationPermission(forUpdate = true)

    fun setUpdateDownloadActive(active: Boolean) {
        GangChatNotifications.setUpdateDownloadActive(active)
    }

    suspend fun requestBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || bluetoothConnectPermissionGranted()) {
            return true
        }
        if (pendingBluetoothPermission != null) {
            throw PlatformException("permission_request_busy", "A Bluetooth permission request is active.")
        }
        val host = GangChatPlatform.requireActivity()
        val pending = CompletableDeferred<Boolean>()
        pendingBluetoothPermission = pending
        ActivityCompat.requestPermissions(
            host,
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
            bluetoothPermissionRequestCode,
        )
        return pending.await()
    }

    private fun bluetoothConnectPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun openNotificationSettings() {
        launch(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            },
        )
    }

    /** The room id from the notification that launched the activity; returned once. */
    fun getInitialNotificationRoomId(): String? {
        val roomId = pendingNotificationRoomId
        pendingNotificationRoomId = null
        return roomId
    }

    // -- Text to speech -------------------------------------------------------

    /** Speaks [segments] in order with [pauseMs] between them; returns when done. */
    suspend fun speak(segments: List<String>, volume: Float, pauseMs: Int) {
        val cleanSegments = segments.map(String::trim).filter(String::isNotEmpty)
        if (cleanSegments.isEmpty()) return
        if (!ensureTextToSpeech()) {
            throw PlatformException("tts_unavailable", "Android text-to-speech is unavailable.")
        }
        val tts = textToSpeech
            ?: throw PlatformException("tts_unavailable", "Android text-to-speech is unavailable.")
        // Presence announcements follow the cue before them: on the call's
        // route at the call volume during a voice call, as media otherwise.
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(
                    if (isInVoiceCall(context)) {
                        AudioAttributes.USAGE_VOICE_COMMUNICATION
                    } else {
                        AudioAttributes.USAGE_MEDIA
                    },
                ).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        val finalId = "gang-chat-${UUID.randomUUID()}"
        val done = CompletableDeferred<Unit>()
        pendingSpeech[finalId] = done
        cleanSegments.forEachIndexed { index, segment ->
            val id = if (index == cleanSegments.lastIndex) finalId else "$finalId-$index"
            val params =
                Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f))
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
        done.await()
    }

    fun stopSpeech() {
        textToSpeech?.stop()
        completePendingSpeech()
    }

    fun disposeSpeech() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        textToSpeechReady = false
        completePendingSpeech()
    }

    private suspend fun ensureTextToSpeech(): Boolean {
        if (textToSpeechReady && textToSpeech != null) return true
        // A previous initialization is still in progress. Presence
        // announcements are best-effort; do not queue behind an unknown engine.
        if (textToSpeech != null) return false
        val initialized = CompletableDeferred<Boolean>()
        textToSpeech =
            TextToSpeech(context) { status ->
                mainHandler.post {
                    val tts = textToSpeech
                    textToSpeechReady = status == TextToSpeech.SUCCESS && tts != null
                    if (textToSpeechReady && tts != null) configureTextToSpeech(tts)
                    initialized.complete(textToSpeechReady)
                }
            }
        return initialized.await()
    }

    private fun configureTextToSpeech(tts: TextToSpeech) {
        val languageResult = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            tts.language = Locale.CHINESE
        }
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        val pending = utteranceId?.let(pendingSpeech::remove) ?: return@post
                        pending.complete(Unit)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onError(utteranceId, TextToSpeech.ERROR)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    mainHandler.post {
                        val pending = utteranceId?.let(pendingSpeech::remove) ?: return@post
                        pending.completeExceptionally(
                            PlatformException("tts_failed", "Android text-to-speech failed ($errorCode)."),
                        )
                    }
                }
            },
        )
    }

    private fun completePendingSpeech() {
        val results = pendingSpeech.values.toList()
        pendingSpeech.clear()
        results.forEach { it.complete(Unit) }
    }

    private fun preferences() = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    companion object {
        const val preferencesName = "gang_chat_android_preferences"
        const val notificationsEnabledKey = "notifications_enabled"

        const val createDocumentRequestCode = 4201
        const val notificationPermissionRequestCode = 4202
        const val bluetoothPermissionRequestCode = 4203
        const val documentStagingDirectory = "document-staging"
        const val clipboardImageDirectory = "clipboard-images"
        private const val clipboardMaxAgeMs = 24L * 60L * 60L * 1000L

        @Volatile
        private var installed: AndroidSystemBridge? = null

        /** The process-wide bridge; [MissingPluginException] before the app installs it. */
        val instance: AndroidSystemBridge
            get() = installed
                ?: throw MissingPluginException("No implementation found for gang_chat/android_system")

        val isInstalled: Boolean get() = installed != null

        fun install(bridge: AndroidSystemBridge) {
            installed = bridge
        }

        fun notifyPushTokenChanged(registration: GangChatPushRegistration) {
            Handler(Looper.getMainLooper()).post {
                installed?.eventHandler?.pushTokenChanged(registration)
            }
        }

        /**
         * Asks the app to shut down gracefully after its task was removed.
         * Returns `false` when there is no app to ask, so the caller stops
         * right away; otherwise [onFinished] runs once the app is done.
         */
        fun notifyTaskRemoved(onFinished: () -> Unit): Boolean {
            val bridge = installed ?: return false
            Handler(Looper.getMainLooper()).post {
                val handler = bridge.eventHandler
                if (handler == null) {
                    onFinished()
                } else {
                    handler.taskRemoved(onFinished)
                }
            }
            return true
        }
    }
}
