import 'dart:io';

import 'android_system_service.dart';
import '../config/host_platform.dart';

class FeedbackMailDraft {
  const FeedbackMailDraft({
    required this.from,
    required this.to,
    required this.subject,
    required this.body,
  });

  final String from;
  final String to;
  final String subject;
  final String body;

  Uri toMailtoUri() {
    return Uri(
      scheme: 'mailto',
      path: to,
      queryParameters: {'subject': subject, 'body': body, 'from': from},
    );
  }
}

class FeedbackMailService {
  const FeedbackMailService({
    this.androidSystemService = const AndroidSystemService(),
  });

  final AndroidSystemService androidSystemService;

  Future<void> openDraft(FeedbackMailDraft draft) {
    return openMailto(draft.toMailtoUri());
  }

  Future<void> openMailto(Uri uri) async {
    final value = uri.toString();
    if (androidSystemService.isSupported) {
      await androidSystemService.openMailto(uri);
      return;
    }
    if (HostPlatform.isWindows) {
      await Process.start('rundll32', ['url.dll,FileProtocolHandler', value]);
      return;
    }
    if (HostPlatform.isMacOS) {
      await Process.start('open', [value]);
      return;
    }
    if (HostPlatform.isLinux) {
      await Process.start('xdg-open', [value]);
      return;
    }
    throw UnsupportedError('当前平台不支持打开邮件客户端');
  }
}
