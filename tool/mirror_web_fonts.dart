// Mirrors the web build's fallback fonts next to the app.
//
// Flutter web downloads glyphs its bundled fonts lack (Chinese, emoji,
// symbols) from fonts.gstatic.com on demand. That host is blocked in mainland
// China, where Chinese text then renders as empty boxes. This script copies
// the fallback font files the build references into build/web/fonts/gstatic/
// and points the loader at them (fontFallbackBaseUrl).
//
// If any download fails, nothing is pointed at the partial copy: the app keeps
// loading fonts from gstatic, exactly as without this script.
//
// Usage (after `flutter build web`): dart tool/mirror_web_fonts.dart [build/web]
import 'dart:async';
import 'dart:io';

/// Fallback font families to host. Others (Japanese, Korean, Traditional
/// Chinese, rare scripts) still come from gstatic when needed.
const _families = {
  'notosanssc', // Simplified Chinese
  'notocoloremoji',
  'notosanssymbols',
  'notosanssymbols2',
  'roboto',
};
const _gstaticBase = 'https://fonts.gstatic.com/s/';
const _mirrorDir = 'fonts/gstatic/';
const _loaderCall = '_flutter.loader.load({';

Future<void> main(List<String> args) async {
  final buildDir = Directory(args.isEmpty ? 'build/web' : args.first);
  final mainJs = File('${buildDir.path}/main.dart.js');
  final bootstrap = File('${buildDir.path}/flutter_bootstrap.js');
  if (!mainJs.existsSync() || !bootstrap.existsSync()) {
    _fail('run `flutter build web` first: ${buildDir.path} is incomplete');
  }

  final paths = _referencedFontPaths(mainJs.readAsStringSync());
  if (paths.isEmpty) {
    _fail('no fallback font paths found in main.dart.js; the format changed?');
  }
  stdout.writeln('Mirroring ${paths.length} fallback font files...');

  final client = HttpClient()..connectionTimeout = const Duration(seconds: 20);
  final failures = <String>[];
  var bytes = 0;
  try {
    final queue = paths.toList();
    Future<void> worker() async {
      while (queue.isNotEmpty) {
        final path = queue.removeLast();
        try {
          final size = await _download(client, path, buildDir);
          bytes += size;
        } catch (error) {
          failures.add('$path: $error');
        }
      }
    }

    await Future.wait(List.generate(8, (_) => worker()));
  } finally {
    client.close(force: true);
  }

  if (failures.isNotEmpty) {
    stderr.writeln(
      'warning: ${failures.length} font downloads failed; the app keeps '
      'using fonts.gstatic.com:',
    );
    for (final failure in failures.take(5)) {
      stderr.writeln('  $failure');
    }
    return;
  }

  final source = bootstrap.readAsStringSync();
  if (source.contains('fontFallbackBaseUrl')) {
    stdout.writeln('flutter_bootstrap.js already sets fontFallbackBaseUrl.');
  } else if (_loaderCall.allMatches(source).length != 1) {
    _fail('expected exactly one `$_loaderCall` in flutter_bootstrap.js');
  } else {
    bootstrap.writeAsStringSync(
      source.replaceFirst(
        _loaderCall,
        '$_loaderCall\n  config: { fontFallbackBaseUrl: "$_mirrorDir" },',
      ),
    );
  }
  stdout.writeln(
    'Mirrored ${paths.length} files '
    '(${(bytes / (1 << 20)).toStringAsFixed(1)} MB) into $_mirrorDir',
  );
}

/// Paths like `notosanssc/v37/<hash>.118.woff2`, relative to gstatic's /s/.
Set<String> _referencedFontPaths(String js) {
  final pattern = RegExp(
    r'"([a-z0-9]+)/(v\d+)/([A-Za-z0-9_-]+(?:\.\d+)?\.(?:woff2|ttf))"',
  );
  return {
    for (final match in pattern.allMatches(js))
      if (_families.contains(match.group(1)))
        '${match.group(1)}/${match.group(2)}/${match.group(3)}',
  };
}

Future<int> _download(HttpClient client, String path, Directory build) async {
  final target = File('${build.path}/$_mirrorDir$path');
  if (target.existsSync() && target.lengthSync() > 0) {
    return target.lengthSync();
  }
  final request = await client.getUrl(Uri.parse('$_gstaticBase$path'));
  final response = await request.close().timeout(const Duration(seconds: 60));
  if (response.statusCode != HttpStatus.ok) {
    await response.drain<void>();
    throw HttpException('HTTP ${response.statusCode}');
  }
  target.parent.createSync(recursive: true);
  final temporary = File('${target.path}.part');
  final sink = temporary.openWrite();
  try {
    await response.pipe(sink);
  } catch (_) {
    if (temporary.existsSync()) temporary.deleteSync();
    rethrow;
  }
  temporary.renameSync(target.path);
  return target.lengthSync();
}

Never _fail(String message) {
  stderr.writeln('error: $message');
  exit(1);
}
