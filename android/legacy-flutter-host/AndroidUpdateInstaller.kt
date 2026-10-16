package com.gangchat.client

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.security.MessageDigest

class AndroidUpdateInstaller(private val activity: Activity) {
    private data class ApkError(
        val code: String,
        val message: String,
    )

    private data class PendingInstall(
        val apk: File,
        val result: MethodChannel.Result,
    )

    private var pendingInstall: PendingInstall? = null
    private var waitingForInstallPermission = false
    private var waitingForDeferredInstallPermission = false
    private var skipNextDeferredInstallResume = false

    private val preferences by lazy {
        activity.getSharedPreferences(updatePreferencesName, Activity.MODE_PRIVATE)
    }

    fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (call.method != "installApk") {
            result.notImplemented()
            return
        }
        if (pendingInstall != null) {
            result.error(
                "install_in_progress",
                "An Android update install is already pending.",
                null,
            )
            return
        }

        val path = call.argument<String>("path")
        if (path.isNullOrBlank()) {
            result.error("invalid_apk", "The APK path is missing.", null)
            return
        }
        val apk = validateApk(path, result) ?: return

        if (!GangChatAppVisibility.isForeground) {
            deferInstaller(apk, result)
            return
        }

        if (needsInstallPermission()) {
            pendingInstall = PendingInstall(apk, result)
            waitingForInstallPermission = true
            try {
                activity.startActivityForResult(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}"),
                    ),
                    installPermissionRequestCode,
                )
            } catch (_: ActivityNotFoundException) {
                clearPendingInstall()?.result?.error(
                    "installer_unavailable",
                    "The system install permission page is unavailable.",
                    null,
                )
            } catch (error: Exception) {
                clearPendingInstall()?.result?.error(
                    "installer_unavailable",
                    error.message,
                    null,
                )
            }
            return
        }

        launchInstaller(apk, result)
    }

    fun onActivityResult(requestCode: Int): Boolean {
        if (requestCode != installPermissionRequestCode) return false
        if (pendingInstall != null) {
            finishInstallPermissionRequest()
        } else if (waitingForDeferredInstallPermission) {
            waitingForDeferredInstallPermission = false
            skipNextDeferredInstallResume = true
            if (!needsInstallPermission()) resumeDeferredInstall()
        }
        return true
    }

    fun onResume() {
        if (waitingForInstallPermission && pendingInstall != null) {
            finishInstallPermissionRequest()
            return
        }
        if (waitingForDeferredInstallPermission) {
            waitingForDeferredInstallPermission = false
            if (!needsInstallPermission()) resumeDeferredInstall()
            return
        }
        if (skipNextDeferredInstallResume) {
            skipNextDeferredInstallResume = false
            return
        }
        resumeDeferredInstall()
    }

    private fun finishInstallPermissionRequest() {
        val pending = clearPendingInstall() ?: return
        if (needsInstallPermission()) {
            pending.result.error(
                "permission_denied",
                "Permission to install unknown apps was not granted.",
                null,
            )
            return
        }
        launchInstaller(pending.apk, pending.result)
    }

    private fun clearPendingInstall(): PendingInstall? {
        waitingForInstallPermission = false
        val pending = pendingInstall
        pendingInstall = null
        return pending
    }

    private fun needsInstallPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
    }

    private fun validateApk(
        path: String,
        result: MethodChannel.Result,
    ): File? {
        val apk = canonicalUpdateApk(path)
        if (apk == null) {
            result.error(
                "invalid_apk",
                "The APK is outside the private update directory or unreadable.",
                null,
            )
            return null
        }

        val error = apkTrustError(apk)
        if (error != null) {
            result.error(error.code, error.message, null)
            return null
        }
        return apk
    }

    private fun canonicalUpdateApk(path: String): File? {
        val apk = try {
            File(path).canonicalFile
        } catch (_: Exception) {
            return null
        }
        val updateRoot = try {
            File(activity.cacheDir, updateDirectoryName).canonicalFile
        } catch (_: Exception) {
            return null
        }
        val allowedPrefix = updateRoot.path + File.separator
        if (!apk.path.startsWith(allowedPrefix) ||
            !apk.name.matches(apkFilenamePattern) ||
            !apk.isFile ||
            !apk.canRead() ||
            apk.length() <= 0
        ) {
            return null
        }
        return apk
    }

    private fun apkTrustError(apk: File): ApkError? {
        val archiveInfo = packageArchiveInfo(apk)
        if (archiveInfo == null) {
            return ApkError("invalid_apk", "Android could not parse the APK.")
        }
        if (archiveInfo.packageName != activity.packageName) {
            return ApkError(
                "invalid_package",
                "The APK belongs to a different application.",
            )
        }

        val installedInfo = installedPackageInfo()
        val installedSigners = installedInfo?.let(::signerDigests).orEmpty()
        val archiveSigners = signerDigests(archiveInfo)
        if (installedSigners.isEmpty() ||
            archiveSigners.isEmpty() ||
            installedSigners.intersect(archiveSigners).isEmpty()
        ) {
            return ApkError(
                "signature_mismatch",
                "The APK signing certificate does not match this installation.",
            )
        }
        return null
    }

    private fun launchInstaller(apk: File, result: MethodChannel.Result) {
        if (!GangChatAppVisibility.isForeground) {
            deferInstaller(apk, result)
            return
        }
        try {
            val intent = installerIntent(apk)
            GangChatNotifications.cancelUpdateReady(activity)
            activity.startActivity(intent)
            clearDeferredInstall()
            result.success(null)
        } catch (_: ActivityNotFoundException) {
            result.error(
                "installer_unavailable",
                "No Android package installer is available.",
                null,
            )
        } catch (error: Exception) {
            result.error("invalid_apk", error.message, null)
        }
    }

    private fun deferInstaller(apk: File, result: MethodChannel.Result) {
        rememberDeferredInstall(apk)
        GangChatNotifications.showUpdateReady(
            activity,
            versionFromFilename(apk.name),
        )
        result.success(null)
    }

    private fun resumeDeferredInstall() {
        if (!GangChatAppVisibility.isForeground || pendingInstall != null) return
        val apk = deferredInstallApk() ?: return
        if (needsInstallPermission()) {
            waitingForDeferredInstallPermission = true
            try {
                activity.startActivityForResult(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}"),
                    ),
                    installPermissionRequestCode,
                )
            } catch (_: Exception) {
                waitingForDeferredInstallPermission = false
            }
            return
        }

        try {
            val intent = installerIntent(apk)
            GangChatNotifications.cancelUpdateReady(activity)
            activity.startActivity(intent)
            clearDeferredInstall()
        } catch (_: Exception) {
            // Keep the validated APK pending so a later foreground resume can retry.
        }
    }

    private fun rememberDeferredInstall(apk: File) {
        preferences.edit().putString(pendingApkPathKey, apk.path).apply()
    }

    private fun deferredInstallApk(): File? {
        val path = preferences.getString(pendingApkPathKey, null)?.trim().orEmpty()
        if (path.isEmpty()) return null
        val apk = canonicalUpdateApk(path)
        if (apk == null || apkTrustError(apk) != null) {
            clearDeferredInstall()
            return null
        }
        return apk
    }

    private fun clearDeferredInstall() {
        preferences.edit().remove(pendingApkPathKey).apply()
    }

    private fun installerIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, apkMimeType)
            clipData = ClipData.newRawUri("Gang Chat update", uri)
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // ColorOS and some other OEM installers hand the request from a
        // visible proxy activity to another component in the installer
        // package. Granting that package explicitly keeps the private APK
        // readable throughout its asynchronous prepare/verify phase.
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(
                    PackageManager.MATCH_DEFAULT_ONLY.toLong(),
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            activity.packageManager.resolveActivity(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY,
            )
        } ?: throw ActivityNotFoundException()
        val installerPackage = installer.activityInfo.packageName
        activity.grantUriPermission(
            installerPackage,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        return intent.setPackage(installerPackage)
    }

    private fun versionFromFilename(filename: String): String =
        apkFilenamePattern.matchEntire(filename)?.groupValues?.getOrNull(1).orEmpty()

    @Suppress("DEPRECATION")
    private fun packageArchiveInfo(apk: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return activity.packageManager.getPackageArchiveInfo(apk.path, flags)
    }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return try {
            activity.packageManager.getPackageInfo(activity.packageName, flags)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            info.signatures
        }
        return signatures
            ?.map { signature ->
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte) }
            }
            ?.toSet()
            .orEmpty()
    }

    companion object {
        const val channelName = "gang_chat/app_update"
        private const val installPermissionRequestCode = 4107
        private const val updateDirectoryName = "release-updates"
        private const val updatePreferencesName = "gang_chat_updates"
        private const val pendingApkPathKey = "pending_apk_path"
        private const val apkMimeType = "application/vnd.android.package-archive"
        private val apkFilenamePattern =
            Regex(
                "^GangChat_v(\\d+\\.\\d+\\.\\d+)\\.apk$",
                RegexOption.IGNORE_CASE,
            )
    }
}
