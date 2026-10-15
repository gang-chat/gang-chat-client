@TestOn('browser')
library;

import 'dart:js_interop';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:web/web.dart' as web;

import 'package:client/src/shell/browser/browser_bridge.dart';

// Run with: flutter test --platform chrome test/unit/shell/browser_bridge_web_test.dart
web.DataTransfer _transfer({String? text, web.File? file}) {
  final transfer = web.DataTransfer();
  if (text != null) transfer.setData('text/plain', text);
  if (file != null) transfer.items.add(file);
  return transfer;
}

web.File _png() {
  return web.File(
    <JSAny>[
      Uint8List.fromList([0x89, 0x50, 0x4E, 0x47]).toJS,
    ].toJS,
    'image.png',
    web.FilePropertyBag(type: 'image/png'),
  );
}

void main() {
  tearDown(() => setBrowserPasteHandler(null));

  test('pasted files and text reach the handler, which can consume them', () {
    List<BrowserFile>? seenFiles;
    String? seenText;
    setBrowserPasteHandler((files, text) {
      seenFiles = files;
      seenText = text;
      return true;
    });

    final event = web.ClipboardEvent(
      'paste',
      web.ClipboardEventInit(
        clipboardData: _transfer(text: 'hello', file: _png()),
        cancelable: true,
      ),
    );
    web.window.dispatchEvent(event);

    expect(seenFiles, hasLength(1));
    expect(seenFiles!.single.name, 'image.png');
    expect(seenFiles!.single.mimeType, 'image/png');
    expect(seenText, 'hello');
    expect(event.defaultPrevented, isTrue);
  });

  test('a paste the handler declines keeps its default behaviour', () {
    setBrowserPasteHandler((files, text) => false);
    final event = web.ClipboardEvent(
      'paste',
      web.ClipboardEventInit(
        clipboardData: _transfer(text: 'plain text'),
        cancelable: true,
      ),
    );
    web.window.dispatchEvent(event);
    expect(event.defaultPrevented, isFalse);
  });

  test('dropped files are reported with their position', () async {
    final drops = browserFileDrops().first;
    final transfer = _transfer(file: _png());

    final over = web.DragEvent(
      'dragover',
      web.DragEventInit(dataTransfer: transfer, cancelable: true),
    );
    web.window.dispatchEvent(over);
    // Without this the browser would navigate away to open the file.
    expect(over.defaultPrevented, isTrue);

    web.window.dispatchEvent(
      web.DragEvent(
        'drop',
        web.DragEventInit(
          dataTransfer: transfer,
          clientX: 40,
          clientY: 60,
          cancelable: true,
        ),
      ),
    );
    final drop = await drops;
    expect(drop.files.single.name, 'image.png');
    expect(await drop.files.single.read(), [0x89, 0x50, 0x4E, 0x47]);
    expect(drop.x, 40);
    expect(drop.y, 60);
  });
}
