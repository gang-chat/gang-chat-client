import 'audio_device_display.dart';
import 'audio_levels.dart';
import '../live/screen_share_quality.dart';

const defaultAudioVolume = 0.5;

class StoredAudioDevices {
  const StoredAudioDevices({
    this.inputDeviceId,
    this.inputDeviceLabel,
    this.inputDeviceGroupId,
    this.outputDeviceId,
    this.outputDeviceLabel,
    this.outputDeviceGroupId,
    this.inputVolume = defaultAudioVolume,
    this.outputVolume = defaultAudioVolume,
    this.musicBoxVolume = defaultAudioVolume,
    this.screenShareVolume = defaultAudioVolume,
    this.screenShareMaxHeight = defaultScreenShareMaxHeight,
    this.screenShareFrameRate = defaultScreenShareFrameRate,
  });

  final String? inputDeviceId;
  final String? inputDeviceLabel;
  final String? inputDeviceGroupId;
  final String? outputDeviceId;
  final String? outputDeviceLabel;
  final String? outputDeviceGroupId;
  final double inputVolume;
  final double outputVolume;

  /// Local listening volume for the music box bot's audio track. Independent of
  /// [outputVolume] — it scales only the `__musicbox__` participant.
  final double musicBoxVolume;

  /// Local listening volume for remote screen-share audio. Independent of
  /// [outputVolume], which scales ordinary voice tracks.
  final double screenShareVolume;

  /// Target max height (px) for the local screen share — one of
  /// [screenShareHeightOptions]. [defaultScreenShareMaxHeight] is the balanced
  /// resolution; lower values cap what we publish to save bandwidth.
  final int screenShareMaxHeight;

  /// Selected publisher frame-rate cap. Platforms may clamp this to their
  /// capture ceiling (Android currently caps it at 30 FPS).
  final int screenShareFrameRate;

  bool get isEmpty =>
      (inputDeviceId == null || inputDeviceId!.isEmpty) &&
      (inputDeviceLabel == null || inputDeviceLabel!.isEmpty) &&
      (inputDeviceGroupId == null || inputDeviceGroupId!.isEmpty) &&
      (outputDeviceId == null || outputDeviceId!.isEmpty) &&
      (outputDeviceLabel == null || outputDeviceLabel!.isEmpty) &&
      (outputDeviceGroupId == null || outputDeviceGroupId!.isEmpty) &&
      inputVolume == defaultAudioVolume &&
      outputVolume == defaultAudioVolume &&
      musicBoxVolume == defaultAudioVolume &&
      screenShareVolume == defaultAudioVolume &&
      screenShareMaxHeight == defaultScreenShareMaxHeight &&
      screenShareFrameRate == defaultScreenShareFrameRate;
}

class RestoredAudioDevices<T> {
  const RestoredAudioDevices({this.input, this.output});

  final T? input;
  final T? output;
}

T? preferredStoredAudioDeviceFrom<T>(
  List<T> devices, {
  required String kind,
  required String? storedDeviceId,
  required AudioDeviceKindOf<T> kindOf,
  required AudioDeviceIdOf<T> deviceIdOf,
  String? storedDeviceLabel,
  String? storedDeviceGroupId,
  AudioDeviceLabelOf<T>? labelOf,
  AudioDeviceGroupIdOf<T>? groupIdOf,
  String? systemDefaultDeviceId,
}) {
  final storedDevice = storedPreferredAudioDeviceFrom(
    devices,
    kind: kind,
    storedDeviceId: storedDeviceId,
    storedDeviceLabel: storedDeviceLabel,
    storedDeviceGroupId: storedDeviceGroupId,
    kindOf: kindOf,
    deviceIdOf: deviceIdOf,
    labelOf: labelOf,
    groupIdOf: groupIdOf,
  );
  if (storedDevice != null) return storedDevice;

  // macOS never enumerates a synthetic "default" device, so the OS-selected
  // device is identified by an explicit id from the native channel. Prefer it
  // when there is no local preference yet so the picker follows the system.
  final systemDefault = storedAudioDeviceFrom(
    devices,
    kind: kind,
    deviceId: systemDefaultDeviceId,
    kindOf: kindOf,
    deviceIdOf: deviceIdOf,
  );
  if (systemDefault != null) return systemDefault;

  // WebRTC exposes the OS-selected device as the synthetic "default" device
  // on Windows. Use it whenever there is no local preference yet, or when the
  // saved device is temporarily unavailable.
  return storedAudioDeviceFrom(
    devices,
    kind: kind,
    deviceId: 'default',
    kindOf: kindOf,
    deviceIdOf: deviceIdOf,
  );
}

