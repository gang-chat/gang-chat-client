import 'package:client/src/shell/desktop_window_controller.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('gang_chat/tray.attention_test');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() {
    messenger.setMockMethodCallHandler(channel, null);
  });

  test(
    'Windows message attention uses the existing native tray channel',
    () async {
      final calls = <MethodCall>[];
      messenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call);
        return null;
      });
      final controller = _SupportedDesktopWindowController(
        trayChannel: channel,
      );

      await controller.requestMessageAttention();

      expect(calls, hasLength(1));
      expect(calls.single.method, 'requestAttention');
      expect(calls.single.arguments, isNull);
    },
  );

  test('message attention ignores native channel failures', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      throw PlatformException(code: 'unavailable');
    });
    final controller = _SupportedDesktopWindowController(trayChannel: channel);

    await expectLater(controller.requestMessageAttention(), completes);
  });

  test('message attention stays disabled outside supported desktop', () async {
    final calls = <MethodCall>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return null;
    });
    final controller = DesktopWindowController(trayChannel: channel);

    await controller.requestMessageAttention();

    expect(calls, isEmpty);
  });

  test('full-screen media brackets the Windows power request', () async {
    final calls = <MethodCall>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return null;
    });
    final controller = _SupportedDesktopWindowController(trayChannel: channel);

    await controller.setFullScreenMediaPlaybackActive(true);
    await controller.setFullScreenMediaPlaybackActive(false);

    expect(calls, hasLength(2));
    expect(calls[0].method, 'setMediaPlaybackActive');
    expect(calls[0].arguments, isTrue);
    expect(calls[1].method, 'setMediaPlaybackActive');
    expect(calls[1].arguments, isFalse);
  });

  test('full-screen media power request ignores channel failures', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      throw PlatformException(code: 'unavailable');
    });
    final controller = _SupportedDesktopWindowController(trayChannel: channel);

    await expectLater(
      controller.setFullScreenMediaPlaybackActive(true),
      completes,
    );
  });
}

class _SupportedDesktopWindowController extends DesktopWindowController {
  _SupportedDesktopWindowController({required super.trayChannel});

  @override
  bool get supportsWindowManagement => true;

  @override
  bool get supportsNativeTray => true;

  @override
  bool get supportsWindowsPowerRequests => true;
}
