import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';

import '../app/app_update.dart';

typedef ReleaseDownloadProgress =
    void Function({required int receivedBytes, int? totalBytes});

typedef ReleaseInstallerProcessRunner =
    Future<ProcessResult> Function(String executable, List<String> arguments);

typedef ReleaseTemporaryDirectoryProvider = Future<Directory> Function();
typedef ReleaseAndroidInstallerLauncher = Future<void> Function(String path);

class ReleaseDownloadCancelledException implements Exception {
  const ReleaseDownloadCancelledException();

  @override
  String toString() => '版本下载已取消';
}

class ReleaseInstallerException implements Exception {
  const ReleaseInstallerException(this.message);

  final String message;

  @override
  String toString() => message;
}

class ReleaseDownloadCancellationToken {
  bool _cancelled = false;
  bool _active = false;
  http.Client? _client;
  File? _partialFile;
  Completer<void>? _completion;

  bool get isCancelled => _cancelled;

  void cancel() {
    _cancelled = true;
    _client?.close();
  }

  Future<void> cancelAndDeletePartialFile() async {
    cancel();
    final completion = _completion;
    if (_active && completion != null && !completion.isCompleted) {
      try {
        await completion.future.timeout(const Duration(seconds: 2));
      } catch (_) {
        // A still-open file can fail deletion on Windows; try anyway below.
      }
    }
    await _deleteFileQuietly(_partialFile);
  }

  void _bind(http.Client client, File partialFile) {
    _client = client;
    _partialFile = partialFile;
    _active = true;
    _completion = Completer<void>();
    if (_cancelled) client.close();
  }

  void _throwIfCancelled() {
    if (_cancelled) throw const ReleaseDownloadCancelledException();
  }

  void _complete() {
    _client = null;
    _active = false;
    final completion = _completion;
    if (completion != null && !completion.isCompleted) {
      completion.complete();
    }
  }
}

class ReleaseUpdateService {
  const ReleaseUpdateService({
    http.Client? httpClient,
    ReleaseInstallerProcessRunner? processRunner,
    ReleaseTemporaryDirectoryProvider? temporaryDirectoryProvider,
    ReleaseAndroidInstallerLauncher? androidInstallerLauncher,
  }) : _httpClient = httpClient,
       _processRunner = processRunner,
       _temporaryDirectoryProvider = temporaryDirectoryProvider,
       _androidInstallerLauncher = androidInstallerLauncher;

  final http.Client? _httpClient;
  final ReleaseInstallerProcessRunner? _processRunner;
  final ReleaseTemporaryDirectoryProvider? _temporaryDirectoryProvider;
  final ReleaseAndroidInstallerLauncher? _androidInstallerLauncher;

  Future<AvailableAppUpdate?> checkForUpdate({
    required String bucketUrl,
    required String currentVersion,
    required AppUpdatePlatform platform,
  }) async {
    final response = await _withClient((client) {
      return client.get(_listUri(bucketUrl));
    });
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw HttpException(
        '读取版本列表失败（状态码 ${response.statusCode}）',
        uri: _listUri(bucketUrl),
      );
    }

    final assets = parseReleaseAssetsFromS3List(response.body);
    final asset = latestReleaseAssetForPlatform(assets, platform);
    if (asset == null ||
        !isNewerAppVersion(
          currentVersion: currentVersion,
          latestVersion: asset.version,
        )) {
      return null;
    }