T? storedPreferredAudioDeviceFrom<T>(
  List<T> devices, {
  required String kind,
  required String? storedDeviceId,
  required String? storedDeviceLabel,
  required String? storedDeviceGroupId,
  required AudioDeviceKindOf<T> kindOf,
  required AudioDeviceIdOf<T> deviceIdOf,
  required AudioDeviceLabelOf<T>? labelOf,
  required AudioDeviceGroupIdOf<T>? groupIdOf,
}) {
  final storedDevice = storedAudioDeviceFrom(
    devices,
    kind: kind,
    deviceId: storedDeviceId,
    kindOf: kindOf,
    deviceIdOf: deviceIdOf,
  );
  if (storedDevice != null) return storedDevice;

  return storedAudioDeviceBySignatureFrom(
    devices,
    kind: kind,
    label: storedDeviceLabel,
    groupId: storedDeviceGroupId,
    kindOf: kindOf,
    labelOf: labelOf,
    groupIdOf: groupIdOf,
  );
}

T? storedAudioDeviceFrom<T>(
  List<T> devices, {
  required String kind,
  required String? deviceId,
  required AudioDeviceKindOf<T> kindOf,
  required AudioDeviceIdOf<T> deviceIdOf,
}) {
  if (deviceId == null || deviceId.isEmpty) return null;
  for (final device in devices) {
    if (kindOf(device) == kind && deviceIdOf(device) == deviceId) {
      return device;
    }
  }
  return null;
}

T? storedAudioDeviceBySignatureFrom<T>(
  List<T> devices, {
  required String kind,
  required String? label,
  required String? groupId,
  required AudioDeviceKindOf<T> kindOf,
  required AudioDeviceLabelOf<T>? labelOf,
  required AudioDeviceGroupIdOf<T>? groupIdOf,
}) {
  final storedLabel = _normalizedAudioDeviceSignaturePart(label);
  final storedGroupId = _normalizedAudioDeviceSignaturePart(groupId);
  if (storedLabel == null && storedGroupId == null) return null;
  if (storedLabel != null && labelOf == null) return null;
  if (storedGroupId != null && groupIdOf == null) return null;

  final matches = <T>[];
  final uniqueLabelFallbacks = <T>[];
  for (final device in devices) {
    if (kindOf(device) != kind) continue;
    final deviceLabel = labelOf == null
        ? null
        : _normalizedAudioDeviceSignaturePart(labelOf(device));
    final deviceGroupId = groupIdOf == null
        ? null
        : _normalizedAudioDeviceSignaturePart(groupIdOf(device));
    final labelMatches = storedLabel != null && deviceLabel == storedLabel;
    final groupMatches =
        storedGroupId != null && deviceGroupId == storedGroupId;

    if (labelMatches) {
      uniqueLabelFallbacks.add(device);
    }
    if (storedGroupId != null) {
      if (groupMatches && (storedLabel == null || labelMatches)) {
        matches.add(device);
      }
    } else if (labelMatches) {
      matches.add(device);
    }
  }
  if (matches.length == 1) return matches.single;
  // Some platforms change endpoint ids/group ids across Bluetooth profile
  // transitions or driver reinstalls. A unique label is safe to restore; an
  // ambiguous label still falls back to the system default.
  if (matches.isEmpty &&
      storedGroupId != null &&
      uniqueLabelFallbacks.length == 1) {
    return uniqueLabelFallbacks.single;
  }
  return null;
}

Future<T?> selectStoredAudioDeviceIfChanged<T>({
  required T? selected,
  required T device,
  required Future<void> Function(T device) select,
  required AudioDeviceKindOf<T> kindOf,
  required AudioDeviceIdOf<T> deviceIdOf,
}) async {
  if (isSameAudioDevice(
    selected,
    device,
    kindOf: kindOf,
    deviceIdOf: deviceIdOf,
  )) {
    return device;
  }
  try {
    await select(device);
    return device;
  } catch (_) {
    return null;
  }
}

String? storedAudioDeviceIdFromStorageValue(String? value) {
  final trimmed = value?.trim();
  return trimmed == null || trimmed.isEmpty ? null : trimmed;
}

String? storedAudioDeviceSignatureFromStorageValue(String? value) {
  final trimmed = value?.trim();
  return trimmed == null || trimmed.isEmpty ? null : trimmed;
}

double storedAudioVolumeFromStorageValue(String? value) {
  final parsed = double.tryParse(value ?? '');
  if (parsed == null) return 1.0;
  return normalizedAudioVolume(parsed);
}

String audioVolumeStorageString(double volume) {
  return normalizedAudioVolume(volume).toStringAsFixed(3);
}

String? _normalizedAudioDeviceSignaturePart(String? value) {
  final trimmed = value?.trim();
  if (trimmed == null || trimmed.isEmpty) return null;
  return trimmed.toLowerCase();
}
