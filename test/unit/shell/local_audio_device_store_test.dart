import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:client/src/app/audio_levels.dart';
import 'package:client/src/shell/local_audio_device_store.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  test('read returns defaults when nothing is stored', () async {
    final store = const LocalAudioDeviceStore();

    final stored = await store.read();

    expect(stored.inputDeviceId, isNull);
    expect(stored.outputDeviceId, isNull);
    expect(stored.inputVolume, 0.5);
    expect(stored.outputVolume, 0.5);
    expect(stored.musicBoxVolume, 0.5);
    expect(stored.screenShareVolume, 0.5);
    expect(stored.screenShareMaxHeight, 720);
    expect(stored.screenShareFrameRate, 30);
    expect(await store.readParticipantVoiceVolume('user_2'), 1.0);
  });

  test('writes round-trip through SharedPreferences', () async {
    final store = const LocalAudioDeviceStore();

    await store.writeInputDevicePreference(
      deviceId: '87',
      label: 'Desk Mic',
      groupId: 'group_mic',
    );
    await store.writeOutputDevicePreference(
      deviceId: '54',
      label: 'USB Headset',
      groupId: 'group_headset',
    );
    await store.writeInputVolume(0.4);
    await store.writeOutputVolume(0.6);
    await store.writeMusicBoxVolume(0.2);
    await store.writeScreenShareVolume(0.8);
    await store.writeScreenShareMaxHeight(720);
    await store.writeScreenShareFrameRate(30);
    await store.writeParticipantVoiceVolume('user/2', 1.75);

    final stored = await store.read();
    expect(stored.inputDeviceId, '87');
    expect(stored.inputDeviceLabel, 'Desk Mic');
    expect(stored.inputDeviceGroupId, 'group_mic');
    expect(stored.outputDeviceId, '54');
    expect(stored.outputDeviceLabel, 'USB Headset');
    expect(stored.outputDeviceGroupId, 'group_headset');
    expect(stored.inputVolume, closeTo(0.4, 1e-9));
    expect(stored.outputVolume, closeTo(0.6, 1e-9));
    expect(stored.musicBoxVolume, closeTo(0.2, 1e-9));
    expect(stored.screenShareVolume, closeTo(0.8, 1e-9));
    expect(stored.screenShareMaxHeight, 720);
    expect(stored.screenShareFrameRate, 30);
    expect(
      await store.readParticipantVoiceVolume('user/2'),
      closeTo(1.75, 1e-9),
    );
  });

  test('keeps an explicitly selected desktop high profile', () async {
    final store = const LocalAudioDeviceStore();

    await store.writeScreenShareMaxHeight(1080);
    await store.writeScreenShareFrameRate(60);
    final stored = await store.read();

    expect(stored.screenShareMaxHeight, 1080);
    expect(stored.screenShareFrameRate, 60);
  });

  test(
    'screen share height is coerced to a supported option on write',
    () async {
      final store = const LocalAudioDeviceStore();

      await store.writeScreenShareMaxHeight(999);
      final stored = await store.read();

      expect(stored.screenShareMaxHeight, 720);
    },
  );

  test('screen share frame rate is coerced to a supported option', () async {
    final store = const LocalAudioDeviceStore();

    await store.writeScreenShareFrameRate(24);
    final stored = await store.read();

    expect(stored.screenShareFrameRate, 30);
  });

  test('volumes are clamped to the valid range on write', () async {
    final store = const LocalAudioDeviceStore();

    await store.writeOutputVolume(5.0);
    await store.writeScreenShareVolume(-0.5);
    await store.writeParticipantVoiceVolume('user_2', 5.0);
    await store.writeParticipantVoiceVolume('user_3', -1.0);
    final stored = await store.read();

    expect(stored.outputVolume, normalizedAudioVolume(5.0));
    expect(stored.outputVolume, 1.0);
    expect(stored.screenShareVolume, 0.0);
    expect(await store.readParticipantVoiceVolume('user_2'), 2.0);
    expect(await store.readParticipantVoiceVolume('user_3'), 0.0);
  });
}
