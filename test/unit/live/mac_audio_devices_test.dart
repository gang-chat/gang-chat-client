import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/live/system_audio_devices.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channelName = 'gang_chat/audio_devices';
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() {
    messenger.setMockMethodCallHandler(const MethodChannel(channelName), null);
  });

  test('currentInputDeviceId returns the native default input id', () async {
    messenger.setMockMethodCallHandler(const MethodChannel(channelName), (
      call,
    ) async {
      if (call.method == 'getDefaultInputDeviceId') return 'mic_2';
      return null;
    });

    final service = SystemAudioDevices(supported: true, sharedEvents: false);
    addTearDown(service.dispose);

    expect(await service.currentInputDeviceId(), 'mic_2');
    expect(await service.currentDeviceId(), 'mic_2');
  });

  test('currentOutputDeviceId returns the native default output id', () async {
    messenger.setMockMethodCallHandler(const MethodChannel(channelName), (
      call,
    ) async {
      if (call.method == 'getDefaultOutputDeviceId') return 'speaker_2';
      return null;
    });

    final service = SystemAudioDevices(supported: true, sharedEvents: false);
    addTearDown(service.dispose);

    expect(await service.currentOutputDeviceId(), 'speaker_2');
  });

  test('current device queries swallow native failures', () async {
    messenger.setMockMethodCallHandler(const MethodChannel(channelName), (
      call,
    ) async {
      throw PlatformException(code: 'unavailable');
    });

    final service = SystemAudioDevices(supported: true, sharedEvents: false);
    addTearDown(service.dispose);

    expect(await service.currentInputDeviceId(), isNull);
    expect(await service.currentOutputDeviceId(), isNull);
  });

  test('enumerateInputs maps the native list to audioinput devices', () async {
    messenger.setMockMethodCallHandler(const MethodChannel(channelName), (
      call,
    ) async {
      if (call.method == 'enumerateInputs') {
        return [
          {'deviceId': '87', 'label': '内建麦克风', 'isDefault': true},
          {'deviceId': '92', 'label': 'USB Mic', 'isDefault': false},
          // Entries without a deviceId are dropped.
          {'deviceId': '', 'label': 'broken', 'isDefault': false},
        ];
      }
      return null;
    });

    final service = SystemAudioDevices(supported: true, sharedEvents: false);
    addTearDown(service.dispose);

    final inputs = await service.enumerateInputs();
    expect(inputs.map((d) => d.deviceId), ['87', '92']);
    expect(inputs.map((d) => d.label), ['内建麦克风', 'USB Mic']);
    expect(inputs.every((d) => d.kind == 'audioinput'), isTrue);
  });

  test('enumeration preserves the native endpoint group id', () async {
    messenger.setMockMethodCallHandler(const MethodChannel(channelName), (
      call,
    ) async {
      if (call.method == 'enumerateInputs') {
        return [
          {'deviceId': 'mic_1', 'label': 'USB Mic', 'groupId': 'container_1'},
        ];
      }
      return const <Map<String, Object?>>[];
    });

    final service = SystemAudioDevices(supported: true, sharedEvents: false);
    addTearDown(service.dispose);

    final inputs = await service.enumerateInputs();
    expect(inputs.single.groupId, 'container_1');
  });

  test('enumerateInputs returns empty when the native side fails', () async {
    messenger.setMockMethodCallHandler(const MethodChannel(channelName), (
      call,
    ) async {
      throw PlatformException(code: 'unavailable');
    });

    final service = SystemAudioDevices(supported: true, sharedEvents: false);
    addTearDown(service.dispose);

    expect(await service.enumerateInputs(), isEmpty);
  });

  test(
    'enumerateOutputs maps the native list to audiooutput devices',
    () async {
      messenger.setMockMethodCallHandler(const MethodChannel(channelName), (
        call,
      ) async {
        if (call.method == 'enumerateOutputs') {
          return [
            {'deviceId': '54', 'label': '内建扬声器', 'isDefault': true},
            {'deviceId': '61', 'label': 'USB Speaker', 'isDefault': false},
            // Entries without a deviceId are dropped.
            {'deviceId': '', 'label': 'broken', 'isDefault': false},
          ];
        }
        return null;
      });

      final service = SystemAudioDevices(supported: true, sharedEvents: false);
      addTearDown(service.dispose);

      final outputs = await service.enumerateOutputs();
      expect(outputs.map((d) => d.deviceId), ['54', '61']);
      expect(outputs.map((d) => d.label), ['内建扬声器', 'USB Speaker']);
      expect(outputs.every((d) => d.kind == 'audiooutput'), isTrue);
    },
  );

  test('enumerateDevices captures inputs and outputs together', () async {
    final calls = <String>[];
    messenger.setMockMethodCallHandler(const MethodChannel(channelName), (
      call,
    ) async {
      calls.add(call.method);
      if (call.method == 'enumerateInputs') {
        return [
          {'deviceId': 'mic_1', 'label': 'Mic'},
        ];
      }
      if (call.method == 'enumerateOutputs') {
        return [
          {'deviceId': 'speaker_1', 'label': 'Speaker'},
        ];
      }
      return null;
    });

    final service = SystemAudioDevices(supported: true, sharedEvents: false);
    addTearDown(service.dispose);

    final devices = await service.enumerateDevices();

    expect(calls.where((call) => call == 'enumerateInputs'), hasLength(1));
    expect(calls.where((call) => call == 'enumerateOutputs'), hasLength(1));
    expect(devices.map((device) => device.deviceId), ['mic_1', 'speaker_1']);
  });

  test('enumerateOutputs returns empty when the native side fails', () async {
    messenger.setMockMethodCallHandler(const MethodChannel(channelName), (
      call,
    ) async {
      throw PlatformException(code: 'unavailable');
    });

    final service = SystemAudioDevices(supported: true, sharedEvents: false);
    addTearDown(service.dispose);

    expect(await service.enumerateOutputs(), isEmpty);
  });

  test('strict enumeration preserves native failures', () async {
    messenger.setMockMethodCallHandler(const MethodChannel(channelName), (
      call,
    ) async {
      throw PlatformException(code: 'device_query_timeout');
    });

    final service = SystemAudioDevices(supported: true, sharedEvents: false);
    addTearDown(service.dispose);

    await expectLater(
      service.enumerateDevicesOrThrow(),
      throwsA(
        isA<PlatformException>().having(
          (error) => error.code,
          'code',
          'device_query_timeout',
        ),
      ),
    );
  });

  test('inputChanges emits the new default when native notifies', () async {
    final startCalls = <String>[];
    messenger.setMockMethodCallHandler(const MethodChannel(channelName), (
      call,
    ) async {
      startCalls.add(call.method);
      return null;
    });

    final service = SystemAudioDevices(supported: true, sharedEvents: false);
    addTearDown(service.dispose);

    final emissions = <String?>[];
    final sub = service.inputChanges.listen(emissions.add);
    addTearDown(sub.cancel);

    // Subscribing asks the native side to start observing.
    await Future<void>.delayed(Duration.zero);
    expect(startCalls, contains('startListening'));

    // Simulate the native channel pushing a default-device change.
    await messenger.handlePlatformMessage(
      channelName,
      const StandardMethodCodec().encodeMethodCall(
        const MethodCall('defaultInputDeviceChanged', 'mic_3'),
      ),
      (_) {},
    );

    expect(emissions, ['mic_3']);
  });

  test('outputChanges emits the new default when native notifies', () async {
    messenger.setMockMethodCallHandler(const MethodChannel(channelName), (
      call,
    ) async {
      return null;
    });

    final service = SystemAudioDevices(supported: true, sharedEvents: false);
    addTearDown(service.dispose);

    final emissions = <String?>[];
    final sub = service.outputChanges.listen(emissions.add);
    addTearDown(sub.cancel);

    await messenger.handlePlatformMessage(
      channelName,
      const StandardMethodCodec().encodeMethodCall(
        const MethodCall('defaultOutputDeviceChanged', 'speaker_3'),
      ),
      (_) {},
    );

    expect(emissions, ['speaker_3']);
  });

  test('changes merges input, output, and Windows topology events', () async {
    messenger.setMockMethodCallHandler(const MethodChannel(channelName), (
      call,
    ) async {
      return null;
    });

    final service = SystemAudioDevices(supported: true, sharedEvents: false);
    addTearDown(service.dispose);

    final emissions = <String?>[];
    final sub = service.changes.listen(emissions.add);
    addTearDown(sub.cancel);

    for (final call in const [
      MethodCall('defaultInputDeviceChanged', 'mic_4'),
      MethodCall('defaultOutputDeviceChanged', 'speaker_4'),
      MethodCall('audioDevicesChanged'),
    ]) {
      await messenger.handlePlatformMessage(
        channelName,
        const StandardMethodCodec().encodeMethodCall(call),
        (_) {},
      );
    }

    expect(emissions, ['mic_4', 'speaker_4', null]);
  });

  test('videoChanges stays separate from audio endpoint changes', () async {
    messenger.setMockMethodCallHandler(const MethodChannel(channelName), (
      call,
    ) async {
      return null;
    });

    final service = SystemAudioDevices(supported: true, sharedEvents: false);
    addTearDown(service.dispose);

    var videoChanges = 0;
    final videoSub = service.videoChanges.listen((_) => videoChanges += 1);
    addTearDown(videoSub.cancel);
    final audioEmissions = <String?>[];
    final audioSub = service.changes.listen(audioEmissions.add);
    addTearDown(audioSub.cancel);

    await messenger.handlePlatformMessage(
      channelName,
      const StandardMethodCodec().encodeMethodCall(
        const MethodCall('videoDevicesChanged'),
      ),
      (_) {},
    );

    expect(videoChanges, 1);
    expect(audioEmissions, isEmpty);
  });

  test('listener registration retries after a transient failure', () async {
    var starts = 0;
    messenger.setMockMethodCallHandler(const MethodChannel(channelName), (
      call,
    ) async {
      if (call.method == 'startListening') {
        starts += 1;
        if (starts == 1) {
          throw PlatformException(code: 'temporarily_unavailable');
        }
      }
      return null;
    });

    final service = SystemAudioDevices(supported: true, sharedEvents: false);
    addTearDown(service.dispose);
    final subscription = service.changes.listen((_) {});
    addTearDown(subscription.cancel);

    final deadline = DateTime.now().add(const Duration(seconds: 2));
    while (starts < 2 && DateTime.now().isBefore(deadline)) {
      await Future<void>.delayed(const Duration(milliseconds: 10));
    }

    expect(starts, 2);
  });
}
