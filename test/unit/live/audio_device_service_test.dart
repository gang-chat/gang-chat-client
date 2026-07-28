import 'package:client/src/live/audio_device_service.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('gang_chat/audio_devices');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() {
    messenger.setMockMethodCallHandler(channel, null);
  });

  test('only native Windows uses the system-only audio path', () {
    expect(systemAudioOnlyForPlatform(isWeb: false, isWindows: true), isTrue);
    expect(
      systemAudioOnlyForPlatform(isWeb: false, isWindows: false),
      isFalse,
      reason: 'macOS keeps its existing CoreAudio/WebRTC merge',
    );
    expect(
      systemAudioOnlyForPlatform(isWeb: true, isWindows: false),
      isFalse,
      reason: 'web/Android keep their existing WebRTC implementation',
    );
  });

  test('Windows audio enumeration uses only SystemAudioDevices', () async {
    final calls = <String>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      return switch (call.method) {
        'enumerateInputs' => [
          {'deviceId': 'system_mic', 'label': 'System Mic'},
        ],
        'enumerateOutputs' => [
          {'deviceId': 'system_speaker', 'label': 'System Speaker'},
        ],
        _ => null,
      };
    });

    const service = LiveAudioDeviceService(systemAudioOnly: true);
    final devices = await service.enumerateDevices();

    expect(calls, containsAll(['enumerateInputs', 'enumerateOutputs']));
    expect(calls, hasLength(2));
    expect(devices.map((device) => '${device.kind}:${device.deviceId}'), [
      'audioinput:system_mic',
      'audiooutput:system_speaker',
    ]);
  });

  test('Windows topology event refreshes the system endpoint list', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      return switch (call.method) {
        'enumerateInputs' => [
          {'deviceId': 'hotplug_mic', 'label': 'Hotplug Mic'},
        ],
        'enumerateOutputs' => [
          {'deviceId': 'hotplug_speaker', 'label': 'Hotplug Speaker'},
        ],
        _ => null,
      };
    });

    const service = LiveAudioDeviceService(systemAudioOnly: true);
    final changed = service.devicesChanged.first;
    await Future<void>.delayed(Duration.zero);
    await messenger.handlePlatformMessage(
      'gang_chat/audio_devices',
      const StandardMethodCodec().encodeMethodCall(
        const MethodCall('audioDevicesChanged'),
      ),
      (_) {},
    );

    final devices = await changed;
    expect(devices.map((device) => device.deviceId), [
      'hotplug_mic',
      'hotplug_speaker',
    ]);
  });
}
