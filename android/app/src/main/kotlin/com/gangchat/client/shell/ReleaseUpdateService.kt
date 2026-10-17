package com.gangchat.client.shell

import com.gangchat.client.app.AppUpdatePlatform
import com.gangchat.client.app.AvailableAppUpdate
import com.gangchat.client.app.isNewerAppVersion
import com.gangchat.client.app.latestReleaseAssetForPlatform
import com.gangchat.client.app.parseReleaseAssetsFromS3List
import com.gangchat.client.app.releaseAssetUrl
import com.gangchat.client.config.HostPlatform
import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.FormatException
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.complete
import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.foundation.http.HttpRequest
import com.gangchat.client.foundation.http.get
import com.gangchat.client.foundation.timeout
import com.gangchat.client.platform.AndroidUpdateInstaller
import com.gangchat.client.platform.PlatformException
import com.gangchat.client.plugins.PathProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Dart's `typedef ReleaseDownloadProgress = void Function({required int
 * receivedBytes, int? totalBytes})`; invoke with named arguments.
 */
fun interface ReleaseDownloadProgress {
    operator fun invoke(receivedBytes: Long, totalBytes: Long?)
}

typealias ReleaseTemporaryDirectoryProvider = suspend () -> File
typealias ReleaseAndroidInstallerLauncher = suspend (path: String) -> Unit

// PORT: the Windows installer process runner (`ReleaseInstallerProcessRunner`)
// and the macOS `open` launcher only ran on desktop and are not ported.

/** dart:io's `HttpException`; `toString()` matches Dart because the UI shows it. */
class HttpException(override val message: String, val uri: Uri? = null) : IOException(message) {
    override fun toString(): String {
        val buffer = StringBuilder("HttpException: ").append(message)
        if (uri != null) buffer.append(", uri = ").append(uri)
        return buffer.toString()
    }
}

class ReleaseDownloadCancelledException : Exception("版本下载已取消") {
    override fun toString(): String = "版本下载已取消"
}

class ReleaseInstallerException(override val message: String) : Exception(message) {
    override fun toString(): String = message
}

class ReleaseDownloadCancellationToken {
    private var _cancelled = false
    private var _active = false
    private var _client: HttpClient? = null
    private var _partialFile: File? = null
    private var _completion: Completer<Unit>? = null

    val isCancelled: Boolean get() = _cancelled

    fun cancel() {
        _cancelled = true
        _client?.close()
    }

    suspend fun cancelAndDeletePartialFile() {
        cancel()
        val completion = _completion
        if (_active && completion != null && !completion.isCompleted) {
            try {
                timeout(2.seconds) { completion.future.await() }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // A still-open file can fail deletion; try anyway below.
            }
        }
        _deleteFileQuietly(_partialFile)
    }

    internal fun _bind(client: HttpClient, partialFile: File) {
        _client = client
        _partialFile = partialFile
        _active = true
        _completion = Completer()
        if (_cancelled) client.close()
    }

    internal fun _throwIfCancelled() {
        if (_cancelled) throw ReleaseDownloadCancelledException()
    }

    internal fun _complete() {
        _client = null
        _active = false
        val completion = _completion
        if (completion != null && !completion.isCompleted) {
            completion.complete()
        }
    }
}

open class ReleaseUpdateService(
    httpClient: HttpClient? = null,
    temporaryDirectoryProvider: ReleaseTemporaryDirectoryProvider? = null,
    androidInstallerLauncher: ReleaseAndroidInstallerLauncher? = null,
) {
    private val _httpClient: HttpClient? = httpClient
    private val _temporaryDirectoryProvider: ReleaseTemporaryDirectoryProvider? = temporaryDirectoryProvider
    private val _androidInstallerLauncher: ReleaseAndroidInstallerLauncher? = androidInstallerLauncher

    open suspend fun checkForUpdate(
        bucketUrl: String,
        currentVersion: String,
        platform: AppUpdatePlatform,
    ): AvailableAppUpdate? {
        val response = _withClient { client -> client.get(_listUri(bucketUrl)) }
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw HttpException(
                "读取版本列表失败（状态码 ${response.statusCode}）",
                uri = _listUri(bucketUrl),
            )
        }

        val assets = parseReleaseAssetsFromS3List(response.body)
        val asset = latestReleaseAssetForPlatform(assets, platform)
        if (asset == null ||
            !isNewerAppVersion(
                currentVersion = currentVersion,
                latestVersion = asset.version,
            )
        ) {
            return null
        }

        return AvailableAppUpdate(
            currentVersion = currentVersion,
            latestVersion = asset.version,
            asset = asset,
            downloadUrl = Uri.parse(releaseAssetUrl(bucketUrl, asset.key)),
        )
    }

    open suspend fun downloadUpdate(
        update: AvailableAppUpdate,
        onProgress: ReleaseDownloadProgress? = null,
        cancellationToken: ReleaseDownloadCancellationToken? = null,
    ): File {
        val request = HttpRequest("GET", update.downloadUrl)
        val injected = _httpClient
        val client = injected ?: HttpClient()
        var file: File? = null
        try {
            file = _downloadTarget(update)
            cancellationToken?._bind(client, file)
            cancellationToken?._throwIfCancelled()
            val response = client.send(request)
            cancellationToken?._throwIfCancelled()
            if (response.statusCode < 200 || response.statusCode >= 300) {
                throw HttpException(
                    "版本下载失败（状态码 ${response.statusCode}）",
                    uri = update.downloadUrl,
                )
            }

            val target = file
            val sink: OutputStream = withContext(Dispatchers.IO) { FileOutputStream(target) }
            var received = 0L
            val contentLength = response.contentLength
            val total = if (contentLength != null && contentLength >= 0) contentLength else null
            onProgress?.invoke(receivedBytes = received, totalBytes = total)
            try {
                response.stream.flow.collect { chunk ->
                    cancellationToken?._throwIfCancelled()
                    received += chunk.size
                    withContext(Dispatchers.IO) { sink.write(chunk) }
                    onProgress?.invoke(receivedBytes = received, totalBytes = total)
                }
                cancellationToken?._throwIfCancelled()
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { sink.close() }
            }
            _validateAndroidApkDownload(
                target,
                receivedBytes = received,
                expectedBytes = total,
            )
            return target
        } catch (error: Exception) {
            withContext(NonCancellable) { _deleteFileQuietly(file) }
            // Coroutine cancellation keeps propagating as such.
            if (error is CancellationException) throw error
            if (error is ReleaseDownloadCancelledException || cancellationToken?.isCancelled == true) {
                throw ReleaseDownloadCancelledException()
            }
            throw error
        } finally {
            cancellationToken?._complete()
            if (injected == null) client.close()
        }
    }

    open suspend fun startInstaller(file: File, platform: AppUpdatePlatform? = null) {
        val resolvedPlatform =
            platform ?: appUpdatePlatformForOperatingSystem(HostPlatform.operatingSystem)
        when (resolvedPlatform) {
            AppUpdatePlatform.android -> _startAndroidInstaller(file)
            // PORT: the Windows (elevated PowerShell) and macOS (`open`)
            // launchers only ran on desktop.
            AppUpdatePlatform.windows, AppUpdatePlatform.macos, null ->
                throw UnsupportedOperationException(
                    "Installing updates is not supported on this platform.",
                )
        }
    }

    private suspend fun _startAndroidInstaller(file: File) {
        if (_pathExtension(file.path).lowercase() != "apk") {
            throw ReleaseInstallerException("下载的文件不是 Android 安装包")
        }
        val launcher = _androidInstallerLauncher ?: ::_invokeAndroidInstaller
        try {
            launcher(file.path)
        } catch (error: PlatformException) {
            throw ReleaseInstallerException(_androidInstallerErrorMessage(error))
        }
    }

    private suspend fun <T> _withClient(action: suspend (client: HttpClient) -> T): T {
        val injected = _httpClient
        if (injected != null) return action(injected)

        val client = HttpClient()
        try {
            return action(client)
        } finally {
            client.close()
        }
    }

    private fun _listUri(bucketUrl: String): Uri {
        val base = Uri.parse(bucketUrl)
        return base.replace(
            queryParameters =
                LinkedHashMap(base.queryParameters).apply {
                    put("list-type", "2")
                    put("prefix", "releases/")
                },
        )
    }

    private suspend fun _downloadTarget(update: AvailableAppUpdate): File {
        // PORT: desktop installers downloaded straight into the temporary
        // directory; only the Android branch is reachable here, since the app
        // only ever offers Android assets (see appUpdatePlatformForOperatingSystem).
        if (update.asset.platform != AppUpdatePlatform.android) {
            throw UnsupportedOperationException(
                "Installing updates is not supported on this platform.",
            )
        }
        val directoryProvider = _temporaryDirectoryProvider ?: { PathProvider.getTemporaryDirectory() }
        val temporaryDirectory = directoryProvider()
        val directory =
            File(
                "${temporaryDirectory.path}${HostPlatform.pathSeparator}" +
                    AndroidUpdateInstaller.updateDirectoryName,
            )
        withContext(Dispatchers.IO) {
            if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
                throw IOException("Creation failed, path = '${directory.path}'")
            }
        }
        _cleanupDownloadedInstallers(directory)
        return File(
            "${directory.path}${HostPlatform.pathSeparator}" +
                "GangChat_v${update.latestVersion}.apk",
        )
    }

    private suspend fun _cleanupDownloadedInstallers(directory: File) {
        withContext(Dispatchers.IO) {
            if (!directory.exists()) return@withContext

            for (entity in directory.listFiles().orEmpty()) {
                if (!entity.isFile) continue
                if (!_downloadedAndroidInstallerPattern.containsMatchIn(_pathBasename(entity.path))) {
                    continue
                }
                // A file that cannot be deleted is skipped; the new download
                // surfaces any conflict with its exact target file.
                entity.delete()
            }
        }
    }
}

private suspend fun _deleteFileQuietly(file: File?) {
    if (file == null) return
    try {
        withContext(Dispatchers.IO) { if (file.exists()) file.delete() }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
    }
}

private val _downloadedAndroidInstallerPattern = Regex("^GangChat_v\\d+\\.\\d+\\.\\d+\\.apk$")

fun appUpdatePlatformForOperatingSystem(operatingSystem: String): AppUpdatePlatform? {
    return when (operatingSystem.trim().lowercase()) {
        "windows" -> AppUpdatePlatform.windows
        "macos" -> AppUpdatePlatform.macos
        "android" -> AppUpdatePlatform.android
        else -> null
    }
}

private suspend fun _validateAndroidApkDownload(
    file: File,
    receivedBytes: Long,
    expectedBytes: Long?,
) {
    if (expectedBytes != null && receivedBytes != expectedBytes) {
        throw FormatException("Android 安装包下载不完整")
    }
    // Dart's `RandomAccessFile.read(4)`: up to four bytes, fewer at end of file.
    val header =
        withContext(Dispatchers.IO) {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(4)
                var count = 0
                while (count < buffer.size) {
                    val read = input.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    count += read
                }
                buffer.copyOf(count)
            }
        }
    if (header.size != 4 ||
        header[0] != 0x50.toByte() ||
        header[1] != 0x4B.toByte() ||
        header[2] != 0x03.toByte() ||
        header[3] != 0x04.toByte()
    ) {
        throw FormatException("下载的文件不是有效的 Android 安装包")
    }
}

private fun _pathBasename(path: String): String {
    val slash = path.lastIndexOf('/')
    val backslash = path.lastIndexOf('\\')
    val index = if (slash > backslash) slash else backslash
    return if (index < 0) path else path.substring(index + 1)
}

private fun _pathExtension(path: String): String {
    val basename = _pathBasename(path)
    val index = basename.lastIndexOf('.')
    return if (index < 0) "" else basename.substring(index + 1)
}

private suspend fun _invokeAndroidInstaller(path: String) {
    AndroidUpdateInstaller.instance.installApk(path)
}

private fun _androidInstallerErrorMessage(error: PlatformException): String {
    return when (error.code) {
        "permission_denied" -> "请允许 Gang Chat 安装未知应用后重试"
        "invalid_apk" -> "下载的 Android 安装包无效，请重新下载"
        "invalid_package" -> "下载的安装包不是 Gang Chat"
        "signature_mismatch" -> "安装包签名与当前应用不一致，无法直接更新"
        "installer_unavailable" -> "系统中没有可用的 Android 安装器"
        "install_in_progress" -> "已有更新安装流程正在进行"
        "notification_permission_required" -> "请开启通知权限，以便后台下载完成后提示安装"
        else -> "无法启动 Android 系统安装器"
    }
}
