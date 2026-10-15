import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/config/host_platform.dart';
import 'package:client/src/shell/sparkle_updater.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('gang_chat/updater');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  test('is unavailable when the native updater is missing', () async {
    expect(await const SparkleUpdater().isAvailable(), isFalse);
  });

  test('is unavailable when the native side reports an error', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      throw PlatformException(code: 'updater_unavailable');
    });
    expect(await const SparkleUpdater().isAvailable(), isFalse);
  });

  test('reports the native answer on macOS only', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'isAvailable');
      return true;
    });
    expect(await const SparkleUpdater().isAvailable(), HostPlatform.isMacOS);
  });

  test('checks the appcast next to the configured installers', () async {
    MethodCall? received;
    messenger.setMockMethodCallHandler(channel, (call) async {
      received = call;
      return null;
    });
    await const SparkleUpdater().checkForUpdates(
      bucketUrl: 'https://os.ky-z.com:9000/gang-chat/',
      background: true,
    );
    expect(received?.method, 'checkForUpdates');
    expect(received?.arguments, <String, Object?>{
      'feedUrl': 'https://os.ky-z.com:9000/gang-chat/releases/appcast.xml',
      'background': true,
    });
  });
}
