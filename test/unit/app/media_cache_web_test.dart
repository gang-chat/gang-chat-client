@TestOn('browser')
library;

import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:client/src/app/file_downloads_controller.dart';
import 'package:client/src/app/media_cache_controller.dart';
import 'package:client/src/ui/cached_asset_image.dart';

// Run with: flutter test --platform chrome test/unit/app/media_cache_web_test.dart
//
// The web has no file system, so media is downloaded into memory there; the
// native on-disk cache is covered by media_cache_controller_test.dart.
void main() {
  MediaCacheRequest request({int? expectedBytes}) {
    return MediaCacheRequest.tryFromUrl(
      url: 'https://os.example.test/gang-chat/avatars/u1.png',
      filename: 'u1.png',
      mimeType: 'image/png',
      expectedBytes: expectedBytes,
    )!;
  }

  test(
    'downloads once and serves repeat and concurrent requests from memory',
    () async {
      var requests = 0;
      final controller = MediaCacheController(
        httpClientFactory: () => MockClient((_) async {
          requests += 1;
          return http.Response.bytes([1, 2, 3, 4], 200);
        }),
      );

      final concurrent = await Future.wait([
        controller.getOrDownloadBytes(request: request(expectedBytes: 4)),
        controller.getOrDownloadBytes(request: request(expectedBytes: 4)),
      ]);
      final later = await controller.readBytes(
        request: request(expectedBytes: 4),
      );

      expect(concurrent.first, [1, 2, 3, 4]);
      expect(concurrent.last, [1, 2, 3, 4]);
      expect(later, [1, 2, 3, 4]);
      expect(requests, 1);
    },
  );

  test('a failed download is reported and retried next time', () async {
    var status = 404;
    var requests = 0;
    final controller = MediaCacheController(
      httpClientFactory: () => MockClient((_) async {
        requests += 1;
        return http.Response.bytes([9], status);
      }),
    );

    await expectLater(
      controller.getOrDownloadBytes(request: request()),
      throwsStateError,
    );
    status = 200;
    expect(await controller.getOrDownloadBytes(request: request()), [9]);
    expect(requests, 2);
  });

  test('rejects a body that does not match the recorded file size', () async {
    final controller = MediaCacheController(
      httpClientFactory: () =>
          MockClient((_) async => http.Response.bytes([1, 2], 200)),
    );

    await expectLater(
      controller.getOrDownloadBytes(request: request(expectedBytes: 4)),
      throwsStateError,
    );
  });

  test('reports download progress to the transfer', () async {
    final controller = MediaCacheController(
      httpClientFactory: () => MockClient.streaming(
        (_, _) async => http.StreamedResponse(
          Stream.fromIterable([
            [1, 2],
            [3, 4],
          ]),
          200,
        ),
      ),
    );
    final transfer = FileDownloadsController().createDownload(
      totalBytes: 4,
      destinationPath: 'report.pdf',
    );
    final progress = <int>[];

    final bytes = await controller.getOrDownloadBytes(
      request: request(expectedBytes: 4),
      transfer: transfer,
      onProgress: ({required sentBytes, required totalBytes}) =>
          progress.add(sentBytes),
    );

    expect(bytes, [1, 2, 3, 4]);
    expect(progress, [0, 2, 4]);
    expect(transfer.sentBytes, 4);
    expect(transfer.downloadClient, isNull);
  });

  test('cancelling a download reports cancellation, not a failure', () async {
    final body = StreamController<List<int>>();
    final controller = MediaCacheController(
      httpClientFactory: () => MockClient.streaming(
        (_, _) async => http.StreamedResponse(body.stream, 200),
      ),
    );
    final downloads = FileDownloadsController();
    final transfer = downloads.createDownload(
      totalBytes: 4,
      destinationPath: 'report.pdf',
    );

    final result = controller.getOrDownloadBytes(
      request: request(expectedBytes: 4),
      transfer: transfer,
      onProgress: ({required sentBytes, required totalBytes}) {
        if (sentBytes > 0) downloads.cancel(transfer);
      },
    );
    body.add([1, 2]);
    body.add([3, 4]);
    unawaited(body.close());

    await expectLater(result, throwsA(isA<MediaCacheCancelledException>()));
  });

  testWidgets('CachedAssetImage shows media from memory on the web', (
    tester,
  ) async {
    // A 1x1 transparent PNG.
    final png = base64Decode(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
    );
    final controller = MediaCacheController(
      httpClientFactory: () =>
          MockClient((_) async => http.Response.bytes(png, 200)),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: CachedAssetImage(
          url: 'https://os.example.test/gang-chat/avatars/u1.png',
          cache: controller,
          width: 10,
          height: 10,
        ),
      ),
    );
    await tester.runAsync(() => Future<void>.delayed(Duration.zero));
    await tester.pump();

    final image = tester.widget<Image>(find.byType(Image));
    expect(image.image, isA<MemoryImage>());
  });
}