    return AvailableAppUpdate(
      currentVersion: currentVersion,
      latestVersion: asset.version,
      asset: asset,
      downloadUrl: Uri.parse(releaseAssetUrl(bucketUrl, asset.key)),
    );
  }

  Future<File> downloadUpdate(
    AvailableAppUpdate update, {
    ReleaseDownloadProgress? onProgress,
    ReleaseDownloadCancellationToken? cancellationToken,
  }) async {
    final request = http.Request('GET', update.downloadUrl);
    final injected = _httpClient;
    final client = injected ?? http.Client();
    File? file;
    try {
      file = await _downloadTarget(update);
      cancellationToken?._bind(client, file);
      cancellationToken?._throwIfCancelled();
      final response = await client.send(request);
      cancellationToken?._throwIfCancelled();
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw HttpException(
          '版本下载失败（状态码 ${response.statusCode}）',
          uri: update.downloadUrl,
        );
      }

      final sink = file.openWrite();
      var received = 0;
      final contentLength = response.contentLength;
      final total = contentLength != null && contentLength >= 0
          ? contentLength
          : null;
      onProgress?.call(receivedBytes: received, totalBytes: total);
      try {
        await for (final chunk in response.stream) {
          cancellationToken?._throwIfCancelled();
          received += chunk.length;
          sink.add(chunk);
          onProgress?.call(receivedBytes: received, totalBytes: total);
        }
        cancellationToken?._throwIfCancelled();
      } finally {
        await sink.close();
      }
      if (update.asset.platform == AppUpdatePlatform.android) {
        await _validateAndroidApkDownload(
          file,
          receivedBytes: received,
          expectedBytes: total,
        );
      }
      return file;
    } catch (error) {
      await _deleteFileQuietly(file);
      if (error is ReleaseDownloadCancelledException ||
          cancellationToken?.isCancelled == true) {
        throw const ReleaseDownloadCancelledException();
      }
      rethrow;
    } finally {
      cancellationToken?._complete();
      if (injected == null) client.close();
    }
  }

  Future<void> startInstaller(File file, {AppUpdatePlatform? platform}) async {
    final resolvedPlatform =
        platform ??
        appUpdatePlatformForOperatingSystem(Platform.operatingSystem);
    switch (resolvedPlatform) {
      case AppUpdatePlatform.windows:
        await _startWindowsInstaller(file);
        return;
      case AppUpdatePlatform.macos:
        await Process.start('open', [
          file.path,
        ], mode: ProcessStartMode.detached);
        return;
      case AppUpdatePlatform.android:
        await _startAndroidInstaller(file);
        return;
      case null:
        throw UnsupportedError(
          'Installing updates is not supported on this platform.',
        );
    }
  }

  Future<void> _startWindowsInstaller(File file) async {
    final arguments = _windowsElevatedInstallerArguments(file.path);
    final runner = _processRunner ?? _runProcess;
    final result = await runner('powershell.exe', arguments);
    if (result.exitCode == 0) return;

    throw ProcessException(
      'powershell.exe',
      arguments,
      _processResultMessage(result),
      result.exitCode,
    );
  }

  Future<void> _startAndroidInstaller(File file) async {
    if (_pathExtension(file.path).toLowerCase() != 'apk') {
      throw const ReleaseInstallerException('下载的文件不是 Android 安装包');
    }
    final launcher = _androidInstallerLauncher ?? _invokeAndroidInstaller;
    try {
      await launcher(file.path);
    } on PlatformException catch (error) {
      throw ReleaseInstallerException(_androidInstallerErrorMessage(error));
    }
  }

  Future<T> _withClient<T>(
    Future<T> Function(http.Client client) action,
  ) async {
    final injected = _httpClient;
    if (injected != null) return action(injected);

    final client = http.Client();
    try {
      return await action(client);
    } finally {
      client.close();
    }
  }

  Uri _listUri(String bucketUrl) {
    final base = Uri.parse(bucketUrl);
    return base.replace(
      queryParameters: {
        ...base.queryParameters,
        'list-type': '2',
        'prefix': 'releases/',
      },
    );
  }

  Future<File> _downloadTarget(AvailableAppUpdate update) async {
    final directoryProvider =
        _temporaryDirectoryProvider ?? getTemporaryDirectory;
    final temporaryDirectory = await directoryProvider();
    final directory = update.asset.platform == AppUpdatePlatform.android
        ? Directory(
            '${temporaryDirectory.path}${Platform.pathSeparator}'
            'release-updates',
          )
        : temporaryDirectory;
    if (!await directory.exists()) {
      await directory.create(recursive: true);
    }
    await _cleanupDownloadedInstallers(
      directory,
      platform: update.asset.platform,
    );
    final extension = switch (update.asset.platform) {
      AppUpdatePlatform.windows => 'exe',
      AppUpdatePlatform.macos => 'dmg',
      AppUpdatePlatform.android => 'apk',
    };
    return File(
      '${directory.path}${Platform.pathSeparator}'
      'GangChat_v${update.latestVersion}.$extension',
    );
  }

  Future<void> _cleanupDownloadedInstallers(
    Directory directory, {
    required AppUpdatePlatform platform,
  }) async {
    if (!await directory.exists()) return;

    final installerPattern = platform == AppUpdatePlatform.android
        ? _downloadedAndroidInstallerPattern
        : _downloadedDesktopInstallerPattern;
    await for (final entity in directory.list(followLinks: false)) {
      if (entity is! File) continue;
      if (!installerPattern.hasMatch(_pathBasename(entity.path))) {
        continue;
      }
      try {
        await entity.delete();
      } catch (_) {
        // A currently running installer may be locked by Windows; skip it and
        // let the new download surface any conflict with its exact target file.
      }
    }
  }
}

