import 'dart:async';
import 'dart:js_interop';
import 'dart:js_interop_unsafe';
import 'dart:typed_data';

import 'package:web/web.dart' as web;

import 'browser_types.dart';

void openBrowserUrl(String url) {
  web.window.open(url, '_blank', 'noopener');
}

/// A mailto link opened in a new tab would leave an empty tab behind once the
/// mail client takes over, so it is followed in place.
void openBrowserMailto(String url) {
  web.window.location.href = url;
}

/// Speaks [text] with the browser's speech synthesis. Completes with false
/// when the browser has no voice for it or speech fails.
Future<bool> speakInBrowser(
  String text, {
  required double volume,
  String lang = 'zh-CN',
}) {
  final done = Completer<bool>();
  try {
    final utterance = web.SpeechSynthesisUtterance(text)
      ..lang = lang
      ..volume = volume.clamp(0.0, 1.0);
    utterance.onend = ((web.Event _) {
      if (!done.isCompleted) done.complete(true);
    }).toJS;
    utterance.onerror = ((web.Event _) {
      if (!done.isCompleted) done.complete(false);
    }).toJS;
    web.window.speechSynthesis.speak(utterance);
  } catch (_) {
    if (!done.isCompleted) done.complete(false);
  }
  return done.future;
}

/// Browsers accept only PNG images on the async clipboard.
Future<bool> writeBrowserClipboardImage(Uint8List pngBytes) async {
  try {
    final blob = web.Blob(
      <JSAny>[pngBytes.toJS].toJS,
      web.BlobPropertyBag(type: 'image/png'),
    );
    final data = JSObject()..setProperty('image/png'.toJS, blob);
    await web.window.navigator.clipboard
        .write(<web.ClipboardItem>[web.ClipboardItem(data)].toJS)
        .toDart;
    return true;
  } catch (_) {
    return false;
  }
}

/// The first image on the clipboard, or null when there is none or the user
/// declines the browser's clipboard permission prompt.
Future<BrowserFile?> readBrowserClipboardImage() async {
  try {
    final items = await web.window.navigator.clipboard.read().toDart;
    for (final item in items.toDart) {
      for (final type in item.types.toDart.map((type) => type.toDart)) {
        if (!type.startsWith('image/')) continue;
        final blob = await item.getType(type).toDart;
        final bytes = (await blob.arrayBuffer().toDart).toDart.asUint8List();
        final extension = type.substring('image/'.length).split('+').first;
        return BrowserFile(
          name: 'clipboard-image.$extension',
          mimeType: type,
          size: bytes.length,
          read: () async => bytes,
        );
      }
    }
  } catch (_) {
    // No permission, no clipboard access, or nothing readable.
  }
  return null;
}

StreamController<BrowserFileDrop>? _fileDrops;

/// Files dropped anywhere on the page. Listening also stops the browser from
/// navigating away to open a file dropped outside a drop target.
Stream<BrowserFileDrop> browserFileDrops() {
  final existing = _fileDrops;
  if (existing != null) return existing.stream;
  final controller = StreamController<BrowserFileDrop>.broadcast();
  _fileDrops = controller;

  bool carriesFiles(web.DragEvent event) {
    final types = event.dataTransfer?.types.toDart ?? const <JSString>[];
    return types.any((type) => type.toDart == 'Files');
  }

  web.window.addEventListener(
    'dragover',
    ((web.DragEvent event) {
      if (!carriesFiles(event)) return;
      event.preventDefault();
      event.dataTransfer?.dropEffect = 'copy';
    }).toJS,
  );
  web.window.addEventListener(
    'drop',
    ((web.DragEvent event) {
      if (!carriesFiles(event)) return;
      event.preventDefault();
      final list = event.dataTransfer?.files;
      if (list == null) return;
      final files = <BrowserFile>[];
      for (var index = 0; index < list.length; index++) {
        final file = list.item(index);
        if (file == null) continue;
        files.add(
          BrowserFile(
            name: file.name,
            mimeType: file.type,
            size: file.size,
            read: () async =>
                (await file.arrayBuffer().toDart).toDart.asUint8List(),
          ),
        );
      }
      if (files.isEmpty) return;
      controller.add(
        BrowserFileDrop(
          files: files,
          x: event.clientX.toDouble(),
          y: event.clientY.toDouble(),
        ),
      );
    }).toJS,
  );
  return controller.stream;
}

bool Function(List<BrowserFile> files, String? text)? _pasteHandler;
bool _pasteListenerInstalled = false;

/// Routes the page's paste events to [handler], which sees the pasted files
/// and plain text and returns true to consume the paste (the browser then
/// inserts nothing). Must decide synchronously: the event is gone after it.
void setBrowserPasteHandler(
  bool Function(List<BrowserFile> files, String? text)? handler,
) {
  _pasteHandler = handler;
  if (_pasteListenerInstalled) return;
  _pasteListenerInstalled = true;
  web.window.addEventListener(
    'paste',
    ((web.ClipboardEvent event) {
      final handler = _pasteHandler;
      final data = event.clipboardData;
      if (handler == null || data == null) return;
      final files = <BrowserFile>[];
      final list = data.files;
      for (var index = 0; index < list.length; index++) {
        final file = list.item(index);
        if (file == null) continue;
        files.add(
          BrowserFile(
            name: file.name,
            mimeType: file.type,
            size: file.size,
            read: () async =>
                (await file.arrayBuffer().toDart).toDart.asUint8List(),
          ),
        );
      }
      final text = data.getData('text/plain');
      if (handler(files, text.isEmpty ? null : text)) {
        event.preventDefault();
        event.stopPropagation();
      }
    }).toJS,
    // Capture, so the decision is made before the page's text field sees it.
    true.toJS,
  );
}
