package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Uri

enum class AppUpdatePlatform { windows, macos, android }

fun shouldTerminateApplicationAfterInstallerLaunch(
    platform: AppUpdatePlatform,
): Boolean = platform == AppUpdatePlatform.windows

/**
 * macOS hands the DMG download to the browser when Sparkle isn't configured.
 * A DMG downloaded by the sandboxed app is marked "created by a sandboxed
 * app", and macOS refuses to launch an app copied out of it.
 */
fun downloadsUpdateInBrowser(platform: AppUpdatePlatform): Boolean =
    platform == AppUpdatePlatform.macos

fun updateDownloadConfirmationBody(platform: AppUpdatePlatform): String {
    if (platform == AppUpdatePlatform.android) {
        return "将下载新版安装包。下载完成后会打开系统安装界面；如果 Gang Chat 在后台，" +
            "将弹出“点击安装”通知。若未开启通知权限，重新打开 Gang Chat 时也会弹出安装界面。" +
            "安装完成前请勿退出 Gang Chat。"
    }
    if (downloadsUpdateInBrowser(platform)) {
        return "将在浏览器中下载新版安装包。下载完成后打开 DMG，" +
            "把 GangChat 拖到“应用程序”文件夹替换旧版本即可。"
    }
    return "将下载新版安装包。下载完成后，Gang Chat 会退出当前程序并启动安装程序。"
}

/** Sparkle's update feed, published next to the installers. */
fun sparkleAppcastUrl(bucketUrl: String): String =
    releaseAssetUrl(bucketUrl, "releases/appcast.xml")

class ReleaseAsset(
    val key: String,
    val version: String,
    val platform: AppUpdatePlatform,
    val releasedAt: DateTime? = null,
)

class AvailableAppUpdate(
    val currentVersion: String,
    val latestVersion: String,
    val asset: ReleaseAsset,
    val downloadUrl: Uri,
    val releaseNotes: String? = null,
)

fun parseReleaseAssetsFromS3List(xmlText: String): List<ReleaseAsset> {
    return _s3Objects(xmlText)
        .mapNotNull { s3Object ->
            parseReleaseAssetKey(s3Object.key, releasedAt = s3Object.lastModified)
        }
}

private val _releaseAssetKeyPattern =
    Regex(
        "^releases/GangChat_v(\\d+\\.\\d+\\.\\d+)\\.(exe|dmg|apk)$",
        RegexOption.IGNORE_CASE,
    )

fun parseReleaseAssetKey(key: String, releasedAt: DateTime? = null): ReleaseAsset? {
    val match = _releaseAssetKeyPattern.find(key.trim()) ?: return null

    val extension = match.groupValues[2].lowercase()
    return ReleaseAsset(
        key = key.trim(),
        version = match.groupValues[1],
        platform =
            when (extension) {
                "exe" -> AppUpdatePlatform.windows
                "dmg" -> AppUpdatePlatform.macos
                else -> AppUpdatePlatform.android
            },
        releasedAt = releasedAt,
    )
}

fun latestReleaseAssetForPlatform(
    assets: Iterable<ReleaseAsset>,
    platform: AppUpdatePlatform,
): ReleaseAsset? {
    var latest: ReleaseAsset? = null
    for (asset in assets.filter { asset -> asset.platform == platform }) {
        if (latest == null ||
            compareAppVersions(asset.version, latest.version) > 0
        ) {
            latest = asset
        }
    }
    return latest
}

fun isNewerAppVersion(
    currentVersion: String,
    latestVersion: String,
): Boolean {
    return compareAppVersions(currentVersion, latestVersion) < 0
}

fun releaseAssetUrl(bucketUrl: String, key: String): String {
    val normalizedBucket =
        if (bucketUrl.endsWith("/")) {
            bucketUrl.substring(0, bucketUrl.length - 1)
        } else {
            bucketUrl
        }
    val encodedKey = key.split('/').joinToString("/") { segment -> Uri.encodeComponent(segment) }
    return "$normalizedBucket/$encodedKey"
}

fun releaseTimeLabel(releasedAt: DateTime?): String {
    return officialDateTimeLabel(releasedAt)
}

fun releaseNotesLabel(releaseNotes: String?): String {
    val trimmed = releaseNotes?.trim() ?: ""
    return if (trimmed.isEmpty()) "无" else trimmed
}

private val _s3ContentsPattern =
    Regex(
        "<Contents>(.*?)</Contents>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

private fun _s3Objects(xmlText: String): List<_S3Object> {
    return _s3ContentsPattern
        .findAll(xmlText)
        .mapNotNull { match -> _s3ObjectFromContents(match.groupValues[1]) }
        .toList()
}

private fun _s3ObjectFromContents(contents: String): _S3Object? {
    val key = _firstXmlValue(contents, "Key")?.trim()
    if (key == null || key.isEmpty()) return null

    return _S3Object(
        key = key,
        lastModified =
            DateTime.tryParse(
                _firstXmlValue(contents, "LastModified")?.trim() ?: "",
            ),
    )
}

private fun _firstXmlValue(xmlText: String, tagName: String): String? {
    val match =
        Regex(
            "<$tagName>(.*?)</$tagName>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        ).find(xmlText) ?: return null
    return _decodeXmlText(match.groupValues[1])
}

private class _S3Object(val key: String, val lastModified: DateTime? = null)

private fun _decodeXmlText(value: String): String {
    return value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}
