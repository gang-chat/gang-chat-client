import 'dart:io';

import 'android_system_service.dart';

class ExternalUriLauncher {
  const ExternalUriLauncher({
    this.androidSystemService = const AndroidSystemService(),
  });

  final AndroidSystemService androidSystemService;

  Future<void> open(Uri uri) async {
    final value = uri.toString();
    if (androidSystemService.isSupported) {
      await androidSystemService.openUri(uri);
      return;
    }
    if (Platform.isWindows) {
      await Process.start('rundll32', ['url.dll,FileProtocolHandler', value]);
      return;
    }
    if (Platform.isMacOS) {
      await Process.start('open', [value]);
      return;
    }
    if (Platform.isLinux) {
      await Process.start('xdg-open', [value]);
      return;
    }
    throw UnsupportedError('当前平台不支持打开链接');
  }
}
