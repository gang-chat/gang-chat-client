import 'dart:typed_data';

/// A file handed over by the browser (dropped or pasted). Browsers expose no
/// paths, only contents, which [read] loads on demand.
class BrowserFile {
  const BrowserFile({
    required this.name,
    required this.mimeType,
    required this.size,
    required this.read,
  });

  final String name;
  final String mimeType;
  final int size;
  final Future<Uint8List> Function() read;
}

/// Files dropped onto the page at window position ([x], [y]), in logical
/// pixels.
class BrowserFileDrop {
  const BrowserFileDrop({
    required this.files,
    required this.x,
    required this.y,
  });

  final List<BrowserFile> files;
  final double x;
  final double y;
}
