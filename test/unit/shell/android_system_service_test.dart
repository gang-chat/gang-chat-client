import 'package:client/src/shell/android_system_service.dart';
import 'package:client/src/shell/external_uri_launcher.dart';
import 'package:client/src/shell/feedback_mail_service.dart';
import 'package:client/src/shell/file_selection_service.dart';
import 'package:client/src/shell/install_info_service.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

class _SupportedAndroidSystemService extends AndroidSystemService {
  const _SupportedAndroidSystemService();

  @override
  bool get isSupported => true;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('gang_chat/android_system');
  const android = _SupportedAndroidSystemService();
  late List<MethodCall> calls;

  setUp(() {
    calls = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          return switch (call.method) {
            'getInstalledAt' => '2026-07-25T04:05:06.000Z',
            'createDocument' => <String, Object?>{
              'uri': 'content://documents/report',
              'path': '/cache/document-staging/report.zip',
            },
            'notificationsEnabled' => true,
            'notificationPreferenceEnabled' => true,
            'requestNotificationPermission' => true,
            'requestUpdateNotificationPermission' => true,
            'requestBluetoothConnectPermission' => true,
            'getPushRegistration' => <String, Object?>{
              'provider': 'fcm',
              'installationId': 'install-1',
              'token': 'token-1',
              'enabled': true,
            },
            _ => null,
          };
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('Android shell launchers use native intents', () async {
    await const ExternalUriLauncher(
      androidSystemService: android,
    ).open(Uri.parse('https://ky-z.com'));
    await const FeedbackMailService(
      androidSystemService: android,
    ).openMailto(Uri.parse('mailto:gang-chat@outlook.com'));

    expect(calls.map((call) => call.method), ['openUri', 'openMailto']);
  });

  test('Android install time comes from PackageManager bridge', () async {
    final value = await const InstallInfoService(
      androidSystemService: android,
    ).readInstalledAt();

    expect(value, '2026-07-25T04:05:06.000Z');
    expect(calls.single.method, 'getInstalledAt');
  });

  test('Android save location keeps URI separate from staging path', () async {
    final service = const FileSelectionService(androidSystemService: android);
    final location = await service.getSaveLocation(suggestedName: 'report.zip');

    expect(location?.path, '/cache/document-staging/report.zip');
    expect(location?.documentUri, 'content://documents/report');
    expect(location?.requiresCommit, isTrue);

    await service.saveBytesToLocation(
      bytes: Uint8List.fromList([1, 2, 3]),
      location: location!,
      filename: 'report.zip',
    );
    expect(calls.last.method, 'writeDocumentBytes');
    expect(
      (calls.last.arguments as Map<Object?, Object?>)['uri'],
      'content://documents/report',
    );
    await service.discardLocation(location);
    expect(calls.last.method, 'discardDocumentFile');
    expect(calls.last.arguments, {
      'path': '/cache/document-staging/report.zip',
    });
  });

  test('notification bridge carries room content and unread badge', () async {
    await android.showRoomMessage(
      roomId: 'room-1',
      roomName: '测试房间',
      sender: 'Alice',
      body: '你好',
      unreadCount: 7,
      messageId: 'message-1',
    );
    await android.syncBadge(7);

    expect(calls[0].method, 'showRoomMessage');
    expect(calls[0].arguments, {
      'roomId': 'room-1',
      'roomName': '测试房间',
      'sender': 'Alice',
      'body': '你好',
      'unreadCount': 7,
      'messageId': 'message-1',
    });
    expect(calls[1].method, 'syncBadge');
    expect(calls[1].arguments, {'unreadCount': 7});
  });

  test(
    'Android audio routing permission is requested through the bridge',
    () async {
      expect(await android.requestBluetoothConnectPermission(), isTrue);
      expect(calls.single.method, 'requestBluetoothConnectPermission');
    },
  );

  test(
    'Android update notifications and background state use native bridge',
    () async {
      expect(await android.requestUpdateNotificationPermission(), isTrue);
      await android.setUpdateDownloadActive(true);
      await android.setUpdateDownloadActive(false);

      expect(calls.map((call) => call.method), [
        'requestUpdateNotificationPermission',
        'setUpdateDownloadActive',
        'setUpdateDownloadActive',
      ]);
      expect(calls[1].arguments, {'active': true});
      expect(calls[2].arguments, {'active': false});
    },
  );

  test('Android push registration is parsed from the native bridge', () async {
    final registration = await android.pushRegistration();

    expect(registration?.provider, 'fcm');
    expect(registration?.installationId, 'install-1');
    expect(registration?.token, 'token-1');
    expect(registration?.enabled, isTrue);
    expect(calls.single.method, 'getPushRegistration');
  });

  test('task removal callback waits for graceful cleanup completion', () async {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    final requests = <AndroidTaskRemovalRequest>[];
    final subscription = android.taskRemovalRequests.listen(requests.add);
    addTearDown(subscription.cancel);
    var nativeReplyReceived = false;

    final delivery = messenger.handlePlatformMessage(
      'gang_chat/android_system',
      const StandardMethodCodec().encodeMethodCall(
        const MethodCall('taskRemoved'),
      ),
      (_) => nativeReplyReceived = true,
    );
    await Future<void>.delayed(Duration.zero);

    expect(requests, hasLength(1));
    expect(nativeReplyReceived, isFalse);

    requests.single.complete();
    await delivery;
    await Future<void>.delayed(Duration.zero);
    expect(nativeReplyReceived, isTrue);
  });
}
