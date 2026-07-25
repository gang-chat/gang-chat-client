import 'dart:io';

import '../app/settings_about.dart';
import 'android_system_service.dart';

class InstallInfoService {
  const InstallInfoService({
    this.fileName = gangChatClientInstallInfoFileName,
    this.androidSystemService = const AndroidSystemService(),
  });

  final String fileName;
  final AndroidSystemService androidSystemService;

  Future<String?> readInstalledAt() async {
    try {
      if (androidSystemService.isSupported) {
        return androidSystemService.installedAt();
      }
      final executableDir = File(Platform.resolvedExecutable).parent;
      final infoFile = File(
        '${executableDir.path}${Platform.pathSeparator}$fileName',
      );
      if (!await infoFile.exists()) return null;
      return infoFile.readAsString();
    } catch (_) {
      return null;
    }
  }
}
