import 'dart:typed_data';

import 'browser_types.dart';

// Native builds never call these; the shell services check kIsWeb first.

void openBrowserUrl(String url) {
  throw UnsupportedError('Only available in a browser.');
}

void openBrowserMailto(String url) {
  throw UnsupportedError('Only available in a browser.');
}

Future<bool> speakInBrowser(
  String text, {
  required double volume,
  String lang = 'zh-CN',
}) async => false;

Future<bool> writeBrowserClipboardImage(Uint8List pngBytes) async => false;

Future<BrowserFile?> readBrowserClipboardImage() async => null;

Stream<BrowserFileDrop> browserFileDrops() => const Stream.empty();

void setBrowserPasteHandler(
  bool Function(List<BrowserFile> files, String? text)? handler,
) {}
