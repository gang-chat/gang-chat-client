// Regenerates every platform app icon from the in-app [BrandMark], so the
// launcher/dock icon always matches the mark drawn inside the app.
//
//   flutter test tool/generate_app_icons_test.dart
//
// Writes macOS AppIcon PNGs, the Windows app_icon.ico (also used by the NSIS
// installer), Android launcher mipmaps, the raster brand logo and the bundled
// superuser avatar. The SVG logos in assets/branding are hand-kept copies of
// the same geometry.
import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:client/src/ui/brand_mark.dart';
import 'package:client/src/ui/tokens.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';

/// Logical canvas every icon is laid out on before scaling to pixels.
const double _canvas = 1024;

/// A tile shape on the [_canvas]: the lime tile's edge length and corner
/// radius. The mark is drawn inside it exactly as [BrandMark] draws it.
typedef _Tile = ({double body, double radius});

/// Full-bleed tile with the same 30% corner radius as the in-app mark.
const _Tile _fullTile = (body: _canvas, radius: _canvas * 0.3);

/// Apple's macOS icon grid: an 824pt rounded tile centred on 1024pt.
const _Tile _macTile = (body: 824, radius: 185.4);

/// Square tile; the app clips this asset to a circle itself.
const _Tile _squareTile = (body: _canvas, radius: 0);

void main() {
  testWidgets('generate app icons', (tester) async {
    tester.view.physicalSize = const Size.square(_canvas);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    Future<Uint8List> render(_Tile tile, int pixels) =>
        _render(tester, tile, pixels);

    for (final size in const [16, 32, 64, 128, 256, 512, 1024]) {
      _write(
        'macos/Runner/Assets.xcassets/AppIcon.appiconset/app_icon_$size.png',
        await render(_macTile, size),
      );
    }

    const androidDensities = {
      'mdpi': 48,
      'hdpi': 72,
      'xhdpi': 96,
      'xxhdpi': 144,
      'xxxhdpi': 192,
    };
    for (final entry in androidDensities.entries) {
      _write(
        'android/app/src/main/res/mipmap-${entry.key}/ic_launcher.png',
        await render(_fullTile, entry.value),
      );
    }

    final icoImages = <int, Uint8List>{};
    for (final size in const [16, 24, 32, 48, 64, 128, 256]) {
      icoImages[size] = await render(_fullTile, size);
    }
    _write('windows/runner/resources/app_icon.ico', _encodeIco(icoImages));

    _write('assets/branding/gang_chat.png', await render(_fullTile, 1024));

    const avatarBase = 36;
    _write(
      'assets/branding/auth_brand_icon.png',
      await render(_squareTile, avatarBase),
    );
    for (final ratio in const ['1.25', '1.5', '1.75', '2.0', '3.0', '4.0']) {
      _write(
        'assets/branding/${ratio}x/auth_brand_icon.png',
        await render(_squareTile, (avatarBase * double.parse(ratio)).round()),
      );
    }
  });
}

Future<Uint8List> _render(WidgetTester tester, _Tile tile, int pixels) async {
  final key = GlobalKey();
  await tester.pumpWidget(
    Directionality(
      textDirection: TextDirection.ltr,
      child: Center(
        child: RepaintBoundary(
          key: key,
          child: SizedBox.square(
            dimension: _canvas,
            child: Center(
              // The clip sets the outer shape (and drops BrandMark's glow);
              // the same-colour fill hides BrandMark's own tile corners when
              // the clip radius is tighter than its 30%.
              child: ClipRRect(
                borderRadius: BorderRadius.circular(tile.radius),
                child: ColoredBox(
                  color: UiColors.accentStrong,
                  child: BrandMark(size: tile.body),
                ),
              ),
            ),
          ),
        ),
      ),
    ),
  );
  final boundary =
      key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
  final bytes = await tester.runAsync(() async {
    final image = await boundary.toImage(pixelRatio: pixels / _canvas);
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    image.dispose();
    return data!.buffer.asUint8List();
  });
  return bytes!;
}

void _write(String path, List<int> bytes) {
  File(path).writeAsBytesSync(bytes);
  // ignore: avoid_print
  print('wrote $path (${bytes.length} bytes)');
}

/// Packs PNG images into a Windows .ico (PNG entries, Vista and later).
Uint8List _encodeIco(Map<int, Uint8List> pngsBySize) {
  final entries = pngsBySize.entries.toList()
    ..sort((a, b) => a.key.compareTo(b.key));
  final header = ByteData(6 + 16 * entries.length)
    ..setUint16(0, 0, Endian.little)
    ..setUint16(2, 1, Endian.little)
    ..setUint16(4, entries.length, Endian.little);
  var offset = header.lengthInBytes;
  for (var i = 0; i < entries.length; i++) {
    final size = entries[i].key;
    final png = entries[i].value;
    final at = 6 + 16 * i;
    header
      ..setUint8(at, size >= 256 ? 0 : size)
      ..setUint8(at + 1, size >= 256 ? 0 : size)
      ..setUint8(at + 2, 0)
      ..setUint8(at + 3, 0)
      ..setUint16(at + 4, 1, Endian.little)
      ..setUint16(at + 6, 32, Endian.little)
      ..setUint32(at + 8, png.length, Endian.little)
      ..setUint32(at + 12, offset, Endian.little);
    offset += png.length;
  }
  final out = BytesBuilder(copy: false)..add(header.buffer.asUint8List());
  for (final entry in entries) {
    out.add(entry.value);
  }
  return out.toBytes();
}
