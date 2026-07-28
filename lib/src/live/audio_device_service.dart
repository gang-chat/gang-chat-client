import 'dart:io' show Platform;

import 'package:flutter/foundation.dart' show kIsWeb, visibleForTesting;
import 'package:livekit_client/livekit_client.dart' as lk;

import '../app/audio_device_info.dart';
import 'system_audio_devices.dart';

class LiveAudioDeviceService {
  const LiveAudioDeviceService({bool? systemAudioOnly})
    : _systemAudioOnlyOverride = systemAudioOnly;

  final bool? _systemAudioOnlyOverride;

  // Desktop native audio access. macOS merges native devices into WebRTC's
  // list; Windows treats the native list as authoritative and never asks the
  // WebRTC ADM to enumerate audio endpoints.
  static final SystemAudioDevices _systemAudio = SystemAudioDevices();

  bool get _systemAudioOnly {
    return _systemAudioOnlyOverride ??
        systemAudioOnlyForPlatform(
          isWeb: kIsWeb,
          isWindows: !kIsWeb && Platform.isWindows,
        );
  }

  Stream<List<AudioDeviceInfo>> get devicesChanged {
    if (_systemAudioOnly) {
      // Windows listens to the OS endpoint channel directly. LiveKit's generic
      // hardware callback historically called WebRTC getSources before
      // emitting, which is exactly the blocking path this service avoids.
      return _systemAudio.changes.asyncMap(
        (_) => _systemAudio.enumerateDevices(),
      );
    }
    // Re-merge native devices on every hardware change so a hotplug doesn't wipe
    // the CoreAudio-sourced devices that WebRTC can't see outside a room.
    return lk.Hardware.instance.onDeviceChange.stream.asyncMap((devices) async {
      return _mergeNativeDevices(_audioDeviceInfos(devices));
    });
  }

  Future<List<AudioDeviceInfo>> enumerateDevices() async {
    if (_systemAudioOnly) {
      return _systemAudio.enumerateDevicesOrThrow();
    }
    final webrtc = _audioDeviceInfos(
      await lk.Hardware.instance.enumerateDevices(),
    );
    return _mergeNativeDevices(webrtc);
  }

  // Adds CoreAudio input and output devices that WebRTC didn't report (matched
  // by deviceId, which is the same stringified AudioDeviceID on both sides).
  // When WebRTC already lists devices (i.e. inside a room) the native list
  // usually overlaps and nothing new is added.
  Future<List<AudioDeviceInfo>> _mergeNativeDevices(
    List<AudioDeviceInfo> webrtc,
  ) async {
    final native = [
      ...await _systemAudio.enumerateInputs(),
      ...await _systemAudio.enumerateOutputs(),
    ];
    if (native.isEmpty) return webrtc;
    final seen = webrtc.map((d) => '${d.kind}:${d.deviceId}').toSet();
    final merged = [...webrtc];
    for (final device in native) {
      if (seen.add('${device.kind}:${device.deviceId}')) merged.add(device);
    }
    return merged;
  }

  AudioDeviceInfo? get selectedAudioInput {
    return _audioDeviceInfoOrNull(lk.Hardware.instance.selectedAudioInput);
  }

  AudioDeviceInfo? get selectedAudioOutput {
    return _audioDeviceInfoOrNull(lk.Hardware.instance.selectedAudioOutput);
  }

  Future<void> selectAudioInput(AudioDeviceInfo device) {
    return lk.Hardware.instance.selectAudioInput(_mediaDevice(device));
  }

  Future<void> selectAudioOutput(AudioDeviceInfo device) {
    return lk.Hardware.instance.selectAudioOutput(_mediaDevice(device));
  }
}

@visibleForTesting
bool systemAudioOnlyForPlatform({
  required bool isWeb,
  required bool isWindows,
}) {
  return !isWeb && isWindows;
}

List<AudioDeviceInfo> _audioDeviceInfos(List<lk.MediaDevice> devices) {
  return devices.map(_audioDeviceInfo).toList();
}

AudioDeviceInfo? _audioDeviceInfoOrNull(lk.MediaDevice? device) {
  return device == null ? null : _audioDeviceInfo(device);
}

AudioDeviceInfo _audioDeviceInfo(lk.MediaDevice device) {
  return AudioDeviceInfo(
    deviceId: device.deviceId,
    label: device.label,
    kind: device.kind,
    groupId: device.groupId ?? '',
  );
}

lk.MediaDevice _mediaDevice(AudioDeviceInfo device) {
  return lk.MediaDevice(
    device.deviceId,
    device.label,
    device.kind,
    device.groupId,
  );
}
