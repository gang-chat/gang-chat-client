import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/audio_device_preferences.dart';

void main() {
  test('stored audio device ids trim empty storage values', () {
    expect(storedAudioDeviceIdFromStorageValue(null), isNull);
    expect(storedAudioDeviceIdFromStorageValue('  '), isNull);
    expect(storedAudioDeviceIdFromStorageValue(' mic_1 '), 'mic_1');
  });

  test('stored audio volume parsing falls back and clamps', () {
    expect(storedAudioVolumeFromStorageValue(null), 1);
    expect(storedAudioVolumeFromStorageValue('bad'), 1);
    expect(storedAudioVolumeFromStorageValue('-0.5'), 0);
    expect(storedAudioVolumeFromStorageValue('1.5'), 1);
    expect(storedAudioVolumeFromStorageValue('0.425'), 0.425);
    expect(audioVolumeStorageString(1.2), '1.000');
    expect(audioVolumeStorageString(0.425), '0.425');
  });

  test(
    'preferredStoredAudioDeviceFrom uses stored id then default fallback',
    () {
      const devices = [
        _Device('default', 'Default Mic', 'audioinput'),
        _Device('mic_1', 'Desk Mic', 'audioinput'),
        _Device('speaker_1', 'Speaker', 'audiooutput'),
      ];

      expect(
        preferredStoredAudioDeviceFrom(
          devices,
          kind: 'audioinput',
          storedDeviceId: 'mic_1',
          kindOf: _kindOf,
          deviceIdOf: _deviceIdOf,
        ),
        devices[1],
      );
      expect(
        preferredStoredAudioDeviceFrom(
          devices,
          kind: 'audioinput',
          storedDeviceId: 'missing',
          kindOf: _kindOf,
          deviceIdOf: _deviceIdOf,
        ),
        devices[0],
      );
      expect(
        preferredStoredAudioDeviceFrom(
          devices,
          kind: 'audiooutput',
          storedDeviceId: 'missing',
          kindOf: _kindOf,
          deviceIdOf: _deviceIdOf,
        ),
        isNull,
      );
    },
  );

  test(
    'preferredStoredAudioDeviceFrom restores hotplugged device signature',
    () {
      const devices = [
        _Device('speaker_2', 'Desk Speaker', 'audiooutput', 'group_speaker'),
        _Device('speaker_new', 'USB Headset', 'audiooutput', 'group_headset'),
      ];

      expect(
        preferredStoredAudioDeviceFrom(
          devices,
          kind: 'audiooutput',
          storedDeviceId: 'speaker_old',
          storedDeviceLabel: 'USB Headset',
          storedDeviceGroupId: 'group_headset',
          systemDefaultDeviceId: 'speaker_2',
          kindOf: _kindOf,
          deviceIdOf: _deviceIdOf,
          labelOf: _labelOf,
          groupIdOf: _groupIdOf,
        ),
        devices[1],
      );
    },
  );

  test(
    'preferredStoredAudioDeviceFrom avoids ambiguous label-only restoration',
    () {
      const devices = [
        _Device('speaker_1', 'USB Headset', 'audiooutput'),
        _Device('speaker_2', 'USB Headset', 'audiooutput'),
        _Device('speaker_3', 'Desk Speaker', 'audiooutput'),
      ];

      expect(
        preferredStoredAudioDeviceFrom(
          devices,
          kind: 'audiooutput',
          storedDeviceId: 'speaker_old',
          storedDeviceLabel: 'USB Headset',
          storedDeviceGroupId: null,
          systemDefaultDeviceId: 'speaker_3',
          kindOf: _kindOf,
          deviceIdOf: _deviceIdOf,
          labelOf: _labelOf,
          groupIdOf: _groupIdOf,
        ),
        devices[2],
      );
    },
  );

  test(
    'preferredStoredAudioDeviceFrom restores unique label after group id churn',
    () {
      const devices = [
        _Device('speaker_new', 'USB Headset', 'audiooutput', 'group_new'),
        _Device('speaker_2', 'Desk Speaker', 'audiooutput', 'group_desk'),
      ];

      expect(
        preferredStoredAudioDeviceFrom(
          devices,
          kind: 'audiooutput',
          storedDeviceId: 'speaker_old',
          storedDeviceLabel: 'USB Headset',
          storedDeviceGroupId: 'group_old',
          systemDefaultDeviceId: 'speaker_2',
          kindOf: _kindOf,
          deviceIdOf: _deviceIdOf,
          labelOf: _labelOf,
          groupIdOf: _groupIdOf,
        ),
        devices[0],
      );
    },
  );

  test('group id churn does not restore an ambiguous device label', () {
    const devices = [
      _Device('speaker_a', 'USB Headset', 'audiooutput', 'group_a'),
      _Device('speaker_b', 'USB Headset', 'audiooutput', 'group_b'),
      _Device('speaker_default', 'Desk Speaker', 'audiooutput', 'group_desk'),
    ];

    expect(
      preferredStoredAudioDeviceFrom(
        devices,
        kind: 'audiooutput',
        storedDeviceId: 'speaker_old',
        storedDeviceLabel: 'USB Headset',
        storedDeviceGroupId: 'group_old',
        systemDefaultDeviceId: 'speaker_default',
        kindOf: _kindOf,
        deviceIdOf: _deviceIdOf,
        labelOf: _labelOf,
        groupIdOf: _groupIdOf,
      ),
      devices[2],
    );
  });

  test(
    'preferredStoredAudioDeviceFrom prefers stored, then system default, then '
    'synthetic default',
    () {
      const devices = [
        _Device('default', 'Default Mic', 'audioinput'),
        _Device('mic_1', 'Desk Mic', 'audioinput'),
        _Device('mic_2', 'Room Mic', 'audioinput'),
      ];

      // A saved preference still wins over the OS default.
      expect(
        preferredStoredAudioDeviceFrom(
          devices,
          kind: 'audioinput',
          storedDeviceId: 'mic_1',
          systemDefaultDeviceId: 'mic_2',
          kindOf: _kindOf,
          deviceIdOf: _deviceIdOf,
        ),
        devices[1],
      );
      // No saved preference: follow the system default the native channel
      // reported, ahead of the synthetic "default" device.
      expect(
        preferredStoredAudioDeviceFrom(
          devices,
          kind: 'audioinput',
          storedDeviceId: null,
          systemDefaultDeviceId: 'mic_2',
          kindOf: _kindOf,
          deviceIdOf: _deviceIdOf,
        ),
        devices[2],
      );
      // No saved preference and no usable system default: fall back to the
      // synthetic "default" device (the Windows path).
      expect(
        preferredStoredAudioDeviceFrom(
          devices,
          kind: 'audioinput',
          storedDeviceId: null,
          systemDefaultDeviceId: 'missing',
          kindOf: _kindOf,
          deviceIdOf: _deviceIdOf,
        ),
        devices[0],
      );
    },
  );

  test('selectStoredAudioDeviceIfChanged skips selected devices', () async {
    const device = _Device('mic_1', 'Desk Mic', 'audioinput');
    var selects = 0;

    final selected = await selectStoredAudioDeviceIfChanged(
      selected: device,
      device: device,
      select: (_) async => selects += 1,
      kindOf: _kindOf,
      deviceIdOf: _deviceIdOf,
    );

    expect(selected, device);
    expect(selects, 0);
  });

  test('selectStoredAudioDeviceIfChanged reports failed selection as null', () {
    const selected = _Device('mic_1', 'Desk Mic', 'audioinput');
    const next = _Device('mic_2', 'Room Mic', 'audioinput');

    expect(
      selectStoredAudioDeviceIfChanged(
        selected: selected,
        device: next,
        select: (_) async => throw StateError('denied'),
        kindOf: _kindOf,
        deviceIdOf: _deviceIdOf,
      ),
      completion(isNull),
    );
  });
}

class _Device {
  const _Device(this.deviceId, this.label, this.kind, [this.groupId = '']);

  final String deviceId;
  final String label;
  final String kind;
  final String groupId;
}

String _kindOf(_Device device) => device.kind;
String _deviceIdOf(_Device device) => device.deviceId;
String _labelOf(_Device device) => device.label;
String _groupIdOf(_Device device) => device.groupId;