Future<void> _deleteFileQuietly(File? file) async {
  if (file == null) return;
  try {
    if (await file.exists()) await file.delete();
  } catch (_) {}
}

final _downloadedDesktopInstallerPattern = RegExp(
  r'^GangChat_v\d+\.\d+\.\d+\.(exe|dmg)$',
);
final _downloadedAndroidInstallerPattern = RegExp(
  r'^GangChat_v\d+\.\d+\.\d+\.apk$',
);

AppUpdatePlatform? appUpdatePlatformForOperatingSystem(String operatingSystem) {
  return switch (operatingSystem.trim().toLowerCase()) {
    'windows' => AppUpdatePlatform.windows,
    'macos' => AppUpdatePlatform.macos,
    'android' => AppUpdatePlatform.android,
    _ => null,
  };
}

Future<void> _validateAndroidApkDownload(
  File file, {
  required int receivedBytes,
  required int? expectedBytes,
}) async {
  if (expectedBytes != null && receivedBytes != expectedBytes) {
    throw const FormatException('Android 安装包下载不完整');
  }
  final input = await file.open();
  try {
    final header = await input.read(4);
    if (header.length != 4 ||
        header[0] != 0x50 ||
        header[1] != 0x4B ||
        header[2] != 0x03 ||
        header[3] != 0x04) {
      throw const FormatException('下载的文件不是有效的 Android 安装包');
    }
  } finally {
    await input.close();
  }
}

String _pathBasename(String path) {
  final slash = path.lastIndexOf('/');
  final backslash = path.lastIndexOf(r'\');
  final index = slash > backslash ? slash : backslash;
  return index < 0 ? path : path.substring(index + 1);
}

String _pathExtension(String path) {
  final basename = _pathBasename(path);
  final index = basename.lastIndexOf('.');
  return index < 0 ? '' : basename.substring(index + 1);
}

Future<ProcessResult> _runProcess(String executable, List<String> arguments) {
  return Process.run(executable, arguments);
}

List<String> _windowsElevatedInstallerArguments(String installerPath) {
  return [
    '-NoProfile',
    '-NonInteractive',
    '-ExecutionPolicy',
    'Bypass',
    '-Command',
    'Start-Process -FilePath '
        '${_powershellSingleQuoted(installerPath)} -Verb RunAs',
  ];
}

String _powershellSingleQuoted(String value) {
  return "'${value.replaceAll("'", "''")}'";
}

String _processResultMessage(ProcessResult result) {
  final stderr = result.stderr?.toString().trim() ?? '';
  if (stderr.isNotEmpty) return stderr;

  final stdout = result.stdout?.toString().trim() ?? '';
  if (stdout.isNotEmpty) return stdout;

  return '安装程序启动失败';
}

Future<void> _invokeAndroidInstaller(String path) {
  return const MethodChannel(
    'gang_chat/app_update',
  ).invokeMethod<void>('installApk', {'path': path});
}

String _androidInstallerErrorMessage(PlatformException error) {
  return switch (error.code) {
    'permission_denied' => '请允许 Gang Chat 安装未知应用后重试',
    'invalid_apk' => '下载的 Android 安装包无效，请重新下载',
    'invalid_package' => '下载的安装包不是 Gang Chat',
    'signature_mismatch' => '安装包签名与当前应用不一致，无法直接更新',
    'installer_unavailable' => '系统中没有可用的 Android 安装器',
    'install_in_progress' => '已有更新安装流程正在进行',
    'notification_permission_required' => '请开启通知权限，以便后台下载完成后提示安装',
    _ => '无法启动 Android 系统安装器',
  };
}
