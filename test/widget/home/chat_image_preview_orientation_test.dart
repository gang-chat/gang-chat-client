import 'dart:io';
import 'dart:ui' as dart_ui;

import 'package:client/src/app/media_cache_controller.dart';
import 'package:client/src/home/chat_image_preview.dart';
import 'package:client/src/shell/android_display_rotation_service.dart';
import 'package:client/src/shell/app_orientation_controller.dart';
import 'package:client/src/ui/ui.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  testWidgets(
    'Android image preview allows landscape and restores portrait on close',
    (tester) async {
      tester.view.physicalSize = const Size(400, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final requests = <List<DeviceOrientation>>[];
      var displayQuarterTurns = 0;
      final controller = AppOrientationController(
        platform: TargetPlatform.android,
        logicalViewSize: () => const Size(400, 800),
        setPreferredOrientations: (orientations) async {
          requests.add(List<DeviceOrientation>.of(orientations));
        },
      );

      await tester.pumpWidget(
        _PreviewTestHost(
          platform: TargetPlatform.android,
          orientationController: controller,
          backdropCapture: _portraitBackdrop,
          displayRotationReader: () async => displayQuarterTurns,
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));

      expect(requests, <List<DeviceOrientation>>[
        AppOrientationController.fullScreenMedia,
      ]);
      expect(_previewRoute(tester).opaque, isTrue);
      expect(_previewScaffold(tester).backgroundColor, Colors.transparent);
      final frozenBackdrop = tester.widget<RawImage>(
        find.byKey(const ValueKey('chat-image-preview-frozen-backdrop')),
      );
      expect(frozenBackdrop.image!.width, 200);
      expect(frozenBackdrop.image!.height, 400);
      expect(frozenBackdrop.fit, BoxFit.fill);
      expect(
        tester.getSize(
          find.byKey(const ValueKey('chat-image-preview-frozen-backdrop')),
        ),
        const Size(400, 800),
      );
      expect(_frozenBackdropRotation(tester).quarterTurns, 0);
      for (final button in tester.widgetList<ButtonIcon>(
        find.byType(ButtonIcon),
      )) {
        expect(button.backgroundColor, const Color(0x66000000));
        expect(button.borderColor, isNull);
      }
      expect(find.byType(ButtonIcon), findsNWidgets(4));

      displayQuarterTurns = 1;
      tester.view.physicalSize = const Size(800, 400);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 160));
      expect(
        find.byKey(const ValueKey('chat-image-preview-frozen-backdrop')),
        findsOneWidget,
      );
      expect(
        tester.getSize(
          find.byKey(const ValueKey('chat-image-preview-frozen-backdrop')),
        ),
        const Size(400, 800),
      );
      expect(_frozenBackdropRotation(tester).quarterTurns, 3);
      expect(
        tester.getSize(
          find.byKey(
            const ValueKey('chat-image-preview-frozen-backdrop-frame'),
          ),
        ),
        const Size(800, 400),
      );
      expect(find.byType(ButtonIcon), findsNWidgets(4));

      await tester.binding.handlePopRoute();
      await tester.pump();

      expect(requests, <List<DeviceOrientation>>[
        AppOrientationController.fullScreenMedia,
        AppOrientationController.portraitOnly,
      ]);
      expect(find.byIcon(Icons.close_rounded), findsOneWidget);

      displayQuarterTurns = 0;
      tester.view.physicalSize = const Size(400, 800);
      await tester.pumpAndSettle();
      expect(find.byIcon(Icons.close_rounded), findsNothing);

      expect(requests, <List<DeviceOrientation>>[
        AppOrientationController.fullScreenMedia,
        AppOrientationController.portraitOnly,
        AppOrientationController.portraitOnly,
      ]);
    },
  );

  testWidgets('Windows preview uses full area and mouse reveals controls', (
    tester,
  ) async {
    var calls = 0;
    var captures = 0;
    var rotationReads = 0;
    final controller = AppOrientationController(
      platform: TargetPlatform.windows,
      setPreferredOrientations: (_) async => calls += 1,
    );

    await tester.pumpWidget(
      _PreviewTestHost(
        platform: TargetPlatform.windows,
        orientationController: controller,
        backdropCapture: () async {
          captures += 1;
          return _portraitBackdrop();
        },
        displayRotationReader: () async {
          rotationReads += 1;
          return 0;
        },
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    expect(_previewRoute(tester).opaque, isFalse);
    expect(_previewScaffold(tester).backgroundColor, Colors.transparent);
    for (final button in tester.widgetList<ButtonIcon>(
      find.byType(ButtonIcon),
    )) {
      expect(button.backgroundColor, const Color(0x66000000));
      expect(button.borderColor, isNull);
    }
    expect(find.byType(ButtonIcon), findsNWidgets(4));
    expect(
      tester.getSize(
        find.byKey(const ValueKey('chat-image-preview-url-image')),
      ),
      const Size(800, 600),
    );

    await tester.pump(const Duration(milliseconds: 3100));
    expect(find.byType(ButtonIcon), findsNothing);

    final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await mouse.addPointer(location: const Offset(20, 20));
    await mouse.moveTo(const Offset(120, 120));
    await tester.pump();
    expect(find.byType(ButtonIcon), findsNWidgets(4));
    await mouse.removePointer();
    await tester.tap(find.byIcon(Icons.close_rounded));
    await tester.pumpAndSettle();

    expect(calls, 0);
    expect(captures, 0);
    expect(rotationReads, 0);
  });

  testWidgets(
    'Android tablet uses the wide preview path without phone orientation locks',
    (tester) async {
      tester.view.physicalSize = const Size(1280, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      var orientationCalls = 0;
      var captures = 0;
      var rotationReads = 0;
      final controller = AppOrientationController(
        platform: TargetPlatform.android,
        logicalViewSize: () => const Size(1280, 800),
        setPreferredOrientations: (_) async => orientationCalls += 1,
      );

      await tester.pumpWidget(
        _PreviewTestHost(
          platform: TargetPlatform.android,
          orientationController: controller,
          backdropCapture: () async {
            captures += 1;
            return _portraitBackdrop();
          },
          displayRotationReader: () async {
            rotationReads += 1;
            return 0;
          },
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));

      expect(_previewRoute(tester).opaque, isFalse);
      expect(
        find.byKey(const ValueKey('chat-image-preview-frozen-backdrop')),
        findsNothing,
      );
      expect(captures, 0);
      expect(rotationReads, 0);
      expect(orientationCalls, 0);

      await tester.tap(find.byIcon(Icons.close_rounded));
      await tester.pumpAndSettle();
    },
  );

  testWidgets(
    'Android uses full area, resets image, and reveals timed controls',
    (tester) async {
      tester.view.physicalSize = const Size(400, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final cacheDirectory = (await tester.runAsync(
        () => Directory.systemTemp.createTemp('gang-chat-preview-test-'),
      ))!;
      addTearDown(() async {
        if (await cacheDirectory.exists()) {
          await cacheDirectory.delete(recursive: true);
        }
      });
      final pngBytes = (await tester.runAsync(
        () => File(
          '${Directory.current.path}${Platform.pathSeparator}assets'
          '${Platform.pathSeparator}branding${Platform.pathSeparator}'
          'auth_brand_icon.png',
        ).readAsBytes(),
      ))!;
      final mediaCache = MediaCacheController(
        httpClientFactory: () => MockClient(
          (_) async => http.Response.bytes(
            pngBytes,
            200,
            headers: const {'content-type': 'image/png'},
          ),
        ),
        cacheDirectoryProvider: () async => cacheDirectory,
      );
      final controller = AppOrientationController(
        platform: TargetPlatform.android,
        logicalViewSize: () => const Size(400, 800),
        setPreferredOrientations: (_) async {},
      );

      await tester.pumpWidget(
        _PreviewTestHost(
          platform: TargetPlatform.android,
          orientationController: controller,
          backdropCapture: _portraitBackdrop,
          imageUrl: 'https://example.invalid/preview.png',
          mediaCache: mediaCache,
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pump();
      await _pumpUntilFound(
        tester,
        find.byKey(const ValueKey('chat-image-preview-image-transform')),
      );

      expect(find.byType(ButtonIcon), findsNWidgets(4));
      expect(
        tester.getSize(find.byKey(const ValueKey('chat-image-preview-viewer'))),
        const Size(400, 800),
      );
      expect(_previewImageScale(tester), 1);
      await _zoomPreview(tester);
      expect(_previewImageScale(tester), greaterThan(1));

      tester.view.physicalSize = const Size(800, 400);
      await tester.pumpAndSettle();

      expect(find.byType(ButtonIcon), findsNWidgets(4));
      expect(
        tester.getSize(find.byKey(const ValueKey('chat-image-preview-viewer'))),
        const Size(800, 400),
      );
      expect(_previewImageScale(tester), 1);

      await tester.pump(const Duration(milliseconds: 3100));
      expect(find.byType(ButtonIcon), findsNothing);
      await tester.tap(find.byKey(const ValueKey('chat-image-preview-viewer')));
      await tester.pump();
      expect(find.byType(ButtonIcon), findsNWidgets(4));

      await _zoomPreview(tester);
      expect(_previewImageScale(tester), greaterThan(1));

      tester.view.physicalSize = const Size(400, 800);
      await tester.pumpAndSettle();

      expect(find.byType(ButtonIcon), findsNWidgets(4));
      expect(
        tester.getSize(find.byKey(const ValueKey('chat-image-preview-viewer'))),
        const Size(400, 800),
      );
      expect(_previewImageScale(tester), 1);
      await tester.tap(find.byIcon(Icons.close_rounded));
      await tester.pumpAndSettle();
    },
  );

  testWidgets('macOS keeps the existing persistent preview controls', (
    tester,
  ) async {
    var calls = 0;
    var captures = 0;
    final controller = AppOrientationController(
      platform: TargetPlatform.macOS,
      setPreferredOrientations: (_) async => calls += 1,
    );

    await tester.pumpWidget(
      _PreviewTestHost(
        platform: TargetPlatform.macOS,
        orientationController: controller,
        backdropCapture: () async {
          captures += 1;
          return _portraitBackdrop();
        },
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(find.byType(ButtonIcon), findsNWidgets(4));
    await tester.pump(const Duration(seconds: 4));
    expect(find.byType(ButtonIcon), findsNWidgets(4));
    await tester.tap(find.byIcon(Icons.close_rounded));
    await tester.pumpAndSettle();

    expect(calls, 0);
    expect(captures, 0);
  });

  testWidgets('Android system back restores portrait after image preview', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(400, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final requests = <List<DeviceOrientation>>[];
    final controller = AppOrientationController(
      platform: TargetPlatform.android,
      logicalViewSize: () => const Size(400, 800),
      setPreferredOrientations: (orientations) async {
        requests.add(List<DeviceOrientation>.of(orientations));
      },
    );

    await tester.pumpWidget(
      _PreviewTestHost(
        platform: TargetPlatform.android,
        orientationController: controller,
        backdropCapture: _portraitBackdrop,
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.close_rounded), findsNothing);

    expect(requests, <List<DeviceOrientation>>[
      AppOrientationController.fullScreenMedia,
      AppOrientationController.portraitOnly,
      AppOrientationController.portraitOnly,
    ]);
  });

  testWidgets('preview action scope can capture the current portrait UI', (
    tester,
  ) async {
    late BuildContext previewContext;
    await tester.pumpWidget(
      MaterialApp(
        home: ChatImagePreviewActionsScope(
          actions: ChatImagePreviewActions.disabled(),
          child: Builder(
            builder: (context) {
              previewContext = context;
              return const ColoredBox(color: Colors.blue);
            },
          ),
        ),
      ),
    );
    await tester.pump();

    final snapshot = await ChatImagePreviewActionsScope.captureBackdrop(
      previewContext,
    );

    expect(snapshot, isNotNull);
    expect(snapshot?.logicalSize, const Size(800, 600));
    snapshot?.dispose();
  });
}

class _PreviewTestHost extends StatelessWidget {
  const _PreviewTestHost({
    required this.platform,
    required this.orientationController,
    this.backdropCapture,
    this.imageUrl = 'invalid-preview-url',
    this.mediaCache,
    this.displayRotationReader,
  });

  final TargetPlatform platform;
  final AppOrientationController orientationController;
  final ImagePreviewBackdropCapture? backdropCapture;
  final String imageUrl;
  final MediaCacheController? mediaCache;
  final AndroidDisplayRotationReader? displayRotationReader;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      theme: uiTheme().copyWith(platform: platform),
      home: Builder(
        builder: (context) => Scaffold(
          body: TextButton(
            onPressed: () => showChatImagePreview(
              context,
              imageUrl: imageUrl,
              suggestedName: 'preview.png',
              actions: ChatImagePreviewActions(
                onDownload: (_, _) async {},
                onSaveAs: (_, _) async {},
                onCopyToClipboard: (_) async {},
                mediaCache: mediaCache ?? MediaCacheController(),
              ),
              orientationController: orientationController,
              backdropCapture: backdropCapture,
              displayRotationReader: displayRotationReader,
            ),
            child: const Text('open'),
          ),
        ),
      ),
    );
  }
}

RotatedBox _frozenBackdropRotation(WidgetTester tester) {
  final finder = find.byWidgetPredicate(
    (widget) =>
        widget is RotatedBox &&
        widget.key is ValueKey<String> &&
        (widget.key! as ValueKey<String>).value.startsWith(
          'chat-image-preview-frozen-backdrop-rotation-',
        ),
  );
  expect(finder, findsOneWidget);
  return tester.widget<RotatedBox>(finder);
}

Future<ImagePreviewBackdropSnapshot> _portraitBackdrop() async {
  final recorder = dart_ui.PictureRecorder();
  final canvas = dart_ui.Canvas(recorder);
  canvas.drawRect(
    const Rect.fromLTWH(0, 0, 200, 400),
    dart_ui.Paint()..color = Colors.blue,
  );
  return ImagePreviewBackdropSnapshot(
    image: await recorder.endRecording().toImage(200, 400),
    logicalSize: const Size(400, 800),
  );
}

ModalRoute<dynamic> _previewRoute(WidgetTester tester) {
  return ModalRoute.of(tester.element(find.byIcon(Icons.close_rounded)))!;
}

Scaffold _previewScaffold(WidgetTester tester) {
  final finder = find.ancestor(
    of: find.byIcon(Icons.close_rounded),
    matching: find.byType(Scaffold),
  );
  expect(finder, findsOneWidget);
  return tester.widget<Scaffold>(finder);
}

double _previewImageScale(WidgetTester tester) {
  final transform = tester.widget<Transform>(
    find.byKey(const ValueKey('chat-image-preview-image-transform')),
  );
  return transform.transform.getMaxScaleOnAxis();
}

Future<void> _zoomPreview(WidgetTester tester) async {
  final viewer = find.byKey(const ValueKey('chat-image-preview-viewer'));
  await tester.sendEventToBinding(
    PointerScrollEvent(
      position: tester.getCenter(viewer),
      scrollDelta: const Offset(0, -120),
    ),
  );
  await tester.pump();
}

Future<void> _pumpUntilFound(WidgetTester tester, Finder finder) async {
  for (var attempt = 0; attempt < 40 && finder.evaluate().isEmpty; attempt++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 10)),
    );
    await tester.pump();
  }
  expect(finder, findsOneWidget);
}
